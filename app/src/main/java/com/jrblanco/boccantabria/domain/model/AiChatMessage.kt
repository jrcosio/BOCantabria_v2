package com.jrblanco.boccantabria.domain.model

/**
 * One turn of the conversation about a publication's document.
 *
 * [id] exists for two reasons and both matter: a Compose list needs a stable key, and retrying has to
 * know **which** question it is resending (FR-033). It is minted in the data layer; the domain only
 * insists that it is there.
 *
 * [atEpochMillis] comes from the injected `TimeProvider` and never from the system clock. That is what
 * makes the tests deterministic.
 */
sealed interface AiChatMessage {

    val id: String
    val atEpochMillis: Long

    data class Question(
        override val id: String,
        override val atEpochMillis: Long,
        val text: String,
    ) : AiChatMessage {
        init {
            require(text.isNotBlank()) { "a question with nothing in it is not a question" }
        }
    }

    /**
     * @param text **already resolved**. When [scope] is [AiAnswerScope.OUT_OF_SCOPE] there is nothing
     *   of the model's in here: the substitution happens in the data layer precisely so that no future
     *   screen can skip it by accident (FR-021, 011 contracts §3.3).
     */
    data class Answer(
        override val id: String,
        override val atEpochMillis: Long,
        val text: String,
        val scope: AiAnswerScope,
        val sources: List<AiAnswerSource> = emptyList(),
    ) : AiChatMessage {
        init {
            require(text.isNotBlank()) { "an empty bubble is not an answer" }
        }
    }
}
