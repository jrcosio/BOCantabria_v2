# Data Model: Resumen IA

**Feature**: `007-resumen-ia` | **Fase**: 1 | **Fecha**: 2 de septiembre de 2026

Lo que esta feature añade al modelo, en el orden en que el dato viaja: del documento al texto, del
texto al resumen, del resumen al almacén y del almacén a la pantalla.

---

## 1. El texto del documento

Vive **solo durante la generación**. No se persiste (D-021).

```kotlin
// domain/model/PdfCorpus.kt
data class PdfCorpus(
    val externalKey: String,
    val pdfSha256: String,
    val totalPages: Int,
    val pages: List<PdfPageText>,
) {
    init {
        require(externalKey.isNotBlank())
        require(totalPages > 0)
        require(pages.isNotEmpty())
        require(pages.map(PdfPageText::pageNumber) == (1..pages.size).toList()) {
            "pages must be consecutive and start at 1"
        }
    }

    /** Páginas con texto aprovechable. Es el denominador de la cobertura. */
    val pagesWithText: List<PdfPageText> get() = pages.filter { it.hasUsableText }

    data class PdfPageText(val pageNumber: Int, val text: String) {
        init { require(pageNumber >= 1) { "pages are numbered from 1 outwards" } }

        val usableCharacters: Int get() = text.count(Char::isLetterOrDigit)
        val hasUsableText: Boolean get() = usableCharacters >= MIN_USABLE_CHARACTERS
    }

    companion object { const val MIN_USABLE_CHARACTERS = 20 }
}
```

`PdfPageText` va **anidado** a propósito: la regla 8 de Konsist exige fichero de prueba por cada
clase de dominio de primer nivel, y un tipo que solo transporta un par de campos no tiene
comportamiento propio que probar aparte. El mismo criterio que ya usan `DocumentStatus` y `AppResult`.

**Numeración**: la biblioteca de PDF numera desde 0; aquí y en todo lo que sale hacia fuera, **desde
1** (D-003). La conversión ocurre una sola vez, en el extractor.

---

## 2. El resumen

```kotlin
// domain/model/AiSummary.kt
data class AiSummary(
    val documentTitle: String,
    val documentType: String,
    val issuingBody: String,
    val plainLanguageSummary: String,
    val keyPoints: List<ReferencedText>,
    val affectedParties: List<ReferencedText>,
    val datesAndDeadlines: List<ReferencedDate>,
    val amounts: List<ReferencedAmount>,
    val requiredActions: List<RequiredAction>,
    val appealsOrClaims: List<ReferencedText>,
    val warnings: List<String>,
    val coverage: SummaryCoverage,
) {
    init {
        require(plainLanguageSummary.isNotBlank()) {
            "a summary with nothing to say is not a summary"
        }
    }

    /** Verdadero cuando no hay ninguna sección estructurada que pintar bajo la tarjeta. */
    val hasOnlyPlainSummary: Boolean get() =
        keyPoints.isEmpty() && affectedParties.isEmpty() && datesAndDeadlines.isEmpty() &&
            amounts.isEmpty() && requiredActions.isEmpty() && appealsOrClaims.isEmpty()

    data class ReferencedText(val text: String, val pages: List<Int>)
    data class ReferencedDate(val dateOrPeriod: String, val description: String, val pages: List<Int>)
    data class ReferencedAmount(val amount: String, val concept: String, val pages: List<Int>)
    data class RequiredAction(val action: String, val deadline: String, val pages: List<Int>)

    data class SummaryCoverage(
        val pagesAnalyzed: List<Int>,
        val totalPages: Int,
        val complete: Boolean,
    ) {
        init {
            require(totalPages > 0)
            require(pagesAnalyzed.all { it in 1..totalPages }) {
                "coverage cannot claim pages the document does not have"
            }
            require(!complete || pagesAnalyzed.size == totalPages) {
                "coverage cannot be complete while pages are missing"
            }
        }
        val isPartial: Boolean get() = !complete
    }
}
```

