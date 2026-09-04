# Feature Specification: Resumen IA con proveedor nuevo

**Feature Branch**: `009-resumen-gemini`

**Created**: 4 de septiembre de 2026

**Status**: Draft

**Input**: User description: "Vamos a por otra Feature, bueno realmente no sé si es una feature o una FIX ya que lo que quiero es sustituir el uso del modelo Qwen por mediación de groq, a usar con la API-Key gratuita que tengo de Gemini, concretamente el modelo gemini-3.5-flash-lite. Por lo tanto quiero quitar groq y poner este modelo de Gemini si consideras que esto requiere SDD hazlo, lo que tú consideres. Claro ahora con este modelo cambia un poco el sistema ya que al tener el contexto de 1M ya entra cualquier texto extraído de los PDFs por lo tanto eso ya no será necesario de calcular si entra o no entra en el contexto del modelo ya que siempre va a entrar."

La ruta técnica —qué servicio, con qué superficie de API y cómo se cuentan sus límites— quedó cerrada
con el propietario antes de escribir esto y **se documenta en `plan.md`, no aquí**. Esta
especificación describe únicamente qué cambia para quien usa la aplicación y qué garantías hay que
seguir cumpliendo.

Decisiones cerradas que por tanto no se vuelven a plantear: el resumen sigue generándose **solo al
pulsarlo**; el texto se sigue extrayendo **en el dispositivo** y el documento **no** se envía en su
formato original; los resúmenes ya generados **no se borran**, se marcan como hechos con una versión
anterior y se pueden regenerar; y se conserva un **tope superior de tamaño** por encima del cual la
lectura vuelve a ser parcial, aunque ninguna publicación ordinaria del boletín lo alcance.

---

## Lo que hay que saber antes de leer nada más *(contexto imprescindible)*

- **Esto no añade una funcionalidad: cambia el suelo sobre el que se apoya una que ya existe.** El
  Resumen IA funciona desde la feature 007. Lo que se sustituye es el servicio de inteligencia
  artificial que lo produce.
- **Toda la funcionalidad actual está construida alrededor de una escasez que va a desaparecer.** El
  servicio de hoy admite tan poco texto de una vez que la mayoría de los documentos se leían **en
  parte**: se enviaban las primeras páginas que cabían y la pantalla lo avisaba dos veces, antes y
  después. El servicio nuevo admite del orden de mil veces más texto, así que **cualquier publicación
  del boletín entra completa**.
- **Esa escasez es también el origen de la mitad de los defectos de la 007**: respuestas que llegaban
  cortadas a media frase, resúmenes que llegaban en blanco, y reintentos que chocaban con el límite
  del mismo minuto. Retirar el racionamiento no es solo simplificar: es cerrar una familia de fallos.
- **Un boletín oficial no admite aproximaciones**, y eso no cambia con el proveedor. Una fecha mal
  copiada o un plazo inventado tienen consecuencias reales para quien se fía. Todo dato del resumen
  debe seguir siendo comprobable en el documento, y el resumen no es sustituto de nada.
- **Cambiar de proveedor cambia las condiciones con las que se generó cada resumen guardado.** Hay
  personas con resúmenes ya hechos en su dispositivo. Ninguno debe desaparecer.
- **El servicio nuevo tampoco es ilimitado, y sigue siendo de plan gratuito**, con límites por unidad
  de tiempo corta y por día, compartidos por toda la aplicación y no por persona. Cada resumen sigue
  costando cuota, y generar sin que nadie lo pida seguiría siendo gastarla en publicaciones que nadie
  va a leer.
- **El servicio nuevo no informa de cuánta cuota queda.** El de hoy lo decía en cada respuesta. El
  nuevo no: la aplicación tiene que llevar la cuenta por sí misma si quiere seguir evitando consultas
  condenadas de antemano y seguir distinguiendo «espera unos segundos» de «vuelve mañana».
- **Los defectos que de verdad rompieron el Resumen IA en un móvil vivían al otro lado de la frontera
  con el servicio**, donde todas las pruebas automáticas ponen dobles. Los encontró el registro de
  diagnóstico en un dispositivo real. Cambiar de proveedor vuelve a poner esa frontera en juego.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Un resumen que cubre el documento entero (Priority: P1)

