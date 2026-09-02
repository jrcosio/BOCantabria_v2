package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.domain.model.PdfCorpus

/**
 * Cleans the extracted text without changing what it says.
 *
 * Every character removed is budget freed for real content, but in a legal document cleaning too
 * much changes the meaning. So the line is drawn explicitly (FR-011).
 *
 * **Allowed**: normalise line breaks, collapse repeated spaces, drop repeated blank lines and
 * control characters, join words split by a hyphen at end of line, and remove headers and footers
 * repeated across most pages.
 *
 * **Forbidden**: rewriting, translating, correcting figures or names, dropping paragraphs for
 * looking unimportant, merging columns by guesswork, and expanding abbreviations. What reaches the
 * service has to still be the document.
 *
 * Pure Kotlin, no `android.*`: these rules are the ones worth testing carefully, and they are tested
 * without an emulator.
 */
class PdfTextNormalizer {

    fun normalise(corpus: PdfCorpus): PdfCorpus {
        val cleaned = corpus.pages.map { page -> page.copy(text = cleanUp(page.text)) }
        val repeated = repeatedLines(cleaned)
        val withoutBoilerplate = cleaned.map { page ->
            page.copy(text = dropLines(page.text, repeated))
        }
        return corpus.copy(pages = withoutBoilerplate)
    }

    private fun cleanUp(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .let(::stripControlCharacters)
        .let(::joinHyphenatedWords)
        .lineSequence()
        .map { it.replace(REPEATED_SPACES, " ").trim() }
        .joinToString("\n")
        .replace(REPEATED_BLANK_LINES, "\n\n")
        .trim()

    /**
     * Tabs and newlines survive; the rest of the control range is noise from the extractor.
     *
     * **Lone surrogates go too**, and that is not housekeeping. A PDF with an unusual font can come
     * back with a UTF-16 surrogate that has no pair, which is not a character at all: encoded into
     * the JSON body it produces invalid UTF-8, and the service rejects the whole request. One
     * unpaired code unit in page four is enough to make a document permanently unsummarisable, with
     * nothing on screen to say why.
     */
    private fun stripControlCharacters(text: String): String {
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            when {
                ch == '\n' || ch == '\t' -> out.append(ch)
                ch.isISOControl() -> Unit
                ch.isHighSurrogate() -> {
                    val next = text.getOrNull(index + 1)
                    if (next != null && next.isLowSurrogate()) {
                        out.append(ch).append(next)
                        index++
                    }
                    // A high surrogate with nothing after it is dropped: it is half a character.
                }
                // A low surrogate reached on its own has already lost its partner.
                ch.isLowSurrogate() -> Unit
                else -> out.append(ch)
            }
            index++
        }
        return out.toString()
    }

    /**
     * The delicate one. `sub-\nvención` is one word split by the typesetter; `Decreto-\nLey` is two
     * words that happen to meet at a line end. Lower case on the next line is the signal that tells
     * them apart, and being wrong here would invent a word that is not in the document.
     */
    private fun joinHyphenatedWords(text: String): String =
        HYPHEN_AT_END_OF_LINE.replace(text) { match -> match.groupValues[1] + match.groupValues[2] }

    /**
     * Headers and footers appear on nearly every page and say nothing. A line has to repeat on at
     * least [REPETITION_THRESHOLD] of the pages to count, so a phrase that genuinely appears twice
     * in a short document is left alone.
     */
    private fun repeatedLines(pages: List<PdfCorpus.PdfPageText>): Set<String> {
        if (pages.size < MIN_PAGES_FOR_BOILERPLATE) return emptySet()
        val minimum = Math.ceil(pages.size * REPETITION_THRESHOLD).toInt()
        return pages
            .flatMap { page -> page.text.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= minimum }
            .keys
    }

    private fun dropLines(text: String, unwanted: Set<String>): String {
        if (unwanted.isEmpty()) return text
        return text
            .lineSequence()
            .filterNot { it.trim() in unwanted }
            .joinToString("\n")
            .replace(REPEATED_BLANK_LINES, "\n\n")
            .trim()
    }

    private companion object {
        val REPEATED_SPACES = Regex("[ \\t\\u00A0]{2,}")
        val REPEATED_BLANK_LINES = Regex("\\n{3,}")

        /** A word, a hyphen, the end of the line, and a continuation that starts in lower case. */
        val HYPHEN_AT_END_OF_LINE = Regex("(\\p{L}+)-\\n(\\p{Ll}\\p{L}*)")

        const val REPETITION_THRESHOLD = 0.6
        const val MIN_PAGES_FOR_BOILERPLATE = 3
    }
}
