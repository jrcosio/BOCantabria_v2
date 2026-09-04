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
 * the option to make it again (FR-009, FR-035 of feature 007).
 *
 * Keeping the identifier here, and the service behind an interface, is what made feature 009 a
 * changed line plus a new implementation rather than a refactor. All three changed at once when the
 * provider did, so every summary stored by the previous version is stale by design — shown, marked,
 * never deleted (009 research.md D-101, D-114).
 */
object AiSummaryConstants {

    /**
     * Published on 21 July 2026, generally available rather than preview, and announced by the
     * provider as optimised for document processing.
     *
     * 1 048 576 tokens of input and 65 536 of output, which is what removed the scarcity the whole
     * feature used to be built around: any bulletin publication now goes in whole
     * (009 research.md D-101).
     */
    //const val MODEL_ID: String = "gemini-3.5-flash-lite"
    const val MODEL_ID: String = "gemini-3.1-flash-lite"

    /**
     * Bump when the wording of the prompt changes: what was generated before no longer matches.
     *
     * v2 shortened the prose target to 90–150 words and told the model that a partial reading does
     * not excuse leaving the structured fields empty. v3 fixed what v2 got wrong: told that way, the
     * model filled the structured fields and left the **summary** blank instead. Both came from
     * answers measured on a real phone. v4 sends the whole document instead of the first pages that
     * fit, so a partial reading became the exception rather than the norm, and asks the model to pick
     * the most relevant items when a section would run past ten (009 research.md D-104, D-112).
     */
    const val PROMPT_VERSION: String = "boc-summary-es-v4"

    /**
     * Bump when the response schema changes shape.
     *
     * v2 moved `plainLanguageSummary` to the **end** of the properties — that order is the order the
     * model generates in — and bounded it with `maxLength`. v3 keeps that order untouched, drops the
     * OpenAI-style envelope the previous provider required, and caps the six referenced lists at ten
     * items each. `warnings` is deliberately left uncapped: it is where the notice about a capped
     * section travels (009 research.md D-105, D-112).
     */
    const val SCHEMA_VERSION: String = "boc-summary-schema-v3"
}
