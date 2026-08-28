package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.repository.ContentRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.ContentLocalDataSource
import com.jrblanco.boccantabria.data.source.local.InMemoryContentLocalDataSource
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.domain.repository.ContentRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Gives one instrumented test its own content chain: the remote source it controls, a fresh
 * local cache and a fresh repository.
 *
 * All three must be replaced, not just the remote one. Instrumented tests share a single
 * process and the app graph is made of `single` definitions, so:
 *
 * - the local cache survives from one test to the next, and a test whose remote fails would be
 *   served content a previous test had cached instead of reaching the error state;
 * - the repository instance is cached the first time it is resolved, holding references to
 *   whichever sources were in place back then. Overriding only the sources leaves that stale
 *   instance in the graph, so the test silently exercises the previous test's chain.
 *
 * Rebuilding the three of them is what makes each test independent of execution order (FR-021).
 */
fun testGraphOverrides(remote: ContentRemoteDataSource): List<Module> = listOf(
    module {
        single<ContentRemoteDataSource> { remote }
        single<ContentLocalDataSource> { InMemoryContentLocalDataSource() }
        single<ContentRepository> {
            ContentRepositoryImpl(
                remoteDataSource = get(),
                localDataSource = get(),
                dispatchers = get(),
            )
        }
    },
)
