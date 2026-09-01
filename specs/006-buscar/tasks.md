# Tasks: Buscar

**Input**: Design documents from `/specs/006-buscar/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: OBLIGATORIOS. El principio V de la constitución los declara no negociables y la
especificación los exige en SC-002, SC-003, SC-004, SC-007, SC-009, SC-010 y SC-012. Dentro de cada
historia, las pruebas se escriben **antes** que la implementación y deben fallar antes de hacerlas
pasar. Donde eso no es posible —una prueba que no compila hasta que existe la columna que
comprueba— la tarea lo dice en voz alta.

**Organization**: las tareas se agrupan por historia de usuario, de forma que cada una pueda
implementarse, probarse y demostrarse por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (ficheros distintos, sin dependencias entre sí)
- **[Story]**: historia a la que pertenece (US1, US2, US3, US4, US5)

## Path Conventions

Abreviaturas: `MAIN/` = `app/src/main/java/com/jrblanco/boccantabria/`,
`TEST/` = `app/src/test/java/com/jrblanco/boccantabria/`,
`ATEST/` = `app/src/androidTest/java/com/jrblanco/boccantabria/`,
`RES/` = `app/src/main/res/`.

Antes de cualquier comando Gradle:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: los recursos que las dos búsquedas necesitan. **No hay dependencias nuevas que añadir**
(research.md D-020): si en la revisión aparece una coordenada en `libs.versions.toml`, hay que
preguntar por qué.

- [X] T001 [P] Añadir `RES/drawable/ic_close.xml`: Material Symbols, trazado tomado de la fuente
      oficial sin modificarlo, el mismo lienzo de 960 y el grupo trasladado que usan los otros
      diecinueve iconos del proyecto. `android:fillColor` es un marcador que Compose tiñe en el
      punto de uso (research.md D-022) (FR-006, FR-022)
- [X] T002 [P] Añadir `RES/drawable/ic_filter_list.xml`, con las mismas reglas que T001 (FR-034)
- [X] T003 [P] Añadir `RES/drawable/ic_sort.xml` —`swap_vert` de Material Symbols—, con las mismas
      reglas que T001 (FR-041)
- [X] T004 En `RES/values/strings.xml`, bajo un encabezado `===== Feature 006: buscar =====`,
      añadir los textos de la pantalla Buscar y de la búsqueda rápida: `search_title`
      («Buscar», la misma palabra que la pestaña), `search_hint` («Buscar publicaciones»), `search_clear`
      («Borrar la búsqueda»), `search_initial_title`, `search_initial_body` («Escribe para buscar en
      todo el BOC»), `search_empty_title` («No hemos encontrado publicaciones»), `search_empty_body`
      («Prueba con otras palabras o quita alguno de los filtros»), `search_truncated`
      («Mostrando 300 de los más recientes. Acota la búsqueda»), `search_result_count` (plural),
      `home_search_hint` («Buscar en esta edición…»), `home_search_close` («Cerrar la búsqueda»),
      `home_search_clear` («Borrar el texto»), `home_search_match_count` (plural),
      `home_no_results_title` («Nada en esta edición»), `home_no_results_body` y
      `home_search_globally` («Buscar en todo el BOC»). **No** retirar todavía
      `coming_soon_search`: se usa hasta T044

**Checkpoint**: recursos listos. Nada de esto cambia el comportamiento aún.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: la normalización, la columna y su relleno. Aquí vive la parte que **no se ve en una
instalación limpia** y que, si falla, deja inbuscable el archivo de quien ya tiene la aplicación.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta que esta fase esté completa.

### La normalización del texto

- [X] T005 [P] Escribir `TEST/core/util/SearchTextTest.kt` **antes** que la implementación:
      `AYUNTAMIENTO DE PIÉLAGOS` → `ayuntamiento de pielagos`; `  Piélagos  ` → `pielagos`;
      `España` → `espana` (la `ñ` pierde la tilde **a propósito**, y las tres formas normalizan
      igual); `Contratación   administrativa` colapsa el espacio doble; cadena vacía y cadena de
      solo espacios devuelven `""`; idempotencia (`normalise(normalise(x)) == normalise(x)`); y el
      resultado **no** depende del `Locale` por defecto —fijar `Locale.setDefault(Locale.forLanguageTag("tr"))`
      en la prueba y comprobar que `I` sigue bajando a `i`— (research.md D-003) (FR-001, FR-002,
      FR-003, SC-002)
- [X] T006 Implementar `MAIN/core/util/SearchText.kt`: `object SearchText { fun normalise(raw: String): String }`
      con NFD → quitar `\p{Mn}` → `lowercase(Locale.ROOT)` → colapsar espacios → recortar. Kotlin
      puro, sin `import android.*`: la usan las tres capas y `domain` la importa como ya importa
      `AppVersionProvider` (contracts §1.1)

### El texto buscable y el patrón de búsqueda

- [X] T007 [P] Escribir `TEST/data/source/local/PublicationSearchTextTest.kt` **antes** que la
      implementación: incluye título, organismo, jerarquía, referencia y **nombre** de sección y
      subsección; omite los nulos sin dejar huecos ni literales `null`; la salida ya viene
      normalizada; y —la propiedad de la que depende todo el relleno— **nunca devuelve cadena
      vacía**, porque `title` no puede estar en blanco (research.md D-002) (FR-025)
- [X] T008 Implementar `MAIN/data/source/local/PublicationSearchText.kt` con
      `internal fun buildSearchText(title, issuer, organizationPath, blobId, sectionName, subsectionName): String`.
      **No** indexar `raw_categories` —viene permutado en el feed 4.3— ni `titleWithoutIssuer`
      —es un derivado del título— (contracts §1.2)
- [X] T009 [P] Escribir `TEST/data/source/local/LikePatternTest.kt` **antes** que la
      implementación: `pielagos` → `%pielagos%`; `100%` → `%100\%%`; `a_b` → `%a\_b%`; `c\d` →
      `%c\\d%`. Es el fallo que **no falla, miente**: sin escapado, la búsqueda deja de ser literal
      (research.md D-010) (FR-004, SC-010)
- [X] T010 Implementar `MAIN/data/source/local/LikePattern.kt` con
      `internal fun likeContains(normalisedQuery: String): String`. Vive junto al SQL y **no** en
      `core/util`: escapar es una regla de SQL, y la búsqueda rápida no debe arrastrar barras
      invertidas (contracts §3.2 de data-model.md)

### La columna y la actualización del almacén

- [X] T011 En `MAIN/data/source/local/PublicationEntity.kt`: añadir
      `@ColumnInfo(name = "search_text", defaultValue = "''") val searchText: String = ""`,
      **sin índice** (research.md D-006), y cambiar la firma de `Publication.toEntity` a
      `toEntity(seenAt: Long, searchText: String)`. Documentar en el KDoc que `search_text` es un
      dato **derivado de la fuente**, al contrario que `saved_at`
- [X] T012 En `MAIN/data/source/local/BocDatabase.kt`: `version = 3` y
      `autoMigrations = [AutoMigration(1, 2), AutoMigration(2, 3)]` —la 1→2 **se conserva**: quien
      se salte una versión tiene que poder llegar de la 1 a la 3—. `bocDatabase()` sigue siendo un
      `.build()` limpio: **ni `addMigrations` ni `fallbackToDestructiveMigration`** (research.md
      D-004). Ejecutar `./gradlew :app:assembleDebug` para que se exporte
      `app/schemas/…BocDatabase/3.json` y **versionarlo**
- [X] T013 Ampliar `TEST/data/source/local/BocDatabaseMigrationTest.kt` con el salto **2→3**,
      siguiendo el patrón hecho a mano que ya tiene: transcribir el esquema de la versión 2
      **verbatim** desde `app/schemas/…BocDatabase/2.json` —incluidos `room_master_table` y su hash
      `1f93c864ff2220ed1bf0114ece8dfb40`—, insertar una publicación y una marca de guardado, abrir
      con `Room.databaseBuilder` y comprobar: la publicación sobrevive, `saved_at` sobrevive,
      `search_text` llega como `''`, y el salto **1→3** también funciona. *(Esta prueba no puede
      escribirse antes que T011 y T012: no compilaría sin la columna. Su valor es de regresión.)*
      (FR-027, SC-004)
- [X] T014 En `MAIN/data/source/local/PublicationDao.kt`: añadir `search_text = :searchText` a la
      lista blanca de `updateColumns` y su parámetro, y las dos sentencias del relleno,
      `withoutSearchText(limit): List<PublicationEntity>` y `setSearchText(externalKey, searchText)`.
      Ampliar el KDoc de la clase explicando **por qué** `search_text` sí entra y `saved_at` y
      `first_seen_at` no (research.md D-009). **Ningún `DELETE`**
- [X] T015 Ampliar `TEST/data/source/local/PublicationDaoTest.kt`: un `upsertAll` sobre una fila ya
      existente **actualiza** `search_text` cuando la fuente corrige el título; `withoutSearchText`
      devuelve solo las filas con la columna vacía y respeta el `limit`; `setSearchText` afecta a
      una fila. **`SavedPublicationDaoTest` no se toca**: si se pone roja tras T014, es que
      `saved_at` se ha colado en la sentencia, y el arreglo es quitarlo (contracts §1.4)

### El relleno

- [X] T016 Ampliar `TEST/data/repository/PublicationRepositoryImplTest.kt` **antes** que la
      implementación, con la prueba que cubre el fallo invisible en instalación limpia: partiendo de
      filas con `search_text = ''` —como las deja la migración—, una llamada a `refresh()` las deja
      todas con su texto buscable; el proceso es **idempotente** (una segunda llamada no reescribe
      nada porque ya no hay filas vacías); y con más filas que el tamaño de lote se rellenan todas
      (research.md D-005) (FR-027)
- [X] T017 En `MAIN/data/repository/PublicationRepositoryImpl.kt`: construir el texto buscable al
      sincronizar —`sectionRepository` ya está inyectado, así que el nombre de sección y subsección
      está a mano— y pasar de `toEntity(now)` a `toEntity(now, searchText)`. Después del `awaitAll`
      de las fuentes, ejecutar el relleno por lotes de 500 hasta que `withoutSearchText` devuelva
      vacío. El relleno **no** ensancha la interfaz de `PublicationRepository`: es un detalle del
      almacén (data-model.md §5)
- [X] T018 [P] Actualizar las llamadas a `toEntity` en los dobles y utilidades de prueba que la
      usan (`TEST/fake/`, `TEST/integration/`), para que la firma nueva compile en toda la suite

**Checkpoint**: la base está en la versión 3, todo lo que entra trae su texto buscable y lo que ya
estaba se rellena solo. Las dos búsquedas pueden empezar.

---

## Phase 3: User Story 1 - Buscar en todo lo almacenado (Priority: P1) 🎯 MVP

**Goal**: la pestaña Buscar deja de ser un marcador de posición y encuentra publicaciones en todo lo
que la aplicación ha descargado, con la tarjeta estándar, abriendo el detalle y pudiendo guardar.

**Independent Test**: con publicaciones de varias fechas almacenadas, escribir un término presente
en unas pocas y comprobar que salen esas y solo esas, que se abren y que se pueden guardar. Con el
dispositivo en modo avión.

### Modelos de dominio

- [X] T019 [P] [US1] Escribir `TEST/domain/model/SearchSortTest.kt` y después
      `MAIN/domain/model/SearchSort.kt`: `enum class SearchSort { NEWEST_FIRST, OLDEST_FIRST }`, con
      `NEWEST_FIRST` como valor por defecto. Dos valores y no tres: la relevancia está fuera de
      alcance por decisión del propietario (FR-041)
- [X] T020 [P] [US1] Escribir `TEST/domain/model/SearchResultsTest.kt` y después
      `MAIN/domain/model/SearchResults.kt`: `data class SearchResults(items, isTruncated)` (FR-032)
- [X] T021 [US1] Escribir `TEST/domain/model/SearchQueryTest.kt` **antes** que el modelo:
      `normalisedText` usa `SearchText`; `isRunnable` es falso con cero y con un carácter y cierto
      con dos (research.md D-011); una consulta de solo espacios equivale a no haber buscado
      (FR-005); `activeFilterCount` cuenta el rango de fechas como **uno**;
      `clearedFilters()` quita los filtros y **conserva el texto y el orden** (FR-040); elegir una
      sección **limpia la subsección** si la que había no le pertenece (FR-036); y **no** hay
      `require` sobre el rango de fechas —la combinación imposible se impide en la interfaz, no con
      una excepción (research.md D-018)
- [X] T022 [US1] Implementar `MAIN/domain/model/SearchQuery.kt` según data-model.md §4.1
- [X] T023 [P] [US1] Declarar `MAIN/domain/repository/SearchRepository.kt` con `search(query, limit)`
      y `observeIssuers()`, ambos `Flow`. KDoc: **lista vacía es éxito**, nunca un fallo
      (contracts §1.5)

### Almacén

- [X] T024 [US1] Escribir `TEST/data/source/local/PublicationSearchDaoTest.kt` **antes** que el DAO
      (Robolectric + base de datos en memoria, como el resto del proyecto, donde **nunca** se falsea
      un DAO): `pielagos` encuentra `Piélagos`; `contratacion` encuentra una publicación de la
      sección Contratación cuyo título no lleva esa palabra; cada filtro por separado y combinados;
      un filtro nulo **no** recorta; los dos órdenes, con los tres términos de desempate; el `limit`
      se respeta; `100%` no devuelve el archivo entero; `observeIssuers` devuelve los organismos sin
      repetir, en orden y sin nulos (FR-024, FR-025, FR-035, FR-041, FR-042, SC-002, SC-003, SC-010)
- [X] T025 [US1] Implementar `MAIN/data/source/local/PublicationSearchDao.kt`: `searchNewestFirst`,
      `searchOldestFirst` y `observeIssuers`, con el SQL de data-model.md §3.1 y `ESCAPE '\'`. **De
      solo lectura**: ni `INSERT`, ni `UPDATE`, ni `DELETE` (contracts §1.3)
