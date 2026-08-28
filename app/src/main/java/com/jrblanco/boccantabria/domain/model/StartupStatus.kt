package com.jrblanco.boccantabria.domain.model

/**
 * Conclusion of the startup preparation.
 *
 * Sealed so the `when` that decides what to show is exhaustive: adding a reason to block the
 * application breaks compilation wherever it must be handled.
 */
sealed interface StartupStatus {

    /** The application can continue to the main content. */
    data object Ready : StartupStatus

    /** The installed version is below the minimum the service supports. */
    data object UpdateRequired : StartupStatus

    /** The service published a maintenance notice. */
    data class Maintenance(val message: String) : StartupStatus
}
