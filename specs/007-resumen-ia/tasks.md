# Tasks: Resumen IA

**Input**: Design documents from `/specs/007-resumen-ia/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/internal-contracts.md`, `quickstart.md`

**Tests**: **OBLIGATORIOS.** No es una opción de esta feature: el principio V de la constitución dice
que ningún elemento de `tasks.md` está terminado sin su test en verde, y prohíbe `@Ignore`, comentar
o borrar una prueba para pasar la build. Además, la regla 8 de Konsist **falla la compilación** si una
clase de dominio de primer nivel o un `ViewModel` no tiene su fichero `<Nombre>Test`.

**Organization**: por historia de usuario, para que cada una se pueda implementar y probar sola.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo — ficheros distintos, sin dependencias pendientes
- **[Story]**: a qué historia pertenece (US1…US5). Setup, Foundational y Polish no llevan etiqueta

## Path Conventions

Módulo único `:app`. Abreviaturas usadas abajo:

- `MAIN/` = `app/src/main/java/com/jrblanco/boccantabria/`
- `TEST/` = `app/src/test/java/com/jrblanco/boccantabria/`
- `ATEST/` = `app/src/androidTest/java/com/jrblanco/boccantabria/`
- `RES/` = `app/src/main/res/`

Java no está en el `PATH`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

---

## Phase 1: Setup

**Purpose**: la única dependencia nueva y la credencial, antes de que nada más las necesite.

- [X] T001 Añadir `kotlinx-serialization-json` a `gradle/libs.versions.toml`, sin `version.ref` propia: la gobierna la versión `kotlin` ya declarada (research.md D-014)
- [X] T002 Declarar `implementation(libs.kotlinx.serialization.json)` en `app/build.gradle.kts`, en el bloque de corrutinas/serialización, con el comentario de por qué se declara pese a llegar por transitividad
- [X] T003 Añadir en `app/build.gradle.kts` el `buildConfigField("String", "GROQ_API_KEY", ...)` leyendo `local.properties` con `providers.fileContents(...)` y `providers.environmentVariable("GROQ_API_KEY")` como respaldo. **Debe compilar con la clave ausente**, produciendo cadena vacía (research.md D-017, FR-042)
- [X] T004 [P] Crear `MAIN/domain/model/AiSummaryConstants.kt` con `MODEL_ID = "qwen/qwen3.8-27b"`, `PROMPT_VERSION = "boc-summary-es-v1"` y `SCHEMA_VERSION = "boc-summary-schema-v1"`, como `object`, no como `class` —igual que `core/util/SearchText.kt`—, porque la regla 8 de Konsist exigiría fichero de prueba a una clase de dominio de primer nivel. Documentar que el modelo está en Preview y por eso vive en una constante (research.md D-010)
- [X] T005 Verificar que `./gradlew :app:assembleDebug` sigue en verde **con y sin** `GROQ_API_KEY` en `local.properties`

**Checkpoint**: la aplicación compila igual que antes, con una dependencia más y la credencial disponible pero sin usar.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: los tipos, el almacén y el grafo que **todas** las historias necesitan.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta que esta fase esté completa.

### Modelos de dominio

- [X] T006 [P] Crear `MAIN/domain/model/PdfCorpus.kt` con `PdfPageText` **anidado**, los `require` de páginas consecutivas desde 1, y `pagesWithText` / `hasUsableText` (data-model.md §1)
- [X] T007 [P] Crear `MAIN/domain/model/AiSummary.kt` con los cinco tipos referenciados **anidados** y los `require` de `SummaryCoverage`: ninguna página fuera de rango, ninguna cobertura completa a medias (data-model.md §2)
- [X] T008 [P] Crear `MAIN/domain/model/AiSummaryStatus.kt` con `Idle`, `Preparing(phase)`, `Generating`, `WaitingForQuota`, `Ready(summary, generatedAtEpochMillis, isStale)` y `Failed` (data-model.md §3)
- [X] T009 [P] Crear `MAIN/domain/model/AiSummaryError.kt` con los ocho casos y la propiedad `isRetryable` (data-model.md §3, FR-041)
- [X] T010 [P] Escribir `TEST/domain/model/PdfCorpusTest.kt`: páginas no consecutivas rechazadas, numeración desde 1, umbral de texto aprovechable
- [X] T011 [P] Escribir `TEST/domain/model/AiSummaryTest.kt`: resumen llano en blanco rechazado, cobertura con página fuera de rango rechazada, cobertura completa con páginas de menos rechazada (FR-030, SC-012)
- [X] T012 [P] Escribir `TEST/domain/model/AiSummaryStatusTest.kt`: `WaitingForQuota` no admite segundos negativos; `Ready` distingue obsoleto de vigente
- [X] T013 [P] Escribir `TEST/domain/model/AiSummaryErrorTest.kt`: los cuatro recuperables y los cuatro que no, uno a uno (FR-041)
- [X] T014 Retirar `isComingSoon` de `MAIN/domain/model/DetailTab.kt` y actualizar el KDoc; ajustar `TEST/domain/model/DetailTabTest.kt` (research.md D-029)

