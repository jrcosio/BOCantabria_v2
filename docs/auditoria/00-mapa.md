# Mapa técnico

## Baseline y alcance

- Fecha: 2026-09-06. Commit: `ee88d240f5b62c56e25d8233196be542d94e1509`. Branch: `main`.
- Working tree inicialmente limpio, verificado con `git status --short`. Las líneas corresponden a ese working tree.
- Exclusivamente Fase 0: reconocimiento estático e historial. Este documento no constituye todavía un inventario de defectos ni una propuesta de optimizaciones.
- Solo se escriben documentos dentro de `docs/auditoria/`. No se han ejecutado builds, tests, lint, instalaciones ni llamadas a servicios remotos.
- Tres subagentes GPT-6 Astra con razonamiento `ultra` reconocen UI, datos y red/asincronía en paralelo, sin escribir archivos. El agente principal revisa plataforma, build, tests e historial y consolida el mapa.
- Convención de rutas: en las referencias siguientes, `M/` significa `app/src/main/java/com/jrblanco/boccantabria/`, `T/` significa `app/src/test/java/com/jrblanco/boccantabria/` y `A/` significa `app/src/androidTest/java/com/jrblanco/boccantabria/`. Son abreviaturas de rutas reales del repositorio.

## Resumen ejecutivo

BOCantabria es una aplicación de consulta del Boletín Oficial de Cantabria con archivo local, búsqueda, guardados, visor/compartición de PDF, resumen y conversación con IA sobre documentos y avisos por reglas. Tiene un único módulo Android, una Activity y UI Compose. La organización principal es `ui → domain ← data`, con Koin conectando implementaciones e interfaces y utilidades transversales en `core`.

Room suministra los datos observados por la UI; RSS alimenta el archivo mediante sincronización. Los PDF se descargan a caché privada. Gemini se consume mediante HTTP directamente desde la aplicación, y Firebase proporciona configuración remota, analítica y Crashlytics. WorkManager reutiliza el ciclo de sincronización para los avisos. La persistencia local no implica por sí sola acceso sin conexión desde arranque: `PrepareStartupUseCase` comprueba primero conectividad (`M/domain/usecase/PrepareStartupUseCase.kt:23`).

## Módulos y estructura

`settings.gradle.kts:25` nombra el proyecto y `settings.gradle.kts:26` incluye únicamente `:app`. No hay módulos separados para dominio, datos, benchmarks o perfiles.

| Área | Responsabilidad real |
|---|---|
| `app/src/main/.../ui` | Pantallas por funcionalidad, ViewModels, rutas, navegación y adaptación a Android para compartir/abrir documentos. |
| `app/src/main/.../domain` | Modelos, errores/resultados, contratos de repositorios y 44 archivos de casos de uso. |
| `app/src/main/.../data` | Implementaciones de repositorios, Room, RSS/HTTP, caché PDF, preferencias, IA, notificaciones, worker y adaptadores Firebase. |
| `app/src/main/.../core` | DI, dispatchers/reloj/aleatoriedad, telemetría, utilidades y componentes/tema Compose. |
| `app/schemas` | Esquemas Room versionados del 1 al 5. |
| `specs`, `docs/diseno`, `Datos_modelo` | Especificaciones, diseño y material auxiliar; no se consideran prueba de comportamiento ejecutado. |
| `app/src/test`, `app/src/androidTest` | Pruebas JVM/integración/arquitectura y pruebas instrumentadas/UI. |

Inventario de archivos Kotlin, obtenido con `rg --files`: 297 en `src/main`, 180 en `src/test` y 46 en `src/androidTest`. Incluye fakes/helpers; no son cantidades de casos de prueba ni cobertura.

## Stack tecnológico

Versiones **declaradas en el repositorio**, no certificación de resolución efectiva ni de compatibilidad:

