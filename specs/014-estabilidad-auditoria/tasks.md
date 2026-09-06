---

description: "Task list for feature 014 — Estabilidad tras la auditoría"
---

# Tasks: Estabilidad tras la auditoría — lo prometido, cumplido también cuando algo falla

**Input**: Design documents from `/specs/014-estabilidad-auditoria/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/internal-contracts.md](./contracts/internal-contracts.md),
[quickstart.md](./quickstart.md)

**Tests**: **obligatorios y primero**. El principio V de la constitución es no negociable, y FR-040 de esta
feature exige que cada corrección lleve una prueba que **falle antes del arreglo**: en cada historia las
tareas de prueba van delante de las de código, se ejecutan, se ven en rojo, y solo entonces se toca el
producto. Ninguna tarea se da por terminada sin su prueba en verde; prohibido `@Ignore`, comentar o
borrar una prueba para que pase la build. Esta feature **no añade pruebas instrumentadas**: no cambia
ninguna pantalla.

**Organization**: por historia de usuario, en el orden que fija `plan.md` (US1+US2 → US3 → US4 → US5).
Las cinco son independientes; US1 y US2 comparten dos ficheros y van seguidas.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ir en paralelo (fichero distinto, sin dependencias pendientes)
- **[Story]**: US1 … US5, la historia de `spec.md` a la que sirve
- Toda tarea lleva la ruta exacta del fichero

## Path Conventions

Aplicación Android de módulo único. Producto en `app/src/main/java/com/jrblanco/boccantabria/`
(abreviado `MAIN/`), pruebas unitarias en `app/src/test/java/com/jrblanco/boccantabria/` (abreviado
`TEST/`), esquemas exportados en `app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/`.

---

## Phase 1: Setup

**Purpose**: partir de una línea base conocida, para que cualquier rojo posterior sea de esta feature.

- [ ] T001 Confirmar que la rama activa es `014-estabilidad-auditoria` y que parte de `main` con la feature 013 integrada (`ee88d24`), con `git branch --show-current` y `git log --oneline -1`
- [ ] T002 Confirmar que `.gitignore` deja fuera todo `docs/auditoria/` salvo los `.md` (`git status --short --untracked-files=all docs/` debe listar solo `00-mapa.md`, `01-hallazgos.md` y `PROGRESO.md`) y que `git check-ignore docs/auditoria/DiagnosticoRed.java` responde
- [ ] T003 Línea base de pruebas: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest` en verde (1.193 pruebas en `main`) antes de tocar nada; anotar el recuento para compararlo en el cierre

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: ninguno.

**Esta fase está vacía a propósito.** Esta feature no cambia ningún constructor, ningún módulo de Koin ni
ninguna dependencia (`plan.md`, Constitution Check IV). Los tres dobles de prueba nuevos
(`TlsMockWebServer`, `FailingOnceAlertMatchDao`, `FlakyFlow`) sirven cada uno a una sola historia y se
crean dentro de ella. La migración de base de datos pertenece a US3 y no bloquea a las demás.

**Checkpoint**: con T001-T003 hechas, cualquier historia puede empezar.

---

## Phase 3: User Story 1 — Una copia local dañada no cierra la aplicación (Priority: P1) 🎯 MVP

**Goal**: que un lateral de verificación vacío, truncado o malformado deje de cerrar la aplicación; que la
copia se repare sola; que el incidente conste sin datos de la publicación (STAB-001).

**Independent Test**: dañar a mano el lateral de un documento guardado y abrir la publicación
(`quickstart.md` §4.1). En automático: `DocumentRepositoryImplTest` y `FileDocumentCacheTest`.

**Requisitos que cierra**: FR-001 a FR-006.

### Pruebas (primero; deben fallar)

- [ ] T004 [US1] Añadir a `TEST/data/source/local/FileDocumentCacheTest.kt` las cuatro pruebas de `contracts/internal-contracts.md` §6: `a sidecar that is present but invalid reads back as the unknown checksum instead of throwing` (casos `""`, `"abc"`, `"A".repeat(64)`, `"a".repeat(63)`, escribiendo el lateral a mano en `File(cache.fileFor(k).parentFile, cache.fileFor(k).nameWithoutExtension + ".sha256")` tras un `put`), `storing leaves no sidecar temporary behind`, `a document that cannot be moved into place leaves no sidecar either` (temporal inexistente → `check` lanza → sin `.sha256`), `a stale sidecar temporary is ignored and replaced by put`. Ejecutar: la primera cae con `IllegalArgumentException`
- [ ] T005 [P] [US1] Añadir a `TEST/domain/model/OfficialDocumentTest.kt` la prueba `isValidChecksum agrees with the constructor` (acepta `"a".repeat(64)` y `UNKNOWN_CHECKSUM`; rechaza vacío, 63, mayúsculas). No compila hasta T007: eso es el rojo
- [ ] T006 [P] [US1] Añadir a `TEST/data/repository/DocumentRepositoryImplTest.kt` una `DelegatingCache(real: DocumentCache, var failGet = false, var failPut = false, var failDiscard = false) : DocumentCache` privada del fichero, cambiar la clase a `RecordingCrashReporter`, y añadir `a stored document whose checksum sidecar was truncated opens without downloading again` (reproducción exacta de la auditoría: `put` real, truncar el `.sha256`, segundo `ensureLocalCopy` → `Success`, `downloader.calls == 1`, y el log contiene `document: checksum sidecar unreadable`) y `a cache that cannot be read is reported and repaired by downloading again` (`failGet` → `Success`, `downloader.calls == 1`, un no fatal, mensaje `document: cache read failed: IllegalStateException`). Ejecutar: la primera cae con la excepción escapando

