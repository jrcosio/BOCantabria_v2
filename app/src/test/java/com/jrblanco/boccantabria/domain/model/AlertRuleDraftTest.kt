package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.fake.alertRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The form's rules, where a test can reach them: what can be saved, what a keyword has to look like,
 * and what the person is offered as a name.
 */
class AlertRuleDraftTest {

    private val sections = BocSectionRepositoryImpl().sections()

    // ---------- Validation ----------

    @Test
    fun `an empty draft cannot be saved, and says why twice`() {
        assertEquals(
            setOf(AlertRuleValidationError.NAME_BLANK, AlertRuleValidationError.NO_CRITERIA),
            AlertRuleDraft().validate(),
        )
    }

    @Test
    fun `a name and one criterion of any kind are enough`() {
        assertTrue(AlertRuleDraft(name = "Ganadería", keywords = listOf("ganadería")).isValid)
        assertTrue(AlertRuleDraft(name = "Oposiciones", sectionCodes = setOf("2.2")).isValid)
        assertTrue(AlertRuleDraft(name = "Piélagos", organizationQuery = "Ayuntamiento de Piélagos").isValid)
    }

    @Test
    fun `the name is trimmed before it is measured`() {
        assertTrue(AlertRuleDraft(name = "  Ganadería  ", keywords = listOf("ganadería")).isValid)
        assertEquals(
            setOf(AlertRuleValidationError.NAME_BLANK, AlertRuleValidationError.NO_CRITERIA),
            AlertRuleDraft(name = "   ").validate(),
        )
    }

    @Test
    fun `a name over sixty characters is refused`() {
        val draft = AlertRuleDraft(name = "a".repeat(61), keywords = listOf("ganadería"))

        assertEquals(setOf(AlertRuleValidationError.NAME_TOO_LONG), draft.validate())
    }

    @Test
    fun `a blank organisation is not a criterion`() {
        assertFalse(AlertRuleDraft(name = "x", organizationQuery = "  ").hasCriteria)
    }

    // ---------- Keywords ----------

    @Test
    fun `a keyword is trimmed and its inner whitespace collapsed`() {
        val added = AlertRuleDraft().addingKeyword("  medio   rural ") as KeywordAddition.Added

        assertEquals(listOf("medio rural"), added.draft.keywords)
    }

    @Test
    fun `a blank keyword is refused`() {
        assertEquals(KeywordAddition.Rejected(KeywordRejection.BLANK), AlertRuleDraft().addingKeyword("   "))
    }

    @Test
    fun `a one character keyword is too short`() {
        assertEquals(KeywordAddition.Rejected(KeywordRejection.TOO_SHORT), AlertRuleDraft().addingKeyword("a"))
    }

    @Test
    fun `a keyword over sixty characters is too long`() {
        assertEquals(
            KeywordAddition.Rejected(KeywordRejection.TOO_LONG),
            AlertRuleDraft().addingKeyword("a".repeat(61)),
        )
    }

    @Test
    fun `the eleventh keyword is refused`() {
        val full = AlertRuleDraft(keywords = (1..10).map { "palabra$it" })

        assertEquals(KeywordAddition.Rejected(KeywordRejection.LIMIT_REACHED), full.addingKeyword("undecima"))
    }

    /** `Cosío` and `COSIO` match the same text, so keeping both would only take a slot (FR-018). */
    @Test
    fun `a duplicate is detected on the normalised form`() {
        val draft = AlertRuleDraft(keywords = listOf("Cosío"))

        assertEquals(KeywordAddition.Rejected(KeywordRejection.DUPLICATE), draft.addingKeyword("COSIO"))
        assertEquals(KeywordAddition.Rejected(KeywordRejection.DUPLICATE), draft.addingKeyword(" cosío "))
    }

    @Test
    fun `the keyword is kept as typed, accents and all`() {
        val added = AlertRuleDraft().addingKeyword("Ganadería") as KeywordAddition.Added

        assertEquals(listOf("Ganadería"), added.draft.keywords)
    }

