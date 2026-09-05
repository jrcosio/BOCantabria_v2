package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatMessageTest {

    @Test
    fun `a question carries its text, its identity and its time`() {
        val question = AiChatMessage.Question(id = "q1", atEpochMillis = 1_000, text = "¿Qué plazo hay?")

        assertEquals("q1", question.id)
        assertEquals(1_000L, question.atEpochMillis)
        assertEquals("¿Qué plazo hay?", question.text)
    }

    @Test
    fun `an answer carries its scope and its sources`() {
        val answer = AiChatMessage.Answer(
            id = "a1",
            atEpochMillis = 2_000,
            text = "Veinte días hábiles.",
            scope = AiAnswerScope.FROM_DOCUMENT,
            sources = listOf(AiAnswerSource(page = 2, label = "Plazo")),
        )

        assertEquals(AiAnswerScope.FROM_DOCUMENT, answer.scope)
        assertEquals(1, answer.sources.size)
        assertEquals(2, answer.sources.first().page)
    }

    @Test
    fun `an answer with no sources is perfectly valid`() {
        val answer = AiChatMessage.Answer(
            id = "a1",
            atEpochMillis = 2_000,
            text = "El documento no fija ese plazo.",
            scope = AiAnswerScope.NOT_IN_DOCUMENT,
        )

        assertTrue(answer.sources.isEmpty())
    }

    @Test
    fun `a blank question cannot be built`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiChatMessage.Question(id = "q1", atEpochMillis = 0, text = "   ")
        }
    }

    @Test
    fun `a blank answer cannot be built, because an empty bubble is not an answer`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiChatMessage.Answer(
                id = "a1",
                atEpochMillis = 0,
                text = "",
                scope = AiAnswerScope.FROM_DOCUMENT,
            )
        }
    }
}