- [X] T026 [US1] Escribir `TEST/data/repository/SearchRepositoryImplTest.kt` **antes** que la
      implementación: elige la sentencia según el orden; construye el patrón normalizando y
      escapando; mapea entidades a modelos de dominio —la entidad **no** cruza a `ui`—; un fallo de
      lectura se registra como no fatal y **emite lista vacía sin matar el flujo**; y **ninguna
      llamada de red** (FR-026, SC-011)
- [X] T027 [US1] Implementar `MAIN/data/repository/SearchRepositoryImpl.kt`, con `dispatchers.io`
      inyectado y `CancellationException` repropagada, como el resto de `data`

### Caso de uso

- [X] T028 [US1] Escribir `TEST/domain/usecase/SearchPublicationsUseCaseTest.kt` **antes** que la
      implementación: con `!query.isRunnable` **no** llama al repositorio; pide `MAX_RESULTS + 1`;
      con 301 filas entrega 300 y `isTruncated = true`; con 300 exactas entrega 300 y
      `isTruncated = false` —que es lo que justifica pedir una de más— (research.md D-017) (FR-028,
      FR-032)
- [X] T029 [US1] Implementar `MAIN/domain/usecase/SearchPublicationsUseCase.kt` con
      `const val MAX_RESULTS = 300`

