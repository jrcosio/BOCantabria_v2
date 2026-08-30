# Data Model: Publicaciones guardadas

**Feature**: `005-publicaciones-guardadas` | **Fase**: 1 | **Fecha**: 2026-08-30

No hay modelo de dominio nuevo. Lo que esta feature añade es **una columna**, y lo interesante es
todo lo que gira alrededor de esa columna para que no la pise nadie.

---

## 1. Cadena completa

```text
SavedScreen ──────────── SavedViewModel ── ObserveSavedPublicationsUseCase ─┐
HomeScreen ───────────── HomeViewModel ─── ObserveSavedKeysUseCase ─────────┤
PublicationDetailScreen ─ …DetailViewModel ─ SetPublicationSavedUseCase ────┤
                                                                            │
                                              SavedPublicationRepository (domain)
                                                                            │
                                              SavedPublicationRepositoryImpl (data)
                                                                            │
                                                     SavedPublicationDao
                                                                            │
                                            tabla `publications`, columna `saved_at`
```

La tabla es la misma que la de Inicio. Lo que cambia es quién escribe qué:

| Escritor | Qué escribe | Qué no puede tocar |
|---|---|---|
| Sincronización (`PublicationDao.upsertAll`) | todo lo que la fuente publica, más `last_seen_at` | `first_seen_at` y **`saved_at`**: no están en el `UPDATE` |
| La persona (`SavedPublicationDao.setSavedAt`) | solo `saved_at` | todo lo demás |

Nadie borra filas. En todo el proyecto sigue sin existir una sentencia de borrado.

---

## 2. Dominio (`domain`) — sin clases nuevas

`Publication` **no cambia** (research.md D-004). La marca no es algo que la fuente publique, y meterla
en el modelo obligaría a rellenarla en el normalizador de feeds, donde no significa nada.

El estado de guardado viaja de dos formas, según lo que la pantalla necesite:

- **La lista**: `List<Publication>`, ya ordenada por el almacén. La pantalla no reordena.
- **El estado de una tarjeta o de un detalle**: `Set<String>` con las claves guardadas. `isSaved` es
  `externalKey in savedKeys`. Un solo flujo sirve a las dos pantallas.

### Contrato de repositorio (`domain/repository/SavedPublicationRepository`)

```text
SavedPublicationRepository
  fun observeSaved(): Flow<List<Publication>>          // orden: saved_at DESC, external_key DESC
  fun observeSavedKeys(): Flow<Set<String>>            // vacío es normal, no es un fallo
  suspend fun setSaved(externalKey: String, saved: Boolean): AppResult<Unit>
```

Mismo contrato que el resto del proyecto:

- Nada lanza. Los fallos viajan como `AppResult.Failure`.
- Ningún guardado es `Success(emptySet())` / `Success(emptyList())`, nunca un fallo.
- `CancellationException` se repropaga **siempre**.
- Los flujos **no terminan con error**: un fallo de lectura local emite lista o conjunto vacío, para
  que la pantalla no se quede sin estado.
- `setSaved` es **idempotente**: guardar lo ya guardado no cambia el instante… ver §4.

---

## 3. Casos de uso (`domain/usecase`)

```text
ObserveSavedPublicationsUseCase()                      → Flow<List<Publication>>
ObserveSavedKeysUseCase()                              → Flow<Set<String>>
SetPublicationSavedUseCase(externalKey, saved)         → AppResult<Unit>
```

Uno solo `operator fun invoke()` cada uno, como manda la convención. Los tres tienen su fichero de
prueba: la regla de Konsist falla la build si una clase de dominio no lo tiene.

---

## 4. Capa de datos (`data`)

### 4.1 La entidad

`PublicationEntity` gana un campo, el último y con valor por defecto:

```text
@ColumnInfo(name = "saved_at") val savedAt: Long? = null
```

