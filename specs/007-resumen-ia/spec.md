# Feature Specification: Resumen IA

**Feature Branch**: `007-resumen-ia`

**Created**: 2026-09-01

**Status**: Draft

**Input**: User description: "Procedemos con la siguiente Feature, le toca a los «resumen IA» que es en Detalle de publicación. `Datos_modelo/resumenIA.png` es un ejemplo que ya te pasé en el pasado, solo céntrate en lo de «Resumen IA» y lo que quiero es exactamente eso, que salga un resumen del PDF que es la auténtica publicación, por lo tanto se tendrá que extraer el texto del PDF y pasárselo al LLM para que este haga un resumen. Tienes un md en `Datos_modelo/ESPECIFICACION_RESUMENES_IA_BOC.md` en el cual está cosas interesantes de cómo funcionar con groq y el modelo que quiero que uses y sus limitaciones, ya que vamos a usar por el momento este servicio gratuito. Me gusta y es muy importante el que ponga «Resumen generado por IA» y la advertencia de comprobar siempre el texto oficial. Si se te ocurre algo más efectivo y que mejore aún más la experiencia de usuario coméntamelo. Lo de Preguntar y que sea un chat será la siguiente Feature, por lo tanto está fuera del alcance de esta Feature, pero lo comento para que lo tengas en cuenta."

Decisiones cerradas con el propietario antes de escribir esta especificación, y que por tanto no se
vuelven a plantear aquí: el resumen se genera **solo al pulsarlo**, nunca al abrir una publicación;
el resultado es una **ficha completa** —no solo viñetas— con la tarjeta en lenguaje llano arriba;
cada dato lleva las **páginas** que lo respaldan y esas referencias **abren el documento por esa
página**; se hace **una sola petición** por publicación, y un documento que no quepa entero se
resume en parte **avisando antes y después**; la **primera vez** se explica que el texto sale del
dispositivo, con opción de cancelar; un documento **sin texto utilizable no llega nunca** al
servicio; y el resumen **se guarda** para que al volver aparezca sin red y sin gastar cuota.

La imagen `Datos_modelo/resumenIA.png` es **una idea del aspecto, no una especificación**: fija el
tono —la tarjeta «Resumen generado por IA», la advertencia y los chips de página— y nada más.

---

## Lo que hay que saber del boletín, y de la IA, antes de leer nada más *(contexto imprescindible)*

- **La publicación de verdad es el PDF.** Lo que llega por el canal de novedades es un título, un
  organismo y una fecha. El contenido —lo que se aprueba, a quién obliga, qué plazo hay— está
  únicamente dentro del documento oficial. Resumir el título sería resumir la portada de un libro.
- **Un boletín oficial no admite aproximaciones.** Una fecha mal copiada, un importe mal leído o un
  plazo inventado tienen consecuencias reales para quien se fía. Por eso todo dato del resumen debe
  poder comprobarse en el documento, y por eso el resumen nunca se presenta como sustituto.
- **Los plazos casi nunca son fechas.** El boletín dice «diez días hábiles desde la publicación»,
  no «hasta el 12 de septiembre». Calcular esa fecha es una interpretación jurídica, y no le
  corresponde a esta aplicación.
- **La longitud de una publicación varía muchísimo.** La mayoría ocupa entre una y cinco páginas,
  pero hay presupuestos y listados de decenas. No todas caben en una sola consulta al servicio.
- **El servicio de inteligencia artificial es de plan gratuito y tiene cuota por minuto y por día**,
  compartida por toda la aplicación y no por persona. Cada resumen cuesta cuota. Generar sin que
  nadie lo pida sería gastarla en publicaciones que nadie va a leer.
- **Algunos PDF no tienen texto**: son imágenes escaneadas, o están protegidos. Preguntarle al
  servicio por un documento vacío gasta cuota y devuelve invención.
