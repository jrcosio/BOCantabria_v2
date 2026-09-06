# Data Model: Avisos

**Feature**: `012-avisos` | **Fecha**: 6 de septiembre de 2026

Esta feature **sí persiste**: dos tablas nuevas y la base de datos pasa a la **versión 5**
(`research.md` D-410). Este documento describe los tipos de las tres capas y las filas.

---

## 1. Dominio

Kotlin puro: cero `android.*`, cero Compose, cero referencias a `data` ni a `ui`. Cada clase de nivel
superior necesita su fichero de prueba (regla novena de Konsist).

### 1.1 `KeywordMatchMode`

```kotlin
enum class KeywordMatchMode {
    ANY, ALL;
    companion object {
        /** Tolerant: an unknown or missing stored name is ANY, the default of the form. */
        fun byNameOrDefault(name: String?): KeywordMatchMode
    }
}
```

### 1.2 `AlertRule` — la regla guardada

```kotlin
data class AlertRule(
    val id: String,
    val name: String,
    val keywords: List<String>,          // as typed; normalised when compared (D-408)
    val matchMode: KeywordMatchMode,
    val sectionCodes: Set<String>,       // leaf classification codes; empty = all (D-407)
    val organizationQuery: String?,      // null/blank = any organisation (D-409)
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val activeSince: Long,               // renewed on create, edit and re-enable (D-405)
) {
    init {
        require(id.isNotBlank()); require(name.isNotBlank())
        require(keywords.size <= AlertRuleDraft.MAX_KEYWORDS)
        require(hasCriteria) { "a rule needs at least one positive criterion" }
    }
    val hasCriteria: Boolean
        get() = keywords.isNotEmpty() || sectionCodes.isNotEmpty() || !organizationQuery.isNullOrBlank()
}
```

### 1.3 `AlertRuleOverview` — lo que la tarjeta pinta

```kotlin
data class AlertRuleOverview(
    val rule: AlertRule,
    val lastMatchedAt: Long?,
    val matchesToday: Int,               // matches since the caller's local-day start (D-432)
)
```

### 1.4 `AlertRuleDraft` — lo que el formulario edita

```kotlin
data class AlertRuleDraft(
    val name: String = "",
    val keywords: List<String> = emptyList(),
    val matchMode: KeywordMatchMode = KeywordMatchMode.ANY,
    val sectionCodes: Set<String> = emptySet(),   // may contain parents until expanded on save
    val organizationQuery: String = "",
    val isEnabled: Boolean = true,
) {
    val hasCriteria: Boolean
    fun validate(): Set<AlertRuleValidationError>
    val isValid: Boolean get() = validate().isEmpty()
    /** Trims, checks length, rejects a normalised duplicate and the eleventh term. */
    fun addingKeyword(raw: String): KeywordAddition
    fun removingKeyword(keyword: String): AlertRuleDraft
    /** Proposes a name from the first criterion when the name is blank (FR-017). */
    fun suggestedName(sections: List<BocSection>): String?
    fun toRule(id: String, now: Long, sections: List<BocSection>): AlertRule   // expands parents

    companion object {
        const val NAME_MAX_LENGTH = 60
        const val KEYWORD_MIN_LENGTH = 2
        const val KEYWORD_MAX_LENGTH = 60
        const val MAX_KEYWORDS = 10
        const val COPY_PREFIX = "Copia de "
        fun duplicateOf(rule: AlertRule): AlertRuleDraft   // disabled, "Copia de <name>"
    }
}

enum class AlertRuleValidationError { NAME_BLANK, NAME_TOO_LONG, NO_CRITERIA }

sealed interface KeywordAddition {
    data class Added(val draft: AlertRuleDraft) : KeywordAddition
    data class Rejected(val reason: KeywordRejection) : KeywordAddition
}

enum class KeywordRejection { BLANK, TOO_SHORT, TOO_LONG, DUPLICATE, LIMIT_REACHED }
```

Reglas (FR-017, FR-018, FR-026): nombre recortado de 1 a 60; término recortado de 2 a 60, como mucho
diez, sin duplicados tras `SearchText.normalise`; al menos un criterio positivo.

