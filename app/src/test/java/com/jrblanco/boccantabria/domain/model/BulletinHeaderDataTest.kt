package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * There is no bulletin number in this model, and that is the point: the official feeds do not
 * publish one. The badge carries a real count instead of an invented figure.
 */
class BulletinHeaderDataTest {

    @Test
    fun `without a section name the header describes the day's bulletin`() {
        val header = BulletinHeaderData(date = LocalDate.of(2026, 8, 27), publicationCount = 48)

        assertTrue(header.isTodaysBulletin)
    }

    @Test
    fun `with a section name the header describes that section`() {
        val header = BulletinHeaderData(
            date = LocalDate.of(2021, 3, 26),
            publicationCount = 9,
            sectionName = "Actuaciones en materia de Seguridad Social",
        )

        assertFalse(header.isTodaysBulletin)
    }

    @Test
    fun `an empty header has no date and no publications`() {
        assertTrue(BulletinHeaderData.EMPTY.isTodaysBulletin)
        assertThrows(IllegalArgumentException::class.java) {
            BulletinHeaderData(date = null, publicationCount = -1)
        }
    }
}
