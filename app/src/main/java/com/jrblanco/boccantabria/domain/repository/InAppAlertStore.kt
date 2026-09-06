package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.InAppAlert
import kotlinx.coroutines.flow.Flow

/**
 * The message waiting to be shown inside the application.
 *
 * A pending **state**, not an event: if a cycle ends while the detail screen covers the shell, the
 * shell is not composed and an event would be lost. A state is shown whenever the shell comes back
 * (research.md D-416). Whoever shows it calls [consume].
 */
interface InAppAlertStore {

    fun observePending(): Flow<InAppAlert?>

    /** Publishes, accumulating with whatever was already pending. */
    fun publish(alert: InAppAlert)

    fun consume()
}
