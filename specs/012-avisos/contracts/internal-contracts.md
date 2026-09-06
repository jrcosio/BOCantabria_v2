# Contratos internos: Avisos

**Feature**: `012-avisos` | **Fecha**: 6 de septiembre de 2026

Esta aplicación no expone API pública. Los contratos que importan son las **costuras internas**: las
fronteras donde una capa habla con otra y donde un cambio se nota. Se documentan las que esta feature
crea o modifica.

---

## 1. La sincronización dice qué es nuevo

### 1.1 `PublicationDao.upsertAll` → `UpsertCounts`

```kotlin
data class UpsertCounts(val inserted: Int = 0, val updated: Int = 0, val insertedKeys: List<String> = emptyList())
```

| Obligación | Comprobado por |
|---|---|
| `insertedKeys.size == inserted` | `PublicationDaoTest` |
| Una fila rechazada por el índice único de `blob_id` **no** está en `insertedKeys` | `PublicationDaoTest` (nuevo) |
| Una fila ya existente por `external_key` **no** está en `insertedKeys` | `PublicationDaoTest` |
| Lote vacío → `UpsertCounts()` | existente |

### 1.2 `PublicationRepository.refresh()` → `AppResult<SyncSummary>`

| Obligación | Comprobado por |
|---|---|
| `isBaseline == true` si y solo si antes del ciclo `feedSyncStateDao.lastSuccessAt() == null` | `PublicationRepositoryImplTest` |
| Con `isBaseline`, `newKeys` está vacío aunque se insertaran filas | ídem |
| Sin línea base, `newKeys` es exactamente la unión de las claves insertadas por los feeds que respondieron | ídem |
| Un feed que falla no aporta claves | ídem |
| `SKIPPED` no cambia: `newKeys` vacío, `isBaseline` falso | `SyncSummaryTest` |
| `plus` une `newKeys` y hace OR de `isBaseline` | `SyncSummaryTest` |
| El evento `boc_sync` **no** lleva claves; gana `baseline` | `PublicationRepositoryImplTest` |

### 1.3 Nuevas lecturas de `PublicationRepository`

```kotlin
suspend fun byKeys(keys: Set<String>): List<Publication>       // troceado a 900; orden no garantizado
suspend fun newest(limit: Int): List<Publication>              // publication_date DESC, blob_id DESC, key DESC
suspend fun lastSuccessfulSyncAt(): Long?
```

---

## 2. El ciclo

### 2.1 `RunSyncCycleUseCase(force: Boolean): AppResult<SyncCycleOutcome>`

**Obligaciones**

| Obligación | Comprobado por |
|---|---|
| Las reglas activas se leen **antes** de `refresh()` | `RunSyncCycleUseCaseTest` (una regla creada durante el refresh no se evalúa) |
| `refresh()` fallido → `Failure`, cero evaluaciones, cero escrituras, cero entregas | ídem |
| `isBaseline` → `Success` con `delivery = NONE`, cero evaluaciones | ídem |
| `newKeys` vacío o sin reglas → `NONE`, y `byKeys` no se llama | ídem |
| Solo se evalúan las publicaciones de `newKeys` | ídem |
| Solo lo que `recordMatches` devuelve se entrega | ídem (segunda vez la misma publicación → nada) |
| Dos reglas sobre una publicación → **una** `AlertNotification` con dos nombres | ídem |
| `isAppVisible()` se consulta una vez; `true` → `InAppAlertStore.publish`, `false` → `AlertNotifier.post` | ídem |
| `releaseUnusedDocuments()` se llama siempre que `refresh()` no falle | ídem |
| `CancellationException` se repropaga | ídem |

**Cómo lo consume `HomeViewModel`**: sustituye `RefreshPublicationsUseCase` por este y deja de llamar a
`ReleaseUnusedDocumentsUseCase`. Lee `outcome.summary.allFailed` para su bandera `isOffline`.

**Cómo lo consume `AlertSyncWorker`**: `force = false`, registra `delivery`, devuelve `Result.success()`.

### 2.2 `MatchAlertRuleUseCase(rule, publication): Boolean`

| Caso (§24 del documento funcional) | Esperado |
|---|---|
| palabra simple en el título | `true` |
| «GANADERIA» vs «ganadería» | `true` |
| «subvención» vs «subvenciones» | `true` |
| frase «medio rural» vs «rural medio» | `false` |
| ANY con una de dos | `true`; ANY con ninguna | `false` |
| ALL con todas | `true`; ALL falta una | `false` |
| solo sección `2.2`, publicación `2.2` | `true`; publicación `2.1` | `false` |
| varias secciones | `true` si `classificationCode` está |
| padre `2` en la regla (tolerancia) y publicación `2.3` | `true` |
| solo organismo «Piélagos» vs `organizationPath = ["Ayuntamiento de Piélagos"]` | `true` |
| palabra + sección + organismo, falla uno | `false` |
| regla pausada | `false` |
| `rawCategories == null` | coincide por título |
| feed 4.3 con categorías permutadas | igual que sin permutar |