### Inyección de dependencias

- [X] T030 [US1] Registrar en `MAIN/core/di/DataModule.kt` el `PublicationSearchDao` —desde
      `get<BocDatabase>()`— y `SearchRepositoryImpl`; y en `MAIN/core/di/DomainModule.kt`
      `SearchPublicationsUseCase`. Nada se instancia a mano
- [X] T031 [US1] Ampliar `TEST/di/KoinModulesTest.kt` para que el grafo nuevo se verifique. Debe
      fallar antes de T030

### Presentación

- [X] T032 [P] [US1] Crear `MAIN/ui/search/SearchUiState.kt` con `SearchUiState` y el sellado
      `SearchContentState { Initial, Results(items, isTruncated), Empty }`. **Sin estado de carga**:
      lo que se lee es local e inmediato, no hay espera que amortiguar, y un estado que nadie emite
      es una rama muerta del `when` —el mismo razonamiento que dejó a `SavedContentState` con dos
      casos—. `share`,
      `savedKeys` y `saveFailed` van **fuera** del sellado, por la misma razón que en Inicio y en
      Guardados: son ejes independientes (data-model.md §6.1)
- [X] T033 [US1] Escribir `TEST/ui/search/SearchViewModelTest.kt` **antes** que el modelo (Turbine +
      `TestDispatcherProvider`): por debajo de dos caracteres el estado es `Initial` y **no** se
      consulta el almacén; el rebote no lanza una búsqueda por tecla; sin coincidencias el estado es
      `Empty`, nunca un error; con resultados llegan los elementos y la marca de recorte; las claves
      guardadas llegan al estado y alternar la marca pide guardar o quitar, y refleja el estado real
      aunque haya cambiado en otra pantalla (FR-031); una escritura fallida pone `saveFailed`; la consulta inicial se **siembra** desde el `SavedStateHandle`; y
      —comprobación de FR-046— el rastreador **no** recibe el texto de la consulta en ningún evento
