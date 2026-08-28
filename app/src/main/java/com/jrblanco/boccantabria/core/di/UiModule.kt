package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.ui.home.HomeViewModel
import com.jrblanco.boccantabria.ui.splash.SplashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::SplashViewModel)
}
