# Data Model: Detalle de publicación y visor del PDF oficial

**Feature**: `004-detalle-publicacion` | **Fase**: 1 | **Fecha**: 2026-08-30

La feature 003 dejó la `Publication` guardada y observable. Esta añade una sola cosa: **la copia
local del documento oficial**, y el estado de esa copia.

---

## 1. Cadena completa

```text
Tarjeta pulsada  ──▶  Route.Detail(externalKey)
                          │
                          ├── observePublication(externalKey) ──▶ Publication?      (ya existía)
                          │
                          └── observeDocument(externalKey) ────▶ DocumentStatus
                                    │
                                    │  Absent → se pide
                                    ▼
                          DocumentCache  ──miss──▶  DocumentDownloader
                                    │                     │ https · host · content-type · %PDF
                                    │                     │ streaming con tope · SHA-256
                                    │◀────fichero temporal validado y renombrado────┘
                                    ▼
                          OfficialDocument(localPath) ──▶ PdfViewerScreen ──▶ PdfViewer
```

---

## 2. Dominio (`domain/model`) — Kotlin puro

### `OfficialDocument`

La copia local del PDF de una publicación.

| Campo | Tipo | Regla |
|---|---|---|
| `externalKey` | `String` | A qué publicación pertenece. Es su identidad |
| `localPath` | `String` | Dónde está en el dispositivo. `String` y no `File`: el dominio no ve la plataforma (research.md D-007) |
| `byteCount` | `Long` | Lo que ocupa. Base del tope de la caché |
| `checksum` | `String` | SHA-256 de lo recibido, en hexadecimal |
| `lastUsedAt` | `Long` | Milisegundos. Base de la retirada por antigüedad |

Invariantes: `externalKey` y `localPath` no vacíos; `byteCount` mayor que cero; `checksum` de 64
caracteres hexadecimales.

**No lleva la URL de origen**: la tiene la `Publication`, y duplicarla aquí sería crear una segunda
verdad que puede quedarse atrás.

### `DocumentStatus`

En qué punto está la copia local. Es lo que el detalle y el visor observan.

```text
DocumentStatus
├── Absent                                  no se ha pedido nunca, o se retiró de la caché
├── Downloading(bytesRead, totalBytes?)     totalBytes es nulo si el servicio no lo declara
├── Available(document: OfficialDocument)
└── Failed(error: DomainError)
```

`Downloading` lleva el progreso porque FR-032 obliga a decir que se está preparando. `totalBytes` es
anulable a propósito: el servicio puede no enviar `Content-Length` y la barra tendrá que ser
indeterminada, que es la verdad.

### `ShareTarget`

Qué se acabó ofreciendo al compartir, y por qué.

```text
ShareTarget
├── Document(document: OfficialDocument)    lo normal
└── Link(url: String, reason: LinkReason)   el caso degradado, con su motivo

LinkReason
└── NO_CONNECTION
```

Existe para que la pantalla pueda **explicar** el caso degradado en lugar de que sorprenda
(FR-033). Un booleano no habría podido decir por qué.

### `DetailTab`

`DOCUMENT` · `AI_SUMMARY`. Sobrevive al cambio de configuración (FR-015). Vive en `domain` porque
las dos pestañas son parte de lo que la feature promete, no una decisión de dibujo.

> **Enmienda (30 de agosto de 2026).** Había un tercer valor, `ASK`. Preguntar es ahora una pantalla
> (`Route.Ask`), no una pestaña. La restauración de la pestaña guardada se hace **por nombre y con
> respaldo**, nunca con `valueOf`: una pestaña retirada entre versiones tumbaría la pantalla al
> volver de la muerte del proceso, en el único camino que nadie recorre a mano.

---

## 3. Contratos de repositorio (`domain/repository`)

```text
DocumentRepository
  fun observeDocument(externalKey: String): Flow<DocumentStatus>
  suspend fun ensureLocalCopy(publication: Publication): AppResult<OfficialDocument>
  suspend fun releaseUnused()                    // retirada por tope y por antigüedad

PublicationRepository                            // AMPLIADO
  fun observePublication(externalKey: String): Flow<Publication?>
```

