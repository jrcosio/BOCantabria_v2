package com.jrblanco.boccantabria.ui.ask

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.AiChatConstants
import com.jrblanco.boccantabria.domain.model.AiChatError
import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiChatStatus
import com.jrblanco.boccantabria.domain.model.AiConversation
import com.jrblanco.boccantabria.domain.usecase.AcceptAiNoticeUseCase
import com.jrblanco.boccantabria.domain.usecase.AskAboutDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiAvailabilityUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiConversationUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiNoticeAcceptedUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.RetryLastQuestionUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.fake.FakeAiChatRepository
import com.jrblanco.boccantabria.fake.FakeAiSummaryRepository
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AskViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val publications = FakePublicationRepository()
    private val saved = FakeSavedPublicationRepository()
    private val chat = FakeAiChatRepository()
    private val summaries = FakeAiSummaryRepository(noticeAccepted = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        publications.publicationsByKey = mapOf("boc:1" to publication(key = "boc:1"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(notice: FakeAiSummaryRepository = summaries) = AskViewModel(
        savedStateHandle = SavedStateHandle(mapOf("externalKey" to "boc:1")),
        observePublication = ObservePublicationUseCase(publications),
        observeConversation = ObserveAiConversationUseCase(chat),
        observeAvailability = ObserveAiAvailabilityUseCase(chat),
        askAboutDocument = AskAboutDocumentUseCase(chat),
        retryLastQuestion = RetryLastQuestionUseCase(chat),
        observeSavedKeys = ObserveSavedKeysUseCase(saved),
        setPublicationSaved = SetPublicationSavedUseCase(saved),
        observeAiNoticeAccepted = ObserveAiNoticeAcceptedUseCase(notice),
        acceptAiNotice = AcceptAiNoticeUseCase(notice),
    )

    // ---------- El estado ----------

    @Test
    fun `starts with the publication, no messages and nothing to send`() = runTest(dispatcher) {
        viewModel().uiState.test {
            awaitItem()
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals("boc:1", state.publication?.externalKey)
            assertTrue(state.messages.isEmpty())
            assertFalse(state.canSend)
            assertTrue(state.showSuggestions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `writing something makes it sendable`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()

            model.onDraftChange("¿Cuál es el plazo?")
            advanceUntilIdle()

            assertTrue(expectMostRecentItem().canSend)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sending hands the question over and clears what was written`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            model.onDraftChange("¿Cuál es el plazo?")
            advanceUntilIdle()

            model.onSend()
            advanceUntilIdle()

            assertEquals(listOf("boc:1" to "¿Cuál es el plazo?"), chat.asked)
            assertEquals("", expectMostRecentItem().draft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank draft cannot be sent`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            model.onDraftChange("   ")
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().canSend)
            model.onSend()
            advanceUntilIdle()
            assertTrue(chat.asked.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing can be sent while a question is in flight`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            chat.emit(AiChatStatus.Thinking)
            model.onDraftChange("Otra más")
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().canSend)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing can be sent while the document is being prepared`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            chat.emit(AiChatStatus.Preparing(AiChatStatus.Preparing.Phase.UPLOADING_DOCUMENT))
            model.onDraftChange("Otra más")
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().canSend)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing can be sent without a credential, and the screen says so`() = runTest(dispatcher) {
        chat.setAvailable(false)
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            model.onDraftChange("¿Y el plazo?")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.isServiceConfigured)
            assertFalse(state.canSend)
            // And the suggestions go too: offering three ways in that lead nowhere is worse than none.
            assertFalse(state.showSuggestions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing can be sent before the publication has been read from what is stored`() =
        runTest(dispatcher) {
            publications.publicationsByKey = emptyMap()
            val model = viewModel()
            model.uiState.test {
                awaitItem()
                advanceUntilIdle()
                model.onDraftChange("¿Y el plazo?")
                advanceUntilIdle()

                assertFalse(expectMostRecentItem().canSend)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------- El límite, visible antes de enviar ----------

    @Test
    fun `the counter appears only when the limit is close`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()

            model.onDraftChange("x".repeat(AiChatConstants.COUNTER_VISIBLE_FROM - 1))
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().showCounter)

            model.onDraftChange("x".repeat(AiChatConstants.COUNTER_VISIBLE_FROM))
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().showCounter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `going over the limit blocks sending before the question goes out, not after`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.uiState.test {
                awaitItem()
                advanceUntilIdle()

                model.onDraftChange("x".repeat(AiChatConstants.MAX_QUESTION_LENGTH + 1))
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertTrue(state.isOverLimit)
                assertTrue(state.showCounter)
                assertFalse(state.canSend)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------- Las sugeridas ----------

    @Test
    fun `tapping a suggestion sends it`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()

            model.onSuggestionTapped("¿A quién afecta?")
            advanceUntilIdle()

            assertEquals(listOf("boc:1" to "¿A quién afecta?"), chat.asked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the suggestions disappear with the first message`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().showSuggestions)

            chat.emit(listOf(AiChatMessage.Question(id = "q1", atEpochMillis = 0, text = "Hola")))
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().showSuggestions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- El aviso ----------

    @Test
    fun `the first question opens the notice instead of sending`() = runTest(dispatcher) {
        val model = viewModel(notice = FakeAiSummaryRepository(noticeAccepted = false))

        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            model.onDraftChange("¿Y el plazo?")
            advanceUntilIdle()

            model.onSend()
            advanceUntilIdle()

            assertTrue(expectMostRecentItem().noticePending)
            assertTrue(chat.asked.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `accepting the notice sends what was written and does not ask again`() = runTest(dispatcher) {
        val model = viewModel(notice = FakeAiSummaryRepository(noticeAccepted = false))

        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            model.onDraftChange("¿Y el plazo?")
            advanceUntilIdle()
            model.onSend()
            advanceUntilIdle()

            model.onNoticeAccepted()
            advanceUntilIdle()

            assertEquals(listOf("boc:1" to "¿Y el plazo?"), chat.asked)
            val state = expectMostRecentItem()
            assertFalse(state.noticePending)
            assertTrue(state.noticeAccepted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the notice sends nothing and keeps what was written`() = runTest(dispatcher) {
        val model = viewModel(notice = FakeAiSummaryRepository(noticeAccepted = false))

        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            model.onDraftChange("¿Y el plazo?")
            advanceUntilIdle()
            model.onSend()
            advanceUntilIdle()

            model.onNoticeDismissed()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.noticePending)
            assertEquals("¿Y el plazo?", state.draft)
            assertTrue(chat.asked.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Reintentar y guardar ----------

    @Test
    fun `retrying asks the repository to resend`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()
            chat.emit(
                AiConversation(
                    externalKey = "boc:1",
                    messages = listOf(
                        AiChatMessage.Question(id = "q1", atEpochMillis = 0, text = "Hola"),
                    ),
                    status = AiChatStatus.Failed(AiChatError.Offline, retryableQuestionId = "q1"),
                ),
            )
            advanceUntilIdle()

            model.onRetry()
            advanceUntilIdle()

            assertEquals(listOf("boc:1"), chat.retries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A write that fails in silence is the worst of the three outcomes: the icon stays as it was,
     * which is correct, and nobody finds out why. The signal has to reach the state so `AskRoute` can
     * say it (007 FR-009).
     */
    @Test
    fun `a save that fails raises the signal and can be cleared once said`() = runTest(dispatcher) {
        saved.failWrites = true
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()

            model.onToggleSaved()
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().saveFailed)

            model.onSaveFailureShown()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().saveFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving from here marks the publication`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.test {
            awaitItem()
            advanceUntilIdle()

            model.onToggleSaved()
            advanceUntilIdle()

            assertTrue(expectMostRecentItem().isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
