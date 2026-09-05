package com.jrblanco.boccantabria.core.di

import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.data.repository.AiChatRepositoryImpl
import com.jrblanco.boccantabria.data.repository.AiSummaryRepositoryImpl
import com.jrblanco.boccantabria.data.repository.AppConfigRepositoryImpl
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.data.repository.DocumentRepositoryImpl
import com.jrblanco.boccantabria.data.repository.ConnectivityRepositoryImpl
import com.jrblanco.boccantabria.data.repository.PublicationRepositoryImpl
import com.jrblanco.boccantabria.data.repository.SavedPublicationRepositoryImpl
import com.jrblanco.boccantabria.data.repository.SearchRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.AiPreferences
import com.jrblanco.boccantabria.data.source.local.AiSummaryDao
import com.jrblanco.boccantabria.data.source.local.AndroidConnectivityDataSource
import com.jrblanco.boccantabria.data.source.local.PdfPageCounter
import com.jrblanco.boccantabria.data.source.local.aiPreferences
import com.jrblanco.boccantabria.data.source.local.pdfPageCounter
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.ConnectivityDataSource
import com.jrblanco.boccantabria.data.source.local.DocumentCache
import com.jrblanco.boccantabria.data.source.local.FileDocumentCache
import com.jrblanco.boccantabria.data.source.local.FeedSyncStateDao
import com.jrblanco.boccantabria.data.source.local.PublicationDao
import com.jrblanco.boccantabria.data.source.local.PublicationSearchDao
import com.jrblanco.boccantabria.data.source.local.SavedPublicationDao
import com.jrblanco.boccantabria.data.source.local.bocDatabase
import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.data.source.remote.BuildConfigGeminiApiKeyProvider
import com.jrblanco.boccantabria.data.source.remote.AiDocumentPreparer
import com.jrblanco.boccantabria.data.source.remote.ChatAnswerValidator
import com.jrblanco.boccantabria.data.source.remote.ChatPromptFactory
import com.jrblanco.boccantabria.data.source.remote.GeminiChatDataSource
import com.jrblanco.boccantabria.data.source.remote.OkHttpGeminiChatDataSource
import com.jrblanco.boccantabria.data.source.remote.AiDocumentSessionStore
import com.jrblanco.boccantabria.data.source.remote.AiDocumentUploader
import com.jrblanco.boccantabria.data.source.remote.GeminiApiKeyProvider
import com.jrblanco.boccantabria.data.source.remote.GeminiRateLimitCoordinator
import com.jrblanco.boccantabria.data.source.remote.GeminiSummaryDataSource
import com.jrblanco.boccantabria.data.source.remote.OkHttpGeminiDocumentUploader
import com.jrblanco.boccantabria.data.source.remote.OkHttpGeminiSummaryDataSource
import com.jrblanco.boccantabria.data.source.remote.SummaryPromptFactory
import com.jrblanco.boccantabria.data.source.remote.SummaryValidator
import com.jrblanco.boccantabria.data.source.remote.BocRssParser
import com.jrblanco.boccantabria.data.source.remote.DocumentDownloader
import com.jrblanco.boccantabria.data.source.remote.OkHttpDocumentDownloader
import com.jrblanco.boccantabria.data.source.remote.OkHttpPublicationRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigDataSource
import com.jrblanco.boccantabria.data.source.remote.bocHttpClient
import com.jrblanco.boccantabria.data.source.remote.firebaseRemoteConfigDataSource
import com.jrblanco.boccantabria.data.telemetry.firebaseAnalyticsTracker
import com.jrblanco.boccantabria.data.telemetry.firebaseCrashReporter
import com.jrblanco.boccantabria.domain.repository.AiChatRepository
import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository
import com.jrblanco.boccantabria.domain.repository.AppConfigRepository
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.repository.DocumentRepository
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import com.jrblanco.boccantabria.domain.repository.SavedPublicationRepository
import com.jrblanco.boccantabria.domain.repository.SearchRepository
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Sources and repositories.
 *
 * Room and OkHttp are built through factory functions declared in `data` —`bocDatabase`,
 * `bocHttpClient`— for the same reason Firebase is: an architecture rule forbids this package
 * from importing a third-party SDK, and that rule is what keeps the graph declarable without
 * dragging every dependency's types into it.
 */
