# Feature Specification: Boletín del día — lectura del BOC y pantalla de Inicio

**Feature Branch**: `003-boletin-del-dia`

**Created**: 2026-08-29

**Status**: Draft

**Input**: User description: "Dos trabajos en una feature. Primero, leer el BOC según el documento de
consumo de feeds RSS aportado y, una vez leído, almacenarlo en el dispositivo para poder utilizarlo
de manera rápida. Segundo, la pantalla de Inicio, que es donde se visualiza lo descargado, con las
tarjetas del mockup aportado y un Navigation Drawer para las secciones del BOC. Las campanitas de
notificación de los mockups se ignoran. En la barra superior: el icono que lanza el panel lateral,
después el escudo con el nombre de la aplicación, y al otro lado la lupa —que muestra
«Próximamente»— y un icono de información que todavía no hace nada. En medio, las tarjetas. Abajo,
la barra de navegación sin el destino de Avisos: Inicio es la pantalla que se está haciendo, Buscar
y Guardados dirán «Próximamente». Pulsar una tarjeta llevará al contenido de la publicación, pero
eso es la feature siguiente. Esta feature es que se visualice lo descargado de forma elegante y con
el estilo de la aplicación."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver el boletín del día nada más abrir (Priority: P1)

Una persona abre BOCantabria. Tras la portada aparece Inicio y, en cuestión de segundos, el boletín
del día: la fecha de la última edición publicada, cuántos anuncios contiene y la lista de
publicaciones en tarjetas legibles, cada una con el organismo que la emite, su título completo y su
fecha. No ha tenido que buscar nada ni configurar nada: la aplicación ha ido al Boletín Oficial de
Cantabria, ha recogido lo publicado en sus fuentes oficiales y se lo ha presentado ordenado.

**Why this priority**: es la razón de ser de la aplicación. Sin esto no hay producto: hoy la
pantalla principal muestra datos de relleno. Es además la primera vez que la aplicación habla con el
servicio real del BOC.

**Independent Test**: instalar en un dispositivo limpio, abrir con conexión y comprobar que aparecen
publicaciones reales del BOC con su organismo, su título y su fecha, y que la cabecera muestra la
fecha de la última edición disponible y el número de publicaciones de esa fecha.

**Acceptance Scenarios**:

1. **Given** una instalación limpia con conexión, **When** la persona llega a Inicio, **Then** ve
   primero marcadores de carga y después las publicaciones de la fecha más reciente disponible,
   ordenadas de forma estable.
2. **Given** publicaciones a la vista, **When** la persona lee una tarjeta, **Then** distingue sin
   esfuerzo el organismo emisor, el título y la fecha de publicación, y reconoce la sección por su
   indicador de color acompañado de texto.
3. **Given** la cabecera de Inicio, **When** hay publicaciones, **Then** muestra la denominación del
   boletín, la fecha de la edición en formato legible en español y el número de publicaciones que
   contiene.
4. **Given** una fuente oficial que no responde, **When** las demás sí responden, **Then** la
   persona ve las publicaciones de las que sí respondieron, sin ningún mensaje de error.
5. **Given** que ninguna fuente oficial responde y no hay nada guardado, **When** la persona llega a
   Inicio, **Then** ve un mensaje comprensible con la acción de reintentar, nunca una pantalla en
   blanco.

---

### User Story 2 - Que lo consultado siga estando sin conexión (Priority: P1)

La misma persona vuelve a abrir la aplicación en el metro, sin cobertura. El boletín que consultó
antes sigue ahí, íntegro y al instante, con un aviso discreto de que no hay conexión. Cuando
recupera la señal, desliza hacia abajo y la lista se actualiza sin que el contenido desaparezca
mientras tanto.

**Why this priority**: es lo que convierte la descarga en almacenamiento útil y lo que justifica
guardar en el dispositivo. Una aplicación de consulta oficial que solo sirve con cobertura no sirve.
Es tan crítica como la historia 1 porque comparte con ella el camino de datos.

**Independent Test**: abrir con conexión, cerrar, activar el modo avión y volver a abrir. Debe verse
exactamente el mismo contenido, de inmediato, con el aviso de falta de conexión.