---

## 3. La entrega

### 3.1 `AlertNotifier.post(notifications)`

**Obligaciones de `AndroidAlertNotifier`** (Robolectric, `ShadowNotificationManager`):

| Obligación | Comprobado por |
|---|---|
| Crea el canal `boc_alerts` antes de publicar; llamarlo dos veces no falla | `AndroidAlertNotifierTest` |
| Con notificaciones desactivadas no publica **nada** y no lanza | ídem |
| Una notificación por publicación, `id = externalKey.hashCode()` | ídem |
| Con ≥ 2 publicaciones, además un resumen con `setGroupSummary(true)` y el mismo `group` | ídem |
| Con 1 publicación, sin resumen | ídem |
| Título «Nueva publicación: <regla>» con una regla; «Nueva publicación del BOC» con varias | ídem |
| `PendingIntent` distinto para dos publicaciones, igual para la misma | ídem |
| El `Intent` lleva `EXTRA_TARGET=publication` y `EXTRA_EXTERNAL_KEY`; el del resumen `EXTRA_TARGET=news` | ídem |
| `FLAG_IMMUTABLE`, `setAutoCancel(true)` | ídem |
| Nada del título ni de las reglas en `CrashReporter.log` | ídem |

### 3.2 `InAppAlertStore`

| Obligación | Comprobado por |
|---|---|
| `publish` sobre vacío → ese aviso | `InMemoryInAppAlertStoreTest` |
| `publish` sobre pendiente → recuentos sumados, `ruleName = null` | ídem |
| `consume` → `null` | ídem |

### 3.3 `MainShell` ↔ `MainShellViewModel`

| Obligación | Comprobado por |
|---|---|
| `unreadAlerts` es `observeUnreadCount()` | `MainShellViewModelTest` |
| Con `pendingAlert` y destino ≠ Avisos → Snackbar con «VER»; después `consume()` | `AlertSnackbarTest` (instrumentada) |
| Con `pendingAlert` y destino = Avisos → sin Snackbar; `consume()` | ídem |
| «VER» navega a `Route.Alerts(tab = "NEWS")` **sin** `restoreState` | ídem |
| `init` llama a `ReconcileBackgroundSyncUseCase` | `MainShellViewModelTest` |

---

## 4. Navegación

### 4.1 Rutas

```kotlin
Route.Alerts(tab: String? = null)                       // interior; tab = AlertsTab.name
Route.AlertForm(ruleId: String? = null, duplicateOf: String? = null)   // exterior
```

| Obligación | Comprobado por |
|---|---|
| `Alerts()` y `Alerts("NEWS")` son rutas distintas con el mismo destino | `RoutesTest` |
| `AlertForm()` = crear; `AlertForm(ruleId=x)` = editar; `AlertForm(duplicateOf=x)` = duplicar; ambos a la vez es inválido | `RoutesTest`, `AlertFormViewModelTest` |
| Los nombres de propiedad coinciden con las claves del `SavedStateHandle` (`ruleId`, `duplicateOf`, `tab`) | `AlertFormViewModelTest`, `AlertsViewModelTest` |

### 4.2 `BocBottomBar(current, onSelect, alertBadge, modifier)`

| Obligación | Comprobado por |
|---|---|
| Cuatro destinos, `ALERTS` el último, tag `bottom_alerts` | `BottomBarNavigationTest` (invertido) |
| `alertBadge == 0` → sin badge; `1..9` → el número; `> 9` → «9+» | `MainShellBadgeTest` |

### 4.3 `PendingNavigationStore`

| Obligación | Comprobado por |
|---|---|
| `MainActivity.onCreate` con `savedInstanceState != null` **no** rellena el almacén | `PendingNavigationTest` (unitario, sobre `Intent.toPendingNavigation()`) + `AlertDeepLinkTest` |
| `onNewIntent` rellena | `AlertDeepLinkTest` |
| Un `Intent` sin extras → `null` | `PendingNavigationTest` |
| `Publication` se consume en `composable<Route.Home>` tras la portada → `Detail(key)` | `AlertDeepLinkTest` |
| `AlertNews` se consume en `MainShell` → `Alerts("NEWS")` | `AlertDeepLinkTest` |
| Consumido una vez: recomponer no vuelve a navegar | `PendingNavigationStoreTest` |

