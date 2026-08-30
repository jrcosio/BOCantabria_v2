package com.jrblanco.boccantabria.ui.pdf

import android.content.Context
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import java.io.File

/**
 * Opens a local file as a document the viewer can render.
 *
 * The one seam between the application and the PDF library. Everything above this speaks of file
 * paths; only this package knows that `androidx.pdf` exists, which is what keeps a beta API from
 * spreading through the codebase (research.md D-014).
 *
 * The underlying loader renders in a **separate, sandboxed process**. That is not incidental: the
 * documents come from a public service over the internet, and a malformed one should not be able
 * to take the application down with it.
 */
interface PdfDocumentLoader {

    /** @throws java.io.IOException when the file is missing, unreadable or not a document. */
    suspend fun open(localPath: String): PdfDocument

    /**
     * The first page, drawn at [targetWidthPx] and closed again.
     *
     * Returns a Compose bitmap rather than the library's own types so the preview component stays
     * ignorant of `androidx.pdf`, and the same renderer serves both the preview and the viewer
     * (research.md D-011).
     */
    suspend fun renderFirstPage(localPath: String, targetWidthPx: Int): ImageBitmap
}

class SandboxedPdfDocumentLoader(
    private val context: Context,
    dispatchers: DispatcherProvider,
) : PdfDocumentLoader {

    private val loader = SandboxedPdfLoader(context, dispatchers.io)

    override suspend fun open(localPath: String): PdfDocument =
        loader.openDocument(File(localPath).toUri())

    override suspend fun renderFirstPage(localPath: String, targetWidthPx: Int): ImageBitmap {
        val document = open(localPath)
        // Closed whatever happens: the pages are rendered in another process, and leaking the
        // handle would keep that process alive holding a file the cache may want to evict.
        return document.use { open ->
            val page = open.getPageInfo(FIRST_PAGE)
            val height = (targetWidthPx.toFloat() * page.height / page.width).toInt()
            open.getPageBitmapSource(FIRST_PAGE).use { source ->
                source.getBitmap(Size(targetWidthPx, height)).asImageBitmap()
            }
        }
    }

    private companion object {
        const val FIRST_PAGE = 0
    }
}

/**
 * Built here rather than in `core/di` for the same reason as Room and OkHttp: an architecture rule
 * keeps third-party SDKs out of the dependency-injection package.
 */
fun pdfDocumentLoader(context: Context, dispatchers: DispatcherProvider): PdfDocumentLoader =
    SandboxedPdfDocumentLoader(context, dispatchers)
