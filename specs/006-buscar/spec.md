# Feature Specification: Buscar

**Feature Branch**: `006-buscar`

**Created**: 2026-08-31

**Status**: Draft

**Input**: User description: "Nueva Feature, que nos centramos en las dos opciones de Buscar. (1) En la
pantalla de Inicio, mediante el icono de lupa situado en la TopBar: al pulsarla la cabecera se
transforma y despliega un campo con el texto de ayuda «Buscar en esta edición…». No abre pantalla
nueva ni busca en todo el histórico: filtra en tiempo real las publicaciones que la persona está
viendo. Búsqueda literal, equivalente a LIKE '%texto%', normalizando mayúsculas/minúsculas y tildes,
de forma que «pielagos» encuentre «Piélagos». Respeta siempre el contexto actual: boletín mostrado y
filtro de sección activo. No llama al backend. Al borrar el texto se recupera la lista completa; al
cerrar se restaura la TopBar manteniendo filtros y estado. Sin coincidencias, se ofrece repetir la
consulta en el buscador global. (2) La opción «Buscar» de la barra inferior, independiente de la
anterior, localiza publicaciones entre todos los datos del BOC Cantabria almacenados localmente,
con campo «Buscar publicaciones», filtros avanzados, chips de filtros activos, orden, tarjetas de
resultado que abren el detalle y se pueden guardar, conservación del estado al volver, estado vacío
propio y recepción del término cuando se llega desde la búsqueda rápida."

Decisiones cerradas con el propietario antes de escribir esta especificación: **una sola feature**
con las dos búsquedas y el puente entre ellas; **sin filtro de municipio**, porque el dato no
existe; **sin ordenación por relevancia** —solo `Más recientes`, por defecto, y `Más antiguas`—;
los filtros avanzados en **hoja inferior** con los chips de filtros activos visibles en la propia
pantalla; y la barra superior de Buscar **sin flecha atrás ni menú de tres puntos**, al contrario
que la imagen de referencia aportada. Esa imagen es una idea y no una especificación: donde discrepa
de lo que la aplicación ya hace, manda la aplicación —de ahí también que la barra vaya en azul
institucional, como la de Guardados, y no blanca, y que se titule `Buscar` en lugar de repetir el
texto de ayuda del campo—.

---

## Lo que el boletín publica, y lo que no *(contexto imprescindible)*

Tres de los campos que se pidieron para buscar **no existen** en el servicio del BOC. Queda escrito
aquí para que ninguna decisión posterior los dé por supuestos:

- **No hay descripción.** Cada anuncio del servicio trae únicamente **título, enlace, fecha y
  categorías**. Lo que la pantalla de detalle rotula como «Descripción» es el título entero, y el
  título ya llega con la forma `ORGANISMO: texto`. **Buscar en título y organismo cubre, por tanto,
  los tres campos pedidos** —título, organismo y descripción— sin dejarse nada fuera.
- **No hay municipio.** El servicio no lo publica y solo podría deducirse del nombre del organismo,
  y únicamente cuando es un ayuntamiento. El filtro queda fuera de esta feature (ver
  «Fuera de alcance»). No se pierde capacidad: como el organismo sí se busca, escribir `pielagos`
  devuelve todo lo del Ayuntamiento de Piélagos.
- **La «referencia» es el identificador del anuncio en el servicio**, el mismo número que la ficha
  del detalle ya muestra. Se busca.
- **La sección y la subsección se almacenan como código**, no como texto. Para que escribir
  `contratación` encuentre algo, el **nombre** de la sección y de la subsección tiene que quedar
  incorporado a lo que se busca.
- **La pantalla de Inicio no tiene selector de fecha.** Lo que la persona está viendo es «el boletín
  más reciente» o «una sección/subsección». La búsqueda rápida respeta ese contexto, sea cual sea;
  esta feature **no** añade el selector de fecha.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Encontrar un anuncio en todo lo que la aplicación ha descargado (Priority: P1)

