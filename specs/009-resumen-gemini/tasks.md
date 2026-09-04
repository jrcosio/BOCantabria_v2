---

description: "Task list for feature 009 — Resumen IA con proveedor nuevo"
---

# Tasks: Resumen IA con proveedor nuevo

**Input**: Design documents from `/specs/009-resumen-gemini/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/internal-contracts.md`,
`quickstart.md` — los seis escritos y validados.

**Tests**: **obligatorios**, no opcionales. El principio V de la constitución dice que ninguna tarea se
considera terminada sin su prueba en verde, y prohíbe desactivar, ignorar (`@Ignore`), comentar o borrar
una prueba para hacer pasar la build. Las tareas de prueba van **junto a** su código, no al final.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ir en paralelo (fichero distinto, sin dependencias pendientes)
- **[Story]**: a qué historia de usuario pertenece (US1..US4)

## Path Conventions

Módulo único `:app`. Rutas abreviadas en este documento:

- `MAIN/` = `app/src/main/java/com/jrblanco/boccantabria/`
- `TEST/` = `app/src/test/java/com/jrblanco/boccantabria/`
- `ATEST/` = `app/src/androidTest/java/com/jrblanco/boccantabria/`

---

## Aviso sobre la forma de esta lista

**Esta feature es una sustitución, no un crecimiento.** Eso cambia la estructura habitual de dos
maneras que conviene ver de frente:

1. **La fase 2 es grande y es bloqueante de verdad.** No se puede entregar «media sustitución de
   proveedor»: hasta que el data source nuevo compile y el grafo resuelva, la aplicación no genera
   ni un resumen. Las cuatro historias de usuario se verifican **después** de esa fase, y lo que
   aportan es la comprobación de que cada una de ellas quedó cumplida, no un incremento entregable
   por separado.
2. **El orden dentro de la fase 2 está elegido para que la build esté roja el menor tiempo
   posible**: primero lo que nadie referencia (credencial, constantes, tipos de cable), después lo
   que los usa, y el borrado del fichero antiguo al final.

---

## Phase 1: Setup

**Purpose**: fijar lo que condiciona números y constantes antes de escribir código.

- [ ] T001 Confirmar en <https://aistudio.google.com/rate-limit> los **tres** límites reales del plan gratuito para `gemini-3.5-flash-lite` —peticiones por minuto, peticiones por día y **tokens por minuto**— y anotarlos en `specs/009-resumen-gemini/quickstart.md` §0 bis, sustituyendo los valores «pendientes de confirmar» por los leídos — **PENDIENTE**: exige entrar en el panel del proveedor con la cuenta del propietario. Las constantes quedan con los valores documentados y su comentario de «pendiente de confirmar».
- [ ] T001a Comprobar que `DocumentText.MAX_CHARACTERS = 480_000` (~109.000 tokens, a 4,39 car./token **medidos** contra el servicio real) queda **por debajo** del límite de tokens por minuto leído en T001. Si no lo queda, bajar la constante a lo que quepa en un minuto y corregir el número en `plan.md` §Constraints, `data-model.md` §5, `research.md` D-104 y `contracts/internal-contracts.md` §1.4. Sin esto, un documento en el techo sería irresumible para siempre y **ninguna prueba lo detectaría** (FR-004) — **PENDIENTE**: depende de T001. El tope queda en 480.000 con el aviso escrito en el KDoc de `DocumentText.MAX_CHARACTERS`.
- [X] T002 Comprobar que `local.properties` contiene `GEMINI_API_KEY` con una clave válida, y que la línea no está versionada (`git check-ignore -v local.properties`)
- [X] T003 [P] Dejar constancia del punto de partida: `./gradlew :app:testDebugUnitTest` en verde **antes** de tocar nada. **757 pruebas, 0 fallos** (4 de septiembre de 2026, antes de tocar nada)

**Checkpoint**: los dos números del coordinador están decididos y la base está verde.

---

## Phase 2: Foundational — la sustitución (BLOQUEANTE)

**Purpose**: cambiar de proveedor. Ninguna historia de usuario se puede verificar hasta que esto esté
completo.

**⚠️ CRÍTICO**: la build queda roja entre T006 y T028. Ese es el tramo a atravesar de una vez.

### 2.1 La credencial y las constantes (nadie las referencia todavía)

