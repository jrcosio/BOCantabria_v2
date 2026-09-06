package com.jrblanco.boccantabria.domain.model

/**
 * One publication to deliver, with every rule it matched.
 *
 * Grouped by publication before delivery so that a publication matching two rules produces **one**
 * notification naming both (FR-043, FR-046).
 */
data class AlertNotification(
    val publication: Publication,
    val ruleNames: List<String>,
) {
    init {
        require(ruleNames.isNotEmpty()) { "a notification names at least one rule" }
    }
}
