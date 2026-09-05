# Data Model: El documento se envía entero, no su texto

**Feature**: `010-gemini-sdk-oficial` | **Fase**: 1 | **Fecha**: 5 de septiembre de 2026

Este documento describe qué formas cambian, cuáles no, y por qué. La justificación de cada elección
está en [`research.md`](./research.md); aquí solo se dice **qué queda**.

---

## 1. Lo que NO cambia de forma, y por qué importa decirlo

| Tipo | Dónde vive | Por qué no cambia |
|---|---|---|
| `AiSummary` y sus cinco tipos anidados | `domain/model/` | El resumen que se muestra es el mismo. Cambia cómo se obtiene, no qué es |
| `SummaryPayload` y los DTO del payload | `data/source/remote/SummaryPayloadDtos.kt` | Es lo que se serializa en la columna `summary_json`. **Ni un nombre de propiedad puede cambiar**, o cada fila ya guardada deja de decodificarse. Hay prueba de regresión |
| `SummarySchema` | `data/source/remote/` | Se entrega tal cual a `responseJsonSchema`, que acepta JSON crudo (D-211). El orden de propiedades sigue siendo carga útil y `SummarySchemaTest` lo sigue vigilando |
| `GeminiRateLimitCoordinator` y `QuotaVerdict` | `data/source/remote/` | El proveedor sigue sin informar de cuota. El contador propio no depende del transporte |
| `GeminiApiKeyProvider` | `data/source/remote/` | Sigue leyendo la credencial de `BuildConfig` y sigue ocultándola en `toString()` |
| `AiSummaryEntity`, `AiSummaryDao`, `BocDatabase` | `data/source/local/` | **La base sigue en la versión 4.** Ninguna migración, ninguna columna, ningún esquema exportado nuevo. Las tres columnas de procedencia ya son agnósticas del proveedor y del transporte, y esta feature es la segunda vez que se cobra ese diseño |
| Los cuatro casos de uso existentes | `domain/usecase/` | Pasan por el repositorio y el contrato del repositorio solo **crece** |
| Los diez componibles de la pestaña | `ui/detail/component/` | Siguen recibiendo `AiSummaryStatus` y devolviendo eventos |

---

## 2. Lo que desaparece

Cinco tipos y todo lo que colgaba de ellos:

```kotlin
// domain/model/PdfCorpus.kt                          BORRADO
data class PdfCorpus(externalKey, pdfSha256, totalPages, pages: List<PdfPageText>) {
    val hasUsableText: Boolean            // el juicio que ahora hace el servicio
    companion object { const val MIN_USABLE_CHARACTERS }
}

// data/source/local/PdfTextExtractor.kt              BORRADO
interface PdfTextExtractor { suspend fun extract(localPath, externalKey, pdfSha256): PdfExtractionResult }
sealed interface PdfExtractionResult { Success | NoExtractableText | EncryptedPdf | Failure }

// data/source/local/PdfTextNormalizer.kt             BORRADO
// data/source/remote/DocumentText.kt                 BORRADO  (RenderedDocument, MAX_CHARACTERS)
// data/source/remote/GeminiDtos.kt                 REESCRITO, no borrado
//   Se pensó borrarlo entero porque la librería oficial iba a tipar el cable. La librería no se
//   puede usar en Android (D-227), así que el fichero se queda y cambia de contenido: fuera los
//   tipos de la Interactions API, dentro los de la Files API y generateContent.
//   GeminiUsage sí se va de aquí: nunca fue un tipo de cable —es parte de la firma de
//   GeminiSummaryResult.Success— y se muda a GeminiSummaryDataSource.kt como SummaryUsage.
```

`PdfTextNormalizer` se lleva consigo una cicatriz que conviene no perder de vista: eliminaba los
sustitutos UTF-16 sin pareja porque uno solo hacía inválido el JSON del cuerpo y el servicio rechazaba
la petición entera con un 400. **Ese problema deja de existir por construcción**: ya no hay texto
nuestro en el cuerpo, sino un fichero binario y unos metadatos que salen de la base de datos.

---

## 3. Lo que nace

### 3.1 Contar páginas, y solo eso

```kotlin
// data/source/local/PdfPageCounter.kt
interface PdfPageCounter {
    suspend fun pageCount(localPath: String): PageCountResult
}

sealed interface PageCountResult {
    data class Success(val totalPages: Int) : PageCountResult
    data object Encrypted : PageCountResult
    data class Failure(val cause: Throwable) : PageCountResult
}
```

