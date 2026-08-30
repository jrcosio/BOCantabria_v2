# Feature Specification: Detalle de publicación y visor del PDF oficial

**Feature Branch**: `004-detalle-publicacion`

**Created**: 2026-08-30

**Status**: Draft

**Input**: User description: "Nueva feature: que al pulsar en la publicación podamos acceder a ella.
Adjunto `screen_ver_publicacion.png` como referencia de cómo me gustaría que fuera. Las opciones de
Resumen IA y de chat de IA para preguntar ponlas como PRÓXIMAMENTE, ya que eso es la feature
siguiente, pero déjalo ya preparado en la interfaz. El botón de Abrir PDF evidentemente eso hay que
hacerlo, y abre dentro de la app el PDF para que se pueda leer; por lo tanto usa el visor de PDF que
mejor funcione en Jetpack Compose. La opción de Guardar, que también está presente en esta pantalla,
para otra feature futura. La opción de compartir es el PDF el que se envía directamente."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Abrir una publicación y leer el documento oficial (Priority: P1)

Una persona ve en el boletín un anuncio que le interesa y lo toca. Se abre una pantalla que le dice
de un vistazo de qué sección es, cuál es el título completo —sin recortar—, qué organismo lo firma y
cuándo se publicó, con un distintivo que deja claro que aquello es un documento oficial. Debajo tiene
la ficha del anuncio y el principio del documento. Toca «Abrir PDF oficial» y el documento se abre
**dentro de la aplicación**: lo lee, amplía lo que no ve bien y se desplaza por las páginas. Al
volver, sigue en el mismo punto del boletín donde estaba.

**Why this priority**: es la razón por la que existe la feature y el remate de todo lo anterior.
Hasta ahora la aplicación enseña titulares; esto es lo que permite leer el anuncio. Sin esto, la
persona tiene que salir al navegador, que es exactamente lo que la aplicación viene a evitar.

**Independent Test**: abrir el boletín, tocar cualquier tarjeta y comprobar que se llega al detalle
con sus datos, y que desde ahí el documento oficial se lee dentro de la aplicación.

**Acceptance Scenarios**:

1. **Given** el boletín en pantalla, **When** la persona toca una tarjeta, **Then** llega al detalle
   de esa publicación con su sección, su título completo, su organismo, su fecha y el distintivo de
   documento oficial.
2. **Given** un título muy largo, **When** se muestra en el detalle, **Then** se ve **entero**, sin
   recortarse ni terminar en puntos suspensivos.
3. **Given** el detalle a la vista, **When** la persona abre el documento oficial, **Then** el
   documento se muestra dentro de la aplicación y puede ampliarse y recorrerse.
4. **Given** el documento abierto, **When** la persona retrocede dos veces, **Then** vuelve al
   boletín en la misma posición de lectura en la que estaba.
5. **Given** un documento ya consultado antes, **When** la persona vuelve a abrirlo, **Then**
   aparece de inmediato, sin volver a descargarse.

---

### User Story 2 - Que el documento sea de fiar, y que fallar se note (Priority: P1)

La misma persona abre un anuncio cuyo enlace, ese día, no devuelve el documento: el servicio
responde con una página de error, o la conexión se cae a medias. En lugar de ver un visor en blanco,
un documento ilegible o —peor— algo que no es el anuncio, recibe un mensaje claro y puede
reintentar. Nada roto queda guardado para la próxima vez.

**Why this priority**: es tan crítica como la historia 1 porque decide si lo que la persona lee es
el documento oficial o cualquier otra cosa. Una aplicación que consulta un boletín oficial no puede
presentar como oficial algo que no ha comprobado que lo sea.

**Independent Test**: apuntar la aplicación a un enlace que devuelva algo que no sea el documento y
comprobar que da un error comprensible con reintento, y que no deja nada guardado.

**Acceptance Scenarios**:

1. **Given** un enlace que responde correctamente pero **no** devuelve el documento esperado,
   **When** la persona intenta abrirlo, **Then** ve un mensaje comprensible con la acción de
   reintentar, y no se le presenta ningún contenido como si fuera oficial.
