package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AlertNotification

/**
 * Delivers a cycle's matches when the application is not on screen.
 *
 * Declared here and implemented in `data`, where the platform's notification manager lives. The
 * implementation must never throw: a notification that cannot be shown is not a failed cycle, and
 * the matches are already stored by the time this is called (FR-062).
 */
interface AlertNotifier {

    fun post(notifications: List<AlertNotification>)
}
