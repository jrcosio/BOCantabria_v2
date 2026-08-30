package com.jrblanco.boccantabria.ui.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.rememberPdfViewerState
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.ErrorMessage
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.DomainError

const val TAG_PDF_VIEWER: String = "pdf_viewer"
const val TAG_PDF_VIEWER_LOADING: String = "pdf_viewer_loading"
const val TAG_PDF_VIEWER_ERROR: String = "pdf_viewer_error"
const val TAG_PDF_VIEWER_BACK: String = "pdf_viewer_back"
const val TAG_PDF_VIEWER_SHARE: String = "pdf_viewer_share"

/**
 * The document viewer, sections 24.1 and 24.2 of the design document.
 *
 * Stateless, so a test can mount each of its three states without a device-side PDF library
 * behind it. Reading the document is what happens **inside** [PdfViewer]; everything around it
 * is ours.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPdfApi::class)
@Composable
fun PdfViewerContent(
    state: PdfViewerUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? PdfViewerUiState.Ready)?.title
                            ?: stringResource(R.string.detail_field_official),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = MAX_TITLE_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_PDF_VIEWER_BACK)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.testTag(TAG_PDF_VIEWER_SHARE),
                    ) {
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
        // Section 24.2: a neutral grey, so the white pages read as pages and not as the screen.
        containerColor = BocTheme.colors.readerSurface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                PdfViewerUiState.Loading -> Loading()
                is PdfViewerUiState.Error -> Failure(state.error, onRetry)
                is PdfViewerUiState.Ready -> Document(state)
            }
        }
    }
}

@OptIn(ExperimentalPdfApi::class)
@Composable
private fun Document(state: PdfViewerUiState.Ready) {
    val viewerState = rememberPdfViewerState()

    // The viewer's own state is not saveable, so the page is remembered by hand and restored on
    // the way back. Rotating the phone and landing on page one of a forty-page bulletin would
    // undo the reader's work (research.md D-010).
    var savedPage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewerState) {
        if (savedPage > 0) viewerState.scrollToPage(savedPage)
        snapshotFlow { viewerState.firstVisiblePage }.collect { savedPage = it }
    }

    PdfViewer(
        pdfDocument = state.pdf,
        state = viewerState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_PDF_VIEWER),
    )
}

@Composable
private fun Loading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_PDF_VIEWER_LOADING),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.pdf_viewer_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
            modifier = Modifier.padding(top = BocTheme.spacing.space10),
        )
    }
}

@Composable
private fun Failure(error: DomainError, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_PDF_VIEWER_ERROR),
    ) {
        ErrorMessage(
            message = stringResource(
                when (error) {
                    DomainError.Network -> R.string.document_error_network
                    DomainError.Unknown -> R.string.pdf_viewer_error
                },
            ),
            onRetry = onRetry,
        )
    }
}

private const val MAX_TITLE_LINES = 1
