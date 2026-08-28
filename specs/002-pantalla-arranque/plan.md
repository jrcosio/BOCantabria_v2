# Implementation Plan: Pantalla de arranque y sistema de diseño institucional

**Branch**: `002-pantalla-arranque` | **Date**: 2026-08-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-pantalla-arranque/spec.md`

## Summary

Entregar la primera pantalla real de la aplicación —el arranque— y, con ella, el sistema de diseño
institucional que heredarán todas las demás.

El arranque no es decorativo: comprueba conexión, obtiene la configuración remota del servicio,
verifica que la versión instalada sigue soportada y pasa al contenido principal. Si algo falla,
ofrece reintentar o continuar sin conexión; si la versión ha quedado obsoleta, bloquea sin salida.

El sistema de diseño entra ahora porque la pantalla lo obliga: hoy la aplicación viste el tema
morado de la plantilla con color dinámico activado, de modo que cada dispositivo la pinta según el
fondo de pantalla de su dueño. Es incompatible con una publicación oficial y contradice el punto 34
del documento de diseño. Hacerlo aquí evita que la portada invente sus propios colores y que la
siguiente pantalla herede el problema.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (aplicado de forma integrada por AGP 9.3.2)

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01) con Material 3, Navigation Compose
2.10.0, Koin 4.2.2, Firebase BOM 34.18.0, corrutinas 1.11.0. Se añaden en esta feature:
`androidx.core:core-splashscreen` 1.2.0 y `com.google.firebase:firebase-config` (sin versión, la
marca el BoM)

**Storage**: N/A. La configuración remota la cachea el propio cliente de Firebase; no se introduce
persistencia propia. La decisión sobre red y base de datos sigue correspondiendo a la feature que
publique el boletín

**Testing**: JUnit 4, MockK 1.14.11, Turbine 1.2.1, `kotlinx-coroutines-test` 1.11.0 (tiempo
virtual), Robolectric 4.16.1, `koin-test`, Compose UI Test, Konsist 0.17.3

**Target Platform**: Android, `minSdk 24`, `compileSdk`/`targetSdk` 37, solo vertical en teléfonos

**Project Type**: Aplicación móvil Android, módulo Gradle único (`:app`) con separación por paquetes

**Performance Goals**: contenido principal alcanzable en menos de 3 s (SC-001); portada visible al
menos 1 s sin parpadeo (SC-002); suite sin dispositivo por debajo de 2 min

**Constraints**: `domain` sin dependencias de plataforma; pruebas deterministas con tiempo virtual,
sin esperas reales; ningún color literal fuera de los tokens; sin color dinámico

**Scale/Scope**: 1 pantalla nueva más el sistema de diseño completo. ~24 ficheros de producción y
~10 de prueba

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | La feature recorre el ciclo completo. La rama `002-pantalla-arranque` la creó la extensión git de Spec Kit. No se escribe código de producto hasta tener `tasks.md` | ✅ |
| **II. Arquitectura limpia** | `AppConfig`, `StartupStatus`, los dos contratos de repositorio y el caso de uso son Kotlin puro. `RemoteConfigValues` se traduce y no cruza a `ui`. El acceso a conectividad se modela como repositorio para que `domain` no vea Android (research.md, D-010). Las reglas de Konsist de la feature 001 lo comprueban solas | ✅ |
| **III. MVVM** | `SplashScreen` + `SplashViewModel` + `SplashUiState` sellado. Estado inmutable expuesto como `StateFlow` de solo lectura; `SplashContent` es un componible sin estado | ✅ |
| **IV. Koin** | Las siete dependencias nuevas se registran en `core/di`. El test del grafo existente fallará hasta que estén: eso es exactamente lo que debe hacer | ✅ |
| **V. Testing exigente** | Las tres capas de la pirámide más las reglas de arquitectura. El tiempo mínimo y el límite de espera se verifican con **tiempo virtual**, sin esperas reales, que es lo que exige el principio de determinismo | ✅ |
| **VI. Observabilidad desacoplada** | El arranque registra su evento de pantalla y reporta los fallos a través de `AnalyticsTracker` y `CrashReporter`, ya existentes. Ningún SDK nuevo fuera de `data` | ✅ |
| **Restricciones tecnológicas** | Dependencias nuevas en el catálogo de versiones; Firebase sin versión por el BoM. Sin XML de layouts ni Fragments. Corrutinas y `Flow`. Código en inglés, documentación en español | ✅ |

**Resultado de la puerta previa a la fase 0**: pasa. Ninguna violación que justificar.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño no introdujo desviaciones. Las
tres decisiones que añaden algo no exigido explícitamente quedan en *Complexity Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/002-pantalla-arranque/
├── spec.md                        # 27 requisitos, 8 criterios de éxito
├── plan.md                        # Este fichero
├── research.md                    # Fase 0: 12 decisiones con alternativas descartadas
├── data-model.md                  # Fase 1: entidades, precedencia y transiciones
├── quickstart.md                  # Fase 1: validación de extremo a extremo
├── contracts/
│   └── internal-contracts.md      # Fase 1: contratos entre capas y contrato visual
├── checklists/
│   └── requirements.md            # Checklist de calidad de la especificación
└── tasks.md                       # Fase 2 (/speckit-tasks — NO lo crea /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── core/
│   ├── di/                            # Se amplían Data, Domain, Ui y Core
│   ├── ui/theme/
│   │   ├── Color.kt                   # REESCRITO: 22 tokens claros + 10 oscuros + 5 de sección
│   │   ├── Theme.kt                   # REESCRITO: esquemas, BocExtendedColors, sin color dinámico
│   │   ├── Type.kt                    # REESCRITO: los 14 estilos del documento
│   │   ├── Spacing.kt                 # NUEVO
│   │   ├── Shape.kt                   # NUEVO
│   │   └── Elevation.kt               # NUEVO
│   └── util/AppVersionProvider.kt     # NUEVO
├── data/
│   ├── repository/
│   │   ├── AppConfigRepositoryImpl.kt
│   │   └── ConnectivityRepositoryImpl.kt
│   └── source/
│       ├── local/ConnectivityDataSource.kt · AndroidConnectivityDataSource.kt
│       └── remote/RemoteConfigDataSource.kt · FirebaseRemoteConfigDataSource.kt · RemoteConfigValues.kt
├── domain/
│   ├── model/AppConfig.kt · StartupStatus.kt
│   ├── repository/AppConfigRepository.kt · ConnectivityRepository.kt
│   └── usecase/PrepareStartupUseCase.kt
├── ui/
│   ├── splash/SplashScreen.kt · SplashViewModel.kt · SplashUiState.kt
│   └── navigation/Routes.kt · BOCantabriaNavHost.kt   # MODIFICADOS
└── MainActivity.kt                    # MODIFICADO: instala el arranque del sistema

app/src/main/res/
├── drawable/ic_escudo_cantabria.xml   # NUEVO: recurso oficial aportado
├── values/themes.xml                  # REESCRITO: tema con arranque del sistema configurado
├── values/strings.xml                 # Textos de la portada
└── xml/remote_config_defaults.xml     # NUEVO

app/src/test/java/com/jrblanco/boccantabria/
├── domain/usecase/PrepareStartupUseCaseTest.kt
├── data/repository/AppConfigRepositoryImplTest.kt
├── ui/splash/SplashViewModelTest.kt
└── fake/  # Se amplía: FakeAppConfigRepository, FakeConnectivityRepository, FixedAppVersionProvider

app/src/androidTest/java/com/jrblanco/boccantabria/
├── ui/splash/SplashContentTest.kt     # Los 4 estados + acciones, sin grafo
└── ui/SplashNavigationTest.kt         # Llega a Home y el retroceso cierra la app

docs/diseno/                            # NUEVO: documento de diseño e imagen de referencia versionados
```

