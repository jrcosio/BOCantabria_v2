# Data Model: Boletín del día

**Feature**: `003-boletin-del-dia` | **Fase**: 1 | **Fecha**: 2026-08-29

Tres vocabularios que no deben mezclarse: lo que llega por la red (DTO), lo que se guarda
(entidades) y lo que el negocio entiende (modelos de dominio). El mapeo entre ellos es traducción
real, no copia de campos.

---

## 1. Cadena completa

```text
19 fuentes oficiales
   │  GET
   ▼
RssChannelDto ── items ──▶ RssItemDto            (data/source/remote — literal, sin interpretar)
   │  BocRssParser
   ▼
PublicationNormalizer  +  BocFeedDefinition       (la fuente es autoritativa)
   │
   ▼
PublicationEntity ──── upsert ────▶ Room          (data/source/local — lo que se guarda)
   │  Flow
   ▼
Publication                                        (domain/model — lo que el negocio entiende)
   │
   ▼
HomeUiState ──▶ HomeScreen                         (ui/home — lo que se dibuja)
```

---

## 2. Dominio (`domain/model`) — Kotlin puro

### `Publication`

Un anuncio del BOC.

| Campo | Tipo | Regla |
|---|---|---|
| `externalKey` | `String` | Identidad estable. `boc:439765` cuando hay identificador en el enlace |
| `blobId` | `String?` | El identificador del enlace, si existe |
| `idSource` | `IdSource` | `BLOB_ID`, `CANONICAL_URL` o `CONTENT_HASH`. Qué respaldo se usó |
| `feedId` | `String` | Fuente de la que se obtuvo. Es la clasificación autoritativa |
| `sectionCode` | `String` | `1` … `9`. Viene del catálogo, nunca de `categorias` |
| `subsectionCode` | `String?` | `2.1`, `7.3`… Nulo en secciones sin subsección |
| `title` | `String` | Íntegro, tal como se recibe. Sin recortar ni completar |
| `issuer` | `String?` | Organismo emisor deducido; nulo si no se pudo deducir |
| `organizationPath` | `List<String>` | Ruta jerárquica del organismo, de más general a más concreta |
| `editionType` | `EditionType` | `ORD`, `EXT` o `UNKNOWN` |
| `publicationDate` | `LocalDate` | Interpretada como `yyyy-MM-dd` |
| `documentUrl` | `String` | Enlace al documento oficial. Siempre HTTPS |
| `rawCategories` | `String?` | El campo `categorias` original, sin tocar |
| `warnings` | `Set<ParserWarning>` | Anomalías detectadas al normalizar. Nunca motivo de descarte |

Invariantes: `externalKey` no vacía; `title` no vacío; `documentUrl` con esquema `https`;
`organizationPath` sin elementos vacíos.

### `EditionType`

`ORD` · `EXT` · `UNKNOWN`. El apartado 9.3 del documento de feeds obliga a buscar el valor en
cualquier componente, no en el último.

### `IdSource`

`BLOB_ID` · `CANONICAL_URL` · `CONTENT_HASH`. Registra qué escalón de la cascada del apartado 12.2 se
usó, para saber si un registro es sustituible por otro mejor identificado.

### `ParserWarning`

`CATEGORY_DOES_NOT_MATCH_FEED` · `EDITION_TYPE_MISSING` · `CATEGORY_ORDER_UNRELIABLE` ·
`CATEGORIES_ABSENT`. Se guardan con la publicación; no impiden mostrarla.

### `BocSection`

Una de las nueve secciones oficiales, o una de sus subsecciones.

| Campo | Tipo | Regla |
|---|---|---|
| `code` | `String` | `1`, `2`, `2.1`… |
| `name` | `String` | Nombre oficial completo |
| `shortName` | `String` | Nombre corto para el chip de filtro |
| `parentCode` | `String?` | Nulo en secciones principales |
| `order` | `Int` | Orden oficial de presentación |
| `colorGroup` | `SectionColorGroup` | Cuál de los cinco colores del diseño le corresponde (D-013) |

Las secciones **2, 4, 7 y 8 no tienen fuente propia**: su contenido es la unión del de sus
subsecciones. Una consulta por sección principal debe recoger también sus descendientes.

### `SectionColorGroup`

