package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.data.repository.AppConfigRepositoryImpl
import com.jrblanco.boccantabria.data.repository.ConnectivityRepositoryImpl
import com.jrblanco.boccantabria.data.repository.ContentRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.AndroidConnectivityDataSource
import com.jrblanco.boccantabria.data.source.local.ConnectivityDataSource
import com.jrblanco.boccantabria.data.source.local.ContentLocalDataSource
import com.jrblanco.boccantabria.data.source.local.InMemoryContentLocalDataSource
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigDataSource
import com.jrblanco.boccantabria.data.source.remote.StubContentRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.firebaseRemoteConfigDataSource
import com.jrblanco.boccantabria.data.telemetry.firebaseAnalyticsTracker
import com.jrblanco.boccantabria.data.telemetry.firebaseCrashReporter
import com.jrblanco.boccantabria.domain.repository.AppConfigRepository
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.repository.ContentRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Data sources, repositories and telemetry implementations.
 *
 * Everything above this module depends on the [AnalyticsTracker] and [CrashReporter]
 * contracts, never on the SDK, which is what lets a test swap them for a double. The Firebase
 * types are built by factories inside `data.telemetry` so that not even this module imports
 * the SDK: there is an architecture test asserting exactly that.
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
    single<RemoteConfigDataSource> { firebaseRemoteConfigDataSource() }
    single<AppConfigRepository> {
        AppConfigRepositoryImpl(remoteConfigDataSource = get(), dispatchers = get())
    }
    single<ConnectivityDataSource> { AndroidConnectivityDataSource(context = androidContext()) }
    single<ConnectivityRepository> { ConnectivityRepositoryImpl(connectivityDataSource = get()) }
    single<AnalyticsTracker> { firebaseAnalyticsTracker(androidContext()) }
    single<CrashReporter> { firebaseCrashReporter() }
}