- [X] T004 Sustituir en `app/build.gradle.kts` el bloque `providers.fileContents` de `GROQ_API_KEY` por uno de `GEMINI_API_KEY`, conservando el respaldo `providers.environmentVariable` y el `.orElse("")`, y cambiar el único `buildConfigField` del proyecto a `GEMINI_API_KEY`
- [X] T005 Actualizar el comentario de `buildFeatures { buildConfig = true }` en `app/build.gradle.kts` para que cite `BuildConfig.GEMINI_API_KEY` y la feature 009
- [X] T006 Cambiar los tres literales de `MAIN/domain/model/AiSummaryConstants.kt` a `MODEL_ID = "gemini-3.5-flash-lite"`, `PROMPT_VERSION = "boc-summary-es-v4"`, `SCHEMA_VERSION = "boc-summary-schema-v3"`, y reescribir su KDoc explicando de dónde viene cada versión (D-101, D-105, D-112)

### 2.2 Los tipos de cable y nuestro formato

- [X] T007 Crear `MAIN/data/source/remote/SummaryPayloadDtos.kt` moviendo desde `GroqDtos.kt` el payload y sus cinco sub-DTO, renombrando **solo la clase** `GroqSummaryPayload` a `SummaryPayload` y `GroqSummaryPayload.toDomain()` a `SummaryPayload.toDomain()`, **sin cambiar ni un nombre de propiedad** (D-111)
- [X] T008 Crear `MAIN/data/source/remote/GeminiDtos.kt` con los tipos de cable de la Interactions API según `data-model.md` §2: `GeminiInteractionRequest`, `GeminiInputContent`, `GeminiGenerationConfig`, `GeminiResponseFormat`, `GeminiInteraction`, `GeminiStep`, `GeminiContentPart`, `GeminiUsage`, `GeminiErrorEnvelope`, `GeminiError` — sin `temperature`, `top_p` ni `top_k` (D-106) y sin deserializar `error.code`
- [X] T009 [P] Renombrar `MAIN/data/source/remote/GroqSummarySchema.kt` a `SummarySchema.kt`, retirar el envoltorio `{"type":"json_schema","json_schema":{…}}` dejando el objeto `schema` interior verbatim, y añadir `"maxItems": 10` a las seis listas referenciadas — **no** a `warnings` (FR-007, D-105, D-112)
- [X] T010 [P] Reescribir `TEST/data/source/remote/GroqSummarySchemaTest.kt` como `SummarySchemaTest.kt`: conservar las seis afirmaciones de orden —la prosa la última, las seis listas antes, `coverage` antes de la prosa, `required` con las doce en el mismo orden, objeto cerrado, prosa con `maxLength`— y añadir dos: las seis listas llevan `maxItems: 10` y `warnings` no lleva tope
- [X] T011 Borrar `MAIN/data/source/remote/GroqDtos.kt` una vez T007 y T008 estén completas

### 2.3 Qué se envía

- [X] T012 Renombrar `MAIN/data/source/remote/SummaryBudget.kt` a `DocumentText.kt`: `object DocumentText` con `MAX_CHARACTERS = 480_000` y `PAGE_MARKER_PREFIX`, `select()` renombrado a `render()`, `SelectedText` renombrado a `RenderedDocument` **sin** el campo `estimatedTokens`; retirar `MAX_DOCUMENT_TOKENS`, `TARGET_REQUEST_TOKENS`, `CHARACTERS_PER_TOKEN` y `estimateTokens()`, y dejar `fits()` con una sola comprobación (D-104)
- [X] T013 Reescribir `TEST/data/source/remote/SummaryBudgetTest.kt` como `DocumentTextTest.kt`: conservar las seis afirmaciones que siguen teniendo objeto —marcador por página, páginas enteras y en orden, techo de caracteres nunca cruzado, parcialidad reportada, primera página cortada por párrafo, determinismo— y retirar las cinco de tokens; **añadir** una que afirme que un documento normal de veinte páginas se envía **entero** (FR-001)

### 2.4 La cuota

