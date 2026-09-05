package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiChatStatus
import com.jrblanco.boccantabria.domain.model.AiConversation
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.AiChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A conversation the test drives. */
class FakeAiChatRepository(
    initial: AiConversation = AiConversation(externalKey = "boc:1"),
    available: Boolean = true,
) : AiChatRepository {

    private val conversation = MutableStateFlow(initial)
    private val availability = MutableStateFlow(available)

    /** Every question this repository was asked, in order, with the key it came for. */
    val asked: MutableList<Pair<String, String>> = mutableListOf()
    val retries: MutableList<String> = mutableListOf()
    val discarded: MutableList<String> = mutableListOf()

    fun emit(next: AiConversation) {
        conversation.value = next
    }

    fun emit(status: AiChatStatus) {
        conversation.value = conversation.value.copy(status = status)
    }

    fun emit(messages: List<AiChatMessage>) {
        conversation.value = conversation.value.copy(messages = messages)
    }

    fun setAvailable(value: Boolean) {
        availability.value = value
    }

    override fun observeConversation(externalKey: String): Flow<AiConversation> = conversation

    override fun observeAvailability(): Flow<Boolean> = availability

    override fun ask(publication: Publication, question: String) {
        asked += publication.externalKey to question
    }

    override fun retry(publication: Publication) {
        retries += publication.externalKey
    }

    override fun discard(externalKey: String) {
        discarded += externalKey
    }
}
