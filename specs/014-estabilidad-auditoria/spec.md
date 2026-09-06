# Feature Specification: Estabilidad tras la auditoría — lo prometido, cumplido también cuando algo falla

**Feature Branch**: `014-estabilidad-auditoria`

**Created**: 6 de septiembre de 2026

**Status**: Draft

**Input**: User description: "Estabilidad tras la auditoría técnica. La auditoría de la fase 1
(docs/auditoria/01-hallazgos.md, baseline ee88d24, 6 de septiembre de 2026) confirmó nueve hallazgos;
esta feature corrige los cuatro de estabilidad y el de cancelación de red, y deja fuera a propósito los
de seguridad y privacidad (SEC-001, SEC-002, SEC-003) y el de caché (PERF-001). Cinco mejoras: (1)
STAB-001, alta: abrir una publicación cuya copia local del documento tiene el fichero lateral del
checksum vacío o truncado cierra la aplicación, y vuelve a cerrarla cada vez que se reabre; debe abrirse
con normalidad, repararse sola y quedar registrado el incidente sin datos personales. (2) STAB-002: si
falla la descarga o el almacenamiento del documento por una excepción inesperada (disco lleno, fichero
que no se puede mover), el detalle y el visor se quedan en «cargando» para siempre; debe verse el error
con reintento, como ya pasa con los rechazos normales, y quien espere la misma descarga no debe quedarse
colgado. (3) STAB-003: un aviso que coincidió con una regla activa pero no pudo registrarse (fallo al
escribir, fallo al leer las publicaciones nuevas, o el proceso murió entre guardar el boletín y
registrar la coincidencia) se pierde para siempre, porque el siguiente ciclo ya no ve esas publicaciones
como nuevas; debe recuperarse en el siguiente ciclo, entregarse exactamente una vez, seguir sin ser
retroactivo (una regla creada o editada después de que la publicación se almacenara no dispara por ella)
y la primera sincronización de una instalación sigue siendo línea base sin avisos. (4) STAB-004: las
listas observadas —Inicio, Guardados, Buscar, Avisos y el contador de la campana— dejan de actualizarse
tras un fallo transitorio de lectura del almacén y se quedan mostrando vacío hasta que se recree la
pantalla; deben recuperarse solas con reintentos acotados y seguir reflejando los cambios posteriores;
NO se pide distinguir en pantalla «vacío» de «fallo de lectura»: la pantalla sigue mostrando vacío
mientras dura el fallo. (5) PERF-002: salir de una pantalla mientras se descarga un documento, se leen
las fuentes RSS, se sube un documento al servicio de IA o se espera un resumen o una respuesta no
detiene la petición de red: sigue consumiendo red y un hilo hasta que responda o agote el tiempo (hasta
180 s en un PDF); cancelar debe detener la petición de verdad y pronto, sin cambiar quién cancela ni
cuándo, y una cancelación nunca debe presentarse como fallo de red ni provocar reintentos. Toda
corrección lleva su prueba de regresión que falle antes del arreglo; ninguna pantalla cambia de aspecto
ni de comportamiento visible salvo lo descrito; los registros siguen sin llevar títulos, palabras clave,
nombres de reglas, texto de documentos ni credenciales."

---

## Lo que hay que saber antes de leer nada más *(contexto imprescindible)*

- **De dónde sale esta feature.** De la auditoría técnica de la fase 1, cuyo informe es
  [`docs/auditoria/01-hallazgos.md`](../../docs/auditoria/01-hallazgos.md): nueve hallazgos con
  evidencia de código, seis de ellos reproducidos además ejecutando las clases reales de la aplicación
  en aislamiento. Esta feature toma **cinco**: STAB-001, STAB-002, STAB-003, STAB-004 y PERF-002. Los
  tres de seguridad y privacidad (SEC-001, SEC-002, SEC-003) y el de la caché de documentos (PERF-001)
  quedan fuera **por decisión del propietario**, no por olvido, y no se tocan ni de paso.
- **Ninguno de los cinco se ve en el camino feliz.** Aparecen cuando algo falla: un fichero a medio
  escribir, un disco lleno, una lectura del almacén que se rompe un instante, un proceso que Android
  mata entre dos pasos, alguien que sale de una pantalla a mitad de una descarga. Por eso sobrevivieron a
  trece features y a mil ciento noventa y tres pruebas: las pruebas de esta casa doblan las fronteras, y
  los cinco defectos viven justo en lo que pasa **cuando la frontera falla**.
- **Los cinco tienen la misma forma.** En cada uno hay una promesa que el propio código documenta
  —«aquí nada lanza», «la lista sigue viva tras un fallo de lectura», «cancelar no es un problema de
  red», «una coincidencia se entrega una vez»— y un camino de fallo por el que la promesa no se cumple.
  La feature hace verdad cada promesa y deja la prueba que la habría cazado.