Reglas heredadas: **nunca lanzan**; el fallo viaja como `AppResult.Failure`;
`CancellationException` se repropaga siempre; las excepciones se traducen dentro de `data`.

`observePublication` emite `null` cuando la publicación ya no está guardada, y eso **no** es un
fallo: es la información que FR-004 necesita.

---

## 4. Capa de datos (`data`)

### 4.1 El descargador

```text
DocumentDownloader
  suspend fun download(url: String, into: File): DownloadResult

DownloadResult
├── Downloaded(byteCount: Long, checksum: String)
└── Rejected(reason: RejectionReason)

RejectionReason
├── INSECURE_SCHEME        no es https
├── UNEXPECTED_HOST        no es el servicio del boletín
├── UNEXPECTED_TYPE        el Content-Type no es application/pdf
├── NOT_A_PDF              los primeros bytes no son %PDF
├── TOO_LARGE              supera el tope
├── HTTP_ERROR(code)
└── NETWORK
```

Devuelve el rechazo **como valor**, igual que la fuente de feeds de la feature 003 y por la misma
razón: el motivo tiene que llegar arriba para poder contarlo, y una excepción por cada forma de
desconfiar convertiría el repositorio en una escalera de capturas.

**Orden de comprobación, y por qué importa**: esquema y host se miran **antes de conectar**; el tipo
declarado, en cuanto llegan las cabeceras; los bytes mágicos, sobre los primeros del cuerpo. Se
rechaza en cuanto se sabe, para no descargar cinco megas de una página de error.

### 4.2 La caché

```text
DocumentCache
  suspend fun get(externalKey: String): OfficialDocument?
  suspend fun put(externalKey, temp: File, byteCount, checksum): OfficialDocument
  suspend fun evict(maxBytes: Long, keepAtLeast: Int)
  fun fileFor(externalKey: String): File
```

`cacheDir/documents/<externalKey>.pdf`. La escritura va primero a `<externalKey>.pdf.part` y solo
se renombra cuando la validación pasa: un renombrado dentro del mismo sistema de ficheros es
atómico, así que **nunca existe un fichero a medias con el nombre bueno** (FR-019).

`externalKey` puede traer `:` y `/` —`boc:439765`, o una URL entera cuando el enlace no lleva
identificador—, así que el nombre de fichero se deriva con una huella del `externalKey`, no con el
`externalKey` en crudo. Una clave que se cuele en una ruta es una forma conocida de escribir donde
no se debe.

---

## 5. Presentación

### 5.1 Detalle (`ui/detail`)

```text
PublicationDetailUiState                     (data class inmutable)
  publication: Publication?                  null mientras carga o si ya no existe
  section: BocSection?
  isMissing: Boolean                         la publicación no está entre lo guardado
  selectedTab: DetailTab
  document: DocumentStatus
  share: ShareState

ShareState
├── Idle
├── Preparing
└── Ready(target: ShareTarget)               evento de un solo uso: se consume y vuelve a Idle
```

`document` y `share` van **fuera** de un sellado único, como en Inicio: se puede estar preparando
algo para compartir mientras el documento ya está disponible, y meterlo todo en una jerarquía
multiplicaría los casos sin que ninguno aportara.

### 5.2 Visor (`ui/pdf`)

```text
PdfViewerUiState
├── Loading
├── Ready(document: OfficialDocument, title: String)
└── Error(error: DomainError)
```

La página visible y la ampliación **no** están aquí: las lleva `PdfViewerState`, que es del visor.
Lo único que se guarda a mano es la primera página visible, con `rememberSaveable`, para
restaurarla tras un cambio de configuración (research.md D-010).

### Transiciones del documento

```text
   Absent ──se entra en la pestaña Documento──▶ Downloading ──ok──▶ Available
      ▲                                              │                   │
      │                                              │ rechazo o red     │ retirada de la caché
      │                                              ▼                   │
      └──────────────onRetry()────────────────── Failed ◀────────────────┘
```

Salir de la pantalla durante `Downloading` cancela el trabajo y **no** deja rastro: el fichero
temporal se borra (FR-023).
