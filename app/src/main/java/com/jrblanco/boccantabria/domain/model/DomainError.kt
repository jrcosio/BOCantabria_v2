package com.jrblanco.boccantabria.domain.model

/**
 * Closed classification of the failures the presentation layer must tell apart.
 *
 * Sealed on purpose: the `when` that picks the message is exhaustive, so adding a case here
 * breaks compilation where it must be handled instead of failing silently at runtime.
 *
 * Exceptions never leave the data layer: they are caught there and translated into these.
 */
sealed interface DomainError {

    /** Content could not be fetched and no usable local fallback was available. */
    data object Network : DomainError

    /** Unclassified failure. Any unexpected exception is translated into this. */
    data object Unknown : DomainError
}
