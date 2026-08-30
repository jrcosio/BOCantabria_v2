# Specification Quality Checklist: Publicaciones guardadas

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-30
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **Verificación de fuga técnica**: comprobado por búsqueda. No aparece ningún marco, biblioteca,
  base de datos, tabla, columna ni API. Se habla de «lo almacenado», «la marca», «una
  sincronización» y «la lista» en lugar de nombrar mecanismos. Dónde vive la marca y cómo se
  actualiza el almacén es exactamente lo que corresponde decidir y justificar en `plan.md`.

- **Tres menciones que sí quedan, y por qué**:
  - **«caché»** (FR-024 y un caso límite) es vocabulario que la feature 004 ya fijó en su FR-024 y
    es lo que hace verificable el límite de esta: guardar marca, no descarga. Ocultarlo tras un
    circunloquio habría hecho el requisito menos comprobable, no más limpio.
  - **«muerte del proceso»** (FR-019, historia 3) es comportamiento del sistema que la persona
    percibe —la aplicación se cierra sola y al volver está donde estaba— y la condición exacta de la
    prueba. No es una elección de implementación.
  - **«actualización de la aplicación sobre una instalación anterior»** (FR-023) es alcance de
    producto: dice qué le pasa a quien ya tiene la aplicación instalada. Es el requisito más fácil de
    incumplir sin darse cuenta y el que peores consecuencias tiene.

- **Tres historias con prioridad P1**. La 2 y la 3 no son casos degradados de la 1:
  - La **2** decide si el estado es de fiar. Un marcador que solo enciende no es un interruptor, y un
    estado que se muestra distinto en dos pantallas destruye la confianza en la lista entera, no solo
    en esa tarjeta.
  - La **3** decide si la lista sirve para algo. Una lista de guardados que se vacía sola es peor que
    no tenerla: la persona confía y pierde el anuncio. Extiende a las marcas la regla que el proyecto
    ya tiene para las publicaciones (SC-005 de la feature 003).
  - Se acepta que la 2 necesita que la 1 exista: no se puede desmarcar lo que no se puede marcar.
    Son dos mitades de un mismo interruptor y se entregan juntas.

- **Ningún `[NEEDS CLARIFICATION]`**: las cuatro decisiones ambiguas —alcance respecto a la lectura
  sin conexión, orden de la lista, tarjeta estándar o compacta, y si desmarcar ofrece deshacer— se
  plantearon al propietario **antes** de redactar. Sus respuestas están en el encabezado de *Input* y
  en *Assumptions*.

- **FR-024 es el requisito incómodo de esta feature, y está a propósito**: la guía operativa y la
  decisión D-003 de la feature 004 prometieron por escrito que Guardados sería *también* la
  funcionalidad de leer sin conexión. Esta feature no lo cumple. Decirlo en un requisito, y no solo
  en una nota al pie, es lo que impide que dentro de seis meses alguien lo lea como un olvido y lo
  «arregle» sin decidirlo.

- **FR-020, FR-021 y FR-022 son el corazón de la historia 3**, y los tres son negativos: dicen lo que
  el sistema **no** debe hacer. Es deliberado. La regla del proyecto de que nunca se borra una
  publicación se sostiene porque la sentencia de borrado no existe, no porque nadie la llame; estos
  tres requisitos piden la misma clase de garantía para la marca.

- **FR-025 no tiene escenario de aceptación** porque no es un flujo: es una restricción de privacidad
  sobre lo que la aplicación puede registrar. Se comprueba leyendo lo que se envía, no recorriendo la
  interfaz. Deriva del principio VI de la constitución.

- **SC-007 fija un número (doscientas) en lugar de decir «muchas»**: sin cifra no habría forma de
  saber si se cumple, y doscientas es un orden de magnitud realista para alguien que guarda un par de
  anuncios al día durante unos meses.

- **SC-006 es el único criterio que no se puede verificar del todo de forma mecánica.** La
  actualización sobre una instalación real solo aparece en un dispositivo que ya tenía datos, así que
  lleva prueba automática del almacén **y** una comprobación a mano. Queda dicho en el propio
  criterio en lugar de darlo por cubierto.
