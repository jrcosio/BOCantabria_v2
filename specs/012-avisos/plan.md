# Implementation Plan: Avisos

**Branch**: `012-avisos` | **Date**: 6 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-avisos/spec.md`

---

## Summary

Un cuarto destino «Avisos» en la barra inferior, con dos pestañas —**Novedades** y **Mis avisos**—, un
formulario para crear y editar reglas, y un ciclo que, tras cada sincronización correcta, evalúa las
reglas activas **solo contra las publicaciones realmente nuevas** y entrega cada coincidencia por un
solo canal: notificación de Android si la aplicación no está en pantalla, Snackbar con «VER» si lo está.
Las coincidencias se conservan como novedades con estado leído/no leído y alimentan un badge en la
campana. Un trabajo periódico de WorkManager repite el ciclo cada cuatro horas mientras exista alguna
regla activa.

La ruta técnica, en una frase: `upsertAll` deja de tirar las claves que insertó, `SyncSummary` las
transporta junto a una marca de línea base, y un `RunSyncCycleUseCase` compartido por `HomeViewModel` y
por el Worker las cruza con las reglas mediante un comparador puro, registra las coincidencias con una
unicidad de base de datos y decide la entrega consultando `ProcessLifecycleOwner` una vez por ciclo.

Tres decisiones de fondo ordenan el resto. **Nunca retroactivo se cumple por el orden del ciclo**, no
comparando fechas: las reglas se leen antes de sincronizar y la primera sincronización correcta es línea
base (D-403, D-405). **La deduplicación la hace la base de datos**: `UNIQUE(rule_id, external_key)` con
`INSERT OR IGNORE`, y solo lo realmente insertado se entrega (D-410). Y **la primera sentencia de borrado
del proyecto** entra aquí, para reglas de la persona y con la doctrina reescrita en la misma entrega
(D-412).

**Cuatro dependencias nuevas**, todas ya en la caché de Gradle: `work-runtime-ktx`, `work-testing`,
`lifecycle-process` y `koin-androidx-workmanager` (D-419).

---

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11, `minSdk 28` / `targetSdk 37`

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01), Koin 4.2.2 (BOM), Room 2.8.4, OkHttp 5.5.0,
Navigation Compose 2.10.0, Firebase (Analytics, Crashlytics). **Nuevas**: `androidx.work:work-runtime-ktx`
2.11.1, `androidx.lifecycle:lifecycle-process` 2.11.0, `io.insert-koin:koin-androidx-workmanager` (BOM),
`androidx.work:work-testing` 2.11.1 (solo pruebas)

**Storage**: Room, **versión 5**. Dos tablas nuevas —`alert_rules`, `alert_matches`— con
`AutoMigration(4, 5)` y `5.json` versionado. Sin relleno: las tablas nacen vacías. El aviso interno
pendiente vive en memoria

**Testing**: JUnit 4, MockK, `kotlinx-coroutines-test`, Turbine, Robolectric (`ShadowNotificationManager`,
`WorkManagerTestInitHelper`), Konsist, `createComposeRule` / `createAndroidComposeRule` /
`ActivityScenario`

**Target Platform**: Android 9 (API 28) en adelante, teléfono, vertical, tema claro único. El permiso de
notificaciones en tiempo de ejecución existe desde API 33

**Project Type**: aplicación Android de módulo único (`:app`), arquitectura limpia + MVVM

**Performance Goals**: la evaluación de un ciclo es memoria pura —como mucho unas decenas de
publicaciones nuevas por unas pocas reglas— y no se nota. La vista previa recorre hasta 5.000
publicaciones almacenadas con un `debounce` de 300 ms. El badge y las novedades se observan de Room

**Constraints**: el ciclo corre en segundo plano cada 4 h con red disponible y nunca a hora exacta. Ni
nombre, ni palabras, ni organismo de una regla salen a analítica, Crashlytics o Logcat. Ninguna
publicación se borra. Una coincidencia se entrega por un solo canal

**Scale/Scope**: dos pantallas nuevas y un shell modificado; ~20 componibles; 12 tipos de dominio, 5
contratos y 19 casos de uso nuevos; 2 entidades, 2 DAO, 1 repositorio, 1 notificador, 1 Worker y 1
planificador; 10 iconos; ~50 cadenas; ~35 clases de prueba nuevas o modificadas

---

## Constitution Check

*Puerta obligatoria antes de la fase 0 y revisada de nuevo tras la fase 1.*

| Principio | Cómo se cumple | Veredicto |
|---|---|---|
| **I — SDD, no negociable** | `specify` → `plan` → `tasks` → `analyze` → `implement`. Rama `012-avisos` creada por Spec Kit sobre `main` con la 011 fusionada. Ninguna línea de producto antes de `tasks.md` | ✅ |
| **II — Arquitectura limpia por capas** | `domain` sigue siendo Kotlin puro: los doce tipos nuevos, los cinco contratos y los diecinueve casos de uso no importan nada de Android. `AlertNotifier`, `BackgroundSyncScheduler` y `NotificationStatusRepository` se declaran en `domain` y se implementan en `data`. `AppVisibilityProvider` sigue el patrón de `TimeProvider` en `core/util`. Ninguna entidad cruza a `ui`: `AlertRuleWithStats` y `AlertNewsRow` se mapean en el repositorio. Las claves del `Intent` viven en `core/notification` para que `data` no nombre `MainActivity` | ✅ |
| **III — MVVM** | `AlertsScreen` + `AlertsViewModel` + `AlertsUiState`; `AlertFormScreen` + `AlertFormViewModel` + `AlertFormUiState`; `MainShellViewModel` + `MainShellUiState`. Estados inmutables, `StateFlow` de solo lectura, componibles tontos. La validación vive en `AlertRuleDraft` (dominio) y la decisión de entrega en el caso de uso, nunca en un `@Composable` | ✅ |
| **IV — Koin** | Todo en `core/di`: DAOs, repositorio, notificador, planificador, `workerOf(::AlertSyncWorker)` con `workManagerFactory()`, tres `viewModelOf`, un `single` para el almacén de navegación. `KoinModulesTest` actualizado en sus dos listas. El inicializador por defecto de WorkManager se retira para que el Worker se construya **desde** Koin y no con `by inject()` | ✅ |
| **V — Testing exigente, no negociable** | Un fichero de prueba por clase de dominio y por modelo de pantalla (regla novena). DAOs sobre Room en memoria, migración 4→5 a mano como las anteriores, notificador con `ShadowNotificationManager`, planificador con `WorkManagerTestInitHelper`, ciclo con los nueve casos de §24, comparador con los dieciséis. Instrumentadas: barra con cuatro destinos, pantalla, formulario, badge, Snackbar, deep link. Lo que solo se ve en un móvil está en `quickstart.md` §3 y **es obligatorio** | ✅ |
| **VI — Observabilidad desacoplada** | Firebase solo desde `data`. Seis eventos nuevos, todos con recuentos y enumerados; **nunca** nombre, palabras u organismo de una regla, ni título de publicación. El registro del ciclo dice cuántas y por qué canal, con prefijos `cycle:` y `alerts:` | ✅ |

**Restricciones tecnológicas**: las cuatro dependencias nuevas en `gradle/libs.versions.toml`, las de
Koin sin versión; ningún color, tamaño ni espaciado literal; `java.time` nativo; Compose y Material 3
para `Switch`, `Checkbox`, `TriStateCheckbox`, `RadioButton`, `BadgedBox`, `SnackbarHost`,
`ModalBottomSheet` y `AlertDialog`, todos inéditos en el proyecto pero del mismo sistema.

**La única sentencia de borrado.** `AlertRuleDao.delete` incumple la letra de CLAUDE.md («ningún DAO
declara borrado») y respeta su espíritu («nunca se borra una publicación»). El propietario lo decidió a
conciencia frente al borrado lógico. La doctrina se reescribe en la misma entrega y una prueba de
regresión demuestra que borrar una regla deja `publications` intacta (D-412). No es una violación de la
constitución —que no menciona el borrado— sino de la guía operativa, y la guía se actualiza.

**Puertas de calidad**: las cuatro de siempre, en orden, con `navigation_mode 0` antes de la tanda
instrumentada.

---

## Project Structure

### Documentation (this feature)

```text
specs/012-avisos/
├── spec.md                        74 FR, 15 SC, 8 historias
├── plan.md                        este fichero
├── research.md                    D-401 … D-442
├── data-model.md                  dominio, filas, presentación, el recorrido de una coincidencia
├── contracts/
│   └── internal-contracts.md      las costuras que esta feature crea o modifica
├── quickstart.md                  las cuatro puertas y el §3 obligatorio en móvil
├── checklists/
│   └── requirements.md            calidad de la especificación
└── tasks.md                       lo genera /speckit-tasks
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                          M  work, work-testing, lifecycle-process, koin-workmanager
app/build.gradle.kts                               M  las cuatro dependencias
app/schemas/…/BocDatabase/5.json                   +  esquema exportado de la versión 5
app/src/main/AndroidManifest.xml                   M  POST_NOTIFICATIONS, singleTop, sin WorkManagerInitializer
app/src/main/java/com/jrblanco/boccantabria/
├── BOCantabriaApp.kt                              M  workManagerFactory()
├── MainActivity.kt                                M  onCreate(savedInstanceState==null) + onNewIntent → PendingNavigationStore
├── core/
│   ├── di/
│   │   ├── CoreModule.kt                          M  AppVisibilityProvider
│   │   ├── DataModule.kt                          M  DAOs, repositorio, notifier, scheduler, status, workerOf
│   │   ├── DomainModule.kt                        M  diecinueve casos de uso
│   │   └── UiModule.kt                            M  tres view models + PendingNavigationStore
│   ├── notification/AlertIntentExtras.kt          +  claves del Intent (D-425)
│   └── util/
│       ├── AppVisibilityProvider.kt               +  interfaz + ProcessLifecycleAppVisibilityProvider (D-415)
│       ├── LocalDay.kt                            +  inicio del día local (D-432)
│       └── RelativeTime.kt                        +  hoy / ayer / hace N / fecha (D-432)
├── data/
│   ├── background/
│   │   ├── AlertSyncWorker.kt                     +  CoroutineWorker → RunSyncCycleUseCase (D-423)
│   │   └── WorkManagerBackgroundSyncScheduler.kt  +  periódico 4 h, UPDATE, boc_alert_sync (D-421)
│   ├── notification/
│   │   └── AndroidAlertNotifier.kt                +  canal, grupo, resumen, PendingIntent (D-417)
│   ├── repository/
│   │   ├── AlertRepositoryImpl.kt                 +
│   │   ├── InMemoryInAppAlertStore.kt             +  (D-416)
│   │   ├── NotificationStatusRepositoryImpl.kt    +
│   │   └── PublicationRepositoryImpl.kt           M  línea base, newKeys, byKeys, newest, lastSuccessfulSyncAt
│   └── source/local/
│       ├── AlertMatchDao.kt                       +  + AlertMatchEntity, AlertNewsRow
│       ├── AlertRuleDao.kt                        +  + AlertRuleEntity, AlertRuleWithStats; el único DELETE
│       ├── AndroidNotificationStatusDataSource.kt +  + NotificationStatusDataSource
│       ├── BocDatabase.kt                         M  versión 5, dos entidades, dos DAO, AutoMigration(4,5)
│       └── PublicationDao.kt                      M  insertedKeys, byKeys, newest
├── domain/
│   ├── model/
│   │   ├── AlertDelivery.kt                       +
│   │   ├── AlertMatch.kt                          +
│   │   ├── AlertNews.kt                           +
│   │   ├── AlertNotification.kt                   +
│   │   ├── AlertRule.kt                           +
│   │   ├── AlertRuleDraft.kt                      +  + AlertRuleValidationError, KeywordAddition, KeywordRejection
│   │   ├── AlertRuleOverview.kt                   +
│   │   ├── InAppAlert.kt                          +
│   │   ├── KeywordMatchMode.kt                    +
│   │   ├── NotificationStatus.kt                  +
│   │   ├── SectionSelection.kt                    +
│   │   ├── SyncCycleOutcome.kt                    +
│   │   └── SyncSummary.kt                         M  newKeys, isBaseline
│   ├── repository/
│   │   ├── AlertNotifier.kt                       +
│   │   ├── AlertRepository.kt                     +
│   │   ├── BackgroundSyncScheduler.kt             +
│   │   ├── InAppAlertStore.kt                     +
│   │   ├── NotificationStatusRepository.kt        +
│   │   └── PublicationRepository.kt               M  byKeys, newest, lastSuccessfulSyncAt
│   └── usecase/
│       ├── RunSyncCycleUseCase.kt                 +  (D-404)
│       ├── MatchAlertRuleUseCase.kt               +  (D-406)
│       ├── ObserveAlertRulesUseCase.kt            +
│       ├── GetAlertRuleUseCase.kt                 +
│       ├── SaveAlertRuleUseCase.kt                +
│       ├── SetAlertRuleEnabledUseCase.kt          +
│       ├── DeleteAlertRuleUseCase.kt              +
│       ├── CountAlertRulesUseCase.kt              +
│       ├── ObserveAlertNewsUseCase.kt             +
│       ├── ObserveUnreadAlertCountUseCase.kt      +
│       ├── MarkAlertReadUseCase.kt                +
│       ├── MarkAllAlertsReadUseCase.kt            +
│       ├── ObservePendingInAppAlertUseCase.kt     +
│       ├── ConsumeInAppAlertUseCase.kt            +
│       ├── GetNotificationStatusUseCase.kt        +
│       ├── ReconcileBackgroundSyncUseCase.kt      +
│       ├── GetLastSyncUseCase.kt                  +
│       └── PreviewAlertRuleUseCase.kt             +  (D-437)
└── ui/
    ├── alerts/
    │   ├── AlertsScreen.kt                        +  AlertsScreen + AlertsContent
    │   ├── AlertsUiState.kt                       +  + AlertsTab, AlertNewsDay, AlertRuleCardState
    │   ├── AlertsViewModel.kt                     +
    │   ├── component/
    │   │   ├── AlertRuleCard.kt                   +
    │   │   ├── AlertNewsItem.kt                   +
    │   │   ├── AlertsIntroCard.kt                 +
    │   │   ├── NotificationsDisabledBanner.kt     +
    │   │   ├── DeleteAlertDialog.kt               +
    │   │   └── AlertSettingsSheet.kt              +
    │   └── form/
    │       ├── AlertFormScreen.kt                 +  AlertFormScreen + AlertFormContent
    │       ├── AlertFormUiState.kt                +  + SectionPickerRow
    │       ├── AlertFormViewModel.kt              +
    │       └── component/
    │           ├── KeywordChipsInput.kt           +
    │           ├── MatchModeSelector.kt           +
    │           ├── SectionPickerSheet.kt          +
    │           ├── OrganizationField.kt           +
    │           ├── RuleSummaryCard.kt             +
    │           ├── PreviewSheet.kt                +
    │           └── NotificationPermissionDialog.kt +
    ├── detail/PublicationDetailViewModel.kt       M  MarkAlertReadUseCase en init (D-426)
    ├── home/HomeViewModel.kt                      M  RunSyncCycleUseCase en vez de refresh + release
    ├── main/
    │   ├── MainShell.kt                           M  cuarto destino, badge, SnackbarHost, AlertNews pendiente
    │   ├── MainShellUiState.kt                    +
    │   └── MainShellViewModel.kt                  +
    └── navigation/
        ├── BOCantabriaNavHost.kt                  M  Route.AlertForm; Publication pendiente tras la portada
        ├── BocBottomBar.kt                        M  ALERTS + alertBadge
        ├── PendingNavigation.kt                   +  + PendingNavigationStore, Intent.toPendingNavigation()
        └── Routes.kt                              M  Alerts(tab), AlertForm(ruleId, duplicateOf)

