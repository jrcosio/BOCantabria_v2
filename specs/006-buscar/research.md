# Research: Buscar

**Feature**: `006-buscar` | **Fase**: 0 | **Fecha**: 2026-08-31

Cada decisión responde a una pregunta que había que cerrar antes de diseñar. Se registra qué se
eligió, por qué, y qué se descartó, para que dentro de seis meses la alternativa no vuelva a
plantearse desde cero.

---

## Cómo se busca

### D-001 — Normalizar al escribir, en una columna, y no al consultar

**Decisión**: cada fila de `publications` guarda una columna `search_text` con su contenido buscable
ya normalizado —minúsculas, sin tildes, sin espacios sobrantes—. La consulta se normaliza igual y se
compara con `LIKE '%…%'`.

**Motivo**: `LIKE` de SQLite solo pliega mayúsculas para ASCII y **nunca** pliega diacríticos. La
única forma de conseguirlo en la propia base de datos sería una colación con ICU, que Android no
incluye, o una función SQL definida por la aplicación, que Room no expone. Normalizar al escribir
resuelve el problema donde es barato: una vez por anuncio, cuando entra, en lugar de una vez por
fila y por pulsación de tecla.

**Alternativas descartadas**:

- **FTS4 con el tokenizador `unicode61`**, que sí ignora diacríticos. Descartada porque **cambia la
  semántica**: un índice de texto completo busca por palabras y prefijos, así que `ielagos` dejaría
  de encontrar `Piélagos` y FR-004 quedaría incumplido. Añade además una tabla en la sombra que hay
  que mantener sincronizada, en un proyecto cuya regla más firme es que aquí no se borra nada.
- **Cargar el archivo entero en memoria y filtrar en Kotlin**. Funciona con las dos mil filas de
  hoy y deja de funcionar cuando el archivo lleve un año creciendo. Y la especificación pide
  explícitamente que la búsqueda global se resuelva contra el almacén.
- **Normalizar solo la consulta y aceptar que las tildes cuenten**. Es exactamente lo que el
  requisito FR-002 prohíbe: `pielagos` tiene que encontrar `Piélagos`.

---

### D-002 — Qué entra en el texto buscable

**Decisión**: `search_text` concatena, normalizados y separados por espacio:

1. el **título** completo —que ya empieza por el organismo, con la forma `ORGANISMO: texto`—,
2. el **organismo**,
3. la **jerarquía de organismos** (`organization_path`),
4. la **referencia** (`blob_id`),
5. el **nombre** de la sección y el de la subsección.

**Motivo**: cubre los cinco campos que pide FR-025 sin inventar ninguno. Los puntos 1 y 2 son los
que hacen que la búsqueda rápida y la global coincidan en lo que encuentran. El punto 5 es
imprescindible: la tabla guarda `section_code = "3"`, no `Contratación administrativa`, así que sin
él escribir `contratacion` no devolvería nada. El nombre sale del catálogo compilado, que
`PublicationRepositoryImpl` **ya tiene inyectado** como `BocSectionRepository`.

**Alternativas descartadas**:

- **Indexar `titleWithoutIssuer` además del título**: es un derivado del título, así que no añade
  ni una coincidencia y sí duplica el tamaño de la columna.
- **Indexar `raw_categories`**: viene en crudo del servicio y con los componentes permutados en el
  feed 4.3. Meterlo haría que una búsqueda por sección encontrara publicaciones mal clasificadas
  por la propia fuente, que es justo lo que la feature 003 decidió no hacer.
- **Una tabla aparte con el texto buscable**: obliga a una unión en cada consulta y a mantener dos
  filas por anuncio, a cambio de nada.

---

### D-003 — Normalización: NFD, quitar marcas, minúsculas de `Locale.ROOT`

**Decisión**: `java.text.Normalizer.normalize(texto, NFD)` → eliminar todo `\p{Mn}` → `lowercase()`
con `Locale.ROOT` → colapsar los espacios consecutivos y recortar.

**Motivo**: `Locale.ROOT` a propósito. La columna se escribe y se consulta en el mismo dispositivo,
pero el idioma del sistema puede cambiar entre una cosa y otra, y hay idiomas —el turco, con su
`I`— donde `lowercase()` da otro resultado. Con `ROOT` el texto normalizado es el mismo siempre.