- **El texto del documento puede contener frases que parezcan órdenes.** Un documento público lo
  escribe un tercero; el sistema debe tratarlo siempre como material a analizar, jamás como
  instrucciones que seguir.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entender una publicación de un vistazo (Priority: P1)

Alguien abre un anuncio del boletín cuyo título no le dice gran cosa —«Aprobación definitiva de la
modificación de la Ordenanza General de Subvenciones»— y quiere saber si le afecta antes de
enfrentarse a seis páginas de lenguaje administrativo. Toca la pestaña **Resumen IA** y pulsa
**Generar resumen**. Mientras se prepara ve en qué fase va. Al terminar aparece una tarjeta que le
cuenta en lenguaje llano de qué va la publicación, y debajo, solo lo que consta en el documento: a
quién afecta, qué fechas y plazos hay, qué importes, qué hay que hacer y qué recursos caben.

**Why this priority**: Es la feature entera. Sin esto no hay nada que probar, guardar ni compartir.

**Independent Test**: Se prueba sola: abrir una publicación con documento, generar el resumen y
comprobar que la ficha aparece con las secciones que el documento sustenta y sin las que no.

**Acceptance Scenarios**:

1. **Given** una publicación con documento oficial y la pestaña Resumen IA abierta, **When** no se
   ha generado nada todavía, **Then** se ve una explicación breve y un botón **Generar resumen**, y
   no se ha consultado el servicio.
2. **Given** que se pulsa **Generar resumen**, **When** el proceso avanza, **Then** se indica la
   fase en curso y la pantalla nunca queda sin información.
3. **Given** que el resumen termina, **When** se muestra, **Then** encabeza la tarjeta **«Resumen
   generado por IA»** con el resumen en lenguaje llano, y debajo aparecen únicamente las secciones
   con contenido: puntos clave, a quién afecta, fechas y plazos, importes, qué hay que hacer,
   recursos o alegaciones y advertencias.
4. **Given** un resumen mostrado, **When** el documento no contiene importes, **Then** la sección de
   importes no aparece, en lugar de aparecer diciendo «No aplica».
5. **Given** un plazo escrito en el documento como «quince días hábiles desde la publicación»,
   **When** se muestra en fechas y plazos, **Then** se conserva esa expresión y no se convierte en
   una fecha concreta.

---

### User Story 2 - Volver y encontrarlo hecho (Priority: P1)

La misma persona sale de la publicación, sigue leyendo el boletín y vuelve más tarde. El resumen
está ahí, aparece al instante y no ha costado ni datos ni cuota. Sigue estando aunque el móvil no
tenga cobertura.

**Why this priority**: Con cuota diaria compartida, regenerar lo ya generado la agotaría en una
tarde. Y un resumen que tarda cada vez es un resumen que nadie consulta dos veces.

**Independent Test**: Generar un resumen, salir, activar el modo avión, volver a entrar y comprobar
que aparece completo sin ningún indicador de espera.

**Acceptance Scenarios**:

1. **Given** una publicación cuyo resumen ya se generó, **When** se vuelve a abrir su pestaña
   Resumen IA, **Then** el resumen aparece directamente, sin botón de generar y sin consultar el
   servicio.
2. **Given** un resumen ya guardado, **When** el dispositivo está sin conexión, **Then** el resumen
   se muestra igual.
3. **Given** un resumen guardado, **When** la persona quiere uno nuevo, **Then** dispone de una
   acción para **regenerarlo**, que sí consulta el servicio.
4. **Given** un resumen guardado que ya no corresponde al documento actual de la publicación,
   **When** se abre la pestaña, **Then** se advierte de que está obsoleto y se ofrece regenerarlo,
   sin borrar el anterior por su cuenta.

---

### User Story 3 - Comprobar cada dato en el documento oficial (Priority: P2)

Antes de fiarse de un plazo, la persona quiere verlo escrito. Junto a cada dato del resumen hay una
referencia a la página que lo respalda; al tocarla, el documento oficial se abre por esa página. La
advertencia de que el texto lo ha generado una máquina está siempre a la vista, y si comparte el
resumen con alguien, la advertencia va dentro del mensaje.