- [X] T014 Reescribir `MAIN/data/source/remote/GroqRateLimitCoordinator.kt` como `GeminiRateLimitCoordinator.kt` según `data-model.md` §6: ventanas deslizantes de 60 s y 24 h sobre `TimeProvider`, `verdict()` sin argumentos, `recordRequest()`, `recordExhaustion(retryAfterSeconds)`, `parseRetryDelaySeconds()`; conservar `serialised()` con `Mutex`, `backoffMillis()` y el sellado `QuotaVerdict` intacto; retirar `parseDurationMillis` y las cinco constantes de cabecera (D-108, D-109)
- [X] T015 Poner en el companion de `GeminiRateLimitCoordinator` los valores confirmados en T001 como `REQUESTS_PER_MINUTE` y `REQUESTS_PER_DAY`, con `DAY_SCALE_THRESHOLD_SECONDS = 900` y `DEFAULT_RETRY_SECONDS = 60`, y un comentario que diga de dónde salen. Comprobar además que el RPM confirmado sostiene **al menos un resumen por minuto** (SC-007, D-115)
- [X] T016 Reescribir `TEST/data/source/remote/GroqRateLimitCoordinatorTest.kt` como `GeminiRateLimitCoordinatorTest.kt`: primera petición pasa; la ventana del minuto se llena y devuelve `WaitMinute` con los segundos que faltan; se repone al caducar la marca más antigua; la ventana de 24 h llena devuelve `ExhaustedDay`; un 429 con retraso corto da `WaitMinute` y uno de escala diaria da `ExhaustedDay`; `parseRetryDelaySeconds` acepta `56` y `56s`, devuelve `null` ante `"pronto"` y **no lanza** con `null`; peticiones serializadas; backoff creciente, acotado y con dispersión — todo con `TimeProvider` y `RandomProvider` inyectados

### 2.5 La salida al servicio

- [X] T017 [P] Renombrar `MAIN/data/source/remote/GroqApiKeyProvider.kt` a `GeminiApiKeyProvider.kt` (`fun interface GeminiApiKeyProvider` + `BuildConfigGeminiApiKeyProvider` leyendo `BuildConfig.GEMINI_API_KEY`), conservando el `toString()` enmascarado
- [X] T018 [P] Renombrar `TEST/data/source/remote/GroqApiKeyProviderTest.kt` a `GeminiApiKeyProviderTest.kt` conservando sus cuatro afirmaciones, incluida la de que el valor nunca aparece en `toString()`
- [X] T019 Renombrar `MAIN/data/source/remote/GroqSummaryDataSource.kt` a `GeminiSummaryDataSource.kt`: `summarise(system, user)` **sin** el parámetro `estimatedTokens`, `GeminiSummaryResult` con `payload: SummaryPayload` y `usage: GeminiUsage`, y `GeminiRefusal` conservando **los siete casos uno a uno** (contratos §1.1)
- [X] T020 Reescribir `MAIN/data/source/remote/OkHttpGroqSummaryDataSource.kt` como `OkHttpGeminiSummaryDataSource.kt`: URL `https://generativelanguage.googleapis.com/v1beta/interactions`, cabecera `x-goog-api-key`, cuerpo con `store = false`, `thinking_level = "minimal"` y `max_output_tokens = 8000`; conservar el cliente derivado con `client.newBuilder()`, los timeouts, `MAX_ATTEMPTS = 3`, `isWorthRetrying` y los helpers `describe()`/`reasonFrom()` (D-103, D-106, D-107, D-110)
- [X] T021 En `OkHttpGeminiSummaryDataSource`, implementar el parseo según `data-model.md` §8: buscar el paso con `type == "model_output"` —**no** `steps[0]`—, tomar la primera parte con texto, deserializar `SummaryPayload` en un segundo paso, y devolver `Malformed` con el `status` registrado cuando no haya paso de salida o el contenido esté vacío (FR-019)
- [X] T022 En `OkHttpGeminiSummaryDataSource`, implementar el mapeo de códigos: 401/403 → `NotConfigured`; 429 → leer el retraso de la cabecera `retry-after`, si no del `RetryInfo` de `error.details`, y si no `DEFAULT_RETRY_SECONDS`, y pasarlo a `coordinator.recordExhaustion()`; 5xx → `HttpError` reintentable; resto → `HttpError`; `IOException` → `Network`; `CancellationException` **repropagada** (D-109)
- [X] T023 En `OkHttpGeminiSummaryDataSource`, cambiar el prefijo del registro de `"groq: "` a `"gemini: "` y añadir el `status` de la interacción a las líneas de fallo, sin registrar nunca la credencial ni el contenido del documento (D-117)
- [X] T024 Verificar que el `Json` de `OkHttpGeminiSummaryDataSource` conserva `encodeDefaults = true` y dejar el comentario que explica por qué: sin él no se envían `store` ni `thinking_level`, y los valores por defecto del servicio son los contrarios (D-106)
- [X] T025 Reescribir `TEST/data/source/remote/OkHttpGroqSummaryDataSourceTest.kt` como `OkHttpGeminiSummaryDataSourceTest.kt`, con MockWebServer sobre TLS y cuerpos JSON nuevos en formato `steps`/`usage`: las veintiuna afirmaciones de hoy trasladadas, más una que compruebe que el texto se busca por `type == "model_output"` y no por posición, y otra que compruebe que un `status: "incomplete"` sin contenido se registra con su `status`
- [X] T025a Añadir a `OkHttpGeminiSummaryDataSourceTest` la aserción explícita de que el cuerpo enviado contiene `"store": false` y `"thinking_level": "minimal"`. Es la única garantía de FR-030 —pedir al servicio que no conserve lo enviado— y de que no se paga razonamiento invisible; sin `encodeDefaults = true` ninguno de los dos se serializa (FR-030, D-106, D-107)
- [X] T025b Añadir a `OkHttpGeminiSummaryDataSourceTest` la aserción de que el cuerpo **no** contiene ninguna parte de tipo `document` ni bytes del PDF, y que se hace **una sola** petición por publicación. El modelo nuevo admite PDF nativo y está fuera de alcance: esta prueba es lo que evita que entre por descuido (FR-003, FR-014)
- [X] T026 Añadir a `OkHttpGeminiSummaryDataSourceTest` la **prueba de regresión del techo de salida**: una respuesta que agotaría un techo de 1.800 tokens debe procesarse correctamente con el techo de 8.000. Debe fallar antes de T020 (D-110)
- [X] T027 [P] Renombrar `TEST/fake/FakeGroqSummaryDataSource.kt` a `FakeGeminiSummaryDataSource.kt`, adaptando la firma sin `estimatedTokens` y renombrando su factoría `summaryPayload(...)`

