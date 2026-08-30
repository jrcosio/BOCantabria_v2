package com.jrblanco.boccantabria.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.ui.detail.component.ComingSoonTab
import com.jrblanco.boccantabria.ui.detail.component.DetailActionBar
import com.jrblanco.boccantabria.ui.detail.component.DetailTabs
import com.jrblanco.boccantabria.ui.detail.component.DocumentHeader
import com.jrblanco.boccantabria.ui.detail.component.DocumentTab
import com.jrblanco.boccantabria.ui.share.ShareState
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_DETAIL_BACK: String = "detail_back"
const val TAG_DETAIL_SAVE: String = "detail_save"
const val TAG_DETAIL_SHARE: String = "detail_share"
const val TAG_DETAIL_MISSING: String = "detail_missing"

/**
 * The detail screen, sections 18 and 19 of the design document.
 *
 * Stateless: everything it draws arrives as [state] and everything it does leaves as a callback.
 * That is what lets the instrumented tests mount it directly with `createComposeRule()` instead of
 * going through the splash and the whole graph.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicationDetailContent(
    state: PublicationDetailUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onTabSelected: (DetailTab) -> Unit,
    onOpenDocument: () -> Unit,
    onAsk: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.detail_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_DETAIL_BACK)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSave, modifier = Modifier.testTag(TAG_DETAIL_SAVE)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bookmark),
                            contentDescription = stringResource(R.string.detail_save),
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.testTag(TAG_DETAIL_SHARE)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.detail_share),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            if (state.publication != null) {
                DetailActionBar(onOpen = onOpenDocument, onAsk = onAsk)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val publication = state.publication
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // FR-032: fetching the document takes seconds, and a share sheet that simply does not
            // appear reads as the application having ignored the tap.
            if (state.share == ShareState.Preparing) {
                PreparingShare()
            }

            when {
                publication != null -> {
                    DocumentHeader(
                        publication = publication,
                        section = state.section,
                        formattedDate = publication.publicationDate.format(SPANISH_LONG_DATE),
                    )
                    DetailTabs(selected = state.selectedTab, onSelect = onTabSelected)
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        when (state.selectedTab) {
                            DetailTab.DOCUMENT -> DocumentTab(
                                publication = publication,
                                section = state.section,
                                formattedDate = publication.publicationDate.format(SPANISH_LONG_DATE),
                                status = state.document,
                                onRetry = onRetry,
                            )

                            DetailTab.AI_SUMMARY -> ComingSoonTab(
                                iconRes = R.drawable.ic_ai,
                                label = stringResource(R.string.detail_summary_label),
                                description = stringResource(R.string.detail_summary_coming),
                            )

                            DetailTab.ASK -> ComingSoonTab(
                                iconRes = R.drawable.ic_ask,
                                label = stringResource(R.string.detail_ask_label),
                                description = stringResource(R.string.detail_ask_coming),
                            )
                        }
                    }
                }

                state.isMissing -> Missing(onBack)

                else -> Loading()
            }
        }
    }
}

/**
 * The publication is not stored any more. It says so and offers the way back, rather than showing
 * an empty frame that looks like a screen that failed to load (FR-011).
 */
@Composable
private fun Missing(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(BocTheme.spacing.space6)
            .testTag(TAG_DETAIL_MISSING),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_document),
            contentDescription = null,
            tint = BocTheme.colors.textMuted,
            modifier = Modifier.size(ILLUSTRATION_SIZE),
        )
        Text(
            text = stringResource(R.string.detail_missing_title),
            style = MaterialTheme.typography.titleLarge,
            color = BocTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.detail_missing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        androidx.compose.material3.Button(onClick = onBack) {
            Text(text = stringResource(R.string.detail_missing_action))
        }
    }
}

const val TAG_DETAIL_SHARE_PREPARING: String = "detail_share_preparing"

@Composable
private fun PreparingShare() {
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_DETAIL_SHARE_PREPARING)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.share_preparing),
            style = MaterialTheme.typography.bodySmall,
            color = BocTheme.colors.textSecondary,
            modifier = Modifier.padding(
                horizontal = BocTheme.spacing.space4,
                vertical = BocTheme.spacing.space2,
            ),
        )
    }
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

private val SPANISH_LONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))

private val ILLUSTRATION_SIZE = 96.dp