Una persona recuerda que hace unas semanas salió algo sobre una convocatoria de su ayuntamiento,
pero no sabe qué día. Entra en Buscar desde la barra inferior, escribe `pielagos` y, mientras
escribe, van apareciendo los anuncios: los del Ayuntamiento de Piélagos y cualquier otro que
mencione el municipio, del más reciente al más antiguo. Toca uno y se abre la publicación, igual
que si la hubiera tocado en el boletín. También puede guardarla desde ahí mismo.

**Why this priority**: es la razón de ser de la feature. Hoy el archivo local crece cada media hora
y no hay ninguna forma de consultarlo: un anuncio que se pierde de vista está perdido. Sin esta
historia, las otras cuatro no tienen dónde apoyarse.

**Independent Test**: con varias publicaciones de distintas fechas almacenadas, escribir un término
que aparezca en unas pocas y comprobar que salen esas y solo esas, que se abren y que se pueden
guardar.

**Acceptance Scenarios**:

1. **Given** publicaciones almacenadas de varias fechas, **When** la persona escribe un término en
   el campo `Buscar publicaciones`, **Then** la lista muestra únicamente las publicaciones que lo
   contienen, sin que haya que pulsar ningún botón de buscar.
2. **Given** una publicación titulada `AYUNTAMIENTO DE PIÉLAGOS: …`, **When** la persona escribe
   `pielagos`, **Then** esa publicación aparece entre los resultados.
3. **Given** una publicación de la sección `Contratación`, **When** la persona escribe
   `contratacion`, **Then** esa publicación aparece, aunque la palabra no esté en su título.
4. **Given** resultados en pantalla, **When** la persona toca uno, **Then** se abre el detalle de esa
   publicación.
5. **Given** resultados en pantalla, **When** la persona toca el marcador de un resultado,
   **Then** la publicación queda guardada y aparece en Guardados.
6. **Given** el dispositivo sin conexión, **When** la persona busca, **Then** los resultados
   aparecen igualmente, porque todo lo que se consulta ya está en el dispositivo.
7. **Given** una consulta sin ninguna coincidencia, **When** termina la búsqueda, **Then** la
   pantalla dice que no se han encontrado publicaciones y sugiere modificar o quitar filtros.

---

### User Story 2 - Filtrar lo que tengo delante sin salir de la pantalla (Priority: P1)

La persona está leyendo el boletín del día, que trae ciento y pico anuncios. Le interesa lo de un
ayuntamiento concreto. Toca la lupa de la barra superior: el escudo y el título dejan sitio a un
campo con el texto de ayuda `Buscar en esta edición…` y el teclado sube solo. Escribe `santoña` y la
lista se queda con lo suyo al instante. Borra el texto y vuelve todo. Cierra el buscador y la
pantalla está exactamente como la dejó.

**Why this priority**: es la mitad de la feature que se usa a diario. Recorrer ciento cincuenta
tarjetas con el pulgar para encontrar una es el motivo por el que la lupa estaba en el diseño desde
el principio. Y es independiente de la historia 1: filtra lo que ya está en pantalla, sin consultar
el archivo.

**Independent Test**: abrir el boletín, pulsar la lupa, escribir un término presente en unas pocas
tarjetas, comprobar que la lista se reduce a esas; borrar y comprobar que vuelve entera; cerrar y
comprobar que la sección y la posición de lectura no han cambiado.

**Acceptance Scenarios**:

1. **Given** el boletín en pantalla, **When** la persona toca la lupa, **Then** la barra superior se
   transforma en un campo de búsqueda con el texto de ayuda `Buscar en esta edición…`, listo para
   escribir.
2. **Given** el buscador abierto, **When** la persona escribe, **Then** la lista se reduce en el acto
   a las publicaciones que contienen el texto, sin abrir ninguna pantalla nueva.
3. **Given** una publicación de `Piélagos` en la edición, **When** la persona escribe `pielagos`,
   **Then** esa publicación sigue en la lista.
4. **Given** la persona viendo **solo** la sección `Contratación`, **When** busca, **Then** la
   búsqueda se hace exclusivamente sobre las publicaciones de esa sección y nunca aparece nada de
   otra.
5. **Given** texto escrito y la lista filtrada, **When** la persona borra el texto, **Then** vuelve
   la lista completa del contexto actual, sin recargar nada.
