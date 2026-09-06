package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.domain.model.OfficialDocument
import java.io.File

/**
 * Where the local copies of the official documents live.
 *
 * A cache and not a library: what it holds may vanish — the system clears it when space runs short,
 * and [evict] does the same on purpose — and nothing is lost when it does, because the document can
 * be fetched again. Keeping publications for offline reading is the Saved feature, still to come.
 */
interface DocumentCache {

    /**
     * The stored document, refreshing its last use, or `null` if it is not there.
     *
     * Never throws for a malformed entry: a checksum sidecar that exists but is not a valid checksum
     * reads as [OfficialDocument.UNKNOWN_CHECKSUM], exactly like a missing one. The document's bytes
     * were verified when they arrived and the file is only ever visible complete; what was lost is
     * the fingerprint, not the document (feature 014, STAB-001).
     */
    suspend fun get(externalKey: String): OfficialDocument?

    /** Moves a verified temporary into place. The move is what makes it visible. */
    suspend fun put(
        externalKey: String,
        temporary: File,
        byteCount: Long,
        checksum: String,
    ): OfficialDocument

    /** Frees space down to [maxBytes], oldest use first, never touching [inUse]. */
    suspend fun evict(maxBytes: Long, inUse: Set<String>)

    /** Where a document would live. Deterministic for a given key. */
    fun fileFor(externalKey: String): File

    /** Where a download in progress writes. Never visible as a document. */
    fun temporaryFor(externalKey: String): File

    /** Removes a leftover temporary. Called on every failing path. */
    suspend fun discardTemporary(externalKey: String)
}
