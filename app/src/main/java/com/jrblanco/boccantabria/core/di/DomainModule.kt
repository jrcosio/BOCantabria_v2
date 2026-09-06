package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.domain.usecase.FilterPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetSearchIssuersUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.AskAboutDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.DiscardAiConversationUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiAvailabilityUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiConversationUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseAiDocumentSessionUseCase
import com.jrblanco.boccantabria.domain.usecase.RetryLastQuestionUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.PrepareStartupUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.SearchPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseUnusedDocumentsUseCase
import com.jrblanco.boccantabria.domain.usecase.AcceptAiNoticeUseCase
import com.jrblanco.boccantabria.domain.usecase.GenerateAiSummaryUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiNoticeAcceptedUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiSummaryUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ConsumeInAppAlertUseCase
import com.jrblanco.boccantabria.domain.usecase.CountAlertRulesUseCase
import com.jrblanco.boccantabria.domain.usecase.DeleteAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.GetAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.GetLastSyncUseCase
import com.jrblanco.boccantabria.domain.usecase.GetNotificationStatusUseCase
import com.jrblanco.boccantabria.domain.usecase.MarkAlertReadUseCase
import com.jrblanco.boccantabria.domain.usecase.MarkAllAlertsReadUseCase
import com.jrblanco.boccantabria.domain.usecase.MatchAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAlertNewsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAlertRulesUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePendingInAppAlertUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveUnreadAlertCountUseCase
import com.jrblanco.boccantabria.domain.usecase.PreviewAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.ReconcileBackgroundSyncUseCase
import com.jrblanco.boccantabria.domain.usecase.RunSyncCycleUseCase
import com.jrblanco.boccantabria.domain.usecase.SaveAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.SetAlertRuleEnabledUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { ObservePublicationsUseCase(repository = get()) }
    factory { ObserveBulletinHeaderUseCase(repository = get()) }
    factory { RefreshPublicationsUseCase(repository = get()) }
    factory { GetBocSectionsUseCase(repository = get()) }
    factory { ObservePublicationUseCase(repository = get()) }
    factory { ObserveOfficialDocumentUseCase(repository = get()) }
    factory { OpenOfficialDocumentUseCase(repository = get()) }
    factory { ShareOfficialDocumentUseCase(documents = get(), connectivity = get()) }
    factory { ReleaseUnusedDocumentsUseCase(documents = get()) }
    factory { PrepareStartupUseCase(connectivity = get(), appConfig = get(), appVersion = get()) }
    factory { ObserveSavedPublicationsUseCase(repository = get()) }
    factory { ObserveSavedKeysUseCase(repository = get()) }
    factory { SetPublicationSavedUseCase(repository = get()) }

    // --- Buscar (feature 006) ---
    factory { SearchPublicationsUseCase(repository = get()) }
    factory { GetSearchIssuersUseCase(repository = get()) }
    // Sin dependencias: es una regla de coincidencia, no una consulta.
    factory { FilterPublicationsUseCase() }

    // --- Resumen IA (feature 007) ---
    factory { ObserveAiSummaryUseCase(repository = get()) }
    factory { GenerateAiSummaryUseCase(repository = get()) }
    factory { ObserveAiNoticeAcceptedUseCase(repository = get()) }
    factory { AcceptAiNoticeUseCase(repository = get()) }
    factory { ReleaseAiDocumentSessionUseCase(repository = get()) }

    // --- Preguntar al BOC (feature 011) ---
    factory { ObserveAiConversationUseCase(repository = get()) }
    factory { ObserveAiAvailabilityUseCase(repository = get()) }
    factory { AskAboutDocumentUseCase(repository = get()) }
    factory { RetryLastQuestionUseCase(repository = get()) }
    factory { DiscardAiConversationUseCase(repository = get()) }

    // --- Avisos (feature 012) ---
    // El comparador es un single: cachea los nombres de sección y no tiene estado mutable.
    single { MatchAlertRuleUseCase(sections = get()) }
    factory {
        RunSyncCycleUseCase(
            refreshPublications = get(),
            publications = get(),
            alerts = get(),
            matchRule = get(),
            notifier = get(),
            inAppAlerts = get(),
            appVisibility = get(),
            releaseUnusedDocuments = get(),
            time = get(),
            crashReporter = get(),
        )
    }
    factory { ObserveAlertRulesUseCase(repository = get(), time = get()) }
    factory { GetAlertRuleUseCase(repository = get()) }
    factory { CountAlertRulesUseCase(repository = get()) }
    factory { ReconcileBackgroundSyncUseCase(repository = get(), scheduler = get()) }
    factory { SaveAlertRuleUseCase(repository = get(), reconcileBackgroundSync = get()) }
    factory { SetAlertRuleEnabledUseCase(repository = get(), reconcileBackgroundSync = get()) }
    factory { DeleteAlertRuleUseCase(repository = get(), reconcileBackgroundSync = get()) }
    factory { ObserveAlertNewsUseCase(repository = get()) }
    factory { ObserveUnreadAlertCountUseCase(repository = get()) }
    factory { MarkAlertReadUseCase(repository = get()) }
    factory { MarkAllAlertsReadUseCase(repository = get()) }
    factory { ObservePendingInAppAlertUseCase(store = get()) }
    factory { ConsumeInAppAlertUseCase(store = get()) }
    factory { GetNotificationStatusUseCase(repository = get()) }
    factory { GetLastSyncUseCase(repository = get()) }
    factory { PreviewAlertRuleUseCase(publications = get(), matchRule = get(), sections = get()) }
}
