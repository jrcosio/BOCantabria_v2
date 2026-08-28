# Tasks: Boletín del día — lectura del BOC y pantalla de Inicio

**Input**: Design documents from `/specs/003-boletin-del-dia/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: OBLIGATORIOS. El principio V de la constitución los declara no negociables y la
especificación los exige en FR-061 … FR-065. Dentro de cada historia, las pruebas se escriben
**antes** que la implementación y deben fallar antes de hacerlas pasar.

**Organization**: las tareas se agrupan por historia de usuario, de forma que cada una pueda
implementarse, probarse y demostrarse por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (ficheros distintos, sin dependencias entre sí)
- **[Story]**: historia a la que pertenece (US1, US2, US3, US4)

## Path Conventions

Abreviaturas: `MAIN/` = `app/src/main/java/com/jrblanco/boccantabria/`,
`TEST/` = `app/src/test/java/com/jrblanco/boccantabria/`,
`TRES/` = `app/src/test/resources/`,
`ATEST/` = `app/src/androidTest/java/com/jrblanco/boccantabria/`,
`RES/` = `app/src/main/res/`.

Antes de cualquier comando Gradle:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependencias, recursos y muestras de prueba. Sin esto no compila nada de lo demás.

- [X] T001 Añadir al catálogo `gradle/libs.versions.toml` las cuatro dependencias nuevas con las
      versiones verificadas en research.md D-017: plugin `com.google.devtools.ksp` `2.2.10-2.0.2`
      (el prefijo **debe** ser el Kotlin del proyecto), Room `2.8.4` (`room-runtime`, `room-ktx`,
      `room-compiler`, `room-testing`), `okhttp-bom` `5.5.0` con `okhttp` y `mockwebserver3-junit4`
      **sin versión**, y `com.android.tools:desugar_jdk_libs` `2.1.5`. Ninguna coordenada ni versión
      literal fuera del catálogo
- [X] T002 Configurar el build: registrar el plugin KSP en `build.gradle.kts` raíz (`apply false`) y
      en `app/build.gradle.kts`; activar `isCoreLibraryDesugaringEnabled = true` con
      `coreLibraryDesugaring(libs.desugar.jdk.libs)`; declarar
      `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`; añadir las dependencias de Room,
      OkHttp y las de prueba (`room-testing`, `mockwebserver3-junit4`) (research.md D-001, D-002,
      D-004)
- [X] T003 [P] Crear las diez muestras de prueba en `TRES/fixtures/`, tomadas de contenido público
      real del BOC y recortadas: `feed_1_disposiciones.xml`, `feed_2_2_oposiciones.xml`,
      `feed_4_3_anomalo.xml` (con los componentes de `categorias` permutados),
      `feed_8_1_vacio.xml` (canal válido con cero items), `feed_size_incorrecto.xml`,
      `feed_item_sin_categorias.xml`, `feed_fecha_invalida.xml`, `feed_campos_desconocidos.xml`,
      `feed_con_doctype.xml` y `feed_con_entidad_externa.xml` (apartado 29 del documento de feeds)
- [X] T004 [P] Añadir a `RES/drawable/` los once vectores que faltan, con los trazados **tomados de
      las fuentes oficiales de Material Symbols Outlined, no inventados** (research.md D-005):
      marcador, sin conexión, y los nueve de sección (disposiciones, personal, contratación,
      economía, expropiación, subvenciones, anuncios, judicial, elecciones)
- [X] T005 [P] Añadir a `RES/values/strings.xml` los textos nuevos, en español: título y fecha de la
      cabecera, etiqueta del recuento, nombres cortos de las nueve secciones para los chips,
      etiquetas de la barra inferior, mensajes de estado vacío por selección, mensaje de error de
      sincronización, texto del aviso de falta de conexión, «Próximamente», y las descripciones de
      accesibilidad de menú, lupa, información, guardar y compartir

**Checkpoint**: `./gradlew :app:assembleDebug` pasa con las dependencias nuevas. Es el punto de
control que confirma que Room, KSP y AGP 9.3.2 se llevan bien; si no, se resuelve aquí y no más
adelante.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el dominio, la base de datos y la limpieza del marcador de posición. Todo lo que las
cuatro historias necesitan.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta que esta fase esté completa.

- [X] T006 [P] Crear los cuatro enumerados de dominio en `MAIN/domain/model/`: `EditionType.kt`
      (`ORD`, `EXT`, `UNKNOWN`), `IdSource.kt` (`BLOB_ID`, `CANONICAL_URL`, `CONTENT_HASH`),
      `ParserWarning.kt` (los cuatro avisos de data-model.md §2) y `SectionColorGroup.kt` (los cinco
      grupos de research.md D-013)
- [X] T007 [P] Crear `MAIN/domain/model/Publication.kt` con los catorce campos de data-model.md §2 y
      su prueba `TEST/domain/model/PublicationTest.kt`, que verifica los invariantes: clave externa y
      título no vacíos, enlace `https`, ruta de organismo sin elementos vacíos (FR-018)
- [X] T008 [P] Crear `MAIN/domain/model/BocSection.kt` y su prueba `TEST/domain/model/BocSectionTest.kt`:
      código, nombre, nombre corto, sección padre, orden y grupo cromático. La prueba fija el mapeo
      de las nueve secciones sobre los cinco colores de D-013 (FR-043)
- [X] T009 [P] Crear `MAIN/domain/model/HomeSelection.kt` (sellado: boletín del día o sección) y su
      prueba `TEST/domain/model/HomeSelectionTest.kt` (FR-034, FR-035)
- [X] T010 [P] Crear `MAIN/domain/model/SyncSummary.kt` con los seis recuentos y las derivadas
      `allFailed` e `isComplete`, y su prueba `TEST/domain/model/SyncSummaryTest.kt` (FR-004, FR-027)
- [X] T011 [P] Crear `MAIN/domain/model/BulletinHeaderData.kt` —título de la selección, fecha y
      recuento— y su prueba `TEST/domain/model/BulletinHeaderDataTest.kt` (FR-032, FR-033)
- [X] T012 Declarar los contratos en `MAIN/domain/repository/PublicationRepository.kt` y
      `MAIN/domain/repository/BocSectionRepository.kt` con las firmas de
      `contracts/internal-contracts.md` §1.1. Documentar en KDoc que **nunca lanzan** y que
      `CancellationException` se repropaga
- [X] T013 [P] Crear `MAIN/core/util/TimeProvider.kt` con su implementación real y su prueba
      `TEST/core/util/TimeProviderTest.kt` —el nombre evita confundirlo con `java.time.Clock` en el
      punto de uso—. Sin esto la caducidad de treinta minutos no es comprobable de
      forma determinista (research.md, tabla de *Complexity Tracking*)
- [X] T014 [P] Crear `MAIN/core/util/RandomProvider.kt` con su implementación real y su prueba
      `TEST/core/util/RandomProviderTest.kt`, para que la espera aleatoria del reintento sea
      determinista en pruebas (research.md D-010)
- [X] T015 [P] Crear `MAIN/data/source/local/Converters.kt` (`LocalDate` ↔ texto ISO, lista de
      organismo y conjunto de avisos) y su prueba `TEST/data/source/local/ConvertersTest.kt`,
      incluida la ida y vuelta con caracteres separadores dentro de un nombre de organismo
      (research.md D-004)
- [X] T016 Crear las entidades `MAIN/data/source/local/PublicationEntity.kt` y
      `FeedSyncStateEntity.kt` con las columnas e índices de data-model.md §4.3: clave primaria
      `external_key`, índice **único** sobre `blob_id` cuando no es nulo, e índices sobre
      `publication_date`, `section_code`, `subsection_code`, `edition_type` y el compuesto
      `feed_id` + `publication_date`
- [X] T017 Escribir `TEST/data/source/local/PublicationDaoTest.kt` **antes** que el DAO, con
      Robolectric y base en memoria (`@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)`):
      inserción, actualización que conserva `first_seen_at` y refresca `last_seen_at`, unicidad por
      `blob_id`, consulta del boletín del día, consulta por sección principal que incluye sus
      subsecciones, consulta por subsección, recuento, y **orden estable** con fechas iguales
      (FR-020, FR-028)
- [X] T018 Crear `MAIN/data/source/local/PublicationDao.kt`, `FeedSyncStateDao.kt` y
      `BocDatabase.kt` (versión 1, esquema exportado a `app/schemas/`) hasta hacer pasar T017.
      **El DAO no declara ninguna sentencia de borrado de publicaciones**: es lo que garantiza FR-021
      (depende de T015, T016, T017)
- [X] T019 [P] Crear `MAIN/data/source/remote/BocFeedCatalog.kt` con las diecinueve definiciones de
      data-model.md §4.2 y su prueba `TEST/data/source/remote/BocFeedCatalogTest.kt`, que verifica:
      hay diecinueve entradas, los identificadores son los publicados, **las direcciones están
      escritas literalmente y no compuestas por cálculo**, y entre todas cubren las nueve secciones y
      las catorce subsecciones sin solapes (FR-002, FR-003)
- [X] T020 [P] Crear `MAIN/data/repository/BocSectionRepositoryImpl.kt` con el árbol oficial de
      secciones y su prueba `TEST/data/repository/BocSectionRepositoryImplTest.kt`: nueve principales,
      catorce subsecciones, orden oficial, y que las secciones 2, 4, 7 y 8 se declaran sin fuente propia
      (FR-043, FR-044)
- [ ] T021 Retirar la cadena de relleno de la feature 001 (research.md D-015): eliminar de `MAIN/`
      `domain/model/ContentItem.kt`, `domain/repository/ContentRepository.kt`,
      `domain/usecase/GetContentItemsUseCase.kt`, `data/repository/ContentRepositoryImpl.kt`,
      `data/source/remote/ContentItemDto.kt`, `ContentRemoteDataSource.kt`,
      `StubContentRemoteDataSource.kt`, `data/source/local/ContentItemEntity.kt`,
      `ContentLocalDataSource.kt` e `InMemoryContentLocalDataSource.kt`; y de las pruebas
      `TEST/domain/usecase/GetContentItemsUseCaseTest.kt`,
      `TEST/data/repository/ContentRepositoryImplTest.kt`,
      `TEST/integration/ContentFlowIntegrationTest.kt` y
      `ATEST/fake/FakeContentRemoteDataSource.kt`. Es sustitución, no supresión: lo que cubrían lo
      cubren las pruebas de la cadena real (depende de T012)
- [ ] T022 Actualizar el grafo tras la retirada: `MAIN/core/di/DataModule.kt`, `DomainModule.kt` y
      `CoreModule.kt` (registrar `Clock`, `RandomProvider`, base de datos, DAOs y el repositorio de
      secciones mediante **funciones factoría en `data`**, porque `core.di` no puede importar el
      SDK); `TEST/di/KoinModulesTest.kt` (ampliar `CROSS_MODULE_TYPES` y la lista de `koin.get<…>()`,
      y **sustituir la base de datos por una en memoria** dentro del bloque
      `loadModules(…, allowOverride = true)`); y `ATEST/fake/TestGraph.kt`, cuyo `testGraphOverrides()`
      debe reconstruir la cadena entera —fuente remota falsa, base en memoria y repositorio— porque
      el grafo es de `single` y se filtra entre pruebas instrumentadas (FR-011 de la 001; trampa
      documentada en `CLAUDE.md`)

**Checkpoint**: `./gradlew :app:testDebugUnitTest` en verde con el dominio, la base de datos y el
catálogo. El grafo resuelve. Ya no queda nada de la cadena inventada.

---

## Phase 3: User Story 1 - Ver el boletín del día nada más abrir (Priority: P1) 🎯 MVP

**Goal**: que el BOC real llegue a la pantalla. Leer las diecinueve fuentes, normalizarlas,
guardarlas y mostrarlas en Inicio con la cabecera editorial y las tarjetas del diseño.

**Independent Test**: instalación limpia con conexión; deben aparecer publicaciones reales con su
organismo, su título y su fecha, y la cabecera con la fecha de la última edición y el recuento.

### Tests for User Story 1 ⚠️

> Se escriben **antes** que la implementación y deben fallar antes de hacerlas pasar.

- [X] T023 [P] [US1] Escribir `TEST/data/source/remote/BocRssParserTest.kt` con la matriz completa del
      apartado 28 sobre las muestras de T003: canal válido, `size` 100, `size` 0, `size` ausente,
      `size` no numérico, sin items, nodo desconocido, los cuatro campos, campos en orden distinto,
      título largo, título con caracteres especiales, sin `categorias`, fecha inválida, enlace
      inválido, y **rechazo del cuerpo con declaración de tipo de documento y con entidad externa**
      (FR-008, FR-010, FR-061)
- [X] T024 [P] [US1] Escribir `TEST/data/source/remote/PublicationNormalizerTest.kt`: tres, cuatro y
      cinco componentes; `ORD` al final, al principio y en medio; `EXT`; sin tipo de edición;
      sección que no corresponde a la fuente; componentes vacíos; barra final; el desorden real del
      feed 4.3; enlace sin identificador (los tres escalones de la cascada); y que el título se
      conserva íntegro (FR-011 … FR-018, FR-061)
- [X] T025 [P] [US1] Escribir `TEST/data/source/remote/OkHttpPublicationRemoteDataSourceTest.kt` con
      MockWebServer: respuesta correcta, 404 sin reintento, 500 con tres reintentos y esperas
      inyectadas, tipo de contenido inesperado, cuerpo por encima del tope de 5 MB, agotamiento de
      espera, huella coincidente que devuelve `NotModified`, y que un esquema distinto de `https` se
      rechaza sin conectar (FR-006, FR-007, FR-008, FR-022)
- [ ] T026 [P] [US1] Escribir `TEST/data/repository/PublicationRepositoryImplTest.kt`: primera
      obtención, segunda sin cambios, publicación nueva, publicación actualizada, publicación que
      sale de la ventana de cien y **sigue guardada**, una fuente que falla y las demás siguen, todas
      fallan con caché, todas fallan sin caché, duplicado entre fuentes, y que nunca se lanza una
      excepción (FR-004, FR-019 … FR-022, FR-027, FR-062)
- [ ] T027 [P] [US1] Escribir las pruebas de los cuatro casos de uso en `TEST/domain/usecase/`:
      `ObservePublicationsUseCaseTest.kt`, `ObserveBulletinHeaderUseCaseTest.kt`,
      `RefreshPublicationsUseCaseTest.kt` (incluido `force = false` con caché fresca, que no toca la
      red) y `GetBocSectionsUseCaseTest.kt` (FR-023, FR-063)
- [ ] T028 [P] [US1] Escribir `TEST/ui/home/HomeViewModelTest.kt` con `runTest` y Turbine: arranque en
      frío con base vacía que mantiene los esqueletos, arranque con caché que pinta al instante,
      resultado vacío que es `Empty` y no `Error`, fallo sin caché que es `Error`, y que reintentar
      con una carga en curso no lanza una segunda (FR-039, FR-040, FR-063)

### Implementation for User Story 1

- [X] T029 [P] [US1] Crear los DTO `MAIN/data/source/remote/RssChannelDto.kt` y `RssItemDto.kt`, con
      todos los campos anulables a propósito: reflejan lo que llega, no lo que debería llegar
      (data-model.md §4.1)
- [X] T030 [US1] Implementar `MAIN/data/source/remote/BocRssParser.kt` hasta hacer pasar T023: guarda
      previa de texto contra `<!DOCTYPE` y `<!ENTITY`, endurecimiento de la fábrica con **cada
      bandera dentro de un `runCatching`**, análisis por nombre de etiqueta, nodos desconocidos
      ignorados y tope de 500 publicaciones. **Kotlin puro: ni un `import android.*`**
      (research.md D-003) (depende de T029)
- [X] T031 [US1] Implementar `MAIN/data/source/remote/PublicationNormalizer.kt` hasta hacer pasar
      T024, siguiendo el algoritmo del apartado 10.2 y las once reglas de
      `contracts/internal-contracts.md` §2.5. La sección la manda la fuente; `categorias` se conserva
      en crudo (research.md D-006, D-007) (depende de T029)
- [X] T032 [US1] Implementar la fuente remota hasta hacer pasar T025:
      `MAIN/data/source/remote/PublicationRemoteDataSource.kt` con el sellado `FeedFetchResult`,
      `OkHttpPublicationRemoteDataSource.kt` y `OkHttpFactory.kt` (función factoría, para que
      `core.di` no vea el SDK). Límites 10/45/60 s, cabeceras por interceptor, cuerpo en *streaming*
      con corte a 5 MB, tres reintentos con esperas 2/5/15 s más aleatoriedad **inyectada**, y
      `Retry-After` respetado (research.md D-002, D-010) (depende de T014, T030, T031)
- [ ] T033 [US1] Implementar `MAIN/data/repository/PublicationRepositoryImpl.kt` hasta hacer pasar
      T026: semáforo de cuatro fuentes simultáneas, **escritura por fuente en cuanto termina** —no al
      final de todas—, inserción-o-actualización por clave externa, huella del cuerpo guardada, y la
      tabla de política de `contracts/internal-contracts.md` §2.1. Ninguna excepción escapa;
      `CancellationException` se repropaga (research.md D-009) (depende de T013, T018, T032)
- [ ] T034 [US1] Implementar los cuatro casos de uso en `MAIN/domain/usecase/` hasta hacer pasar T027:
      `ObservePublicationsUseCase.kt`, `ObserveBulletinHeaderUseCase.kt`,
      `RefreshPublicationsUseCase.kt` y `GetBocSectionsUseCase.kt`, cada uno con un único
      `operator fun invoke` (depende de T012, T033)
- [ ] T035 [US1] Registrar en `MAIN/core/di/DataModule.kt`, `DomainModule.kt` y `UiModule.kt` el
      cliente HTTP, la fuente remota, el analizador, el normalizador, el repositorio de publicaciones,
      los cuatro casos de uso y `HomeViewModel`; y ampliar `TEST/di/KoinModulesTest.kt` en
      consecuencia (depende de T034)
- [ ] T036 [US1] Reescribir `MAIN/ui/home/HomeUiState.kt` como la `data class` de data-model.md §5,
      con el sellado `HomeContent` dentro y `isRefreshing` e `isOffline` **fuera**, porque son ejes
      independientes del contenido (FR-026, FR-041)
- [ ] T037 [US1] Reescribir `MAIN/ui/home/HomeViewModel.kt` hasta hacer pasar T028: observa
      publicaciones y cabecera, distingue arranque en frío de arranque con caché (research.md D-009),
      y registra la vista de pantalla (depende de T034, T036)
- [ ] T038 [P] [US1] Crear `MAIN/ui/home/component/PublicationCard.kt` y `PublicationCardSkeleton.kt`
      según el apartado 12.1 y el contrato visual: línea de sección de 4 dp con el color del grupo,
      organismo en `labelMedium` a dos líneas, título en `titleMedium` a cuatro, fecha con icono, y
      las acciones abajo a la derecha. Sin estado, todo por parámetro. Ni un literal de color, tamaño
      o espaciado (FR-037, FR-038, FR-039, FR-058)
- [ ] T039 [P] [US1] Crear `MAIN/ui/home/component/BulletinHeader.kt` según el apartado 14.3: fondo
      `primary`, título en `headlineLarge`, fecha en formato largo español y distintivo perfilado con
      **el recuento de publicaciones, no un número de boletín** (FR-032, FR-033)
- [ ] T040 [P] [US1] Crear `MAIN/ui/home/component/HomeTopBar.kt` con la composición de FR-031: menú
      al inicio, escudo de 34 dp y título, lupa e información al final. **Sin campana**
- [ ] T041 [US1] Reescribir `MAIN/ui/home/HomeScreen.kt` componiendo lo anterior sobre `Scaffold`, con
      los cuatro estados del contenido y las etiquetas de prueba de
      `contracts/internal-contracts.md` §4.3 (depende de T037, T038, T039, T040)
- [ ] T042 [P] [US1] Escribir `ATEST/ui/home/PublicationCardTest.kt` con `createComposeRule()`:
      organismo, título largo a cuatro líneas, fecha, línea de sección y las dos acciones (FR-064)
- [ ] T043 [P] [US1] Escribir `ATEST/ui/home/HomeContentTest.kt` con `createComposeRule()`: esqueletos,
      contenido, vacío y error con su acción de reintentar, sobre el componible sin estado y sin
      grafo (FR-064)
- [ ] T044 [P] [US1] Escribir `ATEST/ui/BocRssParserDeviceTest.kt`: prueba de humo que confirma que el
      analizador también funciona en un dispositivo real, con las banderas de seguridad que Android
      sí acepta. Sin ella, research.md D-003 sería una suposición
- [ ] T045 [US1] Escribir `TEST/integration/BulletinFlowIntegrationTest.kt`, que sustituye al de la
      feature 001: grafo real con base en memoria y fuente remota doble, recorriendo
      `HomeViewModel → caso de uso → repositorio → analizador → base de datos` (depende de T035)

**Checkpoint**: US1 completa. Con conexión, la aplicación muestra el boletín del día real. Punto de
corte válido si hubiera que partir la feature.

---

## Phase 4: User Story 2 - Que lo consultado siga estando sin conexión (Priority: P1)

**Goal**: que lo guardado se pinte al instante, que la falta de conexión se comunique sin estorbar y
que el gesto de refresco funcione sin hacer desaparecer el contenido.

**Independent Test**: abrir con conexión, cerrar, activar el modo avión y reabrir: mismo contenido,
de inmediato, con aviso de falta de conexión.

### Tests for User Story 2 ⚠️

- [ ] T046 [P] [US2] Ampliar `TEST/data/repository/PublicationRepositoryImplTest.kt` con la caducidad
      de treinta minutos usando el `Clock` inyectado: caché fresca no toca la red, caché caducada sí,
      y `force = true` sincroniza siempre (FR-023, FR-024)
- [ ] T047 [P] [US2] Ampliar `TEST/ui/home/HomeViewModelTest.kt`: `isOffline` cuando el resumen dice
      que todas las fuentes fallaron pero hay contenido; `isRefreshing` durante la actualización sin
      que el contenido se vacíe; y que dos gestos seguidos no lanzan dos sincronizaciones (FR-025,
      FR-026, FR-041)

### Implementation for User Story 2

- [ ] T048 [P] [US2] Crear `MAIN/core/ui/component/OfflineBanner.kt` según el apartado 26.5: icono de
      sin conexión, forma `BocBannerShape`, texto breve, **y que no tapa el contenido** (FR-041)
- [ ] T049 [US2] Implementar en `MAIN/ui/home/HomeViewModel.kt` la caducidad y las banderas hasta
      hacer pasar T046 y T047: sincronización al abrir solo si la caché está caducada, guarda contra
      sincronizaciones simultáneas, y `isOffline` a partir de `SyncSummary.allFailed`
      (depende de T037, T046, T047)
- [ ] T050 [US2] Añadir a `MAIN/ui/home/HomeScreen.kt` el gesto de deslizar para refrescar con
      indicador lineal fino bajo la barra superior, manteniendo el contenido visible durante toda la
      actualización, y colocar el aviso de falta de conexión (FR-024, FR-026, FR-041)
      (depende de T041, T048, T049)
- [ ] T051 [P] [US2] Ampliar `ATEST/ui/home/HomeContentTest.kt` con los dos estados nuevos: aviso de
      falta de conexión con contenido a la vista, y actualización en curso sin que el contenido
      desaparezca (FR-064)
- [ ] T052 [P] [US2] Emitir la telemetría de sincronización desde
      `MAIN/data/repository/PublicationRepositoryImpl.kt` a través de `AnalyticsTracker`: evento
      `boc_sync` con `succeeded`, `failed`, `inserted` y `updated`; los fallos de fuente, a
      `CrashReporter` como no fatales. **Ningún dato personal** (FR-029)

**Checkpoint**: US1 y US2 funcionan por separado. La aplicación ya es útil sin cobertura.

---

## Phase 5: User Story 3 - Explorar el BOC por secciones (Priority: P2)

**Goal**: el panel lateral con las nueve secciones, su filtro de texto y sus subsecciones
desplegables, más los chips, cambiando lo que Inicio muestra.

**Independent Test**: abrir el panel, filtrar por texto, desplegar una sección, elegir una
subsección y ver que la lista y la cabecera cambian; el chip «Todo» devuelve al boletín del día.

### Tests for User Story 3 ⚠️

- [ ] T053 [P] [US3] Escribir `TEST/ui/sections/SectionsViewModelTest.kt` con `runTest` y Turbine:
      árbol completo al inicio, filtrado por texto que **expande automáticamente** las secciones cuyas
      subsecciones coinciden, desplegar y contraer, y filtro sin coincidencias (FR-044, FR-045)
- [ ] T054 [P] [US3] Ampliar `TEST/ui/home/HomeViewModelTest.kt` con la selección: leerla del
      `SavedStateHandle`, cambiar de boletín del día a sección, sección sin publicaciones que da
      `Empty` y no `Error`, y que la cabecera pasa a nombrar la sección (FR-035, FR-040, FR-048)

### Implementation for User Story 3

- [ ] T055 [P] [US3] Crear `MAIN/ui/sections/SectionsUiState.kt` con `SectionRow` según
      data-model.md §5
- [ ] T056 [US3] Crear `MAIN/ui/sections/SectionsViewModel.kt` hasta hacer pasar T053, y registrarlo
      en `MAIN/core/di/UiModule.kt` y en `TEST/di/KoinModulesTest.kt` (depende de T053, T055)
- [ ] T057 [US3] Crear `MAIN/ui/sections/SectionsDrawerContent.kt` según el apartado 16: campo de
      filtro, filas de 72 dp con icono de 28 dp, número y nombre en `titleMedium`, chevron con
      animación de rotación, y subsecciones sobre `surfaceSoft` con radio 12 dp y sangría.
      **Sin campanas y sin tarjeta de alertas** (FR-043 … FR-047) (depende de T004, T055)
- [ ] T058 [P] [US3] Crear `MAIN/ui/home/component/SectionFilterChips.kt` según el apartado 14.4:
      fila con desplazamiento horizontal, «Todo» más las nueve secciones con su nombre corto, y el
      estado seleccionado marcado por forma además de por color (FR-036, FR-038)
- [ ] T059 [US3] Modificar `MAIN/ui/navigation/Routes.kt` para que `Home` sea `data class` con
      `sectionCode` y `subsectionCode` opcionales, y actualizar `BOCantabriaNavHost.kt`: el arranque
      navega a `Home()` con `popUpTo(Splash) { inclusive = true }` **sin cambios**, y elegir sección
      navega con `popUpTo<Home> { inclusive = true }` para que la pila conserve una sola entrada de
      Inicio (research.md D-014) (FR-046, FR-057)
- [ ] T060 [US3] Crear `MAIN/ui/main/MainShell.kt`: `ModalNavigationDrawer` con el panel, alrededor
      del `NavHost`, con el arranque **fuera** del armazón (research.md D-016)
      (depende de T057, T059)
- [ ] T061 [US3] Conectar la selección en `MAIN/ui/home/HomeViewModel.kt` leyéndola del
      `SavedStateHandle` hasta hacer pasar T054, y enlazar los chips y el panel a la navegación
      (depende de T054, T058, T060)
- [ ] T062 [P] [US3] Escribir `ATEST/ui/sections/SectionsDrawerTest.kt` con `createComposeRule()`:
      las nueve secciones, desplegar y contraer, filtrar por texto, y que elegir emite el evento con
      el código correcto (FR-064)
- [ ] T063 [US3] Escribir `ATEST/ui/HomeNavigationTest.kt` con `createAndroidComposeRule<MainActivity>()`
      y `testGraphOverrides()`: tras el arranque, abrir el panel desde la barra superior, elegir una
      subsección y comprobar que la cabecera y la lista cambian. **Una sola llamada a `setContent`**
      (trampa documentada en `CLAUDE.md`) (FR-064)

**Checkpoint**: las tres primeras historias funcionan por separado. El BOC es navegable.

---

## Phase 6: User Story 4 - Moverse por la aplicación sin toparse con callejones (Priority: P3)

**Goal**: la barra inferior de tres destinos, las dos pantallas de relleno y las acciones que
todavía no hacen nada pero lo dicen.

**Independent Test**: recorrer los tres destinos y las cinco acciones aplazadas comprobando que
ninguna deja sin respuesta.

### Tests for User Story 4 ⚠️

- [ ] T064 [P] [US4] Escribir `ATEST/ui/BottomBarNavigationTest.kt`: los tres destinos navegan, la
      barra marca el activo, y **no existe un destino de Avisos** (FR-049, FR-050)
- [ ] T065 [P] [US4] Ampliar `ATEST/ui/home/PublicationCardTest.kt`: guardar invoca su callback y
      pulsar el cuerpo de la tarjeta **no** invoca ninguna navegación (FR-055, FR-056)

### Implementation for User Story 4

- [ ] T066 [P] [US4] Crear `MAIN/core/ui/component/ComingSoonMessage.kt` con la composición de estado
      vacío del apartado 26.3, reutilizable por las dos pantallas de relleno (FR-051)
- [ ] T067 [P] [US4] Crear `MAIN/ui/search/SearchScreen.kt` y `MAIN/ui/saved/SavedScreen.kt` usando
      `ComingSoonMessage`, y añadir `Route.Search` y `Route.Saved` a
      `MAIN/ui/navigation/Routes.kt` y al `NavHost` (FR-051) (depende de T059, T066)
- [ ] T068 [US4] Crear `MAIN/ui/navigation/BocBottomBar.kt` según el apartado 10.1 con **tres**
      destinos, `launchSingleTop`, `popUpTo(Home) { saveState = true }` y `restoreState = true`;
      integrarla en `MainShell.kt` (FR-049, FR-050) (depende de T060, T067)
- [ ] T069 [US4] Conectar las acciones aplazadas en `MAIN/ui/home/HomeScreen.kt`: la lupa muestra un
      aviso «Próximamente», el icono de información está presente y no hace nada, guardar en la
      tarjeta muestra el aviso, y compartir lanza la hoja del sistema con el enlace del documento
      (FR-052 … FR-055) (depende de T041, T068)
- [ ] T070 [US4] Verificar que `ATEST/ui/SplashBackStackTest.kt` sigue en verde con las rutas nuevas
      y ampliarlo si hace falta para afirmar que el retroceso desde Inicio **con sección
      seleccionada** también deja la pila vacía (FR-057)

**Checkpoint**: las cuatro historias funcionan. La aplicación está completa para esta feature.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: dejar la documentación sin contradecir al código y pasar las cuatro puertas.

- [ ] T071 Actualizar `docs/diseno/especificaciones-diseno.md` con las cuatro desviaciones acordadas,
      cada una con su motivo (FR-060): **§10.1** tres destinos en lugar de cuatro, Avisos aplazado;
      **§10.2 y §14.2** nueva composición de la barra superior, sin campana; **§14.3** el distintivo
      muestra el recuento porque el servicio oficial no publica el número de boletín en estas fuentes;
      **§16** «Pantalla Secciones» pasa a ser panel lateral, sin campanas ni tarjeta de alertas, con
      una frase que aclare que no choca con §33 porque este panel no lleva cabecera de autor.
      Ajustar también la checklist del §36
- [ ] T072 [P] Actualizar `CLAUDE.md`: los paquetes nuevos en la sección de arquitectura, Room y
      OkHttp en la de dependencias, la nota de que la elección de datos ya está tomada, y **corregir
      que las reglas de Konsist son ocho, no seis** (el texto quedó desfasado en el commit `f42fe40`)
- [ ] T073 [P] Actualizar `README.md`: estado de la feature, recuento de pruebas, y sustituir «Datos:
      *Por decidir*» por la decisión tomada
- [ ] T074 Revisar la lista blanca `DOMAIN_CLASSES_WITHOUT_BEHAVIOUR` de
      `TEST/architecture/ArchitectureRulesTest.kt`: retirar `ContentItem`, que ya no existe, y **no
      añadir** ninguna clase nueva salvo que se justifique por escrito. Cada entrada es un agujero en
      SC-011
- [ ] T075 Ejecutar las cuatro puertas de calidad en orden y dejarlas en verde:
      `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug` (SC-012)
- [ ] T076 Recorrer `quickstart.md` de principio a fin en un dispositivo real y anotar los tiempos
      medidos de SC-001 y SC-002. **Se miden, no se estiman**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias. El punto de control es que compile con Room y KSP.
- **Foundational (fase 2)**: depende de la 1. **Bloquea las cuatro historias.**
- **US1 (fase 3)**: depende de la 2. Es el MVP.
- **US2 (fase 4)**: depende de US1, porque comparte el repositorio y el modelo de pantalla.
- **US3 (fase 5)**: depende de la 2 y, en la práctica, de US1 para tener algo que filtrar.
- **US4 (fase 6)**: depende de US3, porque la barra inferior vive en el mismo armazón que el panel.
- **Polish (fase 7)**: depende de todo lo anterior.

### Dependencias entre historias

Al contrario que en una aplicación con historias verdaderamente aisladas, aquí US2, US3 y US4 se
apoyan en la pantalla que construye US1. Es consecuencia de que las cuatro comparten una sola
pantalla, no de un mal reparto. Cada una sigue siendo **demostrable por separado**, que es lo que
exige la plantilla.

### Dentro de cada historia

Pruebas primero, y deben fallar. Después modelos, después fuentes, después repositorio, después
casos de uso, después modelo de pantalla, después composición.

---

## Parallel Example: User Story 1

```bash
# Las cinco tandas de pruebas de US1 son ficheros distintos y no dependen entre sí:
T023  BocRssParserTest
T024  PublicationNormalizerTest
T025  OkHttpPublicationRemoteDataSourceTest
T026  PublicationRepositoryImplTest
T027  los cuatro tests de caso de uso
T028  HomeViewModelTest

