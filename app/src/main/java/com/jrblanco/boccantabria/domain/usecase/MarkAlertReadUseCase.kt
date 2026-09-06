package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.repository.AlertRepository

/**
 * Marks a publication's news read.
 *
 * Called by the detail screen when it opens, wherever it was opened from: the notification, the
 * Novedades tab, the bulletin. Idempotent, and a key with no news is a success (FR-056;
 * research.md D-426).
 */
class MarkAlertReadUseCase(private val repository: AlertRepository) {

    suspend operator fun invoke(externalKey: String): AppResult<Unit> = repository.markRead(externalKey)
}
