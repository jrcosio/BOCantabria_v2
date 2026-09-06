package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.fake.RealDispatchers
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.TlsMockWebServer
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/**
 * The resumable upload, against a server that speaks TLS.
 *
 * Until feature 014 this class had no test at all. It gained one with PERF-002 because four of its
 * calls migrate to [Call.await]: the announcement, the bytes, the poll and the deletion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpGeminiDocumentUploaderTest {

    @get:Rule
    val tls = TlsMockWebServer()

    @get:Rule
    val folder = TemporaryFolder()

    private val crashReporter = RecordingCrashReporter()

    @Test
    fun `a document is announced, sent and polled until active`() = runTest {
        val bytes = "%PDF-1.7 boletín".toByteArray()
        val file = folder.newFile("doc.pdf").apply { writeBytes(bytes) }
        tls.server.enqueue(startResponse(tls.url("/upload-session")))
        tls.server.enqueue(jsonResponse(200, envelope(state = "PROCESSING")))
        tls.server.enqueue(jsonResponse(200, fileJson(state = "ACTIVE")))

        val result = uploader().upload(file.absolutePath, "BOC 2026-6695")

        val success = result as UploadResult.Success
        assertEquals("files/abc123", success.document.remoteName)
        assertEquals(FILE_URI, success.document.fileUri)
        assertEquals("application/pdf", success.document.mimeType)

        val start = tls.server.takeRequest()
        assertEquals(API_KEY, start.headers[OkHttpGeminiDocumentUploader.HEADER_API_KEY])
        assertEquals("start", start.headers[OkHttpGeminiDocumentUploader.HEADER_UPLOAD_COMMAND])
        assertEquals(bytes.size.toString(), start.headers[OkHttpGeminiDocumentUploader.HEADER_UPLOAD_CONTENT_LENGTH])
        assertTrue(start.body!!.utf8().contains("BOC 2026-6695"))

        val upload = tls.server.takeRequest()
        assertEquals("/upload-session", upload.url.encodedPath)
        assertEquals("upload, finalize", upload.headers[OkHttpGeminiDocumentUploader.HEADER_UPLOAD_COMMAND])
        assertEquals("0", upload.headers[OkHttpGeminiDocumentUploader.HEADER_UPLOAD_OFFSET])
        assertTrue(upload.body!!.toByteArray().contentEquals(bytes))

        val poll = tls.server.takeRequest()
        assertEquals("/v1beta/files/abc123", poll.url.encodedPath)
        assertTrue(crashReporter.messages.any { it == "upload: ready after 1 poll(s)" })
    }

    @Test
    fun `a start without an upload url is malformed`() = runTest {
        val file = folder.newFile("doc.pdf").apply { writeBytes("%PDF-".toByteArray()) }
        tls.server.enqueue(MockResponse.Builder().code(200).build())

        val result = uploader().upload(file.absolutePath, "BOC")

        assertEquals(UploadResult.Rejected(GeminiRefusal.Malformed), result)
        assertEquals(1, tls.server.requestCount)
        assertTrue(crashReporter.messages.any { it == "upload: start gave no upload url" })
    }

    @Test
    fun `polling stops at the ceiling`() = runTest {
        val file = folder.newFile("doc.pdf").apply { writeBytes("%PDF-".toByteArray()) }
        tls.server.enqueue(startResponse(tls.url("/upload-session")))
        tls.server.enqueue(jsonResponse(200, envelope(state = "PROCESSING")))
        repeat(OkHttpGeminiDocumentUploader.MAX_POLLS) {
            tls.server.enqueue(jsonResponse(200, fileJson(state = "PROCESSING")))
        }

        val result = uploader().upload(file.absolutePath, "BOC")

        assertEquals(UploadResult.Rejected(GeminiRefusal.Malformed), result)
        assertEquals(2 + OkHttpGeminiDocumentUploader.MAX_POLLS, tls.server.requestCount)
    }

    @Test
    fun `delete issues a DELETE and never throws on failure`() = runTest {
        tls.server.enqueue(MockResponse.Builder().code(500).build())

        uploader().delete("files/abc123")

        val request = tls.server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v1beta/files/abc123", request.url.encodedPath)
        assertEquals(API_KEY, request.headers[OkHttpGeminiDocumentUploader.HEADER_API_KEY])
    }

    /**
     * Feature 014, PERF-002: cancelling the coroutine has to cancel the call and come back promptly.
     * Before, the upload thread stayed blocked until the service answered or the timeout expired.
     */
    @Test
    fun `cancelling mid-upload is a cancellation and the call is cancelled`() = runBlocking {
        val file = folder.newFile("doc.pdf").apply { writeBytes(ByteArray(64 * 1024) { 0x25 }) }
        tls.server.enqueue(startResponse(tls.url("/upload-session")))
        tls.server.enqueue(
            MockResponse.Builder()
                .code(200)
                .headersDelay(30, TimeUnit.SECONDS)
                .body(envelope(state = "ACTIVE"))
                .build(),
        )

        var outcome: Any? = null
        val job = launch(Dispatchers.IO) {
            outcome = runCatching { uploader(RealDispatchers).upload(file.absolutePath, "BOC") }.getOrElse { it }
        }
        while (tls.server.requestCount < 2) Thread.sleep(10)
        Thread.sleep(100)

        val started = System.nanoTime()
        job.cancel()
        job.join()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue("tardó $elapsedMillis ms en volver", elapsedMillis < 5_000)
        assertTrue("debía ser una cancelación, fue: $outcome", outcome is CancellationException)
        assertTrue("la llamada no se canceló", tls.calls.last().isCanceled())
        assertFalse(crashReporter.messages.any { it.contains("network:") })
    }

    @Test
    fun `the log never carries the credential`() = runTest {
        val file = folder.newFile("doc.pdf").apply { writeBytes("%PDF-".toByteArray()) }
        tls.server.enqueue(MockResponse.Builder().code(500).build())

        uploader().upload(file.absolutePath, "BOC")

        assertTrue(crashReporter.messages.any { it == "upload: start HTTP 500" })
        assertFalse(crashReporter.messages.any { it.contains(API_KEY) })
    }

    // ---------- Helpers ----------

    private fun TestScope.uploader(): OkHttpGeminiDocumentUploader =
        uploader(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))

    private fun uploader(dispatchers: DispatcherProvider) = OkHttpGeminiDocumentUploader(
        client = tls.client,
        apiKeys = { API_KEY },
        coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter),
        dispatchers = dispatchers,
        crashReporter = crashReporter,
        baseUrl = tls.url("").removeSuffix("/"),
    )

    private fun startResponse(uploadUrl: String) = MockResponse.Builder()
        .code(200)
        .setHeader(OkHttpGeminiDocumentUploader.HEADER_UPLOAD_URL, uploadUrl)
        .build()

    private fun jsonResponse(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun fileJson(state: String) =
        """{"name":"files/abc123","uri":"$FILE_URI","mimeType":"application/pdf","state":"$state"}"""

    private fun envelope(state: String) = """{"file":${fileJson(state)}}"""

    private object FixedClock : TimeProvider {
        override fun nowMillis(): Long = 1_700_000_000_000L
    }

    private object NoJitter : RandomProvider {
        override fun nextLong(bound: Long): Long = 0
    }

    private companion object {
        /** Deliberately unlike a real key: the repository's leak check must not cry wolf. */
        const val API_KEY = "clave-de-prueba-que-no-es-una-clave"
        const val FILE_URI = "https://generativelanguage.googleapis.com/v1beta/files/abc123"
    }
}
