package com.jrblanco.boccantabria.ui.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.domain.model.ShareTarget
import com.jrblanco.boccantabria.ui.share.share
import org.koin.androidx.compose.koinViewModel

/**
 * The viewer with its state attached.
 *
 * Sharing here needs none of the detail screen's deliberation: the viewer only exists once the
 * document is on the device and open, so there is nothing to fetch and no link to fall back to.
 */
@Composable
fun PdfViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfViewerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PdfViewerContent(
        state = state,
        onBack = onBack,
        onShare = {
            (state as? PdfViewerUiState.Ready)?.let { ready ->
                context.share(ShareTarget.Document(ready.document), ready.title)
            }
        },
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}
