package com.jrblanco.boccantabria.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.di.appModules
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.telemetry.NoOpAnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.AppVersionProvider
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.ConnectivityDataSource
import com.jrblanco.boccantabria.data.source.local.FeedSyncStateDao
import com.jrblanco.boccantabria.data.source.local.PublicationDao
import com.jrblanco.boccantabria.data.source.local.PublicationSearchDao
import com.jrblanco.boccantabria.data.source.local.SavedPublicationDao
import com.jrblanco.boccantabria.data.source.remote.BocRssParser
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigValues
import com.jrblanco.boccantabria.data.source.local.AiPreferences
import com.jrblanco.boccantabria.data.source.local.AiSummaryDao
import com.jrblanco.boccantabria.data.source.local.PdfTextExtractor
import com.jrblanco.boccantabria.data.source.local.PdfTextNormalizer
import com.jrblanco.boccantabria.data.source.remote.GroqApiKeyProvider
import com.jrblanco.boccantabria.data.source.remote.GroqRateLimitCoordinator
import com.jrblanco.boccantabria.data.source.remote.GroqSummaryDataSource
import com.jrblanco.boccantabria.data.source.remote.SummaryPromptFactory
import com.jrblanco.boccantabria.data.source.remote.SummaryValidator
import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository
import com.jrblanco.boccantabria.domain.repository.AppConfigRepository
import com.jrblanco.boccantabria.domain.usecase.AcceptAiNoticeUseCase
import com.jrblanco.boccantabria.domain.usecase.GenerateAiSummaryUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiNoticeAcceptedUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiSummaryUseCase
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.repository.DocumentRepository
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import com.jrblanco.boccantabria.domain.repository.SavedPublicationRepository
import com.jrblanco.boccantabria.domain.repository.SearchRepository
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.FilterPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetSearchIssuersUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.SearchPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.PrepareStartupUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseUnusedDocumentsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.ui.detail.PublicationDetailViewModel
import com.jrblanco.boccantabria.ui.home.HomeViewModel
import com.jrblanco.boccantabria.ui.saved.SavedViewModel
import com.jrblanco.boccantabria.ui.search.SearchViewModel
import com.jrblanco.boccantabria.ui.pdf.PdfDocumentLoader
import com.jrblanco.boccantabria.ui.pdf.PdfViewerViewModel
import com.jrblanco.boccantabria.ui.sections.SectionsViewModel
import com.jrblanco.boccantabria.ui.splash.SplashViewModel
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fails if any declared dependency cannot be resolved, so a wiring mistake breaks the build
 * instead of crashing the app on someone's phone (FR-011, FR-018).
 *
 * Runs under Robolectric because the graph is built with an `androidContext`, which is exactly
 * where wiring mistakes tend to hide. It uses the plain [Application] rather than
 * `BOCantabriaApp`: the real one starts the global Koin context, which would survive between
 * tests in the same JVM and make the second one fail. These tests build their own isolated
 * Koin instance instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class KoinModulesTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every declaration has its constructor dependencies satisfied`() {
        // verify() inspects one module at a time, so anything a module resolves from a sibling
        // has to be declared here. Keeping that list explicit doubles as documentation of the
        // edges between modules; the graph test below is what checks they really connect.
        appModules.forEach { module ->
            module.verify(extraTypes = CROSS_MODULE_TYPES)
        }
    }

    @Test
    fun `the whole graph resolves with a real android context`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val koin = koinApplication {
            androidContext(context)
            modules(appModules)
        }.koin

        koin.loadModules(
            listOf(
                module {
                    // Telemetry and remote configuration need a real FirebaseApp, which does not
                    // exist off-device. The bindings are still checked below; the implementations
                    // behind them have their own tests.
                    single<AnalyticsTracker> { NoOpAnalyticsTracker() }
                    single<CrashReporter> { NoOpCrashReporter() }
                    single<RemoteConfigDataSource> {
                        object : RemoteConfigDataSource {
                            override suspend fun fetchValues() =
                                RemoteConfigValues(minSupportedVersionCode = 0L, maintenanceMessage = "")
                        }
                    }
                    // An in-memory database rather than the real file: resolving the graph must
                    // not leave a boc.db behind in whatever directory the test happens to run in.
                    single<BocDatabase> {
                        Room.inMemoryDatabaseBuilder(context, BocDatabase::class.java).build()
                    }
                },
            ),
            allowOverride = true,
        )

        // Resolving the view models walks the whole chain: screen state, use cases, repositories,
        // sources, database and HTTP client. The rest are declarations nothing injects yet,
        // checked one by one so an unreachable binding still fails here rather than in the field.
        koin.get<SplashViewModel>()
        koin.get<PrepareStartupUseCase>()
        koin.get<AppConfigRepository>()
        koin.get<ConnectivityRepository>()
        koin.get<ConnectivityDataSource>()
        koin.get<AppVersionProvider>()

        koin.get<SectionsViewModel>()
        koin.get<PublicationRepository>()
        koin.get<BocSectionRepository>()
        koin.get<ObservePublicationsUseCase>()
        koin.get<ObserveBulletinHeaderUseCase>()
        koin.get<RefreshPublicationsUseCase>()
        koin.get<GetBocSectionsUseCase>()
        koin.get<PublicationRemoteDataSource>()
        koin.get<PublicationDao>()
        koin.get<FeedSyncStateDao>()
        koin.get<BocRssParser>()
        koin.get<PublicationNormalizer>()
        koin.get<OkHttpClient>()

        // El documento oficial. El detalle y el visor no se resuelven aquí a propósito: ambos
        // exigen la clave de la publicación como argumento de navegación, y un grafo sin pantalla
        // no puede inventarla. Lo que sí depende del cableado es todo lo que arrastran, y eso es
        // lo que se resuelve una por una; que las declaraciones existen lo comprueba verify().
        koin.get<DocumentRepository>()
        koin.get<ObservePublicationUseCase>()
        koin.get<ObserveOfficialDocumentUseCase>()
        koin.get<OpenOfficialDocumentUseCase>()
        koin.get<ShareOfficialDocumentUseCase>()
        koin.get<ReleaseUnusedDocumentsUseCase>()
        koin.get<PdfDocumentLoader>()

        // Lo guardado (feature 005). Guardados sí se resuelve entero: su modelo no necesita
        // argumentos de navegación, así que resolverlo recorre la cadena completa hasta el DAO.
        koin.get<SavedViewModel>()
        koin.get<SavedPublicationRepository>()
        koin.get<SavedPublicationDao>()
        koin.get<ObserveSavedPublicationsUseCase>()
        koin.get<ObserveSavedKeysUseCase>()
        koin.get<SetPublicationSavedUseCase>()

        // Buscar (feature 006). El modelo de pantalla no se resuelve aquí: lee el término
        // traspasado del `SavedStateHandle`, y un grafo sin pantalla no puede fabricarlo. Lo que sí
        // depende del cableado es todo lo que arrastra, y eso es lo que se resuelve una por una;
        // que la declaración existe lo comprueba verify().
        koin.get<SearchRepository>()
        koin.get<PublicationSearchDao>()
        koin.get<SearchPublicationsUseCase>()
        koin.get<GetSearchIssuersUseCase>()
        koin.get<FilterPublicationsUseCase>()

        // Resumen IA (feature 007). La cadena entera, porque es la más larga de la aplicación y
        // un eslabón mal declarado no se vería hasta pulsar «Generar resumen» en un móvil.
        koin.get<AiSummaryRepository>()
        koin.get<AiSummaryDao>()
        koin.get<AiPreferences>()
        koin.get<PdfTextExtractor>()
        koin.get<PdfTextNormalizer>()
        koin.get<SummaryPromptFactory>()
        koin.get<SummaryValidator>()
        koin.get<GroqApiKeyProvider>()
        koin.get<GroqRateLimitCoordinator>()
        koin.get<GroqSummaryDataSource>()
        koin.get<ObserveAiSummaryUseCase>()
        koin.get<GenerateAiSummaryUseCase>()
        koin.get<ObserveAiNoticeAcceptedUseCase>()
        koin.get<AcceptAiNoticeUseCase>()

        koin.get<DispatcherProvider>()
        koin.get<TimeProvider>()
        koin.get<RandomProvider>()
        koin.get<AnalyticsTracker>()
        koin.get<CrashReporter>()

        koin.close()
    }

    private companion object {
        val CROSS_MODULE_TYPES = listOf(
            Context::class,
            androidx.lifecycle.SavedStateHandle::class,
            BocDatabase::class,
            PublicationDao::class,
            FeedSyncStateDao::class,
            OkHttpClient::class,
            BocRssParser::class,
            PublicationNormalizer::class,
            PublicationRemoteDataSource::class,
            PublicationRepository::class,
            BocSectionRepository::class,
            ObservePublicationsUseCase::class,
            ObserveBulletinHeaderUseCase::class,
            RefreshPublicationsUseCase::class,
            GetBocSectionsUseCase::class,
            DispatcherProvider::class,
            TimeProvider::class,
            RandomProvider::class,
            AnalyticsTracker::class,
            CrashReporter::class,
            AppConfigRepository::class,
            ConnectivityRepository::class,
            AppVersionProvider::class,
            PrepareStartupUseCase::class,
            HomeViewModel::class,
            DocumentRepository::class,
            ObservePublicationUseCase::class,
            ObserveOfficialDocumentUseCase::class,
            OpenOfficialDocumentUseCase::class,
            ShareOfficialDocumentUseCase::class,
            ReleaseUnusedDocumentsUseCase::class,
            PublicationDetailViewModel::class,
            PdfViewerViewModel::class,
            PdfDocumentLoader::class,
            SavedPublicationDao::class,
            SavedPublicationRepository::class,
            ObserveSavedPublicationsUseCase::class,
            ObserveSavedKeysUseCase::class,
            SetPublicationSavedUseCase::class,
            SavedViewModel::class,
            PublicationSearchDao::class,
            // Resumen IA (feature 007)
            ObserveAiSummaryUseCase::class,
            GenerateAiSummaryUseCase::class,
            ObserveAiNoticeAcceptedUseCase::class,
            AcceptAiNoticeUseCase::class,
            AiSummaryDao::class,
            AiSummaryRepository::class,
            AiPreferences::class,
            PdfTextExtractor::class,
            PdfTextNormalizer::class,
            SummaryPromptFactory::class,
            SummaryValidator::class,
            GroqApiKeyProvider::class,
            GroqRateLimitCoordinator::class,
            GroqSummaryDataSource::class,
            SearchRepository::class,
            SearchPublicationsUseCase::class,
            GetSearchIssuersUseCase::class,
            FilterPublicationsUseCase::class,
            SearchViewModel::class,
        )
    }
}

/** Robolectric has no descriptor for API 37 yet, so the tests run against the newest it knows. */
internal const val ROBOLECTRIC_SDK = 36