**Consecuencia que hay que documentar en el código**: la `ñ` se descompone en `n` + tilde, y quitar
las marcas la deja en `n`. `España` se indexa como `espana`. **Es lo que se quiere**: `espana`,
`españa` y `EspaÑa` normalizan igual y las tres encuentran lo mismo.

**Alternativa descartada**: tratar la `ñ` como letra propia y respetarla. Obligaría a una
sustitución a mano antes de descomponer, y haría que escribir `espana` dejara de encontrar `España`
—que es precisamente el caso que motiva la feature—.

---

### D-004 — Base de datos a la versión 3 con migración automática

**Decisión**: `publications` gana `search_text TEXT NOT NULL DEFAULT ''`. `BocDatabase` pasa a
`version = 3` con `AutoMigration(from = 2, to = 3)`. El `3.json` se versiona en `app/schemas/`.

**Motivo**: añadir una columna con valor por defecto es exactamente el caso que la migración
automática resuelve entero contra el esquema exportado. Escribir la sentencia a mano reproduciría lo
que el compilador ya sabe generar, con el riesgo añadido de un hash de identidad que no cuadre.

**Alternativa descartada, y hay que decirlo en voz alta**: `fallbackToDestructiveMigration()`.
Pasaría la puerta de compilación y **vaciaría el boletín almacenado de quien ya tiene la aplicación
instalada**. No entra aquí ni como último recurso. `bocDatabase()` sigue siendo un `.build()` limpio.

---

### D-005 — El relleno de las filas anteriores corre dentro de la sincronización

**Decisión**: tras la migración, `search_text = ''` marca una fila escrita por una versión anterior.
`PublicationRepositoryImpl.refresh()`, justo después de sincronizar las fuentes, pide lotes de 500
filas con `search_text = ''`, calcula su texto buscable y las actualiza, hasta que no queda ninguna.

**Motivo**: sin relleno, una sincronización solo refresca los últimos cien anuncios de cada fuente,
así que **el archivo anterior sería inbuscable para siempre**. Y es el fallo con peores
consecuencias de la feature porque **no se ve en una instalación limpia**: solo aparece en el móvil
de quien ya tenía la aplicación. Usar `search_text = ''` como marcador es fiable —`title` nunca está
en blanco, así que ninguna fila escrita por esta versión puede tenerlo vacío—, hace el proceso
idempotente y no necesita ninguna bandera guardada. En una instalación nueva cuesta un `SELECT …
LIMIT 500` que devuelve cero filas.

**Alternativas descartadas**:

- **Rellenar dentro de la migración**: SQLite no sabe quitar tildes. Sería el sitio natural y no se
  puede.
- **Rellenar al arrancar**: la portada ya tiene un mínimo de 1,2 s y el relleno es proporcional al
  archivo. Retrasar el arranque por algo que solo hace falta en Buscar es el peor reparto posible.
- **Rellenar en la primera búsqueda**: paga la latencia justo en el momento en que alguien está
  mirando la pantalla y esperando.
- **Una bandera en preferencias**: un estado más que guardar, que mantener sincronizado y que puede
  quedarse a medias si el proceso muere durante el relleno. El marcador en la propia columna no.

---

### D-006 — Sin índice sobre `search_text`

**Decisión**: la columna no lleva índice.

**Motivo**: un `LIKE '%…%'` con comodín inicial **no puede usar un índice B-tree**; SQLite hará
recorrido completo de todas formas. Un índice ahí solo añadiría escrituras en cada sincronización y
tamaño al fichero. Los índices que sí sirven —`publication_date`, `section_code`,
`subsection_code`— ya existen y son los que recortan el conjunto antes del `LIKE`.

**Alternativa descartada**: índice sobre `search_text`. Peso muerto medible y ninguna consulta más
rápida.

---

### D-007 — Un tercer DAO, de solo lectura

**Decisión**: `PublicationSearchDao` con las consultas de búsqueda y la de organismos distintos. Las
dos sentencias del **relleno** van en `PublicationDao`, no aquí.

**Motivo**: `PublicationDao` es el de la sincronización y su cabecera enuncia la regla que mantiene
entero el boletín almacenado —no hay borrado, el `UPDATE` es una lista blanca—; esa regla se sostiene
porque el fichero se lee de un tirón. El precedente es `SavedPublicationDao`, que se separó por lo
mismo. Y la línea queda limpia: **`PublicationDao` escribe lo que se deriva de la fuente**
—incluido el relleno, que es exactamente eso—, **`PublicationSearchDao` solo lee**.

