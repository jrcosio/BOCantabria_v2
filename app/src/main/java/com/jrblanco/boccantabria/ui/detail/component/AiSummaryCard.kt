package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_AI_SUMMARY_CARD: String = "ai_summary_card"
const val TAG_AI_SUMMARY_DISCLAIMER: String = "ai_summary_disclaimer"

/**
 * The card that says a machine wrote this, and the warning under it.
 *
 * §20.1 and §20.2 of the design document: violet container, 18 dp corner, 20 dp padding, a 48 dp
 * circle with the AI mark. It is the first thing read, which is why the plain-language text goes
 * here and the structured sections below (FR-014).
 */
@Composable
fun AiSummaryCard(
    plainLanguageSummary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = BocTheme.colors.aiContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_AI_SUMMARY_CARD),
        ) {
            Row(
                modifier = Modifier.padding(BocTheme.spacing.space5),
                horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
            ) {
                // El robot y no la chispa: este círculo es el avatar de quien habla, y quien habla
                // es una máquina. `ic_ai` sigue marcando la pestaña y las acciones, donde lo que se
                // señala es la función y no el interlocutor (diseño §20.1, enmienda del 2/9/2026).
                Box(
                    modifier = Modifier
                        .size(EMBLEM_SIZE)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_robot),
                        contentDescription = null,
                        tint = BocTheme.colors.aiAccent,
                        modifier = Modifier.size(ICON_SIZE),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3)) {
                    Text(
                        text = stringResource(R.string.ai_summary_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = BocTheme.colors.textPrimary,
                    )
                    Text(
                        text = plainLanguageSummary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = BocTheme.colors.textPrimary,
                    )
                }
            }
        }

        AiDisclaimer()
    }
}

/**
 * «Comprueba siempre el texto oficial».
 *
 * §20.3: an outlined mark in red on a transparent background, never a big red block. And the whole
 * row carries **one** content description that says what the icon means, so a screen reader
 * announces the warning instead of skipping a decorative glyph (FR-024, SC-006).
 */
@Composable
fun AiDisclaimer(modifier: Modifier = Modifier) {
    val accessible = stringResource(R.string.ai_summary_disclaimer_accessible)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = accessible }
            .testTag(TAG_AI_SUMMARY_DISCLAIMER),
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = BocTheme.colors.accentOfficial,
            modifier = Modifier
                .size(ICON_SIZE)
                .clearAndSetSemantics { },
        )
        Text(
            text = stringResource(R.string.ai_summary_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
        )
    }
}

private val EMBLEM_SIZE = 48.dp
private val ICON_SIZE = 24.dp
