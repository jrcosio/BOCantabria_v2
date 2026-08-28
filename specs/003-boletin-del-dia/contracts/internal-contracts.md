# Contratos internos: Boletín del día

**Feature**: `003-boletin-del-dia` | **Fase**: 1 | **Fecha**: 2026-08-29

Lo que cada capa promete a la de al lado. Romper cualquiera de estas promesas debe romper una prueba.

---

## 1. `domain` hacia el resto del mundo

### 1.1 Los repositorios nunca lanzan

```kotlin
interface PublicationRepository {
    fun observePublications(selection: HomeSelection): Flow<List<Publication>>
    fun observeLatestPublicationDate(): Flow<LocalDate?>
    suspend fun isCacheStale(): Boolean
    suspend fun refresh(): AppResult<SyncSummary>
}

interface BocSectionRepository {
    fun sections(): List<BocSection>
}
```

- Ninguna función lanza. El fallo viaja como `AppResult.Failure(DomainError)`.
- «No hay publicaciones» es `Success(emptyList())`, jamás un fallo.
- `CancellationException` **se repropaga siempre**: cancelar una corrutina no es un error de dominio.
- Los `Flow` no terminan con error: si la lectura local falla, emiten lista vacía y el fallo se
  reporta como no fatal.
- `sections()` es determinista y no depende de la red: el árbol de secciones del BOC es conocimiento
  compilado, no un dato remoto.

### 1.2 Los casos de uso

```kotlin
class ObservePublicationsUseCase(repository)
    operator fun invoke(selection: HomeSelection): Flow<List<Publication>>

class RefreshPublicationsUseCase(repository)
    suspend operator fun invoke(force: Boolean): AppResult<SyncSummary>
    // force = false  → no hace nada y devuelve Success con el resumen vacío si la caché es fresca
    // force = true   → sincroniza siempre (gesto de refresco)

class GetBocSectionsUseCase(repository)
    operator fun invoke(): List<BocSection>

class ObserveBulletinHeaderUseCase(repository)
    operator fun invoke(selection: HomeSelection): Flow<BulletinHeaderData>
    // fecha de la selección y recuento, para la cabecera editorial
```

Un caso de uso, una operación, un `operator fun invoke`.

### 1.3 `DomainError` no crece

Sigue teniendo dos casos, `Network` y `Unknown`. «Sin conexión pero con contenido» **no** es un
error: es `Success` con `SyncSummary.allFailed = true` (research.md, D-008).

---

## 2. `data` hacia `domain`

### 2.1 Política del repositorio de publicaciones

| Situación | Qué devuelve `refresh()` | Qué ven los `Flow` |
|---|---|---|
| Todas las fuentes responden | `Success(summary)` con `failedFeeds = 0` | Lo escrito, conforme se escribe |
| Algunas fuentes fallan | `Success(summary)` con `failedFeeds > 0` | Lo de las que sí respondieron |
| Todas fallan, hay contenido guardado | `Success(summary)` con `allFailed = true` | Lo guardado, intacto |
| Todas fallan, no hay nada guardado | `Failure(DomainError.Network)` | Lista vacía |
| Caché fresca y `force = false` | `Success` con resumen vacío, **sin tocar la red** | Lo guardado |

### 2.2 Reglas de escritura, no negociables

- **Insertar o actualizar, nunca duplicar.** La identidad es `externalKey`.
- **Nunca borrar.** No existe ninguna sentencia de borrado de publicaciones en el DAO. Si aparece
  una, la revisión debe rechazarla.
- `first_seen_at` se fija en la inserción y **no se modifica** al actualizar; `last_seen_at` sí.
- Cada fuente se escribe **en cuanto termina**, no al final de todas. Es lo que hace que el contenido
  aparezca progresivamente (research.md, D-009).

### 2.3 Contrato de la fuente remota

```kotlin
interface PublicationRemoteDataSource {
    suspend fun fetchFeed(definition: BocFeedDefinition, knownBodyHash: String?): FeedFetchResult
}

sealed interface FeedFetchResult {
    data class Fetched(val channel: RssChannelDto, val bodyHash: String) : FeedFetchResult
    data object NotModified : FeedFetchResult          // la huella coincide con la conocida
    data class Failed(val cause: FeedFailure) : FeedFetchResult
}
```

**Esta es la única interfaz del proyecto que puede fallar sin lanzar**: devuelve el fallo como valor
para que el orquestador pueda seguir con las demás fuentes sin `try`/`catch` anidados. Se documenta
aquí porque es una excepción deliberada al estilo del resto.

Garantías de la implementación:

- Solo HTTPS. Cualquier otro esquema se rechaza sin conectar.
- Límite de conexión 10 s, de lectura 45 s, total por fuente 60 s.
- El cuerpo se lee en *streaming* y se corta a los 5 MB (apartado 19.2).
- Máximo cuatro fuentes simultáneas, impuesto por el orquestador mediante semáforo.
- Tres intentos con esperas de 2, 5 y 15 segundos más un componente aleatorio **inyectado**, solo
  ante agotamiento de espera, error de conexión, 408, 429 y 5xx. `Retry-After` se respeta.
