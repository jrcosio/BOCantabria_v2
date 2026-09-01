package com.jrblanco.boccantabria.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.IllustratedMessage
import com.jrblanco.boccantabria.core.ui.component.PublicationCard
import com.jrblanco.boccantabria.core.ui.component.SaveFailureToast
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.domain.model.SearchSort
import com.jrblanco.boccantabria.ui.search.component.ActiveFilterChips
import com.jrblanco.boccantabria.ui.search.component.SearchField
import com.jrblanco.boccantabria.ui.search.component.SearchFiltersSheet
import com.jrblanco.boccantabria.ui.search.component.SortSelector
import com.jrblanco.boccantabria.ui.share.ShareEffect
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_SEARCH_SCREEN: String = "search_screen"
const val TAG_SEARCH_RESULTS: String = "search_results"
const val TAG_SEARCH_INITIAL: String = "search_initial"
const val TAG_SEARCH_EMPTY: String = "search_empty"
const val TAG_SEARCH_COUNT: String = "search_count"
const val TAG_SEARCH_TRUNCATED: String = "search_truncated"
const val TAG_SEARCH_FILTERS_OPEN: String = "search_filters_open"

/**
 * The bar's title.
 *
 * Tagged so a test can name it rather than hunt for its words. It earned the tag when the title and
 * the field's placeholder still said the same thing and a search by text found two nodes; the copy
 * has since been split —the bar says `Buscar`, the field `Buscar publicaciones`— but naming the node
 * outright is the better test either way, and it survives the next rewording.
 */
const val TAG_SEARCH_TITLE: String = "search_title"

/**
 * Buscar with its state attached.
 *
 * Split from [SearchContent] so the drawing can be mounted on its own in a test: what needs checking
 * is the composition, not Koin's ability to build a view model.
 *
 * The sections arrive as a parameter, exactly as they do for the bulletin and the saved list: they
 * are the whole tree, they never change, and the frame above already holds them.
 */
@Composable
fun SearchScreen(
    sections: List<BocSection>,
    onOpenPublication: (Publication) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ShareEffect(share = state.share, onConsumed = viewModel::onShareConsumed)
    SaveFailureToast(failed = state.saveFailed, onConsumed = viewModel::onSaveFailureConsumed)

    SearchContent(
        state = state,
        sections = sections,
        onQueryChanged = viewModel::onQueryChanged,
        onClearQuery = viewModel::onClearQuery,
        onSortChanged = viewModel::onSortChanged,
        onFiltersApplied = viewModel::onFiltersApplied,
        onClearFilters = viewModel::onClearFilters,
        onRemoveDateRange = viewModel::onRemoveDateRange,
        onRemoveSection = viewModel::onRemoveSection,
        onRemoveSubsection = viewModel::onRemoveSubsection,
        onRemoveIssuer = viewModel::onRemoveIssuer,
        onOpenPublication = onOpenPublication,
        onShare = viewModel::onShare,
        onToggleSaved = viewModel::onToggleSaved,
        modifier = modifier,
    )
}

