package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The JSON schema an answer must match.
 *
 * **Ours, not the provider's.** It travels verbatim in `responseJsonSchema`, the same way
 * `SummarySchema` does, and for the same reason: the service honours the declared property order
 * implicitly, so rewriting the object would put that bomb back on the table for nothing.
 *
 * ### Why `scope` is first and `answer` is last
 *
 * The order of the properties is the order the model generates in. Whatever is declared after the long
 * field is what disappears when a generation gets cut short — measured on the summary, where a grant
 * call with deadlines and amounts produced a card with nothing in it.
 *
 * Applied here, that means `scope` **must** come first. It is the field that says whether the answer
 * belongs to the document at all, and a blank scope is not a cosmetic loss: it is the injection
 * defence falling over silently (011 research.md D-310). `sources` follows, and `answer` — bounded
 * with `maxLength` — goes last, where losing part of it costs only part of it.
 *
 * `ChatAnswerSchemaTest` asserts that order. If anyone sorts these alphabetically, `answer` climbs to
 * the front and the defence goes with it.
 *
 * A schema also means the answer cannot stream, which is why the wait shows an indicator rather than
 * text appearing (D-306).
 */
object ChatAnswerSchema {

    val value: JsonElement by lazy { Json.parseToJsonElement(RAW) }

    /** Bounded because an answer to a bulletin is short. Reaching it means the prompt is wrong. */
    const val MAX_ANSWER_LENGTH: Int = 1_200

    const val MAX_SOURCES: Int = 6

    private val RAW = """
    {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "scope": { "type": "string", "enum": ["FROM_DOCUMENT", "NOT_IN_DOCUMENT", "OUT_OF_SCOPE"] },
        "sources": {
          "type": "array",
          "maxItems": $MAX_SOURCES,
          "items": {
            "type": "object",
            "additionalProperties": false,
            "properties": {
              "page": { "type": "integer" },
              "label": { "type": "string", "maxLength": 60 }
            },
            "required": ["page", "label"]
          }
        },
        "answer": { "type": "string", "maxLength": $MAX_ANSWER_LENGTH }
      },
      "required": ["scope", "sources", "answer"]
    }
    """.trimIndent()
}