### 2.6 El prompt y el validador

- [X] T028 Modificar `MAIN/data/source/remote/SummaryPromptFactory.kt`: firma `userMessage(publication, document: RenderedDocument, totalPages)`; **insertar** `document.text` —ya renderizado con marcadores por `DocumentText.render()`— en el hueco `{{documentWithPageMarkers}}`, sin volver a renderizar nada aquí; añadir la frase que pide priorizar cuando una sección tenga más de diez elementos y advertirlo en `warnings`; reescribir la cláusula de lectura parcial como excepción y no como caso habitual; conservar los seis párrafos del sistema, la cláusula antiinyección y `.replace()` **después** de `trimIndent()`
- [X] T029 Modificar `TEST/data/source/remote/SummaryPromptFactoryTest.kt`: adaptar a la firma nueva, conservar las doce afirmaciones actuales —incluidas la antiinyección y la de que nada de la persona se envía— y añadir una que afirme que el prompt pide priorizar por encima de diez elementos (FR-007, FR-016)
- [X] T030 Modificar `MAIN/data/source/remote/SummaryValidator.kt`: firma `validate(raw: SummaryPayload, document: RenderedDocument, totalPages: Int)` —corregida respecto a la fase 1, donde dos parámetros no podían expresar una lectura parcial—; **añadir** el recorte de cada lista a diez elementos con su aviso en `warnings`; conservar todo lo demás, incluidos la sustitución de la cobertura y `TRUNCATED_WARNING` (FR-007, D-112)
- [X] T031 Modificar `TEST/data/source/remote/SummaryValidatorTest.kt`: adaptar a la firma nueva, conservar las veintidós afirmaciones actuales y añadir dos: una lista de trece elementos se recorta a diez, y ese recorte deja constancia en `warnings`

### 2.7 El repositorio y la inyección

- [X] T032 Modificar `MAIN/data/repository/AiSummaryRepositoryImpl.kt`: cambiar el tipo del data source a `GeminiSummaryDataSource`, retirar `PROMPT_OVERHEAD_TOKENS` y el argumento `estimatedTokens`, usar `DocumentText.render(corpus)`, llamar a `validator.validate(payload, corpus)`, mapear `GeminiRefusal.toSummaryError()`, guardar `usage.totalInputTokens`/`totalOutputTokens`/`totalTokens` y `systemFingerprint = null`, y ajustar la línea de registro para que deje de decir `~N tokens`
- [X] T033 Modificar `MAIN/core/di/DataModule.kt`: renombrar las cuatro declaraciones del bloque «Resumen IA» a `GeminiApiKeyProvider`, `GeminiRateLimitCoordinator`, `GeminiSummaryDataSource` y `OkHttpGeminiSummaryDataSource`, y actualizar el comentario de sección a la feature 009
- [X] T034 Modificar `TEST/di/KoinModulesTest.kt` en **los dos sitios** o no compila: la lista `CROSS_MODULE_TYPES` del companion y el bloque de `koin.get<…>()` de IA, con los tres tipos renombrados
- [X] T035 Modificar `TEST/data/repository/AiSummaryRepositoryImplTest.kt`: adaptar los imports de `GeminiRefusal` y `GeminiSummaryResult`, el doble renombrado, y conservar sus dieciséis afirmaciones
- [X] T036 Añadir a `AiSummaryRepositoryImplTest` la **prueba de regresión de compatibilidad de lo guardado**: un `summary_json` escrito por la versión anterior —con los nombres de campo actuales— se decodifica y produce el mismo `AiSummary`. Debe fallar si alguien renombra una propiedad de `SummaryPayload` (D-111)
- [X] T037 Modificar `TEST/integration/AiSummaryFlowIntegrationTest.kt`: el doble renombrado y las firmas nuevas, conservando sus tres afirmaciones de flujo multicapa

