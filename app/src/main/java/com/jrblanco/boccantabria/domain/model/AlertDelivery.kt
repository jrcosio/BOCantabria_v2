package com.jrblanco.boccantabria.domain.model

/**
 * Which channel a cycle's matches went through. Exactly one per cycle (FR-052).
 *
 * [NONE] when there was nothing to deliver: no new publications, no rules, or a baseline
 * synchronisation.
 */
enum class AlertDelivery {
    NONE,
    IN_APP,
    SYSTEM,
}
