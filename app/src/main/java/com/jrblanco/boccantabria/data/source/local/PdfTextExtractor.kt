package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.domain.model.PdfCorpus

/**
 * Pulls the text out of a local document, page by page.
 *
 * An interface, and that is the whole point of it. `androidx.pdf` is in beta and its text extraction
 * cannot be queried for support beforehand — `PdfFeature.TEXT_EXTRACTION` exists but
 * `isFeatureSupported` is restricted to the library group. If it ever came back empty on real
 * devices, the way out is a different implementation behind this seam and nothing else changes
 * (research.md D-001).
 */
interface PdfTextExtractor {

    /**
     * @param localPath where the cached document is.
     * @param externalKey the publication it belongs to.
     * @param pdfSha256 the checksum already computed when it was downloaded. Not recomputed here.
     */
    suspend fun extract(
        localPath: String,
        externalKey: String,
        pdfSha256: String,
    ): PdfExtractionResult
}

/**
 * Nothing here throws: every outcome is a case.
 *
 * [NoExtractableText] is decided by **counting**, not by waiting for an exception. A scanned PDF
 * does not fail to extract; it returns empty strings and a few characters of noise. Asking the
 * service about that would spend quota and get invention back (FR-012, research.md D-004).
 */
sealed interface PdfExtractionResult {

    data class Success(val corpus: PdfCorpus) : PdfExtractionResult

    /** Scanned, empty, or otherwise without a text layer worth sending. */
    data object NoExtractableText : PdfExtractionResult

    data object EncryptedPdf : PdfExtractionResult

    data class Failure(val cause: Throwable) : PdfExtractionResult
}
