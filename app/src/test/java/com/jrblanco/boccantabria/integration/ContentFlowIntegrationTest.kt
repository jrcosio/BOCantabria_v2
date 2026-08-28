package com.jrblanco.boccantabria.integration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.jrblanco.boccantabria.core.di.appModules
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.data.source.remote.ContentItemDto
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.ContentItem
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.ui.home.HomeUiState
import com.jrblanco.boccantabria.ui.home.HomeViewModel
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
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Walks the real graph end to end — view model, use case, repository, sources — replacing only
 * the outermost boundary. The unit tests prove each piece is right; this proves they are
 * actually plugged into each other (FR-012, FR-019).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class ContentFlowIntegrationTest {

    private val dispatcher = StandardTestDispatcher()
    private val remote = SwitchableRemoteDataSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `content travels from the remote source all the way to the screen state`() = runTest(dispatcher) {
        val koin = startGraph()

        koin.get<HomeViewModel>().uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(HomeUiState.Content(listOf(ITEM)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        koin.close()
    }

    @Test
    fun `a failure at the boundary surfaces as an error state`() = runTest(dispatcher) {
        remote.failWith = IOException("offline")
        val koin = startGraph()

        koin.get<HomeViewModel>().uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(HomeUiState.Error(DomainError.Network), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        koin.close()
    }

    private fun startGraph(): Koin = koinApplication {
        androidContext(ApplicationProvider.getApplicationContext())
        modules(appModules)
        modules(
            module {
                single<ContentRemoteDataSource> { remote }
                single<DispatcherProvider> { TestDispatcherProvider(dispatcher) }
            },
        )
    }.koin

    private class SwitchableRemoteDataSource : ContentRemoteDataSource {
        var failWith: Throwable? = null

        override suspend fun fetchContentItems(): List<ContentItemDto> {
            failWith?.let { throw it }
            return listOf(ContentItemDto(id = ITEM.id, label = ITEM.title))
        }
    }

    private companion object {
        val ITEM = ContentItem(id = "1", title = "Boletín de prueba")
    }
}
