package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository

/**
 * Lets go of the document prepared in the service for a publication.
 *
 * Called from the detail view model's `onCleared()`, which is the only moment in the lifecycle that
 * means "the reader left this publication": asking and the viewer are pushed **on top** of the
 * detail entry, so it stays alive while they are used and is only cleared on the way back out.
 *
 * Neither suspending nor returning an [com.jrblanco.boccantabria.domain.model.AppResult], and both
 * are deliberate. `onCleared()` has no live coroutine to launch into, and a deletion that fails has
 * nobody to tell — the service expires the file on its own, which is the safety net FR-011 relies
 * on. It is a release, not an operation whose outcome anyone waits for.
 */
class ReleaseAiDocumentSessionUseCase(private val repository: AiSummaryRepository) {

    operator fun invoke(externalKey: String) = repository.releaseDocumentSession(externalKey)
}