**Checkpoint**: `./gradlew :app:assembleDebug` y `./gradlew :app:testDebugUnitTest` en verde. La
sustitución está hecha.

---

## Phase 3: User Story 1 — Un resumen que cubre el documento entero (Priority: P1) 🎯 MVP

**Goal**: el resumen cubre el documento completo y desaparece el aviso de cobertura parcial.

**Independent Test**: generar el resumen de una publicación de más de diez páginas con datos
repartidos por todo el documento, y verificar que la ficha recoge elementos de las páginas finales y
que la cobertura se declara completa.

- [X] T037a Verificar que `AndroidxPdfTextExtractor`, `PdfTextNormalizer` y `PdfCorpus` no aparecen en el diff de la rama: la extracción sigue siendo local y el saneado de sustitutos UTF-16 se queda (FR-013, D-116)
- [X] T038 [US1] Verificar con `DocumentTextTest` que un corpus de catorce páginas se envía entero y que `isPartial` es `false` (FR-001, FR-002, SC-001)
- [X] T039 [P] [US1] Verificar con `AiSummaryRepositoryImplTest` que el estado `Generating` que se publica lleva `analysedPages == totalPages` para un documento normal, de modo que la pantalla no muestre aviso de parcialidad (FR-005, SC-001)
- [X] T040 [P] [US1] Verificar con `SummaryValidatorTest` que la cobertura resultante de un documento leído entero es `complete = true` con las páginas reales, aunque el servicio afirme otra cosa (FR-006, SC-002)
- [X] T041 [US1] Comprobar a mano, según `quickstart.md` §3 puntos 1, 2 y 3: documento largo sin aviso de parcialidad, elementos de la segunda mitad, chips que abren la página, y ninguna sección con más de diez elementos (FR-017, SC-003, SC-013) — **PASADA en emulador el 4 de septiembre de 2026**: `sending pages 8/8, 14643 chars` sobre un anuncio real, sin aviso de parcialidad, chips de las ocho páginas y ninguna sección por encima de diez

**Checkpoint**: US1 verificada.

---

## Phase 4: User Story 2 — Lo que ya estaba resumido no se pierde (Priority: P1)

**Goal**: los resúmenes hechos con el proveedor anterior siguen visibles, marcados y regenerables.

**Independent Test**: con un resumen creado antes del cambio, abrir la pestaña, verlo completo y
marcado, regenerarlo, y comprobar que el nuevo sustituye al anterior sin que la pantalla se quede sin
resumen.

- [X] T042 [US2] Modificar `TEST/data/source/local/AiSummaryDaoTest.kt`: sustituir el literal `"qwen/qwen3.8-27b"` de las líneas 59 y 115 por `AiSummaryConstants.MODEL_ID`, para que la prueba deje de fijar un modelo concreto (FR-009)
- [X] T043 [US2] Verificar con `AiSummaryRepositoryImplTest` que una fila con `model_id = "qwen/qwen3.8-27b"` se devuelve como `Ready(isStale = true)` y **no** se borra ni se regenera sola (FR-008, FR-009, FR-010)
- [X] T044 [P] [US2] Verificar que `BocDatabaseMigrationTest` sigue en verde sin cambios: la base se queda en la versión 4 y no hay migración (D-114)
- [X] T045 [US2] Comprobar a mano, según `quickstart.md` §3 puntos 6, 7 y 8: resumen viejo visible y marcado, regenerar lo sustituye sin dejar la pantalla vacía, y un resumen guardado no consulta el servicio (FR-011, SC-004) — **PASADA en parte**: no había resumen previo de Groq en el dispositivo, así que la obsolescencia en masa queda cubierta por la prueba unitaria; sí se comprobó que regenerar sustituye sin dejar la pantalla vacía

