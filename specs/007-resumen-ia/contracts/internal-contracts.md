# Internal Contracts: Resumen IA

**Feature**: `007-resumen-ia` | **Fase**: 1 | **Fecha**: 2 de septiembre de 2026

Esta aplicación no expone ninguna API pública: no hay clientes externos ni versionado hacia fuera.
Lo que sí tiene son **fronteras internas** que esta feature cruza, y de las que otras piezas
dependerán. Eso es lo que aquí se fija: la firma, la garantía y lo que está prohibido.

---

## 1. Contratos de código

### 1.1 `PdfTextExtractor` — la segunda frontera con `androidx.pdf`

```kotlin
// data/source/local/PdfTextExtractor.kt
interface PdfTextExtractor {
    suspend fun extract(localPath: String, externalKey: String, pdfSha256: String): PdfExtractionResult
}

sealed interface PdfExtractionResult {
    data class Success(val corpus: PdfCorpus) : PdfExtractionResult
    data object NoExtractableText : PdfExtractionResult
    data object EncryptedPdf : PdfExtractionResult
    data class Failure(val cause: Throwable) : PdfExtractionResult
}
```

| Garantía | Detalle |
|---|---|
| **No lanza** | Cualquier excepción se traduce a `Failure`. `CancellationException` se relanza siempre |
| **Fuera del hilo principal** | Todo el trabajo en `dispatchers.io`, inyectado (SC de rendimiento, principio V) |
| **Cierra lo que abre** | El `PdfDocument` es `Closeable` y se cierra con `use`; olvidarlo mantiene vivo el proceso aislado reteniendo un fichero que la caché quiere poder liberar |
| **Numera desde 1** | La biblioteca numera desde 0. La conversión ocurre **solo aquí** (D-003) |
| **Decide con umbral** | `NoExtractableText` se decide contando caracteres aprovechables, no esperando una excepción (D-004) |
| **Es sustituible** | Toda la razón de que sea una interfaz: si `androidx.pdf` fallara en dispositivos reales, se cambia por otra implementación sin tocar nada más |

**Prohibido**: que ningún fichero fuera de `data/source/local/AndroidxPdfTextExtractor.kt` y de
`ui/pdf/` importe `androidx.pdf`. Son las dos únicas fronteras, y hay que dejarlo escrito en
`CLAUDE.md` en este mismo cambio.

### 1.2 `PdfTextNormalizer` — Kotlin puro

```kotlin
// data/source/local/PdfTextNormalizer.kt
class PdfTextNormalizer {
    fun normalise(corpus: PdfCorpus): PdfCorpus
}
```

| Garantía | Detalle |
|---|---|
| **Kotlin puro** | Cero `import android.*`. Sus pruebas corren sin emulador, que es donde se comprueban estas reglas |
| **No destructiva** | Conserva títulos, numeraciones, fechas, importes y referencias normativas (FR-011) |
| **No mezcla páginas** | El número de páginas de entrada y de salida es el mismo, y ningún texto cambia de página |
| **Une guiones con criterio** | Solo si la línea siguiente empieza en minúscula. `sub-\nvención` sí; `Decreto-\nLey` no |
| **Quita repeticiones** | Encabezados y pies presentes en ≥ 60 % de las páginas |

**Prohibido**: reescribir, traducir, corregir cifras o nombres, eliminar párrafos por parecer poco
importantes, unir columnas con heurísticas, interpretar abreviaturas.

### 1.3 `SummaryBudget` — qué cabe

```kotlin
// data/source/remote/SummaryBudget.kt
object SummaryBudget {
    fun select(corpus: PdfCorpus): SelectedText
    fun estimateTokens(text: String): Int
}
```