Implementado por `AndroidxPdfPageCounter`, que abre el documento en el **proceso aislado** —igual que
hacía el extractor y que hace el visor— y devuelve `document.pageCount`. `PdfPasswordException` →
`Encrypted`. Cualquier otro `Throwable` → `Failure`, tras `crashReporter.log`.

Ya no recibe `externalKey` ni `pdfSha256`: los tomaba para construir el `PdfCorpus`, que ya no existe.

### 3.2 El documento subido

```kotlin
// data/source/remote/AiDocumentUploader.kt
interface AiDocumentUploader {
    suspend fun upload(localPath: String, displayName: String): UploadResult
    suspend fun delete(remoteName: String)
}

/** Lo que la petición necesita para referenciar un documento ya subido. */
data class UploadedDocument(
    val remoteName: String,   // el identificador con el que se borra
    val fileUri: String,      // la referencia que viaja en la petición
    val mimeType: String,
)

sealed interface UploadResult {
    data class Success(val document: UploadedDocument) : UploadResult
    data class Rejected(val reason: GeminiRefusal) : UploadResult
}
```

`Rejected` reutiliza `GeminiRefusal` a propósito: subir y generar fallan por las mismas razones
—sin credencial, sin red, sin cuota, error del servicio— y tener dos vocabularios para lo mismo
obligaría a un segundo mapa de traducción sin ganar nada. El único caso propio de la subida, que el
servicio no consiga procesar el fichero, entra como `GeminiRefusal.Malformed`, que es literalmente lo
que significa: lo que se mandó no sirve.

Implementado por `OkHttpGeminiDocumentUploader`, que habla el protocolo de subida reanudable a mano
sobre el cliente compartido —tres llamadas, con los bytes **transmitidos** desde el disco y no
cargados en memoria— porque la librería oficial no se puede usar en Android (D-227).

### 3.3 La sesión

```kotlin
// data/source/remote/AiDocumentSessionStore.kt
class AiDocumentSessionStore(
    private val uploader: AiDocumentUploader,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) {
    /** Idempotente. Reutiliza si coinciden clave y checksum; si no, releva. */
    suspend fun open(externalKey: String, pdfSha256: String, localPath: String, displayName: String): SessionResult

    /** Dispara y olvida, sobre un ámbito propio: quien la llama es onCleared(). */
    fun release(externalKey: String)

    data class Session(val externalKey: String, val pdfSha256: String, val document: UploadedDocument)
}

sealed interface SessionResult {
    data class Ready(val session: AiDocumentSessionStore.Session) : SessionResult
    data class Rejected(val reason: GeminiRefusal) : SessionResult
}
```

**Invariantes** —cada una es una prueba de `AiDocumentSessionStoreTest`—:

1. **Como mucho una sesión viva en todo el proceso.** Es lo que hace comprobable FR-010.
2. **`open` con la misma clave y el mismo checksum no sube nada.** FR-008, SC-005.
3. **`open` con otra clave retira la anterior antes de subir la nueva.** FR-010.
4. **`open` con la misma clave pero otro checksum releva**, porque el documento del boletín cambió.
5. **Dos `open` concurrentes producen una sola subida**, garantizado por un `Mutex`. No es teórico:
   en la feature 011 el resumen y la primera pregunta pueden pedirla a la vez.
6. **`release` con una clave que no es la de la sesión actual no hace nada.** Evita que un
   `onCleared()` tardío se lleve por delante la sesión de la publicación siguiente.
7. **Un `Rejected` no deja sesión abierta.**

El ámbito propio —`CoroutineScope(SupervisorJob() + dispatchers.io)`— existe por una razón sola y
concreta: en `onCleared()` el `viewModelScope` ya está cancelado, y lanzar el borrado ahí no borraría
nada.

### 3.4 El caso de uso que la suelta

```kotlin
// domain/usecase/ReleaseAiDocumentSessionUseCase.kt
class ReleaseAiDocumentSessionUseCase(private val repository: AiSummaryRepository) {
    operator fun invoke(externalKey: String) = repository.releaseDocumentSession(externalKey)
}
```