- [X] T034 [US1] Implementar `MAIN/ui/search/SearchViewModel.kt`: `MutableStateFlow` privado,
      `StateFlow` de solo lectura, rebote de 250 ms y `flatMapLatest` sobre la consulta,
      `trackScreenView("search")` en el `init`, y el evento `boc_search` con **si había filtros** y
      un **tramo** del número de resultados (`0`, `1-9`, `10-99`, `100+`), nunca el texto
      (research.md D-021). Reutiliza `SetPublicationSavedUseCase`, `ObserveSavedKeysUseCase` y
      `ShareOfficialDocumentUseCase`, que ya existen. Añadir el `@OptIn` que pida el compilador para
      `debounce`/`flatMapLatest`, y solo si lo pide
- [X] T035 [P] [US1] Crear `MAIN/ui/search/component/SearchField.kt`, sin estado: 56 dp de alto,
      radio 16, fondo `Surface`, borde de 1 dp `Outline`, lupa a la izquierda, texto de ayuda en
      `BocTheme.colors.textMuted` y aspa de borrar **solo** cuando hay texto (apartado 11.6 del
      diseño; contracts §3.1). **Ningún** color, tamaño ni espaciado literal
- [X] T036 [US1] Reescribir `MAIN/ui/search/SearchScreen.kt`: `SearchScreen` conectado por
      `koinViewModel()` y `SearchContent` **sin estado** para poder montarlo en su prueba. Barra
      superior con el título `Buscar`, **sin flecha atrás y sin menú de tres puntos**
      (FR-023, desviación consciente respecto a la imagen de referencia). Recibe
      `sections: List<BocSection>` **como parámetro**, igual que `SavedScreen.kt:51`, porque
      `PublicationCard` exige `section` y `formattedDate` y las secciones vienen de fuera del modelo
      de pantalla. Lista con `PublicationCard`, la misma de Inicio y Guardados; recuento de resultados; aviso de recorte;
      estados `Initial` y `Empty` con `IllustratedMessage`. Montar `ShareEffect` y
      `SaveFailureToast`, como hace Guardados
- [X] T037 [US1] En `MAIN/ui/main/MainShell.kt`, dejar de pasar el marcador de posición al destino
      `Route.Search`: `SearchScreen` recibe `sections` —el árbol completo, el mismo `val sections`
      que ya reciben Inicio y Guardados, **no** `sectionsState.rows`, que va filtrado por el panel—
      y `onOpenPublication` con la misma lambda que Inicio y Guardados —el detalle vive en el grafo exterior, así que abierto desde aquí tampoco dibuja la
      barra inferior—
- [X] T038 [US1] **Retirar `coming_soon_search` de `RES/values/strings.xml`**: queda sin usar y lint
      señala los recursos muertos

### Pruebas de la historia

- [X] T039 [P] [US1] Escribir `ATEST/ui/search/SearchContentTest.kt` sobre `SearchContent` sin
      estado, montado con `createComposeRule()` para no atravesar el arranque: el estado inicial no
      es un vacío ni un error; los resultados muestran organismo, título, sección y fecha; el aviso
      de recorte aparece solo cuando toca; el estado sin resultados sugiere quitar filtros; una
      publicación guardada se dibuja marcada —por `contentDescription`, no por color—; y tocar una
      tarjeta invoca la devolución de llamada (FR-029, FR-030, FR-033)
- [X] T040 [P] [US1] Ampliar `ATEST/fake/TestGraph.kt` con el DAO y el repositorio nuevos, para que
      `testGraphOverrides()` siga reconstruyendo la cadena entera por prueba
- [X] T041 [US1] Cambiar en `ATEST/ui/BottomBarNavigationTest.kt` la prueba
      `search_still_says_it_is_coming_and_home_comes_back`: Buscar deja de anunciarse como pendiente
      y pasa a ser una pantalla de verdad (FR-021)
