package com.jrblanco.boccantabria.ui.share

import com.jrblanco.boccantabria.domain.model.ShareTarget

/**
 * How far along a share is, wherever it was started from.
 *
 * Shared by the bulletin, the detail screen and the viewer because sharing means the same thing in
 * all three: fetch the document if needed, then hand it over. Three copies of this would be three
 * chances to disagree about it.
 */
sealed interface ShareState {

    data object Idle : ShareState

    /** Fetching the document so it can be shared. The screen says so (FR-032). */
    data object Preparing : ShareState

    /**
     * A one-shot event: the screen hands it to the system and calls back to clear it. Left in
     * place, a configuration change would open the share sheet again.
     *
     * [subject] travels with the target so the screen does not have to look the publication up
     * again to name what it is sending.
     */
    data class Ready(val target: ShareTarget, val subject: String) : ShareState
}
