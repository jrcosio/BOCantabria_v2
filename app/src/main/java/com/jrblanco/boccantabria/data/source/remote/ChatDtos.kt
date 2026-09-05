package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.Serializable

/**
 * An answer, in the shape our own schema guarantees.
 *
 * **This file describes our format, not the provider's**, which is why nothing here is named after
 * whoever generates it — the same line features 009 and 010 drew between `GeminiDtos` and
 * `SummaryPayloadDtos`.
 *
 * ### The order of these properties is payload, not style
 *
 * In an answer with a strict schema the order of declaration is the order of generation, and anything
 * declared **after** the long field comes back empty if the generation is cut short. That was measured
 * on the summary: with the prose in fourth place it ran to exactly 1024 characters, got cut mid-word,
 * and every list after it arrived empty.
 *
 * Here the stakes are higher than an empty card. With [answer] first, a long answer would leave
 * [scope] blank — **and a blank scope is the defence down** (011 research.md D-310). `answer` goes
 * last. `ChatAnswerSchemaTest` is what stops anyone sorting these alphabetically.
 *
 * Unlike `SummaryPayload`, nothing here is ever stored: the conversation lives in memory and dies with
 * the visit. Renaming a property costs nothing but the schema having to agree.
 */
@Serializable
data class ChatAnswerPayload(
    /**
     * Travels as a string and is translated in the validator. An unknown or missing value becomes
     * `OUT_OF_SCOPE`: when in doubt, our text (D-308).
     */
    val scope: String = "",
    val sources: List<ChatSourceDto> = emptyList(),
    val answer: String = "",
)

@Serializable
data class ChatSourceDto(
    val page: Int = 0,
    val label: String = "",
)
