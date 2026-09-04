# Specification Quality Checklist: Resumen IA con proveedor nuevo

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 4 de septiembre de 2026
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

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`

Estado: **35 requisitos funcionales, 14 criterios de éxito, cero marcadores pendientes.**

### Iteración 1 — dos marcadores, ambos elevados al propietario

Fallaban «No [NEEDS CLARIFICATION] markers remain» y, por consecuencia, «All functional
requirements have clear acceptance criteria». Los dos marcadores eran decisiones sin valor por
defecto razonable, así que se preguntaron en lugar de resolverlas por conjetura.

### Iteración 2 — resueltas

- **FR-007 — el tamaño de la ficha.** El propietario elige **tope de diez elementos por sección**,
  pidiendo al servicio que priorice lo relevante. El problema es nuevo: hasta ahora solo se
  enviaban las primeras páginas, así que ninguna ficha podía crecer demasiado; al leer el documento
  completo, un presupuesto de treinta páginas podía producir decenas de puntos clave. Se descartó
  un «ver más» plegable (interacción, textos y pruebas instrumentadas nuevas) y se descartó dejarlo
  sin tope (contradice «entender una publicación de un vistazo», la User Story 1 de la 007). Se
  añadió por escrito que una sección recortada **debe advertirlo**: descartar en silencio sería la
  misma media verdad que esta feature viene a eliminar. Lo mide SC-013.
- **FR-031 — el aviso de envío externo.** El propietario elige **añadir una frase** que diga que el
  servicio puede usar el texto de ese documento público para mejorar sus modelos. De ahí salió una
  consecuencia que no estaba en la pregunta y se ha recogido como **FR-031a**: quien ya había
  aceptado el aviso anterior nunca leyó esa frase, así que el aviso vuelve a mostrarse **una sola
  vez**. Lo mide SC-014, y queda anotado en Assumptions como precio aceptado.

### Cosas que merecieron una segunda lectura

- **El campo `Input` cita literalmente al proveedor y al modelo**, porque es la transcripción de
  lo que pidió el propietario. No es una fuga de detalle de implementación: es el registro de la
  petición, igual que en `specs/007-resumen-ia/spec.md`. El cuerpo de la especificación no los
  nombra en ningún requisito, y FR-028 prohíbe expresamente que la interfaz lo haga.
- **FR-034 (diagnóstico) parece técnico**, y se ha conservado a propósito. Los dos defectos que de
  verdad rompieron el Resumen IA en un móvil no los podía ver ninguna prueba automática, y se
  encontraron por el registro. Que ese registro exista y distinga causas es un requisito de
  calidad del producto, no un detalle de implementación; el cómo queda para `plan.md`.
- **SC-011 habla de compilar y de pasar pruebas**, que es lenguaje de desarrollo. Se mantiene
  porque es la única forma medible de expresar la garantía de FR-033: que el proyecto siga siendo
  utilizable sin la credencial.
- **FR-004 deja el tope superior sin número.** Es deliberado: el número depende de los límites
  reales del plan gratuito, que el proveedor ya no publica y hay que leer en su panel. Fijarlo
  corresponde a `plan.md`, y la especificación se queda con la propiedad comprobable —«por encima
  de cualquier publicación ordinaria»— más el dimensionado que documentan las Assumptions.
- **FR-031a numerado con letra en lugar de correr la numeración.** Nació al resolver FR-031 y
  renumerar treinta y cinco requisitos por una inserción habría invalidado la tabla de relación con
  la 007 sin ganar nada.
- **La sección «Relación con los requisitos de la feature 007» no está en la plantilla.** Se ha
  añadido porque esta feature no crea funcionalidad: reasienta una que existe. Sin esa tabla,
  `/speckit-analyze` no tendría forma de comprobar qué requisitos de la 007 siguen vigentes, cuáles
  se degradan a caso extremo y cuáles se amplían.


---

## Verificación posterior a la implementación — 4 de septiembre de 2026

La especificación se sostuvo. Las cuatro puertas de calidad en verde —**778 pruebas unitarias** y
**154 instrumentadas, cero fallos**, lint sin errores—, la travesía real de la frontera pasada, y las
comprobaciones manuales hechas en emulador sobre publicaciones reales del boletín.

**Lo que la implementación cambió de la especificación**, y conviene que conste:

- **El contrato del validador de la fase 1 no se sostenía.** `validate(raw, corpus)` no puede expresar
  una lectura parcial, porque el corpus solo no dice qué páginas salieron cuando el guardarraíl corta.
  Se corrigió a `validate(raw, document, totalPages)` en `contracts` y en `data-model` en lugar de
  forzar la firma. Ningún requisito cambió.
- **El número del guardarraíl dejó de ser una suposición.** La llamada real dio **4,39 caracteres por
  token** sobre texto del BOC en español, así que 480.000 caracteres son ~109.000 tokens y no los
  ~120.000 que decía el plan. Propagado a los seis artefactos. FR-004 no cambia: sigue siendo «por
  encima de cualquier publicación ordinaria», y ahora con un número medido detrás.
- **Dos afirmaciones del plan eran falsas y las encontró el dispositivo**: el paso de razonamiento se
  llama `thought` y no `model_thoughts` —y llega siempre primero, así que `steps[0]` habría fallado en
  el cien por cien de las respuestas—; y las claves del proveedor tienen dos formatos, `AIza` y `AQ.`,
  y la comprobación de secretos buscaba solo el primero. Corregido en los cuatro sitios.

**Lo que sigue sin verificar**, y no por olvido: los tres límites del plan gratuito exigen el panel de
AI Studio con la cuenta del propietario. El tercero —tokens por minuto— es el único que podría obligar
a bajar el guardarraíl, y está anotado en el KDoc de la constante, en `quickstart.md` §0 bis y en la
tarea T001a. Tampoco se probó el caso de documento sin texto extraíble: no apareció ninguna publicación
escaneada en el boletín del día.
