package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * The one way out to the summarising service, now over the official library.
 *
 * The server really speaks TLS, like every other network test in this project: the application only
 * ever talks https, and a test server speaking plain HTTP would be testing something it does not do.
 *
 * Feature 010 rewrote this class rather than replacing it. The official Kotlin library was adopted
 * for a day and then withdrawn — its Android artifact **throws** when the client is given an API key
 * — so the boundary is still HTTP and the assertions came across with new bodies, which is exactly
 * the argument feature 009 made for writing the request by hand (research.md D-227).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpGeminiSummaryDataSourceTest {

    private val crashReporter = RecordingCrashReporter()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        val localhost = InetAddress.getByName("localhost").canonicalHostName
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(localhost).build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start()
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .retryOnConnectionFailure(false)
            .build()
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    // ---------- What comes back ----------

    @Test
    fun `a well formed answer becomes a payload with its usage`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        val success = result as GeminiSummaryResult.Success
        assertEquals("Se aprueba la ordenanza.", success.payload.plainLanguageSummary)
        assertEquals(5600, success.usage.totalInputTokens)
        assertEquals(1200, success.usage.totalOutputTokens)
        assertEquals(6800, success.usage.totalTokens)
    }

    /** `modelVersion` says which exact version answered. Same nullable column as before. */
    @Test
    fun `the fingerprint carries the model version that answered`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals("gemini-test-001", (result as GeminiSummaryResult.Success).systemFingerprint)
    }

    /**
     * The library skips the parts marked as `thought` on its own. In feature 009 this had to be done
     * by hand, finding the output step **by type and never by position**, against documentation that
     * named the field wrongly.
     */
    @Test
    fun `a reasoning part in front of the answer is skipped`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson(), thoughtsFirst = true)))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(
            "Se aprueba la ordenanza.",
            (result as GeminiSummaryResult.Success).payload.plainLanguageSummary,
        )
    }

    // ---------- What goes out ----------

    /**
     * **The point of the whole feature.** The request carries a reference to a document already
     * uploaded, and **not** the document's text (FR-001).
     */
    @Test
    fun `the request carries a file reference and not the text of the document`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        dataSource().summarise("sistema", "metadatos de la publicacion", DOCUMENT)

        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body, body.contains(DOCUMENT.fileUri))
        assertTrue(body, body.contains("application/pdf"))
        assertTrue("los metadatos sí viajan: $body", body.contains("metadatos de la publicacion"))
        assertFalse("no puede viajar texto marcado por páginas", body.contains("[PÁGINA"))
    }

    @Test
    fun `the request asks for the agreed model, schema and settings`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        val request = server.takeRequest()
        val body = request.body?.utf8().orEmpty()
        assertTrue(request.url.encodedPath, request.url.encodedPath.contains("generateContent"))
        assertTrue("el esquema tiene que viajar: $body", body.contains("plainLanguageSummary"))
        assertTrue(body, body.contains("application/json"))
    }

    /** The provider's default is `medium`, and reasoning is billed. It has to be said out loud. */
    @Test
    fun `the request asks for the least thinking`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body, body.contains("MINIMAL"))
    }

    /**
     * Not 65 536 on purpose: if an answer ever reached this, something is wrong with the prompt and
     * it should show. And the ceiling closed the family of failures where the JSON arrived cut.
     */
    @Test
    fun `the answer ceiling is generous enough for a real summary`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body, body.contains("8000"))
    }

    /** The credential travels in a header, never in the body or the query string. */
    @Test
    fun `the credential never travels in the body or the url`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        val request = server.takeRequest()
        assertFalse(request.url.toString(), request.url.toString().contains(API_KEY))
        assertFalse("nunca en el cuerpo", request.body?.utf8().orEmpty().contains(API_KEY))
    }

    // ---------- The seven refusals ----------

    @Test
    fun `an unauthorised answer is a configuration problem and is not retried`() = runTest {
        server.enqueue(jsonResponse(401, errorBody("API key not valid", "UNAUTHENTICATED")))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiSummaryResult.Rejected).reason)
        assertEquals("no se reintenta", 1, server.requestCount)
    }

    @Test
    fun `a forbidden answer is treated the same way`() = runTest {
        server.enqueue(jsonResponse(403, errorBody("permission denied", "PERMISSION_DENIED")))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiSummaryResult.Rejected).reason)
    }

    /**
     * A 429 is classified by **the delay it asks for**, never by the text it carries: the text changes,
     * it is in English, and FR-028 forbids showing it. Read from the header first and from the
     * `RetryInfo` detail second (009 research.md D-109).
     */
    @Test
    fun `a rate limited answer reads the delay out of the error details`() = runTest {
        server.enqueue(jsonResponse(429, quotaBody("37s")))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(
            GeminiRefusal.QuotaMinute(37),
            (result as GeminiSummaryResult.Rejected).reason,
        )
        assertEquals("un 429 no se reintenta por su cuenta", 1, server.requestCount)
    }

    /** Past fifteen minutes, what ran out is of daily scale rather than of the minute. */
    @Test
    fun `a rate limited answer with a delay of daily scale is exhaustion of the day`() = runTest {
        server.enqueue(jsonResponse(429, quotaBody("3600s")))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(GeminiRefusal.QuotaDay, (result as GeminiSummaryResult.Rejected).reason)
    }

    /** Without a delay the answer is still right, only less precise: the coordinator's own default. */
    @Test
    fun `a rate limited answer with no delay falls back on the coordinator`() = runTest {
        server.enqueue(jsonResponse(429, errorBody("too many requests", "RESOURCE_EXHAUSTED")))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        val reason = (result as GeminiSummaryResult.Rejected).reason
        assertTrue(reason.toString(), reason is GeminiRefusal.QuotaMinute)
    }

    @Test
    fun `a server error is retried and can recover`() = runTest {
        server.enqueue(jsonResponse(503, errorBody("high demand", "UNAVAILABLE")))
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertTrue(result is GeminiSummaryResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a server error that never recovers gives up after three attempts`() = runTest {
        repeat(3) { server.enqueue(jsonResponse(500, errorBody("boom", "INTERNAL"))) }

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(
            GeminiRefusal.HttpError(500),
            (result as GeminiSummaryResult.Rejected).reason,
        )
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a malformed body is refused`() = runTest {
        server.enqueue(jsonResponse(200, generation("{no es json")))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(GeminiRefusal.Malformed, (result as GeminiSummaryResult.Rejected).reason)
    }

    @Test
    fun `an answer with no text at all is refused`() = runTest {
        server.enqueue(jsonResponse(200, EMPTY_GENERATION))

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(GeminiRefusal.Malformed, (result as GeminiSummaryResult.Rejected).reason)
    }

    /**
     * `MAX_TOKENS` used to have to be deduced from JSON that would not parse, and the reader was told
     * «no se ha podido construir un resumen fiable» — our problem wearing the service's clothes. Now
     * it is a typed value and it says so.
     */
    @Test
    fun `a truncated answer says which finish reason it had in the log`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson(prose = ""), finish = "MAX_TOKENS")))
        server.enqueue(jsonResponse(200, generation(summaryJson(prose = ""), finish = "MAX_TOKENS")))

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        val said = crashReporter.messages.joinToString(" ")
        assertTrue(said, said.contains("finishReason=MAX_TOKENS"))
    }

    @Test
    fun `without a credential no request is made at all`() = runTest {
        val result = dataSource(apiKey = null).summarise("sistema", "usuario", DOCUMENT)

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiSummaryResult.Rejected).reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `when the minute allowance is spent nothing is sent`() = runTest {
        val coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter)
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_MINUTE) { coordinator.recordRequest() }

        val result = dataSource(coordinator = coordinator)
            .summarise("sistema", "usuario", DOCUMENT)

        assertTrue(
            result.toString(),
            (result as GeminiSummaryResult.Rejected).reason is GeminiRefusal.QuotaMinute,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an answer with an empty summary is told apart from a malformed one`() = runTest {
        repeat(2) { server.enqueue(jsonResponse(200, generation(summaryJson(prose = "")))) }

        val result = dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(GeminiRefusal.BlankSummary, (result as GeminiSummaryResult.Rejected).reason)
    }

    @Test
    fun `an empty summary is retried once, and once only`() = runTest {
        repeat(3) { server.enqueue(jsonResponse(200, generation(summaryJson(prose = "")))) }

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertEquals(2, server.requestCount)
    }

    /**
     * **Regression, seen on a phone.** After a blank summary the retry went straight out, ran into
     * the minute's allowance, and the reader ended up being told «the limit has been reached» when
     * what had happened was something else. A retry that cannot run must not change the error.
     *
     * Ported from `OkHttpGeminiSummaryDataSourceTest` when that class went: the defect belongs to the
     * retry policy, not to the HTTP client, so changing transport does not fix it
     * (009 research.md D-108, 010 FR-027).
     */
    @Test
    fun `a retry with no room left keeps the original reason instead of a quota one`() = runTest {
        val coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter)
        // One short of the minute's allowance: the first attempt is allowed and spends the last slot,
        // so the re-check before retrying finds no room. Which is exactly what happens for real.
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_MINUTE - 1) { coordinator.recordRequest() }
        server.enqueue(jsonResponse(200, generation(summaryJson(prose = ""))))
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        val result = dataSource(coordinator = coordinator)
            .summarise("sistema", "usuario", DOCUMENT)

        assertEquals(
            "debe seguir siendo el resumen en blanco, no la cuota",
            GeminiRefusal.BlankSummary,
            (result as GeminiSummaryResult.Rejected).reason,
        )
        assertEquals("y no se gasta un segundo intento", 1, server.requestCount)
    }

    /**
     * **Regression, seen on a phone on 4 September 2026.** Somebody pressed Back while a summary was
     * being generated and the log said `SocketException: Software caused connection abort`, and the
     * screen, on the way back, «No hay conexión» — for a failure that never happened.
     *
     * Cancelling a coroutine does not interrupt a blocking call with a `CancellationException`: it
     * breaks the socket, and what comes out is an `IOException`. **Changing the HTTP client does not
     * fix this**, which is why the test came across with the class that replaced the one it was
     * written for: it is a property of any blocking call inside a coroutine (010 research.md D-218).
     *
     * Real dispatchers and `runBlocking` on purpose: what is being checked is a race between a
     * blocked thread and a cancellation, and in virtual time it does not exist.
     */
    @Test
    fun `leaving while a request is in flight is a cancellation and not an offline error`() =
        runBlocking {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "application/json")
                    .body(generation(summaryJson()))
                    .bodyDelay(10, TimeUnit.SECONDS)
                    .build(),
            )
            val source = realDataSource()

            val job = launch(Dispatchers.Default) {
                source.summarise("sistema", "usuario", DOCUMENT)
            }
            while (server.requestCount == 0) delay(20)

            job.cancel() // la persona pulsa Atrás
            server.close() // y el socket se rompe, como en el dispositivo
            job.join()

            assertFalse(
                "irse de la pantalla no es un problema de red: ${crashReporter.messages}",
                crashReporter.messages.any { it.contains("network:") },
            )
        }

    // ---------- The log has to talk, and only about the right things ----------

    @Test
    fun `an http failure says which one it was in the log`() = runTest {
        repeat(3) { server.enqueue(jsonResponse(503, errorBody("high demand", "UNAVAILABLE"))) }

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertTrue(
            crashReporter.messages.toString(),
            crashReporter.messages.any { it.contains("HTTP 503") },
        )
    }

    @Test
    fun `an http failure carries the reason the service gave`() = runTest {
        server.enqueue(jsonResponse(400, errorBody("invalid file reference", "INVALID_ARGUMENT")))

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertTrue(
            crashReporter.messages.toString(),
            crashReporter.messages.any { it.contains("invalid file reference") },
        )
    }

    /**
     * Which keys came back, never what they held: the field names are our own schema, the values are
     * the document (FR-036).
     */
    @Test
    fun `the log describes the shape of an empty answer without quoting it`() = runTest {
        repeat(2) { server.enqueue(jsonResponse(200, generation(summaryJson(prose = "")))) }

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        val said = crashReporter.messages.joinToString(" ")
        assertTrue(said, said.contains("blank summary"))
        assertTrue(said, said.contains("plainLanguageSummary=0"))
        assertTrue(said, said.contains("keyPoints=1"))
        assertFalse("no puede citar el contenido", said.contains("Punto 1"))
    }

    /**
     * **FR-040.** Dos fallos que en pantalla son la misma frase tienen que dejar líneas **distintas**
     * en el registro, o no hay forma de saber cuál ocurrió.
     *
     * Estos dos son el caso más difícil: un cuerpo que no parsea y una respuesta sin texto ninguno
     * llegan ambos como `Malformed` y el lector ve «no se ha podido construir un resumen fiable» en
     * los dos. Son problemas opuestos.
     */
    @Test
    fun `two failures that share a message on screen do not share a line in the log`() = runTest {
        server.enqueue(jsonResponse(200, generation("{no es json")))
        dataSource().summarise("sistema", "usuario", DOCUMENT)
        val unparseable = crashReporter.messages.joinToString(" ")

        crashReporter.messages.clear()
        server.enqueue(jsonResponse(200, EMPTY_GENERATION))
        dataSource().summarise("sistema", "usuario", DOCUMENT)
        val noText = crashReporter.messages.joinToString(" ")

        assertTrue(unparseable, unparseable.contains("unparseable answer"))
        assertTrue(noText, noText.contains("no text"))
        assertFalse("y no se confunden entre sí", noText.contains("unparseable answer"))
    }

    @Test
    fun `every log line is tagged with the service`() = runTest {
        server.enqueue(jsonResponse(500, errorBody("boom", "INTERNAL")))
        server.enqueue(jsonResponse(500, errorBody("boom", "INTERNAL")))
        server.enqueue(jsonResponse(500, errorBody("boom", "INTERNAL")))

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertTrue(
            crashReporter.messages.toString(),
            crashReporter.messages.all { it.startsWith("gemini: ") },
        )
    }

    /** FR-035 and SC-009. Five tests watch this across the feature; this is one of them. */
    @Test
    fun `nothing the reporter is told ever contains the credential`() = runTest {
        server.enqueue(jsonResponse(401, errorBody("API key not valid", "UNAUTHENTICATED")))

        dataSource().summarise("sistema", "usuario", DOCUMENT)

        assertFalse(
            crashReporter.messages.toString(),
            crashReporter.messages.any { it.contains(API_KEY) },
        )
    }

    /** FR-036. The document's contents must not reach the log either, only its shape. */
    @Test
    fun `nothing the reporter is told ever contains the document`() = runTest {
        server.enqueue(jsonResponse(200, generation(summaryJson())))

        dataSource().summarise("sistema", "el texto secreto del boletin", DOCUMENT)

        assertFalse(
            crashReporter.messages.toString(),
            crashReporter.messages.any { it.contains("el texto secreto del boletin") },
        )
    }

    // ---------- Helpers ----------

    /**
     * Virtual time, so the backoff between retries costs nothing. Named apart from [realDataSource]
     * on purpose: two overloads with the same name and default arguments send the type checker round
     * in circles.
     */
    private fun TestScope.dataSource(
        apiKey: String? = API_KEY,
        coordinator: GeminiRateLimitCoordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter),
    ): OkHttpGeminiSummaryDataSource = build(
        apiKey,
        coordinator,
        TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
    )

    /** Real threads. Only the cancellation test needs them, and it needs them genuinely. */
    private fun realDataSource(): OkHttpGeminiSummaryDataSource =
        build(API_KEY, GeminiRateLimitCoordinator(FixedClock, NoJitter), RealDispatchers)

    private fun build(
        apiKey: String?,
        coordinator: GeminiRateLimitCoordinator,
        dispatchers: DispatcherProvider,
    ) = OkHttpGeminiSummaryDataSource(
        client = client,
        apiKeys = { apiKey },
        coordinator = coordinator,
        dispatchers = dispatchers,
        crashReporter = crashReporter,
        baseUrl = server.url("").toString().removeSuffix("/"),
    )

    private fun jsonResponse(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun summaryJson(prose: String = "Se aprueba la ordenanza."): String = """
        {"documentTitle":"Ordenanza","documentType":"Anuncio","issuingBody":"Ayuntamiento",
         "keyPoints":[{"text":"Punto 1","pages":[1]}],
         "affectedParties":[],"datesAndDeadlines":[],"amounts":[],"requiredActions":[],
         "appealsOrClaims":[],"warnings":[],
         "coverage":{"pagesAnalyzed":[1],"totalPages":1,"complete":true},
         "plainLanguageSummary":"$prose"}
    """.trimIndent().replace("\n", "")

    /** What the service answers with, in the library's own shape. */
    private fun generation(
        summary: String,
        finish: String = "STOP",
        thoughtsFirst: Boolean = false,
    ): String {
        val escaped = summary.replace("\\", "\\\\").replace("\"", "\\\"")
        val thoughts = if (thoughtsFirst) {
            """{"text":"pensando en voz alta","thought":true},"""
        } else {
            ""
        }
        return """
            {"candidates":[{"content":{"role":"model","parts":[$thoughts{"text":"$escaped"}]},
              "finishReason":"$finish","index":0}],
             "usageMetadata":{"promptTokenCount":5600,"candidatesTokenCount":1200,
                              "totalTokenCount":6800,"thoughtsTokenCount":0},
             "modelVersion":"gemini-test-001","responseId":"abc"}
        """.trimIndent().replace("\n", "")
    }

    private fun errorBody(message: String, status: String) = """
        {"error":{"code":0,"message":"$message","status":"$status"}}
    """.trimIndent().replace("\n", "")

    /** The shape the service really answers with: `retryDelay` is a field of the `RetryInfo` detail. */
    private fun quotaBody(retryDelay: String) = """
        {"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED",
          "details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[]},
                     {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"$retryDelay"}]}}
    """.trimIndent().replace("\n", "")

    /**
     * Real dispatchers.
     *
     * The library's own client runs on its Ktor engine's threads, so virtual time buys nothing here
     * and the cancellation test genuinely needs a blocked thread.
     */
    private object RealDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.IO
        override val default = Dispatchers.Default
    }

    private object FixedClock : TimeProvider {
        override fun nowMillis(): Long = 1_700_000_000_000L
    }

    private object NoJitter : RandomProvider {
        override fun nextLong(bound: Long): Long = 0
    }

    private companion object {
        /**
         * A fake credential that **deliberately does not look like a real one**.
         *
         * The check that hunts for leaked keys in the repository looks for `AIza…` and `AQ.…` with
         * thirty-odd characters after them, which is exactly what a real Gemini key looks like. A
         * fixture shaped like that makes the check cry wolf on every run, and a check that always
         * fails is a check that stops being read. The assertions here only need a string the log must
         * not contain.
         */
        const val API_KEY = "clave-de-prueba-que-no-es-una-clave"

        val DOCUMENT = UploadedDocument(
            remoteName = "files/abc123",
            fileUri = "https://generativelanguage.googleapis.com/v1beta/files/abc123",
            mimeType = "application/pdf",
        )

        val EMPTY_GENERATION = """
            {"candidates":[{"content":{"role":"model","parts":[]},"finishReason":"OTHER"}],
             "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":0,"totalTokenCount":10}}
        """.trimIndent().replace("\n", "")
    }
}
