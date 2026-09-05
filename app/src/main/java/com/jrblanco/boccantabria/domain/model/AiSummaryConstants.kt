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
     * The one line that changes the provider's model, and the reason it is a constant.
     *
     * Feature 009 measured what that is worth: a sustained capacity outage on `gemini-3.5-flash-lite`
     * on 4 September 2026 was survived by pointing this at a sibling. Do **not** build a fallback
     * chain between models: this value is stored beside every summary and is the column that decides
     * what is stale, so it has to stay deterministic (009 CLAUDE.md).
     *
     * Feature 010 adds a requirement this value must satisfy, and it is not negotiable: the model
     * has to accept a **file part** and honour a strict JSON schema at the same time. That cannot be
     * settled from documentation — it is checked against the live service in `quickstart.md` §3 bis
     * before this line is fixed (010 research.md D-213).
     */
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
     *
     * v5 stops sending text at all. The official document itself is attached to the request and the
     * service reads it, so the slot that carried page-marked text is gone and the system message says
     * where the document is. Two of the three constants change with it and [SCHEMA_VERSION] does not,
     * because the schema is untouched — which is enough to make every stored summary stale, by
     * design: one made from text we extracted was not made under the same conditions as one made from
     * the document (010 research.md D-212).
     */
    const val PROMPT_VERSION: String = "boc-summary-es-v5"

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
