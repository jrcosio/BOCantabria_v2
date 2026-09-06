package com.jrblanco.boccantabria.domain.model

/**
 * A rule together with what its card says about it.
 *
 * Kept apart from [AlertRule] because these two numbers are not configuration: they are derived
 * from the matches and depend on the caller's local day.
 *
 * @param matchesToday matches recorded since the start of the day the caller asked about.
 */
data class AlertRuleOverview(
    val rule: AlertRule,
    val lastMatchedAt: Long?,
    val matchesToday: Int,
) {
    init {
        require(matchesToday >= 0) { "matchesToday must not be negative" }
    }
}