Una persona abre una publicación larga —un pliego de catorce páginas, un listado de treinta— y pide
su resumen. Antes leía «Documento de 14 páginas. Se analizarán las 6 primeras», y el resumen se
quedaba a medias: los plazos que estaban en la página nueve simplemente no aparecían. Ahora el
resumen cubre el documento completo y esa advertencia no aparece.

**Why this priority**: Es el motivo entero del cambio y lo único que la persona nota de verdad. Un
resumen parcial de un documento oficial es exactamente el tipo de media verdad que esta aplicación no
quería producir.

**Independent Test**: Se comprueba de principio a fin generando el resumen de una publicación de más
de diez páginas con datos repartidos por todo el documento, y verificando que la ficha recoge
elementos de las páginas finales y que la cobertura se declara completa.

**Acceptance Scenarios**:

1. **Given** una publicación con documento oficial de catorce páginas con texto, **When** la persona
   pulsa Generar resumen, **Then** el sistema no muestra ningún aviso de cobertura parcial ni antes
   ni después, y el resumen declara haber cubierto el documento completo.
2. **Given** ese mismo resumen ya generado, **When** la persona lo revisa, **Then** encuentra
   elementos respaldados por páginas de la segunda mitad del documento, y cada referencia abre el
   documento oficial por esa página.
3. **Given** una publicación de una sola página, **When** la persona pide su resumen, **Then** el
   comportamiento es idéntico al de hoy: sin avisos de parcialidad y con cobertura completa.

---

### User Story 2 - Lo que ya estaba resumido no se pierde (Priority: P1)

Una persona que ya usaba la aplicación tiene varias publicaciones con su resumen hecho. Tras
actualizar, los abre. Siguen ahí, se leen igual, y llevan la indicación de que se hicieron con una
versión anterior; si quiere, los regenera y obtiene uno nuevo que cubre el documento completo.

**Why this priority**: Es el riesgo real del cambio. Un cambio de proveedor que vaciara el trabajo ya
hecho en el dispositivo de alguien sería una regresión, no una mejora, y la aplicación tiene por norma
no borrar nada de lo que ya guardó.

**Independent Test**: Se comprueba con un resumen creado antes del cambio: abrir la pestaña, verlo
completo y marcado, regenerarlo, y comprobar que el nuevo sustituye al anterior sin que en ningún
momento la pantalla se quede sin resumen.

**Acceptance Scenarios**:

1. **Given** un resumen generado con el proveedor anterior, **When** la persona abre la pestaña
   Resumen IA, **Then** el resumen se muestra completo, sin consultar el servicio, y con la
   indicación de que se hizo con una versión anterior del documento o de las condiciones.
2. **Given** ese resumen marcado, **When** la persona pulsa regenerar, **Then** se genera uno nuevo y
   sustituye al anterior.
3. **Given** ese resumen marcado, **When** la persona no pulsa nada, **Then** el sistema no lo
   regenera por su cuenta ni consulta el servicio.

---

### User Story 3 - Saber qué pasa cuando el servicio dice basta (Priority: P2)

Una persona pide varios resúmenes seguidos y alcanza el límite del servicio. La aplicación se lo dice
en castellano corriente: si la espera es de segundos, cuánto falta y que siga sola; si lo agotado es
el cupo del día, que vuelva mañana y que de momento no insista. En ningún caso ve un código, una
traza ni el nombre del servicio.

**Why this priority**: El plan sigue siendo gratuito y el límite se alcanza de verdad. Que la
aplicación deje de saber cuánta cuota le queda —porque el proveedor nuevo no lo dice— no puede
traducirse en que la persona se quede sin explicación.

**Independent Test**: Se comprueba forzando el límite y verificando que los dos casos —espera corta y
cupo diario— producen mensajes distintos, que solo el primero ofrece continuar, y que ninguno de los
dos contiene jerga técnica.

**Acceptance Scenarios**:

1. **Given** que la aplicación sabe que no hay margen en este momento, **When** la persona pide un
   resumen, **Then** el sistema no lanza la consulta, indica aproximadamente cuánto falta y continúa
   por sí solo cuando haya margen.
2. **Given** que el cupo del día está agotado, **When** la persona pide un resumen, **Then** el
   sistema lo explica y no ofrece un reintento inmediato.
