# Data Model: Resumen IA con proveedor nuevo

**Feature**: `009-resumen-gemini` | **Fase**: 1 | **Fecha**: 4 de septiembre de 2026

Este documento describe **qué cambia de forma y qué no**. Está escrito en ese orden a propósito: lo
que no cambia es la mayor parte, y saberlo es lo que evita tocar de más.

---

## 1. Lo que NO cambia de forma

Ninguno de estos tipos se modifica. Se listan porque la tentación al cambiar de proveedor es
«aprovechar y retocar», y aquí no hay nada que retocar.

| Tipo | Dónde | Por qué no cambia |
|---|---|---|
| `AiSummary` y sus 5 tipos anidados | `domain/model` | El resumen es el mismo; lo que cambia es quién lo escribe |
| `AiSummaryError` (8 casos) | `domain/model` | Los ocho mensajes de FR-040 de la 007 siguen siendo los ocho mensajes |
| `AiSummaryStatus` (6 casos) | `domain/model` | Las fases son las mismas, incluido `WaitingForQuota` |
| `PdfCorpus` y `PdfPageText` | `domain/model` | La extracción no cambia |
| `AiSummaryRepository` | `domain/repository` | El contrato es idéntico |
| Los 4 casos de uso | `domain/usecase` | Idénticos |
| `AiSummaryEntity`, `AiSummaryDao` | `data/source/local` | Ver §7 |
| `BocDatabase` | `data/source/local` | **Versión 4, sin migración** (D-114) |
| `PdfTextExtractor`, `AndroidxPdfTextExtractor`, `PdfTextNormalizer` | `data/source/local` | Ver D-116 |
| Los 10 componibles de `ui/detail` y el `ViewModel` | `ui/detail` | Ni un fichero |

`AiSummaryConstants` sí cambia, pero solo de **valor**, no de forma:

```kotlin
const val MODEL_ID: String       = "gemini-3.5-flash-lite"   // antes "qwen/qwen3.8-27b"
const val PROMPT_VERSION: String = "boc-summary-es-v4"       // antes "boc-summary-es-v3"
const val SCHEMA_VERSION: String = "boc-summary-schema-v3"   // antes "…-schema-v2"
```

Sigue siendo un `object` y no una `class`: la octava regla de Konsist exige fichero de prueba para
toda **clase** top-level de dominio, y estas tres constantes no tienen comportamiento que probar.

---

## 2. El cable nuevo — `GeminiDtos.kt`

Sustituye a la mitad de cable de `GroqDtos.kt`. Tipos de capa `data`; nunca cruzan a `ui`.

### Petición

```kotlin
@Serializable
data class GeminiInteractionRequest(
    val model: String,
    @SerialName("system_instruction") val systemInstruction: String,
    val input: List<GeminiInputContent>,
    /** Retención cero. El valor por defecto del servicio es `true` (D-107). */
    val store: Boolean = false,
    @SerialName("generation_config") val generationConfig: GeminiGenerationConfig,
    @SerialName("response_format") val responseFormat: GeminiResponseFormat,
)

@Serializable
data class GeminiInputContent(val type: String, val text: String)

@Serializable
data class GeminiGenerationConfig(
    /** El valor por defecto del servicio es "medium", y se factura (D-106). */
    @SerialName("thinking_level") val thinkingLevel: String = "minimal",
    @SerialName("max_output_tokens") val maxOutputTokens: Int,
)

@Serializable
data class GeminiResponseFormat(
    val type: String = "text",
    @SerialName("mime_type") val mimeType: String = "application/json",
    /** El esquema entra tal cual desde `SummarySchema`, sin modelar. */
    val schema: JsonElement,
)
```

**Nada de `temperature`, `top_p` ni `top_k`.** No es un olvido: el modelo no admite valores propios y
la documentación pide expresamente no cambiarlos (D-106).

**`encodeDefaults = true` sigue siendo obligatorio** en el `Json` del data source. Sin él, `store` y
`thinkingLevel` —que valen exactamente sus valores por defecto de Kotlin— no se serializarían, y los
valores por defecto del **servicio** son los contrarios en ambos casos: retención activada y
razonamiento medio. Es la misma trampa que en la 007 dejaba sin enviar `stream` y `reasoning_effort`.

### Respuesta

