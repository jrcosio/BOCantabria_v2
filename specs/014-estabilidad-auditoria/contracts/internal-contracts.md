# Contratos internos — Feature 014

Las costuras que esta feature modifica. No hay ninguna frontera externa nueva: la red, el almacén y el
servicio de IA siguen siendo los mismos. Los contratos de esta feature son interfaces de dominio, dos
operadores nuevos de la capa de datos, sentencias de DAO, líneas de registro y dobles de prueba.

---

## 1. Dominio

### 1.1 `PublicationRepository`

**Antes**

```kotlin
suspend fun byKeys(keys: Set<String>): List<Publication>
```

**Después**

```kotlin
/**
 * What a synchronisation cycle evaluates the alerts against: every stored publication still marked
 * pending, with the instant it was stored. Pending survives process death; it is cleared only by
 * [markAlertsEvaluated]. A read failure is a [AppResult.Failure], never an empty list.
 */
suspend fun pendingAlertCandidates(): AppResult<List<AlertCandidate>>

/** Clears the pending mark on exactly [keys]. Touches nothing else. */
suspend fun markAlertsEvaluated(keys: Set<String>): AppResult<Unit>
```

`byKeys` desaparece. El KDoc de la interfaz cambia su última línea: «The flows recover from a transient
read failure with bounded retries; a persistent one leaves the last fallback in place».

### 1.2 `AlertRepository`

**Antes** → **Después**

```kotlin
suspend fun enabledRules(): List<AlertRule>                       → AppResult<List<AlertRule>>
suspend fun recordMatches(candidates: List<AlertMatch>): List<AlertMatch>
                                                                  → AppResult<List<AlertMatch>>
```

Invariantes de `recordMatches` tras el cambio:

- `Success(lista)` contiene **exactamente** las parejas que el almacén dio por nuevas; una pareja ya
  registrada no está.
- El lote es **todo o nada**: si una pareja no puede registrarse, ninguna queda registrada y el resultado
  es `Failure`.
- Un `Failure` **no** implica «nada nuevo»: implica «no sé», y el ciclo lo trata dejando la marca de
  pendiente intacta.
- La analítica (`alert_matches{recorded, publications}`) solo se emite en `Success` con lista no vacía.

El KDoc de la interfaz corrige «a local read failure emits an empty result and stays alive» por la
formulación de 1.1.

### 1.3 `AlertCandidate` — NUEVO

```kotlin
/** A stored publication the alerts have not seen yet, and when the application first stored it. */
data class AlertCandidate(val publication: Publication, val storedAt: Long) {
    init { require(storedAt > 0) { "storedAt must be positive" } }

    /** «Never retroactive», stated once: a rule sees only what was stored at or after it became active. */
    fun isVisibleTo(rule: AlertRule): Boolean = rule.activeSince <= storedAt
}
```

### 1.4 `OfficialDocument.companion` — ahora público

```kotlin
companion object {
    /** Sixty-four zeros: what a copy whose sidecar was lost or is unreadable reports. */
    const val UNKNOWN_CHECKSUM = "0000000000000000000000000000000000000000000000000000000000000000"

    /** The one rule for a checksum, shared with whoever stores one. */
    fun isValidChecksum(value: String): Boolean = CHECKSUM.matches(value)
}
```

`init` sigue exigiendo `isValidChecksum(checksum)`; `UNKNOWN_CHECKSUM` lo cumple.

### 1.5 `DocumentRepository` — contrato reafirmado, con dos invariantes nuevos

El KDoc ya dice «Nothing here throws» y «[observeDocument] never terminates with an error: it emits
[DocumentStatus.Failed]». Se añaden:

- Cancelar la llamada que posee la descarga deja el estado en `Absent`, nunca en `Downloading`.
- Una llamada que espera una descarga de otro **no** termina con la cancelación de ese otro: retoma la
  descarga si su propio contexto sigue activo.

### 1.6 `RunSyncCycleUseCase` — sin cambio de firma; cambio de orden interno

