package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.util.SearchText
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository

/**
 * Does this publication satisfy this rule? Pure Kotlin, no store, no side effects.
 *
 * The three groups —sections, organisation, keywords— all have to hold, and an empty group holds by
 * itself (spec FR-032). Everything is compared **normalised**: lower case, no accents, whitespace
 * collapsed, so «GANADERIA» finds «ganadería» and «subvención» finds «subvenciones» (FR-030). The
 * comparison is a plain substring: nothing the person typed is ever read as a pattern (FR-027).
 *
 * The text a keyword is searched in (FR-029): the title, the raw `categorias` field, the issuer
 * hierarchy, the issuer, and the **names** of the section and subsection, which the publication does
 * not carry and the catalogue does. `categorias` goes in as plain text, so the permuted order of the
 * 4.3 feed changes nothing (FR-036). The blob identifier stays out: it is in Buscar's column because
 * somebody may type a number, and here it would only be noise (research.md D-406).
 *
 * Shared by the synchronisation cycle and by the form's preview, so the two can never disagree
 * (FR-068).
 */
class MatchAlertRuleUseCase(
    private val sections: BocSectionRepository,
) {
    private val nameByCode: Map<String, String> by lazy {
        sections.sections().associate { it.code to it.name }
    }

    operator fun invoke(rule: AlertRule, publication: Publication): Boolean {
        // Kept although the cycle already filters: it is the «regla pausada» case of §24, and it
        // protects the preview, which builds its rule by hand.
        if (!rule.isEnabled) return false

        if (rule.sectionCodes.isNotEmpty() &&
            publication.classificationCode !in rule.sectionCodes &&
            // Belt and braces: a stored rule should hold leaves only, but a parent code must still
            // catch its children rather than silently match nothing.
            publication.sectionCode !in rule.sectionCodes
        ) {
            return false
        }

        val organisation = rule.organizationQuery?.let(SearchText::normalise).orEmpty()
        if (organisation.isNotEmpty()) {
            val path = SearchText.normalise(publication.organizationPath.joinToString(" "))
            val issuer = publication.issuer?.let(SearchText::normalise).orEmpty()
            if (!path.contains(organisation) && !issuer.contains(organisation)) return false
        }

        val needles = rule.keywords.map(SearchText::normalise).filter { it.isNotEmpty() }
        if (needles.isEmpty()) return true

        val haystack = searchableText(publication)
        return when (rule.matchMode) {
            KeywordMatchMode.ANY -> needles.any { haystack.contains(it) }
            KeywordMatchMode.ALL -> needles.all { haystack.contains(it) }
        }
    }

    /** Everything a keyword is searched in, normalised. Public so the tests can read it. */
    fun searchableText(publication: Publication): String = SearchText.normalise(
        buildList {
            add(publication.title)
            publication.rawCategories?.let(::add)
            addAll(publication.organizationPath)
            publication.issuer?.let(::add)
            nameByCode[publication.sectionCode]?.let(::add)
            publication.subsectionCode?.let { nameByCode[it] }?.let(::add)
        }.joinToString(" "),
    )
}
