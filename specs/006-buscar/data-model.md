# Data Model: Buscar

**Feature**: `006-buscar` | **Fase**: 1 | **Fecha**: 2026-08-31

Qué se guarda, qué se consulta y qué viaja por las capas. Las decisiones y sus alternativas están en
[research.md](./research.md); aquí está la forma final.

---

## 1. El texto buscable

### 1.1. La columna

`publications` gana **una** columna. Ninguna tabla nueva.

| Columna | Tipo SQLite | Nulable | Por defecto | Quién la escribe |
|---|---|---|---|---|
| `search_text` | `TEXT` | **no** | `''` | `PublicationDao` — la sincronización y el relleno |

Declaración en la entidad:

```kotlin
@ColumnInfo(name = "search_text", defaultValue = "''")
val searchText: String = "",
```

**Sin índice**, y es deliberado: un `LIKE '%…%'` con comodín inicial no puede usar un índice
B-tree, así que solo añadiría escrituras y tamaño (D-006). Los índices que sí recortan el conjunto
antes del `LIKE` —`publication_date`, `section_code`, `subsection_code`— ya existen desde la
feature 003.

### 1.2. Cómo se construye

Función pura en `data/source/local/PublicationSearchText.kt`, usada por los **dos** sitios que
escriben la columna —la sincronización y el relleno—, para que no puedan divergir:

```kotlin
internal fun buildSearchText(
    title: String,
    issuer: String?,
    organizationPath: List<String>,
    blobId: String?,
    sectionName: String?,
    subsectionName: String?,
): String
```

Concatena con espacio, en este orden, y pasa el resultado entero por `SearchText.normalise`:

1. `title` — ya empieza por el organismo, con la forma `ORGANISMO: texto`
2. `issuer`
3. `organizationPath`, unida por espacios
4. `blobId` — la «referencia» de la ficha del detalle
5. `sectionName` y `subsectionName` — del catálogo compilado, **no** de la tabla, que solo guarda
   códigos

Los nulos se omiten. El resultado nunca queda vacío, porque `title` no puede estar en blanco: esa es
la propiedad que convierte `search_text = ''` en un marcador fiable de «fila aún sin rellenar».

**Lo que NO entra**: `raw_categories`, porque viene en crudo del servicio y con los componentes
permutados en el feed 4.3; y `titleWithoutIssuer`, porque es un derivado del título y no añade ni
una coincidencia (D-002).

### 1.3. La normalización

`core/util/SearchText.kt`, Kotlin puro, sin Android. La usan las tres capas.

```kotlin
object SearchText {
    /** NFD → quitar marcas → minúsculas de Locale.ROOT → colapsar espacios → recortar. */
    fun normalise(raw: String): String
}
```

| Entrada | Salida |
|---|---|
| `AYUNTAMIENTO DE PIÉLAGOS` | `ayuntamiento de pielagos` |
| `  Piélagos  ` | `pielagos` |
| `España` | `espana` |
| `Contratación   administrativa` | `contratacion administrativa` |
| `   ` | `` (cadena vacía) |

`Locale.ROOT` a propósito: el idioma del sistema puede cambiar entre la escritura y la consulta, y
hay idiomas donde `lowercase()` da otro resultado. La `ñ` pierde la tilde y se indexa como `n`, que
es exactamente lo que se quiere (D-003).

---

## 2. La actualización del almacén

### 2.1. Versiones

| Versión | Qué la introdujo | Cambio |
|---|---|---|
| 1 | Feature 003 | Esquema inicial: `publications` y `feed_sync_state` |
| 2 | Feature 005 | `saved_at INTEGER` nulable con su índice |
| **3** | **Esta feature** | **`search_text TEXT NOT NULL DEFAULT ''`** |

```kotlin
@Database(
    entities = [PublicationEntity::class, FeedSyncStateEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
```

La migración 1→2 **se conserva**: alguien que se saltara una versión de la aplicación tiene que
poder llegar de la 1 a la 3.

`bocDatabase()` sigue siendo un `.build()` limpio: las migraciones automáticas no necesitan
`addMigrations`, y `fallbackToDestructiveMigration()` no entra aquí ni como último recurso (D-004).

El esquema de la versión 2 queda congelado en
`app/schemas/…BocDatabase/2.json`, con hash de identidad `1f93c864ff2220ed1bf0114ece8dfb40`. Es el
material del que la prueba de migración transcribe su fixture, igual que hizo la 1→2.

### 2.2. El relleno

Dos sentencias en `PublicationDao` —no en el DAO de búsqueda, que es de solo lectura (D-007)—:

```kotlin
@Query("SELECT * FROM publications WHERE search_text = '' LIMIT :limit")
suspend fun withoutSearchText(limit: Int): List<PublicationEntity>

@Query("UPDATE publications SET search_text = :searchText WHERE external_key = :externalKey")
suspend fun setSearchText(externalKey: String, searchText: String)
```