3. **Given** cualquiera de los dos casos, **When** la persona lee el mensaje, **Then** no aparece
   ningún código de estado, traza, mensaje interno del proveedor, ni el nombre del servicio o del
   modelo.
4. **Given** un fallo del que se puede volver, **When** la persona lo ve, **Then** se le ofrece
   reintentar; y ante uno del que no, no se le ofrece.

---

### User Story 4 - La aplicación sigue siendo compilable y probable sin la credencial (Priority: P3)

Quien clona el repositorio sin la credencial del servicio puede compilar la aplicación y pasar todas
sus pruebas. Al pedir un resumen, la aplicación explica que la función no está configurada en esta
copia, y no insiste.

**Why this priority**: Es la garantía que mantiene el proyecto abierto y la integración continua en
verde sin secretos. Es discreta pero se rompe con facilidad al cambiar de proveedor.

**Independent Test**: Se comprueba compilando y ejecutando las pruebas sin credencial configurada, y
después pidiendo un resumen en la aplicación resultante.

**Acceptance Scenarios**:

1. **Given** un entorno sin la credencial del servicio, **When** se compila la aplicación y se
   ejecutan sus pruebas, **Then** ambas cosas terminan sin error.
2. **Given** esa aplicación, **When** la persona pide un resumen, **Then** el sistema lo explica como
   una limitación de la aplicación y no ofrece un reintento inútil.
3. **Given** cualquier entorno, **When** se revisa el repositorio de código, **Then** la credencial no
   aparece escrita en él.

---

### Edge Cases

- **Un documento tan grande que ni el servicio nuevo lo admita.** Existe el tope superior: la lectura
  vuelve a ser parcial y se advierte antes y después, como hasta ahora. Ninguna publicación ordinaria
  del boletín debería alcanzarlo.
- **Un documento sin texto utilizable** —escaneado, protegido o vacío—: sigue sin llegar al servicio.
  El cambio de proveedor no relaja esta puerta.
- **El servicio responde algo que no se puede usar** —incompleto, vacío o mal formado—: no se muestra
  ni se guarda, y la persona ve que no se pudo construir un resumen fiable con la opción de consultar
  el documento oficial.
- **Un reintento automático que no puede ejecutarse.** Si al reintentar ya no hay margen de cuota, la
  persona debe seguir viendo el motivo original del fallo y no uno nuevo inducido por el reintento.
- **La persona abandona la pantalla durante la generación**: se detiene, como hoy.
- **Un resumen guardado cuyo formato ya no se entiende**: se trata como ausente y se ofrece generar,
  nunca se rompe la pantalla.
- **Quien ya había aceptado el aviso de envío externo.** El aviso cambia de contenido, así que vuelve
  a mostrarse una vez —y solo una— para que nadie se quede sin haber leído lo que ahora dice.
- **Una sección con más elementos de los que caben.** El resumen selecciona los más relevantes y lo
  advierte; no los descarta en silencio.
- **El documento del que se hizo un resumen cambia en el servicio**: el resumen sigue marcado como
  hecho con una versión anterior.

## Requirements *(mandatory)*

### Functional Requirements

#### El resumen cubre el documento completo

- **FR-001**: El resumen MUST construirse a partir del texto de **todas** las páginas con texto del
  documento oficial, no de una selección de las primeras.
- **FR-002**: Cuando el resumen cubra el documento completo, el sistema MUST NOT mostrar aviso alguno
  de cobertura parcial, ni antes de generar ni junto al resultado.
- **FR-003**: El sistema MUST seguir resumiendo cada publicación con una **única** consulta al
  servicio.
- **FR-004**: El sistema MUST conservar un límite superior de tamaño de documento por encima del cual
  la lectura sea parcial. Ese límite MUST quedar por encima de cualquier publicación ordinaria del
  boletín, de modo que en uso normal no se alcance nunca.
- **FR-005**: Cuando la lectura sea parcial por ese límite, el sistema MUST advertirlo **antes** de
  consultar el servicio y MUST indicar junto al resultado qué páginas se analizaron.
- **FR-006**: El sistema MUST NOT declarar cobertura completa cuando no analizó todas las páginas con
  texto, **ni siquiera si el servicio lo afirma**.
