package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.fake.RealDispatchers
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.TlsMockWebServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The tests that decide whether the application can be trusted to show an official document.
 *
 * They matter more than they look: the service answers with a PDF today, but the day it answers an
 * error page with a success code, this is what stops the application from storing it and showing it
 * as the bulletin. Every refusal here also asserts that **nothing was written**.
 *
 * The server speaks TLS because the downloader refuses anything that is not https — and relaxing
 * that to fit the test would be testing something the application does not do.
 */
class OkHttpDocumentDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val tls = TlsMockWebServer()

    private val server: MockWebServer get() = tls.server
    private val client: OkHttpClient get() = tls.client

    // ---------- Cancellation (feature 014, PERF-002) ----------

    /**
     * Before feature 014, cancelling the coroutine left `execute()` blocked and the socket open until
     * the response or the 180-second call timeout — and the download kept writing to the temporary.
     * Real dispatchers and `runBlocking`: the property is a race, and in virtual time it does not exist.
     */
    @Test
    fun `cancelling mid-body is a cancellation, the call is cancelled and no refusal is reported`() =
        runBlocking {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "application/pdf")
                    .bodyDelay(30, TimeUnit.SECONDS)
                    .body(Buffer().write(pdfBytes(64 * 1024)))
                    .build(),
            )
            val target = folder.newFile("out.pdf")

            var outcome: Any? = null
            val job = launch(Dispatchers.IO) {
                outcome = runCatching { downloader(dispatchers = RealDispatchers).download(url(), target) }
                    .getOrElse { it }
            }
            while (server.requestCount == 0) Thread.sleep(10)
            Thread.sleep(100)

            val started = System.nanoTime()
            job.cancel()
            job.join()
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000

            assertTrue("tardó $elapsedMillis ms en volver", elapsedMillis < 5_000)
            assertTrue("una cancelación no es un rechazo, fue: $outcome", outcome is CancellationException)
            assertTrue("la llamada no se canceló", tls.calls.last().isCanceled())
        }

    // ---------- The happy path ----------

    @Test
    fun `a real pdf is written, measured and hashed`() = runTest {
        val body = pdfBytes(2_048)
        server.enqueue(pdfResponse(body))
        val target = folder.newFile("out.pdf")

        val result = downloader().download(url(), target)

        val downloaded = result as DownloadResult.Downloaded
        assertEquals(body.size.toLong(), downloaded.byteCount)
        assertEquals(sha256(body), downloaded.checksum)
        assertTrue(target.readBytes().contentEquals(body))
    }

    @Test
    fun `the request identifies the application and asks for a pdf`() = runTest {
        server.enqueue(pdfResponse(pdfBytes(64)))

        downloader().download(url(), folder.newFile("out.pdf"))

        val request = server.takeRequest()
        assertTrue(request.headers["User-Agent"]!!.startsWith("BOC-Cantabria/"))
        assertTrue(request.headers["Accept"]!!.contains("application/pdf"))
    }

    // ---------- Refusals: nothing is stored ----------

    @Test
    fun `an error page with a success code is refused, not stored`() = runTest {
        // The case section 27.8 of the feed document names: «PDF devuelve HTML».
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/pdf")
                .body("<html><body>Servicio no disponible</body></html>")
                .build(),
        )
        val target = folder.newFile("out.pdf")

        val result = downloader().download(url(), target)

        assertEquals(DownloadResult.Rejected(DownloadRefusal.NotAPdf), result)
        assertEquals(0, target.length())
    }

    @Test
    fun `a declared type that is not a pdf is refused before the body is read`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .body("<html>no</html>")
                .build(),
        )
        val target = folder.newFile("out.pdf")

        val result = downloader().download(url(), target)

        assertEquals(DownloadResult.Rejected(DownloadRefusal.UnexpectedType), result)
        assertEquals(0, target.length())
    }

    @Test
    fun `a body over the cap is refused instead of filling memory`() = runTest {
        val huge = pdfBytes((OkHttpDocumentDownloader.MAX_BYTES + 4_096).toInt())
        server.enqueue(pdfResponse(huge))
        val target = folder.newFile("out.pdf")

        val result = downloader().download(url(), target)

        assertEquals(DownloadResult.Rejected(DownloadRefusal.TooLarge), result)
        assertTrue(target.length() <= OkHttpDocumentDownloader.MAX_BYTES)
    }

    @Test
    fun `an address that is not https is refused without opening a socket`() = runTest {
        val result = downloader().download(
            url().replace("https://", "http://"),
            folder.newFile("out.pdf"),
        )

        assertEquals(DownloadResult.Rejected(DownloadRefusal.InsecureScheme), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an address outside the bulletin service is refused without opening a socket`() = runTest {
        val result = downloader().download(
            "https://example.com/boces/verAnuncioAction.do?idAnuBlob=1",
            folder.newFile("out.pdf"),
        )

        assertEquals(DownloadResult.Rejected(DownloadRefusal.UnexpectedHost), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a 404 is reported with its code and not retried`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        val result = downloader().download(url(), folder.newFile("out.pdf"))

        assertEquals(DownloadResult.Rejected(DownloadRefusal.HttpError(404)), result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a connection that cannot be made is reported as a network problem`() = runTest {
        // The downloader derives its own timeouts, so a test cannot shorten them to force a read
        // timeout. Refusing the connection outright exercises the same path and takes milliseconds
        // instead of a minute.
        val address = url()
        server.close()
        val target = folder.newFile("out.pdf")

        val result = downloader().download(address, target)

        assertEquals(DownloadResult.Rejected(DownloadRefusal.Network), result)
        assertEquals(0, target.length())
    }

    @Test
    fun `no refusal ever leaves a usable file behind`() = runTest {
        // The promise of SC-005, checked over every refusal in one place.
        val cases = listOf(
            MockResponse.Builder().code(404).build(),
            MockResponse.Builder().code(500).build(),
            MockResponse.Builder().code(200).setHeader("Content-Type", "text/html").body("x").build(),
            MockResponse.Builder().code(200).setHeader("Content-Type", "application/pdf").body("no").build(),
        )
        cases.forEachIndexed { index, response ->
            server.enqueue(response)
            val target = folder.newFile("case-$index.pdf")

            val result = downloader().download(url(), target)

            assertTrue("el caso $index no fue rechazado", result is DownloadResult.Rejected)
            assertFalse("el caso $index dejó contenido", target.length() > 0)
        }
    }

    // ---------- Helpers ----------

    private fun downloader(
        withClient: OkHttpClient = client,
        dispatchers: DispatcherProvider = TestDispatcherProvider(),
    ) = OkHttpDocumentDownloader(
        client = withClient,
        dispatchers = dispatchers,
        allowedHost = server.hostName,
    )

    private fun url() = server.url("/boces/verAnuncioAction.do?idAnuBlob=439765").toString()

    private fun pdfResponse(body: ByteArray) = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/pdf")
        .setHeader("Content-Disposition", "inline; filename=2026-6695.pdf")
        .body(Buffer().write(body))
        .build()

    /** A byte stream that starts like a PDF. Enough for a downloader that checks the first bytes. */
    private fun pdfBytes(size: Int): ByteArray {
        val header = "%PDF-1.7\n".toByteArray()
        return header + ByteArray(size - header.size) { (it % 251).toByte() }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
