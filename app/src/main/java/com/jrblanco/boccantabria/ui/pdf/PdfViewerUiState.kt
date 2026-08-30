package com.jrblanco.boccantabria.ui.pdf

import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.OfficialDocument

/**
 * What the viewer draws.
 *
 * The visible page and the zoom are **not** here: they belong to the viewer's own state. The only
 * thing saved by hand is the first visible page, so a configuration change does not send the reader
 * back to page one (research.md D-010).
 */
sealed interface PdfViewerUiState {

    data object Loading : PdfViewerUiState

    data class Ready(val document: OfficialDocument, val title: String) : PdfViewerUiState

    data class Error(val error: DomainError) : PdfViewerUiState
}