- **FR-007**: Cada sección del resumen MUST limitarse a un máximo de **diez** elementos. Cuando el
  documento sustente más, el sistema MUST pedir al servicio que seleccione los más relevantes en
  lugar de volcarlos todos, y el resumen MUST poder advertir entre sus advertencias que una sección
  dejó elementos fuera.

#### Lo que ya estaba resumido

- **FR-008**: Los resúmenes generados antes de este cambio MUST seguir visibles en el dispositivo y
  MUST NOT borrarse.
- **FR-009**: Un resumen generado en condiciones distintas de las vigentes MUST mostrarse marcado
  como hecho con una versión anterior.
- **FR-010**: El sistema MUST permitir regenerar ese resumen a petición explícita de la persona, y
  MUST NOT regenerarlo por su cuenta.
- **FR-011**: Regenerar MUST sustituir el resumen anterior sin que en ningún momento la pantalla se
  quede sin resumen que mostrar.

#### Garantías que el cambio no debe romper

- **FR-012**: El sistema MUST NOT generar ningún resumen sin una acción explícita de la persona.
- **FR-013**: El texto MUST extraerse en el propio dispositivo, conservando a qué página pertenece
  cada fragmento.
- **FR-014**: El sistema MUST NOT enviar el documento en su formato original al servicio externo;
  solo texto extraído.
- **FR-015**: Si el documento no contiene texto utilizable, el sistema MUST NOT consultar el
  servicio.
- **FR-016**: El sistema MUST NOT enviar al servicio externo nada de la persona: ni lo que ha
  guardado, ni lo que ha leído, ni identificadores suyos.
- **FR-017**: Toda referencia de página mostrada MUST corresponder a una página que existe en el
  documento y que se analizó, y MUST abrir el documento oficial por esa página.
- **FR-018**: El resumen MUST seguir identificándose como generado por inteligencia artificial, con
  la advertencia de comprobar el texto oficial, y esa advertencia MUST viajar dentro del texto al
  copiar y al compartir.
- **FR-019**: El sistema MUST NOT mostrar ni guardar un resumen que llegue vacío, incompleto o mal
  formado.

#### Límites de uso del servicio

- **FR-020**: El sistema MUST respetar los límites de uso del servicio y MUST NOT lanzar consultas
  cuando ya sabe que no hay margen disponible.
- **FR-021**: El sistema MUST llevar por sí mismo la cuenta del consumo, **sin depender de que el
  servicio informe de la cuota restante**.
- **FR-022**: El sistema MUST distinguir el límite por unidad de tiempo corta del límite diario y
  MUST tratarlos de forma distinta ante la persona.
- **FR-023**: Cuando haya que esperar por cuota, el sistema MUST indicar aproximadamente cuánto falta
  y MUST continuar por sí solo cuando haya margen.
- **FR-024**: Cuando el límite agotado sea el diario, el sistema MUST decirlo y MUST NOT ofrecer un
  reintento inmediato.
- **FR-025**: Un reintento automático MUST NOT convertir un fallo en otro distinto: si al reintentar
  ya no hay margen, la persona MUST seguir viendo el motivo original.
- **FR-026**: Todo fallo recuperable MUST ofrecer reintentar; los no recuperables MUST NOT ofrecerlo.
  En cualquier fallo MUST poder abrirse el documento oficial.

#### Lo que se dice y lo que no se dice

- **FR-027**: Los mensajes de error MUST explicar la situación en lenguaje corriente y MUST NOT
  mostrar códigos de estado, trazas ni mensajes internos del proveedor.
- **FR-028**: Ningún texto mostrado a la persona MUST nombrar al proveedor del servicio ni al modelo,
  ni antes ni después del cambio.
- **FR-029**: Si el servicio no está configurado en la aplicación, el sistema MUST presentarlo como
  una limitación de la aplicación y MUST NOT ofrecer un reintento inútil.

#### Privacidad y credencial

- **FR-030**: El sistema MUST pedir al servicio que **no conserve** el contenido enviado más allá de
  lo necesario para responder.
- **FR-031**: La primera vez que se solicita un resumen en el dispositivo, el sistema MUST seguir
  explicando que el texto del documento oficial sale del dispositivo, con opción de cancelar. Ese
  aviso MUST decir **además** que el servicio puede usar el texto de ese documento público para
  mejorar sus modelos, y MUST dejar claro que lo que viaja es el documento oficial y nada de la
  persona.