### Implementación

- [ ] T007 [US1] En `MAIN/domain/model/OfficialDocument.kt` hacer público el `companion object` con `const val UNKNOWN_CHECKSUM` (sesenta y cuatro ceros) y `fun isValidChecksum(value: String): Boolean = CHECKSUM.matches(value)`; el `init` pasa a usar `isValidChecksum` (D-602)
- [ ] T008 [US1] En `MAIN/data/source/local/FileDocumentCache.kt`: `readChecksum()` hace `trim()` y devuelve `null` si `!OfficialDocument.isValidChecksum(it)`; el `EMPTY_CHECKSUM` privado se sustituye por `OfficialDocument.UNKNOWN_CHECKSUM`; `writeChecksum()` escribe en `<digest>.sha256.part` y renombra; en `put()` el lateral se escribe **antes** del `renameTo` del PDF y se borra si ese rename falla; `evict()` retira también `.sha256.part` huérfanos; el KDoc de la clase explica el orden (D-601, D-603)
- [ ] T009 [US1] En `MAIN/data/source/local/DocumentCache.kt` ampliar el KDoc de `get`: «Never throws for a malformed entry: a sidecar that exists but is not a valid checksum reads as `UNKNOWN_CHECKSUM`»
- [ ] T010 [US1] En `MAIN/data/repository/DocumentRepositoryImpl.kt` meter `cache.get(key)` en un `try` que repropaga `CancellationException` y, para cualquier otro `Throwable`, hace `crashReporter.log("document: cache read failed: ${it.javaClass.simpleName}")` + `recordNonFatal` y sigue como ausente; si la copia devuelta lleva `OfficialDocument.UNKNOWN_CHECKSUM`, registrar `document: checksum sidecar unreadable, served without checksum` (D-601, D-602). Nunca la clave ni la URL en el mensaje
- [ ] T011 [US1] Ejecutar `./gradlew :app:testDebugUnitTest --tests "*FileDocumentCacheTest*" --tests "*OfficialDocumentTest*" --tests "*DocumentRepositoryImplTest*"` en verde

**Checkpoint**: US1 completa y demostrable sola. El cierre en bucle de la auditoría ya no existe.

---

## Phase 4: User Story 2 — Un fallo al guardar el documento se ve y se puede reintentar (Priority: P1)

**Goal**: que todo camino de error de la descarga publique un estado terminal con reintento; que la
limpieza nunca cuelgue a quien espera; que cancelar deje `Absent`; que quien espera no herede la
cancelación del dueño (STAB-002).

**Independent Test**: hacer que `cache/documents` no admita escrituras y abrir la pestaña Documento
(`quickstart.md` §4.2). En automático: `DocumentRepositoryImplTest`.

**Requisitos que cierra**: FR-007 a FR-012.

### Pruebas (primero; deben fallar)

- [ ] T012 [US2] En `TEST/data/repository/DocumentRepositoryImplTest.kt`: reescribir `an exploding downloader is a failure, not a crash` como `an exploding downloader is observed as failed, not left downloading` (Turbine sobre `observeDocument`, `expectMostRecentItem() == Failed(Unknown)`, log `document: fetch threw: IllegalStateException: boom`) y añadir `a cache that cannot store the document fails visibly and can be retried` (`failPut` una vez → `Failure` + `Failed` + `!leftoversExist()`; segunda llamada `Success`), `cleanup that fails does not hide the failure nor hang the waiters` (`failDiscard` + descargador que explota; dueño y *waiter* concurrentes reciben `Failure`; tercera llamada `Success`), `a cancelled download is observed as absent, not downloading` (descargador con `gate`; cancelar el dueño; estado `Absent`), `a waiter whose owner is cancelled takes over the download` (dueño y *waiter*; cancelar solo el dueño; el *waiter* obtiene `Success` y `downloader.calls == 2`). Ejecutar: las cinco en rojo (`Downloading`, cuelgue, `CancellationException` en el *waiter*)

### Implementación

- [ ] T013 [US2] En `MAIN/data/repository/DocumentRepositoryImpl.kt`: (a) el `catch (unexpected: Throwable)` registra `document: fetch threw: <Clase>: <mensaje>`, `recordNonFatal`, `publish(key, Failed(Unknown))` y devuelve `Failure(Unknown)`; (b) extraer `private suspend fun settle(key, pending, outcome: AppResult?, cancellation: CancellationException?)` que corre bajo `withContext(NonCancellable)`: `runCatching { cache.discardTemporary(key) }.onFailure { log("document: cleanup failed: <Clase>") }` cuando corresponde, `lock.withLock { if (inFlight[key] === pending) { inFlight.remove(key); if (cancellation != null && statuses.value[key] is Downloading) publish(key, Absent) } }`, y por último `pending.complete(outcome)` o `pending.cancel(cancellation)`; (c) convertir `ensureLocalCopy` en un bucle `while (true)`: quien no es dueño hace `try { return pending.await() } catch (c: CancellationException) { currentCoroutineContext().ensureActive() }` y vuelve al `lock` (D-604, D-605, D-606). Actualizar el KDoc de la clase con las dos promesas nuevas
- [ ] T014 [P] [US2] En `MAIN/domain/model/DocumentStatus.kt` ampliar el KDoc de `Absent` («…or its download was cancelled») y en `MAIN/domain/repository/DocumentRepository.kt` añadir al KDoc los dos invariantes de `contracts/internal-contracts.md` §1.5
- [ ] T015 [US2] Ejecutar `./gradlew :app:testDebugUnitTest --tests "*DocumentRepositoryImplTest*" --tests "*PdfViewerViewModelTest*" --tests "*PublicationDetailViewModelTest*" --tests "*DocumentFlowIntegrationTest*"` en verde