**Why this priority**: Es lo que separa una ayuda de un riesgo. Un resumen sin procedencia es una
afirmación sin respaldo, y en un boletín oficial eso no vale.

**Independent Test**: Con un resumen a la vista, tocar una referencia de página y comprobar que el
documento abre por ella; y compartir el resumen y leer el texto resultante.

**Acceptance Scenarios**:

1. **Given** un resumen mostrado, **When** se lee cualquier sección, **Then** cada elemento indica
   las páginas del documento que lo respaldan.
2. **Given** una referencia a la página 3, **When** se toca, **Then** el documento oficial se abre
   mostrando la página 3.
3. **Given** un resumen mostrado, **When** se mira la pantalla, **Then** la advertencia **«Comprueba
   siempre el texto oficial»** está visible junto al resumen.
4. **Given** que se usa un lector de pantalla, **When** se recorre el resumen, **Then** la
   advertencia se anuncia; no depende de ver un icono rojo.
5. **Given** un resumen mostrado, **When** se copia o se comparte, **Then** el texto resultante
   empieza indicando que lo ha generado una inteligencia artificial, que puede contener errores y
   que hay que consultar el documento oficial.
6. **Given** un resumen mostrado, **When** se busca el documento, **Then** hay una acción para
   abrir el PDF oficial completo.

---

### User Story 4 - Saber que el texto sale del dispositivo (Priority: P2)

La primera vez que alguien pide un resumen, la aplicación le explica en dos frases que para hacerlo
envía el texto del documento a un servicio externo, y le deja decidir. Si continúa, no se lo vuelve
a preguntar nunca más. Si cancela, no se envía nada.

**Why this priority**: Enterarse después de que el contenido ha salido del móvil es enterarse tarde.
Es barato de hacer y se hace una sola vez en la vida de la instalación.

**Independent Test**: Con la aplicación recién instalada, pulsar **Generar resumen** y comprobar que
aparece el aviso, que cancelar no genera nada, y que tras continuar no vuelve a salir.

**Acceptance Scenarios**:

1. **Given** que nunca se ha generado un resumen en este dispositivo, **When** se pulsa **Generar
   resumen**, **Then** se muestra un aviso que explica que el texto del documento se envía a un
   servicio externo, con **Continuar** y **Cancelar**.
2. **Given** el aviso a la vista, **When** se cancela, **Then** no se envía nada y la pestaña vuelve
   a su estado inicial.
3. **Given** el aviso a la vista, **When** se continúa, **Then** el resumen se genera y el aviso no
   vuelve a mostrarse en generaciones posteriores.

---

### User Story 5 - Entender por qué no hay resumen (Priority: P3)

A veces no se puede: el documento es una imagen escaneada, no hay conexión, la cuota del día se ha
agotado o el documento es tan largo que no cabe entero. En todos esos casos la persona entiende qué
ha pasado en una frase, sabe si puede volver a intentarlo y siempre puede abrir el documento
oficial. Nunca ve un código técnico.

**Why this priority**: Determina si la función se percibe como fiable o como rota. Depende de que
las anteriores existan, por eso va después.

**Independent Test**: Provocar cada caso —documento sin texto, sin conexión, cuota agotada,
documento largo— y comprobar el mensaje, la posibilidad de reintentar y el acceso al PDF.

**Acceptance Scenarios**:

1. **Given** una publicación cuyo documento no contiene texto que se pueda analizar, **When** se
   pulsa **Generar resumen**, **Then** se explica que no se puede analizar, se ofrece abrir el PDF y
   **no se consulta el servicio**.
2. **Given** que el dispositivo no tiene conexión y no hay resumen guardado, **When** se pulsa
   **Generar resumen**, **Then** se explica que hará falta conexión y se puede reintentar después.
