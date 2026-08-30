# Contratos internos: Publicaciones guardadas

**Feature**: `005-publicaciones-guardadas` | **Fase**: 1 | **Fecha**: 2026-08-30

Lo que cada capa promete a la de arriba. Un contrato roto aquí es un fallo de revisión, no una
cuestión de gusto.

---

## 1. `domain` hacia el resto del mundo

### 1.1 El repositorio de lo guardado no lanza

```kotlin
interface SavedPublicationRepository {
    fun observeSaved(): Flow<List<Publication>>
    fun observeSavedKeys(): Flow<Set<String>>
    suspend fun setSaved(externalKey: String, saved: Boolean): AppResult<Unit>
}
```

| Promesa | Qué significa exactamente |
|---|---|
| Nada lanza | Toda excepción se captura y se traduce en `data`. Una pantalla nunca ve un `Throwable` |
| `CancellationException` se repropaga | Sin excepción y en todos los `catch`. Cancelar un ámbito tiene que cancelarlo |
| Los flujos no terminan con error | Un fallo de lectura local **emite vacío** y sigue vivo. Un flujo terminado deja la pantalla sin estado, que se lee como una aplicación colgada |
| Vacío no es fallo | Sin nada guardado es `emptyList()` / `emptySet()`. Es un estado normal de la pantalla |
| El orden lo pone el almacén | `observeSaved()` emite ya ordenado por instante de guardado descendente, con la clave externa como desempate. La pantalla **no** reordena |
| `setSaved` devuelve `AppResult<Unit>` | Éxito es que la escritura ocurrió. Un fallo es `DomainError.Unknown` |

### 1.2 Los casos de uso

```kotlin
class ObserveSavedPublicationsUseCase(private val repository: SavedPublicationRepository) {
    operator fun invoke(): Flow<List<Publication>>
}

class ObserveSavedKeysUseCase(private val repository: SavedPublicationRepository) {
    operator fun invoke(): Flow<Set<String>>
}

class SetPublicationSavedUseCase(private val repository: SavedPublicationRepository) {
    suspend operator fun invoke(externalKey: String, saved: Boolean): AppResult<Unit>
}
```

- Un único `operator fun invoke()` cada uno. Ninguno añade lógica sobre el repositorio: son la
  frontera que impide que `ui` conozca a `data`, y eso es todo lo que tienen que ser.
- Los tres viven en `domain.usecase` y los tres tienen fichero de prueba. Konsist comprueba las dos
  cosas y falla la build si faltan.

### 1.3 `DomainError` no crece

Sigue con `Network` y `Unknown`. Un fallo del almacén es `Unknown`: no hay nada que la pantalla pueda
hacer distinto según el motivo (research.md D-013).

### 1.4 `Publication` no cambia

Ni un campo. La marca no es algo que la fuente publique. Si en una revisión aparece un `savedAt` en el
modelo de dominio, hay que rechazarlo (research.md D-004).

---

## 2. `data` hacia `domain`

### 2.1 Reglas de escritura, no negociables

1. **`saved_at` no aparece en `PublicationDao.updateColumns`.** Es lo que hace que una sincronización
   no pueda perder una marca. Si alguien la añade a esa sentencia, FR-020 deja de cumplirse y la
   prueba de regresión del DAO se pone roja: **está para eso**.
2. **`Publication.toEntity(seenAt)` deja `savedAt` en su valor por defecto.** Una inserción venida de
   la fuente no puede inventarse una marca.
3. **No existe ninguna sentencia de borrado en ningún DAO del proyecto.** Desmarcar es
   `setSavedAt(key, null)`. Un `@Delete` o un `DELETE FROM` en una revisión se rechaza.
4. **El tiempo se inyecta.** El instante de la marca sale de `TimeProvider`, nunca de
   `System.currentTimeMillis()` en el sitio de uso.
5. **La entidad no cruza a `ui`.** El DAO devuelve `PublicationEntity`; el repositorio mapea a
   `Publication`.

### 2.2 El DAO

```kotlin
@Dao
interface SavedPublicationDao {
    fun observeSaved(): Flow<List<PublicationEntity>>   // saved_at DESC, external_key DESC
    fun observeSavedKeys(): Flow<List<String>>
    suspend fun setSavedAt(externalKey: String, savedAt: Long?): Int
}
```

- El desempate por `external_key` es obligatorio: sin él dos marcas del mismo milisegundo podrían
  ordenarse distinto entre dos lecturas.
- `setSavedAt` devuelve filas afectadas. **Cero es un resultado legítimo**: la clave no está
  almacenada. No se crea nada y no se falla.

### 2.3 El almacén y su actualización

| Promesa | Qué significa |
|---|---|
| Versión 2 con `AutoMigration(1, 2)` | La columna se añade contra el esquema exportado |
| El `2.json` se versiona | Igual que el `1.json`. Es el material de la migración siguiente |
| `bocDatabase()` sigue limpio | Sin `addMigrations` (las automáticas no lo necesitan) y **sin** `fallbackToDestructiveMigration` en ningún caso |
| Una instalación anterior conserva sus filas | Comprobado por `BocDatabaseMigrationTest` sobre el camino real, `Room.databaseBuilder` |

### 2.4 Analítica

```text
publication_save   { saved: "true" | "false" }
```

