package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.DocumentRepository

/**
 * Trims the cached documents back under their limit.
 *
 * Run when a synchronisation ends, which is the moment the bulletin changed and yesterday's
 * documents stopped being the ones anybody is about to open. Doing it while a document is being
 * opened would risk deleting the very file being read, and the repository refuses to evict what is
 * in use for exactly that reason.
 */
class ReleaseUnusedDocumentsUseCase(private val documents: DocumentRepository) {

    suspend operator fun invoke() = documents.releaseUnused()
}
