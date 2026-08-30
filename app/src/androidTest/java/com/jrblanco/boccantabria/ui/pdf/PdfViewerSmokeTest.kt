package com.jrblanco.boccantabria.ui.pdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument as PdfWriter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.rememberPdfViewerState
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import androidx.pdf.ExperimentalPdfApi
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The test that decided this feature.
 *
 * The viewer is what forced raising the minimum supported Android version, and that is an amendment
 * to the project's rules. Before writing anything on top of it, this proves on a real device that
 * the library opens a document and renders it. If it ever stops doing so, the amendment loses its
 * reason and that is worth finding out here rather than three screens later.
 */
@OptIn(ExperimentalPdfApi::class)
class PdfViewerSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_library_opens_a_document_and_reports_its_pages() = runBlocking {
        val file = writePdf(pages = 3)

        val document = loader().open(file.absolutePath)

        assertTrue("se esperaban 3 páginas, hubo ${document.pageCount}", document.pageCount == 3)
        document.close()
    }

    @Test
    fun the_viewer_renders_the_document() = runBlocking {
        val document = loader().open(writePdf(pages = 1).absolutePath)

        composeRule.setContent {
            BOCantabriaTheme {
                val state = rememberPdfViewerState()
                PdfViewer(
                    pdfDocument = document,
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TAG),
                )
            }
        }

        composeRule.onNodeWithTag(TAG).assertIsDisplayed()
        document.close()
    }

    /** A real PDF, written by the platform. Cheaper and clearer than carrying a binary fixture. */
    private fun writePdf(pages: Int): File {
        val writer = PdfWriter()
        repeat(pages) { index ->
            val info = PdfWriter.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = writer.startPage(info)
            page.canvas.drawText(
                "Boletín Oficial de Cantabria — página ${index + 1}",
                MARGIN.toFloat(),
                MARGIN.toFloat(),
                Paint().apply { textSize = TEXT_SIZE },
            )
            writer.finishPage(page)
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "smoke-$pages.pdf")
        file.outputStream().use(writer::writeTo)
        writer.close()
        return file
    }

    private fun loader() = pdfDocumentLoader(
        context = ApplicationProvider.getApplicationContext(),
        dispatchers = RealDispatchers,
    )

    /** The loader talks to a service in another process, so it needs a real dispatcher. */
    private object RealDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Main
        override val io: CoroutineDispatcher get() = Dispatchers.IO
        override val default: CoroutineDispatcher get() = Dispatchers.Default
    }

    private companion object {
        const val TAG = "pdf_viewer_smoke"
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 60
        const val TEXT_SIZE = 14f
    }
}