**Acceptance Scenarios**:

1. **Given** contenido consultado previamente, **When** la persona abre la aplicación sin conexión,
   **Then** ve ese contenido de inmediato, sin esperar a ninguna descarga, con un aviso de que no
   hay conexión que no tapa el contenido.
2. **Given** contenido a la vista, **When** la persona desliza hacia abajo para actualizar,
   **Then** aparece un indicador de progreso discreto y el contenido existente permanece visible
   durante toda la actualización.
3. **Given** una actualización que no encuentra novedades, **When** termina, **Then** el contenido
   permanece intacto y no se muestra ningún error.
4. **Given** publicaciones guardadas que han dejado de aparecer en la fuente oficial por antigüedad,
   **When** se sincroniza de nuevo, **Then** esas publicaciones **siguen** guardadas y consultables.
5. **Given** la aplicación abierta hace menos de treinta minutos, **When** la persona vuelve a
   abrirla, **Then** no se lanza una descarga nueva y el contenido aparece de forma inmediata.

---

### User Story 3 - Explorar el BOC por secciones (Priority: P2)

Una persona busca oposiciones. Toca el icono de menú de la barra superior y se despliega un panel
lateral con las nueve secciones oficiales del BOC. Escribe «oposi» en el campo de filtro y la lista
se reduce; despliega «Autoridades y personal» y elige «Cursos, oposiciones y concursos». El panel se
cierra y la lista muestra las últimas publicaciones de esa subsección, con la cabecera nombrándola.
Un toque en el chip «Todo» la devuelve al boletín del día.

**Why this priority**: es lo que hace navegable el corpus del BOC más allá del día en curso. Va
después de las dos primeras porque necesita que exista contenido guardado que explorar.

**Independent Test**: abrir el panel lateral, filtrar por texto, expandir una sección con
subsecciones y elegir una; comprobar que la lista y la cabecera cambian y que el chip «Todo»
devuelve al boletín del día.

**Acceptance Scenarios**:

1. **Given** Inicio a la vista, **When** la persona toca el icono de menú de la barra superior,
   **Then** se abre el panel lateral con las nueve secciones principales, cada una con su número y
   su nombre.
2. **Given** el panel abierto, **When** la persona escribe texto en el campo de filtro, **Then** se
   muestran únicamente las secciones y subsecciones cuyo nombre coincide.
3. **Given** una sección con subsecciones, **When** la persona la despliega, **Then** ve sus
   subsecciones agrupadas y visualmente subordinadas a ella; al contraerla, desaparecen.
4. **Given** una sección o subsección elegida, **When** el panel se cierra, **Then** la lista muestra
   las publicaciones de esa sección **sin limitarse a la última fecha**, ordenadas de la más reciente
   a la más antigua, y la cabecera la nombra.
5. **Given** una sección sin ninguna publicación —o sin publicaciones desde hace años—, **When** la
   persona la elige, **Then** ve un estado vacío con un mensaje propio, **nunca** un error.
6. **Given** una sección elegida, **When** la persona toca el chip «Todo», **Then** vuelve al boletín
   del día.
7. **Given** una sección elegida, **When** el sistema operativo destruye y recrea la pantalla,
   **Then** la selección se conserva.

---

### User Story 4 - Moverse por la aplicación sin toparse con callejones (Priority: P3)

La persona ve abajo tres destinos —Inicio, Buscar y Guardados— y arriba una lupa y un icono de
información. Todavía no todos hacen algo: los que no, lo dicen con claridad y con el mismo lenguaje,
en lugar de no responder o de llevar a una pantalla rota.

**Why this priority**: es el armazón que sostendrá las features siguientes. No aporta valor por sí
misma, pero evita que la aplicación se perciba como incompleta y fija de una vez la estructura de
navegación.

**Independent Test**: recorrer los tres destinos de la barra inferior y las dos acciones de la barra
superior comprobando que ninguna deja a la persona sin respuesta.

**Acceptance Scenarios**:

