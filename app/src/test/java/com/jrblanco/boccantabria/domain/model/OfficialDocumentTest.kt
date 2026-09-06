package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invariants live in the constructor so that, whichever way a document reaches the application,
 * it cannot exist in a shape that would let something unverified be shown as official.
 */
class OfficialDocumentTest {

    @Test
    fun `a well formed document is accepted`() {
        val document = document()

        assertEquals("boc:439765", document.externalKey)
        assertEquals(120_000L, document.byteCount)
    }

    @Test
    fun `a blank key or path is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { document(externalKey = " ") }
        assertThrows(IllegalArgumentException::class.java) { document(localPath = "") }
    }

    @Test
    fun `a document of zero bytes is rejected`() {
        // An empty file is never a document. Accepting one would let a failed download look valid.
        assertThrows(IllegalArgumentException::class.java) { document(byteCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { document(byteCount = -1) }
    }

    @Test
    fun `a checksum that is not a hexadecimal SHA-256 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { document(checksum = "abc") }
        assertThrows(IllegalArgumentException::class.java) { document(checksum = "Z".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { document(checksum = "A".repeat(64)) }
    }

    @Test
    fun `a lower case hexadecimal checksum of the right length is accepted`() {
        assertEquals("a".repeat(64), document(checksum = "a".repeat(64)).checksum)
    }

    /**
     * Feature 014: the cache validates a stored sidecar with the model's own rule, so the two can
     * never disagree on what a checksum is — which is exactly how STAB-001 happened.
     */
    @Test
    fun `isValidChecksum agrees with the constructor`() {
        assertTrue(OfficialDocument.isValidChecksum("a".repeat(64)))
        assertTrue(OfficialDocument.isValidChecksum(OfficialDocument.UNKNOWN_CHECKSUM))
        assertEquals("0".repeat(64), OfficialDocument.UNKNOWN_CHECKSUM)

        listOf("", "abc", "a".repeat(63), "A".repeat(64), " " + "a".repeat(64)).forEach { invalid ->
            assertFalse("«$invalid» debería rechazarse", OfficialDocument.isValidChecksum(invalid))
            assertThrows(IllegalArgumentException::class.java) { document(checksum = invalid) }
        }
    }

    private fun document(
        externalKey: String = "boc:439765",
        localPath: String = "/data/cache/documents/abc123.pdf",
        byteCount: Long = 120_000,
        checksum: String = "0".repeat(64),
        lastUsedAt: Long = 1_700_000_000_000,
    ) = OfficialDocument(externalKey, localPath, byteCount, checksum, lastUsedAt)
}
