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
 * This is the test that answers the risk written down in `research.md` D-001: the library's text
 * extraction cannot be queried for support beforehand, so the only way to know it works is to run
 * it on a device. If it ever came back empty here, the way out is a different implementation behind
 * `PdfTextExtractor` and nothing else changes.
 *
 * The two documents are minimal PDFs written by hand in `app/src/androidTest/assets`, so they are
 * deterministic and tiny — no generator, no library, nothing to drift.
 */
class AndroidxPdfTextExtractorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun it_reads_the_text_of_every_page_in_order() = runTest {
        val result = extractor().extract(
            localPath = asset("sample_with_text.pdf").absolutePath,
            externalKey = "boc:439765",
            pdfSha256 = "a".repeat(64),
        )

        val corpus = (result as PdfExtractionResult.Success).corpus
        assertEquals(2, corpus.totalPages)
        // Numbered from 1 outwards, whatever the library counts from.
        assertEquals(listOf(1, 2), corpus.pages.map { it.pageNumber })
        assertTrue(corpus.pages[0].text.contains("AYUNTAMIENTO DE PIELAGOS"))
        assertTrue(corpus.pages[0].text.contains("quince dias habiles"))
        assertTrue(corpus.pages[1].text.contains("recurso contencioso"))
        assertTrue(corpus.pages[1].text.contains("12.000,00 euros"))
    }

    /** Pages must not bleed into each other, or a page reference stops meaning anything. */
    @Test
    fun a_page_never_carries_the_text_of_another() = runTest {
        val result = extractor().extract(
            localPath = asset("sample_with_text.pdf").absolutePath,
            externalKey = "boc:439765",
            pdfSha256 = "a".repeat(64),
        )

        val corpus = (result as PdfExtractionResult.Success).corpus
        assertTrue(!corpus.pages[0].text.contains("recurso contencioso"))
        assertTrue(!corpus.pages[1].text.contains("AYUNTAMIENTO DE PIELAGOS"))
    }

    /**
     * FR-012 and SC-005. A document that is all image does not fail to extract: it returns empty
     * strings. This is the check that keeps a contextless request from ever costing quota.
     */
    @Test
    fun a_document_without_a_text_layer_is_reported_rather_than_sent() = runTest {
        val result = extractor().extract(
            localPath = asset("sample_without_text.pdf").absolutePath,
            externalKey = "boc:1",
            pdfSha256 = "b".repeat(64),
        )

        assertEquals(PdfExtractionResult.NoExtractableText, result)
    }

    /** Nothing throws out of here: a missing file is an outcome, not an exception. */
    @Test
    fun a_missing_file_is_a_failure_and_not_a_crash() = runTest {
        val result = extractor().extract(
            localPath = folder.root.resolve("no-existe.pdf").absolutePath,
            externalKey = "boc:1",
            pdfSha256 = "c".repeat(64),
        )

        assertTrue(result is PdfExtractionResult.Failure)
    }

        /**
     * The real dispatchers, not a test one. The work here genuinely crosses to another process, and
     * pretending otherwise would be testing something the application does not do.
     */
    private fun extractor() =
        AndroidxPdfTextExtractor(context, DefaultDispatcherProvider(), NoOpCrashReporter())

    /** Copied out of the test assets, because the extractor takes a path on the file system. */
    private fun asset(name: String): File {
        val target = folder.newFile(name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            target.outputStream().use(input::copyTo)
        }
        return target
    }
}