Se ejecutan desde `PublicationRepositoryImpl.refresh()`, después de sincronizar las fuentes, en
lotes de **500** filas hasta que `withoutSearchText` devuelve vacío. Idempotente y sin bandera que
guardar: el estado vive en la propia columna (D-005).

### 2.3. La lista blanca del `UPDATE` de sincronización

`PublicationDao.updateColumns` es una lista blanca. Tras esta feature queda así:

| Columna | ¿En el `UPDATE`? | Por qué |
|---|---|---|
| `title`, `issuer`, `section_code`, `subsection_code`, … | **Sí** | Las publica la fuente y pueden corregirse |
| **`search_text`** | **Sí, nueva** | Se **deriva** de lo anterior. Si la fuente corrige un título y el texto buscable no se corrige, el anuncio se seguiría encontrando solo por el título viejo |
| `first_seen_at` | No | Cuándo lo supo la aplicación por primera vez. Una vista posterior no reescribe la historia |
| `saved_at` | **No** | Es de la persona, no de la fuente. Aquí es donde `SavedPublicationDaoTest` vigila |

> **Para la revisión**: `SavedPublicationDaoTest` **no se toca en esta feature**. Si se pone roja, es
> que `saved_at` se ha colado en la sentencia. El arreglo es quitarlo, no ajustar la prueba.

---

## 3. Las consultas

### 3.1. Búsqueda global — `PublicationSearchDao`, solo lectura

```sql
SELECT * FROM publications
WHERE search_text LIKE :pattern ESCAPE '\'
  AND (:sectionCode    IS NULL OR section_code     = :sectionCode)
  AND (:subsectionCode IS NULL OR subsection_code  = :subsectionCode)
  AND (:issuer         IS NULL OR issuer           = :issuer)
  AND (:from           IS NULL OR publication_date >= :from)
  AND (:to             IS NULL OR publication_date <= :to)
ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC
LIMIT :limit
```

- `searchNewestFirst(...)` con `DESC` y `searchOldestFirst(...)` con `ASC` en los tres términos.
  Dos sentencias porque Room no parametriza la dirección (D-008).
- Los **tres** términos de ordenación se mantienen: es lo que hace determinista el resultado
  (FR-042).
- `publication_date` se guarda como texto ISO, así que el orden lexicográfico **es** el cronológico
  y los operadores `>=` y `<=` funcionan tal cual, sin conversión.
- `:pattern` lo construye el repositorio, no el DAO: `"%" + escapeForLike(normalise(texto)) + "%"`.

Y la lista de organismos:

```sql
SELECT DISTINCT issuer FROM publications WHERE issuer IS NOT NULL ORDER BY issuer
```

### 3.2. El patrón y su escapado

`data/source/local/LikePattern.kt`, junto al SQL y no en `core/util`, porque escapar es una regla de
SQL y la búsqueda rápida no debe arrastrar barras invertidas (D-010):

```kotlin
internal fun likeContains(normalisedQuery: String): String
```

| Texto normalizado | Patrón |
|---|---|
| `pielagos` | `%pielagos%` |
| `100%` | `%100\%%` |
| `a_b` | `%a\_b%` |
| `c\d` | `%c\\d%` |

### 3.3. Búsqueda rápida — sin SQL

No hay consulta. `FilterPublicationsUseCase` recorre la lista que la pantalla ya tiene y se queda
con las publicaciones cuyo `title` o `issuer`, normalizados, contienen el texto normalizado. Sin
`LIKE`, sin escapado y sin tocar el almacén (FR-012).

---

## 4. Modelos de dominio

Kotlin puro. Los tres son clases de nivel superior en `domain`, así que la regla de Konsist exige
un fichero de prueba para cada uno.

### 4.1. `SearchQuery`

Todo lo que define una búsqueda global. Es lo que se conserva al abrir un resultado y volver.

```kotlin
data class SearchQuery(
    val text: String = "",
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val sectionCode: String? = null,
    val subsectionCode: String? = null,
    val issuer: String? = null,
    val sort: SearchSort = SearchSort.NEWEST_FIRST,
) {
    val normalisedText: String
    /** Cierto a partir de dos caracteres normalizados (D-011). */
    val isRunnable: Boolean
    /** Cuántos filtros hay puestos. El rango de fechas cuenta como uno. */
    val activeFilterCount: Int
    /** Quita los filtros y **conserva el texto y el orden** (FR-040). */
    fun clearedFilters(): SearchQuery
    fun withoutDateRange(): SearchQuery
    fun withoutSection(): SearchQuery
    fun withoutIssuer(): SearchQuery
}
```

**Sin `require` sobre el rango de fechas.** La combinación inválida se impide en la interfaz —la
acción de aplicar queda inhabilitada y el selector de «hasta» no ofrece días anteriores a «desde»—,
porque un `require` convertiría un error de manejo en un cierre de la aplicación (D-018).

Elegir una sección **limpia la subsección** si la que había no le pertenece: esa regla vive aquí,
no en la hoja, para que tenga prueba propia (FR-036).

### 4.2. `SearchSort`

