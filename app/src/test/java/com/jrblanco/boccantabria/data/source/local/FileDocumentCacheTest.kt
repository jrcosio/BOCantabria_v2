package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.core.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The cache is where files get corrupted if anything is done carelessly, so its rules are checked
 * one by one: nothing half-written under the good name, nothing lost that is in use, and a file
 * name that a key cannot turn into a path.
 *
 * Plain JUnit with a temporary folder: no emulator, because none of this needs Android.
 */
class FileDocumentCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now: Long = 1_000_000

    private fun cache() = FileDocumentCache(
        root = folder.root,
        time = object : TimeProvider {
            override fun nowMillis(): Long = now
        },
    )

    private fun tempWith(bytes: Int, name: String = "in"): File =
        folder.newFile(name).apply { writeBytes(ByteArray(bytes) { it.toByte() }) }

    // ---------- Storing and reading ----------

    @Test
    fun `a stored document can be read back`() = runTest {
        val cache = cache()

        val stored = cache.put("boc:1", tempWith(128), byteCount = 128, checksum = "a".repeat(64))

        assertEquals(128L, stored.byteCount)
        assertTrue(File(stored.localPath).exists())
        assertEquals(stored.localPath, cache.get("boc:1")?.localPath)
    }

    @Test
    fun `a key that was never stored reads back as absent`() = runTest {
        assertNull(cache().get("boc:desconocida"))
    }

    @Test
    fun `reading refreshes the last use, which is what eviction by age relies on`() = runTest {
        val cache = cache()
        cache.put("boc:1", tempWith(64), 64, "a".repeat(64))

        now += 10_000
        val read = cache.get("boc:1")

        assertEquals(now, read?.lastUsedAt)
    }

    @Test
    fun `storing twice replaces the file instead of leaving two`() = runTest {
        val cache = cache()
        cache.put("boc:1", tempWith(64, "one"), 64, "a".repeat(64))

        val second = cache.put("boc:1", tempWith(200, "two"), 200, "b".repeat(64))

        assertEquals(200L, second.byteCount)
        assertEquals(1, documentsInStore())
    }

    // ---------- The file name ----------

    @Test
    fun `the file name is derived from the key, never the key itself`() = runTest {
        val cache = cache()

        // Real keys carry ':' and, when the link had no identifier, a whole URL with slashes.
        val awkward = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=1"
        val stored = cache.put(awkward, tempWith(64), 64, "a".repeat(64))

        val name = File(stored.localPath).name
        assertFalse("el nombre contiene la clave en crudo: $name", name.contains("/"))
        assertFalse(name.contains(":"))
        assertEquals(folder.root.resolve("documents"), File(stored.localPath).parentFile)
    }

    @Test
    fun `two different keys never collide`() = runTest {
        val cache = cache()

        val first = cache.put("boc:1", tempWith(64, "a"), 64, "a".repeat(64))
        val second = cache.put("boc:2", tempWith(64, "b"), 64, "b".repeat(64))

        assertTrue(first.localPath != second.localPath)
        assertEquals(2, documentsInStore())
    }

    @Test
    fun `the same key always maps to the same file`() = runTest {
        val cache = cache()

        assertEquals(cache.fileFor("boc:1"), cache.fileFor("boc:1"))
    }

    // ---------- Eviction ----------

    @Test
    fun `eviction by size removes the least recently used first`() = runTest {
        val cache = cache()
        cache.put("boc:viejo", tempWith(100, "v"), 100, "a".repeat(64))
        now += 1_000
        cache.put("boc:nuevo", tempWith(100, "n"), 100, "b".repeat(64))

        cache.evict(maxBytes = 150, inUse = emptySet())

        assertNull(cache.get("boc:viejo"))
        assertNotNull(cache.get("boc:nuevo"))
    }

    @Test
    fun `eviction never removes what is in use`() = runTest {
        val cache = cache()
        cache.put("boc:viejo", tempWith(100, "v"), 100, "a".repeat(64))
        now += 1_000
        cache.put("boc:nuevo", tempWith(100, "n"), 100, "b".repeat(64))

        // The oldest is the one on screen. Evicting it would pull the document from under the reader.
        cache.evict(maxBytes = 150, inUse = setOf("boc:viejo"))

        assertNotNull(cache.get("boc:viejo"))
        assertNull(cache.get("boc:nuevo"))
    }

    @Test
    fun `eviction does nothing when the store is within its budget`() = runTest {
        val cache = cache()
        cache.put("boc:1", tempWith(100), 100, "a".repeat(64))

        cache.evict(maxBytes = 10_000, inUse = emptySet())

        assertNotNull(cache.get("boc:1"))
    }

    @Test
    fun `eviction on an empty store is not an error`() = runTest {
        cache().evict(maxBytes = 100, inUse = emptySet())

        assertEquals(0, documentsInStore())
    }

    // ---------- Leftovers ----------

    @Test
    fun `a half written temporary is not visible as a document`() = runTest {
        val cache = cache()
        // What an interrupted download leaves behind: the temporary, never the good name.
        cache.temporaryFor("boc:1").apply { parentFile?.mkdirs() }.writeBytes(ByteArray(10))

        assertNull(cache.get("boc:1"))
    }

    @Test
    fun `discarding a temporary removes it`() = runTest {
        val cache = cache()
        val temp = cache.temporaryFor("boc:1").apply { parentFile?.mkdirs() }
        temp.writeBytes(ByteArray(10))

        cache.discardTemporary("boc:1")

        assertFalse(temp.exists())
    }

    private fun documentsInStore(): Int =
        folder.root.resolve("documents").listFiles()?.count { it.name.endsWith(".pdf") } ?: 0
}
