package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BocSectionTest {

    @Test
    fun `a top level section has no parent`() {
        assertTrue(section(code = "1", parentCode = null).isTopLevel)
    }

    @Test
    fun `a subsection is not top level`() {
        assertFalse(section(code = "2.1", parentCode = "2").isTopLevel)
    }

    @Test
    fun `the display label is the code and the name, as the drawer rows show it`() {
        assertEquals(
            "1 · Disposiciones generales",
            section(code = "1", name = "Disposiciones generales").displayLabel,
        )
    }

    @Test
    fun `a subsection whose code does not descend from its parent is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            section(code = "3.1", parentCode = "2")
        }
    }

    @Test
    fun `a subsection code that merely starts with the parent digits is rejected`() {
        // "21" starts with "2" but is not a child of it. Without the dot this would slip through.
        assertThrows(IllegalArgumentException::class.java) {
            section(code = "21", parentCode = "2")
        }
    }

    @Test
    fun `blank fields are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { section(code = " ") }
        assertThrows(IllegalArgumentException::class.java) { section(name = "") }
        assertThrows(IllegalArgumentException::class.java) { section(shortName = "  ") }
    }

    private fun section(
        code: String = "1",
        name: String = "Disposiciones generales",
        shortName: String = "Disposiciones",
        parentCode: String? = null,
        order: Int = 1,
        colorGroup: SectionColorGroup = SectionColorGroup.GENERAL,
    ) = BocSection(code, name, shortName, parentCode, order, colorGroup)
}
