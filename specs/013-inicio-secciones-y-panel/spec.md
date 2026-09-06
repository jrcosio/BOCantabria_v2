# Feature Specification: Inicio y panel lateral — que cada control diga lo que hace

**Feature Branch**: `013-inicio-secciones-y-panel`

**Created**: 6 de septiembre de 2026

**Status**: Draft

**Input**: User description: "1ª La opción de «Todo» no crees que debieran de salir todas las
publicaciones??? xq salen 39??? Cuando miras las siguientes y tienen muchísimas más. Ejemplo
«Disposiciones» tiene 106 ahora mismo, «Personal» tiene 336… por lo tanto si es «Todo» tendrían que
ser todas no??? 2ª Continuo con el menú este de la homescreen: en menú Personal tienen realmente
subsecciones, como se puede ver en el menú que sale de la izquierda; lo mismo que Economía, el de
Anuncios y el de Judicial. Por lo tanto aquí te propongo que cuando el usuario pulse salgan esas
opciones debajo para que pueda seleccionar la que más le guste o la general, igual que en el menú
lateral. 3º El menú lateral quiero hacer varios cambios para mejorar la usabilidad y el
entendimiento: el buscar que sale no tiene sentido en este menú, pero en su lugar quiero una flecha
como la de volver atrás que recoja el menú, y también quiero el logo de Cantabria y el nombre de la
app «BOC Cantabria»; la flecha de recoger a la derecha para que se entienda lo que hace. El resto de
funciones igual que está actualmente. 4ª El buscar de la parte superior da pie a no entender que solo
vale para buscar en la screen, así que ese buscar quiero eliminarle por completo. 5ª La fecha que sale
en la parte de arriba no queda claro qué fecha es, por lo tanto habría que poner algo para que el
usuario entienda que es esa fecha, como por ejemplo «Fecha de la publicación más cercana» o algo que
suene mejor o más profesional."

Sobre el punto 4 el propietario cambió de decisión al conocer el alcance: la búsqueda rápida **se
mantiene** y lo que cambia es su redacción, para conservar el puente hacia el buscador global que la
feature 006 construyó a propósito.

---

## Lo que hay que saber antes de leer nada más *(contexto imprescindible)*

- **Las cinco observaciones son el mismo problema con cinco caras**: la interfaz no dice lo que hace.
  Ninguna de las cinco es un defecto de comportamiento. En las cinco el mecanismo es correcto y lo que
  falla es lo que se ve, lo que se lee o lo que no se ofrece. Esta feature **no cambia ni una consulta
  al almacén, ni una regla de negocio, ni un dato guardado**.
- **«Todo» nunca quiso decir «todas las publicaciones»: quiere decir «el boletín del día».** Es el
  requisito FR-034 de `specs/003-boletin-del-dia/spec.md`, y está así porque el boletín del día es la
  portada natural de la aplicación: lo que el BOC ha publicado en su última edición, de todas las
  secciones juntas. Los chips de sección, en cambio, no se restringen a una fecha (FR-035): enseñan el
  archivo entero de esa sección. Y como en esta aplicación **no se borra nunca una publicación**, ese
  archivo solo crece. De ahí que «Todo» muestre 39 y «Personal» 336: no hay ningún tope, ningún
  truncado y ninguna paginación. La etiqueta es la que promete lo que no da.
- **Esa misma fecha de la cabecera azul es la respuesta a la pregunta anterior**, y por eso los puntos
  1 y 5 se arreglan juntos: rotulada, deja de haber misterio en el recuento.
- **Las subsecciones ya existen y ya funcionan**: el panel lateral las despliega desde la feature 003 y
  la navegación a una subsección está resuelta. Lo único que falta es ofrecerlas también en la fila de
  filtros rápidos, que hoy se queda en las nueve secciones de primer nivel. Tienen subsecciones cuatro:
  2 (Autoridades y personal), 4 (Economía, Hacienda y Seguridad Social), 7 (Otros anuncios) y 8
  (Procedimientos judiciales).
