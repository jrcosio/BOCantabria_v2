# Feature Specification: Avisos

**Feature Branch**: `012-avisos`

**Created**: 6 de septiembre de 2026

**Status**: Draft

**Input**: User description: "Nueva feature, sistema de avisos y notificaciones. Vamos a crear en la
barra de navegación una cuarta opción «Avisos» con una campanita; esa opción abre una nueva sección en
la cual el usuario podrá ver y configurar avisos de posibles publicaciones que él mismo haya
configurado. Y la otra pantalla que hay que hacer es la de crear el aviso." Acompañan la petición
tres mockups orientativos (`Datos_modelo/screen_1_avisos.png`, `screen_2_avisos.png`,
`screen_config_avisos.png`) y dos documentos funcionales que son la fuente de verdad del
comportamiento: `Datos_modelo/ESPECIFICACION_FUNCIONAL_AVISOS_BOC.md` y
`Datos_modelo/EXPERIENCIA_NOTIFICACIONES_AVISOS_BOC.md`.

La ruta técnica —cómo se detecta qué publicación es nueva, dónde se guardan las reglas, cómo se
programa la comprobación periódica, cómo se llega al detalle desde una notificación— quedó cerrada
con el propietario antes de escribir esto y **se documenta en `plan.md`, no aquí**. Esta
especificación describe qué ve y qué obtiene quien usa la aplicación, y qué garantías hay que cumplir.

Decisiones cerradas que por tanto no se vuelven a plantear: la pantalla tiene **dos pestañas**,
«Novedades» y «Mis avisos»; la **comprobación periódica en segundo plano** entra en esta feature;
**eliminar un aviso lo borra de verdad**, con sus coincidencias, y es la primera vez que esta
aplicación borra algo; la **vista previa** de coincidencias en el formulario entra con prioridad baja;
y los mockups son **orientativos**: manda el estilo de la aplicación.

---

## Lo que hay que saber antes de leer nada más *(contexto imprescindible)*

- **Avisos estaba aplazado a propósito, no olvidado.** La feature 003 lo dejó fuera por decisión del
  propietario, el documento de diseño redujo la barra inferior a tres destinos y anotó que «cuando
  existan las notificaciones se recuperará como cuarto destino». Tres sitios del código dicen hoy «sin
  campana: un icono que no hace nada es peor que ninguno». Esta feature recupera el cuarto destino y
  retira esas notas.
- **Un aviso es una regla, no una notificación.** Se llama «Avisos» y no «Notificaciones» porque lo
  que la persona crea es una regla —palabras, secciones, organismo— y la notificación de Android es solo
  una de las formas en que esa regla se hace notar. La otra es la propia aplicación: una pestaña de
  novedades, un contador en la barra y un mensaje breve cuando la aplicación está abierta.
- **Solo lo nuevo avisa.** Cada fuente del boletín repite hasta cien anuncios ya conocidos en cada
  descarga. La aplicación ya distingue lo que acaba de aparecer de lo que ya tenía; los avisos se
  evalúan **únicamente** contra lo que acaba de aparecer. Y la primera sincronización de una
  instalación es la línea base: guarda todo y no avisa de nada, porque avisar de mil novecientas
  publicaciones el primer día no es avisar.
- **Se busca en lo que trae el RSS, no en el documento.** Título, organismo, categorías y nombre de la
  sección. El PDF no se descarga para decidir si una regla coincide: costaría red, batería y tiempo
  por cada anuncio nuevo, y la interfaz lo dice con todas las letras: «Busca en el título, organismo y
  categorías del RSS».
- **La comprobación periódica no promete tiempo real.** Android decide cuándo corre el trabajo en
  segundo plano y puede retrasarlo. La aplicación habla de «comprobación periódica» y nunca de
  «al instante».
- **Las palabras de una regla son un dato personal.** Dicen qué le importa a quien usa la aplicación.
  No salen a analítica, ni a informes de fallos, ni al registro del dispositivo. Solo recuentos.
- **La misma coincidencia se entrega por un solo canal.** Si la aplicación está en pantalla, mensaje
  dentro de la aplicación; si no, notificación de Android. Nunca las dos por lo mismo. Y una
  notificación descartada no borra la novedad: sigue en la pestaña hasta que se abra o se marque leída.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear un aviso y enterarse de una publicación nueva (Priority: P1)

Alguien que trabaja en el campo quiere saber cuándo el BOC publica algo sobre ganadería. Pulsa la
campana, «Crear aviso», escribe «ganadería», guarda. Días después, con la aplicación cerrada, aparece
una convocatoria de ayudas a explotaciones ganaderas. Su móvil muestra «Nueva publicación: Ganadería»
con el título del anuncio. Toca la notificación y aterriza directamente en el detalle de esa
publicación, con el documento oficial a un toque.

**Why this priority**: Es la feature. Sin esto no hay nada; con esto solo, ya vale la pena.

**Independent Test**: Crear una regla de una palabra, provocar una sincronización que traiga una
publicación nueva cuyo título la contenga, y comprobar que llega una notificación y que tocarla abre el
detalle correcto.

**Acceptance Scenarios**:

1. **Given** la barra inferior de la aplicación, **When** se mira, **Then** hay un cuarto destino
   «Avisos» con icono de campana, y tocarlo abre la pantalla Avisos.
2. **Given** ningún aviso creado, **When** se entra en «Mis avisos», **Then** se lee «Aún no tienes
   avisos» y se ofrece «Crear mi primer aviso».
3. **Given** el formulario de crear aviso, **When** se escribe una palabra y se guarda, **Then** el
   aviso aparece activo en la lista y la cabecera dice «1 activo».
4. **Given** un aviso activo con la palabra «ganadería» y la aplicación cerrada, **When** una
   sincronización trae una publicación nueva cuyo título contiene «GANADERIA», **Then** se muestra una
   notificación con título «Nueva publicación: Ganadería» y el título del anuncio como cuerpo.