| Componente | Versión/configuración | Evidencia |
|---|---|---|
| AGP | 9.3.2 | `gradle/libs.versions.toml:3`, `:135` |
| Kotlin del catálogo | 2.2.10; usado por plugins Compose y Serialization. No se aplica `org.jetbrains.kotlin.android` separadamente. | `gradle/libs.versions.toml:4`, `:136`, `:137`; `app/build.gradle.kts:1` |
| Kotlin integrado | La configuración contempla Kotlin integrado en AGP; versión efectiva del compilador no resuelta en esta fase. | `gradle.properties:20` y plugins anteriores |
| Compose Compiler | Plugin `org.jetbrains.kotlin.plugin.compose`, declarado 2.2.10 | `gradle/libs.versions.toml:136` |
| KSP | 2.2.10-2.0.2 | `gradle/libs.versions.toml:8` |
| Gradle wrapper | 9.5.0, distribución con SHA-256 fijado | `gradle/wrapper/gradle-wrapper.properties:4` |
| Android | minSdk 28; targetSdk 37; compileSdk release(37) | `app/build.gradle.kts:42`, `:48` |
| Java/JVM | Java source/target 11; daemon/toolchain Gradle 21; no `jvmTarget` Kotlin explícito localizado | `app/build.gradle.kts:66`; `gradle/gradle-daemon-jvm.properties:12` |
| Compose / UI | BOM 2026.02.01, Material 3, Activity Compose 1.13.0 | `gradle/libs.versions.toml:17`, `:18`; `app/build.gradle.kts:92` |
| AndroidX | Core 1.19.0, Splashscreen 1.2.0, Lifecycle 2.11.0 | `gradle/libs.versions.toml:14` |
| Navegación | Navigation Compose 2.10.0; rutas serializables | `gradle/libs.versions.toml:19` |
| DI | Koin BOM 4.2.2, Android/Compose/WorkManager | `gradle/libs.versions.toml:25`; `app/build.gradle.kts:147` |
| Persistencia | Room 2.8.4, SharedPreferences, archivos privados | `gradle/libs.versions.toml:28`; detalle en persistencia |
| Red | OkHttp BOM 5.5.0, llamadas HTTP directas | `gradle/libs.versions.toml:29`; `app/build.gradle.kts:119` |
| Serialización | kotlinx.serialization JSON 1.8.1; parser XML RSS propio | `gradle/libs.versions.toml:11`; detalle en red |
| Async | kotlinx.coroutines 1.11.0, Flow, integración Tasks Firebase | `gradle/libs.versions.toml:35`; `app/build.gradle.kts:135` |
| Segundo plano | WorkManager 2.11.1 | `gradle/libs.versions.toml:38` |
| PDF | AndroidX PDF Compose y document-service 1.0.0-beta01 | `gradle/libs.versions.toml:32`, `:101`, `:104` |
| Firebase | BOM 34.18.0: Analytics, Crashlytics, Remote Config | `gradle/libs.versions.toml:22`; `app/build.gradle.kts:141` |
| Plugins Firebase | Google Services 4.5.0; Crashlytics 3.0.8 | `gradle/libs.versions.toml:5` |
| Testing | JUnit 4.13.2, AndroidX JUnit 1.3.0, Espresso 3.7.0, AndroidX Test 1.7.0, Turbine 1.2.1, MockK 1.14.11, Robolectric 4.16.1, Konsist 0.17.3 | `gradle/libs.versions.toml:40` |

No se localizaron dependencias ni imports de Retrofit, Ktor, DataStore, Coil, Glide o Picasso en producción. Las imágenes inspeccionadas son recursos empaquetados, por ejemplo `painterResource(R.drawable.about_author)` en `M/ui/info/InfoScreen.kt:233`. WebView: no utilizado en el código de producción inspeccionado.

## Build y release

- Versión de aplicación declarada: `2.0.0`, código 4 (`app/build.gradle.kts:50`). Compose y BuildConfig habilitados (`:70`).
- Release declara `optimization { enable = false }` (`app/build.gradle.kts:59`). No hay configuración explícita de firma release, reglas ProGuard propias ni ajuste separado de resource shrinking en el script leído. No se ha inspeccionado un APK final ni medido su tamaño.
- La credencial Gemini se lee mediante un provider desde `local.properties` con alternativa de variable de entorno, y se introduce en BuildConfig (`app/build.gradle.kts:26`, `:56`). Esto delimita una superficie a revisar en Fase 1; no se ha reproducido ningún valor ni comprobado una credencial operativa.
- KSP exporta esquemas a `app/schemas` (`app/build.gradle.kts:87`). Cualquier diagnóstico futuro que compile deberá comprobar que no altera esos archivos versionados: no están incluidos en la excepción de escritura del usuario.
- Configuration cache activada y heap Gradle de 2 GiB (`gradle.properties:9`, `:17`). Existe compatibilidad explícita con source sets Kotlin para KSP (`:24`).
- No se localizaron configuración de métricas/informes Compose Compiler, módulos Macrobenchmark ni Baseline/Startup Profile propios. Su ausencia no se considera defecto. No se intentó obtener métricas en Fase 0.

