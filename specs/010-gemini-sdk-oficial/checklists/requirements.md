# Specification Quality Checklist: El documento se envía entero, no su texto

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

## Notas

### Iteración 1 — qué se revisó y con qué resultado

**Fugas de implementación.** Se buscaron en el cuerpo del documento los nombres de la librería, el
proveedor, el lenguaje, el entorno de compilación, la herramienta de optimización y los paquetes del
proyecto. Cero aciertos. Las dos apariciones que quedan están en la cabecera: el nombre de la rama y
la cita literal de lo que pidió el propietario, que es un campo de la plantilla y se conserva
verbatim, igual que en la 009 —donde la cita nombra a Groq y a un modelo concreto—.

Donde hacía falta nombrar algo técnico, se nombra por lo que hace y no por lo que es:
«la optimización de tamaño» en vez de la herramienta, «una versión del entorno de compilación
superior a la actual» en vez del número, «la librería elegida» en vez de su coordenada. La ruta
técnica está donde le toca, en `plan.md`.

**Cero marcadores de clarificación, y no por comodidad.** Las tres cosas que podrían haberlo sido se
resolvieron antes de escribir, con el propietario: qué se comparte entre el resumen y la pantalla
siguiente (el documento y la conversación, mientras dure la visita a la publicación), qué pasa con la
tubería de extracción de texto (se retira), y si la aplicación distribuida se optimiza (sí, y con una
puerta manual nueva). Están recogidas como decisiones cerradas en la cabecera y como FR-007…FR-011 y
FR-041…FR-042.

### Cosas que merecieron una segunda lectura

**FR-002 contradice un invariante escrito de las features 007 y 009**, y eso no es un descuido: es el
cambio. La regla decía que un documento sin texto utilizable no llega nunca al servicio. Existía
porque lo que se enviaba *era* el texto. Cuando lo que se envía es el documento, un escaneado deja de
ser un caso imposible. Se dejó explícito en la tabla «Relación con los requisitos de las features 007
y 009» con la palabra **Superado**, no «eliminado» ni «incumplido», para que dentro de seis meses
nadie lo lea como una regresión.

**SC-001 depende de un supuesto que no controlamos**: que el servicio sepa leer un PDF escaneado. Se
recogió tal cual en Assumptions, con la instrucción de decirlo si resulta falso en vez de disimularlo.
Es el único criterio de éxito de esta feature que puede caerse por algo ajeno, y conviene que se sepa
antes de escribir código y no después.

**FR-011 describe una red de seguridad, no un mecanismo.** Se redactó con esa frase dentro del propio
requisito para que nadie lo lea como permiso para dejar de retirar el documento (FR-009). Que el
servicio caduque el fichero por su cuenta es lo que salva el caso de que la aplicación muera de golpe;
no es la forma normal de terminar.

**FR-042 es una comprobación manual, y eso choca con el principio V.** Se dejó así a conciencia y con
su motivo escrito: ninguna prueba automática del proyecto se ejecuta sobre la versión optimizada, así
que prometer que funciona sin haberla ejecutado sería declarar verde algo que nadie ha visto correr.
Es el mismo razonamiento del §3 bis de la 009.

**«Como mucho hay uno a la vez»** se subrayó en la entidad *Documento preparado* porque es lo que
hace que FR-010 sea comprobable: sin ese límite, «abrir otra publicación retira el documento de la
anterior» sería una afirmación sobre un conjunto de tamaño desconocido.

### Verificación posterior a `/speckit-analyze` — 5 de septiembre de 2026

El análisis cruzado de `spec.md`, `plan.md` y `tasks.md` encontró **tres críticos**, y los tres eran
defectos reales de los artefactos, no ruido:

1. **`GeminiUsage` se quedaba sin casa.** La tarea que borraba `GeminiDtos.kt` se llevaba por delante un
   tipo que es parte de la firma de `GeminiSummaryResult.Success`, que sobrevive. Tras esa tarea el
   proyecto no habría compilado, y ni `plan.md` ni `data-model.md` decían dónde iba. Resuelto con T041a:
   se muda a `GeminiSummaryDataSource.kt` como `SummaryUsage`, sin anotaciones de serialización, porque
   deja de deserializarse de un cuerpo.
