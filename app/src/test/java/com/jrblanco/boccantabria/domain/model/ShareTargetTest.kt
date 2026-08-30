package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareTargetTest {

    @Test
    fun `the link case carries its reason, so the screen can explain it`() {
        val target = ShareTarget.Link(
            url = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765",
            reason = ShareTarget.LinkReason.NO_CONNECTION,
        )

        assertEquals(ShareTarget.LinkReason.NO_CONNECTION, target.reason)
    }

    @Test
    fun `a link that is not https is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareTarget.Link("http://boc.cantabria.es/a", ShareTarget.LinkReason.NO_CONNECTION)
        }
    }

    @Test
    fun `only one reason exists today, and it is the one the specification names`() {
        assertEquals(listOf(ShareTarget.LinkReason.NO_CONNECTION), ShareTarget.LinkReason.entries)
    }
}