```
invoke(force):
  rules = alerts.enabledRules()                       ← AppResult; Failure → refresh sigue, evaluación aplazada
  summary = refreshPublications(force)                ← igual (Failure → return)
  outcome = evaluate(summary, rules)                  ← igual el try/catch
  releaseUnusedDocuments(); return Success(outcome)

evaluate(summary, rules):
  1. summary.isBaseline            → log "cycle: baseline (N inserted), alerts not evaluated"; NONE
  2. pending = publications.pendingAlertCandidates()
       Failure                     → log "cycle: pending unreadable"; NONE
       vacío                       → log "cycle: N new, 0 pending, R rule(s), nothing to evaluate"; NONE
  3. matches = pending × rules donde candidate.isVisibleTo(rule) && matchRule(rule, publication)
  4. recorded = if (matches.isEmpty()) [] else alerts.recordMatches(matches)
       Failure                     → log "cycle: recording failed, P key(s) kept pending"; NONE  (sin marcar)
  5. publications.markAlertsEvaluated(pending.keys)
       Failure                     → log "cycle: P key(s) recorded but not cleared"; SIGUE
  6. notificaciones desde recorded; delivery; log "cycle: N new, L pending from earlier, R rule(s),
     K match(es) on P publication(s), delivery=X"   (L = pendientes cuya clave no está en summary.newKeys)
```

---

## 2. Capa de datos — operadores nuevos

### 2.1 `ReadRecovery.recoverReads`

```kotlin
internal val READ_RETRY_DELAYS: List<Long> = listOf(1_000L, 5_000L, 30_000L)

/**
 * Keeps a Room flow alive across a transient read failure.
 *
 * On failure: reports, emits [fallback], waits, re-subscribes. A successful emission resets the budget.
 * After [delays].size consecutive failures the flow completes quietly — the fallback was already
 * emitted — so a permanently broken store is not polled forever. Cancellation is rethrown untouched.
 *
 * MUST be the LAST operator, after `flowOn`: the delays then run in the collector's context, which is
 * what `runTest` advances.
 */
internal fun <T> Flow<T>.recoverReads(
    fallback: T,
    name: String,
    crashReporter: CrashReporter,
    delays: List<Long> = READ_RETRY_DELAYS,
): Flow<T>
```

**Invariantes**

- Una `CancellationException` del upstream se repropaga sin reportar ni reintentar.
- Una excepción del **colector** atraviesa el operador intacta (transparencia de excepciones).
- El contador de fallos es **por colección**: dos colecciones simultáneas del mismo `Flow` no se
  influyen.
- Exactamente `1 + delays.size` suscripciones al upstream ante un fallo permanente.
- Registro: `reads: <name> failed: <Clase>, retry in <ms>ms` y `reads: <name> gave up after N retries`.
  Nunca texto de consulta ni datos de la persona.

### 2.2 `CancellableCall.await`

```kotlin
/**
 * Runs the call and consumes its response, cancelling the call if the coroutine is cancelled.
 *
 * The body is consumed INSIDE OkHttp's callback so that cancellation covers headers and body alike.
 * Nothing may escape [onResponse]: a non-IOException leaving the callback is rethrown by OkHttp on its
 * executor thread and kills the process, so every failure of [consume] is routed to the continuation.
 * [consume] MUST NOT suspend.
 */
internal suspend fun <T> Call.await(consume: (Response) -> T): T
```

**Invariantes**

- Cancelar la corrutina cancela la llamada (`Call.isCanceled == true`) y la corrutina termina sin esperar
  la respuesta.
- La `Response` se cierra siempre, con o sin excepción de `consume`.
- Una `IOException` antes de la respuesta llega como esa misma `IOException`.
- Una excepción de `consume` llega al llamante como esa misma excepción; **nunca** al hilo de OkHttp.
- Una respuesta que llega tras la cancelación se descarta en silencio.

**Sitios que migran** (ocho): `OkHttpDocumentDownloader.download`, `OkHttpPublicationRemoteDataSource.attempt`,
`OkHttpGeminiSummaryDataSource.request`, `OkHttpGeminiChatDataSource.request`,
`OkHttpGeminiDocumentUploader.beginUpload`/`sendBytes`/`fetch`/`delete`. En el uploader, los tres
primeros pasan a `suspend`.

**`catch (IOException)` tras la migración**, en los cinco ficheros: primera línea
`currentCoroutineContext().ensureActive()`. En la RSS:

```kotlin
} catch (error: IOException) {
    currentCoroutineContext().ensureActive()
    FeedFetchResult.Failed(if (error is SocketTimeoutException) FeedFailure.TIMEOUT else FeedFailure.NETWORK)
}
```

**`AiDocumentUploader.delete`** KDoc: «Never throws, except `CancellationException`».

---

## 3. DAO

### 3.1 `PublicationDao` — dos sentencias nuevas, una retirada, una intacta

