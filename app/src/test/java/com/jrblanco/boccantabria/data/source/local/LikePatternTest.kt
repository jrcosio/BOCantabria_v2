package com.jrblanco.boccantabria.data.source.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The failure this prevents does not throw and does not log: it lies.
 *
 * `%` and `_` are wildcards in SQL `LIKE`. Without escaping, somebody typing `100%` gets the entire
 * archive back and reads it as "the search is broken"; somebody typing `a_b` gets `axb` and never
 * notices. Nobody tests either by hand, which is why they are pinned here.
 */
class LikePatternTest {

    @Test
    fun `ordinary text is just wrapped`() {
        assertEquals("%pielagos%", likeContains("pielagos"))
    }

    @Test
    fun `a percent sign is a character, not a wildcard`() {
        assertEquals("%100\\%%", likeContains("100%"))
    }

    @Test
    fun `an underscore is a character, not a single-character wildcard`() {
        assertEquals("%a\\_b%", likeContains("a_b"))
    }

    /** The escape character itself has to be escaped, or the escaping is not closed under itself. */
    @Test
    fun `a backslash escapes itself`() {
        assertEquals("%c\\\\d%", likeContains("c\\d"))
    }

    @Test
    fun `the backslash is handled before the wildcards, so nothing is escaped twice`() {
        assertEquals("%\\\\\\%%", likeContains("\\%"))
    }

    @Test
    fun `an empty query matches everything, which is the caller's problem and not this one's`() {
        assertEquals("%%", likeContains(""))
    }
}
