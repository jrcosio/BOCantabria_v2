package com.jrblanco.boccantabria.ui.ask

import com.jrblanco.boccantabria.domain.model.AiChatConstants
import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiChatStatus
import com.jrblanco.boccantabria.domain.model.Publication

/**
 * What the conversation screen shows.
 *
 * [canSend] is where three requirements meet, and having them in one derived property is what makes
 * them checkable together: nothing empty goes out (FR-006), nothing goes out while something else is
 * in flight (FR-005, and therefore FR-050), and nothing goes out at all without a credential (FR-036).
 */
data class AskUiState(
    val publication: Publication? = null,
    val isSaved: Boolean = false,
    val messages: List<AiChatMessage> = emptyList(),
    val status: AiChatStatus = AiChatStatus.Idle,
    val draft: String = "",
    val noticePending: Boolean = false,
    val noticeAccepted: Boolean = false,
    val isServiceConfigured: Boolean = true,
    val saveFailed: Boolean = false,
) {
    val isBusy: Boolean
        get() = status is AiChatStatus.Preparing || status == AiChatStatus.Thinking

    /**
     * `publication != null` is not defensive padding: `ask` needs a `Publication` and the screen is
     * opened with a key, so there is a moment before it is read from what is stored.
     */
    val canSend: Boolean
        get() = publication != null &&
            isServiceConfigured &&
            draft.isNotBlank() &&
            draft.length <= AiChatConstants.MAX_QUESTION_LENGTH &&
            !isBusy

    val showSuggestions: Boolean get() = messages.isEmpty() && isServiceConfigured

    /** Before this the counter would be noise; from here on the limit has to be visible (FR-007). */
    val showCounter: Boolean get() = draft.length >= AiChatConstants.COUNTER_VISIBLE_FROM

    val isOverLimit: Boolean get() = draft.length > AiChatConstants.MAX_QUESTION_LENGTH
}
