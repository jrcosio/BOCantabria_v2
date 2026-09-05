# Implementation Plan: Preguntar al BOC

**Branch**: `011-preguntar-al-boc` | **Date**: 5 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-preguntar-al-boc/spec.md`

---

## Summary

La pantalla **Preguntar** deja de ser un «Próximamente» y pasa a ser una conversación sobre el
documento oficial de la publicación. Se apoya entera en lo que la feature 010 dejó montado: el
documento ya se sube una vez por visita, ya se reutiliza y ya se retira al salir. Esta feature añade el
**segundo consumidor** de esa sesión, que es la razón por la que se construyó.

La ruta técnica, en una frase: un `GeminiChatDataSource` hermano del del resumen, con historial propio
que viaja en cada turno, respuesta con **esquema estricto** cuyo primer campo declara el **ámbito**, y
una conversación en memoria que vive lo que dura la visita y la descarta el detalle al hacer *pop*.

Y una decisión de fondo que ordena el resto: **la defensa de que solo se hable del documento es una
capa observable, no una esperanza**. El esquema obliga al servicio a declarar si su respuesta sale del
documento; cuando dice que no, lo que se pinta es texto nuestro. Esa sustitución es una línea del
repositorio, tiene prueba propia, y es la única parte de las cinco capas que una prueba automática de
esta casa puede afirmar. Las otras cuatro viven al otro lado de la frontera y se comprueban a mano.

**Cero dependencias nuevas.** Todo lo que hace falta ya está en el catálogo.

---

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11, `minSdk 28` / `targetSdk 37`

**Primary Dependencies**: Jetpack Compose (BOM), Koin, OkHttp 5.5.0, kotlinx-serialization, Room 2.8.2,
`androidx.pdf` (beta), Firebase (Analytics, Crashlytics). **Ninguna nueva** (SC-012, D-331)

**Storage**: ninguno. La conversación vive en memoria. **La base de datos se queda en la versión 4**,
sin migración y sin tabla nueva

**Testing**: JUnit 4, MockK, `kotlinx-coroutines-test`, Turbine, Robolectric, MockWebServer con
`okhttp-tls`, Konsist, `createComposeRule` / `createAndroidComposeRule`

**Target Platform**: Android 9 (API 28) en adelante, teléfono, orientación vertical, tema claro único

**Project Type**: aplicación Android de módulo único (`:app`), arquitectura limpia + MVVM

**Performance Goals**: la pantalla se pinta con lo que ya hay en memoria, sin esperar a la red. La
respuesta tarda lo que tarde el servicio —del orden de segundos— y mientras tanto hay un indicador

**Constraints**: el consumo del plan gratuito es compartido entre resumen y chat, y una petición a la
vez en toda la aplicación (`serialised`). Nunca la credencial ni el contenido en el registro. La
pregunta se acota en 500 caracteres y el historial que viaja, en 12 mensajes

**Scale/Scope**: una conversación viva en el proceso; nueve componibles nuevos; siete tipos de dominio
nuevos con sus cinco casos de uso; siete clases de datos nuevas más una extraída de la 010

---

## Constitution Check

*Puerta obligatoria antes de la fase 0 y revisada de nuevo tras la fase 1.*

| Principio | Cómo se cumple | Veredicto |
|---|---|---|
| **I — SDD, no negociable** | Ciclo completo antes de una línea de producto: `specify` → `plan` → `tasks` → `analyze` → `implement`. Rama `011-preguntar-al-boc` creada por Spec Kit sobre `main`, con la 010 ya fusionada | ✅ |
| **II — Arquitectura limpia por capas** | `domain` estrictamente Kotlin puro: los seis tipos nuevos no importan nada de Android. `data` implementa lo que `domain` declara y **ningún DTO cruza a `ui`**: `ChatAnswerPayload` se traduce a `AiChatMessage.Answer` en el repositorio. `ui` solo habla con casos de uso | ✅ |
| **III — MVVM** | `AskScreen` + `AskViewModel` + `AskUiState` + `component/`. `MutableStateFlow` privado, `StateFlow` de solo lectura expuesto, estado inmutable, componibles tontos. Cero lógica de negocio en un `@Composable`: la sustitución del texto fuera de ámbito ocurre en `data`, precisamente para que la pantalla no pueda saltársela | ✅ |
| **IV — Koin** | Todo el grafo en `core/di`, nada instanciado a mano, `viewModel { }` para el nuevo modelo de pantalla, y `KoinModulesTest` actualizado en sus **dos** listas | ✅ |
| **V — Testing exigente, no negociable** | Prueba por cada clase de dominio (regla octava de Konsist), pruebas de repositorio y de origen de datos con MockWebServer sobre TLS, pruebas instrumentadas de la pantalla, y **cero `@Ignore`**. Lo que no se puede automatizar se dice en voz alta y va al `quickstart.md` §3 bis en vez de fingirse cubierto | ✅ |
| **VI — Observabilidad desacoplada** | Ni `ui` ni `domain` tocan Firebase. Registro por `CrashReporter` y analítica por `AnalyticsTracker`, ambos inyectados. **Nunca** el texto de la pregunta, el de la respuesta, el contenido del documento ni la credencial | ✅ |

**Restricciones tecnológicas**: ninguna dependencia nueva; ninguna coordenada literal en un
`build.gradle.kts`; ningún color, tamaño ni espaciado literal —tokens de `BocTheme` y `MaterialTheme`—;
`java.time` nativo; ningún `@Query` de borrado.

**Puertas de calidad**: las cuatro de siempre, en orden, con `navigation_mode 0` antes de la tanda
instrumentada. La quinta puerta que la 010 se planteó —compilar release con R8— **no aplica aquí**: se
retiró con la librería que la exigía y esta feature no añade nada que empaquetar.

**Sin violaciones que justificar.** La sección de complejidad va vacía, y eso es una afirmación: si
apareciera algo ahí, habría que discutirlo antes de escribir código.

---

## Project Structure

### Documentation (this feature)

```text
specs/011-preguntar-al-boc/
├── spec.md                        50 FR, 12 SC, 6 historias
├── plan.md                        este fichero
├── research.md                    D-301 … D-331
├── data-model.md                  tipos de dominio, datos y presentación
├── contracts/
│   └── internal-contracts.md      las costuras que esta feature crea o modifica
├── quickstart.md                  las cuatro puertas y el §3 bis obligatorio
├── checklists/
│   └── requirements.md            calidad de la especificación
└── tasks.md                       lo genera /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── core/
│   ├── di/
│   │   ├── DataModule.kt              M  preparer, chat data source, prompt, validador, repositorio
│   │   ├── DomainModule.kt            M  cinco casos de uso nuevos
│   │   └── UiModule.kt                M  AskViewModel
│   └── ui/component/
│       └── AiNoticeSheet.kt           →  movido desde ui/detail/component (D-316)
├── data/
│   ├── repository/
│   │   ├── AiChatRepositoryImpl.kt    +  la conversación, en memoria y con ámbito propio
│   │   └── AiSummaryRepositoryImpl.kt M  usa AiDocumentPreparer (D-315)
│   └── source/remote/
│       ├── AiDocumentPreparer.kt      +  los cuatro pasos comunes, extraídos
│       ├── ChatAnswerSchema.kt        +  scope, sources, y answer la última
│       ├── ChatAnswerValidator.kt     +  citas imposibles fuera, blanco rechazado
│       ├── ChatDtos.kt                +  ChatAnswerPayload, ChatSourceDto
│       ├── ChatPromptFactory.kt       +  las cinco cláusulas y la pregunta delimitada
│       ├── GeminiChatDataSource.kt    +  interfaz + GeminiChatResult
│       └── OkHttpGeminiChatDataSource.kt  +  hermano del de resumen
├── domain/
│   ├── model/
│   │   ├── AiAnswerScope.kt           +
│   │   ├── AiAnswerSource.kt          +
│   │   ├── AiChatConstants.kt         +
│   │   ├── AiChatError.kt             +
│   │   ├── AiChatMessage.kt           +
│   │   ├── AiChatStatus.kt            +
│   │   └── AiConversation.kt          +
│   ├── repository/AiChatRepository.kt +
│   └── usecase/
│       ├── AskAboutDocumentUseCase.kt        +
│       ├── DiscardAiConversationUseCase.kt   +
│       ├── ObserveAiAvailabilityUseCase.kt   +
       ├── ObserveAiConversationUseCase.kt   +
