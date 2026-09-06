package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow

/** Distinct publications with an unread match: the number on the bell. */
class ObserveUnreadAlertCountUseCase(private val repository: AlertRepository) {

    operator fun invoke(): Flow<Int> = repository.observeUnreadCount()
}
