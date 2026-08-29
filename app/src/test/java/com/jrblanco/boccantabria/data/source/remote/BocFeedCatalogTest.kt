package com.jrblanco.boccantabria.data.source.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue is the one place where a typo would silently misfile announcements for years, so
 * it is checked mechanically rather than by reading.
 */
class BocFeedCatalogTest {

    @Test
    fun `there are the nineteen published sources`() {
        assertEquals(19, BocFeedCatalog.definitions.size)
    }

    @Test
    fun `the identifiers are the published ones, not a sequence`() {
        assertEquals(
            listOf(
                "6802081", "6802084", "6802085", "6802086", "6802087",
                "6802089", "6802090", "6802091", "6802092", "6802094",
                "6802095", "6802097", "6802098", "6802099", "6802100",
                "6802301", "7479572", "6802303", "7293890",
            ),
            BocFeedCatalog.definitions.map { it.feedId },
        )
    }

    @Test
    fun `no identifier is repeated`() {
        val ids = BocFeedCatalog.definitions.map { it.feedId }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every address ends in its own identifier`() {
        // The cheapest guard against a copy-paste that leaves two sources pointing at the same
        // place: it would classify a whole section as another one.
        BocFeedCatalog.definitions.forEach { definition ->
            assertEquals(
                "https://www.cantabria.es/o/BOC/feed/${definition.feedId}",
                definition.url,
            )
        }
    }

    @Test
    fun `no address is repeated`() {
        val urls = BocFeedCatalog.definitions.map { it.url }

        assertEquals(urls.size, urls.toSet().size)
    }

    @Test
    fun `the nine official sections are covered`() {
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"),
            BocFeedCatalog.definitions.map { it.sectionCode }.distinct().sorted(),
        )
    }

    @Test
    fun `the fourteen subsections are covered, and none overlaps`() {
        val subsections = BocFeedCatalog.definitions.mapNotNull { it.subsectionCode }

        assertEquals(
            listOf(
                "2.1", "2.2", "2.3",
                "4.1", "4.2", "4.3", "4.4",
                "7.1", "7.2", "7.3", "7.4", "7.5",
                "8.1", "8.2",
            ),
            subsections.sorted(),
        )
        assertEquals(subsections.size, subsections.toSet().size)
    }

    @Test
    fun `only the sections without subsections have a source of their own`() {
        // 2, 4, 7 and 8 are aggregates: no feed speaks for them directly.
        assertEquals(
            listOf("1", "3", "5", "6", "9"),
            BocFeedCatalog.definitions.filter { it.subsectionCode == null }.map { it.sectionCode },
        )
    }

    @Test
    fun `every classification is claimed by exactly one source`() {
        val codes = BocFeedCatalog.definitions.map { it.classificationCode }

        assertEquals(19, codes.size)
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `the presentation order is complete and without gaps`() {
        assertEquals((1..19).toList(), BocFeedCatalog.definitions.map { it.order }.sorted())
    }

    @Test
    fun `every source is enabled today, and disabling one is possible without touching the reader`() {
        assertEquals(19, BocFeedCatalog.enabled.size)
        assertTrue(BocFeedCatalog.definitions.all { it.enabled })
    }

    @Test
    fun `a source can be looked up by its identifier`() {
        assertNotNull(BocFeedCatalog.byFeedId("6802097"))
        assertEquals("7.1", BocFeedCatalog.byFeedId("6802097")?.subsectionCode)
        assertNull(BocFeedCatalog.byFeedId("no-existe"))
    }

    @Test
    fun `a definition whose subsection does not belong to its section is rejected`() {
        val error = runCatching {
            BocFeedDefinition(
                feedId = "1", url = "https://www.cantabria.es/o/BOC/feed/1",
                sectionCode = "2", subsectionCode = "7.1", order = 1,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
