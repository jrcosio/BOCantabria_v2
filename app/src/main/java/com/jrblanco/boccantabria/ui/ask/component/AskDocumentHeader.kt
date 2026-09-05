package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_ASK_HEADER: String = "ask_header"
const val TAG_ASK_SAVE: String = "ask_save"

/**
 * Which publication is being talked about, and a way to keep it.
 *
 * Saving lives here rather than only in the detail because someone who reads three answers and decides
 * the announcement matters should not have to go back to say so (FR-043, FR-044).
 */
@Composable
fun AskDocumentHeader(
    title: String,
    date: LocalDate,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BocTheme.elevation.level1),
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_ASK_HEADER),
    ) {
        Row(
            modifier = Modifier.padding(BocTheme.spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(ICON_BOX),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_document),
                    contentDescription = null,
                    modifier = Modifier.padding(BocTheme.spacing.space2),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = SPANISH_DATE.format(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = BocTheme.colors.textSecondary,
                )
            }

            IconButton(onClick = onToggleSaved, modifier = Modifier.testTag(TAG_ASK_SAVE)) {
                Icon(
                    painter = painterResource(
                        if (isSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                    ),
                    contentDescription = stringResource(
                        if (isSaved) R.string.detail_unsave else R.string.detail_save,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val ICON_BOX = 40.dp
private val SPANISH_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))