`GENERAL` · `PERSONNEL` · `CONTRACTING` · `ECONOMY` · `ANNOUNCEMENTS`. El mapeo de las nueve secciones
sobre los cinco grupos está en D-013 de `research.md`. Es un tipo de dominio, no un color: la
traducción a color vive en `ui`, porque `domain` no puede ver Compose.

### `HomeSelection`

Lo que Inicio está mostrando.

```text
HomeSelection
├── TodaysBulletin                                   → las publicaciones de la fecha más reciente
└── Section(sectionCode, subsectionCode: String?)    → esa sección, sin límite de fecha
```

### `SyncSummary`

El resultado de una sincronización.

| Campo | Tipo | Significado |
|---|---|---|
| `succeededFeeds` | `Int` | Fuentes consultadas con éxito, incluidas las que no habían cambiado |
| `failedFeeds` | `Int` | Fuentes que fallaron tras agotar los reintentos |
| `unchangedFeeds` | `Int` | Fuentes cuya huella coincidía con la anterior |
| `insertedItems` | `Int` | Publicaciones nuevas |
| `updatedItems` | `Int` | Publicaciones que ya existían y cambiaron |
| `rejectedItems` | `Int` | Publicaciones descartadas por no cumplir los mínimos |

Derivadas: `allFailed` cuando `succeededFeeds == 0`; `isComplete` cuando `failedFeeds == 0`.

---

## 3. Contratos de repositorio (`domain/repository`)

```text
PublicationRepository
  observePublications(selection: HomeSelection): Flow<List<Publication>>
  observeLatestPublicationDate(): Flow<LocalDate?>
  suspend isCacheStale(): Boolean
  suspend refresh(): AppResult<SyncSummary>

BocSectionRepository
  sections(): List<BocSection>            // el árbol completo, ordenado
```

Reglas heredadas del contrato del proyecto: **nunca lanzan**; una lista vacía es
`Success(emptyList())`; `CancellationException` se repropaga siempre; las excepciones se traducen
dentro de `data`.

`refresh()` devuelve `Failure(DomainError.Network)` **solo** cuando todas las fuentes fallan y no hay
nada guardado. Si fallan todas pero hay caché, devuelve `Success` con `allFailed = true`: la pantalla
mostrará contenido y aviso de falta de conexión (D-008).

---

## 4. Capa de datos (`data`)

### 4.1 Lo que llega por la red

```text
RssChannelDto   title, link, description, declaredSize: Int?, items: List<RssItemDto>
RssItemDto      title: String?, link: String?, pubDateRaw: String?, categoriesRaw: String?
```

Todo anulable a propósito: el DTO refleja lo que **llega**, no lo que debería llegar. La validación
es paso aparte, para poder rechazar una publicación sin detener la fuente.

`declaredSize` es informativo (apartado 8.3): si no coincide con el número real de nodos, mandan los
nodos y se registra un aviso.

### 4.2 El catálogo de fuentes

`BocFeedDefinition(feedId, url, sectionCode, subsectionCode, order, enabled)`.

Diecinueve entradas, con las direcciones **escritas literalmente**:

| Fuente | Sección | Subsección |
|---|---|---|
| 6802081 | 1 | — |
| 6802084 | 2 | 2.1 |
| 6802085 | 2 | 2.2 |
| 6802086 | 2 | 2.3 |
| 6802087 | 3 | — |
| 6802089 | 4 | 4.1 |
| 6802090 | 4 | 4.2 |
| 6802091 | 4 | 4.3 |
| 6802092 | 4 | 4.4 |
| 6802094 | 5 | — |
| 6802095 | 6 | — |
| 6802097 | 7 | 7.1 |
| 6802098 | 7 | 7.2 |
| 6802099 | 7 | 7.3 |
| 6802100 | 7 | 7.4 |
| 6802301 | 7 | 7.5 |
| 7479572 | 8 | 8.1 |
| 6802303 | 8 | 8.2 |
| 7293890 | 9 | — |

Base de las direcciones: `https://www.cantabria.es/o/BOC/feed/<feedId>`. Se escribe entera por
entrada; **prohibido componerla por cálculo** (apartado 11.1).

### 4.3 Lo que se guarda

`PublicationEntity` — tabla `publications`

