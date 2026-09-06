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

    // ---------- Feature 012: alerts ----------

    /** The tab travels by name and is optional; the name is restored tolerantly on the other side. */
    @Test
    fun `the alerts route carries an optional tab`() {
        assertEquals(null, Route.Alerts().tab)
        assertEquals("NEWS", Route.Alerts(tab = "NEWS").tab)
    }

    /** Create, edit and duplicate are the same screen told apart by which argument is present. */
    @Test
    fun `the form route distinguishes create, edit and duplicate`() {
        val create = Route.AlertForm()
        val edit = Route.AlertForm(ruleId = "r1")
        val duplicate = Route.AlertForm(duplicateOf = "r1")

        assertEquals(null, create.ruleId)
        assertEquals(null, create.duplicateOf)
        assertEquals("r1", edit.ruleId)
        assertEquals(null, edit.duplicateOf)
        assertEquals(null, duplicate.ruleId)
        assertEquals("r1", duplicate.duplicateOf)
    }
}
