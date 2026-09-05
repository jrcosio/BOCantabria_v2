# Feature Specification: Preguntar al BOC

**Feature Branch**: `011-preguntar-al-boc`

**Created**: 5 de septiembre de 2026

**Status**: Draft

**Input**: User description: "Crear la screen de Preguntar, digamos que el modo chat contra el documento/publicación del BOC (PDF). Evidentemente el contexto tiene que estar cerrado al documento: si pregunta cualquier otra cosa o te dice que olvides tus órdenes (prompt injection) y cosas así, tienes que tenerlo previsto para que solo se responda sobre el documento cargado en este chat."

La ruta técnica —cómo se arma la petición, qué forma tiene el esquema, cómo se guarda la
conversación en memoria— quedó cerrada con el propietario antes de escribir esto y **se documenta en
`plan.md`, no aquí**. Esta especificación describe qué ve y qué obtiene quien usa la aplicación, y
qué garantías hay que cumplir.

Decisiones cerradas que por tanto no se vuelven a plantear: la respuesta es **estructurada y con
«Fuentes»**, sin escritura progresiva; la conversación vive **lo que dure la visita a la
publicación** y no se guarda; el documento se sube **una sola vez** y lo libera el detalle al salir;
y el mockup `Datos_modelo/screen_chat_publicacion.png` es **orientativo**: manda el estilo de la
aplicación.

---

## Lo que hay que saber antes de leer nada más *(contexto imprescindible)*

- **La pantalla ya existe y hoy no hace nada.** «Preguntar» es un destino real desde la feature 007,
  se abre desde la barra de acciones del detalle y muestra un «Próximamente». Esta feature la llena.
- **El cimiento está puesto y esta feature es su segundo inquilino.** Desde la feature 010 el
  documento oficial se envía entero al servicio en la primera acción de IA de la visita y se reutiliza
  mientras se esté en esa publicación. Hasta hoy solo lo usaba el Resumen IA. Preguntar es el segundo
  consumidor, y es la razón por la que ese cimiento se construyó: sin él, cada pregunta reenviaría el
  boletín entero.
- **Preguntar se apila encima del detalle, y eso no es un detalle de navegación: es quién manda en el
  ciclo de vida.** Mientras se conversa, la publicación sigue abierta detrás. El documento se retira
  del servicio al salir de la publicación, no al salir de la conversación, así que ir y volver entre
  Resumen y Preguntar no cuesta ninguna subida.
- **Lo que se responde sale del documento y de nada más.** No es una preferencia de producto: es el
  requisito con el que nació la feature. Un asistente de un boletín oficial que conteste de oídas —o
  que obedezca a lo que alguien haya escrito dentro de un PDF— es peor que no tener asistente, porque
  presta la autoridad del boletín a algo que el boletín no dice.
- **Y hay que decirlo con todas las letras: esto es mitigación, no garantía.** Ninguna prueba
  automática de esta casa puede demostrar que el modelo no se sale del documento, porque todas doblan
  la frontera con el servicio y el comportamiento está justo al otro lado. Lo que sí es demostrable
  —y se exige— es que **cuando la respuesta se declara fuera de ámbito, lo que se lee en pantalla es
  texto nuestro**. Esa es la diferencia entre una esperanza y un mecanismo.
- **Preguntar gasta cuota igual que resumir.** Es el mismo servicio, el mismo plan gratuito y el mismo
  contador. Una conversación larga se nota; el aviso de límite alcanzado tiene que ser el mismo que ya
  existe y estar dicho igual de claro.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Preguntar y obtener una respuesta con sus páginas (Priority: P1)

Alguien está leyendo una convocatoria de treinta páginas y solo quiere saber el plazo. Abre
«Preguntar», escribe «¿cuál es el plazo de presentación?» y recibe una respuesta corta que además le
dice **en qué páginas lo pone**. Toca una de esas páginas y aterriza en el documento oficial, en ese
punto.

**Why this priority**: Es la feature. Sin esto no hay nada; con esto solo, ya vale la pena.

**Independent Test**: Con el documento ya preparado, enviar una pregunta y comprobar que aparece la
pregunta, aparece la respuesta y las fuentes llevan al visor en la página citada.

