package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.domain.model.InAppAlert
import com.jrblanco.boccantabria.domain.repository.InAppAlertStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The pending in-app message, in memory and process-wide.
 *
 * A `StateFlow` rather than a `SharedFlow`: if a cycle ends while the detail screen covers the shell,
 * nobody is collecting, and an event would evaporate. A state waits until the shell is back
 * (012 research.md D-416). Two cycles before anybody looks become one message that counts both.
 */
class InMemoryInAppAlertStore : InAppAlertStore {

    private val pending = MutableStateFlow<InAppAlert?>(null)

    override fun observePending(): Flow<InAppAlert?> = pending.asStateFlow()

    override fun publish(alert: InAppAlert) {
        pending.update { current -> current?.plus(alert) ?: alert }
    }

    override fun consume() {
        pending.value = null
    }
}
