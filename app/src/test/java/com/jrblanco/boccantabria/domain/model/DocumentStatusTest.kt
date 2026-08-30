package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentStatusTest {

    @Test
    fun `progress is a fraction when the total is known`() {
        val downloading = DocumentStatus.Downloading(bytesRead = 50, totalBytes = 200)

        assertEquals(0.25f, downloading.fraction!!, 0.0001f)
    }

    @Test
    fun `progress is unknown when the service does not declare a length`() {
        // The bar has to be indeterminate. Inventing a total would be lying to the reader.
        assertNull(DocumentStatus.Downloading(bytesRead = 50, totalBytes = null).fraction)
    }

    @Test
    fun `a declared total of zero does not produce a division`() {
        assertNull(DocumentStatus.Downloading(bytesRead = 0, totalBytes = 0).fraction)
    }

    @Test
    fun `reading past the declared total still yields a fraction within range`() {
        val downloading = DocumentStatus.Downloading(bytesRead = 300, totalBytes = 200)

        assertEquals(1f, downloading.fraction!!, 0.0001f)
    }

    @Test
    fun `negative progress is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DocumentStatus.Downloading(bytesRead = -1, totalBytes = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DocumentStatus.Downloading(bytesRead = 0, totalBytes = -10)
        }
    }
}
