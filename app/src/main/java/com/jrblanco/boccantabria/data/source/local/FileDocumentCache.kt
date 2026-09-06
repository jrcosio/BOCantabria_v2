package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.domain.model.OfficialDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/**
 * The document cache, on the file system.
 *
 * Two decisions carry the weight here:
 *
 * - **The file name is a digest of the key, never the key.** Keys look like `boc:439765` and, when
 *   a link carried no identifier, like a whole URL. Letting either reach a path is a well-known way
 *   of writing where one should not.
 * - **A download writes to `<name>.part` and is renamed at the end.** A rename within one file
 *   system is atomic, so a file under the good name is always complete. An interrupted download
 *   leaves a temporary, and a temporary is never mistaken for a document.
 * - **The checksum sidecar follows the same rule, and is written first.** Since feature 014 the
 *   sidecar goes through its own `.part` and rename, **before** the document is moved into place, so
 *   a document under its good name always has a complete sidecar beside it. And a sidecar that exists
 *   but is not a checksum — empty, cut short, upper case — reads as a lost fingerprint, never as a
 *   reason to throw. Until then a plain `writeText`, interrupted by a full disk or a dying process,
 *   left a truncated file that made the model's `require` close the application on every reopen
 *   (audit finding STAB-001; research.md D-601 to D-603).
 *
 * Last use is kept as the file's modification time rather than in a table: it is one fact, the file
 * system already stores it, and a row that outlives its file is a lie someone has to reconcile.
 */
class FileDocumentCache(
    private val root: File,
    private val time: TimeProvider,
) : DocumentCache {

    private val mutex = Mutex()

    private val store: File get() = File(root, DIRECTORY).apply { mkdirs() }

    override fun fileFor(externalKey: String): File = File(store, "${digest(externalKey)}$EXTENSION")

    override fun temporaryFor(externalKey: String): File =
        File(store, "${digest(externalKey)}$EXTENSION$TEMPORARY_SUFFIX")

    override suspend fun get(externalKey: String): OfficialDocument? = mutex.withLock {
        val file = fileFor(externalKey)
        if (!file.isFile || file.length() == 0L) return null

        val now = time.nowMillis()
        file.setLastModified(now)
        OfficialDocument(
            externalKey = externalKey,
            localPath = file.absolutePath,
            byteCount = file.length(),
            checksum = readChecksum(externalKey) ?: OfficialDocument.UNKNOWN_CHECKSUM,
            lastUsedAt = now,
        )
    }

    override suspend fun put(
        externalKey: String,
        temporary: File,
        byteCount: Long,
        checksum: String,
    ): OfficialDocument = mutex.withLock {
        val destination = fileFor(externalKey)

        // Sidecar first. The checksum has a consumer — the AI summary compares it with the one it
        // stored to decide whether a summary is stale — and the other order left a window in which a
        // visible document had no sidecar yet, read as UNKNOWN_CHECKSUM, and a good summary was
        // regenerated for nothing (D-603).
        writeChecksum(externalKey, checksum)
        destination.delete()
        if (!temporary.renameTo(destination)) {
            // No document, no promise about one.
            checksumFileFor(externalKey).delete()
            error("could not move the verified document into place: ${temporary.absolutePath}")
        }

        val now = time.nowMillis()
        destination.setLastModified(now)

        OfficialDocument(
            externalKey = externalKey,
            localPath = destination.absolutePath,
            byteCount = byteCount,
            checksum = checksum,
            lastUsedAt = now,
        )
    }

    override suspend fun evict(maxBytes: Long, inUse: Set<String>) = mutex.withLock {
        val protected = inUse.map { digest(it) }.toSet()
        val documents = store.listFiles()
            ?.filter { it.isFile && it.name.endsWith(EXTENSION) }
            ?.sortedBy { it.lastModified() }
            .orEmpty()

        var total = documents.sumOf { it.length() }
        for (file in documents) {
            if (total <= maxBytes) break
            if (file.nameWithoutExtension in protected) continue
            val size = file.length()
            if (file.delete()) {
                File(store, "${file.nameWithoutExtension}$CHECKSUM_SUFFIX").delete()
                total -= size
            }
        }

        // What a sidecar write that never finished leaves behind. Nothing reads it, so it can go.
        store.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith("$CHECKSUM_SUFFIX$TEMPORARY_SUFFIX") }
            .forEach { it.delete() }
    }

    override suspend fun discardTemporary(externalKey: String) {
        mutex.withLock { temporaryFor(externalKey).delete() }
    }

    /**
     * The checksum lives beside the file rather than in the database, for the same reason the last
     * use does: one fact, one place, and nothing to reconcile when the system clears the cache.
     *
     * Written through a temporary and a rename, like the document: a plain write cut short by a full
     * disk or a dying process is exactly how the truncated sidecar of STAB-001 came to exist.
     */
    private fun writeChecksum(externalKey: String, checksum: String) {
        val target = checksumFileFor(externalKey)
        val part = File(target.path + TEMPORARY_SUFFIX)
        part.writeText(checksum)
        target.delete()
        if (!part.renameTo(target)) {
            part.delete()
            error("could not move the checksum into place: ${part.absolutePath}")
        }
    }

    /**
     * `null` for a sidecar that is missing **or that is not a checksum**. Both are a lost fingerprint,
     * and the caller reports both as [OfficialDocument.UNKNOWN_CHECKSUM]. Validated with the model's
     * own rule, so the cache can never hand the model something the model would refuse (D-602).
     */
    private fun readChecksum(externalKey: String): String? = checksumFileFor(externalKey)
        .takeIf { it.isFile }
        ?.readText()
        ?.trim()
        ?.takeIf { OfficialDocument.isValidChecksum(it) }

    private fun checksumFileFor(externalKey: String): File =
        File(store, "${digest(externalKey)}$CHECKSUM_SUFFIX")

    private fun digest(externalKey: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(externalKey.toByteArray(Charsets.UTF_8))
        .take(NAME_BYTES)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIRECTORY = "documents"
        const val EXTENSION = ".pdf"
        const val TEMPORARY_SUFFIX = ".part"
        const val CHECKSUM_SUFFIX = ".sha256"

        /** Sixteen bytes of the digest: thirty-two hex characters, and no realistic collision. */
        const val NAME_BYTES = 16
    }
}
