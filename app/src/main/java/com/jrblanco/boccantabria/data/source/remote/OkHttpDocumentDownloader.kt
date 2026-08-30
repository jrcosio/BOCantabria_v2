package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Fetches an official document over HTTP, refusing anything that is not one.
 *
 * The order of the checks is the design, not an accident: each one runs at the earliest moment it
 * can, so a five-megabyte error page is never downloaded whole to be thrown away.
 *
 * 1. **Scheme and host, before opening a socket.**
 * 2. **Declared type, with the headers**, before reading the body.
 * 3. **The first bytes**, because a header is the easiest thing in a response to get wrong.
 * 4. **The size cap, while reading**, so a hostile length cannot fill memory.
 *
 * The timings are longer than the feed reader's — a document is not a forty-kilobyte XML — which is
 * why the client is **derived** from the shared one instead of being a second client with its own
 * connection pool.
 */
class OkHttpDocumentDownloader(
    client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
    private val allowedHost: String = BULLETIN_HOST,
) : DocumentDownloader {

    private val client: OkHttpClient = client.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun download(url: String, into: File): DownloadResult {
        addressRefusal(url)?.let { return DownloadResult.Rejected(it) }

        return withContext(dispatchers.io) {
            try {
                client.newCall(request(url)).execute().use { response ->
                    writeVerified(response, into)
                }
            } catch (cancellation: CancellationException) {
                // Cancelling a coroutine is not a network problem, and the caller has to know the
                // difference: one leaves a temporary to clean up, the other is a real refusal.
                throw cancellation
            } catch (_: IOException) {
                into.truncate()
                DownloadResult.Rejected(DownloadRefusal.Network)
            }
        }
    }

    /** Everything that can be decided from the address alone, before any connection is made. */
    private fun addressRefusal(url: String): DownloadRefusal? {
        if (!url.startsWith(HTTPS_PREFIX)) return DownloadRefusal.InsecureScheme
        val host = url.removePrefix(HTTPS_PREFIX).substringBefore('/').substringBefore(':')
        return if (host.equals(allowedHost, ignoreCase = true)) null else DownloadRefusal.UnexpectedHost
    }

    private fun writeVerified(response: Response, into: File): DownloadResult {
        if (!response.isSuccessful) {
            into.truncate()
            return DownloadResult.Rejected(DownloadRefusal.HttpError(response.code))
        }

        val declared = response.header(HEADER_CONTENT_TYPE).orEmpty().lowercase()
        if (!declared.contains(PDF_TYPE)) {
            into.truncate()
            return DownloadResult.Rejected(DownloadRefusal.UnexpectedType)
        }

        val body = response.body ?: run {
            into.truncate()
            return DownloadResult.Rejected(DownloadRefusal.NotAPdf)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L

        val source = body.source()
        // Waits until the opening bytes are actually there. A single read is not guaranteed to fill
        // anything: on a slow connection the first one can return one byte, and deciding from that
        // would refuse a perfectly good document for arriving in dribs and drabs.
        try {
            source.require(MAGIC.size.toLong())
        } catch (_: java.io.EOFException) {
            into.truncate()
            return DownloadResult.Rejected(DownloadRefusal.NotAPdf)
        }

        // The content gets the last word over the header, which is the easiest thing in a response
        // to get wrong.
        if (!source.buffer.rangeEquals(0, MAGIC)) {
            into.truncate()
            return DownloadResult.Rejected(DownloadRefusal.NotAPdf)
        }

        source.use { input ->
            into.outputStream().buffered().use { sink ->
                val buffer = ByteArray(BUFFER_BYTES)
                var read = input.read(buffer)
                while (read > 0) {
                    total += read
                    if (total > MAX_BYTES) {
                        sink.flush()
                        into.truncate()
                        return DownloadResult.Rejected(DownloadRefusal.TooLarge)
                    }
                    sink.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
        }

        val checksum = digest.digest().joinToString("") { "%02x".format(it) }
        return DownloadResult.Downloaded(byteCount = total, checksum = checksum)
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header(HEADER_ACCEPT, PDF_TYPE)
        .header(HEADER_USER_AGENT, USER_AGENT)
        .get()
        .build()

    /** Leaves nothing usable behind. Every refusal goes through here. */
    private fun File.truncate() {
        runCatching { if (exists()) writeBytes(ByteArray(0)) }
    }

    companion object {
        const val BULLETIN_HOST = "boc.cantabria.es"

        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 60L
        const val CALL_TIMEOUT_SECONDS = 180L

        /** Safety cap. A bulletin announcement is never anywhere near this. */
        const val MAX_BYTES = 25L * 1024 * 1024

        const val USER_AGENT = "BOC-Cantabria/2.0 (+https://github.com/jrcosio/BOCantabria_v2)"

        private const val HTTPS_PREFIX = "https://"
        private const val PDF_TYPE = "application/pdf"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val BUFFER_BYTES = 16 * 1024

        /** The five bytes that open every PDF. */
        private val MAGIC: ByteString = "%PDF-".encodeUtf8()
    }
}
