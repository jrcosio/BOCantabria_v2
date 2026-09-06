package com.jrblanco.boccantabria.data.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jrblanco.boccantabria.domain.repository.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit

/**
 * The periodic check, scheduled with WorkManager.
 *
 * Every four hours with half an hour of flex, only with a network, and never at an exact time: the
 * bulletin publishes once a working day, and "comprobación periódica" is what the interface promises
 * (FR-063, FR-065; 012 research.md D-421). `UPDATE` rather than `KEEP`, so a future change of interval
 * or constraint reaches phones that already have the job.
 *
 * `WorkManager.getInstance` is called **inside** the methods and never in the constructor: this
 * class is resolved by Koin before WorkManager is guaranteed to be initialised — and under Robolectric
 * it may never be.
 */
class WorkManagerBackgroundSyncScheduler(
    private val context: Context,
) : BackgroundSyncScheduler {

    override fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<AlertSyncWorker>(
            INTERVAL_HOURS, TimeUnit.HOURS,
            FLEX_MINUTES, TimeUnit.MINUTES,
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME: String = "boc_alert_sync"
        const val INTERVAL_HOURS: Long = 4L
        const val FLEX_MINUTES: Long = 30L
    }
}