3. **Given** que la cuota por minuto se ha agotado, **When** hay un resumen en curso, **Then** se
   indica cuánto falta aproximadamente para continuar, y el proceso continúa solo.
4. **Given** que la cuota diaria se ha agotado, **When** se pulsa **Generar resumen**, **Then** se
   explica que hay que intentarlo al día siguiente, sin ofrecer un reintento inmediato inútil.
5. **Given** un documento de catorce páginas de las que solo caben seis, **When** se mira el botón
   antes de pulsarlo, **Then** ya avisa de que se analizará solo una parte y cuál.
6. **Given** ese mismo documento ya resumido, **When** se muestra el resultado, **Then** se indica
   qué páginas se analizaron y que el resumen no cubre el documento completo.
7. **Given** cualquiera de los fallos anteriores, **When** se lee el mensaje, **Then** no contiene
   códigos de error, trazas ni texto del proveedor del servicio.

---

### Edge Cases

- **El documento aún no está descargado.** Quien entra directamente a Resumen IA sin pasar por
  Documento no debería notar la diferencia: la obtención del documento forma parte del proceso y se
  refleja en la fase mostrada.
- **La persona abandona la pantalla mientras se genera.** El trabajo en curso se detiene sin
  convertirse en un error, y al volver la pestaña ofrece intentarlo de nuevo.
- **El servicio devuelve un resumen mal formado o vacío.** No se muestra ni se guarda: se dice que
  no se ha podido construir un resumen fiable y se ofrece el documento oficial.
- **El servicio cita una página que no existe** en el documento, o afirma haber analizado páginas
  que no se le enviaron. Esa afirmación no puede llegar a la pantalla tal cual.
- **El documento contiene frases que parecen instrucciones** dirigidas al sistema. Se tratan como
  contenido a analizar, nunca se ejecutan, y no pueden cambiar el formato ni el alcance del resumen.
- **El documento oficial se retira de la caché** para hacer sitio. El resumen guardado sobrevive;
  regenerarlo vuelve a obtener el documento.
- **Una publicación sin documento oficial.** La pestaña lo dice y no ofrece generar nada.
- **Se pulsa generar dos veces seguidas.** Solo se ejecuta una vez; no se duplica el gasto de cuota.
- **La primera página, sola, no cabe** en el presupuesto de una consulta. Se analiza la parte que
  quepa, cortando por un límite natural del texto, y se declara como cobertura parcial.

## Requirements *(mandatory)*

### Functional Requirements

#### La pestaña y el arranque de la generación

- **FR-001**: La pestaña **Resumen IA** del detalle de publicación MUST dejar de ser un marcador de
  posición y mostrar el estado real del resumen de esa publicación.
- **FR-002**: El sistema MUST NOT generar ningún resumen sin una acción explícita de la persona.
  Abrir la publicación, abrir la pestaña o sincronizar el boletín no generan nada.
- **FR-003**: Cuando no existe resumen, la pestaña MUST ofrecer una acción **Generar resumen** junto
  a una explicación breve de qué hace.
- **FR-004**: Durante la generación el sistema MUST indicar la fase en curso —obtener el documento,
  extraer su texto, generar el resumen— y MUST NOT dejar la pantalla sin información.
- **FR-005**: El sistema MUST tratar una segunda pulsación mientras hay una generación en curso como
  la misma operación, sin lanzar una segunda consulta al servicio.
- **FR-006**: Si la persona abandona la pantalla durante la generación, el sistema MUST detener el
  trabajo sin registrarlo como error.
- **FR-007**: Una publicación sin documento oficial MUST indicar que no hay nada que resumir, sin
  ofrecer la acción de generar.

#### El texto del documento

- **FR-008**: El resumen MUST construirse a partir del texto del documento oficial, no del título ni
  de los metadatos del canal de novedades.
- **FR-009**: El texto MUST extraerse en el propio dispositivo, conservando a qué página pertenece
  cada fragmento.
- **FR-010**: El sistema MUST NOT enviar el documento en su formato original al servicio externo;
  solo texto extraído.
