# Data Model: Preguntar al BOC

**Feature**: `011-preguntar-al-boc` | **Fecha**: 5 de septiembre de 2026

**Nada de esto se persiste.** No hay tabla nueva, no hay migración y la base de datos **se queda en la
versión 4**. La conversación vive en memoria durante una visita a la publicación y muere con ella
(`research.md` D-312). Este documento describe tipos, no filas.

---

## 1. Dominio

Kotlin puro: cero `android.*`, cero Compose, cero referencias a `data` ni a `ui`. Cada clase de nivel
superior necesita su fichero de prueba, o la octava regla de Konsist tumba la build.

### 1.1 `AiAnswerScope`

```kotlin
enum class AiAnswerScope { FROM_DOCUMENT, NOT_IN_DOCUMENT, OUT_OF_SCOPE }
```

Lo que la respuesta declara de sí misma. Es **la única capa de la defensa antiinyección que se puede
comprobar sin cruzar la frontera con el servicio**, y por eso es un tipo de dominio y no un detalle del
transporte.

| Valor | Qué significa | Qué se pinta |
|---|---|---|
| `FROM_DOCUMENT` | La respuesta sale del documento adjunto | El texto del modelo y sus fuentes |
| `NOT_IN_DOCUMENT` | El documento no recoge lo que se pregunta | El texto del modelo, marcado |
| `OUT_OF_SCOPE` | La petición era ajena al documento | **Texto nuestro.** Cero caracteres del modelo |

La tercera fila es FR-021 y SC-004. Es también el motivo de que este enumerado exista: sin él, «el
modelo no se salió del documento» sería una esperanza sin forma de comprobarla (D-307, D-308).

### 1.2 `AiAnswerSource`

```kotlin
data class AiAnswerSource(val page: Int, val label: String) {
    init { require(page >= 1) }
}
```

Una página del documento y una etiqueta corta de qué trata. `page` es **1-based**, como lo lee una
persona; la ruta del visor es 0-based y la conversión se hace en el sitio donde se navega, igual que ya
hace `PageChip` con las citas del resumen.

Toda `AiAnswerSource` que llega a pantalla ha pasado por `ChatAnswerValidator` y por tanto existe en el
documento (FR-014, SC-005).

### 1.3 `AiChatMessage`

```kotlin
sealed interface AiChatMessage {
    val id: String
    val atEpochMillis: Long

    data class Question(
        override val id: String,
        override val atEpochMillis: Long,
        val text: String,
    ) : AiChatMessage

    data class Answer(
        override val id: String,
        override val atEpochMillis: Long,
        val text: String,
        val scope: AiAnswerScope,
        val sources: List<AiAnswerSource>,
    ) : AiChatMessage
}
```

`id` existe para que la lista de Compose tenga clave estable y para que reintentar sepa **qué**
pregunta reenvía. Se genera en la capa de datos; el dominio solo exige que esté.

`atEpochMillis` viene del `TimeProvider` inyectado, nunca del reloj del sistema: es lo que hace
deterministas las pruebas.

`Answer.text` **ya viene resuelto**: cuando el ámbito es `OUT_OF_SCOPE`, aquí no hay nada del modelo.
La sustitución ocurre en `data` y no en `ui`, para que ninguna pantalla futura pueda saltársela por
descuido.

### 1.4 `AiConversation`

```kotlin
data class AiConversation(
    val externalKey: String,
    val messages: List<AiChatMessage>,
    val status: AiChatStatus,
) {
    val isEmpty: Boolean get() = messages.isEmpty()
}
```

Lo hablado sobre **una** publicación durante **una** visita. Como mucho hay una viva en el proceso
(D-312).

### 1.5 `AiChatStatus`

```kotlin
sealed interface AiChatStatus {
    data object Idle : AiChatStatus
    data class Preparing(val phase: Phase) : AiChatStatus {
        enum class Phase { FETCHING_DOCUMENT, UPLOADING_DOCUMENT }
    }
    data object Thinking : AiChatStatus
    data class Failed(val error: AiChatError, val retryableQuestionId: String?) : AiChatStatus
}
```

Las dos fases son **las mismas dos** que el resumen, y a propósito: es la misma preparación, extraída a
una sola clase (D-315). Se declaran aquí en vez de reutilizar `AiSummaryStatus.Preparing.Phase` porque
importar el estado del resumen desde el del chat ataría dos pantallas que no tienen por qué moverse
juntas.

**No hay `WaitingForQuota`.** El resumen lo tiene porque es una operación que puede completarse sola;
una pregunta reanudada un minuto después es una respuesta a destiempo (D-319).

