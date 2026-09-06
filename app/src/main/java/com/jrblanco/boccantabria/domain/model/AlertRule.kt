package com.jrblanco.boccantabria.domain.model

/**
 * A rule the person created: what to watch the bulletin for.
 *
 * Three groups of criteria, all of which have to hold —sections, organisation, keywords— and each of
 * which is optional. An empty group is satisfied by anything. What is **not** allowed is all three
 * empty at once: that would mean "notify me of everything" without anybody having said so
 * (spec FR-026), and it is refused here as well as in the form.
 *
 * @param keywords as the person typed them, accents and all. They are normalised **when compared**,
 *   never when stored, so that a change in the normalisation cannot desynchronise what is stored
 *   from what is matched (research.md D-408).
 * @param sectionCodes **leaf** classification codes —`1`, `2.1`…— never a parent. A parent chosen in
 *   the form is expanded to its children before it gets here (research.md D-407). Empty means every
 *   section.
 * @param organizationQuery free text matched as a normalised substring of the issuer hierarchy.
 *   `null` or blank means any organisation.
 * @param activeSince renewed on creation, edition and re-enabling. Together with the order of the
 *   synchronisation cycle, it is what keeps a rule from ever firing for the past (research.md D-405).
 */
data class AlertRule(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val matchMode: KeywordMatchMode,
    val sectionCodes: Set<String>,
    val organizationQuery: String?,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val activeSince: Long,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(keywords.size <= AlertRuleDraft.MAX_KEYWORDS) {
            "a rule takes at most ${AlertRuleDraft.MAX_KEYWORDS} keywords"
        }
        require(hasCriteria) { "a rule needs at least one positive criterion" }
    }

    /** At least one of the three groups narrows something. */
    val hasCriteria: Boolean
        get() = keywords.isNotEmpty() || sectionCodes.isNotEmpty() || !organizationQuery.isNullOrBlank()
}
