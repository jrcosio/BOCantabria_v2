package com.jrblanco.boccantabria.domain.model

/**
 * What a stored summary was made with.
 *
 * An `object` and not a `class` on purpose, like `core/util/SearchText`: Konsist's eighth rule
 * demands a test file for every top-level domain **class**, and three constants have no behaviour of
 * their own to assert.
 *
 * These three values are the provenance of a summary. They are stored beside it, and when any of
 * them stops matching, what is stored is stale rather than absent: it is still shown, marked, with
 * the option to make it again (FR-035).
 *
 * [MODEL_ID] names a model the provider lists as **preview, not production**. Preview models get
 * retired. Keeping the identifier here, and the service behind an interface, is what turns that
 * retirement into one changed line plus a new implementation (research.md D-010).
 */
object AiSummaryConstants {

    const val MODEL_ID: String = "qwen/qwen3.8-27b"

    /**
     * Bump when the wording of the prompt changes: what was generated before no longer matches.
     *
     * v2 shortened the prose target to 90–150 words and told the model that a partial reading does
     * not excuse leaving the structured fields empty. v3 fixed what v2 got wrong: told that way, the
     * model filled the structured fields and left the **summary** blank instead. It is now stated as
     * always mandatory. Both versions came from answers measured on a real phone.
     */
    const val PROMPT_VERSION: String = "boc-summary-es-v3"

    /**
     * Bump when the response schema changes shape.
     *
     * v2 moved `plainLanguageSummary` to the **end** of the properties — that order is the order the
     * model generates in — and bounded it with `maxLength`.
     */
    const val SCHEMA_VERSION: String = "boc-summary-schema-v2"
}