- **Lo que NO cambia.** Ninguna pantalla cambia de aspecto, de textos ni de navegación. Ningún dato
  mostrado cambia. Quién cancela una operación y cuándo sigue siendo lo que decidieron las features 004,
  007 y 011. Lo único visible que cambia es lo descrito en las cinco historias: que la aplicación deja
  de cerrarse, de quedarse cargando, de perder avisos, de quedarse en blanco y de gastar red por nada.
- **Tres decisiones del propietario, tomadas el 6 de septiembre de 2026, antes de redactar esto:**
  1. **STAB-004 solo recupera la observación.** La pantalla sigue mostrando «vacío» mientras dura el
     fallo; no se añade un estado «no se ha podido leer». Distinguirlos tocaría seis pantallas y queda
     anotado como fuera de alcance.
  2. **De la carpeta de la auditoría se versionan solo los informes** (`00-mapa.md`, `01-hallazgos.md`,
     `PROGRESO.md`), para que esta spec pueda enlazar los hallazgos por su identificador. Los programas
     de diagnóstico, sus ficheros de prueba y los registros quedan fuera del control de versiones.
  3. **Los commits siguen el patrón de las features anteriores**: uno de documentación tras
     especificar y planificar, otro de implementación tras las cuatro puertas; el merge a `main` lo hace
     el propietario.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Una copia local dañada no cierra la aplicación (Priority: P1)

Alguien abre una publicación que ya leyó hace unos días. El documento sigue guardado en el móvil, pero
su fichero lateral de verificación quedó vacío o a medias, porque la escritura se interrumpió: el
proceso murió, el disco se llenó, el sistema recortó la caché en mal momento. Hoy la aplicación **se
cierra** al abrir esa publicación, y se vuelve a cerrar cada vez que se intenta: la única salida es
borrar los datos de la aplicación. Debe abrirse con normalidad, la copia debe repararse sola y el
incidente debe quedar registrado para diagnóstico sin ningún dato de la persona ni de la publicación.

**Why this priority**: es el único hallazgo de severidad alta de la auditoría, y es un cierre en bucle
sobre una publicación concreta del que no se sale desde la aplicación. Un cierre que se repite es la
forma más rápida de perder a quien lo sufre.

**Independent Test**: dañar a mano el fichero lateral de un documento ya guardado (dejarlo vacío, o con
la mitad de los caracteres) y abrir la publicación. Se comprueba sin tocar ninguna otra parte de la
feature.

**Acceptance Scenarios**:

1. **Given** una publicación con copia local cuyo fichero lateral de verificación está vacío, **When**
   la persona abre su documento, **Then** el documento se muestra, la aplicación no se cierra, y la
   siguiente vez que se abre también se muestra.
2. **Given** el fichero lateral está incompleto o con caracteres que no son una huella válida, **When**
   se abre el documento, **Then** ocurre lo mismo que en el caso anterior.
3. **Given** la copia local no puede leerse por cualquier otro motivo inesperado, **When** se abre el
   documento, **Then** la aplicación lo descarga de nuevo, lo muestra, y la copia queda reparada.
4. **Given** el fichero lateral falta por completo (caso que hoy ya se contempla), **When** se abre el
   documento, **Then** el comportamiento es el mismo que hasta ahora: el documento se muestra.
5. **Given** cualquiera de los casos anteriores, **When** se consulta el informe de fallos, **Then**
   consta el tipo de incidente y de dónde vino, y no consta ni el título, ni la dirección, ni el
   identificador de la publicación.
6. **Given** la copia local dañada, **When** la persona intenta **compartir** la publicación desde
   Inicio, Guardados, Buscar o el detalle, o pide un resumen o una respuesta de IA, **Then** ninguna de
   esas acciones cierra la aplicación.

---

### User Story 2 - Un fallo al guardar el documento se ve y se puede reintentar (Priority: P1)

La descarga de un documento termina bien, pero guardarlo falla por algo inesperado: el disco está lleno,
el fichero no se puede mover a su sitio. Hoy la pestaña Documento del detalle y el visor se quedan
girando **para siempre**; la única salida es abandonar la pantalla. Debe verse el mismo error con botón
de reintentar que ya se muestra cuando la red falla; el reintento debe funcionar cuando la causa haya
desaparecido; y si otra pantalla esperaba esa misma descarga, tampoco debe quedarse colgada.

**Why this priority**: una espera sin fin se distingue mal de un fallo de la aplicación y no ofrece
ninguna acción. El error con reintento ya existe para los rechazos previstos; falta para los
imprevistos, que son justo los que no se pueden reproducir a voluntad.

**Independent Test**: provocar un fallo al guardar el documento (disco sin espacio) y abrir la pestaña
Documento; después liberar espacio y pulsar «Reintentar».