**Checkpoint**: US1+US2 completas. Las dos reproducciones de `diagnostico-documentos.log` son ahora
pruebas verdes.

---

## Phase 5: User Story 3 — Un aviso encontrado no se pierde: se recupera una sola vez (Priority: P1)

**Goal**: que el trabajo pendiente de los avisos viva en el almacén y se recupere en el siguiente ciclo,
exactamente una vez, sin volverse retroactivo y sin tocar la línea base (STAB-003).

**Independent Test**: `RunSyncCycleUseCaseTest` y `AlertFlowIntegrationTest` (Room real) con un
registro que falla una vez y un segundo ciclo con fuentes sin cambios. A mano: la línea `cycle: … pending
from earlier …` en `logcat -s BOC:V` (`quickstart.md` §4.3).

**Requisitos que cierra**: FR-013 a FR-025.

### Pruebas (primero; deben fallar o no compilar)

- [ ] T016 [P] [US3] Crear `TEST/domain/model/AlertCandidateTest.kt`: visible con `activeSince == storedAt` y `< storedAt`; no visible con `activeSince > storedAt`; `storedAt <= 0` rechazado. Konsist lo exige (regla novena)
- [ ] T017 [P] [US3] Añadir a `TEST/data/source/local/PublicationDaoTest.kt`: `a row the source brings for the first time is pending evaluation, and a correction does not re-flag it` (insertar con `pendingAlertEvaluation = true`, marcar evaluada, `upsertAll` de la misma clave con título corregido → sigue a `false`; hermana de la guarda de `saved_at`), ampliar `…a blob id collision is not one of them` con «pendiente == `["boc:2"]`», y `marking evaluated clears only the given keys and touches nothing else` (devuelve 1; `saved_at`, `search_text`, `first_seen_at` intactos)
- [ ] T018 [P] [US3] Añadir a `TEST/data/source/local/BocDatabaseMigrationTest.kt`: `VERSION_FIVE_STATEMENTS` transcritas literalmente de `5.json` (cinco tablas y todos sus índices); `a version 5 database keeps its alerts and its publications are not pending`; `a version 1 database can reach version 6 in one go`; `a publication stored right after the upgrade is pending and can be cleared`
- [ ] T019 [P] [US3] Añadir a `TEST/data/repository/AlertRepositoryImplTest.kt`: `a batch that cannot be recorded whole records nothing and reports the failure` (900 candidatos válidos + 1 con `rule_id` inexistente → `Failure`, `database.alertMatchDao().count() == 0`; hoy: 900 filas y `emptyList()`); adaptar `record matches returns only what was really new` para desenvolver `Success`
- [ ] T020 [P] [US3] En `TEST/data/repository/PublicationRepositoryImplTest.kt`: añadir `the baseline stores nothing as pending`, `a later synchronisation leaves exactly the inserted keys pending, stamped with when they were stored` (`storedAt == now`), `the backfill leaves the pending flag alone`; ampliar `the same announcement reaching two sources is stored once` con «pendiente una vez»; retirar la prueba de `byKeys` (línea ~185)
- [ ] T021 [US3] Ampliar los dobles: `TEST/fake/FakePublicationRepository.kt` (`var now`, `val pendingKeys: MutableMap<String, Long>`, `seedPending(publication, storedAt)`, `var pendingReads`, `var failPendingRead`, `var failMarkEvaluated`; `refresh()` sella `refreshResult.newKeys` con `now` salvo `isBaseline`; retirar `byKeys`/`keysAsked`) y `TEST/fake/FakeAlertRepository.kt` (`enabledRules()` y `recordMatches()` devuelven `AppResult`; `var failReads`, `var failRecordMatches`); crear `TEST/fake/FailingOnceAlertMatchDao.kt` (`class FailingOnceAlertMatchDao(private val delegate: AlertMatchDao, var failuresLeft: Int = 1) : AlertMatchDao by delegate`, `insert` lanza mientras `failuresLeft > 0`)
- [ ] T022 [US3] En `TEST/domain/usecase/RunSyncCycleUseCaseTest.kt` añadir las seis de `contracts/internal-contracts.md` §6 (`what could not be recorded is kept pending and delivered exactly once by the next cycle` con tres ciclos, `a rule created between two cycles does not fire for what an earlier cycle left pending` **avanzando `publications.now`**, `a leftover is evaluated even when the refresh is skipped`, `with no rules the new publications are cleared, so a rule created later does not see them`, `a failure to clear the flag does not block delivery and does not deliver twice`, `when the rules cannot be read the refresh still runs and evaluation waits`) y renombrar las cuatro que cambian de significado (`a synchronisation without changes evaluates nothing` → lee lo pendiente una vez y no registra; `a skipped refresh evaluates nothing` → evalúa solo lo que quedó pendiente; `only the new keys are read, never the whole store` → lo pendiente se lee y se marca; en la de línea base, `keysAsked.isEmpty()` → `pendingReads == 0`). No compila hasta T024: rojo
- [ ] T023 [US3] Añadir a `TEST/integration/AlertFlowIntegrationTest.kt` dos pruebas con `FailingOnceAlertMatchDao` inyectado en `AlertRepositoryImpl`: `a match the store could not record is delivered by the next cycle, once` (línea base; ciclo con fallo → `NONE` y `publicationDao().pendingAlertEvaluation()` contiene la clave; ciclo con feed `NotModified` → una notificación, badge 1, nada pendiente; tercer ciclo → sigue 1) y `a rule created after a leftover was stored does not fire for it` (**avanzar `now`** antes de crear la segunda regla; la notificación nombra solo la primera; `alertMatchDao().count() == 1`)