## Plataforma Android y superficie externa

El manifest fuente declara una Activity launcher exportada, `singleTop`, orientación portrait y ajuste `adjustResize` (`app/src/main/AndroidManifest.xml:63`). Declara FileProvider no exportado y AndroidX Startup no exportado; elimina el inicializador automático de WorkManager para conectarlo a Koin (`:33`, `:49`). No declara services ni receivers propios. Esto no equivale a inventariar el manifest fusionado con componentes de dependencias.

| Permiso fuente | Uso localizado | Motivo visible en Fase 0 |
|---|---|---|
| INTERNET (`AndroidManifest.xml:5`) | RSS/PDF/Gemini y Firebase | Acceso a servicios remotos. |
| ACCESS_NETWORK_STATE (`AndroidManifest.xml:6`) | `M/data/source/local/AndroidConnectivityDataSource.kt:19` | Comprobación de red validada en arranque; además hay constraints de WorkManager. |
| POST_NOTIFICATIONS (`AndroidManifest.xml:8`) | `M/ui/alerts/form/AlertFormScreen.kt:157`; `M/data/notification/AndroidAlertNotifier.kt:68` | Solicitud contextual y entrega de avisos. |

Las notificaciones usan PendingIntent inmutable (`M/data/notification/AndroidAlertNotifier.kt:133`) y extras procesados por `MainActivity.onCreate/onNewIntent` (`M/MainActivity.kt:30`, `:56`; `M/ui/navigation/PendingNavigation.kt:41`). El manifest propio no tiene filtros de enlaces web.

Compartir PDF utiliza `content://` y permiso temporal de lectura (`M/ui/share/DocumentSharing.kt:19`). FileProvider limita su raíz a `cache/documents/` (`app/src/main/res/xml/file_paths.xml:8`). Backup está habilitado (`AndroidManifest.xml:12`); los XML de backup/extracción contienen estructura de plantilla sin reglas activas específicas (`app/src/main/res/xml/backup_rules.xml:8`, `app/src/main/res/xml/data_extraction_rules.xml:6`). El contenido efectivo del backup queda para revisión posterior.

## Telemetría

Firebase está encapsulado mediante adaptadores: `FirebaseAnalyticsTracker` envía parámetros saneados por el modelo de eventos y captura fallos de envío (`M/data/telemetry/FirebaseAnalyticsTracker.kt:19`, `:30`). `FirebaseCrashReporter` envía excepciones no fatales y logs a Crashlytics; la copia a Logcat se limita a debug (`M/data/telemetry/FirebaseCrashReporter.kt:24`, `:31`). El logger de Koin usa ERROR en debug y NONE fuera de debug (`M/BOCantabriaApp.kt:17`). Esto describe puntos de salida, no garantiza aún que todos los mensajes estén libres de datos sensibles.

## Testing

CI declarada en `.github/workflows/android.yml`: JDK 21 (`:23`), `:app:assembleDebug` (`:35`), `:app:testDebugUnitTest` (`:38`) y `:app:lintDebug` (`:41`). Publica informes y deja las instrumentadas para ejecución local (`:44`). No se ha consultado el resultado de una ejecución de CI.

Las pruebas JVM tienen recursos Android y retorno de valores predeterminados habilitados (`app/build.gradle.kts:76`). Se declaran Room Testing, MockWebServer con TLS, WorkManager Testing, corrutinas, Turbine y Koin Test (`:153`); instrumentadas con Compose Test, Espresso, MockK Android y Koin (`:172`).

Existen suites de integración de boletín (`T/integration/BulletinFlowIntegrationTest.kt:66`), documentos (`T/integration/DocumentFlowIntegrationTest.kt:68`), guardados (`T/integration/SavedFlowIntegrationTest.kt:67`), búsqueda (`T/integration/SearchFlowIntegrationTest.kt:64`), resumen IA (`T/integration/AiSummaryFlowIntegrationTest.kt:54`) y avisos (`T/integration/AlertFlowIntegrationTest.kt:61`). También migraciones Room (`T/data/source/local/BocDatabaseMigrationTest.kt:36`) y construcción real del worker por Koin en dispositivo (`A/data/background/AlertSyncWorkerKoinTest.kt:26`).

