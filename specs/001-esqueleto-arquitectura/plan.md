# Implementation Plan: Esqueleto de arquitectura de la aplicación

**Branch**: `001-esqueleto-arquitectura` | **Date**: 2026-08-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-esqueleto-arquitectura/spec.md`

## Summary

Montar el andamiaje completo de la aplicación: la estructura de capas de la constitución, el
grafo de dependencias en Koin, la telemetría envuelta en abstracciones propias y una rebanada
vertical funcional (pantalla inicial) que recorre `ui → domain ← data` de extremo a extremo,
todo ello cubierto por pruebas unitarias, de integración, de interfaz y de arquitectura.

El enfoque técnico es deliberadamente conservador en dependencias: se añaden solo dos
(serialización de Kotlin, para rutas de navegación tipadas, y Konsist, para hacer cumplir la
regla de capas), y el origen de datos es en memoria por acuerdo explícito con el propietario.
Lo que se está construyendo no es una funcionalidad: es el molde del que saldrán todas las
demás, y el conjunto de pruebas que impide que ese molde se deforme.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (aplicado de forma integrada por AGP 9.3.2)

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01) con Material 3, Navigation Compose
2.10.0, Koin 4.2.2, Firebase BOM 34.18.0 (Analytics y Crashlytics), corrutinas 1.11.0. Se
añaden en esta feature: plugin de serialización de Kotlin 2.2.10 y Konsist 0.17.3 (solo test)

**Storage**: N/A en esta feature. Fuentes en memoria por decisión registrada (`research.md`,
D-001); la persistencia real se decidirá en la primera feature de negocio

**Testing**: JUnit 4, MockK 1.14.11, Turbine 1.2.1, `kotlinx-coroutines-test` 1.11.0,
Robolectric 4.16.1, `koin-test`, Compose UI Test, Konsist 0.17.3

**Target Platform**: Android, `minSdk 24`, `compileSdk`/`targetSdk` 37

**Project Type**: Aplicación móvil Android, módulo Gradle único (`:app`) con separación por
paquetes

**Performance Goals**: pantalla inicial visible en menos de 2 s en gama media (SC-001); suite de
pruebas sin dispositivo por debajo de 2 min (SC-003)

**Constraints**: `domain` sin dependencias de plataforma ni de proveedores; pruebas
deterministas sin red real; sin información personal identificable en telemetría

**Scale/Scope**: 1 pantalla, ~25 ficheros de producción y ~12 de prueba. Es andamiaje: su
tamaño es pequeño a propósito y su valor está en las restricciones que impone

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | La feature recorre el ciclo completo. No se escribe código de producto hasta tener `tasks.md`. La rama `001-esqueleto-arquitectura` la creó la extensión git de Spec Kit | ✅ |
| **II. Arquitectura limpia** | Paquetes `core`/`data`/`domain`/`ui` según el diagrama de referencia. `domain` es Kotlin puro. Los DTO y entidades se traducen y no cruzan a `ui`. **Se hace cumplir automáticamente** con reglas de Konsist (D-006), no por convención | ✅ |
| **III. MVVM** | Una pantalla = `HomeScreen` + `HomeViewModel` + `HomeUiState`. Estado sellado e inmutable expuesto como `StateFlow` de solo lectura; `MutableStateFlow` privado. `HomeContent` es un componible sin estado | ✅ |
| **IV. Koin** | Todo el grafo en `core/di`, con `appModules` como único punto de entrada. Ninguna construcción manual. Prueba de verificación del grafo (D-008) | ✅ |
| **V. Testing exigente** | Las tres capas de la pirámide más reglas de arquitectura. Cada requisito funcional tiene su prueba; el mapa está en `quickstart.md`. Se eliminan las plantillas de prueba de ejemplo del proyecto generado | ✅ |
| **VI. Observabilidad desacoplada** | `AnalyticsTracker` y `CrashReporter` en `core/telemetry`; los SDK de Firebase solo se tocan en `data/telemetry`. Regla de Konsist que lo impone. Filtrado de claves sensibles con prueba | ✅ |
| **Restricciones tecnológicas** | Todas las dependencias en el catálogo de versiones; familias con BOM sin versión en sus artefactos. Nada de XML de layouts ni Fragments. Corrutinas y `Flow`. Código en inglés, documentación en español | ✅ |

**Resultado de la puerta previa a la fase 0**: pasa. Ninguna violación que justificar.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño no introdujo ninguna
desviación. La única decisión que se aparta de un material de referencia es la ubicación de las
abstracciones de telemetría en `core/telemetry`, que se separa del diagrama aportado por el
propietario pero **no** de la constitución (que solo exige que estén fuera de `data`). Queda
argumentada en `research.md`, D-002, y anotada en *Complexity Tracking* por transparencia.

## Project Structure

### Documentation (this feature)

```text
specs/001-esqueleto-arquitectura/
├── spec.md                        # Especificación (/speckit-specify)
├── plan.md                        # Este fichero (/speckit-plan)
├── research.md                    # Fase 0: 9 decisiones con alternativas descartadas
├── data-model.md                  # Fase 1: entidades, estados y transiciones
├── quickstart.md                  # Fase 1: guía de validación de extremo a extremo
├── contracts/
│   └── internal-contracts.md      # Fase 1: contratos entre capas
├── checklists/
│   └── requirements.md            # Checklist de calidad de la especificación
└── tasks.md                       # Fase 2 (/speckit-tasks — NO lo crea /speckit-plan)
```

### Source Code (repository root)

Módulo Gradle único con separación por paquetes, siguiendo el diagrama de referencia aportado
por el propietario:

```text
app/src/main/java/com/jrblanco/boccantabria/
├── core/
│   ├── di/
│   │   ├── CoreModule.kt              # DispatcherProvider
│   │   ├── DataModule.kt              # Fuentes, repositorios, telemetría
│   │   ├── DomainModule.kt            # Casos de uso
│   │   ├── UiModule.kt                # Modelos de pantalla
│   │   └── AppModules.kt              # val appModules: List<Module>
│   ├── telemetry/
│   │   ├── AnalyticsTracker.kt        # interfaz
│   │   ├── AnalyticsEvent.kt
│   │   └── CrashReporter.kt           # interfaz
│   ├── ui/
│   │   ├── theme/                     # Color.kt, Theme.kt, Type.kt (movidos desde ui/theme)
│   │   └── component/
│   │       ├── LoadingIndicator.kt
│   │       ├── ErrorMessage.kt
│   │       └── EmptyMessage.kt
│   └── util/
│       └── DispatcherProvider.kt
├── data/
│   ├── repository/
│   │   └── ContentRepositoryImpl.kt
│   ├── source/
│   │   ├── local/
│   │   │   ├── ContentLocalDataSource.kt
│   │   │   ├── InMemoryContentLocalDataSource.kt
│   │   │   └── ContentItemEntity.kt
│   │   └── remote/
│   │       ├── ContentRemoteDataSource.kt
│   │       ├── StubContentRemoteDataSource.kt
│   │       └── ContentItemDto.kt
│   └── telemetry/
│       ├── FirebaseAnalyticsTracker.kt
│       └── FirebaseCrashReporter.kt
├── domain/
│   ├── model/
│   │   ├── ContentItem.kt
│   │   ├── DomainError.kt
│   │   └── AppResult.kt
│   ├── repository/
│   │   └── ContentRepository.kt
│   └── usecase/
│       └── GetContentItemsUseCase.kt
├── ui/
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   ├── HomeViewModel.kt
│   │   └── HomeUiState.kt
│   └── navigation/
│       ├── Routes.kt
│       └── BOCantabriaNavHost.kt
├── BOCantabriaApp.kt                  # Application: arranca Koin
└── MainActivity.kt                    # Anfitrión de la navegación

