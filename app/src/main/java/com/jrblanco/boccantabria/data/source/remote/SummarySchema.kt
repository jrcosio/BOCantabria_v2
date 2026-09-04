package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The JSON schema the answer must match.
 *
 * **Ours, not the provider's**, which is why the name says nothing about who generates it. What
 * feature 009 removed was only the OpenAI-style envelope the previous provider required
 * (`{"type":"json_schema","json_schema":{"name":…,"strict":true}}`); the schema object itself went
 * across verbatim. The service supports `$defs`, `$ref` and `additionalProperties`, and honours the
 * declared property order implicitly (009 research.md D-105).
 *
 * `strict: true` had no literal equivalent and is not missed: its job — being able to hide empty
 * sections with confidence and validate references before showing them — is covered by `required`
 * listing all twelve properties, by `additionalProperties: false`, and by `SummaryValidator`, which
 * does not trust the service in any case.
 *
 * A schema also means the answer cannot stream, so the wait shows a phase instead of text appearing
 * (FR-004 of feature 007).
 *
 * **The order of the properties is the order in which the model generates them**, and that is why
 * `plainLanguageSummary` is last rather than fourth. The first real answers proved why it matters:
 * the prose ran to exactly 1024 characters, got cut mid-word, and every list declared after it came
 * back **empty** — a grant call with deadlines and amounts produced a card with nothing in it. With
 * the structured half written first, whatever happens to the prose costs only the prose. `maxLength`
 * on the prose is the other half of the same idea. If anyone sorts these alphabetically the card
 * empties again; `SummarySchemaTest` is what stops them.
 *
 * `maxItems: 10` on the six referenced lists is new in feature 009 and answers a problem the feature
 * created: until now only the first pages that fit were sent, so no card could grow much; with the
 * whole document going in, a thirty-page budget can support dozens of key points (FR-007, D-112).
 * `warnings` is deliberately **uncapped** — it is where the notice about a capped section travels,
 * and bounding it could truncate the very explanation of a truncation.
 *
 * If this shape ever changes, bump `AiSummaryConstants.SCHEMA_VERSION`: what is stored no longer
 * matches, and it should be offered for regeneration rather than silently reinterpreted.
 */
object SummarySchema {

    val value: JsonElement by lazy { Json.parseToJsonElement(RAW) }

    private val RAW = """
    {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "documentTitle": { "type": "string" },
        "documentType": { "type": "string" },
        "issuingBody": { "type": "string" },
        "keyPoints": { "type": "array", "maxItems": 10, "items": { "${'$'}ref": "#/${'$'}defs/referencedText" } },
        "affectedParties": { "type": "array", "maxItems": 10, "items": { "${'$'}ref": "#/${'$'}defs/referencedText" } },
        "datesAndDeadlines": { "type": "array", "maxItems": 10, "items": { "${'$'}ref": "#/${'$'}defs/referencedDate" } },
        "amounts": { "type": "array", "maxItems": 10, "items": { "${'$'}ref": "#/${'$'}defs/referencedAmount" } },
        "requiredActions": { "type": "array", "maxItems": 10, "items": { "${'$'}ref": "#/${'$'}defs/requiredAction" } },
        "appealsOrClaims": { "type": "array", "maxItems": 10, "items": { "${'$'}ref": "#/${'$'}defs/referencedText" } },
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
    """.trimIndent()
}
