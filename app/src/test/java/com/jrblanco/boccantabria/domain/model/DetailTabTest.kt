package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailTabTest {

    @Test
    fun `there are three tabs, in the order the design document fixes`() {
        assertEquals(
            listOf(DetailTab.DOCUMENT, DetailTab.AI_SUMMARY, DetailTab.ASK),
            DetailTab.entries,
        )
    }

    @Test
    fun `only the document tab has content in this feature`() {
        assertFalse(DetailTab.DOCUMENT.isComingSoon)
        assertTrue(DetailTab.AI_SUMMARY.isComingSoon)
        assertTrue(DetailTab.ASK.isComingSoon)
    }
}
