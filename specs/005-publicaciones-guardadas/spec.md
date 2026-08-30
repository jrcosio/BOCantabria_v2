# Feature Specification: Publicaciones guardadas

**Feature Branch**: `005-publicaciones-guardadas`

**Created**: 2026-08-30

**Status**: Draft

**Input**: User description: "Siguiente Feature es la opción de guardar, que cuando en una
publicación marques la publicación quede guardada, y logicamente si la descarmas se quita de
guardados, en el guardados pues sale el listado de todas la publicaciones guardadas y el
comportamiento es el mismo que la principal, una lista de tarjetas que solo son las guardadas, y
cuando pulsas sobre una de ellas pues hace lo mismo habre la publicación y demas..."

Decisiones cerradas con el propietario antes de escribir esta especificación: guardar **solo marca**
—no descarga ni conserva el documento para leer sin conexión—; la lista se ordena por instante de
guardado, la más reciente primero; se usa la tarjeta estándar, la misma que Inicio; y desmarcar no
ofrece deshacer.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Guardar un anuncio y volver a encontrarlo (Priority: P1)

Una persona recorre el boletín del día, ve un anuncio que le interesa —una convocatoria, un plazo—
y no puede leerlo ahora. Toca el marcador de la tarjeta: el marcador se rellena y ya está guardado.
Más tarde, en otro momento del día, entra en Guardados desde la barra inferior y ahí está su anuncio,
con la misma tarjeta que vio en el boletín. Lo toca y se abre la publicación, igual que si lo hubiera
tocado en Inicio.

**Why this priority**: es la feature. Sin esto, un anuncio que se ve y no se puede leer en ese
momento se pierde: el boletín de mañana lo empuja fuera de la pantalla y la única forma de volver a
él es buscarlo a mano. Guardar es lo que convierte la aplicación en algo que se usa dos veces al día
en lugar de una.

**Independent Test**: marcar una publicación en Inicio, ir a Guardados y comprobar que aparece esa y
solo esa, y que al pulsarla se llega a su detalle.

**Acceptance Scenarios**:

1. **Given** el boletín en pantalla, **When** la persona toca el marcador de una tarjeta, **Then** el
   marcador pasa a estar relleno y la publicación queda guardada.
2. **Given** una publicación guardada, **When** la persona entra en Guardados, **Then** ve esa
   publicación con la misma tarjeta que en Inicio: organismo, título, sección, fecha y sus acciones.
3. **Given** la lista de guardados, **When** la persona toca una tarjeta, **Then** se abre el detalle
   de esa publicación.
4. **Given** varias publicaciones guardadas en distintos momentos, **When** la persona abre
   Guardados, **Then** las ve ordenadas por el momento en que las guardó, la más reciente primero.
5. **Given** la lista de guardados, **When** la persona comparte desde una tarjeta, **Then** ocurre
   exactamente lo mismo que al compartir desde Inicio.
6. **Given** una publicación abierta desde Guardados, **When** la persona retrocede, **Then** vuelve
   a la lista en la misma posición de lectura en la que estaba.

---

### User Story 2 - Quitar de guardados, y que el estado sea el mismo en todas partes (Priority: P1)

La misma persona ya ha leído el anuncio y quiere quitarlo de su lista. Puede hacerlo desde la propia
lista, desde la tarjeta en el boletín o desde la barra superior del detalle: en los tres sitios es el
mismo marcador y dice lo mismo. Al quitarlo de la lista, la tarjeta desaparece de Guardados en el
acto. Y en cualquier pantalla donde esa publicación se vea, el marcador aparece contorneado, sin que
haya que salir y volver a entrar.

**Why this priority**: un interruptor que solo enciende no es un interruptor. Y un estado que se
muestra distinto en dos pantallas hace que la persona deje de confiar en él: si el boletín dice que
está guardada y Guardados no la tiene, lo que se rompe es la confianza en la lista entera.

**Independent Test**: marcar desde el detalle y comprobar que la tarjeta del boletín ya aparece
marcada al volver; desmarcar desde la lista y comprobar que el elemento desaparece y que la tarjeta
del boletín queda contorneada.

**Acceptance Scenarios**:

1. **Given** una publicación guardada, **When** la persona toca su marcador relleno, **Then** deja de
   estar guardada y el marcador vuelve a estar contorneado.
2. **Given** la lista de guardados, **When** la persona desmarca un elemento, **Then** ese elemento
   desaparece de la lista de inmediato.
3. **Given** una publicación guardada desde el detalle, **When** la persona vuelve al boletín,
   **Then** su tarjeta ya muestra el marcador relleno, sin recargar nada.