1. **Given** cualquier destino, **When** la persona usa la barra inferior, **Then** llega al destino
   elegido y la barra refleja cuál está activo.
2. **Given** Buscar o Guardados, **When** la persona entra, **Then** ve una pantalla con el aspecto
   de la aplicación que indica que la funcionalidad llegará próximamente.
3. **Given** Inicio, **When** la persona toca la lupa de la barra superior, **Then** recibe un aviso
   breve de que la búsqueda llegará próximamente.
4. **Given** una tarjeta de publicación, **When** la persona usa la acción de compartir, **Then** el
   sistema ofrece las formas habituales de compartir el enlace del documento oficial.
5. **Given** una tarjeta de publicación, **When** la persona usa la acción de guardar, **Then**
   recibe un aviso de que esa funcionalidad llegará próximamente.

---

### Edge Cases

- **Fuente oficial vacía**: la subsección de Subastas judiciales devuelve una respuesta válida con
  cero publicaciones. Es un resultado correcto, no un fallo, y no debe generar aviso alguno.
- **Fuente sin novedades recientes**: hay subsecciones cuya última publicación es de 2021 o de 2024.
  Se muestran tal cual, con su fecha real. No se interpreta como caída del servicio.
- **Categorías desordenadas**: en la subsección de Seguridad Social hay publicaciones antiguas cuyos
  componentes de clasificación aparecen permutados. Ni rompen el proceso ni se descartan: se
  clasifican por la fuente de la que proceden y se anota la anomalía.
- **Clasificación ausente o contradictoria**: si una publicación no trae clasificación, o la que trae
  no corresponde a la fuente de la que se obtuvo, manda la fuente y se conserva el valor original.
- **Publicación sin identificador en el enlace**: debe seguir siendo identificable de forma estable
  para no duplicarse en sincronizaciones sucesivas.
- **La misma publicación en dos fuentes**: se conserva un único registro, no dos tarjetas iguales.
- **Fecha no interpretable**: la publicación se rechaza sin detener el resto de la fuente, y queda
  constancia del motivo.
- **Respuesta que no es lo esperado**: si una fuente devuelve algo que no es el formato acordado, o
  un cuerpo desmesuradamente grande, se descarta esa fuente y las demás continúan.
- **Título muy largo**: se guarda íntegro, sin recortar; el recorte es solo cosa de la pantalla.
- **Primera apertura sin conexión**: no hay nada guardado y no se puede descargar. Debe ofrecerse un
  mensaje claro con reintento, no una pantalla vacía sin explicación.
- **Actualización mientras hay otra en curso**: deslizar repetidamente no debe lanzar descargas
  simultáneas ni duplicar publicaciones.
- **Cambio de configuración del dispositivo**: girar o cambiar el tamaño de letra no reinicia la
  descarga ni pierde la posición de lectura.
- **Tamaño de letra al 200 %**: las tarjetas crecen; no se recorta el organismo, el título ni la
  fecha.

## Requirements *(mandatory)*

### Functional Requirements

**Obtención del boletín**

- **FR-001**: La aplicación MUST obtener las publicaciones del Boletín Oficial de Cantabria de las
  diecinueve fuentes oficiales publicadas, que cubren las nueve secciones y sus subsecciones.
- **FR-002**: El inventario de fuentes MUST estar declarado de forma explícita y versionada, con su
  identificador exacto, su sección, su subsección cuando la tenga y su orden de presentación. Las
  direcciones MUST NOT construirse por cálculo a partir de otras.
- **FR-003**: El inventario MUST permitir añadir, retirar o desactivar una fuente sin alterar el
  proceso de lectura.
- **FR-004**: El fallo de una fuente MUST NOT impedir el procesamiento de las demás.
- **FR-005**: La obtención MUST limitar el número de fuentes consultadas de forma simultánea, para no
  someter al servicio oficial a diecinueve peticiones a la vez.
- **FR-006**: La obtención MUST aplicar límites de espera holgados y reintentos con espera creciente
  ante fallos transitorios, y MUST NOT reintentar ante fallos que no van a resolverse solos.