`retryableQuestionId` es lo que permite que «Reintentar» reenvíe la misma pregunta sin reescribirla
(FR-033, D-320). Es `null` cuando el fallo no se puede reintentar.

### 1.6 `AiChatError`

```kotlin
sealed interface AiChatError {
    data object Offline : AiChatError
    data class QuotaMinute(val secondsRemaining: Long) : AiChatError { init { require(secondsRemaining >= 0) } }
    data object QuotaDay : AiChatError
    data object NotConfigured : AiChatError
    data object UnreadableDocument : AiChatError
    data object EncryptedPdf : AiChatError
    data object InvalidResponse : AiChatError
    data object Unknown : AiChatError

    val isRetryable: Boolean
        get() = when (this) {
            Offline, is QuotaMinute, InvalidResponse, Unknown -> true
            QuotaDay, NotConfigured, UnreadableDocument, EncryptedPdf -> false
        }
}
```

Ocho casos, ocho cadenas en `strings.xml`, ninguna con un código dentro (FR-031). Propio y no
compartido con `AiSummaryError` porque las frases son distintas (D-318).

### 1.7 Contratos de dominio

```kotlin
interface AiChatRepository {
    fun observeConversation(externalKey: String): Flow<AiConversation>
    fun observeAvailability(): Flow<Boolean>
    fun ask(publication: Publication, question: String)
    fun retry(publication: Publication)
    fun discard(externalKey: String)
}
```

`ask`, `retry` y `discard` **no son funciones suspendidas**, y eso es una decisión y no un descuido: el
trabajo vive en un ámbito del repositorio para que salir de la pantalla no lo cancele (D-313), y
`discard` se llama desde `onCleared()`, donde el ámbito de quien llama ya está muerto.

Casos de uso, uno por operación:

| Caso de uso | Firma |
|---|---|
| `ObserveAiConversationUseCase` | `operator fun invoke(externalKey: String): Flow<AiConversation>` |
| `ObserveAiAvailabilityUseCase` | `operator fun invoke(): Flow<Boolean>` |
| `AskAboutDocumentUseCase` | `operator fun invoke(publication: Publication, question: String)` |
| `RetryLastQuestionUseCase` | `operator fun invoke(publication: Publication)` |
| `DiscardAiConversationUseCase` | `operator fun invoke(externalKey: String)` |

### 1.8 `AiChatConstants`

```kotlin
object AiChatConstants {
    const val MAX_QUESTION_LENGTH = 500
    const val COUNTER_VISIBLE_FROM = 400
    const val MAX_HISTORY_MESSAGES = 12
}
```

Un `object` y no una `class`, como `AiSummaryConstants` y `SearchText`: la octava regla de Konsist pide
fichero de prueba para toda **clase** de dominio, y tres constantes no tienen conducta que afirmar.

**No lleva `MODEL_ID`**: el chat usa el del resumen a propósito, para que la escapatoria ante una caída
de capacidad siga siendo una sola línea (D-305).

---

## 2. Capa de datos

### 2.1 `ChatAnswerPayload` — lo que devuelve el servicio

```kotlin
@Serializable
data class ChatAnswerPayload(
    val scope: String,
    val sources: List<ChatSourceDto> = emptyList(),
    val answer: String = "",
)

@Serializable
data class ChatSourceDto(val page: Int, val label: String)
```

**El orden de las propiedades es carga útil, no estética.** `answer` va la última porque en una
respuesta con esquema estricto el orden de declaración es el orden de generación, y lo declarado
después del campo largo se vacía si la generación se corta. Con `answer` primero, una respuesta larga
dejaría `scope` en blanco — y `scope` en blanco es la defensa caída (D-310). Lo vigila
`ChatAnswerSchemaTest`.

`scope` viaja como cadena y se traduce a `AiAnswerScope` en el validador. Un valor desconocido se trata
como `OUT_OF_SCOPE`: ante la duda, se pinta texto nuestro.

### 2.2 `ChatAnswerSchema`

Mismo patrón que `SummarySchema`: un `JsonElement` perezoso construido de un literal, que viaja
**verbatim** en `responseJsonSchema`. `additionalProperties: false`, las tres propiedades en
`required`, `scope` acotado con `enum`, `answer` con `maxLength`, `sources` con `maxItems`.

Nombre sin marca de proveedor, como `SummarySchema`: describe nuestro formato, no el suyo.

### 2.3 `AiDocumentPreparer` — lo que ahora comparten resumen y chat

