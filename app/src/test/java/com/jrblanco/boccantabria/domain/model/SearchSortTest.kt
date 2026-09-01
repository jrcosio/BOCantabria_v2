package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSortTest {

    @Test
    fun `there are exactly two orders, and relevance is not one of them`() {
        assertEquals(listOf("NEWEST_FIRST", "OLDEST_FIRST"), SearchSort.entries.map { it.name })
    }

    @Test
    fun `the newest first is the default`() {
        assertEquals(SearchSort.NEWEST_FIRST, SearchSort.DEFAULT)
    }

    @Test
    fun `a saved order comes back by name`() {
        assertEquals(SearchSort.OLDEST_FIRST, SearchSort.byNameOrDefault("OLDEST_FIRST"))
    }

    /**
     * The path nobody walks by hand: coming back from process death with a name this version no
     * longer has. `valueOf` would throw and take the screen with it.
     */
    @Test
    fun `a name this version does not know falls back instead of throwing`() {
        assertEquals(SearchSort.DEFAULT, SearchSort.byNameOrDefault("RELEVANCE"))
        assertEquals(SearchSort.DEFAULT, SearchSort.byNameOrDefault(null))
        assertEquals(SearchSort.DEFAULT, SearchSort.byNameOrDefault(""))
    }
}
