package com.jrblanco.boccantabria.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The route arguments, which are the part of navigation that breaks in silence.
 *
 * Feature 006 paid for that lesson: navigating with `restoreState` swallowed a handed-over search
 * term and produced **no error at all**, just an empty field. So the shape of what travels is
 * asserted rather than assumed.
 */
class RoutesTest {

    /**
     * FR-021. A page reference in an AI summary has to reach the viewer, and the default keeps the
     * caller that existed before — the detail action bar — working untouched.
     */
    @Test
    fun `the viewer route carries a page and defaults to the first one`() {
        assertEquals(0, Route.PdfViewer("boc:439765").page)
        assertEquals(2, Route.PdfViewer("boc:439765", page = 2).page)
    }

    @Test
    fun `the viewer route keeps the publication it was asked about`() {
        assertEquals("boc:439765", Route.PdfViewer("boc:439765", page = 5).externalKey)
    }

    /**
     * The search route's argument is named `query` on purpose: it is the same key the search screen
     * reads from its saved state, and a different name would break the hand-off in silence.
     */
    @Test
    fun `the search route still carries an optional term`() {
        assertEquals(null, Route.Search().query)
        assertEquals("subvenciones", Route.Search("subvenciones").query)
    }
}