```kotlin
/** The rows a cycle evaluates the alerts against. Same order as every list of the bulletin. */
@Query("""
    SELECT * FROM publications WHERE pending_alert_evaluation = 1
    ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC
""")
suspend fun pendingAlertEvaluation(): List<PublicationEntity>

/** Clears the mark on exactly [keys]; touches nothing else. Callers chunk the IN list at 900. */
@Query("UPDATE publications SET pending_alert_evaluation = 0 WHERE external_key IN (:keys)")
suspend fun markAlertsEvaluated(keys: List<String>): Int
```

- `byKeys` **se retira**.
- `updateColumns` **no cambia**: la columna nueva queda fuera, y `PublicationDaoTest` gana la guarda
  «una corrección de la fuente no re-marca la fila», hermana de la de `saved_at`.
- Sigue sin existir ningún `DELETE` sobre `publications`.

### 3.2 `AlertMatchDao.insert` — sin cambio

`@Insert(onConflict = IGNORE) suspend fun insert(items: List<AlertMatchEntity>): List<Long>`. Lo que
cambia es que `AlertRepositoryImpl` lo llama **una vez** con la lista entera.

### 3.3 `FileDocumentCache` — contrato de `DocumentCache.get` reforzado

KDoc: «The stored document, refreshing its last use, or `null` if it is not there. **Never throws for a
malformed entry**: a sidecar that exists but is not a valid checksum reads as `UNKNOWN_CHECKSUM`».

---

## 4. Registro (`CrashReporter.log`, etiqueta `BOC`)

Líneas nuevas, todas con recuentos, clases de excepción o enumerados; **nunca** títulos, claves de
publicación, texto ni credenciales.

```
document: cache read failed: IllegalStateException
document: checksum sidecar unreadable, served without checksum
document: fetch threw: IllegalStateException: boom
document: cleanup failed: IOException
reads: unread-count failed: SQLiteException, retry in 1000ms
reads: unread-count gave up after 3 retries
cycle: rules unreadable, evaluation deferred
cycle: pending unreadable
cycle: 14 new, 3 pending from earlier, 3 rule(s), 2 match(es) on 2 publication(s), delivery=SYSTEM
cycle: recording failed, 3 key(s) kept pending
cycle: 3 key(s) recorded but not cleared
```

La cola `… match(es) on … publication(s), delivery=…` se conserva: la vigila el test de privacidad del
ciclo.

---

## 5. Dobles de prueba

### 5.1 `FakePublicationRepository`

```
+ var now: Long = 1_000_000                        reloj que sella storedAt
+ val pendingKeys: MutableMap<String, Long>         clave → storedAt
+ fun seedPending(publication, storedAt)            deja un resto de un ciclo anterior
+ var pendingReads: Int                             cuántas veces se leyó lo pendiente
+ var failPendingRead: Boolean                      pendingAlertCandidates() → Failure
+ var failMarkEvaluated: Boolean                    markAlertsEvaluated() → Failure
- byKeys / keysAsked                                retirados
refresh(): sella refreshResult.newKeys con `now` en pendingKeys salvo isBaseline
```

### 5.2 `FakeAlertRepository`

```
enabledRules(): AppResult          + var failReads: Boolean
recordMatches(): AppResult         + var failRecordMatches: Boolean   (hoy no tiene modo de fallo)
```

### 5.3 `FailingOnceAlertMatchDao(delegate: AlertMatchDao, failuresLeft: Int = 1) : AlertMatchDao by delegate`

`insert` lanza `failuresLeft` veces y después delega. Se inyecta en `AlertRepositoryImpl` desde
`AlertFlowIntegrationTest`, con Room real debajo.

### 5.4 `TlsMockWebServer : ExternalResource`

```
val server: MockWebServer          TLS con certificado autofirmado
val client: OkHttpClient           confía en ese certificado; retryOnConnectionFailure(false)
fun url(path: String): String
```

Sustituye el bloque de diecisiete líneas copiado en `OkHttpGeminiChatDataSourceTest`,
`OkHttpGeminiSummaryDataSourceTest`, `OkHttpDocumentDownloaderTest`,
`OkHttpPublicationRemoteDataSourceTest` y `DocumentFlowIntegrationTest`; lo usa el nuevo
`OkHttpGeminiDocumentUploaderTest`.

### 5.5 `FlakyFlow` (privado en `ReadRecoveryTest`)