### Implementación

- [ ] T024 [US3] Dominio: crear `MAIN/domain/model/AlertCandidate.kt` según `contracts/internal-contracts.md` §1.3; en `MAIN/domain/repository/PublicationRepository.kt` retirar `byKeys` y añadir `pendingAlertCandidates(): AppResult<List<AlertCandidate>>` y `markAlertsEvaluated(keys: Set<String>): AppResult<Unit>` con los KDoc de §1.1; en `MAIN/domain/repository/AlertRepository.kt` cambiar `enabledRules()` y `recordMatches()` a `AppResult` y corregir el KDoc de la interfaz; en `MAIN/domain/model/SyncSummary.kt` corregir el KDoc de `newKeys` (D-609, D-611, D-613)
- [ ] T025 [US3] En `MAIN/data/source/local/PublicationEntity.kt` añadir `@ColumnInfo(name = "pending_alert_evaluation", defaultValue = "0") val pendingAlertEvaluation: Boolean = false`, `Index(value = ["pending_alert_evaluation"])` y el tercer parámetro `pendingAlertEvaluation: Boolean = false` en `toEntity(...)`; en `MAIN/data/source/local/BocDatabase.kt` `version = 6`, `AutoMigration(from = 5, to = 6)` y el párrafo de KDoc de la versión 6 (D-607)
- [ ] T026 [US3] En `MAIN/data/source/local/PublicationDao.kt` añadir `pendingAlertEvaluation()` y `markAlertsEvaluated(keys)` con el SQL de `contracts/internal-contracts.md` §3.1, retirar `byKeys`, y ampliar el KDoc de `updateColumns` diciendo que `pending_alert_evaluation` queda fuera y por qué. **No** tocar la sentencia `UPDATE`
- [ ] T027 [US3] En `MAIN/data/repository/PublicationRepositoryImpl.kt`: `syncFeed(definition, isBaseline)` y `toEntity(now, searchTextOf(...), pendingAlertEvaluation = !isBaseline)` en la rama `Fetched`; `pendingAlertCandidates()` (`publicationDao.pendingAlertEvaluation().map { AlertCandidate(it.toDomain(), storedAt = it.firstSeenAt) }` envuelto en `Success`/`Failure(Unknown)` + `recordNonFatal`, cancelación repropagada); `markAlertsEvaluated(keys)` troceado a `SQLITE_VARIABLE_LIMIT`; retirar `byKeys` (D-607, D-608)
- [ ] T028 [P] [US3] En `MAIN/data/repository/AlertRepositoryImpl.kt`: `enabledRules()` → `write { ruleDao.enabledRules().map { it.toDomain() } }`; `recordMatches()` → vacío = `Success(emptyList())`, si no `write { val ids = matchDao.insert(distinct.map { … }); distinct.filterIndexed { i, _ -> ids[i] != IGNORED_ROW_ID } }` en **una** llamada; analítica solo en `Success` con lista no vacía; retirar la constante `SQLITE_VARIABLE_LIMIT` de esta clase; KDoc: un fallo es `Failure`, no «nada nuevo» (D-611)
- [ ] T029 [US3] Exportar el esquema, con el módulo ya compilando (tras T027 y T028): `./gradlew :app:kspDebugKotlin`; comprobar que existe `app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/6.json` con la columna (`notNull: true`, `defaultValue: "0"`) y su índice; `git add` del fichero (`quickstart.md` §2). Sin él, T018 lanza al abrir; antes de T027/T028 la compilación fallaría por `byKeys`
- [ ] T030 [US3] En `MAIN/domain/usecase/RunSyncCycleUseCase.kt` reescribir `evaluate()` según `contracts/internal-contracts.md` §1.6 (lee pendientes; `isVisibleTo && matchRule`; registra solo si hay coincidencias; marca exactamente las claves leídas, también con cero; si el marcado falla, registra y **entrega igualmente**; si el registro falla, no marca y `NONE`); en `invoke()`, `enabledRules()` es `AppResult` y su `Failure` registra `cycle: rules unreadable, evaluation deferred` y deja correr el refresh; las líneas de registro de §4 con la cola `match(es) on … delivery=` intacta; reescribir el KDoc de la clase: el orden garantiza lo nuevo, `isVisibleTo` lo recuperado (D-609, D-610, D-612)
- [ ] T031 [US3] Barrer los demás consumidores: `grep -rn "byKeys\|recordMatches\|enabledRules" app/src` y ajustar lo que quede (`TEST/fake/SyncCycles.kt`, `TEST/data/background/AlertSyncWorkerTest.kt`, las **pruebas** de `TEST/ui/alerts/**` si desenvuelven listas; nunca producto en `ui/`); confirmar que `AlertFormViewModel` sigue usando `newest()` sin cambios
- [ ] T032 [US3] Ejecutar `./gradlew :app:testDebugUnitTest --tests "*AlertCandidateTest*" --tests "*PublicationDaoTest*" --tests "*BocDatabaseMigrationTest*" --tests "*AlertRepositoryImplTest*" --tests "*PublicationRepositoryImplTest*" --tests "*RunSyncCycleUseCaseTest*" --tests "*AlertFlowIntegrationTest*" --tests "*AlertSyncWorkerTest*" --tests "*KoinModulesTest*" --tests "*ArchitectureRulesTest*"` en verde