Emitido por `SavedPublicationRepositoryImpl`, un solo sitio (research.md D-012). **Nunca** viaja la
clave, el título, la sección ni la fecha: qué guarda una persona es una señal de interés personal y el
principio VI lo prohíbe.

`SavedViewModel` registra su vista de pantalla con el nombre `saved`.

---

## 3. Presentación

### 3.1 Estado y eventos

```kotlin
data class SavedUiState(
    val content: SavedContentState = SavedContentState.Empty,
    val share: ShareState = ShareState.Idle,
)

sealed interface SavedContentState {
    data class Publications(val items: List<Publication>) : SavedContentState
    data object Empty : SavedContentState
}
```

| Regla | Motivo |
|---|---|
| `MutableStateFlow` privado, `StateFlow` de solo lectura | Principio III |
| `share` fuera del sellado | Eje independiente, como en Inicio y en el detalle |
| Sin `Skeleton` ni `Error` | La lectura es local e inmediata y un fallo ya emite vacío por contrato |
| Eventos como funciones públicas | `onToggleSaved(publication)`, `onShare(publication)`, `onShareConsumed()` |
| Guardar y compartir están protegidos contra el segundo toque | Igual que en Inicio: un `Job` en curso descarta el siguiente |

`HomeUiState` gana `savedKeys: Set<String>`. `PublicationDetailUiState` gana `isSaved: Boolean`.

### 3.2 Componibles sin estado

| Componible | Contrato |
|---|---|
| `SavedContent(state, onOpenPublication, onShare, onToggleSaved, onExplore)` | Todo entra por parámetro y todo sale como evento. Se monta con `createComposeRule()` sin arrancar el grafo ni cruzar la portada |
| `PublicationCard(publication, section, formattedDate, isSaved, onClick, onShare, onSave)` | Vive en `core/ui/component`. `onSave` sigue sin parámetros: quien la coloca cierra sobre la publicación |
| `IllustratedMessage(iconRes, title, description, action)` | Columna centrada con los tokens del apartado 26.3. `action` es opcional: sin ella, `ComingSoonMessage` |
| `ComingSoonMessage(iconRes, description)` | **Su firma y su etiqueta no cambian.** Pasa a delegar en `IllustratedMessage` |

### 3.3 Etiquetas de prueba — son contrato

| Etiqueta | Constante | Dónde |
|---|---|---|
| `publication_card` | `TAG_PUBLICATION_CARD` | `core/ui/component` (**mudada**, mismo valor) |
| `publication_save` | `TAG_PUBLICATION_SAVE` | ídem |
| `publication_share` | `TAG_PUBLICATION_SHARE` | ídem |
| `saved_list` | `TAG_SAVED_LIST` | `ui/saved` — la lista |
| `saved_empty` | `TAG_SAVED_EMPTY` | `ui/saved` — el estado vacío |
| `saved_empty_action` | `TAG_SAVED_EMPTY_ACTION` | `ui/saved` — «Explorar el BOC» |
| `detail_save` | `TAG_DETAIL_SAVE` | `ui/detail`, sin cambios |
| `bottom_saved` | `TAG_BOTTOM_SAVED` | `ui/navigation`, sin cambios |

**El valor de una etiqueta mudada no cambia.** Mover el fichero cambia el `import` de las pruebas, no
lo que buscan.

**`coming_soon` deja de aparecer en Guardados.** `BottomBarNavigationTest` afirmaba hoy lo contrario y
hay que actualizarlo: es la prueba que demuestra que el marcador se ha retirado de verdad.

### 3.4 Navegación

```text
Route.Saved                       // sin argumentos, sin cambios
```

- `MainShell` pasa a `composable<Route.Saved>` dos cosas: `onOpenPublication`, la **misma** lambda que
  usa Inicio y que desemboca en `Route.Detail` del grafo exterior, y `onExplore`, que lleva a
  `Route.Home()` por el mismo camino que la barra inferior.
- El detalle sigue en el grafo exterior: abierto desde Guardados **no** dibuja la barra inferior, igual
  que abierto desde Inicio.
- La barra inferior mantiene `saveState`/`restoreState`, que es lo que conserva la posición de lectura
  de la lista al volver (FR-018).
- `MainShell` deja de pasar `onSave` a `HomeScreen`: el aviso de «Próximamente» desaparece del armazón.

---

## 4. Contrato visual

Del documento de diseño, y verificable:

| Elemento | Regla |
|---|---|
| Marcador guardado | Trazado **relleno**, color `Primary` (apartado 12) |
| Marcador no guardado | Trazado contorneado, 24 dp (apartado 12) |
| Barra superior de Guardados | Fondo `Primary`, título «Guardados», sin acciones (apartado 22.1, con la enmienda) |
| Tarjetas | La **estándar**, la misma que Inicio (enmienda del apartado 22.2) |
| Estado vacío | Icono de 96 dp, título `TitleLarge`, texto `BodyMedium`, acción secundaria (apartados 22.3 y 26.3) |
| Lista | Márgenes de pantalla a los lados, separación `space3`, holgura inferior `space10` |
| Colores y medidas | Del tema. Ningún literal fuera de `core/ui/theme`; hay una regla de Konsist que lo hace cumplir |
| Área táctil | Mínimo 48 × 48 dp para los dos iconos de la tarjeta (apartado 31.2) |
