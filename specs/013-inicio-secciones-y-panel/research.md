# Investigación — Feature 013: Inicio y panel lateral

Decisiones D-501 a D-512. Formato: **Decisión / Razón / Alternativas descartadas**.

Esta feature no tiene incógnitas técnicas: no hay biblioteca nueva, ni servicio ajeno, ni dato que
persistir. Lo que se investiga aquí es **dónde poner cada cosa** para que el código siga contando la
verdad dentro de un año.

---

## D-501 · La segunda fila de chips se deriva de la selección; no se recuerda

**Decisión.** La lista de subsecciones que la pantalla dibuja se calcula a partir de la selección
vigente —`HomeSelection`, que llega como argumento de navegación— y no de ningún estado de expansión.
Si la selección es `Section("2", …)` y la sección 2 tiene hijas, hay segunda fila; en cualquier otro
caso, no la hay.

**Razón.** Es lo único que hace que FR-015 (sobrevivir al giro y a la muerte del proceso) se cumpla
**sin escribir una línea**. La selección ya viaja en `Route.Home(sectionCode, subsectionCode)` y
`HomeViewModel` ya la reconstruye desde el `SavedStateHandle`. Además elimina de raíz una clase entera
de defectos: no puede haber una fila desplegada que no corresponda con la lista que se está viendo,
porque las dos salen del mismo sitio.

**Alternativas descartadas.**

- *Un `expandedSectionCode` en `HomeUiState`.* Habría que ponerlo al navegar, limpiarlo al volver al
  boletín del día, guardarlo en el `SavedStateHandle` y restaurarlo. Cuatro sitios donde sincronizar
  dos cosas que siempre valen lo mismo. Y el día que se desincronizasen, el síntoma sería una fila de
  subsecciones de una sección que no se está viendo.
- *Replicar el modelo del panel lateral* (`toggled: Set<String>`, chevrón, `AnimatedVisibility`). Ahí
  tiene sentido porque el panel muestra **las nueve secciones a la vez** y hace falta decidir cuáles
  están abiertas. En los chips solo hay una sección seleccionada por definición; un conjunto sería un
  conjunto de como mucho un elemento.

---

## D-502 · Tocar el chip de una sección con subsecciones navega **y** despliega

**Decisión.** Un solo toque hace las dos cosas: la lista pasa a la sección completa y aparece la
segunda fila. No hay un modo «solo desplegar».

**Razón.** Decisión del propietario entre las dos opciones que se le plantearon. El caso común es
querer ver la sección; afinar es la excepción. Cobrar dos toques por el caso común para ahorrar uno en
el raro es el reparto equivocado. Y como el chip ya navegaba, el comportamiento anterior se conserva
íntegro: lo que aparece es información adicional, no un cambio de significado del gesto.

**Alternativas descartadas.** *Desplegar sin navegar*, más explícito pero con dos toques siempre.
*Un chevrón dentro del chip* que separe «ver» de «desplegar»: mete dos objetivos táctiles en un chip de
32 dp de alto y es exactamente lo que las guías de accesibilidad desaconsejan.

---

## D-503 · «Toda la sección» y «Boletín de hoy» los pone la vista, no el modelo de pantalla

**Decisión.** `HomeViewModel` entrega `SectionChip` con lo que sabe el dominio —código, etiqueta corta,
grupo de color, si está seleccionada—. Las dos entradas cuya etiqueta es copia de interfaz las añade
`SectionFilterChips` con `stringResource`.

**Razón.** Es la regla que el propio fichero ya documenta para el chip actual: «un modelo de pantalla
que busca un recurso de cadena es un modelo que ha dejado de poder probarse sin dispositivo». Mantener
la misma línea evita que el fichero acabe explicando dos criterios contradictorios.

**Alternativas descartadas.** Inyectar un proveedor de cadenas en el modelo de pantalla: resuelve el
problema de la prueba pero añade una indirección para dos literales.

---

## D-504 · La primera fila sigue marcando la sección padre mientras hay una subsección elegida

**Decisión.** Sin cambio: `HomeViewModel.buildChips()` ya marca la sección cuando el código
seleccionado empieza por `"<código>."`. Se conserva tal cual y FR-012 queda cumplido por código que ya
existe.

**Razón.** Es la lectura correcta —estar en 2.2 es estar en 2— y ya está probada.

---

## D-505 · La segunda fila se distingue por estilo, no por un separador

**Decisión.** Los chips de subsección usan una variante visual secundaria del mismo `FilterChip`
—fondo `surfaceSoft` en reposo y tipografía un punto menor—, con la separación vertical del sistema de
espaciado. Sin línea divisoria, sin caja, sin fondo propio para la fila.

**Razón.** FR-017 pide que se lea como dependiente de la de arriba. Una jerarquía se comunica con peso
y color; un divisor comunica lo contrario, que son dos cosas distintas. Y el panel lateral ya usa
`surfaceSoft` para el bloque de subsecciones: es el mismo vocabulario visual en los dos sitios donde
aparecen subsecciones.

**Alternativas descartadas.** Sangrar la segunda fila —se pierde al desplazarla—. Un `TabRow`
secundario —no se desplaza y no cabe con cinco subsecciones—.

---

## D-506 · `MainShell` no se toca para la segunda fila

**Decisión.** El cableado existente sirve sin cambios. `HomeScreen` recibe
`onSelectSection: (String?) -> Unit`; `MainShell` resuelve
`sections.firstOrNull { it.code == code }` y `openSection()` ya distingue `isTopLevel` de hija y
construye `Route.Home(sectionCode = parentCode, subsectionCode = code)`.

