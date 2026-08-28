package com.jrblanco.boccantabria.ui.splash

import app.cash.turbine.test
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.AppVersionProvider
import com.jrblanco.boccantabria.domain.model.AppConfig
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.repository.AppConfigRepository
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.usecase.PrepareStartupUseCase
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Every timing assertion here runs on virtual time. Real waits would add well over a second per
 * case and make the suite sensitive to machine load, which the constitution forbids.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()
    private val crashReporter = RecordingCrashReporter()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------- User story 1 ----------

    @Test
    fun `starts loading and reaches Ready`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Ready is not emitted before the minimum on-screen time`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())

            advanceTimeBy(SplashViewModel.MINIMUM_VISIBLE_MILLIS - 1)
            expectNoEvents()

            advanceUntilIdle()
            assertEquals(SplashUiState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the minimum runs alongside the work, it is not added to it`() = runTest(dispatcher) {
        // Waiting in series would make every launch slower for everyone in order to fix a problem
        // only fast devices have. This is what stops that regression.
        val workMillis = SplashViewModel.MINIMUM_VISIBLE_MILLIS + 500
        val viewModel = viewModel(delayMillis = workMillis)
        val start = currentTime

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(workMillis, currentTime - start)
    }

    @Test
    fun `records the screen view exactly once`() = runTest(dispatcher) {
        viewModel()
        advanceUntilIdle()

        assertEquals(listOf(SplashViewModel.SCREEN_NAME), analytics.screenViews)
    }

    // ---------- User story 2 ----------

    @Test
    fun `a failure reaches Error and is reported`() = runTest(dispatcher) {
        val viewModel = viewModel(online = false)

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Error(DomainError.Network), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(crashReporter.nonFatals.isNotEmpty())
    }

    @Test
    fun `an unsupported version reaches Blocked`() = runTest(dispatcher) {
        val viewModel = viewModel(config = AppConfig(minSupportedVersionCode = VERSION + 1))

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Blocked(BlockReason.UpdateRequired), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a maintenance message reaches Blocked carrying it`() = runTest(dispatcher) {
        val viewModel = viewModel(config = AppConfig(maintenanceMessage = MESSAGE))

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Blocked(BlockReason.Maintenance(MESSAGE)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying from Error reaches Ready`() = runTest(dispatcher) {
        val connectivity = SwitchableConnectivity(online = false)
        val viewModel = viewModel(connectivity = connectivity)

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Error(DomainError.Network), awaitItem())

            connectivity.online = true
            viewModel.onRetry()

            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying while a preparation is in flight does not start a second one`() = runTest(dispatcher) {
        val repository = CountingConfigRepository()
        val viewModel = viewModel(configRepository = repository)

        viewModel.onRetry()
        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals(1, repository.calls)
    }

    @Test
    fun `continuing offline from Error reaches Ready`() = runTest(dispatcher) {
        val viewModel = viewModel(online = false)

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Error(DomainError.Network), awaitItem())

            viewModel.onContinueOffline()

            assertEquals(SplashUiState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `continuing offline from Blocked is ignored`() = runTest(dispatcher) {
        // Letting a blocked user through would defeat the whole point of blocking them.
        val viewModel = viewModel(config = AppConfig(minSupportedVersionCode = VERSION + 1))

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Blocked(BlockReason.UpdateRequired), awaitItem())

            viewModel.onContinueOffline()
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exceeding the time limit reaches Error`() = runTest(dispatcher) {
        // A network that accepts the connection but never answers — a hotel captive portal, say —
        // would otherwise leave the cover spinning forever.
        val viewModel = viewModel(delayMillis = SplashViewModel.TIMEOUT_MILLIS + 1_000)

        viewModel.uiState.test {
            assertEquals(SplashUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(SplashUiState.Error(DomainError.Unknown), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Helpers ----------

    private fun viewModel(
        online: Boolean = true,
        config: AppConfig = AppConfig(),
        delayMillis: Long = 0L,
        connectivity: ConnectivityRepository = SwitchableConnectivity(online),
        configRepository: AppConfigRepository = DelayingConfigRepository(config, delayMillis),
    ) = SplashViewModel(
        prepareStartup = PrepareStartupUseCase(
            connectivity = connectivity,
            appConfig = configRepository,
            appVersion = FixedVersion(VERSION),
        ),
        analytics = analytics,
        crashReporter = crashReporter,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private class SwitchableConnectivity(var online: Boolean) : ConnectivityRepository {
        override fun isOnline(): Boolean = online
    }

    private class DelayingConfigRepository(
        private val config: AppConfig,
        private val delayMillis: Long,
    ) : AppConfigRepository {
        override suspend fun loadConfig(): AppResult<AppConfig> {
            if (delayMillis > 0) delay(delayMillis)
            return AppResult.Success(config)
        }
    }

    private class CountingConfigRepository : AppConfigRepository {
        var calls: Int = 0
            private set

        override suspend fun loadConfig(): AppResult<AppConfig> {
            calls++
            return AppResult.Success(AppConfig())
        }
    }

    private class FixedVersion(override val versionCode: Int) : AppVersionProvider

    private class RecordingCrashReporter : CrashReporter {
        val nonFatals = mutableListOf<Throwable>()
        override fun recordNonFatal(throwable: Throwable) {
            nonFatals += throwable
        }

        override fun log(message: String) = Unit
    }

    private companion object {
        const val VERSION = 4
        const val MESSAGE = "Estamos en mantenimiento"
    }
}
