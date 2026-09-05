package com.jrblanco.boccantabria.data.source.remote

/**
 * The one way out to the answering service.
 *
 * **A sibling of [GeminiSummaryDataSource] and not a second method on it.** The summary's signature
 * returns a `SummaryPayload` and takes one turn; a conversation takes several and returns something
 * else entirely. Folding them together would make each one's test double implement a method it does
 * not care about, and turn a clear interface into a switch (011 research.md D-301).
 *
 * What the two **do** share is almost everything underneath: the HTTP client, the credential provider,
 * the rate-limit coordinator, the wire DTOs, the [GeminiRefusal] vocabulary and the whole discipline of
 * the transport.
 */
interface GeminiChatDataSource {

    /**
     * @param history the conversation so far, already trimmed to the window, ending in a
     *   [ChatTurn.Role.USER] turn — the question being asked.
     * @param document a document already uploaded. The implementation places its reference in the
     *   **first** user turn and nowhere else (D-304).
     */
    suspend fun ask(
        system: String,
        history: List<ChatTurn>,
        document: UploadedDocument,
    ): GeminiChatResult
}

/**
 * One turn of what travels.
 *
 * Ours and not the provider's shape, so the repository can build it from domain messages without
 * either side knowing about the other.
 */
data class ChatTurn(val role: Role, val text: String) {
    enum class Role { USER, MODEL }
}

sealed interface GeminiChatResult {

    data class Success(
        val payload: ChatAnswerPayload,
        val usage: SummaryUsage,
    ) : GeminiChatResult

    data class Rejected(val reason: GeminiRefusal) : GeminiChatResult
}
