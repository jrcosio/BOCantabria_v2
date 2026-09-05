package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Pages are counted from one because that is how a person reads them (FR-013). */
class AiAnswerSourceTest {

    @Test
    fun `keeps the page and the label it is given`() {
        val source = AiAnswerSource(page = 3, label = "Entrada en vigor")

        assertEquals(3, source.page)
        assertEquals("Entrada en vigor", source.label)
    }

    @Test
    fun `page one is valid`() {
        assertEquals(1, AiAnswerSource(page = 1, label = "Encabezado").page)
    }

    @Test
    fun `page zero is refused, because zero is the viewer's counting and not the reader's`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiAnswerSource(page = 0, label = "Ninguna")
        }
    }

    @Test
    fun `a negative page is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiAnswerSource(page = -1, label = "Ninguna")
        }
    }
}
