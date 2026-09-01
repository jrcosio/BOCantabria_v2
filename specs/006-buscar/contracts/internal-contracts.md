# Contratos internos: Buscar

**Feature**: `006-buscar` | **Fase**: 1 | **Fecha**: 2026-08-31

Esta aplicación no expone ninguna interfaz a terceros: no hay API pública, ni CLI, ni biblioteca. Lo
que sí tiene son **fronteras internas** que varias piezas comparten y que romper en silencio sale
caro. Esas son las que se fijan aquí, junto a las etiquetas de prueba y el contrato visual, que es
lo que la interfaz promete y lo que sus pruebas afirman.

---

## 1. Contratos de código

### 1.1. `core/util/SearchText`

```kotlin
object SearchText {
    fun normalise(raw: String): String
}
```

| Garantía | Detalle |
|---|---|
| Idempotente | `normalise(normalise(x)) == normalise(x)` |
| Independiente del idioma del sistema | Usa `Locale.ROOT`. El mismo texto da el mismo resultado en un móvil en español y en uno en turco |
| Nunca lanza | Con cadena vacía, con solo espacios y con solo diacríticos devuelve `""` |
| Sin diacríticos | `é→e`, `ñ→n`, `ü→u`, `ç→c` |
| Espacios colapsados | Ninguna secuencia de dos espacios sobrevive; no hay espacio inicial ni final |

**Quién la usa**: el constructor del texto buscable (`data`), el repositorio de búsqueda al preparar
el patrón (`data`) y el filtro de la búsqueda rápida (`domain`). Cambiarla cambia lo que se
encuentra en las dos búsquedas **y** deja el `search_text` ya escrito desalineado con la consulta:
si algún día se toca, hay que forzar un rellenado completo.

### 1.2. `data/source/local/PublicationSearchText`

```kotlin
internal fun buildSearchText(
    title: String, issuer: String?, organizationPath: List<String>,
    blobId: String?, sectionName: String?, subsectionName: String?,
): String
```

| Garantía | Detalle |
|---|---|
| Nunca devuelve cadena vacía | `title` no puede estar en blanco. **De esto depende que `search_text = ''` sea un marcador fiable de fila sin rellenar** |
| Salida ya normalizada | Pasa por `SearchText.normalise` |
| Los nulos se omiten | Sin huecos ni literales `null` en la salida |
| Un solo punto de verdad | La sincronización y el relleno llaman a esta misma función. No puede haber dos formas del texto buscable |

### 1.3. `data/source/local/PublicationSearchDao` — **solo lectura**

```kotlin
fun searchNewestFirst(pattern: String, sectionCode: String?, subsectionCode: String?,
                      issuer: String?, from: String?, to: String?, limit: Int): Flow<List<PublicationEntity>>
fun searchOldestFirst(...): Flow<List<PublicationEntity>>
fun observeIssuers(): Flow<List<String>>
```

| Garantía | Detalle |
|---|---|
| **Ninguna escritura** | Ni `INSERT`, ni `UPDATE`, ni `DELETE`. Una revisión que vea aparecer una aquí debe rechazarla |
| **Ningún borrado, en ningún DAO del proyecto** | La regla sigue en pie y esta feature no la roza |
| Filtro nulo = filtro ausente | `NULL` en un parámetro no recorta nada; no significa «campo vacío» |
| Orden determinista | Los tres términos —fecha, identificador numérico, clave— en las dos sentencias |
| Fechas como texto ISO | `>=` y `<=` comparan lexicográficamente, que aquí **es** cronológicamente |

### 1.4. `data/source/local/PublicationDao` — lo que cambia

| Cambio | Contrato |
|---|---|
| `search_text` entra en `updateColumns` | Es un dato derivado de la fuente. Si la fuente corrige un título, el texto buscable se corrige con él |
| `saved_at` y `first_seen_at` **siguen fuera** | Invariante de las features 003 y 005. `SavedPublicationDaoTest` es la prueba que la vigila y **no se toca en esta feature** |
| Dos sentencias nuevas de relleno | `withoutSearchText(limit)` y `setSearchText(key, text)`. Es la sincronización quien las usa |

### 1.5. `domain/repository/SearchRepository`

```kotlin
interface SearchRepository {
    fun search(query: SearchQuery, limit: Int): Flow<List<Publication>>
    fun observeIssuers(): Flow<List<String>>
}
```

| Garantía | Detalle |
|---|---|
| **Lista vacía es éxito** | «Sin coincidencias» no es un fallo. La distinción entre vacío y error la hace la presentación, como en todo el proyecto |
| El flujo no muere | Un fallo de lectura local se registra como no fatal y emite lista vacía. Una pantalla sin estado se lee como una aplicación colgada |
| Sin red | Ni una petición. Es la garantía de FR-026 y de SC-011 |
| Reemite al cambiar el almacén | Guardar o sincronizar mientras hay resultados en pantalla los actualiza sin volver a buscar |
| Devuelve modelos de dominio | La entidad de Room **no** cruza a `ui` |

