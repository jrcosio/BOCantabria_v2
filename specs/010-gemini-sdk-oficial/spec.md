# Feature Specification: El documento se envía entero, no su texto

**Feature Branch**: `010-gemini-sdk-oficial`

**Created**: 5 de septiembre de 2026

**Status**: Draft

**Input**: User description: "Migrar el acceso a Gemini del cliente escrito a mano a la librería oficial de Kotlin, que ha pasado a versión estable, y con ello dejar de extraer el texto del PDF en el dispositivo: ahora el documento oficial se sube entero y el modelo lo lee directamente. Es además el cimiento de la pantalla Preguntar, porque sin subir el documento una vez, cada pregunta del chat lo reenviaría entero."

La ruta técnica —qué librería, cómo se sube el documento, cómo se empaqueta la aplicación— quedó
cerrada con el propietario antes de escribir esto y **se documenta en `plan.md`, no aquí**. Esta
especificación describe únicamente qué cambia para quien usa la aplicación y qué garantías hay que
seguir cumpliendo.

Decisiones cerradas que por tanto no se vuelven a plantear: el resumen sigue generándose **solo al
pulsarlo**; los resúmenes ya generados **no se borran**, se marcan como hechos con una versión
anterior y se pueden regenerar; la conversación de la pantalla Preguntar **no entra aquí**; y el
documento subido se retira del servicio **al salir de la publicación**, no antes ni después.

---

## Lo que hay que saber antes de leer nada más *(contexto imprescindible)*

- **Esto no añade una funcionalidad: cambia qué sale del dispositivo.** El Resumen IA funciona desde
  la feature 007 y cambió de proveedor en la 009. Lo que cambia ahora es más profundo que un
  proveedor: hasta hoy la aplicación **leía el PDF en el móvil** y enviaba el texto que había sacado;
  a partir de ahora envía **el documento oficial tal cual**, y quien lo lee es el servicio.
- **La consecuencia visible es que deja de haber documentos imposibles.** Hoy, un PDF escaneado —una
  imagen de un papel, sin capa de texto— se rechaza con «Este documento no contiene texto que la
  aplicación pueda analizar». Ese rechazo existía porque lo que enviábamos era el texto, y de un
  escaneado no salía ninguno. Cuando lo que se envía es el documento, un escaneado deja de ser un
  caso imposible y pasa a ser una entrada válida.
- **Es también el cimiento de la pantalla Preguntar**, que llega después. Subir el documento una vez
  y referenciarlo luego es lo que hace que una conversación de diez preguntas no cueste diez envíos
  del boletín entero. Sin este cambio, la pantalla siguiente no sale a cuenta.
- **Que el documento salga del dispositivo en su formato original es un cambio material de lo que se
  dice.** El aviso que hoy se muestra antes de la primera vez habla de enviar «el texto de este
  documento». Deja de ser cierto: se envía el fichero, y el servicio lo conserva un tiempo. Quien ya
  aceptó ese aviso aceptó otra cosa.
- **Un boletín oficial no admite aproximaciones.** Una fecha mal copiada o un plazo inventado tienen
  consecuencias reales para quien se fía. Todo dato del resumen debe seguir siendo comprobable en el
  documento, y el resumen no es sustituto de nada. Que ahora lea el documento entero, incluidos los
  escaneados, no relaja eso: lo hace más importante.
- **Hay personas con resúmenes ya hechos en su dispositivo.** Ninguno debe desaparecer, aunque todos
  queden marcados como hechos en condiciones anteriores.
- **La cuota sigue siendo la de un plan gratuito**, compartida por toda la aplicación y no por
  persona, con un límite por unidad de tiempo corta y otro por día. Cada resumen sigue costando
  cuota, y generar sin que nadie lo pida seguiría siendo gastarla en publicaciones que nadie va a
  leer.
- **Los defectos que de verdad rompieron el Resumen IA en un móvil vivían al otro lado de la frontera
  con el servicio**, donde todas las pruebas automáticas ponen dobles. Los encontró el registro de
  diagnóstico en un dispositivo real. Este cambio vuelve a poner esa frontera en juego, y esta vez
  también la subida del fichero.
- **Y una novedad que no tiene que ver con la inteligencia artificial**: por primera vez la
  aplicación se va a empaquetar con la optimización activada. Hasta hoy nadie compilaba la versión
  que se distribuye, así que nadie la ha ejecutado. Eso deja de ser aceptable en esta feature.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Un documento escaneado también se resume (Priority: P1)