```kotlin
@Serializable
data class GeminiInteraction(
    val id: String? = null,
    val model: String? = null,
    /** completed | incomplete | budget_exceeded | failed | cancelled | in_progress | … */
    val status: String? = null,
    val steps: List<GeminiStep> = emptyList(),
    val usage: GeminiUsage? = null,
)

@Serializable
data class GeminiStep(
    /** Interesa `model_output`. También pueden venir `model_thoughts` y otros. */
    val type: String? = null,
    val content: List<GeminiContentPart> = emptyList(),
)

@Serializable
data class GeminiContentPart(val type: String? = null, val text: String? = null)

@Serializable
data class GeminiUsage(
    @SerialName("total_input_tokens") val totalInputTokens: Int = 0,
    @SerialName("total_output_tokens") val totalOutputTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    /** Debe ser bajo o cero. Si crece, `thinking_level` no se está aplicando (D-106). */
    @SerialName("total_thought_tokens") val totalThoughtTokens: Int = 0,
)
```

**Dónde vive el texto**: `steps` → el primer paso con `type == "model_output"` → `content` → la
primera parte con texto → `text`. Ése es el JSON del esquema, que se deserializa **en un segundo
paso** exactamente como hoy. Se busca el paso por su `type` y no se toma `steps[0]`: la respuesta
puede traer pasos de razonamiento delante, y depender del orden sería depender de algo que el
proveedor no promete.

Todos los campos llevan valor por defecto, por el mismo motivo que en la 007: un cuerpo truncado o
inesperado debe fallar en la **validación**, con una razón que se pueda registrar, y no en la
deserialización con una traza.

### Error

```kotlin
@Serializable
data class GeminiErrorEnvelope(val error: GeminiError? = null)

@Serializable
data class GeminiError(
    val message: String? = null,
    val status: String? = null,
    /** Puede traer un `RetryInfo` con `retryDelay`. Se lee sin modelar (D-109). */
    val details: List<JsonObject> = emptyList(),
)
```

**No se deserializa el `code`**: el código ya lo da la respuesta HTTP, y la documentación no es
consistente sobre si aquí llega como número o como cadena. Modelar un campo que no se necesita solo
crea una forma más de fallar al deserializar.

---

## 3. El payload, con nombre agnóstico — `SummaryPayloadDtos.kt`

Es la otra mitad de `GroqDtos.kt`, extraída a su propio fichero con nombres que no mencionan al
proveedor, porque **este formato es nuestro** (D-111).

```kotlin
@Serializable
data class SummaryPayload(               // antes GroqSummaryPayload
    val documentTitle: String = "",
    val documentType: String = "",
    val issuingBody: String = "",
    val plainLanguageSummary: String = "",
    val keyPoints: List<ReferencedTextDto> = emptyList(),
    val affectedParties: List<ReferencedTextDto> = emptyList(),
    val datesAndDeadlines: List<ReferencedDateDto> = emptyList(),
    val amounts: List<ReferencedAmountDto> = emptyList(),
    val requiredActions: List<RequiredActionDto> = emptyList(),
    val appealsOrClaims: List<ReferencedTextDto> = emptyList(),
    val warnings: List<String> = emptyList(),
    val coverage: CoverageDto = CoverageDto(),
)
// ReferencedTextDto, ReferencedDateDto, ReferencedAmountDto, RequiredActionDto, CoverageDto:
// sin un solo cambio
fun SummaryPayload.toDomain(): AiSummary   // sin un solo cambio en el cuerpo
```

> ### La regla intocable de este fichero
>
> **Ni un nombre de propiedad puede cambiar.** Este tipo es lo que se serializa en la columna
> `summary_json` de `ai_summaries` y lo que se vuelve a leer en `AiSummaryEntity.decode()`. kotlinx
> serializa por nombre de propiedad, así que **renombrar la clase es inocuo** y renombrar un campo
> dejaría ilegible todo lo guardado por versiones anteriores. Lleva prueba de regresión: se decodifica
> un `summary_json` escrito con el nombre antiguo y tiene que producir el mismo resumen.
>
> El orden de los campos aquí **no** coincide con el del esquema, y eso también es a propósito: en el
> dominio `plainLanguageSummary` es el cuarto porque es lo primero que se lee en pantalla; en el
> esquema es el duodécimo porque es lo último que conviene generar. Son dos órdenes distintos con dos
> motivos distintos, y ninguno debe alinearse con el otro.

---

