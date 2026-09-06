package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.NotificationStatus

/**
 * Whether Android will show this application's notifications, right now.
 *
 * Not a flow: the platform emits nothing when the person flips the switch in Settings. The screen
 * asks again when it resumes (research.md D-427).
 */
interface NotificationStatusRepository {

    fun status(): NotificationStatus
}
