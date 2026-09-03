# Implementation Plan: Pantalla «Acerca de»

**Branch**: `008-acerca-de` | **Date**: 3 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-acerca-de/spec.md`

## Summary

El botón Información abre una pantalla secundaria, desplazable y fiel a la referencia del
propietario. La UI es stateless; un modelo de pantalla mínimo proporciona la versión instalada,
telemetría y el aviso de fallo. Android resuelve los dos enlaces HTTPS fuera de la aplicación.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11, AGP 9.3.2.

**Primary Dependencies**: Compose Material 3, Navigation Compose, Koin y Firebase Analytics ya
presentes. Ninguna dependencia nueva.

**Storage**: N/A. Sin Room, preferencias ni migraciones.

**Testing**: JUnit 4, MockK, Compose UI Test, Koin y Konsist.

**Target Platform**: Android `minSdk 28`, `compileSdk`/`targetSdk` 37; vertical y tema claro único.

**Performance Goals**: primera composición sin trabajo bloqueante; fotografía empaquetada por
debajo de 150 KB; apertura del enlace entregada al sistema en menos de 500 ms.

**Constraints**: textos exactos de la referencia, URL exactas, pantalla accesible con fuente grande,
sin WebView, red propia, permisos ni SDK adicional.

**Scale/Scope**: una pantalla, una ruta, un modelo de pantalla, siete vectores y una fotografía.

## Constitution Check

| Principio | Cumplimiento | Puerta |
|---|---|---|
| I. SDD | Rama y artefactos 008 antes del producto; el propietario aprobó el plan | ✅ |
| II. Capas | La feature solo toca `core` transversal y `ui`; no añade lógica de dominio ni data | ✅ |
| III. MVVM | `InfoUiState`, StateFlow privado/expuesto y contenido stateless | ✅ |
| IV. Koin | `InfoViewModel` se declara en `uiModule`; versión inyectada | ✅ |
| V. Testing | Pruebas del modelo, contenido y navegación antes de cerrar tareas | ✅ |
| VI. Observabilidad | Solo se usa `AnalyticsTracker`; destino enumerado y sin PII | ✅ |
| Restricciones | Compose, Material 3, Flow y catálogo sin dependencias nuevas | ✅ |

La re-evaluación tras el diseño también pasa: no hay desviaciones ni complejidad que justificar.

## Project Structure

```text
specs/008-acerca-de/                 artefactos completos de la feature
app/src/main/java/.../ui/info/       pantalla, estado y modelo de pantalla
app/src/main/res/                    fotografía, vectores y textos
app/src/test/.../ui/info/            prueba del modelo
app/src/androidTest/.../ui/          composición y navegación
```

**Structure Decision**: se mantiene el módulo `:app`. `Route.Info` vive en el grafo exterior; el
contenido visual vive en `ui/info`; la versión amplía la frontera transversal que ya existe.

## Complexity Tracking

No hay violaciones constitucionales.