- **FR-007**: La aplicación MUST identificarse ante el servicio oficial de forma reconocible.
- **FR-008**: El procesamiento del contenido recibido MUST rechazar definiciones de tipo de documento
  y entidades externas, y MUST aplicar un límite de tamaño de respuesta y un límite de número de
  publicaciones por fuente.
- **FR-009**: Una fuente que responde correctamente con cero publicaciones MUST tratarse como
  resultado válido.
- **FR-010**: Una publicación que no cumpla los mínimos —título, enlace válido y fecha
  interpretable— MUST rechazarse individualmente, dejando constancia del motivo, sin detener el
  procesamiento del resto de esa fuente.

**Normalización y clasificación**

- **FR-011**: La fecha de publicación MUST interpretarse en formato año-mes-día.
- **FR-012**: La sección y la subsección de una publicación MUST tomarse de la fuente de la que se
  obtuvo, no del campo de clasificación que acompaña a la publicación.
- **FR-013**: El campo de clasificación original MUST conservarse sin modificar, y MUST utilizarse
  solo para enriquecer y verificar.
- **FR-014**: El tipo de edición —ordinaria o extraordinaria— MUST detectarse en cualquier posición
  del campo de clasificación; si no aparece, MUST registrarse como desconocido.
- **FR-015**: El proceso MUST NOT asumir un número fijo de componentes ni un orden fijo en el campo
  de clasificación, y MUST anotar una advertencia cuando el orden o los valores no sean los
  esperados, sin descartar la publicación.
- **FR-016**: De los componentes restantes MUST deducirse el organismo emisor, y MUST poder usarse el
  prefijo del título como dato auxiliar cuando el título lo lleve.
- **FR-017**: Cada publicación MUST tener un identificador externo estable, obtenido del enlace
  cuando sea posible y, si no, de la dirección canónica o de una huella de su contenido, dejando
  constancia de cuál de los tres se usó.
- **FR-018**: El título MUST guardarse íntegro tal como se recibe, sin recortarlo ni completarlo.

**Almacenamiento y actualización**

- **FR-019**: Las publicaciones obtenidas MUST guardarse en el dispositivo, y lo guardado MUST ser la
  única fuente de lo que la aplicación muestra.
- **FR-020**: Una publicación ya conocida que vuelve a aparecer MUST actualizarse, no duplicarse.
- **FR-021**: Las publicaciones guardadas MUST NOT borrarse por dejar de aparecer en la fuente
  oficial.
- **FR-022**: La aplicación MUST poder detectar que una fuente no ha cambiado desde la última
  consulta y evitar volver a procesarla.
- **FR-023**: La aplicación MUST sincronizar al abrirse cuando lo guardado tenga más de treinta
  minutos, y MUST NOT sincronizar si es más reciente.
- **FR-024**: La aplicación MUST ofrecer una actualización manual mediante el gesto de deslizar hacia
  abajo, que MUST poder lanzarse siempre, con independencia de la antigüedad de lo guardado.
- **FR-025**: Una actualización en curso MUST NOT poder duplicarse lanzando otra.
- **FR-026**: Durante una actualización el contenido ya visible MUST permanecer en pantalla.
- **FR-027**: Si toda la sincronización falla pero hay contenido guardado, la aplicación MUST
  mostrarlo e indicar que no hay conexión. Si falla y no hay nada guardado, MUST mostrar un mensaje
  con la acción de reintentar.
- **FR-028**: El orden de las publicaciones MUST ser estable y no depender del orden en que responden
  las fuentes: fecha descendente y, a igualdad de fecha, un criterio de desempate determinista.
- **FR-029**: La aplicación MUST NOT registrar datos personales identificables en la telemetría de
  la sincronización.

**Pantalla de Inicio**

- **FR-030**: Inicio MUST componerse, de arriba abajo, de barra superior clara, cabecera editorial,
  fila de filtros rápidos, listado de publicaciones y barra de navegación inferior.
- **FR-031**: La barra superior MUST llevar, en el lado inicial, el control que abre el panel de
  secciones, seguido del escudo oficial y del nombre de la aplicación; y en el lado final, la acción
  de buscar y la de información. MUST NOT llevar campana de avisos.