**Acceptance Scenarios**:

1. **Given** una descarga cuyo almacenamiento falla de forma inesperada, **When** la pestaña Documento
   está a la vista, **Then** se muestra el estado de error con «Reintentar», nunca el indicador de carga
   indefinido.
2. **Given** ese error en pantalla, **When** la causa ha desaparecido y la persona pulsa «Reintentar»,
   **Then** el documento se descarga y se muestra.
3. **Given** dos pantallas esperando el mismo documento, **When** la descarga falla de forma
   inesperada, **Then** las dos ven el error y ninguna se queda esperando.
4. **Given** una descarga en curso, **When** la persona abandona la pantalla, **Then** no se muestra ni
   se registra ningún error, el documento queda como «no descargado» y la siguiente visita empieza la
   descarga de nuevo mostrando su progreso.
5. **Given** dos pantallas esperando el mismo documento, **When** la que inició la descarga la cancela,
   **Then** la otra obtiene igualmente el documento: la descarga se retoma para ella en vez de quedarse
   esperando o fallar.
6. **Given** un fallo inesperado seguido de un fallo al limpiar sus restos, **When** se observa la
   pantalla, **Then** se ve el error original con reintento, y no quedan ficheros a medias que puedan
   confundirse con un documento.

---

### User Story 3 - Un aviso encontrado no se pierde: se recupera una sola vez (Priority: P1)

Una persona tiene un aviso activo, por ejemplo «ganadería». La sincronización trae una publicación nueva
que coincide, pero registrar la coincidencia falla —el almacén no pudo escribir— o Android mata el
proceso entre guardar el boletín y anotar la coincidencia. Hoy ese aviso **se pierde para siempre**: el
ciclo termina como correcto, y el siguiente ya no ve esa publicación como nueva. Debe recuperarse en el
siguiente ciclo, entregarse **exactamente una vez** (notificación o aviso dentro de la aplicación,
entrada en Novedades y contador de la campana), sin duplicar nada, sin volverse retroactivo, y la
primera sincronización de una instalación debe seguir siendo línea base sin avisos.

**Why this priority**: es la promesa central de la feature 012 —«te avisaremos al encontrar una
publicación nueva que coincida»— rota en silencio y sin rastro. Quien confía en un aviso no vuelve a
mirar la sección a mano; si el aviso se pierde, la publicación se pierde para esa persona.

**Independent Test**: con un aviso activo, hacer fallar el registro de coincidencias en un ciclo y lanzar
otro con las fuentes sin cambios; comprobar que llega una notificación y solo una, y que un tercer ciclo
no añade nada.

**Acceptance Scenarios**:

1. **Given** un aviso activo y una publicación nueva que coincide cuya coincidencia no pudo
   registrarse, **When** se ejecuta el siguiente ciclo aunque las fuentes no hayan cambiado, **Then**
   se entrega exactamente una notificación y una novedad, y un tercer ciclo no entrega nada más.
2. **Given** una publicación pendiente de un ciclo anterior y un aviso **creado después** de que esa
   publicación se almacenara, **When** se ejecuta el siguiente ciclo, **Then** el aviso nuevo no
   dispara por ella; los avisos que ya estaban activos cuando se almacenó sí.
3. **Given** la primera sincronización de una instalación, **When** termina, **Then** no queda nada
   pendiente de evaluar y no se avisa de nada; el ciclo siguiente no convierte ese histórico en
   novedades.
4. **Given** publicaciones nuevas y ningún aviso activo, **When** termina el ciclo, **Then** esas
   publicaciones se dan por evaluadas y un aviso creado después no dispara por ellas.
5. **Given** coincidencias registradas correctamente pero un fallo al anotar que ya se evaluaron,
   **When** termina el ciclo, **Then** la entrega se produce igualmente, una vez, y el ciclo siguiente
   no la repite.
6. **Given** un fallo al leer los avisos activos, **When** se ejecuta el ciclo, **Then** el boletín se
   actualiza igualmente y la evaluación espera al siguiente ciclo sin perder nada.
7. **Given** un lote de coincidencias en el que una no puede registrarse, **When** falla el registro,
   **Then** no queda ninguna registrada a medias: el siguiente ciclo registra y entrega el lote entero.
8. **Given** publicaciones pendientes de un ciclo anterior y una copia del boletín tan reciente que el
   ciclo no consulta la red, **When** se ejecuta el ciclo, **Then** las pendientes se evalúan y se
   entregan igualmente.

---

### User Story 4 - Las listas se recuperan tras un fallo de lectura (Priority: P2)

