package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers

/**
 * Real threads. Only the cancellation tests need them, and they need them genuinely: what they check
 * is a race between a blocked call and a cancellation, and in virtual time it does not exist.
 */
object RealDispatchers : DispatcherProvider {
    override val main = Dispatchers.Unconfined
    override val io = Dispatchers.IO
    override val default = Dispatchers.Default
}