2. **Given** una descarga que se interrumpe a medias, **When** la persona lo intenta de nuevo,
   **Then** la descarga se rehace desde el principio y **no** queda un documento incompleto guardado.
3. **Given** un documento desmesuradamente grande, **When** se intenta abrir, **Then** la descarga se
   detiene y se informa, en lugar de agotar la memoria del dispositivo.
4. **Given** el dispositivo sin conexión y el documento nunca consultado, **When** la persona intenta
   abrirlo, **Then** se le explica que hace falta conexión y se le ofrece reintentar.
5. **Given** el dispositivo sin conexión pero el documento ya consultado antes, **When** la persona
   lo abre, **Then** lo lee con normalidad.

---

### User Story 3 - Compartir el documento, no el enlace (Priority: P2)

La persona quiere mandarle el anuncio a alguien. Toca compartir y lo que se envía es **el documento**,
no una dirección que la otra persona tendrá que abrir. Si el documento aún no se ha descargado, la
aplicación lo trae mientras se lo dice, y luego lo comparte. Y si no hay forma de traerlo porque no
hay cobertura, se le ofrece compartir el enlace en lugar de dejarla sin nada.

**Why this priority**: es la petición explícita del propietario y cambia lo que hacía la feature
anterior, que compartía el enlace. Va después de las dos primeras porque necesita que la descarga y
la validación existan.

**Independent Test**: compartir desde el detalle y comprobar que lo que sale es el documento;
repetirlo en modo avión sin haberlo abierto antes y comprobar que ofrece el enlace.

**Acceptance Scenarios**:

1. **Given** un documento ya consultado, **When** la persona comparte, **Then** se ofrece **el
   documento** a las aplicaciones del sistema, de inmediato.
2. **Given** un documento no consultado y con conexión, **When** la persona comparte, **Then** se le
   indica que se está preparando y a continuación se ofrece el documento.
3. **Given** un documento no consultado y sin conexión, **When** la persona comparte, **Then** se le
   ofrece compartir el enlace al documento oficial, explicándole por qué.
4. **Given** cualquiera de los casos anteriores, **When** se comparte, **Then** la aplicación que
   recibe puede abrir lo compartido sin permisos adicionales.

---

### User Story 4 - Saber qué llegará y no toparse con callejones (Priority: P3)

La persona ve tres pestañas —Documento, Resumen IA y Preguntar— y, abajo, un botón para preguntar
sobre el anuncio. Las dos de inteligencia artificial y el botón de guardar todavía no hacen su
trabajo, pero lo dicen con claridad y conservan su aspecto, de modo que se entiende que llegarán y no
que están rotas.

**Why this priority**: no aporta valor por sí misma, pero deja fijada la estructura de la pantalla
para la feature siguiente y evita que la aplicación se perciba como incompleta.

**Independent Test**: recorrer las tres pestañas y las dos acciones aplazadas comprobando que
ninguna deja a la persona sin respuesta.

**Acceptance Scenarios**:

1. **Given** el detalle a la vista, **When** la persona abre la pestaña de resumen o la de preguntar,
   **Then** ve que esa función llegará próximamente, con el icono y la etiqueta que identifican el
   contenido de inteligencia artificial.
2. **Given** el detalle a la vista, **When** la persona usa el botón de preguntar de la barra
   inferior, **Then** recibe la misma respuesta.
3. **Given** el detalle a la vista, **When** la persona usa la acción de guardar, **Then** se le
   informa de que llegará próximamente.
4. **Given** cualquiera de las anteriores, **When** ocurre, **Then** la acción de abrir el documento
   sigue siendo la más destacada de la pantalla.

---

### Edge Cases

- **Publicación que ya no está guardada**: se llega al detalle de un anuncio que una limpieza de
  datos ha retirado. Debe explicarse, no mostrarse una pantalla vacía.
- **Documento de una sola página**: la previsualización y el visor deben comportarse igual que con
  uno de cincuenta.