Una lectura del almacén falla un instante —el almacén estaba ocupado, un error de entrada/salida— mientras
Inicio, Guardados, Buscar o Avisos están en pantalla, o mientras la campana cuenta las novedades. Hoy la
lista se queda **vacía para siempre** aunque el almacén vuelva a funcionar y aunque lleguen publicaciones
nuevas: solo recrear la pantalla la arregla. En el caso de la campana, ni eso: su contador vive toda la
sesión y se queda a cero. Debe recuperarse sola en segundos, seguir reflejando los cambios posteriores
y, si el fallo es permanente, dejar de intentarlo tras unos pocos reintentos y anotarlo.

**Why this priority**: no cierra la aplicación ni pierde datos, pero deja una pantalla que **miente**
—«no hay nada»— y que no ofrece ninguna acción para salir de ahí. Y el contador de la campana es la
forma de saber que ha llegado un aviso sin abrir la pestaña.

**Independent Test**: provocar un fallo de lectura de una vez y comprobar que la lista vuelve sola, y que
un cambio posterior (una publicación nueva, un guardado) se refleja sin recrear la pantalla.

**Acceptance Scenarios**:

1. **Given** una lista en pantalla y un fallo transitorio al leerla, **When** pasa un instante, **Then**
   la lista se muestra vacía brevemente, vuelve sola sin ninguna acción de la persona, y un cambio
   posterior en el almacén se refleja en ella.
2. **Given** el contador de la campana y un fallo transitorio, **When** se recupera, **Then** el
   contador vuelve a su valor y un aviso posterior lo incrementa.
3. **Given** un fallo permanente de lectura, **When** se agotan los reintentos, **Then** la observación
   se detiene sin cerrar la aplicación ni consumir recursos indefinidamente, el fallo queda anotado y la
   pantalla sigue mostrando vacío; recrear la pantalla vuelve a intentarlo.
4. **Given** una espera entre reintentos, **When** la persona abandona la pantalla, **Then** no queda
   ningún reintento corriendo de fondo.
5. **Given** cualquier fallo anotado, **Then** la anotación dice qué lista falló y qué tipo de fallo
   fue, y no incluye el texto buscado ni ningún dato de la persona.

---

### User Story 5 - Salir de una pantalla detiene la red de verdad (Priority: P2)

Alguien abre un documento pesado y se arrepiente: vuelve atrás a los dos segundos. O sale de la
aplicación mientras se refrescan las fuentes, o mientras se sube un documento al servicio de IA, o
mientras espera un resumen o una respuesta. Hoy la aplicación **da la operación por cancelada pero la
petición de red sigue** hasta que el servidor responde o se agota el tiempo de espera, que en un
documento puede ser de tres minutos: consume datos y un hilo por nada, y en el caso de la IA mantiene
ocupada la única petición que se permite a la vez, así que la siguiente acción espera. Cancelar debe
detener la petición de verdad y pronto, sin cambiar quién cancela ni cuándo, y sin que una cancelación se
presente jamás como «no hay conexión» ni provoque reintentos.

**Why this priority**: es la única mejora de las cinco que no corrige un comportamiento visible
incorrecto, sino un coste oculto —datos, batería, un hilo— y una espera evitable en la IA. Va después de
las que sí se ven, pero entra en la feature porque comparte causa y remedio con ellas: hacer real una
cancelación que ya existía.

**Independent Test**: empezar a descargar un documento grande y salir de la pantalla; comprobar que la
transferencia se detiene en el orden de un segundo, que no queda ningún fichero a medias como documento
y que no se muestra ni se anota ningún fallo de red.

**Acceptance Scenarios**:

1. **Given** un documento descargándose, **When** la persona abandona la pantalla, **Then** la
   transferencia se detiene en el orden de un segundo, no quedan restos que puedan confundirse con un
   documento y no se anota ningún fallo de red.
2. **Given** las fuentes refrescándose, **When** la operación se cancela, **Then** la petición en curso
   se detiene, no se reintenta y no se reporta como fallo de red.
3. **Given** un resumen o una respuesta de IA en curso, **When** quien la posee la cancela según las
   reglas ya vigentes —el resumen al abandonar la pantalla, la conversación solo al salir de la
   publicación—, **Then** la petición se detiene pronto y la siguiente acción de IA no espera a la
   anterior.
4. **Given** un documento subiéndose al servicio de IA, **When** se cancela, **Then** la subida se
   detiene.
5. **Given** un fallo genuino de red —tiempo agotado, conexión caída—, **When** ocurre, **Then** se
   sigue reportando como fallo de red y se reintenta según la política de cada operación, exactamente
   como hoy: cancelación y fallo nunca se confunden.

---

### Edge Cases

- **Un fichero lateral con la huella en mayúsculas o con espacios alrededor.** No es una huella válida
  tal cual; se trata como huella perdida, nunca como motivo de cierre.
- **Un fichero lateral huérfano, sin documento.** Ya hoy se ignora: sin documento no hay copia. Sigue
  igual.