```kotlin
enum class SearchSort { NEWEST_FIRST, OLDEST_FIRST }
```

`NEWEST_FIRST` es el valor por defecto (FR-041). Dos valores y no tres: la relevancia queda fuera de
alcance por decisión del propietario.

### 4.3. `SearchResults`

```kotlin
data class SearchResults(
    val items: List<Publication>,
    /** Había más de los que caben. La pantalla lo dice en voz alta (FR-032). */
    val isTruncated: Boolean = false,
)
```

`SearchPublicationsUseCase` pide **301** filas al repositorio; si vuelven 301, entrega 300 y marca
`isTruncated`. Pedir una más es lo que distingue «exactamente 300» de «más de 300» sin una segunda
consulta de recuento (D-017).

### 4.4. `Publication` no cambia

Ni un campo. `search_text` es una columna del almacén, no un dato de dominio: nada de lo que la
pantalla muestra sale de ella.

---

## 5. Contratos de repositorio

```kotlin
// domain/repository/SearchRepository.kt
interface SearchRepository {
    /** Emite de nuevo cuando el almacén cambia. Lista vacía si no hay coincidencias: nunca un fallo. */
    fun search(query: SearchQuery, limit: Int): Flow<List<Publication>>

    /** Los organismos que realmente tienen algo almacenado, en orden alfabético. */
    fun observeIssuers(): Flow<List<String>>
}
```

Igual que el resto del proyecto: **una lista vacía es un éxito**, no un fallo. Un fallo de lectura
local se registra como no fatal y emite lista vacía, para que la pantalla no se quede sin estado.

`PublicationRepository` gana un método interno para el relleno, o lo resuelve dentro de `refresh()`
sin ampliar la interfaz de dominio —lo segundo, que es lo que hace `tasks.md`: el relleno es un
detalle del almacén y no algo que `domain` deba poder pedir.

---

## 6. Estado de presentación

### 6.1. `SearchUiState`

```kotlin
data class SearchUiState(
    val query: SearchQuery = SearchQuery(),
    val content: SearchContentState = SearchContentState.Initial,
    val issuers: List<String> = emptyList(),
    val share: ShareState = ShareState.Idle,
    val savedKeys: Set<String> = emptySet(),
    val saveFailed: Boolean = false,
)

sealed interface SearchContentState {
    /** Aún no hay consulta suficiente. No es un vacío ni un error. */
    data object Initial : SearchContentState
    data class Results(val items: List<Publication>, val isTruncated: Boolean) : SearchContentState
    data object Empty : SearchContentState
}
```

**Sin estado de carga**, por la misma razón que `SavedContentState` no lo tiene: lo que se lee es
local e inmediato, no hay espera que amortiguar, y un caso que nadie emite es una rama muerta del
`when`.

`share`, `savedKeys` y `saveFailed` van **fuera** del sellado por la misma razón que en Inicio y en
Guardados: son ejes independientes. Se puede estar preparando un documento para compartir con los
resultados en pantalla, y una escritura fallida no es una clase de contenido.

**Persistencia**: la consulta se guarda campo a campo en el `SavedStateHandle` —`query`, `from`,
`to`, `sectionCode`, `subsectionCode`, `issuer`, `sort`— porque la barra inferior destruye el modelo
de pantalla al cambiar de pestaña (D-014).

> **El texto se persiste bajo la clave `query`, no `text`.** Es la clave por la que la ruta tipada
> `Route.Search(query = …)` deja el argumento del puente, de modo que la ruta la siembra y el modelo
> escribe encima. `SearchQuery.text` se mapea desde ella. Usar dos claves distintas rompería el
> puente **en silencio**: sin error, sin excepción, simplemente llegando a Buscar con el campo
> vacío.

### 6.2. `HomeUiState` — lo que crece

```kotlin
data class HomeSearchState(
    val isOpen: Boolean = false,
    val query: String = "",
)
```

Se añade como campo `search` a `HomeUiState`, y `HomeContentState` gana un caso:

```kotlin
/** Hay publicaciones en la edición, pero ninguna coincide. El único estado que ofrece el puente. */
data class NoSearchResults(val query: String) : HomeContentState
```

Es un caso propio para que el `when` de la pantalla siga siendo exhaustivo y para no confundirlo con
`Empty`, que dice justo lo contrario: «aquí no hay nada publicado» (D-016).

La cabecera editorial **no** cambia mientras se busca: sigue contando los anuncios de la edición
(FR-017). El número de coincidencias se muestra junto a la lista.

---

## 7. Rutas

```kotlin
@Serializable
data class Search(val query: String? = null) : Route
```

Deja de ser `data object`. La barra inferior navega a `Route.Search()`; el puente, a
`Route.Search(texto)`. `hasRoute<Route.Search>()` sigue identificando la pestaña igual que antes.

**El detalle importante**: el puente navega con `launchSingleTop = true` y **sin** `restoreState`,
para que el estado guardado de la pestaña no pise el término traspasado (D-013, FR-020). La barra
inferior sigue navegando con `restoreState = true`, como hasta ahora.