5. **Given** esa notificación, **When** se toca, **Then** se abre la aplicación directamente en el
   detalle de esa publicación, la notificación desaparece y la novedad queda marcada como leída.
6. **Given** el aviso guardado, **When** una sincronización trae solo publicaciones ya conocidas,
   **Then** no se muestra nada.

---

### User Story 2 - Nunca retroactivo, nunca dos veces (Priority: P1)

Alguien instala la aplicación, crea tres avisos amplios y espera una avalancha que no llega: la primera
sincronización solo establece qué había. Más tarde edita un aviso para añadir una palabra que
coincide con cien anuncios antiguos: tampoco recibe nada por ellos. Pausa un aviso durante un mes y lo
reactiva: no recibe lo publicado durante la pausa. Y cuando una publicación cumple dos avisos a la vez,
recibe **una** notificación que nombra a los dos.

**Why this priority**: Es lo que separa una herramienta útil de un móvil que no para de sonar. El
documento funcional lo marca como obligatorio, y una vez perdida la confianza en los avisos, nadie
vuelve a activarlos.

**Independent Test**: Con dobles de la sincronización, recorrer los casos —primera sincronización,
edición, reactivación, misma publicación dos veces, dos reglas— y contar notificaciones.

**Acceptance Scenarios**:

1. **Given** una instalación limpia con avisos ya creados, **When** termina la primera sincronización
   correcta, **Then** no se muestra ninguna notificación ni aparece ninguna novedad, aunque cientos de
   publicaciones coincidan.
2. **Given** un aviso existente, **When** se editan sus palabras o secciones y se guarda, **Then** las
   publicaciones ya almacenadas que ahora coincidirían **no** generan novedades ni notificaciones.
3. **Given** un aviso pausado durante el que aparecieron publicaciones coincidentes, **When** se
   reactiva, **Then** esas publicaciones no generan novedades; solo lo que aparezca a partir de ahora.
4. **Given** una publicación que ya generó una novedad, **When** vuelve a llegar en otra sincronización
   —por la misma fuente o por otra—, **Then** no se vuelve a avisar.
5. **Given** dos avisos activos, «Ganadería» y «Subvenciones rurales», **When** una publicación nueva
   cumple los dos, **Then** se muestra **una sola** notificación, «Nueva publicación del BOC», cuyo
   cuerpo dice el título y «Coincide con Ganadería y Subvenciones rurales», y en Novedades aparece
   **una** entrada que nombra a los dos.
6. **Given** una sincronización que falla, **When** termina, **Then** no se evalúa nada, no se avisa de
   nada y la fecha de última coincidencia de los avisos no cambia.
7. **Given** una sincronización cuyas fuentes vienen vacías, **When** termina, **Then** no es un error y
   no genera avisos.

---

### User Story 3 - Las novedades viven en la aplicación, no solo en la notificación (Priority: P1)

Alguien descarta por error la notificación desde el panel de Android. No pasa nada: la campana de la
barra inferior muestra un «1», y en la pestaña «Novedades» está la publicación, con un punto azul, el
aviso que la trajo, su sección y cuándo se detectó. La abre desde ahí, y el contador baja. Otro día,
mientras busca algo en la pestaña Buscar, la aplicación sincroniza y aparece abajo un mensaje breve:
«Una nueva publicación coincide con «Ganadería»  VER». Toca VER y está en Novedades.

**Why this priority**: Es la mitad de la experiencia que el documento de notificaciones define, y es lo
que garantiza que no se pierda nada aunque se descarte la notificación o se tenga el permiso apagado.

**Independent Test**: Con una novedad registrada, comprobar el contador en la barra, la entrada en la
pestaña con su estado no leído, que abrirla la marca leída y baja el contador, y que una sincronización
con la aplicación en pantalla muestra el mensaje con VER y no una notificación.

**Acceptance Scenarios**:

1. **Given** dos publicaciones con novedad sin leer, una de ellas coincidente con dos avisos, **When**
   se mira la barra inferior, **Then** la campana muestra «2», no «3».
2. **Given** diez o más publicaciones sin leer, **When** se mira la barra, **Then** la campana muestra
   «9+»; **Given** cero, **Then** no muestra número alguno.
3. **Given** la pestaña Novedades con entradas, **When** se mira, **Then** cada entrada muestra si está
   leída o no —punto azul y fondo azulado si no—, el título, el aviso o avisos coincidentes, la sección
   y el momento de detección, agrupadas por día con separadores «Hoy», «Ayer» y la fecha.
4. **Given** una novedad sin leer, **When** se toca, **Then** se abre el detalle de la publicación, la
   novedad pasa a leída y el contador de la campana baja en uno.
5. **Given** varias novedades sin leer, **When** se pulsa «Marcar todas como leídas», **Then** todas
   pasan a leídas y el contador desaparece.
6. **Given** novedades sin leer, **When** simplemente se entra en la pestaña Novedades y se sale,
   **Then** siguen sin leer.
7. **Given** la aplicación en pantalla en cualquier destino que no sea Avisos, **When** una
   sincronización produce una coincidencia, **Then** aparece un mensaje breve no bloqueante con la
   acción «VER» y **no** una notificación de Android; VER lleva a Novedades.
8. **Given** la aplicación en pantalla dentro de Avisos, **When** una sincronización produce una
   coincidencia, **Then** la lista y el contador se actualizan sin mensaje ni notificación.
9. **Given** una notificación descartada desde el panel de Android, **When** se abre la aplicación,
   **Then** la novedad sigue sin leer en la pestaña y en el contador.
10. **Given** sin novedades, **When** se entra en la pestaña, **Then** se lee «No tienes avisos nuevos».

---

### User Story 4 - Gestionar mis avisos (Priority: P2)