| Garantía | Detalle |
|---|---|
| **Nunca supera los topes** | Ni 16.000 caracteres ni 5.000 tokens estimados de documento (D-007) |
| **Páginas enteras** | Salvo que la primera sola no quepa, y entonces corta por final de párrafo (D-008) |
| **Siempre devuelve algo** | Con al menos una página con texto, el resultado nunca va vacío |
| **`pages` es la verdad** | Lo que devuelve es exactamente lo que se envía, y alimenta `coverage.pagesAnalyzed` |
| **Determinista** | El mismo corpus da el mismo resultado. Sin azar, sin reloj |

### 1.4 `SummaryPromptFactory` — lo que se manda

```kotlin
// data/source/remote/SummaryPromptFactory.kt
class SummaryPromptFactory {
    fun systemMessage(): String
    fun userMessage(publication: Publication, selected: SelectedText, totalPages: Int): String
}
```

| Garantía | Detalle |
|---|---|
| **Prompt fijo** | El mensaje de sistema es constante entre llamadas; cambiarlo obliga a subir `PROMPT_VERSION` |
| **Nunca escribe `null`** | Un metadato ausente va como cadena vacía o «No disponible», jamás como el literal `null` |
| **Marcadores de página** | El documento va delimitado y con `[PÁGINA n]` antes de cada una (D-009) |
| **Defensa incrustada** | El mensaje de sistema declara el documento como contenido no confiable y prohíbe ejecutar lo que contenga (FR-018, D-019) |

### 1.5 `GroqSummaryDataSource` — la salida a la red

```kotlin
// data/source/remote/GroqSummaryDataSource.kt
interface GroqSummaryDataSource {
    suspend fun summarise(system: String, user: String): GroqSummaryResult
}

sealed interface GroqSummaryResult {
    data class Success(val payload: GroqSummaryPayload, val usage: GroqUsage, val fingerprint: String?)
    data class Rejected(val reason: GroqRefusal)
}

sealed interface GroqRefusal {
    data object NotConfigured; data object Network; data object Malformed
    data class QuotaMinute(val secondsRemaining: Long); data object QuotaDay
    data class HttpError(val code: Int)
}
```

| Garantía | Detalle |
|---|---|
| **No lanza** | Todo rechazo viaja como `Rejected`. `CancellationException` se relanza |
| **Una consulta cada vez** | Serializadas con `Mutex`. Nunca hay dos en vuelo (D-016) |
| **Respeta la cuota** | Consulta al coordinador antes de salir y lo actualiza con las cabeceras reales después |
| **Cliente derivado** | `client.newBuilder()` sobre el `OkHttpClient` compartido, para no duplicar el pool |
| **Sin respuesta progresiva** | `stream: false`, obligado por el esquema estricto (D-011) |

**Prohibido**, y es la prohibición más importante de la feature: **ningún interceptor de registro a
nivel de cuerpo**. Expondría la credencial y el documento entero en el registro del sistema (FR-047,
SC-009). Tampoco la credencial en mensajes de error, en informes de fallo ni en analítica.

### 1.6 `GroqRateLimitCoordinator` — la cuota

```kotlin
// data/source/remote/GroqRateLimitCoordinator.kt
class GroqRateLimitCoordinator(private val time: TimeProvider, private val random: RandomProvider) {
    suspend fun awaitPermission(): QuotaVerdict
    fun record(headers: Headers)
    companion object { fun parseDuration(raw: String): Duration? }
}
```

| Garantía | Detalle |
|---|---|
| **Las cabeceras mandan** | Lo que llega en la respuesta prevalece sobre cualquier valor configurado |
| **Interpreta bien los nombres** | `x-ratelimit-limit-requests` es **por día**; `x-ratelimit-limit-tokens` es **por minuto** (D-015) |
| **Analiza tres formas** | `7.66s`, `2m59.56s` y segundos enteros. Un formato desconocido devuelve `null`, no una excepción |
| **Distingue las dos cuotas** | La del minuto se espera; la del día se comunica y no se reintenta (FR-038, FR-039) |
| **Reloj y azar inyectados** | `TimeProvider` y `RandomProvider`, los que ya existen. Sus pruebas son deterministas |

