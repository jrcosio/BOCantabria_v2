package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.TAG_COMING_SOON
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

/**
 * What the two AI tabs show until the feature that fills them arrives.
 *
 * It keeps the identity of section 20.1 —the icon, the label, the accent and its container— rather
 * than falling back to the generic «Próximamente» panel. The point is to say what is coming, and a
 * blank grey message would say only that something is missing. The first use in the project of the
 * `aiAccent` and `aiContainer` tokens.
 */
@Composable
fun ComingSoonTab(
    iconRes: Int,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(BocTheme.spacing.space6)
            .testTag(TAG_COMING_SOON),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = BocTheme.colors.aiContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(BocTheme.spacing.space5),
                verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(EMBLEM_SIZE)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = BocTheme.colors.aiAccent,
                        modifier = Modifier.size(ICON_SIZE),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = BocTheme.colors.aiAccent,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.coming_soon),
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
            }
        }
    }
}

private val EMBLEM_SIZE = 48.dp
private val ICON_SIZE = 24.dp
