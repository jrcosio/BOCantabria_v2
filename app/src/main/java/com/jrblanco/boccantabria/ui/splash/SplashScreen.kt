package com.jrblanco.boccantabria.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.DomainError
import org.koin.androidx.compose.koinViewModel

const val TAG_SPLASH_ROOT: String = "splash_root"
const val TAG_SPLASH_EMBLEM: String = "splash_emblem"
const val TAG_SPLASH_LOADING: String = "splash_loading"
const val TAG_SPLASH_ERROR: String = "splash_error"
const val TAG_SPLASH_BLOCKED: String = "splash_blocked"
const val TAG_SPLASH_RETRY: String = "splash_retry"
const val TAG_SPLASH_CONTINUE_OFFLINE: String = "splash_continue_offline"

@Composable
fun SplashScreen(
    onStartupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LightSystemBars()

    LaunchedEffect(state) {
        if (state is SplashUiState.Ready) onStartupComplete()
    }

    SplashContent(
        state = state,
        onRetry = viewModel::onRetry,
        onContinueOffline = viewModel::onContinueOffline,
        modifier = modifier,
    )
}

/**
 * Light system bar icons while the institutional blue is on screen, dark again on the way out.
 *
 * The rest of the application sits on a light background and needs dark icons; without this the
 * clock and the battery are illegible on one screen or the other (FR-022).
 *
 * Restores the icons to dark rather than to "whatever they were": the activity pins the light bar
 * style precisely so the appearance never depends on the phone's theme, and reinstating a captured
 * previous value would quietly reintroduce that dependency.
 */
@Composable
private fun LightSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
}

/**
 * Stateless on purpose: the UI tests walk the four states without starting the dependency graph.
 *
 * Composition follows the visual contract in `contracts/internal-contracts.md`. The background
 * extends behind the system bars; only the content is inset, so the blue reaches every edge.
 */
@Composable
fun SplashContent(
    state: SplashUiState,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .testTag(TAG_SPLASH_ROOT),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = BocTheme.spacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(WEIGHT_ABOVE_EMBLEM))

            Emblem()
            Spacer(modifier = Modifier.height(BocTheme.spacing.space6))
            Wordmark()
            Spacer(modifier = Modifier.height(BocTheme.spacing.space8))
            DividerLine()

            Spacer(modifier = Modifier.weight(WEIGHT_BELOW_DIVIDER))

            Authorship()
            Spacer(modifier = Modifier.height(BocTheme.spacing.space6))

            // A minimum rather than a fixed height: enough room for the indicator so the layout
            // does not jump when the state changes, but the error and blocked states grow upward
            // into the flexible space instead of leaving a dead gap underneath.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = STATUS_SLOT_MIN_HEIGHT),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (state) {
                    SplashUiState.Loading, SplashUiState.Ready -> LoadingIndicator()
                    is SplashUiState.Error -> ErrorActions(
                        error = state.error,
                        onRetry = onRetry,
                        onContinueOffline = onContinueOffline,
                    )

                    is SplashUiState.Blocked -> BlockedMessage(reason = state.reason, onRetry = onRetry)
                }
            }

            Spacer(modifier = Modifier.height(BocTheme.spacing.space12))
        }
    }
}

@Composable
private fun Emblem() {
    Image(
        painter = painterResource(R.drawable.ic_escudo_cantabria),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .height(EMBLEM_HEIGHT)
            // Without pinning the ratio too, Image fits the drawable's intrinsic 32 dp width and
            // the requested height never takes effect: the emblem renders tiny.
            .aspectRatio(EMBLEM_ASPECT_RATIO)
            .testTag(TAG_SPLASH_EMBLEM),
    )
}

