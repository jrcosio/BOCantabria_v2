# CLAUDE.md

Guía operativa para Claude Code en este repositorio.

> Este fichero es la **guía operativa**; la **constitución** (`.specify/memory/constitution.md`)
> es la norma. Si enmiendas una, actualiza la otra en el mismo cambio.

---

## Regla número uno: SDD obligatorio

Este proyecto usa **Spec-Driven Development** con **GitHub Spec Kit**. Toda feature recorre el
ciclo completo antes de tocar código de producto:

```
/speckit-specify  →  /speckit-plan  →  /speckit-tasks  →  /speckit-implement
```

- **No escribas código de producto sin un `tasks.md` aprobado** en `specs/<NNN>-<slug>/`.
  Si te piden una feature directamente, arranca por `/speckit-specify`.
- `/speckit-specify` crea también la rama `NNN-slug` (extensión git de Spec Kit).
- Opcionales pero recomendados: `/speckit-clarify` antes de planificar, `/speckit-analyze`
  antes de implementar.
- **Exentos del ciclo**: arreglos de build, subidas de versión, erratas y documentación.

Las normas del proyecto viven en `.specify/memory/constitution.md`.

### Comandos Spec Kit disponibles

| Comando | Cuándo |
|---|---|
| `/speckit-specify` | Arranque de toda feature. Crea rama + `specs/NNN-slug/spec.md` |
| `/speckit-clarify` | Opcional, **antes** de `/speckit-plan`. Resuelve ambigüedades |
| `/speckit-plan` | Diseño técnico → `plan.md` |
| `/speckit-tasks` | Descomposición ejecutable → `tasks.md` |
| `/speckit-checklist` | Opcional, tras `/speckit-plan`. Calidad de requisitos |
| `/speckit-analyze` | Opcional, **antes** de `/speckit-implement`. Coherencia spec/plan/tasks |
| `/speckit-implement` | Ejecuta `tasks.md`. Único punto donde se escribe código de producto |
| `/speckit-constitution` | Enmendar las normas del proyecto |

---

## Comandos

Java **no está en el `PATH`**; usa el JBR que trae Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

| Tarea | Comando |
|---|---|
| Compilar debug | `./gradlew :app:assembleDebug` |
| Tests unitarios + integración | `./gradlew :app:testDebugUnitTest` |
| Tests de UI (requiere emulador) | `./gradlew :app:connectedDebugAndroidTest` |
| Lint | `./gradlew :app:lintDebug` |
| Un solo test | `./gradlew :app:testDebugUnitTest --tests "*HomeViewModelTest*"` |
| Instalar en dispositivo | `./gradlew :app:installDebug` |
| Limpiar | `./gradlew clean` |

Informes de tests: `app/build/reports/tests/testDebugUnitTest/index.html`.
Informe de lint: `app/build/reports/lint-results-debug.html`.

---

## Arquitectura

Arquitectura limpia + MVVM, módulo único `:app`, separación por paquetes bajo
`com.jrblanco.boccantabria`:

```
core/
  di/         Módulos Koin (coreModule, dataModule, domainModule, uiModule)
              y appModules, único punto de entrada del grafo
  telemetry/  Contratos AnalyticsTracker y CrashReporter + AnalyticsEvent
  ui/theme/       Sistema de diseño: Color, Type, Spacing, Shape, Elevation, Theme
  ui/component/   Componibles compartidos sin estado
  util/       DispatcherProvider, AppVersionProvider y utilidades transversales
data/
  repository/     Implementaciones de las interfaces de domain
  source/local/   Fuentes locales (+ entidades)
  source/remote/  Fuentes remotas (+ DTOs)
  telemetry/      Implementaciones de Firebase. ÚNICO sitio que toca el SDK
domain/
  model/          Modelos de dominio, Kotlin puro (AppResult, DomainError, ContentItem)
  repository/     Interfaces de repositorio (contratos)
  usecase/        Casos de uso, una operación por clase
ui/
  splash/         Arranque: SplashScreen + SplashViewModel + SplashUiState
  home/           Pantalla: HomeScreen + HomeViewModel + HomeUiState
  navigation/     Rutas tipadas y NavHost (el arranque es el destino inicial)
BOCantabriaApp    Application: arranca Koin
MainActivity      Anfitrión de la navegación Compose
```

**Regla de dependencias**: `ui → domain ← data`. Siempre hacia dentro.

