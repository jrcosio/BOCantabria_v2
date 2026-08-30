package com.jrblanco.boccantabria.ui.pdf

import android.content.Context
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
}

class SandboxedPdfDocumentLoader(
    private val context: Context,
    dispatchers: DispatcherProvider,
) : PdfDocumentLoader {

    private val loader = SandboxedPdfLoader(context, dispatchers.io)

    override suspend fun open(localPath: String): PdfDocument =
        loader.openDocument(File(localPath).toUri())
}

/**
 * Built here rather than in `core/di` for the same reason as Room and OkHttp: an architecture rule
 * keeps third-party SDKs out of the dependency-injection package.
 */
fun pdfDocumentLoader(context: Context, dispatchers: DispatcherProvider): PdfDocumentLoader =
    SandboxedPdfDocumentLoader(context, dispatchers)