Una persona abre un anuncio que el organismo publicó como imagen escaneada —un acta firmada, un plano
con su leyenda, un edicto fotografiado— y pulsa «Generar resumen». Hoy la aplicación le responde que
no puede analizarlo y ahí se acaba. A partir de ahora obtiene el mismo resumen que obtendría de
cualquier otra publicación: la explicación en lenguaje claro, los puntos clave, las fechas, los
importes, y las páginas que respaldan cada dato.

**Why this priority**: Es el único cambio que una persona nota, y es una funcionalidad que hoy
simplemente no existe para una parte del boletín. Sin esto, la feature es invisible.

**Independent Test**: Se comprueba de punta a punta abriendo una publicación cuyo PDF sea un
escaneado sin capa de texto y pidiendo su resumen; la prueba entrega valor por sí sola porque esa
publicación pasa de no tener resumen posible a tenerlo.

**Acceptance Scenarios**:

1. **Given** una publicación cuyo documento oficial es un escaneado sin texto seleccionable,
   **When** la persona pulsa «Generar resumen» y acepta el aviso, **Then** la aplicación produce un
   resumen con su explicación en lenguaje claro y sus páginas citadas, y **no** muestra el mensaje de
   «no contiene texto que la aplicación pueda analizar».
2. **Given** una publicación con texto seleccionable, **When** la persona pide su resumen, **Then**
   el resultado tiene la misma forma y las mismas secciones que antes de este cambio.
3. **Given** una publicación cuyo documento el servicio no consigue procesar de ninguna manera,
   **When** la persona pide su resumen, **Then** se le dice en lenguaje corriente que ese documento
   no ha podido leerse, sin ningún código ni nombre de servicio, y no se le ofrece reintentar.
4. **Given** un documento protegido con contraseña, **When** la persona pide su resumen, **Then**
   sigue recibiendo el mensaje de documento protegido y **el documento no llega a salir del
   dispositivo**.

---

### User Story 2 - Lo que ya estaba resumido no se pierde (Priority: P1)

Una persona que venía usando la aplicación tiene resúmenes guardados de publicaciones que le
interesan. Tras actualizar, los sigue viendo todos, marcados como hechos con una versión anterior, y
puede rehacer el que quiera cuando quiera.

**Why this priority**: Es el requisito que impide que una mejora por dentro se convierta en una
pérdida por fuera. Vale tanto como la historia anterior.

**Independent Test**: Se comprueba generando resúmenes con la versión actual, actualizando, y
verificando que siguen apareciendo, marcados, y que rehacerlos funciona.

**Acceptance Scenarios**:

1. **Given** un resumen generado antes de este cambio, **When** la persona abre la pestaña de
   resumen, **Then** lo ve completo y con un aviso de que se hizo con una versión anterior.
2. **Given** ese mismo resumen marcado, **When** la persona pulsa «Volver a generar», **Then** se
   produce uno nuevo que sustituye al anterior y deja de estar marcado.
3. **Given** un resumen ya hecho con la versión nueva, **When** la persona cierra la aplicación y
   vuelve a abrir la publicación, **Then** el resumen aparece sin coste y sin esperar a la red.

---

### User Story 3 - El documento se prepara una vez y se retira al salir (Priority: P2)

Una persona pide el resumen de una publicación, lo lee, vuelve a generarlo porque quiere comprobar
algo, y luego sale. La aplicación prepara el documento **una sola vez** durante esa visita, y al
salir lo retira del servicio.

**Why this priority**: Es lo que hace que la funcionalidad no gaste el doble de lo necesario, y es la
base sobre la que se apoya la pantalla siguiente. No es visible salvo en el tiempo de espera, pero
gobierna el coste.

**Independent Test**: Se comprueba pidiendo el resumen, regenerándolo, y verificando que la fase de
preparación del documento aparece la primera vez y no la segunda; y saliendo de la publicación para
verificar que el documento deja de estar disponible en el servicio.

**Acceptance Scenarios**:

1. **Given** una publicación cuyo resumen se acaba de generar, **When** la persona pulsa «Volver a
   generar» sin salir de la pantalla, **Then** la fase «Preparando el documento…» **no** vuelve a
   aparecer y la espera es menor.
2. **Given** una publicación cuyo documento ya está preparado, **When** la persona sale de la
   pantalla de detalle, **Then** el documento se retira del servicio.