### 1.5 `SectionSelection` — la jerarquía, en puro

```kotlin
object SectionSelection {
    enum class ToggleState { CHECKED, INDETERMINATE, UNCHECKED }

    /** A parent in [selected] becomes its children. Leaves pass through. */
    fun expandToLeaves(selected: Set<String>, sections: List<BocSection>): Set<String>

    /** Toggles a parent (all children on/off) or a leaf. */
    fun toggled(selected: Set<String>, code: String, sections: List<BocSection>): Set<String>

    fun stateOf(parent: BocSection, sections: List<BocSection>, selected: Set<String>): ToggleState

    /** null = "Todas las secciones"; "<Padre> (todas)" when every child is in; else the names. */
    fun summaryParts(selected: Set<String>, sections: List<BocSection>): List<String>?
}
```

### 1.6 `AlertMatch`, `AlertNews`, `AlertNotification`, `InAppAlert`

```kotlin
data class AlertMatch(val ruleId: String, val externalKey: String, val matchedAt: Long)

/** One publication that matched, as the Novedades tab shows it. */
data class AlertNews(
    val publication: Publication,
    val ruleNames: List<String>,
    val detectedAt: Long,
    val isRead: Boolean,
)

/** One publication to deliver, with every rule it matched. */
data class AlertNotification(val publication: Publication, val ruleNames: List<String>) {
    init { require(ruleNames.isNotEmpty()) }
}

/** What the in-app Snackbar says. [ruleName] only when a single publication matched a single rule. */
data class InAppAlert(val publicationCount: Int, val ruleName: String?) {
    init { require(publicationCount >= 1) }
}
```

### 1.7 `SyncCycleOutcome` y `AlertDelivery`

```kotlin
enum class AlertDelivery { NONE, IN_APP, SYSTEM }

data class SyncCycleOutcome(
    val summary: SyncSummary,
    val notifications: List<AlertNotification>,
    val delivery: AlertDelivery,
)
```

### 1.8 `SyncSummary` — lo que cambia

```kotlin
data class SyncSummary(
    …six existing counters…,
    val newKeys: Set<String> = emptySet(),
    val isBaseline: Boolean = false,
)
// plus: newKeys = newKeys + other.newKeys, isBaseline = isBaseline || other.isBaseline
```

### 1.9 `NotificationStatus`

```kotlin
enum class NotificationStatus { GRANTED, NEEDS_REQUEST, DISABLED }
```

| Valor | Significa | Qué hace la pantalla |
|---|---|---|
| `GRANTED` | Android muestra notificaciones | Nada |
| `NEEDS_REQUEST` | SDK ≥ 33 y el permiso nunca se concedió | Pide en contexto tras el primer aviso (D-428) |
| `DISABLED` | Denegado o apagado en Ajustes | Banner con «Abrir ajustes» si hay reglas activas |

### 1.10 Contratos de dominio

```kotlin
interface AlertRepository {
    fun observeRules(dayStart: Long): Flow<List<AlertRuleOverview>>
    suspend fun rule(id: String): AlertRule?
    suspend fun enabledRules(): List<AlertRule>
    suspend fun countRules(): Int
    suspend fun countEnabled(): Int
    /** Creates when [id] is null, replaces otherwise. Both renew active_since. Returns the id. */
    suspend fun save(draft: AlertRuleDraft, id: String?): AppResult<String>
    suspend fun setEnabled(id: String, enabled: Boolean): AppResult<Unit>
    suspend fun delete(id: String): AppResult<Unit>
    /** INSERT OR IGNORE; returns only what was really inserted. */
    suspend fun recordMatches(candidates: List<AlertMatch>): List<AlertMatch>
    fun observeNews(): Flow<List<AlertNews>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markRead(externalKey: String): AppResult<Unit>
    suspend fun markAllRead(): AppResult<Unit>
}

interface InAppAlertStore {
    fun observePending(): Flow<InAppAlert?>
    fun publish(alert: InAppAlert)
    fun consume()
}

interface AlertNotifier {
    fun post(notifications: List<AlertNotification>)
}

interface BackgroundSyncScheduler {
    fun ensureScheduled()
    fun cancel()
}

interface NotificationStatusRepository {
    fun status(): NotificationStatus
}

// PublicationRepository gains three reads:
suspend fun byKeys(keys: Set<String>): List<Publication>
suspend fun newest(limit: Int): List<Publication>
suspend fun lastSuccessfulSyncAt(): Long?
```

