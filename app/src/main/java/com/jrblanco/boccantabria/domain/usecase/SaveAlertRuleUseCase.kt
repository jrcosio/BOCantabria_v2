package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.repository.AlertRepository

/**
 * Creates or replaces a rule, then makes sure the periodic check matches the new state.
 *
 * @param id `null` to create. Editing renews `activeSince`, so nothing already stored fires.
 */
class SaveAlertRuleUseCase(
    private val repository: AlertRepository,
    private val reconcileBackgroundSync: ReconcileBackgroundSyncUseCase,
) {
    suspend operator fun invoke(draft: AlertRuleDraft, id: String?): AppResult<String> {
        val result = repository.save(draft, id)
        if (result is AppResult.Success) reconcileBackgroundSync()
        return result
    }
}
