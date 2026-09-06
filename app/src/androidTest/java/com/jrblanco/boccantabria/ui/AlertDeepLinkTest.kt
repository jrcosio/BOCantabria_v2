package com.jrblanco.boccantabria.ui

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.core.notification.AlertIntentExtras
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.alerts.TAG_ALERTS_NEWS_EMPTY
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_HEADER
import org.junit.Rule
import org.junit.Test

/**
 * A tapped notification: through the cover and onto the right screen (FR-048, FR-049).
 *
 * Launched through the real activity because the pending destination is written by `MainActivity`
 * from its intent — the very thing under test.
 */
class AlertDeepLinkTest {

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Test
    fun a_publication_target_crosses_the_cover_and_lands_on_its_detail() {
        val intent = launcherIntent()
            .putExtra(AlertIntentExtras.EXTRA_TARGET, AlertIntentExtras.TARGET_PUBLICATION)
            .putExtra(AlertIntentExtras.EXTRA_EXTERNAL_KEY, "boc:439765")

        ActivityScenario.launch<MainActivity>(intent).use {
            runCatching {
                composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                    composeRule.onAllNodesWithTag(TAG_DETAIL_HEADER).fetchSemanticsNodes().isNotEmpty()
                }
            }.onFailure {
                throw AssertionError("el toque en la notificación no llegó al detalle de la publicación", it)
            }
        }
    }

    @Test
    fun the_news_target_crosses_the_cover_and_lands_on_novedades() {
        val intent = launcherIntent().putExtra(AlertIntentExtras.EXTRA_TARGET, AlertIntentExtras.TARGET_NEWS)

        ActivityScenario.launch<MainActivity>(intent).use {
            runCatching {
                composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                    composeRule.onAllNodesWithTag(TAG_ALERTS_NEWS_EMPTY).fetchSemanticsNodes().isNotEmpty()
                }
            }.onFailure {
                throw AssertionError("el toque en el resumen no llegó a la pestaña Novedades", it)
            }
        }
    }

    private fun launcherIntent(): Intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).setAction(Intent.ACTION_MAIN)

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
    }
}