`core/util`:

```kotlin
interface AppVisibilityProvider { fun isAppVisible(): Boolean }
object LocalDay { fun startOf(nowMillis: Long, zone: ZoneId): Long }
object RelativeTime {
    sealed interface Label { data object Today; data object Yesterday; data class Minutes(n); data class Hours(n); data class Date(LocalDate) }
    fun label(instantMillis: Long, nowMillis: Long, zone: ZoneId): Label
    fun dayOf(instantMillis: Long, nowMillis: Long, zone: ZoneId): Label   // Today / Yesterday / Date
}
```

`core/notification/AlertIntentExtras`: `EXTRA_TARGET`, `TARGET_PUBLICATION`, `TARGET_NEWS`,
`EXTRA_EXTERNAL_KEY`.

### 1.11 Casos de uso

| Caso de uso | Dependencias | Hace |
|---|---|---|
| `RunSyncCycleUseCase(force)` | refresh, publications, alerts, matchRule, notifier, inAppAlerts, appVisibility, releaseUnusedDocuments, time, crashReporter | D-404 |
| `MatchAlertRuleUseCase(rule, publication)` | sections | D-406 |
| `ObserveAlertRulesUseCase()` | alerts, time, zone | `observeRules(LocalDay.startOf(now, zone))` |
| `GetAlertRuleUseCase(id)` | alerts | |
| `SaveAlertRuleUseCase(draft, id?)` | alerts, scheduler | guarda y `ensureScheduled()` / `cancel()` |
| `SetAlertRuleEnabledUseCase(id, enabled)` | alerts, scheduler | ídem |
| `DeleteAlertRuleUseCase(id)` | alerts, scheduler | ídem |
| `CountAlertRulesUseCase()` | alerts | el formulario sabe si es el primero |
| `ObserveAlertNewsUseCase()` | alerts | |
| `ObserveUnreadAlertCountUseCase()` | alerts | |
| `MarkAlertReadUseCase(key)` | alerts | también desde el detalle (D-426) |
| `MarkAllAlertsReadUseCase()` | alerts | |
| `ObservePendingInAppAlertUseCase()` / `ConsumeInAppAlertUseCase()` | inAppAlerts | D-416 |
| `GetNotificationStatusUseCase()` | notificationStatus | D-427 |
| `ReconcileBackgroundSyncUseCase()` | alerts, scheduler | D-422 |
| `GetLastSyncUseCase()` | publications | hoja de ajustes |
| `PreviewAlertRuleUseCase(draft)` | publications, matchRule, sections | D-437 |

---

## 2. Capa de datos

### 2.1 `alert_rules`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | TEXT, PK | UUID (D-414) |
| `name` | TEXT NOT NULL | |
| `keywords` | TEXT NOT NULL | `List<String>` con `Converters` (D-411) |
| `match_mode` | TEXT NOT NULL | `KeywordMatchMode.name`, lectura tolerante |
| `section_codes` | TEXT NOT NULL | `List<String>` de códigos hoja |
| `organization_query` | TEXT | null = cualquiera |
| `enabled` | INTEGER NOT NULL | índice |
| `created_at`, `updated_at`, `active_since` | INTEGER NOT NULL | epoch millis |

### 2.2 `alert_matches`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK autogenerate | |
| `rule_id` | TEXT NOT NULL | FK → `alert_rules.id` **ON DELETE CASCADE**; índice |
| `external_key` | TEXT NOT NULL | índice; sin FK a `publications` |
| `matched_at` | INTEGER NOT NULL | |
| `read_at` | INTEGER | null = pendiente; índice |