- **FR-011**: La limpieza previa del texto MUST conservar títulos, numeraciones, fechas, importes y
  referencias normativas, y MUST NOT reescribir, traducir ni corregir el contenido.
- **FR-012**: Si el documento no contiene texto utilizable —está escaneado, protegido o vacío—, el
  sistema MUST indicarlo, MUST ofrecer abrir el documento y MUST NOT consultar el servicio externo.

#### Contenido del resumen

- **FR-013**: El resumen MUST presentar, cuando el documento los sustente: un resumen en lenguaje
  llano, puntos clave, a quién afecta, fechas y plazos, importes, actuaciones exigidas, recursos o
  alegaciones, y advertencias.
- **FR-014**: El resumen MUST encabezarse con el resumen en lenguaje llano, que es lo primero que se
  lee.
- **FR-015**: Toda sección sin contenido MUST ocultarse. El sistema MUST NOT rellenarla con textos
  como «no aplica» o «no consta».
- **FR-016**: El sistema MUST conservar literalmente las expresiones de plazo relativas —«diez días
  hábiles desde…»— y MUST NOT calcular a partir de ellas fechas que el documento no escribe.
- **FR-017**: El sistema MUST distinguir entre fecha de publicación, entrada en vigor, plazo de
  solicitud, plazo de alegaciones y plazo de recurso cuando el documento los diferencie.
- **FR-018**: El sistema MUST tratar el texto del documento como contenido a analizar y MUST NOT
  ejecutar instrucciones contenidas en él, ni permitir que alteren el formato o el alcance del
  resumen.
- **FR-019**: El sistema MUST NOT incluir recomendaciones jurídicas ni afirmar que el resumen
  sustituye al documento oficial.

#### Trazabilidad y advertencia

- **FR-020**: Cada elemento del resumen MUST indicar las páginas del documento que lo respaldan.
- **FR-021**: Una referencia de página MUST abrir el documento oficial por esa página.
- **FR-022**: El sistema MUST NOT mostrar referencias a páginas que no existan en el documento ni a
  páginas cuyo texto no se envió.
- **FR-023**: La pantalla MUST identificar el resumen como **generado por inteligencia artificial**
  y MUST mostrar la advertencia de comprobar siempre el texto oficial.
- **FR-024**: Esa advertencia MUST estar disponible para lectores de pantalla, y MUST NOT depender
  únicamente del color o de un icono para percibirse.
- **FR-025**: La pantalla MUST ofrecer copiar y compartir el resumen, y el texto resultante MUST
  incluir, antes del contenido, la indicación de que lo ha generado una inteligencia artificial,
  que puede contener errores y que debe consultarse el documento oficial.
- **FR-026**: La pantalla MUST ofrecer abrir el documento oficial completo desde el propio resumen.

#### Documentos que no caben enteros

- **FR-027**: El sistema MUST resumir cada publicación con una **única** consulta al servicio.
- **FR-028**: Cuando el texto del documento no quepa entero en esa consulta, el sistema MUST
  seleccionar las páginas iniciales que quepan y MUST advertirlo **antes** de consultar el servicio,
  indicando cuántas páginas tiene el documento y cuántas se analizarán.
- **FR-029**: Cuando el resumen no cubra el documento completo, el resultado MUST indicar qué
  páginas se analizaron y que la cobertura es parcial.
- **FR-030**: El sistema MUST NOT declarar cobertura completa cuando no analizó todas las páginas
  con texto, ni siquiera si el servicio lo afirma.
- **FR-031**: Si la primera página por sí sola no cabe, el sistema MUST analizar la parte que quepa
  cortando por un límite natural del texto, y declararlo como cobertura parcial.

#### Conservación del resumen

- **FR-032**: El sistema MUST guardar el resumen en el dispositivo, asociado a la publicación y al
  documento concreto del que se obtuvo.
- **FR-033**: Al volver a abrir la pestaña, un resumen guardado MUST mostrarse sin consultar el
  servicio y MUST estar disponible sin conexión.
