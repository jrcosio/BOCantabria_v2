package com.jrblanco.boccantabria.data.source.remote

/**
 * Puts the official document where the service can read it, and takes it away again.
 *
 * The whole point of this frontier is that the document travels **once**. Without it, the chat of
 * the next feature would resend the bulletin on every question, because the history a client replays
 * carries the file inside it.
 */
interface AiDocumentUploader {

    /**
     * Does not return until the document is **ready to be consulted**, not merely received: the
     * service indexes it first, and asking about it too early fails.
     *
     * @param displayName built from public data of the publication. Never anything of the reader's
     *   (FR-006).
     */
    suspend fun upload(localPath: String, displayName: String): UploadResult

    /** Never throws. A deletion that fails must not cover up whatever else was happening. */
    suspend fun delete(remoteName: String)
}

/** What a request needs in order to point at a document already uploaded. */
data class UploadedDocument(
    /** The identifier the document is deleted by. */
    val remoteName: String,
    /** The reference that travels inside the request. */
    val fileUri: String,
    val mimeType: String,
)

sealed interface UploadResult {

    data class Success(val document: UploadedDocument) : UploadResult

    /**
     * Reuses [GeminiRefusal] on purpose: uploading and generating fail for the same reasons — no
     * credential, no network, no allowance, a service error — and having two vocabularies for the
     * same thing would need a second translation map and buy nothing. The one case that belongs to
     * uploading alone, the service failing to process the file, arrives as
     * [GeminiRefusal.Malformed], which is literally what it means: what was sent is of no use.
     */
    data class Rejected(val reason: GeminiRefusal) : UploadResult
}
