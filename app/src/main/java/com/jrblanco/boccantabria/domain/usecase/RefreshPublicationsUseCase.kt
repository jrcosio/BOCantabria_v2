package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.SyncSummary
import com.jrblanco.boccantabria.domain.repository.PublicationRepository

/**
 * Brings the stored bulletin up to date.
 *
 * The staleness rule lives here and not in the repository because it is a product decision, not
 * a storage one: opening the application should not pester a service that publishes once a day,
 * but pulling to refresh must always work — that is what the gesture means.
 */
class RefreshPublicationsUseCase(
    private val repository: PublicationRepository,
) {
    /**
     * @param force `true` for the refresh gesture, which always reaches the network. `false` when
     *   merely opening the screen, which only does so if the stored copy has gone stale.
     */
    suspend operator fun invoke(force: Boolean): AppResult<SyncSummary> {
        if (!force && !repository.isCacheStale()) {
            return AppResult.Success(SyncSummary.SKIPPED)
        }
        return repository.refresh()
    }
}
