package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.PrepareStartupUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseUnusedDocumentsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { ObservePublicationsUseCase(repository = get()) }
    factory { ObserveBulletinHeaderUseCase(repository = get()) }
    factory { RefreshPublicationsUseCase(repository = get()) }
    factory { GetBocSectionsUseCase(repository = get()) }
    factory { ObservePublicationUseCase(repository = get()) }
    factory { ObserveOfficialDocumentUseCase(repository = get()) }
    factory { OpenOfficialDocumentUseCase(repository = get()) }
    factory { ShareOfficialDocumentUseCase(documents = get(), connectivity = get()) }
    factory { ReleaseUnusedDocumentsUseCase(documents = get()) }
    factory { PrepareStartupUseCase(connectivity = get(), appConfig = get(), appVersion = get()) }
    factory { ObserveSavedPublicationsUseCase(repository = get()) }
    factory { ObserveSavedKeysUseCase(repository = get()) }
    factory { SetPublicationSavedUseCase(repository = get()) }
}
