package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_ASK_FOOTER: String = "ask_footer"

/**
 * The way to the official document itself.
 *
 * The answers are an aid and the bulletin is the source. Keeping that one tap away is the same
 * argument that puts «Comprueba siempre el texto oficial» inside the summary when it is shared
 * (FR-046).
 */
@Composable
fun AskFooter(
    onOpenDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onOpenDocument,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_ASK_FOOTER),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_document),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(Modifier.size(BocTheme.spacing.space2))
        Text(
            text = stringResource(R.string.ask_open_document),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val ICON_SIZE = 18.dp
