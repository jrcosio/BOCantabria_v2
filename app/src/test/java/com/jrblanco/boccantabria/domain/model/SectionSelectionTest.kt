package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.SectionSelection.ToggleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hierarchy, against the real catalogue: nine sections, four of them with children, nineteen
 * leaves in total.
 */
class SectionSelectionTest {

    private val sections = BocSectionRepositoryImpl().sections()
    private fun section(code: String) = sections.first { it.code == code }

    @Test
    fun `a parent expands to its children and a leaf stays`() {
        assertEquals(setOf("2.1", "2.2", "2.3", "6"), SectionSelection.expandToLeaves(setOf("2", "6"), sections))
    }

    @Test
    fun `an unknown code passes through rather than vanishing`() {
        assertEquals(setOf("99"), SectionSelection.expandToLeaves(setOf("99"), sections))
    }

    @Test
    fun `toggling a parent selects every child`() {
        assertEquals(setOf("2.1", "2.2", "2.3"), SectionSelection.toggled(emptySet(), "2", sections))
    }

    @Test
    fun `toggling a fully selected parent clears its children and nothing else`() {
        assertEquals(setOf("6"), SectionSelection.toggled(setOf("2.1", "2.2", "2.3", "6"), "2", sections))
    }

    @Test
    fun `toggling a partly selected parent completes it`() {
        assertEquals(setOf("2.1", "2.2", "2.3"), SectionSelection.toggled(setOf("2.1"), "2", sections))
    }

    @Test
    fun `toggling a leaf flips it`() {
        assertEquals(setOf("2.2"), SectionSelection.toggled(emptySet(), "2.2", sections))
        assertEquals(emptySet<String>(), SectionSelection.toggled(setOf("2.2"), "2.2", sections))
    }

    @Test
    fun `a parent's state follows its children`() {
        assertEquals(ToggleState.UNCHECKED, SectionSelection.stateOf(section("2"), sections, emptySet()))
        assertEquals(ToggleState.INDETERMINATE, SectionSelection.stateOf(section("2"), sections, setOf("2.1")))
        assertEquals(ToggleState.CHECKED, SectionSelection.stateOf(section("2"), sections, setOf("2.1", "2.2", "2.3")))
    }

    @Test
    fun `a leaf's state is its own`() {
        assertEquals(ToggleState.CHECKED, SectionSelection.stateOf(section("6"), sections, setOf("6")))
        assertEquals(ToggleState.UNCHECKED, SectionSelection.stateOf(section("2.2"), sections, setOf("6")))
    }

    @Test
    fun `nothing selected summarises as null, which the screen reads as every section`() {
        assertNull(SectionSelection.summaryParts(emptySet(), sections))
    }

    @Test
    fun `every child selected summarises as the parent with all`() {
        val parts = SectionSelection.summaryParts(setOf("2.1", "2.2", "2.3"), sections)!!

        assertEquals(listOf("2"), parts.map { it.section.code })
        assertEquals(true, parts.single().allChildren)
    }

    @Test
    fun `some children selected summarise one by one, in official order`() {
        val parts = SectionSelection.summaryParts(setOf("7.2", "7.1", "6"), sections)!!

        assertEquals(listOf("6", "7.1", "7.2"), parts.map { it.section.code })
        assertEquals(listOf(false, false, false), parts.map { it.allChildren })
    }

    @Test
    fun `the leaf count expands parents before counting`() {
        assertEquals(4, SectionSelection.leafCount(setOf("2", "6"), sections))
    }
}
