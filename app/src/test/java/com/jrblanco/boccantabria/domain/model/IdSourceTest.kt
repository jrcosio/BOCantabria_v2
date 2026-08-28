package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the fallback ladder documented in the feed specification. The order matters: a record
 * identified by content hash is replaceable, one identified by blob id is not.
 */
class IdSourceTest {

    @Test
    fun `the ladder has exactly three rungs, best first`() {
        assertEquals(
            listOf(IdSource.BLOB_ID, IdSource.CANONICAL_URL, IdSource.CONTENT_HASH),
            IdSource.entries,
        )
    }
}
