package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.repository.SavedPublicationRepository

/**
 * Saves a publication or takes it off the list.
 *
 * One call for both directions, with the caller passing the value it wants: the screen already knows
 * which state it is showing, and a `toggle()` that read before writing would add a round trip and a
 * race to serve a case the interface never produces.
 */
class SetPublicationSavedUseCase(
    private val repository: SavedPublicationRepository,
) {
    suspend operator fun invoke(externalKey: String, saved: Boolean): AppResult<Unit> =
        repository.setSaved(externalKey, saved)
}
