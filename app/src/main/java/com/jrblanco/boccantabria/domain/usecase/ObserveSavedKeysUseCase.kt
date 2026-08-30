package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.SavedPublicationRepository
import kotlinx.coroutines.flow.Flow

/**
 * The keys of everything saved, so a card and a detail screen can draw their own state.
 *
 * One flow for both, instead of one per publication: the set is small by definition —a person writes
 * it by hand— and it keeps emitting, which is what makes the mark appear on the card the moment it is
 * made somewhere else.
 */
class ObserveSavedKeysUseCase(
    private val repository: SavedPublicationRepository,
) {
    operator fun invoke(): Flow<Set<String>> = repository.observeSavedKeys()
}