**Reglas que el modelo hace cumplir, no solo documenta:**

| Regla | Dónde | Requisito |
|---|---|---|
| Una lista vacía significa «no consta»; no se rellena con «no aplica» | La pantalla oculta la sección | FR-015 |
| Un resumen sin texto llano no existe | `require` en `AiSummary` | FR-036 |
| Ninguna cobertura cita páginas inexistentes | `require` en `SummaryCoverage` | FR-022 |
| Ninguna cobertura se declara completa a medias | `require` en `SummaryCoverage` | FR-030, SC-012 |

Los `require` de cobertura son la **segunda línea**. La primera es `SummaryValidator` (§5), que
corrige la respuesta del servicio antes de construir el modelo. Que el modelo también lo exija
significa que ningún camino futuro —una lectura del almacén, una prueba mal escrita— puede colar un
resumen que mienta sobre lo que cubre.

---

## 3. El estado de la generación

```kotlin
// domain/model/AiSummaryStatus.kt
sealed interface AiSummaryStatus {

    /** No hay resumen y no se está haciendo nada. */
    data object Idle : AiSummaryStatus

    /** Antes de salir a la red: obtener el documento y sacarle el texto. */
    data class Preparing(val phase: Phase) : AiSummaryStatus {
        enum class Phase { FETCHING_DOCUMENT, EXTRACTING_TEXT }
    }

    /**
     * La consulta está en vuelo. Sin respuesta progresiva: no hay fracción que mostrar (D-011).
     *
     * **Corregido durante la implementación**: lleva la cobertura. FR-028 pide avisar de un resumen
     * parcial *antes* de gastar la petición, y las páginas que caben solo se conocen tras extraer el
     * texto —que exige el documento, que esta aplicación no descarga hasta que alguien lo pide—. Este
     * es el momento más temprano en el que se puede decir la verdad.
     */
    data class Generating(val analysedPages: Int, val totalPages: Int) : AiSummaryStatus {
        val isPartial: Boolean get() = analysedPages < totalPages
    }

    /** Hay que esperar a que se reponga la cuota. Continúa solo. */
    data class WaitingForQuota(val secondsRemaining: Long) : AiSummaryStatus {
        init { require(secondsRemaining >= 0) }
    }

    data class Ready(
        val summary: AiSummary,
        val generatedAtEpochMillis: Long,
        val isStale: Boolean,
    ) : AiSummaryStatus

    data class Failed(val error: AiSummaryError) : AiSummaryStatus
}
```

`Ready.isStale` es lo que hace posible FR-035: el resumen se sigue mostrando, con un aviso de que ya
no corresponde al documento actual, y con la opción de regenerarlo. **No se borra solo.**

```kotlin
// domain/model/AiSummaryError.kt
sealed interface AiSummaryError {
    data object Offline : AiSummaryError              // sin conexión y sin resumen guardado
    data object NoExtractableText : AiSummaryError    // escaneado o vacío. Nunca llega al servicio
    data object EncryptedPdf : AiSummaryError         // protegido
    data class QuotaMinute(val secondsRemaining: Long) : AiSummaryError
    data object QuotaDay : AiSummaryError             // sin reintento inmediato
    data object NotConfigured : AiSummaryError        // credencial ausente o rechazada
    data object InvalidResponse : AiSummaryError      // vacía, mal formada o que no valida
    data object Unknown : AiSummaryError

    /** Si ofrecer o no reintentar (FR-041). */
    val isRetryable: Boolean get() = when (this) {
        Offline, is QuotaMinute, InvalidResponse, Unknown -> true
        NoExtractableText, EncryptedPdf, QuotaDay, NotConfigured -> false
    }
}
```

Ocho casos, uno por cada mensaje de FR-040. Jerarquía propia y **no** casos nuevos en `DomainError`
(D-026): añadirlos allí obligaría a la vista previa del documento y a las demás pantallas a
contemplar situaciones que no les dicen nada.

---

## 4. Lo que se guarda

### Tabla nueva

