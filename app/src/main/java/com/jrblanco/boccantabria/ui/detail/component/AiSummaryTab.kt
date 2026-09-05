package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AiSummaryError
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus

const val TAG_AI_SUMMARY_TAB: String = "ai_summary_tab"
const val TAG_AI_SUMMARY_GENERATE: String = "ai_summary_generate"
const val TAG_AI_SUMMARY_PARTIAL_WARNING: String = "ai_summary_partial_warning"
const val TAG_AI_SUMMARY_PROGRESS: String = "ai_summary_progress"
const val TAG_AI_SUMMARY_QUOTA: String = "ai_summary_quota"
const val TAG_AI_SUMMARY_COVERAGE: String = "ai_summary_coverage"
const val TAG_AI_SUMMARY_ERROR: String = "ai_summary_error"
const val TAG_AI_SUMMARY_RETRY: String = "ai_summary_retry"
const val TAG_AI_SUMMARY_STALE: String = "ai_summary_stale"

/**
 * The AI summary tab, in every state it can be in.
 *
 * Thirteen of them, listed in `contracts/internal-contracts.md`. Writing them down is what stops
 * half of them from being discovered on a phone.
 *
 * Partial coverage is announced from [AiSummaryStatus.Generating], which carries it: the pages that
 * fit are only known once the text has been extracted, and extracting means fetching a document the
 * person may never have asked for. That is the earliest honest moment to say it (FR-028).
 *
 * @param hasDocument FR-007. **Unreachable as the model stands today**, and worth saying so here so
 *   nobody has to derive it again: `PublicationNormalizer` rejects a bulletin entry whose link is not
 *   `https`, and `Publication` requires the same of `documentUrl`, so every publication that exists
 *   has a document. The parameter stays because this composable is stateless and the state is
 *   legitimate to model — if that invariant is ever relaxed, the tab already tells the truth instead
 *   of offering to summarise nothing.
 */
@Composable
fun AiSummaryTab(
    status: AiSummaryStatus,
    hasDocument: Boolean,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onOpenDocument: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(BocTheme.spacing.space4)
            .testTag(TAG_AI_SUMMARY_TAB),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
    ) {
        when {
            // FR-007: nothing to summarise, and no button that would pretend otherwise.
            !hasDocument -> Message(stringResource(R.string.ai_summary_no_document))

            else -> when (status) {
                AiSummaryStatus.Idle -> Initial(onGenerate)
                is AiSummaryStatus.Preparing -> Progress(status.phase.label())
                is AiSummaryStatus.Generating -> Generating(status)
                is AiSummaryStatus.WaitingForQuota -> WaitingForQuota(status.secondsRemaining)
                is AiSummaryStatus.Ready -> Ready(
                    state = status,
                    onRegenerate = onRegenerate,
                    onOpenPage = onOpenPage,
                    onCopy = onCopy,
                    onShare = onShare,
                )
                is AiSummaryStatus.Failed -> Failed(
                    error = status.error,
                    onRetry = onRetry,
                    onOpenDocument = onOpenDocument,
                )
            }
        }
    }
}

// ---------- Before anything has been asked ----------

@Composable
private fun Initial(onGenerate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4)) {
        Text(
            text = stringResource(R.string.ai_summary_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
        )
        Button(
            onClick = onGenerate,
            modifier = Modifier.testTag(TAG_AI_SUMMARY_GENERATE),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai),
                contentDescription = null,
                modifier = Modifier.size(BUTTON_ICON_SIZE),
            )
            Text(
                text = stringResource(R.string.ai_summary_generate),
                modifier = Modifier.padding(start = BocTheme.spacing.space2),
            )
        }
    }
}

// ---------- While it is working ----------

/**
 * §20.5: a skeleton and a **static** mark, no infinite animation.
 *
 * That is not only a design preference. A composition that never comes to rest makes
 * `assertIsDisplayed()` hang instead of fail, and this project has paid for that once already.
 */
@Composable
private fun Progress(phaseRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_AI_SUMMARY_PROGRESS),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE))
        Text(
            text = stringResource(phaseRes),
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
        )
    }
}

/**
 * FR-028: the warning lands here, after the text has been read and **before** the request goes out.
 * It is the earliest honest moment — until the text is extracted nobody knows how many pages fit,
 * and extracting means fetching a document the person may never have asked for.
 */