**Checkpoint**: US3 completa. La reproducción de `diagnostico-avisos.log` («entregas: 0» con un intento)
es ahora una prueba verde con Room real.

---

## Phase 6: User Story 4 — Las listas se recuperan tras un fallo de lectura (Priority: P2)

**Goal**: que los nueve Flows de lectura sobrevivan a un fallo transitorio con tres reintentos, sigan
reflejando cambios posteriores y se detengan ante un fallo permanente (STAB-004).

**Independent Test**: `ReadRecoveryTest` y las reescrituras de `SavedPublicationRepositoryImplTest` y
`SearchRepositoryImplTest`; el caso de la campana en `AlertRepositoryImplTest`.

**Requisitos que cierra**: FR-026 a FR-032.

### Pruebas (primero; deben fallar o no compilar)

- [ ] T033 [US4] Crear `TEST/data/repository/ReadRecoveryTest.kt` con un `FlakyFlow<T>(failures: Int, value: T)` privado que cuenta `subscriptions` **dentro** del `flow { }`, y las siete pruebas de `research.md` D-622 / `plan.md`: `a transient failure emits the fallback and recovers after the first delay` (fallback; `expectNoEvents`; `advanceTimeBy(999)` nada; `advanceTimeBy(1)` valor; `subscriptions == 2`; un no fatal), `a success resets the budget` (cinco alternancias fallo/éxito, siempre 1 s), `a permanent failure stops after three retries and completes with the fallback` (4 fallbacks, `subscriptions == 4`, `awaitComplete()`, último log «gave up»), `an upstream cancellation is rethrown, not reported and not retried`, `an exception thrown by the collector is transparent` (`subscriptions == 1`, sin no fatales), `cancelling during a wait stops the retries`, `the log names the flow and the exception class and nothing else`. No compila hasta T038
- [ ] T034 [P] [US4] En `TEST/data/repository/SavedPublicationRepositoryImplTest.kt` reescribir `a read failure emits empty instead of terminating the flow` como `a read failure emits empty and keeps observing`: MockK devolviendo un `flow {}` con estado (primera colección lanza, segunda emite la lista); Turbine ve vacío, `advanceTimeBy(1_000)`, la lista; cambiar el `repository()` a `RecordingCrashReporter`
- [ ] T035 [P] [US4] En `TEST/data/repository/SearchRepositoryImplTest.kt` cambiar `RecordingSearchDao.failWith: Throwable?` por `failFor: Int` + `subscriptions`, reescribir `a read failure is recorded and emits empty instead of terminating the flow` y `a failure reading the issuers is an empty list, not a broken sheet` para afirmar recuperación, y añadir `a permanent failure gives up after three retries` (`subscriptions == 4`)
- [ ] T036 [P] [US4] Añadir a `TEST/data/repository/AlertRepositoryImplTest.kt` `the unread count survives a read failure` (MockK sobre `AlertMatchDao.observeUnreadCount`, con estado; el `repository()` gana un parámetro `matchDao`): es el caso de la campana de `MainShellViewModel`
- [ ] T037 [P] [US4] Añadir a `TEST/data/repository/PublicationRepositoryImplTest.kt` `observing the bulletin survives a read failure` (MockK sobre `PublicationDao.observeTodaysBulletin`; el `repository()` gana `publicationDao`)

### Implementación

