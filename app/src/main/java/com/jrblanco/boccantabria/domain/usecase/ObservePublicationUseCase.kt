package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.Flow

/** One publication, by its key. Emits `null` when it is no longer stored — information, not error. */
class ObservePublicationUseCase(
    private val repository: PublicationRepository,
) {
    operator fun invoke(externalKey: String): Flow<Publication?> =
        repository.observePublication(externalKey)
}
