package com.jrblanco.boccantabria.fake

import org.junit.rules.ExternalResource
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module

/**
 * Swaps bindings in the graph the Application already started.
 *
 * Declared with `@get:Rule(order = 0)` so it runs before the Compose activity rule: the
 * activity must be launched with the overrides already in place, otherwise the view model
 * resolves the real dependencies first and the test asserts on the wrong graph.
 */
class KoinOverrideRule(private val modules: List<Module>) : ExternalResource() {

    override fun before() {
        loadKoinModules(modules)
    }

    override fun after() {
        unloadKoinModules(modules)
    }
}
