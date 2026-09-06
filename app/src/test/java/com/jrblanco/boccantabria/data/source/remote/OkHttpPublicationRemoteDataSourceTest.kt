package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.RandomProvider
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The transport policy the feed specification asks for, checked against a real HTTP server.
 *
 * The waits between retries run on virtual time, so verifying a policy of two, five and fifteen
 * seconds costs microseconds instead of twenty-two seconds of a real suite.
 *
 * The catalogue only accepts https addresses, and that invariant is worth keeping, so the test server
 * speaks TLS with a certificate the test client trusts. Relaxing the invariant to fit the test would
 * be testing something the application does not do.
 */
class OkHttpPublicationRemoteDataSourceTest {

    @get:Rule
    val tls = TlsMockWebServer()

    private val server: MockWebServer get() = tls.server
    private val trustingClient: OkHttpClient get() = tls.client

    /** Fixed jitter: the policy has to be verifiable, and randomness is the enemy of that. */
    private val fixedRandom = object : RandomProvider {
        override fun nextLong(bound: Long): Long = 0
    }

    // ---------- Cancellation (feature 014, PERF-002) ----------

    /**
     * Before feature 014 a cancelled fetch came out of `execute()` as an `IOException` once the socket
     * gave up, was classified `NETWORK` — which is retryable — and the retry loop went round again.
     * Cancelling could cost up to three attempts. Real dispatchers: the property is a race.
     */
    @Test
    fun `cancelling mid-request is a cancellation, never a network failure nor a retry`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/xml;charset=UTF-8")
                .headersDelay(30, TimeUnit.SECONDS)
                .body(FEED_BODY)
                .build(),
        )

        var outcome: Any? = null
        val job = launch(Dispatchers.IO) {
            outcome = runCatching { dataSource(dispatchers = RealDispatchers).fetchFeed(definition(), knownBodyHash = null) }
                .getOrElse { it }
        }
        while (server.requestCount == 0) Thread.sleep(10)
        Thread.sleep(100)

        val started = System.nanoTime()
        job.cancel()
        job.join()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue("tardó $elapsedMillis ms en volver", elapsedMillis < 5_000)
        assertTrue("una cancelación no es un fallo de red, fue: $outcome", outcome is CancellationException)
        assertEquals("una cancelación no se reintenta", 1, server.requestCount)
        assertTrue("la llamada no se canceló", tls.calls.last().isCanceled())
    }

    @Test
    fun `a correct response is parsed and its body hashed`() = runTest {
        server.enqueue(xmlResponse(FEED_BODY))

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        val fetched = result as FeedFetchResult.Fetched
        assertEquals(1, fetched.channel.items.size)
        assertTrue(fetched.bodyHash.isNotBlank())
    }

    @Test
    fun `the request identifies the application and asks for XML`() = runTest {
        server.enqueue(xmlResponse(FEED_BODY))

        dataSource().fetchFeed(definition(), knownBodyHash = null)

        val request = server.takeRequest()
        assertEquals(OkHttpPublicationRemoteDataSource.USER_AGENT, request.headers["User-Agent"])
        assertEquals(OkHttpPublicationRemoteDataSource.ACCEPT_XML, request.headers["Accept"])
    }

    @Test
    fun `the same body twice is reported as unchanged and never parsed again`() = runTest {
        server.enqueue(xmlResponse(FEED_BODY))
        val first = dataSource().fetchFeed(definition(), knownBodyHash = null)
        val hash = (first as FeedFetchResult.Fetched).bodyHash

        server.enqueue(xmlResponse(FEED_BODY))
        val second = dataSource().fetchFeed(definition(), knownBodyHash = hash)

        assertEquals(FeedFetchResult.NotModified, second)
    }

    @Test
    fun `a changed body is not reported as unchanged`() = runTest {
        server.enqueue(xmlResponse(FEED_BODY.replace("439765", "439766")))

        val result = dataSource().fetchFeed(definition(), knownBodyHash = "una-huella-antigua")

        assertTrue(result is FeedFetchResult.Fetched)
    }

    @Test
    fun `a 404 is not retried, because retrying would only repeat it`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        assertEquals(FeedFetchResult.Failed(FeedFailure.CLIENT_ERROR), result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 500 is retried up to three times and then reported`() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(500).build()) }

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        assertEquals(FeedFetchResult.Failed(FeedFailure.SERVER_ERROR), result)
        assertEquals(OkHttpPublicationRemoteDataSource.MAX_ATTEMPTS, server.requestCount)
    }

    @Test
    fun `a source that recovers on the second attempt is read normally`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(xmlResponse(FEED_BODY))

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        assertTrue(result is FeedFetchResult.Fetched)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `being asked for patience is retryable, unlike the rest of the 4xx family`() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(429).build()) }

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        assertEquals(FeedFetchResult.Failed(FeedFailure.THROTTLED), result)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `an unexpected content type is refused without parsing it`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .body("<html><body>Servicio no disponible</body></html>")
                .build(),
        )

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        assertEquals(FeedFetchResult.Failed(FeedFailure.UNUSABLE_BODY), result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a body over the safety cap is refused instead of being loaded`() = runTest {
        val huge = "<?xml version=\"1.0\"?><rss><channel><title>" +
            "x".repeat((OkHttpPublicationRemoteDataSource.MAX_BODY_BYTES + 1_024).toInt()) +
            "</title></channel></rss>"
        server.enqueue(xmlResponse(huge))

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        assertEquals(FeedFetchResult.Failed(FeedFailure.UNUSABLE_BODY), result)
    }

    @Test
    fun `a well transported but unreadable body is malformed, not a network problem`() = runTest {
        server.enqueue(xmlResponse("esto no es un boletín"))

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        assertEquals(FeedFetchResult.Failed(FeedFailure.MALFORMED), result)
        // Malformed content is not retryable: the same bytes would arrive again.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a body carrying a document type declaration is refused as malformed`() = runTest {
        server.enqueue(
            xmlResponse(
                "<?xml version=\"1.0\"?><!DOCTYPE rss [ <!ENTITY x SYSTEM \"file:///etc/passwd\"> ]>" +
                    "<rss><channel><title>Filtro BOC</title></channel></rss>",
            ),
        )

        val result = dataSource().fetchFeed(definition(), knownBodyHash = null)

        assertEquals(FeedFetchResult.Failed(FeedFailure.MALFORMED), result)
    }

    @Test
    fun `a read that never finishes is a timeout and is retried`() = runTest {
        val slowClient = trustingClient.newBuilder()
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .build()
        repeat(3) {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "text/xml")
                    .body(FEED_BODY)
                    .throttleBody(1, 1, TimeUnit.SECONDS)
                    .build(),
            )
        }

        val result = dataSource(client = slowClient).fetchFeed(definition(), knownBodyHash = null)

        assertEquals(FeedFetchResult.Failed(FeedFailure.TIMEOUT), result)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `an address that is not https cannot even be described`() {
        // The enforcement point is the catalogue, not the transport: a definition holding a
        // plain-http address cannot be constructed, so no request can ever be made to one. The
        // data source repeats the check as defence in depth, which is why it has no test of its
        // own — there is no way to reach it.
        val error = runCatching {
            BocFeedDefinition(
                feedId = "6802081",
                url = "http://www.cantabria.es/o/BOC/feed/6802081",
                sectionCode = "1",
                subsectionCode = null,
                order = 1,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    private fun dataSource(
        client: OkHttpClient = trustingClient,
        dispatchers: DispatcherProvider = TestDispatcherProvider(),
    ) = OkHttpPublicationRemoteDataSource(
        client = client,
        parser = BocRssParser(),
        dispatchers = dispatchers,
        random = fixedRandom,
    )

    private fun definition() = BocFeedDefinition(
        feedId = "6802081",
        url = server.url("/feed/6802081").toString(),
        sectionCode = "1",
        subsectionCode = null,
        order = 1,
    )

    private fun xmlResponse(body: String) = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "text/xml;charset=UTF-8")
        .body(body)
        .build()

    private companion object {
        val FEED_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0"><channel>
              <title>Filtro BOC</title>
              <link>https://www.cantabria.es/o/BOC/feed/6802081</link>
              <size>1</size>
              <item>
                <title>AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.</title>
                <link>https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765</link>
                <pubDate>2026-08-26</pubDate>
                <categorias>1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD</categorias>
              </item>
            </channel></rss>
        """.trimIndent()
    }
}