# Y las tres piezas visuales, también:
T038  PublicationCard + PublicationCardSkeleton
T039  BulletinHeader
T040  HomeTopBar
```

---

## Implementation Strategy

### MVP primero (solo US1)

1. Fase 1: Setup. **No pasar de aquí hasta que `assembleDebug` compile con Room y KSP**: es el
   riesgo técnico con más probabilidad de morder, por las versiones adelantadas de AGP.
2. Fase 2: Foundational. Bloquea todo.
3. Fase 3: US1.
4. **PARAR Y VALIDAR**: pasos 1 y 6 de `quickstart.md`.

### Entrega incremental

1. Setup + Foundational → cimientos.
2. US1 → el BOC real en pantalla. **MVP.**
3. US2 → útil sin cobertura.
4. US3 → navegable por secciones.
5. US4 → sin callejones sin salida.
6. Polish → documentación coherente y las cuatro puertas.

### Riesgos anotados

- **Versiones**: el proyecto va por delante en AGP y Compose. Room, KSP y el *desugaring* hay que
  confirmarlos contra AGP 9.3.2 en T002, no al final.
- **Banderas de seguridad del analizador**: la JVM y Android no aceptan las mismas. Por eso la guarda
  previa es de texto y hay una prueba de humo en dispositivo (T044).
- **Pruebas instrumentadas**: todas atraviesan el mínimo de 1,2 s del arranque y comparten proceso
  con un grafo de `single`. `testGraphOverrides()` es obligatorio y debe reconstruir la cadena
  entera, incluida la base de datos.
- **`SplashRestorationTest`** tiene una intermitencia conocida y documentada. Si aparece, no es de
  esta feature; si aparece **otra** distinta, sí hay que investigarla.

---

## Notes

- `[P]` significa ficheros distintos y sin dependencias entre sí.
- Cada tarea cita el requisito o la decisión que la justifica, para que la trazabilidad sea
  auditable.
- Se compromete el trabajo tras cada tarea o grupo lógico, con mensaje en español.
- Ninguna tarea se da por terminada sin su prueba en verde. Prohibido `@Ignore`.
