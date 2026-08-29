package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The selection travels as navigation arguments, so [HomeSelection.of] is the boundary where a
 * malformed pair has to become something sane rather than a crash on a cold start.
 */
class HomeSelectionTest {

    @Test
    fun `no section means the day's bulletin`() {
        assertEquals(HomeSelection.TodaysBulletin, HomeSelection.of(null, null))
        assertEquals(HomeSelection.TodaysBulletin, HomeSelection.of("", null))
    }

    @Test
    fun `a section without subsection selects the whole section`() {
        assertEquals(HomeSelection.Section("7"), HomeSelection.of("7", null))
    }

    @Test
    fun `a blank subsection is treated as absent, not as a code`() {
        assertEquals(HomeSelection.Section("7"), HomeSelection.of("7", "  "))
    }

    @Test
    fun `a subsection selects the subsection`() {
        val selection = HomeSelection.of("2", "2.2")

        assertEquals(HomeSelection.Section("2", "2.2"), selection)
        assertEquals("2.2", (selection as HomeSelection.Section).code)
    }

    @Test
    fun `the code of a section without subsection is the section itself`() {
        assertEquals("7", HomeSelection.Section("7").code)
    }

    @Test
    fun `a subsection that does not belong to its section is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            HomeSelection.Section(sectionCode = "2", subsectionCode = "7.1")
        }
    }
}
