# Implementation Plan: Resumen IA

**Branch**: `007-resumen-ia` | **Date**: 2 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-resumen-ia/spec.md`

## Summary

La pestaña **Resumen IA** del detalle deja de ser un marcador de posición. Se extrae el texto del
PDF oficial en el propio dispositivo, se envía a un servicio externo de inteligencia artificial y se
muestra una ficha estructurada y trazable: resumen en lenguaje llano, a quién afecta, fechas y
plazos, importes, actuaciones exigidas y recursos, con las páginas que respaldan cada dato.

**Tres cosas definen este plan, y las tres son elecciones, no inercia.**

La primera: **no entra ninguna biblioteca de PDF**. `androidx.pdf`, que la aplicación ya usa para
dibujar el documento, sabe extraer texto por páginas —`getPageContent`— y, lo que importa de verdad,
lo hace **en el proceso aislado**, que es la razón por la que se eligió ese visor en la feature 004.
La especificación técnica del propietario pedía PdfBox; se descarta con motivo escrito (D-001).

La segunda: **casi nada de la tubería es nuevo**. La descarga del documento, sus cinco validaciones,
la deduplicación de descargas concurrentes y el SHA-256 del PDF ya existen y se reutilizan tal cual:
`DocumentRepository.ensureLocalCopy` y `OfficialDocument.checksum`. Lo que la especificación técnica
llamaba `PdfDownloader` está construido desde hace dos features.

La tercera: **una sola consulta por publicación**. Un documento que no quepa entero se resume en
parte, avisando antes de gastar la cuota y declarando después qué páginas se analizaron. El resumen
por fragmentos reanudable queda fuera (D-006).

**Lo que este plan no hace**: no construye el chat «Preguntar», ni un almacén de texto por
adelantado para él, ni reconocimiento óptico, ni pantalla de Ajustes. Y no toca `DomainError`.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11 de origen y destino, AGP 9.3.2, Gradle 9.5.0, KSP
2.2.10-2.0.2.

**Primary Dependencies**: **una nueva y ninguna versión que subir**. Entra
`kotlinx-serialization-json` (versión gobernada por el mismo `kotlin` del catálogo); su plugin ya
estaba aplicado y la biblioteca ya llegaba por transitividad desde `navigation-compose`, pero el
código la importa directamente y la norma del proyecto es declararla (D-014). Todo lo demás se
reutiliza en las versiones que ya hay: `androidx.pdf` 1.0.0-beta01 —`pdf-compose` y
`pdf-document-service`—, OkHttp por BOM 5.5.0, Room 2.8.4, Koin por BOM 4.2.2, Compose por BOM
2026.02.01, corrutinas 1.11.0.

**Storage**: Room sube a la **versión 4** con `AutoMigration(3, 4)` y esquema exportado. Una tabla
nueva, `ai_summaries`, con clave primaria la clave externa de la publicación (D-020). **No** se
almacena el texto del documento, ni hay tabla de búsqueda de texto completo (D-021). Un booleano por
instalación —la aceptación del aviso de privacidad— va en `SharedPreferences` (D-023). Sin
sentencias de borrado en ningún DAO, como en las seis features anteriores.

**Network**: una petición `POST` a `https://api.groq.com/openai/v1/chat/completions` con el modelo
`qwen/qwen3.8-27b`, esquema JSON estricto y sin respuesta progresiva —Structured Outputs y streaming
no son compatibles (D-011)—. El cliente se deriva del `OkHttpClient` compartido con `newBuilder()`,
como ya hace el descargador de documentos. Sin Retrofit. La credencial se lee de `local.properties`
con API de proveedor de Gradle y se expone por `BuildConfig`; si falta, la compilación sigue en verde
y la función se anuncia como no configurada (D-017).

**Testing**: JUnit 4.13.2, MockK 1.14.11, Turbine 1.2.1, `kotlinx-coroutines-test` 1.11.0,
Robolectric 4.16.1 con `@Config(sdk = [36])`, `koin-test`, MockWebServer sobre TLS con `okhttp-tls`
—el proyecto ya lo tiene porque el catálogo de canales exige HTTPS—, Compose UI Test y Konsist
0.17.3.

**Target Platform**: Android, `minSdk 28`, `compileSdk` y `targetSdk` 37. Vertical fijo, tema claro
único.

**Performance Goals**: un resumen ya guardado se muestra en menos de un segundo y sin red (SC-002).
La generación completa de un documento corto no debería pasar de quince segundos en red normal. La
extracción de texto no bloquea el hilo principal en ningún momento.

