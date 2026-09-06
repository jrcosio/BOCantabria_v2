package com.jrblanco.boccantabria.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.EmptyMessage
import com.jrblanco.boccantabria.core.ui.component.PublicationCard
import com.jrblanco.boccantabria.core.ui.component.SaveFailureToast
import com.jrblanco.boccantabria.core.ui.component.ErrorMessage
import com.jrblanco.boccantabria.core.ui.component.IllustratedMessage
import com.jrblanco.boccantabria.core.ui.component.OfflineBanner
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.ui.home.component.BulletinHeader
import com.jrblanco.boccantabria.ui.home.component.HomeTopBar
import com.jrblanco.boccantabria.ui.home.component.PublicationCardSkeleton
import com.jrblanco.boccantabria.ui.home.component.SKELETON_COUNT
import com.jrblanco.boccantabria.ui.home.component.SectionFilterChips
import com.jrblanco.boccantabria.ui.share.ShareEffect
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_PUBLICATIONS: String = "home_publications"
const val TAG_HOME_SEARCH_COUNT: String = "home_search_count"
const val TAG_HOME_NO_RESULTS: String = "home_no_results"
const val TAG_HOME_SEARCH_GLOBALLY: String = "home_search_globally"

@Composable
@Suppress("LongParameterList")
fun HomeScreen(
    sections: List<BocSection>,
    onOpenSections: () -> Unit,
    onSelectSection: (String?) -> Unit,
    onSearchGlobally: (String) -> Unit,
    onInfo: () -> Unit,
    onOpenPublication: (Publication) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Sharing sends the official document, which may still have to be fetched, so both the wait and
    // the fall back to the link are said out loud. Shared with Guardados, which does exactly the
    // same: see `ui/share/ShareEffect.kt`.
    ShareEffect(share = state.share, onConsumed = viewModel::onShareConsumed)
    SaveFailureToast(failed = state.saveFailed, onConsumed = viewModel::onSaveFailureConsumed)

    HomeContent(
        state = state,
        sections = sections,
        onRefresh = viewModel::onRefresh,
        onRetry = viewModel::onRetry,
        onOpenSections = onOpenSections,
        onSelectSection = onSelectSection,
        onSearchOpened = viewModel::onSearchOpened,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchClosed = viewModel::onSearchClosed,
        onSearchGlobally = onSearchGlobally,
        onInfo = onInfo,
        onOpenPublication = onOpenPublication,
        onShare = viewModel::onShare,
        onToggleSaved = viewModel::onToggleSaved,
        modifier = modifier,
    )
}

/**
 * The screen with nothing behind it: every piece of state arrives as a parameter and every
 * gesture leaves as an event.
 *
 * That is what lets the five states be photographed in a test without starting the graph, the
 * network or the splash screen every instrumented test would otherwise have to cross.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun HomeContent(
    state: HomeUiState,
    sections: List<BocSection>,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSections: () -> Unit = {},
    onSelectSection: (String?) -> Unit = {},
    onSearchOpened: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchClosed: () -> Unit = {},
    onSearchGlobally: (String) -> Unit = {},
    onInfo: () -> Unit = {},
    onOpenPublication: (Publication) -> Unit = {},
    onShare: (Publication) -> Unit = {},
    onToggleSaved: (Publication) -> Unit = {},
) {
    val sectionsByCode = remember(sections) { sections.associateBy { it.code } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            HomeTopBar(
                onOpenSections = onOpenSections,
                onSearch = onSearchOpened,
                onInfo = onInfo,
                search = state.search,
                onSearchQueryChanged = onSearchQueryChanged,
                onSearchClosed = onSearchClosed,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            BulletinHeader(header = state.header)

            // A thin line under the header, so an update never makes the content disappear.
            // fillMaxWidth, never fillMaxSize: the indicator would stretch over the whole screen
            // and hide the very content this is here to keep visible.
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
                modifier = Modifier.padding(vertical = BocTheme.spacing.space4),
            ) {
                SectionFilterChips(
                    chips = state.chips,
                    isTodaySelected = state.selection is HomeSelection.TodaysBulletin,
                    onSelect = onSelectSection,
                    subsections = state.subsections,
                    sectionCode = (state.selection as? HomeSelection.Section)?.sectionCode,
                    isWholeSectionSelected = state.isWholeSectionSelected,
                )

                if (state.isOffline) OfflineBanner()
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val content = state.content) {
                    HomeContentState.Skeleton -> SkeletonList()

                    is HomeContentState.Publications -> PublicationList(
                        publications = content.items,
                        sectionsByCode = sectionsByCode,
                        savedKeys = state.savedKeys,
                        matchCount = content.items.size.takeIf { state.search.isFiltering },
                        onOpenPublication = onOpenPublication,
                        onShare = onShare,
                        onToggleSaved = onToggleSaved,
                    )

                    is HomeContentState.NoSearchResults -> NoSearchResultsMessage(
                        query = content.query,
                        onSearchGlobally = { onSearchGlobally(content.query) },
                    )

                    HomeContentState.Empty -> EmptyMessage(
                        message = stringResource(state.selection.emptyMessageRes()),
                    )

                    is HomeContentState.Error -> ErrorMessage(
                        message = stringResource(content.error.messageRes()),
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

/**
 * Nothing matched **in this edition** — which is not the same thing as nothing being published, and
 * must not be worded as if it were.
 *
 * It is the only state that offers a way out: the same words, searched across everything the device
 * holds. Without it the in-place search would end in a dead end at the exact moment somebody needs
 * help, and nobody would ever discover that the archive-wide search exists.
 */