- **El filtro del panel lateral se retira, y hay que decirlo en voz alta.** La feature 003 lo
  especificó y funciona; se quita porque sobre nueve filas no aporta y porque un campo con una lupa
  dentro de un panel de secciones se lee como «buscar publicaciones». Queda **superado**, no
  incumplido.
- **La búsqueda rápida de la barra superior se queda.** El propietario pidió eliminarla y, al conocer
  que con ella se iría el puente «no hay nada aquí → búscalo en todo el BOC» que la feature 006
  construyó para que nadie termine en un callejón sin salida, optó por conservar el mecanismo y cambiar
  solo lo que se lee. Los requisitos FR-006 a FR-020 de `specs/006-buscar/spec.md` **siguen vigentes**;
  cambia su redacción, no su comportamiento.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El primer filtro se llama por su nombre (Priority: P1)

Alguien abre la aplicación, ve el primer chip de la fila de filtros y entiende, antes de tocarlo, que
lo que tiene delante es la última edición del boletín y no el archivo completo. Al pasar a
«Disposiciones» y encontrar muchas más publicaciones no concluye que la aplicación falla: concluye que
está viendo otra cosa.

**Why this priority**: es el malentendido que hace que alguien piense que la aplicación pierde datos.
Un producto que parece roto se abandona antes de que se descubra lo que hace bien. Y cuesta una
palabra.

**Independent Test**: abrir Inicio, leer el primer chip, tocar una sección con mucho archivo y volver
al primer chip. Se comprueba sin tocar ninguna otra parte de la feature.

**Acceptance Scenarios**:

1. **Given** Inicio recién abierto, **When** la persona mira la fila de filtros rápidos, **Then** el
   primer chip nombra el boletín del día y no promete la totalidad del archivo.
2. **Given** una sección elegida, **When** la persona toca el primer chip, **Then** vuelve al boletín
   del día exactamente como hasta ahora, con el mismo contenido y el mismo recuento.

---

### User Story 2 - La fecha de la cabecera se explica sola (Priority: P1)

La cabecera azul muestra una fecha. Quien la lee sabe de qué fecha se trata sin tener que deducirlo:
con el boletín del día es la fecha de la última edición publicada; con una sección elegida es la fecha
de la publicación más reciente de esa sección.

**Why this priority**: es la otra mitad del malentendido anterior. Una fecha sin rótulo junto a un
recuento invita a inventarse la relación entre ambos.

**Independent Test**: abrir Inicio y leer la cabecera; elegir una sección y volver a leerla. El rótulo
cambia y en los dos casos dice qué se está mirando.

**Acceptance Scenarios**:

1. **Given** el boletín del día en pantalla, **When** la persona lee la cabecera, **Then** la fecha va
   acompañada de un rótulo que la identifica como la de la edición publicada.
2. **Given** una sección o subsección elegida, **When** la persona lee la cabecera, **Then** la fecha
   va acompañada de un rótulo que la identifica como la de la publicación más reciente de esa
   selección.
3. **Given** una instalación sin ninguna publicación almacenada todavía, **When** la cabecera se
   dibuja, **Then** no se muestra ni fecha ni rótulo suelto.

---

### User Story 3 - Las subsecciones, sin abrir el panel lateral (Priority: P1)

Alguien toca «Personal» en la fila de filtros. La lista pasa a toda la sección y, justo debajo,
aparece una segunda fila con «Toda la sección», «Nombramientos», «Oposiciones» y «Otros». Toca
«Oposiciones» y la lista y la cabecera pasan a esa subsección. Vuelve a «Toda la sección» y recupera
las 336. Toca «Disposiciones», que no tiene subsecciones, y la segunda fila desaparece.

**Why this priority**: es lo único de esta feature que añade capacidad. Hoy, para llegar a una
subsección hay que saber que existe el panel lateral, abrirlo, encontrar la sección y desplegarla —y
las secciones grandes son justo las que tienen subsecciones y las que más lo necesitan.

**Independent Test**: recorrer las cuatro secciones con subsecciones y las cinco sin ellas,
comprobando que la segunda fila aparece y desaparece y que cada chip lleva a lo que dice.