- [ ] T038 [US4] Crear `MAIN/data/repository/ReadRecovery.kt` con `internal val READ_RETRY_DELAYS` y `internal fun <T> Flow<T>.recoverReads(...)` exactamente como `contracts/internal-contracts.md` §2.1: `flow { var consecutive = 0; emitAll(upstream.onEach { consecutive = 0 }.retryWhen { cause, _ -> if (cause is CancellationException) throw cause; recordNonFatal; emit(fallback); presupuesto; log; delay; true }.catch { if (it is CancellationException) throw it }) }`, con el KDoc que dice **por qué va el último** (D-614, D-615)
- [ ] T039 [US4] Sustituir los nueve `.catch` por `.recoverReads(...)` **después** de `.flowOn(dispatchers.io)` con los `fallback` y `name` de `data-model.md` §5: `MAIN/data/repository/PublicationRepositoryImpl.kt` (`observePublications`, `observePublication`; `observeHeader` no se envuelve), `SavedPublicationRepositoryImpl.kt` (dos; borrar `emitEmptyAfterReporting`), `SearchRepositoryImpl.kt` (dos; borrar `emitEmptyAfterRecording`), `AlertRepositoryImpl.kt` (tres, `observeUnreadCount` con `0`; borrar `emitEmptyAfterReporting`); corregir los comentarios «must not kill the flow» de los cuatro y los KDoc de `MAIN/domain/repository/AlertRepository.kt` y `PublicationRepository.kt` («recovers … with bounded retries»)
- [ ] T040 [US4] Comprobar `grep -rn "\.catch {" app/src/main/java/com/jrblanco/boccantabria/data/repository/` → solo `ReadRecovery.kt`; ejecutar `./gradlew :app:testDebugUnitTest --tests "*ReadRecoveryTest*" --tests "*SavedPublicationRepositoryImplTest*" --tests "*SearchRepositoryImplTest*" --tests "*AlertRepositoryImplTest*" --tests "*PublicationRepositoryImplTest*" --tests "*HomeViewModelTest*" --tests "*SavedViewModelTest*" --tests "*AlertsViewModelTest*" --tests "*MainShellViewModelTest*" --tests "*SearchViewModelTest*"` en verde

**Checkpoint**: US4 completa. Una lectura que falla una vez ya no deja una pantalla vacía para siempre.

---

## Phase 7: User Story 5 — Salir de una pantalla detiene la red de verdad (Priority: P2)

**Goal**: que cancelar una corrutina cancele la llamada de OkHttp y libere el socket en el orden de un
segundo, en los ocho sitios, sin cambiar quién cancela ni cuándo (PERF-002).

**Independent Test**: `CancellableCallTest` con un interceptor bloqueante (`Call.isCanceled`, `join` en
< 2 s); las pruebas de cancelación de los cinco data sources.

**Requisitos que cierra**: FR-033 a FR-039.

### Pruebas (primero; deben fallar)

- [ ] T041 [US5] Crear `TEST/fake/TlsMockWebServer.kt`: regla `ExternalResource` con `server: MockWebServer` en TLS (certificado autofirmado con `HeldCertificate`/`HandshakeCertificates`, como el bloque que hoy repiten cinco tests), `client: OkHttpClient` que confía y `retryOnConnectionFailure(false)`, `fun url(path: String): String`
- [ ] T042 [US5] Crear `TEST/data/source/remote/CancellableCallTest.kt` (sin sockets: interceptores con latches como `docs/auditoria/DiagnosticoRed.java`, `runBlocking` + `Dispatchers.IO`) con las cinco pruebas de `contracts/internal-contracts.md` §6: `cancelling the coroutine cancels the call and returns before the response` (`join` en < 2 s, `isCanceled`, luego soltar el latch), `a failure before the response reaches the caller as its IOException`, `an exception thrown while consuming reaches the caller and never OkHttp's thread` (instalar y restaurar un `Thread.setDefaultUncaughtExceptionHandler` que registra), `the response is closed after consuming`, `a response arriving after cancellation is discarded quietly`. No compila hasta T045
- [ ] T043 [P] [US5] Crear `TEST/data/source/remote/OkHttpGeminiDocumentUploaderTest.kt` con `TlsMockWebServer` y `TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))`: `a document is announced, sent and polled until active` (tres respuestas; `x-goog-api-key`; cabeceras del comando; offset 0; cuerpo de la segunda petición == bytes del fichero), `a start without an upload url is malformed`, `polling stops at the ceiling` (`MAX_POLLS` × PROCESSING), `delete issues a DELETE and never throws on failure`, `cancelling mid-upload is a cancellation and the call is cancelled` (`bodyDelay` en la segunda respuesta; dispatchers reales; interceptor que captura `chain.call()`), `the log never carries the credential`. Las cinco primeras pasan hoy; la de cancelación es el rojo
- [ ] T044 [P] [US5] Pruebas de cancelación en los cuatro tests remotos existentes, migrándolos a `TlsMockWebServer`: en `TEST/data/source/remote/OkHttpGeminiChatDataSourceTest.kt` (~365) y `OkHttpGeminiSummaryDataSourceTest.kt` (~385) afirmar además `job.join()` en < 5 s y `Call.isCanceled` vía interceptor, y en el del resumen mover `server.close()` **después** de la aserción; añadir a `OkHttpDocumentDownloaderTest.kt` `cancelling mid-body is a cancellation, the call is cancelled and no refusal is reported` (`bodyDelay`, dispatchers reales) y a `OkHttpPublicationRemoteDataSourceTest.kt` `cancelling mid-request is a cancellation, never a network failure nor a retry` (`server.requestCount == 1`). Ejecutar: las de prontitud y `isCanceled` en rojo

### Implementación

