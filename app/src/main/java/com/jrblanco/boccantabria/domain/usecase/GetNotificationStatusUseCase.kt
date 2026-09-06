package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.repository.NotificationStatusRepository

/** Whether Android will show this application's notifications, right now. */
class GetNotificationStatusUseCase(private val repository: NotificationStatusRepository) {

    operator fun invoke(): NotificationStatus = repository.status()
}
