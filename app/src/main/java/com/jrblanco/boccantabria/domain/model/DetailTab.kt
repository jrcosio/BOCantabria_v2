package com.jrblanco.boccantabria.domain.model

/**
 * The two tabs of the detail screen.
 *
 * A domain type rather than a drawing detail: both are part of what the feature promises.
 *
 * Asking about the document used to be a third tab. It is a **screen** now, reached from the action
 * bar: a conversation needs the whole screen and its own place in the back stack, which is not what
 * a tab beside a metadata card is for.
 *
 * This used to carry an `isComingSoon` property. Feature 007 filled the summary tab, so the property
 * could only ever have returned false, and a claim that cannot be true is only there to mislead
 * whoever reads it next (research.md D-029).
 */
enum class DetailTab {
    DOCUMENT,
    AI_SUMMARY,
}