- `User-Agent` identificable y `Accept: application/rss+xml, application/xml, text/xml`.

### 2.4 Contrato del analizador

```kotlin
class BocRssParser {
    fun parse(body: String): RssChannelDto      // lanza BocRssParseException si el documento no sirve
}
```

- **Kotlin puro.** Ni un `import android.*`. Es lo que permite probarlo sin emulador.
- Rechaza el cuerpo **antes de construir el árbol** si contiene declaración de tipo de documento o de
  entidad. Es la guarda portátil de research.md D-003.
- Endurece la fábrica en la medida en que la plataforma lo permita, cada bandera dentro de un
  `runCatching`.
- Analiza **por nombre de etiqueta**, nunca por posición ni por orden.
- Ignora los nodos que no conoce, en lugar de fallar.
- Tope de 500 publicaciones por fuente.
- Un `item` inválido no aborta el canal: se omite y se cuenta.

### 2.5 Contrato del normalizador

```kotlin
class PublicationNormalizer {
    fun normalize(item: RssItemDto, definition: BocFeedDefinition): NormalizationResult
}

sealed interface NormalizationResult {
    data class Accepted(val publication: Publication) : NormalizationResult
    data class Rejected(val reason: RejectionReason) : NormalizationResult
}
```

Reglas, todas comprobables una a una:

1. Mínimos para aceptar: título no vacío, enlace HTTPS válido y fecha interpretable como
   `yyyy-MM-dd`. Si falta alguno → `Rejected` con motivo.
2. `sectionCode` y `subsectionCode` salen **siempre** de `definition`, nunca de `categorias`.
3. `categorias` se guarda íntegro en `rawCategories`, sin normalizar.
4. `ORD`/`EXT` se buscan en **cualquier** componente. Si no aparece → `UNKNOWN` más
   `EDITION_TYPE_MISSING`.
5. Los componentes que casan `^\d+(?:\.\d+)?\.` son códigos de sección; el resto, tras quitar el tipo
   de edición, es la ruta del organismo.
6. Si el código de sección declarado no corresponde al de la fuente → `CATEGORY_DOES_NOT_MATCH_FEED`,
   y **manda la fuente**.
7. Si el tipo de edición no está al final → `CATEGORY_ORDER_UNRELIABLE`. No se descarta.
8. Sin `categorias` → `CATEGORIES_ABSENT`, sección de la fuente y tipo `UNKNOWN`.
9. Identificador: `idAnuBlob` del enlace → si no, URL canónica → si no, SHA-256 de
   `feedId|fecha|título|categorias`. Se registra cuál en `idSource`.
10. `issuer`: último elemento de la ruta del organismo; si no hay ruta, el texto anterior al primer
    `:` del título. Es dato auxiliar, puede quedar nulo.
11. El título se guarda **entero**. Recortar es cosa de la pantalla.

---

## 3. `core` y transversales

- Los `Dispatchers` se inyectan vía `DispatcherProvider`. La sincronización corre en `io`; el
  análisis del XML, en `default`.
- La aleatoriedad de los reintentos se inyecta, por la misma razón que los `Dispatchers`: sin eso las
  pruebas no serían deterministas.
- La telemetría sigue detrás de `AnalyticsTracker` y `CrashReporter`. Eventos de esta feature:
  `home` (vista de pantalla), `boc_sync` (con `succeeded`, `failed`, `inserted`, `updated` como
  parámetros) y `section_selected` (con `section_code`). **Ningún dato personal**: los nombres de
  parámetro pasan por el filtro de `AnalyticsEvent`.
- Ningún SDK nuevo se toca fuera de `data`. Room y OkHttp se construyen con funciones factoría en
  `data`, igual que Firebase, porque `core.di` no puede importar el SDK.

---

## 4. Presentación

### 4.1 Estado y eventos

```kotlin
class HomeViewModel(
    observePublications, observeBulletinHeader, refreshPublications,
    getSections, analytics, crashReporter, dispatchers, savedStateHandle,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState>
    fun onRefresh()                       // gesto de deslizar: force = true
    fun onSelectionChanged(HomeSelection) // desde los chips
    fun onRetry()
}

class SectionsViewModel(getSections) : ViewModel() {
    val uiState: StateFlow<SectionsUiState>
    fun onQueryChanged(String)
    fun onToggleExpanded(sectionCode: String)
}
```

`MutableStateFlow` privado, `StateFlow` de solo lectura expuesto. Estado inmutable. Los eventos son
funciones públicas. Ningún composable contiene lógica de negocio.

### 4.2 Componibles sin estado

Todo lo dibujable recibe su estado por parámetro y emite eventos hacia arriba:

```kotlin
@Composable fun HomeContent(state, onRefresh, onRetry, onSelectionChanged, onOpenDrawer,
                            onSearchClick, onInfoClick, onShare, onSave, modifier)
@Composable fun BulletinHeader(header, modifier)
@Composable fun SectionFilterChips(chips, selected, onSelected, modifier)
@Composable fun PublicationCard(publication, onShare, onSave, modifier)
@Composable fun PublicationCardSkeleton(modifier)
@Composable fun SectionsDrawerContent(state, onQueryChanged, onToggleExpanded, onSelected, modifier)
@Composable fun OfflineBanner(modifier)
```

### 4.3 Etiquetas de prueba — son contrato

Renombrarlas rompe pruebas. Cambiarlas exige cambiar también quien las afirma.

| Etiqueta | Dónde |
|---|---|
| `home_skeleton` | Listado en primera carga |
| `home_publications` | Listado con contenido |
| `home_empty` | Estado vacío |
| `home_error` | Estado de error |
| `home_retry` | Acción de reintentar |
| `home_offline_banner` | Aviso de falta de conexión |
| `home_header` | Cabecera editorial |
| `home_header_count` | Distintivo con el recuento |
| `home_chips` | Fila de filtros |
| `home_menu` | Control que abre el panel |
| `home_search` | Lupa de la barra superior |
| `home_info` | Información de la barra superior |
| `publication_card` | Tarjeta (con índice) |
| `publication_share` · `publication_save` | Acciones de la tarjeta |
| `sections_drawer` | Panel lateral |
| `sections_query` | Campo de filtro del panel |
| `section_row_<code>` · `section_toggle_<code>` | Fila y chevron de una sección |
| `bottom_bar` · `bottom_home` · `bottom_search` · `bottom_saved` | Barra inferior |

Las etiquetas heredadas de `core/ui/component` (`home_loading`, `home_empty`, `home_error`,
`home_retry`) llevan prefijo `home_` viviendo en `core`. Se conservan tal cual: renombrarlas es una
ruptura sin ganancia dentro de esta feature.

---

## 5. Navegación

```kotlin
sealed interface Route {
    @Serializable data object Splash : Route
    @Serializable data class  Home(val sectionCode: String? = null,
                                   val subsectionCode: String? = null) : Route
    @Serializable data object Search : Route
    @Serializable data object Saved : Route
}
```

- El arranque sigue siendo el destino inicial y **queda fuera** del armazón con panel y barra
  inferior.
- El arranque navega a `Home()` con `popUpTo(Splash) { inclusive = true }`. **Sin cambios**: la
  afirmación de `SplashBackStackTest` se mantiene.
- Elegir sección navega a `Home(código)` con `popUpTo<Home> { inclusive = true }`: siempre una sola
  entrada de Inicio en la pila (research.md, D-014).
- Los destinos de la barra inferior usan `launchSingleTop` y `popUpTo(Home) { saveState = true }`,
  con `restoreState = true`.
- El retroceso desde Inicio cierra la aplicación, exactamente como hoy.

---

## 6. Contrato visual

Del documento de diseño, apartados 10, 11, 12, 14, 16 y 26. Todo con valores del tema; ni un literal.

| Elemento | Contrato |
|---|---|
| Barra superior | Fondo `surface`. Altura 64 dp más área segura. Escudo 34 dp. Título `titleLarge`. Menú al inicio; lupa e información al final |
| Cabecera editorial | Fondo `primary`. Alto 150–170 dp. Padding `space6`. Título `headlineLarge` en `onPrimary`. Fecha `bodyLarge`. Distintivo perfilado a la derecha con el recuento |
| Chips | Alto 36–40 dp, radio completo, borde 1 dp. Seleccionado: fondo `secondary` y texto blanco. No seleccionado: fondo `surface`, texto `textPrimary`, borde `outline` |
| Tarjeta | Fondo `surface`, radio `medium` (14 dp), padding `space4`, elevación `level1`. Línea de sección de 4 dp con el color del grupo, separada `space3` del contenido |
| Organismo | `labelMedium`, `textSecondary`, máximo 2 líneas |
| Título | `titleMedium`, `textPrimary`, máximo 4 líneas |
| Fecha | Icono 18–20 dp más `bodySmall` en `textSecondary` |
| Acciones | Iconos de 24 dp con área táctil de 48 dp, separadas `space2`, abajo a la derecha |
| Separación entre tarjetas | `space3` (12 dp). Margen horizontal `screenMargin` (16 dp) |
| Esqueletos | Color base `surfaceStrong`, máximo cinco, sin indicador giratorio grande |
| Aviso sin conexión | Banner con icono, forma `BocBannerShape`, que no tapa contenido |
| Panel lateral | Fila de sección de 72 dp mínimo, icono 28 dp, `titleMedium`, chevron y divisor. Subsecciones sobre `surfaceSoft`, radio 12 dp, sangría `space6` |
| Barra inferior | Alto 80 dp más área segura. Icono 24 dp, texto `labelMedium`, separación `space1`. Activo con forma o peso, no solo color |

**Prohibiciones que una regla de Konsist ya vigila**: ningún fichero fuera de `core/ui/theme` importa
`androidx.compose.ui.graphics.Color`; nada importa `isSystemInDarkTheme` ni los esquemas oscuros o
dinámicos.
