package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.Publication

const val TAG_DETAIL_METADATA: String = "detail_metadata"

/**
 * The Documento tab: the metadata card of section 19.2 and, below it, the first page.
 *
 * The card is drawn from what the feed already gave us, so it is complete before any byte of the
 * document has been fetched. Someone on a slow connection reads the announcement's details while
 * the PDF is still arriving instead of watching an empty screen.
 */
@Composable
fun DocumentTab(
    publication: Publication,
    section: BocSection?,
    formattedDate: String,
    status: DocumentStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(BocTheme.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
    ) {
        MetadataCard(publication, section, formattedDate)
        DocumentPreview(status = status, onRetry = onRetry)
    }
}

@Composable
private fun MetadataCard(
    publication: Publication,
    section: BocSection?,
    formattedDate: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_DETAIL_METADATA),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BocTheme.elevation.level1),
    ) {
        Column(modifier = Modifier.padding(BocTheme.spacing.space4)) {
            Field(R.string.detail_field_description, publication.title, isFirst = true)
            // A block with no value is left out rather than filled with a dash: the feed simply
            // did not carry an issuer, and inventing a placeholder would look like a defect.
            publication.issuer?.let { Field(R.string.detail_field_issuer, it) }
            section?.let { Field(R.string.detail_field_section, it.name) }
            Field(R.string.detail_field_date, formattedDate)
            Field(R.string.detail_field_reference, publication.blobId ?: publication.externalKey)
            Field(R.string.detail_field_official, stringResource(publication.editionType.labelRes))
        }
    }
}

@Composable
private fun Field(labelRes: Int, value: String, isFirst: Boolean = false) {
    if (!isFirst) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = BocTheme.spacing.space3),
            color = BocTheme.colors.divider,
        )
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = BocTheme.colors.textSecondary,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = BocTheme.colors.textPrimary,
        modifier = Modifier.padding(top = BocTheme.spacing.space1),
    )
}

private val EditionType.labelRes: Int
    get() = when (this) {
        EditionType.ORDINARY -> R.string.detail_field_edition_ordinary
        EditionType.EXTRAORDINARY -> R.string.detail_field_edition_extraordinary
        EditionType.UNKNOWN -> R.string.detail_field_edition_unknown
    }