@Composable
private fun Wordmark() {
    Text(
        text = stringResource(R.string.splash_acronym),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onPrimary,
    )
    Text(
        text = stringResource(R.string.splash_title_line_one),
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = SUBTITLE_SIZE,
            fontWeight = FontWeight.Medium,
            letterSpacing = SUBTITLE_TRACKING,
        ),
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.splash_title_line_two),
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = SUBTITLE_SIZE,
            fontWeight = FontWeight.Medium,
            letterSpacing = SUBTITLE_TRACKING,
        ),
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .width(DIVIDER_WIDTH)
            .height(DIVIDER_THICKNESS)
            .background(BocTheme.colors.onPrimaryAccent),
    )
}

@Composable
private fun Authorship() {
    Text(
        text = stringResource(R.string.splash_authorship_label),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = AUTHORSHIP_LABEL_SIZE),
        color = BocTheme.colors.onPrimaryMuted,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.splash_authorship_name),
        style = MaterialTheme.typography.titleSmall,
        color = BocTheme.colors.onPrimaryAccent,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LoadingIndicator() {
    val description = stringResource(R.string.splash_loading_description)
    CircularProgressIndicator(
        modifier = Modifier
            .size(INDICATOR_SIZE)
            .semantics { contentDescription = description }
            .testTag(TAG_SPLASH_LOADING),
        color = BocTheme.colors.onPrimaryAccent,
        strokeWidth = INDICATOR_STROKE,
    )
}

@Composable
private fun ErrorActions(
    error: DomainError,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        modifier = Modifier.testTag(TAG_SPLASH_ERROR),
    ) {
        Text(
            text = stringResource(R.string.splash_error_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.onPrimaryMuted,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = BocTheme.colors.onPrimaryAccent,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.testTag(TAG_SPLASH_RETRY),
        ) {
            Text(text = stringResource(R.string.action_retry))
        }
        TextButton(
            onClick = onContinueOffline,
            modifier = Modifier.testTag(TAG_SPLASH_CONTINUE_OFFLINE),
        ) {
            Text(
                text = stringResource(R.string.splash_continue_offline),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * A blocked access offers retry and **nothing else**: no "continue offline". Letting the user
 * through would defeat the point of blocking them.
 */
@Composable
private fun BlockedMessage(reason: BlockReason, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        modifier = Modifier.testTag(TAG_SPLASH_BLOCKED),
    ) {
        Text(
            text = when (reason) {
                BlockReason.UpdateRequired -> stringResource(R.string.splash_update_required_title)
                is BlockReason.Maintenance -> stringResource(R.string.splash_maintenance_title)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when (reason) {
                BlockReason.UpdateRequired -> stringResource(R.string.splash_update_required_message)
                is BlockReason.Maintenance -> reason.message
            },
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.onPrimaryMuted,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = BocTheme.colors.onPrimaryAccent,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.testTag(TAG_SPLASH_RETRY),
        ) {
            Text(text = stringResource(R.string.action_retry))
        }
    }
}

private fun DomainError.messageRes(): Int = when (this) {
    DomainError.Network -> R.string.splash_error_network
    DomainError.Unknown -> R.string.splash_error_unknown
}

// Measurements from the visual contract (§13.2 of the design specification).
private val EMBLEM_HEIGHT = 104.dp

/** The official emblem's own proportions (viewport 79 x 137). Never distorted. */
private const val EMBLEM_ASPECT_RATIO = 79f / 137f
private val DIVIDER_WIDTH = 120.dp
private val DIVIDER_THICKNESS = 2.dp
private val INDICATOR_SIZE = 28.dp
private val INDICATOR_STROKE = 2.dp
private val STATUS_SLOT_MIN_HEIGHT = 72.dp
private val SUBTITLE_SIZE = 20.sp
private val SUBTITLE_TRACKING = 3.sp
private val AUTHORSHIP_LABEL_SIZE = 13.sp

// The emblem sits slightly above the optical centre (§13.1).
private const val WEIGHT_ABOVE_EMBLEM = 1f
private const val WEIGHT_BELOW_DIVIDER = 1.1f