- **FR-034**: El sistema MUST ofrecer regenerar un resumen a petición explícita.
- **FR-035**: Si el documento de la publicación ha cambiado, o han cambiado las condiciones con las
  que se generó, el sistema MUST señalar el resumen como obsoleto y ofrecer regenerarlo, y MUST NOT
  descartarlo por su cuenta.
- **FR-036**: El sistema MUST NOT mostrar ni guardar un resumen que llegue vacío o mal formado.

#### Cuota y errores

- **FR-037**: El sistema MUST respetar los límites de uso del servicio y MUST NOT lanzar consultas
  cuando sabe que no hay margen disponible.
- **FR-038**: Cuando haya que esperar por cuota, el sistema MUST indicar aproximadamente cuánto
  falta y MUST continuar por sí solo cuando pueda.
- **FR-039**: Cuando el límite agotado sea el diario, el sistema MUST decirlo y MUST NOT ofrecer un
  reintento inmediato.
- **FR-040**: Los mensajes de error MUST explicar la situación en lenguaje corriente y MUST NOT
  mostrar códigos de estado, trazas ni mensajes internos del proveedor.
- **FR-041**: Todo fallo recuperable MUST ofrecer reintentar; los no recuperables MUST NOT ofrecerlo.
- **FR-042**: Si el servicio no está configurado correctamente en la aplicación, el sistema MUST
  decirlo como una limitación de la aplicación, sin ofrecer un reintento inútil.

#### Privacidad

- **FR-043**: La primera vez que se solicita un resumen en el dispositivo, el sistema MUST explicar
  que el texto del documento se envía a un servicio externo para generarlo, y MUST permitir
  continuar o cancelar.
- **FR-044**: Cancelar ese aviso MUST NOT enviar nada.
- **FR-045**: Una vez aceptado, el aviso MUST NOT volver a mostrarse.
- **FR-046**: El sistema MUST NOT enviar al servicio externo nada de la persona: ni publicaciones
  guardadas, ni historial, ni identificadores personales o publicitarios.
- **FR-047**: El sistema MUST NOT registrar en diagnósticos, informes de fallo ni analítica la
  credencial de acceso al servicio ni el contenido del documento.
- **FR-048**: La credencial de acceso al servicio MUST NOT quedar escrita en el repositorio de
  código.

### Key Entities

- **Resumen de una publicación**: lo que la aplicación ha entendido de un documento oficial. Se
  compone de un resumen en lenguaje llano y de listas —puntos clave, afectados, fechas y plazos,
  importes, actuaciones exigidas, recursos, advertencias— donde cada elemento arrastra las páginas
  que lo respaldan. Una lista vacía significa que ese dato no consta en el documento.
- **Cobertura**: qué páginas del documento se analizaron realmente, de cuántas, y si eso cubre o no
  el documento entero. Es lo que permite decir la verdad sobre un resumen parcial.
- **Referencia de página**: el vínculo entre una afirmación del resumen y el lugar del documento
  donde comprobarla.
- **Texto del documento por páginas**: el contenido extraído localmente, conservando la página de
  procedencia. Existe durante la generación y es lo único que sale del dispositivo.
- **Procedencia del resumen**: con qué documento y bajo qué condiciones se generó. Es lo que permite
  saber que un resumen guardado ha quedado obsoleto.
- **Aceptación del aviso de envío externo**: si esta instalación ya ha sido informada de que el
  texto sale del dispositivo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde una publicación abierta, obtener su resumen cuesta como máximo dos toques la
  primera vez —abrir la pestaña y generar— y ninguno las siguientes.
- **SC-002**: Un resumen ya generado se muestra completo en menos de un segundo y sin conexión de
  red, verificado en modo avión.
- **SC-003**: El 100 % de las referencias de página mostradas corresponden a páginas existentes del
  documento, y todas abren el documento por la página que anuncian.