**Alternativa descartada**: ampliar `PublicationDao` con todo. Un fichero menos, una invariante peor
contada.

---

### D-008 — Dos sentencias de búsqueda, una por sentido de ordenación

**Decisión**: `searchNewestFirst` y `searchOldestFirst`, con el mismo `WHERE` y el `ORDER BY`
invertido.

**Motivo**: Room no permite parametrizar la dirección de un `ORDER BY`. Las salidas eran un
`@RawQuery` —que renuncia a la verificación de la consulta en tiempo de compilación, que es media
razón para usar Room— o un `ORDER BY CASE WHEN :asc THEN … END ASC, CASE WHEN NOT :asc THEN … END
DESC`, que nadie vuelve a leer con gusto. Duplicar quince líneas de SQL explícito es más barato que
cualquiera de las dos.

**Nota**: los **tres** términos de ordenación se mantienen en las dos —fecha, identificador numérico
y clave—, que es lo que hace que dos ejecuciones coincidan aunque las diecinueve fuentes respondan
en distinto orden. Es lo que exige FR-042.

---

### D-009 — `search_text` entra en la lista blanca del `UPDATE`; `saved_at` no

**Decisión**: `search_text` se añade a `PublicationDao.updateColumns`. `saved_at` y `first_seen_at`
siguen fuera.

**Motivo, y hay que decirlo alto porque es justo el `UPDATE` que la guía del proyecto protege**: la
lista blanca existe para que **un dato de la persona** sobreviva a una sincronización posterior.
`search_text` no es un dato de la persona: se deriva del título, el organismo y la sección que la
fuente publica, así que cuando la fuente corrige un título el texto buscable tiene que corregirse
con él. Dejarlo fuera significaría que un anuncio con el título corregido se seguiría encontrando
solo por el título viejo.

**Verificación**: `SavedPublicationDaoTest` **no se toca**. Si se pone roja tras este cambio, es que
`saved_at` se ha colado en la sentencia, y el arreglo es quitarlo, no ajustar la prueba.

---

### D-010 — Escapado de `%`, `_` y `\`

**Decisión**: antes de construir el patrón, la consulta normalizada escapa `\`, `%` y `_`
anteponiendo `\`, y el SQL termina en `ESCAPE '\'`.

**Motivo**: sin esto, escribir `100%` devuelve el archivo entero y escribir `a_b` encuentra `axb`.
Es un fallo silencioso —no falla, miente— y el tipo de cosa que nadie prueba a mano.

**Dónde vive**: en `data/source/local`, no en `core/util`. El escapado es una regla de SQL; la
normalización no. Mezclarlas obligaría a que la búsqueda rápida, que no toca SQL, arrastrara barras
invertidas en el texto con el que compara.

---

## Alcance y límites

### D-011 — Mínimo de dos caracteres en la global, uno en la rápida

**Decisión**: la búsqueda global no se lanza hasta el segundo carácter; la rápida filtra desde el
primero.

**Motivo**: no son la misma operación. La global recorre todo el archivo y con una sola letra
devolvería una porción enorme que no ayuda a nadie y que cuesta recorrer. La rápida solo recorta una
lista que ya está en memoria y en pantalla, donde una letra sí es un filtro útil y el coste es nulo.

---

### D-012 — El filtrado en memoria es un caso de uso, no un método del `ViewModel`

**Decisión**: `FilterPublicationsUseCase(items, texto): List<Publication>`, puro, sin corrutinas.

**Motivo**: la constitución prohíbe la lógica en los componibles, y una función privada del
`ViewModel` dejaría la regla de coincidencia sin prueba aislada. Como caso de uso tiene su fichero
de prueba obligatorio por la regla de Konsist, y el día que otra pantalla necesite lo mismo está
donde tiene que estar.

**Alternativa descartada**: una función privada en `HomeViewModel`. Un fichero menos y una regla sin
prueba propia.

---

### D-013 — El puente es un argumento de ruta, no un estado compartido

**Decisión**: `Route.Search` pasa de `data object` a `data class Search(val query: String? = null)`.
`SearchViewModel` siembra su consulta inicial desde el `SavedStateHandle`, que es donde la ruta
tipada deja el argumento.

**Motivo**: es el mismo mecanismo que ya usa `Route.Home` para la sección, y por la misma razón
escrita en su comentario: sobrevive a la muerte del proceso sin una línea de código. Un estado
compartido entre pantallas rompería que la ruta sea la única fuente de verdad de la navegación.