- **FR-031a**: Como el contenido del aviso cambia de forma sustancial, quien ya lo había aceptado
  MUST volver a verlo **una sola vez**. Tras aceptarlo, MUST NOT mostrarse de nuevo.
- **FR-032**: El sistema MUST NOT registrar en diagnósticos, informes de fallo ni analítica la
  credencial del servicio ni el contenido de ningún documento.
- **FR-033**: La credencial MUST NOT quedar escrita en el repositorio de código, y su ausencia MUST
  NOT impedir compilar la aplicación ni ejecutar sus pruebas.

#### Diagnóstico

- **FR-034**: Cuando una generación falle, el sistema MUST dejar constancia en el registro de
  diagnóstico de la fase alcanzada y del motivo, con detalle suficiente para distinguir causas que en
  pantalla comparten un mismo mensaje, y MUST NOT incluir en ese registro la credencial ni el
  contenido del documento.

### Relación con los requisitos de la feature 007

Esta feature no reescribe el Resumen IA: lo reasienta. La tabla dice qué pasa con los requisitos de
`specs/007-resumen-ia/spec.md` que este cambio toca. Los no citados siguen vigentes tal cual.

| 007 | Qué pasa | Aquí |
|---|---|---|
| FR-027 una sola consulta | Se mantiene | FR-003 |
| FR-028 avisar antes si no cabe | **Se degrada a caso extremo**: deja de ser lo normal y pasa a ser el tope superior | FR-004, FR-005 |
| FR-029 indicar qué páginas se analizaron | Se mantiene, ligado al tope | FR-005 |
| FR-030 no declarar cobertura falsa | Se mantiene intacto | FR-006 |
| FR-031 cortar la primera página si no cabe | Se mantiene, ligado al tope | FR-005 |
| FR-035 obsoleto, no ausente | Se mantiene y **se activa en masa** por el cambio de condiciones | FR-008, FR-009, FR-010 |
| FR-037 no consultar sin margen | Se mantiene, pero **cambia cómo se sabe** | FR-020, FR-021 |
| FR-038 indicar cuánto falta | Se mantiene | FR-023 |
| FR-039 el límite diario se dice y no se reintenta | Se mantiene, y ahora exige distinguirlo sin ayuda del servicio | FR-022, FR-024 |
| FR-040 sin códigos ni jerga | Se mantiene y **se amplía**: tampoco el nombre del proveedor | FR-027, FR-028 |
| FR-042 servicio no configurado | Se mantiene | FR-029 |
| FR-043 aviso de envío externo la primera vez | Se mantiene y **se amplía** con la reutilización para mejorar modelos | FR-031 |
| FR-045 el aviso no vuelve a mostrarse | Se mantiene, pero **se reinicia una vez** porque su contenido cambia | FR-031a |
| FR-013 secciones del resumen | Se mantiene, y **se acota** la extensión de cada una | FR-007 |
| FR-047, FR-048 credencial y contenido fuera de los registros | Se mantienen | FR-032, FR-033 |

### Key Entities

- **Resumen almacenado**: lo que se guarda por publicación. Además del resumen, guarda **con qué
  condiciones se generó** —qué servicio, con qué instrucciones y con qué formato de respuesta— y el
  coste real de la consulta. Comparar esas condiciones con las vigentes es lo que decide si un
  resumen se marca como hecho con una versión anterior.
- **Cobertura**: qué páginas del documento se analizaron y si el resultado cubre el documento
  completo. Tras este cambio, lo normal es que sea completa.
- **Cuenta de consumo**: lo que la aplicación lleva por sí misma para saber si le queda margen antes
  de consultar. Distingue el límite por unidad de tiempo corta del límite diario. Es un dato de la
  aplicación, no de la persona, y no se envía a ninguna parte.
- **Aviso de envío externo**: la aceptación, una sola vez por dispositivo, de que el texto del
  documento oficial sale del dispositivo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los resúmenes de publicaciones de hasta cien páginas con texto declara
  cobertura completa, y ninguno muestra aviso de cobertura parcial.
- **SC-002**: Ningún resumen mostrado declara haber cubierto páginas que no se analizaron.
- **SC-003**: El 100 % de las referencias de página mostradas corresponden a páginas existentes del
  documento y abren el documento por esa página.
