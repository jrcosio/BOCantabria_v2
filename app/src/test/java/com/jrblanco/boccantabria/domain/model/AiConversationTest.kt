package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConversationTest {

    private fun question(id: String, text: String) =
        AiChatMessage.Question(id = id, atEpochMillis = 0, text = text)

    private fun answer(id: String) = AiChatMessage.Answer(
        id = id,
        atEpochMillis = 0,
        text = "Lo que sea",
        scope = AiAnswerScope.FROM_DOCUMENT,
    )

    @Test
    fun `a new conversation is empty and idle`() {
        val conversation = AiConversation(externalKey = "boc:1")

        assertTrue(conversation.isEmpty)
        assertEquals(AiChatStatus.Idle, conversation.status)
    }

    @Test
    fun `with one message it is no longer empty`() {
        val conversation = AiConversation("boc:1", messages = listOf(question("q1", "¿Y bien?")))

        assertFalse(conversation.isEmpty)
    }

    @Test
    fun `the last question is what retrying would resend, ignoring the answers after it`() {
        val conversation = AiConversation(
            externalKey = "boc:1",
            messages = listOf(
                question("q1", "La primera"),
                answer("a1"),
                question("q2", "La segunda"),
            ),
        )

        assertEquals("q2", conversation.lastQuestion?.id)
        assertEquals("La segunda", conversation.lastQuestion?.text)
    }

    @Test
    fun `a conversation with only answers has no last question`() {
        val conversation = AiConversation("boc:1", messages = listOf(answer("a1")))

        assertNull(conversation.lastQuestion)
    }
}