```kotlin
// data/source/local/AiSummaryEntity.kt
@Entity(tableName = "ai_summaries")
data class AiSummaryEntity(
    @PrimaryKey @ColumnInfo(name = "external_key") val externalKey: String,
    @ColumnInfo(name = "pdf_sha256") val pdfSha256: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "prompt_version") val promptVersion: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: String,
    @ColumnInfo(name = "summary_json") val summaryJson: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "prompt_tokens") val promptTokens: Int,
    @ColumnInfo(name = "completion_tokens") val completionTokens: Int,
    @ColumnInfo(name = "total_tokens") val totalTokens: Int,
    @ColumnInfo(name = "system_fingerprint") val systemFingerprint: String?,
)
```

| Columna | Para qué está |
|---|---|
| `external_key` | Clave primaria. **La publicación**, no un identificador derivado (D-020) |
| `pdf_sha256` | Procedencia: de qué documento salió. Sale de `OfficialDocument.checksum`, que ya se calcula |
| `model_id`, `prompt_version`, `schema_version` | Procedencia: bajo qué condiciones se generó |
| `summary_json` | El resumen serializado. Un documento, no columnas: la forma la fija el esquema y aplanarla sería reimplementarla |
| `created_at` | Cuándo, para poder decirlo |
| `prompt_tokens`, `completion_tokens`, `total_tokens` | Lo que **de verdad** costó, según el servicio. Sirve para calibrar el presupuesto de D-007 |
| `system_fingerprint` | Diagnóstico. Nullable porque el servicio no siempre lo manda |

**No hay índices además de la clave primaria**: se consulta siempre por clave.
**No hay tabla de texto ni de búsqueda de texto completo** (D-021).
**Ninguna columna guarda contenido del documento** salvo el propio resumen, que es lo que se muestra.

### Obsolescencia

Un resumen guardado está **obsoleto**, no ausente, cuando alguna de estas cuatro deja de coincidir:

```kotlin
entity.pdfSha256      != document.checksum       // el boletín republicó el documento
entity.modelId        != AiSummaryConstants.MODEL_ID
entity.promptVersion  != AiSummaryConstants.PROMPT_VERSION
entity.schemaVersion  != AiSummaryConstants.SCHEMA_VERSION
```

Se muestra igualmente, marcado (`Ready.isStale = true`), con la opción de regenerar. Nunca se
descarta por iniciativa propia (FR-035).

**Detalle que importa**: mientras el documento no esté descargado no se conoce su hash, así que las
tres constantes se comprueban siempre y el hash **solo cuando se conoce**. Un resumen guardado se
muestra desde que se abre la pestaña, sin esperar a la red — que es justo lo que exige SC-002.

### DAO, de solo lectura y upsert

```kotlin
// data/source/local/AiSummaryDao.kt
@Dao
interface AiSummaryDao {
    @Query("SELECT * FROM ai_summaries WHERE external_key = :externalKey")
    fun observe(externalKey: String): Flow<AiSummaryEntity?>

    @Query("SELECT * FROM ai_summaries WHERE external_key = :externalKey")
    suspend fun byExternalKey(externalKey: String): AiSummaryEntity?

    @Upsert
    suspend fun upsert(entity: AiSummaryEntity)
}
```

**Sin sentencia de borrado**, como los otros tres DAO del proyecto. Regenerar es un `upsert`, no un
borrado seguido de una inserción. Si aparece un `@Query` de borrado en una revisión, hay que
rechazarlo.

### Migración

```kotlin
@Database(
    entities = [PublicationEntity::class, FeedSyncStateEntity::class, AiSummaryEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
```

Añadir una tabla es automigrable sin escribir SQL. Se conservan las anteriores porque quien se salte
versiones tiene que poder llegar de la 1 a la 4 de una vez. `bocDatabase()` sigue siendo un `build()`
pelado: `fallbackToDestructiveMigration` vaciaría el boletín de quien ya tiene la aplicación, y no
entra ni como último recurso. El esquema `4.json` se versiona: es el material de la migración
siguiente.

