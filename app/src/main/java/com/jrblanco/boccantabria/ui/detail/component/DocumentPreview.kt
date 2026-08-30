package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.ErrorMessage
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.ui.pdf.PagePreview
import com.jrblanco.boccantabria.ui.pdf.rememberFirstPage

const val TAG_DETAIL_PREVIEW: String = "detail_preview"
const val TAG_DETAIL_PREVIEW_LOADING: String = "detail_preview_loading"
const val TAG_DETAIL_PREVIEW_ERROR: String = "detail_preview_error"

/**
 * The first page of the document, or what is happening instead.
 *
 * A download with a known total shows a determinate bar and one without shows a spinner: claiming
 * a percentage the server never told us would be worse than admitting we do not know.
 */
@Composable
fun DocumentPreview(
    status: DocumentStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_DETAIL_PREVIEW),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
    ) {
        Text(
            text = stringResource(R.string.detail_preview_title),
            style = MaterialTheme.typography.labelLarge,
            color = BocTheme.colors.textSecondary,
        )

        when (status) {
            DocumentStatus.Absent -> Waiting(fraction = null)
            is DocumentStatus.Downloading -> Waiting(fraction = status.fraction)
            is DocumentStatus.Available -> FirstPage(status.document.localPath)
            is DocumentStatus.Failed -> Failure(status.error, onRetry)
        }
    }
}

@Composable
private fun Waiting(fraction: Float?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PLACEHOLDER_HEIGHT)
            .testTag(TAG_DETAIL_PREVIEW_LOADING),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (fraction == null) {
            CircularProgressIndicator()
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = stringResource(R.string.detail_preview_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FirstPage(localPath: String) {
    val widthPx = with(LocalDensity.current) { PREVIEW_WIDTH.roundToPx() }
    val preview by rememberFirstPage(localPath = localPath, targetWidthPx = widthPx)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BocTheme.colors.readerSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BocTheme.spacing.space3),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = preview) {
                PagePreview.Loading -> CircularProgressIndicator(
                    modifier = Modifier.padding(BocTheme.spacing.space6),
                )

                is PagePreview.Ready -> Image(
                    bitmap = state.bitmap,
                    contentDescription = stringResource(R.string.detail_preview_title),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )

                // The document is fine, only its picture is not. Said quietly, because the
                // «Abrir PDF oficial» button right below still works.
                PagePreview.Failed -> Text(
                    text = stringResource(R.string.detail_preview_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BocTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(BocTheme.spacing.space6),
                )
            }
        }
    }
}

@Composable
private fun Failure(error: DomainError, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PLACEHOLDER_HEIGHT)
            .testTag(TAG_DETAIL_PREVIEW_ERROR),
    ) {
        ErrorMessage(message = stringResource(error.messageRes), onRetry = onRetry)
    }
}

/** Section 34 of the design document: one voice for errors, whichever screen shows them. */
internal val DomainError.messageRes: Int
    get() = when (this) {
        DomainError.Network -> R.string.document_error_network
        DomainError.Unknown -> R.string.document_error_invalid
    }

private val PLACEHOLDER_HEIGHT = 220.dp
private val PREVIEW_WIDTH = 360.dp
