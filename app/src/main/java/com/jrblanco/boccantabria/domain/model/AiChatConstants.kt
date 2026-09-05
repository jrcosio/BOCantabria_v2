package com.jrblanco.boccantabria.domain.model

/**
 * The three numbers the conversation is bounded by.
 *
 * An `object` and not a `class` on purpose, like [AiSummaryConstants] and `core/util/SearchText`:
 * Konsist's eighth rule demands a test file for every top-level domain **class**, and three constants
 * have no behaviour of their own to assert.
 *
 * **It deliberately carries no `MODEL_ID`.** The chat asks the same model as the summary and reads it
 * from [AiSummaryConstants], so that the escape hatch feature 009 measured — repointing one line
 * during a capacity outage — stays one line. Two constants that must hold the same value are two
 * constants that will one day hold different ones, and nobody will notice until half the application
 * stops answering (011 research.md D-305).
 */
object AiChatConstants {

    /**
     * A question to a bulletin fits in far less than this. The limit is not about cost: it exists so
     * that accidentally pasting half a publication does not become a request. It is shown **before**
     * sending, not discovered after (FR-007).
     */
    const val MAX_QUESTION_LENGTH: Int = 500

    /** From here on the counter appears. Before it, it would only be noise. */
    const val COUNTER_VISIBLE_FROM: Int = 400

    /**
     * How many messages travel back with each question.
     *
     * Not to save anything — the document dominates the input and six exchanges of short text do not
     * register — but because a list with no bound is a request with no bound. Putting the limit in is
     * cheap; finding out where it was is not (D-303).
     */
    const val MAX_HISTORY_MESSAGES: Int = 12
}
