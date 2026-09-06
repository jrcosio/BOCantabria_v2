package com.jrblanco.boccantabria.data.background

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.koin.androidx.workmanager.factory.KoinWorkerFactory

/**
 * The worker built by **Koin's own factory**, in the application's real process, running the real
 * cycle over the test graph.
 *
 * This is the one thing the unit tests could not see and the emulator could not show either:
 * `cmd jobscheduler run -f` reaches WorkManager, but WorkManager delays a periodic job that arrives
 * before its hour («Delaying execution … because it is being executed before schedule»), so the
 * worker never ran. Building it here with `KoinWorkerFactory` proves the factory resolves the two
 * constructor dependencies from the graph — the thing that, if broken, would fail silently every four
 * hours on a phone (012 research.md D-420).
 */
class AlertSyncWorkerKoinTest {

    @get:Rule
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @Test
    fun koin_builds_the_worker_and_the_cycle_runs_to_success() {
        val worker = TestListenableWorkerBuilder<AlertSyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(KoinWorkerFactory())
            .build()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
