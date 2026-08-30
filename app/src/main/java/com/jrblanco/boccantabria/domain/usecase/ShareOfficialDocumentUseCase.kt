package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.ShareTarget
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.repository.DocumentRepository

/**
 * Decides what sharing actually offers.
 *
 * Sharing sends the document. The **only** case where it does not is having no way to fetch it and
 * no stored copy, and then offering the link is better than leaving the person with nothing — as
 * long as they are told why, which is what [ShareTarget.Link] carries its reason for.
 *
 * The rule lives here and nowhere else: the screen asks and obeys. A screen that decided this for
 * itself would be a second place to keep in step.
 */
class ShareOfficialDocumentUseCase(
    private val documents: DocumentRepository,
    private val connectivity: ConnectivityRepository,
) {
    suspend operator fun invoke(publication: Publication): AppResult<ShareTarget> =
        when (val result = documents.ensureLocalCopy(publication)) {
            is AppResult.Success -> AppResult.Success(ShareTarget.Document(result.data))

            is AppResult.Failure -> when {
                // Only a connectivity problem degrades to the link. A document that turned out not
                // to be one is a different matter, and quietly sending its link instead would hide
                // that the bulletin's service is returning something wrong.
                result.error == DomainError.Network && !connectivity.isOnline() ->
                    AppResult.Success(
                        ShareTarget.Link(
                            url = publication.documentUrl,
                            reason = ShareTarget.LinkReason.NO_CONNECTION,
                        ),
                    )

                else -> result
            }
        }
}
