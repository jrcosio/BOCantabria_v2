package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.data.source.local.PageCountResult
import com.jrblanco.boccantabria.data.source.local.PdfPageCounter
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.DocumentRepository

/**
 * Gets the official document ready to be consulted, and counts its pages on the way.
 *
 * **The four steps two features now share**: fetch the local copy, count the pages, open the session,
 * hand back the reference. It was inline in `AiSummaryRepositoryImpl` while the summary was the only
 * caller; feature 011 added a second one, and copying thirty lines would have copied an invariant with
 * them — **a password-protected document never leaves the device** — which is the kind of thing that
 * holds until somebody fixes one of the two copies (011 research.md D-315).
 *
 * **The order of the first two steps is the invariant.** Counting happens *before* uploading, and that
 * is what keeps a protected document on the phone. It also gives whoever asks the one number they must
 * not take from the model: how many pages the document really has (010 D-205).
 *
 * It **never throws** and it **never translates to a screen error**. Every outcome leaves through a
 * case of [PreparationResult], and each repository maps those to its own vocabulary, because
 * `AiSummaryError` and `AiChatError` say different sentences.
 */
class AiDocumentPreparer(
    private val documents: DocumentRepository,
    private val pages: PdfPageCounter,
    private val sessions: AiDocumentSessionStore,
    private val crashReporter: CrashReporter,
) {

    /**
     * @param onPhase called with [Phase.FETCHING_DOCUMENT] and then [Phase.UPLOADING_DOCUMENT], so the
     *   caller can publish whatever its own screen shows. It is not called again for a document that
     *   is already prepared — the session store answers without uploading.
     */
    suspend fun prepare(
        publication: Publication,
        onPhase: (Phase) -> Unit,
    ): PreparationResult {
        onPhase(Phase.FETCHING_DOCUMENT)
        val document = when (val fetched = documents.ensureLocalCopy(publication)) {
            is AppResult.Success -> fetched.data
            is AppResult.Failure -> return PreparationResult.Unreachable(fetched.error)
        }

        crashReporter.log("prepare: document ready, counting pages")
        val totalPages = when (val counted = pages.pageCount(document.localPath)) {
            is PageCountResult.Success -> counted.totalPages
            PageCountResult.Encrypted -> return PreparationResult.Encrypted
            is PageCountResult.Failure -> return PreparationResult.Broken(counted.cause)
        }
        crashReporter.log("prepare: $totalPages pages")

        onPhase(Phase.UPLOADING_DOCUMENT)
        // Idempotent by key **and** checksum: within a visit the document travels once, so a second
        // AI action costs nothing extra (010 FR-008).
        return when (
            val session = sessions.open(
                externalKey = publication.externalKey,
                pdfSha256 = document.checksum,
                localPath = document.localPath,
                displayName = displayNameFor(publication),
            )
        ) {
            is SessionResult.Ready ->
                PreparationResult.Ready(session.session.document, totalPages, document.checksum)
            is SessionResult.Rejected -> PreparationResult.Refused(session.reason)
        }
    }

    /**
     * What the document is called in the service. Public data of the publication and nothing else: not
     * what the reader saved, not what they read, no identifier of theirs (010 FR-006, 011 FR-024).
     */
    private fun displayNameFor(publication: Publication): String =
        "BOC ${publication.externalKey}"

    enum class Phase { FETCHING_DOCUMENT, UPLOADING_DOCUMENT }
}

sealed interface PreparationResult {

    /**
     * @param pdfSha256 the document's checksum. Handed back because the summary stores it beside
     *   the row to decide later whether what is stored still matches this document; the
     *   conversation stores nothing and ignores it.
     */
    data class Ready(
        val document: UploadedDocument,
        val totalPages: Int,
        val pdfSha256: String,
    ) : PreparationResult

    /** The document could not be fetched. Carries the domain error so each caller can be precise. */
    data class Unreachable(val error: DomainError) : PreparationResult

    /** Password-protected. Detected on the device, so nothing left it. */
    data object Encrypted : PreparationResult

    /** The service would not take it. */
    data class Refused(val reason: GeminiRefusal) : PreparationResult

    /** The document is here and could not be read on the device. */
    data class Broken(val cause: Throwable) : PreparationResult
}