Alguien tiene tres avisos. Uno lo pausa desde la propia tarjeta sin entrar a editarlo; otro lo duplica
para hacer una variante más específica; el tercero lo elimina, y la aplicación le pide confirmación
antes, diciéndole qué dejará de recibir.

**Why this priority**: Sin gestión los avisos se acumulan y se apagan en Android. Es lo que mantiene la
funcionalidad viva pasado el primer día.

**Independent Test**: Con varios avisos creados, pausar desde la tarjeta, editar, duplicar y eliminar,
y comprobar el estado resultante de la lista.

**Acceptance Scenarios**:

1. **Given** la pestaña Mis avisos con avisos, **When** se mira, **Then** arriba está la tarjeta «Sigue
   lo que te importa / Recibe un aviso cuando una nueva publicación coincida con tus intereses» con el
   botón «+ Crear aviso», la cabecera «Mis avisos» con «N activos», y una tarjeta por aviso.
2. **Given** una tarjeta de aviso, **When** se mira, **Then** muestra el nombre, el interruptor
   activo/pausado, sus palabras, la sección o «Todas las secciones», y la última coincidencia —«1
   coincidencia hoy», «Última coincidencia: ayer», o «Aviso pausado» si está pausado—.
3. **Given** un aviso activo, **When** se apaga el interruptor de su tarjeta, **Then** pasa a pausado
   sin abrir la edición, conserva su configuración y sus novedades, y el contador de activos baja.
4. **Given** el menú de tres puntos de una tarjeta, **When** se abre, **Then** ofrece «Editar»,
   «Duplicar» y «Eliminar».
5. **Given** «Editar», **When** se cambia algo y se pulsa «Guardar cambios», **Then** la tarjeta refleja
   el cambio.
6. **Given** «Duplicar», **When** se elige, **Then** se abre el formulario con una copia **pausada**
   llamada «Copia de <nombre>», lista para revisar antes de guardar, y sin las coincidencias del original.
7. **Given** «Eliminar», **When** se elige, **Then** aparece un diálogo «¿Eliminar este aviso? Dejarás
   de recibir novedades que coincidan con «<nombre>»» con «Cancelar» y «Eliminar»; solo al confirmar
   desaparecen el aviso y sus novedades, y las publicaciones del boletín no se tocan.

---

### User Story 5 - El formulario guía, no exige saber cómo funciona el BOC (Priority: P2)

Alguien quiere que le avisen de cualquier oposición. No sabe qué es un feed ni un identificador: elige
«2.2 · Cursos, Oposiciones y Concursos» de una lista con las secciones tal como las conoce, no escribe
ninguna palabra, y el formulario le dice en castellano: «Te avisaremos de cualquier publicación nueva de
Cursos, Oposiciones y Concursos». Otra persona escribe «ganadería» y «subvención», elige «Cualquiera de
las palabras» y la sección 6, y lee: «Te avisaremos cuando una publicación nueva de Subvenciones y
Ayudas incluya «ganadería» o «subvención»».

**Why this priority**: La complejidad de diecinueve fuentes y una jerarquía de secciones tiene que
quedar detrás de la interfaz. Si el formulario no explica lo que va a pasar, la gente crea reglas que no
hacen lo que cree.

**Independent Test**: Recorrer el formulario con distintas combinaciones y comprobar la validación, el
resumen en lenguaje natural y el estado del botón de guardar.

**Acceptance Scenarios**:

1. **Given** el formulario vacío, **When** se mira, **Then** arriba se lee «Te avisaremos al encontrar
   una publicación nueva que cumpla estas condiciones», y los bloques van en este orden: nombre,
   palabras clave, coincidencia, secciones, organismo, interruptor de aviso, «Así funcionará» y las
   acciones Cancelar / «Guardar aviso».
2. **Given** el formulario vacío, **When** no hay palabra, ni sección, ni organismo, **Then** «Guardar
   aviso» está deshabilitado; en cuanto hay al menos un criterio y un nombre, se habilita.
3. **Given** el campo de palabras, **When** se escribe «medio rural» y se pulsa Intro o «+», **Then**
   aparece como un chip con una cruz para quitarlo; escribir «Medio Rural» después no añade un
   duplicado; un término de una letra o de más de sesenta se rechaza; el décimo primero se rechaza.
4. **Given** el bloque de coincidencia, **When** se mira sin tocar, **Then** «Cualquiera de las
   palabras» está elegida; se puede cambiar a «Todas las palabras».
5. **Given** el selector de secciones, **When** se abre, **Then** se titula «Seleccionar secciones»,
   ofrece «Todas las secciones», lista las nueve secciones con sus subsecciones debajo, permite marcar
   varias, muestra cuántas hay marcadas y se cierra con «Aplicar».
6. **Given** el selector, **When** se marca «2 · Autoridades y Personal», **Then** quedan marcadas 2.1,
   2.2 y 2.3; **When** se desmarca 2.3, **Then** la sección 2 pasa a estado parcial.
7. **Given** una selección de todas las hijas de una sección, **When** se cierra el selector, **Then** el
   formulario resume «Autoridades y Personal (todas)»; sin ninguna sección, «Todas las secciones».
8. **Given** el campo de organismo, **When** se empieza a escribir «Piél», **Then** se sugieren los
   organismos ya almacenados que contengan ese texto, y se puede dejar el campo vacío, que significa
   «Cualquier organismo».
9. **Given** un nombre aún vacío, **When** se añade el primer criterio, **Then** el nombre se rellena con
   una propuesta a partir de ese criterio, que se puede cambiar.
10. **Given** cualquier combinación, **When** cambia el formulario, **Then** «Así funcionará» se
    actualiza al momento, en castellano corriente, sin palabras técnicas.
11. **Given** el formulario, **When** se pulsa Cancelar o Atrás, **Then** se vuelve a Avisos sin guardar.

---

### User Story 6 - El permiso se pide cuando tiene sentido, y su ausencia no rompe nada (Priority: P2)

