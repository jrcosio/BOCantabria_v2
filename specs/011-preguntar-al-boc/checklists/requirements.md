# Specification Quality Checklist: Preguntar al BOC

**Purpose**: Validar que la especificación está completa y es de calidad antes de planificar
**Created**: 5 de septiembre de 2026
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

## Notas de la validación

Tres cosas que esta lista da por buenas y conviene que consten, porque un revisor podría leerlas
como incumplimientos:

- **«Respuesta estructurada con fuentes» no es un detalle de implementación.** Describe lo que se ve
  en pantalla —un texto y debajo las páginas en las que se apoya— y de ahí salen FR-012 a FR-015.
  Cómo se consigue esa forma es asunto del `plan.md`.
- **FR-020 y FR-021 hablan de una «declaración de ámbito» que suena a mecanismo, y lo es a
  propósito.** Es la única parte de la defensa antiinyección que se puede comprobar sin cruzar la
  frontera con el servicio, y por eso está escrita como requisito y no como aspiración. Sin ella,
  la User Story 2 no tendría ninguna prueba automática posible.
- **SC-009 no se puede automatizar y lo dice.** Es una comprobación manual contra el servicio real.
  Se deja como criterio de éxito porque el criterio existe aunque la prueba sea a mano; ocultarlo
  sería fingir que la feature está más cubierta de lo que está.

Una limitación admitida en la propia especificación: que el modelo se mantenga dentro del documento
es **mitigación y no garantía**. Está dicho en el contexto imprescindible y sostenido por FR-021,
que es lo que convierte una esperanza en un mecanismo observable.
