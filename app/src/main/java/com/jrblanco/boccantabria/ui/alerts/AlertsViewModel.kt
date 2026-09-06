package com.jrblanco.boccantabria.ui.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.util.RelativeTime
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.domain.model.AlertNews
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.AlertRuleOverview
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.model.SectionSelection
import com.jrblanco.boccantabria.domain.usecase.DeleteAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetLastSyncUseCase
import com.jrblanco.boccantabria.domain.usecase.GetNotificationStatusUseCase
import com.jrblanco.boccantabria.domain.usecase.MarkAllAlertsReadUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAlertNewsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAlertRulesUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveUnreadAlertCountUseCase
import com.jrblanco.boccantabria.domain.usecase.SetAlertRuleEnabledUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The alerts screen's state: two tabs, the rules with their cards, the news grouped by day, and
 * what Android says about notifications.
 *
 * The tab is kept in the saved state because the bottom bar navigates with `saveState = true` and
 * destroys this view model when the person moves to another tab; it comes back **by name** so a value
 * from another version can never take the screen down (FR-006).
 */
@Suppress("LongParameterList")
class AlertsViewModel(
    savedStateHandle: SavedStateHandle,
    observeRules: ObserveAlertRulesUseCase,
    observeNews: ObserveAlertNewsUseCase,
    observeUnreadCount: ObserveUnreadAlertCountUseCase,
    private val setRuleEnabled: SetAlertRuleEnabledUseCase,
    private val deleteRule: DeleteAlertRuleUseCase,
    private val markAllRead: MarkAllAlertsReadUseCase,
    private val getNotificationStatus: GetNotificationStatusUseCase,
    private val getLastSync: GetLastSyncUseCase,
    getSections: GetBocSectionsUseCase,
    private val time: TimeProvider,
    private val analytics: AnalyticsTracker,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val handle = savedStateHandle
    private val sections: List<BocSection> = getSections()

    private val local = MutableStateFlow(
        LocalState(
            tab = AlertsTab.byNameOrDefault(savedStateHandle[KEY_TAB]),
            notificationStatus = getNotificationStatus(),
        ),
    )

    private var toggleJob: Job? = null
    private var deleteJob: Job? = null
    private var markJob: Job? = null

    val uiState: StateFlow<AlertsUiState> = combine(
        observeRules(),
        observeNews(),
        observeUnreadCount(),
        local,
    ) { rules, news, unread, own ->
        val now = time.nowMillis()
        AlertsUiState(
            tab = own.tab,
            news = news.groupedByDay(now),
            unreadCount = unread,
            rules = rules.map { it.toCardState(now) },
            notificationStatus = own.notificationStatus,
            pendingDelete = own.pendingDelete,
            settingsOpen = own.settingsOpen,
            lastSyncAt = own.lastSyncAt,
            actionFailed = own.actionFailed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = AlertsUiState(tab = local.value.tab, notificationStatus = local.value.notificationStatus),
    )

    init {
        analytics.trackScreenView(SCREEN_NAME)
        refreshLastSync()
    }

    fun onTabSelected(tab: AlertsTab) {
        handle[KEY_TAB] = tab.name
        local.update { it.copy(tab = tab) }
    }

    /** Coming back from Android's settings emits nothing, so the screen asks again on resume. */
    fun onResumed() {
        local.update { it.copy(notificationStatus = getNotificationStatus()) }
        refreshLastSync()
    }

    fun onToggleEnabled(id: String, enabled: Boolean) {
        if (toggleJob?.isActive == true) return
        toggleJob = viewModelScope.launch {
            if (setRuleEnabled(id, enabled) is AppResult.Failure) local.update { it.copy(actionFailed = true) }
        }
    }

    fun onDeleteRequested(rule: AlertRule) = local.update { it.copy(pendingDelete = rule) }

    fun onDeleteCancelled() = local.update { it.copy(pendingDelete = null) }

    fun onDeleteConfirmed() {
        val rule = local.value.pendingDelete ?: return
        if (deleteJob?.isActive == true) return
        local.update { it.copy(pendingDelete = null) }
        deleteJob = viewModelScope.launch {
            if (deleteRule(rule.id) is AppResult.Failure) local.update { it.copy(actionFailed = true) }
        }
    }

    fun onMarkAllRead() {
        if (markJob?.isActive == true) return
        markJob = viewModelScope.launch {
            if (markAllRead() is AppResult.Failure) local.update { it.copy(actionFailed = true) }
        }
    }

    fun onSettingsOpened() = local.update { it.copy(settingsOpen = true) }

    fun onSettingsClosed() = local.update { it.copy(settingsOpen = false) }

    fun onActionFailureConsumed() = local.update { it.copy(actionFailed = false) }

    private fun refreshLastSync() {
        viewModelScope.launch {
            val at = getLastSync()
            local.update { it.copy(lastSyncAt = at) }
        }
    }

    private fun List<AlertNews>.groupedByDay(now: Long): List<AlertNewsDay> =
        groupBy { RelativeTime.dayOf(it.detectedAt, now, zone) }
            .map { (label, items) -> AlertNewsDay(label, items) }

    private fun AlertRuleOverview.toCardState(now: Long) = AlertRuleCardState(
        overview = this,
        sectionParts = SectionSelection.summaryParts(rule.sectionCodes, sections),
        lastMatchLabel = lastMatchedAt?.let { RelativeTime.label(it, now, zone) },
    )

    private data class LocalState(
        val tab: AlertsTab,
        val notificationStatus: NotificationStatus,
        val pendingDelete: AlertRule? = null,
        val settingsOpen: Boolean = false,
        val lastSyncAt: Long? = null,
        val actionFailed: Boolean = false,
    )

    companion object {
        const val SCREEN_NAME: String = "alerts"

        /** Same name as the route's property, on purpose: it is the key the argument arrives under. */
        const val KEY_TAB: String = "tab"

        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
