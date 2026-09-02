package com.jrblanco.boccantabria.ui.detail

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.domain.model.AiSummary
import com.jrblanco.boccantabria.domain.model.AiSummaryError
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.ui.detail.component.AiSummaryTab
import com.jrblanco.boccantabria.ui.detail.component.SECTION_AMOUNTS
import com.jrblanco.boccantabria.ui.detail.component.SECTION_APPEALS
import com.jrblanco.boccantabria.ui.detail.component.SECTION_DATES
import com.jrblanco.boccantabria.ui.detail.component.SECTION_KEY_POINTS
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_CARD
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_COVERAGE
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_DISCLAIMER
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_ERROR
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_GENERATE
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_PARTIAL_WARNING
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_PROGRESS
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_QUOTA
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_RETRY
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_SOURCES
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_STALE
import com.jrblanco.boccantabria.ui.detail.component.TAG_AI_SUMMARY_TAB
import com.jrblanco.boccantabria.ui.detail.component.aiSectionTag
import com.jrblanco.boccantabria.ui.detail.component.pageChipTag
import com.jrblanco.boccantabria.ui.detail.component.sourceChipTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The summary tab in every state it can be in.
 *
 * Thirteen of them are listed in `contracts/internal-contracts.md`, and writing them down is what
 * stops half of them from being discovered on a phone. Mounted on its own with `createComposeRule`:
 * none of this needs the graph, the network or the splash screen.
 */
class AiSummaryTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ---------- Before anything has been asked ----------

    @Test
    fun the_initial_state_offers_to_generate_and_shows_no_summary() {
        setContent(AiSummaryStatus.Idle)

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_TAB).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_AI_SUMMARY_GENERATE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_AI_SUMMARY_CARD).assertDoesNotExist()
    }

    @Test
    fun generating_is_reported_rather_than_decided_here() {
        var generated = 0
        setContent(AiSummaryStatus.Idle, onGenerate = { generated++ })

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_GENERATE).performClick()

        assertEquals(1, generated)
    }

    /** FR-007: with nothing to summarise there is no button pretending otherwise. */
    @Test
    fun a_publication_without_a_document_offers_nothing() {
        setContent(AiSummaryStatus.Idle, hasDocument = false)

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_GENERATE).assertDoesNotExist()
    }

    // ---------- While it is working ----------

    @Test
    fun each_phase_says_which_one_it_is() {
        val status = mutableStateOf<AiSummaryStatus>(
            AiSummaryStatus.Preparing(AiSummaryStatus.Preparing.Phase.FETCHING_DOCUMENT),
        )
        // One `setContent` per test: calling it twice throws, so the state is driven from outside.
        composeRule.setContent {
            BOCantabriaTheme { Tab(status.value) }
        }

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithText("Obteniendo el documento oficial…").assertIsDisplayed()

        status.value = AiSummaryStatus.Preparing(AiSummaryStatus.Preparing.Phase.EXTRACTING_TEXT)
        composeRule.onNodeWithText("Leyendo el texto del documento…").assertIsDisplayed()

        status.value = AiSummaryStatus.Generating(analysedPages = 2, totalPages = 2)
        composeRule.onNodeWithText("Generando el resumen…").assertIsDisplayed()
    }

    /**
     * FR-028. Said after the text has been read and **before** the allowance is spent, which is the
     * earliest honest moment: until the text is extracted nobody knows how many pages fit.
     */
    @Test
    fun a_document_that_does_not_fit_is_announced_before_the_request() {
        setContent(AiSummaryStatus.Generating(analysedPages = 6, totalPages = 14))

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_PARTIAL_WARNING).assertIsDisplayed()
        composeRule.onNodeWithText("Documento de 14 páginas. Se analizarán las 6 primeras.")
            .assertIsDisplayed()
    }

    @Test
    fun a_document_that_fits_whole_says_nothing_about_coverage() {
        setContent(AiSummaryStatus.Generating(analysedPages = 3, totalPages = 3))

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_PARTIAL_WARNING).assertDoesNotExist()
    }

    /** FR-038: waiting for quota is a countdown, not an error. */
    @Test
    fun waiting_for_quota_shows_how_long_is_left() {
        setContent(AiSummaryStatus.WaitingForQuota(secondsRemaining = 42))

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_QUOTA).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_AI_SUMMARY_ERROR).assertDoesNotExist()
    }

    // ---------- The summary ----------

    @Test
    fun a_finished_summary_leads_with_the_card_and_the_warning() {
        setContent(ready(summary()))

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("Resumen generado por IA").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_AI_SUMMARY_DISCLAIMER).assertIsDisplayed()
    }

    /**
     * FR-024 and SC-006. Perceptible by a third route as well: announced, not only drawn in red.
     * Somebody using a screen reader has to get the warning, not a decorative glyph.
     */
    @Test
    fun the_warning_is_announced_and_not_only_drawn() {
        setContent(ready(summary()))

        composeRule
            .onNodeWithContentDescription(
                "Aviso importante: este resumen lo ha generado una inteligencia artificial y " +
                    "puede contener errores. Comprueba siempre el texto oficial.",
            )
            .assertIsDisplayed()
    }

    /** FR-015: an empty list means the document does not say so, and the section is absent. */
    @Test
    fun sections_the_document_does_not_support_are_absent_rather_than_empty() {
        setContent(
            ready(
                summary(
                    keyPoints = listOf(AiSummary.ReferencedText("Se aprueba la ordenanza", listOf(1))),
                    amounts = emptyList(),
                    appeals = emptyList(),
                ),
            ),
        )

        composeRule.onNodeWithTag(aiSectionTag(SECTION_KEY_POINTS)).assertIsDisplayed()
        composeRule.onNodeWithTag(aiSectionTag(SECTION_AMOUNTS)).assertDoesNotExist()
        composeRule.onNodeWithTag(aiSectionTag(SECTION_APPEALS)).assertDoesNotExist()
    }

    @Test
    fun a_section_the_document_does_support_is_drawn_with_its_reference() {
        setContent(
            ready(
                summary(
                    dates = listOf(
                        AiSummary.ReferencedDate("quince días hábiles", "Alegaciones", listOf(2)),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(aiSectionTag(SECTION_DATES)).assertIsDisplayed()
        composeRule.onNodeWithText("quince días hábiles").assertIsDisplayed()
        // FR-020: la referencia va **junto al dato**, que es donde sirve para comprobarlo.
        composeRule.onNodeWithTag(pageChipTag(2)).assertIsDisplayed()
    }

    /** FR-020: every page that backs something is offered, once. */
    @Test
    fun the_sources_row_lists_every_page_cited() {
        setContent(
            ready(
                summary(
                    keyPoints = listOf(AiSummary.ReferencedText("Se aprueba", listOf(1, 3))),
                    amounts = listOf(AiSummary.ReferencedAmount("100 €", "Crédito", listOf(3))),
                    coverage = AiSummary.SummaryCoverage(listOf(1, 2, 3), 3, complete = true),
                ),
            ),
        )

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_SOURCES).assertIsDisplayed()
        composeRule.onNodeWithTag(sourceChipTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(sourceChipTag(3)).assertIsDisplayed()
        // La página 2 no respalda nada, así que no es fuente de nada.
        composeRule.onNodeWithTag(sourceChipTag(2)).assertDoesNotExist()
    }

    /** FR-021: the reference is reported, so the screen above can open the document there. */
    @Test
    fun tapping_a_page_reports_which_one() {
        var opened: Int? = null
        setContent(
            ready(
                summary(
                    keyPoints = listOf(AiSummary.ReferencedText("Se aprueba", listOf(3))),
                    coverage = AiSummary.SummaryCoverage(listOf(1, 2, 3), 3, complete = true),
                ),
            ),
            onOpenPage = { opened = it },
        )

        // El de la fila de fuentes: el mismo gesto que el chip en línea, distinto sitio.
        composeRule.onNodeWithTag(sourceChipTag(3)).performClick()

        assertEquals(3, opened)
    }

    /** FR-029: said out loud, not buried in the warnings. */
    @Test
    fun a_partial_summary_says_which_pages_it_covers() {
        setContent(
            ready(
                summary(
                    coverage = AiSummary.SummaryCoverage(listOf(1, 2), totalPages = 14, complete = false),
                ),
            ),
        )

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_COVERAGE).assertIsDisplayed()
    }

    @Test
    fun a_complete_summary_says_nothing_about_coverage() {
        setContent(ready(summary()))

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_COVERAGE).assertDoesNotExist()
    }

    /** FR-035: shown and marked, never removed on the application's own initiative. */
    @Test
    fun a_stale_summary_is_marked_and_still_shown() {
        setContent(ready(summary(), isStale = true))

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_STALE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_AI_SUMMARY_CARD).assertIsDisplayed()
    }

    // ---------- When it could not be done ----------

    /** FR-041: offered where trying again could help. */
    @Test
    fun a_recoverable_failure_offers_a_retry() {
        setContent(AiSummaryStatus.Failed(AiSummaryError.Offline))

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_AI_SUMMARY_RETRY).assertIsDisplayed()
    }

    /** And withheld where it could not. Offering it there is its own kind of lie. */
    @Test
    fun a_document_without_text_is_explained_and_offers_no_retry() {
        setContent(AiSummaryStatus.Failed(AiSummaryError.NoExtractableText))

        composeRule.onNodeWithText("Este documento no contiene texto que la aplicación pueda analizar.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_AI_SUMMARY_RETRY).assertDoesNotExist()
    }

    @Test
    fun an_exhausted_daily_allowance_offers_no_immediate_retry() {
        setContent(AiSummaryStatus.Failed(AiSummaryError.QuotaDay))

        composeRule.onNodeWithTag(TAG_AI_SUMMARY_RETRY).assertDoesNotExist()
    }

    @Test
    fun a_missing_credential_is_explained_as_a_limitation_of_the_application() {
        setContent(AiSummaryStatus.Failed(AiSummaryError.NotConfigured))

        composeRule.onNodeWithText("El servicio de resúmenes no está configurado en esta aplicación.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_AI_SUMMARY_RETRY).assertDoesNotExist()
    }

    /** Whatever went wrong, the official document is still there. */
    @Test
    fun every_failure_still_offers_the_official_document() {
        var opened = 0
        setContent(AiSummaryStatus.Failed(AiSummaryError.EncryptedPdf), onOpenDocument = { opened++ })

        composeRule.onNodeWithText("Abrir PDF oficial").performClick()

        assertEquals(1, opened)
    }

    // ---------- Helpers ----------

    private fun setContent(
        status: AiSummaryStatus,
        hasDocument: Boolean = true,
        onGenerate: () -> Unit = {},
        onOpenPage: (Int) -> Unit = {},
        onOpenDocument: () -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                Tab(
                    status = status,
                    hasDocument = hasDocument,
                    onGenerate = onGenerate,
                    onOpenPage = onOpenPage,
                    onOpenDocument = onOpenDocument,
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Tab(
        status: AiSummaryStatus,
        hasDocument: Boolean = true,
        onGenerate: () -> Unit = {},
        onOpenPage: (Int) -> Unit = {},
        onOpenDocument: () -> Unit = {},
    ) {
        AiSummaryTab(
            status = status,
            hasDocument = hasDocument,
            onGenerate = onGenerate,
            onRegenerate = {},
            onRetry = {},
            onOpenPage = onOpenPage,
            onOpenDocument = onOpenDocument,
            onCopy = {},
            onShare = {},
        )
    }

    private fun ready(summary: AiSummary, isStale: Boolean = false) =
        AiSummaryStatus.Ready(summary, generatedAtEpochMillis = 1_000L, isStale = isStale)

    private fun summary(
        keyPoints: List<AiSummary.ReferencedText> = emptyList(),
        dates: List<AiSummary.ReferencedDate> = emptyList(),
        amounts: List<AiSummary.ReferencedAmount> = emptyList(),
        appeals: List<AiSummary.ReferencedText> = emptyList(),
        coverage: AiSummary.SummaryCoverage =
            AiSummary.SummaryCoverage(listOf(1), totalPages = 1, complete = true),
    ) = AiSummary(
        documentTitle = "Aprobación definitiva",
        documentType = "Anuncio",
        issuingBody = "Ayuntamiento de Piélagos",
        plainLanguageSummary = "Se aprueba definitivamente la modificación de la ordenanza.",
        keyPoints = keyPoints,
        affectedParties = emptyList(),
        datesAndDeadlines = dates,
        amounts = amounts,
        requiredActions = emptyList(),
        appealsOrClaims = appeals,
        warnings = emptyList(),
        coverage = coverage,
    )
}