## 4. El esquema — `SummarySchema.kt`

Renombrado desde `GroqSummarySchema`. **El objeto `schema` se conserva verbatim**; se retira el
envoltorio de OpenAI y se añade `maxItems` (D-105, D-112).

```
ANTES                                    DESPUÉS
{                                        {
  "type": "json_schema",         ─┐        "type": "object",
  "json_schema": {                │        "additionalProperties": false,
    "name": "boc_ai_summary",     ├─ se     "properties": { …las 12, mismo orden… },
    "strict": true,               │  va     "required": [ …las 12, mismo orden… ],
    "schema": {  ←── esto es lo  ─┘        "$defs": { …las 5, sin cambios… }
      …                                  }
```

Las doce propiedades conservan **su orden exacto**, con `plainLanguageSummary` la última y su
`maxLength: 900`. Esa es la decisión D-030 de la feature 007, pagada con una medición en un móvil real,
y sigue valiendo: Gemini respeta el orden de declaración de forma implícita.

Lo único que se añade son seis `maxItems`:

```json
"keyPoints":         { "type": "array", "maxItems": 10, "items": { "$ref": "#/$defs/referencedText" } },
"affectedParties":   { "type": "array", "maxItems": 10, "items": { "$ref": "#/$defs/referencedText" } },
"datesAndDeadlines": { "type": "array", "maxItems": 10, "items": { "$ref": "#/$defs/referencedDate" } },
"amounts":           { "type": "array", "maxItems": 10, "items": { "$ref": "#/$defs/referencedAmount" } },
"requiredActions":   { "type": "array", "maxItems": 10, "items": { "$ref": "#/$defs/requiredAction" } },
"appealsOrClaims":   { "type": "array", "maxItems": 10, "items": { "$ref": "#/$defs/referencedText" } },
```

`warnings` **no** lleva tope: es donde viaja el aviso de que una sección se recortó, y ponerle un
límite podría truncar justamente la explicación de un truncamiento.

**Plan B si el servicio rechaza el esquema** (D-105), en este orden y sin cambiar ningún nombre de
campo: aplanar las cinco definiciones en línea; y si además no respetara el orden, añadir
`propertyOrdering` con las doce propiedades.

---

## 5. El documento que se envía — `DocumentText.kt`

Renombrado desde `SummaryBudget`, y reducido. Se va la aritmética de tokens; se queda el guardarraíl
y el renderizado con marcadores de página (D-104).

```kotlin
object DocumentText {

    /**
     * El guardarraíl. ~109.000 tokens medidos —4,39 car./token contra el servicio real—, el 10 %
     * de la ventana de entrada del modelo. Cubre unas 190 páginas de boletín. En uso normal no se
     * alcanza nunca. Pendiente: si el límite de tokens por minuto del plan gratuito estuviera por
     * debajo de 110.000, hay que bajarlo (T001a).
     */
    const val MAX_CHARACTERS: Int = 480_000

    const val PAGE_MARKER_PREFIX: String = "[PÁGINA "

    /** Toma páginas enteras desde la primera mientras quepan. */
    fun render(corpus: PdfCorpus): RenderedDocument
}

data class RenderedDocument(
    val text: String,
    /** Exactamente las páginas que se enviaron. Alimenta `coverage.pagesAnalyzed`. */
    val pages: List<Int>,
    /** Si el documento no entró entero. Se consulta **antes** de la petición (FR-005). */
    val isPartial: Boolean,
)
```

**Qué se conserva del algoritmo, tal cual**: el bucle que toma **páginas enteras** mientras quepan
—una cita de página solo significa algo si la página fue enviada completa—; el corte dentro de la
primera página, y solo de la primera, en el último límite natural del texto que quepa; la inclusión
de páginas casi vacías, para que la cobertura signifique «páginas 1 a N de M» y no una lista con
agujeros; y el `break` en cuanto una página no cabe.

**Qué desaparece**: `MAX_DOCUMENT_TOKENS`, `TARGET_REQUEST_TOKENS`, `CHARACTERS_PER_TOKEN`,
`estimateTokens()`, el campo `estimatedTokens` de `SelectedText`, y `PROMPT_OVERHEAD_TOKENS = 700` de
`AiSummaryRepositoryImpl`. La condición `fits()` pasa de dos comprobaciones a una.

---

## 6. El coordinador de cuota — `GeminiRateLimitCoordinator.kt`