**Acceptance Scenarios**:

1. **Given** el boletín del día en pantalla, **When** la persona toca el chip de una sección con
   subsecciones, **Then** la lista muestra la sección completa **y** aparece debajo una segunda fila
   con «Toda la sección» y una entrada por subsección.
2. **Given** esa segunda fila visible, **When** la persona toca una subsección, **Then** la lista y la
   cabecera pasan a esa subsección, la segunda fila sigue visible y la subsección elegida queda
   marcada.
3. **Given** una subsección elegida, **When** la persona toca «Toda la sección», **Then** vuelve a la
   sección completa sin perder la segunda fila.
4. **Given** el boletín del día en pantalla, **When** la persona toca el chip de una sección sin
   subsecciones, **Then** no aparece ninguna segunda fila.
5. **Given** una subsección elegida desde el **panel lateral**, **When** Inicio se dibuja, **Then** la
   segunda fila aparece igualmente, con esa subsección marcada y su sección padre marcada arriba.
6. **Given** una subsección elegida, **When** el dispositivo gira o el proceso muere y se restaura,
   **Then** la selección y la segunda fila se conservan.

---

### User Story 4 - El panel lateral se presenta y se recoge (Priority: P2)

Al abrir el panel lateral, lo primero que se ve es el escudo de Cantabria y el nombre de la
aplicación, y al final de esa misma fila una flecha que lo recoge. Ya no hay campo de búsqueda. El
resto —las nueve secciones, sus iconos y colores, el desplegable de subsecciones— está igual.

**Why this priority**: mejora la comprensión y da una salida explícita a quien no conoce el gesto de
deslizar, pero nadie se queda sin poder hacer nada por su ausencia: el panel ya se cierra con el gesto
y tocando fuera.

**Independent Test**: abrir el panel, comprobar la cabecera y la ausencia del campo, tocar la flecha y
ver que el panel se recoge sin cambiar de pantalla.

**Acceptance Scenarios**:

1. **Given** Inicio en pantalla, **When** la persona abre el panel lateral, **Then** la primera fila
   muestra el escudo oficial, el nombre de la aplicación y, al final de la fila, un control de recoger.
2. **Given** el panel abierto, **When** la persona toca ese control, **Then** el panel se recoge y la
   pantalla que había debajo queda como estaba, sin navegar a ninguna parte.
3. **Given** el panel abierto, **When** la persona lo recorre, **Then** no hay ningún campo de texto y
   siguen estando las nueve secciones con sus iconos, sus colores y sus subsecciones desplegables.
4. **Given** el panel abierto, **When** la persona despliega una sección y elige una subsección,
   **Then** ocurre exactamente lo que ocurría antes de esta feature.

---

### User Story 5 - La lupa dice qué filtra (Priority: P3)

Quien toca la lupa de la barra superior lee, en el propio campo, que lo que va a hacer es acotar lo
que ya tiene delante, no buscar en todo el boletín. Si no encuentra nada, se le sigue ofreciendo
buscarlo en todo lo almacenado.

**Why this priority**: es un ajuste de redacción sobre un mecanismo que funciona y que además ya tiene
salida cuando no encuentra nada. Molesta, pero no impide nada.

**Independent Test**: tocar la lupa, leer el texto de ayuda, escribir algo que no exista y comprobar
que el mensaje y la salida hacia el buscador global siguen ahí.

**Acceptance Scenarios**:

1. **Given** Inicio en pantalla, **When** la persona toca la lupa, **Then** el texto de ayuda del
   campo dice que se filtra lo que está en pantalla, sin dar a entender que se busca en todo el BOC.
2. **Given** el filtro abierto sin coincidencias, **When** la persona lee el mensaje, **Then** este
   habla de la lista que está viendo y ofrece buscar lo mismo en todo lo almacenado.
3. **Given** esa oferta, **When** la persona la acepta, **Then** el buscador global se abre con el
   término ya escrito, igual que antes de esta feature.

---

### Edge Cases