app/src/test/java/com/jrblanco/boccantabria/
├── architecture/ArchitectureRulesTest.kt        # Konsist: regla de capas
├── data/repository/ContentRepositoryImplTest.kt
├── data/telemetry/FirebaseAnalyticsTrackerTest.kt   # Robolectric + MockK
├── domain/usecase/GetContentItemsUseCaseTest.kt
├── ui/home/HomeViewModelTest.kt                 # Turbine
├── di/KoinModulesTest.kt                        # Robolectric: verifica el grafo
├── integration/ContentFlowIntegrationTest.kt    # Recorrido completo con grafo real
└── fake/                                        # FakeContentRemoteDataSource, RecordingAnalyticsTracker,
                                                 # TestDispatcherProvider

app/src/androidTest/java/com/jrblanco/boccantabria/
├── ui/home/HomeContentTest.kt                   # Los 4 estados + reintento, sin grafo
└── ui/HomeScreenEndToEndTest.kt                 # Actividad real + Koin con módulo de prueba
```

**Structure Decision**: módulo único `:app` con separación por paquetes, exactamente como el
diagrama de referencia del propietario. Se descarta la separación en módulos Gradle por capa:
daría aislamiento estructural sin herramientas extra, pero multiplica la configuración y el
tiempo de compilación para una aplicación con una sola pantalla. La regla de capas se hace
cumplir con Konsist en su lugar (`research.md`, D-006), y la modularización se reconsiderará si
el proyecto crece. Se añaden dos subpaquetes sobre el diagrama original: `core/telemetry` (ver
D-002) y `ui/navigation`, que el diagrama no contemplaba por proceder de una aplicación de una
sola pantalla.

## Complexity Tracking

> La puerta de la constitución pasa sin violaciones. Se registran aquí, por transparencia, las
> tres decisiones que añaden algo que la constitución no exigía explícitamente.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| Dependencia nueva: Konsist (solo test) | El criterio SC-004 exige detectar el 100 % de las violaciones de capa automáticamente. Ni el compilador ni el análisis estático de Android conocen la regla `ui → domain ← data` | Revisión manual en la integración: no cumple «100 % automático» y se degrada con el tiempo. ArchUnit: trabaja sobre bytecode y modela mal los ficheros y funciones de nivel superior de Kotlin |
| Plugin nuevo: serialización de Kotlin | Las rutas de navegación tipadas de Navigation Compose lo requieren; convierten en errores de compilación fallos que de otro modo aparecen en ejecución | Rutas de texto con constantes: cero dependencias, pero traslada esos fallos al tiempo de ejecución. Si el plugin no fuera compatible con AGP 9, esta es la vuelta atrás prevista (D-005) |
| Paquete nuevo: `core/telemetry` | La constitución exige que las abstracciones de telemetría vivan fuera de `data`; necesitan un lugar propio | `core/util`: cabría en el diagrama de referencia del propietario, pero convierte `util` en un cajón de sastre. `domain`: contamina el dominio con una preocupación que no es de negocio |
