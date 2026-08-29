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
    fun `filtering by text keeps only what matches`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("oposi")

            val state = awaitItem()
            assertEquals(listOf("2"), state.rows.map { it.section.code })
            assertEquals(listOf("2.2"), state.rows.single().children.map { it.code })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a section whose subsections match opens on its own`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("urbanismo")

            // Leaving the match behind a closed chevron would be the same as not finding it.
            val state = awaitItem()
            assertTrue("7" in state.expanded)
            assertEquals(listOf("7.1"), state.rows.single().children.map { it.code })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a section that matches by its own name keeps all of its subsections`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("Autoridades")

            val state = awaitItem()
            assertEquals(3, state.rows.single().children.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filtering ignores case and accents are matched literally`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("ECONOMÍA")
            assertEquals(listOf("4"), awaitItem().rows.map { it.section.code })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filtering by a section number finds it`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("4.3")
            val state = awaitItem()
            assertEquals(listOf("4"), state.rows.map { it.section.code })
            assertEquals(listOf("4.3"), state.rows.single().children.map { it.code })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a filter that matches nothing leaves an empty panel, not a broken one`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("zzz")
            assertTrue(awaitItem().rows.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the filter brings the whole tree back`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("oposi")
            awaitItem()
            viewModel.onQueryChanged("")
            assertEquals(9, awaitItem().rows.size)
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

    @Test
    fun `what was expanded by hand survives a filter and its clearing`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onToggleExpanded("4")
            awaitItem()
            viewModel.onQueryChanged("urbanismo")
            awaitItem()
            viewModel.onQueryChanged("")
            assertTrue("4" in awaitItem().expanded)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