- `domain` es Kotlin puro: **cero** `import android.*`, cero Compose, cero Firebase, cero
  referencias a `data` o `ui`.
- `data` implementa lo que `domain` declara. Los DTOs/entidades de `data` **no** cruzan a
  `ui`: se mapean a modelos de `domain`.
- `ui` solo habla con casos de uso. Un `ViewModel` **nunca** importa nada de `data`.
- `core` es transversal y no contiene lógica de negocio.

### Flujo de una operación

```
Composable → ViewModel → UseCase → Repository (interfaz en domain)
                                        ↓
                          RepositoryImpl (data) → DataSource
```

---

## Convenciones

### Presentación (MVVM)

- Una pantalla = `XxxScreen.kt` + `XxxViewModel.kt` + `XxxUiState.kt`.
- `MutableStateFlow` **privado**; se expone `StateFlow<XxxUiState>` de solo lectura.
- `UiState` inmutable (`data class` o `sealed interface`). Eventos = funciones públicas del
  `ViewModel`.
- Composables tontos: renderizan estado y emiten eventos. Cero lógica de negocio en un
  `@Composable`.
- Componentes reutilizables **stateless**, con *state hoisting*.

### Inyección de dependencias

- Todo el grafo en `core/di`. Nunca instanciar dependencias a mano.
- `ViewModel`s vía `koinViewModel()` en Compose.
- Al añadir una dependencia, actualiza su módulo Koin **y** el test de verificación del grafo.

### Corrutinas

- Los `Dispatchers` se **inyectan** (`DispatcherProvider`), nunca se referencian
  estáticamente. Es lo que hace deterministas los tests.

### Sistema de diseño

- **Nunca escribas un color, un tamaño o un espaciado literal.** Los tokens con equivalente en
  Material 3 se consumen por `MaterialTheme`; los propios (`textMuted`, `surfaceSoft`, `aiAccent`,
  los de sección…), por `BocTheme.colors`. También `BocTheme.spacing` y `BocTheme.elevation`.
- Hay una regla de Konsist que **falla la build** si un fichero fuera de `core/ui/theme` importa
  `androidx.compose.ui.graphics.Color`.
- `BOCantabriaTheme` **no** tiene parámetro de color dinámico, y no debe tenerlo: el azul
  institucional no cambia entre pantallas ni entre dispositivos.
- El único color declarado en XML es el fondo del arranque del sistema, en `colors.xml`, porque se
  configura antes de que Compose exista. Debe mantenerse sincronizado con `BocPrimary`.
- Los pesos 650 del documento se implementan como `SemiBold` (600), el peso real más cercano.

### Resultados y errores

- Las operaciones de dominio devuelven `AppResult<T>` (`Success` / `Failure`), **no**
  `kotlin.Result`: el error viaja como `DomainError` sellado, así el `when` de la pantalla es
  exhaustivo y el compilador avisa al añadir un caso.
- Una lista vacía es `Success(emptyList())`, no un fallo. «Vacío» y «error» se distinguen en la
  capa de presentación.
- Las excepciones **no** salen de `data`: se capturan y se traducen ahí. `CancellationException`
  se repropaga siempre.

### Dependencias Gradle

- **Todas** en `gradle/libs.versions.toml`. Nunca una coordenada o versión literal dentro de
  un `build.gradle.kts`.
- Familias con BOM (Compose, Firebase, Koin): sus artefactos van **sin versión**.

### Firebase

- Los SDK solo se tocan desde `data`. Nunca desde `ui`, `domain` ni un `ViewModel`.
- Se usan a través de abstracciones propias (`AnalyticsTracker`, `CrashReporter`) inyectadas
  por Koin, para poder sustituirlas por dobles en test.
- Nunca registres datos personales identificables en eventos ni en trazas.

### Nombres e idioma

- Código, nombres y comentarios en **inglés**.
- Specs, documentación, mensajes de commit y comunicación con el propietario, en **español**.
- Interfaz `XxxRepository` en `domain`, implementación `XxxRepositoryImpl` en `data`.
- Casos de uso en imperativo: `GetBulletinsUseCase`, con un único `operator fun invoke()`.

---

## Testing

Ninguna tarea se da por terminada sin su test en verde. **Prohibido** `@Ignore`, comentar o
borrar un test para que pase la build.

