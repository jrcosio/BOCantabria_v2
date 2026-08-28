# Tasks: Esqueleto de arquitectura de la aplicación

**Input**: Design documents from `/specs/001-esqueleto-arquitectura/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: OBLIGATORIOS. El principio V de la constitución los declara no negociables y la
especificación los exige en FR-017 … FR-022. Dentro de cada historia, las pruebas se escriben
**antes** que la implementación y deben fallar antes de hacerlas pasar.

**Estado**: las 53 tareas están completadas. Ver `plan.md` y los mensajes de commit para el
detalle de lo ejecutado.

**Organization**: las tareas se agrupan por historia de usuario, de forma que cada una pueda
implementarse, probarse y demostrarse por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (ficheros distintos, sin dependencias entre sí)
- **[Story]**: historia a la que pertenece (US1, US2, US3)
- Las rutas de fichero son exactas y relativas a la raíz del repositorio

## Path Conventions

Módulo único `:app`, separación por paquetes bajo `com.jrblanco.boccantabria`:

- Producción: `app/src/main/java/com/jrblanco/boccantabria/`
- Pruebas sin dispositivo: `app/src/test/java/com/jrblanco/boccantabria/`
- Pruebas de interfaz: `app/src/androidTest/java/com/jrblanco/boccantabria/`

Abreviatura usada abajo: `MAIN/` = `app/src/main/java/com/jrblanco/boccantabria/`,
`TEST/` = `app/src/test/java/com/jrblanco/boccantabria/`,
`ATEST/` = `app/src/androidTest/java/com/jrblanco/boccantabria/`.

Antes de cualquier comando Gradle: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dejar el build preparado para la arquitectura y limpiar los restos de la plantilla.

- [x] T001 Añadir al catálogo `gradle/libs.versions.toml` el plugin de serialización de Kotlin
      (`org.jetbrains.kotlin.plugin.serialization`, versión `kotlin` = 2.2.10) y la librería
      `com.lemonappdev:konsist:0.17.3`, siguiendo la regla de no escribir versiones literales
      fuera del catálogo
- [x] T002 Aplicar el plugin de serialización en `build.gradle.kts` (raíz, `apply false`) y en
      `app/build.gradle.kts`; añadir `konsist` como `testImplementation` y activar
      `buildFeatures { buildConfig = true }`, que necesita `BOCantabriaApp` para ajustar el
      nivel de log de Koin
- [x] T003 **Puerta de riesgo (research.md D-005)**: ejecutar `./gradlew :app:assembleDebug` y
      confirmar que el plugin de serialización es compatible con AGP 9. Si falla, aplicar la
      vuelta atrás prevista —rutas de navegación de texto con constantes centralizadas—,
      retirar el plugin y anotar el cambio en `research.md` antes de continuar
- [x] T004 [P] Eliminar las plantillas de prueba del proyecto generado (FR-022):
      `TEST/ExampleUnitTest.kt` y `ATEST/ExampleInstrumentedTest.kt`

**Checkpoint**: el proyecto compila con las dependencias nuevas y sin pruebas de ejemplo.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: los tipos y contratos que todas las historias necesitan.

**⚠️ CRITICAL**: ninguna historia puede empezar hasta que esta fase esté completa.

- [x] T005 [P] Crear `MAIN/domain/model/AppResult.kt`: `sealed interface AppResult<out T>` con
      `Success<T>(data)` y `Failure(error: DomainError)`, según `data-model.md`
- [x] T006 [P] Crear `MAIN/domain/model/DomainError.kt`: `sealed interface DomainError` con
      `Network` y `Unknown` como objetos
- [x] T007 [P] Crear `MAIN/domain/model/ContentItem.kt`: `data class ContentItem(id: String, title: String)`,
      inmutable y sin dependencias de plataforma
- [x] T008 [P] Crear `MAIN/core/util/DispatcherProvider.kt`: la interfaz con `main`, `io` y
      `default`, más `DefaultDispatcherProvider` (research.md D-007, FR-021)
- [x] T009 [P] Crear `MAIN/core/telemetry/AnalyticsEvent.kt`: `data class` con `name` y
      `parameters`, y la lista de claves consideradas sensibles que nunca se envían (FR-016)
- [x] T010 [P] Crear `MAIN/core/telemetry/AnalyticsTracker.kt` y
      `MAIN/core/telemetry/CrashReporter.kt` con las interfaces de `contracts/internal-contracts.md`,
      más implementaciones sin efecto (`NoOpAnalyticsTracker`, `NoOpCrashReporter`) para que las
      historias 1 y 2 no dependan de la 3 (FR-015)
- [x] T011 Mover el tema de `MAIN/ui/theme/` a `MAIN/core/ui/theme/` (`Color.kt`, `Theme.kt`,
      `Type.kt`), actualizando `package` e importaciones
- [x] T012 [P] Crear los componibles compartidos sin estado en `MAIN/core/ui/component/`:
      `LoadingIndicator.kt`, `ErrorMessage.kt` (con acción de reintento) y `EmptyMessage.kt`,
      cada uno con su etiqueta de prueba según `contracts/internal-contracts.md`
- [x] T013 Crear el cableado base: `MAIN/core/di/CoreModule.kt` (despachadores y telemetría sin
      efecto) y `MAIN/core/di/AppModules.kt` con `val appModules: List<Module>` como único punto
      de entrada del grafo (FR-010)
- [x] T014 Crear `MAIN/BOCantabriaApp.kt` que arranca Koin con `androidContext` y `appModules`,
      y registrarla en `app/src/main/AndroidManifest.xml` con `android:name=".BOCantabriaApp"`
- [x] T015 [P] Crear los dobles compartidos de prueba en `TEST/fake/`:
      `TestDispatcherProvider.kt` (respaldado por `TestDispatcher`),
      `RecordingAnalyticsTracker.kt` (guarda los eventos recibidos para poder afirmarlos) y
      `FakeContentRemoteDataSource.kt` (permite forzar éxito, lista vacía o fallo; lo necesitan
      T018, T022 y T039) — FR-021
- [x] T016 Ejecutar `./gradlew :app:assembleDebug` y confirmar que la aplicación sigue
      arrancando en el emulador con Koin iniciado

**Checkpoint**: base lista. Las historias pueden empezar.

---

## Phase 3: User Story 1 - La aplicación arranca y muestra contenido (Priority: P1) 🎯 MVP

**Goal**: una pantalla inicial que carga contenido a través de todas las capas y representa de
forma excluyente los estados cargando, contenido, vacío y error, con reintento.

**Independent Test**: instalar y abrir la aplicación: transita de «cargando» a «contenido» sin
intervención; forzando el fallo del origen aparece el mensaje de error y el reintento funciona.

### Tests for User Story 1 ⚠️ (escribir primero; deben fallar)

- [x] T017 [P] [US1] `TEST/domain/usecase/GetContentItemsUseCaseTest.kt`: el caso de uso
      propaga `Success` y `Failure` del repositorio sin alterarlos (FR-017)
- [x] T018 [P] [US1] `TEST/data/repository/ContentRepositoryImplTest.kt`: los cuatro casos de la
      tabla de política de `contracts/internal-contracts.md` —remoto responde (traduce y
      guarda), remoto falla con respaldo local, remoto falla sin respaldo (`Failure(Network)`) y
      remoto devuelve lista vacía (`Success(emptyList())` y limpia local)— más que ninguna
      excepción escapa del repositorio (FR-004, FR-009)
- [x] T019 [P] [US1] `TEST/ui/home/HomeViewModelTest.kt` con Turbine: estado inicial `Loading`;
      `Loading → Content`; `Loading → Empty`; `Loading → Error`; `onRetry()` desde `Error` lleva
      a `Content`; `onRetry()` durante una carga se ignora y no lanza una segunda; el evento de
      pantalla vista se registra exactamente una vez (FR-002, FR-003, FR-017)
- [x] T020 [P] [US1] `ATEST/ui/home/HomeContentTest.kt`: render de los cuatro estados sobre el
      componible sin estado, comprobando las etiquetas `home_loading`, `home_content`,
      `home_empty` y `home_error`, y que pulsar `home_retry` invoca la devolución de llamada
      (FR-020)
- [x] T021 [P] [US1] `ATEST/ui/home/HomeStateRestorationTest.kt`: tras llegar a `Content`, se
      llama a `recreate()` sobre la actividad y se afirma que el contenido sigue visible y que
      **no** reaparece el indicador de carga, es decir que no se dispara una segunda carga
      (FR-005, FR-023, escenario 4 de la historia 1)
- [x] T022 [US1] `ATEST/ui/HomeScreenEndToEndTest.kt`: la actividad real arranca con Koin
      sustituyendo únicamente el origen remoto por un doble, y la pantalla llega a mostrar el
      contenido (FR-001, FR-019)

### Implementation for User Story 1

- [x] T023 [P] [US1] `MAIN/domain/repository/ContentRepository.kt`: la interfaz
      `suspend fun getContentItems(): AppResult<List<ContentItem>>`
- [x] T024 [P] [US1] Origen remoto en `MAIN/data/source/remote/`: `ContentItemDto.kt` (campos
      `id` y `label`), `ContentRemoteDataSource.kt` (interfaz) y `StubContentRemoteDataSource.kt`
      (lista fija con latencia simulada, research.md D-001)
- [x] T025 [P] [US1] Origen local en `MAIN/data/source/local/`: `ContentItemEntity.kt`,
      `ContentLocalDataSource.kt` (interfaz) e `InMemoryContentLocalDataSource.kt`
- [x] T026 [US1] `MAIN/data/repository/ContentRepositoryImpl.kt`: traducción DTO → dominio y
      entidad → dominio, política de remoto con respaldo local, captura de excepciones y
      traducción a `DomainError`, todo sobre el despachador de entrada/salida inyectado
      (depende de T023, T024, T025)
- [x] T027 [US1] `MAIN/domain/usecase/GetContentItemsUseCase.kt` con `operator fun invoke()`
      (depende de T023)
- [x] T028 [P] [US1] `MAIN/ui/home/HomeUiState.kt`: sellada con `Loading`, `Content(items)`,
      `Empty` y `Error(error)`, según `data-model.md`
- [x] T029 [US1] `MAIN/ui/home/HomeViewModel.kt`: `MutableStateFlow` privado, `StateFlow`
      público, carga inicial única, guarda contra cargas simultáneas en `onRetry()` y registro
      del evento de pantalla vista a través de `AnalyticsTracker` (depende de T027, T028)
- [x] T030 [US1] `MAIN/ui/home/HomeScreen.kt`: `HomeScreen` que obtiene el modelo con
      `koinViewModel()` y `HomeContent` sin estado que dibuja los cuatro casos reutilizando los
      componibles de `core/ui/component` (depende de T028, T012)
- [x] T031 [P] [US1] Navegación en `MAIN/ui/navigation/`: `Routes.kt` con las rutas tipadas y
      `BOCantabriaNavHost.kt` con el grafo (FR-006)
- [x] T032 [US1] Reescribir `MAIN/MainActivity.kt` como anfitrión de la navegación con el tema
      de `core/ui/theme`, eliminando el `Greeting` de la plantilla (depende de T031)
- [x] T033 [US1] Completar el cableado: `MAIN/core/di/DataModule.kt` (orígenes y repositorio),
      `MAIN/core/di/DomainModule.kt` (caso de uso) y `MAIN/core/di/UiModule.kt`
      (`viewModelOf(::HomeViewModel)`), y registrarlos en `AppModules.kt` (depende de T026, T027, T029)
- [x] T034 [P] [US1] Añadir a `app/src/main/res/values/strings.xml` los textos visibles en
      español: título de la pantalla, mensaje de error, mensaje de sin contenido y etiqueta del
      botón de reintentar
- [x] T035 [US1] Ejecutar `./gradlew :app:testDebugUnitTest` y
      `./gradlew :app:connectedDebugAndroidTest`; poner en verde T017–T022

**Checkpoint**: la historia 1 es funcional y demostrable por sí sola. Es el producto mínimo
viable de esta feature.

---

## Phase 4: User Story 2 - Patrón reproducible para añadir features (Priority: P2)

**Goal**: que la organización del código sea evidente y que las violaciones de la regla de
capas y del cableado se detecten automáticamente en lugar de degradarse en silencio.

**Independent Test**: introducir una violación deliberada de capa o quitar una dependencia del
cableado hace fallar las comprobaciones antes de llegar al dispositivo.

### Tests for User Story 2 ⚠️

- [x] T036 [P] [US2] `TEST/architecture/ArchitectureRulesTest.kt` con Konsist, con las cinco
      primeras reglas de `research.md` D-006: `domain` sin `android.*`, `androidx.*`,
      `com.google.firebase.*`, `org.koin.*` ni referencias a `data`/`ui`; `ui` sin importar
      `data`; las clases `*UseCase` en `domain.usecase`; las clases `*ViewModel` en `ui` y
      extendiendo `ViewModel`; y `com.google.firebase.*` importado únicamente desde `data`
      (FR-007, FR-008, FR-009)
- [x] T037 [P] [US2] Añadir a `TEST/architecture/ArchitectureRulesTest.kt` la sexta regla
      (research.md D-006): toda clase de `domain` y toda clase `*ViewModel` debe tener un fichero
      de prueba asociado en `TEST/`. Es lo que hace verificable el criterio SC-002 en cada build
      en lugar de dejarlo como afirmación
- [x] T038 [P] [US2] `TEST/di/KoinModulesTest.kt` bajo Robolectric: arranca Koin con
      `appModules` y un `Context` real y verifica que **todas** las dependencias declaradas
      resuelven (FR-011, FR-018, research.md D-008)
- [x] T039 [US2] `TEST/integration/ContentFlowIntegrationTest.kt`: recorrido completo
      `HomeViewModel → GetContentItemsUseCase → ContentRepositoryImpl → orígenes` resolviendo
      desde el grafo real de Koin y sustituyendo únicamente el origen remoto (FR-012, FR-019)

### Implementation for User Story 2

- [x] T040 [US2] Corregir cualquier violación que destapen T036, T037 y T038 (movimientos de fichero,
      importaciones o registros de módulo que falten)
- [x] T041 [US2] **Verificar que las reglas muerden** siguiendo el paso 2 de `quickstart.md`:
      introducir `import android.content.Context` en un fichero de `domain`, comprobar que
      `ArchitectureRulesTest` **falla**, revertir y comprobar que vuelve a pasar (SC-004)

**Checkpoint**: las historias 1 y 2 funcionan de forma independiente. La arquitectura está
protegida por pruebas, no por convención.

---

## Phase 5: User Story 3 - Visibilidad de uso y de fallos (Priority: P3)

**Goal**: que los eventos de uso y los cierres inesperados lleguen a sus paneles, sin que
ninguna prueba contacte con el servicio real.

**Independent Test**: abrir la aplicación y comprobar que el evento de pantalla vista llega al
panel de analítica; provocar un cierre inesperado y comprobar que la traza aparece.

### Tests for User Story 3 ⚠️

- [x] T042 [P] [US3] `TEST/data/telemetry/FirebaseAnalyticsTrackerTest.kt` con Robolectric y un
      doble de MockK sobre el cliente de Firebase (research.md D-009): comprueba el nombre y los
      atributos del evento enviado y, sobre todo, que las claves sensibles se descartan y nunca
      se envían (FR-013, FR-016)
- [x] T043 [P] [US3] `TEST/data/telemetry/FirebaseCrashReporterTest.kt`: los fallos no fatales y
      los mensajes se delegan en el cliente, y el envoltorio **nunca** propaga una excepción a
      quien lo llama (FR-014, contrato de «dispara y olvida»)

### Implementation for User Story 3

- [x] T044 [P] [US3] `MAIN/data/telemetry/FirebaseAnalyticsTracker.kt`: implementa
      `AnalyticsTracker` sobre `FirebaseAnalytics`, compone el `Bundle`, filtra las claves
      sensibles y captura cualquier fallo sin propagarlo
- [x] T045 [P] [US3] `MAIN/data/telemetry/FirebaseCrashReporter.kt`: implementa `CrashReporter`
      sobre `FirebaseCrashlytics` con el mismo contrato de no propagación
- [x] T046 [US3] Sustituir en `MAIN/core/di/DataModule.kt` las implementaciones sin efecto por
      las de Firebase, dejando las primeras disponibles para las pruebas (depende de T044, T045)
- [x] T047 [US3] Comprobar en el emulador que Firebase inicializa y que el evento de pantalla
      vista se emite, siguiendo los pasos 4 y 6 de `quickstart.md`

**Checkpoint**: las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T048 Ejecutar `quickstart.md` completo de principio a fin y confirmar que los pasos 1, 2,
      3 y 5 terminan en verde y que el paso 4 se comporta como describe
- [x] T049 Medir SC-001: `adb shell am start -W -n com.jrblanco.boccantabria/.MainActivity`
      sobre una instalación limpia y comprobar que el campo `TotalTime` queda por debajo de
      2000 ms; anotar la cifra obtenida
- [x] T050 `./gradlew :app:lintDebug` sin avisos nuevos atribuibles a esta feature
- [x] T051 Confirmar SC-003: la suite sin dispositivo termina por debajo de 2 minutos
- [x] T052 [P] Revisar que `CLAUDE.md` sigue describiendo la estructura real; actualizar el mapa
      de paquetes con `core/telemetry` y `ui/navigation`, y la constitución si procede (la guía
      y la norma se actualizan en el mismo cambio)
- [x] T053 Empujar la rama, comprobar que la integración continua queda en verde y abrir la
      solicitud de incorporación hacia `main`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias. T003 es una puerta: si falla, hay que aplicar la vuelta
  atrás antes de seguir
- **Foundational (fase 2)**: depende de la fase 1. **Bloquea todas las historias**
- **Historias (fases 3–5)**: todas dependen de la fase 2
  - US1 no depende de US2 ni de US3 (usa la telemetría sin efecto de T010)
  - US2 necesita que exista la rebanada de US1, porque es lo que sus reglas verifican
  - US3 es independiente de US2
- **Polish (fase 6)**: depende de las tres historias

### User Story Dependencies

- **US1 (P1)**: puede empezar en cuanto termine la fase 2. Sin dependencias de otras historias
- **US2 (P2)**: requiere US1 completa. Sus pruebas de arquitectura necesitan código real que
  inspeccionar
- **US3 (P3)**: puede empezar en cuanto termine la fase 2, en paralelo con US2. Solo sustituye
  implementaciones detrás de contratos que ya existen

### Within Each User Story

- Las pruebas se escriben **antes** y deben fallar antes de implementarlas
- Modelos antes que repositorios; repositorios antes que casos de uso; casos de uso antes que
  modelos de pantalla; modelos de pantalla antes que componibles
- El cableado de Koin al final de cada historia, cuando ya existen las clases que registra

### Parallel Opportunities

- Fase 2: T005–T010, T012 y T015 son independientes entre sí
- US1: las seis pruebas T017–T022 se escriben en paralelo; T023, T024, T025, T028, T031 y T034
  tocan ficheros distintos
- US2 y US3 pueden abordarse en paralelo una vez cerrada US1
- US3: T042/T043 y T044/T045 son parejas independientes

---

## Parallel Example: User Story 1

```text
# Pruebas de la historia 1, todas a la vez (deben fallar):
Tarea: "GetContentItemsUseCaseTest en TEST/domain/usecase/"
Tarea: "ContentRepositoryImplTest en TEST/data/repository/"
Tarea: "HomeViewModelTest en TEST/ui/home/"
Tarea: "HomeContentTest en ATEST/ui/home/"

# Piezas independientes de la historia 1, todas a la vez:
Tarea: "ContentRepository (interfaz) en MAIN/domain/repository/"
Tarea: "Origen remoto y su DTO en MAIN/data/source/remote/"
Tarea: "Origen local y su entidad en MAIN/data/source/local/"
Tarea: "HomeUiState en MAIN/ui/home/"
Tarea: "Rutas y grafo de navegación en MAIN/ui/navigation/"
```

---

## Implementation Strategy

### MVP First (solo historia 1)

1. Completar la fase 1 (Setup), atendiendo a la puerta de riesgo T003
2. Completar la fase 2 (Foundational) — bloquea todo lo demás
3. Completar la fase 3 (US1)
4. **PARAR Y VALIDAR**: instalar en el emulador y comprobar los cuatro estados y el reintento
5. En este punto ya hay algo demostrable

### Incremental Delivery

1. Setup + Foundational → base lista
2. US1 → validar → producto mínimo viable
3. US2 → validar que una violación deliberada falla → arquitectura protegida
4. US3 → validar en la consola de Firebase → observabilidad activa
5. Polish → `quickstart.md` completo, integración continua en verde, solicitud de incorporación

Cada historia aporta valor sin romper las anteriores.
