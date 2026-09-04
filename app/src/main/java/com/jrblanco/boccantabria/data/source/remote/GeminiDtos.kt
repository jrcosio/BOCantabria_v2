package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * What travels to and from the summarising service, in the shape of its Interactions API.
 *
 * **This file describes the provider**, so it carries its name and it is the file to throw away when
 * the provider changes. Our own format lives next door in `SummaryPayloadDtos.kt`
 * (009 research.md D-111).
 *
 * Data-layer types: they never cross to `ui`.
 */
@Serializable
data class GeminiInteractionRequest(
    val model: String,
    @SerialName("system_instruction") val systemInstruction: String,
    val input: List<GeminiInputContent>,
    /**
     * Zero retention.
     *
     * The service's own default is `true`: it keeps the interaction object to enable stateful
     * conversation and background execution, and on a free account it holds it for a day. This
     * feature uses neither — one request per publication, no thread — so retention would be cost
     * without benefit (009 research.md D-107, FR-030).
     */
    val store: Boolean = false,
    @SerialName("generation_config") val generationConfig: GeminiGenerationConfig,
    @SerialName("response_format") val responseFormat: GeminiResponseFormat,
)

@Serializable
data class GeminiInputContent(val type: String, val text: String)

/**
 * No `temperature`, no `top_p`, no `top_k`, and that is not an oversight.
 *
 * The provider's documentation for this generation says outright not to change them, and this model
 * does not accept custom values for temperature, top-K or top-P at all. Sending them would be noise
 * at best and a 400 at worst (009 research.md D-106).
 */
@Serializable
data class GeminiGenerationConfig(
    /**
     * The service's default for this generation is `"medium"`, and thinking is billed.
     *
     * Summarising a bulletin does not need extended reasoning, so leaving the default in place would
     * be paying for tokens nobody ever sees. This is the same lesson `reasoning_effort` cost in
     * feature 007, under a different name. Note the documented caveat: on Flash-Lite `minimal` does
     * not guarantee thinking is off, only that it is as close to off as the model offers.
     */
    @SerialName("thinking_level") val thinkingLevel: String = "minimal",
    @SerialName("max_output_tokens") val maxOutputTokens: Int,
)

@Serializable
data class GeminiResponseFormat(
    val type: String = "text",
    @SerialName("mime_type") val mimeType: String = "application/json",
    /** Goes in verbatim from `SummarySchema`, unmodelled. */
    val schema: JsonElement,
)

@Serializable
data class GeminiInteraction(
    val id: String? = null,
    val model: String? = null,
    /**
     * `completed`, `incomplete`, `budget_exceeded`, `failed`, `cancelled`, `in_progress`…
     *
     * This is what the previous provider made us deduce from `finish_reason` and from counting empty
     * fields. On screen every one of them is the same sentence, on purpose (FR-027); in the log they
     * must not be (009 research.md D-117).
     */
    val status: String? = null,
    val steps: List<GeminiStep> = emptyList(),
    val usage: GeminiUsage? = null,
)

@Serializable
data class GeminiStep(
    /**
     * `model_output` is the one that matters, and it is **not** the first step.
     *
     * Observed against the real service on 4 September 2026: a reasoning step of type `thought`
     * comes before it. The documentation calls that one `model_thoughts`; what actually arrives is
     * `thought`. Which is exactly why the parser looks for `model_output` **by type** and never by
     * position (009 quickstart §3 bis).
     */
    val type: String? = null,
    val content: List<GeminiContentPart> = emptyList(),
)

@Serializable
data class GeminiContentPart(val type: String? = null, val text: String? = null)

@Serializable
data class GeminiUsage(
    @SerialName("total_input_tokens") val totalInputTokens: Int = 0,
    @SerialName("total_output_tokens") val totalOutputTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    /** Should be low or zero. If it grows, `thinking_level` is not being applied. */
    @SerialName("total_thought_tokens") val totalThoughtTokens: Int = 0,
)

@Serializable
data class GeminiErrorEnvelope(val error: GeminiError? = null)

/**
 * `code` is deliberately not deserialised: the HTTP response already carries it, and the
 * documentation is not consistent about whether it arrives here as a number or a string. Modelling a
 * field nobody needs only adds another way to fail at deserialisation.
 */
@Serializable
data class GeminiError(
    val message: String? = null,
    val status: String? = null,
    /** May carry a `RetryInfo` with a `retryDelay`. Read unmodelled (009 research.md D-109). */
    val details: List<JsonObject> = emptyList(),
)
