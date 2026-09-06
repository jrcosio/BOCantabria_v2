package com.jrblanco.boccantabria.data.source.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The one place where a coroutine's cancellation reaches an OkHttp call (feature 014, PERF-002).
 *
 * No sockets: an interceptor stands in for the network and can be held on a latch, which is what the
 * audit's diagnostic did to show `Call.isCanceled=false` after cancelling. Real dispatchers and
 * `runBlocking` on purpose — the property is a race between a blocked call and a cancellation.
 */
class CancellableCallTest {

    private val uncaught = AtomicReference<Throwable?>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun recordUncaughtExceptions() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.set(error) }
    }

    @After
    fun restoreUncaughtHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test
    fun `cancelling the coroutine cancels the call and returns before the response`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val active = AtomicReference<Call>()
        val client = clientThat { chain ->
            active.set(chain.call())
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            pdf(chain)
        }

        var outcome: Any? = null
        val job = launch(Dispatchers.IO) {
            outcome = runCatching { client.newCall(request()).await { it.body.string() } }.getOrElse { it }
        }
        assertTrue("la llamada no llegó a entrar", entered.await(3, TimeUnit.SECONDS))

        val started = System.nanoTime()
        job.cancel()
        job.join()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        // Before feature 014: `Call.isCanceled=false`, and the job only finished once the interceptor
        // let the response through (`docs/auditoria/diagnostico-red.log`).
        assertTrue("tardó $elapsedMillis ms en volver", elapsedMillis < 2_000)
        assertTrue("la llamada no se canceló", active.get().isCanceled())
        assertTrue("debía ser una cancelación, fue: $outcome", outcome is CancellationException)
        release.countDown()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun `a failure before the response reaches the caller as its IOException`() = runBlocking {
        val client = clientThat { throw IOException("boom") }

        val outcome = runCatching { client.newCall(request()).await { it.body.string() } }

        assertEquals("boom", outcome.exceptionOrNull()?.message)
        assertTrue(outcome.exceptionOrNull() is IOException)
    }

    /**
     * Load-bearing, not defensive: `RealCall.AsyncCall.run` treats a non-`IOException` escaping the
     * callback as fatal — it cancels and **rethrows on the executor thread**, which on Android is an
     * uncaught exception and a dead process. Every failure of `consume` has to be routed to the
     * continuation instead (research.md D-618).
     */
    @Test
    fun `an exception thrown while consuming reaches the caller and never OkHttp's thread`() = runBlocking {
        val client = clientThat { chain -> pdf(chain) }

        val outcome = runCatching {
            client.newCall(request()).await<String> { throw IllegalStateException("parse failed") }
        }

        assertEquals("parse failed", outcome.exceptionOrNull()?.message)
        client.dispatcher.executorService.shutdown()
        client.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        assertNull("una excepción llegó al hilo de OkHttp: ${uncaught.get()}", uncaught.get())
    }

    @Test
    fun `the response is closed after consuming`() = runBlocking {
        val closed = AtomicBoolean(false)
        val client = clientThat { chain -> pdf(chain, body = closingBody(closed)) }

        val text = client.newCall(request()).await { it.body.string() }

        assertEquals("%PDF-diagnostico", text)
        assertTrue("la respuesta no se cerró", closed.get())
    }

    @Test
    fun `a response arriving after cancellation is discarded quietly`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val consumed = AtomicBoolean(false)
        val client = clientThat { chain ->
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            pdf(chain)
        }

        val job = launch(Dispatchers.IO) {
            runCatching { client.newCall(request()).await { consumed.set(true) } }
        }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        job.cancel()
        job.join()

        // Now the network answers a coroutine that is gone. Nothing may blow up anywhere.
        release.countDown()
        client.dispatcher.executorService.shutdown()
        client.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        assertNull("algo escapó tras la cancelación: ${uncaught.get()}", uncaught.get())
    }

    // ---------- Helpers ----------

    private fun clientThat(interceptor: Interceptor) = OkHttpClient.Builder().addInterceptor(interceptor).build()

    private fun request() = Request.Builder().url("https://boc.cantabria.es/diagnostico.pdf").build()

    private fun pdf(chain: Interceptor.Chain, body: ResponseBody = "%PDF-diagnostico".toResponseBody(PDF)): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

    /** A body that says when it was closed, which a plain `Buffer` cannot: closing one is a no-op. */
    private fun closingBody(closed: AtomicBoolean): ResponseBody {
        val bytes = Buffer().writeUtf8("%PDF-diagnostico")
        val source = object : ForwardingSource(bytes) {
            override fun close() {
                closed.set(true)
                super.close()
            }
        }.buffer()
        return object : ResponseBody() {
            override fun contentType() = PDF
            override fun contentLength(): Long = bytes.size
            override fun source(): BufferedSource = source
        }
    }

    private companion object {
        val PDF = "application/pdf".toMediaType()
    }
}
