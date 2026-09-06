package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.core.util.AppVersionProvider
import com.jrblanco.boccantabria.core.util.AppVisibilityProvider
import com.jrblanco.boccantabria.core.util.BuildConfigAppVersionProvider
import com.jrblanco.boccantabria.core.util.DefaultDispatcherProvider
import com.jrblanco.boccantabria.core.util.DefaultRandomProvider
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.ProcessLifecycleAppVisibilityProvider
import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.SystemTimeProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import org.koin.dsl.module

/**
 * Cross-cutting seams. All four exist so that time, threading, randomness and the installed
 * version can be replaced in a test, which is what makes the suite deterministic.
 */
val coreModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<AppVersionProvider> { BuildConfigAppVersionProvider() }
    single<TimeProvider> { SystemTimeProvider() }
    single<RandomProvider> { DefaultRandomProvider() }
    // Whether the process is on screen: decides notification or Snackbar (012 research.md D-415).
    single<AppVisibilityProvider> { ProcessLifecycleAppVisibilityProvider() }
}