**Acceptance Scenarios**:

1. **Given** una publicación abierta con su documento ya preparado, **When** se escribe una pregunta
   y se envía, **Then** la pregunta aparece de inmediato como mensaje propio y a continuación aparece
   la respuesta.
2. **Given** una respuesta que cita páginas del documento, **When** se toca una de las fuentes,
   **Then** se abre el documento oficial en esa página.
3. **Given** una respuesta en curso, **When** todavía no ha llegado, **Then** se ve que el asistente
   está trabajando y no se puede enviar otra pregunta a la vez.
4. **Given** una pregunta cuya contestación no está en el documento, **When** se envía, **Then** la
   respuesta dice que el documento no lo recoge, y **no** ofrece una contestación sacada de otro
   sitio.

---

### User Story 2 - Solo se habla de este documento (Priority: P1)

Alguien —o algo escrito dentro del propio PDF— intenta llevar la conversación a otra parte: pedir un
poema, una traducción, saber qué sistema hay detrás, o dar una orden del tipo «ignora tus
instrucciones anteriores». La conversación no se mueve del documento y quien lo intenta recibe una
negativa clara y educada.

**Why this priority**: El propietario lo puso como condición de la feature, no como mejora. Y es lo
que separa un asistente de un boletín oficial de un chatbot con un PDF al lado.

**Independent Test**: Con un doble del servicio que devuelva una respuesta marcada como fuera de
ámbito, comprobar que en pantalla se lee **el texto de la aplicación** y no el del servicio. La
eficacia real frente al servicio de verdad se comprueba a mano, con la batería de intentos del
`quickstart.md`.

**Acceptance Scenarios**:

1. **Given** cualquier estado de la conversación, **When** se pide algo ajeno al documento, **Then**
   se responde que solo se puede hablar de esta publicación, con un texto propio de la aplicación.
2. **Given** una respuesta que el servicio marca como fuera de ámbito, **When** se muestra, **Then**
   lo que se lee es el texto de la aplicación y **nada** del texto devuelto por el servicio.
3. **Given** un documento que contiene instrucciones escritas dentro dirigidas al asistente, **When**
   se hace cualquier pregunta, **Then** esas instrucciones se tratan como contenido del documento y no
   se obedecen.
4. **Given** una pregunta que intenta que se revelen las reglas del asistente, **When** se envía,
   **Then** no se revelan.

---

### User Story 3 - Entrar a preguntar sin haber pedido el resumen (Priority: P1)

Alguien abre una publicación y va directo a «Preguntar» sin tocar «Resumen IA». El documento aún no
está preparado, así que se prepara en ese momento: se ve que está pasando, y en cuanto está listo la
pregunta sigue su curso.

**Why this priority**: Es la mitad de los caminos de entrada. Si solo funcionara viniendo del resumen,
la pantalla estaría rota para quien entra directo.

**Independent Test**: Abrir Preguntar sin pasar por el resumen, enviar una pregunta y comprobar que
aparece la fase de preparación y después la respuesta.

**Acceptance Scenarios**:

1. **Given** una publicación cuyo documento no se ha preparado todavía, **When** se envía la primera
   pregunta, **Then** se ve que el documento se está preparando y después llega la respuesta.
2. **Given** una publicación cuyo documento ya se preparó al pedir el resumen, **When** se entra a
   Preguntar y se envían tres preguntas, **Then** el documento **no** vuelve a prepararse ninguna vez.
3. **Given** el documento no se ha podido preparar, **When** se intenta preguntar, **Then** se explica
   en lenguaje corriente y se ofrece reintentar.

---

### User Story 4 - La conversación dura lo que dura la visita (Priority: P2)

Alguien pregunta un par de cosas, vuelve al detalle a mirar la ficha, entra otra vez a Preguntar y
**la conversación sigue ahí**. Cuando sale de la publicación, la conversación se descarta y el
documento se retira del servicio.

**Why this priority**: Es lo que hace que la pantalla se sienta parte de la publicación y no una
herramienta aparte. Y es la promesa de privacidad: lo enviado no se queda.

**Independent Test**: Preguntar, volver atrás al detalle, entrar de nuevo y comprobar que los
mensajes anteriores siguen; salir de la publicación, volver a entrar y comprobar que la conversación
empieza vacía.