Alguien guarda su primer aviso. Justo entonces —no al arrancar la aplicación— lee «Activa las
notificaciones. Permite que BOC Cantabria te avise cuando aparezcan publicaciones relacionadas con tus
intereses» con «Ahora no» y «Continuar». Continúa, Android le pregunta y acepta. Otra persona dice
«Ahora no»: su aviso se guarda igual, y en la pantalla Avisos aparece un aviso persistente con «Abrir
ajustes». Sus novedades se siguen registrando y el contador sigue subiendo, solo que sin notificaciones.

**Why this priority**: Sin permiso no hay notificaciones, pero pedirlo sin contexto lo niega casi todo el
mundo. Y negarlo no puede dejar la funcionalidad inútil.

**Independent Test**: Guardar el primer aviso y comprobar el diálogo; rechazar y comprobar el banner;
con el permiso rechazado, provocar una coincidencia y comprobar que la novedad y el contador aparecen.

**Acceptance Scenarios**:

1. **Given** una instalación que nunca pidió el permiso, **When** se arranca la aplicación, **Then** no se
   pide ningún permiso.
2. **Given** ningún aviso previo y el permiso pendiente, **When** se guarda el primer aviso activo,
   **Then** aparece el diálogo explicativo con «Ahora no» y «Continuar»; «Continuar» lanza la petición
   de Android.
3. **Given** el diálogo, **When** se pulsa «Ahora no» o se rechaza en Android, **Then** el aviso queda
   guardado y activo, y no se vuelve a pedir en cada apertura.
4. **Given** avisos activos y las notificaciones desactivadas en Android, **When** se entra en Avisos,
   **Then** se muestra un aviso persistente y no bloqueante —«Tus avisos están configurados, pero
   Android no permite mostrar notificaciones»— con «Abrir ajustes», que lleva a los ajustes de
   notificaciones de la aplicación.
5. **Given** las notificaciones desactivadas, **When** una sincronización produce coincidencias,
   **Then** se registran como novedades, el contador sube y la pestaña las muestra; no se intenta
   mostrar ninguna notificación.
6. **Given** el icono de ajustes de la barra superior de Avisos, **When** se toca, **Then** se ve el
   estado del permiso de notificaciones, un acceso a los ajustes de Android de la aplicación y la fecha y
   hora de la última comprobación; **no** se ofrece elegir una frecuencia exacta.

---

### User Story 7 - La aplicación comprueba aunque nadie la abra (Priority: P2)

Alguien crea un aviso y se olvida de la aplicación. Días después, sin haberla abierto, recibe la
notificación de una publicación nueva. La aplicación comprobó el boletín por su cuenta, con red, y a la
hora que Android le dejó.

**Why this priority**: Sin comprobación periódica, la notificación solo saldría cuando alguien abre la
aplicación, que es justo cuando menos falta hace. El documento funcional la exige si no existe ya.

**Independent Test**: Con un aviso activo y la aplicación cerrada, forzar la comprobación periódica y
comprobar que evalúa y notifica igual que una sincronización manual.

**Acceptance Scenarios**:

1. **Given** al menos un aviso activo, **When** pasa el intervalo y hay red, **Then** la aplicación
   sincroniza en segundo plano y evalúa los avisos con las mismas reglas que una sincronización manual.
2. **Given** ningún aviso activo, **When** pasa el tiempo, **Then** no se ejecuta ninguna comprobación
   periódica.
3. **Given** que no hay red, **When** toca comprobar, **Then** la comprobación espera a que la haya.
4. **Given** cualquier texto de la interfaz sobre la comprobación, **When** se lee, **Then** habla de
   «comprobación periódica» y nunca promete tiempo real ni una hora exacta.
5. **Given** la comprobación periódica corriendo mientras la aplicación está en pantalla, **When**
   encuentra coincidencias, **Then** se comportan como en la historia 3: mensaje con VER, sin
   notificación.

---

### User Story 8 - Saber antes de guardar si la regla hace algo (Priority: P3)

Alguien duda si «Cosío» encontrará algo. Mientras rellena el formulario, lee «3 publicaciones actuales
coinciden con esta configuración» y pulsa «Ver resultados» para hojearlas. Nada de eso le llega como
notificación ni aparece en Novedades: es solo una comprobación.

**Why this priority**: Es una ayuda para confiar en la regla, no la regla. El documento la marca como
recomendable, y el propietario la quiso con prioridad baja.

**Independent Test**: Rellenar el formulario con una configuración que coincida con publicaciones ya
almacenadas y comprobar el recuento y la lista, y que ni el contador ni las novedades cambian.

**Acceptance Scenarios**:

1. **Given** un formulario válido, **When** su configuración coincide con publicaciones ya almacenadas,
   **Then** se lee «N publicaciones actuales coinciden con esta configuración» con «Ver resultados».
2. **Given** «Ver resultados», **When** se toca, **Then** se ven esas publicaciones y se puede abrir
   cualquiera.
3. **Given** la vista previa mostrada, **When** se mira el contador de la campana y la pestaña Novedades,
   **Then** no han cambiado, y la fecha de última coincidencia del aviso tampoco.
4. **Given** una configuración que no coincide con nada almacenado, **When** se mira, **Then** se lee que
   no hay publicaciones actuales que coincidan, y se puede guardar igualmente.

---

### Edge Cases

- **Publicación sin categorías en el RSS**: puede coincidir igual por título, sección u organismo.
- **Categorías en orden anómalo** (la fuente 4.3 las trae permutadas): no afecta a la regla.
- **Regla sin palabras**: válida si tiene sección u organismo.
- **Palabra que es una frase** («medio rural»): debe aparecer consecutiva en el texto.
- **Coincidencia parcial** («subvención» frente a «subvenciones», «Cosío» frente a «COSIO»): coincide.
- **Publicación repetida en dos fuentes**: la deduplicación de la aplicación manda; una novedad.
- **Publicación que colisiona con una ya conocida por su identificador**: no es nueva, no avisa.
- **Instalada sin conexión y primera sincronización correcta días después**: sigue siendo la línea base.
- **Largo tiempo sin sincronizar**: pueden quedar publicaciones fuera de la ventana de cien anuncios; la
  interfaz no promete histórico completo.
