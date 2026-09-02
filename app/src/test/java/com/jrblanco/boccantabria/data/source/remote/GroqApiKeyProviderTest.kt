package com.jrblanco.boccantabria.data.source.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GroqApiKeyProviderTest {

    @Test
    fun `a configured credential is handed through`() = runTest {
        assertEquals("una-clave", BuildConfigGroqApiKeyProvider("una-clave").apiKey())
    }

    /** FR-042: absent is a state, not a crash. The build stays green without a key. */
    @Test
    fun `an absent credential is null rather than empty`() = runTest {
        assertNull(BuildConfigGroqApiKeyProvider("").apiKey())
    }

    @Test
    fun `a blank credential counts as absent`() = runTest {
        assertNull(BuildConfigGroqApiKeyProvider("   ").apiKey())
    }

    /**
     * FR-047 and SC-009. This is the object most likely to end up in a log line or a crash report,
     * and a credential that reaches one has left the device.
     */
    @Test
    fun `the credential never appears in the text form of the provider`() {
        val rendered = BuildConfigGroqApiKeyProvider("gsk_secreto_que_no_debe_salir").toString()

        assertFalse(rendered.contains("gsk_secreto_que_no_debe_salir"))
        assertFalse(rendered.contains("secreto"))
    }
}
