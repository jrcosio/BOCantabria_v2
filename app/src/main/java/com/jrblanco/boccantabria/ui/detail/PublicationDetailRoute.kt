package com.jrblanco.boccantabria.ui.detail

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.SaveFailureToast
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.domain.model.ShareTarget
import com.jrblanco.boccantabria.ui.share.ShareState
import com.jrblanco.boccantabria.ui.share.share
import org.koin.androidx.compose.koinViewModel

/**
 * The detail screen with its state attached.
 *
 * Split from [PublicationDetailContent] so the drawing can be mounted on its own in a test: what
 * needs checking is the composition, not Koin's ability to build a view model.
 */
@Composable
fun PublicationDetailScreen(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicationDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val linkFallback = stringResource(R.string.share_link_fallback)

    SaveFailureToast(failed = state.saveFailed, onConsumed = viewModel::onSaveFailureConsumed)

    // The fetch is triggered by the tab being on screen, not by the screen opening: someone who
    // only wanted to see what the announcement was about should not pay for a PDF in data.
    LaunchedEffect(state.selectedTab, state.publication) {
        if (state.selectedTab == DetailTab.DOCUMENT && state.publication != null) {
            viewModel.onDocumentTabShown()
        }
    }

    val share = state.share
    LaunchedEffect(share) {
        val ready = share as? ShareState.Ready ?: return@LaunchedEffect
        // Said out loud, because getting a link when you asked for the document would otherwise
        // look like the application ignoring you (FR-034).
        if (ready.target is ShareTarget.Link) {
            Toast.makeText(context, linkFallback, Toast.LENGTH_LONG).show()
        }
        context.share(ready.target, ready.subject)
        viewModel.onShareConsumed()
    }

    PublicationDetailContent(
        state = state,
        onBack = onBack,
        onSave = viewModel::onToggleSaved,
        onShare = viewModel::onShare,
        onTabSelected = viewModel::onTabSelected,
        onOpenDocument = { state.publication?.let { onOpenDocument(it.externalKey) } },
        onAsk = { state.publication?.let { onAsk(it.externalKey) } },
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}
