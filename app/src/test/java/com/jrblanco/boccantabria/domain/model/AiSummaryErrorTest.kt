package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-041. Offering a retry where it cannot help is its own kind of lie, and hiding one where it
 * would work strands the person. So every case is asserted one by one rather than by rule.
 */
class AiSummaryErrorTest {

    @Test
    fun `being offline is worth trying again`() {
        assertTrue(AiSummaryError.Offline.isRetryable)
    }

    @Test
    fun `running out of the minute allowance is worth trying again`() {
        assertTrue(AiSummaryError.QuotaMinute(secondsRemaining = 42).isRetryable)
    }

    @Test
    fun `an unusable answer is worth trying again`() {
        assertTrue(AiSummaryError.InvalidResponse.isRetryable)
    }

    @Test
    fun `an unknown failure is worth trying again`() {
        assertTrue(AiSummaryError.Unknown.isRetryable)
    }

    @Test
    fun `a document without text will not become one by retrying`() {
        assertFalse(AiSummaryError.NoExtractableText.isRetryable)
    }

    @Test
    fun `a protected document will not open by retrying`() {
        assertFalse(AiSummaryError.EncryptedPdf.isRetryable)
    }

    @Test
    fun `the daily allowance does not come back within the session`() {
        assertFalse(AiSummaryError.QuotaDay.isRetryable)
    }

    @Test
    fun `a missing credential is a limitation of the application, not a hiccup`() {
        assertFalse(AiSummaryError.NotConfigured.isRetryable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a quota countdown cannot run backwards`() {
        AiSummaryError.QuotaMinute(secondsRemaining = -5)
    }

    @Test
    fun `the countdown is carried so the screen can show it`() {
        assertEquals(42L, AiSummaryError.QuotaMinute(42).secondsRemaining)
    }
}