4. **Given** una publicación desmarcada desde el boletín, **When** la persona abre su detalle,
   **Then** la barra superior muestra el marcador contorneado.
5. **Given** una publicación que ya no está almacenada y cuyo detalle explica que no está,
   **When** la persona mira la barra superior, **Then** no se le ofrece guardar algo que no existe.
6. **Given** el dispositivo sin conexión, **When** la persona guarda o desmarca, **Then** funciona
   con normalidad: guardar no necesita red.

---

### User Story 3 - Que lo guardado no se pierda (Priority: P1)

La persona guarda tres anuncios el lunes. El martes la aplicación se sincroniza sola y trae el
boletín nuevo; el miércoles cierra la aplicación del todo y el sistema mata el proceso; el jueves
actualiza la aplicación desde la tienda. Los tres anuncios siguen en su lista, aunque sus anuncios
hayan dejado de figurar en la fuente oficial —que solo publica sus últimos cien—.

**Why this priority**: es tan crítica como las dos anteriores, y es la que decide si la lista sirve
para algo. Una lista de guardados que se vacía sola es peor que no tener lista: la persona confía y
pierde el anuncio. El proyecto ya tiene la regla de que **nunca se borra una publicación**
(SC-005 de la feature 003), y esta feature la extiende a las marcas.

**Independent Test**: guardar, forzar una sincronización, matar el proceso y volver a entrar; y
actualizar la aplicación sobre una instalación que ya tenía boletín almacenado.

**Acceptance Scenarios**:

1. **Given** una publicación guardada, **When** se completa una sincronización que vuelve a traer
   esa misma publicación, **Then** sigue guardada.
2. **Given** una publicación guardada, **When** el proceso de la aplicación muere y la persona
   vuelve a entrar, **Then** sigue guardada.
3. **Given** una publicación guardada, **When** deja de aparecer en su fuente oficial, **Then**
   sigue guardada y sigue apareciendo en la lista.
4. **Given** una instalación anterior con boletín almacenado, **When** se actualiza a la versión con
   esta feature, **Then** el boletín almacenado sigue ahí y la aplicación arranca con normalidad.
5. **Given** una publicación desmarcada, **When** se comprueba lo almacenado, **Then** la
   publicación sigue almacenada: lo único que se ha retirado es la marca.

---

### User Story 4 - Una lista vacía que explica qué hacer (Priority: P3)

Alguien que acaba de instalar la aplicación toca Guardados por curiosidad. En lugar de una pantalla
en blanco o un «Próximamente», ve un marcador grande, un título que le dice que todavía no ha
guardado nada, una frase que le explica cómo se guarda y un botón que le lleva al boletín.

**Why this priority**: no aporta valor por sí misma, pero es el primer contacto con la feature para
todo el mundo, y una pantalla vacía sin explicación se lee como una pantalla rota.

**Independent Test**: entrar en Guardados sin nada guardado y comprobar que se explica y que el botón
lleva a Inicio.

**Acceptance Scenarios**:

1. **Given** ninguna publicación guardada, **When** la persona entra en Guardados, **Then** ve el
   icono de marcador, el título «Aún no has guardado publicaciones», un texto de apoyo y una acción
   secundaria «Explorar el BOC».
2. **Given** el estado vacío, **When** la persona toca «Explorar el BOC», **Then** llega al boletín.
3. **Given** el estado vacío, **When** la persona guarda una publicación y vuelve, **Then** el estado
   vacío ha sido sustituido por la lista.

---

### Edge Cases

- **Toques rápidos y repetidos en el mismo marcador**: el estado final coincide con el último toque y
  no queda a medias; la lista no parpadea ni duplica el elemento.
- **Guardar mientras una sincronización está en marcha**: la marca se conserva, sin importar cuál de
  las dos escrituras llegue después.
- **El documento de una publicación guardada ya no está en la caché**: al abrirla se vuelve a
  descargar, como cualquier otra; sin conexión, se explica igual que hoy. Guardar **no** conserva el
  documento (ver Assumptions).
- **La publicación guardada dejó de estar almacenada**: la lista solo muestra lo que está almacenado,
  y el detalle sigue explicando que no está en lugar de mostrar una pantalla vacía.
- **Muchas publicaciones guardadas** (varios cientos): la lista se abre y se desplaza sin saltos.
- **Rotación o muerte del proceso con Guardados abierto**: al volver, la lista y su posición de
  lectura se conservan.
- **Fallo al escribir la marca**: la persona se entera y la interfaz **no** se queda mostrando un
  estado que no se ha guardado.