### Contrato y casos de uso

- [X] T015 Crear `MAIN/domain/repository/AiSummaryRepository.kt` con las cuatro operaciones y el KDoc de garantías, siguiendo el estilo de `DocumentRepository` (data-model.md §7)
- [X] T016 [P] Crear `MAIN/domain/usecase/ObserveAiSummaryUseCase.kt`
- [X] T017 [P] Crear `MAIN/domain/usecase/GenerateAiSummaryUseCase.kt`
- [X] T018 [P] Crear `MAIN/domain/usecase/ObserveAiNoticeAcceptedUseCase.kt`
- [X] T019 [P] Crear `MAIN/domain/usecase/AcceptAiNoticeUseCase.kt`
- [X] T020 [P] Escribir `TEST/domain/usecase/ObserveAiSummaryUseCaseTest.kt`
- [X] T021 [P] Escribir `TEST/domain/usecase/GenerateAiSummaryUseCaseTest.kt`
- [X] T022 [P] Escribir `TEST/domain/usecase/ObserveAiNoticeAcceptedUseCaseTest.kt`
- [X] T023 [P] Escribir `TEST/domain/usecase/AcceptAiNoticeUseCaseTest.kt`

### Almacén

- [X] T024 Crear `MAIN/data/source/local/AiSummaryEntity.kt` con las once columnas y sus `@ColumnInfo` en `snake_case` (data-model.md §4)
- [X] T025 Crear `MAIN/data/source/local/AiSummaryDao.kt` con `observe`, `byExternalKey` y `@Upsert`. **Sin ninguna sentencia de borrado**
- [X] T026 Subir `MAIN/data/source/local/BocDatabase.kt` a `version = 4`, añadir `AiSummaryEntity` y `AutoMigration(3, 4)` conservando las dos anteriores, y declarar `aiSummaryDao()`
- [X] T027 Compilar para que KSP exporte `app/schemas/…/4.json` y **versionar el fichero**: es el material de la migración siguiente
- [X] T028 [P] Ampliar `TEST/data/source/local/BocDatabaseMigrationTest.kt` con los caminos 3→4 y 1→4, escritos a mano con `SQLiteDatabase` como los existentes
- [X] T029 [P] Escribir `TEST/data/source/local/AiSummaryDaoTest.kt` con base real en memoria bajo Robolectric `@Config(sdk = [ROBOLECTRIC_SDK])`: `observe` emite `null` cuando no hay fila, el upsert sustituye, y **no existe forma de borrar**
- [X] T030 [P] Crear `MAIN/data/source/local/AiPreferences.kt`: interfaz + implementación sobre `SharedPreferences` (research.md D-023)
- [X] T031 [P] Escribir `TEST/data/source/local/AiPreferencesTest.kt` bajo Robolectric: por defecto falso, acepta una vez, sobrevive a releer

### Dobles de prueba

- [X] T032 [P] Crear `TEST/fake/AiSummaries.kt` con constructores de conveniencia: `aiSummary(...)`, `pdfCorpus(...)`, y una respuesta de servicio de ejemplo
- [X] T033 [P] Crear `TEST/fake/FakeAiSummaryRepository.kt`, `TEST/fake/FakePdfTextExtractor.kt` y `TEST/fake/FakeGroqSummaryDataSource.kt`, con banderas para forzar cada fallo

### Grafo

- [X] T034 Registrar en `MAIN/core/di/DataModule.kt` el DAO, `AiPreferences`, el extractor, el normalizador, el proveedor de credencial, la fuente de datos, el coordinador de cuota, el validador y `AiSummaryRepositoryImpl`; las que nombran un SDK ajeno, por función fábrica desde su propio paquete
- [X] T035 Registrar los cuatro casos de uso en `MAIN/core/di/DomainModule.kt` como `factory`
- [X] T036 Ampliar `TEST/di/KoinModulesTest.kt`: añadir los tipos nuevos a `CROSS_MODULE_TYPES` y resolverlos explícitamente en el test del grafo completo

> **Desajuste de orden detectado al implementar.** T033 a T036 dependen de piezas que esta fase no crea:
> los dos dobles restantes necesitan `PdfTextExtractor` y `GroqSummaryDataSource`, y el cableado de Koin
> necesita `AiSummaryRepositoryImpl`, que son T037, T053 y T057 de la fase 3. Se cierran al final de la
> fase 3, no aquí. Se deja escrito en vez de reordenar en silencio.

