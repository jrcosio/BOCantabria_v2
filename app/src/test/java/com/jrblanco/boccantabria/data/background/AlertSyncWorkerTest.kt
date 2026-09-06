package com.jrblanco.boccantabria.data.background

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.testSyncCycle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The worker runs the cycle and always succeeds: the next period is the retry (D-423). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AlertSyncWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val crashReporter = RecordingCrashReporter()

    @Test
    fun `a good cycle is a success and is logged`() = runTest {
        val publications = FakePublicationRepository()
        val worker = worker(publications)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, publications.refreshCount)
        assertTrue(crashReporter.messages.any { it == "alerts: worker run, delivery=NONE" })
        // Counts and enumerations only: never a title, a rule name or a key (FR-070).
        assertTrue(crashReporter.messages.none { it.contains("boc:") })
    }

    @Test
    fun `a failed cycle is still a success, so it does not retry inside the period`() = runTest {
        val publications = FakePublicationRepository().apply { refreshResult = AppResult.Failure(DomainError.Network) }

        val result = worker(publications).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(crashReporter.messages.any { it == "alerts: worker run failed: Network" })
    }

    private fun worker(publications: FakePublicationRepository): AlertSyncWorker {
        val cycle = testSyncCycle(publications, crashReporter = crashReporter)
        return TestListenableWorkerBuilder<AlertSyncWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters) =
                        AlertSyncWorker(appContext, params, cycle, crashReporter)
                },
            )
            .build()
    }
}
