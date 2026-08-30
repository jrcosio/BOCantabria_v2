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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

/**
 * A screenful with nothing in it, said properly: an illustration, a headline, a line of support and
 * —when there is somewhere to go— a way out.
 *
 * Section 26.3 of the design document. Born by generalising `ComingSoonMessage`, which drew the same
 * centred column with a fixed headline: the saved screen's empty state needed a headline of its own
 * and an action, and `EmptyMessage` has neither an icon nor a button. Three places were already
 * drawing this shape without it having a name.
 *
 * @param action the secondary action, or nothing. A message with no way out is a legitimate case —
 *   that is what "coming soon" is.
 */
@Composable
fun IllustratedMessage(
    iconRes: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(BocTheme.spacing.space6),
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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = BocTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

private val ILLUSTRATION_SIZE = 96.dp