Reescrito. Mismo oficio, otra fuente de verdad (D-108, D-109).

```kotlin
class GeminiRateLimitCoordinator(
    private val time: TimeProvider,
    private val random: RandomProvider,
) {
    /** Una petición a la vez en toda la aplicación. Se conserva. */
    suspend fun <T> serialised(block: suspend () -> T): T

    /** Sin argumentos: ya no hay tokens que estimar. */
    fun verdict(): QuotaVerdict

    /** Se apunta al salir, no al volver: lo que consume cuota es pedir. */
    fun recordRequest()

    /** Un 429 manda sobre cualquier cuenta propia. Devuelve el veredicto resultante. */
    fun recordExhaustion(retryAfterSeconds: Long): QuotaVerdict

    /** Se conserva íntegro: 1s / 2s / 4s con hasta 500 ms de dispersión. */
    fun backoffMillis(attempt: Int): Long

    companion object {
        /** Pendientes de confirmar en el panel del proveedor (D-115). Cambiarlos es una línea. */
        const val REQUESTS_PER_MINUTE: Int = 30
        const val REQUESTS_PER_DAY: Int = 1_500

        /** Por encima de este retraso, lo agotado es de escala diaria y no del minuto. */
        const val DAY_SCALE_THRESHOLD_SECONDS: Long = 900

        const val HEADER_RETRY_AFTER = "retry-after"
        const val DEFAULT_RETRY_SECONDS: Long = 60

        /** Acepta `56` y `56s`, que es la forma de una duración de protobuf. Nunca lanza. */
        fun parseRetryDelaySeconds(raw: String?): Long?
    }
}

sealed interface QuotaVerdict {          // sin un solo cambio
    data object Allowed : QuotaVerdict
    data class WaitMinute(val secondsRemaining: Long) : QuotaVerdict
    data object ExhaustedDay : QuotaVerdict
}
```

**Estado interno**: dos listas de marcas de tiempo, `minuteWindow` y `dayWindow`, más un
`exhaustedUntilMillis` con su escala. `verdict()` poda las ventanas contra `time.nowMillis()` y
compara sus tamaños con las dos constantes.

| Situación | Veredicto |
|---|---|
| Hay una exhaustión de escala diaria vigente | `ExhaustedDay` |
| Hay una exhaustión de escala corta vigente | `WaitMinute(segundos que faltan)` |
| La ventana de 24 h llegó a `REQUESTS_PER_DAY` | `ExhaustedDay` |
| La ventana de 60 s llegó a `REQUESTS_PER_MINUTE` | `WaitMinute(hasta que caduque la más antigua)` |
| Cualquier otro caso | `Allowed` |

**Ventana diaria deslizante de 24 horas y no día de calendario**: el proveedor repone en su zona
horaria, no en la del móvil. Una ventana deslizante nunca permite más de lo que él permite, sea cual
sea su momento de reposición, y no exige suponer ninguna zona. El mensaje «Se ha alcanzado el límite
de resúmenes de hoy. Inténtalo mañana» sigue siendo cierto bajo esa regla.

**Sin persistencia, a conciencia** (D-108): un reinicio olvida la cuenta. Mil quinientas peticiones
en un día son una cada cincuenta y siete segundos durante veinticuatro horas seguidas: no es
alcanzable pulsando un botón. La ventana del minuto, que sí protege de una ráfaga, no necesita
sobrevivir a un reinicio porque un reinicio tarda más que el minuto.

**Se retira** `parseDurationMillis` con sus tres formas y las cinco constantes de cabecera de Groq.
Lo que queda es `parseRetryDelaySeconds`, que solo entiende un número y un número con `s`.

---

## 7. Lo que se guarda — sin cambios, y una clave que sube de versión

`AiSummaryEntity` y `AiSummaryDao` **no se tocan**. La tabla ya es agnóstica:

| Columna | De dónde sale ahora |
|---|---|
| `model_id`, `prompt_version`, `schema_version` | `AiSummaryConstants`, con los valores nuevos |
| `summary_json` | `SummaryPayload` ya corregido por el validador, mismos nombres de campo |
| `prompt_tokens` | `usage.total_input_tokens` |
| `completion_tokens` | `usage.total_output_tokens` |
| `total_tokens` | `usage.total_tokens` |
| `system_fingerprint` | **`null`.** Gemini no tiene equivalente, y la columna ya es nullable |
| `pdf_sha256`, `created_at`, `external_key` | Igual que hoy |