- **Documento con muchas páginas**: recorrerlo no puede agotar la memoria ni bloquear la interfaz.
- **Documento protegido o ilegible**: si el visor no puede abrirlo, se explica y se ofrece salir.
- **Dos aperturas seguidas del mismo documento**: no deben lanzarse dos descargas a la vez.
- **Salir mientras se descarga**: la descarga se cancela y no deja nada a medias.
- **Espacio de almacenamiento agotado**: se informa en lugar de fallar de forma opaca.
- **Publicación sin organismo**: la cabecera se compone igual, sin dejar un hueco ni un texto vacío.
- **Giro o cambio de configuración con el documento abierto**: no se pierde la página ni el nivel de
  ampliación, y no se vuelve a descargar.
- **Tamaño de letra del sistema al 200 %**: el título no se recorta y los botones de la barra de
  acciones se apilan si no caben.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso al detalle**

- **FR-001**: Pulsar una tarjeta de publicación MUST llevar al detalle de esa publicación.
- **FR-002**: El detalle MUST identificar la publicación por su identificador estable, y MUST
  obtener sus datos de lo que la aplicación tiene guardado, no de lo que le pase quien navega.
- **FR-003**: El detalle MUST reflejar los cambios que una sincronización posterior haga sobre esa
  publicación, sin necesidad de volver a entrar.
- **FR-004**: Si la publicación ya no existe entre lo guardado, el detalle MUST explicarlo y ofrecer
  volver.
- **FR-005**: El retroceso desde el detalle MUST devolver al boletín conservando su posición de
  lectura y la sección seleccionada.
- **FR-006**: El detalle MUST NOT mostrar la barra de navegación inferior: tiene su propia barra de
  acciones.

**Cabecera del documento**

- **FR-007**: La cabecera MUST presentar, en este orden: etiqueta de sección, título, organismo,
  fecha y distintivo de documento oficial.
- **FR-008**: El título MUST mostrarse **completo**, sin recortarlo.
- **FR-009**: El organismo y la fecha MUST ir acompañados de un icono que los identifique.
- **FR-010**: Cuando la publicación no tenga organismo, la cabecera MUST componerse sin dejar hueco
  ni texto vacío.
- **FR-011**: La barra superior MUST llevar retroceso, escudo, el título de la pantalla y las
  acciones de guardar y compartir.

**Pestañas**

- **FR-012**: El detalle MUST ofrecer tres pestañas: documento, resumen de inteligencia artificial y
  preguntar.
- **FR-013**: La pestaña de documento MUST mostrar una ficha con los datos del anuncio —descripción,
  organismo, sección, fecha de publicación, referencia y documento oficial— y, debajo, la primera
  página del documento oficial como previsualización.
- **FR-014**: Las pestañas de resumen y de preguntar MUST informar de que la función llegará
  próximamente, conservando el icono y la etiqueta que identifican el contenido de inteligencia
  artificial.
- **FR-015**: La pestaña seleccionada MUST sobrevivir a un cambio de configuración del dispositivo.

**El documento oficial**

- **FR-016**: La aplicación MUST obtener el documento oficial desde el enlace que la publicación
  trae, y MUST rechazarlo si el enlace no usa un canal seguro o no apunta al servicio del boletín.
- **FR-017**: La aplicación MUST comprobar, antes de dar por bueno un documento, que el servicio lo
  declara como documento portátil **y** que su contenido realmente lo es. Una respuesta correcta que
  contenga otra cosa MUST rechazarse.
- **FR-018**: La descarga MUST tener un límite de tamaño y MUST detenerse al superarlo, sin cargar la
  respuesta entera en memoria.
- **FR-019**: Una descarga interrumpida o rechazada MUST NOT dejar ningún documento guardado.
- **FR-020**: La aplicación MUST calcular y guardar una suma de verificación del documento obtenido.
- **FR-021**: Un documento ya obtenido MUST reutilizarse sin volver a descargarlo.
- **FR-022**: Dos peticiones simultáneas del mismo documento MUST NOT producir dos descargas.
- **FR-023**: Abandonar la pantalla durante una descarga MUST cancelarla.
- **FR-024**: Lo guardado es **caché**: la aplicación MUST poder liberar espacio retirando documentos
  por antigüedad de uso y por tope de tamaño, y MUST NOT presentarlo a la persona como una biblioteca
  de documentos guardados.