Konsist expresa reglas de separación UI/datos/dominio y encapsulación de SDKs (`T/architecture/ArchitectureRulesTest.kt:18`, `:31`, `:60`). Una regla exige archivo de test para clases de dominio y ViewModels (`:135`); la presencia de ese archivo no demuestra cobertura suficiente de ramas ni resultados satisfactorios.

## Hotspots según Git

118 commits alcanzables desde HEAD, incluyendo merges, entre el primer commit del 2026-08-28 y el baseline del 2026-09-06. Medición: `git rev-list --count HEAD`, `git log --reverse`, `git log --stat -5` y `git log --no-merges --format= --name-only -- app/src | sort | uniq -c | sort -nr`. El conteo siguiente es número de commits no merge que mencionan el archivo; no líneas cambiadas ni tasa de defectos.

| Archivo | Commits |
|---|---:|
| `app/src/main/res/values/strings.xml` | 19 |
| `T/di/KoinModulesTest.kt` | 14 |
| `M/core/di/DataModule.kt` | 13 |
| `M/core/di/DomainModule.kt` | 12 |
| `M/ui/navigation/BOCantabriaNavHost.kt` | 10 |
| `M/ui/main/MainShell.kt` | 10 |
| `M/core/di/UiModule.kt` | 10 |
| `M/ui/navigation/Routes.kt` | 9 |
| `M/ui/home/HomeViewModel.kt`, `M/ui/home/HomeScreen.kt`, `M/ui/detail/PublicationDetailViewModel.kt`, manifest | 8 cada uno |

Las integraciones más recientes son Inicio/panel lateral (`ee88d24`, `57dfc2b`) y Avisos (`e929cae`, `7c5edba`). Temas de fixes registrados: errores mostrados en Preguntar (`5e96b6d`, `a3cf819`), alcance de conversación (`a3cfb13`), carrera al liberar documentos (`852bbc9`), insets/listas/acciones (`b70ab47`, `3ba025d`) y navegación desde portada (`8a8d0f9`). Son señales para revisar regresiones y contexto; no se afirma que estos fallos sigan presentes.

El commit `57dfc2b` relata 1.193 unitarias, 229 instrumentadas y 17 incidencias de lint. Son afirmaciones históricas del commit, **no resultados reproducidos por esta auditoría**.

## Incertidumbres y límites de esta fase

- Sin ejecución de Gradle: no se certifican resolución efectiva de dependencias, versión del compilador Kotlin integrado, target bytecode Kotlin, compatibilidad del toolchain ni estado actual de build/lint/tests.
- Sin dispositivo, profiler ni benchmark: no se certifican arranque, recomposiciones, memoria, tiempos SQL/red, batería, restauración ni funcionamiento del proceso PDF.
- Sin consultas externas: no se verifican CVEs, versiones disponibles ni compatibilidad contra documentación de proveedores. No hay afirmaciones de vulnerabilidades verificadas.
- Sin manifest fusionado ni APK: el inventario de componentes/permisos y configuración de empaquetado se limita al código fuente y dependencias declaradas.
- Sin acceso a Firebase/Gemini en servicio: se desconoce configuración remota publicada, cuotas reales, restricciones de credencial, facturación y comportamiento de respuestas reales.
- Las lecturas iniciales de `app/proguard-rules.pro` y `.github/workflows/ci.yml` devolvieron archivo inexistente; se encontró y leyó el workflow real `.github/workflows/android.yml`. Tampoco existen las rutas tentativas `BocHttpClient.kt`/`HttpClientFactory.kt`: la fábrica se localizó en `OkHttpPublicationRemoteDataSource.kt:161`. No falló ninguna herramienta de build: no se intentó ejecutarla.

## Arquitectura real y flujo de datos

```text
Interacción Compose → callback → ViewModel → caso de uso → contrato de repositorio
                                                           ↓ Koin
                                                  implementación data
                                                   ↙              ↘
                                      Room / preferencias / PDF    HTTP / Firebase
                                                   ↓
                                     Flow → modelo dominio → UiState → Compose

Home o Worker → RunSyncCycleUseCase → refresh RSS → nuevas claves Room
                                                → evaluar reglas → persistir coincidencias
                                                → notificación / aviso interno
                                                → limpieza de caché documental
```