| Columna | Tipo | Índice |
|---|---|---|
| `external_key` | `TEXT` | **Clave primaria** |
| `blob_id` | `TEXT?` | Único cuando no es nulo |
| `id_source` | `TEXT` | |
| `feed_id` | `TEXT` | Compuesto con `publication_date` |
| `section_code` | `TEXT` | Sí |
| `subsection_code` | `TEXT?` | Sí |
| `title` | `TEXT` | |
| `issuer` | `TEXT?` | |
| `organization_path` | `TEXT` | Ruta serializada |
| `edition_type` | `TEXT` | Sí |
| `publication_date` | `TEXT` | Sí. ISO, ordena igual que cronológicamente |
| `document_url` | `TEXT` | |
| `raw_categories` | `TEXT?` | |
| `warnings` | `TEXT` | Conjunto serializado |
| `first_seen_at` | `INTEGER` | Se fija al insertar y **no se toca al actualizar** |
| `last_seen_at` | `INTEGER` | Se actualiza en cada aparición |

`FeedSyncStateEntity` — tabla `feed_sync_state`

| Columna | Tipo | Significado |
|---|---|---|
| `feed_id` | `TEXT` | Clave primaria |
| `body_hash` | `TEXT?` | SHA-256 del último cuerpo procesado (D-011) |
| `etag` | `TEXT?` | Si el servicio llega a publicarlo |
| `last_modified` | `TEXT?` | Ídem |
| `last_success_at` | `INTEGER?` | Base del cálculo de caducidad de treinta minutos |
| `consecutive_failures` | `INTEGER` | Para diagnóstico |

**Regla de oro**: no existe ninguna operación de borrado de publicaciones. Salir de la ventana de
cien de una fuente no elimina nada (apartado 18.3, FR-021).

### 4.4 Consultas

| Necesidad | Forma |
|---|---|
| Fecha más reciente | `SELECT MAX(publication_date) FROM publications` |
| Boletín del día | `WHERE publication_date = (SELECT MAX(...))`, orden estable |
| Sección principal | `WHERE section_code = :code`, incluye todas sus subsecciones |
| Subsección | `WHERE subsection_code = :code` |
| Recuento de la selección | `COUNT(*)` sobre el mismo filtro |

**Orden estable** en todas ellas, según el apartado 15.1:
`ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC`.
El último criterio es el desempate determinista que hace que dos ejecuciones den el mismo orden
aunque las fuentes respondan en distinto orden.

---

## 5. Presentación (`ui/home`)

```text
HomeUiState                      (data class inmutable)
  selection: HomeSelection
  header: BulletinHeader?        título, fecha, recuento
  chips: List<SectionChip>       «Todo» más las nueve secciones
  content: HomeContent
  isRefreshing: Boolean
  isOffline: Boolean

HomeContent                      (sealed interface)
├── Skeleton                     primera carga sin nada guardado
├── Publications(items)          hay contenido
├── Empty                        la selección no tiene publicaciones — NO es un error
└── Error(error: DomainError)    no hay nada que mostrar y la sincronización falló
```

`isRefreshing` e `isOffline` son ejes **independientes** de `content`: se puede estar actualizando con
contenido a la vista (FR-026) y se puede estar sin conexión mostrando contenido guardado (FR-041).
Meterlos dentro del sellado obligaría a multiplicar los casos.

### Transiciones

```text
                    hay contenido guardado
   [inicio] ──────────────────────────────▶ Publications ──refresh()──▶ Publications
       │                                          ▲                          │ allFailed
       │ base de datos vacía                      └──────────────────────────┘ + isOffline
       ▼
   Skeleton ──refresh() éxito con items──▶ Publications
       │
       ├──refresh() éxito sin items──────▶ Empty
       │
       └──refresh() Failure──────────────▶ Error ──onRetry()──▶ Skeleton
```

Cambiar de selección **no** vuelve a `Skeleton`: los datos ya están guardados, así que la transición
es inmediata de `Publications` a `Publications` o a `Empty`.

### Panel lateral (`ui/sections`)

```text
SectionsUiState
  query: String                  texto del campo de filtro
  sections: List<SectionRow>     ya filtradas por query
  expanded: Set<String>          códigos de sección desplegados
  selected: HomeSelection        para marcar la fila activa

SectionRow
  section: BocSection
  children: List<BocSection>     vacío si no tiene subsecciones
  isExpandable: Boolean
```

Filtrar por texto **expande automáticamente** las secciones cuyas subsecciones coinciden: si no, una
coincidencia quedaría escondida detrás de un chevron cerrado.
