package com.jrblanco.boccantabria.ui.alerts

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.IllustratedMessage
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.ui.alerts.component.AlertNewsItem
import com.jrblanco.boccantabria.ui.alerts.component.AlertRuleCard
import com.jrblanco.boccantabria.ui.alerts.component.AlertSettingsSheet
import com.jrblanco.boccantabria.ui.alerts.component.AlertsIntroCard
import com.jrblanco.boccantabria.ui.alerts.component.DeleteAlertDialog
import com.jrblanco.boccantabria.ui.alerts.component.NotificationsDisabledBanner
import com.jrblanco.boccantabria.ui.alerts.component.openNotificationSettings
import com.jrblanco.boccantabria.ui.alerts.component.relativeLabelText
import org.koin.androidx.compose.koinViewModel

const val TAG_ALERTS_SCREEN: String = "alerts_screen"
const val TAG_ALERTS_TABS: String = "alerts_tabs"
const val TAG_ALERTS_TAB_NEWS: String = "alerts_tab_news"
const val TAG_ALERTS_TAB_RULES: String = "alerts_tab_rules"
const val TAG_ALERTS_SETTINGS: String = "alerts_settings"
const val TAG_ALERTS_NEWS_LIST: String = "alerts_news_list"
const val TAG_ALERTS_NEWS_EMPTY: String = "alerts_news_empty"
const val TAG_ALERTS_RULES_LIST: String = "alerts_rules_list"
const val TAG_ALERTS_RULES_EMPTY: String = "alerts_rules_empty"
const val TAG_ALERTS_EMPTY_ACTION: String = "alerts_empty_action"
const val TAG_ALERTS_MARK_ALL_READ: String = "alerts_mark_all_read"
const val TAG_ALERTS_ACTIVE_COUNT: String = "alerts_active_count"

/**
 * Avisos with its state attached.
 *
 * Split from [AlertsContent] so the drawing can be mounted on its own in a test, like every screen of
 * the house. The sections arrive as a parameter, as they do for the bulletin: they are the whole
 * tree, they never change, and the frame above already has them.
 */
@Composable
@Suppress("LongParameterList")
fun AlertsScreen(
    sections: List<BocSection>,
    onOpenPublication: (String) -> Unit,
    onCreateRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onDuplicateRule: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coming back from Android's settings emits nothing: the screen asks again on resume.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    val failureText = stringResource(R.string.alerts_action_failed)
    LaunchedEffect(state.actionFailed) {
        if (state.actionFailed) {
            Toast.makeText(context, failureText, Toast.LENGTH_SHORT).show()
            viewModel.onActionFailureConsumed()
        }
    }

    AlertsContent(
        state = state,
        sections = sections,
        onTabSelected = viewModel::onTabSelected,
        onOpenPublication = onOpenPublication,
        onCreateRule = onCreateRule,
        onEditRule = onEditRule,
        onDuplicateRule = onDuplicateRule,
        onToggleEnabled = viewModel::onToggleEnabled,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onMarkAllRead = viewModel::onMarkAllRead,
        onSettingsOpened = viewModel::onSettingsOpened,
        onSettingsClosed = viewModel::onSettingsClosed,
        onOpenAndroidSettings = { openNotificationSettings(context) },
        modifier = modifier,
    )
}

