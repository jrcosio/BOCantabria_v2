package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The token can appear in any component of `categorias`, so the search belongs to the caller and
 * this only has to classify one token at a time — including the untidy shapes the source ships.
 */
class EditionTypeTest {

    @Test
    fun `recognises the two documented tokens`() {
        assertEquals(EditionType.ORDINARY, EditionType.fromToken("ORD"))
        assertEquals(EditionType.EXTRAORDINARY, EditionType.fromToken("EXT"))
    }

    @Test
    fun `tolerates surrounding whitespace and lower case`() {
        assertEquals(EditionType.ORDINARY, EditionType.fromToken("  ord "))
        assertEquals(EditionType.EXTRAORDINARY, EditionType.fromToken("Ext"))
    }

    @Test
    fun `anything else is not an edition token`() {
        assertNull(EditionType.fromToken("Ayuntamiento de Piélagos"))
        assertNull(EditionType.fromToken("1.Disposiciones Generales"))
        assertNull(EditionType.fromToken(""))
        assertNull(EditionType.fromToken("ORDINARIO"))
    }
}