- **FR-032**: La cabecera editorial MUST mostrar la denominación de lo que se está viendo, su fecha
  en formato legible en español y un distintivo perfilado con el número de publicaciones.
- **FR-033**: La cabecera MUST NOT mostrar un número de boletín: el servicio oficial no lo publica en
  las fuentes que la aplicación consume.
- **FR-034**: Cuando la selección es el boletín del día, el listado MUST mostrar las publicaciones de
  la fecha más reciente disponible entre todas las secciones.
- **FR-035**: Cuando la selección es una sección o subsección, el listado MUST mostrar las
  publicaciones de esa sección **sin restringirlas a una fecha**, y la cabecera MUST nombrarla.
- **FR-036**: La fila de filtros rápidos MUST desplazarse horizontalmente, MUST reflejar la selección
  vigente y MUST incluir una opción que devuelva al boletín del día.
- **FR-037**: Cada publicación MUST presentarse en una tarjeta que muestre, en este orden, el
  organismo emisor, el título, la fecha con su icono, y las acciones secundarias; con un indicador
  vertical del color de su sección.
- **FR-038**: El indicador de sección MUST ir siempre acompañado de texto, para no depender del color
  como único portador de significado.
- **FR-039**: Durante la primera carga sin contenido guardado, el listado MUST mostrar marcadores con
  la forma del contenido final, en número reducido, en lugar de un indicador giratorio grande.
- **FR-040**: El listado MUST distinguir visualmente «no hay publicaciones» de «se ha producido un
  error», y el estado vacío MUST ofrecer un mensaje propio.
- **FR-041**: La falta de conexión MUST comunicarse mediante un aviso que no oculte el contenido.
- **FR-042**: El listado MUST conservar la posición de lectura ante un cambio de configuración del
  dispositivo.

**Panel de secciones**

- **FR-043**: El panel lateral MUST presentar las nueve secciones principales del BOC con su número y
  su nombre, en el orden oficial.
- **FR-044**: Las secciones con subsecciones MUST poder desplegarse y contraerse, mostrando sus
  subsecciones agrupadas y visualmente subordinadas.
- **FR-045**: El panel MUST ofrecer un campo que filtre secciones y subsecciones por texto.
- **FR-046**: Elegir una sección o subsección MUST cerrar el panel y aplicar la selección al listado.
- **FR-047**: El panel MUST NOT contener campanas de aviso ni tarjeta de alertas: las notificaciones
  quedan fuera del alcance de esta feature.
- **FR-048**: La selección vigente MUST sobrevivir a la destrucción y recreación de la pantalla por
  parte del sistema operativo.

**Marco de navegación y acciones aplazadas**

- **FR-049**: La barra inferior MUST ofrecer exactamente tres destinos: Inicio, Buscar y Guardados.
  MUST NOT incluir el destino de Avisos.
- **FR-050**: La barra inferior MUST indicar cuál es el destino activo mediante forma o peso, además
  del color.
- **FR-051**: Buscar y Guardados MUST ser destinos reales que muestren, con el aspecto de la
  aplicación, que la funcionalidad llegará próximamente.
- **FR-052**: La acción de buscar de la barra superior MUST informar de que la búsqueda llegará
  próximamente.
- **FR-053**: La acción de información de la barra superior MUST estar presente y MUST NOT realizar
  ninguna acción todavía.
- **FR-054**: La acción de compartir de una tarjeta MUST ofrecer las formas habituales del sistema
  para compartir el enlace del documento oficial.
- **FR-055**: La acción de guardar de una tarjeta MUST informar de que llegará próximamente.
- **FR-056**: Pulsar una tarjeta MUST NOT navegar a ningún sitio en esta feature; el detalle de la
  publicación corresponde a la siguiente.
- **FR-057**: El retroceso desde Inicio MUST seguir cerrando la aplicación, sin reaparecer la
  pantalla de arranque.

**Identidad visual**

- **FR-058**: Todo lo que esta feature dibuja MUST usar exclusivamente los valores con nombre del
  sistema de diseño ya implantado: ningún color, tamaño ni espaciado escrito directamente en el
  punto de uso.
