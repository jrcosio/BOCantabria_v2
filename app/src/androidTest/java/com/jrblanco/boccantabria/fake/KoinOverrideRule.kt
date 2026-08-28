package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.core.di.appModules
import org.junit.rules.ExternalResource
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module

/**
 * Swaps bindings in the graph the Application already started.
 *
 * Declared with `@get:Rule(order = 0)` so it runs before the Compose activity rule: the activity
 * must be launched with the overrides already in place, otherwise the view model resolves the real
 * dependencies first and the test asserts on the wrong graph.
 *
 * Cleanup reloads [appModules] rather than merely unloading the overrides. `unloadKoinModules`
 * **removes** the definitions instead of restoring the ones they shadowed, so a rule that only
 * unloaded would leave holes where those types used to be, and every later test class in the same
 * process would fail resolving them. Reloading the real modules puts the graph back and, as a
 * bonus, discards instances cached during the test.
 */
class KoinOverrideRule(private val modules: List<Module>) : ExternalResource() {

    override fun before() {
        loadKoinModules(modules)
    }

    override fun after() {
        unloadKoinModules(modules)
        loadKoinModules(appModules)
    }
}