**Razón.** Se comprobó antes de planificar: un código `"2.1"` recorre hoy ese camino correctamente,
porque es exactamente el que usa el panel lateral. FR-018 —que llegar desde los chips y desde el panel
den el mismo resultado— se cumple **por construcción**, no por coincidencia: es literalmente la misma
función.

---

## D-507 · El rótulo de la fecha se decide con `isTodaysBulletin`, que ya existe

**Decisión.** `BulletinHeader` elige entre dos cadenas según `header.isTodaysBulletin`, propiedad que
`BulletinHeaderData` ya expone (`sectionName == null`). No se añade ningún campo al modelo.

**Razón.** El modelo ya distingue exactamente los dos casos que FR-005 quiere distinguir. Añadir un
enumerado de rótulo sería duplicar una condición que ya está resuelta, y en el sitio equivocado: el
dominio no debe saber qué se rotula.

**Alternativas descartadas.** Pasar la `HomeSelection` al componible —ya se pasa el `header`, que es
justo el objeto hecho para esto—.

---

## D-508 · Sin fecha no hay rótulo

**Decisión.** El rótulo vive **dentro** del `header.date?.let { … }` que ya envuelve la fila de la
fecha. Si no hay fecha, no se dibuja nada.

**Razón.** FR-006. Un «Edición del» huérfano en la primera ejecución sería peor que la fecha desnuda de
hoy. Y el mecanismo ya está: solo hay que no sacarlo del bloque.

---

## D-509 · El rótulo va con la fecha, en una sola cadena con marcador

**Decisión.** Dos recursos con `%1$s` —`Edición del %1$s` y `Última publicación: %1$s`— formateando la
fecha con el `DateTimeFormatter` español largo que ya vive en `BulletinHeader`.

**Razón.** Una sola cadena por caso deja la frase entera en manos de quien la escribe, incluida la
preposición. Partirla en «rótulo» + «fecha» obligaría a que el castellano encajase en una plantilla
inglesa, que es como nacen los «Edición de el 4 de septiembre».

**Nota heredada de `CLAUDE.md`.** Ese formateador está **duplicado** como privado en `HomeScreen.kt`
para las tarjetas. Esta feature no lo unifica —sería trabajo ajeno a lo pedido— pero tampoco crea un
tercero: se usa el que ya está en el fichero que se está tocando.

---

## D-510 · La flecha de recoger es la de volver atrás, colocada al final de la fila

**Decisión.** El icono es `ic_arrow_back`, el mismo glifo que usan las seis pantallas con barra de
volver, colocado en el extremo final de la cabecera del panel. Apunta a la izquierda, que es hacia
donde el panel se retira.

**Razón.** Lo pidió así el propietario —«una flecha como la de volver atrás», «a la derecha para que se
entienda lo que hace»— y además es correcto: el vector de la flecha y el vector del movimiento
coinciden. Se comprobó el inventario de vectores: **no existen** `ic_chevron_left`, `ic_menu_open` ni
`ic_arrow_forward`, y añadir un vector nuevo tendría que sortear la trampa de los dos lienzos de
Material Symbols que `CLAUDE.md` documenta —la mitad de los símbolos llegan en escala 24 y sin
`viewBox`, y copiar el equivocado no falla: simplemente no dibuja nada—.

**Alternativas descartadas.** `ic_close`: dice «cerrar», que en esta aplicación es lo que hace la equis
de un campo de texto y de una hoja inferior; para un panel que se retira lateralmente dice menos que
una flecha.

---

## D-511 · La cabecera del panel reutiliza el escudo y el título de la barra superior

**Decisión.** Escudo `ic_escudo_cantabria` y la cadena `app_bar_title` («BOC Cantabria»), con el mismo
tratamiento de color que en `HomeTopBar`.

**Razón.** Es la misma identidad; una segunda variante sería una segunda cosa que mantener sincronizada
con la primera.

**Trampa que hay que respetar** (`CLAUDE.md`, pagada en la feature 002): un `Image` con solo `height`
se ajusta al **ancho intrínseco** del vector —32 dp— y la altura pedida no se aplica; el escudo sale
diminuto. Hay que fijar también `aspectRatio(79f / 137f)`, como hace `SplashScreen.Emblem()`.
`HomeTopBar` no lo hace y le funciona por casualidad de proporciones; en la cabecera del panel, con
más alto disponible, no se puede confiar en eso.

---

## D-512 · Al retirar el filtro del panel se retira también su lógica, no solo su campo

**Decisión.** Desaparecen `SectionsUiState.query`, `SectionsViewModel.onQueryChanged`, el predicado
`BocSection.matches`, la construcción condicional de `rows` y la auto-apertura de secciones cuyas hijas
coincidían. `stateFor` queda reducido a componer las nueve filas con sus hijas.

**Razón.** Un campo retirado de la pantalla cuya lógica sigue viva es una función que nadie llama y que
la siguiente persona tendrá que averiguar si importa. La auto-apertura, en particular, solo existía
**porque** había un filtro: un resultado escondido tras un chevrón cerrado es un resultado que nadie
encuentra. Sin filtro, no tiene a qué referirse.

**Consecuencia declarada.** Las pruebas de `SectionsViewModelTest` y `SectionsDrawerTest` que
describían ese filtrado se retiran **con la funcionalidad**, no para pasar la build. La especificación
lo recoge como requisito propio (FR-024) y como requisito superado de la feature 003.

**Lo que NO se retira.** `SectionsUiState.selection` y `onSelectionChanged` se quedan: no tienen nada
que ver con el filtro y siguen siendo el canal por el que el panel —que vive por encima del anfitrión
de navegación— se entera de qué hay elegido.