| Tipo | Ubicación | Herramientas |
|---|---|---|
| Unitario | `app/src/test` | JUnit 4, MockK, `kotlinx-coroutines-test`, Turbine |
| Integración | `app/src/test` | Koin `verify()`/`checkModules()`, grafo real con dobles en la frontera |
| UI | `app/src/androidTest` | `createAndroidComposeRule`, Koin con módulos de test |
| Sin emulador con contexto Android | `app/src/test` | Robolectric |

- ViewModels: `runTest` + Turbine observando el `StateFlow`.
- Todo bug corregido lleva un test de regresión que falla **antes** del arreglo.
- Tests deterministas: sin red real, sin reloj del sistema, sin depender del orden.

**Reglas de arquitectura** (`ArchitectureRulesTest`, Konsist): seis reglas hacen cumplir la
separación de capas y exigen que toda clase de dominio de nivel superior y todo `ViewModel`
tenga su fichero de prueba. Si añades una clase de dominio sin test, la build falla.

**Trampas conocidas al escribir tests** — estas costaron tiempo, no las repitas:

- Los tests instrumentados **comparten proceso** y el grafo es de `single`. Una caché o un
  repositorio ya resuelto se filtran de una prueba a la siguiente. Usa `testGraphOverrides()`
  (androidTest), que reconstruye la cadena entera por prueba.
- Declarar un módulo dos veces dentro del mismo `koinApplication { }` **no** sustituye nada.
  Para sustituir, `koin.loadModules(listOf(...), allowOverride = true)`.
- `ActivityScenario.recreate()` **conserva el ViewModel** por diseño. No sirve para forzar una
  recarga; para eso, haz que el origen falle desde el arranque.
- En Robolectric usa `@Config(application = Application::class)`: la `BOCantabriaApp` real
  arranca el Koin global, que sobrevive entre tests del mismo JVM y hace fallar al segundo.
- Robolectric aún no tiene descriptor para la API 37: los tests usan `@Config(sdk = [36])`.
- `unloadKoinModules` **elimina** las definiciones, no restaura las que tapaba. `KoinOverrideRule`
  recarga `appModules` al terminar; si escribes otra regla que cargue módulos, haz lo mismo o
  dejarás agujeros en el grafo para las clases de prueba siguientes.
- Toda pantalla queda detrás del arranque, así que una prueba instrumentada de cualquier pantalla
  pasa por él. Usa `testGraphOverrides()`, que ya sustituye la cadena de arranque por dobles y
  mantiene la prueba fuera de la red.
- Firebase (Analytics, Crashlytics, Remote Config) necesita un `FirebaseApp` real: bajo Robolectric
  hay que sustituirlo por dobles.
- Un `Image` con solo `height` ajusta al ancho intrínseco del vector y la altura pedida no se
  aplica. Fija también `aspectRatio`.
- El splash del sistema recorta el icono a un círculo: el escudo debe ir inscrito en la zona segura
  (192 dp dentro de un lienzo de 288). Para eso existe `ic_splash_emblem`.

---

## Git

- `main` es la rama estable. Cada feature vive en su rama `NNN-slug` creada por Spec Kit.
  **Nunca** implementes una feature directamente sobre `main`.
- Commits en español, imperativo, con prefijo Conventional Commits (`feat:`, `fix:`, `test:`,
  `refactor:`, `chore:`, `docs:`).
- Remoto: `https://github.com/jrcosio/BOCantabria_v2.git`.

Antes de dar una feature por terminada, en este orden:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

---

## Notas del proyecto

- **Orientación**: la aplicación está **bloqueada en vertical** por decisión de producto. En
  pantallas de 600 dp o más Android ignora la restricción desde la API 36 y no se intentará
  sortearlo. Los dos avisos de lint correspondientes están suprimidos a conciencia en el manifest.
- **Documentación de diseño**: `docs/diseno/` contiene las especificaciones visuales y la imagen de
  referencia del arranque. Es la fuente de verdad de la interfaz; si cambias algo acordado, actualiza
  también el documento.
- **Package**: `com.jrblanco.boccantabria` (con doble «c»). Es intencionado: el
  `google-services.json` está registrado con ese package exacto en el proyecto Firebase
  `bocantabria-6e90f`. **No lo renombres** sin registrar antes una app nueva en la consola de
  Firebase.
- `Datos_modelo/` contiene material de referencia y **no se versiona**.
- AGP 9.x aplica Kotlin de forma integrada: no existe ni hace falta el plugin `kotlin-android`.
