package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.fake.alertRule
import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Never retroactive», stated once. Until feature 014 it held purely by the order of the cycle —
 * rules read before the refresh — which says nothing about a publication left pending from an
 * earlier cycle; this is the rule that covers both (014 research.md D-609).
 */
class AlertCandidateTest {

    private val stored = AlertCandidate(publication("boc:1"), storedAt = 10_000L)

    @Test
    fun `a rule active before the publication was stored sees it`() {
        assertTrue(stored.isVisibleTo(alertRule(activeSince = 9_999L)))
    }

    /**
     * The frozen-clock case: every integration test stores and activates at the same instant. With
     * `<` nothing would ever fire; `<=` is not a detail.
     */
    @Test
    fun `a rule active at the very instant the publication was stored sees it`() {
        assertTrue(stored.isVisibleTo(alertRule(activeSince = 10_000L)))
    }

    @Test
    fun `a rule activated after the publication was stored does not see it`() {
        assertFalse(stored.isVisibleTo(alertRule(activeSince = 10_001L)))
    }

    @Test
    fun `a candidate needs the instant it was stored`() {
        assertThrows(IllegalArgumentException::class.java) { AlertCandidate(publication("boc:1"), storedAt = 0L) }
        assertThrows(IllegalArgumentException::class.java) { AlertCandidate(publication("boc:1"), storedAt = -1L) }
    }
}
