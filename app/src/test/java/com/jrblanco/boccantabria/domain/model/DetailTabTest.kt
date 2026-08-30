package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailTabTest {

    @Test
    fun `there are two tabs, in the order the design document fixes`() {
        // Asking used to be the third. It is a screen now: a conversation needs the whole screen
        // and its own place in the back stack.
        assertEquals(listOf(DetailTab.DOCUMENT, DetailTab.AI_SUMMARY), DetailTab.entries)
    }

    @Test
    fun `only the document tab has content in this feature`() {
        assertFalse(DetailTab.DOCUMENT.isComingSoon)
        assertTrue(DetailTab.AI_SUMMARY.isComingSoon)
    }
}
