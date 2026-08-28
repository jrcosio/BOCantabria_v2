# Tasks: Pantalla de arranque y sistema de diseño institucional

**Input**: Design documents from `/specs/002-pantalla-arranque/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: OBLIGATORIOS. El principio V de la constitución los declara no negociables y la
especificación los exige en FR-024 … FR-027. Dentro de cada historia, las pruebas se escriben
**antes** que la implementación y deben fallar antes de hacerlas pasar.

**Organization**: las tareas se agrupan por historia de usuario, de forma que cada una pueda
implementarse, probarse y demostrarse por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (ficheros distintos, sin dependencias entre sí)
- **[Story]**: historia a la que pertenece (US1, US2, US3)

## Path Conventions

Abreviaturas: `MAIN/` = `app/src/main/java/com/jrblanco/boccantabria/`,
`TEST/` = `app/src/test/java/com/jrblanco/boccantabria/`,
`ATEST/` = `app/src/androidTest/java/com/jrblanco/boccantabria/`,
`RES/` = `app/src/main/res/`.

Antes de cualquier comando Gradle:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependencias, recursos y documentación versionada.

- [ ] T001 Añadir al catálogo `gradle/libs.versions.toml` la librería
      `androidx.core:core-splashscreen` 1.2.0 y `com.google.firebase:firebase-config` (sin versión,
      la gobierna el BoM), y declararlas en `app/build.gradle.kts`
- [ ] T002 [P] Copiar el escudo aportado a `RES/drawable/ic_escudo_cantabria.xml`, verificando que
      conserva su viewport 79×137 y sus colores originales (FR-021)
- [ ] T003 [P] Versionar la documentación de diseño: mover el documento de especificaciones y la
      imagen de referencia a `docs/diseno/`. Sin esto, el criterio SC-007 no es verificable por
      nadie que no tenga los ficheros en su máquina. `Datos_modelo/` sigue ignorado y así debe
      quedarse: conserva material de trabajo que no procede versionar (SC-007)
- [ ] T004 [P] Corregir en el documento de diseño los apartados 13.3 y 25.3, que siguen diciendo
      «Aplicación creada por José Ramón Blanco», para que documento y aplicación no se contradigan
- [ ] T005 Declarar `android:screenOrientation="portrait"` en `MainActivity` dentro de
      `app/src/main/AndroidManifest.xml`. Es el mecanismo estándar y el único que el sistema
      respeta; en pantallas de 600 dp o más lo ignora y no se intentará sortearlo (research.md
      D-011) (FR-023)

**Checkpoint**: el proyecto compila con las dependencias nuevas y el escudo disponible como recurso.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el sistema de diseño y los tipos que todas las historias necesitan.

**⚠️ CRITICAL**: ninguna historia puede empezar hasta que esta fase esté completa.

- [ ] T006 Reescribir `MAIN/core/ui/theme/Color.kt` con los 22 tokens del modo claro (§4.1), los 10
      del oscuro (§5.1) y los 5 de sección (§4.4). Nombres de token, no de color (FR-016)
- [ ] T007 [P] Crear `MAIN/core/ui/theme/Spacing.kt` con los nueve valores de §7.1 y su
      `CompositionLocal` (FR-018)
- [ ] T008 [P] Crear `MAIN/core/ui/theme/Shape.kt` con los radios de §8.1 sobre las formas de
      Material 3, más los que no tienen equivalente (FR-018)
- [ ] T009 [P] Crear `MAIN/core/ui/theme/Elevation.kt` con los cinco niveles de §8.2 (FR-018)
- [ ] T010 Reescribir `MAIN/core/ui/theme/Type.kt` con los 14 estilos de §6.2: tamaño, interlineado
      y peso exactos. Los pesos 650 se implementan como `SemiBold` (research.md D-012) (FR-018)
- [ ] T011 Reescribir `MAIN/core/ui/theme/Theme.kt`: esquemas claro y oscuro desde los tokens,
      `BocExtendedColors` con los diez tokens sin equivalente más los de sección, el objeto
      `BocTheme` con sus accesos (research.md D-001), y **eliminar el parámetro de color dinámico**
      (research.md D-002).
      `BocTheme` fuera del tema debe fallar de forma explícita (FR-016, FR-017)
      (depende de T006, T007, T008, T009, T010)
- [ ] T012 Ampliar `RES/values/themes.xml` con el tema de arranque del sistema: fondo azul
      institucional e icono el escudo, para que no haya destello blanco. El tema posterior al
      arranque sigue siendo el actual: la aplicación es Compose puro y **no** debe añadirse la
      librería de componentes Material para XML, que no usa (research.md D-003) (FR-002)
      (depende de T002)
- [ ] T013 Instalar el arranque del sistema en `MAIN/MainActivity.kt` y liberar su condición de
      permanencia en cuanto la primera composición esté lista, de modo que la preparación ocurra ya
      en la pantalla de Compose (research.md D-003) (FR-002) (depende de T012)
- [ ] T014 [P] Crear `MAIN/core/util/AppVersionProvider.kt`: la interfaz y su implementación
      leyendo el número de versión generado en la compilación (research.md D-009)
- [ ] T015 Verificar que el proyecto compila y que la aplicación sigue arrancando en el emulador con
      la identidad visual nueva. La pantalla Home de la feature 001 cambiará de aspecto al heredar
      la paleta institucional: es lo esperado

**Checkpoint**: identidad visual aplicada en toda la aplicación. Las historias pueden empezar.

---

## Phase 3: User Story 1 - Abrir la aplicación y entrar sin fricción (Priority: P1) 🎯 MVP

**Goal**: una portada institucional que prepara la aplicación sola y da paso al contenido
principal, sin destellos y sin parpadeos.

**Independent Test**: instalar en un dispositivo limpio y abrir: la portada aparece y, sin
intervención, pasa al contenido principal.

### Tests for User Story 1 ⚠️ (escribir primero; deben fallar)

- [ ] T016 [P] [US1] `TEST/domain/usecase/PrepareStartupUseCaseTest.kt`: con conexión y
      configuración correcta devuelve `Ready`; propaga el fallo del repositorio de configuración;
      no lanza nunca (FR-003, FR-024)
- [ ] T017 [P] [US1] `TEST/data/repository/AppConfigRepositoryImplTest.kt`: traduce los valores
      remotos a dominio ajustando el tipo numérico, convierte el mensaje vacío en nulo, devuelve los
      valores por defecto cuando no hay nada publicado, y **ninguna excepción escapa** (FR-014,
      FR-024)
- [ ] T018 [P] [US1] `TEST/ui/splash/SplashViewModelTest.kt`, parte de la historia 1, con Turbine:
      estado inicial `Loading`; `Loading → Ready`; el evento de pantalla vista se registra
      exactamente una vez; **el tiempo mínimo de 1,2 s se respeta y no se suma al trabajo real**,
      comprobado con tiempo virtual (FR-005, FR-027, SC-002)
- [ ] T019 [P] [US1] `ATEST/ui/splash/SplashContentTest.kt`, parte de la historia 1: render del
      estado de preparación comprobando las etiquetas `splash_root`, `splash_emblem` y
      `splash_loading` (FR-025)
- [ ] T020 [US1] `ATEST/ui/SplashNavigationTest.kt`: al completarse el arranque se llega al
      contenido principal, y el retroceso **cierra la aplicación** en lugar de volver a la portada
      (FR-007, FR-026)
- [ ] T021 [P] [US1] `ATEST/ui/splash/SplashRestorationTest.kt`: durante la preparación se llama a
      `recreate()` y se afirma que **no** se dispara una segunda —el contador del doble sigue en
      uno— y que el estado no retrocede. Con el bloqueo vertical ya no hay giro, pero el modo
      oscuro, el tamaño de letra y el idioma siguen recreando la actividad; cubre además el caso
      límite de salir de la aplicación y volver, que es el mismo mecanismo (FR-008)

### Implementation for User Story 1

- [ ] T022 [P] [US1] `MAIN/domain/model/AppConfig.kt` y `MAIN/domain/model/StartupStatus.kt` según
      `data-model.md`, con los valores por defecto que equivalen a «todo permitido»
- [ ] T023 [P] [US1] `MAIN/domain/repository/AppConfigRepository.kt` y
      `MAIN/domain/repository/ConnectivityRepository.kt` con los contratos de
      `contracts/internal-contracts.md`
- [ ] T024 [P] [US1] Configuración remota en `MAIN/data/source/remote/`: `RemoteConfigValues.kt`
      (campos con los nombres del servicio), `RemoteConfigDataSource.kt` y
      `FirebaseRemoteConfigDataSource.kt`, más los valores por defecto en
      `RES/xml/remote_config_defaults.xml` (research.md D-008) (FR-014)
- [ ] T025 [P] [US1] Conectividad en `MAIN/data/source/local/`: `ConnectivityDataSource.kt` y
      `AndroidConnectivityDataSource.kt`, que responde si hay red **con acceso validado a
      internet**, no si hay una interfaz activa (research.md D-010)
- [ ] T026 [US1] `MAIN/data/repository/AppConfigRepositoryImpl.kt` y
      `MAIN/data/repository/ConnectivityRepositoryImpl.kt`: traducción a dominio, captura de
      excepciones y trabajo sobre el despachador de entrada/salida inyectado
      (depende de T022, T023, T024, T025)
- [ ] T027 [US1] `MAIN/domain/usecase/PrepareStartupUseCase.kt` con la precedencia documentada en
      `data-model.md`. Un único caso de uso orquesta las tres comprobaciones, para que la política
      de arranque sea Kotlin puro y no viva en la capa de presentación (research.md D-006)
      (depende de T022, T023)
- [ ] T028 [P] [US1] `MAIN/ui/splash/SplashUiState.kt`: sellado con `Loading`, `Ready`, `Error` y
      `Blocked`, y `BlockReason` sellado. `Blocked` es un estado propio y no un `Error` con
      bandera, para que la combinación «bloqueado pero con salida» no se pueda ni escribir
      (research.md D-007) (FR-009)
- [ ] T029 [US1] `MAIN/ui/splash/SplashViewModel.kt`, parte de la historia 1: preparación única al
      construirse, tiempo mínimo **en paralelo** al trabajo (research.md D-004), y registro del
      evento de pantalla vista (depende de T027, T028)
- [ ] T030 [US1] `MAIN/ui/splash/SplashScreen.kt`: `SplashScreen` con `koinViewModel()` y
      `SplashContent` sin estado. Composición exacta del contrato visual de
      `contracts/internal-contracts.md`: escudo 104 dp, `BOC`, denominación en dos líneas, línea
      divisoria, autoría en dos colores e indicador discreto, sobre fondo azul a pantalla completa
      extendido tras las barras con iconos claros (FR-019, FR-020, FR-021, FR-022)
      (depende de T011, T028)
- [ ] T031 [US1] Ajustar la apariencia de las barras del sistema por pantalla: iconos **claros**
      mientras la portada azul está visible e **oscuros** al pasar al contenido principal, que tiene
      fondo claro. Sin esto los iconos del sistema quedan ilegibles en una de las dos (FR-022)
      (depende de T030)
- [ ] T032 [P] [US1] Añadir a `RES/values/strings.xml` los textos de la portada: siglas,
      denominación en dos líneas, «Diseñada y desarrollada por» y «José Ramón Blanco Gutiérrez»
- [ ] T033 [US1] Navegación: `Route.Splash` en `MAIN/ui/navigation/Routes.kt` como destino inicial,
      y en `MAIN/ui/navigation/BOCantabriaNavHost.kt` la navegación a Home descartando la portada de
      la pila (FR-001, FR-004, FR-007) (depende de T030)
- [ ] T034 [US1] Registrar en `MAIN/core/di/` las siete dependencias nuevas: fuentes, repositorios,
      caso de uso, proveedor de versión y modelo de pantalla (depende de T026, T027, T029)
- [ ] T035 [US1] Poner en verde T016–T020 con `./gradlew :app:testDebugUnitTest` y
      `./gradlew :app:connectedDebugAndroidTest`

**Checkpoint**: la historia 1 es demostrable por sí sola. Es el producto mínimo viable.

---

## Phase 4: User Story 2 - Enterarse de que algo va mal, y poder seguir (Priority: P1)

**Goal**: que ningún fallo deje a la persona atrapada en la portada, y que un acceso bloqueado no
se pueda saltar.

**Independent Test**: activar el modo avión y abrir la aplicación: aparece el mensaje con sus dos
acciones, y «continuar» lleva al contenido principal.

### Tests for User Story 2 ⚠️

- [ ] T036 [P] [US2] Ampliar `TEST/domain/usecase/PrepareStartupUseCaseTest.kt` con la precedencia
      completa: sin conexión manda sobre todo; versión por debajo del mínimo → `UpdateRequired`;
      versión obsoleta **y** mantenimiento a la vez → gana `UpdateRequired`; mensaje de
      mantenimiento → `Maintenance` (FR-012, FR-013)
- [ ] T037 [P] [US2] Ampliar `TEST/ui/splash/SplashViewModelTest.kt`: `Loading → Error`;
      `Loading → Blocked`; reintentar desde `Error` alcanza `Ready`; **reintentar durante una
      preparación en curso no lanza una segunda**; continuar sin conexión desde `Error` emite
      `Ready`; continuar sin conexión desde `Blocked` **se ignora**; superar el límite de espera de
      8 s produce `Error`, comprobado con tiempo virtual (FR-006, FR-010, FR-011, FR-012)
- [ ] T038 [P] [US2] Ampliar `ATEST/ui/splash/SplashContentTest.kt` con los estados de error y de
      acceso bloqueado: el estado de error muestra `splash_retry` y `splash_continue_offline` y
      ambos invocan su devolución de llamada; el estado bloqueado muestra `splash_blocked` y
      **`splash_continue_offline` no existe** (FR-010, FR-012, FR-025)

### Implementation for User Story 2

- [ ] T039 [US2] Completar `PrepareStartupUseCase` con la precedencia completa (depende de T027)
- [ ] T040 [US2] Completar `SplashViewModel` con `onRetry()`, `onContinueOffline()`, la guarda
      contra preparaciones simultáneas, el límite de espera de 8 s (research.md D-005) y el reporte
      de los fallos al servicio de errores (FR-015) (depende de T029)
- [ ] T041 [US2] Completar `SplashContent` con los estados de error y bloqueado, cada uno con sus
      propias acciones, reutilizando los componibles compartidos de `core/ui/component` donde
      encajen (depende de T030)
- [ ] T042 [P] [US2] Añadir a `RES/values/strings.xml` los textos de error, de actualización
      requerida y de las acciones
- [ ] T043 [US2] Poner en verde T036–T038 y comprobar en el emulador, siguiendo el paso 5 de
      `quickstart.md`, que sin conexión se llega al contenido principal en dos toques como máximo
      (SC-003)

**Checkpoint**: las historias 1 y 2 funcionan de forma independiente.

---

## Phase 5: User Story 3 - Una aplicación institucional y constante (Priority: P2)

**Goal**: que la identidad visual no dependa del dispositivo ni de la orientación, y que aguante el
modo oscuro y el texto ampliado.

**Independent Test**: cambiar el fondo de pantalla por uno de color intenso y abrir la aplicación:
los colores no cambian. Girar el dispositivo: permanece vertical.

### Tests for User Story 3 ⚠️

- [ ] T044 [P] [US3] Añadir a `TEST/architecture/ArchitectureRulesTest.kt` una regla que falle si
      algún fichero fuera de `core/ui/theme` importa `androidx.compose.ui.graphics.Color`: los
      colores se consumen del tema, no se declaran en el punto de uso. Se expresa como regla de
      **importación** y no buscando literales `Color(0xFF…)` dentro de los cuerpos, porque eso
      último produce falsos positivos con `Color.Transparent` y con los modificadores de alfa
      (FR-018, SC-005)
- [ ] T045 [P] [US3] Añadir `AppConfig` y `RemoteConfigValues` a la lista de portadores de datos sin
      comportamiento de `ArchitectureRulesTest`, o darles prueba propia si acaban teniéndolo. La
      lista debe seguir siendo corta: cada entrada es un hueco en SC-004

### Implementation for User Story 3

- [ ] T046 [US3] Corregir lo que destape T044 y verificar en el emulador, siguiendo el paso 6 de
      `quickstart.md`, que con dos fondos de pantalla de colores opuestos la interfaz no cambia
      (FR-017, SC-005)
- [ ] T047 [US3] Verificar la orientación siguiendo el paso 4 de `quickstart.md`: al girar el
      dispositivo la captura sigue en vertical (FR-023)
- [ ] T048 [US3] Verificar el modo oscuro y el texto al 200 % siguiendo el paso 6 de
      `quickstart.md`: la portada conserva el azul institucional, el resto adopta la paleta oscura y
      ningún texto queda recortado (SC-006)

**Checkpoint**: las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T049 Ejecutar `quickstart.md` completo, incluida la comprobación de que las reglas de
      arquitectura fallan ante una violación deliberada
- [ ] T050 Verificar la ausencia de destello siguiendo el paso 4 de `quickstart.md`, grabando el
      arranque y revisando los primeros fotogramas (FR-002)
- [ ] T051 Comparar la captura real contra la imagen de referencia: proporciones del escudo,
      jerarquía de `BOC`, línea divisoria y los dos colores de la autoría (SC-007)
- [ ] T052 Medir SC-001 con `adb shell am start -W` sobre instalación limpia: `TotalTime` por
      debajo de 3000 ms. Anotar la cifra
- [ ] T053 `./gradlew :app:lintDebug` sin avisos nuevos atribuibles a esta feature. Con esto las
      cuatro comprobaciones de calidad quedan en verde (SC-008)
- [ ] T054 [P] Actualizar `CLAUDE.md`: el mapa de paquetes con `ui/splash`, el sistema de diseño y
      la regla de consumir tokens en lugar de valores sueltos. Si algo de esto afecta a la
      constitución, enmendarla en el mismo cambio
- [ ] T055 Empujar la rama, comprobar que la integración continua queda en verde y abrir la
      solicitud de incorporación hacia `main`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias
- **Foundational (fase 2)**: depende de la fase 1. **Bloquea todas las historias**
- **Historias (fases 3–5)**: todas dependen de la fase 2
  - US2 amplía ficheros que crea US1, así que va después
  - US3 verifica la identidad visual sobre pantallas que ya existen, así que va después de US1
- **Polish (fase 6)**: depende de las tres historias

### Within Each User Story

- Las pruebas se escriben **antes** y deben fallar antes de implementarlas
- Modelos y contratos antes que fuentes; fuentes antes que repositorios; repositorios antes que el
  caso de uso; el caso de uso antes que el modelo de pantalla; el modelo de pantalla antes que el
  componible
- El cableado de Koin al final de cada historia, cuando ya existen las clases que registra

### Parallel Opportunities

- Fase 1: T002, T003 y T004 son independientes entre sí
- Fase 2: T007, T008, T009 y T014 tocan ficheros distintos
- US1: las pruebas T016–T019 se escriben en paralelo; T022, T023, T024, T025, T028 y T032 tocan
  ficheros distintos
- US2: T036, T037 y T038 son independientes
- US3: T044 y T045 son independientes

---

## Parallel Example: User Story 1

```text
# Pruebas de la historia 1, todas a la vez (deben fallar):
Tarea: "PrepareStartupUseCaseTest en TEST/domain/usecase/"
Tarea: "AppConfigRepositoryImplTest en TEST/data/repository/"
Tarea: "SplashViewModelTest en TEST/ui/splash/"
Tarea: "SplashContentTest en ATEST/ui/splash/"