### 1.7 `SummaryValidator` — la última puerta

```kotlin
// data/source/remote/SummaryValidator.kt
class SummaryValidator {
    fun validate(raw: GroqSummaryPayload, corpus: PdfCorpus, sentPages: List<Int>): AiSummary?
}
```

Garantías detalladas en `data-model.md` §5. La que hay que repetir aquí: **`coverage.complete` se
corrige a falso si faltaron páginas, aunque el servicio afirme lo contrario** (FR-030, SC-012). El
esquema estricto garantiza la forma de la respuesta, no su verdad.

### 1.8 `AiSummaryRepository` — el contrato que sube a `domain`

Firma y garantías en `data-model.md` §7. Las tres que no pueden relajarse:

- **Cero consultas sin acción explícita.** Observar no genera (FR-002, SC-004).
- **Un documento sin texto no llega al servicio** (FR-012, SC-005).
- **Dos generaciones concurrentes de la misma publicación comparten una consulta** (FR-005).

### 1.9 `GroqApiKeyProvider` — la credencial

```kotlin
// data/source/remote/GroqApiKeyProvider.kt
fun interface GroqApiKeyProvider { suspend fun apiKey(): String? }
```

Devuelve `null` cuando no hay credencial, y eso se traduce en `NotConfigured` (FR-042). El resto de
la funcionalidad **no debe saber** de dónde sale: hoy de `BuildConfig`, mañana de un servicio
intermedio. Nunca aparece en un mensaje de interfaz, en un registro ni en el repositorio de código.

---

## 2. Etiquetas de prueba

### Nuevas

> **Corregido al ejecutar la tanda instrumentada.** Las dos filas de chips llevaban la misma
> etiqueta, y la misma página aparece legítimamente en ambas: junto al dato que respalda y en el
> resumen de fuentes. Dos nodos con la misma identidad son dos nodos que nada puede direccionar.
> Ahora cada sitio tiene la suya.

| Constante | Valor | Dónde |
|---|---|---|
| `TAG_AI_SUMMARY_TAB` | `ai_summary_tab` | Raíz de la pestaña |
| `TAG_AI_SUMMARY_GENERATE` | `ai_summary_generate` | Botón «Generar resumen» |
| `TAG_AI_SUMMARY_PARTIAL_WARNING` | `ai_summary_partial_warning` | El aviso previo de cobertura parcial |
| `TAG_AI_SUMMARY_PROGRESS` | `ai_summary_progress` | Esqueleto y fase en curso |
| `TAG_AI_SUMMARY_QUOTA` | `ai_summary_quota` | La cuenta atrás de cuota |
| `TAG_AI_SUMMARY_CARD` | `ai_summary_card` | La tarjeta «Resumen generado por IA» |
| `TAG_AI_SUMMARY_DISCLAIMER` | `ai_summary_disclaimer` | «Comprueba siempre el texto oficial» |
| `TAG_AI_SUMMARY_COVERAGE` | `ai_summary_coverage` | La banda de cobertura parcial del resultado |
| `TAG_AI_SUMMARY_SOURCES` | `ai_summary_sources` | La fila de chips de página |
| `TAG_AI_SUMMARY_ERROR` | `ai_summary_error` | Cualquier estado de fallo |
| `TAG_AI_SUMMARY_RETRY` | `ai_summary_retry` | Reintentar; **solo** si el fallo es recuperable |
| `TAG_AI_SUMMARY_STALE` | `ai_summary_stale` | El aviso de resumen obsoleto |
| `TAG_AI_NOTICE_SHEET` | `ai_notice_sheet` | La hoja de la primera vez |
| `TAG_AI_NOTICE_CONTINUE` / `TAG_AI_NOTICE_CANCEL` | `ai_notice_continue` / `ai_notice_cancel` | Sus dos acciones |
| `aiSectionTag(key)` | `ai_section_<key>` | Una por sección estructurada, para afirmar que las vacías **no** están |
| `pageChipTag(page)` | `ai_page_chip_<n>` | El chip **junto al dato**: «¿de dónde sale esto?» |
| `sourceChipTag(page)` | `ai_source_chip_<n>` | El chip de la fila de fuentes: «¿qué páginas leyó?» |

