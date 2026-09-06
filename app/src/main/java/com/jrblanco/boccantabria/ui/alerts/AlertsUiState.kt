package com.jrblanco.boccantabria.ui.alerts

import com.jrblanco.boccantabria.core.util.RelativeTime
import com.jrblanco.boccantabria.domain.model.AlertNews
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.AlertRuleOverview
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.model.SectionSelection

/** The two tabs. Restored **by name**, never with `valueOf` (FR-006). */
enum class AlertsTab {
    NEWS,
    RULES,
    ;

    companion object {
        fun byNameOrDefault(name: String?): AlertsTab = entries.firstOrNull { it.name == name } ?: NEWS
    }
}

/**
 * What the alerts screen draws.
 *
 * @param news grouped by the local day they were detected, newest first.
 * @param pendingDelete the rule whose deletion is waiting for confirmation, or nothing.
 * @param actionFailed one-shot: the screen says so and clears it.
 */
data class AlertsUiState(
    val tab: AlertsTab = AlertsTab.NEWS,
    val news: List<AlertNewsDay> = emptyList(),
    val unreadCount: Int = 0,
    val rules: List<AlertRuleCardState> = emptyList(),
    val notificationStatus: NotificationStatus = NotificationStatus.GRANTED,
    val pendingDelete: AlertRule? = null,
    val settingsOpen: Boolean = false,
    val lastSyncAt: Long? = null,
    val actionFailed: Boolean = false,
) {
    val activeCount: Int get() = rules.count { it.overview.rule.isEnabled }

    /**
     * Rules that would fire, and an Android that will not show them (FR-014).
     *
     * Anything but granted, on purpose: on Android 13+ switching notifications off in Settings
     * **revokes the runtime permission**, so the platform reports it exactly like "never asked".
     * Once there are active rules the distinction no longer matters to the person — the way out is
     * Settings either way. Seen on the emulator during the manual run of 6 September 2026.
     */
    val showsPermissionBanner: Boolean
        get() = activeCount > 0 && notificationStatus != NotificationStatus.GRANTED
}

/** One day of news: «Hoy», «Ayer» or a date, and what was detected then. */
data class AlertNewsDay(
    val label: RelativeTime.Label,
    val items: List<AlertNews>,
)

/**
 * A rule's card, with what it says already worked out.
 *
 * @param sectionParts `null` means every section.
 * @param lastMatchLabel `null` when the rule never matched.
 */
data class AlertRuleCardState(
    val overview: AlertRuleOverview,
    val sectionParts: List<SectionSelection.Part>?,
    val lastMatchLabel: RelativeTime.Label?,
)