**El riesgo real, y su mitigación**: la barra inferior navega con `popUpTo(start) { saveState =
true }` y `restoreState = true`. Si el puente navegara igual, el estado guardado de la pestaña
Buscar **pisaría el argumento** y el término traspasado se perdería —justo en el caso de alguien que
ya había usado Buscar antes—. Por eso el puente navega **sin** `restoreState`, con `launchSingleTop
= true`, y hay una prueba instrumentada (`SearchHandoffTest`) que recorre exactamente esa secuencia.

---

### D-014 — El estado de Buscar vive en el `SavedStateHandle`

**Decisión**: consulta, filtros y orden se guardan y se leen del `SavedStateHandle` mediante
`getStateFlow`, y **el texto usa la clave `query`**, la misma con la que la ruta tipada deja el
argumento del puente. La posición de desplazamiento va en `rememberSaveable`.

**Motivo**: no es una precaución de más. `popUpTo(saveState = true)` **destruye el modelo de
pantalla** al cambiar de pestaña; guardar el estado solo en el `ViewModel` incumpliría FR-044 —ir a
Inicio y volver— y FR-045 —muerte del proceso—. Y una sola clave por dato sirve para las dos cosas:
la ruta deja ahí el argumento del puente y el `ViewModel` escribe encima lo que la persona teclea.

**La clave importa**: `Route.Search` declara la propiedad `query`, así que el argumento aterriza en
el `SavedStateHandle` bajo ese nombre. Si el modelo persistiera el texto bajo otra clave —`text`, por
ejemplo, que es como se llama el campo de `SearchQuery`—, la siembra del puente no encontraría nada y
fallaría **sin error**: se llegaría a Buscar con el campo vacío. Una clave, `query`, y el mapeo a
`SearchQuery.text` en el modelo.

**Nota**: el caso de abrir un resultado y volver (FR-043) no depende de esto. El detalle vive en el
grafo **exterior**, así que la entrada de Buscar no se saca de la pila y el modelo sigue vivo.

---

### D-015 — En `HomeViewModel`, agrupar el estado local en lugar de añadir un sexto flujo

**Decisión**: `shareState`, `saveFailed` y el estado de la búsqueda rápida se funden en un único
`MutableStateFlow` de estado local. El `combine` pasa de cinco argumentos a cuatro.

**Motivo**: el comentario que ya hay en `HomeViewModel` avisa de que un sexto flujo obliga a la
forma de lista de `combine`, que cambia el tipo del bloque y lo vuelve ilegible. Agrupar lo que ya
es estado local de la pantalla —y que hoy está repartido en dos flujos por razones históricas— baja
la aridad en vez de subirla.

---

### D-016 — «Sin coincidencias» es un estado de contenido propio

**Decisión**: `HomeContentState` gana `NoSearchResults(query)`.

**Motivo**: es lo que hace que el `when` de la pantalla siga siendo exhaustivo y que el compilador
avise si alguien añade un caso. Y separa de raíz dos cosas que dicen lo contrario: `Empty` es «esta
sección no tiene publicaciones»; `NoSearchResults` es «hay publicaciones, pero ninguna coincide», y
es el único que ofrece el puente.

---

### D-017 — Tope de 300 resultados, dicho en pantalla

**Decisión**: la consulta pide 301 filas; si vuelven 301, se muestran 300 y la pantalla dice que hay
más. El acarreo lo hace `SearchPublicationsUseCase`, que devuelve `SearchResults(items,
isTruncated)`.

**Motivo**: pedir una más es lo que distingue «hay exactamente 300» de «hay más de 300» sin una
segunda consulta de recuento. Y decirlo en pantalla es lo que evita que la lista mienta: una lista
recortada en silencio se lee como una lista completa.

**Alternativa descartada**: paginación. El proyecto no tiene la biblioteca de paginación y meterla
para una pantalla sería una dependencia nueva y un modelo de datos distinto; con filtros, 300 es de
sobra.

---

### D-018 — Fechas con el selector de Material 3

**Decisión**: los campos «desde» y «hasta» abren el `DatePickerDialog` de Material 3, que ya está en
el BOM de Compose.

**Motivo**: cero dependencias nuevas y el comportamiento que la gente espera. Esto **no** es el
selector visual de fecha del apartado 15 del documento de diseño —el de navegar entre boletines por
día—, que sigue fuera de alcance: aquí solo se acotan dos extremos de un filtro.