**Acceptance Scenarios**:

1. **Given** una conversación con mensajes, **When** se vuelve al detalle y se entra otra vez a
   Preguntar, **Then** los mensajes siguen ahí.
2. **Given** una conversación con mensajes, **When** se sale de la publicación y se vuelve a entrar,
   **Then** la conversación está vacía.
3. **Given** una conversación con mensajes, **When** se sale de la publicación, **Then** el documento
   se retira del servicio.
4. **Given** una conversación de una publicación, **When** se abre otra publicación distinta,
   **Then** su conversación empieza vacía y no arrastra nada de la anterior.

---

### User Story 5 - Cuando algo va mal, se entiende (Priority: P2)

No hay conexión, se ha agotado el cupo del día, o el servicio no contesta. Quien pregunta lee una
frase en castellano que dice qué pasa y qué puede hacer, sin códigos, sin nombres de proveedor y sin
jerga.

**Why this priority**: Es el estado más frecuente después del feliz, y es donde una aplicación se
gana o se pierde la confianza.

**Independent Test**: Provocar cada fallo con un doble del servicio y comprobar el mensaje y la
presencia o ausencia del botón de reintentar.

**Acceptance Scenarios**:

1. **Given** que no hay conexión, **When** se envía una pregunta, **Then** se dice que no hay conexión
   y se puede reintentar.
2. **Given** que se ha alcanzado el límite de uso, **When** se envía una pregunta, **Then** se explica
   que se ha alcanzado el límite y cuándo tiene sentido volver, sin códigos.
3. **Given** que la aplicación no tiene configurado el acceso al servicio, **When** se abre Preguntar,
   **Then** se dice que no está disponible y **no** se permite enviar preguntas.
4. **Given** un fallo del que se puede reintentar, **When** se pulsa reintentar, **Then** se reenvía
   **la misma** pregunta y no se pierde lo escrito.

---

### User Story 6 - La pantalla se parece a la aplicación (Priority: P3)

Quien llega desde el detalle no siente que ha cambiado de aplicación: los mismos azules, la misma
tipografía, la misma cabecera con el escudo, la publicación identificada arriba y el documento
oficial a un toque.

**Why this priority**: El mockup es de otra aplicación. Sin este requisito por escrito, lo fácil es
copiarlo tal cual y romper la unidad visual que las diez features anteriores construyeron.

**Independent Test**: Abrir la pantalla y comprobar que la cabecera de la publicación, el aviso de
ámbito, las preguntas sugeridas, el compositor y el enlace al documento están donde deben, y que el
compositor no queda tapado por la barra de navegación del sistema ni por el teclado.

**Acceptance Scenarios**:

1. **Given** la pantalla abierta, **When** se mira, **Then** se ve el título y la fecha de la
   publicación, y se puede guardarla o desguardarla sin salir.
2. **Given** la conversación vacía, **When** se abre, **Then** se ofrecen tres preguntas sugeridas que
   se pueden tocar para enviarlas.
3. **Given** la conversación con al menos un mensaje, **When** se mira, **Then** las preguntas
   sugeridas ya no ocupan sitio.
4. **Given** el teclado abierto, **When** se escribe, **Then** el campo de texto sigue visible y por
   encima del teclado.
5. **Given** un teléfono con navegación de tres botones, **When** se mira el pie, **Then** el campo de
   texto no queda debajo de la barra del sistema.

---

### Edge Cases

- **Pregunta vacía o solo espacios**: no se envía y no gasta nada.
- **Pregunta larguísima**: se limita, y quien escribe lo ve antes de enviar, no después.
- **Respuesta que cita una página que no existe** (el documento tiene 9 y cita la 14): esa cita se
  descarta; si no queda ninguna, la respuesta se muestra sin bloque de fuentes.
- **Respuesta en blanco**: se trata como fallo, no se muestra una burbuja vacía.
- **Documento protegido con contraseña**: se dice que no se puede leer y no se permite preguntar.
- **Se sale de la pantalla mientras se espera una respuesta**: no se muestra ningún error al volver;
  una salida no es un fallo.
