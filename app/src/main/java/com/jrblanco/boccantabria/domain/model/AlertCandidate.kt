package com.jrblanco.boccantabria.domain.model

/**
 * A stored publication the alerts have not evaluated yet, and when the application first stored it.
 *
 * Since feature 014 the cycle evaluates the alerts against what the store still marks as pending,
 * not against the keys the last refresh happened to insert. A match whose recording failed, or a
 * process that died between storing the bulletin and recording the match, leaves the publication
 * pending, and the next cycle picks it up — once (audit finding STAB-003; research.md D-607).
 *
 * [isVisibleTo] is «never retroactive», stated once. The order of the cycle — rules read before the
 * refresh — guarantees it for what this cycle inserted, and says nothing about a leftover: a rule
 * may have been created between the two cycles, after the publication was stored. Comparing the two
 * instants covers both cases with one rule (D-609).
 */
data class AlertCandidate(
    val publication: Publication,
    val storedAt: Long,
) {
    init {
        require(storedAt > 0) { "storedAt must be a positive instant, was: $storedAt" }
    }

    /**
     * Whether [rule] may fire for this publication: only if the rule was already active when the
     * publication was stored. `<=` and not `<`: storing and activating within the same millisecond
     * — which every frozen-clock test does — counts as "already active".
     */
    fun isVisibleTo(rule: AlertRule): Boolean = rule.activeSince <= storedAt
}