3. **Given** una persona que abre una publicación distinta, **When** pide su resumen, **Then** el
   documento de la anterior ya no ocupa sitio en el servicio.
4. **Given** que la aplicación se cierra de golpe sin poder retirar el documento, **When** pasa el
   plazo de conservación del servicio, **Then** el documento desaparece por sí solo sin intervención
   de nadie.

---

### User Story 4 - Volver a ver el aviso, una sola vez (Priority: P2)

Una persona que ya había aceptado el aviso de envío a un servicio externo lo vuelve a ver la primera
vez que pide un resumen tras actualizar, porque lo que se envía ha cambiado. Lo lee, lo acepta, y no
vuelve a verlo.

**Why this priority**: Aceptar «se envía el texto» no es aceptar «se envía el documento y el servicio
lo conserva». Volver a preguntarlo es la parte no negociable; que se pregunte **una sola vez** es lo
que impide que sea una molestia.

**Independent Test**: Se comprueba aceptando el aviso con la versión anterior, actualizando, y
verificando que reaparece una vez y solo una.

**Acceptance Scenarios**:

1. **Given** una persona que ya había aceptado el aviso anterior, **When** pulsa «Generar resumen»
   por primera vez tras actualizar, **Then** ve el aviso con su contenido nuevo.
2. **Given** que lo acepta, **When** genera más resúmenes, de esa o de otras publicaciones, **Then**
   el aviso no vuelve a aparecer.
3. **Given** que lo rechaza, **When** se cierra el aviso, **Then** no se envía nada y no se genera
   ningún resumen.

---

### User Story 5 - La aplicación se puede compilar, probar y empaquetar (Priority: P3)

Quien mantiene el proyecto puede compilarlo y pasar todas sus pruebas **sin ninguna credencial**, y
puede además generar e instalar la versión optimizada que se distribuye y recorrerla entera.

**Why this priority**: No lo ve nadie que use la aplicación, pero sin ello no hay forma de saber si
lo que se publica funciona. La segunda mitad —la versión optimizada— es nueva en esta feature y no
existía antes.

**Independent Test**: Se comprueba compilando y ejecutando la batería de pruebas en un entorno sin
credencial, y por separado instalando la versión optimizada en un dispositivo y recorriendo la
aplicación.

**Acceptance Scenarios**:

1. **Given** un entorno sin credencial configurada, **When** se compila y se ejecutan todas las
   pruebas, **Then** todo queda en verde.
2. **Given** esa misma compilación instalada, **When** una persona pulsa «Generar resumen»,
   **Then** se le dice que la función no está configurada en esta aplicación, y no se intenta ningún
   envío.
3. **Given** la versión optimizada que se distribuye, **When** se instala y se recorren el arranque,
   el boletín, la búsqueda, los guardados, el detalle, el visor del documento y el resumen,
   **Then** todo funciona igual que en la versión de desarrollo.

---

### Edge Cases

- **El documento se prepara pero la persona se va antes de que termine.** Irse no es un fallo: no se
  muestra ningún error, y lo que se hubiera empezado a preparar se retira.
- **El documento se prepara correctamente pero el servicio se queda sin cuota.** Se distingue igual
  que hoy entre «espera unos segundos» y «vuelve mañana», y el documento preparado no se pierde por
  ello: si se reintenta dentro de la misma visita, no vuelve a prepararse.
- **La preparación del documento no termina nunca.** Debe haber un tope de espera tras el cual se
  informa de que no ha podido prepararse, en vez de dejar la pantalla girando.
- **El documento cambia en el servidor del boletín entre dos visitas.** El resumen guardado se marca
  como obsoleto, como hasta ahora, y una regeneración prepara el documento nuevo.
- **La persona abre el visor del PDF mientras el documento se está preparando.** Son dos cosas
  independientes: el visor lee la copia local y no depende de la preparación.
- **El servicio devuelve un resumen que cita una página que no existe** en el documento. Esa cita se
  descarta antes de mostrarse, para que no haya un enlace que lleve a ninguna parte.
- **Dos peticiones simultáneas sobre la misma publicación.** Comparten una sola preparación y una
  sola generación, como ya ocurría.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Lo que se envía al servicio

- **FR-001**: El resumen MUST construirse a partir del **documento oficial completo**, enviado en su
  formato original, y no a partir de un texto extraído previamente en el dispositivo.
