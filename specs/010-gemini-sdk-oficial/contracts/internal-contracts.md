# Contratos internos: El documento se envía entero, no su texto

**Feature**: `010-gemini-sdk-oficial` | **Fase**: 1 | **Fecha**: 5 de septiembre de 2026

Las fronteras internas que esta feature crea o cambia. Cada una dice su firma, qué garantiza y —donde
hace falta— qué está prohibido.

---

## 1. Contratos de código

### 1.1 `PdfPageCounter` — cuántas páginas tiene, y nada más

```kotlin
interface PdfPageCounter {
    suspend fun pageCount(localPath: String): PageCountResult
}
sealed interface PageCountResult {
    data class Success(val totalPages: Int) : PageCountResult
    data object Encrypted : PageCountResult
    data class Failure(val cause: Throwable) : PageCountResult
}
```

**Sustituye a**: `PdfTextExtractor`.

**Garantías**:
- Abre el documento en el **proceso aislado**, nunca en el de la aplicación. Los documentos vienen de
  internet y uno malformado no puede tumbar la app.
- `totalPages >= 1` en `Success`.
- Un documento con contraseña produce `Encrypted` y **nunca** una excepción hacia arriba.
- Cierra el documento pase lo que pase, en el hilo de entrada/salida y dentro de `runCatching`:
  cerrarlo es una llamada binder síncrona, y el proceso aislado puede haber muerto ya por su cuenta.

**Prohibido**: extraer texto. Si algún día vuelve a hacer falta, será otra interfaz con otro nombre;
esta existe para responder una pregunta y que esa pregunta cueste lo mínimo.

---

### 1.2 `AiDocumentUploader` — subir y borrar

```kotlin
interface AiDocumentUploader {
    suspend fun upload(localPath: String, displayName: String): UploadResult
    suspend fun delete(remoteName: String)
}
```

**Garantías**:
- `upload` no devuelve hasta que el documento está **listo para consultarse**, no solo recibido: sondea
  el estado y solo entonces devuelve `Success`.
- El sondeo tiene **tope**. Superado, `Rejected(Malformed)`. Nunca un bucle sin fin (FR-012).
- Sin credencial, `Rejected(NotConfigured)` **sin ninguna llamada de red**.
- `delete` no lanza nunca: envuelve en `runCatching` y registra. Un borrado que falla no puede tapar
  lo que estuviera pasando (y la caducidad del servicio lo cubre de todos modos).
- `displayName` se compone de datos públicos de la publicación. **Nunca** de nada de la persona.

---

### 1.3 `AiDocumentSessionStore` — como mucho un documento preparado

```kotlin
class AiDocumentSessionStore(uploader, dispatchers, crashReporter) {
    suspend fun open(externalKey: String, pdfSha256: String, localPath: String, displayName: String): SessionResult
    fun release(externalKey: String)
}
```

**Garantías**, cada una con su prueba:

| # | Garantía | Requisito |
|---|---|---|
| 1 | Como mucho **una** sesión viva en el proceso | FR-010 |
| 2 | `open` con misma clave y mismo checksum **no sube nada** | FR-008, SC-005 |
| 3 | `open` con otra clave retira la anterior **antes** de subir | FR-010 |
| 4 | `open` con misma clave y otro checksum releva | — |
| 5 | Dos `open` concurrentes producen **una** subida | FR-022 |
| 6 | `release` de una clave que no es la actual no hace nada | FR-009 |
| 7 | Un `Rejected` no deja sesión abierta | — |

**`release` es deliberadamente no suspendida.** Quien la llama es `onCleared()`, donde el
`viewModelScope` ya está cancelado; lanzar el borrado ahí no borraría nada. Por eso el almacén tiene
su propio `CoroutineScope(SupervisorJob() + dispatchers.io)`, que vive lo que el `single`.

**Prohibido**: persistir la sesión. No va a Room, no va a `SharedPreferences`, no va a
`SavedStateHandle`. Muere con el proceso y la caducidad del servicio limpia lo que quede.

---

### 1.4 `GeminiSummaryDataSource` — la frontera con el servicio

```kotlin
interface GeminiSummaryDataSource {
    suspend fun summarise(system: String, user: String, document: UploadedDocument): GeminiSummaryResult
}
```

