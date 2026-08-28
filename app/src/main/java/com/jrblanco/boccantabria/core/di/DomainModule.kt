package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.domain.usecase.GetContentItemsUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetContentItemsUseCase(repository = get()) }
}