- **FR-059**: La aplicación MUST conservar su aspecto único, sin depender del ajuste de tema del
  sistema operativo ni de la personalización cromática del dispositivo.
- **FR-060**: Las cuatro desviaciones respecto al documento de diseño acordado —tres destinos en
  lugar de cuatro, nueva composición de la barra superior, recuento en lugar del número de boletín, y
  las secciones como panel lateral en lugar de pantalla propia— MUST quedar registradas en el propio
  documento de diseño, para que documento y aplicación no se contradigan.

**Verificación**

- **FR-061**: El procesamiento del contenido de las fuentes MUST tener pruebas automáticas que se
  ejecuten sin dispositivo y sin red, cubriendo al menos: fuente vacía, recuento declarado
  incorrecto, campos desconocidos, clasificación ausente, clasificación con tres, cuatro y cinco
  componentes, tipo de edición al principio, en medio y al final, tipo de edición ausente,
  clasificación que no corresponde a la fuente, orden anómalo, fecha inválida, enlace sin
  identificador y título muy largo.
- **FR-062**: El comportamiento de sincronización MUST tener pruebas automáticas que cubran primera
  obtención, segunda sin cambios, publicación nueva, publicación actualizada, publicación que sale de
  la ventana de la fuente, una fuente que falla, todas las fuentes que fallan con y sin contenido
  guardado, y duplicados.
- **FR-063**: Cada modelo de pantalla que introduzca esta feature MUST tener pruebas automáticas sin
  dispositivo.
- **FR-064**: MUST existir pruebas automáticas de interfaz que validen los estados del listado
  —cargando, con contenido, vacío, error y sin conexión—, la composición de la tarjeta, el
  despliegue y filtrado del panel de secciones, y la navegación entre los tres destinos.
- **FR-065**: Las pruebas MUST ser deterministas: sin red real, sin reloj del sistema y sin depender
  del orden de ejecución.

### Key Entities

- **Publicación**: un anuncio del BOC. Guarda su identificador externo y de dónde salió, la fuente de
  la que se obtuvo, su sección y subsección, el título recibido íntegro, el organismo emisor y su
  ruta jerárquica, el tipo de edición, la fecha de publicación, el enlace al documento oficial, la
  clasificación original sin modificar, y las advertencias que se detectaron al normalizarla.
- **Sección del BOC**: una de las nueve categorías oficiales, con su código, su nombre, su orden de
  presentación y, cuando las tiene, sus subsecciones. Las secciones 2, 4, 7 y 8 no tienen fuente
  propia: su contenido es la suma del de sus subsecciones.
- **Fuente del boletín**: cada una de las diecinueve procedencias oficiales, con su identificador, la
  sección y subsección que representa de forma autoritativa, y si está activa.
- **Estado de sincronización de una fuente**: cuándo se consultó por última vez, una huella del
  contenido que devolvió para saber si ha cambiado, y cuántos fallos consecutivos acumula.
- **Selección de Inicio**: lo que la pantalla está mostrando en un instante dado. Es el boletín del
  día o una sección o subsección concreta. Determina el listado y el texto de la cabecera.
- **Resumen de sincronización**: el resultado de una actualización —fuentes consultadas con éxito,
  fuentes fallidas, publicaciones nuevas y actualizadas—, que es lo que permite decidir si mostrar
  contenido, aviso de falta de conexión o mensaje de error.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con contenido ya guardado, la persona ve publicaciones en Inicio en menos de 1 segundo
  desde que la pantalla aparece, con y sin conexión. El tiempo se mide, no se estima.
- **SC-002**: En una instalación limpia con conexión, la persona ve el boletín del día en menos de 15
  segundos desde que abre la aplicación, en un dispositivo de gama media.
- **SC-003**: Sin conexión, la persona accede a todo el contenido consultado con anterioridad sin
  encontrar en ningún momento una pantalla vacía o un error irrecuperable.
- **SC-004**: Ninguna publicación aparece duplicada tras cinco sincronizaciones consecutivas.
- **SC-005**: Ninguna publicación guardada desaparece por dejar de figurar en la fuente oficial. Se
  verifica de forma mecánica.