- **El BOC no publica todos los días.** En domingo, «Boletín de hoy» muestra la edición del viernes.
  El rótulo de la fecha es justamente lo que evita que eso se lea como un fallo; la etiqueta del chip
  no debe afirmar que la edición es de hoy más de lo que ya lo hacía la cabecera.
- **Una subsección legítimamente vacía.** La subsección 8.1 (Subastas) no tiene publicaciones. Al
  elegirla debe verse el estado vacío de sección, nunca un error.
- **Una sección sin subsecciones.** Cinco de las nueve no las tienen; en ellas la segunda fila no
  existe, no aparece vacía ni deja hueco.
- **El primer arranque, sin nada almacenado.** La cabecera no tiene fecha que rotular y no debe pintar
  un rótulo huérfano.
- **La segunda fila con el filtro rápido abierto.** Filtrar no cambia la selección, así que la segunda
  fila permanece.
- **Un panel sin campo de búsqueda sigue teniendo veintitrés filas posibles.** Con varias secciones
  desplegadas a la vez, el panel debe poder recorrerse por completo, cabecera incluida.
- **Pantallas estrechas.** Las dos filas de filtros se desplazan horizontalmente; ninguna debe
  recortar texto ni obligar a desplazar la pantalla en horizontal.

---

## Requirements *(mandatory)*

### Functional Requirements

#### El primer filtro y la cabecera (puntos 1 y 5)

- **FR-001**: El primer chip de la fila de filtros rápidos MUST nombrar el boletín del día y MUST NOT
  emplear una palabra que prometa la totalidad de lo almacenado.
- **FR-002**: El comportamiento del primer chip MUST permanecer inalterado: sigue devolviendo a las
  publicaciones de la fecha más reciente disponible entre todas las secciones (FR-034 de la 003).
- **FR-003**: Las consultas al almacén, el recuento de la cabecera y el orden del listado MUST
  permanecer inalterados por esta feature.
- **FR-004**: La fecha de la cabecera editorial MUST ir acompañada de un rótulo que diga qué fecha es.
- **FR-005**: Ese rótulo MUST distinguir los dos significados: con el boletín del día, la fecha de la
  edición publicada; con una sección o subsección, la de su publicación más reciente.
- **FR-006**: Cuando no haya fecha que mostrar, la cabecera MUST NOT mostrar el rótulo.
- **FR-007**: El rótulo MUST estar redactado en español, sin tecnicismos y sin códigos.

#### La segunda fila de filtros (punto 2)

- **FR-008**: Al seleccionarse una sección que tiene subsecciones, la pantalla MUST mostrar una segunda
  fila de filtros con una entrada por subsección de esa sección.
- **FR-009**: Esa segunda fila MUST incluir una entrada que devuelva a la sección completa.
- **FR-010**: Tocar el chip de una sección con subsecciones MUST mostrar la sección completa **y**
  desplegar la segunda fila en el mismo gesto; MUST NOT exigir un segundo toque para ver la sección.
- **FR-011**: La segunda fila MUST reflejar cuál es la selección vigente: la subsección elegida, o la
  entrada de sección completa cuando no hay subsección elegida.
- **FR-012**: La primera fila MUST seguir marcando la sección padre mientras haya una subsección
  elegida.
- **FR-013**: La segunda fila MUST NOT mostrarse cuando la selección sea el boletín del día ni cuando
  sea una sección sin subsecciones.
- **FR-014**: La segunda fila MUST aparecer también cuando se llegue a una subsección desde el panel
  lateral, sin diferencia con haber llegado desde los chips.
- **FR-015**: La selección MUST sobrevivir a un cambio de configuración del dispositivo y a la muerte
  del proceso, y la segunda fila MUST reconstruirse en consecuencia.
- **FR-016**: Ambas filas MUST desplazarse horizontalmente y MUST NOT provocar desplazamiento
  horizontal de la pantalla.
- **FR-017**: La segunda fila MUST distinguirse visualmente de la primera, de modo que se lea como
  dependiente de ella y no como una segunda lista de secciones.
