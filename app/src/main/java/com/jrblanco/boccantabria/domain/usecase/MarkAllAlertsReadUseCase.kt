package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.repository.AlertRepository

/** «Marcar todas como leídas». */
class MarkAllAlertsReadUseCase(private val repository: AlertRepository) {

    suspend operator fun invoke(): AppResult<Unit> = repository.markAllRead()
}