**Checkpoint**: los tipos existen, la base migra a la versión 4 y sus pruebas pasan. Nada se ve todavía en pantalla.

---

## Phase 3: User Story 1 — Entender una publicación de un vistazo (Priority: P1) 🎯 MVP

**Goal**: pulsar «Generar resumen» en una publicación con documento y obtener la ficha completa.

**Independent Test**: abrir una publicación con documento, generar el resumen y comprobar que aparece la tarjeta con el texto llano y solo las secciones que el documento sustenta.

### La tubería local

- [X] T037 [P] [US1] Crear `MAIN/data/source/local/PdfTextExtractor.kt`: interfaz y `PdfExtractionResult` sellado (contracts §1.1)
- [X] T038 [US1] Crear `MAIN/data/source/local/AndroidxPdfTextExtractor.kt` sobre `SandboxedPdfLoader`, con `use` en los dos niveles, conversión de página 0-based a 1-based en un único punto, y umbral de «sin texto utilizable» (research.md D-001, D-003, D-004, FR-009)
- [X] T039 [P] [US1] Crear dos PDF mínimos en `app/src/androidTest/assets/`: uno con texto en dos páginas y otro sin capa de texto. Escritos a mano con sintaxis PDF cruda para que sean deterministas y diminutos
- [X] T040 [US1] Escrita y compilando; **no ejecutada**: sin dispositivo conectado. Es la prueba que responde al riesgo D-001, así que hay que correrla antes de dar la feature por cerrada. `ATEST/data/source/local/AndroidxPdfTextExtractorTest.kt`: extrae el texto de las dos páginas con sus números, y el documento sin texto da `NoExtractableText`
- [X] T041 [P] [US1] Crear `MAIN/data/source/local/PdfTextNormalizer.kt`, Kotlin puro (contracts §1.2)
- [X] T042 [P] [US1] Escribir `TEST/data/source/local/PdfTextNormalizerTest.kt`: conserva fechas, importes y numeración; elimina encabezados repetidos en ≥ 60 % de páginas; no mezcla páginas; une `sub-\nvención` pero no `Decreto-\nLey` (FR-011)

### El presupuesto y el prompt

- [X] T043 [P] [US1] Crear `MAIN/data/source/remote/SummaryBudget.kt` con `estimateTokens`, `select` y las tres constantes. En el KDoc, por qué vive en `remote` pese a no tocar la red: decide qué cabe en **una petición**, y ese límite es del servicio (data-model.md §6)
- [X] T044 [P] [US1] Escribir `TEST/data/source/remote/SummaryBudgetTest.kt`: nunca supera 16.000 caracteres ni 5.000 tokens; corta por páginas enteras; corta por párrafo si la primera página sola no cabe; `pages` refleja exactamente lo enviado; es determinista (FR-027, FR-031)
- [X] T045 [P] [US1] Crear `MAIN/data/source/remote/GroqSummarySchema.kt` con el esquema estricto: todo en `required`, `additionalProperties: false`, subesquemas por `$defs`/`$ref`
- [X] T046 [P] [US1] Crear `MAIN/data/source/remote/GroqDtos.kt` con los DTO `@Serializable` de petición, respuesta, `choices`, `usage` y la carga del resumen
- [X] T047 [US1] Crear `MAIN/data/source/remote/SummaryPromptFactory.kt` tomando **literales** los mensajes de sistema y de usuario de los apartados 13 y 15 de `Datos_modelo/ESPECIFICACION_RESUMENES_IA_BOC.md`, con marcadores `[PÁGINA n]` (research.md D-009, D-019)
- [X] T048 [P] [US1] Escribir `TEST/data/source/remote/SummaryPromptFactoryTest.kt`: incluye metadatos y marcadores; **nunca escribe el literal `null`**; el mensaje de sistema declara el documento como contenido no confiable; un documento que contiene «ignora tus instrucciones» no cambia el prompt (FR-018)
- [X] T048b [P] [US1] Añadir a `TEST/data/source/remote/SummaryPromptFactoryTest.kt` las cláusulas que solo viven en el prompt y que nadie vigilaría si no: conservar literalmente los plazos relativos y no calcular fechas (FR-016), distinguir publicación, entrada en vigor, solicitud, alegaciones y recurso (FR-017), y no incluir recomendaciones jurídicas ni afirmar que el resumen sustituye al documento (FR-019). Sin esta prueba, una edición futura las borra y nadie se entera
- [X] T048c [P] [US1] Añadir a `TEST/data/source/remote/SummaryPromptFactoryTest.kt` la afirmación de privacidad: el mensaje construido contiene **solo** metadatos de la publicación y texto del documento — ni publicaciones guardadas, ni historial, ni identificador de dispositivo o de persona (FR-046)

