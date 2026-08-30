package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_ACTION_OPEN: String = "detail_action_open"
const val TAG_ACTION_ASK: String = "detail_action_ask"

/**
 * The bottom bar of section 18.5.
 *
 * Below a narrow width the two buttons stack. Side by side on a small screen, «Abrir PDF oficial»
 * would either wrap mid-word or ellipsise into something unreadable, and the principal action of
 * the screen cannot be the one that gets cut.
 */
@Composable
fun DetailActionBar(
    onOpen: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stacked = LocalConfiguration.current.screenWidthDp < STACK_BELOW_WIDTH_DP

    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = BocTheme.colors.divider)
            if (stacked) {
                Column(
                    modifier = Modifier.barPadding(),
                    verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
                ) {
                    OpenButton(onOpen, Modifier.fillMaxWidth())
                    AskButton(onAsk, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.barPadding(),
                    horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
                ) {
                    OpenButton(onOpen, Modifier.weight(OPEN_WEIGHT))
                    AskButton(onAsk, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Modifier.barPadding(): Modifier = fillMaxWidth()
    .padding(horizontal = BocTheme.spacing.space4, vertical = BocTheme.spacing.space3)

@Composable
private fun OpenButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.testTag(TAG_ACTION_OPEN)) {
        Icon(
            painter = painterResource(R.drawable.ic_document),
            contentDescription = null,
            modifier = Modifier.size(BUTTON_ICON_SIZE),
        )
        Text(
            text = stringResource(R.string.detail_action_open),
            modifier = Modifier.padding(start = BocTheme.spacing.space2),
        )
    }
}

@Composable
private fun AskButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.testTag(TAG_ACTION_ASK)) {
        Icon(
            painter = painterResource(R.drawable.ic_ask),
            contentDescription = null,
            tint = BocTheme.colors.aiAccent,
            modifier = Modifier.size(BUTTON_ICON_SIZE),
        )
        Text(
            text = stringResource(R.string.detail_action_ask),
            modifier = Modifier.padding(start = BocTheme.spacing.space2),
        )
    }
}

private const val STACK_BELOW_WIDTH_DP = 360
private const val OPEN_WEIGHT = 1.4f
private val BUTTON_ICON_SIZE = 18.dp
