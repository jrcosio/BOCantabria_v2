package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.core.util.AppVersionProvider
import com.jrblanco.boccantabria.core.util.BuildConfigAppVersionProvider
import com.jrblanco.boccantabria.core.util.DefaultDispatcherProvider
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import org.koin.dsl.module

/** Cross-cutting dependencies with no layer of their own. */
val coreModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<AppVersionProvider> { BuildConfigAppVersionProvider() }
}
