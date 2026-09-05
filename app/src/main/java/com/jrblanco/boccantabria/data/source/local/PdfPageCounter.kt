package com.jrblanco.boccantabria.data.source.local

/**
 * How many pages the official document has. Nothing else.
 *
 * This is what is left of reading the PDF on the device, and it survives for two reasons that pay
 * for themselves.
 *
 * The first is that [com.jrblanco.boccantabria.data.source.remote.SummaryValidator] discards page
 * citations outside the document, and every citation is a link that opens the viewer at that page —
 * a link to nowhere in a bulletin application is worse than no link at all. The validator exists
 * precisely so as not to believe the page count the model declares, so the real one has to come from
 * somewhere else (010 research.md D-205).
 *
 * The second is that a password-protected document cannot be summarised anywhere, and finding that
 * out here costs one exception and saves both a request and sending a document that is of no use.
 *
 * **Not** a text extractor. If pulling text is ever needed again it will be another interface with
 * another name; this one answers one question and is meant to cost the minimum.
 */
interface PdfPageCounter {

    suspend fun pageCount(localPath: String): PageCountResult
}

sealed interface PageCountResult {

    /** A readable document. [totalPages] is at least 1. */
    data class Success(val totalPages: Int) : PageCountResult

    /** Password-protected. It must not leave the device (FR-004). */
    data object Encrypted : PageCountResult

    /** Anything else: an unreadable file, or the sandboxed process taken away mid-read. */
    data class Failure(val cause: Throwable) : PageCountResult
}
