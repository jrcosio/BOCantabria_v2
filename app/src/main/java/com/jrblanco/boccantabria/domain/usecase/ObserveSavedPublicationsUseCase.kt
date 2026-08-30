package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.SavedPublicationRepository
import kotlinx.coroutines.flow.Flow

/**
 * What the person has saved, most recently saved first.
 *
 * The order arrives already applied: sorting here would be a second place deciding it, and the
 * screen has no way of knowing when each mark was made.
 */
class ObserveSavedPublicationsUseCase(
    private val repository: SavedPublicationRepository,
) {
    operator fun invoke(): Flow<List<Publication>> = repository.observeSaved()
}
