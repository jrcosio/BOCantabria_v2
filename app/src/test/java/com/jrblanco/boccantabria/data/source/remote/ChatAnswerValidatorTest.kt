package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.AiAnswerScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAnswerValidatorTest {

    private val validator = ChatAnswerValidator()

    private fun payload(
        scope: String = "FROM_DOCUMENT",
        answer: String = "El plazo es de veinte días hábiles.",
        sources: List<ChatSourceDto> = emptyList(),
    ) = ChatAnswerPayload(scope = scope, sources = sources, answer = answer)

    // --- El ámbito ---

    @Test
    fun `translates the three declared scopes`() {
        assertEquals(
            AiAnswerScope.FROM_DOCUMENT,
            validator.validate(payload(scope = "FROM_DOCUMENT"), 9)?.scope,
        )
        assertEquals(
            AiAnswerScope.NOT_IN_DOCUMENT,
            validator.validate(payload(scope = "NOT_IN_DOCUMENT"), 9)?.scope,
        )
        assertEquals(
            AiAnswerScope.OUT_OF_SCOPE,
            validator.validate(payload(scope = "OUT_OF_SCOPE"), 9)?.scope,
        )
    }

    @Test
    fun `an unknown scope becomes out of scope, because when in doubt our text is shown`() {
        assertEquals(
            AiAnswerScope.OUT_OF_SCOPE,
            validator.validate(payload(scope = "ANYTHING_ELSE"), 9)?.scope,
        )
    }

    @Test
    fun `a missing scope becomes out of scope too`() {
        assertEquals(
            AiAnswerScope.OUT_OF_SCOPE,
            validator.validate(payload(scope = ""), 9)?.scope,
        )
    }

    @Test
    fun `an out-of-scope answer keeps none of the model's text`() {
        val result = validator.validate(
            payload(scope = "OUT_OF_SCOPE", answer = "Aquí va un poema sobre Cantabria"),
            9,
        )

        assertEquals("", result?.text)
        assertTrue(result?.sources.orEmpty().isEmpty())
    }

    @Test
    fun `an out-of-scope answer is not refused even when its body is blank`() {
        // The prompt tells the model it may leave `answer` empty out of scope, because the
        // application supplies the text. Refusing it would turn a working defence into an error.
        val result = validator.validate(payload(scope = "OUT_OF_SCOPE", answer = ""), 9)

        assertEquals(AiAnswerScope.OUT_OF_SCOPE, result?.scope)
    }

    // --- Las citas imposibles ---

    @Test
    fun `drops a citation to a page the document does not have`() {
        val result = validator.validate(
            payload(
                sources = listOf(
                    ChatSourceDto(page = 2, label = "Plazo"),
                    ChatSourceDto(page = 14, label = "Inventada"),
                ),
            ),
            totalPages = 9,
        )

        assertEquals(listOf(2), result?.sources?.map { it.page })
        assertEquals(1, result?.droppedCitations)
    }

    @Test
    fun `drops page zero and negative pages`() {
        val result = validator.validate(
            payload(
                sources = listOf(
                    ChatSourceDto(page = 0, label = "Cero"),
                    ChatSourceDto(page = -3, label = "Negativa"),
                    ChatSourceDto(page = 1, label = "Buena"),
                ),
            ),
            totalPages = 9,
        )

        assertEquals(listOf(1), result?.sources?.map { it.page })
        assertEquals(2, result?.droppedCitations)
    }

    @Test
    fun `drops a citation with no label, because a source nobody can read is not a source`() {
        val result = validator.validate(
            payload(sources = listOf(ChatSourceDto(page = 2, label = "  "))),
            totalPages = 9,
        )

        assertTrue(result?.sources.orEmpty().isEmpty())
    }

    @Test
    fun `keeps one citation per page and sorts them`() {
        val result = validator.validate(
            payload(
                sources = listOf(
                    ChatSourceDto(page = 3, label = "Tercera"),
                    ChatSourceDto(page = 1, label = "Primera"),
                    ChatSourceDto(page = 3, label = "Tercera otra vez"),
                ),
            ),
            totalPages = 9,
        )

        assertEquals(listOf(1, 3), result?.sources?.map { it.page })
    }

    @Test
    fun `an answer whose every citation is impossible keeps its text`() {
        // FR-015. Losing the answer because its references were wrong would be losing the useful half.
        val result = validator.validate(
            payload(sources = listOf(ChatSourceDto(page = 99, label = "Inventada"))),
            totalPages = 9,
        )

        assertEquals("El plazo es de veinte días hábiles.", result?.text)
        assertTrue(result?.sources.orEmpty().isEmpty())
    }

    // --- El texto ---

    @Test
    fun `trims text that stops mid-sentence back to the last one that finished`() {
        val result = validator.validate(
            payload(answer = "El plazo es de veinte días. Los requisitos de nacionalidad,"),
            totalPages = 9,
        )

        assertEquals("El plazo es de veinte días.", result?.text)
    }

    @Test
    fun `leaves text that ends properly alone`() {
        val result = validator.validate(payload(answer = "Veinte días hábiles."), totalPages = 9)

        assertEquals("Veinte días hábiles.", result?.text)
    }

    @Test
    fun `keeps text with no sentence ending at all rather than losing it`() {
        val result = validator.validate(payload(answer = "Veinte días hábiles"), totalPages = 9)

        assertEquals("Veinte días hábiles", result?.text)
    }

    @Test
    fun `question and exclamation marks count as endings`() {
        val result = validator.validate(
            payload(answer = "¿Veinte días? Sí. Y hay una prórroga,"),
            totalPages = 9,
        )

        assertEquals("¿Veinte días? Sí.", result?.text)
    }

    // --- El cuerpo en blanco ---

    @Test
    fun `a blank body is refused, because an empty bubble is not an answer`() {
        assertNull(validator.validate(payload(answer = ""), totalPages = 9))
        assertNull(validator.validate(payload(answer = "   \n  "), totalPages = 9))
    }

    @Test
    fun `a blank body is refused for not-in-document too`() {
        assertNull(validator.validate(payload(scope = "NOT_IN_DOCUMENT", answer = ""), 9))
    }
}
