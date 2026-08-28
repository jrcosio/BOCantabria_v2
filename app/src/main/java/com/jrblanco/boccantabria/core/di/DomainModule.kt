package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.domain.usecase.GetContentItemsUseCase
import com.jrblanco.boccantabria.domain.usecase.PrepareStartupUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetContentItemsUseCase(repository = get()) }
    factory {
        PrepareStartupUseCase(
            connectivity = get(),
            appConfig = get(),
            appVersion = get(),
        )
    }
}
