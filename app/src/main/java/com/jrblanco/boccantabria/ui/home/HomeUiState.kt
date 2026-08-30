package com.jrblanco.boccantabria.ui.home

import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SectionColorGroup
import com.jrblanco.boccantabria.ui.share.ShareState

/**
 * Everything the home screen draws.
 *
 * [isRefreshing] and [isOffline] sit **outside** [content] on purpose. They are independent axes:
 * a refresh happens with content on screen, and being offline is a way of showing content, not a
 * way of failing to. Folding them into the sealed hierarchy would multiply its cases and force
 * the screen to decide which of its errors are really content.
 *
 * [savedKeys] and [saveFailed] are there for the same reason: which publications are saved cuts
 * across every content state, and a failed write is not a kind of content.
 */
data class HomeUiState(
    val selection: HomeSelection = HomeSelection.TodaysBulletin,
    val header: BulletinHeaderData = BulletinHeaderData.EMPTY,
    val chips: List<SectionChip> = emptyList(),
    val content: HomeContentState = HomeContentState.Skeleton,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val share: ShareState = ShareState.Idle,
    /** The keys of everything saved. A card asks whether its own is in here. */
    val savedKeys: Set<String> = emptySet(),
    /** One-shot: the screen says so and clears it (FR-009). */
    val saveFailed: Boolean = false,
)

sealed interface HomeContentState {

    /** First run with nothing stored: placeholders shaped like the content to come. */
    data object Skeleton : HomeContentState

    data class Publications(val items: List<Publication>) : HomeContentState

    /** The selection holds nothing. Not an error: several sections are legitimately quiet. */
    data object Empty : HomeContentState

    /** Nothing to show and the synchronisation failed. The only case that offers a retry. */
    data class Error(val error: DomainError) : HomeContentState
}

/**
 * One quick filter, for one of the nine sections.
 *
 * The chip that returns to the day's bulletin is not here: its label is interface copy, so the
 * screen adds it. This carries only what the domain knows.
 */
data class SectionChip(
    val code: String,
    val label: String,
    val colorGroup: SectionColorGroup,
    val isSelected: Boolean,
)
