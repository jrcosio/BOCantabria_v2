package com.jrblanco.boccantabria.data.background

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The periodic job: one and only one, with a network, cancellable, and never touched from the
 * constructor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class WorkManagerBackgroundSyncSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun `scheduling twice leaves exactly one periodic job with a network constraint`() {
        val scheduler = WorkManagerBackgroundSyncScheduler(context)

        scheduler.ensureScheduled()
        scheduler.ensureScheduled()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerBackgroundSyncScheduler.WORK_NAME).get()
        assertEquals(1, infos.size)
        val info = infos.single()
        assertTrue(info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(WorkManagerBackgroundSyncScheduler.INTERVAL_HOURS * 60 * 60 * 1000, info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `cancelling removes it`() {
        val scheduler = WorkManagerBackgroundSyncScheduler(context)
        scheduler.ensureScheduled()

        scheduler.cancel()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerBackgroundSyncScheduler.WORK_NAME).get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `cancelling what was never scheduled is fine`() {
        WorkManagerBackgroundSyncScheduler(context).cancel()
    }
}
