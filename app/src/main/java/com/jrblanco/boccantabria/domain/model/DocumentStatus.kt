package com.jrblanco.boccantabria.domain.model

/**
 * How far along the local copy of a document is. What the detail screen and the viewer observe.
 */
sealed interface DocumentStatus {

    /** Never asked for, or evicted from the cache. */
    data object Absent : DocumentStatus

    /**
     * @param totalBytes `null` when the service does not declare a length. The progress bar is then
     *   indeterminate, which is the truth rather than a guess.
     */
    data class Downloading(val bytesRead: Long, val totalBytes: Long?) : DocumentStatus {
        init {
            require(bytesRead >= 0) { "bytesRead must not be negative" }
            require(totalBytes == null || totalBytes >= 0) { "totalBytes must not be negative" }
        }

        /** `null` when the total is unknown: a caller must not invent one. */
        val fraction: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toFloat() / it).coerceIn(0f, 1f) }
    }

    data class Available(val document: OfficialDocument) : DocumentStatus

    data class Failed(val error: DomainError) : DocumentStatus
}