- **SC-004**: El número de consultas al servicio externo es exactamente cero mientras nadie pulse
  generar o regenerar, incluso navegando por todo el boletín y abriendo publicaciones.
- **SC-005**: Un documento sin texto utilizable produce cero consultas al servicio externo.
- **SC-006**: La advertencia sobre el origen del resumen es perceptible por tres vías: visible en
  pantalla, anunciada por lector de pantalla e incluida en el texto copiado y compartido.
- **SC-007**: Ante un documento que no cabe entero, la persona puede saber que el resumen es parcial
  y qué páginas cubre sin abrir el documento.
- **SC-008**: Ningún mensaje mostrado a la persona contiene códigos de estado, trazas ni texto
  literal del proveedor del servicio.
- **SC-009**: Ni la credencial del servicio ni el contenido de ningún documento aparecen en los
  registros de diagnóstico de la aplicación.
- **SC-010**: El aviso de envío externo se muestra antes del primer envío y exactamente una vez por
  instalación.
- **SC-011**: Se puede generar al menos un resumen por minuto de forma sostenida sin que la persona
  vea un error de cuota; cuando toca esperar, ve una cuenta atrás y el proceso continúa solo.
- **SC-012**: Ningún resumen mostrado declara haber cubierto páginas que no se analizaron.

## Fuera de alcance

Se dice en voz alta para que no se lea como un olvido:

- **El chat «Preguntar a esta publicación».** Es la feature siguiente, por decisión del propietario.
  Esta feature no construye conversación, ni historial, ni recuperación de fragmentos, ni un almacén
  de texto por adelantado para ella.
- **El resumen por fragmentos de documentos largos.** Un documento que no cabe se resume en parte y
  se dice. Trocear, resumir cada trozo y fundirlos —con su progreso reanudable— es un problema
  distinto y bastante mayor; se aborda cuando se compruebe que hace falta de verdad.
- **El reconocimiento óptico de documentos escaneados.** Un PDF sin capa de texto se declara no
  analizable. Los del boletín normalmente sí la tienen.
- **Resumir en bloque las publicaciones de una lista.** La cuota es compartida y limitada; resumir
  lo que nadie ha pedido la gastaría en balde.
- **Una pantalla de Ajustes** desde la que borrar los datos de IA o releer el aviso. Hoy no existe
  esa pantalla y crearla no es parte de esta feature.
- **Un servicio propio intermedio** que custodie la credencial. Se asume el modelo de credencial
  local con sus limitaciones, declaradas en los supuestos.
- **Traducir el resumen** o adaptarlo a lectura fácil.

## Assumptions

- **La credencial del servicio se configura en la máquina de desarrollo, fuera del repositorio.**
  Se asume, con conocimiento del propietario, que una credencial incluida en una aplicación
  distribuida es recuperable por quien analice el paquete. Es aceptable mientras la aplicación no se
  publique, y por eso la credencial debe poder revocarse y tener límites estrictos. Si falta, la
  aplicación compila y funciona: solo la generación de resúmenes queda anunciada como no disponible.
- **Un resumen por publicación.** No se conserva historial de versiones anteriores de un resumen;
  regenerar sustituye al anterior.
- **La mayoría de las publicaciones del boletín caben en una sola consulta.** El caso parcial se
  trata bien, pero se asume que es minoritario. Si resultara ser habitual, el resumen por fragmentos
  dejaría de estar fuera de alcance.
- **Se conserva el documento, no su texto.** El texto extraído vive durante la generación; volver a
  extraerlo es local y no cuesta cuota. Por eso no se almacena.
- **El servicio devuelve una estructura de datos, no prosa libre**, lo que permite ocultar secciones
  vacías y validar las referencias antes de mostrarlas.
- **Las páginas se numeran desde 1** de cara a la persona, como en cualquier documento.
- **La aplicación sigue bloqueada en vertical y con un único tema claro**, como el resto de
  pantallas; esta feature no introduce excepciones.
