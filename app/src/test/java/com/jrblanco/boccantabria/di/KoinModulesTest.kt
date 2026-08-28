package com.jrblanco.boccantabria.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.di.appModules
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.telemetry.NoOpAnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.data.source.local.ContentLocalDataSource
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.domain.repository.ContentRepository
import com.jrblanco.boccantabria.domain.usecase.GetContentItemsUseCase
import com.jrblanco.boccantabria.ui.home.HomeViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fails if any declared dependency cannot be resolved, so a wiring mistake breaks the build
 * instead of crashing the app on someone's phone (FR-011, FR-018).
 *
 * Runs under Robolectric because the graph is built with an `androidContext`, which is exactly
 * where wiring mistakes tend to hide. It uses the plain [Application] rather than
 * `BOCantabriaApp`: the real one starts the global Koin context, which would survive between
 * tests in the same JVM and make the second one fail. These tests build their own isolated
 * Koin instance instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class KoinModulesTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every declaration has its constructor dependencies satisfied`() {
        // verify() inspects one module at a time, so anything a module resolves from a sibling
        // has to be declared here. Keeping that list explicit doubles as documentation of the
        // edges between modules; the graph test below is what checks they really connect.
        appModules.forEach { module ->
            module.verify(extraTypes = CROSS_MODULE_TYPES)
        }
    }

    @Test
    fun `the whole graph resolves with a real android context`() {
        val koin = koinApplication {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(appModules)
        }.koin
        // Telemetry is the one thing that cannot be built here: the Firebase clients need a
        // real FirebaseApp, which does not exist outside a device. The bindings themselves are
        // still checked below; the implementations behind them have their own tests.
        koin.loadModules(
            listOf(
                module {
                    single<AnalyticsTracker> { NoOpAnalyticsTracker() }
                    single<CrashReporter> { NoOpCrashReporter() }
                },
            ),
            allowOverride = true,
        )

        // Resolving the view model walks the entire chain: view model, use case, repository and
        // both data sources. The rest are declarations nothing injects yet, checked one by one
        // so an unreachable binding still fails here rather than the first time it is needed.
        koin.get<HomeViewModel>()
        koin.get<ContentRepository>()
        koin.get<GetContentItemsUseCase>()
        koin.get<ContentRemoteDataSource>()
        koin.get<ContentLocalDataSource>()
        koin.get<DispatcherProvider>()
        koin.get<AnalyticsTracker>()
        koin.get<CrashReporter>()

        koin.close()
    }

    private companion object {
        val CROSS_MODULE_TYPES = listOf(
            Context::class,
            ContentRepository::class,
            ContentRemoteDataSource::class,
            ContentLocalDataSource::class,
            DispatcherProvider::class,
            GetContentItemsUseCase::class,
            AnalyticsTracker::class,
        )
    }
}

/** Robolectric has no descriptor for API 37 yet, so the tests run against the newest it knows. */
internal const val ROBOLECTRIC_SDK = 36
