package com.jrblanco.boccantabria.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.usecase.GetContentItemsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getContentItems: GetContentItemsUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Guards against concurrent loads. Tapping retry repeatedly is a real user behaviour and
     * without this each tap would start its own request, with the last one to finish winning.
     */
    private var loadJob: Job? = null

    init {
        analytics.trackScreenView(SCREEN_NAME)
        load()
    }

    fun onRetry() {
        load()
    }

    private fun load() {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            _uiState.value = when (val result = getContentItems()) {
                is AppResult.Success ->
                    if (result.data.isEmpty()) HomeUiState.Empty else HomeUiState.Content(result.data)

                is AppResult.Failure -> HomeUiState.Error(result.error)
            }
        }
    }

    companion object {
        const val SCREEN_NAME: String = "home"
    }
}
