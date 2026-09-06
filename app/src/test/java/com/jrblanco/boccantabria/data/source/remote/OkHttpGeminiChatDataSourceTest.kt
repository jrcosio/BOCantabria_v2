package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.jrblanco.boccantabria.fake.TlsMockWebServer
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The conversation's way out to the service.
 *
 * The server really speaks TLS, like every other network test here: the application only ever talks
 * https, and a test server speaking plain HTTP would be testing something it does not do.
 *
 * Sibling of `OkHttpGeminiSummaryDataSourceTest`, and the overlap is the point — everything the
 * summary paid for once is asserted again here, because a second data source is a second chance to
 * lose it: the credential in a header, the request counted on the way out, `ensureActive()` first in
 * the `IOException` catch, and a log that carries shape and never content.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpGeminiChatDataSourceTest {

    private val crashReporter = RecordingCrashReporter()

    @get:Rule
    val tls = TlsMockWebServer()

    private val server: MockWebServer get() = tls.server
    private val client: OkHttpClient get() = tls.client

    // ---------- Lo que vuelve ----------

    @Test
    fun `a well formed answer becomes a payload with its usage`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        val success = result as GeminiChatResult.Success
        assertEquals("FROM_DOCUMENT", success.payload.scope)
        assertEquals("Veinte días hábiles.", success.payload.answer)
        assertEquals(1, success.payload.sources.size)
        assertEquals(2, success.payload.sources.first().page)
        assertEquals(6800, success.usage.totalTokens)
    }

    @Test
    fun `the reasoning step is skipped by its flag and never by position`() = runTest {
        // Measured against the live service in feature 009: the reasoning part arrives **before** the
        // answer every single time, so taking the first part would be wrong a hundred per cent of the
        // time.
        server.enqueue(jsonResponse(200, generation(answerJson(), thoughtsFirst = true)))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals("FROM_DOCUMENT", (result as GeminiChatResult.Success).payload.scope)
    }

    @Test
    fun `an answer with no text at all is malformed`() = runTest {
        server.enqueue(jsonResponse(200, EMPTY_GENERATION))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(GeminiRefusal.Malformed, (result as GeminiChatResult.Rejected).reason)
    }

    @Test
    fun `a body that will not parse is malformed`() = runTest {
        server.enqueue(jsonResponse(200, "no es json"))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(GeminiRefusal.Malformed, (result as GeminiChatResult.Rejected).reason)
    }

    // ---------- Lo que sale ----------

    @Test
    fun `the credential travels in the header and never in the body or the url`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        val request = server.takeRequest()
        assertEquals(API_KEY, request.headers["x-goog-api-key"])
        assertFalse(request.body!!.utf8().contains(API_KEY))
        assertFalse(request.url.toString().contains(API_KEY))
    }

    @Test
    fun `the document travels once, in the first user turn`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(
            SYSTEM,
            listOf(userTurn("La primera"), modelTurn("La respuesta"), userTurn("La segunda")),
            DOCUMENT,
        )

        val body = sentBody()
        val contents = body.jsonObject["contents"]!!.jsonArray
        assertEquals(3, contents.size)

        val firstParts = contents[0].jsonObject["parts"]!!.jsonArray
        assertEquals(
            DOCUMENT.fileUri,
            firstParts[0].jsonObject["file_data"]!!.jsonObject["file_uri"]!!.jsonPrimitive.content,
        )
        assertEquals("La primera", firstParts[1].jsonObject["text"]!!.jsonPrimitive.content)

        // And nowhere else: repeating it adds no context and adds a second way to be wrong. Counted
        // over the whole body rather than asserted absent per turn, because `encodeDefaults` makes
        // kotlinx write the null explicitly — `"file_data": null` is present, and empty.
        assertEquals(1, body.toString().split(DOCUMENT.fileUri).size - 1)
    }

    @Test
    fun `the document moves to whichever user turn is first, so it can never be missing`() = runTest {
        // What happens after the window has trimmed the turn that originally carried it (D-304).
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(SYSTEM, listOf(modelTurn("Cola de un turno anterior"), userTurn()), DOCUMENT)

        val contents = sentBody().jsonObject["contents"]!!.jsonArray
        assertTrue(contents[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["file_data"] is JsonNull)
        assertEquals(
            DOCUMENT.fileUri,
            contents[1].jsonObject["parts"]!!.jsonArray[0].jsonObject["file_data"]!!
                .jsonObject["file_uri"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `the roles alternate as the service expects`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(
            SYSTEM,
            listOf(userTurn("Una"), modelTurn("Dos"), userTurn("Tres")),
            DOCUMENT,
        )

        val roles = sentBody().jsonObject["contents"]!!.jsonArray
            .map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("user", "model", "user"), roles)
    }

    @Test
    fun `the schema travels verbatim, with the scope first`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        val schema = sentBody().jsonObject["generationConfig"]!!
            .jsonObject["responseJsonSchema"]!!.jsonObject
        assertEquals(
            listOf("scope", "sources", "answer"),
            schema["properties"]!!.jsonObject.keys.toList(),
        )
    }

    @Test
    fun `the thinking level is sent explicitly, because the provider's default is billed`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        val config = sentBody().jsonObject["generationConfig"]!!.jsonObject
        assertEquals(
            "MINIMAL",
            config["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `the output ceiling is a quarter of the summary's, because an answer is short`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        val config = sentBody().jsonObject["generationConfig"]!!.jsonObject
        assertEquals(2000, config["maxOutputTokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `the system instruction goes where the service looks for it`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        val parts = sentBody().jsonObject["system_instruction"]!!.jsonObject["parts"]!!.jsonArray
        assertEquals(SYSTEM, parts[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    // ---------- Lo que se rechaza ----------

    @Test
    fun `a 401 is a configuration problem and is not retried`() = runTest {
        server.enqueue(jsonResponse(401, errorBody("API key not valid", "UNAUTHENTICATED")))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiChatResult.Rejected).reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 403 is a configuration problem too`() = runTest {
        server.enqueue(jsonResponse(403, errorBody("Forbidden", "PERMISSION_DENIED")))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiChatResult.Rejected).reason)
    }

    @Test
    fun `a 429 is classified by the delay it asks for, read from the RetryInfo detail`() = runTest {
        server.enqueue(jsonResponse(429, quotaBody("37s")))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(
            GeminiRefusal.QuotaMinute(37),
            (result as GeminiChatResult.Rejected).reason,
        )
    }

    @Test
    fun `a 429 asking for a very long wait is the day's allowance`() = runTest {
        server.enqueue(jsonResponse(429, quotaBody("7200s")))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(GeminiRefusal.QuotaDay, (result as GeminiChatResult.Rejected).reason)
    }

    @Test
    fun `a 500 is retried and the second attempt can succeed`() = runTest {
        server.enqueue(jsonResponse(500, errorBody("high demand", "UNAVAILABLE")))
        server.enqueue(jsonResponse(200, generation(answerJson())))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertTrue(result is GeminiChatResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `three 500s give up and say so`() = runTest {
        repeat(3) { server.enqueue(jsonResponse(500, errorBody("high demand", "UNAVAILABLE"))) }

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(
            GeminiRefusal.HttpError(500),
            (result as GeminiChatResult.Rejected).reason,
        )
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a 400 is not retried, because asking again will not fix our request`() = runTest {
        server.enqueue(jsonResponse(400, errorBody("Invalid file_uri", "INVALID_ARGUMENT")))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(GeminiRefusal.HttpError(400), (result as GeminiChatResult.Rejected).reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a malformed body is not retried either`() = runTest {
        server.enqueue(jsonResponse(200, "{"))

        val result = dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(GeminiRefusal.Malformed, (result as GeminiChatResult.Rejected).reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `no credential means no request at all`() = runTest {
        val result = dataSource(apiKey = null).ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(GeminiRefusal.NotConfigured, (result as GeminiChatResult.Rejected).reason)
        assertEquals(0, server.requestCount)
    }

    // ---------- La cuota ----------

    @Test
    fun `the request is counted when it goes out, not when it comes back`() = runTest {
        // Three 500s spend three requests of the daily allowance for zero answers.
        val coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter)
        repeat(3) { server.enqueue(jsonResponse(500, errorBody("boom", "UNAVAILABLE"))) }

        dataSource(coordinator = coordinator).ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertEquals(3, server.requestCount)
    }

    @Test
    fun `the allowance is shared with the summary, so an exhausted day refuses before asking`() =
        runTest {
            val coordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter)
            coordinator.recordExhaustion(retryAfterSeconds = 7_200)

            val result = dataSource(coordinator = coordinator)
                .ask(SYSTEM, listOf(userTurn()), DOCUMENT)

            assertEquals(GeminiRefusal.QuotaDay, (result as GeminiChatResult.Rejected).reason)
            assertEquals(0, server.requestCount)
        }

    // ---------- La cancelación ----------

    @Test
    fun `cancelling mid-request is a cancellation and never a network failure`() {
        // **A blocking OkHttp call does not throw CancellationException when the coroutine is
        // cancelled**: it tears the socket down and an IOException comes out. Without
        // `ensureActive()` as the first line of that catch, leaving the screen is reported as «no
        // connection» for a failure that never happened. Seen on a real phone in feature 009.
        //
        // Feature 014 (PERF-002) adds the other half: cancelling has to **cancel the call** and come
        // back promptly. Before, the thread stayed blocked until the body arrived or the read timed
        // out, and the single AI request the application allows at a time stayed occupied with it.
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .bodyDelay(30, TimeUnit.SECONDS)
                .body(generation(answerJson()))
                .build(),
        )

        var outcome: Any? = null
        var elapsedMillis = 0L
        runBlocking {
            val job = launch(Dispatchers.IO) {
                outcome = try {
                    realDataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)
                } catch (cancellation: CancellationException) {
                    cancellation
                }
            }
            while (server.requestCount == 0) Thread.sleep(10)
            Thread.sleep(100)
            val started = System.nanoTime()
            job.cancel()
            job.join()
            elapsedMillis = (System.nanoTime() - started) / 1_000_000
        }

        assertTrue(
            "una cancelación no puede salir como fallo de red, era: $outcome",
            outcome is CancellationException || outcome == null,
        )
        assertTrue("tardó $elapsedMillis ms en volver", elapsedMillis < 5_000)
        assertTrue("la llamada no se canceló", tls.calls.last().isCanceled())
    }

    // ---------- El registro ----------

    @Test
    fun `the log never carries the credential`() = runTest {
        server.enqueue(jsonResponse(400, errorBody("Invalid request", "INVALID_ARGUMENT")))

        dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertFalse(crashReporter.messages.any { it.contains(API_KEY) })
    }

    @Test
    fun `the log never carries the question`() = runTest {
        server.enqueue(jsonResponse(400, errorBody("Invalid request", "INVALID_ARGUMENT")))

        dataSource().ask(SYSTEM, listOf(userTurn(SECRET_QUESTION)), DOCUMENT)

        assertFalse(crashReporter.messages.any { it.contains(SECRET_QUESTION) })
    }

    @Test
    fun `the log never carries the answer`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson(answer = SECRET_ANSWER))))

        dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertFalse(crashReporter.messages.any { it.contains(SECRET_ANSWER) })
    }

    @Test
    fun `the log never carries the document reference either`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)

        assertFalse(crashReporter.messages.any { it.contains(DOCUMENT.fileUri) })
    }

    @Test
    fun `the log does carry the shape, which is what makes a failure diagnosable`() = runTest {
        server.enqueue(jsonResponse(200, generation(answerJson())))

        dataSource().ask(SYSTEM, listOf(userTurn(), modelTurn("Ya"), userTurn()), DOCUMENT)

        assertTrue(crashReporter.messages.any { it == "chat: asking with 3 message(s)" })
        assertTrue(crashReporter.messages.any { it == "chat: answer scope=FROM_DOCUMENT, 1 source(s)" })
    }

    @Test
    fun `the four failures are told apart in the log, because on screen they are the same sentence`() =
        runTest {
            server.enqueue(jsonResponse(429, quotaBody("37s")))
            dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)
            assertTrue(crashReporter.messages.any { it == "chat: HTTP 429, retry in 37s" })

            crashReporter.messages.clear()
            server.enqueue(jsonResponse(400, errorBody("Invalid file_uri", "INVALID_ARGUMENT")))
            dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)
            assertTrue(crashReporter.messages.any { it.startsWith("chat: HTTP 400: Invalid file_uri") })

            crashReporter.messages.clear()
            server.enqueue(jsonResponse(200, "no es json"))
            dataSource().ask(SYSTEM, listOf(userTurn()), DOCUMENT)
            assertTrue(crashReporter.messages.any { it.startsWith("chat: unparseable answer of") })
        }

    // ---------- Ayudantes ----------

    private fun sentBody() = Json.parseToJsonElement(server.takeRequest().body!!.utf8())

    private fun TestScope.dataSource(
        apiKey: String? = API_KEY,
        coordinator: GeminiRateLimitCoordinator = GeminiRateLimitCoordinator(FixedClock, NoJitter),
    ): OkHttpGeminiChatDataSource = build(
        apiKey,
        coordinator,
        TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
    )

    /** Real threads. Only the cancellation test needs them, and it needs them genuinely. */
    private fun realDataSource(): OkHttpGeminiChatDataSource =
        build(API_KEY, GeminiRateLimitCoordinator(FixedClock, NoJitter), RealDispatchers)

    private fun build(
        apiKey: String?,
        coordinator: GeminiRateLimitCoordinator,
        dispatchers: DispatcherProvider,
    ) = OkHttpGeminiChatDataSource(
        client = client,
        apiKeys = { apiKey },
        coordinator = coordinator,
        dispatchers = dispatchers,
        crashReporter = crashReporter,
        baseUrl = server.url("").toString().removeSuffix("/"),
    )

    private fun userTurn(text: String = "<pregunta>\n¿Cuál es el plazo?\n</pregunta>") =
        ChatTurn(ChatTurn.Role.USER, text)

    private fun modelTurn(text: String) = ChatTurn(ChatTurn.Role.MODEL, text)

    private fun jsonResponse(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun answerJson(
        scope: String = "FROM_DOCUMENT",
        answer: String = "Veinte días hábiles.",
    ): String = """
        {"scope":"$scope","sources":[{"page":2,"label":"Plazo"}],"answer":"$answer"}
    """.trimIndent().replace("\n", "")

    private fun generation(
        answer: String,
        finish: String = "STOP",
        thoughtsFirst: Boolean = false,
    ): String {
        val escaped = answer.replace("\\", "\\\\").replace("\"", "\\\"")
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
         * A fake credential that **deliberately does not look like a real one**, for the reason the
         * summary's test spells out: a fixture shaped like `AQ.A…` makes the repository's leak check
         * cry wolf on every run, and a check that always fails is a check that stops being read.
         */
        const val API_KEY = "clave-de-prueba-que-no-es-una-clave"

        const val SYSTEM = "Eres un asistente del BOC."
        const val SECRET_QUESTION = "¿cuanto cobra el alcalde de Piélagos?"
        const val SECRET_ANSWER = "El alcalde cobra cuarenta mil euros anuales."

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
