package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AiAnswerSource

const val TAG_ANSWER_SOURCES: String = "ask_answer_sources"

fun sourceRowTag(page: Int): String = "ask_source_$page"

/**
 * Where the answer says it comes from, and a way to go and check.
 *
 * A reference nobody can follow is not a reference — the same argument that gave the summary's page
 * chips their tap target. Every page here has been through the validator and exists in the document
 * (FR-013, FR-014).
 */
@Composable
fun AnswerSources(
    sources: List<AiAnswerSource>,
    onSourceClick: (AiAnswerSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(TAG_ANSWER_SOURCES)) {
        HorizontalDivider(color = BocTheme.colors.divider)

        Text(
            text = stringResource(R.string.ask_sources),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = BocTheme.spacing.space4,
                end = BocTheme.spacing.space4,
                top = BocTheme.spacing.space3,
                bottom = BocTheme.spacing.space2,
            ),
        )

        sources.forEachIndexed { index, source ->
            if (index > 0) {
                HorizontalDivider(
                    color = BocTheme.colors.divider,
                    modifier = Modifier.padding(horizontal = BocTheme.spacing.space4),
                )
            }
            SourceRow(source = source, onClick = { onSourceClick(source) })
        }
    }
}

@Composable
private fun SourceRow(source: AiAnswerSource, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(sourceRowTag(source.page))
            .padding(
                horizontal = BocTheme.spacing.space4,
                vertical = BocTheme.spacing.space3,
            ),
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(ICON_BOX),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_document),
                contentDescription = null,
                modifier = Modifier.padding(BocTheme.spacing.space2),
            )
        }

        Text(
            text = stringResource(R.string.ask_source_line, source.page, source.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = null,
            tint = BocTheme.colors.textMuted,
            // The same arrow, turned round: the icon set has no forward chevron and inventing one
            // would be a fortieth vector for a rotation.
            modifier = Modifier.graphicsLayer(rotationZ = 180f),
        )
    }
}

private val ICON_BOX = 32.dp
