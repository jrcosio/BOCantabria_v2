package com.jrblanco.boccantabria.data.source.local

import androidx.test.platform.app.InstrumentationRegistry
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.DefaultDispatcherProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The second frontier with `androidx.pdf`, against real documents.
 *
 * It replaces the text extractor's test, and it is much smaller because what is left to answer is
 * much smaller: how many pages, and whether the document is locked. Both matter beyond their size —
 * the page count is what lets the validator throw away a citation to a page that does not exist, and
 * the lock is the one thing that still stops a document from leaving the device (010 D-205).
 *
 * The two documents are minimal PDFs written by hand in `app/src/androidTest/assets`, so they are
 * deterministic and tiny — no generator, no library, nothing to drift.
 */
class AndroidxPdfPageCounterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun it_counts_the_pages_of_a_document() = runTest {
        val result = counter().pageCount(asset("sample_with_text.pdf").absolutePath)

        assertEquals(PageCountResult.Success(totalPages = 2), result)
    }

    /**
     * **FR-002, and the change this feature is about.** A document with no text layer used to be
     * refused here, because what travelled was the text. It is a valid document now: it has pages,
     * it gets counted, and the service reads it.
     */
    @Test
    fun a_document_without_a_text_layer_is_counted_like_any_other() = runTest {
        val result = counter().pageCount(asset("sample_without_text.pdf").absolutePath)

        assertTrue(result.toString(), result is PageCountResult.Success)
        assertTrue((result as PageCountResult.Success).totalPages >= 1)
    }

    /** Nothing throws out of here: a missing file is an outcome, not an exception. */
    @Test
    fun a_missing_file_is_a_failure_and_not_a_crash() = runTest {
        val result = counter().pageCount(folder.root.resolve("no-existe.pdf").absolutePath)

        assertTrue(result is PageCountResult.Failure)
    }

    /**
     * The real dispatchers, not a test one. The work here genuinely crosses to another process, and
     * pretending otherwise would be testing something the application does not do.
     */
    private fun counter() =
        AndroidxPdfPageCounter(context, DefaultDispatcherProvider(), NoOpCrashReporter())

    /** Copied out of the test assets, because the counter takes a path on the file system. */
    private fun asset(name: String): File {
        val target = folder.newFile(name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            target.outputStream().use(input::copyTo)
        }
        return target
    }
}