- **Aplicación detenida a la fuerza por la persona**: Android puede impedir la comprobación hasta que
  vuelva a abrirse; no se intenta rodear.
- **Permiso rechazado dos veces**: Android deja de mostrar el diálogo; el banner con «Abrir ajustes»
  sigue siendo el camino.
- **Notificación tocada con la aplicación bloqueada por versión mínima o mantenimiento**: se muestra la
  portada con el bloqueo y no se navega al detalle.
- **Notificación tocada mientras se lee otra publicación**: se abre la publicación de la notificación.
- **Muerte del proceso con el formulario a medias**: se acepta perder el borrador; el formulario no debe
  quedar en un estado roto.
- **Nombre con espacios al principio o al final**: se recortan.
- **Sección padre guardada por error en una regla antigua**: la regla sigue coincidiendo con sus hijas.
- **Coincidencias con la aplicación en pantalla pero con el detalle de otra publicación abierto**: el
  mensaje con VER se muestra al volver a la barra inferior, no se pierde.

## Requirements *(mandatory)*

### Functional Requirements

**Navegación y pantalla Avisos**

- **FR-001**: La aplicación MUST ofrecer un cuarto destino «Avisos» en la barra inferior, con icono de
  campana, en cuarta posición tras Inicio, Buscar y Guardados.
- **FR-002**: El destino Avisos MUST mostrar un contador con el número de publicaciones con novedad sin
  leer; MUST mostrar «9+» por encima de nueve y MUST ocultarlo cuando sea cero.
- **FR-003**: El contador MUST contar publicaciones, no coincidencias: una publicación que cumple dos
  avisos cuenta una vez.
- **FR-004**: La pantalla Avisos MUST tener una barra superior con el escudo, el título «Avisos» y un
  icono de ajustes que abra la información del permiso, el acceso a los ajustes de Android de la
  aplicación y la fecha y hora de la última comprobación.
- **FR-005**: La pantalla Avisos MUST organizarse en dos pestañas, «Novedades» —con su número de no
  leídas— y «Mis avisos».
- **FR-006**: La aplicación MUST recordar la pestaña elegida al volver a Avisos dentro de la misma
  visita, y MUST volver a un estado válido si el nombre guardado ya no existe.

**Mis avisos**

- **FR-007**: La pestaña Mis avisos MUST mostrar la tarjeta introductoria «Sigue lo que te importa /
  Recibe un aviso cuando una nueva publicación coincida con tus intereses» con el botón «+ Crear aviso».
- **FR-008**: La pestaña MUST mostrar la cabecera «Mis avisos» con el número de avisos activos, calculado
  en cada momento.
- **FR-009**: Cada tarjeta de aviso MUST mostrar nombre, interruptor activo/pausado, palabras, sección o
  «Todas las secciones», y última coincidencia —«N coincidencias hoy» si las hubo hoy, «Última
  coincidencia: <cuándo>» en otro caso, «Aviso pausado» si está pausado— y un menú con Editar,
  Duplicar y Eliminar.
- **FR-010**: Quien usa la aplicación MUST poder pausar y reactivar un aviso desde el interruptor de su
  tarjeta, sin entrar a editarlo; pausar MUST conservar la configuración y las novedades.
- **FR-011**: Duplicar MUST crear una copia pausada con el nombre «Copia de <nombre>», MUST abrir el
  formulario para revisarla antes de guardar y MUST NOT copiar las coincidencias del original.
- **FR-012**: Eliminar MUST pedir confirmación con un diálogo que nombre el aviso; al confirmar MUST
  borrar el aviso y sus coincidencias, y MUST NOT tocar ninguna publicación del boletín.
- **FR-013**: Sin avisos, la pestaña MUST mostrar «Aún no tienes avisos» con la explicación y el botón
  «Crear mi primer aviso».
- **FR-014**: Con avisos activos y las notificaciones desactivadas en Android, la pantalla MUST mostrar
  un aviso persistente y no bloqueante con la acción «Abrir ajustes»; MUST NOT eliminar ni pausar los
  avisos por ello.

**Crear y editar un aviso**

- **FR-015**: Crear y editar MUST compartir la misma pantalla, fuera de la barra inferior, con barra
  superior y Atrás; el botón principal dice «Guardar aviso» al crear y «Guardar cambios» al editar.
- **FR-016**: El formulario MUST mostrar arriba «Te avisaremos al encontrar una publicación nueva que
  cumpla estas condiciones» y ordenar sus bloques: nombre, palabras clave, tipo de coincidencia,
  secciones, organismo, interruptor de aviso, resumen «Así funcionará», acciones.
- **FR-017**: El nombre MUST ser obligatorio, de 1 a 60 caracteres tras recortar espacios; si está vacío
  al añadir el primer criterio, la aplicación MUST proponer uno a partir de ese criterio, siempre editable.
- **FR-018**: Las palabras o frases MUST añadirse como chips eliminables con Intro o «+», de 2 a 60
  caracteres cada una, como mucho diez por aviso, sin vacías y sin duplicados una vez normalizadas; la
  ayuda del bloque MUST decir «Busca en el título, organismo y categorías del RSS».
- **FR-019**: El tipo de coincidencia MUST ofrecer «Cualquiera de las palabras», elegida por defecto, y
  «Todas las palabras».
- **FR-020**: La selección de secciones MUST ser opcional —«Opcional · Si no eliges ninguna, se buscará
  en todas»— y MUST hacerse en un selector titulado «Seleccionar secciones» con la opción «Todas las
  secciones», la lista jerárquica de las nueve secciones y sus subsecciones, selección múltiple,
  contador de seleccionadas y botón «Aplicar».