**A diferencia de la feature 006, no hay relleno de filas anteriores.** Una tabla nueva nace vacía, y
no tener resumen es el estado normal de una publicación. `PublicationDao.updateColumns` **no se
toca**: sigue sin `saved_at` ni `first_seen_at`, y `SavedPublicationDaoTest` sigue vigilándolo.

### El aviso aceptado

```kotlin
// data/source/local/AiPreferences.kt
interface AiPreferences {
    fun observeNoticeAccepted(): Flow<Boolean>
    suspend fun acceptNotice()
}
```

Respaldado por `SharedPreferences` (D-023): es un booleano por instalación, no un dato del boletín.

---

## 5. Del servicio al modelo

Entre la respuesta y `AiSummary` hay un paso obligatorio.

```kotlin
// data/source/remote/SummaryValidator.kt
fun validate(
    raw: GroqSummaryPayload,
    corpus: PdfCorpus,
    sentPages: List<Int>,
): GroqSummaryPayload?
```

**Corregido durante la implementación**: devuelve el DTO **ya corregido**, no el modelo de dominio. El
motivo es que lo que se guarda tiene que ser el JSON corregido, no el que llegó: así lo almacenado dice
la verdad sobre su cobertura aunque la respuesta original no la dijera, y al releerlo basta con mapear a
dominio sin volver a validar. La conversión la hace `GroqSummaryPayload.toDomain()`.

Devuelve `null` —que la capa superior traduce a `InvalidResponse`— cuando el resumen en lenguaje
llano viene en blanco. En los demás casos **corrige antes de construir**:

| Qué llega | Qué se hace | Requisito |
|---|---|---|
| Un elemento cita la página 40 de un documento de 12 | Se descarta esa página del elemento; si se queda sin ninguna, se conserva el texto sin referencia | FR-022 |
| Un elemento cita una página real que no se envió | Igual: se descarta | FR-022 |
| `coverage.pagesAnalyzed` dice algo distinto de lo enviado | Se **sustituye** por `sentPages` | FR-029 |
| `coverage.complete` es `true` pero faltaron páginas con texto | Se pone a `false` | FR-030, SC-012 |
| `choices` vacío, JSON mal formado, texto llano en blanco | `null` → `InvalidResponse`, ni se muestra ni se guarda | FR-036 |

**Lo que no se hace**: comprobar cada afirmación buscándola literalmente en el texto. Un resumen es
una paráfrasis, y esa comprobación daría falsos negativos constantes. La garantía es la suma del
prompt, la procedencia por páginas y poder abrir el original (D-018).

---

## 6. El presupuesto

```kotlin
// data/source/remote/SummaryBudget.kt
data class SelectedText(
    val text: String,          // ya con marcadores [PÁGINA n]
    val pages: List<Int>,      // las enviadas. Alimenta coverage.pagesAnalyzed
    val isPartial: Boolean,
    val estimatedTokens: Int,
)

fun select(corpus: PdfCorpus): SelectedText
fun estimateTokens(text: String): Int = ceil(text.length / 3.2).toInt()

const val MAX_DOCUMENT_CHARACTERS = 16_000
const val MAX_DOCUMENT_TOKENS = 5_000
const val TARGET_REQUEST_TOKENS = 7_200
```

Se acumulan **páginas enteras** desde la primera mientras quepan en los dos límites. Solo si la
primera página sola ya no cabe se corta dentro de ella, por el último final de párrafo que entre
(D-008). El tope de caracteres es el guardarraíl; la estimación decide el corte.

`isPartial` es lo que permite avisar **antes** de gastar la consulta (FR-028): la pantalla lo consulta
con el documento ya extraído y antes de salir a la red.

---

## 7. Contratos de repositorio

```kotlin
// domain/repository/AiSummaryRepository.kt
interface AiSummaryRepository {
    fun observeSummary(externalKey: String): Flow<AiSummaryStatus>
    suspend fun generate(publication: Publication, force: Boolean): AppResult<AiSummary>
    fun observeNoticeAccepted(): Flow<Boolean>
    suspend fun acceptNotice()
}
```

