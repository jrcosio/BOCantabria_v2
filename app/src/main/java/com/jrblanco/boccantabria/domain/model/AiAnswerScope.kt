package com.jrblanco.boccantabria.domain.model

/**
 * What an answer says about itself: where it came from.
 *
 * **This is the only layer of the injection defence that can be checked without crossing the frontier
 * with the service**, which is why it is a domain type and not a detail of the transport. The other
 * four layers — the system instruction, the delimited question, the document declared as data, and the
 * cheap hygiene — live on the far side, and every test in this house doubles that frontier
 * (011 research.md D-307).
 *
 * What it buys, precisely: an answer the model itself labels as outside the document **cannot reach
 * the screen wearing the document's authority**, because the repository swaps its text for ours. What
 * it does not buy: protection from a model that writes a poem and labels it [FROM_DOCUMENT]. It is a
 * barrier, not a proof, and the specification says so in those words.
 */
enum class AiAnswerScope {

    /** The answer comes from the attached document. Its text and its sources are shown. */
    FROM_DOCUMENT,

    /**
     * A fair question the document does not answer.
     *
     * **The model's own wording is shown here**, and that is deliberate: «this announcement sets no
     * deadline for objections» is better information than any generic sentence of ours. It is the
     * opposite of [OUT_OF_SCOPE], not a milder version of it (D-308).
     */
    NOT_IN_DOCUMENT,

    /**
     * The request had nothing to do with the document.
     *
     * The one case where **our** text is shown and not a single character of the service's answer
     * (FR-021, SC-004). An unknown or missing value is treated as this one: when in doubt, our text.
     */
    OUT_OF_SCOPE,
}