- [X] T042 [US1] Escribir `TEST/integration/SearchFlowIntegrationTest.kt`: con el grafo real y solo
      el transporte falseado con bytes de feeds reales, sincronizar y comprobar que una palabra del
      título de una publicación la encuentra en Buscar. Es el camino completo, de los bytes del
      servicio a la pantalla

- [X] T042b [US1] **SC-008 se comprueba a mano, no con una prueba de volumen**, y el motivo hay que
      escribirlo: sembrar decenas de miles de filas dentro de un test de Robolectric deja al JVM de
      pruebas sin poder responder a la adjunción del agente de ByteBuddy, y entonces **todas** las
      clases que usan MockK caen con `Could not initialize class io.mockk.impl.JvmMockKGateway`. Se
      diagnosticó en esta misma feature con seiscientas filas. Añadir el paso correspondiente a
      `quickstart.md` §3.5 —buscar un término muy común con el archivo lleno y comprobar que
      responde de inmediato— y anotar la trampa en `CLAUDE.md` (T071)

**Checkpoint**: Buscar funciona de extremo a extremo, con el orden por defecto y sin filtros. Es el
MVP y se puede demostrar.

---

## Phase 4: User Story 2 - Buscar en la edición que estoy viendo (Priority: P1)

**Goal**: la lupa de Inicio transforma la barra superior y filtra en el acto lo que la pantalla ya
tiene, sin abrir nada, sin red y respetando el contexto.

**Independent Test**: abrir el boletín, pulsar la lupa, escribir un término presente en unas pocas
tarjetas, comprobar que la lista se reduce a esas; borrar y comprobar que vuelve entera; cerrar y
comprobar que la sección y la posición de lectura no han cambiado.

- [X] T043 [US2] Escribir `TEST/domain/usecase/FilterPublicationsUseCaseTest.kt` **antes** que la
      implementación: con texto en blanco devuelve la lista **tal cual**, en el mismo orden;
      `pielagos` encuentra `AYUNTAMIENTO DE PIÉLAGOS`; compara contra título **y** organismo;
      **conserva el orden de entrada** —reordenar aquí contradiría el que fija el almacén—; y sobre
      una lista vacía devuelve vacío (FR-010, SC-002)
- [X] T044 [US2] Implementar `MAIN/domain/usecase/FilterPublicationsUseCase.kt`: puro y síncrono, ni
      corrutinas ni almacén. Registrarlo en `MAIN/core/di/DomainModule.kt` y ampliar
      `TEST/di/KoinModulesTest.kt` (research.md D-012)
- [X] T045 [US2] En `MAIN/ui/home/HomeUiState.kt`: añadir
      `data class HomeSearchState(val isOpen: Boolean = false, val query: String = "")` y el campo
      `search`; y añadir a `HomeContentState` el caso `NoSearchResults(val query: String)`, con KDoc
      explicando por qué **no** es lo mismo que `Empty` (research.md D-016)
- [X] T046 [US2] Ampliar `TEST/ui/home/HomeViewModelTest.kt` **antes** de tocar el modelo: abrir la
      lupa no dispara ninguna sincronización; escribir recorta la lista; el filtrado se aplica
      **sobre la selección vigente** y nunca trae nada de otra sección; borrar el texto devuelve la
      lista completa; cerrar limpia el texto, de modo que volver a abrir empieza en blanco; sin
      coincidencias el estado es `NoSearchResults`, **no** `Empty`; la cabecera sigue contando los
      anuncios de la edición, no las coincidencias; y **el estado sobrevive a que se reconstruya la
      pantalla**, que es lo que hace un giro del dispositivo (FR-015). Ningún evento navega
      (FR-008) (FR-009, FR-011, FR-013, FR-014, FR-015, FR-017)
- [X] T047 [US2] En `MAIN/ui/home/HomeViewModel.kt`: fundir `shareState`, `saveFailed` y el estado de
      la búsqueda en **un único** `MutableStateFlow` de estado local, con lo que el `combine` baja de
      cinco argumentos a cuatro —el comentario que ya hay avisa de que un sexto obliga a la forma de
      lista (research.md D-015)—; añadir `onSearchOpened()`, `onSearchQueryChanged(query)` y
      `onSearchClosed()`; y aplicar `FilterPublicationsUseCase` al calcular el contenido. **Cero red**
      (FR-012)
- [X] T048 [US2] En `MAIN/ui/home/component/HomeTopBar.kt`: añadir el modo de búsqueda. El menú, el
      escudo y el título dejan sitio al campo con el texto de ayuda `Buscar en esta edición…`; a la
      izquierda, cerrar; a la derecha, borrar cuando hay texto. `FocusRequester` al abrirse, para que
      suba el teclado (FR-006, FR-007). El fondo sigue siendo `Surface`, para que no parezca otra
      pantalla (contracts §3.5)
- [X] T049 [US2] En `MAIN/ui/home/HomeScreen.kt`: cablear los tres eventos, mostrar el número de
      coincidencias junto a la lista y dibujar el caso `NoSearchResults` con su mensaje —el mensaje
      dice «en la edición actual», no «no hay resultados» a secas (FR-018)—. La acción que lleva al
      buscador global llega en T053, así que **FR-018 no queda completo hasta US3**; al validar US2
      por separado hay que contarlo. `onSearch` deja de ser un parámetro que venga de fuera