**Validación de FR-038**: la acción `Aplicar filtros` queda inhabilitada mientras «desde» sea
posterior a «hasta», y el selector de «hasta» no ofrece días anteriores a «desde». Se impide la
combinación en la interfaz, no con una excepción: un `require` en el modelo de dominio convertiría
un error de manejo en un cierre de la aplicación.

---

### D-019 — La lista de organismos sale de lo almacenado y se puede filtrar escribiendo

**Decisión**: `SELECT DISTINCT issuer FROM publications WHERE issuer IS NOT NULL ORDER BY issuer`,
mostrada dentro de la hoja con su propio campo de filtro.

**Motivo**: son cientos —cada ayuntamiento, cada consejería, cada juzgado—, así que un desplegable
plano es inservible. Y sacarla de lo almacenado, en lugar de un catálogo fijo, significa que nunca
ofrece un organismo que no tenga ni un anuncio detrás.

---

### D-020 — Ninguna dependencia nueva

**Decisión**: `gradle/libs.versions.toml` no se toca.

**Motivo**: todo lo que hace falta está: `ModalBottomSheet`, `DatePicker`, `FilterChip`,
`OutlinedTextField` y `DropdownMenu` vienen de Material 3; `debounce` y `flatMapLatest`, de
corrutinas; `Normalizer`, de la biblioteca estándar de Java. **Si en la revisión aparece una
coordenada nueva, hay que preguntar por qué.**

---

## Presentación

### D-021 — Telemetría sin el texto de la consulta

**Decisión**: `trackScreenView("search")` en el `init` del modelo de pantalla, y un evento
`boc_search` con dos parámetros: si había filtros activos y un **tramo** del número de resultados
(`0`, `1-9`, `10-99`, `100+`). Nunca el texto.

**Motivo**: la constitución prohíbe registrar datos personales, y una consulta escrita a mano puede
llevarlos —un nombre, una matrícula, una dirección—. El tramo responde a la única pregunta que
importa —«¿la gente encuentra algo?»— sin guardar nada de nadie.

---

### D-022 — Tres vectores nuevos

**Decisión**: `ic_close`, `ic_filter_list` e `ic_sort`, dibujados como los otros diecinueve: trazado
de Material Symbols sin modificar, lienzo de 960, `android:fillColor` como marcador que Compose tiñe
en el punto de uso.

**Motivo**: el conjunto básico de iconos de Material **no está en el classpath** con este BOM, y
cerrar, filtrar y ordenar no tienen equivalente entre los que ya hay. Reutilizar `ic_arrow_back`
como aspa confundiría más de lo que ahorra.

---

### D-023 — Los filtros en hoja inferior, los chips en la pantalla

**Decisión**: la hoja `Filtrar resultados` con los selectores y las acciones `Limpiar` y `Aplicar
filtros`; en la pantalla, los chips de lo que está activo, cada uno con su aspa, y `Limpiar todo`.

**Motivo**: es lo que ya dice el apartado 17.3 del documento de diseño, y además une las dos cosas
que la imagen de referencia del propietario pedía —ver los filtros aplicados sin abrir nada— sin el
coste que tenía: un panel de seis selectores siempre visible empuja los resultados fuera de la
pantalla, y los resultados son lo que se ha venido a ver.

**Consecuencia**: la barra superior lleva el título `Buscar` **sin flecha atrás ni
menú de tres puntos**, al contrario que la imagen. Es un destino de la barra inferior, no una
pantalla apilada, y una flecha atrás en un destino de primer nivel no tiene a dónde ir.

---

### D-024 — La tarjeta de resultado es la estándar

**Decisión**: `PublicationCard`, la misma de Inicio y Guardados, sin parámetro de densidad.

**Motivo**: ya muestra organismo, título, sección, fecha, marcador y compartir, que es exactamente
lo que pide FR-029 y lo que dibuja la imagen de referencia. La variante compacta del apartado 12.2
del documento de diseño queda aplazada por el mismo motivo que la aplazó la feature 005: añadir un
parámetro de densidad para diferenciar listas que muestran lo mismo es más superficie de la que la
diferencia justifica. Si algún día la búsqueda la necesita, se añade entonces y con su motivo.

**Consecuencia aceptada**: **no hay resaltado de coincidencias** (apartado 17.2 del diseño). El
título completo se ve hasta cuatro líneas, que es donde está el término buscado.