- **Se gira o se recompone la pantalla mientras se espera**: no se envía la pregunta dos veces.
- **El documento no se consigue descargar**: no se puede preguntar, se dice por qué y se puede reintentar.
- **Muerte del proceso con la conversación abierta**: la conversación se pierde; es el
  comportamiento esperado y no debe dejar la pantalla en un estado roto.

## Requirements *(mandatory)*

### Functional Requirements

**La conversación**

- **FR-001**: La aplicación MUST ofrecer, dentro de una publicación, una pantalla de conversación
  sobre el documento oficial de esa publicación.
- **FR-002**: La aplicación MUST mostrar cada pregunta enviada como mensaje propio, en el orden en que
  se envió, antes de que llegue la respuesta.
- **FR-003**: La aplicación MUST mostrar la respuesta como mensaje del asistente, distinguible a
  simple vista del mensaje propio.
- **FR-004**: La aplicación MUST indicar mientras se espera que la respuesta se está preparando.
- **FR-005**: La aplicación MUST impedir enviar una segunda pregunta mientras la anterior está en
  curso.
- **FR-006**: La aplicación MUST rechazar sin enviar una pregunta vacía o compuesta solo de espacios.
- **FR-007**: La aplicación MUST limitar la longitud de la pregunta y hacer visible ese límite antes
  de enviar.
- **FR-008**: La aplicación MUST conservar la conversación mientras se permanezca en la publicación,
  incluyendo ir al detalle y volver.
- **FR-009**: La aplicación MUST descartar la conversación al salir de la publicación.
- **FR-010**: La aplicación MUST NOT guardar la conversación de forma permanente: al volver a entrar
  en la publicación, la conversación empieza vacía.
- **FR-011**: La aplicación MUST NOT mezclar conversaciones de publicaciones distintas.

**Las fuentes**

- **FR-012**: La aplicación MUST mostrar, cuando la respuesta las traiga, las páginas del documento en
  las que se apoya, cada una con una etiqueta que diga de qué trata.
- **FR-013**: Quien usa la aplicación MUST poder abrir el documento oficial en la página citada
  tocando la fuente.
- **FR-014**: La aplicación MUST descartar toda cita a una página que no exista en el documento.
- **FR-015**: La aplicación MUST mostrar la respuesta sin bloque de fuentes cuando no quede ninguna
  cita válida, en lugar de ocultar la respuesta.

**Que solo se hable del documento**

- **FR-016**: La aplicación MUST instruir al servicio de que la única fuente admisible es el documento
  adjunto de esa publicación.
- **FR-017**: La aplicación MUST hacer que lo que el documento no recoja se diga como tal, en lugar de
  completarse con conocimiento ajeno al documento.
- **FR-018**: La aplicación MUST tratar el contenido del documento como datos y nunca como
  instrucciones dirigidas al asistente.
- **FR-019**: La aplicación MUST tratar el texto de la pregunta como datos y nunca como instrucciones
  dirigidas al asistente.
- **FR-020**: La aplicación MUST obtener de cada respuesta una declaración explícita de si trata sobre
  el documento, si el documento no lo recoge, o si la petición era ajena al documento.
- **FR-021**: Cuando la respuesta se declare ajena al documento, la aplicación MUST mostrar **un texto
  propio de la aplicación** y MUST NOT mostrar el texto devuelto por el servicio.
- **FR-022**: La aplicación MUST NOT revelar las reglas de funcionamiento del asistente aunque se
  pidan.
- **FR-023**: La aplicación MUST rechazar una respuesta cuyo cuerpo esté en blanco, en lugar de
  mostrar un mensaje vacío.
- **FR-024**: La aplicación MUST NOT enviar al servicio ningún dato de la persona: ni sus
  publicaciones guardadas, ni conversaciones de otras publicaciones, ni identificador alguno.

**El documento**

- **FR-025**: La aplicación MUST preparar el documento la primera vez que hace falta en la visita, sea
  desde el resumen o desde la conversación.
- **FR-026**: La aplicación MUST reutilizar el documento ya preparado para todas las preguntas
  siguientes de la misma visita, sin volver a enviarlo.
