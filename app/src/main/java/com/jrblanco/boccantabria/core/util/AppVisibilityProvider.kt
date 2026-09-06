package com.jrblanco.boccantabria.core.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Whether the application is on screen, injected.
 *
 * It decides how a synchronisation cycle delivers what it found: a Snackbar if the person is
 * looking, a system notification if not. Injected like [TimeProvider] so the cycle's tests can hold
 * the answer still (012 research.md D-415).
 */
interface AppVisibilityProvider {

    fun isAppVisible(): Boolean
}

/**
 * Reads the process lifecycle, which is the only thing that knows about every activity — and here
 * there is one, but the detail, the viewer and the conversation all live outside the shell, so a
 * flag published by the shell would say "not visible" while somebody was reading a document.
 *
 * **Only reads**: no observer is registered, so this is safe from the worker's thread.
 */
class ProcessLifecycleAppVisibilityProvider : AppVisibilityProvider {

    override fun isAppVisible(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
