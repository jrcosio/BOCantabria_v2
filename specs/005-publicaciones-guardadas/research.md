# Research: Publicaciones guardadas

**Feature**: `005-publicaciones-guardadas` | **Fase**: 0 | **Fecha**: 2026-08-30

Quince decisiones. Las cuatro primeras deciden dónde vive la marca y cómo se actualiza el almacén
sin perder lo que ya hay; el resto son de forma. Cada una lleva lo que se descartó, porque dentro de
un año lo descartado es lo que nadie recuerda.

---

## D-001: Una columna en la tabla de publicaciones, no una tabla aparte

**Decisión**: la marca es una columna `saved_at INTEGER` **nullable** en la tabla `publications`.
Guardada es `saved_at IS NOT NULL`; el instante que contiene es el que ordena la lista.

**Rationale**: `PublicationDao.updateColumns` no es un `UPDATE` genérico: es una **lista blanca de
columnas**, escrita a mano y con un comentario que explica por qué. Se diseñó para que
`first_seen_at` sobreviva a una sincronización posterior, y hay una prueba que lo fija
(`PublicationDaoTest`, «an update refreshes the last sighting and never rewrites the first»).
`saved_at` entra por esa misma puerta: no aparece en la sentencia, y el `@Insert` es
`OnConflictStrategy.IGNORE`, que solo actúa sobre filas que aún no existen. De ahí que **ninguna
sincronización pueda pisar una marca** (FR-020), y que la garantía no dependa de que nadie llame a
nada: depende de que la columna no esté en el `UPDATE`.

También hace la consulta trivial: un `WHERE saved_at IS NOT NULL ORDER BY saved_at DESC`, sin unión,
sin combinar dos flujos y sin decidir qué hacer cuando una de las dos partes falta.

**Alternativas descartadas**:
- **Tabla `saved_publications` con la clave y el instante**: separa lo que la persona posee de lo que
  la fuente escribe, que es un argumento real. Pero obliga a una unión o a combinar dos flujos para
  cada pantalla, y desmarcar sería un `DELETE` —legal en esa tabla, pero introduce en el proyecto la
  primera sentencia de borrado y con ella la conversación de si la regla sigue significando lo que
  dice—. Ante la duda, gana la opción más simple (Governance de la constitución).
- **Columna booleana `saved`**: no puede ordenar por el momento en que se guardó, que es lo que pide
  FR-011. Habría que añadir el instante igualmente, y entonces el booleano es información duplicada
  que puede contradecirse.
- **Preferencias o `DataStore`**: un segundo almacén para un dato que se lee junto a las
  publicaciones, en la misma consulta y con el mismo orden. Obligaría a cruzar dos fuentes en memoria
  y a mantenerlas de acuerdo. La feature 002 ya declinó introducir un almacén de preferencias por no
  tener nada que guardar; esto tampoco lo justifica.

---

## D-002: La base de datos sube a la versión 2 con migración automática

**Decisión**: `@Database(version = 2, autoMigrations = [AutoMigration(from = 1, to = 2)])`. El
esquema `2.json` que genera el compilador se **versiona** junto al `1.json` que ya está. La función
`bocDatabase()` no cambia: sigue siendo un `Room.databaseBuilder(...).build()` limpio.

**Rationale**: añadir una columna nullable es el caso que la migración automática resuelve entera y
sin ambigüedad, contra el esquema exportado. Escribir la sentencia a mano sería reproducir lo que el
compilador ya sabe generar, con el riesgo añadido de que la huella del esquema no coincida y la
aplicación se caiga al abrir. El esquema se exporta desde la versión 1 precisamente para este día: es
lo que dice el comentario de `BocDatabase`, escrito en la feature 003.

Lo importante es lo que **no** se hace: `fallbackToDestructiveMigration()` no entra. Con él la
actualización pasaría la puerta de compilación y borraría el boletín almacenado de todo el mundo en
silencio, que es justo lo que FR-023 prohíbe.

**Alternativas descartadas**:
- **`Migration(1, 2)` a mano**: más código para el mismo `ALTER TABLE`, y hay que copiar la sentencia
  del `2.json` generado para que la huella cuadre. Se reserva para cuando una migración tenga que
  transformar datos, que no es el caso.
- **`fallbackToDestructiveMigration()`**: pierde el boletín. Incumple FR-023 y SC-006.
- **No versionar `2.json`**: dejaría al proyecto sin nada contra lo que comparar cuando llegue la 3, y
  volvería a costar arqueología. Es el error que la feature 003 se molestó en evitar.

---

## D-003: La prueba de migración es propia y corre sin emulador

