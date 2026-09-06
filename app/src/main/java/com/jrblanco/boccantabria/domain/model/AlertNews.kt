package com.jrblanco.boccantabria.domain.model

/**
 * One publication that matched, as the Novedades tab shows it.
 *
 * A publication, not a match: when two rules catch the same announcement the person sees one row
 * naming both, and the unread counter counts it once (FR-003).
 *
 * @param detectedAt the earliest match, which is when the person could first have known.
 */
data class AlertNews(
    val publication: Publication,
    val ruleNames: List<String>,
    val detectedAt: Long,
    val isRead: Boolean,
) {
    init {
        require(ruleNames.isNotEmpty()) { "a piece of news names at least one rule" }
    }
}