@Composable
private fun Generating(state: AiSummaryStatus.Generating) {
    // No warning ahead of the request any more: the whole document is sent, so there is no
    // fraction to announce and announcing one would be announcing something false. What a summary
    // did or did not cover is still said **afterwards**, from its own coverage (010 D-212).
    Progress(R.string.ai_summary_phase_generating)
}

@Composable
private fun WaitingForQuota(secondsRemaining: Long) {
    Notice(
        text = stringResource(R.string.ai_summary_quota_wait, secondsRemaining.toInt()),
        testTag = TAG_AI_SUMMARY_QUOTA,
    )
}

// ---------- The summary ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Ready(
    state: AiSummaryStatus.Ready,
    onRegenerate: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val summary = state.summary

    Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space6)) {
        // FR-035: shown, marked, never removed on the application's own initiative.
        if (state.isStale) {
            Notice(stringResource(R.string.ai_summary_stale), TAG_AI_SUMMARY_STALE)
        }

        AiSummaryCard(plainLanguageSummary = summary.plainLanguageSummary)

        if (!summary.hasOnlyPlainSummary) {
            AiSummarySections(summary = summary, onOpenPage = onOpenPage)
        }

        // FR-029: said out loud rather than buried in the warnings.
        if (summary.coverage.isPartial) {
            Notice(
                text = pluralStringResource(
                    R.plurals.ai_summary_partial_after,
                    summary.coverage.pagesAnalyzed.size,
                    summary.coverage.pagesAnalyzed.joinToString(", "),
                    summary.coverage.totalPages,
                ),
                testTag = TAG_AI_SUMMARY_COVERAGE,
            )
        }

        if (summary.citedPages.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3)) {
                Text(
                    text = stringResource(R.string.ai_summary_sources),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_AI_SUMMARY_SOURCES),
                    horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
                    verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
                ) {
                    summary.citedPages.forEach { page ->
                        PageChip(
                            page = page,
                            onOpenPage = onOpenPage,
                            testTag = sourceChipTag(page),
                        )
                    }
                }
            }
        }

        AiSummaryActions(onCopy = onCopy, onShare = onShare, onRegenerate = onRegenerate)
    }
}

// ---------- When it could not be done ----------

@Composable
private fun Failed(
    error: AiSummaryError,
    onRetry: () -> Unit,
    onOpenDocument: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_AI_SUMMARY_ERROR),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
    ) {
        Notice(stringResource(error.messageRes()), testTag = null)

        Row(
            horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // FR-041: offered only where trying again could help. Offering it elsewhere is its own
            // kind of lie.
            if (error.isRetryable) {
                Button(onClick = onRetry, modifier = Modifier.testTag(TAG_AI_SUMMARY_RETRY)) {
                    Text(stringResource(R.string.ai_summary_retry))
                }
            }
            // Always available: whatever went wrong, the official document is still there.
            OutlinedButton(onClick = onOpenDocument) {
                Text(stringResource(R.string.detail_action_open))
            }
        }
    }
}

// ---------- Shared bits ----------

@Composable
private fun Notice(text: String, testTag: String?) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = BocTheme.colors.surfaceStrong,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textPrimary,
            modifier = Modifier.padding(BocTheme.spacing.space4),
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = BocTheme.colors.textSecondary,
    )
}

private fun AiSummaryStatus.Preparing.Phase.label(): Int = when (this) {
    AiSummaryStatus.Preparing.Phase.FETCHING_DOCUMENT -> R.string.ai_summary_phase_fetching
    AiSummaryStatus.Preparing.Phase.UPLOADING_DOCUMENT -> R.string.ai_summary_phase_uploading
}

/** One message per case of FR-040. No status codes, no traces, no wording from the provider. */
internal fun AiSummaryError.messageRes(): Int = when (this) {
    AiSummaryError.Offline -> R.string.ai_error_offline
    AiSummaryError.UnreadableDocument -> R.string.ai_error_unreadable
    AiSummaryError.EncryptedPdf -> R.string.ai_error_encrypted
    is AiSummaryError.QuotaMinute -> R.string.ai_error_quota_minute
    AiSummaryError.QuotaDay -> R.string.ai_error_quota_day
    AiSummaryError.NotConfigured -> R.string.ai_error_not_configured
    AiSummaryError.InvalidResponse -> R.string.ai_error_invalid
    AiSummaryError.Unknown -> R.string.ai_error_unknown
}

private val BUTTON_ICON_SIZE = 18.dp
private val PROGRESS_SIZE = 32.dp