- [ ] T045 [US5] Crear `MAIN/data/source/remote/CancellableCall.kt` con `internal suspend fun <T> Call.await(consume: (Response) -> T): T` sobre `suspendCancellableCoroutine`: `cont.invokeOnCancellation { cancel() }`, `enqueue`, `onFailure → resumeWithException`, `onResponse → try { response.use(consume) } catch (t: Throwable) { cont.resumeWithException(t); return }; cont.resume(result)`; KDoc con la regla «nada escapa de `onResponse`» y su motivo (D-617, D-618)
- [ ] T046 [US5] En `MAIN/data/source/remote/OkHttpDocumentDownloader.kt`: `client.newCall(request(url)).await { response -> writeVerified(response, into) }`; en el `catch (IOException)`, `currentCoroutineContext().ensureActive()` como primera línea; **no** truncar en el `catch (CancellationException)`, y decir en el KDoc que los restos los borra el repositorio (D-620, D-621)
- [ ] T047 [P] [US5] En `MAIN/data/source/remote/OkHttpPublicationRemoteDataSource.kt`: `await` en `attempt()`, y fundir los dos `catch` en `catch (error: IOException) { currentCoroutineContext().ensureActive(); FeedFetchResult.Failed(if (error is SocketTimeoutException) TIMEOUT else NETWORK) }` (D-620)
- [ ] T048 [P] [US5] En `MAIN/data/source/remote/OkHttpGeminiSummaryDataSource.kt` y `OkHttpGeminiChatDataSource.kt`: `client.newCall(request).await { response -> … }` conservando los `ensureActive()` y los `catch` existentes
- [ ] T049 [P] [US5] En `MAIN/data/source/remote/OkHttpGeminiDocumentUploader.kt`: `await` en `beginUpload`, `sendBytes`, `fetch` (que pasan a `suspend`) y `delete`; `delete()` sustituye `runCatching` por `try { … } catch (c: CancellationException) { throw c } catch (t: Throwable) { log }`; en `MAIN/data/source/remote/AiDocumentUploader.kt` KDoc de `delete`: «Never throws, except `CancellationException`» (D-620)
- [ ] T050 [US5] Comprobar `grep -rn "\.execute()" app/src/main/java/com/jrblanco/boccantabria/data/source/remote/` → sin resultados; opcionalmente migrar `TEST/integration/DocumentFlowIntegrationTest.kt` a `TlsMockWebServer`; ejecutar `./gradlew :app:testDebugUnitTest --tests "*CancellableCallTest*" --tests "*OkHttp*Test*" --tests "*DocumentFlowIntegrationTest*" --tests "*AiDocumentPreparerTest*" --tests "*AiSummaryRepositoryImplTest*" --tests "*AiChatRepositoryImplTest*"` en verde

**Checkpoint**: US5 completa. `Call.isCanceled=false` tras cancelar ya no ocurre en ningún sitio.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T051 [P] Actualizar `CLAUDE.md` con las lecciones de `plan.md` Fase 3: BD en versión 6 y la columna `pending_alert_evaluation` **fuera** de `updateColumns` (nombrar la guarda de `PublicationDaoTest`); «nunca retroactivo» por orden **y** por `isVisibleTo`; el ciclo evalúa lo pendiente del almacén y `recordMatches` es una sola inserción; el lateral validado con la regla del modelo y escrito antes que el PDF, `settle()` bajo `NonCancellable`, `Absent` tras cancelar, el *waiter* retoma; `recoverReads` siempre después de `flowOn` y el prefijo `reads:`; `Call.await`, «nada escapa de `onResponse`», `ensureActive()` en los cinco, la cola de `maxRequestsPerHost`; las trampas nuevas (`retryWhen` re-colecciona el mismo objeto, el reloj congelado hace inerte el filtro, `.first()` no ve la terminación); las líneas nuevas `document:`, `reads:` y `cycle:` en la sección de registro
- [ ] T052 [P] Añadir al final de `specs/012-avisos/research.md` una nota fechada: D-401 («las claves las transporta `newKeys` y el ciclo las lee con `byKeys`») y D-405 («sin comparar fechas») quedan **superadas** por D-607 y D-609 de la feature 014, con enlace a `specs/014-estabilidad-auditoria/research.md`
- [ ] T053 Comprobaciones de `quickstart.md` §5: registro sin datos personales; la columna nueva solo en sus dos consultas; un único `DELETE` (`AlertRuleDao`); ningún `.execute()` en `data/source/remote/`; ningún `.catch {` fuera de `ReadRecovery.kt`
- [ ] T054 Comprobar que nada visible cambió: `git diff --stat main -- app/src/main/java/com/jrblanco/boccantabria/ui app/src/main/res gradle/libs.versions.toml app/build.gradle.kts app/src/androidTest` debe salir **vacío**; y que `6.json` está en el índice (`git status --short app/schemas/`)
- [ ] T055 Puerta 1: `./gradlew :app:assembleDebug`
- [ ] T056 Puerta 2: `./gradlew :app:testDebugUnitTest`; anotar el recuento frente al de T003 (debe subir en unas 45)
- [ ] T057 Puerta 3: `adb shell settings put secure navigation_mode 0` y después `./gradlew :app:connectedDebugAndroidTest` con **un solo dispositivo** conectado o `ANDROID_SERIAL` fijado. Tarda unas dos horas; lanzarla en segundo plano. No hay pruebas instrumentadas nuevas: solo confirma que nada se rompió
- [ ] T058 Puerta 4: `./gradlew :app:lintDebug`; las incidencias deben ser las mismas 17 que en `main`
- [ ] T059 Recorrido manual de `quickstart.md` §4 en el emulador: el lateral dañado (§4.1, tres variantes), el almacenamiento que falla (§4.2), y las comprobaciones de registro de §4.3 con `adb logcat -s BOC:V`
- [ ] T060 Volver a ejecutar los diagnósticos de la auditoría que siguen compilando (`docs/auditoria/DiagnosticoDocumentos.java`, `DiagnosticoRed.java`, vía `python3 docs/auditoria/ejecutar-diagnosticos.py`) y confirmar que ya no imprimen «Excepción escapada…», «Downloading» tras fallo de `put` ni `Call.isCanceled=false`; anotar que `DiagnosticoAvisos.java` necesita ajuste por el cambio de contrato (no se versiona)
- [ ] T061 Escribir la sección «Cierre — <fecha>» al final de este `tasks.md` con la tabla de las cuatro puertas (comando, resultado, recuento), lo comprobado a mano según T059 y T060, y lo que no pudo comprobarse y por qué
- [ ] T062 Commit `feat(014): …` en la rama `014-estabilidad-auditoria` con `6.json`, el código, las pruebas, `CLAUDE.md`, la nota de la 012 y este `tasks.md` cerrado (decisión del propietario: el merge a `main` lo hace él)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias.
- **Foundational (fase 2)**: vacía a propósito. No bloquea nada.
- **Historias (fases 3-7)**: todas dependen solo de la fase 1. Entre ellas, **ninguna dependencia de
  código**; el orden US1+US2 → US3 → US4 → US5 es el recomendado por `plan.md` (de menos a más ficheros;
  US5 es el único que cambia el hilo de ejecución), no una obligación.