- **SC-006**: Las diecinueve fuentes oficiales están configuradas y las nueve secciones con sus
  subsecciones se ofrecen para su consulta.
- **SC-007**: Las anomalías conocidas del servicio —una fuente vacía, una fuente sin publicaciones
  recientes, clasificaciones desordenadas— no producen ningún mensaje de error y no descartan ninguna
  publicación válida. Se verifica de forma mecánica con muestras reales conservadas.
- **SC-008**: Desde Inicio, la persona alcanza cualquiera de las nueve secciones o de sus
  subsecciones en tres toques como máximo.
- **SC-009**: Ninguna acción visible deja a la persona sin respuesta: toda acción todavía no
  disponible lo comunica de forma explícita.
- **SC-010**: La pantalla conserva su jerarquía y no recorta el organismo, el título ni la fecha con
  el tamaño de letra del sistema al 200 %.
- **SC-011**: Toda pieza de reglas de negocio y todo modelo de pantalla que introduce esta feature
  tiene su prueba automática. Se verifica de forma mecánica: una comprobación automatizada falla si
  alguno carece de fichero de prueba asociado.
- **SC-012**: Las cuatro comprobaciones de calidad del proyecto —compilación, pruebas sin
  dispositivo, pruebas de interfaz y análisis estático— terminan en verde.

## Assumptions

- **No hay servicio propio intermedio.** El documento de consumo de feeds recomienda un servidor
  central que agregue las fuentes, y califica la lectura directa desde el móvil de solución de
  respaldo. Hoy ese servidor no existe, y se acuerda con el propietario que la aplicación lea las
  fuentes oficiales por su cuenta. Las consecuencias se aceptan a conciencia: no hay histórico más
  allá de lo que cada fuente publica, no hay avisos inmediatos, se genera más tráfico y una
  reinstalación empieza sin contenido. La frontera se coloca de forma que sustituir la procedencia
  por un servicio propio no obligue a rehacer ni la pantalla ni las reglas de negocio.
- **Las decisiones técnicas de red y de persistencia se justifican en el plan.** La constitución las
  dejó deliberadamente abiertas hasta la primera feature que las necesitara, y ésta lo es. La
  especificación describe qué debe ocurrir; el cómo se decide y se argumenta en `plan.md` y
  `research.md`.
- **El almacenamiento local es la única fuente de lo que se muestra.** La pantalla nunca lee de la
  red directamente: observa lo guardado, y la sincronización solo escribe.
- **Las notificaciones quedan fuera por completo.** Los mockups aportados incluyen campanas de aviso
  y una tarjeta de alertas personalizadas; se ignoran a propósito, por decisión del propietario. Se
  abordarán en una feature futura.
- **El detalle de la publicación es la feature siguiente.** Aquí la tarjeta no navega. Tampoco se
  descarga ni se valida el documento en PDF: se conserva su enlace, nada más.
- **Buscar y Guardados son marcadores de posición.** Existen como destinos para que la estructura de
  navegación quede fijada, pero su contenido llegará en features posteriores.
- **La ventana de treinta minutos** para considerar caducado lo guardado es un valor de partida
  razonable para un boletín que se publica una vez al día. Se elige por prudencia con el servicio
  oficial y podrá ajustarse con datos de uso.
- **El documento de diseño manda en lo visual**, y sus cuatro desviaciones se anotan en él en el
  mismo cambio, como se hizo en la feature anterior con el apartado de modo oscuro.
- **Los textos visibles están en español**, y las fechas se muestran en formato largo español.
- **Los colores de sección** del documento de diseño agrupan más de lo que agrupa el BOC: hay cinco
  colores para nueve secciones. Se asigna cada sección al grupo cromático que le corresponde
  conceptualmente y se acompaña siempre de texto, de modo que la agrupación no reste información.
- **La escala de la primera sincronización** ronda las mil novecientas publicaciones, porque la
  mayoría de fuentes devuelve un máximo de cien. Las sincronizaciones siguientes son
  incrementales.