- **FR-021**: Marcar una sección con subsecciones MUST seleccionar todas sus subsecciones; desmarcar una
  subsección MUST dejar la sección en estado parcial; el resumen MUST decir «<Sección> (todas)» cuando
  estén todas sus subsecciones.
- **FR-022**: La aplicación MUST identificar cada sección elegida con el mismo código estable que usa el
  resto de la aplicación, no con el texto visible, y MUST NOT producir coincidencias duplicadas por tener
  marcadas a la vez una sección y sus subsecciones.
- **FR-023**: El organismo MUST ser texto libre opcional, con sugerencias tomadas de los organismos ya
  almacenados; vacío significa «Cualquier organismo».
- **FR-024**: El formulario MUST incluir un interruptor «Aviso / Notificar al encontrar coincidencias
  nuevas», activo por defecto al crear.
- **FR-025**: El bloque «Así funcionará» MUST describir la regla en castellano corriente y actualizarse
  con cada cambio; MUST NOT contener términos técnicos como AND, OR, identificadores de fuente ni nombres
  de campos.
- **FR-026**: Para guardar MUST existir al menos un criterio positivo —una palabra, una sección o un
  organismo— además del nombre; el botón principal MUST estar deshabilitado mientras el formulario no sea
  válido o se esté guardando.
- **FR-027**: La aplicación MUST NOT interpretar lo escrito como expresión regular.
- **FR-028**: Al guardar una edición, la aplicación MUST tratar la regla como nueva a efectos de
  coincidencias: nada de lo ya almacenado genera novedades.

**Qué coincide**

- **FR-029**: La aplicación MUST evaluar cada aviso sobre el título, las categorías, el organismo y su
  jerarquía, y el nombre de la sección y subsección de la publicación tal como llegan del RSS; MUST NOT
  buscar en la dirección del documento, en identificadores técnicos ni en el contenido del PDF.
- **FR-030**: La comparación MUST ignorar mayúsculas, tildes y diferencias de espacios, y MUST ser por
  subcadena: «subvención» encuentra «subvenciones».
- **FR-031**: Una frase MUST coincidir solo si sus palabras aparecen consecutivas.
- **FR-032**: Los tres grupos de criterios —secciones, organismo, palabras— MUST cumplirse todos; un
  grupo sin criterios MUST considerarse cumplido.
- **FR-033**: Dentro de las palabras, «Cualquiera» MUST cumplirse con al menos una y «Todas» MUST exigir
  todas, en cualquier orden y posición.
- **FR-034**: El organismo MUST compararse de forma parcial y normalizada contra la jerarquía completa del
  organismo y contra el emisor del título, si existe.
- **FR-035**: Un aviso pausado MUST NOT coincidir con nada.
- **FR-036**: La aplicación MUST NOT depender de la posición de los elementos del campo de categorías.

**Cuándo se evalúa y qué se guarda**

- **FR-037**: La aplicación MUST evaluar los avisos activos inmediatamente después de cada sincronización
  correcta, tanto manual como periódica, y MUST hacerlo con las mismas reglas en los dos casos.
- **FR-038**: La aplicación MUST evaluar únicamente las publicaciones realmente nuevas de esa
  sincronización; MUST NOT tratar como nuevas las ya conocidas ni las conocidas que hayan cambiado.
- **FR-039**: La primera sincronización correcta de una instalación MUST establecer la línea base: MUST
  guardar las publicaciones y MUST NOT generar novedad ni notificación alguna.
- **FR-040**: Crear, editar o reactivar un aviso MUST hacer que solo avise de publicaciones detectadas en
  sincronizaciones posteriores a ese momento.
- **FR-041**: Una sincronización fallida MUST NOT evaluar nada ni alterar la última coincidencia de ningún
  aviso; una fuente vacía MUST tratarse como correcta y sin novedades.
- **FR-042**: La aplicación MUST registrar cada coincidencia como la relación entre un aviso y una
  publicación en un momento, y MUST impedir que la misma pareja se registre dos veces.
- **FR-043**: Una publicación MUST notificarse como mucho una vez, aunque coincida con varios avisos y
  aunque vuelva a recibirse en otra sincronización o por otra fuente.
- **FR-044**: Las coincidencias MUST conservarse aunque la notificación de Android se descarte, aunque el
  permiso esté desactivado y aunque el aviso se pause; MUST borrarse solo al eliminar el aviso.

**Cómo se entrega**

- **FR-045**: Cuando la aplicación no esté en pantalla, cada coincidencia MUST entregarse como
  notificación de Android en un canal propio llamado «Avisos del BOC», de importancia normal, con icono
  pequeño monocromo, acento azul institucional y estilo expandido para leer títulos largos.
- **FR-046**: Una publicación que coincide con un aviso MUST notificarse como «Nueva publicación:
  <aviso>» con el título de la publicación como cuerpo; una que coincide con varios MUST notificarse como
  «Nueva publicación del BOC» con el título y «Coincide con <A> y <B>».
- **FR-047**: Varias publicaciones en una misma sincronización MUST agruparse bajo una clave común con un
  resumen «N publicaciones nuevas coinciden con tus avisos»; MUST NOT sonar ni vibrar por cada una.
- **FR-048**: Tocar una notificación individual MUST abrir la aplicación directamente en el detalle de
  esa publicación, MUST marcar la novedad como leída y MUST retirar la notificación; tocar el resumen
  MUST abrir la pestaña Novedades.
- **FR-049**: La apertura desde una notificación MUST pasar por la portada de arranque y respetar sus
  bloqueos —versión mínima, mantenimiento—; si hay bloqueo, MUST NOT navegar al detalle.
