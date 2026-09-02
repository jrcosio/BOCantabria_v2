package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The strict JSON schema the answer must match.
 *
 * Strict mode is what makes it safe to hide empty sections and to validate references before showing
 * them: with free prose there would be a parser to write, and a prose parser fails exactly on the
 * strange documents. The provider's rules for `strict: true` are not optional — **every** property
 * in `required`, **every** object with `additionalProperties: false` — and reusable subschemas go
 * through `$defs`/`$ref` (research.md D-011).
 *
 * The price is that the answer cannot stream: structured outputs and streaming are not compatible
 * today. That is why the wait shows a phase instead of text appearing (FR-004).
 *
 * **The order of the properties is the order in which the model generates them**, and that is why
 * `plainLanguageSummary` is last rather than fourth. The first real answers proved why it matters: the
 * prose ran to exactly 1024 characters, got cut mid-word, and every list declared after it came back
 * **empty** — a grant call with deadlines and amounts produced a card with nothing in it. With the
 * structured half written first, whatever happens to the prose costs only the prose.
 *
 * `maxLength` on the prose is the other half of the same idea: bounded, the model plans a summary that
 * fits instead of writing until something cuts it.
 *
 * If this shape ever changes, bump `AiSummaryConstants.SCHEMA_VERSION`: what is stored no longer
 * matches, and it should be offered for regeneration rather than silently reinterpreted.
 */
object GroqSummarySchema {

    val value: JsonElement by lazy { Json.parseToJsonElement(RAW) }

    private val RAW = """
    {
      "type": "json_schema",
      "json_schema": {
        "name": "boc_ai_summary",
        "strict": true,
        "schema": {
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "documentTitle": { "type": "string" },
            "documentType": { "type": "string" },
            "issuingBody": { "type": "string" },
            "keyPoints": { "type": "array", "items": { "${'$'}ref": "#/${'$'}defs/referencedText" } },
            "affectedParties": { "type": "array", "items": { "${'$'}ref": "#/${'$'}defs/referencedText" } },
            "datesAndDeadlines": { "type": "array", "items": { "${'$'}ref": "#/${'$'}defs/referencedDate" } },
            "amounts": { "type": "array", "items": { "${'$'}ref": "#/${'$'}defs/referencedAmount" } },
            "requiredActions": { "type": "array", "items": { "${'$'}ref": "#/${'$'}defs/requiredAction" } },
            "appealsOrClaims": { "type": "array", "items": { "${'$'}ref": "#/${'$'}defs/referencedText" } },
            "warnings": { "type": "array", "items": { "type": "string" } },
            "coverage": { "${'$'}ref": "#/${'$'}defs/coverage" },
            "plainLanguageSummary": { "type": "string", "maxLength": 900 }
          },
          "required": [
            "documentTitle", "documentType", "issuingBody",
            "keyPoints", "affectedParties", "datesAndDeadlines", "amounts",
            "requiredActions", "appealsOrClaims", "warnings", "coverage",
            "plainLanguageSummary"
          ],
          "${'$'}defs": {
            "referencedText": {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "text": { "type": "string" },
                "pages": { "type": "array", "items": { "type": "integer" } }
              },
              "required": ["text", "pages"]
            },
            "referencedDate": {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "dateOrPeriod": { "type": "string" },
                "description": { "type": "string" },
                "pages": { "type": "array", "items": { "type": "integer" } }
              },
              "required": ["dateOrPeriod", "description", "pages"]
            },
            "referencedAmount": {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "amount": { "type": "string" },
                "concept": { "type": "string" },
                "pages": { "type": "array", "items": { "type": "integer" } }
              },
              "required": ["amount", "concept", "pages"]
            },
            "requiredAction": {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "action": { "type": "string" },
                "deadline": { "type": "string" },
                "pages": { "type": "array", "items": { "type": "integer" } }
              },
              "required": ["action", "deadline", "pages"]
            },
            "coverage": {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "pagesAnalyzed": { "type": "array", "items": { "type": "integer" } },
                "totalPages": { "type": "integer" },
                "complete": { "type": "boolean" }
              },
              "required": ["pagesAnalyzed", "totalPages", "complete"]
            }
          }
        }
      }
    }
    """.trimIndent()
}
