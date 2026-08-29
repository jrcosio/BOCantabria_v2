package com.jrblanco.boccantabria.fake

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.telemetry.NoOpAnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.data.repository.PublicationRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.FeedSyncStateDao
import com.jrblanco.boccantabria.data.source.local.PublicationDao
import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Gives one instrumented test its own bulletin chain: the source it controls, a fresh in-memory
 * database, fresh data-access objects and a fresh repository.
 *
 * **All of them have to be replaced, not just the source.** Instrumented tests share one process
 * and the application graph is made of `single` definitions, so:
 *
 * - the database survives from one test to the next, and a test whose source fails would be
 *   served whatever a previous test had stored instead of reaching its empty or error state;
 * - the repository is cached the first time it is resolved, holding references to whichever
 *   sources were in place back then. Overriding only the source leaves that stale instance in
 *   the graph, and the test silently exercises the previous test's chain.
 *
 * Rebuilding the lot is what makes each test independent of execution order.
 */
fun testGraphOverrides(remote: PublicationRemoteDataSource): List<Module> = listOf(
    module {
        single<BocDatabase> {
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                BocDatabase::class.java,
            ).build()
        }
        single<PublicationDao> { get<BocDatabase>().publicationDao() }
        single<FeedSyncStateDao> { get<BocDatabase>().feedSyncStateDao() }
        single<PublicationRemoteDataSource> { remote }
        single<AnalyticsTracker> { NoOpAnalyticsTracker() }
        single<CrashReporter> { NoOpCrashReporter() }
        single<PublicationRepository> {
            PublicationRepositoryImpl(
                remoteDataSource = get(),
                publicationDao = get(),
                feedSyncStateDao = get(),
                normalizer = PublicationNormalizer(),
                sectionRepository = BocSectionRepositoryImpl(),
                feeds = BocFeedCatalog.definitions,
                time = get(),
                dispatchers = get(),
                analytics = get(),
                crashReporter = get(),
            )
        }
    },
) + startupGraphOverrides(
    // Every screen sits behind the splash, so a content test has to get through it. Faking the
    // startup chain keeps these tests off the network and independent of what Remote Config has
    // published, which is what "deterministic" means here.
    connectivity = FakeConnectivityDataSource(online = true),
    remoteConfig = FakeRemoteConfigDataSource(),
)