### 1.6. Casos de uso

```kotlin
class SearchPublicationsUseCase(repository: SearchRepository) {
    operator fun invoke(query: SearchQuery): Flow<SearchResults>
    companion object { const val MAX_RESULTS = 300 }
}

class GetSearchIssuersUseCase(repository: SearchRepository) {
    operator fun invoke(): Flow<List<String>>
}

/** Puro y síncrono: ni corrutinas ni almacén. */
class FilterPublicationsUseCase {
    operator fun invoke(items: List<Publication>, text: String): List<Publication>
}
```

| Caso de uso | Garantías |
|---|---|
| `SearchPublicationsUseCase` | Pide `MAX_RESULTS + 1` al repositorio; entrega como mucho `MAX_RESULTS` y marca `isTruncated` cuando había más. Con `!query.isRunnable` no llama al repositorio |
| `FilterPublicationsUseCase` | Con texto en blanco devuelve la lista **tal cual**, misma instancia de elementos y mismo orden. Compara contra `title` e `issuer` normalizados. **Conserva el orden de entrada**: reordenar aquí contradiría el que fija el almacén |

### 1.7. `HomeViewModel` — eventos nuevos

```kotlin
fun onSearchOpened()
fun onSearchQueryChanged(query: String)
fun onSearchClosed()
```

| Garantía | Detalle |
|---|---|
| `onSearchClosed` limpia el texto | Volver a abrir la lupa empieza en blanco. Un filtro invisible que sigue aplicado es peor que ninguno |
| La selección no se toca | `HomeSelection` es un `val` leído del `SavedStateHandle`. Abrir, escribir y cerrar no navega |
| Cambiar de sección cierra la búsqueda | Se navega a otra `Route.Home`, y con ella llega un modelo nuevo. La búsqueda no puede sobrevivir a eso, y es lo correcto (FR-016) |
| Sin red | Ninguno de los tres eventos dispara sincronización |

### 1.8. `Route.Search`

```kotlin
@Serializable
data class Search(val query: String? = null) : Route
```

| Origen | Cómo se navega | Por qué |
|---|---|---|
| Barra inferior | `Route.Search()`, con `popUpTo(start) { saveState = true }`, `launchSingleTop`, `restoreState = true` | Es cambiar de pestaña: se recupera lo que hubiera |
| Puente desde Inicio | `Route.Search(texto)`, con `launchSingleTop` y **sin `restoreState`** | El término nuevo tiene que ganar al estado guardado (FR-020). Con restauración, el argumento se pierde |

---

## 2. Etiquetas de prueba

Las etiquetas son parte del contrato: las pruebas instrumentadas dependen de ellas y renombrar una
rompe pruebas sin romper la compilación.

### 2.1. Nuevas — pantalla Buscar

| Constante | Valor | Dónde |
|---|---|---|
| `TAG_SEARCH_SCREEN` | `search_screen` | Raíz de la pantalla |
| `TAG_SEARCH_FIELD` | `search_field` | Campo principal |
| `TAG_SEARCH_CLEAR` | `search_clear` | Aspa de borrar el texto |
| `TAG_SEARCH_RESULTS` | `search_results` | Lista de resultados |
| `TAG_SEARCH_EMPTY` | `search_empty` | Estado sin resultados |
| `TAG_SEARCH_INITIAL` | `search_initial` | Estado inicial, antes de dos caracteres |
| `TAG_SEARCH_TRUNCATED` | `search_truncated` | Aviso de «hay más de los que caben» |
| `TAG_SEARCH_COUNT` | `search_count` | Número de resultados |
| `TAG_SEARCH_SORT` | `search_sort` | Selector de orden |
| `TAG_SEARCH_FILTERS_OPEN` | `search_filters_open` | Acción que abre la hoja |
| `TAG_SEARCH_FILTERS_SHEET` | `search_filters_sheet` | La hoja |
| `TAG_SEARCH_FILTERS_APPLY` | `search_filters_apply` | `Aplicar filtros` |
| `TAG_SEARCH_FILTERS_CLEAR` | `search_filters_clear` | `Limpiar`, dentro de la hoja |
| `TAG_SEARCH_CHIPS` | `search_chips` | Fila de filtros activos |
| `TAG_SEARCH_CHIPS_CLEAR_ALL` | `search_chips_clear_all` | `Limpiar todo` |
| `searchChipTag(kind)` | `search_chip_<kind>` | Un chip: `dates`, `section`, `subsection`, `issuer` |

