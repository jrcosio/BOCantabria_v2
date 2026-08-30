package com.jrblanco.boccantabria.ui.detail

import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.ShareTarget

/**
 * Everything the detail screen draws.
 *
 * [document] and [share] sit outside a single sealed hierarchy on purpose, as on the home screen:
 * something can be being prepared for sharing while the document is already available, and folding
 * both into one type would multiply its cases without anyone gaining from it.
 */
data class PublicationDetailUiState(
    val publication: Publication? = null,
    val section: BocSection? = null,
    val isMissing: Boolean = false,
    val selectedTab: DetailTab = DetailTab.DOCUMENT,
    val document: DocumentStatus = DocumentStatus.Absent,
    val share: ShareState = ShareState.Idle,
) {
    /** Still reading the stored copy: neither found nor known to be gone. */
    val isLoading: Boolean get() = publication == null && !isMissing
}

sealed interface ShareState {

    data object Idle : ShareState

    /** Fetching the document so it can be shared. The screen says so (FR-032). */
    data object Preparing : ShareState

    /**
     * A one-shot event: the screen hands it to the system and calls back to clear it. Left in
     * place, a configuration change would open the share sheet again.
     */
    data class Ready(val target: ShareTarget) : ShareState
}
