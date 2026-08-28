package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.telemetry.NoOpAnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.data.repository.ContentRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.ContentLocalDataSource
import com.jrblanco.boccantabria.data.source.local.InMemoryContentLocalDataSource
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.StubContentRemoteDataSource
import com.jrblanco.boccantabria.domain.repository.ContentRepository
import org.koin.dsl.module

/**
 * Data sources, repositories and telemetry implementations.
 *
 * Telemetry is still bound to the no-op implementations; the Firebase ones land with user
 * story 3. Keeping the binding in one place means that swap is a one-line change here.
 */
val dataModule = module {
    single<ContentRemoteDataSource> { StubContentRemoteDataSource() }
    single<ContentLocalDataSource> { InMemoryContentLocalDataSource() }
    single<ContentRepository> {
        ContentRepositoryImpl(
            remoteDataSource = get(),
            localDataSource = get(),
            dispatchers = get(),
        )
    }
    single<AnalyticsTracker> { NoOpAnalyticsTracker() }
    single<CrashReporter> { NoOpCrashReporter() }
}
