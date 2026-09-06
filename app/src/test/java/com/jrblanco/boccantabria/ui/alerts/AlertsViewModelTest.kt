package com.jrblanco.boccantabria.ui.alerts

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.core.util.RelativeTime
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.usecase.DeleteAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetLastSyncUseCase
import com.jrblanco.boccantabria.domain.usecase.GetNotificationStatusUseCase
import com.jrblanco.boccantabria.domain.usecase.MarkAllAlertsReadUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAlertNewsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAlertRulesUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveUnreadAlertCountUseCase
import com.jrblanco.boccantabria.domain.usecase.ReconcileBackgroundSyncUseCase
import com.jrblanco.boccantabria.domain.usecase.SetAlertRuleEnabledUseCase
import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.FakeBackgroundSyncScheduler
import com.jrblanco.boccantabria.fake.FakeNotificationStatusRepository
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.alertRule
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = LocalDate.of(2026, 9, 6).atTime(LocalTime.of(10, 0)).atZone(madrid).toInstant().toEpochMilli()
    private val yesterday = LocalDate.of(2026, 9, 5).atTime(LocalTime.of(9, 0)).atZone(madrid).toInstant().toEpochMilli()

    private val alerts = FakeAlertRepository(now = now)
    private val publications = FakePublicationRepository()
    private val scheduler = FakeBackgroundSyncScheduler()
    private val notificationStatus = FakeNotificationStatusRepository()
    private val analytics = RecordingAnalyticsTracker()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedTab: String? = null): AlertsViewModel {
        val reconcile = ReconcileBackgroundSyncUseCase(alerts, scheduler)
        val time = object : TimeProvider { override fun nowMillis() = now }
        return AlertsViewModel(
            savedStateHandle = SavedStateHandle(buildMap { savedTab?.let { put(AlertsViewModel.KEY_TAB, it) } }),
            observeRules = ObserveAlertRulesUseCase(alerts, time, madrid),
            observeNews = ObserveAlertNewsUseCase(alerts),
            observeUnreadCount = ObserveUnreadAlertCountUseCase(alerts),
            setRuleEnabled = SetAlertRuleEnabledUseCase(alerts, reconcile),
            deleteRule = DeleteAlertRuleUseCase(alerts, reconcile),
            markAllRead = MarkAllAlertsReadUseCase(alerts),
            getNotificationStatus = GetNotificationStatusUseCase(notificationStatus),
            getLastSync = GetLastSyncUseCase(publications),
            getSections = GetBocSectionsUseCase(BocSectionRepositoryImpl()),
            time = time,
            analytics = analytics,
            zone = madrid,
        )
    }

    // ---------- Tabs ----------

    @Test
    fun `the default tab is the news, and a saved name is restored`() = runTest(dispatcher) {
        assertEquals(AlertsTab.NEWS, viewModel().uiState.value.tab)
        assertEquals(AlertsTab.RULES, viewModel(savedTab = "RULES").uiState.value.tab)
    }

    /** A name from another version must never take the screen down. */
    @Test
    fun `an unknown saved tab falls back to the news`() = runTest(dispatcher) {
        assertEquals(AlertsTab.NEWS, viewModel(savedTab = "PREGUNTAR").uiState.value.tab)
    }

    @Test
    fun `selecting a tab is reflected`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            viewModel.onTabSelected(AlertsTab.RULES)
            advanceUntilIdle()
            assertEquals(AlertsTab.RULES, expectMostRecentItem().tab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Rules ----------

    @Test
    fun `rules come with their cards and the active count`() = runTest(dispatcher) {
        alerts.emitRules(
            listOf(
                alertRule(id = "r1", sectionCodes = setOf("2.1", "2.2", "2.3")),
                alertRule(id = "r2", isEnabled = false),
            ),
        )
        alerts.seedMatch("r1", "boc:1", matchedAt = now - 60_000)
        alerts.seedMatch("r1", "boc:2", matchedAt = yesterday)

        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals(2, state.rules.size)
            assertEquals(1, state.activeCount)
            val card = state.rules.single { it.overview.rule.id == "r1" }
            assertEquals(1, card.overview.matchesToday)
            assertEquals(true, card.sectionParts!!.single().allChildren)
            assertEquals("2", card.sectionParts.single().section.code)
            assertEquals(RelativeTime.Label.Minutes(1), card.lastMatchLabel)
            assertNull(state.rules.single { it.overview.rule.id == "r2" }.sectionParts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pausing from the card writes and keeps the periodic check in step`() = runTest(dispatcher) {
        alerts.emitRules(listOf(alertRule(id = "r1")))
        val viewModel = viewModel()

        viewModel.onToggleEnabled("r1", enabled = false)
        advanceUntilIdle()

        assertFalse(alerts.storedRules.single().isEnabled)
        assertEquals(listOf("cancel"), scheduler.calls)
    }

    @Test
    fun `deleting asks first, then removes`() = runTest(dispatcher) {
        val rule = alertRule(id = "r1")
        alerts.emitRules(listOf(rule))
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onDeleteRequested(rule)
            advanceUntilIdle()
            assertEquals(rule, expectMostRecentItem().pendingDelete)

            viewModel.onDeleteCancelled()
            advanceUntilIdle()
            assertNull(expectMostRecentItem().pendingDelete)
            assertEquals(1, alerts.storedRules.size)

            viewModel.onDeleteRequested(rule)
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()
            assertNull(expectMostRecentItem().pendingDelete)
            assertTrue(alerts.storedRules.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed write is said once`() = runTest(dispatcher) {
        alerts.emitRules(listOf(alertRule(id = "r1")))
        alerts.failWrites = true
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onToggleEnabled("r1", enabled = false)
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().actionFailed)
            viewModel.onActionFailureConsumed()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().actionFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- News ----------

    @Test
    fun `news is grouped by local day, today first`() = runTest(dispatcher) {
        alerts.emitRules(listOf(alertRule(id = "r1")))
        alerts.publications["boc:1"] = publication("boc:1", date = LocalDate.of(2026, 9, 6))
        alerts.publications["boc:2"] = publication("boc:2", date = LocalDate.of(2026, 9, 5))
        alerts.seedMatch("r1", "boc:1", matchedAt = now - 60_000)
        alerts.seedMatch("r1", "boc:2", matchedAt = yesterday, read = true)

        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals(listOf(RelativeTime.Label.Today, RelativeTime.Label.Yesterday), state.news.map { it.label })
            assertEquals(1, state.unreadCount)
            assertFalse(state.news.first().items.single().isRead)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mark all read delegates`() = runTest(dispatcher) {
        alerts.emitRules(listOf(alertRule(id = "r1")))
        alerts.seedMatch("r1", "boc:1")

        viewModel().onMarkAllRead()
        advanceUntilIdle()

        assertTrue(alerts.calls.contains("markAllRead"))
    }

    // ---------- Permission ----------

    @Test
    fun `the banner shows only with active rules and notifications disabled`() = runTest(dispatcher) {
        notificationStatus.status = NotificationStatus.DISABLED
        alerts.emitRules(listOf(alertRule(id = "r1", isEnabled = false)))
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().showsPermissionBanner)

            alerts.emitRules(listOf(alertRule(id = "r1", isEnabled = true)))
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().showsPermissionBanner)

            notificationStatus.status = NotificationStatus.GRANTED
            viewModel.onResumed()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().showsPermissionBanner)

            // Android 13+ reports "switched off in Settings" as a missing permission: same banner.
            notificationStatus.status = NotificationStatus.NEEDS_REQUEST
            viewModel.onResumed()
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().showsPermissionBanner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the settings sheet carries the last check`() = runTest(dispatcher) {
        publications.lastSuccessAt = 4_000L
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onSettingsOpened()
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.settingsOpen)
            assertEquals(java.lang.Long.valueOf(4_000L), state.lastSyncAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the screen is reported, and nothing about the rules is`() = runTest(dispatcher) {
        alerts.emitRules(listOf(alertRule(id = "r1", name = "Ganadería")))
        viewModel()
        advanceUntilIdle()

        assertEquals(listOf("alerts"), analytics.screenViews)
        assertFalse(analytics.events.joinToString().contains("Ganadería"))
    }
}