### Reutilizadas

`TAG_TAB_SUMMARY` (la pestaña ya existe), `TAG_DETAIL_LIST`, `TAG_ACTION_OPEN`, `TAG_PDF_VIEWER`.
`TAG_COMING_SOON` deja de aparecer en el detalle, pero **sigue existiendo**: lo usa la pantalla
Preguntar.

---

## 3. Contrato visual

Contra `docs/diseno/especificaciones-diseno.md` §20, que es la fuente de verdad.

| Apartado | Qué se implementa |
|---|---|
| §20.1 Identidad | Icono propio `ic_ai` —el conjunto de Material no está en el classpath—, etiqueta «Resumen generado por IA», color `BocTheme.colors.aiAccent`, fondo `aiContainer`. Ambos tokens ya existen desde la feature 004 |
| §20.2 Tarjeta | Radio 18 dp (`shapes.large`), relleno 20 dp (`spacing.space5`), círculo de 48 dp con el icono, título `titleLarge`, viñetas azules, cuerpo `bodyLarge`, separación `space3`/`space4` entre puntos |
| §20.3 Aviso | Icono de advertencia en `accentOfficial` sobre fondo transparente. **Nada de bloque rojo grande** |
| §20.4 Fuentes | Cabecera «Fuentes del resumen», chips con contorno, icono de documento, texto «Página N», altura mínima 48 dp, y **azul, no violeta** |
| §20.5 Carga | Esqueleto de título y tres líneas, icono de IA **estático**, texto breve debajo. Sin animación futurista ni partículas |

**Discrepancia con el mockup, resuelta a favor del documento**: `Datos_modelo/resumenIA.png` rotula
«Fuente del resumen», en singular; §20.4 dice «Fuentes del resumen». Manda el documento de diseño,
que es la fuente de verdad según `CLAUDE.md`. El mockup es una idea del aspecto, y así lo dice
`spec.md`.

**Enmienda que hay que escribir**: §20 no contempla la cobertura parcial, ni el aviso de privacidad,
ni el estado de resumen obsoleto, ni las acciones de copiar y compartir, porque se decidieron después.
Se añade un blockquote fechado al principio del apartado, como se hizo con §17 en la feature 006.

### Estados que la pestaña debe saber pintar

Inicial · Progreso por fases · Espera de cuota · Éxito completo · Éxito parcial · Éxito obsoleto ·
Sin texto utilizable · PDF protegido · Sin conexión · Cuota diaria agotada · No configurado ·
Respuesta no válida · Publicación sin documento.

**Trece.** Escribirlos aquí es lo que impide que la mitad se descubran en el móvil.

### Accesibilidad

- La advertencia **se anuncia**, no solo se ve: `contentDescription` propio, no dependiente del color
  ni del icono (FR-024, SC-006).
- Los chips de página tienen 48 dp de objetivo táctil y anuncian «Abrir la página N del documento»,
  no solo «Página N»: quien no ve la pantalla tiene que saber que se puede tocar.
- La regla de sección y los adornos, con `clearAndSetSemantics {}`, como ya hace `PublicationCard`.
- La cobertura parcial se anuncia con el resto del texto; no puede ser solo un color de fondo.

### Trampa conocida que aplica aquí

El esqueleto de carga de §20.5 **no debe animarse en bucle infinito** o, si lo hace, sus pruebas
tienen que conducir el reloj a mano (`mainClock.autoAdvance = false` + `advanceTimeByFrame()`). Una
animación infinita impide que la composición llegue a reposo, y `assertIsDisplayed()` **se cuelga** en
lugar de fallar. Ya costó tiempo una vez en este proyecto.
