package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.repository.AlertRepository

/** Removes a rule and its matches —never a publication— and keeps the periodic check in step. */
class DeleteAlertRuleUseCase(
    private val repository: AlertRepository,
    private val reconcileBackgroundSync: ReconcileBackgroundSyncUseCase,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> {
        val result = repository.delete(id)
        if (result is AppResult.Success) reconcileBackgroundSync()
        return result
    }
}
