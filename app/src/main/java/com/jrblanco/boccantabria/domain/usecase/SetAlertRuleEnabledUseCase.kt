package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.repository.AlertRepository

/** Pauses or re-enables from the card's switch, and keeps the periodic check in step. */
class SetAlertRuleEnabledUseCase(
    private val repository: AlertRepository,
    private val reconcileBackgroundSync: ReconcileBackgroundSyncUseCase,
) {
    suspend operator fun invoke(id: String, enabled: Boolean): AppResult<Unit> {
        val result = repository.setEnabled(id, enabled)
        if (result is AppResult.Success) reconcileBackgroundSync()
        return result
    }
}
