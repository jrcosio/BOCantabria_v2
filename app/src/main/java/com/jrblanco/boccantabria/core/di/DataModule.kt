package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.telemetry.NoOpAnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import org.koin.dsl.module

/**
 * Data sources, repositories and telemetry implementations.
 *
 * Telemetry is bound to the no-op implementations until the Firebase ones land, so the rest of
 * the app can depend on the contracts without pulling Firebase into every test.
 */
val dataModule = module {
    single<AnalyticsTracker> { NoOpAnalyticsTracker() }
    single<CrashReporter> { NoOpCrashReporter() }
}
