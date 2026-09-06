package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository
import com.jrblanco.boccantabria.domain.repository.PublicationRepository

/**
 * Which stored publications a draft would match today.
 *
 * Information only: it writes nothing, notifies nothing and touches no rule's last match (FR-068).
 * It uses the very same comparator the cycle uses, so what it shows is what would have fired
 * (research.md D-437). An invalid draft previews nothing.
 */
class PreviewAlertRuleUseCase(
    private val publications: PublicationRepository,
    private val matchRule: MatchAlertRuleUseCase,
    private val sections: BocSectionRepository,
) {
    suspend operator fun invoke(draft: AlertRuleDraft): List<Publication> {
        if (!draft.hasCriteria) return emptyList()
        // The name does not take part in the match; a placeholder keeps the rule's own invariants.
        val rule = draft.copy(name = draft.name.ifBlank { PREVIEW_NAME }, isEnabled = true)
            .toRule(id = PREVIEW_ID, now = 0L, sections = sections.sections())
        return publications.newest(PREVIEW_LIMIT).filter { matchRule(rule, it) }
    }

    companion object {
        /**
         * Today the whole archive fits; the cap exists so an archive of years cannot freeze the form.
         */
        const val PREVIEW_LIMIT: Int = 5_000
        private const val PREVIEW_ID = "preview"
        private const val PREVIEW_NAME = "preview"
    }
}
