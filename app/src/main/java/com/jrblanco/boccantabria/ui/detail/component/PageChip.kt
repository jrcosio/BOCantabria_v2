package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_AI_SUMMARY_SOURCES: String = "ai_summary_sources"

/**
 * The chip beside a claim: «where does *this* come from?» (FR-020).
 *
 * Distinct from [sourceChipTag] because the same page legitimately appears in both places, and two
 * nodes carrying the same tag are two nodes nothing can address — neither a test nor anything else
 * that navigates by identity.
 */
fun pageChipTag(page: Int): String = "ai_page_chip_$page"

/** The chip in the sources row: «which pages did this summary read?» (design §20.4). */
fun sourceChipTag(page: Int): String = "ai_source_chip_$page"

/**
 * A page of the document that backs something the summary says, and opens it there.
 *
 * A reference that cannot be followed is not a reference (FR-021). The content description says it
 * can be opened, not just which page it is: somebody who cannot see the screen has to be told there
 * is something to press.
 *
 * Blue rather than the AI violet, per §20.4 of the design document: the chip points at the official
 * document, and the document is not the part a machine wrote.
 */
@Composable
fun PageChip(
    page: Int,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = pageChipTag(page),
) {
    val label = stringResource(R.string.ai_summary_page_chip, page)
    val accessible = stringResource(R.string.ai_summary_page_chip_accessible, page)

    AssistChip(
        onClick = { onOpenPage(page) },
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_document),
                contentDescription = null,
                modifier = Modifier.size(CHIP_ICON_SIZE),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier
            // §20.4 and the accessibility rules: a touch target below this is a target people miss.
            .defaultMinSize(minHeight = MIN_TOUCH_TARGET)
            .semantics { contentDescription = accessible }
            .testTag(testTag),
    )
}

/** The pages a single element of the summary rests on. */
@Composable
fun PageChips(pages: List<Int>, onOpenPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (pages.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEach { page -> PageChip(page = page, onOpenPage = onOpenPage) }
    }
}

private val CHIP_ICON_SIZE = 18.dp
private val MIN_TOUCH_TARGET = 48.dp
