package com.jrblanco.boccantabria.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.TAG_PUBLICATIONS
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_BAR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression: no dead strip between a destination's content and the bottom bar.
 *
 * `MainShell` reserves the bar's room with `padding(innerPadding)`, but padding does **not** consume
 * a window inset. Without saying so out loud, each destination's own `Scaffold` —which has no bottom
 * bar and takes the default `systemBars` insets— applies the bottom system inset a second time, and
 * the list ends one navigation bar short of the bar that is already clear of it.
 *
 * It is measured on the bulletin because that is where it was reported, but the cause is shared: the
 * three destinations hang off the same host and the fix lives there, so this covers all three.
 *
 * On a device using gesture navigation the bottom inset can be zero and the assertion, while still
 * correct, proves nothing. The instrumented suite is meant to run on an emulator with **three-button
 * navigation** for exactly this reason; it is written down in `quickstart.md`.
 */
class MainShellBottomInsetTest {

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun the_content_reaches_the_bottom_bar_with_no_dead_strip_between_them() {
        var insetPx = 0

        composeRule.setContent {
            insetPx = WindowInsets.systemBars.getBottom(LocalDensity.current)
            BOCantabriaTheme {
                MainShell(navController = rememberNavController(), onOpenPublication = {})
            }
        }
        // Espera a que aterrice la primera sincronización: mientras están los esqueletos la
        // composición no llega a reposo y una espera de reposo se colgaría en vez de fallar.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATIONS).fetchSemanticsNodes().isNotEmpty()
        }

        val listBottom = composeRule.onNodeWithTag(TAG_PUBLICATIONS)
            .fetchSemanticsNode().boundsInRoot.bottom
        val barTop = composeRule.onNodeWithTag(TAG_BOTTOM_BAR)
            .fetchSemanticsNode().boundsInRoot.top

        // Se afirma lo que dice el requisito y nada más: entre el fondo de la lista y el techo de la
        // barra no cabe nada. La tolerancia es por redondeo de píxeles, no por holgura de diseño.
        //
        // Sin el arreglo el hueco es del orden del margen del sistema —una barra de navegación
        // entera—, así que la prueba distingue de sobra un caso del otro.
        val gap = (barTop - listBottom).toInt()
        assertTrue(
            "queda una franja muerta de $gap px entre la lista y la barra inferior; " +
                "el margen del sistema mide $insetPx px, y la tolerancia es de $TOLERANCE_PX",
            gap <= TOLERANCE_PX,
        )
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L

        /** Redondeo de píxeles al medir dos nodos distintos. Nada más. */
        const val TOLERANCE_PX = 2
    }
}