**Decisión**: `BocDatabaseMigrationTest` en `app/src/test`, bajo Robolectric. Crea un fichero de base
de datos **en la versión 1** ejecutando las sentencias `CREATE` que el propio `1.json` exporta, mete
una publicación, cierra, y a continuación abre la base de datos real con
`Room.databaseBuilder(...).build()` —el mismo camino que la aplicación— y comprueba que la
publicación sigue ahí y que la columna nueva existe y vale `NULL`.

**Rationale**: hay un tanteo detrás. `MigrationTestHelper` de `room-testing` **exige una
`Instrumentation`** en todos sus constructores de Android y carga el esquema desde los *assets* del
paquete de pruebas; el propio Room documenta el montaje añadiendo `$projectDir/schemas` a los assets
de `androidTest`. Bajo Robolectric no hay dos paquetes, así que habría que meter los esquemas en los
assets de la aplicación —y enviarlos dentro del APK— o mover la prueba a `androidTest` y pagar un
emulador por algo que no tiene nada que ver con la pantalla.

La versión propia sale ganando en lo que importa: prueba **el camino de producción**
(`Room.databaseBuilder`, que es lo que se cae en un dispositivo real si la migración falta) en lugar
de un ayudante que abre la base de datos por su cuenta, y vive en `src/test`, que es la puerta que CI
ejecuta en cada empujón. Las sentencias de la versión 1 se transcriben del `1.json` **literalmente**
y con un comentario que dice de dónde vienen; el fichero está congelado, así que no pueden
desincronizarse.

**Alternativas descartadas**:
- **`MigrationTestHelper` en `androidTest`**: es la vía documentada y funciona con migraciones
  automáticas, pero exige `androidTestImplementation` de `room-testing` y declarar los esquemas como
  assets de prueba, y traslada la comprobación a la puerta que necesita emulador.
- **`MigrationTestHelper` bajo Robolectric**: requiere que los esquemas estén en los assets de la
  aplicación. Enviar el esquema de la base de datos dentro del APK para poder probarlo es la clase de
  concesión que no se hace.
- **No probar la migración**: es la que había hasta hoy, y `BocDatabase` prometió por escrito lo
  contrario. Además SC-006 la exige.

---

## D-004: `saved_at` no sube al dominio; el estado viaja como conjunto de claves

**Decisión**: `Publication` **no cambia**. La lista de guardados es un `List<Publication>` ordenado
ya por el almacén, y el estado de guardado llega a la interfaz como un `Set<String>` de claves.

**Rationale**: `Publication` es lo que la fuente publica sobre un anuncio, y la marca no lo es: es de
la persona. Añadirle un campo obligaría a tocar todas las fábricas de prueba y a decidir qué vale ese
campo cuando la publicación viene del analizador de feeds, donde no significa nada.

El conjunto de claves resuelve las dos pantallas con un solo flujo: Inicio pinta cada tarjeta según
si su clave está dentro, y el detalle hace lo mismo con la suya. El orden no necesita el instante
porque lo aplica el `ORDER BY`.

**Alternativas descartadas**:
- **`Publication.savedAt: Long?`**: mete un dato local en el modelo de la fuente y obliga a rellenarlo
  en cada sitio que construye una publicación, incluido el normalizador.
- **Un `SavedPublication(publication, savedAt)` envolvente**: haría falta si la interfaz mostrara la
  fecha de guardado, y no la muestra (queda fuera de alcance). Un envoltorio que solo se desenvuelve
  es ceremonia.
- **`observeIsSaved(externalKey)` propio para el detalle**: un método de repositorio, un caso de uso y
  una prueba más para derivar un booleano de un conjunto que la aplicación ya tiene. El conjunto de
  guardados es pequeño por definición —lo escribe una persona a mano— así que no hay nada que
  optimizar.

---

## D-005: Un repositorio propio para lo guardado

**Decisión**: `SavedPublicationRepository` en `domain/repository`, con
`SavedPublicationRepositoryImpl` en `data/repository`. Cuatro dependencias: el DAO, el proveedor de
tiempo, los despachadores y el reportero de fallos, más el registrador de analítica.

**Rationale**: `PublicationRepositoryImpl` ya recibe diez dependencias y lleva
`@Suppress("LongParameterList")`. Añadirle un undécimo parámetro para una responsabilidad distinta
—lo que la persona marca, frente a lo que la fuente publica— empeora la clase que ya está en el
límite. Separarlos también deja el contrato de la sincronización intacto: quien lee
`PublicationRepository` no tiene que enterarse de que existen las marcas.

