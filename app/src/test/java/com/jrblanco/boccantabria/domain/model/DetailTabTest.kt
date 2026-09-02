package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailTabTest {

    @Test
    fun `there are two tabs, in the order the design document fixes`() {
        // Asking used to be the third. It is a screen now: a conversation needs the whole screen
        // and its own place in the back stack.
        assertEquals(listOf(DetailTab.DOCUMENT, DetailTab.AI_SUMMARY), DetailTab.entries)
    }

    /**
     * A saved tab is restored **by name**, never with `valueOf`. Asking was a tab once; a stored
     * name that no longer exists would take down the detail screen on the one path nobody walks by
     * hand — coming back from process death.
     */
    @Test
    fun `a tab name that no longer exists does not resolve`() {
        assertEquals(null, DetailTab.entries.firstOrNull { it.name == "ASK" })
        assertEquals(DetailTab.AI_SUMMARY, DetailTab.entries.firstOrNull { it.name == "AI_SUMMARY" })
    }
}