- [X] T050 [US2] En `MAIN/ui/main/MainShell.kt`: quitar `onSearch = ::showComingSoon`. Si
      `showComingSoon` se queda sin usos, retirarla también
- [X] T051 [US2] Escribir `ATEST/ui/home/HomeSearchTest.kt`, montando `HomeContent` con
      `createComposeRule()`: la lupa transforma la barra; escribir recorta la lista; borrar la
      recupera; cerrar restaura la barra normal; el mensaje de «nada en esta edición» aparece con
      su texto propio; y **elegir otra sección con el buscador abierto lo cierra y muestra la
      sección entera** (FR-016) —ocurre por construcción, porque navegar trae un modelo nuevo, y
      «por construcción» no es una garantía que esta constitución acepte sin prueba—. Si en pantalla hay un esqueleto pulsando, conducir el reloj a mano
      (`mainClock.autoAdvance = false`), que si no `assertIsDisplayed` se cuelga

**Checkpoint**: la lupa funciona. Inicio y Buscar son dos búsquedas independientes que aún no se
hablan.

---

## Phase 5: User Story 3 - El puente hacia el buscador global (Priority: P1)

**Goal**: cuando la búsqueda rápida no encuentra nada, ofrecer la misma consulta en el buscador
global, que la recibe ya escrita y ejecutada.

**Independent Test**: buscar en la edición un término que no aparezca en ella pero sí en el archivo,
tocar la opción ofrecida y comprobar que se llega a Buscar con el término escrito y los resultados
ya en pantalla.

- [X] T052 [US3] En `MAIN/ui/navigation/Routes.kt`: `Route.Search` pasa de `data object` a
      `data class Search(val query: String? = null)`. Comprobar que `hasRoute<Route.Search>()` sigue
      identificando la pestaña y que `navigateTo(BottomDestination.SEARCH)` navega a `Route.Search()`
- [X] T053 [US3] En `MAIN/ui/home/HomeScreen.kt` y `MAIN/ui/main/MainShell.kt`: añadir
      `onSearchGlobally: (String) -> Unit`, cablear la acción del estado `NoSearchResults` y navegar
      con `launchSingleTop = true` y **sin `restoreState`**. Comentar en el código **por qué**: con
      restauración, el estado guardado de la pestaña pisa el argumento y el término traspasado se
      pierde (research.md D-013) (FR-019, FR-020)
- [X] T054 [US3] Ampliar `TEST/ui/search/SearchViewModelTest.kt`: el término que llega por el
      argumento **gana** a cualquier consulta guardada, y la búsqueda se ejecuta sin que nadie
      teclee nada (FR-020)
- [X] T055 [US3] Escribir `ATEST/ui/SearchHandoffTest.kt`, con `MainShell` real y Koin sobrescrito
      (`KoinOverrideRule` + `testGraphOverrides(...)`): buscar en Buscar un término, volver a Inicio,
      buscar allí **otro** término sin coincidencias, usar el puente, y comprobar que llega el
      término **nuevo**. Es la secuencia exacta que el estado restaurado rompería (FR-020, SC-006)

**Checkpoint**: las tres historias P1 están completas. La feature ya es demostrable de principio a
fin.

---

## Phase 6: User Story 4 - Acotar la búsqueda y elegir el orden (Priority: P2)

**Goal**: filtros avanzados en hoja inferior, chips de lo activo en la pantalla y elección de orden.

**Independent Test**: con resultados en pantalla, aplicar cada filtro por separado y comprobar que
recorta lo que debe; quitar una etiqueta y comprobar que el texto **no** se borra; cambiar el orden
y comprobar que se invierte.

- [X] T056 [P] [US4] Escribir `TEST/domain/usecase/GetSearchIssuersUseCaseTest.kt` y después
      `MAIN/domain/usecase/GetSearchIssuersUseCase.kt`. Registrarlo en `DomainModule.kt` y ampliar
      `TEST/di/KoinModulesTest.kt` (FR-037)
- [X] T057 [P] [US4] Crear `MAIN/ui/search/component/ActiveFilterChips.kt`, sin estado: un chip por
      filtro activo, con su aspa y un área táctil de 48 dp, más `Limpiar todo`. Cada chip describe
      **qué** filtro quita, no solo «quitar» (apartado 11.5 del diseño; contracts §3.2, §3.7)
      (FR-039)
- [X] T058 [P] [US4] Crear `MAIN/ui/search/component/SortSelector.kt`, sin estado: `Más recientes` y
      `Más antiguas`, con `Más recientes` por defecto (FR-041)
- [X] T059 [US4] Crear `MAIN/ui/search/component/SearchFiltersSheet.kt`: `ModalBottomSheet` de
      Material 3 titulado `Filtrar resultados`, con fecha desde, fecha hasta, sección, subsección y
      organismo, y las acciones `Limpiar` y `Aplicar filtros` (apartado 17.3 del diseño). Las fechas
      abren el `DatePickerDialog` de Material 3, que ya está en el BOM (research.md D-018).
      **`Aplicar filtros` queda inhabilitado** mientras «desde» sea posterior a «hasta», y el
      selector de «hasta» no ofrece días anteriores a «desde» (FR-038). La lista de subsecciones es
      la de la sección elegida (FR-036) y la de organismos, la de lo realmente almacenado, con su
      propio campo para recorrerla escribiendo, porque son cientos (FR-037). **Sin municipio**