Es una separación por paquetes dentro del mismo módulo, no aislamiento entre módulos Gradle. El grafo central está en `M/core/di/AppModules.kt:11`; `DataModule.kt:94` conecta interfaces y adaptadores. Reloj, dispatchers, aleatoriedad y visibilidad del proceso se inyectan desde `M/core/di/CoreModule.kt:19`. La UI registra doce ViewModels (`M/core/di/UiModule.kt:24`).

Los casos de uso contienen política como conectividad/versión/mantenimiento en arranque (`M/domain/usecase/PrepareStartupUseCase.kt:23`) y secuencia sincronizar/evaluar/notificar (`M/domain/usecase/RunSyncCycleUseCase.kt:48`). Los repositorios también contienen lógica relevante: concurrencia por fuente, backfill de búsqueda, deduplicación de generación y procedencia de resúmenes. Por tanto, el dominio no concentra toda la lógica operativa; `data` hace más que transportar datos (`M/data/repository/PublicationRepositoryImpl.kt:139`, `M/data/repository/AiSummaryRepositoryImpl.kt:125`). Esto describe la distribución real, sin juzgarla como defecto arquitectónico.

Las transformaciones de frontera se hacen en el parser/normalizador y mappers de entidad; los ViewModels combinan estado y preparan presentación. Home filtra en memoria (`M/ui/home/HomeViewModel.kt:244`); Search delega consultas SQL locales (`M/data/source/local/PublicationSearchDao.kt:32`). Los fallos usan `AppResult`/`DomainError` y estados específicos de PDF/IA; también existen lecturas que capturan fallo y devuelven vacío/null. Su semántica debe revisarse con los consumidores, sin deducir todavía pérdida de datos (`M/data/repository/PublicationRepositoryImpl.kt:129`).

## Navegación

- **Grafo exterior:** Splash → MainShell; Info, formulario de aviso, detalle, PDF y Preguntar se abren sin barra inferior (`M/ui/navigation/BOCantabriaNavHost.kt:39`, `:67`, `:89`, `:100`, `:117`, `:120`). Al continuar, Splash sale del back stack (`:60`).
- **Grafo interior:** Inicio, Buscar, Guardados y Avisos, con drawer de secciones y Scaffold compartido (`M/ui/main/MainShell.kt:177`, `:209`). Cambiar de pestaña usa save/restore y singleTop (`:272`).
- **Rutas tipadas:** tipos serializables con claves de publicación, filtros y argumentos; referencias de página PDF se transportan en base cero (`M/ui/navigation/Routes.kt:5`, `:92`).
- **Traspaso de búsqueda:** Home entrega texto a Buscar mediante una ruta nueva, sin restaurar un estado previo que lo sustituya (`M/ui/main/MainShell.kt:103`).
- **Documento y conversación:** PDF y Ask se apilan sobre detalle; el ViewModel del detalle es el punto de liberación de sesión IA y descarte de conversación (`M/ui/navigation/BOCantabriaNavHost.kt:112`, `:127`; `M/ui/detail/PublicationDetailViewModel.kt:338`).
- **Notificaciones:** se retiene un destino pendiente a nivel de proceso y se consume después de portada; Home exterior abre publicaciones y MainShell abre Novedades (`M/ui/navigation/PendingNavigation.kt:23`; `M/ui/navigation/BOCantabriaNavHost.kt:72`; `M/ui/main/MainShell.kt:152`).

## Persistencia

