package com.jrblanco.boccantabria.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.IllustratedMessage
import com.jrblanco.boccantabria.core.ui.component.PublicationCard
import com.jrblanco.boccantabria.core.ui.component.SaveFailureToast
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.ui.share.ShareEffect
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_SAVED_LIST: String = "saved_list"
const val TAG_SAVED_EMPTY: String = "saved_empty"
const val TAG_SAVED_EMPTY_ACTION: String = "saved_empty_action"

/**
 * Guardados with its state attached.
 *
 * Split from [SavedContent] so the drawing can be mounted on its own in a test: what needs checking
 * is the composition, not Koin's ability to build a view model.
 *
 * The sections arrive as a parameter rather than from the view model, exactly as they do for the
 * bulletin: they are the whole tree, they never change, and the frame above already has them.
 */
@Composable
fun SavedScreen(
    sections: List<BocSection>,
    onOpenPublication: (Publication) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ShareEffect(share = state.share, onConsumed = viewModel::onShareConsumed)
    SaveFailureToast(failed = state.saveFailed, onConsumed = viewModel::onSaveFailureConsumed)

    SavedContent(
        state = state,
        sections = sections,
        onOpenPublication = onOpenPublication,
        onShare = viewModel::onShare,
        onToggleSaved = viewModel::onToggleSaved,
        onExplore = onExplore,
        modifier = modifier,
    )
}

/**
 * The screen with nothing behind it: every piece of state arrives as a parameter and every gesture
 * leaves as an event.
 *
 * The bar carries the title and **no actions**. Sorting would be a menu of one option —the list has a
 * single order— and multiple selection is optional in the design document; both are recorded as
 * deferred in section 22.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SavedContent(
    state: SavedUiState,
    sections: List<BocSection>,
    modifier: Modifier = Modifier,
    onOpenPublication: (Publication) -> Unit = {},
    onShare: (Publication) -> Unit = {},
    onToggleSaved: (Publication) -> Unit = {},
    onExplore: () -> Unit = {},
) {
    val sectionsByCode = remember(sections) { sections.associateBy { it.code } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.saved_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
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
            when (val content = state.content) {
                is SavedContentState.Publications -> SavedList(
                    publications = content.items,
                    sectionsByCode = sectionsByCode,
                    onOpenPublication = onOpenPublication,
                    onShare = onShare,
                    onToggleSaved = onToggleSaved,
                )

                SavedContentState.Empty -> SavedEmpty(onExplore = onExplore)
            }
        }
    }
}

/**
 * The same list as the bulletin: same margins, same spacing and the same bottom slack, so moving
 * between the two destinations does not feel like moving between two applications.
 *
 * `isSaved` is always true here — everything on this screen is saved by definition — so the bookmark
 * comes filled and the action announces taking it off.
 */
@Composable
private fun SavedList(
    publications: List<Publication>,
    sectionsByCode: Map<String, BocSection>,
    onOpenPublication: (Publication) -> Unit,
    onShare: (Publication) -> Unit,
    onToggleSaved: (Publication) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_SAVED_LIST),
        contentPadding = PaddingValues(
            start = BocTheme.spacing.screenMargin,
            end = BocTheme.spacing.screenMargin,
            top = BocTheme.spacing.space3,
            bottom = BocTheme.spacing.space10,
        ),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
    ) {
        items(items = publications, key = { it.externalKey }) { publication ->
            PublicationCard(
                publication = publication,
                section = sectionsByCode[publication.classificationCode],
                formattedDate = publication.publicationDate.format(SPANISH_LONG_DATE),
                isSaved = true,
                onClick = { onOpenPublication(publication) },
                onShare = { onShare(publication) },
                onSave = { onToggleSaved(publication) },
            )
        }
    }
}

/**
 * Nothing saved yet, section 22.3 of the design document.
 *
 * Not a blank screen and not a spinner: whoever taps Guardados out of curiosity on day one gets told
 * what is missing, how it gets there, and where to go. An empty screen with no explanation reads as
 * a broken one.
 */
@Composable
private fun SavedEmpty(onExplore: () -> Unit) {
    IllustratedMessage(
        iconRes = R.drawable.ic_bookmark,
        title = stringResource(R.string.saved_empty_title),
        description = stringResource(R.string.saved_empty_body),
        modifier = Modifier.testTag(TAG_SAVED_EMPTY),
        action = {
            OutlinedButton(
                onClick = onExplore,
                modifier = Modifier.testTag(TAG_SAVED_EMPTY_ACTION),
            ) {
                Text(text = stringResource(R.string.saved_empty_action))
            }
        },
    )
}

private val SPANISH_LONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))
