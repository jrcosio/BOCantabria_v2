package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.remote.ChatAnswerPayload
import com.jrblanco.boccantabria.data.source.remote.ChatSourceDto
import com.jrblanco.boccantabria.data.source.remote.ChatTurn
import com.jrblanco.boccantabria.data.source.remote.GeminiChatDataSource
import com.jrblanco.boccantabria.data.source.remote.GeminiChatResult
import com.jrblanco.boccantabria.data.source.remote.GeminiRefusal
import com.jrblanco.boccantabria.data.source.remote.SummaryUsage
import com.jrblanco.boccantabria.data.source.remote.UploadedDocument
import kotlinx.coroutines.CompletableDeferred

/** The answering service, faked. */
class FakeGeminiChatDataSource(
    var result: GeminiChatResult = GeminiChatResult.Success(
        payload = ChatAnswerPayload(
            scope = "FROM_DOCUMENT",
            sources = listOf(ChatSourceDto(page = 1, label = "Plazo")),
            answer = "Veinte días hábiles.",
        ),
        usage = SummaryUsage(),
    ),
) : GeminiChatDataSource {

    /** Held open, the request stays in flight — which is what makes an in-flight state observable. */
    var gate: CompletableDeferred<Unit>? = null

    var calls: Int = 0
        private set

    /** Every history this source was handed, so a test can assert what actually travelled. */
    val histories: MutableList<List<ChatTurn>> = mutableListOf()
    val systems: MutableList<String> = mutableListOf()

    fun answerWith(scope: String, answer: String, sources: List<ChatSourceDto> = emptyList()) {
        result = GeminiChatResult.Success(
            payload = ChatAnswerPayload(scope = scope, sources = sources, answer = answer),
            usage = SummaryUsage(),
        )
    }

    fun rejectWith(reason: GeminiRefusal) {
        result = GeminiChatResult.Rejected(reason)
    }

    override suspend fun ask(
        system: String,
        history: List<ChatTurn>,
        document: UploadedDocument,
    ): GeminiChatResult {
        calls++
        systems += system
        histories += history
        gate?.await()
        return result
    }
}