| Almacén | Datos y política observada | Evidencia |
|---|---|---|
| Room `boc.db`, v5 | Cinco entidades: publicaciones, estado por feed, resúmenes IA, reglas y coincidencias. Auto-migraciones 1→2→3→4→5; sin fallback destructivo. | `M/data/source/local/BocDatabase.kt:38`, `:86` |
| Publicaciones | Identidad por clave externa, blob único e índices de clasificación/fecha/guardado. Sin purga de publicaciones en las rutas de sincronización leídas. | `M/data/source/local/PublicationEntity.kt:37`; `M/data/source/local/PublicationDao.kt:145` |
| Guardados | Marca `saved_at` en la misma tabla; DAO específico. UPDATE de fuente no sobrescribe `saved_at` ni `first_seen_at`. | `M/data/source/local/PublicationDao.kt:96` |
| Búsqueda | Columna normalizada `search_text`, LIKE con escape, filtros, orden y límite. Backfill de filas antiguas por lotes de 500. No se observa FTS/Paging en este flujo. | `M/data/source/local/PublicationSearchDao.kt:32`; `M/data/repository/PublicationRepositoryImpl.kt:192` |
| Resúmenes IA | JSON y procedencia: hash de PDF, modelo, versiones de prompt/esquema y consumo; evaluación de obsolescencia. | `M/data/source/local/AiSummaryEntity.kt:28`; `M/data/repository/AiSummaryRepositoryImpl.kt:276` |
| Avisos | Unicidad regla/publicación, relación con regla y cascada al eliminarla; estado de leído por publicación. | `M/data/source/local/AlertMatchEntity.kt:23` |
| SharedPreferences | Archivo privado `boc_ai`, aceptación del aviso IA versionada `ai_notice_accepted_v3`; observación con listener y `awaitClose`. | `M/data/source/local/AiPreferences.kt:37`, `:70`, `:78` |
| Caché PDF | `cacheDir/documents`, nombres derivados de hash, temporal `.part`, checksum `.sha256`, mtime de uso. Evicción por antigüedad hasta presupuesto de 100 MiB, excluyendo claves suministradas como en uso. No es un límite absoluto si hay archivos protegidos. | `M/data/source/local/FileDocumentCache.kt:34`, `:79`; `M/data/repository/DocumentRepositoryImpl.kt:151` |
| Memoria del proceso | Conversación activa, sesión documental remota, coordinación de cuota y destino de notificación pendiente. No equivalen a persistencia durable. | `M/data/repository/AiChatRepositoryImpl.kt:78`; `M/data/source/remote/AiDocumentSessionStore.kt:47`; `M/ui/navigation/PendingNavigation.kt:23` |

El parser usa DOM XML; declara defensas DTD/entidades y limita los elementos procesados. El normalizador valida título, enlace y fecha, conserva categorías/advertencias y calcula identidad con blob/URL/hash (`M/data/source/remote/BocRssParser.kt:31`; `M/data/source/remote/PublicationNormalizer.kt:24`). La eficacia ante entradas adversariales no se ha probado en Fase 0.

## Red

El catálogo declara 19 fuentes HTTPS de `www.cantabria.es/o/BOC/feed/...` (`M/data/source/remote/BocFeedCatalog.kt:15`). `OkHttpPublicationRemoteDataSource` descarga RSS, limita el cuerpo a 5 MiB, compara SHA-256 y puede reintentar tres veces con espera creciente y jitter (`M/data/source/remote/OkHttpPublicationRemoteDataSource.kt:34`, `:77`, `:121`, `:130`). La fábrica real del cliente compartido está en ese mismo archivo (`:161`), no en una clase `BocHttpClient` separada. El repositorio limita a cuatro fuentes simultáneas, almacena resultados por fuente y agrega éxitos/fallos parciales (`M/data/repository/PublicationRepositoryImpl.kt:139`, `:207`). Su criterio de frescura usa el último éxito y TTL de 30 minutos (`:129`, `:325`).

Los PDF usan `OkHttpDocumentDownloader`, enlazado en `M/core/di/DataModule.kt:120`. La IA usa Gemini REST con subida resumible a Files API, finalización/sondeo y posterior generación; no hay backend propio visible en ese recorrido (`M/data/source/remote/OkHttpGeminiDocumentUploader.kt:54`, `:136`). El identificador configurado es `gemini-3.1-flash-lite` (`M/domain/model/AiSummaryConstants.kt:34`); no se ha comprobado disponibilidad o contrato real contra el servicio.

Resumen y chat comparten preparación y coordinador de cuota. El resumen se valida y persiste; el chat mantiene conversación en memoria, valida citas/respuestas y limita historial enviado a 12 mensajes y pregunta a 500 caracteres (`M/data/repository/AiSummaryRepositoryImpl.kt:224`; `M/data/repository/AiChatRepositoryImpl.kt:182`; `M/domain/model/AiChatConstants.kt:23`, `:35`). El transporte modela 429 y esperas del servicio y reintentos de red/5xx (`M/data/source/remote/OkHttpGeminiSummaryDataSource.kt:55`, `:242`, `:268`). Los límites locales de cuota requieren contraste con el proyecto real: el código lo reconoce (`M/data/source/remote/GeminiRateLimitCoordinator.kt:130`).

