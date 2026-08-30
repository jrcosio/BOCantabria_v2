package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.OfficialDocument
import com.jrblanco.boccantabria.domain.model.Publication
import kotlinx.coroutines.flow.Flow

/**
 * The local copy of the official documents.
 *
 * Contract, the same as the rest of the project:
 * - Nothing here throws. Failures travel as [AppResult.Failure].
 * - [observeDocument] never terminates with an error: it emits [DocumentStatus.Failed].
 * - `CancellationException` is always rethrown.
 *
 * And two of its own, which the specification demands:
 * - [ensureLocalCopy] is **idempotent and deduplicated**: two concurrent calls for the same key
 *   share a single download rather than racing to write the same file.
 * - Nothing is ever stored unless it has been verified to be the document. A response that arrives
 *   with a success code and holds something else is rejected, not cached.
 */
interface DocumentRepository {

    fun observeDocument(externalKey: String): Flow<DocumentStatus>

    suspend fun ensureLocalCopy(publication: Publication): AppResult<OfficialDocument>

    /**
     * Frees cache space, oldest use first, never touching what is in use.
     *
     * This is a cache and not a library: what it holds may vanish, and nothing is lost when it
     * does. Storing publications for offline reading is the Saved feature, which is still to come.
     */
    suspend fun releaseUnused()
}
