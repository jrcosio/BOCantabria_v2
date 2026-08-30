package com.jrblanco.boccantabria.ui.saved

import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.ui.share.ShareState

/**
 * Everything the saved screen draws.
 *
 * [share] and [saveFailed] sit **outside** [content] for the same reason they do on the bulletin:
 * they are independent axes. Something can be being prepared for sharing with the list on screen,
 * and a failed write is not a kind of content.
 */
data class SavedUiState(
    val content: SavedContentState = SavedContentState.Empty,
    val share: ShareState = ShareState.Idle,
    /**
     * A one-shot signal: the screen says so out loud and clears it (FR-009). The other half of that
     * requirement comes for free — the bookmark is derived from what is stored, so a write that
     * failed leaves the icon exactly as it was.
     */
    val saveFailed: Boolean = false,
)

/**
 * What the list holds.
 *
 * **There is no `Skeleton` and no `Error`**, and that is a decision rather than an omission: what is
 * read is local and immediate, so there is no wait to cushion, and a read failure already emits an
 * empty list by contract. Inventing the two states would force the screen to tell "empty" from
 * "could not read" with nothing to tell them apart.
 */
sealed interface SavedContentState {

    data class Publications(val items: List<Publication>) : SavedContentState

    /** Nothing saved. A normal state, not a failure. */
    data object Empty : SavedContentState
}