Remote Config usa `fetchAndActivate().await()` encapsulado y defaults empaquetados (`M/data/source/remote/FirebaseRemoteConfigDataSource.kt:17`, `:36`; `app/src/main/res/xml/remote_config_defaults.xml:9`).

## Asincronía, arranque y segundo plano

`Application.onCreate` inicia Koin y la integración WorkManager, exceptuando el proceso aislado del PDF (`M/BOCantabriaApp.kt:14`, `:28`). No hace una llamada explícita a red o Room en ese cuerpo; esto no mide el coste de inicializadores/SDKs. Splash ejecuta preparación y espera mínima en paralelo, con timeout de 8 segundos y mínimo visible de 1,2 segundos. Modela Ready/Error/Blocked; desde Error permite continuar sin conexión, desde Blocked no (`M/ui/splash/SplashViewModel.kt:49`, `:58`, `:100`).

Los ViewModels usan `viewModelScope`; en data se inyectan dispatchers para I/O y hay `Mutex`, semáforos y `CompletableDeferred` para coordinación. Sesión documental y chat tienen scopes propios con `SupervisorJob`, por lo que no toda operación tiene duración limitada a una composición (`M/data/source/remote/AiDocumentSessionStore.kt:47`; `M/data/repository/AiChatRepositoryImpl.kt:78`). La sesión se reutiliza por clave+checksum y su liberación se ejecuta bajo el mismo lock (`M/data/source/remote/AiDocumentSessionStore.kt:60`, `:94`). Falta verificar intercalados y muerte de proceso.

Home y Worker llaman `RunSyncCycleUseCase`: captura reglas habilitadas, sincroniza, evalúa nuevas claves, guarda coincidencias, elige notificación o aviso interno según visibilidad y libera documentos sin uso. Omite avisos para la sincronización inicial (`M/domain/usecase/RunSyncCycleUseCase.kt:48`, `:75`, `:105`).

`ReconcileBackgroundSyncUseCase` programa trabajo solo con reglas activas (`M/domain/usecase/ReconcileBackgroundSyncUseCase.kt:18`). WorkManager utiliza trabajo periódico único con UPDATE, red conectada, intervalo de 4 horas y flex de 30 minutos (`M/data/background/WorkManagerBackgroundSyncScheduler.kt:28`, `:43`). Ante los resultados de dominio contemplados, el Worker devuelve success y espera al siguiente periodo para otro intento (`M/data/background/AlertSyncWorker.kt:25`). No se ha medido frecuencia efectiva, consumo ni comportamiento bajo restricciones del sistema.

## Estado y lifecycle

Los adaptadores Screen/Route observan StateFlow con `collectAsStateWithLifecycle`, y Content recibe estado/callbacks (`M/ui/home/HomeScreen.kt:55`, `:102`; `M/ui/detail/PublicationDetailRoute.kt:30`). Los ViewModels combinan datos persistidos y estado local; Search usa debounce/flatMapLatest y WhileSubscribed de 5 segundos (`M/ui/search/SearchViewModel.kt:80`, `:106`).

La conservación de estado es selectiva: Search escribe texto/filtros/orden en SavedStateHandle (`M/ui/search/SearchViewModel.kt:221`), Alertas conserva pestaña (`M/ui/alerts/AlertsViewModel.kt:103`), mientras el borrador del formulario vive en memoria del ViewModel (`M/ui/alerts/form/AlertFormViewModel.kt:68`). PDF conserva página con `rememberSaveable`/`snapshotFlow` y el ViewModel conserva/cierra el documento (`M/ui/pdf/PdfViewerScreen.kt:131`; `M/ui/pdf/PdfViewerViewModel.kt:108`, `:142`). No se extrapola esto a restauración completa tras muerte del proceso.

Compartir utiliza un efecto consumible con estados Preparing/Ready/Idle (`M/ui/share/ShareEffect.kt:32`); MainShell presenta Snackbar con acción a Novedades (`M/ui/main/MainShell.kt:132`). Son puntos de adaptación de eventos al lifecycle que conviene revisar con cambios de pantalla.

