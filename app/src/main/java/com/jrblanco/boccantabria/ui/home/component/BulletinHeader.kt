package com.jrblanco.boccantabria.ui.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_HEADER: String = "home_header"
const val TAG_HEADER_COUNT: String = "home_header_count"

/**
 * The editorial header of section 14.3.
 *
 * The outlined badge carries the number of announcements and **not** a bulletin number: the
 * official feeds do not publish one, and printing an invented figure next to the shield would be
 * presenting fabricated data as official.
 */
@Composable
fun BulletinHeader(
    header: BulletinHeaderData,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(BocTheme.spacing.space6)
            .testTag(TAG_HEADER),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        ) {
            Text(
                text = header.sectionName ?: stringResource(R.string.home_bulletin_today),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )

            header.date?.let { date ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar),
                        contentDescription = null,
                        tint = BocTheme.colors.onPrimaryAccent,
                        modifier = Modifier.size(DATE_ICON_SIZE),
                    )
                    Text(
                        text = date.format(SPANISH_LONG_DATE),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BocTheme.colors.onPrimaryMuted,
                        modifier = Modifier.padding(start = BocTheme.spacing.space2),
                    )
                }
            }
        }

        if (header.publicationCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.home_publication_count,
                    header.publicationCount,
                    header.publicationCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .border(
                        width = BADGE_BORDER,
                        color = BocTheme.colors.onPrimaryAccent,
                        shape = RoundedCornerShape(BADGE_RADIUS),
                    )
                    .padding(horizontal = BocTheme.spacing.space3, vertical = BocTheme.spacing.space2)
                    .testTag(TAG_HEADER_COUNT),
            )
        }
    }
}

/** `27 de agosto de 2026`, the form the design document shows. */
private val SPANISH_LONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))

private val DATE_ICON_SIZE = 18.dp
private val BADGE_BORDER = 1.dp
private val BADGE_RADIUS = 10.dp
