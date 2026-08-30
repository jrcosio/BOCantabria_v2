package com.jrblanco.boccantabria.ui.pdf

import androidx.pdf.PdfDocument
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.OfficialDocument

/**
 * What the viewer draws.
 *
 * [Ready] carries the **open** document rather than a path because opening it is the expensive,
 * failable step, and doing it inside the composition would reopen the file on every configuration
 * change and leak the handle. The view model owns it and closes it (research.md D-009).
 *
 * The visible page and the zoom are not here: they belong to the viewer's own state. Only the
 * first visible page is saved by hand, so a rotation does not send the reader back to page one.
 */
sealed interface PdfViewerUiState {

    data object Loading : PdfViewerUiState

    data class Ready(
        val pdf: PdfDocument,
        val document: OfficialDocument,
        val title: String,
    ) : PdfViewerUiState

    data class Error(val error: DomainError) : PdfViewerUiState
}