- **FR-050**: Cuando la aplicación esté en pantalla fuera de Avisos, cada tanda de coincidencias MUST
  anunciarse con un mensaje breve no bloqueante —«Una nueva publicación coincide con «<aviso>»» o «N
  nuevas publicaciones coinciden con tus avisos»— con la acción «VER», que abre Novedades; descartarlo
  MUST NOT marcar nada como leído.
- **FR-051**: Cuando la aplicación esté en pantalla dentro de Avisos, la lista y el contador MUST
  actualizarse sin mensaje.
- **FR-052**: La aplicación MUST NOT mostrar a la vez notificación de Android y mensaje interno por la
  misma coincidencia; la decisión MUST tomarse una vez por sincronización.
- **FR-053**: La aplicación MUST NOT usar diálogos, ventanas emergentes ni avisos de pantalla completa
  para comunicar coincidencias; los diálogos quedan para el permiso y la eliminación.

**Novedades y lectura**

- **FR-054**: La pestaña Novedades MUST listar las publicaciones con coincidencia, agrupadas por día con
  separadores «Hoy», «Ayer» y la fecha, ordenadas por fecha de publicación y momento de detección.
- **FR-055**: Cada novedad MUST mostrar si está leída, el título, los avisos coincidentes, la sección y el
  momento de detección; sin leer MUST distinguirse con un punto azul y un fondo azulado; leída, sin
  indicador destacado.
- **FR-056**: Una novedad MUST pasar a leída al abrir la publicación desde la notificación, desde Novedades
  o desde cualquier otro sitio de la aplicación, o al usar «Marcar todas como leídas».
- **FR-057**: Una novedad MUST NOT pasar a leída por descartar el mensaje interno, por descartar la
  notificación, por entrar en Avisos ni por cambiar a la pestaña Novedades.
- **FR-058**: La pestaña MUST ofrecer «Marcar todas como leídas», y sin novedades MUST mostrar «No tienes
  avisos nuevos».

**Permiso**

- **FR-059**: La aplicación MUST NOT pedir el permiso de notificaciones al arrancar.
- **FR-060**: Tras guardar correctamente el primer aviso activo, si el permiso es necesario y no se ha
  concedido, la aplicación MUST mostrar «Activa las notificaciones / Permite que BOC Cantabria te avise
  cuando aparezcan publicaciones relacionadas con tus intereses» con «Ahora no» y «Continuar», y solo
  tras «Continuar» MUST lanzar la petición de Android.
- **FR-061**: Si el permiso se rechaza, la aplicación MUST conservar el aviso, MUST mostrar el aviso
  persistente con «Abrir ajustes» y MUST NOT volver a pedir el permiso en cada apertura.
- **FR-062**: Antes de mostrar una notificación, la aplicación MUST comprobar que Android lo permite; si
  no, MUST guardar la coincidencia y actualizar el contador sin intentarlo.

**Comprobación periódica**

- **FR-063**: La aplicación MUST comprobar el boletín periódicamente en segundo plano mientras exista al
  menos un aviso activo, con red disponible, sin hora exacta, sin alarmas exactas y sin servicio
  permanente en primer plano.
- **FR-064**: La comprobación periódica MUST reutilizar el mismo ciclo de sincronización y evaluación que
  la actualización manual.
- **FR-065**: La interfaz MUST hablar de «comprobación periódica» y MUST NOT prometer tiempo real ni
  ofrecer configurar una frecuencia exacta.
- **FR-066**: Sin avisos activos, la aplicación MUST NOT ejecutar la comprobación periódica.

**Vista previa**

- **FR-067**: El formulario MUST poder mostrar cuántas publicaciones ya almacenadas coinciden con la
  configuración actual —«N publicaciones actuales coinciden con esta configuración»— con «Ver
  resultados» para hojearlas.
- **FR-068**: La vista previa MUST usar exactamente la misma comparación que la evaluación real, y MUST
  NOT generar notificaciones, ni novedades, ni cambiar la última coincidencia de ningún aviso.

**Privacidad y registro**

- **FR-069**: La aplicación MUST NOT enviar a analítica ni a informes de fallos el nombre, las palabras ni
  el organismo de ningún aviso; solo recuentos y categorías cerradas.
- **FR-070**: La aplicación MUST NOT escribir en el registro del dispositivo las palabras de los avisos ni
  los títulos de las publicaciones; MUST dejar lo suficiente para distinguir cuántas publicaciones nuevas
  hubo, cuántas coincidieron y por qué canal se entregaron.
- **FR-071**: Los avisos y las novedades MUST vivir solo en el dispositivo: sin cuenta, sin servidor, sin
  sincronización entre dispositivos.

**Diseño**

- **FR-072**: Las pantallas nuevas MUST usar el sistema de diseño de la aplicación —color, tipografía,
  espaciados, formas— y MUST NOT introducir un aspecto distinto al del resto; los mockups son orientativos.
- **FR-073**: Los textos MUST ser los recogidos en esta especificación; las cifras en castellano MUST
  concordar en número («1 activo», «2 activos», «1 coincidencia», «3 coincidencias»).
- **FR-074**: La aplicación MUST retirar de su documentación de diseño y de sus notas internas la
  anotación de que Avisos está aplazado, en la misma entrega.

### Key Entities

- **Aviso**: la regla que la persona crea. Tiene nombre, palabras o frases, tipo de coincidencia
  (cualquiera / todas), secciones elegidas (ninguna significa todas), organismo (vacío significa
  cualquiera), estado (activo / pausado), momento de creación y **momento desde el que vigila**, que se
  renueva al crear, editar o reactivar. Es la razón por la que nunca avisa hacia atrás.
- **Coincidencia**: el hecho de que una publicación nueva cumplió un aviso en un momento. Una por pareja
  aviso–publicación, nunca dos. Se borra solo con su aviso.
- **Novedad**: una publicación con al menos una coincidencia. Lleva los avisos que la trajeron, cuándo se
  detectó y si está leída. Es lo que cuenta el contador de la campana y lo que lista la pestaña.
