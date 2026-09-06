package com.jrblanco.boccantabria.ui.ask

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.domain.model.AiAnswerScope
import com.jrblanco.boccantabria.domain.model.AiAnswerSource
import com.jrblanco.boccantabria.domain.model.AiChatError
import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiChatStatus
import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.ui.ask.component.TAG_ANSWER_SOURCES
import com.jrblanco.boccantabria.ui.ask.component.TAG_ASK_FOOTER
import com.jrblanco.boccantabria.ui.ask.component.TAG_ASK_HEADER
import com.jrblanco.boccantabria.ui.ask.component.TAG_ASK_SAVE
import com.jrblanco.boccantabria.ui.ask.component.TAG_CHAT_ERROR
import com.jrblanco.boccantabria.ui.ask.component.TAG_CHAT_RETRY
import com.jrblanco.boccantabria.ui.ask.component.TAG_COMPOSER_COUNTER
import com.jrblanco.boccantabria.ui.ask.component.TAG_COMPOSER_FIELD
import com.jrblanco.boccantabria.ui.ask.component.TAG_COMPOSER_SEND
import com.jrblanco.boccantabria.ui.ask.component.TAG_SCOPE_NOTICE
import com.jrblanco.boccantabria.ui.ask.component.TAG_SUGGESTIONS
import com.jrblanco.boccantabria.ui.ask.component.TAG_THINKING
import com.jrblanco.boccantabria.ui.ask.component.answerBubbleTag
import com.jrblanco.boccantabria.ui.ask.component.questionBubbleTag
import com.jrblanco.boccantabria.ui.ask.component.sourceRowTag
import com.jrblanco.boccantabria.ui.ask.component.suggestionTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * The conversation screen.
 *
 * Mounted with `createComposeRule()` and [AskContent] rather than the activity: it saves the 1.2 s
 * minimum of going through the splash on every test, and the full instrumented run already takes close
 * to two hours.
 *
 * **The thinking indicator animates for ever**, so any test that has it on screen drives the clock by
 * hand: an endless animation stops the composition reaching idle and `assertIsDisplayed()` waits for
 * idle — it hangs instead of failing (011 research.md D-326).
 */
class AskScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ---------- La conversación ----------

    @Test
    fun a_question_and_its_answer_are_both_shown() {
        setContent(state(messages = listOf(QUESTION, ANSWER)))

        composeRule.onNodeWithTag(questionBubbleTag("q1")).assertIsDisplayed()
        composeRule.onNodeWithText("¿Cuál es el plazo?").assertIsDisplayed()
        composeRule.onNodeWithTag(answerBubbleTag("a1")).assertIsDisplayed()
        composeRule.onNodeWithText("Veinte días hábiles.").assertIsDisplayed()
    }

    @Test
    fun the_sources_open_the_document_on_the_page_they_cite() {
        var opened: Int? = null
        setContent(state(messages = listOf(QUESTION, ANSWER)), onSourceClick = { opened = it.page })

        composeRule.onNodeWithTag(TAG_ANSWER_SOURCES).assertIsDisplayed()
        composeRule.onNodeWithTag(sourceRowTag(3)).performClick()

        assertEquals(3, opened)
    }

    @Test
    fun two_sources_of_the_same_answer_lead_to_different_pages() {
        val pages = mutableListOf<Int>()
        setContent(state(messages = listOf(QUESTION, ANSWER)), onSourceClick = { pages += it.page })

        composeRule.onNodeWithTag(sourceRowTag(2)).performClick()
        composeRule.onNodeWithTag(sourceRowTag(3)).performClick()

        assertEquals(listOf(2, 3), pages)
    }

    /** FR-015: losing the citations must not lose the answer. */
    @Test
    fun an_answer_whose_citations_were_all_impossible_is_shown_without_the_sources_block() {
        val bare = ANSWER.copy(id = "a2", sources = emptyList())
        setContent(state(messages = listOf(QUESTION, bare)))

        composeRule.onNodeWithTag(answerBubbleTag("a2")).assertIsDisplayed()
        composeRule.onNodeWithText("Veinte días hábiles.").assertIsDisplayed()
        composeRule.onAllNodesWithTagCount(TAG_ANSWER_SOURCES, expected = 0)
    }

    // ---------- Que solo se hable del documento ----------

    /**
     * **FR-021 and SC-004 seen from the screen.**
     *
     * The substitution happens in the data layer, so what this asserts is the other half: that the
     * screen shows whatever text it is given without second-guessing it, and that a bubble marked out
     * of scope carries our sentence.
     */
    @Test
    fun an_out_of_scope_answer_shows_the_applications_own_text() {
        val refusal = AiChatMessage.Answer(
            id = "a3",
            atEpochMillis = AT,
            text = OUR_REFUSAL,
            scope = AiAnswerScope.OUT_OF_SCOPE,
        )
        setContent(state(messages = listOf(QUESTION, refusal)))

        composeRule.onNodeWithText(OUR_REFUSAL, substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithTextCount("poema", expected = 0)
    }

    @Test
    fun the_scope_notice_is_always_on_screen() {
        setContent(state(messages = listOf(QUESTION, ANSWER)))

        // Permanent and not dismissible: it is the promise the whole feature rests on, and a promise
        // that scrolls away after the first read is one nobody remembers at the fifth answer.
        composeRule.onNodeWithTag(TAG_SCOPE_NOTICE).assertIsDisplayed()
    }

    /**
     * **FR-008 seen from the screen.**
     *
     * The conversation does not live here — it lives in the repository, precisely so that going back
     * to the detail and returning does not lose it. What this asserts is the half the screen owns:
     * given the messages, it renders them, so a freshly built screen over a live conversation shows
     * what was said (011 research.md D-312).
     */
    @Test
    fun a_screen_built_over_an_existing_conversation_shows_what_was_said() {
        setContent(state(messages = listOf(QUESTION, ANSWER)))

        composeRule.onNodeWithTag(questionBubbleTag("q1")).assertIsDisplayed()
        composeRule.onNodeWithTag(answerBubbleTag("a1")).assertIsDisplayed()
        // And the suggestions are gone, because this is not a fresh conversation.
        composeRule.onAllNodesWithTagCount(TAG_SUGGESTIONS, expected = 0)
    }

    // ---------- Enviar ----------

    @Test
    fun typing_a_question_enables_sending_it() {
        var sent = 0
        setContent(state(draft = "¿Cuál es el plazo?"), onSend = { sent++ })

        composeRule.onNodeWithTag(TAG_COMPOSER_SEND).assertIsEnabled().performClick()

        assertEquals(1, sent)
    }

    @Test
    fun an_empty_composer_cannot_be_sent() {
        setContent(state())

        composeRule.onNodeWithTag(TAG_COMPOSER_SEND).assertIsNotEnabled()
    }

    @Test
    fun what_is_typed_reaches_the_view_model() {
        val typed = mutableListOf<String>()
        setContent(state(), onDraftChange = { typed += it })

        composeRule.onNodeWithTag(TAG_COMPOSER_FIELD).performTextInput("¿Y el importe?")

        assertTrue("no llegó nada de lo escrito", typed.isNotEmpty())
    }

    /** FR-007: the limit is visible **before** sending, not discovered after. */
    @Test
    fun the_counter_appears_when_the_limit_is_close() {
        setContent(state(draft = "x".repeat(450)))

        composeRule.onNodeWithTag(TAG_COMPOSER_COUNTER).assertIsDisplayed()
        composeRule.onNodeWithText("450/500").assertIsDisplayed()
    }

    @Test
    fun going_over_the_limit_blocks_the_send_button() {
        setContent(state(draft = "x".repeat(501)))

        composeRule.onNodeWithTag(TAG_COMPOSER_SEND).assertIsNotEnabled()
    }

    // ---------- Las fases y la espera ----------

    @Test
    fun the_preparation_phase_is_named_and_the_composer_is_disabled() {
        composeRule.mainClock.autoAdvance = false
        setContent(
            state(status = AiChatStatus.Preparing(AiChatStatus.Preparing.Phase.UPLOADING_DOCUMENT)),
        )
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(TAG_THINKING).assertExists()
        composeRule.onNodeWithText("Preparando el documento…").assertExists()
        composeRule.onNodeWithTag(TAG_COMPOSER_SEND).assertIsNotEnabled()
    }

    @Test
    fun the_first_phase_says_the_document_is_being_fetched() {
        composeRule.mainClock.autoAdvance = false
        setContent(
            state(status = AiChatStatus.Preparing(AiChatStatus.Preparing.Phase.FETCHING_DOCUMENT)),
        )
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("Obteniendo el documento…").assertExists()
    }

    @Test
    fun while_thinking_nothing_else_can_be_sent() {
        composeRule.mainClock.autoAdvance = false
        setContent(state(draft = "otra pregunta", status = AiChatStatus.Thinking))
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(TAG_THINKING).assertExists()
        composeRule.onNodeWithTag(TAG_COMPOSER_SEND).assertIsNotEnabled()
    }

    // ---------- Los fallos ----------

    @Test
    fun a_retryable_failure_offers_the_way_out_and_keeps_the_question() {
        var retries = 0
        setContent(
            state(
                messages = listOf(QUESTION),
                status = AiChatStatus.Failed(AiChatError.Offline, retryableQuestionId = "q1"),
            ),
            onRetry = { retries++ },
        )

        composeRule.onNodeWithTag(TAG_CHAT_ERROR).assertIsDisplayed()
        // The question stays: whoever asked already wrote it once.
        composeRule.onNodeWithTag(questionBubbleTag("q1")).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CHAT_RETRY).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun a_failure_that_cannot_be_retried_offers_no_button() {
        setContent(
            state(
                status = AiChatStatus.Failed(AiChatError.EncryptedPdf, retryableQuestionId = null),
            ),
        )

        composeRule.onNodeWithTag(TAG_CHAT_ERROR).assertIsDisplayed()
        composeRule.onAllNodesWithTagCount(TAG_CHAT_RETRY, expected = 0)
    }

    /**
     * FR-031, seen from the screen rather than from the string table.
     *
     * Reads the **text of the subtree**, not `config.toString()`, which is what it used to do. Two
     * things were wrong with that. It carried more than the message — property keys, actions and
     * object identities rendered as `@1f429ac` — and one of those identity hashes containing `429`
     * failed this test roughly once every few full runs and never in isolation, printing a message
     * («el mensaje visible menciona 429») that was true of the dump and false of the text. And it
     * carried **less**: the tagged node holds no text of its own, so the assertion never actually
     * looked at the message it was written to protect. It passed for the wrong reason and failed for
     * a different wrong reason.
     */
    @Test
    fun the_failure_text_carries_no_code_and_no_provider_name() {
        setContent(
            state(status = AiChatStatus.Failed(AiChatError.Unknown, retryableQuestionId = "q1")),
        )

        val text = composeRule.onNodeWithTag(TAG_CHAT_ERROR).fetchSemanticsNode().visibleText()
        assertTrue("el nodo de error no muestra ningún texto", text.isNotBlank())
        listOf("500", "429", "gemini", "http", "json").forEach { forbidden ->
            assertTrue(
                "el mensaje visible «$text» menciona «$forbidden»",
                !text.lowercase().contains(forbidden),
            )
        }
    }

    /**
     * **FR-036, both halves.**
     *
     * The composer being dead is only half of it. Walking a build with no credential is what showed
     * the other half was missing: nothing worked and nothing said why, so somebody would type a
     * question and find the button inert with no explanation.
     */
    @Test
    fun without_a_credential_nothing_can_be_sent_and_the_screen_says_why() {
        setContent(state(draft = "¿Y el plazo?", isServiceConfigured = false))

        composeRule.onNodeWithTag(TAG_COMPOSER_SEND).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_CHAT_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Preguntar no está disponible en esta aplicación.")
            .assertIsDisplayed()
        // No retry: a build without a credential does not grow one by asking again.
        composeRule.onAllNodesWithTagCount(TAG_CHAT_RETRY, expected = 0)
    }

    @Test
    fun with_a_credential_the_unavailable_notice_is_not_there() {
        setContent(state())

        composeRule.onAllNodesWithTagCount(TAG_CHAT_ERROR, expected = 0)
    }

    // ---------- La cabecera, las sugeridas y el pie ----------

    @Test
    fun the_header_names_the_publication_and_its_date() {
        setContent(state())

        composeRule.onNodeWithTag(TAG_ASK_HEADER).assertIsDisplayed()
        composeRule.onNodeWithText("Aprobación definitiva de la Ordenanza Fiscal.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("13 de julio de 2022").assertIsDisplayed()
    }

    @Test
    fun the_star_reports_the_change() {
        var toggles = 0
        setContent(state(), onToggleSaved = { toggles++ })

        composeRule.onNodeWithTag(TAG_ASK_SAVE).performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun the_three_suggestions_are_offered_while_the_conversation_is_empty() {
        val tapped = mutableListOf<String>()
        setContent(state(), onSuggestionTapped = { tapped += it })

        composeRule.onNodeWithTag(TAG_SUGGESTIONS).assertIsDisplayed()
        composeRule.onNodeWithTag(suggestionTag(0)).performClick()

        assertEquals(listOf("¿A quién afecta?"), tapped)
    }

    @Test
    fun the_suggestions_are_gone_once_there_is_a_message() {
        setContent(state(messages = listOf(QUESTION)))

        composeRule.onAllNodesWithTagCount(TAG_SUGGESTIONS, expected = 0)
    }

    @Test
    fun the_official_document_is_one_tap_away() {
        var opened = 0
        setContent(state(), onOpenDocument = { opened++ })

        composeRule.onNodeWithTag(TAG_ASK_FOOTER).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun going_back_is_offered_and_reported() {
        var backs = 0
        setContent(state(), onBack = { backs++ })

        composeRule.onNodeWithTag(TAG_ASK_BACK).performClick()

        assertEquals(1, backs)
    }

    // ---------- Ayudantes ----------

    private fun state(
        messages: List<AiChatMessage> = emptyList(),
        status: AiChatStatus = AiChatStatus.Idle,
        draft: String = "",
        isServiceConfigured: Boolean = true,
    ) = AskUiState(
        publication = PUBLICATION,
        isSaved = false,
        messages = messages,
        status = status,
        draft = draft,
        noticeAccepted = true,
        isServiceConfigured = isServiceConfigured,
    )

    @Suppress("LongParameterList")
    /**
     * Every piece of text under a node, its own included.
     *
     * The tag sits on a container, and what a reader sees hangs off its children; asking only the
     * tagged node returns nothing at all.
     */
    private fun SemanticsNode.visibleText(): String =
        (config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            children.map { it.visibleText() }).joinToString(" ").trim()

    private fun setContent(
        state: AskUiState,
        onBack: () -> Unit = {},
        onDraftChange: (String) -> Unit = {},
        onSend: () -> Unit = {},
        onSuggestionTapped: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onToggleSaved: () -> Unit = {},
        onOpenDocument: () -> Unit = {},
        onSourceClick: (AiAnswerSource) -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                AskContent(
                    state = state,
                    onBack = onBack,
                    onDraftChange = onDraftChange,
                    onSend = onSend,
                    onSuggestionTapped = onSuggestionTapped,
                    onRetry = onRetry,
                    onToggleSaved = onToggleSaved,
                    onOpenDocument = onOpenDocument,
                    onSourceClick = onSourceClick,
                    onNoticeAccepted = {},
                    onNoticeDismissed = {},
                )
            }
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(
        tag: String,
        expected: Int,
    ) = assertEquals(
        expected,
        onAllNodesWithTag(tag).fetchSemanticsNodes().size,
    )

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(
        text: String,
        expected: Int,
    ) = assertEquals(
        expected,
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size,
    )

    private companion object {
        const val AT = 1_700_000_000_000L
        const val OUR_REFUSAL = "Solo puedo responder sobre esta publicación del BOC."

        val QUESTION = AiChatMessage.Question(id = "q1", atEpochMillis = AT, text = "¿Cuál es el plazo?")

        val ANSWER = AiChatMessage.Answer(
            id = "a1",
            atEpochMillis = AT,
            text = "Veinte días hábiles.",
            scope = AiAnswerScope.FROM_DOCUMENT,
            sources = listOf(
                AiAnswerSource(page = 2, label = "Disposición final"),
                AiAnswerSource(page = 3, label = "Entrada en vigor"),
            ),
        )

        val PUBLICATION = Publication(
            externalKey = "boc:439765",
            blobId = "439765",
            idSource = IdSource.BLOB_ID,
            feedId = "6802081",
            sectionCode = "1",
            subsectionCode = null,
            title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva de la Ordenanza Fiscal.",
            issuer = "AYUNTAMIENTO DE PIÉLAGOS",
            organizationPath = listOf("AYUNTAMIENTO DE PIÉLAGOS"),
            editionType = EditionType.ORDINARY,
            publicationDate = LocalDate.of(2022, 7, 13),
            documentUrl = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765",
            rawCategories = null,
        )
    }
}
