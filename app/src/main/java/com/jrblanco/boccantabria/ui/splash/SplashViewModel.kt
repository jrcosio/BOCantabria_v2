package com.jrblanco.boccantabria.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.StartupStatus
import com.jrblanco.boccantabria.domain.usecase.PrepareStartupUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashViewModel(
    private val prepareStartup: PrepareStartupUseCase,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    /** Guards against concurrent preparations: tapping retry twice is a real user behaviour. */
    private var preparationJob: Job? = null

    init {
        analytics.trackScreenView(SCREEN_NAME)
        prepare()
    }

    fun onRetry() {
        prepare()
    }

    /**
     * Enters the application without a completed preparation.
     *
     * Deliberately ignored from [SplashUiState.Blocked]: an obsolete version or a service under
     * maintenance must not have an escape hatch.
     */
    fun onContinueOffline() {
        if (_uiState.value is SplashUiState.Error) {
            _uiState.value = SplashUiState.Ready
        }
    }

    private fun prepare() {
        if (preparationJob?.isActive == true) return

        preparationJob = viewModelScope.launch(dispatchers.default) {
            _uiState.value = SplashUiState.Loading

            // The minimum on-screen time runs *alongside* the work, not before it. Waiting in
            // series would add the two durations and make every launch slower for everyone, to fix
            // a flicker only fast devices ever see.
            val work = async { withTimeoutOrNull(TIMEOUT_MILLIS) { prepareStartup() } }
            val minimumVisible = async { delay(MINIMUM_VISIBLE_MILLIS) }

            val result = work.await()
            minimumVisible.await()

            _uiState.value = when (result) {
                null -> {
                    // The network accepted the connection but never answered.
                    reportFailure(DomainError.Unknown)
                    SplashUiState.Error(DomainError.Unknown)
                }

                is AppResult.Failure -> {
                    reportFailure(result.error)
                    SplashUiState.Error(result.error)
                }

                is AppResult.Success -> when (val status = result.data) {
                    StartupStatus.Ready -> SplashUiState.Ready
                    StartupStatus.UpdateRequired -> SplashUiState.Blocked(BlockReason.UpdateRequired)
                    is StartupStatus.Maintenance ->
                        SplashUiState.Blocked(BlockReason.Maintenance(status.message))
                }
            }
        }
    }

    private fun reportFailure(error: DomainError) {
        crashReporter.recordNonFatal(IllegalStateException("Startup preparation failed: $error"))
    }

    companion object {
        const val SCREEN_NAME: String = "splash"

        /** Below this the cover flickers, and a flicker reads as a bug rather than as speed. */
        const val MINIMUM_VISIBLE_MILLIS: Long = 1_200

        /** Long enough for a slow but honest network; short enough to beat a user's patience. */
        const val TIMEOUT_MILLIS: Long = 8_000
    }
}