# Piezas independientes de la historia 1, todas a la vez:
Tarea: "AppConfig y StartupStatus en MAIN/domain/model/"
Tarea: "Los dos contratos de repositorio en MAIN/domain/repository/"
Tarea: "Configuración remota y sus valores por defecto en MAIN/data/source/remote/"
Tarea: "Conectividad en MAIN/data/source/local/"
Tarea: "SplashUiState en MAIN/ui/splash/"
Tarea: "Textos de la portada en RES/values/strings.xml"
```

---

## Implementation Strategy

### MVP First (solo historia 1)

1. Completar la fase 1 (Setup)
2. Completar la fase 2 (Foundational) — bloquea todo lo demás
3. Completar la fase 3 (US1)
4. **PARAR Y VALIDAR**: instalar en el emulador, comprobar que no hay destello, que la portada se
   ve al menos un segundo y que pasa sola al contenido principal
5. En este punto ya hay algo demostrable

### Incremental Delivery

1. Setup + Foundational → identidad visual aplicada
2. US1 → validar → producto mínimo viable
3. US2 → validar en modo avión → la aplicación es confiable
4. US3 → validar con dos fondos de pantalla y girando → la identidad es constante
5. Polish → `quickstart.md` completo, integración continua en verde, solicitud de incorporación

Cada historia aporta valor sin romper las anteriores.
