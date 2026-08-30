# Specification Quality Checklist: Detalle de publicación y visor del PDF oficial

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

- **Verificación de fuga técnica**: comprobado por búsqueda. En el cuerpo no aparece ningún marco,
  biblioteca ni API. Se habla de «documento portátil», «canal seguro», «servicio del boletín» y
  «suma de verificación» en lugar de nombrar productos o algoritmos. La elección del visor y la del
  mecanismo de descarga son precisamente lo que corresponde decidir y justificar en `plan.md`.

- **Tres menciones que sí quedan, y por qué**:
  - `PDF` (FR-013, entidades, historia 1) es **microcopy del documento de diseño** —el apartado 32
    fija literalmente `Abrir PDF oficial`— y el nombre con el que la persona conoce la cosa. No es
    una elección técnica.
  - `48 × 48 dp` (FR-043) es el mínimo de área táctil del apartado 31.2 del documento de diseño, que
    es norma de accesibilidad del proyecto, no detalle de implementación.
  - **Android, 24 y 28** (FR-039) son alcance de producto: dicen qué dispositivos deja de soportar
    la aplicación. Es una decisión de negocio con consecuencias visibles, y además una enmienda de
    las normas del proyecto que SC-012 exige registrar. Ocultarla tras un circunloquio habría hecho
    el requisito menos verificable, no más limpio.

- **Dos historias con prioridad P1**: la historia 2 no es un caso degradado de la 1. Decide si lo
  que la persona lee es el documento oficial o cualquier otra cosa que el servicio haya devuelto ese
  día con un código 200. En una aplicación que consulta un boletín oficial, esa distinción es tan
  importante como poder leerlo.

- **Ningún `[NEEDS CLARIFICATION]`**: las tres decisiones ambiguas —qué visor y a qué coste, qué
  contiene la pestaña de documento, y qué compartir cuando no hay documento— se plantearon al
  propietario **antes** de redactar. Quedan en *Assumptions* y en FR-013, FR-026, FR-031 y FR-033.

- **FR-024 marca un límite que es fácil de cruzar sin querer**: lo descargado es caché y no puede
  presentarse como una biblioteca de documentos guardados. Guardar para leer sin conexión es la
  funcionalidad de Guardados, que es futura, y esta feature no debe adelantarla ni a medias.

- **FR-040 existe para que la enmienda no deje restos**: subir la versión mínima vuelve innecesario
  un mecanismo de compatibilidad que la anterior obligaba a mantener. Sin este requisito se quedaría
  ahí por inercia, y nadie sabría dentro de un año por qué.

- **FR-017 y SC-004 son el corazón de la historia 2**: no basta con que el servicio *diga* que
  devuelve un documento; hay que comprobar que lo que llega lo es. SC-004 lo verifica con respuestas
  fabricadas a propósito para engañar.

- **Riesgo anotado, no oculto**: la feature depende de que exista un visor que funcione bien dentro
  del marco de interfaz del proyecto. Si al implementarlo resultara que no sirve, la enmienda de la
  versión mínima se quedaría sin su motivo y habría que revertirla o justificarla por sí sola. Por
  eso el plan debe poner esa comprobación al principio.

- Resultado de la validación: **todos los ítems pasan en la primera iteración**.
