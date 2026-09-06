package com.jrblanco.boccantabria.ui.sections

import app.cash.turbine.test
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panel's presentation: what is listed, what is open, and which row is current.
 *
 * This class used to be twelve tests; six of them described a text filter over the section list.
 * Feature 013 removed that field — over nine rows it earned nothing, and a magnifier inside a panel
 * of sections reads as «search publications» — so those six went **with the functionality they
 * described**, not to make a build pass. The specification records the removal as a requirement of
 * its own (FR-024) and as a superseded requirement of feature 003.
 */
class SectionsViewModelTest {

    private fun viewModel() =
        SectionsViewModel(GetBocSectionsUseCase(BocSectionRepositoryImpl()))

    @Test
    fun `it opens showing the nine sections, all collapsed`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(9, state.rows.size)
            assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"), state.rows.map { it.section.code })
            assertTrue(state.expanded.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only the sections that have subsections can be expanded`() = runTest {
        viewModel().uiState.test {
            val expandable = awaitItem().rows.filter { it.isExpandable }.map { it.section.code }

            assertEquals(listOf("2", "4", "7", "8"), expandable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every section carries all of its subsections, always`() = runTest {
        // Nothing prunes the tree any more. Before feature 013 a query could hand back a section
        // with only its matching children, and this is the assertion that says that is over.
        viewModel().uiState.test {
            val children = awaitItem().rows.associate { it.section.code to it.children.map { c -> c.code } }

            assertEquals(listOf("2.1", "2.2", "2.3"), children.getValue("2"))
            assertEquals(listOf("4.1", "4.2", "4.3", "4.4"), children.getValue("4"))
            assertEquals(listOf("7.1", "7.2", "7.3", "7.4", "7.5"), children.getValue("7"))
            assertEquals(listOf("8.1", "8.2"), children.getValue("8"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expanding and collapsing a section toggles it`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onToggleExpanded("2")
            assertTrue("2" in awaitItem().expanded)
            viewModel.onToggleExpanded("2")
            assertFalse("2" in awaitItem().expanded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `several sections can be open at once`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onToggleExpanded("2")
            awaitItem()
            viewModel.onToggleExpanded("7")
            assertEquals(setOf("2", "7"), awaitItem().expanded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `what is expanded survives a change of selection`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onToggleExpanded("4")
            awaitItem()
            viewModel.onSelectionChanged(HomeSelection.Section("2", "2.2"))
            assertTrue("4" in awaitItem().expanded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the panel is told which selection is current, since it sits above the screen`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertEquals(HomeSelection.TodaysBulletin, awaitItem().selection)
            viewModel.onSelectionChanged(HomeSelection.Section("2", "2.2"))
            assertEquals(HomeSelection.Section("2", "2.2"), awaitItem().selection)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