Índices: `UNIQUE(rule_id, external_key)`, `(external_key)`, `(read_at)`, `(rule_id)`.

### 2.3 DAOs

```kotlin
@Dao interface AlertRuleDao {
    @Query("""SELECT r.*, MAX(m.matched_at) AS last_matched_at,
                     COALESCE(SUM(CASE WHEN m.matched_at >= :dayStart THEN 1 ELSE 0 END), 0) AS matches_today
              FROM alert_rules r LEFT JOIN alert_matches m ON m.rule_id = r.id
              GROUP BY r.id ORDER BY r.created_at DESC, r.id DESC""")
    fun observeRules(dayStart: Long): Flow<List<AlertRuleWithStats>>
    @Query("SELECT * FROM alert_rules WHERE id = :id") suspend fun byId(id: String): AlertRuleEntity?
    @Query("SELECT * FROM alert_rules WHERE enabled = 1") suspend fun enabledRules(): List<AlertRuleEntity>
    @Query("SELECT COUNT(*) FROM alert_rules") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM alert_rules WHERE enabled = 1") suspend fun countEnabled(): Int
    @Upsert suspend fun upsert(rule: AlertRuleEntity)
    @Query("UPDATE alert_rules SET enabled = :enabled, active_since = :now, updated_at = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long): Int
    /** The project's first and only delete. Rules belong to the person (D-412). */
    @Query("DELETE FROM alert_rules WHERE id = :id") suspend fun delete(id: String): Int
}

data class AlertRuleWithStats(@Embedded val rule: AlertRuleEntity,
    @ColumnInfo(name = "last_matched_at") val lastMatchedAt: Long?,
    @ColumnInfo(name = "matches_today") val matchesToday: Int)

@Dao interface AlertMatchDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(items: List<AlertMatchEntity>): List<Long>
    @Query("""SELECT p.*, GROUP_CONCAT(r.name, '') AS rule_names, MIN(m.matched_at) AS detected_at,
                     MAX(CASE WHEN m.read_at IS NULL THEN 1 ELSE 0 END) AS unread
              FROM alert_matches m
              JOIN publications p ON p.external_key = m.external_key
              JOIN alert_rules r ON r.id = m.rule_id
              GROUP BY p.external_key
              ORDER BY p.publication_date DESC, detected_at DESC, p.external_key DESC""")
    fun observeNews(): Flow<List<AlertNewsRow>>
    @Query("SELECT COUNT(DISTINCT external_key) FROM alert_matches WHERE read_at IS NULL")
    fun observeUnreadCount(): Flow<Int>
    @Query("UPDATE alert_matches SET read_at = :now WHERE external_key = :externalKey AND read_at IS NULL")
    suspend fun markRead(externalKey: String, now: Long): Int
    @Query("UPDATE alert_matches SET read_at = :now WHERE read_at IS NULL") suspend fun markAllRead(now: Long): Int
}

data class AlertNewsRow(@Embedded val publication: PublicationEntity,
    @ColumnInfo(name = "rule_names") val ruleNames: String,
    @ColumnInfo(name = "detected_at") val detectedAt: Long,
    @ColumnInfo(name = "unread") val unread: Int)
```

`PublicationDao` gana dos lecturas: `byKeys(keys)` y `newest(limit)`; `UpsertCounts` gana
`insertedKeys` (D-401). **Ningún otro DAO cambia.**

### 2.4 `AlertRepositoryImpl`

Patrón de `SavedPublicationRepositoryImpl`: `withContext(dispatchers.io)`, `try/catch` que repropaga
`CancellationException` y traduce el resto a `DomainError.Unknown` con `crashReporter.recordNonFatal`,
flujos con `.catch { emit(empty) }` y `.flowOn(io)`. Genera el UUID, escribe `time.nowMillis()` en
`created_at`/`updated_at`/`active_since`, expande padres con `SectionSelection.expandToLeaves` y emite
la analítica de D-438. `recordMatches` trocea a 900 y filtra `rowId == -1`.