- **Una copia cuya huella se perdió sigue siendo una copia válida.** Los bytes se verificaron al
  descargar; lo único que falta es la huella, y con ella la comprobación de vigencia del resumen IA
  puede pedir regenerar. Es el comportamiento de hoy para un lateral ausente y se acepta; tras esta
  feature el caso pasa a ser raro, porque un documento no se hace visible hasta que su verificación está
  escrita completa.
- **Quien espera una descarga que otro canceló.** Se hace cargo de la descarga en vez de morir en
  silencio. Hoy el reintento de la pestaña Documento y el del visor cancelan y relanzan, y esa carrera
  podía dejar la pantalla cargando sin botón de reintento.
- **El proceso muere entre registrar una coincidencia y entregar la notificación.** La notificación del
  sistema se pierde; la novedad y el contador de la campana no, porque están grabados. Se acepta y se
  documenta: es exactamente lo que hoy pasa también.
- **Un aviso editado entre dos ciclos.** Editar renueva desde cuándo está activo, así que la coincidencia
  pendiente del aviso anterior se descarta con razón (FR-028 y FR-040 de la feature 012).
- **Una primera sincronización interrumpida a mitad.** Lo que las fuentes restantes traigan en el
  siguiente ciclo cuenta como novedad, igual que hoy. Sin cambio.
- **El reloj del dispositivo retrocede entre guardar un aviso y sincronizar.** Una publicación
  legítimamente nueva podría no dispararlo. Es una dependencia del reloj que hoy no existe y que se
  acepta a cambio de una regla única; queda documentada.
- **Un fallo de lectura permanente.** Tras los reintentos la pantalla sigue vacía; recrear la pantalla
  vuelve a intentarlo. No se muestra error porque el propietario decidió no distinguirlos.
- **Dos documentos a la vez durante una sincronización.** Hay un tope de peticiones simultáneas al
  mismo servidor; el segundo documento espera su turno unos instantes en vez de fallar.
- **Los tiempos de espera del propio cliente de red.** Siguen siendo fallos de red: no son una
  cancelación de la persona y se reportan como hasta ahora.
- **Una respuesta que no se entiende.** Un error al interpretar lo que devuelve un servidor llega a quien
  hizo la petición igual que hoy y jamás cierra la aplicación.

---

## Requirements *(mandatory)*

### Functional Requirements

#### La copia local del documento (STAB-001)

- **FR-001**: Abrir una publicación cuya copia local exista pero cuyo fichero lateral de verificación
  esté vacío, incompleto o malformado MUST mostrar el documento y MUST NOT cerrar la aplicación.
- **FR-002**: Una copia local que no pueda leerse por cualquier otro motivo inesperado MUST tratarse como
  ausente: el documento se descarga de nuevo y la copia queda reparada.
- **FR-003**: El fichero lateral de verificación MUST escribirse de modo que nunca pueda quedar a medias
  junto a un documento ya visible: o está completo o no está, y un documento MUST NOT hacerse visible
  antes de que su verificación esté escrita.
- **FR-004**: Un lateral perdido o ilegible MUST reportarse como huella desconocida, exactamente igual
  que hoy uno ausente, sin cambiar el comportamiento de la copia.
- **FR-005**: Cada incidente de copia ilegible MUST quedar registrado en el informe de fallos con el tipo
  de fallo y su origen, y MUST NOT incluir título, dirección, identificador ni contenido de la
  publicación.
- **FR-006**: Ningún fallo de la copia local MUST escapar a las pantallas: el detalle, el visor, compartir
  desde cualquier lista y la preparación del documento para la IA reciben siempre un resultado, nunca
  una excepción.

#### El estado del documento (STAB-002)

- **FR-007**: Todo fallo inesperado al descargar o almacenar el documento MUST publicar un estado de
  error visible con reintento, idéntico al de los rechazos ya contemplados por la feature 004.
- **FR-008**: Reintentar tras ese error MUST funcionar cuando la causa haya desaparecido.
- **FR-009**: Un fallo en la limpieza posterior a un error MUST NOT ocultar el error original ni dejar
  esperando a quien aguardaba la misma descarga.
- **FR-010**: Cancelar una descarga MUST NOT presentarse como error ni registrarse como fallo, y el
  documento MUST quedar como «no descargado», nunca como «descargando».
- **FR-011**: Si quien inició una descarga la cancela mientras otra pantalla la espera, la otra MUST
  obtener el documento igualmente: la descarga se retoma para ella.
- **FR-012**: El estado observable del documento MUST ser coherente con el resultado devuelto a quien
  pidió la descarga, en todos los caminos: éxito, rechazo, fallo inesperado y cancelación.

#### Los avisos (STAB-003)

- **FR-013**: Una publicación nueva MUST quedar marcada como pendiente de evaluar en el mismo acto en que
  se almacena, y esa marca MUST sobrevivir a la muerte del proceso.