6. **Given** el buscador abierto con texto, **When** la persona lo cierra, **Then** vuelve la barra
   superior normal y la pantalla conserva su sección, su cabecera y su posición de lectura.
7. **Given** el buscador abierto, **When** la persona busca, **Then** la aplicación **no** hace
   ninguna petición al servicio del BOC.
8. **Given** el buscador abierto con texto, **When** la persona gira el dispositivo, **Then** el
   texto y los resultados siguen ahí.

---

### User Story 3 - Si no está aquí, buscarlo en todo el BOC (Priority: P1)

La persona busca `expropiación` dentro del boletín del día y no hay nada. En lugar de un callejón
sin salida, la pantalla le dice que en esta edición no hay coincidencias y le ofrece buscar ese
mismo término en todo el BOC. Lo toca y aterriza en Buscar con `expropiación` ya escrito y la
búsqueda ya hecha.

**Why this priority**: sin este puente, la búsqueda rápida es una promesa a medias —«no está»— en el
momento exacto en que la persona más ayuda necesita. Y es lo que enseña que existe el buscador
global sin tener que explicarlo en ningún sitio.

**Independent Test**: buscar en la edición un término que no aparezca en ella pero sí en el archivo,
tocar la opción ofrecida y comprobar que se llega a Buscar con el término escrito y los resultados
ya en pantalla.

**Acceptance Scenarios**:

1. **Given** una búsqueda en la edición sin coincidencias, **When** termina la búsqueda, **Then** la
   pantalla explica que no hay resultados **en la edición actual** y ofrece buscar en todo el BOC.
2. **Given** ese mensaje, **When** la persona toca la opción ofrecida, **Then** se abre Buscar con el
   término ya escrito y la búsqueda global ya ejecutada, sin tener que teclearlo otra vez.
3. **Given** que la persona ya había usado Buscar antes con otra consulta, **When** llega por este
   camino con un término nuevo, **Then** lo que ve es el término nuevo y sus resultados, no la
   consulta anterior.
4. **Given** que ha llegado a Buscar por este camino, **When** vuelve a Inicio, **Then** el boletín
   sigue con su sección y su estado, y el buscador rápido queda cerrado.

---

### User Story 4 - Acotar la búsqueda y elegir el orden (Priority: P2)

Los resultados de `subvenciones` son demasiados. La persona abre los filtros, acota entre dos fechas
y elige la sección `Subvenciones y Ayudas`. Los filtros aplicados quedan visibles como etiquetas
sobre los resultados, cada una con su aspa. Quita la de la fecha con un toque y el texto que había
escrito sigue intacto. Cambia el orden a `Más antiguas` para ver por dónde empezó todo.

**Why this priority**: hace utilizable el buscador cuando el archivo crece, pero la historia 1 ya
entrega valor sin ella. Se puede demostrar y probar por separado.

**Independent Test**: con resultados en pantalla, aplicar cada filtro por separado y comprobar que
recorta lo que debe; quitar una etiqueta y comprobar que el texto no se borra; cambiar el orden y
comprobar que se invierte.

**Acceptance Scenarios**:

1. **Given** resultados en pantalla, **When** la persona abre los filtros, **Then** puede acotar por
   fecha desde, fecha hasta, sección, subsección y organismo.
2. **Given** filtros aplicados, **When** vuelve a los resultados, **Then** cada filtro activo se ve
   como una etiqueta con su aspa, y hay una acción para quitarlos todos.
3. **Given** varios filtros activos y texto escrito, **When** la persona quita una etiqueta o los
   quita todos, **Then** el texto escrito **no** se borra y los resultados se recalculan.
4. **Given** resultados en pantalla, **When** la persona elige `Más antiguas`, **Then** el orden se
   invierte; al elegir `Más recientes` vuelve al orden por defecto.
5. **Given** una combinación de filtros sin ninguna coincidencia, **When** se aplica, **Then** la
   pantalla lo dice y sugiere modificar o quitar alguno.
6. **Given** un filtro de sección aplicado, **When** la persona elige una subsección que no pertenece
   a esa sección, **Then** la propia lista de subsecciones no se lo ofrece.

---

