package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.PrepareStartupUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { ObservePublicationsUseCase(repository = get()) }
    factory { ObserveBulletinHeaderUseCase(repository = get()) }
    factory { RefreshPublicationsUseCase(repository = get()) }
    factory { GetBocSectionsUseCase(repository = get()) }
    factory { PrepareStartupUseCase(connectivity = get(), appConfig = get(), appVersion = get()) }
}
