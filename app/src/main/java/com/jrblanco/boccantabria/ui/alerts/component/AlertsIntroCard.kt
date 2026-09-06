package com.jrblanco.boccantabria.ui.alerts.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

const val TAG_ALERTS_CREATE: String = "alerts_create"

/** «Sigue lo que te importa», with the one action that matters on this screen (spec §12.2). */
@Composable
fun AlertsIntroCard(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = BocTheme.elevation.level0),
    ) {
        Column(
            modifier = Modifier.padding(BocTheme.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(R.drawable.ic_notifications),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ICON_SIZE),
                )
                Spacer(modifier = Modifier.width(BocTheme.spacing.space4))
                Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space1)) {
                    Text(
                        text = stringResource(R.string.alerts_intro_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.alerts_intro_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BocTheme.colors.textSecondary,
                    )
                }
            }
            Button(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_ALERTS_CREATE),
            ) {
                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(BUTTON_ICON_SIZE))
                Spacer(modifier = Modifier.width(BocTheme.spacing.space2))
                Text(text = stringResource(R.string.alerts_create))
            }
        }
    }
}

private val ICON_SIZE = 40.dp
private val BUTTON_ICON_SIZE = 20.dp