- **Notificación**: la forma en que Android muestra una novedad cuando la aplicación no está en pantalla.
  Puede desaparecer sin que la novedad desaparezca.
- **Comprobación periódica**: la sincronización que la aplicación hace por su cuenta mientras haya avisos
  activos. Usa el mismo ciclo que la manual.
- **Publicación**: la que ya existe en la aplicación; esta feature la lee y la abre, nunca la modifica ni
  la borra.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde cualquier pestaña, crear un aviso de una sola palabra cuesta **cuatro toques o
  menos** —campana, Crear aviso, escribir, Guardar— y aparece activo en la lista al momento.
- **SC-002**: La primera sincronización de una instalación con avisos ya creados produce **cero**
  notificaciones y **cero** novedades, aunque coincidan **cientos** de publicaciones.
- **SC-003**: Una publicación que coincide con **dos** avisos produce **una** notificación, **una** novedad
  y **una** unidad en el contador.
- **SC-004**: La misma publicación recibida en **dos** sincronizaciones consecutivas, o por **dos** fuentes,
  produce **una** novedad.
- **SC-005**: Editar o reactivar un aviso produce **cero** novedades de publicaciones ya almacenadas.
- **SC-006**: Tocar una notificación individual abre el detalle correcto en **el 100 %** de los casos y deja
  la novedad leída; tocar el resumen abre Novedades.
- **SC-007**: Con la aplicación en pantalla, una coincidencia produce **un** mensaje con VER y **cero**
  notificaciones de Android; con la aplicación cerrada, **una** notificación y **cero** mensajes.
- **SC-008**: Con el permiso de notificaciones denegado, **el 100 %** de las coincidencias siguen
  apareciendo en Novedades y en el contador.
- **SC-009**: «ganadería», «GANADERIA» y «Ganaderia» coinciden con el mismo texto; «subvención» coincide con
  «subvenciones»; «medio rural» no coincide con «rural medio».
- **SC-010**: Los casos de la tabla de configuraciones del documento funcional —ganadería en todas las
  secciones; ganadería y medio rural en la 6; ayuda **y** jóvenes en la 6; solo la sección 2.2; solo el
  Ayuntamiento de Piélagos; Urbanismo de Santander; la frase «jóvenes agricultores»; «Cosío»— se comprueban
  **todos** y coinciden exactamente con lo esperado.
- **SC-011**: **Cero** apariciones del nombre, las palabras o el organismo de un aviso en analítica, informes
  de fallos y registro del dispositivo.
- **SC-012**: **Ningún** texto visible menciona tiempo real, hora exacta ni frecuencia configurable.
- **SC-013**: Con la aplicación cerrada y un aviso activo, la comprobación periódica entrega una
  notificación **sin** que nadie abra la aplicación, comprobable en un móvil real.
- **SC-014**: La eliminación de un aviso deja **intactas** todas las publicaciones y **elimina** todas sus
  coincidencias.
- **SC-015**: Las cuatro puertas del proyecto —compilación, pruebas unitarias e integración, pruebas de
  interfaz y lint— quedan en verde, y la base de datos de quien ya tiene la aplicación **conserva** todo lo
  que tenía.

## Fuera de alcance

Se dice en voz alta para que no se cuele por el camino:

- **Buscar dentro del PDF** para decidir si una regla coincide. En una versión futura, como opción
  explícita y nunca mezclada con esta.
- **Resumen automático o clasificación con IA** en la notificación o en la regla.
- **Cuenta de usuario, servidor, notificaciones push, sincronización entre dispositivos.**
- **Alarmas exactas o servicio permanente en primer plano.**
- **Expresiones regulares, exclusión de palabras** («no incluir estas palabras»), **filtro ORD/EXT**.
- **Horarios silenciosos, prioridad distinta por aviso, resumen diario agrupado.**
- **Historial del BOC anterior a la instalación.**
- **Acción rápida de guardar en favoritos desde la notificación** y **recomendaciones de avisos**.
- **Marcar como leída deslizando** en la lista de novedades: si se añade, será «Marcar como leída» con
  deshacer, pero no en esta entrega.

## Assumptions

- **Los dos documentos de `Datos_modelo/` son la fuente de verdad** del comportamiento, y los mockups
  solo orientan; cuando discrepan, mandan los documentos y las decisiones cerradas de arriba.
- **La aplicación ya distingue lo nuevo de lo conocido** en cada sincronización; esta feature aprovecha
  esa distinción, no la reinventa.
- **Las secciones se identifican con el mismo código estable que ya usa el resto de la aplicación**, y
  las diecinueve entradas más finas del árbol de secciones se corresponden una a una con las diecinueve
  fuentes del boletín. Elegir una sección con subsecciones equivale a elegir todas sus subsecciones.
- **La normalización de texto es la misma que ya usa Buscar**: minúsculas, sin tildes, espacios
  compactados. No se añade una segunda forma de normalizar.
- **El intervalo de la comprobación periódica lo decide el plan técnico**, es del orden de horas y no de
  minutos, porque el boletín se publica una vez por día laborable; Android puede retrasarlo.
- **El contador «N coincidencias hoy» usa el día local del dispositivo.**
- **Los nombres de los avisos aparecen en las notificaciones**, porque esa es su función; lo que no
  aparece nunca es en analítica ni en informes.
- **Una regla creada mientras una sincronización está en curso** empieza a vigilar en la siguiente.
- **Se acepta perder el borrador del formulario** si el proceso muere a medias.
- **La ventana de cien anuncios por fuente** es una limitación de la fuente; la aplicación no promete
  histórico completo tras un largo periodo sin comprobar.
- **La primera sentencia de borrado del proyecto** afecta solo a los avisos y sus coincidencias; ninguna
  publicación del boletín se borra nunca, y esa regla se reformula en la documentación del proyecto en la
  misma entrega.