No es `suspend` y no devuelve `AppResult`, y ambas cosas son deliberadas: es una limpieza que se
dispara desde un punto del ciclo de vida sin corrutina viva, y su fallo no tiene a quién informar
—el servicio caduca el fichero de todos modos (FR-011)—. Lo que sí hace es dejar rastro en el
registro.

---

## 4. Lo que cambia de firma

### 4.1 El contrato del repositorio, que solo crece

```kotlin
interface AiSummaryRepository {
    fun observeSummary(externalKey: String): Flow<AiSummaryStatus>
    suspend fun generate(publication: Publication, force: Boolean): AppResult<AiSummary>
    fun observeNoticeAccepted(): Flow<Boolean>
    suspend fun acceptNotice()
    fun releaseDocumentSession(externalKey: String)                     // NUEVO
}
```

### 4.2 La fuente de datos del resumen

```kotlin
interface GeminiSummaryDataSource {
    suspend fun summarise(
        system: String,
        user: String,
        document: UploadedDocument,                                     // NUEVO
    ): GeminiSummaryResult
}
```

`GeminiSummaryResult` y **los siete casos de `GeminiRefusal` no cambian**. Sí cambia de dónde sale
`Success.systemFingerprint`: antes venía del proveedor con ese nombre, ahora se rellena con
`GenerateContentResponse.modelVersion`, que es el mismo tipo de dato —qué versión exacta contestó— y
va a la misma columna nullable.

### 4.3 El validador y el prompt

```kotlin
class SummaryValidator {
    fun validate(raw: SummaryPayload, totalPages: Int): SummaryPayload?    // antes: (raw, document, totalPages)
}

class SummaryPromptFactory {
    fun systemMessage(): String
    fun userMessage(publication: Publication, totalPages: Int): String     // antes: (publication, document, totalPages)
}
```

El validador ya no recibe qué páginas se enviaron porque **se envían todas**: el conjunto de páginas
admisibles pasa a ser `1..totalPages`. El resto de su trabajo —recortar la prosa a la última frase
completa, filtrar entradas en blanco, capar cada lista a diez con su aviso, y **recalcular `coverage`
en vez de creerse el que declara el modelo**— no cambia ni una línea.

La plantilla del prompt pierde el hueco `{{documentWithPageMarkers}}` y conserva los otros cinco. La
regla de sustituir **después** de `trimIndent()` sigue en pie y sigue teniendo su prueba.

---

## 5. Lo que cambia en el dominio

### 5.1 Las fases de preparación

```kotlin
data class Preparing(val phase: Phase) : AiSummaryStatus {
    enum class Phase { FETCHING_DOCUMENT, UPLOADING_DOCUMENT }   // antes: EXTRACTING_TEXT
}
```

### 5.2 El estado de generación pierde una mitad

```kotlin
data class Generating(val totalPages: Int) : AiSummaryStatus     // antes: (analysedPages, totalPages)
```

`analysedPages` e `isPartial` desaparecen, y con ellos el aviso **previo** de lectura parcial
(`ai_summary_partial_before`). El motivo es que ya no se elige qué páginas caben: se envía el
documento entero, así que un anuncio de «se analizarán las 6 primeras» sería siempre falso. Mantener
el campo con `analysedPages == totalPages` habría dejado una rama de interfaz y su prueba vivas sin
poder ejecutarse nunca, que es precisamente lo que el principio V prohíbe.

**Lo que sí sobrevive es la cobertura, y por un motivo que no es el que parece.** `SummaryCoverage`,
`isPartial` y el plural `ai_summary_partial_after` se quedan, pero **no** porque un resumen nuevo
pueda ser parcial: no puede, porque va el documento entero y el validador sigue calculando la
cobertura desde lo que **se envió** y nunca desde lo que el modelo declara. Se quedan porque **las
filas guardadas antes de esta feature sí son parciales**, siguen mostrándose —marcadas como
obsoletas, nunca borradas— y la pantalla tiene que saber decirlo. Quitar el tipo habría roto la
lectura de lo ya almacenado, que es justo lo que FR-014 prohíbe.

### 5.3 Un error se sustituye por otro

```kotlin
sealed interface AiSummaryError {
    data object Offline
    data object UnreadableDocument      // antes: NoExtractableText
    data object EncryptedPdf
    data class QuotaMinute(secondsRemaining)
    data object QuotaDay
    data object NotConfigured
    data object InvalidResponse
    data object Unknown

    val isRetryable get() = when (this) {
        Offline, is QuotaMinute, InvalidResponse, Unknown -> true
        UnreadableDocument, EncryptedPdf, QuotaDay, NotConfigured -> false
    }
}
```

