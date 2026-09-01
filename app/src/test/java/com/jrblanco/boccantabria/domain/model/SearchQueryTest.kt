package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The rules about a search live here and not in the filter sheet, so that they can be checked
 * without mounting a screen and so that no future caller can forget them.
 */
class SearchQueryTest {

    @Test
    fun `the text is normalised the same way the stored text is`() {
        assertEquals("pielagos", SearchQuery(text = "  PIÉLAGOS ").normalisedText)
    }

    // ---------- When there is enough to search for ----------

    @Test
    fun `one character is not enough to go to the archive with`() {
        assertFalse(SearchQuery(text = "").isRunnable)
        assertFalse(SearchQuery(text = "a").isRunnable)
    }

    @Test
    fun `two characters are enough`() {
        assertTrue(SearchQuery(text = "ab").isRunnable)
    }

    /** Spaces are not text. A field holding only blanks is a field nobody typed in. */
    @Test
    fun `a query of only spaces is the same as not having searched`() {
        assertFalse(SearchQuery(text = "   ").isRunnable)
        assertEquals("", SearchQuery(text = "   ").normalisedText)
    }

    @Test
    fun `surrounding spaces do not count towards the minimum`() {
        assertFalse(SearchQuery(text = "  a  ").isRunnable)
    }

    // ---------- Counting the filters ----------

    @Test
    fun `no filters means none active`() {
        assertEquals(0, SearchQuery().activeFilterCount)
        assertFalse(SearchQuery().hasFilters)
    }

    /** A range reads as one filter, so it counts as one. */
    @Test
    fun `a date range counts as a single filter`() {
        val query = SearchQuery(from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 8, 27))

        assertEquals(1, query.activeFilterCount)
    }

    @Test
    fun `one open end still counts as one`() {
        assertEquals(1, SearchQuery(from = LocalDate.of(2026, 1, 1)).activeFilterCount)
        assertEquals(1, SearchQuery(to = LocalDate.of(2026, 1, 1)).activeFilterCount)
    }

    @Test
    fun `the rest count one each`() {
        val query = SearchQuery(
            from = LocalDate.of(2026, 1, 1),
            sectionCode = "3",
            subsectionCode = "3.1",
            issuer = "Ayuntamiento de Piélagos",
        )

        assertEquals(4, query.activeFilterCount)
    }

    // ---------- Clearing filters ----------

    /** The requirement that breaks most easily: clearing filters must not clear what was typed. */
    @Test
    fun `clearing the filters keeps the text and the order`() {
        val query = SearchQuery(
            text = "subvenciones",
            from = LocalDate.of(2026, 1, 1),
            sectionCode = "6",
            issuer = "Gobierno de Cantabria",
            sort = SearchSort.OLDEST_FIRST,
        )

        val cleared = query.clearedFilters()

        assertEquals("subvenciones", cleared.text)
        assertEquals(SearchSort.OLDEST_FIRST, cleared.sort)
        assertEquals(0, cleared.activeFilterCount)
    }

    @Test
    fun `removing one filter leaves the others alone`() {
        val query = SearchQuery(
            from = LocalDate.of(2026, 1, 1),
            sectionCode = "3",
            issuer = "Gobierno de Cantabria",
        )

        val withoutDates = query.withoutDateRange()

        assertNull(withoutDates.from)
        assertEquals("3", withoutDates.sectionCode)
        assertEquals("Gobierno de Cantabria", withoutDates.issuer)
    }

    /** A subsection with no section behind it filters by something nobody chose. */
    @Test
    fun `dropping the section drops its subsection with it`() {
        val query = SearchQuery(sectionCode = "3", subsectionCode = "3.1")

        val withoutSection = query.withoutSection()

        assertNull(withoutSection.sectionCode)
        assertNull(withoutSection.subsectionCode)
    }

    // ---------- Choosing a section ----------

    @Test
    fun `choosing a section clears a subsection that does not belong to it`() {
        val query = SearchQuery(sectionCode = "2", subsectionCode = "2.2")

        val moved = query.withSection("7")

        assertEquals("7", moved.sectionCode)
        assertNull(moved.subsectionCode)
    }

    @Test
    fun `choosing the same section keeps its subsection`() {
        val query = SearchQuery(sectionCode = "2", subsectionCode = "2.2")

        assertEquals("2.2", query.withSection("2").subsectionCode)
    }

    @Test
    fun `choosing no section at all clears both`() {
        val query = SearchQuery(sectionCode = "2", subsectionCode = "2.2")

        assertNull(query.withSection(null).sectionCode)
        assertNull(query.withSection(null).subsectionCode)
    }

    // ---------- The impossible range ----------

    /**
     * Reported rather than thrown. The sheet keeps the apply action disabled while this is true; a
     * `require` here would turn a handling mistake into a crash.
     */
    @Test
    fun `an inverted range is reported, not thrown`() {
        val inverted = SearchQuery(from = LocalDate.of(2026, 8, 27), to = LocalDate.of(2026, 1, 1))

        assertTrue(inverted.hasInvalidDateRange)
    }

    @Test
    fun `a sound range, an open one and none at all are all valid`() {
        assertFalse(SearchQuery(from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 8, 27)).hasInvalidDateRange)
        assertFalse(SearchQuery(from = LocalDate.of(2026, 1, 1)).hasInvalidDateRange)
        assertFalse(SearchQuery().hasInvalidDateRange)
    }

    @Test
    fun `the same day at both ends is a valid range`() {
        val sameDay = LocalDate.of(2026, 8, 27)

        assertFalse(SearchQuery(from = sameDay, to = sameDay).hasInvalidDateRange)
    }
}
