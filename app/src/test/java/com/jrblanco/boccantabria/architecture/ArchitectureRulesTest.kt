package com.jrblanco.boccantabria.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * The layering rule enforced as a test.
 *
 * Neither the Kotlin compiler nor Android Lint knows about `ui -> domain <- data`, so without
 * something like this the architecture degrades silently, one convenient import at a time.
 * These tests are the only thing that makes success criterion SC-004 real: a deliberate
 * violation must fail automatically, before the code ever reaches a device.
 */
class ArchitectureRulesTest {

    @Test
    fun `domain does not depend on the platform, on providers or on other layers`() {
        Konsist
            .scopeFromProject(sourceSetName = MAIN_SOURCE_SET)
            .files
            .filter { it.packagee?.name?.startsWith(DOMAIN_PACKAGE) == true }
            .assertTrue { file ->
                file.imports.none { import ->
                    FORBIDDEN_IN_DOMAIN.any { forbidden -> import.name.startsWith(forbidden) }
                }
            }
    }

    @Test
    fun `ui does not depend on data`() {
        Konsist
            .scopeFromProject(sourceSetName = MAIN_SOURCE_SET)
            .files
            .filter { it.packagee?.name?.startsWith(UI_PACKAGE) == true }
            .assertTrue { file ->
                file.imports.none { it.name.startsWith(DATA_PACKAGE) }
            }
    }

    @Test
    fun `use cases live in the domain usecase package`() {
        Konsist
            .scopeFromProject(sourceSetName = MAIN_SOURCE_SET)
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("$DOMAIN_PACKAGE.usecase") }
    }

    @Test
    fun `view models live in ui and extend ViewModel`() {
        Konsist
            .scopeFromProject(sourceSetName = MAIN_SOURCE_SET)
            .classes()
            .withNameEndingWith("ViewModel")
            .assertTrue { it.resideInPackage("$UI_PACKAGE..") && it.hasParentWithName("ViewModel") }
    }

    @Test
    fun `only data imports the Firebase SDK`() {
        Konsist
            .scopeFromProject(sourceSetName = MAIN_SOURCE_SET)
            .files
            .filter { it.packagee?.name?.startsWith(DATA_PACKAGE) != true }
            .assertTrue { file ->
                file.imports.none { it.name.startsWith(FIREBASE_PACKAGE) }
            }
    }

    /**
     * Colours are consumed from the theme, never declared at the point of use.
     *
     * Expressed as an import rule rather than by hunting for `Color(0xFF…)` literals inside
     * bodies: that would flag `Color.Transparent` and every alpha modifier, and a rule that cries
     * wolf is a rule people learn to work around.
     */
    @Test
    fun `only the theme package declares colours`() {
        Konsist
            .scopeFromProject(sourceSetName = MAIN_SOURCE_SET)
            .files
            .filter { it.packagee?.name?.startsWith(THEME_PACKAGE) != true }
            .assertTrue { file ->
                file.imports.none { it.name == COMPOSE_COLOR_IMPORT }
            }
    }

    /**
     * Makes SC-002 verifiable instead of a statement of good faith: every domain class and every
     * view model must have a test file named after it.
     */
    @Test
    fun `every domain class and every view model has a test file`() {
        val testFileNames = Konsist
            .scopeFromProject(sourceSetName = TEST_SOURCE_SET)
            .files
            .map { it.name }
            .toSet()

        Konsist
            .scopeFromProject(sourceSetName = MAIN_SOURCE_SET)
            .classes()
            .filter { klass ->
                // Only top-level classes: the nested carriers of a sealed hierarchy belong to
                // their parent's file and hold no behaviour of their own.
                val inDomain = klass.isTopLevel &&
                    klass.resideInPackage("$DOMAIN_PACKAGE..") &&
                    klass.name !in DOMAIN_CLASSES_WITHOUT_BEHAVIOUR
                inDomain || klass.name.endsWith("ViewModel")
            }
            .assertTrue { klass -> "${klass.name}Test" in testFileNames }
    }

    private companion object {
        const val MAIN_SOURCE_SET = "main"
        const val TEST_SOURCE_SET = "test"

        const val ROOT = "com.jrblanco.boccantabria"
        const val DOMAIN_PACKAGE = "$ROOT.domain"
        const val DATA_PACKAGE = "$ROOT.data"
        const val UI_PACKAGE = "$ROOT.ui"
        const val FIREBASE_PACKAGE = "com.google.firebase"
        const val THEME_PACKAGE = "$ROOT.core.ui.theme"
        const val COMPOSE_COLOR_IMPORT = "androidx.compose.ui.graphics.Color"

        val FORBIDDEN_IN_DOMAIN = listOf(
            "android.",
            "androidx.",
            FIREBASE_PACKAGE,
            "org.koin.",
            DATA_PACKAGE,
            UI_PACKAGE,
        )

        /**
         * Plain data holders with no behaviour to protect. Their contracts are exercised by the
         * tests of whatever consumes them, so demanding a dedicated test file would only add
         * ceremony. Keep this list short: every entry is a hole in SC-002.
         */
        val DOMAIN_CLASSES_WITHOUT_BEHAVIOUR = setOf("ContentItem", "AppConfig")
    }
}