### 2.5 `AndroidAlertNotifier` (`data/notification/`)

Constructor `(context, crashReporter)`. Ver `research.md` D-417, D-418, D-425 y
`contracts/internal-contracts.md` §3.

### 2.6 `AlertSyncWorker` y `WorkManagerBackgroundSyncScheduler` (`data/background/`)

```kotlin
class AlertSyncWorker(context, params, private val runSyncCycle: RunSyncCycleUseCase,
                      private val crashReporter: CrashReporter) : CoroutineWorker(context, params)

class WorkManagerBackgroundSyncScheduler(private val context: Context) : BackgroundSyncScheduler {
    companion object { const val WORK_NAME = "boc_alert_sync"; const val INTERVAL_HOURS = 4L; const val FLEX_MINUTES = 30L }
}
```

### 2.7 `NotificationStatusDataSource` (`data/source/local/`)

`AndroidNotificationStatusDataSource(context)`: `NotificationManagerCompat.areNotificationsEnabled()`
→ `GRANTED`; si no, `NEEDS_REQUEST` cuando SDK ≥ 33 y `checkSelfPermission(POST_NOTIFICATIONS) !=
GRANTED` y no se ha denegado de forma permanente… (la plataforma no expone «denegado para siempre», así
que `NEEDS_REQUEST` mientras el permiso no esté concedido y `DISABLED` cuando esté concedido pero las
notificaciones estén apagadas; el diálogo del sistema deja de aparecer solo, D-428).

### 2.8 `InMemoryInAppAlertStore`

`MutableStateFlow<InAppAlert?>`; `publish` **acumula** si ya había uno pendiente (suma recuentos y
pierde el `ruleName`), `consume` lo pone a null.

---

## 3. Presentación

### 3.1 Rutas

```kotlin
@Serializable data class Alerts(val tab: String? = null) : Route          // inner graph
@Serializable data class AlertForm(val ruleId: String? = null, val duplicateOf: String? = null) : Route  // outer
```

### 3.2 `MainShellUiState`

```kotlin
data class MainShellUiState(val unreadAlerts: Int = 0, val pendingAlert: InAppAlert? = null)
```

### 3.3 `AlertsUiState`

```kotlin
enum class AlertsTab { NEWS, RULES; companion object { fun byNameOrDefault(name: String?) } }

data class AlertsUiState(
    val tab: AlertsTab = AlertsTab.NEWS,
    val news: List<AlertNewsDay> = emptyList(),      // grouped: Today / Yesterday / date
    val unreadCount: Int = 0,
    val rules: List<AlertRuleCardState> = emptyList(),
    val activeCount: Int = 0,
    val notificationStatus: NotificationStatus = NotificationStatus.GRANTED,
    val pendingDelete: AlertRule? = null,
    val settingsOpen: Boolean = false,
    val lastSyncAt: Long? = null,
    val actionFailed: Boolean = false,               // one-shot
) {
    val showsPermissionBanner: Boolean get() = activeCount > 0 && notificationStatus == NotificationStatus.DISABLED
}

data class AlertNewsDay(val label: RelativeTime.Label, val items: List<AlertNews>)

data class AlertRuleCardState(
    val overview: AlertRuleOverview,
    val sectionSummary: List<String>?,               // null = all sections
    val lastMatchLabel: RelativeTime.Label?,
)
```

### 3.4 `AlertFormUiState`

```kotlin
sealed interface AlertFormUiState {
    data object Loading : AlertFormUiState
    data class Ready(
        val draft: AlertRuleDraft,
        val errors: Set<AlertRuleValidationError>,
        val keywordRejection: KeywordRejection?,      // one-shot
        val sectionRows: List<SectionPickerRow>,
        val sectionSummary: List<String>?,
        val organizationSuggestions: List<String>,
        val isEdit: Boolean,
        val isSaving: Boolean,
        val previewCount: Int?,                       // null while unknown / draft invalid
        val previewOpen: Boolean,
        val preview: List<Publication>,
        val saveFailed: Boolean,
    ) : AlertFormUiState {
        val canSave: Boolean get() = errors.isEmpty() && !isSaving
    }
    data class Saved(val requestPermission: Boolean) : AlertFormUiState
}

data class SectionPickerRow(val section: BocSection, val children: List<BocSection>, val state: SectionSelection.ToggleState)
```