Ocho casos antes y ocho después. `NoExtractableText` significaba «este PDF no tiene texto que podamos
sacar» y deja de poder ocurrir; `UnreadableDocument` significa «el servicio no ha podido leer este
documento» y empieza a poder ocurrir. Los dos son no reintentables, así que `isRetryable` conserva su
reparto.

### 5.4 Las constantes de procedencia

```kotlin
object AiSummaryConstants {
    const val MODEL_ID: String = "<se fija tras el quickstart §3 bis>"   // CAMBIA
    const val PROMPT_VERSION: String = "boc-summary-es-v5"               // CAMBIA (era v4)
    const val SCHEMA_VERSION: String = "boc-summary-schema-v3"           // NO cambia
}
```

El esquema no cambia, así que su versión tampoco. Con dos de las tres distintas, **todo lo guardado
queda obsoleto** —no borrado— y la pantalla lo marca. Es FR-014 y FR-015.

---

## 6. La tubería, antes y después

```
ANTES  ensureLocalCopy → extract → normalise → render(marcas de página) → summarise(texto) → validate → store
                          └──────────── cinco clases y cuatro pruebas ────────────┘

AHORA  ensureLocalCopy → pageCount → session.open(sube) → summarise(referencia) → validate → store
                          └── una clase ──┘   └── dos clases ──┘
```

El orden importa: **`pageCount` va antes que la subida**, no después. Es lo que hace que un documento
protegido con contraseña no llegue a salir del dispositivo (FR-004, SC-007).

Y una cosa que no cambia y que es la más fácil de romper: **el resumen se cachea antes de todo esto**.
Si hay una fila guardada y no se pidió `force`, `generate` devuelve sin tocar nada — ni el contador de
páginas, ni la subida, ni el servicio.

---

## 7. La preferencia del aviso

```kotlin
private const val KEY_NOTICE_ACCEPTED = "ai_notice_accepted_v3"   // era _v2
```

Una constante. Quien había aceptado el `_v2` no tiene el `_v3`, así que el aviso reescrito se lee una
vez y solo una (FR-032, FR-033, SC-008). La clave vieja **no se lee, no se migra y no se borra**:
leerla sería dar por aceptado un texto que nadie ha visto, y borrarla no aporta nada.

---

## 8. Del servicio a nuestro modelo

| Lo que llega | Adónde va | Nota |
|---|---|---|
| La primera parte de la respuesta **que no lleve `thought: true`** | JSON del que sale `SummaryPayload` | Se filtra por la marca y **nunca por posición**: el paso de razonamiento llega siempre delante, así que quedarse con la primera parte habría fallado el cien por cien de las veces (009 D-117) |
| `candidates[0].finishReason` | `GeminiRefusal.BlankSummary` si no es `STOP` | `MAX_TOKENS` deja de deducirse de un JSON que no parsea: viene dicho |
| `usageMetadata.promptTokenCount` | `SummaryUsage.totalInputTokens` → `AiSummaryEntity.promptTokens` | |
| `usageMetadata.thoughtsTokenCount` | `SummaryUsage.totalThoughtTokens` | No se persiste. Es el diagnóstico que dice si el razonamiento está de verdad apagado |
| `usageMetadata.candidatesTokenCount` | `AiSummaryEntity.completionTokens` | |
| `usageMetadata.totalTokenCount` | `AiSummaryEntity.totalTokens` | |
| `modelVersion` | `AiSummaryEntity.systemFingerprint` | Misma columna nullable, mismo tipo de dato |
| HTTP 401/403 | `GeminiRefusal.NotConfigured` | |
| HTTP 429 | `QuotaMinute(s)` o `QuotaDay` | Los segundos salen de la cabecera `Retry-After` primero y del detalle `RetryInfo` del cuerpo después (009 D-109) |
| HTTP 5xx | `HttpError(code)` | Reintentable |
| `IOException` | `Network`, tras `ensureActive()` | La trampa de la cancelación sigue viva con cualquier cliente (D-218) |
| `FileState.FAILED` o tope de sondeo | `GeminiRefusal.Malformed` → `AiSummaryError.UnreadableDocument` | |
