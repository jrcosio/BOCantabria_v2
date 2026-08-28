package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.domain.model.SectionColorGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tree and the source catalogue have to agree. They are written in different files for good
 * architectural reasons, which is exactly why something has to check they still describe the same
 * bulletin.
 */
class BocSectionRepositoryImplTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @Test
    fun `there are nine top level sections and fourteen subsections`() {
        assertEquals(9, sections.count { it.isTopLevel })
        assertEquals(14, sections.count { !it.isTopLevel })
    }

    @Test
    fun `the presentation order is complete and without gaps`() {
        assertEquals((1..23).toList(), sections.map { it.order }.sorted())
    }

    @Test
    fun `the tree is returned in official order`() {
        assertEquals(sections.sortedBy { it.order }, sections)
    }

    @Test
    fun `every subsection has a parent that exists`() {
        val codes = sections.map { it.code }.toSet()

        sections.filter { !it.isTopLevel }.forEach { subsection ->
            assertTrue(
                "el padre de ${subsection.code} no está en el árbol",
                subsection.parentCode in codes,
            )
        }
    }

    @Test
    fun `sections 2, 4, 7 and 8 are the aggregates, and they have no source of their own`() {
        val withSubsections = sections
            .filter { !it.isTopLevel }
            .mapNotNull { it.parentCode }
            .distinct()
            .sorted()

        assertEquals(listOf("2", "4", "7", "8"), withSubsections)
        assertTrue(
            BocFeedCatalog.definitions.none { it.subsectionCode == null && it.sectionCode in withSubsections },
        )
    }

    @Test
    fun `every classification the catalogue claims exists in the tree`() {
        val codes = sections.map { it.code }.toSet()

        BocFeedCatalog.definitions.forEach { definition ->
            assertTrue(
                "la clasificación ${definition.classificationCode} no está en el árbol",
                definition.classificationCode in codes,
            )
        }
    }

    @Test
    fun `every leaf of the tree has a source that feeds it`() {
        val claimed = BocFeedCatalog.definitions.map { it.classificationCode }.toSet()
        val leaves = sections.filter { section ->
            sections.none { it.parentCode == section.code }
        }

        leaves.forEach { leaf ->
            assertTrue("nadie alimenta ${leaf.code}", leaf.code in claimed)
        }
    }

    @Test
    fun `a subsection shares the colour group of its parent`() {
        sections.filter { !it.isTopLevel }.forEach { subsection ->
            val parent = sections.first { it.code == subsection.parentCode }
            assertEquals(
                "${subsection.code} no comparte color con ${parent.code}",
                parent.colorGroup,
                subsection.colorGroup,
            )
        }
    }

    @Test
    fun `the nine sections map onto the five colour groups of the design document`() {
        val byGroup = sections.filter { it.isTopLevel }.groupBy { it.colorGroup }

        assertEquals(listOf("1", "9"), byGroup.getValue(SectionColorGroup.GENERAL).map { it.code })
        assertEquals(listOf("2"), byGroup.getValue(SectionColorGroup.PERSONNEL).map { it.code })
        assertEquals(listOf("3"), byGroup.getValue(SectionColorGroup.CONTRACTING).map { it.code })
        assertEquals(listOf("4", "6"), byGroup.getValue(SectionColorGroup.ECONOMY).map { it.code })
        assertEquals(
            listOf("5", "7", "8"),
            byGroup.getValue(SectionColorGroup.ANNOUNCEMENTS).map { it.code },
        )
    }

    @Test
    fun `short names are short enough for a chip`() {
        sections.filter { it.isTopLevel }.forEach {
            assertTrue("«${it.shortName}» es demasiado largo para un chip", it.shortName.length <= 16)
        }
    }

    @Test
    fun `codes are unique`() {
        assertEquals(sections.size, sections.map { it.code }.toSet().size)
    }
}
