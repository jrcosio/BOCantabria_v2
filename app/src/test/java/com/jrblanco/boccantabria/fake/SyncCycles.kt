package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.repository.AlertRepository
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import com.jrblanco.boccantabria.domain.usecase.MatchAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseUnusedDocumentsUseCase
import com.jrblanco.boccantabria.domain.usecase.RunSyncCycleUseCase

/**
 * A synchronisation cycle over [publications] with everything else faked, for the tests that only
 * need the home screen to be able to synchronise.
 */
@Suppress("LongParameterList")
fun testSyncCycle(
    publications: PublicationRepository,
    alerts: AlertRepository = FakeAlertRepository(),
    notifier: RecordingAlertNotifier = RecordingAlertNotifier(),
    inAppAlerts: FakeInAppAlertStore = FakeInAppAlertStore(),
    visibility: FakeAppVisibilityProvider = FakeAppVisibilityProvider(visible = true),
    documents: FakeDocumentRepository = FakeDocumentRepository(),
    crashReporter: CrashReporter = NoOpCrashReporter(),
    now: Long = 1_000_000L,
): RunSyncCycleUseCase = RunSyncCycleUseCase(
    refreshPublications = RefreshPublicationsUseCase(publications),
    publications = publications,
    alerts = alerts,
    matchRule = MatchAlertRuleUseCase(BocSectionRepositoryImpl()),
    notifier = notifier,
    inAppAlerts = inAppAlerts,
    appVisibility = visibility,
    releaseUnusedDocuments = ReleaseUnusedDocumentsUseCase(documents),
    time = object : TimeProvider { override fun nowMillis() = now },
    crashReporter = crashReporter,
)