val dataModule = module {

    // --- Almacenamiento local ---
    single<BocDatabase> { bocDatabase(androidContext()) }
    single<PublicationDao> { get<BocDatabase>().publicationDao() }
    single<FeedSyncStateDao> { get<BocDatabase>().feedSyncStateDao() }
    single<SavedPublicationDao> { get<BocDatabase>().savedPublicationDao() }
    single<PublicationSearchDao> { get<BocDatabase>().publicationSearchDao() }
    single<AiSummaryDao> { get<BocDatabase>().aiSummaryDao() }

    // --- Red ---
    single<OkHttpClient> { bocHttpClient() }
    single { BocRssParser() }
    single { PublicationNormalizer() }
    single<PublicationRemoteDataSource> {
        OkHttpPublicationRemoteDataSource(
            client = get(),
            parser = get(),
            dispatchers = get(),
            random = get(),
        )
    }

    // --- El documento oficial ---
    single<DocumentDownloader> {
        OkHttpDocumentDownloader(client = get(), dispatchers = get())
    }
    single<DocumentCache> {
        FileDocumentCache(root = androidContext().cacheDir, time = get())
    }
    single<DocumentRepository> {
        DocumentRepositoryImpl(
            downloader = get(),
            cache = get(),
            dispatchers = get(),
            analytics = get(),
            crashReporter = get(),
        )
    }

    // --- Resumen IA (features 007, 009 y 010) ---
    // El contador de páginas y las preferencias entran por función fábrica desde su propio paquete:
    // este módulo no puede nombrar `androidx.pdf` ni `SharedPreferences`. Lo mismo vale para el
    // cliente del servicio de IA, que se construye dentro de `GenAiClientProvider`.
    single<PdfPageCounter> {
        pdfPageCounter(androidContext(), dispatchers = get(), crashReporter = get())
    }
    single<AiPreferences> { aiPreferences(androidContext(), dispatchers = get()) }
    single { SummaryPromptFactory() }
    single { SummaryValidator() }
    single<GeminiApiKeyProvider> { BuildConfigGeminiApiKeyProvider() }
    // Uno solo para toda la aplicación: es lo que serializa las peticiones y, desde la feature 009,
    // lo que lleva la cuenta del consumo, porque este servicio no manda cabeceras de cuota.
    single { GeminiRateLimitCoordinator(time = get(), random = get()) }
    // La subida del documento a la Files API. Escrita a mano sobre el OkHttp compartido: la
    // librería oficial de Google **prohíbe** construir su cliente con una credencial en Android
    // (010 research.md D-227).
    single<AiDocumentUploader> {
        OkHttpGeminiDocumentUploader(
            client = get(),
            apiKeys = get(),
            coordinator = get(),
            dispatchers = get(),
            crashReporter = get(),
        )
    }
    // Como mucho **una** sesión viva en todo el proceso. Es lo que hace que el documento viaje una
    // sola vez por visita, y lo que la pantalla Preguntar reutilizará (010 research.md D-207).
    single {
        AiDocumentSessionStore(uploader = get(), dispatchers = get(), crashReporter = get())
    }
    single {
        AiDocumentPreparer(
            documents = get(),
            pages = get(),
            sessions = get(),
            crashReporter = get(),
        )
    }
    single<GeminiSummaryDataSource> {
        OkHttpGeminiSummaryDataSource(
            client = get(),
            apiKeys = get(),
            coordinator = get(),
            dispatchers = get(),
            crashReporter = get(),
        )
    }
    single<GeminiChatDataSource> {
        OkHttpGeminiChatDataSource(
            client = get(),
            apiKeys = get(),
            coordinator = get(),
            dispatchers = get(),
            crashReporter = get(),
        )
    }
    factory { ChatPromptFactory() }
    factory { ChatAnswerValidator() }
    single<AiChatRepository> {
        AiChatRepositoryImpl(
            preparer = get(),
            prompts = get(),
            chat = get(),
            validator = get(),
            apiKeys = get(),
            time = get(),
            dispatchers = get(),
            analytics = get(),
            crashReporter = get(),
            // Resolved here because `data` does not read `strings.xml`, and captured once because
            // this application has a single language. With two, this becomes a provider
            // (011 contracts §3.3).
            outOfScopeText = androidContext().getString(R.string.ask_out_of_scope),
        )
    }
    single<AiSummaryRepository> {
        AiSummaryRepositoryImpl(
            documents = get(),
            preparer = get(),
            sessions = get(),
            prompts = get(),
            summaries = get(),
            validator = get(),
            dao = get(),
            preferences = get(),
            time = get(),
            dispatchers = get(),
            analytics = get(),
            crashReporter = get(),
        )
    }

    // --- El boletín ---
    single<BocSectionRepository> { BocSectionRepositoryImpl() }
    single<PublicationRepository> {
        PublicationRepositoryImpl(
            remoteDataSource = get(),
            publicationDao = get(),
            feedSyncStateDao = get(),
            normalizer = get(),
            sectionRepository = get(),
            feeds = BocFeedCatalog.definitions,
            time = get(),
            dispatchers = get(),
            analytics = get(),
            crashReporter = get(),
        )
    }

    // --- Lo guardado (feature 005) ---
    single<SavedPublicationRepository> {
        SavedPublicationRepositoryImpl(
            savedPublicationDao = get(),
            time = get(),
            dispatchers = get(),
            analytics = get(),
            crashReporter = get(),
        )
    }

    // --- Buscar (feature 006) ---
    single<SearchRepository> {
        SearchRepositoryImpl(
            searchDao = get(),
            dispatchers = get(),
            crashReporter = get(),
        )
    }

    // --- Arranque (feature 002) ---
    single<RemoteConfigDataSource> { firebaseRemoteConfigDataSource() }
    single<AppConfigRepository> {
        AppConfigRepositoryImpl(remoteConfigDataSource = get(), dispatchers = get())
    }
    single<ConnectivityDataSource> { AndroidConnectivityDataSource(context = androidContext()) }
    single<ConnectivityRepository> { ConnectivityRepositoryImpl(connectivityDataSource = get()) }

    // --- Telemetría ---
    single<AnalyticsTracker> { firebaseAnalyticsTracker(androidContext()) }
    single<CrashReporter> { firebaseCrashReporter() }
}