`system_fingerprint` a `null` no necesita nada nuevo: `AiSummaryDaoTest` ya tiene una prueba para el
caso de la huella ausente. No se guarda el `id` de la interacción en su lugar, porque con
`store: false` ese identificador no se puede consultar después y sería un dato inerte.

**Obsolescencia en masa**: `isStale()` compara las tres constantes de procedencia con las de la fila.
Al cambiar las tres, **todas** las filas existentes quedan obsoletas. Se muestran con el aviso «se hizo
con una versión anterior» y se pueden regenerar. **No se borra ninguna**, y no se regenera nada por
cuenta propia (D-114, FR-008 a FR-011).

**Lo único que cambia en almacenamiento local**, y es una constante:

```kotlin
// SharedPreferencesAiPreferences
private const val KEY_NOTICE_ACCEPTED = "ai_notice_accepted_v2"   // antes "ai_notice_accepted"
```

El aviso amplía su texto, así que quien ya lo había aceptado vuelve a verlo **una vez** (D-113,
FR-031a). El fichero de preferencias sigue siendo `boc_ai`, y la clave antigua se queda ahí sin leerse:
borrarla exigiría una migración de preferencias para no ganar nada.

---

## 8. Del servicio al modelo — los dos mapeos

### La respuesta

```
GeminiInteraction
  └─ steps.first { type == "model_output" }.content.first { text != null }.text
       └─ Json.decodeFromString<SummaryPayload>(…)          ← segundo nivel, como hoy
            └─ SummaryValidator.validate(payload, corpus)   ← la última puerta
                 └─ SummaryPayload.toDomain(): AiSummary
```

`SummaryValidator` cambia de firma —`validate(raw: SummaryPayload, document: RenderedDocument,
totalPages: Int)`: recibe lo que salió en lugar del corpus y las páginas por separado, porque el corpus
solo no puede decir qué páginas salieron cuando el guardarraíl corta— y gana **una** responsabilidad: recortar
cada lista a diez y añadir el aviso a `warnings` (D-112). Todo lo demás se conserva: descartar páginas
inexistentes o no enviadas, deduplicar y ordenar referencias, sustituir la cobertura por la real,
recortar la prosa a la última frase completa avisando en `warnings`, y rechazar un resumen con la
prosa en blanco.

### Los rechazos

`GeminiRefusal` conserva **los siete casos, uno a uno**, y su mapeo a `AiSummaryError` no cambia. Eso
es lo que hace que `strings.xml`, `AiSummaryTab` y las veintiuna pruebas instrumentadas no se enteren
del cambio de proveedor.

| Situación en el cable | `GeminiRefusal` | `AiSummaryError` |
|---|---|---|
| Sin credencial configurada | `NotConfigured` | `NotConfigured` |
| HTTP 401 o 403 | `NotConfigured` | `NotConfigured` |
| `IOException` | `Network` | `Offline` |
| Cuerpo que no parsea, o sin paso `model_output` | `Malformed` | `InvalidResponse` |
| `status` `incomplete` o `budget_exceeded` sin contenido | `Malformed` | `InvalidResponse` |
| Parsea pero `plainLanguageSummary` en blanco | `BlankSummary` | `InvalidResponse` |
| HTTP 429 con retraso corto | `QuotaMinute(s)` | `QuotaMinute(s)` |
| HTTP 429 con retraso de escala diaria | `QuotaDay` | `QuotaDay` |
| Cualquier otro código | `HttpError(code)` | `Unknown` |

El código HTTP se pierde en el último salto **a propósito**: FR-027 prohíbe mostrarlo. Va al registro,
no a la pantalla.

### Qué se reintenta

Sin cambios de criterio respecto a la 007: `HttpError` solo si `code >= 500` y quedan intentos;
`Network` mientras queden; `BlankSummary` **una sola vez**; y `NotConfigured`, `Malformed`, `QuotaDay`
y `QuotaMinute`, nunca. Antes de cada reintento se vuelve a consultar el veredicto de cuota, y si ya
no hay margen se devuelve **el rechazo original** — la regla de D-036, que existe porque un arreglo
que convierte un error en otro es peor que no arreglar nada.

`CancellationException` se **repropaga** siempre, en los tres sitios donde se captura.
