package com.jrblanco.boccantabria.domain.model

/**
 * The fact that one new publication satisfied one rule at one instant.
 *
 * One per rule–publication pair, never two: the store enforces it with a unique index, and only
 * what the store really inserted gets delivered (research.md D-410).
 */
data class AlertMatch(
    val ruleId: String,
    val externalKey: String,
    val matchedAt: Long,
) {
    init {
        require(ruleId.isNotBlank()) { "ruleId must not be blank" }
        require(externalKey.isNotBlank()) { "externalKey must not be blank" }
    }
}
