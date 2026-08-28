package com.jrblanco.boccantabria.ui.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.core.ui.theme.sectionColor
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.Publication

const val TAG_PUBLICATION_CARD: String = "publication_card"
const val TAG_PUBLICATION_SAVE: String = "publication_save"
const val TAG_PUBLICATION_SHARE: String = "publication_share"

/**
 * The central component of the application, per section 12 of the design document.
 *
 * Reading order is issuer, title, date, actions. The four-dp rule on the left carries the
 * section's colour, and the section name travels with it as text: colour groups, text
 * identifies. Nine sections share five colours, so without the text the indicator would say
 * less than it appears to.
 */
@Composable
fun PublicationCard(
    publication: Publication,
    section: BocSection?,
    formattedDate: String,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_PUBLICATION_CARD),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BocTheme.elevation.level1),
    ) {
        Row(modifier = Modifier.padding(BocTheme.spacing.space4)) {
            SectionRule(section)

            Column(
                modifier = Modifier.padding(start = BocTheme.spacing.space3),
                verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
            ) {
                publication.issuer?.let { issuer ->
                    Text(
                        text = issuer.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = BocTheme.colors.textSecondary,
                        maxLines = MAX_ISSUER_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = publication.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = MAX_TITLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )

                section?.let {
                    Text(
                        text = stringResource(R.string.publication_section, it.name),
                        style = MaterialTheme.typography.labelSmall,
                        color = sectionColor(it.colorGroup),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar),
                        contentDescription = null,
                        tint = BocTheme.colors.textSecondary,
                        modifier = Modifier.size(DATE_ICON_SIZE),
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = BocTheme.colors.textSecondary,
                        modifier = Modifier
                            .padding(start = BocTheme.spacing.space2)
                            .weight(1f),
                    )

                    IconButton(onClick = onSave, modifier = Modifier.testTag(TAG_PUBLICATION_SAVE)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bookmark),
                            contentDescription = stringResource(R.string.publication_save),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ACTION_ICON_SIZE),
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.testTag(TAG_PUBLICATION_SHARE)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.publication_share),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ACTION_ICON_SIZE),
                        )
                    }
                }
            }
        }
    }
}

/** The four-dp section rule. Decorative on its own: the section is named in text alongside. */
@Composable
private fun SectionRule(section: BocSection?) {
    Column(
        modifier = Modifier
            .width(RULE_WIDTH)
            .height(RULE_HEIGHT)
            .background(
                color = section?.let { sectionColor(it.colorGroup) }
                    ?: MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.extraSmall,
            )
            .clearAndSetSemantics { },
    ) {}
}

private val RULE_WIDTH = 4.dp
private val RULE_HEIGHT = 56.dp
private val DATE_ICON_SIZE = 18.dp
private val ACTION_ICON_SIZE = 24.dp
private const val MAX_ISSUER_LINES = 2
private const val MAX_TITLE_LINES = 4