**Constraints**: presupuesto de **7.200 tokens estimados por consulta**, con tope duro de **16.000
caracteres** de texto documental (D-007). Cuota del servicio de 8.000 tokens por minuto compartidos
por toda la organización, lo que da aproximadamente **un resumen por minuto sostenido**. Cero
consultas al servicio sin que alguien pulse el botón. Ni la credencial ni el contenido del documento
pueden aparecer en registros.

**Scale/Scope**: 22 ficheros de producción nuevos y 15 modificados; 20 ficheros de prueba nuevos y 4
modificados. Una pantalla existente ampliada, ninguna nueva.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluado contra `.specify/memory/constitution.md` **versión 1.1.0** (enmendada el 30 de agosto de
2026).

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | La feature recorre el ciclo completo. `spec.md` y su lista de calidad están escritos y validados; este `plan.md`, `research.md`, `data-model.md`, `contracts/` y `quickstart.md` son la fase de diseño; no se escribe una línea de producto hasta que `/speckit-tasks` produzca `tasks.md`. La rama `007-resumen-ia` la creó la extensión git de Spec Kit | ✅ |
| **II. Arquitectura limpia** | `domain` es Kotlin puro: los cuatro modelos nuevos, el contrato del repositorio y los cuatro casos de uso no importan `android.*`, `androidx.*`, Firebase, Koin, `data` ni `ui`. `androidx.pdf`, OkHttp, Room y `SharedPreferences` viven solo en `data`; el extractor de texto se coloca en `data/source/local` precisamente por eso (D-002). La pestaña habla solo con casos de uso | ✅ |
| **III. MVVM** | Se amplía `PublicationDetailViewModel` en vez de crear otro: una pantalla, un modelo de pantalla (D-024). `PublicationDetailUiState` sigue siendo inmutable y el `StateFlow` de solo lectura. Los componibles nuevos son tontos y sin estado; la orquestación entera —descarga, extracción, presupuesto, consulta, validación— vive en el repositorio, no en el modelo de pantalla | ✅ |
| **IV. Koin** | Las trece declaraciones nuevas van a `DataModule` y `DomainModule`; nada se instancia a mano. Las que nombran un SDK de terceros entran por función fábrica desde su propio paquete, como ya hacen `bocDatabase()` y `bocHttpClient()`. `KoinModulesTest` se amplía en los dos sentidos: `CROSS_MODULE_TYPES` y la resolución explícita | ✅ |
| **V. Testing exigente** | Ninguna tarea se cierra sin su prueba en verde. Unitarias para normalizador, presupuesto, fábrica de prompts, validador, coordinador de cuota, proveedor de credencial, fuente de datos con servidor de pruebas sobre TLS y repositorio; de integración para el flujo completo con dobles solo en la frontera; instrumentadas para el extractor con un PDF de prueba y para la pestaña en todos sus estados. Konsist obliga además a una prueba por cada clase de dominio y por el modelo de pantalla | ✅ |
| **VI. Observabilidad desacoplada** | La telemetría de la feature pasa por `AnalyticsTracker` y `CrashReporter`, inyectados. Ningún SDK de Firebase se nombra fuera de `data`. Se registran métricas sin contenido —fases, tokens devueltos, éxito o categoría de error— y **nunca** el texto del documento ni la credencial (FR-047) | ✅ |
| **Restricciones tecnológicas** | Compose y Material 3 sin XML ni Fragments; Koin, no Hilt; corrutinas y `Flow`, sin RxJava; la única dependencia nueva entra por `libs.versions.toml`, sin coordenadas literales en `build.gradle.kts`; código y comentarios en inglés, documentación en español | ✅ |
| **Flujo y puertas de calidad** | Trabajo en la rama `007-resumen-ia`, nunca en `main`. Commits en español con prefijo convencional. Las cuatro puertas en orden —`assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug`— antes de dar la feature por terminada, y así está escrito en `quickstart.md` | ✅ |

**Resultado de la puerta previa a la fase 0**: pasa sin violaciones.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño detallado no introdujo ninguna
desviación nueva. Los dos puntos que merecían una segunda mirada se resolvieron a favor de la norma:
el extractor de texto se coloca en `data` y **no** se mueve allí `PdfDocumentLoader`, que rompería la
regla «ui no depende de data» (D-002); y los fallos de la feature usan una jerarquía propia en vez de
ensanchar `DomainError`, que habría obligado a media aplicación a contemplar casos que no le dicen
nada (D-026).

## Project Structure

### Documentation (this feature)

