package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Five groups, not nine. If someone adds a sixth, the palette of the design document no longer
 * covers it and this fails, which is the point.
 */
class SectionColorGroupTest {

    @Test
    fun `there are exactly the five groups the design document defines`() {
        assertEquals(5, SectionColorGroup.entries.size)
    }
}
