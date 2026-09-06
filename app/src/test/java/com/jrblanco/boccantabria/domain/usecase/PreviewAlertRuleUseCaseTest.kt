package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Information only: the same comparator, nothing written (FR-068). */
class PreviewAlertRuleUseCaseTest {

    private val sections = BocSectionRepositoryImpl()
    private val publications = FakePublicationRepository(
        listOf(
            publication("boc:1", title = "Ayudas a la ganadería."),
            publication("boc:2", title = "Bases de una oposición.", sectionCode = "2", subsectionCode = "2.2"),
            publication("boc:3", title = "Convocatoria de pesca."),
        ),
    )
    private val preview = PreviewAlertRuleUseCase(publications, MatchAlertRuleUseCase(sections), sections)

    @Test
    fun `counts what the stored bulletin already contains`() = runTest {
        val found = preview(AlertRuleDraft(keywords = listOf("ganadería")))

        assertEquals(listOf("boc:1"), found.map { it.externalKey })
    }

    @Test
    fun `a parent section in the draft is expanded before matching`() = runTest {
        assertEquals(listOf("boc:2"), preview(AlertRuleDraft(sectionCodes = setOf("2"))).map { it.externalKey })
    }

    @Test
    fun `a paused draft previews as if enabled`() = runTest {
        assertEquals(1, preview(AlertRuleDraft(keywords = listOf("ganadería"), isEnabled = false)).size)
    }

    @Test
    fun `a draft without criteria previews nothing rather than everything`() = runTest {
        assertTrue(preview(AlertRuleDraft(name = "x")).isEmpty())
    }

    @Test
    fun `a nameless draft still previews`() = runTest {
        assertEquals(1, preview(AlertRuleDraft(keywords = listOf("pesca"))).size)
    }
}