Misma forma que `DocumentRepository`, y por la misma razón (D-025). Garantías, iguales que en el
resto del proyecto:

- Nada lanza. Los fallos viajan como `AppResult.Failure` o como `AiSummaryStatus.Failed`.
- `observeSummary` **nunca** termina con error: emite `Failed`.
- `CancellationException` se relanza siempre; abandonar la pantalla no es un fallo (FR-006).
- `generate` es **idempotente mientras hay una en curso**: dos llamadas para la misma clave comparten
  una consulta en lugar de gastar cuota dos veces (FR-005). Mismo `Mutex` + `CompletableDeferred` que
  ya usa `DocumentRepositoryImpl`.
- `generate` **no consulta el servicio** si el documento no tiene texto utilizable (FR-012).

Cuatro casos de uso, uno por operación, siguiendo la norma del proyecto:
`ObserveAiSummaryUseCase`, `GenerateAiSummaryUseCase`, `ObserveAiNoticeAcceptedUseCase` y
`AcceptAiNoticeUseCase`.

---

## 8. El estado de la pantalla

```kotlin
// ui/detail/PublicationDetailUiState.kt
data class PublicationDetailUiState(
    val publication: Publication? = null,
    val section: BocSection? = null,
    val isMissing: Boolean = false,
    val selectedTab: DetailTab = DetailTab.DOCUMENT,
    val document: DocumentStatus = DocumentStatus.Absent,
    val share: ShareState = ShareState.Idle,
    val isSaved: Boolean = false,
    val saveFailed: Boolean = false,
    // Nuevo en esta feature
    val summary: AiSummaryStatus = AiSummaryStatus.Idle,
    val aiNoticeAccepted: Boolean = false,
    val aiNoticePending: Boolean = false,
) {
    val isLoading: Boolean get() = publication == null && !isMissing
}
```

`aiNoticePending` es la hoja abierta esperando decisión. Se distingue de `aiNoticeAccepted` porque
son cosas distintas: una es lo que esta instalación ya sabe, la otra es lo que está ocurriendo ahora.

Eventos nuevos del modelo de pantalla, todos funciones públicas:

| Evento | Qué hace |
|---|---|
| `onGenerateSummary()` | Si el aviso no se ha aceptado, abre la hoja; si sí, genera |
| `onAiNoticeAccepted()` | Recuerda la aceptación y continúa con la generación |
| `onAiNoticeDismissed()` | Cierra sin enviar nada (FR-044) |
| `onRegenerateSummary()` | Genera con `force = true` |
| `onCopySummary()` / `onShareSummary()` | Con la advertencia por delante (FR-025, D-028) |

El texto lo construye `summaryAsSharableText(disclaimer)`, que devuelve `null` cuando no hay resumen
que entregar: así la pantalla no copia una cadena vacía.

**No hay `onSummaryTabShown()`.** Es deliberado: mostrar la pestaña no genera nada (FR-002). Lo único
que ocurre al abrirla es que se observa lo que ya haya guardado.

`DetailTab` pierde `isComingSoon` (D-029): con `AI_SUMMARY` implementada, esa propiedad sería siempre
falsa, y una afirmación que no puede ser cierta solo sirve para confundir.

---

## 9. Rutas

```kotlin
@Serializable data class PdfViewer(val externalKey: String, val page: Int = 0) : Route
```

El único cambio de navegación. Valor por defecto 0 para que las llamadas existentes —la barra de
acciones del detalle— sigan valiendo sin tocarse. El visor ya sabe posicionarse: usa `scrollToPage()`
para restaurar la página tras rotar, y aquí se reutiliza para la página inicial (D-027).

**Cuidado heredado de la feature 006**: navegar con `restoreState = true` se traga el argumento de la
ruta. Aquí no aplica —el visor no es un destino de la barra inferior— pero no debe reintroducirse ese
patrón al añadir el parámetro.
