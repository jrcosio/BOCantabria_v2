package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AlertNews
import com.jrblanco.boccantabria.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow

/** The publications that matched, one row each. */
class ObserveAlertNewsUseCase(private val repository: AlertRepository) {

    operator fun invoke(): Flow<List<AlertNews>> = repository.observeNews()
}