Un `Flow<T>` que cuenta suscripciones **dentro** del `flow { }`, falla las `failures` primeras y emite
`value` después. Es lo que `retryWhen` necesita para probarse: re-colecciona el mismo objeto.

---

## 6. Pruebas de regresión — nombre y qué demuestran

| Clase | Prueba | Antes del arreglo |
|---|---|---|
| `FileDocumentCacheTest` | `a sidecar that is present but invalid reads back as the unknown checksum instead of throwing` | `IllegalArgumentException` |
| | `storing leaves no sidecar temporary behind` / `a document that cannot be moved into place leaves no sidecar either` / `a stale sidecar temporary is ignored and replaced by put` | — |
| `OfficialDocumentTest` | `isValidChecksum agrees with the constructor` | — |
| `DocumentRepositoryImplTest` | `a stored document whose checksum sidecar was truncated opens without downloading again` | excepción escapa |
| | `a cache that cannot be read is reported and repaired by downloading again` | excepción escapa |
| | `an exploding downloader is observed as failed, not left downloading` | `Downloading` |
| | `a cache that cannot store the document fails visibly and can be retried` | `Downloading` |
| | `cleanup that fails does not hide the failure nor hang the waiters` | *waiter* colgado |
| | `a cancelled download is observed as absent, not downloading` | `Downloading` |
| | `a waiter whose owner is cancelled takes over the download` | *waiter* muere |
| `RunSyncCycleUseCaseTest` | `what could not be recorded is kept pending and delivered exactly once by the next cycle` | 0 entregas |
| | `a rule created between two cycles does not fire for what an earlier cycle left pending` | — |
| | `a leftover is evaluated even when the refresh is skipped` | — |
| | `with no rules the new publications are cleared, so a rule created later does not see them` | — |
| | `a failure to clear the flag does not block delivery and does not deliver twice` | — |
| | `when the rules cannot be read the refresh still runs and evaluation waits` | — |
| `AlertFlowIntegrationTest` | `a match the store could not record is delivered by the next cycle, once` | 0 entregas |
| | `a rule created after a leftover was stored does not fire for it` | — |
| `PublicationDaoTest` | `a row the source brings for the first time is pending evaluation, and a correction does not re-flag it` | — |
| | `marking evaluated clears only the given keys and touches nothing else` | — |
| `PublicationRepositoryImplTest` | `the baseline stores nothing as pending` / `a later synchronisation leaves exactly the inserted keys pending, stamped with when they were stored` / `the backfill leaves the pending flag alone` / `observing the bulletin survives a read failure` | — |
| `AlertRepositoryImplTest` | `a batch that cannot be recorded whole records nothing and reports the failure` | 900 filas, `emptyList()` |
| | `the unread count survives a read failure` | completa en 0 |
| `BocDatabaseMigrationTest` | `a version 5 database keeps its alerts and its publications are not pending` / `a version 1 database can reach version 6 in one go` / `a publication stored right after the upgrade is pending and can be cleared` | — |
| `AlertCandidateTest` | visible en/después de `activeSince`, no antes | — |
| `ReadRecoveryTest` | siete pruebas (transitorio, presupuesto, permanente, cancelación upstream, transparencia, cancelar en espera, registro) | — |
| `SavedPublicationRepositoryImplTest` | `a read failure emits empty and keeps observing` (reescrita) | completa tras el vacío |
| `SearchRepositoryImplTest` | dos reescritas + `a permanent failure gives up after three retries` | completa tras el vacío |
| `CancellableCallTest` | cinco pruebas (cancela y vuelve pronto, IOException, excepción al consumir nunca al hilo de OkHttp, respuesta cerrada, tardía descartada) | `isCanceled=false` |
| `OkHttpGeminiChatDataSourceTest` / `OkHttpGeminiSummaryDataSourceTest` | las de cancelación afirman prontitud e `isCanceled` | 30 s de espera |
| `OkHttpDocumentDownloaderTest` | `cancelling mid-body is a cancellation, the call is cancelled and no refusal is reported` | `Network` |
| `OkHttpPublicationRemoteDataSourceTest` | `cancelling mid-request is a cancellation, never a network failure nor a retry` | `NETWORK` + reintento |
| `OkHttpGeminiDocumentUploaderTest` (NUEVA) | seis pruebas (flujo completo, sin URL, tope de sondeo, delete, cancelación, credencial fuera del log) | sin pruebas |
