# Tasks: Pantalla «Acerca de»

**Input**: Design documents from `/specs/008-acerca-de/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: obligatorios por la constitución; se escriben antes del código correspondiente.

## Phase 1: Setup

- [X] T001 Optimizar `Datos_modelo/yoandroid.png` como `app/src/main/res/drawable-nodpi/about_author.jpg`
- [X] T002 [P] Añadir textos y vectores de la pantalla en `app/src/main/res/`

## Phase 2: Foundational

- [X] T003 [P] Ampliar y probar `AppVersionProvider` con `versionName` en `app/src/main/java/com/jrblanco/boccantabria/core/util/` y sus dobles de test
- [X] T004 [P] Crear el contrato `InfoUiState`/`InfoLink` en `app/src/main/java/com/jrblanco/boccantabria/ui/info/`
- [X] T005 Registrar `InfoViewModel` en `app/src/main/java/com/jrblanco/boccantabria/core/di/UiModule.kt` y actualizar la prueba del grafo

## Phase 3: User Story 1 — Conocer la aplicación y a su autor (P1) 🎯 MVP

**Independent Test**: Información abre la pantalla completa, se alcanza todo el contenido y Atrás
regresa a Inicio.

- [X] T006 [P] [US1] Escribir `InfoViewModelTest` en `app/src/test/java/com/jrblanco/boccantabria/ui/info/InfoViewModelTest.kt`
- [X] T007 [P] [US1] Escribir las pruebas de composición en `app/src/androidTest/java/com/jrblanco/boccantabria/ui/info/InfoScreenTest.kt`
- [X] T008 [US1] Implementar `InfoViewModel` en `app/src/main/java/com/jrblanco/boccantabria/ui/info/InfoViewModel.kt`
- [X] T009 [US1] Implementar la pantalla desplazable en `app/src/main/java/com/jrblanco/boccantabria/ui/info/InfoScreen.kt`
- [X] T010 [US1] Añadir `Route.Info` y conectar Inicio con el grafo exterior en `ui/navigation/BOCantabriaNavHost.kt`, `ui/navigation/Routes.kt` y `ui/main/MainShell.kt`
- [X] T011 [US1] Crear `InfoNavigationTest` para comprobar apertura, ausencia de barra inferior y Atrás

## Phase 4: User Story 2 — Abrir los perfiles externos (P2)

**Independent Test**: cada botón entrega exactamente su URL y el fallo muestra un aviso sin salir.

- [X] T012 [P] [US2] Añadir pruebas de callbacks y fallo de enlace a `app/src/androidTest/java/com/jrblanco/boccantabria/ui/info/InfoScreenTest.kt`
- [X] T013 [US2] Conectar `InfoLink` con el manejador de URI, telemetría y Snackbar en `app/src/main/java/com/jrblanco/boccantabria/ui/info/`

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T014 [P] Enmendar «Acerca de» en `docs/diseno/especificaciones-diseno.md`
- [X] T015 [P] Actualizar arquitectura y decisiones en `CLAUDE.md`
- [X] T016 [P] Actualizar el estado y las referencias obsoletas afectadas en `README.md`
- [X] T017 Ejecutar la validación manual de `specs/008-acerca-de/quickstart.md` hasta donde permita el dispositivo disponible
- [X] T018 Ejecutar `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` y `lintDebug` en ese orden

## Dependencies & Execution Order

- Setup precede a la composición; Foundational precede a las dos historias.
- US1 proporciona pantalla y navegación; US2 añade comportamiento a sus acciones.
- Las pruebas de cada historia se escriben antes de su implementación.
- Polish empieza cuando las dos historias están completas.

## Implementation Strategy

El MVP es US1. Después se conectan ambos enlaces como un incremento aislado y se cierra con
documentación y las cuatro puertas de calidad. Las 18 tareas son específicas y ejecutables sin
decisiones pendientes.
