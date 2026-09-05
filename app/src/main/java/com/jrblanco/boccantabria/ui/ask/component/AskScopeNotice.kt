package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_SCOPE_NOTICE: String = "ask_scope_notice"

/**
 * «Las respuestas se basan solo en este documento», permanently on screen (FR-041).
 *
 * Permanent and not dismissible on purpose: it is the promise the whole feature rests on, and a
 * promise that scrolls away after the first read is a promise nobody remembers when the fifth answer
 * arrives.
 */
@Composable
fun AskScopeNotice(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_SCOPE_NOTICE),
    ) {
        Row(
            modifier = Modifier.padding(BocTheme.spacing.space3),
            horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
            )
            Text(
                text = stringResource(R.string.ask_scope_notice),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val ICON_SIZE = 20.dp