- **FR-002**: Un documento sin capa de texto —escaneado o generado como imagen— MUST poder resumirse
  igual que cualquier otro, y MUST NOT rechazarse por ese motivo.
- **FR-003**: El documento MUST haber sido validado como documento oficial del boletín antes de
  salir del dispositivo, con las mismas comprobaciones que ya se aplican al descargarlo.
- **FR-004**: Un documento protegido con contraseña MUST detectarse **en el dispositivo** y MUST NOT
  llegar a enviarse.
- **FR-005**: La aplicación MUST seguir conociendo el número de páginas del documento sin depender de
  lo que diga el servicio, para poder descartar citas a páginas que no existen.
- **FR-006**: MUST NOT enviarse nada de la persona: ni lo que ha guardado, ni lo que ha leído, ni
  ningún identificador. Solo el documento oficial y los datos públicos de la publicación.

#### El ciclo de vida del documento preparado

- **FR-007**: El documento MUST prepararse en el servicio **la primera vez que se necesita** dentro
  de una visita a la publicación, y no antes.
- **FR-008**: Una segunda petición sobre la misma publicación, dentro de la misma visita y sobre el
  mismo documento, MUST reutilizar la preparación anterior y MUST NOT volver a enviarlo.
- **FR-009**: Al salir de la pantalla de detalle de la publicación, el documento preparado MUST
  retirarse del servicio.
- **FR-010**: Abrir la pantalla de detalle de otra publicación MUST retirar el documento de la
  anterior.
- **FR-011**: Si la aplicación termina sin poder retirarlo, el documento MUST desaparecer por sí solo
  transcurrido el plazo de conservación del servicio, sin acción de nadie. Esta es una red de
  seguridad, no el mecanismo principal.
- **FR-012**: La preparación MUST tener un tope de espera; superado, se informa de que el documento
  no ha podido prepararse.
- **FR-013**: Mientras el documento se prepara, la pantalla MUST decirlo con una fase propia, en
  lenguaje corriente.

#### Lo que ya estaba resumido

- **FR-014**: Ningún resumen guardado MUST borrarse por este cambio.
- **FR-015**: Todo resumen generado en condiciones anteriores MUST mostrarse marcado como hecho con
  una versión anterior, y MUST poder rehacerse.
- **FR-016**: Un resumen guardado MUST mostrarse desde que se abre la pestaña, sin esperar a la red.

#### Garantías que el cambio no debe romper

- **FR-017**: Observar la pestaña de resumen MUST NOT generar nada. Solo el botón gasta cuota.
- **FR-018**: Cada dato del resumen MUST seguir citando las páginas del documento que lo respaldan, y
  esas citas MUST seguir abriendo el documento por la página correspondiente.
- **FR-019**: Una cita a una página que no existe en el documento MUST descartarse antes de
  mostrarse.
- **FR-020**: El resumen MUST seguir llevando visible la advertencia de que lo ha generado una
  inteligencia artificial y de que hay que comprobar el texto oficial, y esa advertencia MUST seguir
  viajando **dentro** del texto al copiarlo o compartirlo.
- **FR-021**: Las secciones del resumen que el documento no sustente MUST seguir ocultándose, en vez
  de mostrarse vacías.
- **FR-022**: Dos peticiones simultáneas sobre la misma publicación MUST compartir un solo envío.
- **FR-023**: Salir de la pantalla mientras se genera MUST NOT presentarse como un fallo.

#### Límites de uso del servicio

- **FR-024**: La aplicación MUST seguir llevando su propia cuenta del consumo, por unidad de tiempo
  corta y por día, sin depender de que el servicio la informe.
- **FR-025**: Una petición condenada de antemano por falta de cuota MUST NOT llegar a enviarse.
- **FR-026**: MUST seguir distinguiéndose «se ha alcanzado el límite, espera unos segundos» de «se ha
  alcanzado el límite de hoy, vuelve mañana», y solo la primera MUST ofrecer esperar.
- **FR-027**: Un fallo temporal del servicio MUST reintentarse un número acotado de veces con espera
  creciente, y un reintento MUST NOT lanzarse si no hay cuota para atenderlo.

#### Lo que se dice y lo que no se dice

- **FR-028**: Ningún mensaje en pantalla MUST contener códigos de error, nombres de servicio, nombres
  de modelo ni texto devuelto por el servicio.