```kotlin
class AiDocumentPreparer(documents, pages, sessions, crashReporter) {
    suspend fun prepare(publication: Publication, onPhase: (Phase) -> Unit): PreparationResult
    enum class Phase { FETCHING_DOCUMENT, UPLOADING_DOCUMENT }
}

sealed interface PreparationResult {
    data class Ready(val document: UploadedDocument, val totalPages: Int) : PreparationResult
    data class Unreachable(val error: DomainError) : PreparationResult
    data object Encrypted : PreparationResult
    data class Refused(val reason: GeminiRefusal) : PreparationResult
    data class Broken(val cause: Throwable) : PreparationResult
}
```

Encierra los cuatro pasos que los dos repositorios hacían igual: copia local, cuenta de páginas,
apertura de sesión, y el invariante que va con ellos — **un documento protegido con contraseña no sale
nunca del dispositivo**, porque se cuenta antes de subir (D-315).

Devuelve casos y no un `AppResult` para que cada repositorio traduzca a su propio vocabulario de error;
esa traducción se queda donde vive la frase que se lee en pantalla.

### 2.4 `GeminiChatDataSource`

```kotlin
interface GeminiChatDataSource {
    suspend fun ask(
        system: String,
        history: List<ChatTurn>,
        document: UploadedDocument,
    ): GeminiChatResult
}

data class ChatTurn(val role: Role, val text: String) { enum class Role { USER, MODEL } }

sealed interface GeminiChatResult {
    data class Success(val payload: ChatAnswerPayload, val usage: SummaryUsage) : GeminiChatResult
    data class Rejected(val reason: GeminiRefusal) : GeminiChatResult
}
```

Hermano de `GeminiSummaryDataSource`, no un método suyo (D-301). Comparte `GeminiRefusal` y
`SummaryUsage` —que ya no es un tipo de cable desde la 010, es nuestro— y toda la disciplina del
transporte.

`history` llega ya recortado a la ventana de D-303, y `OkHttpGeminiChatDataSource` pone la referencia
del documento en el **primer** turno de usuario que reciba (D-304).

### 2.5 `ChatAnswerValidator`

```kotlin
class ChatAnswerValidator {
    fun validate(raw: ChatAnswerPayload, totalPages: Int): ValidatedAnswer?
}

data class ValidatedAnswer(
    val scope: AiAnswerScope,
    val text: String,
    val sources: List<AiAnswerSource>,
    val droppedCitations: Int,
)
```

Tres reglas y ninguna de ellas se fía del servicio:

1. **Fuera las citas imposibles.** Una página fuera de `1..totalPages` se descarta. El total viene del
   dispositivo, que es la razón por la que la 010 conservó el contador de páginas (FR-014).
2. **El texto se recorta a la última frase completa** si venía cortado, igual que hace
   `SummaryValidator` con la prosa.
3. **Un cuerpo en blanco devuelve `null`**, y el repositorio lo convierte en `InvalidResponse`. Una
   burbuja vacía no es una respuesta (FR-023).

`droppedCitations` no se pinta: se registra (D-328). Es la forma de saber, sobre un móvil de verdad, si
el modelo está inventando páginas.

**La sustitución de `OUT_OF_SCOPE` no ocurre aquí sino en el repositorio**, porque el texto que se pone
en su lugar es un recurso de cadena y `data` no lee `strings.xml`. El validador devuelve el ámbito; el
repositorio decide qué texto lleva la respuesta. Ver §4.

### 2.6 `ChatPromptFactory`

```kotlin
class ChatPromptFactory {
    fun systemMessage(publication: Publication, totalPages: Int): String
    fun question(raw: String): String
}
```

`systemMessage` lleva las cinco cláusulas de la defensa (D-307) y los metadatos de la publicación.
`question` envuelve el texto entre marcas y es lo único que se hace con él: **no se filtra, no se
reescribe y no se censura**. Filtrar la pregunta sería adivinar intenciones sobre texto libre, y quien
pregunta legítimamente por «recursos» no debe tropezar con un filtro.

La sustitución va **después** de `trimIndent()`, como en `SummaryPromptFactory`: un valor multilínea
interpolado antes arrastra el indent común a cero y el prompt entero sale con ocho espacios por línea.
Hay prueba.

---

## 3. Presentación

### 3.1 `AskUiState`

```kotlin
data class AskUiState(
    val publication: Publication? = null,
    val isSaved: Boolean = false,
    val messages: List<AiChatMessage> = emptyList(),
    val status: AiChatStatus = AiChatStatus.Idle,
    val draft: String = "",
    val noticePending: Boolean = false,
    val noticeAccepted: Boolean = false,
    val isServiceConfigured: Boolean = true,
) {
    val canSend: Boolean get() =
        publication != null &&
            isServiceConfigured &&
            draft.isNotBlank() &&
            draft.length <= AiChatConstants.MAX_QUESTION_LENGTH &&
            status !is AiChatStatus.Preparing && status != AiChatStatus.Thinking
    val showSuggestions: Boolean get() = messages.isEmpty()
    val showCounter: Boolean get() = draft.length >= AiChatConstants.COUNTER_VISIBLE_FROM
}
```

