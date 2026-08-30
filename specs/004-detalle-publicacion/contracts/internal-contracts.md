# Contratos internos: Detalle de publicación y visor del PDF

**Feature**: `004-detalle-publicacion` | **Fase**: 1 | **Fecha**: 2026-08-30

Lo que cada capa promete a la de al lado. Romper cualquiera de estas promesas debe romper una prueba.

---

## 1. `domain` hacia el resto del mundo

### 1.1 Los repositorios nunca lanzan

```kotlin
interface DocumentRepository {
    fun observeDocument(externalKey: String): Flow<DocumentStatus>
    suspend fun ensureLocalCopy(publication: Publication): AppResult<OfficialDocument>
    suspend fun releaseUnused()
}

interface PublicationRepository {                       // AMPLIADO
    fun observePublication(externalKey: String): Flow<Publication?>
    // …lo demás, sin cambios
}
```

- Nada lanza. El fallo viaja como `AppResult.Failure(DomainError)`.
- `observePublication` emite `null` si la publicación no está guardada. **No es un fallo.**
- `observeDocument` no termina con error: emite `DocumentStatus.Failed`.
- `CancellationException` **se repropaga siempre**.
- `ensureLocalCopy` es **idempotente y deduplicada**: dos llamadas concurrentes para la misma clave
  comparten una sola descarga (FR-022).

### 1.2 Los casos de uso

```kotlin
class ObservePublicationUseCase(repository)
    operator fun invoke(externalKey: String): Flow<Publication?>

class ObserveOfficialDocumentUseCase(repository)
    operator fun invoke(externalKey: String): Flow<DocumentStatus>

class OpenOfficialDocumentUseCase(repository)
    suspend operator fun invoke(publication: Publication): AppResult<OfficialDocument>

class ShareOfficialDocumentUseCase(documents, connectivity)
    suspend operator fun invoke(publication: Publication): AppResult<ShareTarget>
    // Documento si está o puede obtenerse; enlace con su motivo si no hay conexión (FR-031, FR-033)
```

`ShareOfficialDocumentUseCase` es el único sitio donde vive la regla de degradación. La pantalla no
decide entre fichero y enlace: pregunta y obedece.

### 1.3 `DomainError` no crece

Sigue con `Network` y `Unknown`. Un rechazo de validación es `Unknown` (research.md D-015).

---

## 2. `data` hacia `domain`

### 2.1 Política del repositorio de documentos

| Situación | `ensureLocalCopy` | `observeDocument` emite |
|---|---|---|
| Ya está en caché | `Success` inmediato, refresca `lastUsedAt` | `Available` |
| No está y se descarga bien | `Success` | `Downloading…` → `Available` |
| No está y el servicio falla | `Failure(Network)` | `Downloading…` → `Failed(Network)` |
| No está y lo devuelto no es un PDF | `Failure(Unknown)` | `Downloading…` → `Failed(Unknown)` |
| Ya hay una descarga en curso | Espera a la misma y devuelve su resultado | `Downloading…` compartido |
| Se cancela la corrutina | Repropaga `CancellationException` | deja de emitir |

### 2.2 Reglas de escritura, no negociables

- **Se escribe a un temporal y se renombra al final.** Nunca existe un fichero con el nombre bueno y
  contenido a medias.
- **Un rechazo o un fallo borran el temporal.** Cero restos (FR-019).
- **El nombre del fichero se deriva de una huella del `externalKey`**, no de la clave en crudo: la
  clave puede contener `:` y `/`.
- La retirada por tope y por antigüedad **nunca** borra el documento que se está usando.

### 2.3 Contrato del descargador

```kotlin
interface DocumentDownloader {
    suspend fun download(url: String, into: File): DownloadResult
}
```

Devuelve el rechazo como valor, no como excepción — igual que `PublicationRemoteDataSource` y por la
misma razón. Garantías de la implementación, en este orden:

1. **Antes de conectar**: esquema `https` y host `boc.cantabria.es`. Si no, `Rejected` sin abrir
   socket.
2. **Con las cabeceras**: `Content-Type` que contenga `application/pdf`. Si no, `Rejected` sin leer
   el cuerpo.
3. **Con los primeros bytes**: `%PDF`. Si no, `Rejected`.
4. **Durante el cuerpo**: corte al superar el tope de tamaño.
5. Al terminar: SHA-256 de lo escrito.

Límites: conexión 10 s, lectura 60 s, total 180 s. Un documento tarda más que un feed, y por eso el
cliente se **deriva** del existente en lugar de reutilizar sus tiempos (research.md D-004).

---

## 3. Transversal

- Los `Dispatchers` se inyectan. La descarga y la escritura van en `io`.
- `TimeProvider` marca `lastUsedAt` y decide la antigüedad: sin él la retirada no sería comprobable.
- Telemetría: `document_opened` con `cached` (`true`/`false`) y `document_share` con `target`
  (`document`/`link`). **Sin datos personales**: ni el título, ni la URL, ni la clave.
- Ningún SDK nuevo fuera de `data` y `ui/pdf`.

---

## 4. Presentación

### 4.1 Estado y eventos

```kotlin
class PublicationDetailViewModel(
    savedStateHandle, observePublication, observeDocument, openDocument,
    shareDocument, getSections, analytics,
) : ViewModel() {
    val uiState: StateFlow<PublicationDetailUiState>
    fun onTabSelected(tab: DetailTab)
    fun onDocumentTabShown()          // dispara la obtención: no se descarga al abrir el detalle
    fun onShare()
    fun onShareConsumed()
    fun onRetry()
}

class PdfViewerViewModel(savedStateHandle, observeDocument, openDocument) : ViewModel() {
    val uiState: StateFlow<PdfViewerUiState>
    fun onRetry()
}
```