- **FR-029**: Cuando el documento no ha podido leerse, el mensaje MUST decirlo en lenguaje corriente
  y MUST NOT ofrecer reintentar, porque reintentar no puede ayudar.
- **FR-030**: Cuando falta la credencial, el mensaje MUST decir que la función no está configurada en
  esta aplicación, y MUST NOT sugerir que es un problema de la persona ni de su conexión.
- **FR-031**: Los mensajes de error MUST ofrecer reintentar únicamente cuando reintentar pueda
  cambiar el resultado.

#### Privacidad y credencial

- **FR-032**: El aviso previo al primer envío MUST decir que se envía **el documento oficial
  completo**, que el servicio lo conserva durante un tiempo limitado, y que la aplicación lo retira
  al salir de la publicación.
- **FR-033**: Como el contenido del aviso cambia de forma sustancial, quien ya lo había aceptado MUST
  volver a verlo **una sola vez**. Tras aceptarlo, MUST NOT mostrarse de nuevo.
- **FR-034**: Rechazar el aviso MUST NOT enviar nada.
- **FR-035**: La credencial MUST NOT aparecer nunca en el registro de diagnóstico, en los informes de
  fallo ni en la analítica.
- **FR-036**: El contenido del documento MUST NOT aparecer nunca en el registro de diagnóstico, en
  los informes de fallo ni en la analítica.
- **FR-037**: La analítica MUST NOT registrar datos personales identificables.

#### Diagnóstico

- **FR-038**: El registro de diagnóstico MUST permitir distinguir en qué fase falló algo: obtener el
  documento, prepararlo en el servicio, generar el resumen, o validarlo.
- **FR-039**: De una respuesta del servicio MUST registrarse su forma —qué campos trae y de qué
  tamaño— y nunca su contenido.
- **FR-040**: Situaciones distintas que en pantalla comparten el mismo mensaje MUST distinguirse en el
  registro.

#### La aplicación empaquetada

- **FR-041**: La versión que se distribuye MUST generarse con la optimización de tamaño activada.
- **FR-042**: MUST existir una comprobación, ejecutada antes de dar la feature por terminada, de que
  esa versión optimizada arranca y de que todas sus pantallas funcionan. No es sustituible por
  pruebas automáticas, porque ninguna se ejecuta sobre ella.
- **FR-043**: La aplicación MUST seguir compilándose y pasando todas sus pruebas sin ninguna
  credencial configurada.

---

### Relación con los requisitos de las features 007 y 009

| Requisito anterior | Qué pasa | Aquí |
|---|---|---|
| 007 FR-012 / 009: «un documento sin texto utilizable nunca llega al servicio» | **Superado.** Existía porque lo que se enviaba era el texto. Al enviarse el documento, un escaneado es entrada válida | FR-002 |
| 007: el texto se extrae en el dispositivo conservando la página de cada fragmento | **Retirado.** Ya no hay extracción; las páginas las cita el servicio y las valida la aplicación contra el número real de páginas | FR-001, FR-005, FR-019 |
| 009 FR-001: el resumen cubre el documento completo | **Se mantiene**, ahora por construcción y no por cálculo de cuánto cabía | FR-001 |
| 009: tope superior de tamaño de texto por encima del cual la lectura es parcial | **Retirado.** No hay texto que medir. El tope pasa a ser el del propio documento, que ya se comprueba al descargarlo | FR-003 |
| 009 FR-031a: volver a mostrar el aviso una sola vez al cambiar su contenido | **Se repite**, por el mismo motivo y con contenido nuevo | FR-033 |
| 007/009: detección del PDF protegido con contraseña | **Se mantiene**, y sigue ocurriendo en el dispositivo | FR-004 |
| 007/009: no generar al observar, cuota propia, mensajes sin códigos, no registrar credencial ni contenido | **Se mantienen sin cambios** | FR-017, FR-024…FR-027, FR-028…FR-031, FR-035, FR-036 |

---

### Key Entities

- **Documento preparado**: el documento oficial de una publicación, disponible en el servicio para
  ser consultado sin volver a enviarlo. Pertenece a una publicación concreta y a un contenido
  concreto del documento; deja de existir al salir de la publicación o, como red de seguridad, al
  cumplirse el plazo de conservación del servicio. **Como mucho hay uno a la vez.**
- **Resumen guardado**: lo que la aplicación conserva de un resumen ya generado, junto con las
  condiciones en que se hizo, que son las que permiten decir después si sigue vigente o si es de una
  versión anterior. No se borra nunca.
