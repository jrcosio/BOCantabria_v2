package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.AiSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * What travels to and from the summarising service.
 *
 * Data-layer types: they never cross to `ui`. [GroqSummaryPayload] is mapped to [AiSummary] after
 * the validator has corrected it, and the **corrected** payload is what gets stored, so what is on
 * disk always matches the documented schema.
 */
@Serializable
data class GroqMessage(val role: String, val content: String)

@Serializable
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int,
    val stream: Boolean = false,
    @SerialName("reasoning_effort") val reasoningEffort: String = "none",
    @SerialName("response_format") val responseFormat: JsonElement,
)

@Serializable
data class GroqChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<GroqChoice> = emptyList(),
    val usage: GroqUsage? = null,
    @SerialName("system_fingerprint") val systemFingerprint: String? = null,
)

@Serializable
data class GroqChoice(
    val index: Int = 0,
    val message: GroqMessage,
    /**
     * `"stop"` when the model finished on its own, `"length"` when it ran into
     * `max_completion_tokens`. The second means the JSON arrived **cut**, which is indistinguishable
     * from a malformed body once it fails to parse — and telling them apart is the difference between
     * «the service misbehaved» and «our own limit is too low».
     */
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class GroqUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

/**
 * The summary itself, in the shape the strict schema guarantees.
 *
 * Every field is required by the schema, so no default here would ever be used against a real
 * answer. They are present anyway so that a truncated or malformed body fails at validation with a
 * clear reason rather than at deserialisation with a stack trace.
 */
@Serializable
data class GroqSummaryPayload(
    val documentTitle: String = "",
    val documentType: String = "",
    val issuingBody: String = "",
    val plainLanguageSummary: String = "",
    val keyPoints: List<ReferencedTextDto> = emptyList(),
    val affectedParties: List<ReferencedTextDto> = emptyList(),
    val datesAndDeadlines: List<ReferencedDateDto> = emptyList(),
    val amounts: List<ReferencedAmountDto> = emptyList(),
    val requiredActions: List<RequiredActionDto> = emptyList(),
    val appealsOrClaims: List<ReferencedTextDto> = emptyList(),
    val warnings: List<String> = emptyList(),
    val coverage: CoverageDto = CoverageDto(),
)

@Serializable
data class ReferencedTextDto(val text: String = "", val pages: List<Int> = emptyList())

@Serializable
data class ReferencedDateDto(
    val dateOrPeriod: String = "",
    val description: String = "",
    val pages: List<Int> = emptyList(),
)

@Serializable
data class ReferencedAmountDto(
    val amount: String = "",
    val concept: String = "",
    val pages: List<Int> = emptyList(),
)

@Serializable
data class RequiredActionDto(
    val action: String = "",
    val deadline: String = "",
    val pages: List<Int> = emptyList(),
)

@Serializable
data class CoverageDto(
    val pagesAnalyzed: List<Int> = emptyList(),
    val totalPages: Int = 0,
    val complete: Boolean = false,
)

/**
 * Maps a **validated** payload to the domain.
 *
 * Only ever called on the output of `SummaryValidator`, which is what guarantees the domain's own
 * `require` checks cannot fire here: pages are already in range and coverage already tells the
 * truth (FR-022, FR-030).
 */
fun GroqSummaryPayload.toDomain(): AiSummary = AiSummary(
    documentTitle = documentTitle,
    documentType = documentType,
    issuingBody = issuingBody,
    plainLanguageSummary = plainLanguageSummary,
    keyPoints = keyPoints.map { AiSummary.ReferencedText(it.text, it.pages) },
    affectedParties = affectedParties.map { AiSummary.ReferencedText(it.text, it.pages) },
    datesAndDeadlines = datesAndDeadlines.map {
        AiSummary.ReferencedDate(it.dateOrPeriod, it.description, it.pages)
    },
    amounts = amounts.map { AiSummary.ReferencedAmount(it.amount, it.concept, it.pages) },
    requiredActions = requiredActions.map {
        AiSummary.RequiredAction(it.action, it.deadline, it.pages)
    },
    appealsOrClaims = appealsOrClaims.map { AiSummary.ReferencedText(it.text, it.pages) },
    warnings = warnings,
    coverage = AiSummary.SummaryCoverage(
        pagesAnalyzed = coverage.pagesAnalyzed,
        totalPages = coverage.totalPages,
        complete = coverage.complete,
    ),
)