### 2.2. Nuevas — búsqueda rápida en Inicio

| Constante | Valor | Dónde |
|---|---|---|
| `TAG_HOME_SEARCH_FIELD` | `home_search_field` | El campo de la barra superior en modo búsqueda |
| `TAG_HOME_SEARCH_CLOSE` | `home_search_close` | Cerrar el buscador |
| `TAG_HOME_SEARCH_CLEAR` | `home_search_clear` | Borrar el texto |
| `TAG_HOME_SEARCH_COUNT` | `home_search_count` | Número de coincidencias |
| `TAG_HOME_NO_RESULTS` | `home_no_results` | Mensaje de «nada en esta edición» |
| `TAG_HOME_SEARCH_GLOBALLY` | `home_search_globally` | El puente |

### 2.3. Existentes que se reutilizan sin tocar

`TAG_SEARCH` (`home_search`, la lupa de la barra superior), `TAG_PUBLICATION_CARD`,
`TAG_PUBLICATION_SAVE`, `TAG_PUBLICATION_SHARE`, `TAG_BOTTOM_SEARCH`, `TAG_PUBLICATIONS`.

---

## 3. Contrato visual

Del documento de diseño, apartados 11.5, 11.6 y 17. Todo por tokens: **ningún** color, tamaño ni
espaciado literal fuera de `core/ui/theme`.

### 3.1. Campo de búsqueda (apartado 11.6)

| Propiedad | Valor |
|---|---|
| Altura | 56 dp |
| Radio | 16 dp |
| Fondo | `Surface` |
| Borde | 1 dp `Outline` |
| Icono de lupa | A la izquierda, 24 dp |
| Texto | `BodyLarge`; el de ayuda, en `TextMuted` |
| Acción de borrar | A la derecha, **solo** cuando hay texto |

### 3.2. Chip de filtro activo (apartado 11.5)

| Estado | Fondo | Texto | Borde |
|---|---|---|---|
| Activo | `Secondary` | Blanco | `Secondary` |
| No activo | `Surface` | `TextPrimary` | `Outline` |

Altura 36–40 dp, padding horizontal 14 dp, texto `LabelLarge`, aspa de 20 dp con área táctil de
48 dp.

### 3.3. Hoja de filtros (apartado 17.3)

Título `Filtrar resultados`, grupos separados visualmente, botón de texto `Limpiar` y botón
principal `Aplicar filtros`. `Aplicar filtros` **inhabilitado** mientras «desde» sea posterior a
«hasta».

### 3.4. Barra superior de Buscar

Título **`Buscar`** en `TitleMedium`, fondo **`Primary`** y contenido en `OnPrimary`, igual que
Guardados. `Buscar` y no `Buscar publicaciones`: esa frase es el texto de ayuda del campo que va
justo debajo, y repetirla es ruido en pantalla y una doble lectura para quien usa lector. La imagen de referencia la dibujaba blanca, pero era una idea, no una especificación:
dos destinos de la misma barra inferior con cabeceras de distinto color se leen como dos
aplicaciones. Inicio conserva la suya blanca por un motivo propio —debajo va la cabecera editorial
azul—.

**Sin acción de retroceso y sin menú de opciones**, al contrario que la imagen de referencia: es un
destino de la barra inferior, no una pantalla apilada. Queda anotado como desviación consciente.

### 3.5. Barra superior de Inicio en modo búsqueda

El menú, el escudo y el título dejan sitio al campo. A la izquierda, cerrar; a la derecha, borrar
cuando hay texto. El fondo sigue siendo `Surface`, para que la transformación no parezca otra
pantalla.

### 3.6. Estados

| Estado | Componible | Texto |
|---|---|---|
| Inicial (menos de dos caracteres) | `IllustratedMessage` con `ic_search` | «Escribe para buscar en todo el BOC» |
| Sin resultados en Buscar | `IllustratedMessage` con `ic_search` | «No hemos encontrado publicaciones» + «Prueba con otras palabras o quita alguno de los filtros» |
| Sin resultados en la edición | Mensaje en línea con acción | «Nada en esta edición» + `Buscar en todo el BOC` |
| Lista recortada | Aviso sobre la lista | «Mostrando 300 de los más recientes. Acota la búsqueda» |

### 3.7. Accesibilidad

- Área táctil mínima de 48 × 48 dp en todas las acciones nuevas, incluidas las aspas de los chips.
- El campo de búsqueda de Inicio recibe el foco al abrirse y lo anuncia con su texto de ayuda.
- Cada chip de filtro activo describe **qué** filtro quita, no solo «quitar».
- El estado del marcador de un resultado se distingue por el trazado del icono **y** por su
  descripción, no solo por color, igual que en Inicio.