- **Aviso de envío externo**: la declaración de qué sale del dispositivo, que se acepta una vez y
  vuelve a pedirse cuando su contenido cambia de forma sustancial.
- **Cuenta de consumo**: lo que la aplicación sabe de su propio gasto en el servicio, por unidad de
  tiempo corta y por día, y que le permite no enviar peticiones condenadas.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una publicación cuyo documento es un escaneado sin capa de texto obtiene un resumen
  completo, con al menos la explicación en lenguaje claro y una página citada. Hoy obtiene cero.
- **SC-002**: Un resumen ya guardado aparece en pantalla en menos de un segundo desde que se abre la
  pestaña, sin conexión de red.
- **SC-003**: Ningún resumen existente desaparece tras actualizar: el número de publicaciones con
  resumen antes y después es el mismo.
- **SC-004**: Abrir la pestaña de resumen y no tocar nada produce **cero** envíos al servicio.
- **SC-005**: Regenerar un resumen sin salir de la publicación produce **cero** envíos adicionales
  del documento.
- **SC-006**: Al salir de una publicación, el documento deja de estar disponible en el servicio.
- **SC-007**: Un documento protegido con contraseña produce **cero** envíos.
- **SC-008**: Quien ya había aceptado el aviso lo ve exactamente **una vez** más, y ninguna después.
- **SC-009**: Con el registro de diagnóstico activado y una generación completa, **cero** líneas
  contienen la credencial y **cero** contienen texto del documento.
- **SC-010**: El proyecto compila y pasa el cien por cien de sus pruebas sin credencial configurada.
- **SC-011**: La versión optimizada que se distribuye arranca y permite recorrer las **nueve** pantallas
  de la aplicación —arranque, boletín, panel de secciones, búsqueda, guardados, detalle, visor, resumen y
  acerca de— sin ningún cierre inesperado.
- **SC-012**: Ningún mensaje visible contiene un código numérico, un nombre de servicio o un nombre
  de modelo.

---

## Fuera de alcance

- **La pantalla Preguntar.** Es la feature siguiente. Aquí solo se deja preparado el mecanismo que la
  hará barata: que el documento se suba una vez y se comparta durante la visita.
- **Guardar el documento para leerlo sin conexión.** Sigue aplazada desde la feature 005.
- **Persistir conversaciones o resúmenes fuera de lo que ya se guarda.**
- **Cambiar la forma, las secciones o el contenido del resumen.** Lo que se ve debe seguir siendo lo
  mismo.
- **Cambiar la pestaña del documento, el visor, la búsqueda, los guardados o el boletín.**
- **Resumir varias publicaciones a la vez o por adelantado.**

---

## Assumptions

- **El servicio conserva el documento subido durante un plazo limitado y lo elimina solo.** Se asume
  del orden de un par de días, suficiente como red de seguridad y demasiado corto para ser un
  almacén. El plazo exacto se confirma en `plan.md`.
- **El servicio acepta el tamaño de documento que el boletín publica.** El límite de descarga que la
  aplicación ya aplica es muy inferior a lo que el servicio admite, así que ninguna publicación
  ordinaria debería rechazarse por tamaño.
- **El servicio sabe leer un PDF escaneado.** Es la premisa de la historia 1. Si resultara falso para
  el modelo elegido, la historia 1 no se cumple y hay que decirlo, no disimularlo.
- **La cuota del plan gratuito no cambia con este cambio.** Preparar el documento no debería contar
  como una generación, pero se asume que sí podría, y la cuenta propia se lleva sobre las
  generaciones en cualquier caso.
- **La librería elegida exige una versión del entorno de compilación superior a la actual del
  proyecto.** Subirla es un cambio de configuración de build, exento del ciclo SDD, pero se hace
  dentro de esta feature porque sin él no compila.
- **La librería elegida arrastra dependencias considerables.** De ahí FR-041: sin optimización, el
  tamaño de la aplicación distribuida crecería de forma desproporcionada para lo que aporta.
- **Nadie ha ejecutado nunca la versión optimizada de esta aplicación.** De ahí que FR-042 sea una
  comprobación manual y no una promesa.
- **Se mantiene, con conocimiento del propietario, que una credencial incluida en una aplicación
  distribuida es recuperable por quien la descargue.** No cambia con este cambio, y sigue siendo una
  decisión asumida, no un descuido.
