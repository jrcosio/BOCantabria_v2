# Specification Quality Checklist: Avisos

**Purpose**: Validar que la especificación está completa y es de calidad antes de planificar
**Created**: 6 de septiembre de 2026
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

Cuatro cosas que esta lista da por buenas y conviene que consten, porque un revisor podría leerlas
como incumplimientos:

- **«Notificación de Android», «permiso», «canal», «RSS» y «PDF» no son detalles de implementación.**
  Son el vocabulario del producto: la persona ve una notificación de Android, concede un permiso, y la
  interfaz misma dice «Busca en el título, organismo y categorías del RSS». Cómo se crea el canal o se
  pide el permiso es asunto del `plan.md`.
- **La «línea base» (FR-039) y el «momento desde el que vigila» (Key Entities) describen un comportamiento
  observable, no un mecanismo.** Son la única forma de expresar «nunca retroactivo» de manera que se
  pueda probar: primera sincronización sin avisos, edición sin avisos, reactivación sin avisos.
- **FR-022 pide «el mismo código estable que ya usa el resto de la aplicación»** para las secciones. Suena
  técnico, pero es un requisito de estabilidad —que renombrar una sección no rompa las reglas guardadas—
  y viene del documento funcional (§5.4). Qué código es queda para el plan.
- **La comprobación periódica (FR-063 a FR-066) se describe por lo que la persona percibe** —con red, sin
  hora exacta, sin promesa de tiempo real— y por lo que se prohíbe, que es lo que el documento funcional
  exige. El intervalo concreto se deja al plan (Assumptions).

Dos límites admitidos en la propia especificación: **SC-013** exige comprobar en un móvil real que la
comprobación periódica entrega una notificación con la aplicación cerrada, porque la frontera con Android
no la ven las pruebas de esta casa; y la **ventana de cien anuncios** por fuente puede dejar publicaciones
fuera tras un largo periodo sin comprobar, y la interfaz no lo oculta.

Cero marcadores `[NEEDS CLARIFICATION]`: las cuatro decisiones que podían haberlo sido —dos pestañas,
segundo plano en esta feature, borrado real y vista previa— las cerró el propietario antes de escribir la
especificación y constan en su cabecera.