- **FR-018**: Elegir una subsección desde los chips MUST llevar exactamente al mismo resultado que
  elegirla desde el panel lateral, incluida la cabecera y el comportamiento del gesto de volver.

#### El panel lateral (punto 3)

- **FR-019**: El panel lateral MUST mostrar como primer elemento una cabecera con el escudo oficial de
  Cantabria y el nombre de la aplicación.
- **FR-020**: Esa cabecera MUST incluir, al final de la fila, un control que recoja el panel.
- **FR-021**: El control de recoger MUST emplear una flecha del mismo tipo que la de volver atrás, para
  que se lea como «devolver el panel a su sitio».
- **FR-022**: Accionar ese control MUST recoger el panel y MUST NOT navegar a ninguna parte ni alterar
  la selección vigente.
- **FR-023**: El control MUST tener descripción para lectores de pantalla.
- **FR-024**: El panel lateral MUST NOT ofrecer campo de búsqueda ni filtrado de la lista de secciones.
- **FR-025**: El resto del panel —las nueve secciones, sus iconos, sus colores, el desplegable de
  subsecciones y la navegación al elegir— MUST permanecer inalterado.
- **FR-026**: El panel MUST poder recorrerse por completo, cabecera incluida, con todas las secciones
  desplegadas a la vez.
- **FR-027**: Los gestos de cierre que ya existen —deslizar y tocar fuera— MUST seguir funcionando.

#### La búsqueda rápida (punto 4)

- **FR-028**: El texto de ayuda del filtro rápido MUST decir que acota lo que está en pantalla y MUST
  NOT dar a entender que consulta todo el boletín.
- **FR-029**: La descripción del control que abre el filtro MUST decir lo mismo.
- **FR-030**: El mensaje de «sin coincidencias» MUST referirse a la lista que se está viendo y no
  únicamente a una edición, porque con una sección elegida lo que se filtra abarca muchas fechas.
- **FR-031**: El puente hacia el buscador global MUST conservarse tal cual: mismo ofrecimiento, mismo
  traspaso del término escrito.
- **FR-032**: El comportamiento del filtro rápido MUST permanecer inalterado en todo lo demás: sigue
  sin tocar la red ni el almacén, sigue sin navegar y sigue reaccionando desde el primer carácter.

#### Transversales

- **FR-033**: Todo lo que esta feature dibuja MUST usar exclusivamente los valores con nombre del
  sistema de diseño; ni un color, ni un tamaño, ni un espaciado literal.
- **FR-034**: Todos los textos nuevos MUST vivir en los recursos de cadenas de la aplicación, nunca
  incrustados en la pantalla.
- **FR-035**: Los controles nuevos MUST tener área táctil suficiente y descripción accesible.
- **FR-036**: Esta feature MUST NOT registrar en analítica ni en el informe de fallos ningún dato nuevo
  que identifique a la persona; en particular, MUST NOT registrar el texto del filtro rápido.
- **FR-037**: Esta feature MUST NOT introducir migraciones de base de datos ni cambiar ningún dato
  almacenado.

---

## Requisitos de features anteriores que quedan superados

Se dejan escritos para que no se lean como incumplidos:

- **Feature 003, el campo de filtro del panel lateral**: **retirado** por FR-024 de esta feature. El
  panel deja de filtrarse; a cambio gana cabecera y un cierre explícito.
- **Feature 003, FR-036** (la fila de filtros rápidos incluye una opción que devuelve al boletín del
  día): **se amplía**. Sigue habiendo esa opción, cambia su etiqueta (FR-001) y aparece una segunda
  fila para las subsecciones (FR-008 a FR-018).
- **Feature 003, FR-032** (la cabecera muestra denominación, fecha y recuento): **se amplía** con el
  rótulo de la fecha (FR-004, FR-005).
- **Feature 003, FR-031** (la barra superior lleva la acción de buscar): **se mantiene**; solo cambia
  su redacción (FR-029).