- **FR-014**: La marca MUST retirarse solo cuando sus coincidencias hayan quedado registradas —o cuando
  no haya ninguna—; si el registro falla, la marca permanece.
- **FR-015**: Un ciclo posterior MUST evaluar lo pendiente aunque las fuentes no hayan cambiado y aunque
  la copia del boletín fuera tan reciente que no se consultara la red.
- **FR-016**: Una coincidencia recuperada MUST entregarse exactamente una vez: nunca dos notificaciones
  ni dos novedades por la misma pareja aviso–publicación (FR-042 de la feature 012 se mantiene).
- **FR-017**: «Nunca retroactivo» MUST cumplirse también en la recuperación: un aviso MUST NOT disparar
  por una publicación almacenada antes de que el aviso estuviera activo, sea por creación, edición o
  reactivación (FR-040 de la feature 012 se mantiene).
- **FR-018**: La primera sincronización correcta de una instalación MUST seguir siendo línea base: MUST
  NOT marcar nada como pendiente ni avisar de nada, y lo que almacenó MUST NOT convertirse en aviso en
  ciclos posteriores (FR-039 de la feature 012 se mantiene).
- **FR-019**: Cuando no haya avisos activos, las publicaciones nuevas MUST darse por evaluadas, para que
  un aviso creado después no dispare por ellas.
- **FR-020**: Si los avisos no pueden leerse, el boletín MUST actualizarse igualmente y la evaluación MUST
  aplazarse al siguiente ciclo sin perder nada.
- **FR-021**: El registro de un lote de coincidencias MUST ser todo o nada: MUST NOT quedar una parte
  grabada y sin entregar.
- **FR-022**: Si las coincidencias quedan registradas pero la marca de evaluado no puede retirarse, la
  entrega MUST producirse igualmente y el ciclo siguiente MUST NOT repetirla.
- **FR-023**: Un fallo al leer las publicaciones pendientes MUST dejar la marca intacta; el ciclo termina
  sin avisos y lo pendiente espera al siguiente.
- **FR-024**: Los registros de diagnóstico del ciclo MUST decir cuántas publicaciones venían pendientes
  de ciclos anteriores y cuándo se aplaza una evaluación y por qué, siempre con recuentos y nunca con
  títulos, palabras clave ni nombres de avisos (FR-070 de la feature 012 se mantiene).
- **FR-025**: La comprobación periódica en segundo plano MUST seguir usando el mismo ciclo que la
  pantalla de Inicio (FR-064 de la feature 012), de modo que la recuperación funcione igual por los dos
  caminos.

#### Las listas observadas (STAB-004)

- **FR-026**: Tras un fallo de lectura del almacén, cada lista observada —el listado de Inicio y su
  cabecera, el detalle de una publicación, Guardados y sus marcas, los resultados y los organismos de
  Buscar, los avisos, las novedades y el contador de la campana— MUST volver a intentarlo sola y, al
  recuperarse, MUST seguir reflejando los cambios posteriores del almacén.
- **FR-027**: Mientras dura el fallo, la lista MUST mostrar vacío, o cero en el contador, como hoy. No se
  pide un estado de error visible.
- **FR-028**: Los reintentos MUST estar acotados: tres, con esperas crecientes que en total no lleguen al
  minuto. Agotados, la observación MUST detenerse sin fallar ni cerrar la aplicación, y el fallo MUST
  quedar registrado.
- **FR-029**: Una lectura correcta MUST reiniciar el presupuesto de reintentos.
- **FR-030**: Abandonar la pantalla durante una espera MUST detener los reintentos; MUST NOT quedar
  trabajo corriendo de fondo.
- **FR-031**: El registro MUST decir qué lista falló y el tipo de fallo, y MUST NOT incluir el texto
  buscado ni ningún dato de la persona.
- **FR-032**: La recuperación MUST aplicarse de una única forma a todas las listas observadas, para que
  ninguna pueda quedarse fuera por descuido.

#### La cancelación de red (PERF-002)

- **FR-033**: Cancelar una operación en curso —descarga de documento, lectura de fuentes, subida de
  documento al servicio de IA, su sondeo y su borrado, resumen y respuesta— MUST interrumpir la petición
  de red correspondiente en el orden de un segundo, no del tiempo de espera de la petición.
- **FR-034**: Una cancelación MUST NOT presentarse como fallo de red, MUST NOT contarse como error y MUST
  NOT provocar reintentos.
- **FR-035**: Un fallo genuino de red —tiempo agotado, conexión caída— MUST seguir reportándose y
  reintentándose exactamente como hoy.
- **FR-036**: La cancelación MUST cubrir tanto la espera de la respuesta como la lectura de su contenido:
  una descarga cancelada MUST NOT seguir escribiendo en el dispositivo.
