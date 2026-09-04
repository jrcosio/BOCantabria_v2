package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.AiSummary
import kotlinx.serialization.Serializable

/**
 * The summary itself, in the shape our own schema guarantees.
 *
 * **This file describes our format, not the provider's**, and that is why nothing here is named
 * after whoever generates it. It was split out of the previous provider's DTO file in feature 009
 * because the two halves have very different lives: the wire types die with the provider, and these
 * do not (009 research.md D-111).
 *
 * ### The untouchable rule of this file
 *
 * **Not one property name may change.** [SummaryPayload] is what gets serialised into the
 * `summary_json` column of `ai_summaries` and read back in `AiSummaryEntity.decode()`. kotlinx
 * serialises by property name, so renaming the *class* is harmless and renaming a *field* would make
 * every row written by an earlier version unreadable. There is a regression test for exactly that.
 *
 * The field order here does **not** match the schema's, and that is deliberate too:
 * `plainLanguageSummary` is fourth here because it is the first thing read on screen, and twelfth in
 * the schema because it is the last thing worth generating. Two orders, two reasons, neither should
 * be aligned with the other.
 *
 * Every field has a default although the schema marks all of them required. They are here so that a
 * truncated or unexpected body fails at **validation**, with a reason that can be logged, rather
 * than at deserialisation with a stack trace.
 */
@Serializable
data class SummaryPayload(
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
 * truth (FR-017, FR-006).
 */
fun SummaryPayload.toDomain(): AiSummary = AiSummary(
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