### 3.5 Componibles

| Fichero | Papel |
|---|---|
| `ui/alerts/AlertsScreen.kt` | `AlertsScreen` (con estado, `koinViewModel`) + `AlertsContent` (tonto); tags `TAG_ALERTS_*` |
| `ui/alerts/component/AlertRuleCard.kt` | nombre, `Switch`, chips, secciones, última coincidencia, menú tres puntos |
| `ui/alerts/component/AlertNewsItem.kt` | punto azul + `surfaceSoft` si no leída |
| `ui/alerts/component/AlertsIntroCard.kt` | «Sigue lo que te importa» + «Crear aviso» |
| `ui/alerts/component/NotificationsDisabledBanner.kt` | banner + «Abrir ajustes» |
| `ui/alerts/component/DeleteAlertDialog.kt` | confirmación |
| `ui/alerts/component/AlertSettingsSheet.kt` | permiso, ajustes, última comprobación |
| `ui/alerts/form/AlertFormScreen.kt` | `AlertFormScreen` + `AlertFormContent` |
| `ui/alerts/form/component/KeywordChipsInput.kt` | campo + «+» + `InputChip` con cruz |
| `ui/alerts/form/component/MatchModeSelector.kt` | radios |
| `ui/alerts/form/component/SectionPickerSheet.kt` | `ModalBottomSheet`, `TriStateCheckbox`/`Checkbox`, contador, «Aplicar» |
| `ui/alerts/form/component/OrganizationField.kt` | texto libre + sugerencias |
| `ui/alerts/form/component/RuleSummaryCard.kt` | «Así funcionará…» |
| `ui/alerts/form/component/PreviewSheet.kt` | «Ver resultados» |
| `ui/alerts/form/component/NotificationPermissionDialog.kt` | «Activa las notificaciones» |
| `ui/main/MainShellViewModel.kt`, `MainShellUiState.kt` | badge + Snackbar + reconciliación |
| `ui/navigation/PendingNavigation.kt` | almacén del deep link |

---

## 4. El recorrido de una coincidencia, de punta a punta

```
Worker (o pull-to-refresh en Inicio)
  → RunSyncCycleUseCase(force)
      → enabledRules()                                  [instantánea, D-405]
      → RefreshPublicationsUseCase → PublicationRepositoryImpl.refresh()
            isBaseline = lastSuccessAt()==null           [D-403]
            upsertAll → insertedKeys                     [D-401]
            SyncSummary(newKeys, isBaseline)             [D-402]
      → byKeys(newKeys) → MatchAlertRuleUseCase × reglas [D-406]
      → recordMatches → INSERT OR IGNORE                 [D-410]  → Room notifica observeNews/observeUnreadCount
      → isAppVisible()?                                  [D-415]
            SYSTEM → AndroidAlertNotifier.post           [D-417]  → PendingIntent(extras)  [D-425]
            IN_APP → InAppAlertStore.publish             [D-416]  → MainShell Snackbar «VER»
      → releaseUnusedDocuments()
Toque en la notificación
  → MainActivity.onNewIntent → PendingNavigationStore.set            [D-424]
  → Splash → Home → navigate(Detail(key))
  → PublicationDetailViewModel.init → MarkAlertReadUseCase            [D-426]  → badge baja
```

---

## 5. Lo que NO cambia

- `Publication`, `PublicationEntity` y sus catorce campos. `toDomain()` sigue sin mapear `first_seen_at`.
- `SavedPublicationDao`, `PublicationSearchDao`, `AiSummaryDao`, `FeedSyncStateDao`.
- La lista blanca de `PublicationDao.updateColumns`.
- `SearchText.normalise`.
- Ninguna publicación se borra. El único `DELETE` es `AlertRuleDao.delete` (D-412).