**Cambio de firma**: gana `document`. Antes el documento **era** el parámetro `user`; ahora `user`
lleva solo los metadatos de la publicación y el documento viaja por referencia. La superficie del
servicio también cambia, de la Interactions API a `generateContent`, porque es a esa a la que
pertenecen las referencias de la Files API.

**Lo que NO cambia**: `GeminiSummaryResult` y los **siete** casos de `GeminiRefusal`. Su campo `usage`
pasa a ser `SummaryUsage`, que es el mismo tipo mudado desde `GeminiDtos.kt` y despojado de sus
anotaciones de serialización: ya no se deserializa de un cuerpo, se construye desde `usageMetadata`. Es lo que hace
que este cambio no llegue a la capa de presentación, igual que en la 009. Cada uno alimenta un mensaje
de `AiSummaryTab.messageRes()` y una prueba instrumentada.

**Garantías**:
- Sin credencial, `Rejected(NotConfigured)` **sin llamada de red**.
- Todo pasa por `coordinator.serialised { }`: una petición en vuelo en toda la aplicación.
- Se consulta `coordinator.verdict()` **antes** de pedir, y un reintento **también** lo consulta: un
  reintento que choca con la cuota del mismo minuto convierte un error en otro peor, y eso ya pasó.
- `currentCoroutineContext().ensureActive()` es la **primera** línea del `catch (IOException)`.
- Máximo tres intentos con espera creciente, y solo para lo que puede mejorar reintentando.

**Prohibido, y es la prohibición más importante del fichero**: cualquier interceptor de registro a
nivel de cuerpo, en cualquier cliente del camino de inteligencia artificial. Filtraría la credencial y
el contenido del documento a Logcat y a Crashlytics. Cinco pruebas lo vigilan.

---

### 1.5 `OkHttpGeminiDocumentUploader` — el protocolo de subida, a mano

```kotlin
class OkHttpGeminiDocumentUploader(
    client: OkHttpClient, apiKeys: GeminiApiKeyProvider, coordinator: GeminiRateLimitCoordinator,
    dispatchers: DispatcherProvider, crashReporter: CrashReporter, baseUrl: String = DEFAULT_BASE_URL,
) : AiDocumentUploader
```

Aquí iba `GenAiClientProvider`, envolviendo el cliente de la librería oficial. **La librería no se
puede usar en Android** (research.md D-227), así que la Files API se escribe sobre el `OkHttpClient`
compartido, derivado con `newBuilder()` como el resto del proyecto.

**Garantías**:
- Sin credencial, `Rejected(NotConfigured)` **sin ninguna llamada de red** (FR-030, SC-010).
- La credencial viaja en la cabecera `x-goog-api-key`, nunca en el cuerpo ni en la URL.
- `baseUrl` existe para que las pruebas apunten a un MockWebServer **sobre TLS**, como el resto de
  las pruebas de red del proyecto.

---

### 1.6 `AiSummaryRepository` — el contrato solo crece

```kotlin
interface AiSummaryRepository {
    fun observeSummary(externalKey: String): Flow<AiSummaryStatus>
    suspend fun generate(publication: Publication, force: Boolean): AppResult<AiSummary>
    fun observeNoticeAccepted(): Flow<Boolean>
    suspend fun acceptNotice()
    fun releaseDocumentSession(externalKey: String)      // NUEVO
}
```

**Las tres garantías del repositorio, actualizadas**:
- **Observar nunca genera.** Solo `generate` alcanza el servicio (FR-017, SC-004).
- **Un documento protegido nunca sale del dispositivo** (FR-004, SC-007). Sustituye a la garantía
  anterior, «un documento sin texto utilizable nunca llega al servicio», que queda superada (D-204).
- **Dos generaciones concurrentes de la misma publicación comparten una petición** (FR-022).

---

### 1.7 `SummaryValidator` — la última puerta, con una entrada menos

```kotlin
fun validate(raw: SummaryPayload, totalPages: Int): SummaryPayload?
```

**Garantías, sin cambios salvo el conjunto admisible**:
- Descarta toda cita de página fuera de `1..totalPages`. Antes el conjunto era «las páginas que se
  enviaron»; ahora se envían todas.