- **FR-037**: Quién cancela y cuándo MUST NOT cambiar: el detalle y el visor al abandonar la pantalla
  (FR-023 de la feature 004), el resumen al abandonar la pantalla (FR-006 de la feature 007) y la
  conversación solo al salir de la publicación (feature 011).
- **FR-038**: Un error inesperado al procesar una respuesta MUST llegar a quien hizo la petición como
  hasta ahora y MUST NOT cerrar la aplicación.
- **FR-039**: Los restos de una descarga cancelada MUST limpiarse y MUST NOT poder confundirse con un
  documento (FR-019 de la feature 004 se mantiene).

#### Transversales

- **FR-040**: Cada corrección MUST ir acompañada de al menos una prueba automática que reproduzca el
  fallo tal como lo describe la auditoría y que falle antes del arreglo.
- **FR-041**: Ninguna pantalla MUST cambiar de aspecto, textos ni navegación.
- **FR-042**: Actualizar desde cualquier versión anterior MUST conservar el boletín almacenado, los
  guardados, los resúmenes y los avisos con sus novedades; ningún dato de la persona MUST perderse ni
  cambiar de sitio.
- **FR-043**: Ningún registro nuevo MUST contener títulos, palabras clave, nombres de avisos, texto de
  documentos, direcciones ni credenciales.
- **FR-044**: Los hallazgos SEC-001, SEC-002, SEC-003 y PERF-001 MUST quedar fuera de esta feature y MUST
  NOT tocarse ni de paso.

### Key Entities *(include if feature involves data)*

- **Copia local del documento**: el fichero del documento oficial guardado en el dispositivo y su
  verificación lateral. Estados observables: no descargado, descargando, disponible, con error. Tras
  esta feature, «descargando» significa siempre que hay una descarga en vuelo.
- **Publicación pendiente de evaluar**: una publicación almacenada por una sincronización que aún no ha
  pasado por los avisos, con el instante en que se almacenó. La marca nace con la publicación y muere
  al evaluarla; nunca se pone a una publicación que ya existía ni a las de la línea base.
- **Coincidencia**: la relación entre un aviso y una publicación en un instante. Única por pareja;
  registrada a lo sumo una vez; entregada solo cuando el registro la dio por nueva.
- **Lista observada**: una vista viva sobre el almacén que la pantalla mira. Gana un presupuesto de
  recuperación: tres reintentos que un éxito repone.
- **Operación de red cancelable**: una petición cuya cancelación llega al transporte y libera la
  conexión y el hilo, en lugar de dejarlos hasta la respuesta.

---

## Requisitos de features anteriores que quedan afectados

Se dejan escritos para que no se lean como incumplidos ni como olvidados:

- **Feature 004, el contrato del documento** («aquí nada lanza; el estado observable termina en error,
  no en excepción»): **se hace verdad**. FR-001 a FR-012 de esta feature cierran los dos caminos por
  los que no se cumplía. FR-019, FR-021, FR-022 y FR-023 de la 004 **se mantienen íntegros**.
- **Feature 012, FR-038** («evaluar únicamente las publicaciones realmente nuevas»): **se amplía**. Lo
  nuevo sigue siendo lo que se evalúa; lo que cambia es que «nuevo» deja de recordarse solo en memoria y
  pasa a recordarse en el almacén, para que sobreviva a un fallo o a la muerte del proceso (FR-013 a
  FR-015).
- **Feature 012, la decisión D-405** («nunca retroactivo se cumple por el orden del ciclo, sin comparar
  fechas»): **queda superada, no incumplida**. El orden sigue garantizándolo para lo nuevo del ciclo; la
  recuperación de lo pendiente exige además comparar desde cuándo está activo el aviso con cuándo se
  almacenó la publicación (FR-017). Se anota en la investigación de esta feature.
- **Feature 012, FR-039, FR-040, FR-042, FR-064 y FR-070**: **se mantienen** y esta feature los
  reafirma uno a uno (FR-016, FR-017, FR-018, FR-024, FR-025).
- **Features 003, 005, 006 y 012, la promesa «un fallo de lectura emite vacío y la lista sigue viva»**:
  **se hace verdad**. La primera mitad ya se cumplía; la segunda es FR-026 a FR-032.
- **Feature 007, FR-006** («abandonar la pantalla detiene la generación») y **feature 009/010, la lección
  de que cancelar una operación no interrumpe por sí solo la petición de red en curso**: **se mantienen y se completan**.
  La lección decía cómo *clasificar* una cancelación; esta feature hace que además *detenga* la petición
  (FR-033 a FR-039).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una publicación cuya copia local tiene el fichero lateral dañado se abre a la primera y sin
  cierre de la aplicación en el cien por cien de los intentos, y la copia queda reparada tras el
  primero.
- **SC-002**: Un fallo inesperado al almacenar un documento muestra el error con reintento en menos de
  un segundo desde que ocurre, y el reintento con la causa resuelta muestra el documento.
