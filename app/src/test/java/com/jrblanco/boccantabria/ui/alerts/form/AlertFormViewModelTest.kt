package com.jrblanco.boccantabria.ui.alerts.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AlertRuleValidationError
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.domain.model.KeywordRejection
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.model.SectionSelection
import com.jrblanco.boccantabria.domain.usecase.CountAlertRulesUseCase
import com.jrblanco.boccantabria.domain.usecase.GetAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetNotificationStatusUseCase
import com.jrblanco.boccantabria.domain.usecase.GetSearchIssuersUseCase
import com.jrblanco.boccantabria.domain.usecase.MatchAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.PreviewAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.ReconcileBackgroundSyncUseCase
import com.jrblanco.boccantabria.domain.usecase.SaveAlertRuleUseCase
import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.FakeBackgroundSyncScheduler
import com.jrblanco.boccantabria.fake.FakeNotificationStatusRepository
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.FakeSearchRepository
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.alertRule
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sections = BocSectionRepositoryImpl()
    private val alerts = FakeAlertRepository()
    private val publications = FakePublicationRepository(
        listOf(publication("boc:1", title = "Ayudas a la ganadería."), publication("boc:2", title = "Pesca.")),
    )
    private val issuers = FakeSearchRepository()
    private val scheduler = FakeBackgroundSyncScheduler()
    private val notificationStatus = FakeNotificationStatusRepository(NotificationStatus.GRANTED)
    private val analytics = RecordingAnalyticsTracker()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(ruleId: String? = null, duplicateOf: String? = null) = AlertFormViewModel(
        savedStateHandle = SavedStateHandle(
            buildMap {
                ruleId?.let { put(AlertFormViewModel.ARG_RULE_ID, it) }
                duplicateOf?.let { put(AlertFormViewModel.ARG_DUPLICATE_OF, it) }
            },
        ),
        getRule = GetAlertRuleUseCase(alerts),
        saveRule = SaveAlertRuleUseCase(alerts, ReconcileBackgroundSyncUseCase(alerts, scheduler)),
        countRules = CountAlertRulesUseCase(alerts),
        getNotificationStatus = GetNotificationStatusUseCase(notificationStatus),
        getSections = GetBocSectionsUseCase(sections),
        getSearchIssuers = GetSearchIssuersUseCase(issuers),
        previewRule = PreviewAlertRuleUseCase(publications, MatchAlertRuleUseCase(sections), sections),
        analytics = analytics,
    )

    private fun AlertFormUiState.ready() = this as AlertFormUiState.Ready

    // ---------- Create ----------

    @Test
    fun `an empty form cannot be saved and says why`() = runTest(dispatcher) {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertFalse(state.canSave)
            assertEquals(setOf(AlertRuleValidationError.NAME_BLANK, AlertRuleValidationError.NO_CRITERIA), state.errors)
            assertFalse(state.isEdit)
            assertNull(state.previewCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding a keyword proposes a name and enables saving`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onKeywordAdded("ganadería")
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertEquals(listOf("ganadería"), state.draft.keywords)
            assertEquals("Ganadería", state.draft.name)
            assertTrue(state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refused keyword is said once and the draft is untouched`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onKeywordAdded("a")
            advanceUntilIdle()
            assertEquals(KeywordRejection.TOO_SHORT, expectMostRecentItem().ready().keywordRejection)
            viewModel.onKeywordRejectionConsumed()
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertNull(state.keywordRejection)
            assertTrue(state.draft.keywords.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a typed name is never overwritten by a proposal`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onNameChanged("Mi aviso")
            viewModel.onKeywordAdded("ganadería")
            advanceUntilIdle()
            assertEquals("Mi aviso", expectMostRecentItem().ready().draft.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Sections ----------

    @Test
    fun `toggling a parent selects its children and the summary says all`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSectionToggled("2")
            advanceUntilIdle()
            var state = expectMostRecentItem().ready()
            assertEquals(setOf("2.1", "2.2", "2.3"), state.draft.sectionCodes)
            assertEquals(3, state.selectedLeafCount)
            assertEquals(true, state.sectionParts!!.single().allChildren)
            assertEquals(SectionSelection.ToggleState.CHECKED, state.sectionRows.first { it.section.code == "2" }.state)

            viewModel.onSectionToggled("2.3")
            advanceUntilIdle()
            state = expectMostRecentItem().ready()
            assertEquals(SectionSelection.ToggleState.INDETERMINATE, state.sectionRows.first { it.section.code == "2" }.state)
            assertEquals(listOf("2.1", "2.2"), state.sectionParts!!.map { it.section.code })

            viewModel.onAllSectionsSelected()
            advanceUntilIdle()
            assertNull(expectMostRecentItem().ready().sectionParts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a section alone is enough to save`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSectionToggled("2.2")
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertTrue(state.canSave)
            assertEquals("Cursos, oposiciones y concursos", state.draft.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Organisation ----------

    @Test
    fun `suggestions come from what is stored and match a normalised fragment`() = runTest(dispatcher) {
        issuers.emitIssuers(listOf("Ayuntamiento de Piélagos", "Ayuntamiento de Santander", "Consejería"))
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onOrganizationChanged("pielag")
            advanceUntilIdle()
            assertEquals(listOf("Ayuntamiento de Piélagos"), expectMostRecentItem().ready().organizationSuggestions)
            viewModel.onOrganizationChosen("Ayuntamiento de Piélagos")
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertEquals("Ayuntamiento de Piélagos", state.draft.organizationQuery)
            assertTrue(state.organizationSuggestions.isEmpty())
            assertTrue(state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Match mode and enabled ----------

    @Test
    fun `mode and switch are reflected`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onMatchModeChanged(KeywordMatchMode.ALL)
            viewModel.onEnabledChanged(false)
            advanceUntilIdle()
            val draft = expectMostRecentItem().ready().draft
            assertEquals(KeywordMatchMode.ALL, draft.matchMode)
            assertFalse(draft.isEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Preview ----------

    @Test
    fun `the preview counts stored matches after the debounce and writes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onKeywordAdded("ganadería")
            advanceTimeBy(AlertFormViewModel.PREVIEW_DEBOUNCE_MILLIS + 1)
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertEquals(1, state.previewCount)
            assertEquals(listOf("boc:1"), state.preview.map { it.externalKey })

            viewModel.onPreviewOpened()
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().ready().previewOpen)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(alerts.calls.none { it.startsWith("recordMatches") || it.startsWith("save") })
    }

    // ---------- Save, and the permission ----------

    @Test
    fun `the first enabled rule with the permission pending asks for it`() = runTest(dispatcher) {
        notificationStatus.status = NotificationStatus.NEEDS_REQUEST
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onKeywordAdded("ganadería")
            viewModel.onSave()
            advanceUntilIdle()
            assertEquals(AlertFormUiState.Saved(requestPermission = true), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, alerts.storedRules.size)
        assertEquals(listOf("ensureScheduled"), scheduler.calls)
    }

    @Test
    fun `with the permission granted nothing is asked`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onKeywordAdded("ganadería")
            viewModel.onSave()
            advanceUntilIdle()
            assertEquals(AlertFormUiState.Saved(requestPermission = false), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second rule never asks, nor does a paused first one`() = runTest(dispatcher) {
        notificationStatus.status = NotificationStatus.NEEDS_REQUEST
        alerts.emitRules(listOf(alertRule(id = "existing")))
        val second = viewModel()
        second.uiState.test {
            advanceUntilIdle()
            second.onKeywordAdded("pesca")
            second.onSave()
            advanceUntilIdle()
            assertEquals(AlertFormUiState.Saved(requestPermission = false), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }

        alerts.emitRules(emptyList())
        val paused = viewModel()
        paused.uiState.test {
            advanceUntilIdle()
            paused.onKeywordAdded("pesca")
            paused.onEnabledChanged(false)
            paused.onSave()
            advanceUntilIdle()
            assertEquals(AlertFormUiState.Saved(requestPermission = false), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an invalid draft is not saved`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(alerts.storedRules.isEmpty())
    }

    @Test
    fun `a failed save is said once and the form stays`() = runTest(dispatcher) {
        alerts.failWrites = true
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onKeywordAdded("ganadería")
            viewModel.onSave()
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertTrue(state.saveFailed)
            assertFalse(state.isSaving)
            viewModel.onSaveFailureConsumed()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().ready().saveFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the permission answer is reported as a flag, and nothing about the rule ever is`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onNameChanged("Ganadería de Piélagos")
            viewModel.onKeywordAdded("ganadería")
            viewModel.onOrganizationChanged("Ayuntamiento de Piélagos")
            viewModel.onSave()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.onPermissionResult(granted = false)

        val event = analytics.events.single { it.name == AlertFormViewModel.EVENT_PERMISSION }
        assertEquals(mapOf("granted" to "false"), event.parameters)
        val everything = analytics.events.joinToString { "${it.name}${it.parameters}" }
        assertFalse(everything.contains("Ganadería"))
        assertFalse(everything.contains("ganadería"))
        assertFalse(everything.contains("Piélagos"))
    }

    // ---------- Edit and duplicate ----------

    @Test
    fun `editing loads the rule and saves under the same id`() = runTest(dispatcher) {
        alerts.emitRules(listOf(alertRule(id = "r1", name = "Ganadería", keywords = listOf("ganadería"), sectionCodes = setOf("6"))))
        val viewModel = viewModel(ruleId = "r1")
        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertTrue(state.isEdit)
            assertEquals("Ganadería", state.draft.name)
            assertEquals(setOf("6"), state.draft.sectionCodes)

            viewModel.onKeywordAdded("rural")
            viewModel.onSave()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, alerts.storedRules.size)
        assertEquals(listOf("ganadería", "rural"), alerts.storedRules.single().keywords)
    }

    /** FR-011: paused, «Copia de …», and a new identity so no match travels with it. */
    @Test
    fun `duplicating starts from a paused copy and creates anew`() = runTest(dispatcher) {
        alerts.emitRules(listOf(alertRule(id = "r1", name = "Ganadería")))
        val viewModel = viewModel(duplicateOf = "r1")
        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem().ready()
            assertFalse(state.isEdit)
            assertEquals("Copia de Ganadería", state.draft.name)
            assertFalse(state.draft.isEnabled)

            viewModel.onSave()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, alerts.storedRules.size)
    }

    @Test
    fun `a missing rule to edit starts an empty form rather than failing`() = runTest(dispatcher) {
        viewModel(ruleId = "gone").uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().ready().draft.keywords.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
