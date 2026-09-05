# Contratos internos: Preguntar al BOC

**Feature**: `011-preguntar-al-boc` | **Fecha**: 5 de septiembre de 2026

Esta aplicación no expone API pública. Los contratos que importan son las **costuras internas**: las
fronteras donde una capa habla con otra y donde un cambio se nota. Se documentan las que esta feature
crea o modifica.

---

## 1. La frontera con el servicio

### 1.1 `GeminiChatDataSource.ask`

```kotlin
suspend fun ask(
    system: String,
    history: List<ChatTurn>,
    document: UploadedDocument,
): GeminiChatResult
```

**Obligaciones de quien llama**

| Obligación | Por qué |
|---|---|
| `history` no vacío y termina en `Role.USER` | La última entrada es la pregunta que hay que responder |
| `history` de 12 entradas como mucho | Ventana de `research.md` D-303 |
| `document` procede de una sesión abierta | Sin él la petición carece de la única fuente admisible |
| `system` viene de `ChatPromptFactory` | Es donde viven las cinco cláusulas de la defensa |

**Obligaciones de quien implementa**

| Obligación | Comprobado por |
|---|---|
| La credencial viaja **en cabecera**, nunca en el cuerpo ni en la URL | `OkHttpGeminiChatDataSourceTest` |
| El documento va como `file_data` en el **primer** turno de usuario, y solo ahí | ídem |
| El esquema viaja **verbatim** en `responseJsonSchema` | ídem |
| Se envía `thinking_level` mínimo y `encodeDefaults = true` | ídem |
| La petición se cuenta **al salir**, no al volver | ídem |
| `currentCoroutineContext().ensureActive()` es la **primera** línea del `catch (IOException)` | ídem, con regresión |
| Tres intentos con retroceso, y se consulta al coordinador **antes** de cada reintento | ídem |
| Ni la credencial ni el contenido aparecen en el registro | ídem, cinco aserciones |
| `CancellationException` se repropaga siempre | ídem |

**Los siete casos de `GeminiRefusal` se conservan uno a uno.** Son el vocabulario compartido con el
resumen y su clasificación es idéntica: 401/403 → `NotConfigured`, 429 → `QuotaMinute`/`QuotaDay` según
el retraso que pida, 5xx → `HttpError`, `IOException` → `Network`, cuerpo que no parsea → `Malformed`.

### 1.2 Forma de la petición

```
POST {base}/v1beta/models/{MODEL_ID}:generateContent
x-goog-api-key: <credencial>

{
  "systemInstruction": { "parts": [ { "text": "<las cinco cláusulas>" } ] },
  "contents": [
    { "role": "user",  "parts": [ { "fileData": { "fileUri": "…", "mimeType": "application/pdf" } },
                                  { "text": "<pregunta 1 delimitada>" } ] },
    { "role": "model", "parts": [ { "text": "<respuesta 1>" } ] },
    { "role": "user",  "parts": [ { "text": "<pregunta 2 delimitada>" } ] }
  ],
  "generationConfig": {
    "thinkingConfig": { "thinkingLevel": "MINIMAL" },
    "maxOutputTokens": 2000,
    "responseMimeType": "application/json",
    "responseJsonSchema": { … ChatAnswerSchema … }
  }
}
```

**`maxOutputTokens` es 2.000 y no 8.000.** Una respuesta de chat es corta por diseño; si alguna llegara
a tocar el techo, es que el prompt va mal y conviene que se note. Mismo razonamiento que el resumen, con
otro número.

**El turno de modelo lleva el texto que se pintó**, incluido el nuestro cuando el ámbito fue
`OUT_OF_SCOPE`. Reenviar el texto del modelo que descartamos sería devolverle al contexto justo lo que
se decidió no mostrar.

### 1.3 Forma de la respuesta

Idéntica a la del resumen: `candidates[0].content.parts`, saltando las partes de razonamiento **por su
marca `thought` y nunca por posición** —el paso de razonamiento llega siempre antes, así que tomar la
primera parte fallaría el cien por cien de las veces—. El texto que queda es el JSON del esquema.

---

## 2. La preparación del documento

### 2.1 `AiDocumentPreparer.prepare`

```kotlin
suspend fun prepare(publication: Publication, onPhase: (Phase) -> Unit): PreparationResult
```

**Es la costura nueva de esta feature y la única que toca código de la 010.**

| Obligación | Por qué |
|---|---|
| Cuenta las páginas **antes** de subir | Es lo que mantiene un documento protegido dentro del dispositivo (FR-029) |
| `onPhase` se invoca con `FETCHING_DOCUMENT` y luego con `UPLOADING_DOCUMENT` | Cada repositorio traduce a su propio estado |
| `sessions.open` es idempotente por clave **y** suma de comprobación | Ya lo era; aquí solo se depende de ello |
| Nunca lanza: todo fallo sale por un caso de `PreparationResult` | Quien llama hace un `when` exhaustivo |
| No traduce a ningún error de pantalla | Esa traducción pertenece a cada repositorio |

**Quien lo consume**: `AiSummaryRepositoryImpl` (modificado) y `AiChatRepositoryImpl` (nuevo). Que sean
dos es el motivo de que exista; si algún día vuelve a ser uno, se vuelve a meter dentro.

---

## 3. Dominio ↔ presentación

### 3.1 `AiChatRepository`