y la lista de índices gana `Index(value = ["saved_at"])`, que es lo que sostiene el
`WHERE saved_at IS NOT NULL ORDER BY saved_at DESC`.

`Publication.toEntity(seenAt)` **no lo rellena**: deja el valor por defecto. Es deliberado y es lo que
hace que una inserción venida de la fuente no pueda inventarse una marca. `PublicationEntity.toDomain()`
tampoco lo lee: `Publication` no tiene dónde ponerlo.

### 4.2 El DAO

```text
SavedPublicationDao
  fun observeSaved(): Flow<List<PublicationEntity>>
      SELECT * FROM publications
      WHERE saved_at IS NOT NULL
      ORDER BY saved_at DESC, external_key DESC

  fun observeSavedKeys(): Flow<List<String>>
      SELECT external_key FROM publications WHERE saved_at IS NOT NULL

  suspend fun setSavedAt(externalKey: String, savedAt: Long?): Int
      UPDATE publications SET saved_at = :savedAt WHERE external_key = :externalKey
```

Tres notas que son contrato:

1. **El segundo término del orden, `external_key DESC`, no es decorativo.** Dos marcas puestas en el
   mismo milisegundo empatarían, y sin desempate el orden de la lista podría cambiar entre dos
   lecturas. Es la misma razón por la que las tres consultas del boletín llevan tres términos.
2. **`setSavedAt` devuelve las filas afectadas.** Cero significa que la clave no está almacenada: no
   se crea nada y no se falla. Es lo que permite a la prueba del DAO afirmar ese caso.
3. **Aquí no hay `@Delete` ni `DELETE FROM`**, igual que en `PublicationDao`. Desmarcar es
   `setSavedAt(key, null)`.

### 4.3 La base de datos y su actualización

```text
@Database(
    entities = [PublicationEntity::class, FeedSyncStateEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class BocDatabase : RoomDatabase() {
    abstract fun publicationDao(): PublicationDao
    abstract fun feedSyncStateDao(): FeedSyncStateDao
    abstract fun savedPublicationDao(): SavedPublicationDao      // NUEVO
}
```

- El `2.json` que genera el compilador **se versiona** junto al `1.json`. Sin él, la migración de la
  versión 3 volverá a costar arqueología.
- `bocDatabase()` **no se toca**: sigue siendo un `Room.databaseBuilder(...).build()` limpio. Las
  migraciones automáticas no necesitan `addMigrations`, y `fallbackToDestructiveMigration` no entra
  aquí ni como último recurso (research.md D-002).
- La migración que Room genera **recrea la tabla** —tabla nueva, `INSERT … SELECT` de las dieciséis
  columnas que ya había, borrado de la vieja, renombrado y los siete índices— en lugar de un
  `ALTER TABLE … ADD COLUMN`. Es lo que elige el generador cuando el cambio trae además un índice
  nuevo, y sigue siendo seguro: corre dentro de una transacción y copia las filas. Se anota aquí porque
  leerlo por primera vez en el fichero generado sorprende. La columna es nullable, así que las filas
  existentes quedan como «no guardadas», que es exactamente lo correcto.

### 4.4 El repositorio

`SavedPublicationRepositoryImpl(savedPublicationDao, time, dispatchers, analytics, crashReporter)`.

- Las lecturas mapean a dominio, van en `flowOn(dispatchers.io)` y llevan el mismo `.catch` que las de
  publicaciones: repropagar la cancelación, registrar el fallo como no fatal y emitir vacío.
- `setSaved(key, true)` escribe `time.nowMillis()`; `setSaved(key, false)` escribe `null`. El tiempo se
  **inyecta**: es lo que hace comprobable el orden de la lista sin esperar milisegundos reales.
- Emite el evento de analítica `publication_save` con un único parámetro (`saved` → `"true"` /
  `"false"`). Ni clave, ni título, ni sección (research.md D-012, FR-025).
