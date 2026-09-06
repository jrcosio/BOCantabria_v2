package com.jrblanco.boccantabria.domain.model

/**
 * Whether Android will show this application's notifications.
 *
 * Three states, not two: [NEEDS_REQUEST] is the one the form acts on —the permission exists and was
 * never asked for— and [DISABLED] is the one the banner acts on (research.md D-427).
 */
enum class NotificationStatus {
    GRANTED,
    NEEDS_REQUEST,
    DISABLED,
}
