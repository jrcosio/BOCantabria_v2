package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.AlertRepository
import com.jrblanco.boccantabria.domain.repository.BackgroundSyncScheduler

/**
 * Makes the periodic check agree with the rules: scheduled while any is enabled, cancelled when
 * none is.
 *
 * Called after every write that can change the answer, and once when the shell starts, so an
 * application update or a restored backup ends up in the right state without touching
 * `Application.onCreate` (research.md D-422).
 */
class ReconcileBackgroundSyncUseCase(
    private val repository: AlertRepository,
    private val scheduler: BackgroundSyncScheduler,
) {
    suspend operator fun invoke() {
        if (repository.countEnabled() > 0) scheduler.ensureScheduled() else scheduler.cancel()
    }
}
