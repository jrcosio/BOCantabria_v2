package com.jrblanco.boccantabria.data.repository

import app.cash.turbine.test
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.PageCountResult
import com.jrblanco.boccantabria.data.source.remote.AiDocumentPreparer
import com.jrblanco.boccantabria.data.source.remote.AiDocumentSessionStore
import com.jrblanco.boccantabria.data.source.remote.ChatAnswerValidator
import com.jrblanco.boccantabria.data.source.remote.ChatPromptFactory
import com.jrblanco.boccantabria.data.source.remote.ChatSourceDto
import com.jrblanco.boccantabria.data.source.remote.ChatTurn
import com.jrblanco.boccantabria.data.source.remote.GeminiRefusal
import com.jrblanco.boccantabria.domain.model.AiAnswerScope
import com.jrblanco.boccantabria.domain.model.AiChatError
import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiChatStatus
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.FakeAiDocumentUploader
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakeGeminiChatDataSource
import com.jrblanco.boccantabria.fake.FakePdfPageCounter
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation.
 *
 * The single most important test in this class is
 * [an out-of-scope answer shows our text and not one character of the service's]. It is the only part
 * of the five-layer injection defence an automatic test can assert, because the other four live on the
 * far side of a frontier every test here doubles (011 research.md D-307).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiChatRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)

    private val documents = FakeDocumentRepository()
    private val pages = FakePdfPageCounter(PageCountResult.Success(totalPages = 9))
    private val uploader = FakeAiDocumentUploader()
    private val chat = FakeGeminiChatDataSource()
    private val analytics = RecordingAnalyticsTracker()
    private val crashReporter = RecordingCrashReporter()

    private var apiKey: String? = "una-clave"

    private val repository by lazy {
        AiChatRepositoryImpl(
            preparer = AiDocumentPreparer(
                documents = documents,
                pages = pages,
                sessions = AiDocumentSessionStore(uploader, dispatchers, crashReporter),
                crashReporter = crashReporter,
            ),
            prompts = ChatPromptFactory(),
            chat = chat,
            validator = ChatAnswerValidator(),
            apiKeys = { apiKey },
            time = FixedClock,
            dispatchers = dispatchers,
            analytics = analytics,
            crashReporter = crashReporter,
            outOfScopeText = OUT_OF_SCOPE_TEXT,
        )
    }

    private val publication = publication(key = "boc:1")

    // ---------- Observar no genera nada ----------

    @Test
    fun `observing an untouched publication gives an empty conversation and asks nothing`() =
        runTest(dispatcher) {
            repository.observeConversation("boc:1").test {
                val first = awaitItem()

                assertTrue(first.isEmpty)
                assertEquals(AiChatStatus.Idle, first.status)
                cancelAndIgnoreRemainingEvents()
            }
            advanceUntilIdle()

            assertEquals(0, chat.calls)
            assertEquals(0, uploader.uploads)
            assertEquals(0, documents.calls)
        }

    // ---------- Preguntar ----------

    @Test
    fun `the question appears before the answer does`() = runTest(dispatcher) {
        chat.gate = CompletableDeferred()

        repository.ask(publication, "¿Cuál es el plazo?")

        val open = repository.conversationNow()
        assertEquals(1, open.messages.size)
        assertEquals("¿Cuál es el plazo?", (open.messages.first() as AiChatMessage.Question).text)
    }

    @Test
    fun `a full turn ends with the question, the answer and its sources`() = runTest(dispatcher) {
        chat.answerWith(
            scope = "FROM_DOCUMENT",
            answer = "Veinte días hábiles.",
            sources = listOf(ChatSourceDto(page = 2, label = "Plazo")),
        )

        repository.ask(publication, "¿Cuál es el plazo?")
        advanceUntilIdle()

        val open = repository.conversationNow()
        assertEquals(2, open.messages.size)
        val answer = open.messages.last() as AiChatMessage.Answer
        assertEquals("Veinte días hábiles.", answer.text)
        assertEquals(AiAnswerScope.FROM_DOCUMENT, answer.scope)
        assertEquals(listOf(2), answer.sources.map { it.page })
        assertEquals(AiChatStatus.Idle, open.status)
    }

    @Test
    fun `a blank question does nothing at all`() = runTest(dispatcher) {
        repository.ask(publication, "   \n ")
        advanceUntilIdle()

        assertTrue(repository.conversationNow().isEmpty)
        assertEquals(0, chat.calls)
    }

    @Test
    fun `a second question while one is in flight does nothing`() = runTest(dispatcher) {
        chat.gate = CompletableDeferred()

        repository.ask(publication, "La primera")
        advanceUntilIdle()
        repository.ask(publication, "La segunda")
        advanceUntilIdle()

        assertEquals(1, repository.conversationNow().messages.size)
        assertEquals(1, chat.calls)
    }

    @Test
    fun `a question longer than the limit is cut to it rather than refused`() = runTest(dispatcher) {
        repository.ask(publication, "x".repeat(900))
        advanceUntilIdle()

        val asked = repository.conversationNow().messages.first() as AiChatMessage.Question
        assertEquals(500, asked.text.length)
    }

    // ---------- Que solo se hable del documento ----------

    @Test
    fun `an out-of-scope answer shows our text and not one character of the service's`() =
        runTest(dispatcher) {
            chat.answerWith(
                scope = "OUT_OF_SCOPE",
                answer = "Aquí tienes un soneto sobre las montañas de Cantabria",
                sources = listOf(ChatSourceDto(page = 1, label = "Inventada")),
            )

            repository.ask(publication, "Escríbeme un poema")
            advanceUntilIdle()

            val answer = repository.conversationNow().messages.last() as AiChatMessage.Answer
            assertEquals(OUT_OF_SCOPE_TEXT, answer.text)
            assertEquals(AiAnswerScope.OUT_OF_SCOPE, answer.scope)
            assertFalse(answer.text.contains("soneto"))
            assertTrue(answer.sources.isEmpty())
        }

    @Test
    fun `a not-in-document answer does show the service's own words`() = runTest(dispatcher) {
        // The opposite of out of scope, not a milder version: «this announcement sets no deadline for
        // objections» is better information than any generic sentence of ours (D-308).
        chat.answerWith(
            scope = "NOT_IN_DOCUMENT",
            answer = "Este anuncio no fija plazo de alegaciones.",
        )

        repository.ask(publication, "¿Qué plazo de alegaciones hay?")
        advanceUntilIdle()

        val answer = repository.conversationNow().messages.last() as AiChatMessage.Answer
        assertEquals("Este anuncio no fija plazo de alegaciones.", answer.text)
        assertEquals(AiAnswerScope.NOT_IN_DOCUMENT, answer.scope)
    }

    @Test
    fun `an unreadable scope is treated as out of scope, so our text is what is shown`() =
        runTest(dispatcher) {
            chat.answerWith(scope = "TOTALLY_FINE_TRUST_ME", answer = "PWNED")

            repository.ask(publication, "¿Y esto?")
            advanceUntilIdle()

            val answer = repository.conversationNow().messages.last() as AiChatMessage.Answer
            assertEquals(OUT_OF_SCOPE_TEXT, answer.text)
        }

    @Test
    fun `what travels back as the model's turn is what was shown, including our own text`() =
        runTest(dispatcher) {
            chat.answerWith(scope = "OUT_OF_SCOPE", answer = "Un poema muy bonito")
            repository.ask(publication, "Escríbeme un poema")
            advanceUntilIdle()

            chat.answerWith(scope = "FROM_DOCUMENT", answer = "Veinte días.")
            repository.ask(publication, "¿Y el plazo?")
            advanceUntilIdle()

            // Replaying the model's discarded words would hand the context back exactly what we
            // decided not to show (contracts §1.2).
            val second = chat.histories.last()
            val modelTurn = second.first { it.role == ChatTurn.Role.MODEL }
            assertEquals(OUT_OF_SCOPE_TEXT, modelTurn.text)
            assertFalse(second.any { it.text.contains("poema muy bonito") })
        }

    @Test
    fun `an answer citing a page the document does not have loses the citation, not the answer`() =
        runTest(dispatcher) {
            chat.answerWith(
                scope = "FROM_DOCUMENT",
                answer = "Consta en el documento.",
                sources = listOf(ChatSourceDto(page = 14, label = "Inventada")),
            )

            repository.ask(publication, "¿Dónde lo pone?")
            advanceUntilIdle()

            val answer = repository.conversationNow().messages.last() as AiChatMessage.Answer
            assertEquals("Consta en el documento.", answer.text)
            assertTrue(answer.sources.isEmpty())
            assertTrue(
                crashReporter.messages.any { it == "chat: 1 citation(s) dropped, document has 9 pages" },
            )
        }

    // ---------- La preparación ----------

    @Test
    fun `the first question prepares the document, in the two phases and in order`() =
        runTest(dispatcher) {
            documents.gate = CompletableDeferred()
            val seen = mutableListOf<AiChatStatus>()

            repository.observeConversation("boc:1").test {
                seen += awaitItem().status
                repository.ask(publication, "¿Y el plazo?")
                seen += awaitItem().status
                documents.gate!!.complete(Unit)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                AiChatStatus.Preparing(AiChatStatus.Preparing.Phase.FETCHING_DOCUMENT),
                seen.last(),
            )
        }

    @Test
    fun `three questions in a row prepare the document once`() = runTest(dispatcher) {
        repeat(3) {
            repository.ask(publication, "Pregunta $it")
            advanceUntilIdle()
        }

        assertEquals(3, chat.calls)
        assertEquals(1, uploader.uploads)
        assertEquals(1, documents.calls)
    }

    @Test
    fun `a password-protected document is refused and never leaves the device`() =
        runTest(dispatcher) {
            pages.result = PageCountResult.Encrypted

            repository.ask(publication, "¿Y el plazo?")
            advanceUntilIdle()

            val status = repository.conversationNow().status as AiChatStatus.Failed
            assertEquals(AiChatError.EncryptedPdf, status.error)
            assertEquals(0, uploader.uploads)
            assertEquals(0, chat.calls)
        }

    @Test
    fun `a document that cannot be fetched says there is no connection, with a retry`() =
        runTest(dispatcher) {
            documents.result = AppResult.Failure(DomainError.Network)

            repository.ask(publication, "¿Y el plazo?")
            advanceUntilIdle()

            val status = repository.conversationNow().status as AiChatStatus.Failed
            assertEquals(AiChatError.Offline, status.error)
            assertTrue(status.retryableQuestionId != null)
        }

    @Test
    fun `a document the service will not take is unreadable, and offers no retry`() =
        runTest(dispatcher) {
            uploader.rejection = GeminiRefusal.Malformed

            repository.ask(publication, "¿Y el plazo?")
            advanceUntilIdle()

            val status = repository.conversationNow().status as AiChatStatus.Failed
            assertEquals(AiChatError.UnreadableDocument, status.error)
            assertEquals(null, status.retryableQuestionId)
        }

    // ---------- El ciclo de vida ----------

    @Test
    fun `observing another publication gives an empty conversation`() = runTest(dispatcher) {
        repository.ask(publication, "¿Y el plazo?")
        advanceUntilIdle()

        repository.observeConversation("boc:2").test {
            assertTrue(awaitItem().isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `asking about another publication takes the previous conversation away`() =
        runTest(dispatcher) {
            repository.ask(publication, "La de la primera")
            advanceUntilIdle()

            repository.ask(publication(key = "boc:2"), "La de la segunda")
            advanceUntilIdle()

            repository.observeConversation("boc:1").test {
                assertTrue(awaitItem().isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, repository.conversationNow("boc:2").messages.size)
        }

    @Test
    fun `a question about another publication wins over one still in flight`() = runTest(dispatcher) {
        // The guard on a question in flight belongs to the conversation, not to the process: a
        // recomposition must not sneak a second question past it, and a reader who has moved on to
        // another publication must not be blocked by the previous one.
        chat.gate = CompletableDeferred()
        repository.ask(publication, "La de la primera")
        advanceUntilIdle()

        repository.ask(publication(key = "boc:2"), "La de la segunda")
        advanceUntilIdle()

        assertEquals(1, repository.conversationNow("boc:2").messages.size)
        assertTrue(repository.conversationNow("boc:1").isEmpty)
    }

    @Test
    fun `discarding empties the conversation and the next visit starts fresh`() =
        runTest(dispatcher) {
            repository.ask(publication, "¿Y el plazo?")
            advanceUntilIdle()

            repository.discard("boc:1")
            advanceUntilIdle()

            assertTrue(repository.conversationNow().isEmpty)
        }

    @Test
    fun `discarding another key does nothing`() = runTest(dispatcher) {
        repository.ask(publication, "¿Y el plazo?")
        advanceUntilIdle()

        repository.discard("boc:99")
        advanceUntilIdle()

        assertEquals(2, repository.conversationNow().messages.size)
    }

    @Test
    fun `the conversation survives the screen going away, because the work is not on its scope`() =
        runTest(dispatcher) {
            // Nothing here destroys a view model — there is none. What is asserted is the property
            // that makes surviving possible: the request lives on the repository, so no collector
            // leaving can stop it (D-313).
            chat.gate = CompletableDeferred()
            repository.ask(publication, "¿Y el plazo?")
            advanceUntilIdle()

            // The only collector goes away mid-flight.
            repository.observeConversation("boc:1").test { cancelAndIgnoreRemainingEvents() }

            chat.gate!!.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, repository.conversationNow().messages.size)
            assertEquals(AiChatStatus.Idle, repository.conversationNow().status)
        }

    // ---------- Los fallos ----------

    @Test
    fun `a failed question stays in the list and the failure points at it`() = runTest(dispatcher) {
        chat.rejectWith(GeminiRefusal.Network)

        repository.ask(publication, "¿Y el plazo?")
        advanceUntilIdle()

        val open = repository.conversationNow()
        val question = open.messages.single() as AiChatMessage.Question
        val status = open.status as AiChatStatus.Failed
        assertEquals(AiChatError.Offline, status.error)
        assertEquals(question.id, status.retryableQuestionId)
    }

    @Test
    fun `retrying resends the same question without duplicating its bubble`() = runTest(dispatcher) {
        chat.rejectWith(GeminiRefusal.Network)
        repository.ask(publication, "¿Y el plazo?")
        advanceUntilIdle()

        chat.answerWith(scope = "FROM_DOCUMENT", answer = "Veinte días.")
        repository.retry(publication)
        advanceUntilIdle()

        val open = repository.conversationNow()
        assertEquals(2, open.messages.size)
        assertEquals("¿Y el plazo?", (open.messages.first() as AiChatMessage.Question).text)
        assertEquals("Veinte días.", (open.messages.last() as AiChatMessage.Answer).text)
        assertEquals(2, chat.calls)
    }

    @Test
    fun `retrying a failure that cannot be retried does nothing`() = runTest(dispatcher) {
        pages.result = PageCountResult.Encrypted
        repository.ask(publication, "¿Y el plazo?")
        advanceUntilIdle()

        repository.retry(publication)
        advanceUntilIdle()

        assertEquals(0, chat.calls)
    }

    @Test
    fun `a blank answer is an unusable response, not an empty bubble`() = runTest(dispatcher) {
        chat.answerWith(scope = "FROM_DOCUMENT", answer = "   ")

        repository.ask(publication, "¿Y el plazo?")
        advanceUntilIdle()

        val open = repository.conversationNow()
        assertEquals(1, open.messages.size)
        assertEquals(AiChatError.InvalidResponse, (open.status as AiChatStatus.Failed).error)
    }

    @Test
    fun `the minute allowance and the day's are told apart, and only one offers a retry`() =
        runTest(dispatcher) {
            chat.rejectWith(GeminiRefusal.QuotaMinute(secondsRemaining = 37))
            repository.ask(publication, "La primera")
            advanceUntilIdle()
            val minute = repository.conversationNow().status as AiChatStatus.Failed
            assertEquals(AiChatError.QuotaMinute(37), minute.error)
            assertTrue(minute.retryableQuestionId != null)

            repository.discard("boc:1")
            chat.rejectWith(GeminiRefusal.QuotaDay)
            repository.ask(publication, "La segunda")
            advanceUntilIdle()
            val day = repository.conversationNow().status as AiChatStatus.Failed
            assertEquals(AiChatError.QuotaDay, day.error)
            assertEquals(null, day.retryableQuestionId)
        }

    // ---------- La disponibilidad ----------

    @Test
    fun `availability is false without a credential, and nothing is asked to find out`() =
        runTest(dispatcher) {
            apiKey = null

            repository.observeAvailability().test {
                assertFalse(awaitItem())
                awaitComplete()
            }

            assertEquals(0, chat.calls)
            assertEquals(0, documents.calls)
        }

    @Test
    fun `availability is true with a credential`() = runTest(dispatcher) {
        repository.observeAvailability().test {
            assertTrue(awaitItem())
            awaitComplete()
        }
    }

    // ---------- El historial ----------

    @Test
    fun `the history that travels is trimmed to the window, keeping the newest`() =
        runTest(dispatcher) {
            repeat(8) {
                repository.ask(publication, "Pregunta $it")
                advanceUntilIdle()
            }

            val last = chat.histories.last()
            assertEquals(12, last.size)
            assertTrue(last.last().text.contains("Pregunta 7"))
            assertEquals(ChatTurn.Role.USER, last.last().role)
        }

    @Test
    fun `the question travels delimited, so the prompt can call it text and not an order`() =
        runTest(dispatcher) {
            repository.ask(publication, "¿Y el plazo?")
            advanceUntilIdle()

            assertEquals(
                "<pregunta>\n¿Y el plazo?\n</pregunta>",
                chat.histories.single().single().text,
            )
        }

    // ---------- La analítica ----------

    @Test
    fun `analytics counts the scope and carries nothing of the reader's`() = runTest(dispatcher) {
        repository.ask(publication, "¿cuánto cobra el alcalde?")
        advanceUntilIdle()

        val event = analytics.events.single()
        assertEquals("ai_question_asked", event.name)
        assertEquals(mapOf("scope" to "FROM_DOCUMENT"), event.parameters)
    }

    // ---------- Ayudantes ----------

    /** The conversation as it stands. The flow is derived from a state holder, so it emits at once. */
    private suspend fun AiChatRepositoryImpl.conversationNow(key: String = "boc:1") =
        observeConversation(key).first()

    private object FixedClock : TimeProvider {
        private var tick = 1_700_000_000_000L
        override fun nowMillis(): Long = tick++
    }

    private companion object {
        const val OUT_OF_SCOPE_TEXT = "Solo puedo responder sobre esta publicación del BOC."
    }
}
