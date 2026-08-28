package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.Flow

/** The publications the current selection shows, kept up to date as the sources land. */
class ObservePublicationsUseCase(
    private val repository: PublicationRepository,
) {
    operator fun invoke(selection: HomeSelection): Flow<List<Publication>> =
        repository.observePublications(selection)
}