- **FR-027**: La aplicación MUST mostrar que el documento se está preparando cuando esa preparación
  ocurra al enviar la primera pregunta.
- **FR-028**: La aplicación MUST retirar el documento del servicio al salir de la publicación, tanto
  si se usó para resumir como si se usó para conversar.
- **FR-029**: La aplicación MUST impedir preguntar cuando el documento esté protegido con contraseña,
  y decirlo en lenguaje corriente.
- **FR-030**: La aplicación MUST impedir preguntar cuando el documento oficial no se pueda obtener, y
  decirlo en lenguaje corriente. *(Reescrito al planificar. Decía «cuando la publicación no tenga
  documento disponible», y ese estado **no existe**: toda publicación del boletín trae la dirección de
  su documento. Lo que sí puede ocurrir es que no se consiga descargar. Ver `research.md` D-321.)*

**Errores, cuota y credencial**

- **FR-031**: La aplicación MUST expresar todo fallo en castellano corriente, sin códigos de error, sin
  nombres de proveedor y sin jerga técnica.
- **FR-032**: La aplicación MUST ofrecer reintentar cuando el fallo lo admita, y no ofrecerlo cuando no.
- **FR-033**: Al reintentar, la aplicación MUST reenviar la misma pregunta sin obligar a reescribirla.
- **FR-034**: La aplicación MUST contabilizar las preguntas en el mismo control de consumo que los
  resúmenes.
- **FR-035**: La aplicación MUST decir cuándo se ha alcanzado el límite de uso y desde cuándo tiene
  sentido volver a intentarlo.
- **FR-036**: La aplicación MUST indicar que la conversación no está disponible cuando no haya acceso
  configurado al servicio, y MUST NOT permitir enviar preguntas en ese caso.
- **FR-037**: La aplicación MUST tratar la salida de la pantalla durante una espera como una
  cancelación y no como un fallo: al volver no se muestra ningún error.

**Privacidad y registro**

- **FR-038**: La aplicación MUST NOT registrar nunca la credencial del servicio, ni en el registro del
  dispositivo, ni en informes de fallos, ni en analítica.
- **FR-039**: La aplicación MUST NOT registrar nunca el contenido del documento ni el texto de las
  preguntas ni el de las respuestas.
- **FR-040**: La aplicación MUST dejar en el registro del dispositivo lo suficiente para distinguir
  entre fallo de red, límite alcanzado, respuesta malformada y respuesta rechazada por ámbito.
- **FR-041**: La aplicación MUST mostrar de forma permanente en la pantalla que las respuestas se
  basan únicamente en ese documento.
- **FR-042**: La aplicación MUST mantener el aviso de envío a un servicio externo antes de la primera
  acción de IA de quien no lo haya aceptado, igual que hace el resumen, sin pedirlo dos veces.

**La pantalla**

- **FR-043**: La aplicación MUST identificar arriba la publicación por su título y su fecha.
- **FR-044**: Quien usa la aplicación MUST poder guardar o desguardar la publicación desde esta
  pantalla.
- **FR-045**: La aplicación MUST ofrecer preguntas sugeridas mientras la conversación esté vacía, y
  MUST dejar de ocuparse de ellas en cuanto haya algún mensaje.
- **FR-046**: La aplicación MUST permitir abrir el documento oficial completo desde esta pantalla.
- **FR-047**: La aplicación MUST permitir volver a la publicación con el gesto y el control de
  retroceso habituales.
- **FR-048**: La aplicación MUST mantener visible el campo de escritura por encima del teclado y por
  encima de la barra de navegación del sistema.
- **FR-049**: La aplicación MUST usar el sistema de diseño de la aplicación —su color, su tipografía y
  sus espaciados— y MUST NOT introducir un aspecto distinto al del resto de pantallas.
- **FR-050**: La aplicación MUST NOT enviar la misma pregunta dos veces por una recomposición o un
  cambio de configuración.

### Key Entities

- **Conversación**: lo hablado sobre una publicación durante una visita. Tiene una publicación, una
  lista ordenada de mensajes y un estado (en reposo, preparando el documento, esperando respuesta,
  fallida). No sobrevive a la salida de la publicación.
- **Mensaje**: o una pregunta de quien usa la aplicación, o una respuesta del asistente. Lleva su
  texto y la hora.
