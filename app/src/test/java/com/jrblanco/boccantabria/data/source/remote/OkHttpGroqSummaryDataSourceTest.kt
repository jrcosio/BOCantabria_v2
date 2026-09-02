package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
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

/**
 * The one way out to the summarising service, against a server that really speaks TLS.
 *
 * `okhttp-tls` is already in the project because the feed catalogue demands https, and the same
 * reasoning applies here: a test server speaking plain HTTP would be testing something the
 * application does not do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpGroqSummaryDataSourceTest {

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
        server.enqueue(jsonResponse(200, successBody()))

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        val success = result as GroqSummaryResult.Success
        assertEquals(
            "Se aprueba definitivamente la modificacion de la ordenanza.",
            success.payload.plainLanguageSummary,
        )
        assertEquals(1, success.payload.keyPoints.size)
        assertEquals(6_800, success.usage.totalTokens)
        assertEquals("fp_abc", success.systemFingerprint)
    }

    @Test
    fun `the request asks for the agreed model, schema and settings`() = runTest {
        server.enqueue(jsonResponse(200, successBody()))

        dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("qwen/qwen3.8-27b"))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"reasoning_effort\":\"none\""))
        // Medido: un resumen real llegó a 1.185 tokens con el techo en 1.200. Pasarse significa un
        // JSON cortado que no parsea, y una frase al lector culpando al servicio de lo que era
        // nuestro presupuesto.
        assertTrue(body.contains("\"max_completion_tokens\":1800"))
        assertTrue(body.contains("boc_ai_summary"))
        assertTrue(body.contains("\"strict\":true"))
    }

    /**
     * FR-010 and FR-047. The credential travels in the header so a captured body cannot carry it,
     * and what goes out is JSON text — never the bytes of the document.
     */
    @Test
    fun `the credential travels in the header and the body is text`() = runTest {
        server.enqueue(jsonResponse(200, successBody()))

        dataSource().summarise("sistema", "usuario contenido del documento", estimatedTokens = 7_000)

        val request = server.takeRequest()
        assertEquals("Bearer una-clave", request.headers["Authorization"])

        val body = request.body!!.utf8()
        assertFalse("la credencial no puede ir en el cuerpo", body.contains("una-clave"))
        assertFalse("no se envía el fichero, solo texto", body.contains("%PDF-"))
        assertTrue(body.contains("usuario contenido del documento"))
    }

    // ---------- Refusals ----------

    /** A 401 does not become a 200 by asking again: it is configuration, and it is not retried. */
    @Test
    fun `an unauthorised answer is a configuration problem and is not retried`() = runTest {
        server.enqueue(jsonResponse(401, """{"error":"invalid api key"}"""))

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(GroqRefusal.NotConfigured, (result as GroqSummaryResult.Rejected).reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a forbidden answer is treated the same way`() = runTest {
        server.enqueue(jsonResponse(403, """{"error":"forbidden"}"""))

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(GroqRefusal.NotConfigured, (result as GroqSummaryResult.Rejected).reason)
    }

    /** FR-038: the wait the service asks for is respected, not second-guessed. */
    @Test
    fun `a rate limited answer reports the wait the service asked for`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("retry-after", "42")
                .body("""{"error":"rate limit"}""")
                .build(),
        )

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(
            GroqRefusal.QuotaMinute(secondsRemaining = 42),
            (result as GroqSummaryResult.Rejected).reason,
        )
        assertEquals("no se reintenta un 429 por su cuenta", 1, server.requestCount)
    }

    @Test
    fun `a server error is retried and can recover`() = runTest {
        server.enqueue(jsonResponse(503, """{"error":"unavailable"}"""))
        server.enqueue(jsonResponse(200, successBody()))

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertTrue(result is GroqSummaryResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a server error that never recovers gives up after three attempts`() = runTest {
        repeat(3) { server.enqueue(jsonResponse(500, """{"error":"boom"}""")) }

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(GroqRefusal.HttpError(500), (result as GroqSummaryResult.Rejected).reason)
        assertEquals(3, server.requestCount)
    }

    /** FR-036: an unusable answer is refused rather than shown. */
    @Test
    fun `a malformed body is refused`() = runTest {
        server.enqueue(jsonResponse(200, "esto no es json"))

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(GroqRefusal.Malformed, (result as GroqSummaryResult.Rejected).reason)
    }

    @Test
    fun `an answer with no choice in it is refused`() = runTest {
        server.enqueue(jsonResponse(200, """{"id":"1","choices":[]}"""))

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(GroqRefusal.Malformed, (result as GroqSummaryResult.Rejected).reason)
    }

    // ---------- Without a credential nothing goes out ----------

    /** FR-042: not configured is a state, and it costs no request. */
    @Test
    fun `without a credential no request is made at all`() = runTest {
        val result = dataSource(key = null).summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(GroqRefusal.NotConfigured, (result as GroqSummaryResult.Rejected).reason)
        assertEquals(0, server.requestCount)
    }

    // ---------- The allowance is checked before going out ----------

    @Test
    fun `when the minute allowance cannot cover the request nothing is sent`() = runTest {
        val coordinator = GroqRateLimitCoordinator(FixedClock, NoJitter)
        coordinator.record(
            mapOf(
                GroqRateLimitCoordinator.HEADER_REMAINING_TOKENS to "100",
                GroqRateLimitCoordinator.HEADER_RESET_TOKENS to "30s",
            ),
        )

        val result = dataSource(coordinator = coordinator)
            .summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(
            GroqRefusal.QuotaMinute(secondsRemaining = 30),
            (result as GroqSummaryResult.Rejected).reason,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an exhausted daily allowance is told apart and sends nothing`() = runTest {
        val coordinator = GroqRateLimitCoordinator(FixedClock, NoJitter)
        coordinator.record(
            mapOf(
                GroqRateLimitCoordinator.HEADER_REMAINING_REQUESTS to "0",
                GroqRateLimitCoordinator.HEADER_RESET_REQUESTS to "3h",
            ),
        )

        val result = dataSource(coordinator = coordinator)
            .summarise("sistema", "usuario", estimatedTokens = 100)

        assertEquals(GroqRefusal.QuotaDay, (result as GroqSummaryResult.Rejected).reason)
        assertEquals(0, server.requestCount)
    }

    /**
     * The dispatcher is built from the test's own scheduler. Creating an independent one makes the
     * backoff `delay` throw «different schedulers» instead of running in virtual time.
     */
    // ---------- Una respuesta que no dice nada ----------

    /**
     * **Visto en un móvil real, y no era lo que yo predije.** El servicio devolvió, en segundo y
     * medio, una respuesta con la forma correcta y el resumen **vacío**. Eso no es un cuerpo mal
     * formado: es el servicio rindiéndose, y merece su propio nombre y un reintento.
     */
    @Test
    fun `an answer with an empty summary is told apart from a malformed one`() = runTest {
        repeat(2) { server.enqueue(jsonResponse(200, bodyWithSummary(""))) }

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(GroqRefusal.BlankSummary, (result as GroqSummaryResult.Rejected).reason)
    }

    @Test
    fun `an empty summary is retried once, and once only`() = runTest {
        repeat(3) { server.enqueue(jsonResponse(200, bodyWithSummary(""))) }

        dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals("una vez más, no dos: la cuota es compartida", 2, server.requestCount)
    }

    @Test
    fun `a retry that comes back with something is used`() = runTest {
        server.enqueue(jsonResponse(200, bodyWithSummary("")))
        server.enqueue(jsonResponse(200, successBody()))

        val result = dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertTrue(result is GroqSummaryResult.Success)
    }

    /**
     * La forma, nunca los valores: los nombres de campo son nuestro esquema, el contenido es el
     * documento (FR-047). Sin esto no se distingue una respuesta vacía de una cuyos campos no
     * supimos leer, que son problemas opuestos.
     */
    @Test
    fun `the log describes the shape of an empty answer without quoting it`() = runTest {
        repeat(2) { server.enqueue(jsonResponse(200, bodyWithSummary(""))) }

        dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        val said = crashReporter.messages.joinToString(" ")
        assertTrue(said, said.contains("blank summary"))
        assertTrue(said, said.contains("plainLanguageSummary=0"))
        assertTrue(said, said.contains("keyPoints=1"))
        assertFalse("no puede citar el contenido", said.contains("Se aprueba la ordenanza"))
    }

    /**
     * **Regresión, vista en un móvil.** Tras un resumen en blanco, el reintento salió disparado, chocó
     * con la cuota del minuto —el proveedor la cobra al pedir, no al responder— y el lector acabó
     * leyendo «se ha alcanzado el límite» cuando lo que había pasado era otra cosa. Un reintento que
     * no puede ejecutarse no debe cambiar el error.
     */
    @Test
    fun `a retry with no room left keeps the original reason instead of a quota one`() = runTest {
        val coordinator = GroqRateLimitCoordinator(FixedClock, NoJitter)
        // El resumen llega en blanco **y** con el minuto ya sin margen: es lo que ocurre de verdad,
        // porque el proveedor cobra la cuota al pedir y la petición que acaba de volver ya la gastó.
        server.enqueue(
            MockResponse.Builder().code(200).setHeader("Content-Type", "application/json")
                .setHeader("x-ratelimit-remaining-tokens", "10")
                .setHeader("x-ratelimit-reset-tokens", "45s")
                .body(bodyWithSummary("")).build(),
        )
        server.enqueue(jsonResponse(200, successBody()))

        val result = dataSource(coordinator = coordinator)
            .summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertEquals(
            "debe seguir siendo el resumen en blanco, no la cuota",
            GroqRefusal.BlankSummary,
            (result as GroqSummaryResult.Rejected).reason,
        )
        assertEquals("y no se gasta un segundo intento", 1, server.requestCount)
    }

    // ---------- Que el registro hable ----------

    /**
     * **Regresión.** El código de estado no llega nunca a la pantalla, y es justo por eso que tiene
     * que llegar al registro: un 400 y un 503 son la misma frase para quien lee y dos problemas
     * distintos para quien lo arregla. La primera vez que esto se probó en un móvil, el rastro se
     * perdía aquí.
     */
    @Test
    fun `an http failure says which one it was in the log`() = runTest {
        server.enqueue(jsonResponse(400, """{"error":"bad request"}"""))

        dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertTrue(
            "el registro debe nombrar el código: ${crashReporter.messages}",
            crashReporter.messages.any { it.contains("400") },
        )
    }

    @Test
    fun `an unparseable answer says so without quoting the document`() = runTest {
        server.enqueue(jsonResponse(200, "esto no es json"))

        dataSource().summarise("sistema", "usuario", estimatedTokens = 7_000)

        val said = crashReporter.messages.joinToString(" ")
        assertTrue(said.contains("unparseable"))
        // Nunca el cuerpo: es el documento (FR-047).
        assertFalse(said.contains("esto no es json"))
    }

    /** Y jamás la credencial, en ningún mensaje. */
    @Test
    fun `nothing the reporter is told ever contains the credential`() = runTest {
        server.enqueue(jsonResponse(401, """{"error":"invalid api key"}"""))

        dataSource(key = "gsk_secreto").summarise("sistema", "usuario", estimatedTokens = 7_000)

        assertFalse(crashReporter.messages.any { it.contains("gsk_secreto") })
    }

    private fun TestScope.dataSource(
        key: String? = "una-clave",
        coordinator: GroqRateLimitCoordinator = GroqRateLimitCoordinator(FixedClock, NoJitter),
    ) = OkHttpGroqSummaryDataSource(
        client = client,
        apiKeys = { key },
        coordinator = coordinator,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        crashReporter = crashReporter,
        baseUrl = server.url("/openai/v1/chat/completions").toString(),
    )

    private fun jsonResponse(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json")
        .setHeader("system_fingerprint", "fp_abc")
        .body(body)
        .build()

    /** Un cuerpo bien formado cuyo resumen llano es el que se le pase. */
    private fun bodyWithSummary(prose: String): String {
        val summary = """
            {"documentTitle":"Aprobacion definitiva","documentType":"Anuncio",
             "issuingBody":"Ayuntamiento de Pielagos",
             "keyPoints":[{"text":"Se aprueba la ordenanza","pages":[1]}],
             "affectedParties":[],"datesAndDeadlines":[],"amounts":[],"requiredActions":[],
             "appealsOrClaims":[],"warnings":[],
             "coverage":{"pagesAnalyzed":[1],"totalPages":1,"complete":true},
             "plainLanguageSummary":"$prose"}
        """.trimIndent().replace("\n", "")
        val escaped = summary.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            {"id":"chatcmpl-1","model":"qwen/qwen3.8-27b",
             "usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150},
             "choices":[{"index":0,"finish_reason":"stop",
                         "message":{"role":"assistant","content":"$escaped"}}]}
        """.trimIndent().replace("\n", "")
    }

    private fun successBody(): String {
        val summary = """
            {"documentTitle":"Aprobacion definitiva","documentType":"Anuncio",
             "issuingBody":"Ayuntamiento de Pielagos",
             "plainLanguageSummary":"Se aprueba definitivamente la modificacion de la ordenanza.",
             "keyPoints":[{"text":"Se aprueba la ordenanza","pages":[1]}],
             "affectedParties":[],"datesAndDeadlines":[],"amounts":[],"requiredActions":[],
             "appealsOrClaims":[],"warnings":[],
             "coverage":{"pagesAnalyzed":[1],"totalPages":1,"complete":true}}
        """.trimIndent().replace("\n", "")
        val escaped = summary.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            {"id":"chatcmpl-1","model":"qwen/qwen3.8-27b","system_fingerprint":"fp_abc",
             "usage":{"prompt_tokens":5600,"completion_tokens":1200,"total_tokens":6800},
             "choices":[{"index":0,"message":{"role":"assistant","content":"$escaped"}}]}
        """.trimIndent().replace("\n", "")
    }

    private object FixedClock : TimeProvider {
        override fun nowMillis(): Long = 1_700_000_000_000L
    }

    private object NoJitter : RandomProvider {
        override fun nextLong(bound: Long): Long = 0
    }
}
