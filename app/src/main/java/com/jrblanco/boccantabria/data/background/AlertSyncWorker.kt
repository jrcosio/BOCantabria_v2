package com.jrblanco.boccantabria.data.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.usecase.RunSyncCycleUseCase

/**
 * The periodic check: the same cycle the home screen runs, without a screen.
 *
 * Built by Koin's worker factory, so the use case arrives by constructor and `KoinModulesTest` sees
 * it (012 research.md D-420). Always returns success: a retry with backoff would overlap the period,
 * and a source that is down does not come back up in thirty seconds — the next period is the retry
 * (D-423).
 */
class AlertSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val runSyncCycle: RunSyncCycleUseCase,
    private val crashReporter: CrashReporter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        when (val result = runSyncCycle(force = false)) {
            is AppResult.Success -> crashReporter.log("alerts: worker run, delivery=${result.data.delivery}")
            is AppResult.Failure -> crashReporter.log("alerts: worker run failed: ${result.error}")
        }
        return Result.success()
    }
}