    @Test
    fun `removing a keyword leaves the rest`() {
        val draft = AlertRuleDraft(keywords = listOf("ganadería", "subvención"))

        assertEquals(listOf("subvención"), draft.removingKeyword("ganadería").keywords)
    }

    // ---------- Suggested name ----------

    @Test
    fun `the first keyword, capitalised, is proposed as a name`() {
        assertEquals("Ganadería", AlertRuleDraft(keywords = listOf("ganadería", "rural")).suggestedName(sections))
    }

    @Test
    fun `without keywords the first section is proposed`() {
        assertEquals(
            "Cursos, oposiciones y concursos",
            AlertRuleDraft(sectionCodes = setOf("2.2")).suggestedName(sections),
        )
    }

    @Test
    fun `a whole parent proposes the parent's name`() {
        assertEquals(
            "Autoridades y personal",
            AlertRuleDraft(sectionCodes = setOf("2.1", "2.2", "2.3")).suggestedName(sections),
        )
    }

    @Test
    fun `without keywords or sections the organisation is proposed`() {
        assertEquals(
            "Ayuntamiento de Piélagos",
            AlertRuleDraft(organizationQuery = " Ayuntamiento de Piélagos ").suggestedName(sections),
        )
    }

    @Test
    fun `nothing is proposed for an empty draft`() {
        assertNull(AlertRuleDraft().suggestedName(sections))
    }

    // ---------- To a rule and back ----------

    @Test
    fun `a parent in the draft becomes its children in the rule`() {
        val rule = AlertRuleDraft(name = "Personal", sectionCodes = setOf("2")).toRule("id", 5_000L, sections)

        assertEquals(setOf("2.1", "2.2", "2.3"), rule.sectionCodes)
        assertEquals(5_000L, rule.activeSince)
        assertEquals(5_000L, rule.createdAt)
    }

    @Test
    fun `blanks become any organisation and the name is trimmed`() {
        val rule = AlertRuleDraft(name = " Ganadería ", keywords = listOf("x1"), organizationQuery = "  ")
            .toRule("id", 1L, sections)

        assertNull(rule.organizationQuery)
        assertEquals("Ganadería", rule.name)
    }

    @Test
    fun `editing keeps the creation instant and renews the rest`() {
        val rule = AlertRuleDraft(name = "G", keywords = listOf("x1")).toRule("id", now = 9L, sections, createdAt = 1L)

        assertEquals(1L, rule.createdAt)
        assertEquals(9L, rule.updatedAt)
        assertEquals(9L, rule.activeSince)
    }

    @Test
    fun `a draft from a rule carries everything the form edits`() {
        val rule = alertRule(
            name = "Ganadería",
            keywords = listOf("ganadería", "rural"),
            matchMode = KeywordMatchMode.ALL,
            sectionCodes = setOf("6"),
            organizationQuery = "Consejería",
            isEnabled = false,
        )

        val draft = AlertRuleDraft.from(rule)

        assertEquals("Ganadería", draft.name)
        assertEquals(listOf("ganadería", "rural"), draft.keywords)
        assertEquals(KeywordMatchMode.ALL, draft.matchMode)
        assertEquals(setOf("6"), draft.sectionCodes)
        assertEquals("Consejería", draft.organizationQuery)
        assertFalse(draft.isEnabled)
    }

    /** FR-011: paused, «Copia de …», and — because it is created anew — without the original's matches. */
    @Test
    fun `a duplicate is paused and named as a copy`() {
        val draft = AlertRuleDraft.duplicateOf(alertRule(name = "Ganadería", isEnabled = true))

        assertEquals("Copia de Ganadería", draft.name)
        assertFalse(draft.isEnabled)
        assertEquals(listOf("ganadería"), draft.keywords)
    }

    @Test
    fun `a duplicate's name never exceeds the limit`() {
        val draft = AlertRuleDraft.duplicateOf(alertRule(name = "a".repeat(60)))

        assertEquals(60, draft.name.length)
        assertTrue(draft.name.startsWith("Copia de "))
    }
}
