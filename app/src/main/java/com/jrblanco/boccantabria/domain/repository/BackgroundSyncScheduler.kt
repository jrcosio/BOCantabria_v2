package com.jrblanco.boccantabria.domain.repository

/**
 * The periodic check the application runs on its own while there is a rule to evaluate.
 *
 * Idempotent on purpose: [ensureScheduled] called twice leaves one job, and [cancel] on nothing is
 * fine. Who calls it and when is decided by the use cases that write rules (research.md D-422).
 */
interface BackgroundSyncScheduler {

    fun ensureScheduled()

    fun cancel()
}
