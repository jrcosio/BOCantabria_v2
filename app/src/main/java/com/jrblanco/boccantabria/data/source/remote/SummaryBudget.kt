package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.PdfCorpus
import kotlin.math.ceil

/**
 * What fits in one request.
 *
 * Lives in `remote` even though it never touches the network, because what it decides is what fits
 * in **one request**, and that limit belongs to the service (research.md D-007).
 *
 * The ceiling that matters is not the model's 131K context: it is the **quota of 8.000 tokens per
 * minute**, shared across the whole organisation. Hence a target of 7.200 estimated tokens per
 * request, which works out at roughly one summary a minute — what the free plan actually sustains.
 *
 * There is no light tokenizer for this model on Android and it is not worth carrying one. A
 * conservative estimate plus a **hard character ceiling** meets the real goal. The character limit
 * is the guardrail; the estimate decides where to cut.
 */
object SummaryBudget {

    /** The guardrail. */
    const val MAX_DOCUMENT_CHARACTERS: Int = 14_400

    /**
     * The estimate's ceiling for document text alone.
     *
     * Lowered from 5.000 to make room for the answer. Measured on real bulletins: a rich summary of a
     * selection process came back at **1.185 completion tokens against a ceiling of 1.200**. Over that
     * ceiling the JSON arrives cut, fails to parse, and reaches the reader as «no se ha podido
     * construir un resumen fiable» — a service problem that was really our own budget being too tight.
     */
    const val MAX_DOCUMENT_TOKENS: Int = 4_500

    /**
     * Fixed prompt and metadata, plus the document, plus the answer, plus margin.
     *
     * The provider counts **`input + max_completion_tokens`** against the per-minute allowance, not
     * what the answer actually costs — a 429 spelled it out: «Limit 8000, Used 7346, Requested 6475».
     * So the ceiling on the answer is spent whether it is used or not, and the sum has to fit under
     * 8.000 with room to spare: 4.500 of document + ~700 of prompt + 1.800 of answer ≈ 7.000.
     */
    const val TARGET_REQUEST_TOKENS: Int = 7_000

    /**
     * Characters per token. Deliberately low: overestimating tokens costs a page of context,
     * underestimating them costs a 429 that was avoidable.
     */
    private const val CHARACTERS_PER_TOKEN = 3.2

    fun estimateTokens(text: String): Int = ceil(text.length / CHARACTERS_PER_TOKEN).toInt()

    /**
     * Takes whole pages from the first while they fit.
     *
     * Whole pages, because a page reference only means something if the page was sent entire: half
     * a page produces citations that cannot be checked. Cutting inside a page happens only when the
     * **first page alone** does not fit, and then at the last paragraph boundary that does
     * (research.md D-008).
     *
     * Near-empty pages are included rather than skipped. They cost their marker and nothing else,
     * and keeping the run contiguous is what lets coverage mean «pages 1 to N of M» instead of a
     * list with holes in it.
     */
    fun select(corpus: PdfCorpus): SelectedText {
        val builder = StringBuilder()
        val taken = mutableListOf<Int>()

        for (page in corpus.pages) {
            val block = block(page.pageNumber, page.text)
            val candidate = if (builder.isEmpty()) block else "${builder}\n\n$block"

            if (fits(candidate)) {
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
        return SelectedText(
            text = text,
            pages = taken,
            isPartial = taken.size < corpus.totalPages || wasCutInside(corpus, taken, text),
            estimatedTokens = estimateTokens(text),
        )
    }

    private fun fits(candidate: String): Boolean =
        candidate.length <= MAX_DOCUMENT_CHARACTERS && estimateTokens(candidate) <= MAX_DOCUMENT_TOKENS

    private fun block(pageNumber: Int, text: String) = "$PAGE_MARKER_PREFIX$pageNumber]\n$text"

    /**
     * Cuts at the last paragraph break that fits; failing that at the last line break; failing that
     * at the character ceiling, which is the only case where a word can be split in two.
     */
    private fun cutToFit(pageNumber: Int, text: String): String {
        val header = "$PAGE_MARKER_PREFIX$pageNumber]\n"
        val room = minOf(
            MAX_DOCUMENT_CHARACTERS,
            (MAX_DOCUMENT_TOKENS * CHARACTERS_PER_TOKEN).toInt(),
        ) - header.length
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

    const val PAGE_MARKER_PREFIX: String = "[PÁGINA "
}

/**
 * @param pages exactly the pages that were sent. This is what feeds `coverage.pagesAnalyzed`, and
 *   the validator replaces whatever the service claims with it.
 * @param isPartial whether the document went in whole. Consulted **before** the request so the
 *   button can say what will be analysed rather than reporting it afterwards (FR-028).
 */
data class SelectedText(
    val text: String,
    val pages: List<Int>,
    val isPartial: Boolean,
    val estimatedTokens: Int,
)
