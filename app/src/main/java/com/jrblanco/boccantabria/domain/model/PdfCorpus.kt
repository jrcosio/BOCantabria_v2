package com.jrblanco.boccantabria.domain.model

/**
 * The text of an official document, page by page.
 *
 * Lives only while a summary is being made. It is deliberately **not** stored: regenerating extracts
 * it again, which is local and costs no quota, and keeping the text of hundreds of documents would
 * grow without a ceiling and would need the project's first delete statement to bound it
 * (research.md D-021).
 *
 * Pages are numbered **from 1**. The PDF library numbers from 0 and the conversion happens once, in
 * the extractor, so that nothing above ever has to remember which convention it is looking at.
 */
data class PdfCorpus(
    val externalKey: String,
    val pdfSha256: String,
    val totalPages: Int,
    val pages: List<PdfPageText>,
) {
    init {
        require(externalKey.isNotBlank()) { "externalKey must not be blank" }
        require(pdfSha256.isNotBlank()) { "pdfSha256 must not be blank" }
        require(totalPages > 0) { "a document with no pages is not a document, was: $totalPages" }
        require(pages.isNotEmpty()) { "pages must not be empty" }
        require(pages.size == totalPages) {
            "pages must cover the document: ${pages.size} of $totalPages"
        }
        require(pages.map(PdfPageText::pageNumber) == (1..totalPages).toList()) {
            "pages must be consecutive and start at 1"
        }
    }

    /**
     * The pages that hold something worth sending. This is the denominator of coverage: a summary is
     * complete when it analysed all of these, not all of [totalPages] — a bulletin can legitimately
     * carry a page that is only a stamp or a table image.
     */
    val pagesWithText: List<PdfPageText> get() = pages.filter(PdfPageText::hasUsableText)

    /**
     * Whether the document is worth sending at all.
     *
     * Decided by counting, not by waiting for an exception: a scanned PDF does not fail to extract,
     * it returns empty strings and four characters of noise. Asking the service about that would
     * spend quota and get invention back (FR-012, research.md D-004).
     */
    val hasUsableText: Boolean
        get() {
            val usable = pages.sumOf(PdfPageText::usableCharacters)
            val blank = pages.count { !it.hasUsableText }
            return usable >= totalPages * MIN_USABLE_CHARACTERS_PER_PAGE && blank <= totalPages / 2
        }

    /**
     * @param pageNumber 1-based, as everyone reading a document expects.
     */
    data class PdfPageText(val pageNumber: Int, val text: String) {
        init { require(pageNumber >= 1) { "pages are numbered from 1 outwards, was: $pageNumber" } }

        val usableCharacters: Int get() = text.count(Char::isLetterOrDigit)

        val hasUsableText: Boolean get() = usableCharacters >= MIN_USABLE_CHARACTERS
    }

    companion object {
        /** Below this a page is noise rather than content. */
        const val MIN_USABLE_CHARACTERS: Int = 20

        /** Averaged over the whole document, below this there is no text layer worth sending. */
        const val MIN_USABLE_CHARACTERS_PER_PAGE: Int = 40
    }
}
