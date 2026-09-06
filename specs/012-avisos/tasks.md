---

description: "Task list for 012-avisos"
---

# Tasks: Avisos

**Input**: Design documents from `/specs/012-avisos/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/internal-contracts.md](./contracts/internal-contracts.md),
[quickstart.md](./quickstart.md)

**Tests**: obligatorios. El principio V de la constitución no es negociable y la regla novena de Konsist
**tumba la build** si una clase de dominio de nivel superior o un `ViewModel` no tiene fichero de
prueba. No hay tareas de prueba «opcionales» en este proyecto.

Rutas abreviadas: `src/` = `app/src/main/java/com/jrblanco/boccantabria/`, `test/` =
`app/src/test/java/com/jrblanco/boccantabria/`, `androidTest/` =
`app/src/androidTest/java/com/jrblanco/boccantabria/`, `res/` = `app/src/main/res/`.

## Formato: `[ID] [P?] [Story] Descripción`

- **[P]**: se puede hacer en paralelo — fichero distinto, sin dependencias pendientes
- **[Story]**: a qué historia pertenece (US1 … US8). Setup, Foundational y Polish no llevan etiqueta

---

## Phase 1: Setup

**Propósito**: dependencias, manifest, iconos y textos. Nada de esto depende de código nuevo.

- [X] T001 Añadir a `gradle/libs.versions.toml` la versión `work = "2.11.1"` y las librerías `androidx-work-runtime-ktx`, `androidx-work-testing`, `androidx-lifecycle-process` (`version.ref = "lifecycle"`) y `koin-androidx-workmanager` **sin versión** (D-419)
- [X] T002 Declarar en `app/build.gradle.kts` `implementation` de `work-runtime-ktx`, `lifecycle-process` y `koin-androidx-workmanager`, y `testImplementation(libs.androidx.work.testing)`; comprobar con `./gradlew :app:assembleDebug` que resuelve
- [X] T003 En `app/src/main/AndroidManifest.xml`: `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`, `android:launchMode="singleTop"` en `MainActivity`, y el `provider` `androidx.startup.InitializationProvider` con `tools:node="merge"` que retira `androidx.work.WorkManagerInitializer` con `tools:node="remove"` (D-420, D-424)
- [X] T004 `src/BOCantabriaApp.kt`: añadir `workManagerFactory()` al `startKoin` (D-420)
- [X] T005 [P] Añadir en `res/drawable/` los diez iconos `ic_notifications`, `ic_notifications_filled`, `ic_notifications_off`, `ic_notification_bell`, `ic_tune`, `ic_add`, `ic_more_vert`, `ic_edit`, `ic_delete`, `ic_done_all`, de Material Symbols, **comprobando uno a uno que el trazado lleva coordenadas negativas** antes de usar la plantilla de 960 (D-435)
- [X] T006 [P] Añadir a `res/values/strings.xml` el bloque `<!-- ===== Feature 012: avisos ===== -->` con `nav_alerts`, los textos de la pantalla Avisos (título, pestañas, tarjeta introductoria, cabecera, estados vacíos, banner, hoja de ajustes, menú, diálogo de eliminar), los del formulario (mensaje superior, etiquetas, ayudas, radios, selector, resumen «Así funcionará», acciones, rechazos de palabra), los de la notificación y el Snackbar, los del diálogo de permiso, y los `plurals` `alerts_active_count`, `alerts_matches_today`, `alert_notification_summary`, `alert_snackbar_many`, `alert_form_preview_count`, `alert_form_sections_selected` (D-436, FR-073)

---

## Phase 2: Foundational — bloquea todas las historias

**Propósito**: dominio, persistencia, la sincronización que dice qué es nuevo, el ciclo y el grafo.
**Ninguna historia puede empezar hasta que esta fase esté entera**: todas pasan por el mismo camino.

### 2.1 Dominio

Una prueba por clase, o la build no compila.

- [X] T007 [P] `src/domain/model/KeywordMatchMode.kt` con `byNameOrDefault` + `test/domain/model/KeywordMatchModeTest.kt`
- [X] T008 [P] `src/domain/model/AlertRule.kt` con `hasCriteria` y sus `require` + `AlertRuleTest` (D-407, D-414)
- [X] T009 [P] `src/domain/model/AlertRuleDraft.kt` con `validate`, `addingKeyword`, `removingKeyword`, `suggestedName`, `toRule`, `duplicateOf`, las constantes y los tipos `AlertRuleValidationError`, `KeywordAddition`, `KeywordRejection` + `AlertRuleDraftTest` (nombre 1..60 recortado, término 2..60, décimo primero rechazado, duplicado normalizado rechazado, «Copia de », padre expandido en `toRule`) **y** `AlertRuleValidationErrorTest` y `KeywordRejectionTest` —son enums de nivel superior en `domain` y la regla novena de Konsist exige un fichero por cada uno— (FR-017, FR-018, FR-026, D-408)
- [X] T010 [P] `src/domain/model/SectionSelection.kt` (`expandToLeaves`, `toggled`, `stateOf`, `summaryParts`) + `SectionSelectionTest` con el catálogo real de `BocSectionRepositoryImpl` (padre → hijas, hija desmarcada → INDETERMINATE, «(todas)», null = todas) (D-407, FR-021)
- [X] T011 [P] `src/domain/model/AlertRuleOverview.kt`, `AlertMatch.kt`, `AlertNews.kt`, `AlertNotification.kt` (`require(ruleNames.isNotEmpty())`), `InAppAlert.kt` (`require(publicationCount >= 1)`) + sus cinco pruebas
- [X] T012 [P] `src/domain/model/AlertDelivery.kt`, `NotificationStatus.kt`, `SyncCycleOutcome.kt` + sus tres pruebas (D-441)
- [X] T013 `src/domain/model/SyncSummary.kt`: `newKeys` e `isBaseline` con defaults, `plus` une y hace OR; ampliar `test/domain/model/SyncSummaryTest.kt` (`SKIPPED` intacto, `plus` une, baseline sobrevive) (D-402)
- [X] T014 [P] `src/domain/repository/AlertRepository.kt`, `InAppAlertStore.kt`, `AlertNotifier.kt`, `BackgroundSyncScheduler.kt`, `NotificationStatusRepository.kt` según `data-model.md` §1.10
- [X] T015 `src/domain/repository/PublicationRepository.kt`: añadir `byKeys(keys: Set<String>)`, `newest(limit: Int)`, `lastSuccessfulSyncAt()`; actualizar los fakes de `test/fake/` y `androidTest/fake/` que lo implementan
- [X] T016 [P] `src/core/util/AppVisibilityProvider.kt` (interfaz + `ProcessLifecycleAppVisibilityProvider`, solo lectura de `currentState`) + `test/core/util/AppVisibilityProviderTest.kt` (Robolectric, `ProcessLifecycleOwner` en CREATED → false) (D-415)
- [X] T017 [P] `src/core/util/LocalDay.kt` + `LocalDayTest` (medianoche local con zona inyectada; cambio de día) (D-432)
- [X] T018 [P] `src/core/util/RelativeTime.kt` (`label`, `dayOf`) + `RelativeTimeTest` (hace N min, hace N h, hoy, ayer, fecha) (D-432)
- [X] T019 [P] `src/core/notification/AlertIntentExtras.kt` (D-425)
- [X] T020 [P] `src/domain/usecase/MatchAlertRuleUseCase.kt` + `test/domain/usecase/MatchAlertRuleUseCaseTest.kt` con los **dieciséis** casos de `contracts` §2.2, usando fixtures con `rawCategories` permutado del 4.3, **más** las ocho configuraciones de la tabla §22 del documento funcional (SC-010) y un caso con una palabra que contiene metacaracteres (`100%`, `a.b`, `(ayuda)`) tratada como texto literal (FR-027) (D-406, D-409)
- [X] T021 [P] `src/domain/usecase/ObserveAlertRulesUseCase.kt` (con `TimeProvider` y `ZoneId` inyectable) + prueba (pasa `LocalDay.startOf`)
- [X] T022 [P] `src/domain/usecase/GetAlertRuleUseCase.kt`, `CountAlertRulesUseCase.kt`, `ObserveAlertNewsUseCase.kt`, `ObserveUnreadAlertCountUseCase.kt`, `MarkAlertReadUseCase.kt`, `MarkAllAlertsReadUseCase.kt`, `ObservePendingInAppAlertUseCase.kt`, `ConsumeInAppAlertUseCase.kt`, `GetNotificationStatusUseCase.kt`, `GetLastSyncUseCase.kt` + sus diez pruebas
- [X] T023 [P] `src/domain/usecase/SaveAlertRuleUseCase.kt`, `SetAlertRuleEnabledUseCase.kt`, `DeleteAlertRuleUseCase.kt`, `ReconcileBackgroundSyncUseCase.kt` — escriben y después `ensureScheduled()` si `countEnabled() > 0`, si no `cancel()` — + sus cuatro pruebas con un `FakeBackgroundSyncScheduler` en `test/fake/` (D-422, `contracts` §6.2)
- [X] T024 `test/fake/FakeAlertRepository.kt`, `FakeInAppAlertStore.kt`, `RecordingAlertNotifier.kt`, `FakeAppVisibilityProvider.kt`, `FakeNotificationStatusRepository.kt` para las pruebas de casos de uso y modelos de pantalla

### 2.2 Persistencia, versión 5

- [X] T025 [P] `src/data/source/local/AlertRuleEntity.kt` y `AlertMatchEntity.kt` con índices, FK `CASCADE` y `UNIQUE(rule_id, external_key)` según `data-model.md` §2.1–2.2 (D-410)
- [X] T026 `src/data/source/local/AlertRuleDao.kt` (`observeRules(dayStart)` con `AlertRuleWithStats`, `byId`, `enabledRules`, `count`, `countEnabled`, `upsert`, `setEnabled`, **`delete`**) con KDoc que explique por qué es la única sentencia de borrado (D-412)
- [X] T027 `src/data/source/local/AlertMatchDao.kt` (`insert` IGNORE, `observeNews` con `AlertNewsRow`, `observeUnreadCount`, `markRead`, `markAllRead`) (D-413)
- [X] T028 `src/data/source/local/BocDatabase.kt`: entidades, `version = 5`, `AutoMigration(from = 4, to = 5)`, `alertRuleDao()`, `alertMatchDao()`, KDoc de la versión 5; compilar para generar `app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/5.json` y **versionarlo**
- [X] T029 `test/data/source/local/BocDatabaseMigrationTest.kt`: `a version 4 database keeps its rows and gains two empty alert tables` escribiendo la v4 a mano como las anteriores; comprobar 1→5
- [X] T030 [P] `test/data/source/local/AlertRuleDaoTest.kt` (Robolectric, Room en memoria): upsert y lectura, `observeRules` con `last_matched_at` y `matches_today`, `setEnabled` renueva `active_since`, **`delete` borra la regla y sus coincidencias en cascada y deja `publications` con las mismas filas** (D-412)
- [X] T031 [P] `test/data/source/local/AlertMatchDaoTest.kt`: unicidad (`-1` en la segunda inserción de la misma pareja), `observeNews` agrupa por publicación y concatena nombres, `observeUnreadCount` cuenta publicaciones distintas, `markRead` idempotente y marca todas las parejas de la publicación, `markAllRead`
- [X] T032 `src/data/repository/AlertRepositoryImpl.kt` (UUID, tiempos de `TimeProvider`, `expandToLeaves` al guardar, `recordMatches` troceado a 900 filtrando `-1`, mapeos `AlertRuleWithStats → AlertRuleOverview` y `AlertNewsRow → AlertNews`, analítica de D-438) + `test/data/repository/AlertRepositoryImplTest.kt` (incluida una prueba de que el evento nunca lleva nombre, palabras ni organismo)
- [X] T033 [P] `src/data/repository/InMemoryInAppAlertStore.kt` (acumula, consume) + `test/data/repository/InMemoryInAppAlertStoreTest.kt` (D-416)

### 2.3 La sincronización dice qué es nuevo

- [X] T034 `src/data/source/local/PublicationDao.kt`: `UpsertCounts.insertedKeys` filtrando `rowId == -1`, `byKeys(keys)`, `newest(limit)`; ampliar `test/data/source/local/PublicationDaoTest.kt` (`inserting reports which keys were new, and a blob-id collision is not one of them`) (D-401)
- [X] T035 `src/data/repository/PublicationRepositoryImpl.kt`: `isBaseline` una vez antes del ciclo, `newKeys` en `syncFeed`, vaciado con línea base, `baseline` en `toEvent()`, `byKeys`, `newest`, `lastSuccessfulSyncAt`; ampliar `test/data/repository/PublicationRepositoryImplTest.kt` (primera sync sin claves y baseline; segunda con las claves exactas; feed fallido sin claves; el evento no lleva claves) (D-403)

### 2.4 El ciclo y la entrega

- [X] T036 `src/domain/usecase/RunSyncCycleUseCase.kt` con los diez pasos de D-404 y el registro de D-439 + `test/domain/usecase/RunSyncCycleUseCaseTest.kt` con los **nueve** casos de §24 y las obligaciones de `contracts` §2.1 (regla creada durante el refresh no se evalúa; fallo → nada; baseline → nada; segunda vez la misma publicación → nada; dos reglas → una notificación; visible → in-app; no visible → sistema; `releaseUnusedDocuments` siempre)
- [X] T037 `src/data/notification/AndroidAlertNotifier.kt` (canal idempotente, guardia `areNotificationsEnabled`, una por publicación, resumen con ≥2, `GROUP_ALERT_SUMMARY`, `PendingIntent` con extras, `setColor` desde `colors.xml`, `ic_notification_bell`) + `test/data/notification/AndroidAlertNotifierTest.kt` con `ShadowNotificationManager` y las diez obligaciones de `contracts` §3.1 (D-417, D-418, D-425)
- [X] T038 `src/ui/home/HomeViewModel.kt`: `RunSyncCycleUseCase` en vez de `RefreshPublicationsUseCase`, retirar `ReleaseUnusedDocumentsUseCase`, leer `outcome.summary.allFailed`; actualizar `test/ui/home/HomeViewModelTest.kt`, `test/integration/BulletinFlowIntegrationTest.kt` y `test/integration/SavedFlowIntegrationTest.kt` con los fakes de T024

### 2.5 WorkManager

- [X] T039 `src/data/background/WorkManagerBackgroundSyncScheduler.kt` (`WorkManager.getInstance` dentro de los métodos; 4 h / flex 30 min; `CONNECTED`; `UPDATE`; `boc_alert_sync`) + `test/data/background/WorkManagerBackgroundSyncSchedulerTest.kt` con `WorkManagerTestInitHelper` (una petición tras dos llamadas, cancelación, restricción de red, construir sin inicializar no lanza) (D-421)
- [X] T040 `src/data/background/AlertSyncWorker.kt` (`CoroutineWorker`, siempre `success`, registro `cycle:`) + `test/data/background/AlertSyncWorkerTest.kt` con `TestListenableWorkerBuilder` y una `WorkerFactory` de prueba (D-423)

### 2.6 El grafo

- [X] T041 `src/core/di/CoreModule.kt` (`AppVisibilityProvider`), `DataModule.kt` (DAOs, `AlertRepository`, `InAppAlertStore`, `AlertNotifier`, `BackgroundSyncScheduler`, `NotificationStatusDataSource`, `NotificationStatusRepository`, `workerOf(::AlertSyncWorker)`), `DomainModule.kt` (los diecinueve casos de uso), `UiModule.kt` (`PendingNavigationStore`; los tres `viewModelOf` se añaden en sus historias)
- [X] T042 `test/di/KoinModulesTest.kt`: todos los tipos nuevos en `CROSS_MODULE_TYPES` más `WorkerParameters::class`; `koin.get<>()` de cada uno salvo el Worker y `AlertFormViewModel`; el módulo de sobrescritura sustituye `AlertNotifier`, `AppVisibilityProvider` y `BackgroundSyncScheduler` por fakes (`contracts` §7)
- [X] T043 `androidTest/fake/TestGraph.kt`: añadir `AlertRuleDao`, `AlertMatchDao`, `AlertRepositoryImpl` sobre la base en memoria, `InMemoryInAppAlertStore`, y fakes `RecordingAlertNotifier`, `FakeAppVisibilityProvider`, `FakeBackgroundSyncScheduler`, `FakeNotificationStatusDataSource` en `androidTest/fake/`

---

## Phase 3: User Story 1 — Crear un aviso y enterarse de una publicación nueva (P1) 🎯 MVP

**Objetivo**: la campana en la barra, la lista de avisos con su vacío, un formulario mínimo que guarda
una regla de una palabra, y la notificación que abre el detalle.

**Prueba independiente**: crear una regla de una palabra, provocar un ciclo con una publicación nueva
que la contenga, y comprobar la notificación y que tocarla abre el detalle correcto y marca leída.

- [X] T044 [US1] `src/ui/navigation/Routes.kt`: `Route.Alerts(tab: String? = null)` y `Route.AlertForm(ruleId: String? = null, duplicateOf: String? = null)`; ampliar `test/ui/navigation/RoutesTest.kt` (`contracts` §4.1) (D-430)
- [X] T045 [US1] `src/ui/navigation/BocBottomBar.kt`: `BottomDestination.ALERTS(ic_notifications, nav_alerts, TAG_BOTTOM_ALERTS)` en cuarta posición, parámetro `alertBadge: Int = 0` con `BadgedBox` («9+» sobre nueve, oculto a cero), y retirar el KDoc «Three destinations» (D-431)
- [X] T046 [US1] `src/ui/main/MainShell.kt`: casos `ALERTS` en `navigateTo` y `toDestination`, `composable<Route.Alerts>`, nuevo parámetro `onOpenAlertForm: (Route.AlertForm) -> Unit`; `src/ui/navigation/BOCantabriaNavHost.kt`: `composable<Route.AlertForm>` y el cableado de `onOpenAlertForm`
- [X] T047 [P] [US1] `src/ui/alerts/AlertsUiState.kt` (`AlertsTab` con `byNameOrDefault`, `AlertsUiState`, `AlertNewsDay`, `AlertRuleCardState`) según `data-model.md` §3.3
- [X] T048 [US1] `src/ui/alerts/AlertsViewModel.kt` (combina reglas, novedades, contador, estado del permiso y estado local; pestaña persistida por nombre en `SavedStateHandle["tab"]`; eventos de `data-model.md`) + `test/ui/alerts/AlertsViewModelTest.kt` (Turbine: vacío, lista con activas y pausadas, contador de activos, pestaña restaurada por nombre y nombre desconocido → NEWS)
- [X] T049 [US1] `src/ui/alerts/component/AlertsIntroCard.kt` («Sigue lo que te importa» + «+ Crear aviso», `TAG_ALERTS_CREATE`) y `src/ui/alerts/component/AlertRuleCard.kt` en su versión mínima (nombre, `Switch`, palabras, «Todas las secciones», `alertRuleTag(id)`)
- [X] T050 [US1] `src/ui/alerts/AlertsScreen.kt`: `AlertsScreen` (koinViewModel) + `AlertsContent` (barra superior con escudo, «Avisos» e `ic_tune`; `TabRow` Novedades / Mis avisos; pestaña Mis avisos con tarjeta introductoria, cabecera «Mis avisos · N activos», lista y vacío «Aún no tienes avisos» con `IllustratedMessage` e `ic_notifications_filled`); tags `TAG_ALERTS_SCREEN`, `TAG_ALERTS_TABS`, `TAG_ALERTS_RULES_LIST`, `TAG_ALERTS_RULES_EMPTY`, `TAG_ALERTS_EMPTY_ACTION`; `viewModelOf(::AlertsViewModel)` en `UiModule.kt`
- [X] T051 [P] [US1] `src/ui/alerts/form/AlertFormUiState.kt` (`Loading | Ready | Saved`, `SectionPickerRow`) según `data-model.md` §3.4
- [X] T052 [US1] `src/ui/alerts/form/AlertFormViewModel.kt` en su versión mínima: `SavedStateHandle["ruleId"|"duplicateOf"]`, carga con `GetAlertRuleUseCase`, `onNameChanged`, `onKeywordAdded/Removed`, `onEnabledChanged`, `onSave` con `SaveAlertRuleUseCase` y `Saved(requestPermission = false)` por ahora + `test/ui/alerts/form/AlertFormViewModelTest.kt` (crear, editar carga la regla, validación deshabilita, guardar emite `Saved`)
- [X] T053 [US1] `src/ui/alerts/form/component/KeywordChipsInput.kt` (campo + `ic_add` + `InputChip` con `ic_close`, Intro añade; rechazo mostrado bajo el campo) y `src/ui/alerts/form/AlertFormScreen.kt`: `AlertFormScreen` + `AlertFormContent` con barra azul y Atrás (patrón `InfoContent`), mensaje superior, nombre, palabras, interruptor «Aviso», Cancelar / «Guardar aviso» / «Guardar cambios», botón deshabilitado si `!canSave`; tags `TAG_ALERT_FORM_SCREEN`, `TAG_ALERT_FORM_NAME`, `TAG_ALERT_FORM_KEYWORD_INPUT`, `TAG_ALERT_FORM_KEYWORD_ADD`, `TAG_ALERT_FORM_SAVE`, `TAG_ALERT_FORM_CANCEL`, `alertKeywordChipTag(word)`; `viewModelOf(::AlertFormViewModel)` en `UiModule.kt`
- [X] T054 [US1] `src/ui/navigation/PendingNavigation.kt` (`PendingNavigation`, `PendingNavigationStore`, `Intent.toPendingNavigation()`) + `test/ui/navigation/PendingNavigationTest.kt` (extras → `Publication`/`AlertNews`/null; `consume` una vez) (D-424)
- [X] T055 [US1] `src/MainActivity.kt`: rellenar el almacén en `onCreate` **solo si `savedInstanceState == null`** y en `onNewIntent`; `src/ui/navigation/BOCantabriaNavHost.kt`: `LaunchedEffect` en `composable<Route.Home>` que consume `Publication(key)` → `navigate(Route.Detail(key))`; `src/ui/main/MainShell.kt`: consume `AlertNews` → `Route.Alerts("NEWS")` **sin `restoreState`**
- [X] T056 [US1] `src/ui/detail/PublicationDetailViewModel.kt`: `MarkAlertReadUseCase(externalKey)` en `init`; ampliar `test/ui/detail/PublicationDetailViewModelTest.kt` (D-426)
- [X] T057 [US1] `androidTest/ui/BottomBarNavigationTest.kt`: invertir `the_bar_offers_exactly_three_destinations_and_none_of_them_is_alerts` → `the_bar_offers_four_destinations_and_alerts_opens_its_screen` (`TAG_BOTTOM_ALERTS` visible, pulsar muestra `TAG_ALERTS_SCREEN`)
- [X] T058 [P] [US1] `androidTest/ui/alerts/AlertsScreenTest.kt` (con `createComposeRule` y `AlertsContent`): vacío con «Aún no tienes avisos» y acción; lista con dos activas y una pausada y cabecera «2 activos»
- [X] T059 [P] [US1] `androidTest/ui/alerts/AlertFormScreenTest.kt` (con `AlertFormContent`): botón deshabilitado sin criterio; escribir palabra + Intro crea el chip; la cruz lo quita; con nombre y palabra se habilita; **afirmar `TAG_ALERT_FORM_SCREEN` antes de teclear**
- [X] T060 [US1] `androidTest/ui/AlertDeepLinkTest.kt`: `ActivityScenario.launch(intent con EXTRA_TARGET=publication)` con `testGraphOverrides` y una publicación sembrada → atraviesa la portada y muestra `TAG_DETAIL_HEADER` con su título; con `EXTRA_TARGET=news` → `TAG_ALERTS_SCREEN` en Novedades
- [X] T061 [US1] `test/integration/AlertFlowIntegrationTest.kt`, primera parte: con el grafo real y `FakePublicationRemoteDataSource`, crear una regla, segunda sincronización con un ítem nuevo coincidente y `FakeAppVisibilityProvider(false)` → `RecordingAlertNotifier` recibe una notificación con la publicación y el nombre de la regla; la novedad queda sin leer; `MarkAlertReadUseCase` la marca

**Checkpoint**: la campana existe, se crea un aviso, una publicación nueva notifica, el toque abre el
detalle y lo deja leído.

---

## Phase 4: User Story 2 — Nunca retroactivo, nunca dos veces (P1)

**Objetivo**: demostrar con pruebas de flujo completo lo que el ciclo ya hace por construcción.

**Prueba independiente**: los nueve casos de §24 sobre el grafo real.

- [X] T062 [US2] Ampliar `test/integration/AlertFlowIntegrationTest.kt`: primera sincronización con reglas ya creadas → cero notificaciones, cero novedades, `cycle: baseline` registrado; segunda sincronización sin cambios → nada; ítem nuevo que no coincide → nada; el mismo ítem otra vez → nada; ítem que coincide con dos reglas → **una** notificación con dos nombres y **una** novedad; sincronización parcial con un feed fallido → solo lo nuevo de los que respondieron
- [X] T063 [US2] Ampliar `test/integration/AlertFlowIntegrationTest.kt`: editar una regla (`SaveAlertRuleUseCase` con `id`) y sincronizar sin ítems nuevos → nada, aunque el archivo tenga coincidencias; pausar, sincronizar con coincidencias, reactivar → nada de lo publicado durante la pausa; la siguiente sincronización con un ítem nuevo → sí
- [X] T064 [P] [US2] Ampliar `test/data/repository/AlertRepositoryImplTest.kt`: `save` con `id` renueva `active_since` y `updated_at` y conserva `created_at`; `setEnabled(true)` renueva `active_since`; `recordMatches` de una pareja ya existente devuelve vacío
- [X] T065 [P] [US2] Ampliar `test/domain/usecase/RunSyncCycleUseCaseTest.kt`: una sincronización con `SyncSummary.SKIPPED` (caché fresca) no evalúa ni entrega; feed vacío correcto → `NONE`

**Checkpoint**: los nueve casos de ciclo de §24 en verde sobre el grafo real.

---

## Phase 5: User Story 3 — Las novedades viven en la aplicación (P1)

**Objetivo**: pestaña Novedades con leído/no leído y agrupación por día, badge en la campana, Snackbar
con «VER» cuando la aplicación está en pantalla, «Marcar todas como leídas».

**Prueba independiente**: con novedades registradas, el contador de la barra, la entrada con su punto
azul, abrirla la deja leída, el Snackbar aparece fuera de Avisos y no dentro.

- [X] T066 [P] [US3] `src/ui/main/MainShellUiState.kt` y `src/ui/main/MainShellViewModel.kt` (`ObserveUnreadAlertCountUseCase`, `ObservePendingInAppAlertUseCase`, `ConsumeInAppAlertUseCase`, `ReconcileBackgroundSyncUseCase` en `init`) + `test/ui/main/MainShellViewModelTest.kt`; `viewModelOf(::MainShellViewModel)` en `UiModule.kt` (D-416)
- [X] T067 [US3] `src/ui/main/MainShell.kt`: `MainShellViewModel` por `koinViewModel()`, `alertBadge` a `BocBottomBar`, `snackbarHost` en el `Scaffold`, `LaunchedEffect(state.pendingAlert)` que muestra el Snackbar con «VER» si el destino actual no es Avisos y consume en todo caso; «VER» navega a `Route.Alerts("NEWS")` sin `restoreState`
- [X] T068 [P] [US3] `src/ui/alerts/component/AlertNewsItem.kt` (punto azul + fondo `surfaceSoft` si no leída; título, «Coincide con: A y B», sección con `sectionColor`, momento con `RelativeTime`; `alertNewsTag(key)`) y el separador de día
- [X] T069 [US3] `src/ui/alerts/AlertsScreen.kt`: pestaña Novedades con la lista agrupada (`AlertNewsDay`), acción «Marcar todas como leídas» (`ic_done_all`, `TAG_ALERTS_MARK_ALL_READ`), vacío «No tienes avisos nuevos» (`TAG_ALERTS_NEWS_EMPTY`) que, **cuando no existe ninguna regla**, añade la acción «Crear mi primer aviso» para que quien aterriza en la pestaña por defecto no se quede sin camino; contador en la etiqueta de la pestaña; `AlertsViewModel`: agrupación con `RelativeTime.dayOf`, `onNewsOpened(key)` (no marca: lo hace el detalle), `onMarkAllRead`; ampliar `AlertsViewModelTest` (agrupación Hoy/Ayer/fecha; `onMarkAllRead` llama al caso de uso)
- [X] T070 [P] [US3] `androidTest/ui/alerts/AlertsScreenTest.kt`: novedades con una leída y una sin leer (el punto azul solo en una); «Marcar todas como leídas» emite el evento; vacío de Novedades; entrar en la pestaña no emite marcado
- [X] T071 [P] [US3] `androidTest/ui/MainShellBadgeTest.kt` (con `createComposeRule` y `BocBottomBar`): sin badge a 0; «3» a 3; «9+» a 12
- [X] T072 [US3] `androidTest/ui/AlertSnackbarTest.kt`: con `testGraphOverrides`, monta `BOCantabriaNavHost`, navega a Buscar y **publica un `InAppAlert` en el `InAppAlertStore` del grafo** → Snackbar con «VER» visible; descartarlo no marca nada leído (FR-057); pulsarlo navega a Avisos › Novedades; publicado estando en Avisos → no hay Snackbar; en ambos casos el almacén queda consumido
- [X] T073 [US3] Ampliar `test/integration/AlertFlowIntegrationTest.kt`: con `FakeAppVisibilityProvider(true)` la coincidencia publica un `InAppAlert` y **no** llama al notifier; el contador de no leídas cuenta publicaciones distintas (dos reglas, una publicación → 1)

**Checkpoint**: descartar la notificación no pierde nada; el badge y la pestaña lo conservan.

---

## Phase 6: User Story 4 — Gestionar mis avisos (P2)

**Objetivo**: pausar desde la tarjeta, editar, duplicar, eliminar con confirmación, última coincidencia.

**Prueba independiente**: con varios avisos, cada acción del menú deja la lista en el estado esperado.

- [X] T074 [US4] `src/ui/alerts/component/AlertRuleCard.kt` completa: chips-resumen (tipo de criterio / sección), resumen de secciones con `SectionSelection.summaryParts`, «N coincidencias hoy» / «Última coincidencia: <relativo>» / «Aviso pausado», menú `ic_more_vert` con `DropdownMenu` Editar / Duplicar / Eliminar; tags `alertRuleSwitchTag(id)`, `alertRuleMenuTag(id)`, `TAG_ALERT_MENU_EDIT`, `TAG_ALERT_MENU_DUPLICATE`, `TAG_ALERT_MENU_DELETE` (D-432, FR-009)
- [X] T075 [P] [US4] `src/ui/alerts/component/DeleteAlertDialog.kt` («¿Eliminar este aviso?» con el nombre, Cancelar / Eliminar en `error`; `TAG_ALERT_DELETE_DIALOG`, `TAG_ALERT_DELETE_CONFIRM`)
- [X] T076 [US4] `src/ui/alerts/AlertsViewModel.kt`: `onToggleEnabled(id, enabled)` con guarda de `Job`, `onDeleteRequested(rule)`, `onDeleteConfirmed`, `onDeleteCancelled`, `onEditRequested(id)` / `onDuplicateRequested(id)` → la pantalla llama a `onOpenAlertForm(Route.AlertForm(ruleId=…))` / `(duplicateOf=…)`; `actionFailed` de un solo uso; ampliar `AlertsViewModelTest`
- [X] T077 [US4] `src/ui/alerts/form/AlertFormViewModel.kt`: modo duplicado con `AlertRuleDraft.duplicateOf` (pausada, «Copia de »), `isEdit` y título «Guardar cambios»; ampliar `AlertFormViewModelTest` (duplicado no copia coincidencias porque crea con `id = null`)
- [X] T078 [P] [US4] `androidTest/ui/alerts/AlertsScreenTest.kt`: el interruptor emite `onToggleEnabled`; la tarjeta pausada dice «Aviso pausado»; el menú ofrece las tres acciones; Eliminar abre el diálogo y confirmar emite el evento; la tarjeta muestra «1 coincidencia hoy»
- [X] T079 [P] [US4] `androidTest/ui/alerts/AlertFormScreenTest.kt`: en edición el botón dice «Guardar cambios»; en duplicado el nombre empieza por «Copia de » y el interruptor está apagado
- [X] T080 [US4] Ampliar `test/integration/AlertFlowIntegrationTest.kt`: eliminar una regla con coincidencias deja `publications` intacta y las novedades de esa regla desaparecen; pausar conserva reglas y novedades

**Checkpoint**: los avisos se administran sin salir de la lista; eliminar pide confirmación.

---

## Phase 7: User Story 5 — El formulario guía (P2)

**Objetivo**: tipo de coincidencia, selector jerárquico de secciones, organismo con sugerencias,
nombre propuesto, resumen «Así funcionará».

**Prueba independiente**: recorrer el formulario con las combinaciones de la tabla de §22 del documento
funcional y leer el resumen correcto.

- [X] T081 [P] [US5] `src/ui/alerts/form/component/MatchModeSelector.kt` (dos `RadioButton`, «Cualquiera» por defecto; `TAG_ALERT_FORM_MODE_ANY`, `TAG_ALERT_FORM_MODE_ALL`)
- [X] T082 [P] [US5] `src/ui/alerts/form/component/SectionPickerSheet.kt` (`ModalBottomSheet` como `SearchFiltersSheet`; título «Seleccionar secciones»; fila «Todas las secciones»; `TriStateCheckbox` por padre y `Checkbox` por hija; contador «N seleccionadas»; «Aplicar»; tags `TAG_ALERT_FORM_SECTIONS_SHEET`, `TAG_ALERT_FORM_SECTIONS_APPLY`, `alertSectionTag(code)`) (D-433)
- [X] T083 [P] [US5] `src/ui/alerts/form/component/OrganizationField.kt` (texto libre con `ExposedDropdownMenuBox` de sugerencias filtradas por subcadena normalizada; placeholder «Cualquier organismo»; `TAG_ALERT_FORM_ORGANIZATION`)
- [X] T084 [P] [US5] `src/ui/alerts/form/component/RuleSummaryCard.kt` («Así funcionará» + texto; `TAG_ALERT_FORM_SUMMARY`)
- [X] T085 [US5] `src/ui/alerts/form/AlertFormViewModel.kt`: `onMatchModeChanged`, `onSectionToggled(code)` con `SectionSelection.toggled`, `onAllSectionsSelected`, `onSectionsApplied`, `onOrganizationChanged`, filas del selector con `SectionSelection.stateOf`, sugerencias con `GetSearchIssuersUseCase`, nombre propuesto con `suggestedName` al añadir el primer criterio si el nombre está vacío, y el resumen «Así funcionará» compuesto en el VM a partir de `summaryParts`, palabras y modo (sin términos técnicos); ampliar `AlertFormViewModelTest` (padre → hijas; hija desmarcada → parcial; «(todas)»; resumen de los ocho casos de §22; nombre propuesto editable)
- [X] T086 [US5] `src/ui/alerts/form/AlertFormScreen.kt`: integrar los cuatro componentes en el orden de FR-016, campo de secciones con `ic_document` que abre la hoja, ayudas «Busca en el título…» y «Opcional · Si no eliges ninguna…»
- [X] T087 [US5] `androidTest/ui/alerts/AlertFormScreenTest.kt`: cambiar a «Todas las palabras»; abrir el selector, marcar la sección 2 → 2.1, 2.2, 2.3 marcadas y «3 seleccionadas»; desmarcar 2.3 → padre indeterminado; Aplicar cierra y el formulario resume; el resumen «Así funcionará» cambia con las palabras

**Checkpoint**: una regla solo por sección, o solo por organismo, se crea y se lee en castellano.

---

## Phase 8: User Story 6 — El permiso se pide cuando tiene sentido (P2)

**Objetivo**: estado del permiso, diálogo contextual tras el primer aviso, banner con «Abrir ajustes»,
hoja de ajustes con la última comprobación.

**Prueba independiente**: guardar el primer aviso emite la petición; rechazar deja el aviso y muestra el
banner; con el permiso apagado, las novedades siguen llegando.

- [X] T088 [P] [US6] `src/data/source/local/NotificationStatusDataSource.kt` + `AndroidNotificationStatusDataSource.kt` (`areNotificationsEnabled`, SDK ≥ 33 → `NEEDS_REQUEST`) + `test/data/source/local/AndroidNotificationStatusDataSourceTest.kt` (Robolectric, `ShadowNotificationManager.setNotificationsEnabled`) (D-427)
- [X] T089 [P] [US6] `src/data/repository/NotificationStatusRepositoryImpl.kt` + prueba
- [X] T090 [P] [US6] `src/ui/alerts/form/component/NotificationPermissionDialog.kt` («Activa las notificaciones…», «Ahora no» / «Continuar»; `TAG_ALERT_PERMISSION_DIALOG`, `TAG_ALERT_PERMISSION_CONTINUE`, `TAG_ALERT_PERMISSION_LATER`)
- [X] T091 [US6] `src/ui/alerts/form/AlertFormViewModel.kt`: `Saved(requestPermission = countRulesBefore == 0 && draft.isEnabled && status == NEEDS_REQUEST)` con `CountAlertRulesUseCase` y `GetNotificationStatusUseCase`; `onPermissionResult(granted)` emite `alert_permission{granted}`; `AlertFormScreen`: al llegar `Saved(true)` muestra el diálogo y con «Continuar» lanza `rememberLauncherForActivityResult(RequestPermission())`, después `onBack()`; ampliar `AlertFormViewModelTest` con las cuatro combinaciones de `contracts` §5.2 (D-428)
- [X] T092 [P] [US6] `src/ui/alerts/component/NotificationsDisabledBanner.kt` (`BocBannerShape`, `ic_notifications_off`, «Abrir ajustes» lanza `Settings.ACTION_APP_NOTIFICATION_SETTINGS` con `EXTRA_APP_PACKAGE`; `TAG_ALERTS_PERMISSION_BANNER`) (D-429)
- [X] T093 [P] [US6] `src/ui/alerts/component/AlertSettingsSheet.kt` (estado del permiso en castellano, «Abrir ajustes», «Última comprobación: <fecha y hora>» o «Todavía no se ha comprobado»; `TAG_ALERTS_SETTINGS_SHEET`)
- [X] T094 [US6] `src/ui/alerts/AlertsViewModel.kt`: `notificationStatus` con `onResumed()` (la pantalla lo llama desde `LifecycleResumeEffect`), `lastSyncAt` con `GetLastSyncUseCase`, `onSettingsOpened/Closed`; `AlertsScreen`: banner si `showsPermissionBanner`, `ic_tune` abre la hoja; ampliar `AlertsViewModelTest` (banner solo con activas y `DISABLED`)
- [X] T095 [P] [US6] `androidTest/ui/alerts/AlertsScreenTest.kt`: banner visible con reglas activas y `DISABLED`; ausente con `GRANTED`; ausente sin reglas activas; la hoja de ajustes muestra la última comprobación
- [X] T096 [P] [US6] `androidTest/ui/alerts/AlertFormScreenTest.kt`: con `Saved(requestPermission = true)` aparece el diálogo; «Ahora no» llama a `onBack`
- [X] T097 [US6] Ampliar `test/domain/usecase/RunSyncCycleUseCaseTest.kt` y `AndroidAlertNotifierTest`: con notificaciones desactivadas las coincidencias se registran, el contador sube y no se publica nada (FR-062)

**Checkpoint**: sin permiso, nada se pierde; el permiso se pide una vez y en contexto.

---

## Phase 9: User Story 7 — La aplicación comprueba aunque nadie la abra (P2)

**Objetivo**: el Worker programado, reconciliado en cada arranque, ejecutando el mismo ciclo.

**Prueba independiente**: con una regla activa, `dumpsys jobscheduler` muestra `boc_alert_sync`; con
cero, no; el Worker forzado con `adb` notifica con la aplicación cerrada (`quickstart.md` §3.3).

- [X] T098 [US7] Comprobar el cableado completo: `workerOf(::AlertSyncWorker)` en `DataModule.kt`, `workManagerFactory()` en `BOCantabriaApp`, inicializador retirado en el manifest; `KoinModulesTest` verifica el Worker en `verify()` con `WorkerParameters::class`
- [X] T099 [US7] Ampliar `test/domain/usecase/SaveAlertRuleUseCaseTest.kt`, `SetAlertRuleEnabledUseCaseTest.kt`, `DeleteAlertRuleUseCaseTest.kt`, `ReconcileBackgroundSyncUseCaseTest.kt`: `ensureScheduled` con la primera activa, `cancel` al quedar cero, nada si ya había activas y sigue habiendo
- [X] T100 [US7] Ampliar `test/ui/main/MainShellViewModelTest.kt`: `init` reconcilia una vez
- [X] T101 [US7] Recorrer `quickstart.md` §3.3, §3.5 y §3.8 en el emulador con el Worker forzado, y anotar el registro tal cual en `quickstart.md` §5 (SC-013, D-442)

**Checkpoint**: la notificación llega sin abrir la aplicación.

---

## Phase 10: User Story 8 — Saber antes de guardar si la regla hace algo (P3)

**Objetivo**: vista previa de coincidencias en el formulario, sin efectos.

**Prueba independiente**: una configuración que coincide con publicaciones almacenadas muestra el
recuento y la lista; el contador y las novedades no cambian.

- [X] T102 [US8] `src/domain/usecase/PreviewAlertRuleUseCase.kt` (`publications.newest(PREVIEW_LIMIT)` filtrado con `MatchAlertRuleUseCase` sobre `draft.toRule(…, isEnabled = true)`) + `test/domain/usecase/PreviewAlertRuleUseCaseTest.kt` (no escribe: el `FakeAlertRepository` no recibe llamadas) (D-437)
- [X] T103 [P] [US8] `src/ui/alerts/form/component/PreviewSheet.kt` (`ModalBottomSheet` con `PublicationCard` por resultado, abre el detalle por `onOpenPublication`; `TAG_ALERT_FORM_PREVIEW_SHEET`)
- [X] T104 [US8] `src/ui/alerts/form/AlertFormViewModel.kt`: `previewCount` y `preview` recalculados con `debounce(300)` sobre el borrador válido, `onPreviewOpened/Closed`; `AlertFormScreen`: línea «N publicaciones actuales coinciden con esta configuración» + «Ver resultados» (`TAG_ALERT_FORM_PREVIEW`), o «Ninguna publicación actual coincide»; `Route.AlertForm` gana `onOpenPublication` cableado al detalle en `BOCantabriaNavHost`; ampliar `AlertFormViewModelTest` (recuento; borrador inválido → null; abrir la vista previa no toca el repositorio de avisos)
- [X] T105 [P] [US8] `androidTest/ui/alerts/AlertFormScreenTest.kt`: con `previewCount = 3` se lee el texto y «Ver resultados» abre la hoja con tres tarjetas

**Checkpoint**: la vista previa informa y no notifica.

---

## Phase 11: Polish & Cross-Cutting

- [X] T106 [P] `CLAUDE.md`: arquitectura (`core/notification`, `data/background`, `data/notification`, `ui/alerts`, `ui/main/MainShellViewModel`, `core/util/AppVisibilityProvider`, `RelativeTime`, `LocalDay`); **reescribir la regla del borrado** —«nunca se borra una publicación; ningún DAO sobre `publications` declara borrado; `AlertRuleDao.delete` borra reglas de la persona y sus coincidencias por CASCADE, con la regresión en `AlertRuleDaoTest`»— y la línea «en ninguno de los cinco DAO»; base de datos en la versión 5; Buscar/Avisos como las dos normalizaciones que comparten `SearchText`; trampas nuevas (inicializador de WorkManager retirado, `singleTop`, `GROUP_ALERT_SUMMARY`, icono pequeño monocromo, `ProcessLifecycleOwner` solo lectura, `restoreState` con `Route.Alerts(tab)`); la deuda de la barra azul repetida (D-434); prefijos `cycle:` y `alerts:` en la sección del registro (D-440)
- [X] T107 [P] `docs/diseno/especificaciones-diseno.md`: §10.1 cuarto destino recuperado (retirar la enmienda de aplazamiento), §10.2 campana, §23 pantalla Avisos actualizada a dos pestañas y al formulario, §36 checklist; nota de fecha (D-440)
- [X] T108 [P] KDoc: `PublicationDao`, `SavedPublicationDao` y `AiSummaryDao` dejan de decir «como los otros DAO» y citan la excepción de `AlertRuleDao`; `HomeTopBar` y `SectionsDrawerContent` retiran la nota «no bell yet» (la campana vive en la barra inferior, no en la superior ni en el panel); `BocBottomBar` ya actualizado en T045
- [X] T109 [P] Cinco pruebas de registro (como en la 011): `RunSyncCycleUseCaseTest`, `AlertSyncWorkerTest`, `AndroidAlertNotifierTest`, `AlertRepositoryImplTest`, `AlertFormViewModelTest` afirman que `CrashReporter.log` y `AnalyticsTracker` no reciben nombre, palabras, organismo ni título (FR-069, FR-070, SC-011)
- [X] T110 `./gradlew :app:assembleDebug` en verde
- [X] T111 `./gradlew :app:testDebugUnitTest` en verde, incluidas `ArchitectureRulesTest` (regla novena con todas las clases nuevas) y `KoinModulesTest`
- [X] T112 `adb shell settings put secure navigation_mode 0` y `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` en verde, en segundo plano
- [X] T113 `./gradlew :app:lintDebug` en verde (atención a `MissingPermission` en `notify`, cubierto por la guardia)
- [X] T114 Recorrer `quickstart.md` §3 completo en el emulador (API 33+, tres botones) y rellenar §5 con fechas, recuentos y el registro del Worker con la aplicación cerrada
- [X] T115 Actualizar la memoria del proyecto (`~/.claude/projects/…/memory/feature-012-avisos-decisions.md`) con el estado de cierre

---

## Dependencias y orden

### Entre fases

```
Phase 1 (Setup) ──► Phase 2 (Foundational) ──► Phase 3 (US1) 🎯 MVP
                                                   ├──► Phase 4 (US2)  [solo pruebas de flujo]
                                                   ├──► Phase 5 (US3)  [badge, Snackbar, Novedades]
                                                   ├──► Phase 6 (US4)  [gestión]
                                                   ├──► Phase 7 (US5)  [formulario completo]
                                                   ├──► Phase 8 (US6)  [permiso]  ← necesita US4 (banner con activas) y US7 (hoja de ajustes)
                                                   ├──► Phase 9 (US7)  [Worker en móvil]
                                                   └──► Phase 10 (US8) [vista previa] ← necesita US5 (formulario completo)
                                                                        └──► Phase 11 (Polish)