```text
specs/007-resumen-ia/
├── spec.md                     # Qué y por qué. 48 requisitos, 12 criterios de éxito
├── plan.md                     # Este fichero
├── research.md                 # Fase 0. 29 decisiones, D-001 a D-029
├── data-model.md               # Fase 1. Modelos, tabla, migración, estado de pantalla
├── quickstart.md               # Fase 1. Cómo se valida de extremo a extremo
├── contracts/
│   └── internal-contracts.md   # Fase 1. Fronteras internas, etiquetas y contrato visual
├── checklists/
│   └── requirements.md         # Calidad de la especificación. 16 de 16
└── tasks.md                    # Fase 2. Lo crea /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── core/di/
│   ├── DataModule.kt                          MODIFICADO  9 declaraciones nuevas
│   └── DomainModule.kt                        MODIFICADO  4 casos de uso
├── domain/
│   ├── model/
│   │   ├── AiSummaryConstants.kt              NUEVO   modelo, versión de prompt y de esquema
│   │   ├── AiSummary.kt                       NUEVO   con sus 5 tipos referenciados anidados
│   │   ├── AiSummaryStatus.kt                 NUEVO   Idle, Preparing, Generating, WaitingForQuota,
│   │   │                                              Ready, Failed
│   │   ├── AiSummaryError.kt                  NUEVO   8 casos
│   │   ├── PdfCorpus.kt                       NUEVO   con PdfPageText anidado
│   │   └── DetailTab.kt                       MODIFICADO  se retira isComingSoon (D-029)
│   ├── repository/AiSummaryRepository.kt      NUEVO
│   └── usecase/
│       ├── ObserveAiSummaryUseCase.kt         NUEVO
│       ├── GenerateAiSummaryUseCase.kt        NUEVO
│       ├── ObserveAiNoticeAcceptedUseCase.kt  NUEVO
│       └── AcceptAiNoticeUseCase.kt           NUEVO
├── data/
│   ├── repository/AiSummaryRepositoryImpl.kt  NUEVO   el orquestador
│   └── source/
│       ├── local/
│       │   ├── PdfTextExtractor.kt            NUEVO   interfaz + resultado sellado
│       │   ├── AndroidxPdfTextExtractor.kt    NUEVO   segunda frontera con androidx.pdf
│       │   ├── PdfTextNormalizer.kt           NUEVO   Kotlin puro
│       │   ├── AiSummaryEntity.kt             NUEVO
│       │   ├── AiSummaryDao.kt                NUEVO   solo lectura y upsert
│       │   ├── AiPreferences.kt               NUEVO   SharedPreferences, un booleano
│       │   └── BocDatabase.kt                 MODIFICADO  versión 4, AutoMigration(3,4)
│       └── remote/
│           ├── GroqApiKeyProvider.kt          NUEVO
│           ├── GroqSummaryDataSource.kt       NUEVO   interfaz + resultado sellado
│           ├── OkHttpGroqSummaryDataSource.kt NUEVO
│           ├── GroqDtos.kt                    NUEVO
│           ├── GroqSummarySchema.kt            NUEVO   el esquema estricto
│           ├── SummaryPromptFactory.kt        NUEVO
│           ├── SummaryBudget.kt               NUEVO   estimación y selección de páginas
│           ├── SummaryValidator.kt            NUEVO
│           └── GroqRateLimitCoordinator.kt    NUEVO
└── ui/
    ├── detail/
    │   ├── PublicationDetailViewModel.kt      MODIFICADO
    │   ├── PublicationDetailUiState.kt        MODIFICADO
    │   ├── PublicationDetailRoute.kt          MODIFICADO
    │   ├── PublicationDetailScreen.kt         MODIFICADO  AI_SUMMARY deja de ser ComingSoonTab
    │   └── component/
    │       ├── AiSummaryTab.kt                NUEVO   el conmutador de estados
    │       ├── AiSummaryCard.kt               NUEVO   la tarjeta del mockup
    │       ├── AiSummarySections.kt           NUEVO   las siete secciones ocultables
    │       ├── PageChip.kt                    NUEVO
    │       ├── AiSummaryActions.kt            NUEVO   copiar, compartir, regenerar
    │       └── AiNoticeSheet.kt               NUEVO   el aviso de la primera vez
    ├── navigation/
    │   ├── Routes.kt                          MODIFICADO  Route.PdfViewer gana page
    │   └── BOCantabriaNavHost.kt              MODIFICADO
    └── pdf/
        ├── PdfViewerViewModel.kt              MODIFICADO  página inicial
        └── PdfViewerScreen.kt                 MODIFICADO

app/src/main/res/values/strings.xml            MODIFICADO  ~40 literales nuevos
app/schemas/…/4.json                           NUEVO   esquema exportado

app/src/test/java/com/jrblanco/boccantabria/
├── architecture/ArchitectureRulesTest.kt      SIN CAMBIOS  las 8 reglas siguen valiendo
├── di/KoinModulesTest.kt                      MODIFICADO
├── domain/model/                              NUEVO   AiSummaryTest, AiSummaryStatusTest,
│                                                      AiSummaryErrorTest, PdfCorpusTest
├── domain/model/DetailTabTest.kt              MODIFICADO
├── domain/usecase/                            NUEVO   4 pruebas, una por caso de uso
├── data/repository/AiSummaryRepositoryImplTest.kt      NUEVO
├── data/source/local/
│   ├── PdfTextNormalizerTest.kt               NUEVO
│   ├── AiSummaryDaoTest.kt                    NUEVO
│   ├── AiPreferencesTest.kt                   NUEVO
│   └── BocDatabaseMigrationTest.kt            MODIFICADO  1→4 y 3→4
├── data/source/remote/
│   ├── SummaryBudgetTest.kt                   NUEVO
│   ├── SummaryPromptFactoryTest.kt            NUEVO
│   ├── SummaryValidatorTest.kt                NUEVO
│   ├── GroqRateLimitCoordinatorTest.kt        NUEVO
│   ├── GroqApiKeyProviderTest.kt              NUEVO
│   └── OkHttpGroqSummaryDataSourceTest.kt     NUEVO   MockWebServer sobre TLS
├── integration/AiSummaryFlowIntegrationTest.kt         NUEVO
├── fake/                                      NUEVO   FakeAiSummaryRepository, FakePdfTextExtractor,
│                                                      FakeGroqSummaryDataSource, AiSummaries.kt
└── ui/detail/PublicationDetailViewModelTest.kt         MODIFICADO

app/src/androidTest/java/com/jrblanco/boccantabria/
├── data/source/local/AndroidxPdfTextExtractorTest.kt   NUEVO
├── ui/detail/AiSummaryTabTest.kt                       NUEVO
└── ui/AiSummaryPageHandoffTest.kt                      NUEVO   el chip que abre la página
app/src/androidTest/assets/                             NUEVO   dos PDF mínimos de prueba

gradle/libs.versions.toml                      MODIFICADO  kotlinx-serialization-json
app/build.gradle.kts                           MODIFICADO  dependencia y buildConfigField
CLAUDE.md                                      MODIFICADO  frontera de androidx.pdf, base v4, credencial
docs/diseno/especificaciones-diseno.md         MODIFICADO  §20 enmendado in situ, con fecha
```