**Structure Decision**: se mantiene el módulo único `:app` con separación por paquetes de la feature
001. Las piezas nuevas caen en los paquetes que ya existen sin inventar ninguno: la portada es una
pantalla más en `ui/`, la configuración remota una fuente más en `data/source/remote`, y la
conectividad una fuente local, porque es información que el dispositivo provee. El sistema de diseño
amplía `core/ui/theme`, que es donde ya vivía el tema.

## Complexity Tracking

> La puerta de la constitución pasa sin violaciones. Se registran aquí, por transparencia, las
> decisiones que añaden algo que la constitución no exigía explícitamente.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| Dependencia nueva: `core-splashscreen` | FR-002 exige una transición sin destellos. Sin configurar el arranque del sistema, Android lo pinta con el fondo del tema —hoy blanco— y se ve un parpadeo blanco → azul en **cada** apertura | Solo pantalla de Compose: el destello permanece y es lo primero que ve el usuario. Configurar el tema sin la biblioteca: obligaría a dos comportamientos distintos según la versión de Android, con `minSdk 24` |
| Dependencia nueva: `firebase-config` | Es lo que permite que el arranque haga trabajo útil hoy —versión mínima y mensaje de mantenimiento— sin esperar al backend del BOC. Va dentro del BoM ya presente, así que no añade gestión de versiones | Constantes compiladas en la aplicación: cambiar la versión mínima soportada exigiría publicar una versión nueva, que es justo lo que no puede hacerse cuando hay que bloquear una versión rota |
| Contenedor propio `BocExtendedColors` | Diez tokens del documento no tienen rol equivalente en Material 3. Meterlos en roles que significan otra cosa hace que el código mienta en el punto de uso | Estirar los roles de Material 3: no hay huecos suficientes. Constantes sueltas: pierden el cambio automático entre claro y oscuro |