- **Respuesta**: además del texto, lleva su **ámbito** —del documento, no está en el documento, ajena
  al documento— y una lista de **fuentes**.
- **Fuente**: una página del documento y una etiqueta corta que dice de qué trata esa página. Se puede
  abrir.
- **Documento preparado**: el documento oficial ya disponible para el servicio durante esta visita.
  Es el mismo que usa el resumen; esta feature lo comparte, no lo duplica.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde el detalle de una publicación, se obtiene la respuesta a una pregunta sencilla en
  **menos de tres toques** —Preguntar, escribir, enviar— sin salir de la publicación.
- **SC-002**: **Tres preguntas seguidas** sobre la misma publicación consumen **una sola** preparación
  del documento.
- **SC-003**: Al salir de la publicación, el documento queda **retirado del servicio**, comprobable en
  el registro del dispositivo.
- **SC-004**: **El 100 %** de las respuestas marcadas como ajenas al documento muestran texto de la
  aplicación y **cero** caracteres del texto devuelto por el servicio.
- **SC-005**: **Cero** citas a páginas inexistentes llegan a la pantalla.
- **SC-006**: **Ningún** mensaje de error visible contiene un código, un número de estado o el nombre
  del proveedor.
- **SC-007**: **Cero** apariciones de la credencial, del contenido del documento o del texto de las
  preguntas en el registro del dispositivo, en informes de fallos y en analítica.
- **SC-008**: La conversación sobrevive a **ir al detalle y volver** y no sobrevive a **salir de la
  publicación**, ambas cosas comprobables sin herramientas.
- **SC-009**: La batería manual de intentos de desvío del `quickstart.md` —al menos cinco intentos
  distintos, incluido un documento con instrucciones inyectadas— se responde **siempre** dentro del
  documento o con la negativa de la aplicación, **nunca** con la petición ajena cumplida.
- **SC-010**: La aplicación compila y **todas** sus pruebas pasan **sin credencial** configurada.
- **SC-011**: El campo de escritura es visible y utilizable con el teclado abierto en un teléfono con
  navegación de tres botones, **sin** quedar tapado.
- **SC-012**: La aplicación **no crece de forma apreciable**: cero dependencias nuevas.

## Fuera de alcance

Se dice en voz alta para que no se cuele por el camino:

- **Guardar la conversación** entre visitas o entre arranques de la aplicación. Persistirla exigiría
  tabla nueva, migración y —tarde o temprano— la primera sentencia de borrado del proyecto.
- **Compartir o copiar** una respuesta.
- **Adjuntar más de un documento** o preguntar sobre varias publicaciones a la vez.
- **Buscar dentro de la conversación.**
- **La escritura progresiva** de la respuesta.
- **Editar o borrar** un mensaje ya enviado.
- **El doble check de leído y el menú de tres puntos** del mockup: el primero no significa nada aquí
  y el segundo no tiene nada dentro.

## Assumptions

- **El documento se comparte con el resumen, no se duplica.** Esta feature no cambia cómo se prepara
  ni cuándo se retira: usa lo que la feature 010 dejó hecho.
- **Quien pregunta ya aceptó el aviso de envío externo**, o lo acepta aquí la primera vez. El aviso es
  el mismo y se acepta una sola vez para las dos acciones de IA.
- **La respuesta breve es la respuesta útil.** Se asume que quien pregunta a un boletín quiere el dato,
  no una redacción; la extensión se acota.
- **Tres preguntas sugeridas** es el número: entran en una línea y no compiten con el campo de texto.
- **Las preguntas sugeridas son fijas**, iguales para toda publicación. Adaptarlas al contenido
  exigiría una petición más, y por tanto cuota, antes de que nadie haya preguntado nada.
- **La conversación en memoria es suficiente** para la duración de una visita. Se acepta que una
  muerte del proceso la pierda.
- **El mismo control de consumo** cubre resúmenes y preguntas: es el mismo servicio y el mismo plan.
- **Las dos cifras del plan gratuito siguen sin confirmar** —heredado de la feature 009—, así que el
  control de consumo sigue siendo una estimación propia y no una lectura del proveedor.