- **SC-004**: El 100 % de los resúmenes generados antes del cambio siguen visibles tras actualizar,
  marcados como hechos con una versión anterior, y se pueden regenerar.
- **SC-005**: El número de consultas al servicio externo es exactamente cero mientras nadie pulse
  generar o regenerar.
- **SC-006**: Un documento sin texto utilizable produce cero consultas al servicio externo.
- **SC-007**: Se puede generar al menos un resumen por minuto de forma sostenida sin que la persona
  vea un error de límite.
- **SC-008**: Alcanzado el límite diario, el número de consultas adicionales al servicio es cero
  hasta su reposición.
- **SC-009**: Ningún mensaje mostrado a la persona contiene códigos de estado, trazas, mensajes
  internos del proveedor, ni el nombre del proveedor o del modelo.
- **SC-010**: Ni la credencial del servicio ni el contenido de ningún documento aparecen en los
  registros de diagnóstico, en los informes de fallo ni en la analítica.
- **SC-011**: La aplicación compila y pasa el 100 % de sus pruebas sin credencial del servicio
  configurada.
- **SC-012**: Ante un fallo de generación, el registro de diagnóstico permite distinguir la fase y la
  causa en el 100 % de los casos en que la pantalla muestra un mismo mensaje para causas distintas.
- **SC-013**: Ninguna sección de ningún resumen mostrado contiene más de diez elementos, y cuando el
  documento sustentaba más, el resumen lo advierte.
- **SC-014**: El aviso de envío externo, con su texto nuevo, se muestra exactamente una vez por
  dispositivo tras actualizar, incluso a quien ya había aceptado el anterior.

## Fuera de alcance

- **Enviar el documento al servicio en su formato original**, aunque el servicio nuevo lo admita.
  Contradice FR-014 y gasta datos de la persona. Es candidata a una feature futura, no a esta.
- **La pantalla Preguntar** y el resumen conversacional. Sigue siendo una feature aparte.
- **Cambiar la extensión, el tono o la estructura de la prosa del resumen.** La tarjeta está
  calibrada para el tamaño actual; tocarla es una decisión de producto, no de proveedor.
- **Custodiar la credencial en un servicio propio intermedio.** Sigue fuera de alcance, igual que en
  la 007.
- **Guardar el texto del documento** para no volver a extraerlo. Sigue fuera de alcance: extraerlo es
  local y gratuito, y almacenarlo crecería sin tope.
- **Añadir capacidades nuevas del servicio** —búsqueda, ejecución de código, herramientas—. Esta
  feature sustituye un proveedor; no amplía lo que el resumen hace.

## Assumptions

- **El propietario dispone de una credencial del servicio nuevo en plan gratuito**, ya configurada en
  su máquina de desarrollo y fuera del repositorio. Se asume, con su conocimiento, que una credencial
  incluida en una aplicación distribuida es recuperable por quien la analice.
- **Los límites exactos del plan gratuito se leen en el panel del proveedor, no se suponen.** El
  proveedor nuevo ya no los publica en su documentación. Fijarlos corresponde a `plan.md`.
- **Una publicación ordinaria del boletín ocupa entre una y cinco páginas**, y las excepcionales
  —presupuestos, listados— llegan a varias decenas. El tope superior de FR-004 se dimensiona sobre
  esa realidad, no sobre el máximo teórico del servicio.
- **Se acepta que todos los resúmenes ya guardados queden marcados como hechos con una versión
  anterior** en cuanto se instale la versión nueva. Es consecuencia directa y deseada de FR-009.
- **La pantalla y su diseño no cambian.** El apartado §20 del documento de diseño sigue vigente. Los
  únicos cambios de texto son la desaparición de los avisos de cobertura parcial en el uso normal y
  la frase nueva del aviso de envío externo.
- **Diez elementos por sección se considera suficiente** para cualquier publicación del boletín. El
  número sale de dimensionar la tarjeta del §20 sobre un scroll razonable, no de una medición: si en
  uso real resultara corto, subirlo es cambiar una cifra.
- **Se acepta que el aviso de envío externo reaparezca una vez** a quien ya lo había aceptado. Es la
  consecuencia directa de FR-031a y el precio de que el aviso diga toda la verdad.
- **La aplicación sigue sin conservar el documento para leerlo sin conexión.** Ese aplazamiento viene
  de la feature 005 y este cambio no lo toca.
