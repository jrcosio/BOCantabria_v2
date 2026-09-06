package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordMatchModeTest {

    @Test
    fun `a stored name is restored`() {
        assertEquals(KeywordMatchMode.ALL, KeywordMatchMode.byNameOrDefault("ALL"))
        assertEquals(KeywordMatchMode.ANY, KeywordMatchMode.byNameOrDefault("ANY"))
    }

    /** A value written by another version must never take the screen down. */
    @Test
    fun `an unknown or missing name is the form's default`() {
        assertEquals(KeywordMatchMode.ANY, KeywordMatchMode.byNameOrDefault("SOME"))
        assertEquals(KeywordMatchMode.ANY, KeywordMatchMode.byNameOrDefault(null))
    }
}
