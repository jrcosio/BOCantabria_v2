package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.PdfCorpus

/**
 * The document text as it goes out, and the one ceiling left.
 *
 * Lives in `remote` even though it never touches the network, because what it decides is what fits
 * in **one request**, and that limit belongs to the service.
 *
 * ### What feature 009 took out of here, and why
 *
 * This used to be `SummaryBudget`, and it used to ration. The previous provider allowed 8 000 tokens
 * a *minute*, shared across the whole organisation, so most documents were read **in part**: the
 * first pages that fit went out and the screen said so twice. Out with it went a token estimate at
 * 3.2 characters per token that was calibrated by eye for a different tokenizer and was never
 * measured (009 research.md D-104).
 *
 * ### What is left, and why it is not rationing
 *
 * One hard character ceiling. [MAX_CHARACTERS] works out at roughly **109 000 tokens**, about 10 % of
 * the model's input window — and that figure is **measured, not estimated**: against the real service
 * on 4 September 2026, 6 036 characters of Spanish bulletin text were charged as 1 376 tokens, so 4.39
 * characters per token. The previous provider's budget assumed 3.2 and never checked. At a bulletin
 * page of some 2 500 characters the ceiling covers about a hundred and ninety pages, against the
 * hundred that SC-001 calls the ordinary envelope, so **in normal use it is never reached**. It is what
 * stops a pathological thousand-page publication from taking the whole request down.
 *
 * And it buys something concrete: it keeps the partial-coverage path **alive and tested** — the
 * `Generating` partial state, both `plurals` in `strings.xml`, and three instrumented tests — instead
 * of deleting all of it (FR-004, FR-005).
 */
object DocumentText {

    /**
     * The guardrail.
     *
     * Pending confirmation against the provider's own console: the tokens-per-minute figure this is
     * sized against is not published anywhere. See `quickstart.md` §0 bis — if the real allowance is
     * below about **110 000 tokens a minute**, this has to come down, because a document sitting at the
     * ceiling would otherwise be unsummarisable for ever and no test would catch it.
     */
    const val MAX_CHARACTERS: Int = 480_000

    const val PAGE_MARKER_PREFIX: String = "[PÁGINA "

    /**
     * Renders the document with a page marker per page, taking whole pages from the first while they
     * fit.
     *
     * Whole pages, because a page reference only means something if the page was sent entire: half a
     * page produces citations that cannot be checked. Cutting inside a page happens only when the
     * **first page alone** does not fit, and then at the last paragraph boundary that does.
     *
     * Near-empty pages are included rather than skipped. They cost their marker and nothing else, and
     * keeping the run contiguous is what lets coverage mean «pages 1 to N of M» instead of a list
     * with holes in it.
     */
    fun render(corpus: PdfCorpus): RenderedDocument {
        val builder = StringBuilder()
        val taken = mutableListOf<Int>()

        for (page in corpus.pages) {
            val block = block(page.pageNumber, page.text)
            val candidate = if (builder.isEmpty()) block else "${builder}\n\n$block"

            if (candidate.length <= MAX_CHARACTERS) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(block)
                taken += page.pageNumber
                continue
            }

            // The first page on its own does not fit: cut inside it, at a natural boundary.
            if (taken.isEmpty()) {
                val trimmed = cutToFit(page.pageNumber, page.text)
                builder.append(trimmed)
                taken += page.pageNumber
            }
            break
        }

        val text = builder.toString()
        return RenderedDocument(
            text = text,
            pages = taken,
            isPartial = taken.size < corpus.totalPages || wasCutInside(corpus, taken, text),
        )
    }

    private fun block(pageNumber: Int, text: String) = "$PAGE_MARKER_PREFIX$pageNumber]\n$text"

    /**
     * Cuts at the last paragraph break that fits; failing that at the last line break; failing that
     * at the character ceiling, which is the only case where a word can be split in two.
     */
    private fun cutToFit(pageNumber: Int, text: String): String {
        val header = "$PAGE_MARKER_PREFIX$pageNumber]\n"
        val room = MAX_CHARACTERS - header.length
        if (room <= 0) return header

        val window = text.take(room)
        val boundary = window.lastIndexOf("\n\n").takeIf { it > room / 2 }
            ?: window.lastIndexOf('\n').takeIf { it > room / 2 }
            ?: room
        return header + window.take(boundary).trimEnd()
    }

    /** A page that went in shorter than it is was cut inside, and that is partial too. */
    private fun wasCutInside(corpus: PdfCorpus, taken: List<Int>, text: String): Boolean =
        taken.size == 1 &&
            corpus.pages.firstOrNull()?.text?.let { whole -> !text.endsWith(whole.trimEnd()) } == true
}

/**
 * @param pages exactly the pages that were sent. This is what feeds `coverage.pagesAnalyzed`, and
 *   the validator replaces whatever the service claims with it.
 * @param isPartial whether the document went in whole. Consulted **before** the request so the
 *   screen can say what will be analysed rather than reporting it afterwards (FR-005). Since feature
 *   009 this is `false` for every ordinary publication.
 */
data class RenderedDocument(
    val text: String,
    val pages: List<Int>,
    val isPartial: Boolean,
)
