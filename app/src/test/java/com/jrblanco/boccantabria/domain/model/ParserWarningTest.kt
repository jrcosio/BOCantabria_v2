package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the promise that warnings are recorded and never used to drop a publication. */
class ParserWarningTest {

    @Test
    fun `the four documented anomalies are covered`() {
        assertEquals(
            setOf(
                ParserWarning.CATEGORY_DOES_NOT_MATCH_FEED,
                ParserWarning.EDITION_TYPE_MISSING,
                ParserWarning.CATEGORY_ORDER_UNRELIABLE,
                ParserWarning.CATEGORIES_ABSENT,
            ),
            ParserWarning.entries.toSet(),
        )
    }
}
