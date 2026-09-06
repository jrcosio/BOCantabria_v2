package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/**
 * The upload, written by hand over the Files API.
 *
 * **Written by hand rather than taken from the official library, and not by preference.** The
 * library's Android artifact throws on construction when given an API key — a hard guard, not a
 * warning — so on Android it cannot be used at all without a backend of our own. The whole story is
 * in research.md D-227.
 *
 * The protocol is Google's resumable upload and it is three calls:
 *
 * 1. `POST /upload/v1beta/files` with `X-Goog-Upload-Command: start` and the metadata. The answer
 *    carries the real upload address in the `x-goog-upload-url` header.
 * 2. `POST <that address>` with `upload, finalize` and the bytes.
 * 3. `GET /v1beta/files/<name>` until the state stops being `PROCESSING`, because the service indexes
 *    the document before letting anyone ask about it.
 *
 * The bytes are **streamed** from disk with `File.asRequestBody`, not read into memory first. The
 * research for this feature reasoned that reading it whole would be acceptable —
 * `OkHttpDocumentDownloader` already refuses anything over 25 MB — and that is true, but OkHttp
 * streams for free and there was no reason to hold a bulletin in the heap to prove a point.
 */
class OkHttpGeminiDocumentUploader(
    client: OkHttpClient,
    private val apiKeys: GeminiApiKeyProvider,
    private val coordinator: GeminiRateLimitCoordinator,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : AiDocumentUploader {

    // Derived from the shared client so the connection pool is the same one.
    private val client = client.newBuilder().build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun upload(localPath: String, displayName: String): UploadResult =
        withContext(dispatchers.io) {
            val key = apiKeys.apiKey() ?: return@withContext UploadResult.Rejected(
                GeminiRefusal.NotConfigured,
            )
            try {
                val file = File(localPath)
                crashReporter.log("upload: sending ${file.length() / BYTES_PER_KB} KB")

                val uploadUrl = beginUpload(key, file.length(), displayName)
                    ?: return@withContext UploadResult.Rejected(GeminiRefusal.Malformed)

                val uploaded = sendBytes(key, uploadUrl, file)
                    ?: return@withContext UploadResult.Rejected(GeminiRefusal.Malformed)

                awaitReady(key, uploaded)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IOException) {
                // A cancelled call surfaces as an IOException on OkHttp's side (009 research.md D-119,
                // 010 D-218); since feature 014 `await` cancels the call itself, and this check stays
                // as the second line of defence.
                currentCoroutineContext().ensureActive()
                crashReporter.log("upload: network: ${error.javaClass.simpleName}")
                UploadResult.Rejected(GeminiRefusal.Network)
            } catch (error: Throwable) {
                crashReporter.log("upload failed: ${error.javaClass.simpleName}: ${error.message}")
                UploadResult.Rejected(GeminiRefusal.Malformed)
            }
        }

    // The four calls below go through `Call.await` rather than `execute()`: cancelling the coroutine
    // cancels the call and frees the socket, also halfway through the streamed bytes (014, PERF-002).

    /** Step one: announce the file and get the address the bytes go to. */
    private suspend fun beginUpload(key: String, length: Long, displayName: String): String? {
        val request = Request.Builder()
            .url("$baseUrl/upload/$API_VERSION/files")
            .header(HEADER_API_KEY, key)
            .header(HEADER_UPLOAD_PROTOCOL, "resumable")
            .header(HEADER_UPLOAD_COMMAND, "start")
            .header(HEADER_UPLOAD_CONTENT_LENGTH, length.toString())
            .header(HEADER_UPLOAD_CONTENT_TYPE, MIME_TYPE_PDF)
            .post(
                json.encodeToString(GeminiFileUploadStart(GeminiFileDisplayName(displayName)))
                    .toRequestBody(MEDIA_TYPE_JSON.toMediaType()),
            )
            .build()

        return client.newCall(request).await { response ->
            if (!response.isSuccessful) {
                crashReporter.log("upload: start HTTP ${response.code}")
                return@await null
            }
            response.header(HEADER_UPLOAD_URL)
                ?: null.also { crashReporter.log("upload: start gave no upload url") }
        }
    }

    /** Step two: the bytes, in one go. */
    private suspend fun sendBytes(key: String, uploadUrl: String, file: File): GeminiFile? {
        val request = Request.Builder()
            .url(uploadUrl)
            .header(HEADER_API_KEY, key)
            .header(HEADER_UPLOAD_COMMAND, "upload, finalize")
            .header(HEADER_UPLOAD_OFFSET, "0")
            .post(file.asRequestBody(MIME_TYPE_PDF.toMediaType()))
            .build()

        return client.newCall(request).await { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                crashReporter.log("upload: bytes HTTP ${response.code}")
                return@await null
            }
            runCatching { json.decodeFromString<GeminiFileEnvelope>(body).file }.getOrNull()
        }
    }

    /**
     * Step three: wait until the service has indexed it, with a **ceiling**.
     *
     * A `while (state == PROCESSING)` with no ceiling is a screen spinning for ever, and this project
     * already knows what a failure that looks like a hang costs (FR-012).
     */
    private suspend fun awaitReady(key: String, uploaded: GeminiFile): UploadResult {
        val name = uploaded.name ?: return UploadResult.Rejected(GeminiRefusal.Malformed)
        var file = uploaded
        var polls = 0

        while (file.state == STATE_PROCESSING && polls < MAX_POLLS) {
            delay(POLL_INTERVAL_MILLIS)
            polls++
            file = fetch(key, name) ?: return UploadResult.Rejected(GeminiRefusal.Malformed)
        }

        return when {
            file.state == STATE_ACTIVE && file.uri != null -> {
                crashReporter.log("upload: ready after $polls poll(s)")
                UploadResult.Success(
                    UploadedDocument(
                        remoteName = name,
                        fileUri = requireNotNull(file.uri),
                        mimeType = file.mimeType ?: MIME_TYPE_PDF,
                    ),
                )
            }

            else -> {
                crashReporter.log("upload: state=${file.state} after $polls poll(s)")
                UploadResult.Rejected(GeminiRefusal.Malformed)
            }
        }
    }

    private suspend fun fetch(key: String, name: String): GeminiFile? {
        val request = Request.Builder()
            .url("$baseUrl/$API_VERSION/$name")
            .header(HEADER_API_KEY, key)
            .get()
            .build()

        return client.newCall(request).await { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                crashReporter.log("upload: poll HTTP ${response.code}")
                return@await null
            }
            runCatching { json.decodeFromString<GeminiFile>(body) }.getOrNull()
        }
    }

    override suspend fun delete(remoteName: String) {
        withContext(dispatchers.io) {
            val key = apiKeys.apiKey() ?: return@withContext
            val request = Request.Builder()
                .url("$baseUrl/$API_VERSION/$remoteName")
                .header(HEADER_API_KEY, key)
                .delete()
                .build()
            // A deletion that fails must not cover up whatever else was happening, and the service
            // expires the file on its own anyway (FR-011). A cancellation is not a failure to cover
            // up, though: it is rethrown, where `runCatching` used to swallow it (014 D-620).
            try {
                client.newCall(request).await { }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                crashReporter.log("delete failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        const val API_VERSION = "v1beta"

        const val HEADER_API_KEY = "x-goog-api-key"
        const val HEADER_UPLOAD_PROTOCOL = "X-Goog-Upload-Protocol"
        const val HEADER_UPLOAD_COMMAND = "X-Goog-Upload-Command"
        const val HEADER_UPLOAD_CONTENT_LENGTH = "X-Goog-Upload-Header-Content-Length"
        const val HEADER_UPLOAD_CONTENT_TYPE = "X-Goog-Upload-Header-Content-Type"
        const val HEADER_UPLOAD_OFFSET = "X-Goog-Upload-Offset"
        const val HEADER_UPLOAD_URL = "x-goog-upload-url"

        const val MIME_TYPE_PDF = "application/pdf"
        const val BYTES_PER_KB = 1024
        const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"

        const val STATE_PROCESSING = "PROCESSING"
        const val STATE_ACTIVE = "ACTIVE"

        /** Twenty seconds of ceiling for a bulletin of a few hundred kilobytes. */
        const val MAX_POLLS = 20
        const val POLL_INTERVAL_MILLIS = 1_000L
    }
}
