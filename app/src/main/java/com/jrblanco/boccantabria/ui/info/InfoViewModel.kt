package com.jrblanco.boccantabria.ui.info

import androidx.lifecycle.ViewModel
import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.util.AppVersionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InfoViewModel(
    appVersion: AppVersionProvider,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InfoUiState(versionName = appVersion.versionName))
    val uiState: StateFlow<InfoUiState> = _uiState.asStateFlow()

    init {
        analytics.trackScreenView(SCREEN_NAME)
    }

    fun onLinkTapped(link: InfoLink) {
        analytics.track(
            AnalyticsEvent(
                name = EVENT_LINK_TAPPED,
                parameters = mapOf(PARAM_DESTINATION to link.analyticsValue),
            ),
        )
    }

    fun onLinkOpenFailed() {
        _uiState.update { it.copy(linkOpenFailed = true) }
    }

    fun onLinkErrorConsumed() {
        _uiState.update { it.copy(linkOpenFailed = false) }
    }

    companion object {
        const val SCREEN_NAME: String = "info"
        const val EVENT_LINK_TAPPED: String = "info_link_tapped"
        const val PARAM_DESTINATION: String = "destination"
    }
}
