package com.jrblanco.boccantabria.ui.alerts.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.ui.alerts.AlertRuleCardState

const val TAG_ALERT_MENU_EDIT: String = "alert_menu_edit"
const val TAG_ALERT_MENU_DUPLICATE: String = "alert_menu_duplicate"
const val TAG_ALERT_MENU_DELETE: String = "alert_menu_delete"

fun alertRuleTag(id: String): String = "alert_rule_$id"
fun alertRuleSwitchTag(id: String): String = "alert_rule_switch_$id"
fun alertRuleMenuTag(id: String): String = "alert_rule_menu_$id"

/**
 * One rule, as the mockup draws it: name, switch, a chip or two saying what kind of rule it is, the
 * words, the sections, and when it last fired. The menu holds the three actions of spec §12.4.
 *
 * The switch is the only control that writes without leaving the list (FR-010).
 */
@Composable
@Suppress("LongParameterList")
fun AlertRuleCard(
    card: AlertRuleCardState,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = card.overview.rule
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(alertRuleTag(rule.id)),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = BocTheme.elevation.level1),
    ) {
        Column(
            modifier = Modifier.padding(BocTheme.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val toggleDescription = stringResource(R.string.alerts_rule_toggle, rule.name)
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier
                        .semantics { contentDescription = toggleDescription }
                        .testTag(alertRuleSwitchTag(rule.id)),
                )
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag(alertRuleMenuTag(rule.id)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.alerts_rule_menu, rule.name),
                            tint = BocTheme.colors.textSecondary,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuEntry(R.string.alerts_rule_edit, R.drawable.ic_edit, TAG_ALERT_MENU_EDIT) {
                            menuOpen = false
                            onEdit()
                        }
                        MenuEntry(R.string.alerts_rule_duplicate, R.drawable.ic_copy, TAG_ALERT_MENU_DUPLICATE) {
                            menuOpen = false
                            onDuplicate()
                        }
                        MenuEntry(R.string.alerts_rule_delete, R.drawable.ic_delete, TAG_ALERT_MENU_DELETE) {
                            menuOpen = false
                            onDelete()
                        }
                    }
                }
            }

            KindChips(card)

            if (rule.keywords.isNotEmpty()) {
                IconLine(iconRes = R.drawable.ic_search, text = rule.keywords.joinToString(" · "))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconLine(
                    iconRes = R.drawable.ic_document,
                    text = sectionSummaryText(card.sectionParts),
                    modifier = Modifier.weight(1f),
                )
                LastMatch(card)
            }
        }
    }
}

/** The chips of the mockup: what kind of criteria the rule has, at a glance. */
@Composable
private fun KindChips(card: AlertRuleCardState) {
    val rule = card.overview.rule
    Row(horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2)) {
        card.sectionParts?.firstOrNull()?.let { part ->
            OutlineChip(text = if (card.sectionParts.size == 1) part.section.displayLabel else part.section.shortName)
        }
        when (rule.keywords.size) {
            0 -> Unit
            1 -> OutlineChip(text = stringResource(R.string.alerts_rule_kind_keyword))
            else -> OutlineChip(text = pluralStringResource(R.plurals.alerts_rule_kind_keywords, rule.keywords.size, rule.keywords.size))
        }
        if (!rule.organizationQuery.isNullOrBlank()) {
            OutlineChip(text = stringResource(R.string.alerts_rule_kind_organization))
        }
    }
}

@Composable
private fun OutlineChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BORDER_WIDTH, MaterialTheme.colorScheme.secondary),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = BocTheme.spacing.space2, vertical = BocTheme.spacing.space1),
        )
    }
}

@Composable
private fun IconLine(iconRes: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = BocTheme.colors.textSecondary,
            modifier = Modifier.size(LINE_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(BocTheme.spacing.space2))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «1 coincidencia hoy» in blue, «Última coincidencia: ayer» in grey, or «Aviso pausado». */
@Composable
private fun LastMatch(card: AlertRuleCardState) {
    val rule = card.overview.rule
    val today = card.overview.matchesToday
    when {
        !rule.isEnabled -> Text(
            text = stringResource(R.string.alerts_rule_paused),
            style = MaterialTheme.typography.labelMedium,
            color = BocTheme.colors.textMuted,
        )
        today > 0 -> Text(
            text = pluralStringResource(R.plurals.alerts_matches_today, today, today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        card.lastMatchLabel != null -> Text(
            text = stringResource(R.string.alerts_rule_last_match, relativeLabelText(card.lastMatchLabel)),
            style = MaterialTheme.typography.labelMedium,
            color = BocTheme.colors.textMuted,
        )
        else -> Text(
            text = stringResource(R.string.alerts_rule_no_match),
            style = MaterialTheme.typography.labelMedium,
            color = BocTheme.colors.textMuted,
        )
    }
}

@Composable
private fun MenuEntry(labelRes: Int, iconRes: Int, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = stringResource(labelRes)) },
        leadingIcon = { Icon(painter = painterResource(iconRes), contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

private val BORDER_WIDTH = 1.dp
private val LINE_ICON_SIZE = 20.dp