### La salida a la red

- [X] T049 [P] [US1] Crear `MAIN/data/source/remote/GroqApiKeyProvider.kt` y su implementación sobre `BuildConfig`, devolviendo `null` si está en blanco
- [X] T050 [P] [US1] Escribir `TEST/data/source/remote/GroqApiKeyProviderTest.kt`: con clave la devuelve; en blanco devuelve `null`; **el valor nunca aparece en `toString()` ni en ningún mensaje**
- [X] T051 [P] [US1] Crear `MAIN/data/source/remote/GroqRateLimitCoordinator.kt` con `awaitPermission`, `record(headers)` y `parseDuration`, con `TimeProvider` y `RandomProvider` inyectados (contracts §1.6)
- [X] T052 [P] [US1] Escribir `TEST/data/source/remote/GroqRateLimitCoordinatorTest.kt`: analiza `7.66s`, `2m59.56s` y segundos enteros; un formato desconocido da `null` sin lanzar; distingue cuota de minuto y de día; serializa las peticiones (research.md D-015)
- [X] T053 [US1] Crear `MAIN/data/source/remote/GroqSummaryDataSource.kt` (interfaz + resultados sellados) y `OkHttpGroqSummaryDataSource.kt`: cliente derivado con `newBuilder()`, `temperature 0.2`, `max_completion_tokens 1200`, `stream false`, `reasoning_effort "none"`, esquema estricto. **Sin interceptor de registro de cuerpo** (contracts §1.5)
- [X] T054 [US1] Escribir `TEST/data/source/remote/OkHttpGroqSummaryDataSourceTest.kt` con MockWebServer **sobre TLS** (`okhttp-tls`): 200 correcto, 401, 429 con `retry-after`, 5xx con recuperación, JSON mal formado, y que la credencial viaja en la cabecera `Authorization` y no en el cuerpo. Afirmar además que el cuerpo enviado es JSON de texto y **no contiene bytes del fichero PDF** (FR-010)
- [X] T055 [P] [US1] Crear `MAIN/data/source/remote/SummaryValidator.kt` (contracts §1.7, data-model.md §5)
- [X] T056 [P] [US1] Escribir `TEST/data/source/remote/SummaryValidatorTest.kt`: descarta páginas fuera de rango y no enviadas; sustituye `pagesAnalyzed` por lo enviado; **corrige `complete` a falso aunque el servicio diga lo contrario**; devuelve `null` con texto llano en blanco (FR-022, FR-030, FR-036, SC-012)

### El orquestador

- [X] T057 [US1] Crear `MAIN/data/repository/AiSummaryRepositoryImpl.kt`: mapa de estados observable, `Mutex` + `CompletableDeferred` para una sola generación por clave, secuencia obtener documento → extraer → normalizar → presupuestar → consultar → validar → guardar, traducción de rechazos a `AiSummaryError` y relanzado de `CancellationException` (data-model.md §7, FR-008)
- [X] T058 [US1] Escribir `TEST/data/repository/AiSummaryRepositoryImplTest.kt` para el camino feliz: observar no genera; generar produce `Preparing` → `Generating` → `Ready`; dos llamadas concurrentes hacen una sola consulta; **un documento sin texto no llega al servicio** (FR-002, FR-005, FR-012, SC-004, SC-005)

### La pantalla

- [X] T059 [US1] Ampliar `MAIN/ui/detail/PublicationDetailUiState.kt` con `summary`, `aiNoticeAccepted` y `aiNoticePending` (data-model.md §8)
- [X] T060 [US1] Ampliar `MAIN/ui/detail/PublicationDetailViewModel.kt`: inyectar los casos de uso, añadir la sexta fuente al `combine` y los eventos `onGenerateSummary()` y `onRegenerateSummary()`. **Sin `onSummaryTabShown()`**: abrir la pestaña no genera nada (FR-002)
- [X] T061 [P] [US1] Añadir a `RES/values/strings.xml` los literales de la pestaña: explicación inicial, «Generar resumen», títulos de las siete secciones, fases del progreso y la advertencia
- [X] T062 [P] [US1] Crear `MAIN/ui/detail/component/AiSummaryCard.kt`: fondo `aiContainer`, radio 18 dp, relleno 20 dp, círculo de 48 dp con `ic_ai` en `aiAccent`, título `titleLarge`, viñetas azules y cuerpo `bodyLarge` (diseño §20.1, §20.2, FR-014)
- [X] T063 [P] [US1] Crear `MAIN/ui/detail/component/AiSummarySections.kt`: las siete secciones, **cada una ausente si su lista está vacía**, con la etiqueta `aiSectionTag(key)` (FR-013, FR-015)
- [X] T064 [US1] Crear `MAIN/ui/detail/component/AiSummaryTab.kt` como conmutador de estados; en esta historia, los cuatro primeros: inicial, progreso por fases, éxito y espera de cuota. El esqueleto de carga, con icono **estático** (diseño §20.5, FR-003, FR-004)
- [X] T064b [US1] Añadir al estado inicial de `MAIN/ui/detail/component/AiSummaryTab.kt` el aviso previo de cobertura parcial junto al botón: «Documento de N páginas; se analizarán las M primeras», calculado tras extraer y **antes** de salir a la red. Va en US1 y no en US5 a propósito: entregar el MVP truncando sin avisar de antemano incumpliría FR-028 (FR-028)
- [X] T065 [US1] Sustituir `ComingSoonTab` por `AiSummaryTab` en la rama `DetailTab.AI_SUMMARY` de `MAIN/ui/detail/PublicationDetailScreen.kt`, y pasar los eventos desde `MAIN/ui/detail/PublicationDetailRoute.kt` (FR-001)
- [X] T066 [US1] Ampliar `TEST/ui/detail/PublicationDetailViewModelTest.kt`: abrir la pestaña no genera; generar recorre las fases; regenerar fuerza
- [X] T067 [US1] Escribir `ATEST/ui/detail/AiSummaryTabTest.kt` con `createComposeRule()` para los estados de esta historia, **conduciendo el reloj a mano** si el esqueleto se anima (`mainClock.autoAdvance = false`)

