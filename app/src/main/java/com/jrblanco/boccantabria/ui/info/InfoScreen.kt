package com.jrblanco.boccantabria.ui.info

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import org.koin.androidx.compose.koinViewModel

const val TAG_INFO_SCREEN: String = "info_screen"
const val TAG_INFO_LIST: String = "info_list"
const val TAG_INFO_BACK: String = "info_back"
const val TAG_INFO_PORTRAIT: String = "info_portrait"
const val TAG_INFO_LINKEDIN: String = "info_link_linkedin"
const val TAG_INFO_GITHUB: String = "info_link_github"
const val TAG_INFO_VERSION: String = "info_version"
const val TAG_INFO_LINK_ERROR: String = "info_link_error"

/**
 * The route-aware edge of the about screen.
 *
 * The content stays stateless: this wrapper reads the installed version, reports gestures and
 * hands HTTPS links to Android. A web URL is deliberate — Android can route it to an associated
 * app such as LinkedIn and otherwise to the browser, without a fragile custom-scheme fallback.
 */
@Composable
fun InfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InfoViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val linkErrorMessage = stringResource(R.string.info_link_error)

    LaunchedEffect(state.linkOpenFailed) {
        if (state.linkOpenFailed) {
            snackbarHostState.showSnackbar(linkErrorMessage)
            viewModel.onLinkErrorConsumed()
        }
    }

    InfoContent(
        state = state,
        onBack = onBack,
        onOpenLink = { link ->
            viewModel.onLinkTapped(link)
            runCatching { uriHandler.openUri(link.url) }
                .onFailure { viewModel.onLinkOpenFailed() }
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Every value arrives and every gesture leaves: previews and UI tests need no graph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoContent(
    state: InfoUiState,
    onBack: () -> Unit,
    onOpenLink: (InfoLink) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_INFO_SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.info_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_INFO_BACK)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.info_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.testTag(TAG_INFO_LINK_ERROR),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .testTag(TAG_INFO_LIST),
                contentPadding = PaddingValues(
                    horizontal = BocTheme.spacing.screenMargin,
                    vertical = BocTheme.spacing.space6,
                ),
                verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space6),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.info_brand),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    AuthorCard(onOpenLink = onOpenLink)
                }
                item {
                    InfoSection(
                        iconRes = R.drawable.ic_target,
                        title = stringResource(R.string.info_purpose_title),
                    ) {
                        Text(
                            text = stringResource(R.string.info_purpose_first),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(BocTheme.spacing.space3))
                        Text(
                            text = stringResource(R.string.info_purpose_second),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item {
                    InfoSection(
                        iconRes = R.drawable.ic_group,
                        title = stringResource(R.string.info_community_title),
                    ) {
                        Text(
                            text = stringResource(R.string.info_community_body),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item { WorkCard() }
                item { IndependenceCard(versionName = state.versionName) }
            }
        }
    }
}

@Composable
private fun AuthorCard(onOpenLink: (InfoLink) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = BocTheme.elevation.level1),
    ) {
        Column(
            modifier = Modifier.padding(BocTheme.spacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier
                    .size(PORTRAIT_SIZE)
                    .testTag(TAG_INFO_PORTRAIT),
                shape = CircleShape,
                border = BorderStroke(PORTRAIT_BORDER_WIDTH, MaterialTheme.colorScheme.secondary),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Image(
                    painter = painterResource(R.drawable.about_author),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .padding(PORTRAIT_BORDER_PADDING)
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            }
            Spacer(Modifier.height(BocTheme.spacing.space5))
            Text(
                text = stringResource(R.string.info_author_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.info_author_role),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BocTheme.spacing.space3))
            Text(
                text = stringResource(R.string.info_author_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = BocTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BocTheme.spacing.space5))
            ExternalLinkButton(
                label = stringResource(R.string.info_linkedin),
                leadingIcon = R.drawable.ic_person,
                onClick = { onOpenLink(InfoLink.LINKEDIN) },
                modifier = Modifier.testTag(TAG_INFO_LINKEDIN),
                outlined = false,
            )
            Spacer(Modifier.height(BocTheme.spacing.space3))
            ExternalLinkButton(
                label = stringResource(R.string.info_github),
                leadingIcon = R.drawable.ic_code,
                onClick = { onOpenLink(InfoLink.GITHUB) },
                modifier = Modifier.testTag(TAG_INFO_GITHUB),
                outlined = true,
            )
        }
    }
}

@Composable
private fun ExternalLinkButton(
    label: String,
    @DrawableRes leadingIcon: Int,
    onClick: () -> Unit,
    outlined: Boolean,
    modifier: Modifier = Modifier,
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(leadingIcon), contentDescription = null)
        Spacer(Modifier.width(BocTheme.spacing.space3))
        Text(text = label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.width(BocTheme.spacing.space3))
        Icon(
            painter = painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
        )
    }

    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(LINK_BUTTON_HEIGHT),
            border = BorderStroke(BORDER_WIDTH, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = BocTheme.spacing.space4),
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(LINK_BUTTON_HEIGHT),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = BocTheme.spacing.space4),
            content = content,
        )
    }
}

@Composable
private fun InfoSection(
    @DrawableRes iconRes: Int,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(SECTION_ICON_SIZE),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(BocTheme.spacing.space3))
            content()
        }
    }
}

@Composable
private fun WorkCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(BORDER_WIDTH, MaterialTheme.colorScheme.secondary),
    ) {
        Column(
            modifier = Modifier.padding(BocTheme.spacing.space5),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
        ) {
            Text(
                text = stringResource(R.string.info_work_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            WorkRow(R.drawable.ic_ai, stringResource(R.string.info_work_ai))
            WorkRow(R.drawable.ic_phone, stringResource(R.string.info_work_mobile))
            WorkRow(R.drawable.ic_storage, stringResource(R.string.info_work_backend))
        }
    }
}

@Composable
private fun WorkRow(@DrawableRes iconRes: Int, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(WORK_ICON_SIZE),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun IndependenceCard(versionName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BocTheme.colors.surfaceStrong),
        border = BorderStroke(BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(BocTheme.spacing.space5),
            horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = BocTheme.colors.textSecondary,
                modifier = Modifier.size(SECTION_ICON_SIZE),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
            ) {
                Text(
                    text = stringResource(R.string.info_independent),
                    style = MaterialTheme.typography.bodyLarge,
                    color = BocTheme.colors.textSecondary,
                )
                Text(
                    text = stringResource(R.string.info_source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BocTheme.colors.textSecondary,
                )
                Text(
                    text = stringResource(R.string.info_version, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = BocTheme.colors.textMuted,
                    modifier = Modifier.testTag(TAG_INFO_VERSION),
                )
            }
        }
    }
}

private val CONTENT_MAX_WIDTH = 640.dp
private val PORTRAIT_SIZE = 152.dp
private val PORTRAIT_BORDER_WIDTH = 2.dp
private val PORTRAIT_BORDER_PADDING = 3.dp
private val LINK_BUTTON_HEIGHT = 56.dp
private val SECTION_ICON_SIZE = 36.dp
private val WORK_ICON_SIZE = 28.dp
private val BORDER_WIDTH = 1.dp