## Flujos críticos y áreas prioritarias para la auditoría

La prioridad combina centralidad funcional, fronteras de red/disco, estado compartido e historial; no se infiere solo de longitud de archivo.

| Prioridad de investigación | Razón y recorrido concreto | Evidencia de entrada |
|---|---|---|
| 1. Sincronización y avisos | Home y Worker comparten ciclo; resultados parciales, primera carga, nuevas claves y entrega dependen de varias escrituras/estados. Seguir concurrencia, cancelación e idempotencia. | `M/domain/usecase/RunSyncCycleUseCase.kt:48`; `M/data/repository/PublicationRepositoryImpl.kt:139` |
| 2. PDF e IA | Descarga, caché, servicio PDF y archivo remoto se comparten entre detalle/visor/Preguntar. Seguir propiedad, cierre, evicción, errores y superficie de credencial/transporte. | `M/data/source/remote/AiDocumentSessionStore.kt:60`; `M/ui/detail/PublicationDetailViewModel.kt:338` |
| 3. Integridad y crecimiento del archivo | Upsert debe conservar guardados; instalaciones antiguas requieren migraciones/backfill; búsqueda LIKE y secciones leen histórico creciente. Corrección primero; tiempos solo con medición. | `M/data/source/local/PublicationDao.kt:96`; `M/data/source/local/PublicationSearchDao.kt:32` |
| 4. Navegación, restauración y efectos | Dos grafos, traspaso de búsqueda, notificaciones, acciones de compartir y estados con duraciones distintas. Separar regreso entre pantallas, recreación y muerte del proceso. | `M/ui/navigation/BOCantabriaNavHost.kt:72`; `M/ui/main/MainShell.kt:272` |
| 5. Plataforma y build efectivo | Contrastar manifest fusionado/backup, telemetría y credencial empaquetada; resolver toolchain real y ejecutar diagnósticos sin modificar fuentes/esquemas. | `app/src/main/AndroidManifest.xml:12`; `app/build.gradle.kts:26`, `:59` |

Estas cinco áreas son **preguntas de investigación**, no hallazgos confirmados ni optimizaciones aprobadas.

## Áreas aparentemente bien resueltas

- Lectura Room separada de sincronización y UPDATE que preserva la marca personal del usuario (`M/data/source/local/PublicationDao.kt:96`). Hay prueba específica de esa preservación (`T/data/source/local/SavedPublicationDaoTest.kt:141`).
- Migraciones encadenadas y esquemas versionados sin fallback destructivo (`M/data/source/local/BocDatabase.kt:48`, `:86`). Los tests abren la clase actual v5 aunque algunos títulos se refieren a versiones anteriores; no se debe inferir ausencia total de prueba 4→5 por esos nombres.
- Dependencias sustituibles y contratos de capas comprobables con Konsist/Koin (`T/architecture/ArchitectureRulesTest.kt:18`; `T/di/KoinModulesTest.kt:144`).
- Mecanismos explícitos de límite de concurrencia/tamaño RSS, deduplicación de operaciones y procedencia de resúmenes. No se presentan como garantía de ausencia de carreras o entradas problemáticas.
- Navegación tipada, observación con lifecycle, cierre del objeto PDF y tratamiento explícito de insets (`M/ui/navigation/Routes.kt:5`; `M/ui/pdf/PdfViewerViewModel.kt:142`; `M/ui/main/MainShell.kt:221`).
- FileProvider acotado, PendingIntent inmutable y solicitud contextual de notificaciones; el arranque de WorkManager excluye el proceso aislado (`app/src/main/res/xml/file_paths.xml:8`; `M/data/notification/AndroidAlertNotifier.kt:139`; `M/BOCantabriaApp.kt:28`).
- Tests instrumentados dirigidos a navegación, búsqueda transferida, notificaciones, insets y PDF: `A/ui/SearchHandoffTest.kt:81`, `A/ui/AlertDeepLinkTest.kt:33`, `A/ui/MainShellBottomInsetTest.kt:45`, `A/ui/pdf/PdfViewerSmokeTest.kt:40`. Presencia comprobada; ejecución pendiente.

## Checkpoint 0

Fase 0 terminada. No se han iniciado hallazgos, optimizaciones ni plan de mejora. Continuar únicamente tras autorización del usuario. El estado durable está en `docs/auditoria/PROGRESO.md`.
