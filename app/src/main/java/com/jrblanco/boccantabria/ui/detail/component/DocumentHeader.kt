package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.jrblanco.boccantabria.core.ui.theme.sectionColor
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.Publication

const val TAG_DETAIL_HEADER: String = "detail_header"
const val TAG_DETAIL_SECTION: String = "detail_section"
const val TAG_DETAIL_TITLE: String = "detail_title"
const val TAG_DETAIL_ISSUER: String = "detail_issuer"
const val TAG_DETAIL_DATE: String = "detail_date"
const val TAG_DETAIL_OFFICIAL_BADGE: String = "detail_official_badge"

/**
 * The head of the detail screen, section 18.2 in the order fixed by 18.3.
 *
 * The title is **not** truncated, unlike the card's. On the bulletin a long title has to compete
 * with a hundred others and is cut at four lines; here it is the whole reason the screen exists,
 * and a reader who cannot see the end of the heading has to open the PDF to learn what the
 * announcement is about.
 */
@Composable
fun DocumentHeader(
    publication: Publication,
    section: BocSection?,
    formattedDate: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_DETAIL_HEADER),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = BocTheme.spacing.space5,
                vertical = BocTheme.spacing.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
        ) {
            section?.let {
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = sectionColor(it.colorGroup),
                    modifier = Modifier.testTag(TAG_DETAIL_SECTION),
                )
            }

            Text(
                text = publication.titleWithoutIssuer,
                style = MaterialTheme.typography.headlineLarge,
                color = BocTheme.colors.textPrimary,
                modifier = Modifier.testTag(TAG_DETAIL_TITLE),
            )

            // Nothing is drawn when the feed gave no issuer: an icon beside an empty line would
            // read as a loading row that never finishes.
            publication.issuer?.let { issuer ->
                IconLine(
                    iconRes = R.drawable.ic_organization,
                    description = stringResource(R.string.detail_issuer_description),
                    text = issuer,
                    modifier = Modifier.testTag(TAG_DETAIL_ISSUER),
                )
            }

            IconLine(
                iconRes = R.drawable.ic_calendar,
                description = stringResource(R.string.detail_date_description),
                text = formattedDate,
                modifier = Modifier.testTag(TAG_DETAIL_DATE),
            )

            OfficialBadge(modifier = Modifier.testTag(TAG_DETAIL_OFFICIAL_BADGE))
        }
    }
}

@Composable
private fun IconLine(
    iconRes: Int,
    description: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = BocTheme.colors.textSecondary,
            modifier = Modifier.size(LINE_ICON_SIZE),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
            modifier = Modifier.padding(start = BocTheme.spacing.space2),
        )
    }
}

/** Outlined rather than filled: it states a fact about the document, it is not an action. */
@Composable
private fun OfficialBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(BADGE_BORDER, BocTheme.colors.accentOfficial),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BocTheme.spacing.space3,
                vertical = BocTheme.spacing.space2,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_official),
                contentDescription = null,
                tint = BocTheme.colors.accentOfficial,
                modifier = Modifier.size(BADGE_ICON_SIZE),
            )
            Text(
                text = stringResource(R.string.detail_official_badge),
                style = MaterialTheme.typography.labelLarge,
                color = BocTheme.colors.accentOfficial,
                modifier = Modifier.padding(start = BocTheme.spacing.space2),
            )
        }
    }
}

private val LINE_ICON_SIZE = 18.dp
private val BADGE_ICON_SIZE = 16.dp
private val BADGE_BORDER = 1.dp
