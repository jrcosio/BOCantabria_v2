package com.jrblanco.boccantabria.ui.alerts.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.core.ui.theme.sectionColor
import com.jrblanco.boccantabria.core.util.RelativeTime
import com.jrblanco.boccantabria.domain.model.AlertNews
import com.jrblanco.boccantabria.domain.model.BocSection

fun alertNewsTag(externalKey: String): String = "alert_news_$externalKey"
fun alertNewsUnreadTag(externalKey: String): String = "alert_news_unread_$externalKey"

/**
 * One publication that matched: a blue dot and a tinted background while unread, the title, which
 * rules caught it, its section and when (experience document §10.1).
 */
@Composable
fun AlertNewsItem(
    news: AlertNews,
    section: BocSection?,
    detected: RelativeTime.Label,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unreadDescription = stringResource(R.string.alerts_news_unread)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(alertNewsTag(news.publication.externalKey)),
        color = if (news.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(BocTheme.spacing.space4),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.width(DOT_COLUMN), contentAlignment = Alignment.Center) {
                if (!news.isRead) {
                    Box(
                        modifier = Modifier
                            .size(DOT_SIZE)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            .semantics { contentDescription = unreadDescription }
                            .testTag(alertNewsUnreadTag(news.publication.externalKey)),
                    )
                }
            }
            Spacer(modifier = Modifier.width(BocTheme.spacing.space2))
            Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space1)) {
                Text(
                    text = news.publication.titleWithoutIssuer,
                    style = MaterialTheme.typography.titleSmall,
                    color = BocTheme.colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.alerts_news_matches, news.ruleNames.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    section?.let {
                        Text(
                            text = it.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = sectionColor(it.colorGroup),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = BocTheme.colors.textMuted,
                        )
                    }
                    Text(
                        text = relativeLabelText(detected),
                        style = MaterialTheme.typography.labelSmall,
                        color = BocTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

private val DOT_COLUMN = 12.dp
private val DOT_SIZE = 10.dp