@Composable
private fun NoSearchResultsMessage(query: String, onSearchGlobally: () -> Unit) {
    IllustratedMessage(
        iconRes = R.drawable.ic_search,
        title = stringResource(R.string.home_no_results_title),
        description = stringResource(R.string.home_no_results_body, query),
        modifier = Modifier.testTag(TAG_HOME_NO_RESULTS),
        action = {
            TextButton(
                onClick = onSearchGlobally,
                modifier = Modifier.testTag(TAG_HOME_SEARCH_GLOBALLY),
            ) {
                Text(text = stringResource(R.string.home_search_globally))
            }
        },
    )
}

@Composable
private fun SkeletonList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = BocTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
        userScrollEnabled = false,
    ) {
        items(count = SKELETON_COUNT) { PublicationCardSkeleton() }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PublicationList(
    publications: List<Publication>,
    sectionsByCode: Map<String, BocSection>,
    savedKeys: Set<String>,
    matchCount: Int?,
    onOpenPublication: (Publication) -> Unit,
    onShare: (Publication) -> Unit,
    onToggleSaved: (Publication) -> Unit,
) {
    // While a search is on, how many matched. The editorial header above says nothing about it: it
    // describes the edition, not the result, and rewriting it would make the day's count untrustworthy.
    if (matchCount != null) {
        Text(
            text = pluralStringResource(R.plurals.home_search_match_count, matchCount, matchCount),
            style = MaterialTheme.typography.labelLarge,
            color = BocTheme.colors.textSecondary,
            modifier = Modifier
                .padding(
                    start = BocTheme.spacing.screenMargin,
                    end = BocTheme.spacing.screenMargin,
                    bottom = BocTheme.spacing.space2,
                )
                .testTag(TAG_HOME_SEARCH_COUNT),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_PUBLICATIONS),
        contentPadding = PaddingValues(
            start = BocTheme.spacing.screenMargin,
            end = BocTheme.spacing.screenMargin,
            bottom = BocTheme.spacing.space10,
        ),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
    ) {
        items(items = publications, key = { it.externalKey }) { publication ->
            PublicationCard(
                publication = publication,
                section = sectionsByCode[publication.classificationCode],
                formattedDate = publication.publicationDate.format(SPANISH_LONG_DATE),
                isSaved = publication.externalKey in savedKeys,
                onClick = { onOpenPublication(publication) },
                onShare = { onShare(publication) },
                onSave = { onToggleSaved(publication) },
            )
        }
    }
}

/** A quiet section and a quiet day are not the same news, so they do not share a message. */
private fun HomeSelection.emptyMessageRes(): Int = when (this) {
    HomeSelection.TodaysBulletin -> R.string.home_empty_today
    is HomeSelection.Section -> R.string.home_empty_section
}

private fun DomainError.messageRes(): Int = when (this) {
    DomainError.Network -> R.string.home_error_sync
    DomainError.Unknown -> R.string.home_error_unknown
}

private val SPANISH_LONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))
