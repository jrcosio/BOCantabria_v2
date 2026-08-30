package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.OfficialDocument
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.DocumentRepository

/**
 * Makes sure the official document is on the device, fetching it if it is not.
 *
 * Called both when the document tab appears and when the viewer opens. Asking twice is safe: the
 * repository shares a single download.
 */
class OpenOfficialDocumentUseCase(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(publication: Publication): AppResult<OfficialDocument> =
        repository.ensureLocalCopy(publication)
}
