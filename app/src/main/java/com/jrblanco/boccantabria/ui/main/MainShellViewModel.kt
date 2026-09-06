package com.jrblanco.boccantabria.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.domain.usecase.ConsumeInAppAlertUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePendingInAppAlertUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveUnreadAlertCountUseCase
import com.jrblanco.boccantabria.domain.usecase.ReconcileBackgroundSyncUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The frame's own state: the badge on the bell and the in-app message.
 *
 * It also reconciles the periodic check once, when the shell starts: an application update or a
 * restored backup ends up in the right state without `Application.onCreate` touching the database
 * (012 research.md D-422).
 */
class MainShellViewModel(
    observeUnreadAlertCount: ObserveUnreadAlertCountUseCase,
    observePendingInAppAlert: ObservePendingInAppAlertUseCase,
    private val consumeInAppAlert: ConsumeInAppAlertUseCase,
    private val reconcileBackgroundSync: ReconcileBackgroundSyncUseCase,
) : ViewModel() {

    val uiState: StateFlow<MainShellUiState> = combine(
        observeUnreadAlertCount(),
        observePendingInAppAlert(),
    ) { unread, pending ->
        MainShellUiState(unreadAlerts = unread, pendingAlert = pending)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = MainShellUiState(),
    )

    init {
        viewModelScope.launch { reconcileBackgroundSync() }
    }

    /** Shown, or deliberately not shown because the person was already on the alerts. */
    fun onInAppAlertHandled() = consumeInAppAlert()

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