- **FR-025**: Los fallos al obtener el documento MUST comunicarse con un mensaje comprensible y la
  acción de reintentar, con el mismo estilo que el resto de errores de la aplicación.

**Visor**

- **FR-026**: Abrir el documento oficial MUST mostrarlo **dentro de la aplicación**, sin delegar en
  otra aplicación ni en el navegador.
- **FR-027**: El visor MUST permitir ampliar y recorrer el documento con los gestos habituales.
- **FR-028**: El visor MUST ocupar una pantalla propia, con retroceso, el título abreviado del
  documento y la acción de compartir.
- **FR-029**: El visor MUST conservar la página y la ampliación ante un cambio de configuración del
  dispositivo.
- **FR-030**: Un documento que el visor no pueda abrir MUST producir un mensaje claro y una salida,
  nunca una pantalla en blanco.

**Compartir**

- **FR-031**: Compartir MUST ofrecer el **documento**, no su enlace, cuando el documento esté
  disponible o pueda obtenerse.
- **FR-032**: Cuando el documento no esté disponible y haya que obtenerlo, la aplicación MUST
  indicar que lo está preparando.
- **FR-033**: Cuando el documento no pueda obtenerse por falta de conexión, la aplicación MUST
  ofrecer compartir el enlace, explicando por qué.
- **FR-034**: Lo compartido MUST poder abrirse por la aplicación que lo recibe sin exigirle permisos
  adicionales, y sin exponerle nada más que ese documento.

**Funciones aplazadas**

- **FR-035**: La acción de guardar MUST informar de que llegará próximamente.
- **FR-036**: El botón de preguntar de la barra de acciones MUST informar de que llegará
  próximamente.
- **FR-037**: Abrir el documento oficial MUST ser la acción más destacada de la pantalla.
- **FR-038**: Ninguna acción visible MUST quedarse sin respuesta.

**Alcance de la plataforma**

- **FR-039**: La versión mínima de Android soportada MUST pasar de 24 a 28, y el cambio MUST quedar
  registrado como enmienda de las normas del proyecto, con su motivo y su nueva versión.
- **FR-040**: Los mecanismos de compatibilidad que la versión mínima anterior obligaba a mantener y
  que dejen de ser necesarios MUST retirarse, no quedarse por inercia.

**Identidad visual**

- **FR-041**: Todo lo que esta feature dibuja MUST usar exclusivamente los valores con nombre del
  sistema de diseño: ningún color, tamaño ni espaciado escrito en el punto de uso.
- **FR-042**: El contenido de inteligencia artificial MUST distinguirse por icono y etiqueta, no
  solo por color.
- **FR-043**: Todos los controles MUST respetar un área táctil mínima de 48 × 48 dp.

**Verificación**

- **FR-044**: La obtención y validación del documento MUST tener pruebas automáticas que se ejecuten
  sin dispositivo y sin red, cubriendo al menos: documento correcto; respuesta correcta cuyo
  contenido no es un documento portátil; tipo declarado inesperado; contenido cuyos primeros bytes
  no corresponden; tamaño por encima del límite; enlace que no usa canal seguro; enlace que no
  apunta al servicio del boletín; y error del servicio.
- **FR-045**: El comportamiento de la caché MUST tener pruebas automáticas que cubran reutilización,
  no dejar restos tras un fallo, y retirada por antigüedad y por tope.
- **FR-046**: Cada modelo de pantalla que introduzca esta feature MUST tener pruebas automáticas sin
  dispositivo.
- **FR-047**: MUST existir pruebas automáticas de interfaz que validen la composición de la cabecera
  en su orden, que el título largo no se recorta, las tres pestañas con su contenido, la barra de
  acciones, los estados del visor y la navegación desde la tarjeta hasta el documento.
- **FR-048**: Las pruebas MUST ser deterministas: sin red real, sin reloj del sistema y sin depender
  del orden de ejecución.

### Key Entities