- Un fallo de escritura devuelve `AppResult.Failure(DomainError.Unknown)`. `DomainError` no crece
  (D-013).

**Sobre la idempotencia**: `setSaved(key, true)` sobre algo ya guardado reescribe el instante y por
tanto puede subir el elemento al principio de la lista. No es un problema en la práctica —la interfaz
solo ofrece la acción contraria a lo que muestra— y la alternativa (leer antes de escribir) añadiría
una lectura y una carrera para evitar un caso que la pantalla no produce. Queda dicho aquí en lugar de
descubrirse leyendo el SQL.

---

## 5. Presentación

### 5.1 Guardados (`ui/saved`)

```text
SavedUiState
  content:    SavedContentState = SavedContentState.Empty
  share:      ShareState        = ShareState.Idle
  saveFailed: Boolean           = false

SavedContentState
  Publications(items: List<Publication>)
  Empty
```

**No hay `Skeleton` ni `Error`**, y es una decisión: lo que se lee es local e inmediato, así que no hay
espera que amortiguar, y un fallo de lectura ya emite lista vacía por contrato. Inventar los dos
estados obligaría a la pantalla a distinguir «vacío» de «no he podido leer» sin tener con qué.

`share` va **fuera** del sellado por la misma razón que en Inicio y en el detalle: es un eje
independiente. Se puede estar preparando algo para compartir con la lista en pantalla.

Eventos del modelo: `onToggleSaved(publication)`, `onShare(publication)`, `onShareConsumed()`,
`onSaveFailureConsumed()`.

`saveFailed` es una señal de un solo uso: la pantalla la muestra y la consume. Está en los **tres**
estados —Guardados, Inicio y detalle— porque guardar se dispara desde los tres, y las tres la
dibujan con la misma pieza compartida (FR-009). La otra mitad del requisito sale gratis: `isSaved` se
deriva de lo almacenado, así que una escritura fallida deja el icono como estaba.

### 5.2 Inicio (`ui/home`)

`HomeUiState` gana `savedKeys: Set<String> = emptySet()` y `saveFailed: Boolean = false`. El
`combine` del modelo pasa de cuatro
flujos a cinco. `HomeScreen` **pierde** el parámetro `onSave`: guardar deja de ser algo que el armazón
resuelve con un aviso y pasa a ser un evento del propio modelo, como compartir.

### 5.3 Detalle (`ui/detail`)

`PublicationDetailUiState` gana `isSaved: Boolean = false`, derivado del conjunto de claves, y
`saveFailed: Boolean = false`. El icono
de la barra superior se rellena con él, y **no se dibuja** cuando no hay publicación que guardar
(FR-008). `PublicationDetailRoute` cambia el aviso de «Próximamente» por `viewModel::onToggleSaved`.

### 5.4 La tarjeta (`core/ui/component`)

`PublicationCard` gana `isSaved: Boolean`. Con él elige el vector —`ic_bookmark` contorneado o
`ic_bookmark_filled`— y el texto accesible —«Guardar» o «Quitar de guardados»—. En Guardados
`isSaved` es siempre `true`.

---

## 6. Transiciones de la marca

```text
                    setSaved(key, true)
    ┌──────────────┐ ─────────────────▶ ┌──────────────────────────┐
    │ no guardada  │                    │ guardada (saved_at = t)  │
    │ saved_at NULL│ ◀───────────────── │                          │
    └──────────────┘  setSaved(key,false)└──────────────────────────┘
            ▲                                        │
            │                                        │
            └─── una sincronización NO provoca ───────┘
                 esta transición: la columna no
                 está en el UPDATE de la fuente
```

Dos estados y dos transiciones, las dos disparadas por la persona. Ningún otro camino las produce:
ni la sincronización, ni la purga de documentos, ni la actualización de la aplicación. Eso es lo que
dicen FR-020, FR-022 y FR-023, y lo que comprueban SC-004 y SC-006.