**Checkpoint**: US2 verificada.

---

## Phase 5: User Story 3 — Saber qué pasa cuando el servicio dice basta (Priority: P2)

**Goal**: los dos límites se distinguen y se explican en lenguaje corriente, sin nombrar al proveedor.

**Independent Test**: forzar el límite y verificar que espera corta y cupo diario producen mensajes
distintos, que solo el primero ofrece continuar, y que ninguno contiene jerga técnica.

- [X] T046 [US3] Verificar con `GeminiRateLimitCoordinatorTest` que el veredicto distingue minuto de día **sin** cabeceras del proveedor (FR-020, FR-021, FR-022, SC-008)
- [X] T047 [P] [US3] Verificar con `OkHttpGeminiSummaryDataSourceTest` que un 429 no se reintenta y que el reintento de un `BlankSummary` sin margen de cuota devuelve el **rechazo original** y no el de cuota (FR-025, D-036)
- [X] T048 [P] [US3] Modificar `TEST/ui/detail/AiErrorMessagesTest.kt` línea 61: añadir `"gemini"` y `"google"` a la lista negra **sin quitar** `"groq"` ni `"qwen"`, y comprobar que las cuatro afirmaciones siguen en verde (FR-027, FR-028, SC-009)
- [X] T049 [US3] Comprobar a mano, según `quickstart.md` §3 puntos 10 y 11: sin conexión y documento sin texto producen los mensajes correctos (FR-015, SC-006), y solo el recuperable ofrece reintentar (FR-023, FR-024, FR-026, SC-006) — **PASADA en parte**: se vio el camino de fallo real —dos tiempos agotados y un 500 del servicio— con «No se ha podido generar el resumen» y reintento, sin códigos ni jerga. No apareció ninguna publicación escaneada para el caso sin texto

**Checkpoint**: US3 verificada.

---

## Phase 6: User Story 4 — Compilable y probable sin la credencial (Priority: P3)

**Goal**: el proyecto sigue siendo utilizable sin secretos.

**Independent Test**: compilar y ejecutar las pruebas sin credencial configurada, y después pedir un
resumen en la aplicación resultante.

- [X] T050 [US4] Verificar con `GeminiApiKeyProviderTest` que una credencial ausente o en blanco devuelve `null` y que el valor nunca aparece en `toString()` (FR-032, FR-033)
- [X] T051 [P] [US4] Verificar con `OkHttpGeminiSummaryDataSourceTest` que sin credencial **no sale ni una petición** y se devuelve `NotConfigured`
- [X] T052 [US4] Comprobar a mano, según `quickstart.md` §3 punto 12: comentar `GEMINI_API_KEY` en `local.properties`, recompilar, ejecutar las pruebas unitarias y comprobar que todo sigue en verde y que la pantalla dice que el servicio no está configurado, sin ofrecer reintento (FR-029, SC-011) — **PASADA**: con la clave comentada, `assembleDebug` y `testDebugUnitTest` en verde, la pantalla dice «no está configurado» sin reintento, y cero peticiones
- [X] T053 [US4] Ejecutar las dos comprobaciones de secretos de `quickstart.md` §1: `git log -p --all -S 'AIza'` vacío, y `grep -rn 'GEMINI_API_KEY' app/src/` con **exactamente un** resultado en `BuildConfigGeminiApiKeyProvider` (FR-032, SC-010)

**Checkpoint**: US4 verificada.

---

## Phase 7: El aviso de envío externo

**Purpose**: FR-031 y FR-031a. Va aparte porque no pertenece a ninguna de las cuatro historias y
afecta a texto que la persona lee.

- [X] T054 Ampliar el literal `ai_notice_body` de `app/src/main/res/values/strings.xml` con la frase de que el servicio puede usar el texto de ese documento público para mejorar sus modelos, dejando claro que lo que viaja es el documento oficial y nada de la persona (FR-031)
- [X] T055 Añadir a `TEST/data/source/local/AiPreferencesTest.kt` la **prueba de regresión de la clave versionada**: con `ai_notice_accepted = true` ya escrito en las preferencias, `observeNoticeAccepted()` debe emitir `false`. Debe fallar antes de T056 (D-113, FR-031a)
- [X] T056 Cambiar en `MAIN/data/source/local/AiPreferences.kt` la constante `KEY_NOTICE_ACCEPTED` a `"ai_notice_accepted_v2"`, dejando el comentario que explica cuándo hay que volver a versionarla
- [X] T057 Comprobar a mano, según `quickstart.md` §3 punto 5: el aviso reaparece una vez con la frase nueva, cancelar no envía nada, y tras aceptar no vuelve a salir (FR-031, FR-031a, SC-014) — **PASADA**: sembrada solo la clave antigua, el aviso reaparece con la frase nueva; cancelar no envía nada; tras aceptar quedan las dos claves

