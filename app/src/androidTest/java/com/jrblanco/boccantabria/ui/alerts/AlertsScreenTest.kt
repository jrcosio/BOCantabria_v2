package com.jrblanco.boccantabria.ui.alerts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.core.util.RelativeTime
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AlertNews
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.AlertRuleOverview
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.model.SectionSelection
import com.jrblanco.boccantabria.fake.alertRule
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.alerts.component.TAG_ALERTS_PERMISSION_BANNER
import com.jrblanco.boccantabria.ui.alerts.component.TAG_ALERT_DELETE_CONFIRM
import com.jrblanco.boccantabria.ui.alerts.component.TAG_ALERT_DELETE_DIALOG
import com.jrblanco.boccantabria.ui.alerts.component.TAG_ALERT_MENU_DELETE
import com.jrblanco.boccantabria.ui.alerts.component.TAG_ALERT_MENU_DUPLICATE
import com.jrblanco.boccantabria.ui.alerts.component.TAG_ALERT_MENU_EDIT
import com.jrblanco.boccantabria.ui.alerts.component.alertNewsUnreadTag
import com.jrblanco.boccantabria.ui.alerts.component.alertRuleMenuTag
import com.jrblanco.boccantabria.ui.alerts.component.alertRuleSwitchTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * What Avisos draws, on the stateless composable, in the two tabs.
 *
 * Mounted with `createComposeRule()` and not the real activity: every instrumented test that launches
 * it has to cross the cover's second and a bit, and this screen has nothing to do with the cover.
 */
class AlertsScreenTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    // ---------- Mis avisos ----------

    @Test
    fun with_no_rules_the_rules_tab_explains_what_is_missing_and_offers_a_way() {
        var created = 0
        setContent(AlertsUiState(tab = AlertsTab.RULES), onCreateRule = { created++ })

        composeRule.onNodeWithTag(TAG_ALERTS_RULES_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Aún no tienes avisos").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERTS_EMPTY_ACTION).performClick()

        assertEquals(1, created)
    }

    @Test
    fun the_rules_tab_lists_active_and_paused_rules_and_counts_the_active_ones() {
        setContent(
            AlertsUiState(
                tab = AlertsTab.RULES,
                rules = listOf(
                    card(alertRule(id = "r1", name = "Ganadería")),
                    card(alertRule(id = "r2", name = "Subvenciones rurales", keywords = listOf("ganadería", "medio rural"))),
                    card(alertRule(id = "r3", name = "Oposiciones", keywords = emptyList(), sectionCodes = setOf("2.2"), isEnabled = false)),
                ),
            ),
        )

        composeRule.onNodeWithTag(TAG_ALERTS_RULES_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("2 activos").assertIsDisplayed()
        composeRule.onNodeWithText("Ganadería").assertIsDisplayed()
        composeRule.onNodeWithText("Aviso pausado").assertIsDisplayed()
        composeRule.onNodeWithTag(alertRuleSwitchTag("r1")).assertIsOn()
        composeRule.onNodeWithTag(alertRuleSwitchTag("r3")).assertIsOff()
    }

    @Test
    fun the_switch_emits_the_toggle_without_opening_anything() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        setContent(
            AlertsUiState(tab = AlertsTab.RULES, rules = listOf(card(alertRule(id = "r1")))),
            onToggleEnabled = { id, enabled -> toggles += id to enabled },
        )

        composeRule.onNodeWithTag(alertRuleSwitchTag("r1")).performClick()

        assertEquals(listOf("r1" to false), toggles)
    }

    @Test
    fun the_card_shows_todays_matches() {
        setContent(AlertsUiState(tab = AlertsTab.RULES, rules = listOf(card(alertRule(id = "r1"), matchesToday = 1))))

        composeRule.onNodeWithText("1 coincidencia hoy").assertIsDisplayed()
    }

    @Test
    fun the_menu_offers_edit_duplicate_and_delete_and_delete_asks_first() {
        val rule = alertRule(id = "r1", name = "Ganadería")
        var edited: String? = null
        var duplicated: String? = null
        var deleteRequested: AlertRule? = null
        setContent(
            AlertsUiState(tab = AlertsTab.RULES, rules = listOf(card(rule))),
            onEditRule = { edited = it },
            onDuplicateRule = { duplicated = it },
            onDeleteRequested = { deleteRequested = it },
        )

        composeRule.onNodeWithTag(alertRuleMenuTag("r1")).performClick()
        composeRule.onNodeWithTag(TAG_ALERT_MENU_EDIT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERT_MENU_DUPLICATE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERT_MENU_DELETE).performClick()

        assertEquals(rule, deleteRequested)
        assertEquals(null, edited)
        assertEquals(null, duplicated)
    }

    @Test
    fun the_delete_dialog_names_the_rule_and_confirming_emits() {
        var confirmed = 0
        setContent(
            AlertsUiState(tab = AlertsTab.RULES, rules = listOf(card(alertRule(id = "r1", name = "Ganadería"))), pendingDelete = alertRule(id = "r1", name = "Ganadería")),
            onDeleteConfirmed = { confirmed++ },
        )

        composeRule.onNodeWithTag(TAG_ALERT_DELETE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Dejarás de recibir novedades que coincidan con «Ganadería».").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERT_DELETE_CONFIRM).performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun the_banner_shows_only_with_active_rules_and_notifications_disabled() {
        setContent(AlertsUiState(tab = AlertsTab.RULES, rules = listOf(card(alertRule(id = "r1"))), notificationStatus = NotificationStatus.DISABLED))
        composeRule.onNodeWithTag(TAG_ALERTS_PERMISSION_BANNER).assertIsDisplayed()
    }

    @Test
    fun the_banner_is_absent_when_notifications_are_granted() {
        setContent(AlertsUiState(tab = AlertsTab.RULES, rules = listOf(card(alertRule(id = "r1"))), notificationStatus = NotificationStatus.GRANTED))
        composeRule.onNodeWithTag(TAG_ALERTS_PERMISSION_BANNER).assertDoesNotExist()
    }

    // ---------- Novedades ----------

    @Test
    fun with_no_news_the_tab_says_so_and_without_rules_offers_to_create_one() {
        setContent(AlertsUiState(tab = AlertsTab.NEWS))

        composeRule.onNodeWithTag(TAG_ALERTS_NEWS_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No tienes avisos nuevos").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERTS_EMPTY_ACTION).assertIsDisplayed()
    }

    @Test
    fun news_marks_only_the_unread_ones_and_offers_to_read_all() {
        var markedAll = 0
        setContent(
            AlertsUiState(
                tab = AlertsTab.NEWS,
                unreadCount = 1,
                news = listOf(
                    AlertNewsDay(
                        RelativeTime.Label.Today,
                        listOf(
                            AlertNews(publication("boc:1", title = "AYUNTAMIENTO DE PIÉLAGOS: Ayudas ganaderas."), listOf("Ganadería"), 1L, isRead = false),
                            AlertNews(publication("boc:2", title = "AYUNTAMIENTO DE PIÉLAGOS: Bases de la bolsa."), listOf("Oposiciones"), 1L, isRead = true),
                        ),
                    ),
                ),
            ),
            onMarkAllRead = { markedAll++ },
        )

        // The dot sits inside a clickable row, whose semantics merge its children: the unmerged tree
        // is where its tag still exists.
        composeRule.onNodeWithTag(alertNewsUnreadTag("boc:1"), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(alertNewsUnreadTag("boc:2"), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Coincide con: Ganadería").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERTS_MARK_ALL_READ).performClick()

        assertEquals(1, markedAll)
    }

    @Test
    fun tapping_a_piece_of_news_opens_its_publication() {
        var opened: String? = null
        setContent(
            AlertsUiState(
                tab = AlertsTab.NEWS,
                news = listOf(AlertNewsDay(RelativeTime.Label.Today, listOf(AlertNews(publication("boc:1"), listOf("Ganadería"), 1L, isRead = false)))),
            ),
            onOpenPublication = { opened = it },
        )

        composeRule.onNodeWithText("Aprobación definitiva de la Ordenanza Fiscal.").performClick()

        assertEquals("boc:1", opened)
    }

    @Test
    fun the_settings_sheet_shows_the_last_check() {
        setContent(AlertsUiState(settingsOpen = true, lastSyncAt = 1_757_145_600_000L))

        composeRule.onNodeWithText("Última comprobación", substring = true).assertIsDisplayed()
    }

    private fun card(rule: AlertRule, matchesToday: Int = 0) = AlertRuleCardState(
        overview = AlertRuleOverview(rule, lastMatchedAt = null, matchesToday = matchesToday),
        sectionParts = SectionSelection.summaryParts(rule.sectionCodes, sections),
        lastMatchLabel = null,
    )

    @Suppress("LongParameterList")
    private fun setContent(
        state: AlertsUiState,
        onCreateRule: () -> Unit = {},
        onEditRule: (String) -> Unit = {},
        onDuplicateRule: (String) -> Unit = {},
        onToggleEnabled: (String, Boolean) -> Unit = { _, _ -> },
        onDeleteRequested: (AlertRule) -> Unit = {},
        onDeleteConfirmed: () -> Unit = {},
        onMarkAllRead: () -> Unit = {},
        onOpenPublication: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                AlertsContent(
                    state = state,
                    sections = sections,
                    onCreateRule = onCreateRule,
                    onEditRule = onEditRule,
                    onDuplicateRule = onDuplicateRule,
                    onToggleEnabled = onToggleEnabled,
                    onDeleteRequested = onDeleteRequested,
                    onDeleteConfirmed = onDeleteConfirmed,
                    onMarkAllRead = onMarkAllRead,
                    onOpenPublication = onOpenPublication,
                )
            }
        }
    }
}