- **El marcador se pulsa dentro de una tarjeta que también es pulsable**: guardar no abre además la
  publicación.

## Requirements *(mandatory)*

### Functional Requirements

#### Guardar y desmarcar

- **FR-001**: La aplicación MUST permitir marcar una publicación como guardada desde su tarjeta en el
  boletín y desde la barra superior de su detalle.
- **FR-002**: La aplicación MUST permitir quitarla de guardados desde esos mismos sitios y desde la
  propia lista de guardados.
- **FR-003**: La acción MUST mostrar el estado actual: marcador **relleno** cuando la publicación
  está guardada y **contorneado** cuando no lo está.
- **FR-004**: La descripción accesible de la acción MUST decir lo que hará al pulsarla, distinguiendo
  guardar de quitar de guardados.
- **FR-005**: Marcar o desmarcar MUST reflejarse en todas las pantallas que muestren esa publicación
  sin que la persona tenga que recargar, salir ni volver a entrar.
- **FR-006**: Guardar y desmarcar MUST funcionar sin conexión.
- **FR-007**: Guardar desde una tarjeta MUST NOT abrir además la publicación.
- **FR-008**: La aplicación MUST NOT ofrecer guardar en un detalle que no tiene publicación que
  guardar porque ya no está almacenada.
- **FR-009**: Si la escritura de la marca falla, la aplicación MUST informar a la persona y MUST NOT
  presentar la publicación como guardada.

#### La pantalla Guardados

- **FR-010**: El destino Guardados MUST mostrar las publicaciones guardadas y MUST NOT anunciar que
  la funcionalidad llegará próximamente.
- **FR-011**: La lista MUST ordenarse por el instante en que se guardó cada publicación, la más
  reciente primero.
- **FR-012**: Cada publicación de la lista MUST presentarse con la misma tarjeta que el boletín, con
  su organismo, título, sección, fecha y acciones.
- **FR-013**: Pulsar una publicación de la lista MUST abrir su detalle, con el mismo comportamiento
  que pulsarla en el boletín.
- **FR-014**: Compartir desde una tarjeta de la lista MUST comportarse exactamente igual que
  compartir desde el boletín.
- **FR-015**: Quitar de guardados desde la lista MUST retirar esa publicación de la lista de
  inmediato.
- **FR-016**: La pantalla MUST llevar cabecera con el título «Guardados».
- **FR-017**: Sin nada guardado, la pantalla MUST mostrar un estado vacío con icono de marcador,
  el título «Aún no has guardado publicaciones», un texto de apoyo y una acción secundaria
  «Explorar el BOC» que lleve al boletín.
- **FR-018**: Al volver del detalle, la lista MUST conservar su posición de lectura.

#### Lo guardado, y lo que nunca se pierde

- **FR-019**: La marca de guardado MUST persistir en el dispositivo entre ejecuciones y sobrevivir a
  la muerte del proceso.
- **FR-020**: Una sincronización MUST NOT alterar la marca de guardado de ninguna publicación.
- **FR-021**: Quitar de guardados MUST NOT borrar la publicación almacenada: retira la marca y nada
  más.
- **FR-022**: Una publicación guardada MUST seguir guardada y seguir apareciendo en la lista aunque
  deje de figurar en su fuente oficial.
- **FR-023**: La actualización de la aplicación sobre una instalación anterior MUST conservar el
  boletín almacenado y las marcas ya existentes, sin obligar a reinstalar ni a volver a
  sincronizarlo todo.
- **FR-024**: Guardar MUST NOT descargar el documento oficial ni impedir que se retire de la caché.
  Guardar marca la publicación; no la descarga.
- **FR-025**: La aplicación MUST poder registrar que se ha guardado o desmarcado algo, y MUST NOT
  registrar de qué publicación se trata: lo que una persona guarda es una señal de interés personal.

### Key Entities

- **Marca de guardado**: el instante en que la persona guardó una publicación. Pertenece a la
  persona, no a la fuente oficial, y por eso ninguna sincronización la escribe ni la borra. Su
  ausencia es el estado «no guardada»: no hay un tercer estado.
- **Publicación**: la que ya existe. No cambia. La marca se le añade por fuera, y lo que la fuente
  publica sobre ella sigue siendo lo único que la fuente puede escribir.
- **Lista de guardados**: las publicaciones almacenadas que tienen marca, ordenadas por el instante
  de la marca. Vacía es un estado normal, no un fallo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Guardar una publicación desde el boletín es **un solo toque**, y su efecto se ve en ese
  mismo toque.