/**
 * The screen with nothing behind it: every piece of state arrives as a parameter and every gesture
 * leaves as an event.
 *
 * The bar carries the title and **no navigation icon and no overflow menu**, unlike the reference
 * image the owner supplied. This is a destination of the bottom bar, not a screen stacked on top of
 * another: a back arrow here would have nowhere to go, and the three dots had nothing to offer.
 * Recorded as a deliberate deviation in the specification.
 *
 * The title is `Buscar`, the same word as the tab that leads here and what section 17.1 of the
 * design document asks for. The reference image said `Buscar publicaciones`, but the field right
 * below already says that: repeating it read as noise on screen and a screen reader announced it
 * twice in a row.
 *
 * It is **institutional blue, like the saved list**. The reference image drew it white, but that
 * image was an idea rather than a specification, and two destinations of the same bottom bar with
 * headers of different colours read as two applications. Only the bulletin keeps a white bar, and
 * for a reason of its own: the blue editorial header sits directly underneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SearchContent(
    state: SearchUiState,
    sections: List<BocSection>,
    modifier: Modifier = Modifier,
    onQueryChanged: (String) -> Unit = {},
    onClearQuery: () -> Unit = {},
    onSortChanged: (SearchSort) -> Unit = {},
    onFiltersApplied: (SearchQuery) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onRemoveDateRange: () -> Unit = {},
    onRemoveSection: () -> Unit = {},
    onRemoveSubsection: () -> Unit = {},
    onRemoveIssuer: () -> Unit = {},
    onOpenPublication: (Publication) -> Unit = {},
    onShare: (Publication) -> Unit = {},
    onToggleSaved: (Publication) -> Unit = {},
) {
    val sectionsByCode = remember(sections) { sections.associateBy { it.code } }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_SEARCH_SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.search_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(TAG_SEARCH_TITLE),
                    )
                },
                actions = {
                    IconButton(
                        onClick = { filtersOpen = true },
                        modifier = Modifier.testTag(TAG_SEARCH_FILTERS_OPEN),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_filter_list),
                            contentDescription = stringResource(R.string.search_filters_open),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SearchField(
                value = state.query.text,
                onValueChange = onQueryChanged,
                onClear = onClearQuery,
                modifier = Modifier.padding(
                    horizontal = BocTheme.spacing.screenMargin,
                    vertical = BocTheme.spacing.space2,
                ),
            )

            ActiveFilterChips(
                query = state.query,
                sectionsByCode = sectionsByCode,
                onRemoveDates = onRemoveDateRange,
                onRemoveSection = onRemoveSection,
                onRemoveSubsection = onRemoveSubsection,
                onRemoveIssuer = onRemoveIssuer,
                onClearAll = onClearFilters,
            )

            when (val content = state.content) {
                SearchContentState.Initial -> IllustratedMessage(
                    iconRes = R.drawable.ic_search,
                    title = stringResource(R.string.search_initial_title),
                    description = stringResource(R.string.search_initial_body),
                    modifier = Modifier.testTag(TAG_SEARCH_INITIAL),
                )

                SearchContentState.Empty -> IllustratedMessage(
                    iconRes = R.drawable.ic_search,
                    title = stringResource(R.string.search_empty_title),
                    description = stringResource(R.string.search_empty_body),
                    modifier = Modifier.testTag(TAG_SEARCH_EMPTY),
                )

                is SearchContentState.Results -> SearchResultList(
                    content = content,
                    sectionsByCode = sectionsByCode,
                    savedKeys = state.savedKeys,
                    sort = state.query.sort,
                    onSortChanged = onSortChanged,
                    onOpenPublication = onOpenPublication,
                    onShare = onShare,
                    onToggleSaved = onToggleSaved,
                )
            }
        }
    }

    if (filtersOpen) {
        SearchFiltersSheet(
            query = state.query,
            sections = sections,
            issuers = state.issuers,
            onApply = { applied ->
                filtersOpen = false
                onFiltersApplied(applied)
            },
            onDismiss = { filtersOpen = false },
        )
    }
}

/**
 * The same list as the bulletin and the saved screen: same margins, same spacing and the same bottom
 * slack, so moving between destinations does not feel like moving between applications.
 *
 * The scroll position is remembered across configuration changes and across leaving for a
 * publication and coming back, which is the only reason the state is hoisted here at all.
 */
@Composable
@Suppress("LongParameterList")
private fun SearchResultList(
    content: SearchContentState.Results,
    sectionsByCode: Map<String, BocSection>,
    savedKeys: Set<String>,
    sort: SearchSort,
    onSortChanged: (SearchSort) -> Unit,
    onOpenPublication: (Publication) -> Unit,
    onShare: (Publication) -> Unit,
    onToggleSaved: (Publication) -> Unit,
) {
    // `rememberLazyListState` saves itself, so the reading position survives a rotation and the
    // trip out to a publication and back.
    val listState = rememberLazyListState()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = BocTheme.spacing.screenMargin,
                    vertical = BocTheme.spacing.space2,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.search_result_count,
                    content.items.size,
                    content.items.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = BocTheme.colors.textSecondary,
                modifier = Modifier.testTag(TAG_SEARCH_COUNT),
            )
            SortSelector(sort = sort, onSortChanged = onSortChanged)
        }

        // A list quietly cut short reads as a complete list. Better to say it.
        if (content.isTruncated) {
            Text(
                text = pluralStringResource(
                    R.plurals.search_truncated,
                    content.items.size,
                    content.items.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = BocTheme.colors.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = BocTheme.spacing.screenMargin,
                        vertical = BocTheme.spacing.space1,
                    )
                    .testTag(TAG_SEARCH_TRUNCATED),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_SEARCH_RESULTS),
            contentPadding = PaddingValues(
                start = BocTheme.spacing.screenMargin,
                end = BocTheme.spacing.screenMargin,
                bottom = BocTheme.spacing.space10,
            ),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
        ) {
            items(items = content.items, key = { it.externalKey }) { publication ->
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
}

private val SPANISH_LONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))
