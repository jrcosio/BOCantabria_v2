package com.jrblanco.boccantabria.ui.info

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.jrblanco.boccantabria.core.telemetry.NoOpAnalyticsTracker
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.core.util.AppVersionProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InfoScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun all_reference_content_and_the_installed_version_are_reachable() {
        composeRule.setContent {
            BOCantabriaTheme {
                InfoContent(
                    state = InfoUiState(versionName = VERSION),
                    onBack = {},
                    onOpenLink = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Acerca de").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_INFO_PORTRAIT).assertIsDisplayed()
        composeRule.onNodeWithText("José Ramón Blanco").assertIsDisplayed()
        composeRule.onNodeWithText("Tecnología con propósito").assertIsDisplayed()

        repeat(SCROLL_GESTURES_TO_BOTTOM) {
            composeRule.onNodeWithTag(TAG_INFO_LIST).performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithTag(TAG_INFO_VERSION).assertIsDisplayed()
        composeRule.onNodeWithText("Versión $VERSION").assertIsDisplayed()
        composeRule.onNodeWithText("Fuente de las publicaciones: Boletín Oficial de Cantabria.")
            .assertIsDisplayed()
    }

    @Test
    fun the_two_buttons_emit_their_exact_destinations() {
        val opened = mutableListOf<InfoLink>()
        composeRule.setContent {
            BOCantabriaTheme {
                InfoContent(
                    state = InfoUiState(versionName = VERSION),
                    onBack = {},
                    onOpenLink = opened::add,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithTag(TAG_INFO_LINKEDIN).performClick()
        composeRule.onNodeWithTag(TAG_INFO_GITHUB).performClick()

        assertEquals(listOf(InfoLink.LINKEDIN, InfoLink.GITHUB), opened)
        assertEquals("https://www.linkedin.com/in/jr-blanco/", opened[0].url)
        assertEquals("https://github.com/jrcosio/BOCantabria_v2.git", opened[1].url)
    }

    @Test
    fun a_missing_external_handler_keeps_the_screen_and_explains_the_failure() {
        val viewModel = InfoViewModel(FixedVersion, NoOpAnalyticsTracker())
        composeRule.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) {
                        throw IllegalArgumentException("No activity handles $uri")
                    }
                },
            ) {
                BOCantabriaTheme {
                    InfoScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }

        composeRule.onNodeWithTag(TAG_INFO_LINKEDIN).performClick()

        composeRule.onNodeWithTag(TAG_INFO_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("No hay ninguna aplicación disponible para abrir este enlace.")
            .assertIsDisplayed()
    }

    private object FixedVersion : AppVersionProvider {
        override val versionCode: Int = 8
        override val versionName: String = VERSION
    }

    private companion object {
        const val VERSION = "8.0.0-test"
        const val SCROLL_GESTURES_TO_BOTTOM = 4
    }
}