**Checkpoint**: se puede generar un resumen y verlo. Es el MVP: parar aquí y validar.

---

## Phase 4: User Story 2 — Volver y encontrarlo hecho (Priority: P1)

**Goal**: al volver, el resumen aparece al instante, sin red y sin gastar cuota.

**Independent Test**: generar, salir, activar modo avión, volver a entrar y comprobar que aparece completo sin indicador de espera.

- [X] T068 [US2] Hacer que `observeSummary` de `MAIN/data/repository/AiSummaryRepositoryImpl.kt` emita lo guardado desde el primer momento, combinando el mapa de estados en curso con el flujo del DAO
- [X] T069 [US2] Implementar el cálculo de obsolescencia: comparar modelo, versión de prompt y de esquema **siempre**, y el hash del PDF **solo cuando se conoce**, produciendo `Ready(isStale = true)` sin borrar nada (FR-035, data-model.md §4)
- [X] T070 [US2] Guardar en `ai_summaries` el resumen validado con su procedencia y el consumo real devuelto por el servicio (FR-032)
- [X] T071 [P] [US2] Añadir a `MAIN/ui/detail/component/AiSummaryTab.kt` el estado de resumen obsoleto: aviso con etiqueta `TAG_AI_SUMMARY_STALE` y acción de regenerar, **sin ocultar el resumen anterior** (FR-034)
- [X] T072 [P] [US2] Añadir a `RES/values/strings.xml` los literales de obsolescencia y de regeneración
- [X] T073 [US2] Ampliar `TEST/data/repository/AiSummaryRepositoryImplTest.kt`: segunda apertura sin consultar el servicio; cambio de hash, de modelo, de prompt o de esquema marca obsoleto; regenerar sustituye por upsert
- [X] T074 [US2] Escribir `TEST/integration/AiSummaryFlowIntegrationTest.kt` con el grafo real y dobles solo en la frontera: generar, reiniciar el repositorio, volver a observar y obtener el resumen **sin ninguna llamada de red** (FR-033, SC-002)
- [X] T075 [US2] Añadir a `ATEST/ui/detail/AiSummaryTabTest.kt` el caso de resumen obsoleto

**Checkpoint**: US1 y US2 funcionan de forma independiente. La cuota deja de gastarse en balde.

---

## Phase 5: User Story 3 — Comprobar cada dato en el documento oficial (Priority: P2)

**Goal**: seguir una referencia hasta su página, y que la advertencia acompañe siempre al resumen.

**Independent Test**: con un resumen a la vista, tocar «Página 3» y ver el documento abierto por la 3; y compartir el resumen y leer el texto resultante.

