package com.jrblanco.boccantabria.domain.usecase

import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ObserveBulletinHeaderUseCaseTest {

    @Test
    fun `the day's bulletin header carries its date and its count, and no section name`() = runTest {
        val repository = FakePublicationRepository(
            listOf(
                publication("boc:1", date = LocalDate.of(2026, 8, 27)),
                publication("boc:2", date = LocalDate.of(2026, 8, 27)),
            ),
        )

        ObserveBulletinHeaderUseCase(repository)(HomeSelection.TodaysBulletin).test {
            val header = awaitItem()
            assertEquals(LocalDate.of(2026, 8, 27), header.date)
            assertEquals(2, header.publicationCount)
            assertTrue(header.isTodaysBulletin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a section header names the section`() = runTest {
        val repository = FakePublicationRepository(listOf(publication("boc:1")))
        repository.sectionNameFor = { "Cursos, oposiciones y concursos" }

        ObserveBulletinHeaderUseCase(repository)(HomeSelection.Section("2", "2.2")).test {
            val header = awaitItem()
            assertEquals("Cursos, oposiciones y concursos", header.sectionName)
            assertTrue(!header.isTodaysBulletin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty selection has no date and counts zero, which is not an error`() = runTest {
        ObserveBulletinHeaderUseCase(FakePublicationRepository())(HomeSelection.TodaysBulletin).test {
            val header = awaitItem()
            assertNull(header.date)
            assertEquals(0, header.publicationCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