`MutableStateFlow` privado, `StateFlow` de solo lectura. Estado inmutable. Ningún componible con
lógica de negocio.

### 4.2 Componibles sin estado

```kotlin
@Composable fun PublicationDetailContent(state, onBack, onSave, onShare, onTabSelected,
                                         onDocumentTabShown, onOpenDocument, onAsk, onRetry, modifier)
@Composable fun DocumentHeader(publication, section, formattedDate, modifier)
@Composable fun DetailTabs(selected, onSelect, modifier)
@Composable fun DocumentTab(publication, section, status, onRetry, modifier)
@Composable fun ComingSoonTab(kind: ComingSoonKind, modifier)
@Composable fun DetailActionBar(onOpenDocument, onAsk, enabled, modifier)
@Composable fun PdfViewerContent(state, onBack, onShare, onRetry, modifier)
```

### 4.3 Etiquetas de prueba — son contrato

| Etiqueta | Dónde |
|---|---|
| `detail_back` · `detail_save` · `detail_share` | Barra superior |
| `detail_header` · `detail_section` · `detail_title` · `detail_issuer` · `detail_date` · `detail_official_badge` | Cabecera |
| `detail_tabs` · `detail_tab_document` · `detail_tab_summary` | Pestañas |
| `detail_list` | La lista que desplaza cabecera y contenido |
| `detail_metadata` · `detail_preview` · `detail_preview_loading` · `detail_preview_error` | Pestaña Documento |
| `detail_action_open` · `detail_action_ask` | Barra de acciones |
| `detail_missing` | La publicación ya no está guardada |
| `ask_screen` · `ask_back` | Pantalla de preguntar |
| `pdf_viewer` · `pdf_viewer_loading` · `pdf_viewer_error` · `pdf_viewer_share` · `pdf_viewer_back` | Visor |

Se reutilizan `TAG_ERROR`, `TAG_RETRY` y `TAG_COMING_SOON` de `core/ui/component`: el error del
visor no puede tener un estilo propio (apartado 34 del documento de diseño).

---

## 5. Navegación

```kotlin
sealed interface Route {
    @Serializable data object Splash : Route
    @Serializable data class  Home(sectionCode: String? = null, subsectionCode: String? = null) : Route
    @Serializable data object Search : Route
    @Serializable data object Saved : Route
    @Serializable data class  Detail(val externalKey: String) : Route          // NUEVO
    @Serializable data class  PdfViewer(val externalKey: String) : Route       // NUEVO
    @Serializable data class  Ask(val externalKey: String) : Route             // NUEVO
}
```

- `Detail` y `PdfViewer` van en el `NavHost` **exterior**, junto al arranque: no llevan barra
  inferior (research.md D-005).
- La tarjeta navega a `Detail(publication.externalKey)`. Se apila: el retroceso vuelve al boletín
  **en su posición**, que es lo que FR-005 pide.
- `Abrir PDF oficial` navega a `PdfViewer(externalKey)`, que se apila sobre el detalle.
- `Preguntar` de la barra de acciones navega a `Ask(externalKey)`, también en el host exterior y
  también apilada. Lleva la clave aunque el marcador de posición no la lea: la conversación que
  venga será sobre *ese* documento, y añadir el argumento después obligaría a cambiar una ruta que
  ya estaría en la calle.
- El retroceso desde el detalle **no** cierra la aplicación: vuelve a Inicio. Solo desde Inicio
  cierra, como hasta ahora.

---

## 6. Contrato visual

Del documento de diseño, apartados 10.3, 11, 18, 19, 20, 24, 26 y 31. Todo con valores del tema.

| Elemento | Contrato |
|---|---|
| Barra superior | Fondo `primary`, iconos y texto blancos. Atrás, escudo de 32 dp, título, guardar y compartir. El escudo puede omitirse en pantallas estrechas |
| Cabecera | Fondo `surface`, padding 20 dp horizontal y 24 dp vertical. Sección en `labelLarge` y `primary`; título en `headlineLarge` **sin recortar**; organismo y fecha con icono; distintivo perfilado `Documento oficial` |
| Pestañas | Alto 56 dp, indicador inferior de 3 dp, activa en `primary`, inactiva en `textSecondary`. La de resumen lleva el icono de IA |
| Ficha de metadatos | Tarjeta `surface`, radio `medium`, etiquetas en `labelMedium` y valores en `bodyLarge`, con separadores |
| Previsualización | La primera página sobre fondo `surfaceStrong`, con sombra suave |
| Pestañas aplazadas | Icono y etiqueta de IA en `aiAccent` sobre `aiContainer`, con el texto de «Próximamente». Identidad conservada, función no |
| Barra de acciones | Fondo `surface` con borde superior, padding 12–16 dp más área segura. `Abrir PDF oficial` principal, `Preguntar` secundario. **Se apilan** si no caben |
| Visor | Zona de documento sobre gris neutro, páginas blancas separadas 12 dp. Barra superior propia |
| Todo control | Área táctil mínima de 48 × 48 dp (apartado 31.2) |

**Prohibiciones que una regla de Konsist ya vigila**: ningún fichero fuera de `core/ui/theme` importa
`androidx.compose.ui.graphics.Color`; nada importa `isSystemInDarkTheme` ni los esquemas oscuros o
dinámicos.