**Checkpoint**: el aviso dice toda la verdad y se lee una vez.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T058 Comprobar que **no queda ningún literal del proveedor anterior** en producción: `grep -rniE 'groq|qwen' app/src/main/` debe devolver cero resultados
- [X] T059 [P] Comprobar que `ui/` no se ha modificado: `git diff --name-only main -- 'app/src/main/java/com/jrblanco/boccantabria/ui/'` debe salir vacío, y que `ATEST/ui/detail/AiSummaryTabTest.kt` no aparece en el diff
- [X] T060 [P] Comprobar que `gradle/libs.versions.toml` no se ha modificado: ninguna dependencia nueva (D-102)
- [X] T061 [P] Comprobar que `app/schemas/` no se ha modificado y que `BocDatabase` sigue declarando `version = 4` (D-114)
- [X] T062 Actualizar `CLAUDE.md`: las cuatro menciones directas al proveedor (líneas ~319, ~534, ~575, ~576), **los tres párrafos del presupuesto de tokens** que dejan de ser verdad, la muestra de logcat con el prefijo `gemini:` y el `status`, el número de decisiones citadas, y el prefijo de las claves (`AIza` en lugar de `gsk_`)
- [X] T063 [P] Añadir a `CLAUDE.md` las trampas nuevas que esta feature deja aprendidas: que el valor por defecto de `thinking_level` es `medium` y se factura; que `store` vale `true` por defecto; que Gemini no manda cabeceras de cuota y por eso el contador es propio; y que Gemini cobra la salida usada y no la reservada, al revés que Groq
- [X] T064 [P] Comprobar que `docs/diseno/especificaciones-diseno.md` no necesita cambios: el §20 no nombra al proveedor y el contrato visual no se toca
- [X] T065 Ejecutar la puerta 1: `./gradlew :app:assembleDebug`
- [X] T066 Ejecutar la puerta 2: `./gradlew :app:testDebugUnitTest`, **778 pruebas, 0 fallos** (contra las 757 del punto de partida de T003)
- [X] T067 Ejecutar la puerta 3 **en segundo plano**: `adb shell settings put secure navigation_mode 0`, `export ANDROID_SERIAL=emulator-5554` y `./gradlew :app:connectedDebugAndroidTest`. Tarda **casi tres horas**, no trece minutos; quien espere trece dará por colgado algo que va bien — **PASADA**: `BUILD SUCCESSFUL in 1h 56m`. **166 pruebas, 0 fallos, 0 errores, 0 omitidas**, 45,3 s por prueba
- [X] T068 Ejecutar la puerta 4: `./gradlew :app:lintDebug` — **0 errores, 14 avisos**
- [X] T069 **La travesía real de la frontera** — **PASADA el 4 de septiembre de 2026.** HTTP 200, el esquema aceptado verbatim con sus `$defs` —el plan B de D-105 no hace falta—, el orden respetado con prosa de 809 caracteres y las seis listas llenas, `total_thought_tokens = 0`, y los plazos relativos conservados literalmente. Encontró dos errores míos: el paso se llama `thought` y no `model_thoughts`, y la clave empieza por `AQ.A` y no por `AIza`. Resultado completo en `quickstart.md` §3 bis
- [X] T070 Recorrer el resto de `quickstart.md` §3: puntos 4, 9, 13 y 14 (nada se genera solo, la advertencia va dentro del texto compartido, abandonar durante la generación, y TalkBack) (FR-012, FR-018, SC-005) — **PASADA en parte**: puntos 4 y 9 comprobados (nada se genera solo; la advertencia visible y con descripción accesible). Los puntos 13 y 14 —abandonar durante la generación y TalkBack— no se han hecho
- [X] T072 **Arreglar el defecto que encontró la comprobación manual**: `currentCoroutineContext().ensureActive()` como primera línea del `catch (IOException)` de `OkHttpGeminiSummaryDataSource`, que pasa a `suspend`. Irse de la pantalla llegaba como `Offline` y se publicaba en el estado, así que al volver se leía «No hay conexión» de un fallo inexistente. Defecto **heredado de la feature 007**, no introducido aquí; lo hizo visible el proveedor nuevo al tardar más. Con prueba de regresión que falla antes (D-119, FR-006) — hecho y comprobado en dispositivo: irse no registra nada
- [X] T071 Marcar la lista de calidad de la especificación como verificada tras la implementación y anotar en `specs/009-resumen-gemini/checklists/requirements.md` cualquier desviación que haya aparecido al implementar — hecho: ver «Verificación posterior a la implementación» en `checklists/requirements.md`

