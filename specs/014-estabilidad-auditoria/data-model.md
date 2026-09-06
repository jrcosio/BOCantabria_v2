# Modelo de datos — Feature 014

Esta feature toca datos en **un** sitio: una columna nueva en `publications` y, con ella, la versión 6
de la base de datos. Todo lo demás es estado en memoria, ficheros de la caché de documentos y contratos
de dominio. Como en la 013, se enumera con el mismo detalle lo que **no** cambia, porque cualquier
cambio ahí sería un defecto introducido por esta feature.

---

## 1. Lo que NO cambia

| Elemento | Estado |
|---|---|
| `PublicationDao.updateColumns` — la lista blanca de columnas que una sincronización puede reescribir | **intacta**: la columna nueva **no** entra. Una publicación corregida por la fuente no se re-marca |
| `saved_at`, `first_seen_at` — fuera de esa lista blanca | **intactos**, y siguen fuera |
| `alert_rules`, `alert_matches` y su `UNIQUE(rule_id, external_key)` con `INSERT … IGNORE` | **intactos** |
| `AlertRuleDao.delete` — la única sentencia `DELETE` del proyecto | **sigue siendo la única** |
| Las siete DAO y su regla «ninguna borra publicaciones» | **intacta** |
| `feed_sync_state`, `ai_summaries` | **intactas** |
| Esquemas exportados `1.json` … `5.json` | **intactos**; se añade `6.json` |
| `Publication` (modelo de dominio) | **intacto**: no gana `firstSeenAt` |
| `OfficialDocument` (campos y validación) | **intacto**; gana un `companion` público con `isValidChecksum` y `UNKNOWN_CHECKSUM` |
| `DocumentStatus` (los cuatro estados) | **intacto**; `Absent` amplía su significado |
| `SyncSummary` (campos) | **intacto**; cambia el KDoc de `newKeys` |
| Todos los constructores de repositorios, casos de uso y modelos de pantalla | **intactos** → `core/di` y `KoinModulesTest` no se tocan |
| `ui/` entero | **intacto** |

---

## 2. Room — versión 6

### 2.1 `publications` gana una columna

```
publications
├── external_key               TEXT PK           (sin cambio)
├── …                                            (sin cambio: las 18 columnas actuales)
├── saved_at                   INTEGER NULL      (sin cambio; fuera de updateColumns)
├── search_text                TEXT NOT NULL DEFAULT ''   (sin cambio; dentro de updateColumns)
└── pending_alert_evaluation   INTEGER NOT NULL DEFAULT 0  ← NUEVA; fuera de updateColumns
                               índice: idx publications(pending_alert_evaluation)
```

**Semántica.** `1` = «almacenada por una sincronización y aún no evaluada contra los avisos». `0` = todo
lo demás: evaluada, anterior a la migración, o insertada por la línea base.

**Quién la escribe.**

| Operación | Efecto sobre la marca |
|---|---|
| INSERT de una fila nueva en una sincronización **que no es línea base** | `1` (viene en la entidad) |
| INSERT de una fila nueva en la **línea base** | `0` (viene en la entidad: `pending = !isBaseline`) |
| UPDATE de una fila que ya existía (`updateColumns`) | **no la toca** |
| Fila rechazada por el índice único de `blob_id` | no existe fila → nada |
| `markAlertsEvaluated(keys)` | `0` para exactamente esas claves |
| Relleno de `search_text` (`setSearchText`) | **no la toca** |
| Migración 5→6 | `0` para todas las filas existentes (el `DEFAULT`) |

**Quién la lee.** Solo `pendingAlertEvaluation()`: `SELECT * FROM publications WHERE
pending_alert_evaluation = 1 ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key
DESC` (el mismo orden que toda lista del boletín).

### 2.2 Migración

- `BocDatabase`: `version = 6`, `autoMigrations += AutoMigration(from = 5, to = 6)`. Las anteriores se
  conservan: quien venga de la 1 llega a la 6 de una vez.