**Structure Decision**: se mantiene el módulo único `:app` con separación por paquetes, como en las
seis features anteriores; no hay ninguna razón para partir en módulos Gradle una funcionalidad que
cabe en una pestaña. Cada pieza va a la capa que le corresponde por lo que **es**, no por para qué se
usa: la extracción de texto es una fuente de datos y va a `data/source/local` aunque el resultado se
pinte en `ui`; el orquestador es un repositorio y va a `data/repository`; los tipos que cruzan capas
son de `domain` y son Kotlin puro. El único componente nuevo que se coloca en `ui/detail/component/`
y no en `core/ui/component/` es la ficha del resumen, porque hoy solo la dibuja una pantalla; si
«Preguntar» acaba reutilizándola, subirá entonces, que es lo que se hizo con `PublicationCard` en la
feature 005.

## Complexity Tracking

> La puerta constitucional pasa sin violaciones. Se registran aquí, por transparencia, las cuatro
> decisiones que añaden algo al proyecto y podrían discutirse en una revisión. Cada una remite a su
> decisión de `research.md`.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| **Segunda frontera con `androidx.pdf`**, ahora también en `data/source/local` (D-002) | Extraer texto es una fuente de datos. La única forma de mantener una sola frontera sería orquestar la tubería desde el modelo de pantalla, que incumple el principio III | Mover `PdfDocumentLoader` a `data` y ampliarlo: rompe la regla Konsist «ui no depende de data», porque el visor y la vista previa lo importan |
| **Una dependencia nueva**, `kotlinx-serialization-json` (D-014) | El cuerpo de la petición y la respuesta del servicio son JSON, y el proyecto no tiene ningún serializador declarado | Analizar el JSON a mano con `org.json`: reimplementar el mapeo de un esquema de doce campos anidados, sin tipos y sin errores de compilación cuando cambie |
| **`SharedPreferences` para un booleano** (D-023) | La aceptación del aviso es un dato por instalación que debe sobrevivir a la muerte del proceso y no pertenece al modelo de datos del boletín | Una tabla de clave-valor en Room: tabla, DAO y migración para guardar un bit, y la puerta de entrada a un cajón de sastre |
| **Credencial en `BuildConfig`** (D-017) | Es la opción C de la especificación técnica, elegida por el propietario con su límite escrito en los supuestos de `spec.md` | Un servicio intermedio propio: es lo correcto para publicar, pero exige desplegar y mantener algo que hoy no existe. La abstracción `GroqApiKeyProvider` deja ese cambio en una implementación y una URL |
