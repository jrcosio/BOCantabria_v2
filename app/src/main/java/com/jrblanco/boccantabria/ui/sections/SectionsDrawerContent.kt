package com.jrblanco.boccantabria.ui.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.core.ui.theme.sectionColor
import com.jrblanco.boccantabria.domain.model.BocSection

const val TAG_SECTIONS_DRAWER: String = "sections_drawer"
const val TAG_SECTIONS_HEADER: String = "sections_header"
const val TAG_SECTIONS_CLOSE: String = "sections_close"

fun sectionRowTag(code: String): String = "section_row_$code"
fun sectionToggleTag(code: String): String = "section_toggle_$code"

/**
 * The sections of the bulletin, as a side panel.
 *
 * The design document described this as a screen of its own; the owner asked for a panel, and the
 * document has been updated to match. It holds a header, the nine sections and their subsections.
 * There are no bells and no alerts card: since feature 012 the alerts live in the bottom bar, and a
 * rule is created from there, not from a row here.
 *
 * Since feature 013 there is also no filter field. Over nine rows it earned nothing, and a magnifier
 * inside a panel of sections reads as «search publications», which is the one thing it never did. In
 * its place the panel now says whose panel it is — shield and name — and offers an explicit way to
 * put itself away, for whoever does not know the swipe.
 */
@Composable
fun SectionsDrawerContent(
    state: SectionsUiState,
    onToggleExpanded: (String) -> Unit,
    onSelect: (BocSection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .testTag(TAG_SECTIONS_DRAWER),
    ) {
        DrawerHeader(onClose = onClose)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = state.rows, key = { it.section.code }) { row ->
                SectionRowItem(
                    row = row,
                    isExpanded = row.section.code in state.expanded,
                    onToggle = { onToggleExpanded(row.section.code) },
                    onSelect = onSelect,
                )
            }
        }
    }
}

/**
 * Shield, name and a way out.
 *
 * The arrow is the same glyph as the back arrow of the six screens that have one, and it sits at the
 * **end** of the row: it points left, which is where the panel goes when it is put away, so the
 * arrow and the movement agree. `ic_close` would have said «close», which in this application is
 * what the cross of a text field does.
 *
 * The shield needs **both** a height and an aspect ratio. An `Image` given only a height takes the
 * vector's intrinsic width — 32 dp — and the height asked for is never applied, so the shield comes
 * out tiny. That one cost time in feature 002; `SplashScreen.Emblem()` carries the same note.
 */
@Composable
private fun DrawerHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(start = BocTheme.spacing.space4, end = BocTheme.spacing.space2)
            .testTag(TAG_SECTIONS_HEADER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_escudo_cantabria),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(SHIELD_HEIGHT)
                .aspectRatio(SHIELD_ASPECT_RATIO),
        )
        Text(
            text = stringResource(R.string.app_bar_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(start = BocTheme.spacing.space3),
        )
        IconButton(onClick = onClose, modifier = Modifier.testTag(TAG_SECTIONS_CLOSE)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.sections_close),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SectionRowItem(
    row: SectionRow,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (BocSection) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) CHEVRON_OPEN_DEGREES else 0f,
        label = "chevron",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .clickable { onSelect(row.section) }
            .padding(horizontal = BocTheme.spacing.space4)
            .testTag(sectionRowTag(row.section.code)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(row.section.iconRes()),
            contentDescription = null,
            tint = sectionColor(row.section.colorGroup),
            modifier = Modifier.size(SECTION_ICON_SIZE),
        )
        Text(
            text = row.section.displayLabel,
            style = MaterialTheme.typography.titleMedium,
            color = BocTheme.colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = BocTheme.spacing.space4),
        )
        if (row.isExpandable) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(
                    if (isExpanded) R.string.sections_collapse else R.string.sections_expand,
                    row.section.name,
                ),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(SECTION_ICON_SIZE)
                    .rotate(chevronRotation)
                    .clickable(onClick = onToggle)
                    .testTag(sectionToggleTag(row.section.code)),
            )
        }
    }

    AnimatedVisibility(visible = isExpanded && row.isExpandable) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BocTheme.spacing.space4)
                .clip(MaterialTheme.shapes.small)
                .background(BocTheme.colors.surfaceSoft),
        ) {
            row.children.forEach { child ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CHILD_MIN_HEIGHT)
                        .clickable { onSelect(child) }
                        .padding(
                            start = BocTheme.spacing.space6,
                            end = BocTheme.spacing.space4,
                        )
                        .testTag(sectionRowTag(child.code)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
                ) {
                    Box(
                        modifier = Modifier
                            .size(BULLET_SIZE)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape),
                    )
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = BocTheme.colors.textPrimary,
                    )
                }
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** One icon per section, from section 9.2 of the design document. */
private fun BocSection.iconRes(): Int = when (parentCode ?: code) {
    "1" -> R.drawable.ic_section_general
    "2" -> R.drawable.ic_section_personnel
    "3" -> R.drawable.ic_section_contracting
    "4" -> R.drawable.ic_section_economy
    "5" -> R.drawable.ic_section_expropriation
    "6" -> R.drawable.ic_section_grants
    "7" -> R.drawable.ic_section_announcements
    "8" -> R.drawable.ic_section_judicial
    else -> R.drawable.ic_section_elections
}

private val SHIELD_HEIGHT = 40.dp

/** The vector is 79 x 137; without this the height above is silently ignored. */
private const val SHIELD_ASPECT_RATIO = 79f / 137f

private val ROW_MIN_HEIGHT = 72.dp
private val CHILD_MIN_HEIGHT = 56.dp
private val SECTION_ICON_SIZE = 28.dp
private val BULLET_SIZE = 6.dp
private const val CHEVRON_OPEN_DEGREES = 180f