- **Polish (fase 8)**: depende de que estén hechas las historias que se quieran entregar.

### Dependencias entre historias

- **US2 depende de US1** solo en que tocan el mismo fichero (`DocumentRepositoryImpl.kt`) y
  `DelegatingCache` (T006) se reutiliza en T012. Se implementan seguidas.
- **US3, US4 y US5 son independientes** entre sí y de US1/US2. US4 toca `AlertRepositoryImpl.kt` y
  `PublicationRepositoryImpl.kt`, que US3 también toca: si van en paralelo, resolver esos dos ficheros a
  mano.
- Ninguna historia cambia un constructor: el grafo de Koin no es punto de conflicto.

### Dentro de cada historia

1. Pruebas (en rojo, ejecutadas) → 2. dominio → 3. datos → 4. barrido de consumidores → 5. pruebas en
verde. En US3 el esquema exportado (T029) va **después** de los dos repositorios (T027, T028), cuando el
módulo vuelve a compilar; la prueba de migración (T018) lo necesita.

### Parallel Opportunities

- Fase 3: T005 y T006 en paralelo con T004.
- Fase 5: T016-T020 en paralelo entre sí; T028 en paralelo con T027.
- Fase 6: T034-T037 en paralelo entre sí, tras T033.
- Fase 7: T043 y T044 en paralelo tras T041; T047-T049 en paralelo tras T045.
- Fase 8: T051 y T052 en paralelo; las puertas, en orden.

---

## Parallel Example: reparto entre tres

```text
Persona A: US1 → US2 → US3     (documento y avisos; DocumentRepositoryImpl y luego la migración)
Persona B: US4                 (ReadRecovery y los cuatro repositorios; coordinar dos ficheros con A)
Persona C: US5                 (CancellableCall, los cinco data sources, el test nuevo del uploader)
```

---

## Implementation Strategy

### MVP (US1+US2)

1. Fase 1 (T001-T003).
2. Fase 3 (T004-T011) y fase 4 (T012-T015).
3. **Parar y validar**: el único hallazgo de severidad alta de la auditoría —el cierre en bucle— ya no
   existe, y el estado del documento es coherente en todos los caminos.

### Entrega incremental

1. Fase 1 → línea base.
2. US1 → el lateral dañado no cierra la aplicación → demostrable (`quickstart.md` §4.1).
3. US2 → el fallo al guardar se ve y se reintenta → demostrable (§4.2). **Con estas dos, lo de
   severidad alta está cerrado.**
4. US3 → los avisos no se pierden → demostrable con Room real en `AlertFlowIntegrationTest`.
5. US4 → las listas se recuperan → demostrable en `ReadRecoveryTest`.
6. US5 → la red se cancela de verdad → demostrable en `CancellableCallTest`.
7. Fase 8 → documentación, las cuatro puertas, el recorrido manual y el cierre.

---

## Notes

- **La prueba va antes, y se ve fallar.** Es FR-040 y es lo que distingue una regresión de una prueba que
  siempre estuvo verde. Si una prueba nueva pasa antes del arreglo, o no reproduce el defecto o el defecto
  no era ese: parar y mirar.
- **Ni un cambio en `ui/`, ni en `gradle/`, ni en `res/`.** T054 lo comprueba. Si hace falta tocar algo
  ahí, o es un error de ejecución o la especificación se quedó corta.
- **`6.json` se versiona** (T029, T054). Un esquema exportado obsoleto solo falla en dispositivos
  actualizados.
- **Prohibido `@Ignore`.** Las pruebas que se renombran (T022) cambian porque cambia lo que describen, y
  `research.md` D-612 lo deja escrito; las que se reescriben (T034, T035) lo hacen porque `.first()` no
  podía ver la terminación del `Flow` (D-622).
- **Los relojes se avanzan** en las pruebas nuevas de US3 (T022, T023): con el reloj congelado el filtro
  `isVisibleTo` es inerte y la prueba no comprueba nada (D-609).
- Un commit por tarea o por grupo lógico no es necesario en esta feature: el propietario pidió dos
  commits, el de documentación (ya hecho antes de implementar) y el `feat(014)` de T062.
