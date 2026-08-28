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
  di/       Módulos Koin (appModule, dataModule, domainModule, uiModule)
  ui/       Tema (Color, Theme, Type) y componentes Compose compartidos
  util/     Result, DispatcherProvider, extensiones transversales
data/
  repository/     Implementaciones de las interfaces de domain
  source/local/   Fuentes locales
  source/remote/  Fuentes remotas
domain/
  model/          Modelos de dominio (Kotlin puro)
  repository/     Interfaces de repositorio (contratos)
  usecase/        Casos de uso, una operación por clase
ui/               Pantallas: Screen + ViewModel + UiState por feature
BOCantabriaApp    Application: arranca Koin
MainActivity      Host de navegación Compose
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

- **Package**: `com.jrblanco.boccantabria` (con doble «c»). Es intencionado: el
  `google-services.json` está registrado con ese package exacto en el proyecto Firebase
  `bocantabria-6e90f`. **No lo renombres** sin registrar antes una app nueva en la consola de
  Firebase.
- `Datos_modelo/` contiene material de referencia y **no se versiona**.
- AGP 9.x aplica Kotlin de forma integrada: no existe ni hace falta el plugin `kotlin-android`.
