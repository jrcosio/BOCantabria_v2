package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.core.util.SearchText

/**
 * What the form edits: a rule before it is a rule.
 *
 * The validation lives here and not in the composable, so that it has a test of its own and no
 * screen can forget a rule (principle III). Every mutation returns a new draft; the view model holds
 * the current one.
 *
 * @param sectionCodes may hold a parent while editing; [toRule] expands it to its children. The form
 *   itself keeps leaves only, through [SectionSelection.toggled], so this is a safety net.
 * @param organizationQuery kept as typed; blank means any organisation.
 */
data class AlertRuleDraft(
    val name: String = "",
    val keywords: List<String> = emptyList(),
    val matchMode: KeywordMatchMode = KeywordMatchMode.ANY,
    val sectionCodes: Set<String> = emptySet(),
    val organizationQuery: String = "",
    val isEnabled: Boolean = true,
) {

    val hasCriteria: Boolean
        get() = keywords.isNotEmpty() || sectionCodes.isNotEmpty() || organizationQuery.isNotBlank()

    /** Empty when the draft can be saved. */
    fun validate(): Set<AlertRuleValidationError> = buildSet {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) add(AlertRuleValidationError.NAME_BLANK)
        if (trimmed.length > NAME_MAX_LENGTH) add(AlertRuleValidationError.NAME_TOO_LONG)
        if (!hasCriteria) add(AlertRuleValidationError.NO_CRITERIA)
    }

    val isValid: Boolean get() = validate().isEmpty()

    /**
     * Adds a word or phrase, or says why not.
     *
     * The term is trimmed and its inner whitespace collapsed before anything else, so `" medio  rural "`
     * and `"medio rural"` are the same term. Duplicates are detected on the **normalised** form: `Cosío`
     * and `COSIO` would match the same text, so keeping both would only take a slot (FR-018).
     */
    fun addingKeyword(raw: String): KeywordAddition {
        val term = raw.trim().replace(WHITESPACE_RUN, " ")
        val reason = when {
            term.isEmpty() -> KeywordRejection.BLANK
            term.length < KEYWORD_MIN_LENGTH -> KeywordRejection.TOO_SHORT
            term.length > KEYWORD_MAX_LENGTH -> KeywordRejection.TOO_LONG
            keywords.size >= MAX_KEYWORDS -> KeywordRejection.LIMIT_REACHED
            keywords.any { SearchText.normalise(it) == SearchText.normalise(term) } -> KeywordRejection.DUPLICATE
            else -> null
        }
        return if (reason == null) {
            KeywordAddition.Added(copy(keywords = keywords + term))
        } else {
            KeywordAddition.Rejected(reason)
        }
    }

    fun removingKeyword(keyword: String): AlertRuleDraft = copy(keywords = keywords - keyword)

    /**
     * A name to propose when the person has not typed one: the first criterion, in reading form.
     *
     * The first keyword capitalised; failing that, the name of the first chosen section —its parent's
     * when every child is in—; failing that, the organisation. `null` when there is nothing yet.
     * Always editable: this is a proposal, not a decision (FR-017).
     */
    fun suggestedName(sections: List<BocSection>): String? {
        keywords.firstOrNull()?.let { return it.replaceFirstChar(Char::uppercaseChar) }
        SectionSelection.summaryParts(sectionCodes, sections)?.firstOrNull()?.let { return it.section.name }
        return organizationQuery.trim().ifEmpty { null }
    }

    /**
     * The rule this draft describes, with parents expanded to leaves and blanks turned into "any".
     *
     * Used by the preview and by whoever persists: the repository gives it the identity and the
     * instants, so that the draft never has to know about clocks.
     */
    fun toRule(id: String, now: Long, sections: List<BocSection>, createdAt: Long = now): AlertRule = AlertRule(
        id = id,
        name = name.trim(),
        keywords = keywords,
        matchMode = matchMode,
        sectionCodes = SectionSelection.expandToLeaves(sectionCodes, sections),
        organizationQuery = organizationQuery.trim().ifEmpty { null },
        isEnabled = isEnabled,
        createdAt = createdAt,
        updatedAt = now,
        activeSince = now,
    )

    companion object {
        const val NAME_MAX_LENGTH: Int = 60
        const val KEYWORD_MIN_LENGTH: Int = 2
        const val KEYWORD_MAX_LENGTH: Int = 60
        const val MAX_KEYWORDS: Int = 10
        const val COPY_PREFIX: String = "Copia de "

        private val WHITESPACE_RUN = Regex("\\s+")

        /** A draft for editing [rule] as it is. */
        fun from(rule: AlertRule): AlertRuleDraft = AlertRuleDraft(
            name = rule.name,
            keywords = rule.keywords,
            matchMode = rule.matchMode,
            sectionCodes = rule.sectionCodes,
            organizationQuery = rule.organizationQuery.orEmpty(),
            isEnabled = rule.isEnabled,
        )

        /**
         * A copy to review before saving: **paused**, named «Copia de …», and — because it will be
         * created with a new identity — carrying none of the original's matches (FR-011).
         */
        fun duplicateOf(rule: AlertRule): AlertRuleDraft = from(rule).copy(
            name = (COPY_PREFIX + rule.name).take(NAME_MAX_LENGTH),
            isEnabled = false,
        )
    }
}

/** What stops a draft from being saved. The keyword rules are enforced when adding, not here. */
enum class AlertRuleValidationError {
    NAME_BLANK,
    NAME_TOO_LONG,
    NO_CRITERIA,
}

/** The outcome of trying to add a keyword. */
sealed interface KeywordAddition {
    data class Added(val draft: AlertRuleDraft) : KeywordAddition
    data class Rejected(val reason: KeywordRejection) : KeywordAddition
}

/** Why a keyword was not added. Each one has a sentence in the interface. */
enum class KeywordRejection {
    BLANK,
    TOO_SHORT,
    TOO_LONG,
    DUPLICATE,
    LIMIT_REACHED,
}
