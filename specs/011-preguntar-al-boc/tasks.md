---

description: "Task list for 011-preguntar-al-boc"
---

# Tasks: Preguntar al BOC

**Input**: Design documents from `/specs/011-preguntar-al-boc/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/internal-contracts.md](./contracts/internal-contracts.md),
[quickstart.md](./quickstart.md)

**Tests**: obligatorios. El principio V de la constitución no es negociable y la octava regla de
Konsist **tumba la build** si una clase de dominio de nivel superior o un `ViewModel` no tiene fichero
de prueba. No hay tareas de prueba «opcionales» en este proyecto.

## Formato: `[ID] [P?] [Story] Descripción`

- **[P]**: se puede hacer en paralelo — fichero distinto, sin dependencias pendientes
- **[Story]**: a qué historia pertenece (US1 … US6). Setup, Foundational y Polish no llevan etiqueta

---

## Phase 1: Setup

**Propósito**: lo que hace falta antes de escribir la primera clase, y que no depende de nada.

- [X] T001 [P] Añadir `ic_send` en `app/src/main/res/drawable/ic_send.xml`, tomado de Material Symbols, **comprobando antes que el trazado lleva coordenadas negativas** (D-323)
- [X] T002 [P] Añadir a `app/src/main/res/values/strings.xml` los textos de la pantalla: título, aviso de ámbito, las tres preguntas sugeridas, la marca de posición del compositor, «Fuentes», «Ver PDF oficial», las dos fases y el contador de caracteres
- [X] T003 [P] Añadir a `strings.xml` los **ocho** mensajes de error de `AiChatError` y el texto de fuera de ámbito, todos sin código, sin número de estado y sin nombre de proveedor (FR-031)
- [X] T004 Mover `AiNoticeSheet.kt` de `ui/detail/component/` a `core/ui/component/`, actualizando el paquete y los dos usos (D-316)

---

## Phase 2: Foundational — bloquea todas las historias

**Propósito**: los tipos, la frontera con el servicio y el grafo. **Ninguna historia puede empezar
hasta que esta fase esté entera**, porque todas pasan por el mismo camino de datos.

### 2.1 Dominio

Una prueba por clase, o la build no compila.

- [X] T005 [P] `domain/model/AiAnswerScope.kt` + `AiAnswerScopeTest`
- [X] T006 [P] `domain/model/AiAnswerSource.kt` con `require(page >= 1)` + `AiAnswerSourceTest`
- [X] T007 [P] `domain/model/AiChatMessage.kt` (`Question` | `Answer`) + `AiChatMessageTest`
- [X] T008 [P] `domain/model/AiChatError.kt` con `isRetryable` + `AiChatErrorTest`, que afirma los ocho casos y su reintentabilidad
- [X] T009 [P] `domain/model/AiChatStatus.kt` con `Preparing.Phase`, `Thinking` y `Failed(error, retryableQuestionId)` + `AiChatStatusTest`
- [X] T010 [P] `domain/model/AiConversation.kt` + `AiConversationTest`
- [X] T011 [P] `domain/model/AiChatConstants.kt` como `object` (sin `MODEL_ID`, D-305)
- [X] T012 `domain/repository/AiChatRepository.kt`, las **cinco** funciones —incluida `observeAvailability()`— y **ninguna suspendida** salvo las dos que devuelven `Flow` (D-313, D-320b)
- [X] T013 [P] `domain/usecase/ObserveAiConversationUseCase.kt` + su prueba
- [X] T014 [P] `domain/usecase/AskAboutDocumentUseCase.kt` + su prueba
- [X] T015 [P] `domain/usecase/RetryLastQuestionUseCase.kt` + su prueba
- [X] T016 [P] `domain/usecase/DiscardAiConversationUseCase.kt` + su prueba
- [X] T016a [P] `domain/usecase/ObserveAiAvailabilityUseCase.kt` + su prueba (D-320b)

### 2.2 La preparación del documento, extraída *(toca código de la 010)*

- [X] T017 Crear `data/source/remote/AiDocumentPreparer.kt` con `prepare(publication, onPhase)` y `PreparationResult` de cinco casos, **contando las páginas antes de subir** (D-315, contratos §2.1)
- [X] T018 `AiDocumentPreparerTest`: las cinco salidas, el orden de las dos fases, y la regresión que importa — **un documento protegido no llega al subidor**
- [X] T019 Modificar `data/repository/AiSummaryRepositoryImpl.kt` para que use `AiDocumentPreparer` en vez de `PdfPageCounter` y `AiDocumentSessionStore` directamente, sin cambiar su interfaz ni sus errores
- [X] T020 Ejecutar `AiSummaryRepositoryImplTest` y `AiSummaryFlowIntegrationTest` **en verde antes de seguir**. La 010 tiene que quedar exactamente igual por fuera

### 2.3 La frontera con el servicio

- [X] T021 [P] `data/source/remote/ChatDtos.kt`: `ChatAnswerPayload` y `ChatSourceDto`, con `answer` **la última propiedad** (D-310)
- [X] T022 [P] `data/source/remote/ChatAnswerSchema.kt`, con `scope` primero, `sources` después y `answer` al final; `additionalProperties: false`, las tres en `required`
- [X] T023 [P] `ChatAnswerSchemaTest`: afirma el **orden** de las propiedades, calcado de `SummarySchemaTest`. Sin esta prueba, ordenarlas alfabéticamente vacía el ámbito y tumba la defensa
- [X] T024 `data/source/remote/ChatPromptFactory.kt` con las cinco cláusulas de la defensa y la pregunta delimitada; sustitución **después** de `trimIndent()` (D-307)
- [X] T025 `ChatPromptFactoryTest`: presencia de cada cláusula sobre el mensaje **con los espacios colapsados** —`replace(Regex("\\s+"), " ")`—, nunca sobre fragmentos elegidos para caber en una línea; y que la pregunta viaja delimitada y sin filtrar
- [X] T026 `data/source/remote/ChatAnswerValidator.kt`: citas fuera de `1..totalPages` descartadas, texto recortado a la última frase completa, cuerpo en blanco → `null`, `scope` desconocido → `OUT_OF_SCOPE`
- [X] T027 `ChatAnswerValidatorTest`: las cuatro reglas, más `droppedCitations` contando bien
- [X] T028 `data/source/remote/GeminiChatDataSource.kt`: interfaz, `ChatTurn` y `GeminiChatResult`
- [X] T029 `data/source/remote/OkHttpGeminiChatDataSource.kt`: `generateContent` con historial, `fileData` en el **primer** turno de usuario, esquema verbatim, `thinkingLevel` mínimo, `maxOutputTokens = 2000`, tres intentos con retroceso, `coordinator.serialised` y `verdict()` antes de cada reintento
- [X] T030 `OkHttpGeminiChatDataSourceTest` con MockWebServer **sobre TLS** (`okhttp-tls`): la credencial va en cabecera y no en cuerpo ni URL; el documento va una sola vez y en el primer turno; el esquema viaja; 401 → `NotConfigured`; 429 con `RetryInfo` real → cuota con su retraso; 500 → reintento; cuerpo ilegible → `Malformed`
- [X] T031 `OkHttpGeminiChatDataSourceTest`, segunda tanda: **`currentCoroutineContext().ensureActive()` es la primera línea del `catch (IOException)`** —regresión de la trampa de la 009: cancelar rompe el socket y sale `IOException`, no `CancellationException`—, y `CancellationException` se repropaga
- [X] T032 `OkHttpGeminiChatDataSourceTest`, tercera tanda: **cinco aserciones de que no se registra nada sensible** — ni la credencial, ni el contenido del documento, ni la pregunta, ni la respuesta; y sí la forma: fase, número de mensajes, ámbito y número de fuentes (FR-038, FR-039, FR-040)

### 2.4 El repositorio de la conversación

- [X] T033 `data/repository/AiChatRepositoryImpl.kt`: como mucho **una** conversación viva, ámbito propio `SupervisorJob() + dispatchers.io`, guarda contra pregunta en curso y contra texto en blanco, recorte a `MAX_QUESTION_LENGTH`, ventana de historial de doce mensajes (D-303, D-312, D-313)
- [X] T034 **La línea que sustituye el texto cuando el ámbito es `OUT_OF_SCOPE`**, en el repositorio y no en la pantalla, con el texto inyectado como cadena ya resuelta (contratos §3.3)
- [X] T034a `AiChatRepositoryImpl.observeAvailability()` sobre `GeminiApiKeyProvider`, **sin hacer ninguna petición** (FR-036, D-320b)
- [X] T035 `AiChatRepositoryImplTest`, primera tanda: observar no genera nada; una pregunta añade su burbuja antes de la respuesta; una segunda pregunta en curso no hace nada; una pregunta en blanco no hace nada
- [X] T036 `AiChatRepositoryImplTest`, segunda tanda: observar otra clave emite conversación vacía; `discard` cancela lo que hay en vuelo y vacía; el historial que viaja se recorta a doce y **el documento sigue estando en el primer turno** tras el recorte (D-304)

### 2.5 El grafo

- [X] T037 `core/di/DataModule.kt`: `AiDocumentPreparer`, `GeminiChatDataSource`, `ChatPromptFactory`, `ChatAnswerValidator`, `AiChatRepository` —**resolviendo aquí el texto de fuera de ámbito** con `androidContext().getString(...)`, porque `data` no lee `strings.xml` (contratos §3.3)—; y `AiSummaryRepositoryImpl` con su lista de dependencias cambiada
- [X] T038 `core/di/DomainModule.kt`: los **cinco** casos de uso
- [X] T039 `core/di/UiModule.kt`: `viewModel { AskViewModel(...) }`
- [X] T040 `KoinModulesTest`: actualizar **las dos** listas, `CROSS_MODULE_TYPES` y la resolución uno a uno. Un tipo en solo una de las dos se resuelve en la prueba y falla en el móvil

**Checkpoint**: el grafo arranca, el transporte tiene pruebas y el Resumen IA sigue exactamente igual.

---

## Phase 3: User Story 1 — Preguntar y obtener una respuesta con sus páginas (P1) 🎯 MVP

**Objetivo**: se escribe una pregunta, llega la respuesta, y sus fuentes abren el documento en la
página citada.

**Prueba independiente**: con el documento ya preparado, enviar una pregunta y comprobar que aparecen
la pregunta, la respuesta y unas fuentes que llevan al visor en su página.

- [X] T041 [US1] `ui/ask/AskUiState.kt` con `canSend`, `showSuggestions` y `showCounter` (data-model §3.1)
- [X] T042 [US1] `ui/ask/AskViewModel.kt`: observa publicación, guardados, conversación, aviso y disponibilidad; expone `onDraftChange`, `onSend`, `onSuggestionTapped`, `onRetry`, `onToggleSaved`, `onNoticeAccepted`, `onNoticeDismissed`
- [X] T042a [US1] **Agrupar en un tipo propio lo que viene del repositorio para no pasar de cinco flujos en el `combine`.** Con seis o más cae en la sobrecarga de `vararg`, que exige el mismo tipo y devuelve `Array<Any?>`; es la piedra con la que ya tropezó `PublicationDetailViewModel` (D-327b)
- [X] T043 [US1] `AskViewModelTest` con Turbine sobre el `StateFlow`: el estado inicial, enviar limpia el borrador, y `canSend` es falso con borrador vacío, con una pregunta en curso, **sin publicación cargada** y **sin credencial** (A4)
- [X] T043a [US1] `AskViewModelTest`: el contador de caracteres aparece a partir de `COUNTER_VISIBLE_FROM` y `canSend` es falso al pasar de `MAX_QUESTION_LENGTH` — el límite **se ve antes de enviar**, no después (FR-007)
- [X] T044 [P] [US1] `ui/ask/component/ChatBubble.kt`: pregunta y respuesta distinguibles a simple vista, con su hora. Solo tokens
- [X] T045 [P] [US1] `ui/ask/component/AnswerSources.kt`: el bloque «Fuentes», cada línea con página y etiqueta y una flecha
- [X] T045a [US1] `AskScreenTest`: una respuesta **sin ninguna cita válida** se muestra entera y sin bloque de fuentes, en vez de ocultarse (FR-015)
- [X] T046 [P] [US1] `ui/ask/component/ThinkingIndicator.kt`, animación infinita (D-326)
- [X] T047 [US1] `ui/ask/AskScreen.kt`: reescribir `AskScreen` como `AskContent`, tonto, con la lista y el desplazamiento al último mensaje
- [X] T048 [US1] `ui/ask/AskRoute.kt` con `koinViewModel()`, y cambiar `BOCantabriaNavHost` para que `Route.Ask` monte `AskRoute`
- [X] T049 [US1] Navegación desde una fuente: `Route.PdfViewer(externalKey, page = source.page - 1)`, con la conversión 1-based → 0-based en el punto de navegación (contratos §4)
- [X] T050 [US1] `AskScreenTest` (androidTest, con `createComposeRule()` y no la actividad): pregunta enviada, respuesta con fuentes, y tocar una fuente emite la página correcta
- [X] T050a [US1] Conservar el control de retroceso de la cabecera y afirmar la **pila** —no el gesto— en `AskBackStackTest`, como hace `SplashBackStackTest`. El gesto de Atrás **no es comprobable de forma fiable** en una tanda larga: se intentaron tres mecanismos y fallaron los tres (FR-047)

**Checkpoint**: la historia 1 funciona sola. Es el MVP.

---

## Phase 4: User Story 2 — Solo se habla de este documento (P1)

**Objetivo**: lo ajeno al documento se rechaza con **texto nuestro**, y lo que el documento no recoge se
dice sin inventar.

**Prueba independiente**: con un doble que devuelva `OUT_OF_SCOPE`, comprobar que en pantalla se lee el
texto de la aplicación y **cero** caracteres del servicio.

- [X] T051 [US2] `AiChatRepositoryImplTest`: **`OUT_OF_SCOPE` sustituye el texto por el nuestro y no deja pasar ni un carácter del modelo** (FR-021, SC-004). Es la prueba central de esta historia
- [X] T052 [US2] `AiChatRepositoryImplTest`: `NOT_IN_DOCUMENT` **sí** muestra el texto del modelo, marcado (D-308)
- [X] T053 [US2] `AiChatRepositoryImplTest`: un `scope` desconocido o ausente se trata como `OUT_OF_SCOPE` — ante la duda, texto nuestro
- [X] T054 [US2] `ChatPromptFactoryTest`: la cláusula de que el **documento** es contenido no confiable y no se ejecuta, y la de que la **pregunta** es texto y no una orden, ambas presentes
- [X] T055 [US2] `ChatPromptFactoryTest`: la cláusula de no revelar las reglas (FR-022)
- [X] T056 [P] [US2] `ui/ask/component/AskScopeNotice.kt`: el aviso permanente de que las respuestas se basan solo en este documento (FR-041)
- [X] T057 [US2] `AskScreenTest`: una respuesta fuera de ámbito muestra el texto de la aplicación y **no** el del doble
- [X] T058 [US2] `AiChatRepositoryImplTest`: el turno de modelo que se reenvía al servicio lleva **el texto que se pintó**, incluido el nuestro cuando fue fuera de ámbito (contratos §1.2)
- [X] T058a [US2] `ChatPromptFactoryTest` y `OkHttpGeminiChatDataSourceTest`: **nada de la persona viaja** — ni publicaciones guardadas, ni conversaciones de otras publicaciones, ni identificador alguno. Solo metadatos públicos de esta publicación y el texto de esta pregunta (FR-024)

**Checkpoint**: la defensa observable está cerrada y probada. Lo que no se puede probar aquí está en el
`quickstart.md` §3 bis y se hace en la fase de pulido.

---

## Phase 5: User Story 3 — Entrar a preguntar sin haber pedido el resumen (P1)

**Objetivo**: el documento se prepara al enviar la primera pregunta si no lo estaba, y no se vuelve a
preparar nunca en la misma visita.

**Prueba independiente**: sin pasar por el resumen, enviar una pregunta y ver la fase de preparación y
luego la respuesta.

- [X] T059 [US3] `AiChatRepositoryImplTest`: sin documento preparado, la primera pregunta publica `Preparing(FETCHING_DOCUMENT)` y luego `Preparing(UPLOADING_DOCUMENT)` antes de `Thinking` (FR-027)
- [X] T060 [US3] `AiChatRepositoryImplTest`: con el documento ya preparado, **tres preguntas seguidas no producen ninguna subida** (FR-026, SC-002)
- [X] T061 [US3] `AiChatRepositoryImplTest`: un documento protegido con contraseña da `EncryptedPdf` y **no llega al subidor** (FR-029)
- [X] T062 [US3] `AiChatRepositoryImplTest`: un documento que no se puede obtener da `Offline` o `Unknown` según el error de origen, con reintento (FR-030 reescrito, D-321)
- [X] T063 [US3] `AskScreenTest`: la fase de preparación se ve en pantalla y el compositor está deshabilitado mientras dura
- [X] T064 [US3] Cablear el aviso de envío externo en `AskViewModel`: si no está aceptado, la primera pregunta abre `AiNoticeSheet` en vez de enviar; aceptar envía; cancelar no envía nada (FR-042, D-316)
- [X] T065 [US3] `AskViewModelTest`: las tres ramas del aviso, y que **una vez aceptado no vuelve a pedirse** ni aquí ni en el resumen

**Checkpoint**: los dos caminos de entrada funcionan y comparten una sola subida.

---

## Phase 6: User Story 4 — La conversación dura lo que dura la visita (P2)

**Objetivo**: sobrevive a ir al detalle y volver; muere al salir de la publicación, y el documento con
ella.

**Prueba independiente**: preguntar, volver, entrar y ver los mensajes; salir, volver a entrar y ver la
conversación vacía.

- [X] T066 [US4] `PublicationDetailViewModel.onCleared()` llama **también** a `DiscardAiConversationUseCase`, junto al que ya libera el documento (D-314)
- [X] T067 [US4] `PublicationDetailViewModelTest`: `onCleared()` invoca **las dos** limpiezas, con la clave correcta
- [X] T068 [US4] `AiChatRepositoryImplTest`: la conversación sobrevive a que el modelo de pantalla de Preguntar se destruya y se vuelva a crear
- [X] T069 [US4] `AiChatRepositoryImplTest`: abrir otra publicación descarta la anterior — como mucho una viva (FR-011, D-312)
- [X] T070 [US4] `AiChatRepositoryImplTest`: **salir de la pantalla mientras se espera no cancela la petición**, y la respuesta aparece igual (D-313, FR-037)
- [X] T071 [US4] `AskScreenTest`: la conversación se restaura al volver a montar la pantalla

**Checkpoint**: el ciclo de vida está cerrado y el documento se retira una sola vez, en un solo sitio.

---

## Phase 7: User Story 5 — Cuando algo va mal, se entiende (P2)

**Objetivo**: ocho errores, ocho frases en castellano, reintento donde ayuda y en ningún otro sitio.

**Prueba independiente**: provocar cada fallo con un doble y comprobar el mensaje y la presencia del
botón.

- [X] T072 [P] [US5] `ui/ask/component/ChatErrorRow.kt`: la frase y, si procede, «Reintentar»
- [X] T073 [US5] Mapeo `AiChatError` → recurso de cadena en `ui/ask/`, exhaustivo por `when` sobre el sellado
- [X] T074 [US5] `AiChatErrorMessagesTest`: los ocho casos tienen texto, **ninguno contiene un código, un número de estado ni el nombre del proveedor** (FR-031), calcado de `AiErrorMessagesTest`
- [X] T075 [US5] `AiChatRepositoryImplTest`: al fallar, **la pregunta se queda en la lista** y `retryableQuestionId` la señala (FR-033, D-320)
- [X] T076 [US5] `AiChatRepositoryImplTest`: `retry` reenvía esa misma pregunta y no duplica la burbuja
- [X] T077 [US5] `AiChatRepositoryImplTest`: sin credencial, `NotConfigured`, **cero peticiones** y el compositor deshabilitado (FR-036, SC-010)
- [X] T078 [US5] `AiChatRepositoryImplTest`: cuota de minuto y cuota de día se distinguen y solo la primera ofrece reintentar (D-318, D-319)
- [X] T079 [US5] `AskScreenTest`: un error con reintento y otro sin él, y que el texto no lleva ningún código

**Checkpoint**: los ocho caminos de fallo dicen algo útil.

---

## Phase 8: User Story 6 — La pantalla se parece a la aplicación (P3)

**Objetivo**: que quien llega del detalle no sienta que ha cambiado de aplicación.

**Prueba independiente**: abrir la pantalla y comprobar cabecera, aviso, sugeridas, compositor y pie, y
que el compositor no queda tapado.

- [X] T080 [P] [US6] `ui/ask/component/AskDocumentHeader.kt`: título, fecha y la estrella de guardar (FR-043, FR-044)
- [X] T081 [P] [US6] `ui/ask/component/SuggestedQuestions.kt`: tres chips, solo con la conversación vacía (FR-045, D-325)
- [X] T082 [P] [US6] `ui/ask/component/AskFooter.kt`: «Ver PDF oficial» (FR-046)
- [X] T083 [US6] `ui/ask/component/AskComposer.kt` como `bottomBar`, con `windowInsetsPadding(systemBars.only(Horizontal + Bottom))` **dentro de su `Surface`** y `imePadding()` (D-324, FR-048)
- [X] T084 [US6] `AskComposerInsetTest` (androidTest), calcado de `DetailActionBarInsetTest`: el margen inferior no se pierde. **Solo muerde con `navigation_mode 0`**
- [X] T085 [US6] `AskScreenTest`: la cabecera muestra título y fecha, y la estrella guarda y desguarda
- [X] T086 [US6] `AskScreenTest`: las sugeridas se ven con la conversación vacía y desaparecen con el primer mensaje. **Con el reloj conducido a mano** si el indicador está en pantalla (D-326)
- [X] T087 [US6] Repasar que ningún fichero de `ui/ask/` importa `androidx.compose.ui.graphics.Color` — hay una regla de Konsist que tumba la build, pero es más barato verlo antes

**Checkpoint**: las seis historias, completas.

---

## Phase 9: Polish & Cross-Cutting

- [X] T088 [P] Registro: prefijo `chat:` con fase, número de mensajes, ámbito, fuentes y motivo; **nunca** credencial ni contenido (D-328)
- [X] T089 [P] Analítica: `ai_question_asked` con el ámbito y nada más — sin texto, sin identificador, sin clave de publicación (D-329)
- [X] T089a [P] Comprobar que `gradle/libs.versions.toml` **no ha cambiado**: `git diff main -- gradle/libs.versions.toml` sin salida (SC-012)
- [X] T090 `ArchitectureRulesTest`: comprobar que las **nueve** reglas siguen en verde con los ficheros nuevos, en especial que `ui` no importa nada de `data`
- [X] T091 Ejecutar `./gradlew :app:assembleDebug` y `:app:testDebugUnitTest` en verde
- [X] T092 `adb shell settings put secure navigation_mode 0` y ejecutar `:app:connectedDebugAndroidTest` en segundo plano — **tarda cerca de dos horas**
- [X] T093 Ejecutar `./gradlew :app:lintDebug` sin errores
- [X] T094 Recorrer a mano `quickstart.md` §3.1 a §3.7 y anotar cada resultado en la tabla del §5
- [X] T095 **Recorrer la batería de desvío del `quickstart.md` §3 bis.1**, las siete filas, anotando el ámbito de cada una. La fila 7 —una pregunta legítima— importa tanto como las seis primeras
- [X] T096 **Fabricar el PDF con la instrucción inyectada y recorrer §3 bis.2.** Si falla, la feature no está terminada
- [X] T097 Comprobar §3 bis.3: `grep` del registro y `git grep` de la credencial con `':!app/google-services.json'`
- [X] T098 Actualizar `CLAUDE.md`: el mapa de arquitectura con `ui/ask/`, `AiNoticeSheet` en `core/ui/component`, `AiDocumentPreparer` como sitio único de la preparación, la conversación en memoria y su ciclo de vida, la muestra de registro `chat:` y lo que esta feature enseñó
- [X] T099 Cerrar `tasks.md` y la tabla de resultados de `quickstart.md` con las cuatro puertas y sus cifras

---

## Dependencias y orden

### Entre fases

- **Setup (1)**: sin dependencias
- **Foundational (2)**: depende de Setup. **Bloquea todas las historias**
- **US1 (3)**: depende de Foundational. Es el MVP
- **US2 (4)**: depende de Foundational; sus pruebas de pantalla dependen de US1
- **US3 (5)**: depende de Foundational; T064 depende de T004 (el aviso movido)
- **US4 (6)**: depende de Foundational; T071 depende de US1
- **US5 (7)**: depende de Foundational; T079 depende de US1
- **US6 (8)**: depende de US1 para tener una pantalla donde montar los componibles
- **Polish (9)**: depende de todo

### Dentro de Foundational, el único orden que importa

**T017 → T018 → T019 → T020 es una cadena y no se puede paralelizar.** Es el momento en que se toca
código de la 010, y T020 es la puerta: si el Resumen IA no está en verde después de T019, no se sigue.

### Oportunidades de paralelismo

- Todo el Setup salvo T004
- Los doce tipos de dominio de §2.1, cada uno con su prueba, salvo T012 que los usa
- §2.3 y §2.4 no: el repositorio necesita el origen de datos
- Los componibles de cada historia entre sí, porque son ficheros distintos

---

## Estrategia

### MVP primero

1. Fase 1 y Fase 2 completas
2. Fase 3 (US1) → **parar y comprobar**: se pregunta, se responde, las fuentes llevan a su página
3. Fase 4 (US2) inmediatamente después, y no más tarde: es la condición con la que nació la feature

### Después

US3, US4, US5 y US6 en ese orden, cada una comprobable por su cuenta. Y el pulido entero, incluida la
travesía manual, que **no es opcional**: es la única comprobación que existe de la User Story 2.

---

## Notas

- Ninguna tarea se marca `[X]` sin su prueba en verde. Marcar en bloque es exactamente cómo la 010 se
  dio por terminada tres veces sin estarlo
- Prohibido `@Ignore`, comentar o borrar una prueba para que pase la build
- Commits en español, imperativo, con prefijo Conventional Commits
- `--tests` **no existe** en `connectedDebugAndroidTest`; para una sola clase,
  `-Pandroid.testInstrumentationRunnerArguments.class=…`


---

## Cómo quedó

**107 tareas, 107 hechas.** Las cuatro puertas:

| Puerta | Resultado |
|---|---|
| `assembleDebug` | ✅ |
| `testDebugUnitTest` | ✅ **938 pruebas, 0 fallos** (761 antes de la feature) |
| `connectedDebugAndroidTest` | ✅ **177 pruebas, 0 fallos, 134 minutos** (153 antes). `AskScreenTest` se reejecutó tras el último arreglo de pantalla —26 pruebas, 0 fallos—, porque dar por verde una clase cuya pantalla se ha tocado después es exactamente lo que la 010 enseñó a no hacer |
| `lintDebug` | ✅ 16 avisos, **0 errores** |

Y la travesía del §3 bis, que es la única comprobación que existe de la User Story 2: la batería de
siete preguntas **7/7** y un PDF con la instrucción inyectada dentro que **no se obedece**.

### Cinco defectos que encontró escribir las pruebas y recorrer la app, no la revisión

Se anotan porque un defecto encontrado y corregido en silencio parece un defecto que nunca existió:

1. **`orNotAvailable()` en `ChatPromptFactory` no tenía forma de ejecutarse**: los tres campos que
   interpola son siempre no vacíos. Retirado, no dejado vivo (principio V).
2. **Tocar una sugerencia leía `uiState` antes de que se recompusiera**, así que enviaba el borrador
   anterior. Una carrera real, no un artefacto de la prueba. Ahora viaja el valor.
3. **`saveFailed` llegaba al estado y nadie lo mostraba**: guardar podía fallar en silencio desde
   Preguntar. Cableado `SaveFailureToast`, que ya existía para las otras tres pantallas.
4. **La guarda de «una pregunta a la vez» era del proceso y no de la conversación**, lo que dejaba
   inalcanzable la cancelación al cambiar de publicación.
5. **FR-036 estaba cumplido a medias, y lo destapó recorrer la app sin credencial**: el compositor
   quedaba muerto y nada decía por qué. Ninguna prueba lo veía porque ninguna miraba lo que *no*
   estaba.

### Una traza sin explicar

Al terminar la tanda instrumentada, Gradle vuelca `Logcat of last crash` con un
`RuntimeException: Unable to get provider androidx.startup.InitializationProvider` en el proceso
**de pruebas** (`com.jrblanco.boccantabria.test`). Las 177 pruebas pasaron y ninguna falló por ello.
El proveedor lo declara el manifiesto fusionado de la aplicación, no esta feature. **No se ha
establecido cuándo empezó a aparecer**, y anotarlo así es preferible a inventarle una causa.

---

## Lo que encontró `/speckit-analyze` y se corrigió antes de escribir código

Doce hallazgos sobre estos mismos artefactos, uno crítico y dos altos. Se anotan porque un defecto
encontrado y corregido en silencio parece un defecto que nunca existió:

- **Crítico** — `AskUiState.isServiceConfigured` existía en el modelo de datos **sin ninguna costura
  que lo alimentara**. El Resumen IA descubre «no configurado» al pulsar, y FR-036 exige saberlo al
  abrir. Se añadieron `observeAvailability()`, su caso de uso y sus tareas (T012, T016a, T034a, T038).
- **Alto** — FR-024, que prohíbe que viaje nada de la persona, **no tenía ninguna prueba**. Es un
  requisito de privacidad y el resumen sí tiene la suya (T058a).
- **Alto** — el modelo de pantalla iba a combinar seis flujos, y `combine` pasa de cinco a la
  sobrecarga de `vararg`. Es la piedra con la que ya tropezó el detalle (T042a).
- **Medios** — `canSend` no exigía publicación cargada (T043); nadie decía quién resuelve el texto de
  fuera de ámbito (T037); FR-047, FR-015 y FR-007 no tenían prueba (T050a, T045a, T043a).
- **Bajos** — SC-012 no se verificaba (T089a). Y dos que se dejan como están **a conciencia**: tres
  enumerados `Phase` con los mismos dos valores, cada uno con su dueño; y `SummaryUsage` reutilizado
  por el chat con un nombre que dice «summary», deuda con nombre que renombrar costaría tocar la 010.
