package com.jrblanco.boccantabria.ui.main

import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.InAppAlert
import com.jrblanco.boccantabria.domain.usecase.ConsumeInAppAlertUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePendingInAppAlertUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveUnreadAlertCountUseCase
import com.jrblanco.boccantabria.domain.usecase.ReconcileBackgroundSyncUseCase
import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.FakeBackgroundSyncScheduler
import com.jrblanco.boccantabria.fake.FakeInAppAlertStore
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainShellViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val alerts = FakeAlertRepository(listOf(alertRule(id = "r1"), alertRule(id = "r2")))
    private val store = FakeInAppAlertStore()
    private val scheduler = FakeBackgroundSyncScheduler()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MainShellViewModel(
        observeUnreadAlertCount = ObserveUnreadAlertCountUseCase(alerts),
        observePendingInAppAlert = ObservePendingInAppAlertUseCase(store),
        consumeInAppAlert = ConsumeInAppAlertUseCase(store),
        reconcileBackgroundSync = ReconcileBackgroundSyncUseCase(alerts, scheduler),
    )

    /** FR-003: a publication caught by two rules is one on the bell. */
    @Test
    fun `the badge counts unread publications`() = runTest(dispatcher) {
        alerts.seedMatch("r1", "boc:1")
        alerts.seedMatch("r2", "boc:1")
        alerts.seedMatch("r1", "boc:2", read = true)

        viewModel().uiState.test {
            advanceUntilIdle()
            assertEquals(1, expectMostRecentItem().unreadAlerts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the pending message is exposed and handled once`() = runTest(dispatcher) {
        store.publish(InAppAlert(1, "Ganadería"))
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(InAppAlert(1, "Ganadería"), expectMostRecentItem().pendingAlert)

            viewModel.onInAppAlertHandled()
            advanceUntilIdle()
            assertNull(expectMostRecentItem().pendingAlert)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, store.consumed)
    }

    /** D-422: an update or a restored backup ends up scheduled without touching the Application. */
    @Test
    fun `starting the shell reconciles the periodic check once`() = runTest(dispatcher) {
        viewModel()
        advanceUntilIdle()

        assertEquals(listOf("ensureScheduled"), scheduler.calls)
    }
}