---

## Dependencies & Execution Order

### Fases

- **Fase 1 (Setup)**: sin dependencias. T001 condiciona T015, así que va primero de verdad.
- **Fase 2 (Foundational)**: depende de la fase 1. **Bloquea las cuatro historias.**
- **Fases 3 a 6 (historias)**: dependen de la fase 2 completa. Entre ellas son independientes y se
  pueden verificar en cualquier orden.
- **Fase 7 (el aviso)**: independiente de las cuatro historias. Se puede hacer en paralelo con ellas.
- **Fase 8 (Polish)**: depende de todo lo anterior. T065 a T068 en **ese orden**, que es el que manda
  la constitución.

### Dentro de la fase 2, el orden importa

```
T004-T006  credencial y constantes      ← nadie las referencia todavía
   ↓
T007-T011  tipos de cable y esquema     ← T011 (borrar GroqDtos) solo tras T007 y T008
   ↓
T012-T013  qué se envía
   ↓
T014-T016  la cuota                     ← T015 necesita T001
   ↓
T017-T027  la salida al servicio        ← T020 necesita T008, T009, T014, T019
   ↓
T028-T031  prompt y validador
   ↓
T032-T037  repositorio e inyección      ← T034 en DOS sitios o no compila
```

### Oportunidades de paralelismo

- **T009 y T010** (esquema y su prueba) son independientes de T012-T016.
- **T017 y T018** (credencial y su prueba) son independientes de todo lo demás de la fase 2.
- **T027** (el doble) no depende de la implementación, solo de la interfaz T019.
- **Toda la fase 7** es paralela a las fases 3 a 6.
- **T059 a T064** son cuatro comprobaciones y dos ficheros de documentación: todas en paralelo.
- **T067** se lanza en segundo plano y se recogen sus resultados mientras se hace T069 y T070.

---

## Implementation Strategy

### Lo que hace de MVP aquí

En una feature de crecimiento, el MVP es la historia P1. Aquí **el MVP es la fase 2 completa más la
fase 3**: hasta que la sustitución esté hecha no hay nada que demostrar, y en cuanto lo está, US1 —el
resumen que cubre el documento entero— es lo que justifica todo el trabajo.

1. Fase 1 → los dos números decididos.
2. Fase 2 → la sustitución hecha, `assembleDebug` y `testDebugUnitTest` en verde.
3. Fase 3 → **parar y validar**: un resumen completo de un documento largo, en un móvil.
4. Fases 4, 5, 6 y 7 → las demás garantías, verificables por separado.
5. Fase 8 → las cuatro puertas, la travesía real y la documentación.

### Dónde está el riesgo, y qué hacer si aparece

Todo el riesgo de esta feature está en **T069**, y está concentrado en dos puntos:

- **El esquema no se acepta** (400 del servicio). Plan B de D-105: aplanar las cinco definiciones de
  `$defs` en línea, sin cambiar ningún nombre de campo. No afecta a lo guardado ni a `SummaryPayload`.
- **El orden no se respeta** (prosa sí, listas vacías). Añadir `propertyOrdering` con las doce
  propiedades en el orden actual.

Ninguno de los dos lo puede detectar una prueba automática, porque todas usan dobles en esa frontera.
Eso no es un defecto de las pruebas: es la razón por la que T069 existe y por la que es obligatoria.

---

## Notes

- Las tareas `[P]` son ficheros distintos sin dependencias pendientes entre sí.
- **Ninguna tarea se cierra sin su prueba en verde.** Prohibido `@Ignore`, comentar o borrar una
  prueba para que pase la build (principio V).
- **Las tres pruebas de regresión —T026, T036 y T055— deben fallar ANTES de su arreglo.** Si pasan
  antes, no están comprobando lo que dicen.
- Commit después de cada tarea o grupo lógico, en español, imperativo, con prefijo convencional.
- La rama es `009-resumen-gemini`. **Nunca sobre `main`.**