```

US2 depende solo de Foundational + US1 (usa el grafo de integración). US3 depende de US1 (pantalla y
rutas). US4 y US5 dependen de US1. US6 depende de US4 (banner necesita `activeCount`) y toca el
formulario de US5. US7 depende de Foundational (2.5). US8 depende de US5.

### Dentro de Foundational, el único orden que importa

T007–T024 (dominio) antes de T025–T033 (persistencia) por los tipos que mapean; T034–T035 (claves
nuevas) antes de T036 (ciclo); T036–T038 antes de T039–T040 solo por `RunSyncCycleUseCase`; T041–T043
(grafo) al final, cuando existan todas las clases.

### Oportunidades de paralelismo

- Setup: T005 y T006 en paralelo con T001–T004.
- Dominio: T007–T012, T016–T023 son ficheros distintos sin dependencias entre sí.
- Persistencia: T025 → (T026 ∥ T027) → T028 → (T029 ∥ T030 ∥ T031) → (T032 ∥ T033).
- US1: T047 ∥ T051 ∥ T054 antes de sus consumidores; T058 ∥ T059 al final.
- US3: T066 ∥ T068; T070 ∥ T071 ∥ T072.
- US5: T081 ∥ T082 ∥ T083 ∥ T084 antes de T085.
- US6: T088 ∥ T089 ∥ T090 ∥ T092 ∥ T093 antes de T091 y T094.
- Polish: T106 ∥ T107 ∥ T108 ∥ T109.

---

## Estrategia

### MVP primero

Phase 1 + Phase 2 + Phase 3. Al terminar hay una campana, un aviso de una palabra, y una notificación
que abre el detalle. **Es demostrable en un móvil** y ya cumple la promesa funcional exacta del
documento: «después de cada sincronización se comprobarán las publicaciones nuevas y se avisará».

### Después

US2 (pruebas de flujo, barato y necesario), US3 (sin esto, descartar la notificación pierde la novedad),
US4 y US5 (la lista y el formulario completos), US6 (permiso), US7 (la comprobación real en móvil), US8
(vista previa) y Polish.

---

## Notas

- **La regla novena de Konsist manda**: cada tarea de dominio o `ViewModel` lleva su `XTest` en la
  misma tarea. No se aplaza un test «para después».
- **`AlertFlowIntegrationTest` crece en cuatro fases** (US1, US2, US3, US4). Es una sola clase; el
  reparto es para que cada historia se pueda cerrar sola.
- **Afirmar la pantalla antes de teclear** en todas las instrumentadas del formulario: la carrera de la
  006 vuelve si no.
- **El `5.json` se genera compilando** (T028) y se versiona; sin él no hay migración automática que
  probar.
- **`quickstart.md` §5 se rellena de verdad** (T101, T114). Un quickstart sin resultados es una
  promesa, no una comprobación.

---

## Cómo quedó

**6 de septiembre de 2026.** Las 115 tareas recorridas en la misma sesión que arrancó la feature.
Cuatro puertas en verde: `assembleDebug`, `testDebugUnitTest` (~1.200 pruebas), `lintDebug` (0 errores)
y `connectedDebugAndroidTest` (209 pruebas + `AlertSyncWorkerKoinTest`) en `emulator-5554`. Resultados y
recorrido manual en `quickstart.md` §5.

### Cuatro defectos que encontraron las pruebas en el emulador, no la revisión

1. **`workManagerFactory()` en `Application.onCreate` mataba el proceso aislado del PDF** (sin
   `ConnectivityManager`). El visor y el contador de páginas del Resumen IA habrían muerto en un móvil.
   `if (!Process.isIsolated())`. Lo cazó `PdfViewerSmokeTest` colgándose dos horas.
2. **El Snackbar «VER» se cancelaba a sí mismo**: consumir el aviso pendiente reiniciaba el
   `LaunchedEffect` que lo mostraba. Ahora corre en el ámbito del shell. Lo cazó `AlertSnackbarTest`.
3. **Los conectores de «Así funcionará» perdían los espacios**: Android recorta las cadenas de recursos
   no entrecomilladas. `" o "`, `" y "`, `", "`.
4. **El banner de permiso no salía en Android 13+**: apagar las notificaciones revoca el permiso y el
   estado es `NEEDS_REQUEST`, no `DISABLED`. Ahora sale con cualquier estado distinto de concedido. Lo
   cazó el recorrido manual del `quickstart.md`.

### Lo que no se pudo ver

La notificación con una publicación nueva **de verdad** y el Snackbar con la aplicación abierta: exigen
un boletín nuevo. WorkManager retrasa un periódico forzado antes de su hora y la imagen del emulador no
tiene `root` ni `sqlite3`. Queda para el primer día laborable con la aplicación instalada.