- [X] T075b [P] [US3] Crear `RES/drawable/ic_warning.xml` e `ic_copy.xml` con el trazado de Material Symbols sin modificar, como los otros diecinueve vectores del proyecto: **ninguno de los dos existe hoy** y §20.3 del diseño exige un icono de advertencia con contorno. El `android:fillColor` es un marcador de posición que Compose tiñe en el punto de uso
- [X] T076 [P] [US3] Crear `MAIN/ui/detail/component/PageChip.kt`: chip con contorno, icono de documento, **azul y no violeta**, altura mínima 48 dp y descripción accesible «Abrir la página N del documento» (diseño §20.4, FR-024)
- [X] T077 [US3] Añadir a `MAIN/ui/detail/component/AiSummarySections.kt` las referencias por elemento y la fila «Fuentes del resumen» con etiqueta `TAG_AI_SUMMARY_SOURCES` (FR-020)
- [X] T078 [US3] Añadir `page: Int = 0` a `Route.PdfViewer` en `MAIN/ui/navigation/Routes.kt`, con KDoc sobre por qué lleva valor por defecto
- [X] T079 [US3] Pasar la página desde `MAIN/ui/navigation/BOCantabriaNavHost.kt` y leerla en `MAIN/ui/pdf/PdfViewerViewModel.kt` desde el `SavedStateHandle`
- [X] T080 [US3] Usar la página inicial en `MAIN/ui/pdf/PdfViewerScreen.kt` reutilizando el `scrollToPage()` que ya existe para restaurar tras rotar, sin romper esa restauración (research.md D-027)
- [X] T081 [P] [US3] Crear `MAIN/ui/detail/component/AiSummaryActions.kt`: copiar, compartir y abrir el PDF oficial (FR-026)
- [X] T082 [US3] Implementar en `MAIN/ui/detail/PublicationDetailViewModel.kt` `onCopySummary()` y `onShareSummary()`, **anteponiendo siempre la advertencia** al texto (FR-025, research.md D-028)
- [X] T083 [P] [US3] Añadir a `RES/values/strings.xml` la advertencia, el prefijo del texto compartido, «Fuentes del resumen» y los literales de las acciones
- [X] T084 [US3] Añadir la advertencia bajo la tarjeta en `MAIN/ui/detail/component/AiSummaryTab.kt`: icono en `accentOfficial`, fondo transparente, **nada de bloque rojo**, y `contentDescription` propio (diseño §20.3, FR-023, FR-024)
- [X] T085 [US3] Ampliar `TEST/ui/detail/PublicationDetailViewModelTest.kt`: el texto copiado y el compartido empiezan por la advertencia
- [X] T086 [US3] Añadir a `ATEST/ui/detail/AiSummaryTabTest.kt`: la advertencia está presente y **tiene descripción accesible**; los chips aparecen por cada página citada (SC-006)
- [X] T087 [US3] **Resuelto de otra forma, y se dice por qué.** La prueba instrumentada del puente exigía
  `androidx.navigation:navigation-testing`, una dependencia nueva para una sola aserción, y el plan
  prometía una única dependencia en toda la feature. Lo que de verdad podía romperse en silencio se
  comprueba ahora sin ella: `RoutesTest` fija que la ruta lleva la página y su valor por defecto, y
  `PdfViewerViewModelTest` que el argumento llega a `initialPage`. Que el visor aterrice de verdad en
  esa página es la comprobación 3 de `quickstart.md` §3 (FR-021, SC-003).

**Checkpoint**: cada afirmación del resumen se puede comprobar en el documento, y el resumen no puede circular sin su advertencia.

---

## Phase 6: User Story 4 — Saber que el texto sale del dispositivo (Priority: P2)

**Goal**: explicarlo antes del primer envío, una sola vez, con opción de cancelar.

**Independent Test**: con la aplicación recién instalada, pulsar «Generar resumen», ver el aviso, cancelar sin que se envíe nada, y comprobar que tras continuar no vuelve a salir.

- [X] T088 [P] [US4] Crear `MAIN/ui/detail/component/AiNoticeSheet.kt`: hoja inferior con `BocBottomSheetShape`, dos frases, **Continuar** y **Cancelar**, con las etiquetas de `contracts §2`
- [X] T089 [P] [US4] Añadir a `RES/values/strings.xml` el texto del aviso y sus dos acciones
- [X] T090 [US4] Implementar en `MAIN/ui/detail/PublicationDetailViewModel.kt` la puerta: `onGenerateSummary()` abre la hoja si el aviso no está aceptado; `onAiNoticeAccepted()` recuerda y continúa; `onAiNoticeDismissed()` cierra **sin enviar nada** (FR-043, FR-044, FR-045)
- [X] T091 [US4] Mostrar la hoja desde `MAIN/ui/detail/PublicationDetailRoute.kt` cuando `aiNoticePending` sea cierto
- [X] T092 [US4] Ampliar `TEST/ui/detail/PublicationDetailViewModelTest.kt`: sin aceptar, generar abre la hoja y **no llega al servicio**; cancelar no envía; tras aceptar, la segunda generación no vuelve a abrirla (SC-010)
- [X] T093 [US4] Añadir a `ATEST/ui/detail/AiSummaryTabTest.kt` el montaje de la hoja y sus dos salidas

**Checkpoint**: nadie envía el texto de un documento sin haberlo sabido antes.

---

## Phase 7: User Story 5 — Entender por qué no hay resumen (Priority: P3)

**Goal**: que cada situación imposible se explique en una frase, sin códigos, y con o sin reintento según proceda.