- [X] T060 [US4] Ampliar `TEST/ui/search/SearchViewModelTest.kt` **antes** de tocar el modelo:
      aplicar filtros recalcula los resultados; quitar un chip o usar `Limpiar todo` **no borra el
      texto** (FR-040); cambiar el orden invierte la lista; y elegir una sección limpia una
      subsección que no le pertenezca
- [X] T061 [US4] En `MAIN/ui/search/SearchViewModel.kt`: eventos de filtros y de orden, delegando en
      los métodos de `SearchQuery` para que la regla viva en el modelo y tenga prueba propia
- [X] T062 [US4] En `MAIN/ui/search/SearchScreen.kt`: montar la hoja, los chips y el selector de
      orden, con la acción que abre la hoja en la barra superior o junto al campo
- [X] T063 [US4] Añadir a `RES/values/strings.xml` los textos de la hoja y de los chips:
      `search_filters_title` («Filtrar resultados»), `search_filters_apply` («Aplicar filtros»),
      `search_filters_clear` («Limpiar»), `search_chips_clear_all` («Limpiar todo»),
      `search_filter_date_from` («Fecha desde»), `search_filter_date_to` («Fecha hasta»),
      `search_filter_section` («Sección»), `search_filter_subsection` («Subsección»),
      `search_filter_issuer` («Organismo»), `search_filter_all` («Todas»/«Todos»),
      `search_sort_newest` («Más recientes»), `search_sort_oldest` («Más antiguas»),
      `search_sort_label` («Ordenar por») y las descripciones de accesibilidad de cada aspa
- [X] T064 [US4] Escribir `ATEST/ui/search/SearchFiltersSheetTest.kt`: la hoja se abre y se cierra;
      `Aplicar filtros` está inhabilitado con el rango invertido; los chips aparecen con lo aplicado;
      quitar un chip conserva el texto; `Limpiar todo` conserva el texto; y **no hay ningún filtro de
      municipio**

**Checkpoint**: la búsqueda global es utilizable con el archivo grande.

---

## Phase 7: User Story 5 - Volver y encontrarlo todo como estaba (Priority: P2)

**Goal**: consulta, filtros, orden y posición de desplazamiento sobreviven a abrir un resultado,
a cambiar de pestaña y a la muerte del proceso.

**Independent Test**: buscar, filtrar, desplazarse, abrir un resultado, volver y comprobar los cuatro
elementos; después ir a Inicio y volver a Buscar.

- [X] T065 [US5] Ampliar `TEST/ui/search/SearchViewModelTest.kt` **antes** de tocar el modelo: un
      modelo nuevo construido con un `SavedStateHandle` que ya trae texto, filtros y orden arranca
      con ellos y ejecuta la búsqueda; y escribir o filtrar **escribe** en el `SavedStateHandle`
      (FR-044, FR-045)
- [X] T066 [US5] En `MAIN/ui/search/SearchViewModel.kt`: leer y escribir `text`, `from`, `to`,
      `sectionCode`, `subsectionCode`, `issuer` y `sort` en el `SavedStateHandle` mediante
      `getStateFlow`. **El texto se persiste bajo la clave `query`, no `text`**: es la clave por la
      que la ruta tipada `Route.Search(query = …)` deja el argumento del puente, y usar dos claves
      distintas haría que la siembra no encontrara nada —sin error y sin excepción, simplemente
      llegando a Buscar con el campo vacío—. `SearchQuery.text` se mapea desde ella. Sin esto, ir a Inicio y volver pierde la consulta,
      porque `popUpTo(saveState = true)` **destruye el modelo de pantalla** (research.md D-014)
- [X] T067 [US5] En `MAIN/ui/search/SearchScreen.kt`: elevar el `LazyListState` a `rememberSaveable`
      para que la posición de desplazamiento sobreviva (FR-043)
- [X] T068 [US5] Escribir en `ATEST/ui/search/SearchContentTest.kt` —o en un fichero propio si crece
      demasiado— la comprobación de que abrir un resultado y volver conserva consulta, filtros,
      orden y posición, y de que cambiar a Inicio y volver conserva consulta y filtros (SC-009)

**Checkpoint**: las cinco historias completas.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T069 [P] Enmendar el apartado 17 de `docs/diseno/especificaciones-diseno.md` con fecha y
      motivo, como se hizo en las features 003 y 005: la barra superior va **sin flecha atrás ni
      menú**; los filtros van en hoja inferior **con chips activos en la pantalla**; **no** hay
      ordenación por relevancia, ni resaltado de coincidencias (17.2), ni búsquedas recientes ni
      bloque «Explorar por» (17.1); y la tarjeta es la **estándar**, no la compacta del apartado 12.2
- [X] T070 [P] Actualizar `CLAUDE.md`: el árbol de paquetes con `ui/search` real y `core/util/SearchText`;
      la base de datos **en la versión 3** con las dos migraciones automáticas; que `search_text`
      entra en la lista blanca de `updateColumns` **y por qué**, mientras `saved_at` y
      `first_seen_at` siguen fuera; que `PublicationSearchDao` es de solo lectura; y que sigue sin
      haber ninguna sentencia de borrado en el proyecto
