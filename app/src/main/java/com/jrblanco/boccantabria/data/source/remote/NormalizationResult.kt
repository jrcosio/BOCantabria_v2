package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.Publication

/**
 * What became of one announcement.
 *
 * A rejection carries its reason so the synchronisation can count and report it. Rejecting one
 * announcement never stops the rest of its source.
 */
sealed interface NormalizationResult {

    data class Accepted(val publication: Publication) : NormalizationResult

    data class Rejected(val reason: RejectionReason) : NormalizationResult
}

/** Why an announcement could not be accepted. Only these three are fatal. */
enum class RejectionReason {
    BLANK_TITLE,
    INVALID_LINK,
    INVALID_DATE,
}