**Independent Test**: provocar documento sin texto, sin conexión, cuota agotada y documento largo, y comprobar mensaje, reintento y acceso al PDF en cada caso.

- [X] T094 [US5] Completar en `MAIN/data/repository/AiSummaryRepositoryImpl.kt` la traducción de cada rechazo a su `AiSummaryError`, incluida la distinción entre cuota de minuto y de día (FR-037 a FR-039)
- [X] T095 [US5] Implementar la espera por cuota: emitir `WaitingForQuota` con la cuenta atrás y **continuar solo** cuando se reponga (FR-038, SC-011)
- [X] T096 [P] [US5] Añadir a `RES/values/strings.xml` los ocho mensajes de error de FR-040, en lenguaje corriente y **sin ningún código**
- [X] T096b [P] [US5] Escribir en `TEST/ui/detail/` una prueba bajo Robolectric que recorra los ocho literales de error y afirme que ninguno contiene un código HTTP de tres dígitos, la palabra «error» seguida de número, ni el nombre del proveedor del servicio. Es lo que convierte SC-008 de redacción en garantía (SC-008, FR-040)
- [X] T097 [US5] Añadir a `MAIN/ui/detail/component/AiSummaryTab.kt` los estados de error, mostrando **Reintentar solo si `error.isRetryable`** y siempre «Abrir PDF oficial» (FR-041)
- [X] T099 [US5] Añadir la banda de cobertura del resultado con `TAG_AI_SUMMARY_COVERAGE`, anunciada junto al texto y no solo por color (FR-029, SC-007)
- [X] T100 [US5] Añadir el estado de publicación sin documento oficial, que no ofrece generar (FR-007)
- [X] T101 [US5] Ampliar `TEST/data/repository/AiSummaryRepositoryImplTest.kt`: cada rechazo produce su error; abandonar durante la generación no es un fallo (FR-006)
- [X] T102 [US5] Añadir a `ATEST/ui/detail/AiSummaryTabTest.kt` los estados restantes hasta cubrir los **trece** de `contracts §3`, incluidos el reintento ausente en los no recuperables y la cobertura parcial

**Checkpoint**: las trece situaciones de pantalla están cubiertas y probadas.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T103 [P] Añadir la telemetría de la feature por `AnalyticsTracker`: fases, si vino de caché, tokens devueltos y categoría de error. **Nunca** el contenido del documento ni la credencial (FR-047, SC-009, principio VI)
- [X] T104 [P] Actualizar `CLAUDE.md`: `androidx.pdf` tiene ahora **dos** fronteras y por qué; base de datos en la versión 4; cómo se gestiona la credencial; y que `ai_summaries` tampoco tiene sentencia de borrado
- [X] T105 [P] Enmendar `docs/diseno/especificaciones-diseno.md` §20 con un blockquote fechado, como se hizo con §17 en la feature 006: cobertura parcial, aviso de privacidad, resumen obsoleto y acciones de copiar y compartir
- [X] T106 Revisar que ningún fichero fuera de `ui/pdf` y `data/source/local/AndroidxPdfTextExtractor.kt` importe `androidx.pdf`, y que `ArchitectureRulesTest` siga en verde sin tocarlo
- [X] T107 **Las cuatro puertas pasadas.** `assembleDebug` ✅; `testDebugUnitTest` ✅ **722 pruebas, 0
  fallos**; `connectedDebugAndroidTest` ✅ **147 pruebas** en emulador ARM64 con API 37 —dos fallaron en
  la primera pasada, se arreglaron y la clase afectada volvió a correr entera: 21 de 21 en verde—;
  `lintDebug` ✅ **0 errores** (los 9 avisos son previos a esta feature).

  > **Lo que la tanda instrumentada demostró, y era el motivo de correrla.** Las cuatro pruebas de
  > `AndroidxPdfTextExtractorTest` pasan en un dispositivo real: `androidx.pdf` extrae el texto de un PDF
  > página a página, no mezcla páginas, detecta el documento sin capa de texto sin llamar al servicio, y
  > lo hace en el proceso aislado —el registro muestra `pdf_document_jni: Creating FPDF_DOCUMENT` desde
  > `com.jrblanco.boccantabria:androidx.pdf.service.PdfDocumentServiceImpl`—. **El riesgo D-001 queda
  > cerrado y no hace falta PdfBox.**
  >
  > **El fallo que encontró, y era real.** Los chips de página se dibujaban dos veces con la misma
  > identidad: junto al dato que respaldan y en la fila «Fuentes del resumen». Un resumen con tres puntos
  > clave citando la página 1 producía cuatro nodos indistinguibles. Las dos filas responden a preguntas
  > distintas y las dos deben estar —FR-020 pide la referencia junto al dato, §20.4 exige la fila—, así
  > que se conservan con identidad propia: `pageChipTag` y `sourceChipTag`.
  >
  > **Coste real:** 3 h 49 m para 147 pruebas, unos 93 s por prueba frente al mínimo teórico de 1,2 s.
  > Es el emulador ARM64, no el código. Si la tanda se va a repetir a menudo, merece mirarse aparte.

