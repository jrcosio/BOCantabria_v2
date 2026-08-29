package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.data.repository.AppConfigRepositoryImpl
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.data.repository.ConnectivityRepositoryImpl
import com.jrblanco.boccantabria.data.repository.PublicationRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.AndroidConnectivityDataSource
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.ConnectivityDataSource
import com.jrblanco.boccantabria.data.source.local.FeedSyncStateDao
import com.jrblanco.boccantabria.data.source.local.PublicationDao
import com.jrblanco.boccantabria.data.source.local.bocDatabase
import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.data.source.remote.BocRssParser
import com.jrblanco.boccantabria.data.source.remote.OkHttpPublicationRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigDataSource
import com.jrblanco.boccantabria.data.source.remote.bocHttpClient
import com.jrblanco.boccantabria.data.source.remote.firebaseRemoteConfigDataSource
import com.jrblanco.boccantabria.data.telemetry.firebaseAnalyticsTracker
import com.jrblanco.boccantabria.data.telemetry.firebaseCrashReporter
import com.jrblanco.boccantabria.domain.repository.AppConfigRepository
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Sources and repositories.
 *
 * Room and OkHttp are built through factory functions declared in `data` —`bocDatabase`,
 * `bocHttpClient`— for the same reason Firebase is: an architecture rule forbids this package
 * from importing a third-party SDK, and that rule is what keeps the graph declarable without
 * dragging every dependency's types into it.
 */
val dataModule = module {

    // --- Almacenamiento local ---
    single<BocDatabase> { bocDatabase(androidContext()) }
    single<PublicationDao> { get<BocDatabase>().publicationDao() }
    single<FeedSyncStateDao> { get<BocDatabase>().feedSyncStateDao() }

    // --- Red ---
    single<OkHttpClient> { bocHttpClient() }
    single { BocRssParser() }
    single { PublicationNormalizer() }
    single<PublicationRemoteDataSource> {
        OkHttpPublicationRemoteDataSource(
            client = get(),
            parser = get(),
            dispatchers = get(),
            random = get(),
        )
    }

    // --- El boletín ---
    single<BocSectionRepository> { BocSectionRepositoryImpl() }
    single<PublicationRepository> {
        PublicationRepositoryImpl(
            remoteDataSource = get(),
            publicationDao = get(),
            feedSyncStateDao = get(),
            normalizer = get(),
            sectionRepository = get(),
            feeds = BocFeedCatalog.definitions,
            time = get(),
            dispatchers = get(),
            analytics = get(),
            crashReporter = get(),
        )
    }

    // --- Arranque (feature 002) ---
    single<RemoteConfigDataSource> { firebaseRemoteConfigDataSource() }
    single<AppConfigRepository> {
        AppConfigRepositoryImpl(remoteConfigDataSource = get(), dispatchers = get())
    }
    single<ConnectivityDataSource> { AndroidConnectivityDataSource(context = androidContext()) }
    single<ConnectivityRepository> { ConnectivityRepositoryImpl(connectivityDataSource = get()) }

    // --- Telemetría ---
    single<AnalyticsTracker> { firebaseAnalyticsTracker(androidContext()) }
    single<CrashReporter> { firebaseCrashReporter() }
}
