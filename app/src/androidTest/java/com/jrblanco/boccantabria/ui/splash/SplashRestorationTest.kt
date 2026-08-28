package com.jrblanco.boccantabria.ui.splash

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.fake.FakeConnectivityDataSource
import com.jrblanco.boccantabria.fake.FakeRemoteConfigDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.startupGraphOverrides
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers FR-008: a configuration change must not restart the preparation.
 *
 * With the portrait lock there is no rotation any more, but switching to dark mode, changing the
 * font size or the device language still recreate the activity. The assertion that matters is the
 * call count: showing the content again after a silent reload would look identical on screen and
 * would be exactly the bug this test exists to catch.
 */
class SplashRestorationTest {

    private val connectivity = FakeConnectivityDataSource(online = true)
    private val remoteConfig = FakeRemoteConfigDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(startupGraphOverrides(connectivity, remoteConfig))

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun a_configuration_change_does_not_restart_the_preparation() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(HOME_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        val callsBeforeRecreate = remoteConfig.calls

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(HOME_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(callsBeforeRecreate, remoteConfig.calls)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val HOME_TITLE = "BOCantabria"
    }
}