- Recorta la prosa a la última frase completa.
- Capa cada lista a diez, y **lo dice** en una advertencia generada.
- **Recalcula `coverage` a partir de lo que el resumen cita de verdad**, nunca a partir de lo que el
  modelo declara. Esta es la razón por la que `totalPages` tiene que venir del dispositivo (§1.1).
- Devuelve `null` cuando la prosa queda en blanco → `AiSummaryError.InvalidResponse`.

---

### 1.8 `SummaryPromptFactory` — sin el hueco del documento

```kotlin
fun systemMessage(): String
fun userMessage(publication: Publication, totalPages: Int): String
```

**Garantías**:
- Cinco huecos sustituidos: identificador, título, fecha, sección y total de páginas. El sexto,
  `{{documentWithPageMarkers}}`, desaparece.
- La sustitución se hace **después** de `trimIndent()`. Al revés, un valor multilínea arrastra el
  indent común a cero y el mensaje entero sale con ocho espacios por línea, pagados de la cuota.
- El mensaje de sistema conserva su cláusula antiinyección y dice que el documento va adjunto.
- **Nada de la persona entra en el prompt**: ni guardados, ni leídos, ni identificadores.

---

### 1.9 Contrato de registro

Las líneas del registro van en inglés, llevan etiqueta `BOC` y **nunca** contienen la credencial ni el
contenido del documento. Fases mínimas que deben poder distinguirse (FR-038, FR-040):

```
summary: document ready, counting pages
summary: 9 pages
upload: sending 412 KB
upload: state=PROCESSING, poll 1/N
upload: ready
gemini: HTTP 429, retry in 37s
gemini: finishReason=MAX_TOKENS
gemini: blank summary: plainLanguageSummary=0 keyPoints=6 …
summary failed: UnreadableDocument
session: released <clave>
```

De una respuesta se registra **su forma** —qué campos trae y de qué tamaño— y nunca su contenido. Del
servicio se registra su `error.message`, que habla de nuestra petición.

`AiSummaryError.Unknown` sigue cubriendo varias situaciones distintas —documento que no se descarga,
contador de páginas roto, código HTTP sin mejor sitio, cualquier excepción del camino—: en pantalla
son la misma frase y en el registro no pueden serlo.

---

## 2. Etiquetas de prueba

Ninguna nueva. Las de la pestaña (`TAG_AI_SUMMARY_*`) siguen valiendo tal cual, que es la señal de
que la capa de presentación no se ha movido.

---

## 3. Contrato visual

Sin cambios de diseño. Dos literales:

| Recurso | Antes | Ahora |
|---|---|---|
| `ai_summary_phase_extracting` | «Leyendo el texto del documento…» | **borrado** |
| `ai_summary_phase_uploading` | — | «Preparando el documento…» |
| `ai_error_no_text` | «Este documento no contiene texto que la aplicación pueda analizar.» | **borrado** |
| `ai_error_unreadable` | — | «No se ha podido leer este documento. Puedes consultar el PDF oficial.» |
| `ai_summary_partial_before` | plural con «se analizarán las N primeras» | **borrado**: ya no se elige qué páginas caben |
| `ai_notice_body` | habla de enviar «el texto de este documento» | reescrito: el documento completo, conservado un tiempo limitado, retirado al salir |

`ai_summary_partial_after` **se conserva**: enviar el documento entero no garantiza que el modelo lo
lea entero.

---

## 4. Contrato de build

| Qué | Valor |
|---|---|
| Dependencias | **ninguna nueva.** `gradle/libs.versions.toml` no se toca (D-227) |
| Java | **11**, sin cambios. Subir a 17 lo exigía la librería retirada |
| Empaquetado | Sin cambios |
| Optimización | Sin cambios: sigue desactivada para release, como antes de esta feature |
| Credencial | Sigue leyéndose de `GEMINI_API_KEY` en `local.properties` con API de proveedor de Gradle, con respaldo por variable de entorno. Si falta, **la build sigue en verde** y el campo es cadena vacía |
| Base de datos | `app/schemas/` **sin cambios**. Versión 4 |