- `app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/6.json`: lo genera una build y
  **se versiona**. Un esquema exportado obsoleto hace que la prueba de migración lance al abrir, y el
  síntoma solo se ve en dispositivos actualizados.
- `bocDatabase()` sigue siendo un `.build()` limpio. `fallbackToDestructiveMigration()` no entra ni como
  último recurso.
- `BocDatabaseMigrationTest`: `VERSION_FIVE_STATEMENTS` transcritas del `5.json`; «una base en versión 5
  conserva sus avisos y ninguna publicación queda pendiente»; «una base en versión 1 llega a la 6 de una
  vez»; «una publicación almacenada justo tras actualizar queda pendiente y puede marcarse».

### 2.3 `PublicationEntity`

```kotlin
@ColumnInfo(name = "pending_alert_evaluation", defaultValue = "0")
val pendingAlertEvaluation: Boolean = false,
```

más `Index(value = ["pending_alert_evaluation"])`. El mapeador `toEntity(seenAt, searchText,
pendingAlertEvaluation: Boolean = false)` gana el tercer parámetro **con valor por defecto**, para que los
tres puntos de llamada en pruebas no cambien; solo la sincronización pasa `!isBaseline`.

---

## 3. Dominio

### 3.1 `AlertCandidate` — NUEVO

```
AlertCandidate
├── publication   Publication
└── storedAt      Long          first_seen_at: cuándo la aplicación supo de ella

fun isVisibleTo(rule: AlertRule): Boolean = rule.activeSince <= storedAt
```

«Nunca retroactivo», dicho una vez. Invariante: `storedAt > 0`. Konsist exige `AlertCandidateTest`.

### 3.2 Contratos que cambian

| Contrato | Antes | Después |
|---|---|---|
| `PublicationRepository.byKeys(keys): List<Publication>` | existe | **retirado** |
| `PublicationRepository.pendingAlertCandidates()` | — | `suspend fun (): AppResult<List<AlertCandidate>>` |
| `PublicationRepository.markAlertsEvaluated(keys)` | — | `suspend fun (keys: Set<String>): AppResult<Unit>` |
| `AlertRepository.enabledRules()` | `List<AlertRule>` (fallo → vacío) | `AppResult<List<AlertRule>>` |
| `AlertRepository.recordMatches(candidates)` | `List<AlertMatch>` (fallo → vacío) | `AppResult<List<AlertMatch>>` |
| `OfficialDocument.companion` | privado | `fun isValidChecksum(value: String): Boolean`, `const val UNKNOWN_CHECKSUM` |

### 3.3 `DocumentStatus` — significado ampliado, tipos intactos

```
Absent        nunca pedido · expulsado de la caché · SU DESCARGA SE CANCELÓ  ← ampliado
Downloading   hay una descarga EN VUELO (tras esta feature, siempre)
Available     copia local verificada; checksum puede ser UNKNOWN_CHECKSUM si el lateral se perdió
Failed        rechazo previsto O fallo inesperado  ← ampliado (hoy solo el rechazo)
```

**Transiciones de `DocumentRepositoryImpl`** (por clave):

```
                 ┌──────────── cache.get() devuelve copia ────────────┐
                 │                                                     ▼
Absent ──fetch──► Downloading ──put ok──► Available
                 │        │
                 │        ├── rechazo del descargador ──────────────► Failed(Network | Unknown)
                 │        ├── excepción inesperada (download/put) ──► Failed(Unknown)      ← NUEVO
                 │        └── cancelación del dueño ────────────────► Absent               ← NUEVO
                 │                                                     (solo si inFlight[key] === pending)
                 └── cache.get() lanza ──► se trata como Absent y sigue por fetch          ← NUEVO
```

**Estado en memoria del repositorio** (sin cambios de forma):

```
statuses   MutableStateFlow<Map<String, DocumentStatus>>
inFlight   MutableMap<String, CompletableDeferred<AppResult<OfficialDocument>>>   bajo `lock`
```