`canSend` es lo que cumple FR-005 (no dos a la vez), FR-006 (nada vacío) y FR-036 (sin credencial no
se envía) en un solo sitio comprobable. La condición `publication != null` no es defensiva: `ask()`
necesita una `Publication` y la pantalla se monta con una clave, así que hay un instante en el que
todavía no está.

**Y el modelo de pantalla no puede combinar esto con un solo `combine`**: son siete flujos y `combine`
pasa de cinco a la sobrecarga de `vararg`, que exige el mismo tipo y devuelve `Array<Any?>`. Lo que
viene del repositorio se agrupa en un tipo propio, como hizo `PublicationDetailViewModel` con
`PersonalState` (D-327b).

### 3.2 Componibles

Todos en `ui/ask/component/`, tontos y sin estado.

| Componible | Qué dibuja |
|---|---|
| `AskDocumentHeader` | Título, fecha y la estrella de guardar |
| `AskScopeNotice` | «Las respuestas se basan solo en este documento» |
| `SuggestedQuestions` | Tres chips, solo con la conversación vacía |
| `ChatBubble` | Pregunta o respuesta, con su hora |
| `AnswerSources` | El bloque «Fuentes»; cada línea abre el visor en su página |
| `ThinkingIndicator` | Que el asistente trabaja. Animación infinita (D-326) |
| `ChatErrorRow` | El fallo y su «Reintentar» cuando lo admite |
| `AskComposer` | Campo, contador y botón de envío. `bottomBar` (D-324) |
| `AskFooter` | «Ver PDF oficial» |

**Solo tokens**: `BocTheme.colors.aiAccent` / `aiContainer` / `surfaceSoft` / `textMuted`,
`BocTheme.spacing.*`, `MaterialTheme.shapes.*`. Hay una regla de Konsist que tumba la build si un
fichero fuera de `core/ui/theme` importa `androidx.compose.ui.graphics.Color`.

---

## 4. El recorrido de una pregunta, de punta a punta

```
AskScreen                → onSend(draft)
AskViewModel             → askAboutDocument(publication, draft)     [no suspende]
AiChatRepositoryImpl     → añade Question a la lista, status = Preparing
                         → scope propio: launch { ... }
AiDocumentPreparer       → ensureLocalCopy → pageCount → sessions.open
                            ├ Encrypted        → Failed(EncryptedPdf)
                            ├ Unreachable      → Failed(Offline | Unknown)
                            ├ Refused(...)     → Failed(UnreadableDocument | ...)
                            └ Ready(doc, pages)
                         → status = Thinking
ChatPromptFactory        → system + la pregunta delimitada
GeminiChatDataSource     → ask(system, historial recortado a 12, doc)
                            └ coordinator.serialised { verdict → 3 intentos → parse }
ChatAnswerValidator      → citas fuera de rango descartadas, texto recortado, blanco → null
AiChatRepositoryImpl     → scope == OUT_OF_SCOPE ? texto nuestro : texto del modelo
                         → añade Answer a la lista, status = Idle
AskViewModel             → el flujo emite; la pantalla pinta
```

**El punto donde se sustituye el texto está marcado a propósito.** Es una línea del repositorio y es
toda la diferencia entre FR-021 cumplido y no cumplido. Tiene prueba propia.

---

## 5. Lo que NO cambia

- **La base de datos.** Versión 4, sin migración, sin tabla nueva, y **sigue sin haber ninguna
  sentencia de borrado en los cinco DAO**.
- **`SummarySchema`, `SummaryPayloadDtos`, `SummaryValidator`, `AiSummaryDao`.** El resumen no se toca.
- **`AiSummaryConstants`.** Ni el modelo, ni la versión de prompt, ni la de esquema. **Ningún resumen
  guardado queda obsoleto por esta feature**, que es como debe ser: no cambia cómo se resume.
- **`GeminiRateLimitCoordinator`, `GeminiApiKeyProvider`, `OkHttpGeminiDocumentUploader`,
  `AiDocumentSessionStore`.** Se usan tal cual.
- **`AiSummaryRepositoryImpl` cambia por dentro pero no por fuera**: su interfaz, sus errores y sus
  estados son los mismos. Lo único que se mueve es que la preparación del documento la hace ahora
  `AiDocumentPreparer` (D-315).
