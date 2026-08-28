package com.jrblanco.boccantabria.ui.home

import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.ContentItem
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.repository.ContentRepository
import com.jrblanco.boccantabria.domain.usecase.GetContentItemsUseCase
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading and reaches content`() = runTest(dispatcher) {
        val viewModel = viewModel(AppResult.Success(ITEMS))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(HomeUiState.Content(ITEMS), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty result is Empty, not Error`() = runTest(dispatcher) {
        val viewModel = viewModel(AppResult.Success(emptyList()))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(HomeUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure reaches Error carrying the domain error`() = runTest(dispatcher) {
        val viewModel = viewModel(AppResult.Failure(DomainError.Network))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(HomeUiState.Error(DomainError.Network), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying from Error reaches Content`() = runTest(dispatcher) {
        val useCase = SwitchableUseCase(AppResult.Failure(DomainError.Network))
        val viewModel = HomeViewModel(GetContentItemsUseCase(useCase), analytics)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(HomeUiState.Error(DomainError.Network), awaitItem())

            useCase.result = AppResult.Success(ITEMS)
            viewModel.onRetry()

            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(HomeUiState.Content(ITEMS), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying while a load is in flight does not start a second one`() = runTest(dispatcher) {
        val useCase = CountingUseCase(AppResult.Success(ITEMS))
        val viewModel = HomeViewModel(GetContentItemsUseCase(useCase), analytics)

        // The initial load is still in flight because the dispatcher has not run yet.
        viewModel.onRetry()
        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals(1, useCase.calls)
    }

    @Test
    fun `records the screen view exactly once per instance`() = runTest(dispatcher) {
        viewModel(AppResult.Success(ITEMS))
        advanceUntilIdle()

        assertEquals(listOf(HomeViewModel.SCREEN_NAME), analytics.screenViews)
    }

    private fun viewModel(result: AppResult<List<ContentItem>>) = HomeViewModel(
        getContentItems = GetContentItemsUseCase(SwitchableUseCase(result)),
        analytics = analytics,
    )

    private class SwitchableUseCase(var result: AppResult<List<ContentItem>>) : ContentRepository {
        override suspend fun getContentItems(): AppResult<List<ContentItem>> = result
    }

    private class CountingUseCase(private val result: AppResult<List<ContentItem>>) : ContentRepository {
        var calls: Int = 0
            private set

        override suspend fun getContentItems(): AppResult<List<ContentItem>> {
            calls++
            return result
        }
    }

    private companion object {
        val ITEMS = listOf(
            ContentItem(id = "1", title = "Boletín del lunes"),
            ContentItem(id = "2", title = "Boletín del martes"),
        )
    }
}