2. **Dos pruebas de regresión se perdían con el fichero que las contenía**: la de la cancelación mal
   clasificada como error de red (FR-023, D-218) y la del reintento que choca con la cuota del mismo
   minuto (FR-027). Los dos defectos **siguen siendo posibles con la librería nueva** —el primero es una
   propiedad de cualquier llamada bloqueante en corrutina, no del cliente HTTP—, así que borrarlas
   incumplía el principio V sin decirlo. Resuelto con T049a y T049b, y con la dependencia hacia atrás
   escrita en el grafo: portar primero, borrar después.
3. **Una referencia cruzada equivocada** (T010 apuntaba a T072 en vez de a T081) que habría mandado a
   quien implementara a la tarea de la preferencia del aviso en lugar de a la del modelo.

Además, cuatro correcciones menores: se añadió la comprobación de que el `displayName` que viaja al
servicio solo lleva datos públicos (FR-006, que era el único requisito de privacidad sin verificación);
se añadió la aserción de que dos fallos con el mismo mensaje en pantalla dejan líneas distintas en el
registro (FR-040); se corrigió el alcance de la tarea de `AiSummaryTabTest`, que decía «dos literales»
cuando el cambio de forma de `Generating` toca tres construcciones y elimina una prueba; y se unificó
en **nueve** el recuento de pantallas de SC-011, que decía siete mientras el `quickstart.md` enumeraba
nueve.

La lista pasa de 86 a 90 tareas. Cobertura tras las correcciones: **55 de 55** requisitos con al menos
una tarea que los implementa o los verifica.

### Verificación posterior a la implementación — 5 de septiembre de 2026

**Una de las cinco decisiones del plan no sobrevivió, y no fue por un error de diseño.**
`com.google.genai:google-genai-kotlin` se adoptó entera —catálogo, Java 17, exclusiones de
empaquetado, tres clases escritas contra ella, APK compilando— y el primer test que **construyó** el
cliente reveló que su artefacto de Android lleva un `throw` incondicional si se le da una credencial.
La forma de la dependencia se había verificado a fondo antes de escribir una línea: README, código
fuente de cada tipo, POM, bytecode del AAR. Nada de eso enseña un `throw` dentro de una función
`actual` de `androidMain`. Está en `research.md` D-227, y es la lección que esta feature deja:
**verificar la forma de una dependencia no es lo mismo que ejecutarla**.

Lo que eso obligó a retirar: FR-041 y FR-042 —la optimización del empaquetado y su comprobación
manual— porque su única causa era la cola de dependencias, y SC-011 con ellos. Se sustituyó por
FR-044 y un SC-011 nuevo: cero dependencias nuevas. **El resto de la feature no se movió**: retirar
la librería tocó cuatro ficheros de `data/source/remote/` y ni uno de `domain` ni de `ui`.

**Tres tareas estaban marcadas como hechas sin estarlo**, y se descubrió al recorrer la aplicación en
el emulador: el aviso seguía diciendo «envía el texto de este documento» (T071), la clave de la
preferencia seguía en `_v2` (T072), y faltaban tres pruebas del repositorio (T061, T067, T068). Se
hicieron después, con su prueba de regresión. Marcarlas en bloque fue el error; la comprobación manual
fue lo que lo destapó, que es exactamente para lo que existe.

**Lo comprobado de verdad, y no solo con dobles:**

| Qué | Cómo |
|---|---|
| SC-001, el único criterio que podía caerse | Un PDF **rasterizado sin capa de texto** —verificado: cero operadores `BT`, cobrado por el servicio como `modality: IMAGE`— produce el resumen completo, con organismo, fecha y plazo leídos de los píxeles |
| La caducidad de 48 h | El propio `expirationTime` de la respuesta de subida, 48 h exactas después de `createTime` |
| El documento viaja una vez | En el emulador: «Volver a generar» da `session: reusing document` y **cero** líneas `upload: sending` |
| El documento se retira | Pulsar Atrás da `session: released`, y el listado del servicio queda a cero ficheros |
| La cancelación no miente | Salir con «Generando el resumen…» en pantalla no deja ninguna línea `network:`, y al volver no hay mensaje de error |
| El registro no filtra nada | Cinco líneas en una generación completa, todas de fase y tamaño |

**Las dos cifras de cuota siguen sin confirmar**, y siguen exigiendo el panel del proveedor. Es lo
único que esta feature hereda de la 009 sin resolver.