```kotlin
fun observeConversation(externalKey: String): Flow<AiConversation>
fun observeAvailability(): Flow<Boolean>
fun ask(publication: Publication, question: String)
fun retry(publication: Publication)
fun discard(externalKey: String)
```

| Obligación | Por qué |
|---|---|
| Observar **nunca** genera nada | Es la regla número uno del Resumen IA, y aquí vale igual: abrir la pantalla no gasta cuota |
| `observeConversation` de otra clave emite una conversación vacía | Como mucho una viva (D-312), y FR-011 pasa a ser estructural |
| `observeAvailability` emite `false` sin credencial, y **sin hacer ninguna petición** | FR-036, SC-010. El resumen no tenía esta costura y por eso solo lo descubría al pulsar (D-320b) |
| `ask` con una pregunta en curso **no hace nada** | FR-005 y FR-050, en un solo sitio |
| `ask` con texto en blanco **no hace nada** | FR-006 |
| `ask` recorta la pregunta a `MAX_QUESTION_LENGTH` | Cinturón además del tirante de la interfaz |
| `retry` reenvía la pregunta señalada por `retryableQuestionId` | FR-033 |
| `discard` cancela lo que haya en vuelo y vacía | Es lo que hace que salir de la publicación limpie |
| Ninguna de las tres suspende | Su trabajo vive en el ámbito del repositorio (D-313) |

### 3.2 `AiChatStatus` → pantalla

| Estado | Lo que se ve |
|---|---|
| `Idle` | La conversación, y el compositor habilitado |
| `Preparing(FETCHING_DOCUMENT)` | «Obteniendo el documento…» |
| `Preparing(UPLOADING_DOCUMENT)` | «Preparando el documento…» |
| `Thinking` | El indicador animado |
| `Failed(error, id)` | La frase del error y, si `id != null`, «Reintentar» |

**Ninguna de esas frases contiene un código, un número de estado ni el nombre del proveedor**
(FR-031). Lo vigila una prueba sobre `strings.xml`, como ya hace `AiErrorMessagesTest`.

### 3.3 El punto donde se sustituye el texto fuera de ámbito

```kotlin
val text = when (validated.scope) {
    AiAnswerScope.OUT_OF_SCOPE -> outOfScopeText          // nuestro
    else -> validated.text                                 // del modelo
}
```

**Una línea, y es FR-021 entero.** Vive en `AiChatRepositoryImpl` y no en la pantalla, para que ninguna
pantalla futura pueda saltárselo. Tiene prueba propia y es la única parte de la defensa antiinyección
que una prueba automática puede afirmar (D-307).

`outOfScopeText` se inyecta como cadena **ya resuelta**, no como identificador de recurso: `data` no
lee `strings.xml`. **Quien la resuelve es `DataModule`**, con `androidContext().getString(...)` al
construir el grafo. Se captura una vez y no sigue un cambio de idioma en caliente, lo cual es correcto
aquí: la aplicación tiene un solo idioma. Si algún día tuviera dos, esto pasa a ser un proveedor.

---

## 4. Navegación

| Ruta | Cambio |
|---|---|
| `Route.Ask(externalKey)` | **Sin cambios.** Ya existe y ya lleva la clave |
| `Route.PdfViewer(externalKey, page)` | **Sin cambios.** Ya acepta página, y es lo que hace seguible una fuente |

Preguntar sigue apilándose **encima** del detalle. Esa relación no es cosmética: es lo que mantiene viva
la entrada del detalle mientras se conversa, y por tanto lo que hace que sea el detalle quien libera el
documento y descarta la conversación (D-314).

De una fuente se navega con `Route.PdfViewer(externalKey, page = source.page - 1)`: las páginas se
cuentan desde uno para quien lee y desde cero para el visor, y la conversión se hace en el sitio donde
se navega, como ya hace `PageChip`.

---

## 5. Inyección de dependencias

| Módulo | Añadidos |
|---|---|
| `DataModule` | `single { AiDocumentPreparer(...) }`, `single<GeminiChatDataSource> { OkHttpGeminiChatDataSource(...) }`, `factory { ChatPromptFactory() }`, `factory { ChatAnswerValidator() }`, `single<AiChatRepository> { AiChatRepositoryImpl(...) }` |
| `DataModule` (modificado) | `AiSummaryRepositoryImpl` deja de recibir `PdfPageCounter` y `AiDocumentSessionStore` y recibe `AiDocumentPreparer` |
| `DomainModule` | `factory { ObserveAiConversationUseCase(get()) }`, `AskAboutDocumentUseCase`, `RetryLastQuestionUseCase`, `DiscardAiConversationUseCase` |
| `UiModule` | `viewModel { AskViewModel(...) }` |

`KoinModulesTest` se actualiza en sus **dos** listas: `CROSS_MODULE_TYPES` y la resolución uno a uno.
Un tipo que solo aparece en una de las dos es un tipo que se resuelve en la prueba y falla en el móvil.

---

## 6. Lo que este contrato promete que NO cambia

- **`GeminiSummaryDataSource`**: misma interfaz, misma firma, mismos siete casos de rechazo.
- **`AiSummaryRepository`**: misma interfaz. Cambia por dentro (D-315) y no por fuera.
- **`AiSummaryConstants`**: los tres valores intactos. **Ningún resumen guardado queda obsoleto.**
- **La base de datos**: versión 4, sin migración, sin borrados.
- **`libs.versions.toml`**: ni una coordenada nueva.
