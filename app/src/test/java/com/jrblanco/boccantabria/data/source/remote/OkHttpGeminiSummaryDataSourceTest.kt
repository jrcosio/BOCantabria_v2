package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.domain.model.AiSummaryConstants
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * The one way out to the summarising service, against a server that really speaks TLS.
 *
 * `okhttp-tls` is already in the project because the feed catalogue demands https, and the same
 * reasoning applies here: a test server speaking plain HTTP would be testing something the
 * application does not do.
 *
 * Feature 009 swapped the provider and this class was **rewritten rather than replaced**: the
 * boundary is still HTTP, so the twenty-one assertions came across with new bodies. That was the
 * argument for writing the request by hand instead of taking an SDK (009 research.md D-102).
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

    // ---------- The happy path ----------

    @Test
    fun `a well formed answer becomes a payload with its usage`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        val result = dataSource().summarise("sistema", "usuario")

        val success = result as GeminiSummaryResult.Success
        assertEquals(
            "Se aprueba definitivamente la modificacion de la ordenanza.",
            success.payload.plainLanguageSummary,
        )
        assertEquals(1, success.payload.keyPoints.size)
        assertEquals(6_800, success.usage.totalTokens)
        assertEquals(5_600, success.usage.totalInputTokens)
        assertEquals(1_200, success.usage.totalOutputTokens)
    }

    /** This service has no fingerprint of its serving configuration. The column is nullable. */
    @Test
    fun `the fingerprint is absent because this service has none`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        val success = dataSource().summarise("sistema", "usuario") as GeminiSummaryResult.Success

        assertEquals(null, success.systemFingerprint)
    }

    @Test
    fun `the request asks for the agreed model, schema and settings`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        dataSource().summarise("sistema", "usuario")

        val body = server.takeRequest().body!!.utf8()
        // Contra la constante, no contra un literal: lo que esta prueba fija es que la petición pide
        // **el modelo acordado**, no en qué modelo estamos. Cambiarlo es una operación legítima —una
        // línea en `AiSummaryConstants`— y no debería poner roja una prueba que va de otra cosa.
        assertTrue(body, body.contains(AiSummaryConstants.MODEL_ID))
        assertTrue(body, body.contains("\"mime_type\":\"application/json\""))
        assertTrue(body, body.contains("\"maxLength\":900"))
        assertTrue("el esquema debe viajar sin envoltorio", !body.contains("json_schema"))
    }

    /**
     * 009 FR-030 and D-107. `store` defaults to `true` in the service: it keeps the interaction, a
     * day on a free account. And `thinking_level` defaults to `medium`, which is billed.
     *
     * Both are equal to their Kotlin defaults, so **without `encodeDefaults = true` neither would be
     * serialised at all** and the service would apply its own — the opposite of both. This assertion
     * is the only thing standing between that and a silent regression.
     */
    @Test
    fun `the request asks for zero retention and the least thinking`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        dataSource().summarise("sistema", "usuario")

        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, body.contains("\"store\":false"))
        assertTrue(body, body.contains("\"thinking_level\":\"minimal\""))
    }

    /**
     * The provider's documentation for this generation says outright not to change the sampling
     * parameters, and this model does not accept custom values at all. Sending them would be noise at
     * best and a 400 at worst (009 research.md D-106).
     */
    @Test
    fun `the request sends no sampling parameters`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        dataSource().summarise("sistema", "usuario")

        val body = server.takeRequest().body!!.utf8()
        assertFalse(body, body.contains("temperature"))
        assertFalse(body, body.contains("top_p"))
        assertFalse(body, body.contains("top_k"))
    }

    /**
     * **Regression, 009 research.md D-110.** The previous provider charged the per-minute allowance
     * for `input + max_completion_tokens` when the request was *made*, spent or not, so the answer's
     * ceiling had to stay at 1 800 — and a real summary once came back at 1 625. Past a ceiling the
     * JSON arrives cut, does not parse, and the reader is told the service produced nothing reliable,
     * which is a problem of ours dressed up as one of theirs.
     *
     * **This service charges the output actually used**, so the ceiling is generous and that whole
     * family of failures is closed. The number is asserted because it is the fix.
     */
    @Test
    fun `the answer ceiling is generous enough for a real summary`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson(prose = "a".repeat(890), keyPoints = 10))))

        val body = interaction(summaryJson())
        server.enqueue(jsonResponse(200, body))

        val result = dataSource().summarise("sistema", "usuario")

        val sent = server.takeRequest().body!!.utf8()
        assertTrue(
            "el techo de salida debe ser holgado: $sent",
            sent.contains("\"max_output_tokens\":8000"),
        )
        val success = result as GeminiSummaryResult.Success
        assertEquals("un resumen al límite del esquema debe parsear", 890, success.payload.plainLanguageSummary.length)
        assertEquals(10, success.payload.keyPoints.size)
    }

    /**
     * 009 FR-014 and FR-003. The credential travels in the header so a captured body cannot carry it,
     * what goes out is JSON text — never the bytes of the document — and it is **one** request.
     *
     * This model reads PDF natively and that is deliberately out of scope: this assertion is what
     * keeps it from creeping in.
     */
    @Test
    fun `the credential travels in the header and the body carries text and nothing else`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        dataSource().summarise("sistema", "usuario contenido del documento")

        val request = server.takeRequest()
        assertEquals("una-clave", request.headers[OkHttpGeminiSummaryDataSource.HEADER_API_KEY])

        val body = request.body!!.utf8()
        assertFalse("la credencial no puede ir en el cuerpo", body.contains("una-clave"))
        assertFalse("no se envía el fichero, solo texto", body.contains("%PDF-"))
        assertFalse("no se envía el documento como tal", body.contains("\"type\":\"document\""))
        assertTrue(body.contains("usuario contenido del documento"))
        assertEquals("una sola consulta por publicación", 1, server.requestCount)
    }

    /**
     * Found by its type and never by position, and this is not hypothetical: against the real service
     * a step of type `thought` **always** comes first. Taking `steps[0]` would fail on every single
     * answer (009 quickstart §3 bis).
     */
    @Test
    fun `the answer is found by its step type and not by its position`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson(), thoughtsFirst = true)))

        val result = dataSource().summarise("sistema", "usuario")

        assertTrue("debe encontrar model_output aunque no sea el primero", result is GeminiSummaryResult.Success)
    }

    // ---------- Refusals ----------

    /** A 401 does not become a 200 by asking again: it is configuration, and it is not retried. */
    @Test
    fun `an unauthorised answer is a configuration problem and is not retried`() = runTest {
        server.enqueue(jsonResponse(401, """{"error":{"message":"invalid api key"}}"""))

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiSummaryResult.Rejected).reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a forbidden answer is treated the same way`() = runTest {
        server.enqueue(jsonResponse(403, """{"error":{"message":"forbidden"}}"""))

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiSummaryResult.Rejected).reason)
    }

    /** 009 FR-023: the wait the service asks for is respected, not second-guessed. */
    @Test
    fun `a rate limited answer reports the wait the header asked for`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("retry-after", "42")
                .body("""{"error":{"message":"rate limit"}}""")
                .build(),
        )

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(
            GeminiRefusal.QuotaMinute(secondsRemaining = 42),
            (result as GeminiSummaryResult.Rejected).reason,
        )
        assertEquals("no se reintenta un 429 por su cuenta", 1, server.requestCount)
    }

    /**
     * The documentation promises no `retry-after` header, so a `RetryInfo` in the error details is
     * read too. Without one of the two there is no way to tell a wait of seconds from one of hours
     * (009 research.md D-109).
     */
    @Test
    fun `a rate limited answer reads the delay from the error details when there is no header`() = runTest {
        server.enqueue(
            jsonResponse(
                429,
                """{"error":{"message":"rate limit","status":"RESOURCE_EXHAUSTED",
                   "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo",
                               "retryDelay":"37s"}]}}""".replace("\n", ""),
            ),
        )

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(
            GeminiRefusal.QuotaMinute(secondsRemaining = 37),
            (result as GeminiSummaryResult.Rejected).reason,
        )
    }

    /** 009 FR-024. A delay of daily scale is a different sentence to the reader, and no retry. */
    @Test
    fun `a rate limited answer with a delay of daily scale is exhaustion of the day`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("retry-after", "7200")
                .body("""{"error":{"message":"quota exceeded"}}""")
                .build(),
        )

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.QuotaDay, (result as GeminiSummaryResult.Rejected).reason)
    }

    @Test
    fun `a server error is retried and can recover`() = runTest {
        server.enqueue(jsonResponse(503, """{"error":{"message":"unavailable"}}"""))
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        val result = dataSource().summarise("sistema", "usuario")

        assertTrue(result is GeminiSummaryResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a server error that never recovers gives up after three attempts`() = runTest {
        repeat(3) { server.enqueue(jsonResponse(500, """{"error":{"message":"boom"}}""")) }

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.HttpError(500), (result as GeminiSummaryResult.Rejected).reason)
        assertEquals(3, server.requestCount)
    }

    /** 009 FR-019: an unusable answer is refused rather than shown. */
    @Test
    fun `a malformed body is refused`() = runTest {
        server.enqueue(jsonResponse(200, "esto no es json"))

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.Malformed, (result as GeminiSummaryResult.Rejected).reason)
    }

    @Test
    fun `an answer with no model output step is refused`() = runTest {
        server.enqueue(jsonResponse(200, interactionWithoutOutput("completed")))

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.Malformed, (result as GeminiSummaryResult.Rejected).reason)
    }

    /**
     * `incomplete` and `budget_exceeded` mean a ceiling cut the answer, not that the service
     * misbehaved. On screen they are the same sentence, on purpose; **in the log they must not be**,
     * and with the previous provider this had to be instrumented after the fact
     * (009 research.md D-117).
     */
    @Test
    fun `an incomplete answer says which status it had in the log`() = runTest {
        server.enqueue(jsonResponse(200, interactionWithoutOutput("incomplete")))

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.Malformed, (result as GeminiSummaryResult.Rejected).reason)
        val said = crashReporter.messages.joinToString(" ")
        assertTrue(said, said.contains("status=incomplete"))
    }

    // ---------- Without a credential nothing goes out ----------

    /** 009 FR-029: not configured is a state, and it costs no request. */
    @Test
    fun `without a credential no request is made at all`() = runTest {
        val result = dataSource(key = null).summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiSummaryResult.Rejected).reason)
        assertEquals(0, server.requestCount)
    }

    // ---------- The allowance is checked before going out ----------

    /** 009 FR-020: a request the application already knows has no room does not go out. */
    @Test
    fun `when the minute allowance is spent nothing is sent`() = runTest {
        val coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter)
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_MINUTE) { coordinator.recordRequest() }

        val result = dataSource(coordinator = coordinator).summarise("sistema", "usuario")

        assertTrue(
            (result as GeminiSummaryResult.Rejected).reason is GeminiRefusal.QuotaMinute,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an exhausted daily allowance is told apart and sends nothing`() = runTest {
        val coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter)
        coordinator.recordExhaustion(
            retryAfterSeconds = GeminiRateLimitCoordinator.DAY_SCALE_THRESHOLD_SECONDS + 1,
        )

        val result = dataSource(coordinator = coordinator).summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.QuotaDay, (result as GeminiSummaryResult.Rejected).reason)
        assertEquals(0, server.requestCount)
    }

    // ---------- An answer that says nothing ----------

    /**
     * **Seen on a real phone, and it was not what I predicted.** The previous service returned, in a
     * second and a half, an answer with the right shape and the summary **empty**. That is not a
     * malformed body: it is the service giving up, and it deserves its own name and one retry.
     */
    @Test
    fun `an answer with an empty summary is told apart from a malformed one`() = runTest {
        repeat(2) { server.enqueue(jsonResponse(200, interaction(summaryJson(prose = "")))) }

        val result = dataSource().summarise("sistema", "usuario")

        assertEquals(GeminiRefusal.BlankSummary, (result as GeminiSummaryResult.Rejected).reason)
    }

    @Test
    fun `an empty summary is retried once, and once only`() = runTest {
        repeat(3) { server.enqueue(jsonResponse(200, interaction(summaryJson(prose = "")))) }

        dataSource().summarise("sistema", "usuario")

        assertEquals("una vez más, no dos: la cuota es compartida", 2, server.requestCount)
    }

    @Test
    fun `a retry that comes back with something is used`() = runTest {
        server.enqueue(jsonResponse(200, interaction(summaryJson(prose = ""))))
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        val result = dataSource().summarise("sistema", "usuario")

        assertTrue(result is GeminiSummaryResult.Success)
    }

    /**
     * The shape, never the values: the field names are our own schema, the content is the document
     * (009 FR-032). Without this there is no telling an empty answer from one whose fields we failed
     * to read, which are opposite problems.
     */
    @Test
    fun `the log describes the shape of an empty answer without quoting it`() = runTest {
        repeat(2) { server.enqueue(jsonResponse(200, interaction(summaryJson(prose = "")))) }

        dataSource().summarise("sistema", "usuario")

        val said = crashReporter.messages.joinToString(" ")
        assertTrue(said, said.contains("blank summary"))
        assertTrue(said, said.contains("plainLanguageSummary=0"))
        assertTrue(said, said.contains("keyPoints=1"))
        assertFalse("no puede citar el contenido", said.contains("Punto 1"))
    }

    /**
     * **Regression, seen on a phone.** After a blank summary the retry went straight out, ran into
     * the minute's allowance, and the reader ended up being told «the limit has been reached» when
     * what had happened was something else. A retry that cannot run must not change the error
     * (009 research.md D-108, FR-025).
     */
    @Test
    fun `a retry with no room left keeps the original reason instead of a quota one`() = runTest {
        val coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter)
        // One short of the minute's allowance: the first attempt is allowed and spends the last slot,
        // so the re-check before retrying finds no room. Which is exactly what happens for real.
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_MINUTE - 1) { coordinator.recordRequest() }
        server.enqueue(jsonResponse(200, interaction(summaryJson(prose = ""))))
        server.enqueue(jsonResponse(200, interaction(summaryJson())))

        val result = dataSource(coordinator = coordinator).summarise("sistema", "usuario")

        assertEquals(
            "debe seguir siendo el resumen en blanco, no la cuota",
            GeminiRefusal.BlankSummary,
            (result as GeminiSummaryResult.Rejected).reason,
        )
        assertEquals("y no se gasta un segundo intento", 1, server.requestCount)
    }

    // ---------- Leaving the screen ----------

    /**
     * **Regresión, vista en un móvil el 4 de septiembre de 2026.** Alguien pulsó Atrás mientras se
     * generaba un resumen y el registro dijo:
     *
     * ```
     * gemini: network: SocketException: Software caused connection abort
     * summary failed: Offline
     * ```
     *
     * No había ningún problema de conexión: la persona se fue. Y el fallo no se queda en el registro
     * —`fail()` publica `Failed(Offline)`, y en `observeSummary` el estado en curso **gana** al resumen
     * almacenado—, así que al volver a esa publicación se lee «No hay conexión» de un fallo que nunca
     * ocurrió.
     *
     * La causa es que `Call.execute()` **bloquea**: cancelar la corrutina no lo interrumpe con una
     * `CancellationException`, le rompe el socket, y lo que sale es una `IOException`. El
     * `catch (CancellationException)` de arriba no llega a dispararse nunca.
     *
     * `generate()` ya sabía qué hacer con una cancelación —limpia el estado y no la reporta, con FR-006
     * citado en el comentario—; lo que faltaba era que la cancelación llegara hasta allí.
     *
     * Va con `runBlocking` y dispatchers de verdad a propósito: lo que se comprueba es una carrera entre
     * un hilo bloqueado y una cancelación, y en tiempo virtual no existe.
     */
    @Test
    fun `leaving while a request is in flight is a cancellation and not an offline error`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(interaction(summaryJson()))
                .bodyDelay(10, TimeUnit.SECONDS)
                .build(),
        )
        val source = OkHttpGeminiSummaryDataSource(
            client = client,
            apiKeys = { "una-clave" },
            coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter),
            dispatchers = RealDispatchers,
            crashReporter = crashReporter,
            baseUrl = server.url("/v1beta/interactions").toString(),
        )

        val job = launch(Dispatchers.Default) { source.summarise("sistema", "usuario") }
        while (server.requestCount == 0) delay(20)

        job.cancel()        // la persona pulsa Atrás
        server.close()      // y el socket se rompe, como en el dispositivo
        job.join()

        assertFalse(
            "irse de la pantalla no es un problema de red: ${crashReporter.messages}",
            crashReporter.messages.any { it.contains("network:") },
        )
    }

    // ---------- The log has to talk ----------

    /**
     * **Regression.** The status code never reaches the screen, and that is precisely why it has to
     * reach the log: a 400 and a 503 are the same sentence to whoever reads and two different
     * problems to whoever fixes it. The first time this was tried on a phone the trail went cold
     * right here.
     */
    @Test
    fun `an http failure says which one it was in the log`() = runTest {
        server.enqueue(jsonResponse(400, """{"error":{"message":"bad request"}}"""))

        dataSource().summarise("sistema", "usuario")

        assertTrue(
            "el registro debe nombrar el código: ${crashReporter.messages}",
            crashReporter.messages.any { it.contains("400") },
        )
    }

    /** And the provider's own explanation of our request, which is what tells us what to fix. */
    @Test
    fun `an http failure carries the reason the service gave`() = runTest {
        server.enqueue(jsonResponse(400, """{"error":{"message":"Invalid JSON payload"}}"""))

        dataSource().summarise("sistema", "usuario")

        assertTrue(
            crashReporter.messages.joinToString(" "),
            crashReporter.messages.any { it.contains("Invalid JSON payload") },
        )
    }

    @Test
    fun `an unparseable answer says so without quoting the document`() = runTest {
        server.enqueue(jsonResponse(200, "esto no es json"))

        dataSource().summarise("sistema", "usuario")

        val said = crashReporter.messages.joinToString(" ")
        assertTrue(said.contains("unparseable"))
        // Never the body: it is the document (009 FR-032).
        assertFalse(said.contains("esto no es json"))
    }

    /** Every line of the log carries this prefix, which is how it is filtered on a device. */
    @Test
    fun `every log line is tagged with the service`() = runTest {
        server.enqueue(jsonResponse(400, """{"error":{"message":"bad request"}}"""))

        dataSource().summarise("sistema", "usuario")

        assertTrue(
            crashReporter.messages.toString(),
            crashReporter.messages.all { it.startsWith("gemini: ") },
        )
    }

    /** And never the credential, in any message. */
    @Test
    fun `nothing the reporter is told ever contains the credential`() = runTest {
        server.enqueue(jsonResponse(401, """{"error":{"message":"invalid api key"}}"""))

        dataSource(key = "AQ.AbSecreto").summarise("sistema", "usuario")

        assertFalse(crashReporter.messages.any { it.contains("AQ.AbSecreto") })
    }

    /**
     * The dispatcher is built from the test's own scheduler. Creating an independent one makes the
     * backoff `delay` throw «different schedulers» instead of running in virtual time.
     */
    private fun TestScope.dataSource(
        key: String? = "una-clave",
        coordinator: GeminiRateLimitCoordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter),
    ) = OkHttpGeminiSummaryDataSource(
        client = client,
        apiKeys = { key },
        coordinator = coordinator,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        crashReporter = crashReporter,
        baseUrl = server.url("/v1beta/interactions").toString(),
    )

    private fun jsonResponse(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    /** Our own payload, with the prose and the number of key points the caller wants. */
    private fun summaryJson(
        prose: String = "Se aprueba definitivamente la modificacion de la ordenanza.",
        keyPoints: Int = 1,
    ): String {
        val points = (1..keyPoints).joinToString(",") { """{"text":"Punto $it","pages":[1]}""" }
        return """
            {"documentTitle":"Aprobacion definitiva","documentType":"Anuncio",
             "issuingBody":"Ayuntamiento de Pielagos",
             "keyPoints":[$points],
             "affectedParties":[],"datesAndDeadlines":[],"amounts":[],"requiredActions":[],
             "appealsOrClaims":[],"warnings":[],
             "coverage":{"pagesAnalyzed":[1],"totalPages":1,"complete":true},
             "plainLanguageSummary":"$prose"}
        """.trimIndent().replace("\n", "")
    }

    /** The interaction the service answers with, in the schema current since 8 June 2026. */
    private fun interaction(
        summary: String,
        status: String = "completed",
        thoughtsFirst: Boolean = false,
    ): String {
        val escaped = summary.replace("\\", "\\\\").replace("\"", "\\\"")
        val thoughts = if (thoughtsFirst) {
            """{"type":"thought","content":[{"type":"text","text":"pensando"}]},"""
        } else {
            ""
        }
        return """
            {"id":"v1_abc","model":"${AiSummaryConstants.MODEL_ID}","status":"$status",
             "usage":{"total_input_tokens":5600,"total_output_tokens":1200,"total_tokens":6800,
                      "total_thought_tokens":0},
             "steps":[$thoughts{"type":"model_output","content":[{"type":"text","text":"$escaped"}]}]}
        """.trimIndent().replace("\n", "")
    }

    private fun interactionWithoutOutput(status: String) = """
        {"id":"v1_abc","model":"${AiSummaryConstants.MODEL_ID}","status":"$status","steps":[],
         "usage":{"total_input_tokens":10,"total_output_tokens":0,"total_tokens":10}}
    """.trimIndent().replace("\n", "")

    /**
     * Dispatchers de verdad, solo para la prueba de cancelación.
     *
     * Es la única de esta clase que no puede usar tiempo virtual: lo que comprueba es una carrera
     * entre un hilo bloqueado en un socket y una cancelación, y eso no existe en un scheduler de
     * pruebas. `main` no se toca en este camino.
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
}
