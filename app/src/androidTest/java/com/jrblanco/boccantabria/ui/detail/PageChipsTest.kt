package com.jrblanco.boccantabria.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.ui.detail.component.PageChips
import com.jrblanco.boccantabria.ui.detail.component.pageChipTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the row of page chips does when the pages do not fit on one line.
 *
 * Regression test. `PageChips` was a bare [androidx.compose.foundation.layout.Row], which measures
 * its children against the width that is left over: the fourth chip was handed almost none, so
 * «Página 4» broke to one glyph per line and grew into a tall unreadable sliver, dragging the
 * height of the whole row with it. Seen on a real phone, never by a test — every existing chip
 * test cites one or two pages, and `assertIsDisplayed` passes on a squeezed chip because a
 * squeezed chip is still on screen. Height is what tells «wrapped» from «crushed» apart.
 *
 * Mounted on its own with `createComposeRule` and inside a container of a fixed width, so the
 * overflow does not depend on the screen of whatever device runs the suite.
 */
class PageChipsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun a_page_that_does_not_fit_wraps_at_its_full_width() {
        setContent(pages = listOf(1, 2, 3, 4))

        val first = composeRule.onNodeWithTag(pageChipTag(1)).getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithTag(pageChipTag(4)).getUnclippedBoundsInRoot()

        // El defecto: al chip que no cabía le llegaba un ancho casi nulo y crecía a lo alto. Con
        // float y delta a propósito, no con la sobrecarga de Object: comparar dos Dp por
        // autoboxeo es la trampa que ya está anotada en CLAUDE.md.
        assertEquals(
            "el chip que no cabe se está comprimiendo en lugar de bajar de línea",
            first.height.value,
            last.height.value,
            HEIGHT_TOLERANCE,
        )
        // Y que de verdad bajó, en lugar de quedarse en la misma línea o salirse por la derecha.
        assertTrue(
            "«Página 4» debería estar en una línea inferior",
            last.top.value >= first.bottom.value,
        )
        assertTrue(
            "«Página 4» se sale del contenedor",
            last.right.value <= CONTAINER_WIDTH.value,
        )
    }

    /** Envolver no debe costar la pulsación: el chip que bajó de línea sigue abriendo su página. */
    @Test
    fun the_page_that_wrapped_still_opens_the_document() {
        var opened: Int? = null
        setContent(pages = listOf(1, 2, 3, 4), onOpenPage = { opened = it })

        composeRule.onNodeWithTag(pageChipTag(4)).performClick()

        assertEquals(4, opened)
    }

    /** Lo que ya funcionaba: con lo que cabe en una línea, nada baja. */
    @Test
    fun pages_that_fit_stay_on_one_line() {
        setContent(pages = listOf(1, 2))

        val first = composeRule.onNodeWithTag(pageChipTag(1)).getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag(pageChipTag(2)).getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag(pageChipTag(2)).assertIsDisplayed()
        assertEquals(first.top.value, second.top.value, HEIGHT_TOLERANCE)
    }

    private fun setContent(pages: List<Int>, onOpenPage: (Int) -> Unit = {}) {
        composeRule.setContent {
            BOCantabriaTheme {
                // Un chip mide unos 110 dp, así que a este ancho entran dos por línea y cuatro
                // páginas desbordan siempre, en cualquier dispositivo que corra la tanda.
                Box(Modifier.width(CONTAINER_WIDTH)) {
                    PageChips(pages = pages, onOpenPage = onOpenPage)
                }
            }
        }
    }
}

private val CONTAINER_WIDTH = 280.dp
private const val HEIGHT_TOLERANCE = 0.5f