- [X] T108 Comprobar que la credencial no está en el repositorio ni en su historial: `git log -p --all -S 'gsk_'` debe salir vacío (FR-048)
- [ ] T109 **Pendiente, y es lo único que queda.** Una parte se ha verificado ya sin móvil y está en
  `quickstart.md` §3 bis: el servicio **acepta el esquema estricto** (HTTP 200 con el modelo acordado),
  la respuesta encaja con el DTO, las cabeceras de cuota son las documentadas, y el modelo conserva un
  plazo relativo sin convertirlo en fecha. Lo que sigue necesitando a una persona mirando la pantalla:
  la hoja de aviso y su cancelación, que los datos del resumen cuadren con el PDF, que un chip aterrice
  en su página, compartir y leer el texto, un documento largo de verdad, chocar con la cuota, y **leer
  `adb logcat` durante una generación completa** para confirmar que ni la clave ni el texto del
  documento aparecen. Recorrer las catorce comprobaciones manuales de `quickstart.md` §3 con un móvil real, incluida la lectura de `adb logcat` durante una generación completa (SC-001, SC-011)

---

## Dependencies & Execution Order

### Entre fases

- **Setup (1)**: sin dependencias
- **Foundational (2)**: depende de Setup. **Bloquea todas las historias**
- **US1 (3)**: depende de Foundational. Es el MVP
- **US2 (4)**: depende de Foundational. Toca el mismo repositorio que US1, así que en la práctica va después
- **US3 (5)**, **US4 (6)**, **US5 (7)**: dependen de Foundational; comparten con US1 el fichero `AiSummaryTab.kt`
- **Polish (8)**: al final

### Entre historias

Las cinco son **independientemente comprobables**, pero tres comparten fichero con US1
(`AiSummaryTab.kt`, `PublicationDetailViewModel.kt`). Con una sola persona trabajando el orden natural
es el de prioridad: US1 → US2 → US3 → US4 → US5. Con varias, conviene que US1 cierre el esqueleto del
conmutador de estados antes de que las demás añadan ramas.

### Dentro de cada historia

Pruebas y modelos primero, después la implementación, después la integración. Y la norma del
proyecto: **toda tarea se cierra con su prueba en verde**, no antes.

---

## Parallel Example: Phase 2

```bash
# Los cuatro modelos de dominio son ficheros distintos y no dependen entre sí:
T006  PdfCorpus.kt
T007  AiSummary.kt
T008  AiSummaryStatus.kt
T009  AiSummaryError.kt

# Y sus cuatro pruebas, una vez existen los modelos:
T010  PdfCorpusTest.kt
T011  AiSummaryTest.kt
T012  AiSummaryStatusTest.kt
T013  AiSummaryErrorTest.kt

# Los cuatro casos de uso, en cuanto exista el contrato T015:
T016  T017  T018  T019
```

---

## Implementation Strategy

### El MVP es US1

1. Fase 1 y fase 2 completas
2. Fase 3 completa
3. **Parar y validar**: generar un resumen real en un móvil, con una publicación corta del boletín
4. Si la ficha aparece y los datos cuadran con el PDF, la apuesta técnica está confirmada

Ese punto de parada importa más de lo normal: es donde se comprueba el **riesgo declarado en D-001**,
que la extracción de texto de `androidx.pdf` funcione en dispositivos reales. Si fallara, se cambia la
implementación de `PdfTextExtractor` sin tocar nada de lo demás — y es mucho mejor descubrirlo con
las fases 1 a 3 hechas que con las ocho.

### Entrega incremental

Cada historia añade valor sin romper la anterior: US2 deja de gastar cuota al volver; US3 hace
comprobable lo que US1 afirma; US4 pone el aviso antes del primer envío; US5 convierte los fallos en
explicaciones.

---

## Notes

- `[P]` = ficheros distintos, sin dependencias pendientes
- Commits en español, imperativo, con prefijo convencional, en la rama `007-resumen-ia`. **Nunca en `main`**
- Cada tarea con referencia a su `FR-0NN`/`SC-0NN` o a la decisión `D-0NN` que la justifica
- Prohibido `@Ignore`, comentar o borrar una prueba para pasar la build (principio V)
- Ninguna sentencia de borrado en ningún DAO, tampoco en el nuevo
- Ni la credencial ni el contenido del documento pueden aparecer en un registro, en un informe de fallo ni en analítica
