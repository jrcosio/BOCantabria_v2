package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * At most **one** document prepared in the service, for as long as a reader stays in a publication.
 *
 * This is what makes the whole feature pay for itself. The document travels once and every request
 * afterwards carries a reference; regenerating a summary costs nothing extra, and the chat of the
 * next feature will not resend the bulletin on every question.
 *
 * **Why one and not a map.** The statement that has to be checkable is FR-010, "opening another
 * publication takes the previous document away". Over a set of unknown size that is not a statement,
 * it is an intention (010 research.md D-207).
 *
 * The seven invariants, each with a test of its own in `AiDocumentSessionStoreTest`:
 *
 * 1. At most one live session in the process.
 * 2. [open] with the same key and the same checksum uploads nothing.
 * 3. [open] with another key takes the previous one away **before** uploading.
 * 4. [open] with the same key and another checksum relieves it — the bulletin's document changed.
 * 5. Two concurrent [open] calls produce **one** upload.
 * 6. [release] of a key that is not the current one does nothing.
 * 7. A rejection leaves no session open.
 *
 * Nothing here is persisted. Not to the database, not to preferences, not to saved state: it dies
 * with the process, and the service expiring the file is the safety net for what is left behind
 * (FR-011).
 */
class AiDocumentSessionStore(
    private val uploader: AiDocumentUploader,
    dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) {

    /**
     * Deletions run here and not on the caller's scope, and that is the whole reason this scope
     * exists: [release] is called from the detail view model's `onCleared()`, where `viewModelScope`
     * is **already cancelled**. Launching the deletion there would delete nothing.
     */
    private val cleanup = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val lock = Mutex()

    @Volatile
    private var current: Session? = null

    /**
     * The document ready to be consulted, uploading it first if it is not.
     *
     * Idempotent by key **and checksum**: the same publication with a different document is a
     * different document, and the bulletin does correct what it publishes.
     */
    suspend fun open(
        externalKey: String,
        pdfSha256: String,
        localPath: String,
        displayName: String,
    ): SessionResult = lock.withLock {
        current?.let { open ->
            if (open.externalKey == externalKey && open.pdfSha256 == pdfSha256) {
                crashReporter.log("session: reusing document for $externalKey")
                return@withLock SessionResult.Ready(open)
            }
            // Another publication, or the same one with a new document. Away with the old one
            // first: at most one lives at a time (FR-010).
            deleteNow(open)
        }
        current = null

        when (val uploaded = uploader.upload(localPath, displayName)) {
            is UploadResult.Success -> {
                val session = Session(externalKey, pdfSha256, uploaded.document)
                current = session
                SessionResult.Ready(session)
            }
            // Invariant 7: a rejection must not leave a half-open session behind.
            is UploadResult.Rejected -> SessionResult.Rejected(uploaded.reason)
        }
    }

    /**
     * Lets the document of [externalKey] go, if it is the one that is open.
     *
     * Not suspending: see [cleanup]. The guard on the key is not decoration either — a late
     * `onCleared()` must not take away the session of the publication the reader has just opened.
     */
    fun release(externalKey: String) {
        val open = current ?: return
        if (open.externalKey != externalKey) return
        current = null
        cleanup.launch {
            crashReporter.log("session: released $externalKey")
            uploader.delete(open.document.remoteName)
        }
    }

    private suspend fun deleteNow(session: Session) {
        crashReporter.log("session: relieving ${session.externalKey}")
        uploader.delete(session.document.remoteName)
    }

    data class Session(
        val externalKey: String,
        val pdfSha256: String,
        val document: UploadedDocument,
    )
}

sealed interface SessionResult {
    data class Ready(val session: AiDocumentSessionStore.Session) : SessionResult
    data class Rejected(val reason: GeminiRefusal) : SessionResult
}
