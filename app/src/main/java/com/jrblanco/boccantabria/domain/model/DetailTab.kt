package com.jrblanco.boccantabria.domain.model

/**
 * The two tabs of the detail screen.
 *
 * A domain type rather than a drawing detail: both are part of what the feature promises, and one
 * of them is a promise for later.
 *
 * Asking about the document used to be a third tab. It is a **screen** now, reached from the action
 * bar: a conversation needs the whole screen and its own place in the back stack, which is not what
 * a tab beside a metadata card is for.
 */
enum class DetailTab {
    DOCUMENT,
    AI_SUMMARY,
    ;

    /** Whether the tab's content is still to come. */
    val isComingSoon: Boolean get() = this != DOCUMENT
}
