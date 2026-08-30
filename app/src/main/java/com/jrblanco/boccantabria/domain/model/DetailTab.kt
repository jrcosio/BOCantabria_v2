package com.jrblanco.boccantabria.domain.model

/**
 * The three tabs of the detail screen.
 *
 * A domain type rather than a drawing detail: the three are part of what the feature promises, and
 * two of them are promises for later.
 */
enum class DetailTab {
    DOCUMENT,
    AI_SUMMARY,
    ASK,
    ;

    /** Whether the tab's content is still to come. */
    val isComingSoon: Boolean get() = this != DOCUMENT
}
