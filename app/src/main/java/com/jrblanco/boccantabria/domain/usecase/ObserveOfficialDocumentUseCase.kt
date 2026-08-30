package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow

/** How far along the local copy of a document is. */
class ObserveOfficialDocumentUseCase(
    private val repository: DocumentRepository,
) {
    operator fun invoke(externalKey: String): Flow<DocumentStatus> =
        repository.observeDocument(externalKey)
}
