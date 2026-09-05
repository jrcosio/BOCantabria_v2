package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-032, asserted case by case rather than by rule.
 *
 * Offering a retry where it cannot help is its own kind of lie, and hiding one where it would work
 * strands the person. A loop over the sealed hierarchy would pass while saying nothing.
 */
class AiChatErrorTest {

    @Test
    fun `being offline is worth trying again`() {
        assertTrue(AiChatError.Offline.isRetryable)
    }

    @Test
    fun `running out of the minute allowance is worth trying again`() {
        assertTrue(AiChatError.QuotaMinute(secondsRemaining = 37).isRetryable)
    }

    @Test
    fun `an unusable answer is worth trying again`() {
        assertTrue(AiChatError.InvalidResponse.isRetryable)
    }

    @Test
    fun `an unknown failure is worth trying again`() {
        assertTrue(AiChatError.Unknown.isRetryable)
    }

    @Test
    fun `the day's allowance will not come back by asking again`() {
        assertFalse(AiChatError.QuotaDay.isRetryable)
    }

    @Test
    fun `a build without a credential will not grow one by retrying`() {
        assertFalse(AiChatError.NotConfigured.isRetryable)
    }

    @Test
    fun `a document the service could not read will not become readable`() {
        assertFalse(AiChatError.UnreadableDocument.isRetryable)
    }

    @Test
    fun `a protected document will not open by retrying`() {
        assertFalse(AiChatError.EncryptedPdf.isRetryable)
    }

    @Test
    fun `a negative wait is refused, because it would render as a countdown that runs backwards`() {
        val error = runCatching { AiChatError.QuotaMinute(secondsRemaining = -1) }

        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `zero seconds is valid, because the wait can be over`() {
        assertTrue(AiChatError.QuotaMinute(secondsRemaining = 0).isRetryable)
    }
}
