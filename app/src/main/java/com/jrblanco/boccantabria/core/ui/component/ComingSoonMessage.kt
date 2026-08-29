package com.jrblanco.boccantabria.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_COMING_SOON: String = "coming_soon"

/**
 * What a destination that is not built yet shows.
 *
 * Shared rather than written twice so that the two places it appears say the same thing in the
 * same voice. A destination that simply did nothing would read as a broken application.
 */
@Composable
fun ComingSoonMessage(
    iconRes: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(BocTheme.spacing.space6)
            .testTag(TAG_COMING_SOON),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = BocTheme.colors.textMuted,
            modifier = Modifier.size(ILLUSTRATION_SIZE),
        )
        Text(
            text = stringResource(R.string.coming_soon),
            style = MaterialTheme.typography.titleLarge,
            color = BocTheme.colors.textPrimary,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private val ILLUSTRATION_SIZE = 96.dp
