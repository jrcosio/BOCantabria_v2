package com.jrblanco.boccantabria.domain.model

/**
 * The local copy of a publication's official document.
 *
 * @param localPath where the file is. A `String` and not a `File` on purpose: the domain cannot see
 *   `java.io.*` without dragging the platform into tests that run without an emulator, and everyone
 *   above only needs to know where it is (research.md D-007).
 * @param checksum SHA-256 of what was received, in hexadecimal. Kept so a document can be proved to
 *   be the one that was downloaded. [UNKNOWN_CHECKSUM] when the copy is fine but its checksum was
 *   lost: the bytes were verified on arrival and the file is only ever visible complete, so a lost
 *   sidecar is a lost fingerprint, not a bad document (014 research.md D-602).
 * @param lastUsedAt basis of the cache's eviction by age.
 *
 * The source URL is deliberately absent: the [Publication] already holds it, and a second copy is a
 * second truth that can fall behind.
 */
data class OfficialDocument(
    val externalKey: String,
    val localPath: String,
    val byteCount: Long,
    val checksum: String,
    val lastUsedAt: Long,
) {
    init {
        require(externalKey.isNotBlank()) { "externalKey must not be blank" }
        require(localPath.isNotBlank()) { "localPath must not be blank" }
        require(byteCount > 0) { "byteCount must be positive, was: $byteCount" }
        require(isValidChecksum(checksum)) { "checksum must be a hex SHA-256, was: $checksum" }
    }

    companion object {
        private val CHECKSUM = Regex("^[0-9a-f]{64}$")

        /** Sixty-four zeros: what a copy whose sidecar was lost or is unreadable reports. */
        const val UNKNOWN_CHECKSUM = "0000000000000000000000000000000000000000000000000000000000000000"

        /**
         * The one rule for a checksum, shared with whoever stores one. Feature 014: the cache used to
         * hand back whatever its sidecar held and let this constructor throw on a truncated file —
         * outside every error boundary — which closed the application on each reopen (STAB-001).
         */
        fun isValidChecksum(value: String): Boolean = CHECKSUM.matches(value)
    }
}
