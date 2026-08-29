package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.domain.model.ParserWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `a date survives the round trip and is stored in ISO form`() {
        val date = LocalDate.of(2026, 8, 27)

        assertEquals("2026-08-27", converters.dateToString(date))
        assertEquals(date, converters.stringToDate("2026-08-27"))
    }

    @Test
    fun `ISO text sorts the same way dates do, which is why it is the stored form`() {
        val stored = listOf("2021-03-26", "2026-08-27", "2019-10-08").sorted()

        assertEquals(listOf("2019-10-08", "2021-03-26", "2026-08-27"), stored)
    }

    @Test
    fun `a null date stays null in both directions`() {
        assertEquals(null, converters.dateToString(null))
        assertEquals(null, converters.stringToDate(null))
    }

    @Test
    fun `an organization path survives the round trip`() {
        val path = listOf("Consejería de Salud", "Secretaría General")

        assertEquals(path, converters.stringToList(converters.listToString(path)))
    }

    @Test
    fun `separators that appear in real names do not split a path element`() {
        // Both characters occur in the source: commas inside organisation names, pipes as the
        // separator of `categorias` itself. Neither may break an element in two.
        val path = listOf(
            "Consejería de Fomento, Vivienda, Ordenación del Territorio y Medio Ambiente",
            "Dirección General de Urbanismo | Ordenación del Territorio",
        )

        assertEquals(path, converters.stringToList(converters.listToString(path)))
    }

    @Test
    fun `an empty path round trips to an empty list, not to a list holding one blank`() {
        assertEquals(
            emptyList<String>(),
            converters.stringToList(converters.listToString(emptyList())),
        )
        assertEquals(emptyList<String>(), converters.stringToList(null))
    }

    @Test
    fun `warnings survive the round trip`() {
        val warnings = setOf(
            ParserWarning.CATEGORY_ORDER_UNRELIABLE,
            ParserWarning.EDITION_TYPE_MISSING,
        )

        assertEquals(warnings, converters.stringToWarnings(converters.warningsToString(warnings)))
    }

    @Test
    fun `a warning this version does not know is dropped instead of crashing`() {
        // A database written by a later version must stay readable by this one.
        val stored = "EDITION_TYPE_MISSING\u001FWARNING_FROM_THE_FUTURE"

        assertEquals(setOf(ParserWarning.EDITION_TYPE_MISSING), converters.stringToWarnings(stored))
    }

    @Test
    fun `no warnings round trips to an empty set`() {
        assertTrue(converters.stringToWarnings(converters.warningsToString(emptySet())).isEmpty())
        assertTrue(converters.stringToWarnings(null).isEmpty())
    }
}
