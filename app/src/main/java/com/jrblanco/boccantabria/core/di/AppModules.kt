package com.jrblanco.boccantabria.core.di

import org.koin.core.module.Module

/**
 * The single entry point of the dependency graph.
 *
 * [com.jrblanco.boccantabria.BOCantabriaApp] knows only this list, never the individual
 * modules, so adding a module never means touching the Application class.
 */
val appModules: List<Module> = listOf(
    coreModule,
    dataModule,
    domainModule,
    uiModule,
)
