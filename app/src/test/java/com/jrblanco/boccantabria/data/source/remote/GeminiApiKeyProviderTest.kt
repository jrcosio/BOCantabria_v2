package com.jrblanco.boccantabria.data.source.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiApiKeyProviderTest {

    @Test
    fun `a configured credential is handed through`() = runTest {
        assertEquals("una-clave", BuildConfigGeminiApiKeyProvider("una-clave").apiKey())
    }

    /** FR-029 and FR-033: absent is a state, not a crash. The build stays green without a key. */
    @Test
    fun `an absent credential is null rather than empty`() = runTest {
        assertNull(BuildConfigGeminiApiKeyProvider("").apiKey())
    }

    @Test
    fun `a blank credential counts as absent`() = runTest {
        assertNull(BuildConfigGeminiApiKeyProvider("   ").apiKey())
    }

    /**
     * FR-032 and SC-010. This is the object most likely to end up in a log line or a crash report,
     * and a credential that reaches one has left the device.
     *
     * **This provider has two key formats, and picking the wrong one is how a leak gets declared
     * clean.** The classic one starts with `AIza`; the one issued today starts with `AQ.` — checked
     * against the owner's own key on 4 September 2026, which is 53 characters beginning `AQ.A`. Both
     * are covered here, and both must be covered by the secret scans in `quickstart.md` §1.
     */
    @Test
    fun `the credential never appears in the text form of the provider`() {
        // Both prefixes, and both fixtures **deliberately too short to look like a real key**. The
        // repository's leak scan hunts for `AIza` plus thirty-odd characters and `AQ.` plus twenty,
        // which is what a real credential looks like; a fixture shaped like that makes the scan cry
        // wolf on every run, and a check that always fails is a check that stops being read. What
        // this test needs is the prefix, not the length (011, same lesson as 010 CLAUDE.md).
        listOf("AIzaNoEsUnaClave", "AQ.ANoEsUnaClave").forEach { secret ->
            val rendered = BuildConfigGeminiApiKeyProvider(secret).toString()

            assertFalse(rendered.contains(secret))
            assertFalse(rendered.contains("NoEsUnaClave"))
        }
    }
}