### User Story 5 - Volver y encontrarlo todo como estaba (Priority: P2)

La persona ha buscado, ha filtrado, ha bajado un buen trecho por los resultados y abre uno. Lee, y
vuelve. Los resultados están donde los dejó: la misma consulta, los mismos filtros, el mismo orden y
la misma posición.

**Why this priority**: sin esto, cada consulta que se abre cuesta rehacer la búsqueda entera, y una
lista larga se vuelve inservible. Pero la búsqueda ya funciona sin ello, así que va detrás.

**Independent Test**: buscar, filtrar, desplazarse, abrir un resultado, volver y comprobar los cuatro
elementos.

**Acceptance Scenarios**:

1. **Given** una búsqueda con filtros, orden y desplazamiento, **When** la persona abre un resultado
   y vuelve, **Then** consulta, filtros, orden y posición son los mismos.
2. **Given** una búsqueda en curso, **When** la persona va a Inicio o a Guardados y vuelve a Buscar,
   **Then** su consulta y sus filtros siguen ahí.
3. **Given** una búsqueda en curso, **When** el sistema cierra la aplicación por falta de memoria y
   la persona vuelve, **Then** la consulta y los filtros se recuperan.

---

### Edge Cases

- **Texto con caracteres que significan algo al buscar** (`%`, `_`, `\`): se buscan como lo que son,
  caracteres. Escribir `100%` devuelve lo que contiene `100%`, nunca el archivo entero.
- **Consulta de una sola letra en el buscador global**: no se lanza. Devolvería medio archivo y no
  ayuda a nadie. La pantalla se queda en su estado inicial hasta el segundo carácter. En la búsqueda
  rápida sí filtra desde la primera letra, porque solo recorta lo que ya está en pantalla.
- **Solo espacios, o espacios sobrantes alrededor**: se ignoran. `  piélagos  ` busca lo mismo que
  `piélagos`, y un campo con solo espacios equivale a un campo vacío.
- **Demasiados resultados**: el buscador global muestra como mucho **300**, los más recientes o los
  más antiguos según el orden elegido, y **lo dice en pantalla** en lugar de aparentar que eso es
  todo. La salida es acotar con filtros.
- **Publicaciones descargadas antes de instalar esta versión**: tienen que encontrarse igual que las
  nuevas. Que el buscador solo viera lo descargado a partir de hoy sería un archivo que empieza el
  día de la actualización.
- **El archivo vacío** (primera ejecución, sincronización aún sin terminar): buscar no falla; la
  pantalla dice que no hay resultados, no que haya habido un error.
- **Cerrar la búsqueda rápida sin haber escrito nada**: la barra vuelve a la normalidad y no cambia
  nada.
- **Cambiar de sección con la búsqueda rápida abierta**: la búsqueda se cierra y la nueva sección se
  muestra entera. Mantener un filtro de texto de la sección anterior sería una lista recortada sin
  que se vea por qué.
- **Fecha «desde» posterior a la fecha «hasta»**: no se acepta como combinación; la pantalla lo
  impide en lugar de devolver una lista vacía sin explicación.
- **Un resultado guardado y luego desmarcado desde otra pantalla**: el marcador del resultado refleja
  el estado real sin que haya que volver a buscar.

## Requirements *(mandatory)*

### Functional Requirements

#### Normalización del texto, común a las dos búsquedas

- **FR-001**: Las dos búsquedas MUST ignorar las diferencias entre mayúsculas y minúsculas.
- **FR-002**: Las dos búsquedas MUST ignorar las tildes y los signos diacríticos, en el texto escrito
  y en el contenido buscado, de forma que `pielagos` encuentre `Piélagos` y `piélagos` encuentre
  `Pielagos`.
- **FR-003**: Las dos búsquedas MUST ignorar los espacios sobrantes al principio, al final y entre
  palabras.
- **FR-004**: Las dos búsquedas MUST ser literales sobre el texto introducido —el equivalente a
  buscar esa secuencia en cualquier posición—, sin interpretar comodines, operadores ni caracteres
  especiales.
- **FR-005**: Un campo con el texto vacío, o con solo espacios, MUST equivaler a no haber buscado.

#### Búsqueda rápida en la edición actual

- **FR-006**: La barra superior de Inicio MUST ofrecer una lupa que, al pulsarla, transforme la
  cabecera en un campo de búsqueda con el texto de ayuda `Buscar en esta edición…`.
- **FR-007**: Al abrirse, el campo MUST quedar listo para escribir, con el teclado visible.
- **FR-008**: La búsqueda rápida MUST NOT abrir otra pantalla ni navegar a ninguna parte.
- **FR-009**: La búsqueda rápida MUST filtrar la lista mientras se escribe, desde el primer
  carácter, sin que la persona tenga que confirmar nada.
- **FR-010**: La búsqueda rápida MUST buscar sobre el título y el organismo de cada publicación.
- **FR-011**: La búsqueda rápida MUST operar exclusivamente sobre las publicaciones del contexto que
  la pantalla está mostrando —el boletín visible y el filtro de sección o subsección activo— y MUST
  NOT devolver ninguna publicación fuera de él.
- **FR-012**: La búsqueda rápida MUST NOT realizar ninguna petición al servicio del BOC ni consultar
  publicaciones que no estén ya en pantalla.
- **FR-013**: Al borrar el texto, la lista completa del contexto actual MUST volver automáticamente.
- **FR-014**: Al cerrar el buscador, la barra superior normal MUST restaurarse y la pantalla MUST
  conservar su boletín, su filtro de sección, su cabecera y su posición de lectura.
- **FR-015**: El texto escrito y el resultado del filtrado MUST sobrevivir a un giro del dispositivo.
- **FR-016**: Cambiar de sección con el buscador abierto MUST cerrarlo y mostrar la nueva sección
  completa.
- **FR-017**: La cabecera editorial (título de la edición o de la sección, fecha y recuento de
  anuncios) MUST seguir describiendo la edición y no el resultado de la búsqueda. El número de
  coincidencias MUST mostrarse junto a la lista.

#### El puente hacia el buscador global

- **FR-018**: Cuando la búsqueda rápida no encuentre ninguna coincidencia, la pantalla MUST decir
  que no hay resultados **en la edición actual** —no «no hay resultados» a secas— y MUST ofrecer
  buscar ese mismo término en todo el BOC.
- **FR-019**: Al aceptar esa oferta, el buscador global MUST abrirse con el término ya escrito y la
  búsqueda ya ejecutada.
- **FR-020**: El término traspasado MUST prevalecer sobre cualquier consulta anterior que el
  buscador global tuviera guardada.

#### Buscador global

- **FR-021**: La pestaña `Buscar` de la barra inferior MUST dejar de anunciarse como pendiente y
  MUST mostrar el buscador.
- **FR-022**: La pantalla MUST tener un campo principal con el texto de ayuda
  `Buscar publicaciones` y una acción para borrar su contenido cuando lo tenga.
- **FR-023**: La barra superior MUST llevar el título **`Buscar`** —la misma palabra que la pestaña
  que lleva a ella—, sin acción de retroceso y sin menú de opciones. Es un destino de la barra
  inferior, no una pantalla apilada. Y MUST usar el **azul institucional**, como Guardados: dos
  destinos de la misma barra inferior con cabeceras de distinto color se leen como dos aplicaciones.
  El título **MUST NOT** repetir el texto de ayuda del campo que tiene justo debajo: la misma frase
  dos veces seguidas es ruido en pantalla y un lector de pantalla la anuncia dos veces.
- **FR-024**: La búsqueda global MUST recorrer todas las publicaciones almacenadas en el
  dispositivo, sin limitarse al boletín, la fecha ni la sección que Inicio esté mostrando.
- **FR-025**: La búsqueda global MUST buscar sobre el título, el organismo, la jerarquía de
  organismos, la referencia del anuncio y el nombre de la sección y de la subsección.
- **FR-026**: La búsqueda global MUST resolverse íntegramente con datos del dispositivo, sin
  ninguna petición de red, y MUST funcionar sin conexión.
- **FR-027**: La búsqueda global MUST encontrar también las publicaciones descargadas **antes** de
  instalar esta versión de la aplicación.
- **FR-028**: Los resultados MUST actualizarse mientras se escribe, sin botón de buscar. La consulta
  MUST tener al menos dos caracteres; por debajo, la pantalla MUST quedarse en su estado inicial.
- **FR-029**: Cada resultado MUST mostrar, como mínimo, título, organismo, fecha y sección.
- **FR-030**: Cada resultado MUST poder abrirse, llevando al mismo detalle que desde Inicio.
- **FR-031**: Cada resultado MUST poder marcarse y desmarcarse como guardado, con el mismo
  comportamiento que en Inicio, y MUST reflejar el estado real de la marca aunque haya cambiado en
  otra pantalla.
- **FR-032**: Cuando el número de coincidencias supere el máximo que la pantalla muestra —**300**—,
  la pantalla MUST decirlo explícitamente y sugerir acotar la búsqueda.
- **FR-033**: Sin resultados, la pantalla MUST mostrar un estado vacío que diga que no se han
  encontrado publicaciones y sugiera modificar o quitar alguno de los filtros.

#### Filtros y orden del buscador global

- **FR-034**: La pantalla MUST ofrecer filtros avanzados en una hoja inferior titulada
  `Filtrar resultados`, con acciones para limpiar y para aplicar.
- **FR-035**: Los filtros disponibles MUST ser: fecha desde, fecha hasta, sección, subsección y
  organismo.
- **FR-036**: La lista de subsecciones ofrecida MUST corresponder a la sección elegida.
- **FR-037**: La lista de organismos ofrecida MUST estar formada por los organismos realmente
  presentes en lo almacenado, y MUST poder recorrerse escribiendo, porque son cientos.
- **FR-038**: El sistema MUST impedir una combinación con la fecha «desde» posterior a la fecha
  «hasta».
- **FR-039**: Los filtros activos MUST mostrarse en la propia pantalla como etiquetas legibles, cada
  una con una acción para quitarla, más una acción para quitarlos todos.
- **FR-040**: Quitar una etiqueta, o quitarlas todas, MUST NOT borrar el texto escrito.
- **FR-041**: Los resultados MUST poder ordenarse por `Más recientes` y por `Más antiguas`, siendo
  `Más recientes` el orden por defecto.
- **FR-042**: El orden MUST ser estable: dos búsquedas iguales devuelven los mismos resultados en el
  mismo orden.

#### Conservación del estado

- **FR-043**: Al abrir un resultado y volver, la pantalla MUST conservar la consulta, los filtros, el
  orden y la posición de desplazamiento.
- **FR-044**: Al cambiar a otro destino de la barra inferior y volver, la pantalla MUST conservar la
  consulta, los filtros y el orden.
- **FR-045**: La consulta y los filtros MUST sobrevivir a la muerte del proceso.

#### Privacidad

- **FR-046**: El sistema MUST NOT registrar el texto que la persona escribe en ninguna búsqueda, ni
  en analítica ni en trazas de error. Una consulta escrita a mano puede contener datos personales.

### Key Entities

- **Consulta de búsqueda**: lo que define una búsqueda global — el texto introducido, los filtros
  activos (fecha desde, fecha hasta, sección, subsección, organismo) y el orden elegido. Es lo que se
  conserva al abrir un resultado y volver.
- **Texto buscable de una publicación**: la forma normalizada —sin tildes, en minúsculas, sin
  espacios sobrantes— del título, el organismo, la jerarquía de organismos, la referencia y el nombre
  de la sección y la subsección de una publicación. Es contra lo que se compara el texto introducido.
  Se deriva de la publicación; no lo escribe la persona y ninguna sincronización lo contradice.
- **Resultado de búsqueda**: una publicación devuelta por una consulta, mostrada con la misma tarjeta
  que en Inicio y con las mismas acciones.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Escribir en la búsqueda rápida recorta la lista **sin salir de la pantalla de Inicio**
  y sin ninguna petición de red: se comprueba con el dispositivo en modo avión.
- **SC-002**: Escribir `pielagos`, sin tilde y en minúsculas, encuentra `AYUNTAMIENTO DE PIÉLAGOS`
  en las dos búsquedas.
- **SC-003**: Escribir `contratacion` en el buscador global devuelve publicaciones de la sección
  `Contratación` cuyo título no contiene esa palabra.
- **SC-004**: Una persona que ya tenía la aplicación instalada encuentra, tras actualizar,
  publicaciones que había descargado antes de la actualización.
- **SC-005**: Cerrar la búsqueda rápida devuelve la pantalla al mismo boletín, la misma sección y la
  misma posición de lectura que antes de abrirla, en el 100 % de los casos.
- **SC-006**: Desde una búsqueda rápida sin resultados, llegar al buscador global con el término ya
  escrito y ejecutado cuesta **un solo toque** y **cero pulsaciones de teclado**.
- **SC-007**: Quitar un filtro, o quitarlos todos, conserva el texto escrito en el 100 % de los
  casos.
- **SC-008**: Con el archivo lleno de un año de boletines, una búsqueda global devuelve sus
  resultados en **menos de un segundo** desde que se deja de escribir.
- **SC-009**: Abrir un resultado y volver conserva consulta, filtros, orden y posición de
  desplazamiento en el 100 % de los casos.
- **SC-010**: Buscar `100%` devuelve solo publicaciones que contienen `100%`, nunca el archivo
  entero.
- **SC-011**: Ninguna búsqueda, rápida o global, genera tráfico de red.
- **SC-012**: El texto de ninguna consulta aparece en los eventos de analítica ni en las trazas de
  error.

## Fuera de alcance

Se dice en voz alta para que no se lea como un olvido:

- **Filtro por municipio.** El servicio no publica el municipio y deducirlo del organismo solo
  funcionaría con los ayuntamientos, dejando fuera juntas vecinales, mancomunidades y todo lo
  autonómico. Queda **aplazado**, no descartado, igual que se hizo con la lectura sin conexión en la
  feature 005. Mientras tanto, el organismo se busca, así que escribir el nombre del municipio ya
  devuelve lo de su ayuntamiento.
- **Ordenación por relevancia.** Solo `Más recientes` y `Más antiguas`. Una puntuación de relevancia
  hay que poder explicarla, y con el orden cronológico basta para el volumen que maneja esta
  aplicación.
- **Resaltado de las coincidencias** dentro del texto del resultado (apartado 17.2 del documento de
  diseño) y **fragmento de contexto** alrededor de la coincidencia. La tarjeta estándar ya muestra el
  título completo hasta cuatro líneas, que es donde está el término.
- **Búsquedas recientes** y bloque `Explorar por` del estado inicial (apartado 17.1 del documento de
  diseño).
- **Tarjeta compacta** para resultados: se reutiliza la tarjeta estándar, como ya hizo Guardados.
- **Selector de fecha para Inicio** (apartado 15 del documento de diseño). La búsqueda rápida respeta
  el contexto que haya, pero no lo amplía.
- **Buscar dentro del documento PDF**, que la feature 004 ya dejó explícitamente fuera.

## Assumptions

- La búsqueda rápida opera sobre lo que la pantalla de Inicio ya tiene cargado; no se define un
  contexto de fecha porque la aplicación todavía no permite elegirla.
- El mínimo de dos caracteres para la búsqueda global es una decisión de esta especificación: una
  sola letra devolvería una porción enorme del archivo sin ayudar a nadie. La búsqueda rápida filtra
  desde el primer carácter porque solo recorta lo que ya está en pantalla.
- El tope de 300 resultados es una decisión de esta especificación, tomada para que la pantalla no
  prometa una lista completa que no muestra. Si con el uso resulta corto o largo, se cambia con su
  motivo.
- «Referencia» se entiende como el identificador del anuncio en el servicio, que es el que la ficha
  del detalle ya muestra con ese nombre.
- El comportamiento de guardar desde un resultado es exactamente el de la feature 005: marca, no
  descarga, y no conserva el documento para leer sin conexión.
- Las publicaciones nunca se borran del dispositivo —regla vigente desde la feature 003—, así que el
  archivo sobre el que se busca solo crece.
- La tarjeta de resultado es la misma que la de Inicio y Guardados, con sus acciones de abrir,
  guardar y compartir.
