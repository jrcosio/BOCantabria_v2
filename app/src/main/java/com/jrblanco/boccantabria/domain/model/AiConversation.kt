package com.jrblanco.boccantabria.domain.model

/**
 * What has been said about **one** publication during **one** visit.
 *
 * At most one is alive in the process, and that is a structural decision rather than a limit that
 * happened: «opening another publication takes the previous conversation away» is a checkable claim
 * about a single slot, and over a map of unknown size it is only an intention. It is the same argument
 * that shapes the document session (011 research.md D-312, FR-011).
 *
 * Nothing here is persisted. Not to the database, not to preferences, not to saved state: it dies with
 * the visit, and with it goes the uploaded document.
 */
data class AiConversation(
    val externalKey: String,
    val messages: List<AiChatMessage> = emptyList(),
    val status: AiChatStatus = AiChatStatus.Idle,
) {
    val isEmpty: Boolean get() = messages.isEmpty()

    /** The last question asked, which is what «Retry» resends. */
    val lastQuestion: AiChatMessage.Question?
        get() = messages.filterIsInstance<AiChatMessage.Question>().lastOrNull()
}