- **SC-002**: Una publicación recién guardada aparece en Guardados **al primer intento**, sin
  recargar, sin salir y volver, y sin esperar una sincronización.
- **SC-003**: El estado de guardado que muestra la tarjeta y el que muestra el detalle **coinciden
  siempre**. Se verifica de forma mecánica.
- **SC-004**: Ninguna sincronización pierde una marca de guardado. Se verifica de forma mecánica.
- **SC-005**: Quitar de guardados no borra ninguna publicación almacenada. Se verifica de forma
  mecánica.
- **SC-006**: Lo guardado sobrevive a cerrar la aplicación, a la muerte del proceso y a la
  actualización desde la versión anterior sin pérdida del boletín almacenado. Las dos primeras se
  verifican de forma mecánica; la tercera, con una prueba de actualización del almacén y una
  comprobación a mano sobre una instalación real.
- **SC-007**: Con **doscientas** publicaciones guardadas, la lista se abre y se desplaza sin saltos
  perceptibles.
- **SC-008**: Ninguna acción visible de esta feature deja a la persona sin respuesta. En particular,
  ya no queda ninguna acción de guardar que anuncie que llegará próximamente.
- **SC-009**: El estado vacío se entiende sin ayuda: dice qué falta, cómo se consigue y adónde ir.
- **SC-010**: Toda pieza de reglas de negocio y todo modelo de pantalla que introduce esta feature
  tiene su prueba automática. Se verifica de forma mecánica.
- **SC-011**: Las cuatro comprobaciones de calidad del proyecto —compilación, pruebas sin
  dispositivo, pruebas de interfaz y análisis estático— terminan en verde.
- **SC-012**: Las desviaciones respecto al documento de diseño quedan anotadas en el propio documento
  con su motivo, y la guía operativa no lo contradice.

## Assumptions

- **Guardar solo marca. Leer sin conexión queda aplazado, y es una promesa a medias que se reconoce
  aquí.** La guía operativa del proyecto (`CLAUDE.md`) y la decisión D-003 de la feature 004 dijeron
  por escrito que guardar para leer sin conexión **sería** la funcionalidad de Guardados. Esta
  feature no lo cumple: decide el propietario, y la razón es que conservar el documento es una
  decisión propia —qué se guarda, cuánto, quién lo borra, dónde vive el fichero, cómo se comparte
  desde ahí— que merece su propia feature en lugar de entrar a medias en esta. **Consecuencia
  aceptada**: el documento de una publicación guardada puede retirarse de la caché y volver a
  descargarse al abrirla, y sin conexión puede no estar disponible. Queda anotado en la guía
  operativa para que nadie lo lea como un olvido.
- **El orden es el instante de guardado, el más reciente primero.** Es lo que la persona acaba de
  hacer, y es el único orden que la feature ofrece: por eso no hay acción de ordenar.
- **La tarjeta es la estándar, la misma que Inicio.** El apartado 22.2 del documento de diseño pide
  la tarjeta compacta; se enmienda el documento en lugar de introducir una variante que ahora mismo
  no aporta nada. La tarjeta pasa a ser un componente compartido entre las dos pantallas.
- **Desmarcar no ofrece deshacer.** El marcador que se vacía y la tarjeta que desaparece son la
  respuesta; volver a guardar es un toque.
- **Quedan fuera, y se anotan como enmienda en el apartado 22 del documento de diseño**: los chips de
  clasificación «Todos» / «Sin conexión» / «Con resumen» —el primero sería el único estado posible y
  los otros dos dependen de la lectura sin conexión y del resumen de inteligencia artificial, que
  todavía no existen—, la acción de ordenar, la selección múltiple —ya marcada como opcional en el
  propio documento—, la tarjeta compacta del apartado 12.2, el indicador de descarga y la fecha de
  guardado como metadato visible.
- **No hay insignia con el número de guardados** en la barra inferior: nadie la ha pedido y la barra
  no tiene hoy ese mecanismo.
- **El estado de guardado es local al dispositivo.** No hay cuenta, ni sesión, ni sincronización
  entre dispositivos, y esta feature no las introduce.
- **No hay límite de publicaciones guardadas ni caducidad.** Una marca solo desaparece porque la
  persona la retira.
- **Guardados no lleva cabecera editorial ni chips de sección.** No es una selección del boletín: es
  una lista propia de la persona, así que no tiene fecha ni recuento de sección que mostrar.
- **Los textos visibles están en español**, y las fechas se muestran en formato largo español, como
  en el resto de la aplicación.