│       └── RetryLastQuestionUseCase.kt       +
└── ui/
    ├── ask/
    │   ├── AskRoute.kt                +  con estado, koinViewModel()
    │   ├── AskScreen.kt               M  de «Próximamente» a AskContent, tonto
    │   ├── AskUiState.kt              +
    │   ├── AskViewModel.kt            +
    │   └── component/
    │       ├── AnswerSources.kt       +
    │       ├── AskComposer.kt         +
    │       ├── AskDocumentHeader.kt   +
    │       ├── AskFooter.kt           +
    │       ├── AskScopeNotice.kt      +
    │       ├── ChatBubble.kt          +
    │       ├── ChatErrorRow.kt        +
    │       ├── SuggestedQuestions.kt  +
    │       └── ThinkingIndicator.kt   +
    ├── detail/PublicationDetailViewModel.kt   M  onCleared() descarta también la conversación
    └── navigation/BOCantabriaNavHost.kt       M  Route.Ask pasa a AskRoute

app/src/main/res/
├── drawable/ic_send.xml               +  con coordenadas negativas comprobadas (D-323)
└── values/strings.xml                 M  errores, fases, sugeridas, aviso de ámbito, fuera de ámbito
```

**Structure Decision**: se mantiene el módulo único `:app` con separación por paquetes y la regla de
dependencias `ui → domain ← data`. `ui/ask/` pasa de un fichero suelto a la estructura de la casa
—ruta, pantalla, modelo, estado y `component/`—, que es la misma que tienen `home`, `detail`, `search`
y `saved`.

---

## Fases

### Fase 0 — Investigación *(hecha)*

`research.md`, decisiones **D-301 … D-331**. Las cinco que más ordenan el resto:

- **D-301**: origen de datos propio para el chat, hermano y no método del resumen.
- **D-307/D-308**: cinco capas de defensa, y solo la del ámbito es demostrable. Tres valores, y solo
  `OUT_OF_SCOPE` sustituye el texto.
- **D-312/D-313**: la conversación vive en un repositorio, como mucho una, y el trabajo corre en un
  ámbito suyo para que salir de la pantalla no cancele lo que ya está pagado.
- **D-315**: la preparación del documento se extrae y **se modifica código de la 010**, porque
  duplicar treinta líneas es duplicar el invariante del PDF protegido.
- **D-321**: un hallazgo que **corrige la especificación**. `Publication.documentUrl` no es nulable, así
  que el estado que FR-030 describía no existe; el requisito se reescribió en vez de cumplirse a medias.

### Fase 1 — Diseño *(hecha)*

`data-model.md`, `contracts/internal-contracts.md`, `quickstart.md`.

**Constitution Check tras el diseño**: se vuelve a pasar. El diseño no introdujo ninguna violación, y
el punto que más lo tensaba —dónde ocurre la sustitución del texto fuera de ámbito— se resolvió a favor
del principio III: en `data`, no en la pantalla.

### Fase 2 — Tareas

`/speckit-tasks`. Orden previsible: dominio y sus pruebas → el preparador extraído y la 010 verde otra
vez → transporte con MockWebServer → repositorio → modelo de pantalla → componibles → pantalla y
navegación → instrumentadas → travesía manual → documentación.

**`/speckit-analyze` encontró doce defectos en estos artefactos antes de escribir código**, uno
crítico: `AskUiState.isServiceConfigured` existía sin ninguna costura que lo alimentara, y FR-036 exige
saber que no hay credencial **al abrir** la pantalla, no al pulsar. Se corrigió aquí, no en la
implementación. El detalle está al final de `tasks.md`.

### Fase 3 — Implementación

`/speckit-implement`, previo `/speckit-analyze`. Ninguna línea de producto antes de un `tasks.md`
aprobado.

---

## Riesgos, con su salida

| Riesgo | Salida |
|---|---|
| **El modelo no respeta el ámbito y etiqueta mal** | Se ve en el §3 bis, que es obligatorio. La salida es afinar el prompt; el mecanismo de sustitución no depende de que acierte, solo de que declare |
| **La respuesta se corta y `scope` viene vacío** | No puede: `scope` va **primero** en el esquema (D-310), que es exactamente la lección medida de `SummarySchema`. Lo vigila `ChatAnswerSchemaTest` |
| **Extraer `AiDocumentPreparer` rompe el Resumen IA** | Las pruebas de la 010 existen y son la red. Si algo se mueve, se ve en `AiSummaryRepositoryImplTest` y en `AiSummaryFlowIntegrationTest` antes de tocar la pantalla |
| **El compositor queda bajo la barra del sistema** | Prueba de márgenes calcada de `DetailActionBarInsetTest`, y `navigation_mode 0` antes de la tanda, sin lo cual la prueba pasa sin comprobar nada |
| **`ic_send` no se ve** | Se comprueba que el trazado lleva coordenadas negativas antes de meterlo en la plantilla de 960. Le pasó a `ic_ai` durante cuatro features sin que nada fallara |
| **El indicador animado cuelga las pruebas** | `mainClock.autoAdvance = false` + `advanceTimeByFrame()`. Una animación infinita **cuelga** `assertIsDisplayed()` en vez de fallar |
| **El modelo tiene una caída de capacidad** | Ya pasó el 4 de septiembre de 2026. La escapatoria es una línea en `AiSummaryConstants.MODEL_ID`, compartida con el resumen a propósito (D-305) |
| **Preguntar y resumir se estorban** | Es correcto y esperado: `serialised { }` mantiene una petición a la vez en toda la aplicación. Se documenta para que no se diagnostique como cuelgue (D-317) |

---

## Complexity Tracking

> Solo se rellena si la puerta constitucional encuentra violaciones que justificar.

Ninguna. La feature no añade dependencias, no añade módulos, no toca la base de datos y no introduce
ningún patrón que el proyecto no use ya.