Invariante nuevo: una entrada de `inFlight` la quita **siempre** quien la puso, en `settle()` bajo
`NonCancellable`, antes de completar o cancelar el `Deferred`; y un *waiter* que recibe la cancelación
del dueño vuelve al `lock` y se convierte en dueño si su propio contexto sigue activo.

---

## 4. La caché de documentos en disco

```
<cacheDir>/documents/
├── <digest>.pdf            el documento; visible solo completo (rename desde .part)
├── <digest>.pdf.part       descarga en curso; nunca se confunde con un documento
├── <digest>.sha256         la huella; 64 hex minúsculas; visible solo completo   ← ahora atómico
└── <digest>.sha256.part    escritura en curso                                     ← NUEVO
```

**Orden de `put()`** — antes: rename PDF → escribir lateral. **Después**: escribir lateral (`.part` +
rename) → rename PDF → si el rename del PDF falla, borrar el lateral. Invariante: **PDF visible ⇒ lateral
válido**, para toda entrada fresca.

**Lectura del lateral** — `readChecksum()`: `null` si no existe **o si su contenido no cumple
`OfficialDocument.isValidChecksum`** (vacío, truncado, mayúsculas, espacios → se hace `trim()` antes).
`null` → `UNKNOWN_CHECKSUM`. El repositorio, al recibir una copia con `UNKNOWN_CHECKSUM`, registra
`document: checksum sidecar unreadable, served without checksum`.

**`evict`** — además de `.pdf` y su `.sha256`, retira `.sha256.part` huérfanos.

---

## 5. Recuperación de lecturas — estado por colección

```
recoverReads(fallback, name, delays = [1_000, 5_000, 30_000])
└── por cada colección del Flow:
    consecutive : Int = 0        fallos consecutivos desde la última emisión correcta
    ├── emisión correcta          → consecutive = 0
    ├── fallo, consecutive < 3    → reporta · emite fallback · espera delays[consecutive] · consecutive++ · resuscribe
    └── fallo, consecutive == 3   → reporta «gave up» · completa (el fallback ya se emitió)
```

Aplicado a nueve flujos, con su `fallback` y su `name`:

| Repositorio | Flujo | fallback | name |
|---|---|---|---|
| `PublicationRepositoryImpl` | `observePublications` (y `observeHeader`, que deriva de él) | `emptyList()` | `publications` |
| | `observePublication` | `null` | `publication` |
| `SavedPublicationRepositoryImpl` | `observeSaved` | `emptyList()` | `saved` |
| | `observeSavedKeys` | `emptySet()` | `saved-keys` |
| `SearchRepositoryImpl` | `search` | `emptyList()` | `search` |
| | `observeIssuers` | `emptyList()` | `issuers` |
| `AlertRepositoryImpl` | `observeRules` | `emptyList()` | `rules` |
| | `observeNews` | `emptyList()` | `news` |
| | `observeUnreadCount` | `0` | `unread-count` |

---

## 6. La llamada de red cancelable — sin estado persistente

```
Call.await(consume)
├── invokeOnCancellation → call.cancel()          cierra el socket: cabeceras Y cuerpo
├── onFailure(e)          → resumeWithException(e)  (no-op si ya cancelada)
└── onResponse(r)         → try { r.use(consume) } catch (Throwable) { resumeWithException } ; resume
```

Los ocho sitios y qué consumen:

| Fichero | Sitio | Cuerpo |
|---|---|---|
| `OkHttpDocumentDownloader` | `download` | **a disco**, hasta 25 MB, con SHA-256 al vuelo |
| `OkHttpPublicationRemoteDataSource` | `attempt` | a memoria, tope 5 MB |
| `OkHttpGeminiSummaryDataSource` | `request` | `body.string()` |
| `OkHttpGeminiChatDataSource` | `request` | `body.string()` |
| `OkHttpGeminiDocumentUploader` | `beginUpload` | solo cabeceras |
| | `sendBytes` | `body.string()`; **la petición** se emite desde fichero |
| | `fetch` (sondeo) | `body.string()` |
| | `delete` | se cierra sin leer |
