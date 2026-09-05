package com.jrblanco.boccantabria.ui.ask

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.core.ui.component.SaveFailureToast
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.domain.model.AiChatError
import org.koin.androidx.compose.koinViewModel

/**
 * The conversation, with its state.
 *
 * The split this house uses everywhere: this half knows about Koin and the view model, [AskContent]
 * knows about neither and can therefore be mounted by a test with `createComposeRule()` — which saves
 * the 1.2 s minimum of going through the splash on every instrumented test, and the full run already
 * takes close to two hours.
 */
@Composable
fun AskRoute(
    onBack: () -> Unit,
    onOpenDocument: (page: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AskViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Saving is offered here too, so the failure has to be said here too. A write that fails in
    // silence is the worst of the three outcomes: the icon stays as it was, which is correct, and
    // nobody finds out why (007 FR-009).
    SaveFailureToast(failed = state.saveFailed, onConsumed = viewModel::onSaveFailureShown)

    AskContent(
        state = state,
        onBack = onBack,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::onSend,
        onSuggestionTapped = viewModel::onSuggestionTapped,
        onRetry = viewModel::onRetry,
        onToggleSaved = viewModel::onToggleSaved,
        onOpenDocument = { onOpenDocument(FIRST_PAGE) },
        // Pages are counted from one for whoever reads them and from zero for the viewer. The
        // conversion happens here, where the navigation happens, exactly as the summary's page chips
        // already do (011 contracts §4).
        onSourceClick = { source -> onOpenDocument(source.page - 1) },
        onNoticeAccepted = viewModel::onNoticeAccepted,
        onNoticeDismissed = viewModel::onNoticeDismissed,
        modifier = modifier,
    )
}

/**
 * One sentence per case, and **not one of them carries a status code, a number or the provider's
 * name** (FR-031). Exhaustive over the sealed hierarchy, so adding a case makes the compiler complain
 * rather than leaving a silent blank on screen.
 */
internal fun AiChatError.messageRes(): Int = when (this) {
    AiChatError.Offline -> R.string.ask_error_offline
    is AiChatError.QuotaMinute -> R.string.ask_error_quota_minute
    AiChatError.QuotaDay -> R.string.ask_error_quota_day
    AiChatError.NotConfigured -> R.string.ask_error_not_configured
    AiChatError.UnreadableDocument -> R.string.ask_error_unreadable
    AiChatError.EncryptedPdf -> R.string.ask_error_encrypted
    AiChatError.InvalidResponse -> R.string.ask_error_invalid
    AiChatError.Unknown -> R.string.ask_error_unknown
}

private const val FIRST_PAGE = 0