**Alternativas descartadas**:
- **Ampliar `PublicationRepository`**: tres métodos más en una interfaz que hoy tiene cinco y una
  responsabilidad clara.
- **Un caso de uso que hable directamente con el DAO**: rompe el principio II —`domain` no puede ver
  `data`— y no es negociable.

---

## D-006: Un DAO propio, sobre la misma tabla

**Decisión**: `SavedPublicationDao`, con las tres sentencias de la marca, sobre la tabla
`publications`. `PublicationDao` no se toca más que para nada.

**Rationale**: el comentario de cabecera de `PublicationDao` describe la sincronización y su regla de
oro. Meter ahí un `UPDATE` que la persona dispara desde un botón mezcla dos historias en un fichero
cuya invariante se sostiene precisamente porque se lee de un tirón. Room permite varios DAO sobre una
misma tabla y no cuesta nada.

**Alternativas descartadas**:
- **Ampliar `PublicationDao`**: funcionaría, pero diluye el fichero donde vive la regla de «aquí no
  hay borrados» justo cuando se añade la primera escritura que no viene de la fuente.

---

## D-007: Desmarcar es un `UPDATE` a `NULL`; sigue sin haber borrados

**Decisión**: `UPDATE publications SET saved_at = :savedAt WHERE external_key = :externalKey`, con
`null` para desmarcar. La sentencia devuelve el número de filas afectadas.

**Rationale**: la regla del proyecto —«nunca se borra una publicación», y si aparece un `@Query` de
borrado en una revisión hay que rechazarlo— se cumple **literalmente**, no reinterpretada: en todo el
proyecto sigue sin existir una sentencia de borrado. Desmarcar retira la marca y deja la publicación
donde estaba, que es exactamente lo que dice FR-021.

Devolver las filas afectadas permite que la prueba del DAO afirme el caso raro: marcar una clave que
no está almacenada no crea nada y no falla, simplemente no toca ninguna fila.

**Alternativas descartadas**:
- **Borrar la fila de una tabla de marcas**: solo existe si se elige D-001 al contrario.
- **Un campo de tres estados**: no hay tercer estado. Guardada o no.

---

## D-008: La tarjeta se muda a componente compartido y aprende su estado

**Decisión**: `PublicationCard` pasa de `ui/home/component/` a `core/ui/component/` y recibe
`isSaved: Boolean`. `onSave` sigue sin parámetros: quien la coloca cierra sobre la publicación, igual
que ya hace con `onShare`.

**Rationale**: a partir de esta feature la usan dos pantallas, y la guía del proyecto dice que los
componibles compartidos sin estado viven en `core/ui/component`. Dejarla en Inicio e importarla desde
Guardados funcionaría, pero convierte un paquete de pantalla en biblioteca de otra, y esa es la clase
de dependencia que nadie deshace después. `PublicationCardSkeleton` **no** se muda: solo lo usa la
primera carga del boletín.

**Alternativas descartadas**:
- **Importarla desde `ui/saved`**: cero cambios ahora, una dependencia entre pantallas para siempre.
- **Duplicar la tarjeta**: dos copias del componente central de la aplicación. La primera vez que se
  cambie una, la otra queda vieja.
- **Pasar `onSave: (Publication) -> Unit`**: cambia el estilo de la tarjeta sin ganar nada; el patrón
  de la casa es cerrar sobre el elemento en el punto de uso.

---

## D-009: El marcador relleno es un vector nuevo, no un truco de tinte

**Decisión**: `ic_bookmark_filled.xml` nuevo, Material Symbols relleno, con el mismo lienzo de 960 y
el grupo trasladado que los otros dieciocho iconos.

**Rationale**: el apartado 12 del documento de diseño pide «icono relleno» para el estado guardado, y
relleno es un trazado distinto, no el mismo trazado con otro color. Un cambio de tinte no distingue
los dos estados en la barra superior azul del detalle, donde el icono ya es blanco. Y el conjunto
básico de iconos de Material no está en el classpath de este proyecto, así que no hay
`Icons.Filled.Bookmark` que usar.

**Alternativas descartadas**:
- **Cambiar la tinte o la opacidad**: no funciona sobre fondo azul, y distinguir un estado por
  intensidad de color es exactamente lo que el apartado de accesibilidad prohíbe.
- **Añadir la biblioteca de iconos extendida de Material**: una dependencia nueva para un icono, en un
  proyecto que ya tiene diecinueve vectores propios por decisión tomada.

---

## D-010: `ComingSoonMessage` se generaliza en lugar de escribir un cuarto mensaje centrado