app/src/main/res/
├── drawable/ic_notifications.xml, ic_notifications_filled.xml, ic_notifications_off.xml,
│            ic_notification_bell.xml, ic_tune.xml, ic_add.xml, ic_more_vert.xml, ic_edit.xml,
│            ic_delete.xml, ic_done_all.xml                                     +  (D-435)
└── values/strings.xml                             M  nav_alerts, alerts_*, alert_form_*, alert_notification_*, alert_permission_*

app/src/test/…                                     +  ~30 clases nuevas; M  HomeViewModelTest, PublicationDaoTest,
                                                      PublicationRepositoryImplTest, SyncSummaryTest, RoutesTest,
                                                      KoinModulesTest, BocDatabaseMigrationTest, PublicationDetailViewModelTest,
                                                      BulletinFlowIntegrationTest, SavedFlowIntegrationTest
app/src/androidTest/…                              +  AlertsScreenTest, AlertFormScreenTest, MainShellBadgeTest,
                                                      AlertSnackbarTest, AlertDeepLinkTest; M  BottomBarNavigationTest,
                                                      fake/TestGraph.kt
CLAUDE.md, docs/diseno/especificaciones-diseno.md  M  (D-440)
```

**Structure Decision**: módulo único `:app`, separación por paquetes y regla `ui → domain ← data`. Dos
paquetes nuevos en `data` —`background` para lo que toca `androidx.work`, `notification` para lo que
toca `NotificationManagerCompat`— siguiendo la regla de que un SDK de plataforma vive en un solo sitio,
como `ui/pdf` con `androidx.pdf`. `ui/alerts/` y `ui/alerts/form/` con la estructura de la casa.

---

## Fases

### Fase 0 — Investigación *(hecha)*

`research.md`, decisiones **D-401 … D-442**. Las que más ordenan el resto:

- **D-401/D-402/D-403**: las claves nuevas nacen en `upsertAll`, viajan en `SyncSummary`, y la línea
  base se decide una vez y antes de los diecinueve feeds.
- **D-404/D-405**: un solo ciclo para Inicio y Worker, con la instantánea de reglas **antes** de
  sincronizar, que es lo que hace «nunca retroactivo» sin comparar fechas.
- **D-410/D-412**: la deduplicación es una unicidad de base de datos, y el primer `DELETE` del proyecto
  llega con su doctrina reescrita.
- **D-415/D-416**: visibilidad por `ProcessLifecycleOwner` decidida una vez por ciclo, y el aviso interno
  como estado pendiente que consume el shell.
- **D-424**: el deep link atraviesa la portada mediante un almacén pendiente, no con `navDeepLink`.

### Fase 1 — Diseño *(hecha)*

`data-model.md`, `contracts/internal-contracts.md`, `quickstart.md`.

**Constitution Check tras el diseño**: se vuelve a pasar. El punto que más lo tensaba —dónde se decide
notificación o Snackbar— se resolvió en el caso de uso, no en la pantalla ni en el repositorio (III y
II a la vez). Y el que parecía una violación —el `DELETE`— resultó ser de la guía y no de la
constitución, y se documenta como cambio de doctrina, no como excepción tolerada.

### Fase 2 — Tareas

`/speckit-tasks`. Orden previsible: catálogo y manifest → dominio y sus pruebas → Room v5 → claves
nuevas y línea base → ciclo, notificador, visibilidad → WorkManager → navegación, shell, badge,
Snackbar → pantalla Avisos → formulario y selector → permiso → deep link y marcar leída → vista
previa → documentación y cuatro puertas.

### Fase 3 — Implementación

`/speckit-implement`, previo `/speckit-analyze`.

---

## Riesgos, con su salida

| Riesgo | Salida |
|---|---|
| **Retirar `WorkManagerInitializer` y que algo llame a `WorkManager.getInstance` antes de Koin** | El único acceso está dentro de los métodos del planificador. En Robolectric, `WorkManagerTestInitHelper` o el fake. Y `KoinModulesTest` resuelve el planificador sin llamarlo |
| **`ProcessLifecycleOwner` desde el hilo del Worker** | Solo se lee `currentState`, sin observadores. `lifecycle-process` se inicializa con su propio `Initializer`, que se conserva con `tools:node="merge"` |
| **El aviso interno se pierde con el detalle encima** | Estado pendiente y no evento (D-416): se muestra al volver al shell |
| **La primera sincronización correcta tras semanas sin abrir trae publicaciones «nuevas» que ya son viejas** | Aceptado y previsto en la especificación (§23 del documento funcional). La línea base solo cubre la primera vez. La interfaz no promete histórico |
| **Cambiar el constructor de `HomeViewModel` rompe tres pruebas** | `HomeViewModelTest`, `BulletinFlowIntegrationTest`, `SavedFlowIntegrationTest` con fakes nuevos en `test/fake` |
| **`MissingPermission` en `notify` (lint)** | Guardia `areNotificationsEnabled()` antes de publicar, que además cumple FR-062 |
| **El icono pequeño sale como una mancha** | Vector monocromo dedicado, y `viewBox` comprobado uno a uno (D-418, D-435) |
| **Tocar Avisos y teclear en el formulario en una prueba instrumentada es una carrera** | Afirmar `TAG_ALERT_FORM_SCREEN` mostrado **antes** de interactuar, como aprendió la 006 |
| **La tanda instrumentada crece** | Seis clases nuevas a ~46 s cada una; el deep link con `ActivityScenario` solo en dos pruebas |
| **`GROUP_CONCAT` desordena los nombres** | Aceptable para «Coincide con A y B». Si molesta, subconsulta ordenada |

---

## Complexity Tracking

> Solo se rellena si la puerta constitucional encuentra violaciones que justificar.

| Violación | Por qué hace falta | Alternativa más simple rechazada porque |
|---|---|---|
| **Primera sentencia `DELETE`** (`AlertRuleDao.delete`), contra la letra de CLAUDE.md | Eliminar un aviso es una acción de la persona con confirmación; conservar filas muertas no es «eliminar» | Borrado lógico con `deleted_at`: mantenía la letra a costa de un `WHERE` en cada consulta, de filas que nunca desaparecen y de una palabra en la interfaz que no significaría lo que dice. Decidido por el propietario; la guía se reescribe como «nunca se borra una publicación» |
| **Cuatro dependencias nuevas** | No hay trabajo en segundo plano ni forma de saber si el proceso está en pantalla sin ellas | Alarmas exactas o servicio en primer plano: prohibidos por la especificación. Bandera de visibilidad publicada por el shell: falla con el detalle abierto (D-415) |
