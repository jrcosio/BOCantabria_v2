package com.jrblanco.boccantabria.core.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Coroutine dispatchers, injected rather than referenced statically.
 *
 * Referencing [Dispatchers.IO] directly inside a repository makes it impossible to control
 * virtual time from a test and produces flaky results. Injecting them lets every test pick its
 * own scheduler, which is what the constitution means by deterministic tests.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