- **Feature 006, FR-006 a FR-020** (la búsqueda rápida y el puente al buscador global): **se
  mantienen íntegros**. Cambian los textos de FR-006 y del estado sin coincidencias; el mecanismo, el
  ámbito y el puente no.
- **Feature 003, FR-034 y FR-035** (qué muestra el boletín del día y qué muestra una sección): **siguen
  vigentes sin cambio alguno**. Esta feature confirma que la respuesta a la pregunta del propietario es
  que el comportamiento era el correcto.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Alguien que abre la aplicación por primera vez identifica, sin ayuda y en menos de cinco
  segundos, que la lista inicial corresponde a una edición concreta del boletín y a qué fecha.
- **SC-002**: Ninguna persona de una prueba de recorrido interpreta la diferencia entre el recuento del
  primer filtro y el de una sección como una pérdida de publicaciones.
- **SC-003**: Llegar a una subsección desde Inicio pasa de requerir cuatro acciones —abrir el panel,
  localizar la sección, desplegarla, elegir— a requerir dos toques.
- **SC-004**: Las cuatro secciones con subsecciones ofrecen sus catorce subsecciones desde la fila de
  filtros, y las cinco secciones sin subsecciones no ofrecen ninguna.
- **SC-005**: El panel lateral se puede cerrar con un control visible, además de con los dos gestos que
  ya existían.
- **SC-006**: El recuento y el contenido del boletín del día y de cada sección son idénticos antes y
  después de esta feature, para los mismos datos almacenados.
- **SC-007**: No aparece ninguna consulta nueva al almacén, ninguna petición de red nueva y ninguna
  migración de base de datos.
- **SC-008**: Toda pieza de reglas de presentación que esta feature introduce queda cubierta por
  pruebas automáticas, y las cuatro puertas de calidad del proyecto quedan en verde.

---

## Fuera de alcance

- Cambiar qué muestra el boletín del día o qué muestra una sección. El comportamiento es el correcto y
  se conserva.
- Añadir un filtro que muestre el archivo completo de todas las secciones a la vez. Para eso ya está la
  pestaña Buscar, que recorre todo lo almacenado con filtros y orden.
- Paginar, limitar o truncar el listado de una sección.
- Eliminar la búsqueda rápida de Inicio.
- Unificar la barra azul con flecha de volver que hoy está repetida en cuatro pantallas. Es deuda
  conocida desde la feature 012 y no entra aquí.
- Cualquier cambio en Guardados, Buscar, Avisos, el detalle, el visor o las funciones de IA.

---

## Assumptions

- **La etiqueta del primer chip será «Boletín de hoy»**, la misma denominación que ya usa la cabecera
  editorial cuando esa es la selección. Si en pantallas estrechas resulta demasiado ancha, la
  alternativa acordada es «Hoy». Decidido con el propietario al plantear las opciones.
- **Los rótulos de la fecha serán «Edición del …» para el boletín del día y «Última publicación: …»
  para una sección o subsección**, conservando el formato de fecha largo en español que ya se usa. Son
  una propuesta de redacción: pueden afinarse en `/speckit-clarify` sin que cambie ningún requisito.
- **La entrada que devuelve a la sección completa se llamará «Toda la sección»**, por paralelismo con
  el primer chip de la fila de arriba.
- **El texto de ayuda del filtro rápido pasará a hablar de «lo que estás viendo»** en lugar de «esta
  edición», por la razón que recoge FR-030.
- **La segunda fila se deriva de la selección vigente**, que ya viaja como argumento de navegación y ya
  sobrevive a la muerte del proceso. No se introduce ningún estado de expansión nuevo, y por eso FR-015
  se cumple sin trabajo adicional.
- **El escudo y el nombre de la cabecera del panel son los mismos que ya usa la barra superior de
  Inicio**, no una variante nueva.
- **El control de recoger va al final de la fila** —a la derecha en una interfaz de izquierda a
  derecha—, tal como pidió el propietario, con una flecha que apunta hacia el lado por el que el panel
  se retira.
- **No hay que tocar la capa de datos ni la de dominio del listado.** Toda la feature vive en la
  presentación.
