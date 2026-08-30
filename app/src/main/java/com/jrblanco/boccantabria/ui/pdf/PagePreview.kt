package com.jrblanco.boccantabria.ui.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

/** The first page of a document, as the preview sees it. */
@Immutable
sealed interface PagePreview {

    data object Loading : PagePreview

    data class Ready(val bitmap: ImageBitmap) : PagePreview

    data object Failed : PagePreview
}

/**
 * Renders the first page of [localPath], once, off the main thread.
 *
 * A failure here is deliberately just [PagePreview.Failed] and not an error screen: the document
 * downloaded correctly and can still be opened in the viewer. Only the picture is missing, and a
 * red banner over a working document would be a lie.
 */
@Composable
fun rememberFirstPage(
    localPath: String,
    targetWidthPx: Int,
    loader: PdfDocumentLoader = koinInject(),
): State<PagePreview> = produceState<PagePreview>(PagePreview.Loading, localPath, targetWidthPx) {
    value = PagePreview.Loading
    value = try {
        PagePreview.Ready(loader.renderFirstPage(localPath, targetWidthPx))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        PagePreview.Failed
    }
}
