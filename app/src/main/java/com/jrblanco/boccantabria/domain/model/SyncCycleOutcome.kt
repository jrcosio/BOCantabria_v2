package com.jrblanco.boccantabria.domain.model

/**
 * What a synchronisation cycle did: the synchronisation itself, what it found for the alerts, and
 * how it was delivered.
 *
 * The screens read [summary] exactly as they read the old refresh result; [notifications] and
 * [delivery] exist for the worker's log and for the tests.
 */
data class SyncCycleOutcome(
    val summary: SyncSummary,
    val notifications: List<AlertNotification>,
    val delivery: AlertDelivery,
) {
    init {
        require(notifications.isEmpty() == (delivery == AlertDelivery.NONE)) {
            "nothing delivered means no notifications, and the other way round"
        }
    }
}
