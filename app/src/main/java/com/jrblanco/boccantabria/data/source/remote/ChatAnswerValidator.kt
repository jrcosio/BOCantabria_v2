package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.AiAnswerScope
import com.jrblanco.boccantabria.domain.model.AiAnswerSource

/**
 * What comes back from the service, checked before anybody sees it.
 *
 * Three rules and not one of them trusts the answer:
 *
 * 1. **A citation to a page the document does not have is dropped.** The total comes from the device —
 *    which is precisely why feature 010 kept the page counter when it deleted the text extractor — and
 *    a link that leads nowhere is worse than no link (FR-014, SC-005).
 * 2. **Text that stops mid-sentence is trimmed back** to the last sentence that finished. Prose that
 *    ends «…los requisitos de nacionalidad,» reads as broken; it happened on three of the first four
 *    real summaries.
 * 3. **A blank body is refused outright**, and becomes `InvalidResponse` upstairs. An empty bubble is
 *    not an answer (FR-023).
 *
 * **An unknown or missing scope becomes [AiAnswerScope.OUT_OF_SCOPE]**, and that direction is
 * deliberate: when the one field the defence rests on is unreadable, what gets shown is our text
 * (011 research.md D-308).
 *
 * **The substitution of the out-of-scope text does not happen here.** This class returns the scope; the
 * repository decides what text the answer carries, because the replacement is a string resource and
 * `data` does not read `strings.xml` (contracts §3.3).
 */
class ChatAnswerValidator {

    /**
     * @param totalPages how many pages the document really has, counted on the device.
     * @return `null` when there is nothing to show.
     */
    fun validate(raw: ChatAnswerPayload, totalPages: Int): ValidatedAnswer? {
        val scope = raw.scope.toScope()

        // Out of scope, the model's text is thrown away whole, so there is nothing to validate about
        // it: the repository supplies the text and no citation of an off-topic answer means anything.
        if (scope == AiAnswerScope.OUT_OF_SCOPE) {
            return ValidatedAnswer(
                scope = scope,
                text = "",
                sources = emptyList(),
                droppedCitations = raw.sources.size,
            )
        }

        val text = trimToLastCompleteSentence(raw.answer.trim())
        if (text.isBlank()) return null

        val kept = raw.sources
            .filter { it.page in 1..totalPages && it.label.isNotBlank() }
            .distinctBy { it.page }
            .sortedBy { it.page }
            .map { AiAnswerSource(page = it.page, label = it.label.trim()) }

        return ValidatedAnswer(
            scope = scope,
            text = text,
            sources = kept,
            droppedCitations = raw.sources.size - kept.size,
        )
    }

    private fun String.toScope(): AiAnswerScope =
        AiAnswerScope.entries.firstOrNull { it.name == this.trim() } ?: AiAnswerScope.OUT_OF_SCOPE

    /**
     * Text that stops mid-word reads as broken. It is trimmed back to the last sentence that finished.
     *
     * If nothing finished, what arrived is kept: losing the whole answer would be worse than showing
     * it short.
     */
    private fun trimToLastCompleteSentence(text: String): String {
        if (text.isEmpty() || text.last() in SENTENCE_ENDINGS) return text

        val lastEnding = text.indexOfLast { it in SENTENCE_ENDINGS }
        if (lastEnding < 0) return text
        return text.take(lastEnding + 1).trimEnd()
    }

    private companion object {
        val SENTENCE_ENDINGS = charArrayOf('.', '!', '?', '…')
    }
}

/**
 * @param droppedCitations how many citations were thrown away. **Never shown**: it is logged, and it
 *   is the way to find out on a real phone whether the model is inventing pages (011 research.md D-328).
 * @param text empty when [scope] is [AiAnswerScope.OUT_OF_SCOPE]. The repository fills it with ours.
 */
data class ValidatedAnswer(
    val scope: AiAnswerScope,
    val text: String,
    val sources: List<AiAnswerSource>,
    val droppedCitations: Int,
)
