# Specification Quality Checklist: Buscar

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-31
**Feature**: [spec.md](../spec.md)

## Content Quality

- [X] No implementation details (languages, frameworks, APIs)
- [X] Focused on user value and business needs
- [X] Written for non-technical stakeholders
- [X] All mandatory sections completed

## Requirement Completeness

- [X] No [NEEDS CLARIFICATION] markers remain
- [X] Requirements are testable and unambiguous
- [X] Success criteria are measurable
- [X] Success criteria are technology-agnostic (no implementation details)
- [X] All acceptance scenarios are defined
- [X] Edge cases are identified
- [X] Scope is clearly bounded
- [X] Dependencies and assumptions identified

## Feature Readiness

- [X] All functional requirements have clear acceptance criteria
- [X] User scenarios cover primary flows
- [X] Feature meets measurable outcomes defined in Success Criteria
- [X] No implementation details leak into specification

## Notes

Revisión realizada el 2026-08-31, una sola iteración. Comprobaciones que merecían mirarse dos veces:

- **«Sin detalles de implementación»**: la especificación no nombra ni la base de datos, ni el
  cliente HTTP, ni el marco de interfaz. Donde el texto de entrada del propietario decía
  `LIKE '%texto%'`, el requisito FR-004 lo expresa como «el equivalente a buscar esa secuencia en
  cualquier posición». La cita literal de esa entrada se conserva en la cabecera **Input**, que es
  precisamente para eso.
- **«Texto buscable de una publicación»** aparece en *Key Entities* porque el requisito FR-027 —que
  se encuentren las publicaciones descargadas antes de instalar esta versión— no se entiende sin
  ella. Está descrita como lo que es para quien lee, sin nombrar columna ni almacén.
- **Cero marcadores `[NEEDS CLARIFICATION]`**: las cuatro ambigüedades reales (troceado, municipio,
  relevancia y forma de los filtros) se cerraron con el propietario **antes** de redactar, y quedan
  recogidas en la cabecera de la especificación.
- **Dos decisiones que la especificación toma por su cuenta** y que declara como tales en
  *Assumptions*, por si el uso las desmiente: el mínimo de dos caracteres para la búsqueda global y
  el tope de 300 resultados.
- **Lo que se deja fuera está escrito en voz alta**, con su motivo, en el apartado *Fuera de
  alcance*: municipio, relevancia, resaltado de coincidencias, búsquedas recientes, tarjeta
  compacta y selector de fecha. Un olvido y una decisión no se distinguen si no se dice cuál es.
