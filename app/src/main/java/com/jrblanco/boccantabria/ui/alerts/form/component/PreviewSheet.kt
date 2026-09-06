package com.jrblanco.boccantabria.ui.alerts.form.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.PublicationCard
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.Publication
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_ALERT_FORM_PREVIEW_SHEET: String = "alert_form_preview_sheet"

/**
 * «Ver resultados»: what the draft would already match, with the bulletin's own card. Opening one
 * lands on its detail; nothing here writes anything (FR-068).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewSheet(
    publications: List<Publication>,
    sections: List<BocSection>,
    onOpenPublication: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sectionsByCode = remember(sections) { sections.associateBy { it.code } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(TAG_ALERT_FORM_PREVIEW_SHEET),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.alert_form_preview_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = BocTheme.spacing.space6, vertical = BocTheme.spacing.space2),
            )
            LazyColumn(
                contentPadding = PaddingValues(
                    start = BocTheme.spacing.screenMargin,
                    end = BocTheme.spacing.screenMargin,
                    top = BocTheme.spacing.space2,
                    bottom = BocTheme.spacing.space10,
                ),
                verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
            ) {
                items(items = publications, key = { it.externalKey }) { publication ->
                    PublicationCard(
                        publication = publication,
                        section = sectionsByCode[publication.classificationCode],
                        formattedDate = publication.publicationDate.format(SPANISH_LONG_DATE),
                        onClick = { onOpenPublication(publication.externalKey) },
                        onShare = {},
                        onSave = {},
                    )
                }
            }
        }
    }
}

private val SPANISH_LONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))