### 4.4 `MainShell(navController, onOpenPublication, onOpenInfo, onOpenAlertForm, …)`

Nuevo parámetro `onOpenAlertForm: (Route.AlertForm) -> Unit`, cableado en `BOCantabriaNavHost` a
`navController.navigate(route)` del grafo exterior.

---

## 5. El permiso

### 5.1 `NotificationStatusRepository.status()`

| Estado de Android | Resultado |
|---|---|
| `areNotificationsEnabled() == true` | `GRANTED` |
| SDK ≥ 33, permiso no concedido | `NEEDS_REQUEST` |
| permiso concedido pero notificaciones/canal apagados | `DISABLED` |
| SDK < 33, notificaciones apagadas | `DISABLED` |

### 5.2 `AlertFormViewModel.onSave()` → `Saved(requestPermission)`

`requestPermission == (countRulesBefore == 0 && draft.isEnabled && status == NEEDS_REQUEST)`.
Comprobado por `AlertFormViewModelTest` en sus cuatro combinaciones.

---

## 6. La comprobación periódica

### 6.1 `BackgroundSyncScheduler`

| Obligación | Comprobado por |
|---|---|
| `ensureScheduled()` encola `boc_alert_sync` periódico 4 h / flex 30 min con `NetworkType.CONNECTED`; llamarlo dos veces deja **una** petición | `WorkManagerBackgroundSyncSchedulerTest` (`WorkManagerTestInitHelper`) |
| `cancel()` la retira | ídem |
| `WorkManager.getInstance` no se llama en el constructor | ídem (construir sin inicializar no lanza) |

### 6.2 Quién lo llama

| Caso de uso | Tras escribir |
|---|---|
| `SaveAlertRuleUseCase` | `countEnabled() > 0 → ensureScheduled()` si no `cancel()` |
| `SetAlertRuleEnabledUseCase` | ídem |
| `DeleteAlertRuleUseCase` | ídem |
| `ReconcileBackgroundSyncUseCase` | ídem, sin escribir |

### 6.3 `AlertSyncWorker.doWork()`

Siempre `Result.success()`. Con `RunSyncCycleUseCase` que falla, registra `cycle: refresh failed: …`.
Comprobado por `AlertSyncWorkerTest` con `TestListenableWorkerBuilder` y una fábrica de prueba.

---

## 7. Inyección de dependencias

Nuevas declaraciones y dónde:

| Módulo | Declaración |
|---|---|
| `coreModule` | `single<AppVisibilityProvider> { ProcessLifecycleAppVisibilityProvider() }` |
| `dataModule` | `AlertRuleDao`, `AlertMatchDao`, `AlertRepository`, `InAppAlertStore`, `AlertNotifier`, `BackgroundSyncScheduler`, `NotificationStatusDataSource`, `NotificationStatusRepository`, `workerOf(::AlertSyncWorker)` |
| `domainModule` | los diecinueve casos de uso de `data-model.md` §1.11 |
| `uiModule` | `viewModelOf(::AlertsViewModel)`, `viewModelOf(::AlertFormViewModel)`, `viewModelOf(::MainShellViewModel)`, `single { PendingNavigationStore() }` |

`KoinModulesTest`: todos los tipos anteriores en `CROSS_MODULE_TYPES` más `WorkerParameters::class`;
`koin.get<>()` de cada uno salvo `AlertFormViewModel` y `AlertSyncWorker` (argumentos de navegación /
parámetros del Worker, misma excepción documentada que Detail). `testGraphOverrides()` (androidTest)
añade los dos DAO, `AlertRepositoryImpl` sobre la base en memoria, `InMemoryInAppAlertStore`, y fakes
de `AlertNotifier`, `AppVisibilityProvider`, `BackgroundSyncScheduler` y `NotificationStatusDataSource`.

---

## 8. Lo que este contrato promete que NO cambia

- `PublicationRepository.observe*`, `isCacheStale()`, y la semántica de `refresh()` para quien ignore
  los dos campos nuevos.
- `SavedPublicationRepository`, `SearchRepository`, `AiSummaryRepository`, `AiChatRepository`.
- `Route.Home`, `Route.Search`, `Route.Saved`, `Route.Info`, `Route.Detail`, `Route.PdfViewer`,
  `Route.Ask`.
- El comportamiento de la portada: sigue siendo `startDestination` y sigue bloqueando por versión
  mínima y mantenimiento antes de cualquier navegación.
- Que ninguna publicación se borre.
