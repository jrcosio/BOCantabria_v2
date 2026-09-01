package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The bulletin's in-place search. No store, no coroutines, no minimum length.
 */
class FilterPublicationsUseCaseTest {

    private val filter = FilterPublicationsUseCase()

    @Test
    fun `no text means no filtering at all`() {
        val items = listOf(publication(key = "boc:1"), publication(key = "boc:2"))

        assertSame(items, filter(items, ""))
        assertSame(items, filter(items, "   "))
    }

    @Test
    fun `a query without accents finds a title that has them`() {
        val items = listOf(
            publication(key = "boc:1", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación."),
            publication(key = "boc:2", title = "AYUNTAMIENTO DE SANTOÑA: Bases.", issuer = "Ayuntamiento de Santoña"),
        )

        assertEquals(listOf("boc:1"), filter(items, "pielagos").map { it.externalKey })
    }

    @Test
    fun `it matches the issuer as well as the title`() {
        val items = listOf(
            publication(key = "boc:1", title = "Resolución de 3 de marzo", issuer = "Gobierno de Cantabria"),
        )

        assertEquals(listOf("boc:1"), filter(items, "gobierno").map { it.externalKey })
    }

    @Test
    fun `the match can start in the middle of a word`() {
        val items = listOf(publication(key = "boc:1", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación."))

        assertEquals(1, filter(items, "ielagos").size)
    }

    /** Re-sorting here would quietly contradict the order the store decided. */
    @Test
    fun `the order of what comes in is the order of what goes out`() {
        val items = listOf(
            publication(key = "boc:3", title = "Piélagos tres"),
            publication(key = "boc:1", title = "Piélagos uno"),
            publication(key = "boc:2", title = "Piélagos dos"),
        )

        assertEquals(listOf("boc:3", "boc:1", "boc:2"), filter(items, "pielagos").map { it.externalKey })
    }

    @Test
    fun `nothing matching is an empty list`() {
        val items = listOf(publication(key = "boc:1", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación."))

        assertEquals(emptyList<String>(), filter(items, "expropiacion").map { it.externalKey })
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals(emptyList<String>(), filter(emptyList(), "pielagos"))
    }

    /** One character is a useful filter here, unlike in the archive search. */
    @Test
    fun `a single character already narrows the list`() {
        val items = listOf(
            publication(key = "boc:1", title = "Zarzuela", issuer = null),
            publication(key = "boc:2", title = "Bases", issuer = null),
        )

        assertEquals(listOf("boc:1"), filter(items, "z").map { it.externalKey })
    }
}