- **Documento oficial**: la copia local del PDF de una publicación. Guarda a qué publicación
  pertenece, dónde está en el dispositivo, cuánto ocupa, su suma de verificación y cuándo se usó por
  última vez. Es contenido de caché: puede desaparecer sin que se pierda nada.
- **Estado del documento**: en qué punto está la copia local para una publicación —ausente,
  obteniéndose, disponible o fallida—. Es lo que el detalle y el visor observan para saber qué
  dibujar.
- **Selección de pestaña**: cuál de las tres pestañas está activa. Sobrevive a un cambio de
  configuración.
- **Resultado de compartir**: qué se acabó ofreciendo —el documento o su enlace— y por qué. Es lo
  que permite explicar a la persona el caso degradado en lugar de que le sorprenda.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde el boletín, la persona llega al detalle de una publicación en **un solo toque**.
- **SC-002**: Un documento ya consultado se abre en **menos de 1 segundo**, sin red. El tiempo se
  mide, no se estima.
- **SC-003**: Un documento no consultado, con una conexión normal, se abre en **menos de 10
  segundos**, informando durante la espera.
- **SC-004**: Ninguna respuesta que no sea el documento oficial llega a presentarse como tal. Se
  verifica de forma mecánica con respuestas fabricadas para engañar.
- **SC-005**: Ninguna descarga fallida o interrumpida deja restos en el dispositivo. Se verifica de
  forma mecánica.
- **SC-006**: Recorrer un documento de cincuenta páginas no agota la memoria de un dispositivo de
  gama media ni bloquea la interfaz.
- **SC-007**: Compartir entrega el documento en todos los casos en que exista o pueda obtenerse; en
  los demás, ofrece el enlace con una explicación. Nunca deja a la persona sin nada.
- **SC-008**: El título de la publicación se lee completo en el detalle, también con el tamaño de
  letra del sistema al 200 %.
- **SC-009**: Ninguna acción visible deja a la persona sin respuesta: toda acción aplazada lo dice.
- **SC-010**: Toda pieza de reglas de negocio y todo modelo de pantalla que introduce esta feature
  tiene su prueba automática. Se verifica de forma mecánica.
- **SC-011**: Las cuatro comprobaciones de calidad del proyecto —compilación, pruebas sin
  dispositivo, pruebas de interfaz y análisis estático— terminan en verde.
- **SC-012**: El cambio de versión mínima queda registrado en las normas del proyecto con su motivo,
  y la guía operativa no las contradice.

## Assumptions

- **La versión mínima sube de 24 a 28, y es una enmienda de las normas.** El propietario la aprobó
  antes de escribir esta especificación. El motivo es que el visor de documentos que mejor funciona
  en el marco de interfaz del proyecto lo exige. Deja fuera Android 7 y 8, en torno al 2 % de los
  dispositivos en 2026. La enmienda se hace en esta misma rama y **antes** de planificar, para que
  el plan se evalúe contra las normas ya enmendadas.
- **El enlace del anuncio devuelve el documento directamente.** Así lo documenta el fichero de
  consumo de feeds y así se observó: no hay página intermedia. Si algún día la hubiera, la validación
  de contenido de FR-017 lo detectaría y lo rechazaría, que es el comportamiento correcto.
- **Lo descargado es caché, no biblioteca.** Guardar publicaciones para consultarlas sin conexión es
  la funcionalidad de Guardados, que es futura. Meterla aquí sería adelantar una decisión que no
  toca.
- **La previsualización de la primera página obliga a obtener el documento.** Se obtiene al entrar en
  la pestaña de documento, no al abrir el detalle, para no gastar datos de la persona en un anuncio
  que quizá solo quería ojear.
- **El resumen de inteligencia artificial y el chat quedan fuera.** Aquí solo se reserva su sitio en
  la interfaz. Tampoco entra buscar dentro del documento.
- **El texto del documento no se extrae.** La aplicación muestra el documento, no lo convierte a
  texto. El modo lectura que describe el documento de diseño queda para cuando exista esa extracción.
- **Los textos visibles están en español**, y las fechas se muestran en formato largo español.
- **La acción de compartir cambia respecto a la feature anterior**, que enviaba el enlace. Es un
  cambio pedido explícitamente por el propietario.
