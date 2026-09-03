package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.ui.detail.PublicationDetailViewModel
import com.jrblanco.boccantabria.ui.home.HomeViewModel
import com.jrblanco.boccantabria.ui.info.InfoViewModel
import com.jrblanco.boccantabria.ui.pdf.PdfDocumentLoader
import com.jrblanco.boccantabria.ui.pdf.PdfViewerViewModel
import com.jrblanco.boccantabria.ui.pdf.pdfDocumentLoader
import com.jrblanco.boccantabria.ui.saved.SavedViewModel
import com.jrblanco.boccantabria.ui.search.SearchViewModel
import com.jrblanco.boccantabria.ui.sections.SectionsViewModel
import com.jrblanco.boccantabria.ui.splash.SplashViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::SectionsViewModel)
    viewModelOf(::SplashViewModel)
    viewModelOf(::PublicationDetailViewModel)
    viewModelOf(::PdfViewerViewModel)
    viewModelOf(::SavedViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::InfoViewModel)

    // Built through a factory function declared in `ui/pdf` for the same reason Room and OkHttp
    // are: the viewer library is a third-party SDK, and only that package is allowed to name it.
    single<PdfDocumentLoader> { pdfDocumentLoader(androidContext(), dispatchers = get()) }
}