**Decisión**: nace `IllustratedMessage(iconRes, title, description, action)` en
`core/ui/component/`, y `ComingSoonMessage` pasa a delegar en él sin cambiar su firma ni su etiqueta
de prueba.

**Rationale**: el estado vacío del apartado 22.3 necesita icono grande, título propio, texto de apoyo
y una acción secundaria. `EmptyMessage` no tiene ni icono ni acción; `ComingSoonMessage` tiene el
título fijado a «Próximamente». Los tres dibujan la misma columna centrada con los mismos tokens, así
que la pieza compartida ya existía sin nombre.

**Alternativas descartadas**:
- **Un componible privado en `ui/saved`**: la cuarta copia de la misma columna centrada.
- **Ampliar `EmptyMessage`**: lo usa Inicio en dos sitios con un mensaje a secas; darle icono y acción
  opcionales lo convierte en un componente con cuatro combinaciones y ningún nombre que las describa.

---

## D-011: El efecto de compartir se extrae, no se copia por tercera vez

**Decisión**: el `LaunchedEffect` que avisa, entrega al sistema y confirma el consumo se extrae a
`ui/share/` y lo usan Inicio y Guardados. El detalle **no** se toca: además dibuja una línea de
progreso propia y su aviso es distinto.

**Rationale**: Guardados comparte exactamente igual que Inicio (FR-014). Escribir la tercera copia de
las mismas veinte líneas garantiza que la próxima corrección se aplique a dos de las tres.

**Alternativas descartadas**:
- **Copiar el efecto**: tres copias.
- **Unificar también el detalle**: su comportamiento no es el mismo, y forzarlo obligaría a un
  parámetro que solo sirve para distinguirlos. Se deja, y se anota.

---

## D-012: La analítica se registra en el repositorio, no en tres modelos de pantalla

**Decisión**: el evento `publication_save` lo emite `SavedPublicationRepositoryImpl`, con un único
parámetro que dice si se guardó o se quitó. `SavedViewModel` registra su vista de pantalla, como
todas.

**Rationale**: guardar se dispara desde tres sitios. Con el evento en el repositorio hay un solo
lugar que lo emite y un solo lugar que lo prueba. El proyecto ya lo hace así con `boc_sync` y con los
eventos del documento.

**Ningún dato personal**: no viaja la clave, ni el título, ni la sección. Qué guarda una persona es
una señal de interés personal, y el principio VI lo prohíbe (FR-025).

**Alternativas descartadas**:
- **Registrarlo en cada modelo de pantalla**: tres emisiones que hay que mantener de acuerdo.
- **Incluir la clave o la sección**: describiría los intereses de una persona identificada por su
  instalación.

---

## D-013: `DomainError` sigue sin crecer

**Decisión**: dos casos, `Network` y `Unknown`. Un fallo al escribir la marca es `Unknown`.

**Rationale**: la feature 004 ya resistió la tentación de añadir un caso, y aquí el argumento es más
fuerte: no hay nada que la pantalla pueda hacer distinto según por qué falló el almacén. El mensaje
es el mismo y la acción es la misma —volver a intentarlo—.

**Alternativas descartadas**:
- **`DomainError.Storage`**: obligaría a ampliar el `when` exhaustivo de tres pantallas para mostrar
  el mismo texto.

---

## D-014: Guardados no entra en `HomeSelection`

**Decisión**: `HomeSelection` se queda con sus dos casos. Guardados es su propia pantalla, con su
modelo y su estado.

**Rationale**: `HomeSelection` alimenta también `observeHeader`, que produce fecha y recuento de la
selección. Guardados no tiene fecha de boletín ni sección, así que un tercer caso obligaría a esa
función a devolver algo inventado o a ramificar en vacío. Y las tarjetas de Guardados no llevan chips
de sección ni cabecera editorial (FR-016).

**Alternativas descartadas**:
- **`HomeSelection.Saved` y reutilizar Inicio entera**: ahorra una pantalla y contamina cuatro piezas
  —selección, cabecera, chips y mensajes de vacío— con un caso que no encaja en ninguna.

---

## D-015: Versiones y compatibilidad

**Decisión**: **ninguna dependencia nueva y ninguna versión que subir**. Room 2.8.4 trae la migración
automática; `androidx.room:room-testing` ya está en el classpath de `src/test` aunque D-003 no lo
necesite; Turbine, MockK, Robolectric y Compose UI Test ya están.

**Rationale**: la feature es de almacén y de pantalla, y el proyecto tiene las dos cosas resueltas
desde la 003.

**Consecuencia**: `libs.versions.toml` no se toca. Si en la revisión aparece una coordenada nueva, hay
que preguntar por qué.
