package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.OfficialDocument
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** A document store the test drives. */
class FakeDocumentRepository(
    initial: DocumentStatus = DocumentStatus.Absent,
) : DocumentRepository {

    private val status = MutableStateFlow(initial)

    var result: AppResult<OfficialDocument> = AppResult.Success(officialDocument())

    /**
     * Held open, the fetch stays in flight. That is what makes a transient state like «preparing»
     * genuinely observable: without something suspending, a StateFlow conflates it away before any
     * collector runs, and the test would be asserting on a race.
     */
    var gate: CompletableDeferred<Unit>? = null
    var calls: Int = 0
        private set
    var released: Int = 0
        private set

    fun emit(next: DocumentStatus) {
        status.value = next
    }

    override fun observeDocument(externalKey: String): Flow<DocumentStatus> = status

    override suspend fun ensureLocalCopy(publication: Publication): AppResult<OfficialDocument> {
        calls++
        gate?.await()
        return result
    }

    override suspend fun releaseUnused() {
        released++
    }
}

fun officialDocument(
    externalKey: String = "boc:439765",
    localPath: String = "/cache/documents/abc.pdf",
    byteCount: Long = 120_000,
    checksum: String = "a".repeat(64),
    lastUsedAt: Long = 1_700_000_000_000,
) = OfficialDocument(externalKey, localPath, byteCount, checksum, lastUsedAt)
