package com.jrblanco.boccantabria.fake

import androidx.compose.ui.graphics.ImageBitmap
import androidx.pdf.PdfDocument
import com.jrblanco.boccantabria.ui.pdf.PdfDocumentLoader
import io.mockk.mockk
import java.io.IOException

/**
 * Stands in for the PDF library, which needs a device and a second process to do anything.
 *
 * The document itself is a relaxed mock: what the tests care about is who opens it and, above all,
 * who closes it — not what it renders.
 */
class FakePdfDocumentLoader(
    var failOnOpen: Boolean = false,
) : PdfDocumentLoader {

    val opened = mutableListOf<String>()
    var document: PdfDocument = mockk(relaxed = true)

    override suspend fun open(localPath: String): PdfDocument {
        opened += localPath
        if (failOnOpen) throw IOException("no se puede abrir")
        return document
    }

    override suspend fun renderFirstPage(localPath: String, targetWidthPx: Int): ImageBitmap =
        throw IOException("la previsualización no se usa en estas pruebas")
}