/**
 * The screen with nothing behind it: every piece of state arrives as a parameter and every gesture
 * leaves as an event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
fun AlertsContent(
    state: AlertsUiState,
    sections: List<BocSection>,
    modifier: Modifier = Modifier,
    onTabSelected: (AlertsTab) -> Unit = {},
    onOpenPublication: (String) -> Unit = {},
    onCreateRule: () -> Unit = {},
    onEditRule: (String) -> Unit = {},
    onDuplicateRule: (String) -> Unit = {},
    onToggleEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteRequested: (AlertRule) -> Unit = {},
    onDeleteConfirmed: () -> Unit = {},
    onDeleteCancelled: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onSettingsOpened: () -> Unit = {},
    onSettingsClosed: () -> Unit = {},
    onOpenAndroidSettings: () -> Unit = {},
) {
    val sectionsByCode = remember(sections) { sections.associateBy { it.code } }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_ALERTS_SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_escudo_cantabria),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(SHIELD_HEIGHT),
                        )
                        Text(
                            text = stringResource(R.string.alerts_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = BocTheme.spacing.space3),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsOpened, modifier = Modifier.testTag(TAG_ALERTS_SETTINGS)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tune),
                            contentDescription = stringResource(R.string.alerts_settings),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            AlertsTabs(selected = state.tab, unreadCount = state.unreadCount, onSelect = onTabSelected)
            when (state.tab) {
                AlertsTab.NEWS -> NewsTab(
                    state = state,
                    sectionsByCode = sectionsByCode,
                    onOpenPublication = onOpenPublication,
                    onMarkAllRead = onMarkAllRead,
                    onCreateRule = onCreateRule,
                )
                AlertsTab.RULES -> RulesTab(
                    state = state,
                    onCreateRule = onCreateRule,
                    onEditRule = onEditRule,
                    onDuplicateRule = onDuplicateRule,
                    onToggleEnabled = onToggleEnabled,
                    onDeleteRequested = onDeleteRequested,
                    onOpenAndroidSettings = onOpenAndroidSettings,
                )
            }
        }
    }

    state.pendingDelete?.let { rule ->
        DeleteAlertDialog(rule = rule, onConfirm = onDeleteConfirmed, onCancel = onDeleteCancelled)
    }
    if (state.settingsOpen) {
        AlertSettingsSheet(
            status = state.notificationStatus,
            lastSyncAt = state.lastSyncAt,
            onOpenSettings = onOpenAndroidSettings,
            onDismiss = onSettingsClosed,
        )
    }
}

/** The two tabs, in the style of the detail's: a 3 dp indicator and no capsules (design §11.7). */
@Composable
private fun AlertsTabs(selected: AlertsTab, unreadCount: Int, onSelect: (AlertsTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = Modifier.testTag(TAG_ALERTS_TABS),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { positions ->
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(positions[selected.ordinal]),
                height = INDICATOR_HEIGHT,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        Tab(
            selected = selected == AlertsTab.NEWS,
            onClick = { onSelect(AlertsTab.NEWS) },
            modifier = Modifier.testTag(TAG_ALERTS_TAB_NEWS),
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = BocTheme.colors.textSecondary,
            text = {
                Text(
                    text = if (unreadCount > 0) {
                        stringResource(R.string.alerts_tab_news_count, unreadCount)
                    } else {
                        stringResource(R.string.alerts_tab_news)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            },
        )
        Tab(
            selected = selected == AlertsTab.RULES,
            onClick = { onSelect(AlertsTab.RULES) },
            modifier = Modifier.testTag(TAG_ALERTS_TAB_RULES),
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = BocTheme.colors.textSecondary,
            text = { Text(text = stringResource(R.string.alerts_tab_rules), style = MaterialTheme.typography.labelLarge) },
        )
    }
}

@Composable
private fun NewsTab(
    state: AlertsUiState,
    sectionsByCode: Map<String, BocSection>,
    onOpenPublication: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onCreateRule: () -> Unit,
) {
    if (state.news.isEmpty()) {
        IllustratedMessage(
            iconRes = R.drawable.ic_notifications_filled,
            title = stringResource(R.string.alerts_news_empty_title),
            description = stringResource(R.string.alerts_news_empty_body),
            modifier = Modifier.testTag(TAG_ALERTS_NEWS_EMPTY),
            // The default tab of a person without rules: a way to the one thing worth doing.
            action = if (state.rules.isEmpty()) {
                {
                    OutlinedButton(onClick = onCreateRule, modifier = Modifier.testTag(TAG_ALERTS_EMPTY_ACTION)) {
                        Text(text = stringResource(R.string.alerts_rules_empty_action))
                    }
                }
            } else {
                null
            },
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_ALERTS_NEWS_LIST),
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
    ) {
        if (state.unreadCount > 0) {
            item(key = "mark_all") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onMarkAllRead, modifier = Modifier.testTag(TAG_ALERTS_MARK_ALL_READ)) {
                        Icon(painter = painterResource(R.drawable.ic_done_all), contentDescription = null)
                        Text(
                            text = stringResource(R.string.alerts_news_mark_all_read),
                            modifier = Modifier.padding(start = BocTheme.spacing.space2),
                        )
                    }
                }
            }
        }
        state.news.forEach { day ->
            item(key = "day_${day.label}") {
                Text(
                    text = relativeLabelText(day.label).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = BocTheme.colors.textMuted,
                    modifier = Modifier.padding(top = BocTheme.spacing.space3, bottom = BocTheme.spacing.space1),
                )
            }
            items(items = day.items, key = { it.publication.externalKey }) { news ->
                AlertNewsItem(
                    news = news,
                    section = sectionsByCode[news.publication.classificationCode],
                    detected = day.label,
                    onClick = { onOpenPublication(news.publication.externalKey) },
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun RulesTab(
    state: AlertsUiState,
    onCreateRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onDuplicateRule: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDeleteRequested: (AlertRule) -> Unit,
    onOpenAndroidSettings: () -> Unit,
) {
    if (state.rules.isEmpty()) {
        IllustratedMessage(
            iconRes = R.drawable.ic_notifications_filled,
            title = stringResource(R.string.alerts_rules_empty_title),
            description = stringResource(R.string.alerts_rules_empty_body),
            modifier = Modifier.testTag(TAG_ALERTS_RULES_EMPTY),
            action = {
                OutlinedButton(onClick = onCreateRule, modifier = Modifier.testTag(TAG_ALERTS_EMPTY_ACTION)) {
                    Text(text = stringResource(R.string.alerts_rules_empty_action))
                }
            },
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_ALERTS_RULES_LIST),
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
    ) {
        if (state.showsPermissionBanner) {
            item(key = "banner") { NotificationsDisabledBanner(onOpenSettings = onOpenAndroidSettings) }
        }
        item(key = "intro") { AlertsIntroCard(onCreate = onCreateRule) }
        item(key = "header") {
            Column(modifier = Modifier.padding(top = BocTheme.spacing.space2)) {
                Text(
                    text = stringResource(R.string.alerts_rules_header),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = pluralStringResource(R.plurals.alerts_active_count, state.activeCount, state.activeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BocTheme.colors.textMuted,
                    modifier = Modifier.testTag(TAG_ALERTS_ACTIVE_COUNT),
                )
            }
        }
        items(items = state.rules, key = { it.overview.rule.id }) { card ->
            AlertRuleCard(
                card = card,
                onToggleEnabled = { enabled -> onToggleEnabled(card.overview.rule.id, enabled) },
                onEdit = { onEditRule(card.overview.rule.id) },
                onDuplicate = { onDuplicateRule(card.overview.rule.id) },
                onDelete = { onDeleteRequested(card.overview.rule) },
            )
        }
    }
}

@Composable
private fun listPadding() = PaddingValues(
    start = BocTheme.spacing.screenMargin,
    end = BocTheme.spacing.screenMargin,
    top = BocTheme.spacing.space3,
    bottom = BocTheme.spacing.space10,
)

private val SHIELD_HEIGHT = 34.dp
private val INDICATOR_HEIGHT = 3.dp