- [X] T071 [P] Añadir a la sección de trampas conocidas de `CLAUDE.md` las tres de esta feature: que
      `LIKE` de SQLite **no** ignora tildes y por eso la normalización se hace al escribir; que una
      columna nueva deja las filas anteriores sin rellenar y que el relleno es **invisible en una
      instalación limpia**; y que navegar con `restoreState` se traga el argumento de una ruta
- [X] T071b [P] Decidir por escrito qué pasa con `MAIN/core/ui/component/ComingSoonMessage.kt`:
      tras T036 se queda **sin ningún llamante**, porque `SearchScreen` era el último. O se retira,
      o se conserva con un comentario que diga para qué. `ComingSoonTab`, en `ui/detail/component`,
      es otro componible y no se toca
- [X] T072 Ejecutar las cuatro puertas en orden y dejarlas en verde:
      `./gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`
      —con `adb shell settings put secure navigation_mode 0` antes— y `:app:lintDebug`
- [~] T073 Recorrer `quickstart.md` a mano en un dispositivo, **empezando por el apartado 3.1**:
      instalar la versión anterior, dejar que sincronice, instalar esta encima **sin desinstalar** y
      comprobar que el boletín sigue entero y que lo descargado con la versión anterior se encuentra.
      Es el único camino que ninguna prueba recorre sobre datos reales.
      **El apartado 3.1 quedó ejecutado y en verde el 1 de septiembre de 2026**, sobre un móvil real
      cuya base estaba en la **versión 1** con **1773 publicaciones desde 2018**: la migración saltó
      de la 1 a la 3 de una vez, el recuento no varió, el relleno terminó sin dejar una sola fila, y
      buscar `muprespa` devolvió anuncios de 2018 y 2021 que la versión anterior había descargado sin
      texto buscable. El detalle está en `quickstart.md`. **Quedan por recorrer los apartados 3.2 a
      3.8**, que confirman a mano lo que ya está cubierto por pruebas automáticas.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias.
- **Foundational (fase 2)**: depende de la fase 1. **Bloquea todas las historias.**
- **US1 (fase 3)**: depende de la fase 2.
- **US2 (fase 4)**: depende de la fase 2. **No depende de US1**: filtra en memoria y no toca el
  almacén de búsqueda.
- **US3 (fase 5)**: depende de **US1 y US2**. Es el puente entre las dos; sin ellas no hay nada que
  unir.
- **US4 (fase 6)**: depende de US1.
- **US5 (fase 7)**: depende de US1, y de US4 si se quiere que también se conserven los filtros.
- **Polish (fase 8)**: depende de todo lo anterior.

### Dentro de cada historia

- Las pruebas se escriben **antes** y deben fallar. Las dos excepciones están marcadas en su tarea
  (T013 y T015 no compilan hasta que existe la columna).
- Modelos de dominio → almacén → casos de uso → inyección → presentación → pruebas instrumentadas.

### Parallel Opportunities

- Fase 1: T001, T002 y T003 en paralelo. T004 puede ir con ellas.
- Fase 2: T005, T007 y T009 en paralelo —tres pruebas de tres ficheros distintos—. T018 en paralelo
  con T017.
- US1: T019, T020 y T023 en paralelo. T032 y T035 en paralelo. T039 y T040 en paralelo.
- US4: T056, T057 y T058 en paralelo.
- Fase 8: T069, T070 y T071 en paralelo.
- Con más de una persona: **US1 y US2 pueden hacerse a la vez** en cuanto la fase 2 esté cerrada.
  Solo se cruzan en `MainShell.kt`, y en dos líneas distintas.

---

## Parallel Example: fase 2

```bash
# Las tres pruebas de la base, a la vez: tres ficheros, ninguna dependencia entre ellos
Task: "TEST/core/util/SearchTextTest.kt"
Task: "TEST/data/source/local/PublicationSearchTextTest.kt"
Task: "TEST/data/source/local/LikePatternTest.kt"
```

---

## Implementation Strategy

### MVP primero (US1)

1. Fase 1: recursos.
2. Fase 2: normalización, columna y relleno. **Crítica: bloquea todo.**
3. Fase 3: el buscador global.
4. **PARAR Y VALIDAR**: apartados 3.1 y 3.5 de `quickstart.md`, con el paso de actualizar sobre una
   instalación anterior. Si el relleno no funciona, no seguir.
5. Demostrable.

### Entrega incremental

1. Fase 2 → base lista.
2. US1 → Buscar funciona → demo (MVP).
3. US2 → la lupa funciona → demo.
4. US3 → las dos se hablan → demo.
5. US4 → filtros y orden → demo.
6. US5 → nada se pierde al volver → demo.

Cada historia añade valor sin romper la anterior.

---

## Notes

- `[P]` = ficheros distintos, sin dependencias.
- **Prohibido** `@Ignore`, comentar o borrar una prueba para que pase la build.
- **`SavedPublicationDaoTest` no se toca en toda la feature.** Si se pone roja, es que `saved_at` se
  ha colado en `updateColumns`; el arreglo es quitarlo de la sentencia.
- **Ninguna sentencia de borrado**, en ningún DAO, en ninguna tarea.
- Commit tras cada tarea o grupo lógico, en español e imperativo, con prefijo Conventional Commits.
- Parar en cualquier *checkpoint* para validar la historia por separado.