- **SC-003**: Una coincidencia cuyo registro falló se entrega en el siguiente ciclo, y el número total
  de notificaciones, novedades e incrementos de la campana por esa pareja aviso–publicación es
  exactamente uno, medido a lo largo de tres ciclos consecutivos.
- **SC-004**: Ningún aviso creado, editado o reactivado después de almacenarse una publicación dispara
  por ella, ni en el ciclo normal ni en el de recuperación.
- **SC-005**: Tras un fallo transitorio de lectura, toda lista observada vuelve a mostrar sus datos en
  menos de dos segundos sin intervención de la persona, y refleja el siguiente cambio del almacén.
- **SC-006**: Ante un fallo de lectura permanente, la aplicación no realiza más de cuatro lecturas de
  esa lista por suscripción y no muestra ningún cierre ni bloqueo.
- **SC-007**: Cancelar una descarga o una petición en curso libera la conexión en menos de dos
  segundos, frente a los hasta ciento ochenta segundos actuales, y no genera ningún mensaje de «no hay
  conexión» ni ningún reintento.
- **SC-008**: Las seis reproducciones de la auditoría que hoy demuestran el defecto —excepción escapada,
  estado «descargando» tras fallo de almacenamiento, cero entregas con un intento de registro, y llamada
  no cancelada— dejan de reproducirse, y cada una queda convertida en una prueba automática del
  proyecto.
- **SC-009**: Actualizar una instalación con boletín, guardados, resúmenes y avisos conserva el cien por
  cien de esos datos.
- **SC-010**: Las cuatro puertas de calidad del proyecto quedan en verde, ninguna prueba se desactiva y
  el recuento de pruebas automáticas aumenta.

---

## Fuera de alcance

- Los hallazgos de seguridad y privacidad de la auditoría: SEC-001 (la credencial del servicio de IA
  dentro del cliente), SEC-002 (regenerar un resumen sin volver a aceptar la versión vigente del aviso de
  IA) y SEC-003 (la comprobación del servidor de descarga admite direcciones hacia otros dominios). Por
  decisión del propietario, quedan para otra feature.
- PERF-001: que todo documento visitado quede protegido de la expulsión de la caché durante todo el
  proceso.
- Distinguir en pantalla «vacío» de «no se ha podido leer». Decidido por el propietario.
- Cambiar quién cancela una operación y cuándo: que una descarga sobreviva a la pantalla que la pidió,
  o que un resumen siga generándose tras salir. Se hace real la cancelación que ya existía.
- Cualquier cambio visible en las pantallas, los textos o la navegación.
- Reintentar indefinidamente una lectura que falla siempre.

---

## Assumptions

- **El lateral de verificación dañado se trata como perdido, no como documento inválido.** Los bytes del
  documento se verificaron al descargar y el fichero solo se hace visible completo; lo que se perdió es
  la huella, y así se reporta.
- **Tres reintentos con esperas de un segundo, cinco y treinta** son el presupuesto de recuperación de
  una lista. Cubren un almacén ocupado o un fallo de entrada/salida pasajero sin insistir sobre una
  corrupción permanente; pueden afinarse en planificación sin que cambie ningún requisito.
- **La marca de pendiente vive con la publicación** y se retira exactamente para las publicaciones que
  se leyeron en ese ciclo, nunca en bloque, para que dos ciclos simultáneos —el de Inicio y el de segundo
  plano— no se pisen.
- **«Desde cuándo está activo un aviso» y «cuándo se almacenó una publicación» se miden con el mismo
  reloj del dispositivo**, el que ya usan las dos. La comparación es «activo en o antes de almacenarse».
- **Un ciclo con la copia fresca del boletín sí evalúa lo pendiente.** Es el caso más frecuente —abrir
  Inicio dentro de la media hora— y el camino natural de recuperación tras la muerte del proceso.
- **La entrega de una coincidencia recuperada usa el mismo canal que una normal**: notificación del
  sistema con la aplicación en segundo plano, aviso interno con la aplicación en pantalla. No hay canal
  especial para «recuperada».
- **Quien espera una descarga que otro canceló la retoma.** Cuesta volver a descargar los bytes
  parciales, algo rarísimo —hacen falta dos pantallas esperando el mismo documento y que la primera se
  vaya—, a cambio de que ninguna pantalla se quede cargando sin salida.
- **Un tope de peticiones simultáneas al mismo servidor es aceptable.** Con cuatro fuentes leyéndose a
  la vez y un documento, se llega al límite; un segundo documento espera unos instantes. No se cambia el
  límite en esta feature.
- **Los diagnósticos de la auditoría siguen en el disco del propietario aunque no se versionen**, así
  que pueden volver a ejecutarse tras la implementación para confirmar que dejan de reproducir los
  defectos.
