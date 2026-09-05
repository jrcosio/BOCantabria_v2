package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.remote.AiDocumentUploader
import com.jrblanco.boccantabria.data.source.remote.GeminiRefusal
import com.jrblanco.boccantabria.data.source.remote.UploadResult
import com.jrblanco.boccantabria.data.source.remote.UploadedDocument
import kotlinx.coroutines.CompletableDeferred

/**
 * The upload, faked.
 *
 * [uploads] is the number that matters most: three requirements are about a document travelling
 * **once** (FR-008, SC-005) and one is about it going away (FR-009).
 */
class FakeAiDocumentUploader(
    /** Programmed to fail when set. */
    var rejection: GeminiRefusal? = null,
) : AiDocumentUploader {

    /** Held open, the upload stays in flight — which is what makes two concurrent opens a race. */
    var gate: CompletableDeferred<Unit>? = null

    var uploads: Int = 0
        private set
    val deleted: MutableList<String> = mutableListOf()
    val displayNames: MutableList<String> = mutableListOf()

    override suspend fun upload(localPath: String, displayName: String): UploadResult {
        uploads++
        displayNames += displayName
        gate?.await()
        rejection?.let { return UploadResult.Rejected(it) }
        return UploadResult.Success(
            UploadedDocument(
                remoteName = "files/fake-$uploads",
                fileUri = "https://example.invalid/v1beta/files/fake-$uploads",
                mimeType = "application/pdf",
            ),
        )
    }

    override suspend fun delete(remoteName: String) {
        deleted += remoteName
    }
}
