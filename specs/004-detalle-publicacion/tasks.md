# Tasks: Detalle de publicación y visor del PDF oficial

**Input**: Design documents from `/specs/004-detalle-publicacion/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: OBLIGATORIOS. El principio V de la constitución los declara no negociables y la
especificación los exige en FR-044 … FR-048. Dentro de cada historia, las pruebas se escriben
**antes** que la implementación y deben fallar antes de hacerlas pasar.

**Organization**: las tareas se agrupan por historia de usuario, de forma que cada una pueda
implementarse, probarse y demostrarse por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (ficheros distintos, sin dependencias entre sí)
- **[Story]**: historia a la que pertenece (US1, US2, US3, US4)

## Path Conventions

Abreviaturas: `MAIN/` = `app/src/main/java/com/jrblanco/boccantabria/`,
`TEST/` = `app/src/test/java/com/jrblanco/boccantabria/`,
`ATEST/` = `app/src/androidTest/java/com/jrblanco/boccantabria/`,
`RES/` = `app/src/main/res/`.

Antes de cualquier comando Gradle:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: la enmienda en el código, la dependencia del visor y —lo primero de todo— comprobar que
el visor sirve. Es el riesgo con más probabilidad de morder: si no sirviera, la enmienda de `minSdk`
se quedaría sin su motivo.

- [X] T001 Añadir `androidx.pdf:pdf-compose` `1.0.0-beta01` a `gradle/libs.versions.toml` y **retirar
      de él** `desugar_jdk_libs`. Ninguna coordenada ni versión literal fuera del catálogo
      (research.md D-001, D-013) (FR-040)
- [X] T002 En `app/build.gradle.kts`: `minSdk = 28`, declarar `pdf-compose`, y **eliminar**
      `isCoreLibraryDesugaringEnabled` y `coreLibraryDesugaring(...)` junto con el comentario que los
      justificaba. Si al retirarlos algo deja de compilar, se quedan y se anota **para qué sirven
      ahora** (FR-039, FR-040)
- [X] T003 **Prueba de humo del visor, antes que nada más**: componer una pantalla mínima que abra un
      PDF de ejemplo con `PdfLoader.openDocument()` y lo pinte con `PdfViewer`, y comprobar que
      compila y se ve en un dispositivo API 28+. Confirmar de paso si
      `androidx.pdf:pdf-document-service` hay que declararlo explícitamente o llega por transitividad,
      y si el BOM de Compose del proyecto convive con lo que el artefacto pide (research.md D-016).
      **Si esto no sale, hay que volver al propietario antes de seguir** (depende de T002)
- [X] T004 [P] Añadir a `RES/drawable/` los vectores que faltan, con los trazados **tomados de las
      fuentes oficiales de Material Symbols, no inventados**, con el mismo mecanismo de la feature
      003: `arrow_back`, `account_balance` (organismo), `verified_user` (documento oficial),
      `auto_awesome` (IA), `chat_bubble` (preguntar) y `description` (documento)
- [X] T005 [P] Añadir a `RES/values/strings.xml` los textos del apartado 32 del documento de diseño,
      en español: `Detalle de publicación`, `Documento oficial`, `Documento`, `Resumen IA`,
      `Preguntar`, `Abrir PDF oficial`; más las etiquetas de la ficha de metadatos, los mensajes de
      error de descarga, el aviso del caso degradado al compartir y las descripciones de
      accesibilidad de cada acción
- [X] T006 Declarar el `FileProvider` en `app/src/main/AndroidManifest.xml` con `authorities` propio
      y `RES/xml/file_paths.xml` **acotado al subdirectorio `documents` de la caché**, no a la caché
      entera: acotar es lo que impide que un fallo futuro exponga algo que no toca
      (research.md D-008) (FR-034)

**Checkpoint**: `./gradlew :app:assembleDebug` pasa con `minSdk 28`, sin azucarado y con el visor
declarado, y T003 demuestra en un dispositivo que el visor abre un PDF.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el dominio de la copia local y el almacenamiento en fichero. Todo lo que las cuatro
historias necesitan.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta que esta fase esté completa.

- [X] T007 [P] Crear `MAIN/domain/model/OfficialDocument.kt` con los cinco campos de data-model.md §2
      y su prueba `TEST/domain/model/OfficialDocumentTest.kt`: clave y ruta no vacías, tamaño mayor
      que cero, y suma de verificación de 64 caracteres hexadecimales
- [X] T008 [P] Crear `MAIN/domain/model/DocumentStatus.kt` (sellado: `Absent`, `Downloading`,
      `Available`, `Failed`) y su prueba `TEST/domain/model/DocumentStatusTest.kt`, incluido que el
      total de bytes es anulable porque el servicio puede no declararlo
- [X] T009 [P] Crear `MAIN/domain/model/ShareTarget.kt` con `LinkReason` y su prueba
      `TEST/domain/model/ShareTargetTest.kt`. Existe para poder **explicar** el caso degradado, no
      solo para señalarlo (FR-033)
- [X] T010 [P] Crear `MAIN/domain/model/DetailTab.kt` y su prueba `TEST/domain/model/DetailTabTest.kt`
      (FR-012, FR-015)
- [X] T011 Declarar `MAIN/domain/repository/DocumentRepository.kt` y ampliar
      `MAIN/domain/repository/PublicationRepository.kt` con `observePublication`, según
      `contracts/internal-contracts.md` §1.1. Documentar en KDoc que **nunca lanzan**, que
      `observePublication` emite `null` sin que eso sea un fallo, y que `ensureLocalCopy` es
      idempotente y deduplicada (FR-004, FR-022)
- [X] T012 Ampliar `TEST/data/source/local/PublicationDaoTest.kt` con `observePublication`
      —encontrada, no encontrada, y que emite de nuevo cuando la publicación cambia— y después
      añadir la consulta a `MAIN/data/source/local/PublicationDao.kt` (FR-002, FR-003, FR-004)
- [X] T013 Escribir `TEST/data/source/local/FileDocumentCacheTest.kt` **antes** que la caché:
      escritura y lectura, reutilización sin volver a escribir, que el nombre de fichero se deriva de
      una **huella** del `externalKey` y no de la clave en crudo —una clave trae `:` y `/`—, retirada
      por tope de tamaño, retirada por antigüedad de uso con el tiempo inyectado, y que la retirada
      **nunca** borra el documento en uso (FR-024, FR-045)
- [X] T014 Crear `MAIN/data/source/local/DocumentCache.kt` y `FileDocumentCache.kt` hasta hacer pasar
      T013. Escritura a `<huella>.pdf.part` y renombrado atómico al final: **nunca existe un fichero
      con el nombre bueno y contenido a medias** (FR-019) (depende de T013)

**Checkpoint**: `./gradlew :app:testDebugUnitTest` en verde con el dominio y la caché.

---

## Phase 3: User Story 1 - Abrir una publicación y leer el documento oficial (Priority: P1) 🎯 MVP

**Goal**: del titular al documento. Pulsar una tarjeta abre el detalle, y desde ahí el PDF se lee
dentro de la aplicación.

**Independent Test**: abrir el boletín, tocar una tarjeta, ver la ficha y abrir el documento.

### Tests for User Story 1 ⚠️

> Se escriben **antes** que la implementación y deben fallar antes de hacerlas pasar.

- [X] T015 [P] [US1] Escribir `TEST/data/source/remote/OkHttpDocumentDownloaderTest.kt` con
      MockWebServer sobre TLS —como el de la feature 003, con `okhttp-tls`, porque la validación
      exige `https`—: descarga correcta con su suma de verificación; 404 sin reintento; agotamiento
      de espera; y que el fichero temporal queda escrito solo si todo pasa, con su SHA-256 (FR-016, FR-020,
      FR-044)
- [X] T016 [P] [US1] Escribir `TEST/data/repository/DocumentRepositoryImplTest.kt`: descarga cuando
      falta, reutiliza cuando está, refresca la marca de uso, **dos peticiones concurrentes producen
      una sola descarga**, y ninguna excepción escapa (FR-021, FR-022)
- [X] T017 [P] [US1] Escribir las pruebas de los tres casos de uso en `TEST/domain/usecase/`:
      `ObservePublicationUseCaseTest.kt` (incluido el caso de publicación retirada),
      `ObserveOfficialDocumentUseCaseTest.kt` y `OpenOfficialDocumentUseCaseTest.kt`
      (FR-004, FR-046)
- [X] T018 [P] [US1] Escribir `TEST/ui/detail/PublicationDetailViewModelTest.kt` con `runTest` y
      Turbine: lee la clave del `SavedStateHandle`; publicación encontrada y retirada; la pestaña
      seleccionada sobrevive; **no se descarga al abrir el detalle, sí al mostrar la pestaña
      Documento**; y reintentar tras un fallo llega al documento (FR-004, FR-015, FR-046)
- [X] T019 [P] [US1] Escribir `TEST/ui/pdf/PdfViewerViewModelTest.kt`: carga, documento listo, error
      con reintento, y que el documento se cierra al limpiarse el modelo (research.md D-009)

### Implementation for User Story 1

- [X] T020 [US1] Crear `MAIN/data/source/remote/DocumentDownloader.kt` con `DownloadResult` y
      `RejectionReason`, y `OkHttpDocumentDownloader.kt` hasta hacer pasar T015. El cliente se
      **deriva** del existente con `newBuilder()` para darle 10/60/180 s: un documento tarda más que
      un feed (research.md D-004). Validación en el orden de `contracts/internal-contracts.md` §2.3,
      cortando en cuanto se sabe (FR-016, FR-018)
- [X] T021 [US1] Crear `MAIN/data/repository/DocumentRepositoryImpl.kt` hasta hacer pasar T016:
      agrupa las peticiones concurrentes tras un mismo trabajo en curso, escribe a temporal y
      renombra, borra el temporal ante cualquier fallo, y traduce el rechazo a `DomainError`
      (research.md D-012, D-015) (FR-019, FR-020, FR-021, FR-022) (depende de T014, T020)
- [X] T022 [US1] Crear los tres casos de uso en `MAIN/domain/usecase/` hasta hacer pasar T017, cada
      uno con un único `operator fun invoke` (depende de T011, T021)
- [X] T023 [US1] Registrar en `MAIN/core/di/` el descargador, la caché, el repositorio de documentos,
      los casos de uso y los dos modelos de pantalla; y ampliar `TEST/di/KoinModulesTest.kt` con los
      tipos nuevos y sus `koin.get<…>()`. El cargador de PDF se construye con **función factoría**,
      porque `core.di` no puede importar el SDK (depende de T022)
- [X] T024 [US1] Añadir `Route.Detail(externalKey)` y `Route.PdfViewer(externalKey)` a
      `MAIN/ui/navigation/Routes.kt` y registrarlas en `BOCantabriaNavHost.kt`, en el `NavHost`
      **exterior**: no llevan barra inferior, y la publicación viaja por clave y no por objeto
      (research.md D-005, D-006) (FR-006)
- [X] T025 [US1] Hacer que la tarjeta navegue: `MAIN/ui/home/component/PublicationCard.kt` acepta
      `onClick` y `MAIN/ui/main/MainShell.kt` lo enlaza a `Route.Detail`. **Cambiar
      `ATEST/ui/home/PublicationCardTest.kt`**, cuya prueba afirma hoy que la tarjeta no navega:
      justo eso es lo que esta feature añade (FR-001) (depende de T024)
- [X] T026 [US1] Crear `MAIN/ui/detail/PublicationDetailUiState.kt` según data-model.md §5.1, con
      `document` y `share` **fuera** de un sellado único porque son ejes independientes
- [X] T027 [US1] Crear `MAIN/ui/detail/PublicationDetailViewModel.kt` hasta hacer pasar T018
      (depende de T022, T026)
- [X] T028 [P] [US1] Crear `MAIN/ui/detail/component/DocumentHeader.kt` con los cinco elementos del
      apartado 18.2 en el orden del 18.3, el título en `headlineLarge` **sin recortar** y el
      distintivo perfilado. Sin hueco ni texto vacío cuando no hay organismo
      (FR-007, FR-008, FR-009, FR-010, FR-041)
- [X] T029 [P] [US1] Crear `MAIN/ui/detail/component/DetailTabs.kt` con las tres pestañas del
      apartado 18.4, indicador inferior de 3 dp y el icono de IA en la de resumen (FR-012)
- [X] T030 [P] [US1] Crear `MAIN/ui/detail/component/DetailActionBar.kt` según el apartado 18.5:
      fondo `surface` con borde superior, `Abrir PDF oficial` principal y `Preguntar` secundario,
      **apilados si no caben** (FR-037, FR-043)
- [X] T031 [US1] Crear `MAIN/ui/detail/component/DocumentTab.kt` y `DocumentPreview.kt`: ficha de
      metadatos con los seis bloques del apartado 19.2 y, debajo, la primera página obtenida con
      `PdfDocument.BitmapSource` (research.md D-011). La obtención se dispara **al mostrar la
      pestaña**, no al abrir el detalle (FR-013)
- [X] T032 [US1] Crear `MAIN/ui/detail/PublicationDetailScreen.kt` componiendo lo anterior, con la
      barra superior del apartado 18.1 —retroceso, escudo, título, guardar y compartir— y las
      etiquetas de prueba de `contracts/internal-contracts.md` §4.3 (FR-011) (depende de T027, T028, T029, T030, T031)
- [X] T033 [US1] Crear `MAIN/ui/pdf/PdfDocumentLoader.kt`, `PdfViewerUiState.kt` y
      `PdfViewerViewModel.kt` hasta hacer pasar T019. El documento es `Closeable` y lo cierra el
      modelo en `onCleared()` (research.md D-009) (depende de T022)
- [X] T034 [US1] Crear `MAIN/ui/pdf/PdfViewerScreen.kt` con `PdfViewer` y `rememberPdfViewerState`,
      la barra superior del apartado 24.1 y la zona de documento del 24.2. La primera página visible
      se guarda con `rememberSaveable` y se restaura con `scrollToPage()`, porque el estado del visor
      **no** es saveable (research.md D-010) (FR-026 … FR-029) (depende de T033)
- [X] T035 [P] [US1] Escribir `ATEST/ui/detail/DocumentHeaderTest.kt` con `createComposeRule()`: los
      cinco elementos en su orden, el título largo **completo**, y la composición sin organismo
      (FR-008, FR-047)
- [X] T036 [P] [US1] Escribir `ATEST/ui/detail/PublicationDetailContentTest.kt`: las tres pestañas, la
      barra de acciones, el estado de publicación retirada y el cambio de pestaña (FR-047)
- [X] T037 [P] [US1] Escribir `ATEST/ui/pdf/PdfViewerContentTest.kt`: cargando, documento listo y
      error con reintento, sobre el componible sin estado (FR-030, FR-047)
- [X] T038 [US1] Escribir `ATEST/ui/DetailNavigationTest.kt`: desde el boletín, tocar una tarjeta
      abre el detalle; el retroceso vuelve **en la misma posición**; y `Abrir PDF oficial` lleva al
      visor. Montar el armazón con `createComposeRule()` en lugar de atravesar el arranque, como
      documenta `CLAUDE.md` (FR-001, FR-005, FR-047, SC-001)
- [X] T039 [US1] Escribir `TEST/integration/DocumentFlowIntegrationTest.kt`: la cadena real desde el
      modelo de pantalla hasta el fichero, con un PDF de muestra servido por un doble, comprobando
      que lo que llega al visor es lo que se descargó

**Checkpoint**: US1 completa. Se entra en la publicación y se lee el documento. Punto de corte
válido si hubiera que partir la feature.

---

## Phase 4: User Story 2 - Que el documento sea de fiar, y que fallar se note (Priority: P1)

**Goal**: que nada que no sea el documento oficial llegue a presentarse como tal, y que un fallo se
explique y no deje restos.

**Independent Test**: apuntar a un enlace que devuelva algo que no sea el documento y comprobar que
se rechaza con mensaje y sin dejar fichero.

### Tests for User Story 2 ⚠️

- [X] T040 [P] [US2] Ampliar `TEST/data/source/remote/OkHttpDocumentDownloaderTest.kt` con los casos
      que dan sentido a la historia: **respuesta con código 200 y cuerpo HTML**; `Content-Type`
      inesperado; primeros bytes que no son `%PDF`; cuerpo por encima del tope; enlace que no usa
      `https`; y host que no es el del boletín. Los seis rechazados y **sin dejar fichero**
      (FR-017, FR-018, FR-044, SC-004)
- [ ] T041 [P] [US2] Ampliar `TEST/data/repository/DocumentRepositoryImplTest.kt`: un rechazo no deja
      temporal ni destino; una descarga cancelada tampoco; y tras un fallo el estado vuelve a
      permitir reintentar (FR-019, FR-023, SC-005)
- [X] T042 [P] [US2] Ampliar `TEST/ui/detail/PublicationDetailViewModelTest.kt` y
      `TEST/ui/pdf/PdfViewerViewModelTest.kt` con el fallo de descarga y su reintento (FR-025)

### Implementation for User Story 2

- [X] T043 [US2] Completar la validación de `OkHttpDocumentDownloader` hasta hacer pasar T040:
      esquema y host **antes de conectar**, tipo declarado con las cabeceras, bytes mágicos sobre el
      principio del cuerpo, y corte por tamaño durante la lectura (research.md D-002) (FR-016, FR-017, FR-018)
- [X] T044 [US2] Asegurar en `DocumentRepositoryImpl` el borrado del temporal en todos los caminos de
      salida —rechazo, error y cancelación— hasta hacer pasar T041. `CancellationException` se
      repropaga (FR-019, FR-023)
- [X] T045 [US2] Mostrar el fallo en `DocumentTab` y en `PdfViewerScreen` reutilizando `ErrorMessage`
      de `core/ui/component`, para que el error no tenga un estilo propio por pantalla (apartado 34
      del documento de diseño) (FR-025, FR-030)
- [X] T046 [P] [US2] Ampliar `ATEST/ui/detail/PublicationDetailContentTest.kt` y
      `ATEST/ui/pdf/PdfViewerContentTest.kt` con el estado de error y su acción de reintentar
      (FR-047)
- [X] T047 [P] [US2] Emitir `document_opened` desde `DocumentRepositoryImpl` con la bandera de si
      venía de la caché. **Ningún dato personal**: ni título, ni URL, ni clave (FR-029 de la 003,
      principio VI)

**Checkpoint**: US1 y US2 funcionan por separado. Lo que se lee es el documento oficial o no se lee.

---

## Phase 5: User Story 3 - Compartir el documento, no el enlace (Priority: P2)

**Goal**: compartir entrega el PDF, y degrada al enlace con una explicación cuando no puede.

**Independent Test**: compartir con el documento en caché, sin él y con conexión, y sin él en modo
avión.

### Tests for User Story 3 ⚠️

- [X] T048 [P] [US3] Escribir `TEST/domain/usecase/ShareOfficialDocumentUseCaseTest.kt`: documento en
      caché devuelve `Document`; ausente con conexión lo descarga y devuelve `Document`; ausente sin
      conexión devuelve `Link` **con su motivo**; y un fallo distinto de la falta de conexión no se
      disfraza de enlace (FR-031, FR-033, SC-007)
- [X] T049 [P] [US3] Ampliar `TEST/ui/detail/PublicationDetailViewModelTest.kt` con `ShareState`:
      pasa por `Preparing`, llega a `Ready`, y **se consume una sola vez** (FR-032)

### Implementation for User Story 3

- [X] T050 [US3] Crear `MAIN/domain/usecase/ShareOfficialDocumentUseCase.kt` hasta hacer pasar T048.
      Es el **único** sitio donde vive la regla de degradación: la pantalla pregunta y obedece
      (depende de T022)
- [X] T051 [US3] Conectar compartir en `PublicationDetailScreen` y `PdfViewerScreen`: el fichero se
      ofrece con la `content://` del `FileProvider` y permiso temporal de lectura; el enlace, como
      texto, con su explicación. **Retirar el compartir por enlace de `MainShell`**, que la feature
      003 puso en la tarjeta (FR-031, FR-034) (depende de T006, T050)
- [X] T052 [P] [US3] Emitir `document_share` con el destino (`document` o `link`). Sin datos
      personales
- [X] T053 [P] [US3] Ampliar `ATEST/ui/detail/PublicationDetailContentTest.kt` con el estado de
      preparación al compartir (FR-032)

**Checkpoint**: las tres primeras historias funcionan por separado.

---

## Phase 6: User Story 4 - Saber qué llegará y no toparse con callejones (Priority: P3)

**Goal**: las dos funciones de IA y la de guardar quedan dichas, con su sitio hecho.

**Independent Test**: recorrer las tres pestañas y las dos acciones aplazadas.

### Tests for User Story 4 ⚠️

- [X] T054 [P] [US4] Escribir `ATEST/ui/detail/ComingSoonTabTest.kt`: las pestañas de resumen y de
      preguntar dicen que llegarán próximamente **conservando el icono y la etiqueta de IA**, no solo
      el color (FR-014, FR-042)

### Implementation for User Story 4

- [X] T055 [US4] Crear `MAIN/ui/detail/component/ComingSoonTab.kt` con la identidad visual del
      apartado 20.1 —icono de IA sobre `aiContainer`, etiqueta en `aiAccent`— y el texto de
      «Próximamente». Es la primera vez que el proyecto usa esos dos tokens (FR-014, FR-042)
      (depende de T054)
- [X] T056 [US4] Conectar las acciones aplazadas en `PublicationDetailScreen`: guardar de la barra
      superior y `Preguntar` de la barra de acciones avisan de que llegarán próximamente, y
      `Abrir PDF oficial` sigue siendo la acción más destacada (FR-035, FR-036, FR-037, FR-038,
      SC-009)
      (depende de T032, T055)

**Checkpoint**: las cuatro historias funcionan. Ninguna acción visible deja sin respuesta.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: dejar la caché acotada, la documentación sin contradecir al código y las cuatro puertas
en verde.

- [X] T057 Enlazar `releaseUnused()` a un momento razonable —al terminar una sincronización, o al
      abrir un documento nuevo— para que la caché no crezca sin límite, y probarlo (FR-024)
- [X] T058 Actualizar `docs/diseno/especificaciones-diseno.md` con las dos desviaciones de esta
      feature, cada una con su motivo, como se hizo en la 003: **§19** la pestaña Documento muestra
      ficha y previsualización en lugar de bloques de texto extraído, porque del RSS no llega texto y
      el modo lectura del 19.3 queda para cuando exista esa extracción; **§24.1** el visor lleva
      Atrás, título y compartir, sin buscar en el documento ni menú de opciones, que quedan fuera de
      alcance
- [X] T059 [P] Actualizar `CLAUDE.md`: los paquetes nuevos `ui/detail` y `ui/pdf`, la nota de que
      `ui/pdf` es la única frontera con la API en beta del visor, el `FileProvider`, y las trampas
      nuevas que aparezcan al implementar
- [X] T060 [P] Actualizar `README.md`: estado de la feature, recuento de pruebas y la fila del visor
      de PDF en la tabla de stack
- [X] T061 Revisar la lista blanca `DOMAIN_CLASSES_WITHOUT_BEHAVIOUR` de
      `TEST/architecture/ArchitectureRulesTest.kt`: **no añadir** ninguna clase nueva salvo que se
      justifique por escrito. Cada entrada es un agujero en SC-010
      → Revisada: sigue con una sola entrada, `AppConfig`. Ninguna clase de esta feature se ha
      añadido; las nueve nuevas de dominio tienen su fichero de prueba. `LinkReason` estuvo a punto
      de necesitar entrada y se resolvió anidándola dentro de `ShareTarget`, que además es donde
      significa algo.
- [ ] T062 Ejecutar las cuatro puertas de calidad en orden y dejarlas en verde:
      `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug` (SC-011)
- [ ] T063 Recorrer `quickstart.md` de principio a fin en un dispositivo API 28+ y anotar los tiempos
      medidos de SC-002 y SC-003. **Se miden, no se estiman**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias. **T003 es la puerta real**: si el visor no sirviera, la
  enmienda de `minSdk` se quedaría sin su motivo y hay que volver al propietario.
- **Foundational (fase 2)**: depende de la 1. **Bloquea las cuatro historias.**
- **US1 (fase 3)**: depende de la 2. Es el MVP.
- **US2 (fase 4)**: depende de US1: endurece la descarga que US1 construye.
- **US3 (fase 5)**: depende de US1 y de la fase 1 (el `FileProvider`).
- **US4 (fase 6)**: depende de US1, porque las pestañas viven en su pantalla.
- **Polish (fase 7)**: depende de todo lo anterior.

### Dentro de cada historia

Pruebas primero, y deben fallar. Después modelos, fuentes, repositorio, casos de uso, modelo de
pantalla y composición.

---

## Parallel Example: User Story 1

```bash
# Las cinco tandas de pruebas de US1 son ficheros distintos y no dependen entre sí:
T015  OkHttpDocumentDownloaderTest
T016  DocumentRepositoryImplTest
T017  los tres tests de caso de uso
T018  PublicationDetailViewModelTest
T019  PdfViewerViewModelTest

# Y las cuatro piezas visuales del detalle, también:
T028  DocumentHeader
T029  DetailTabs
T030  DetailActionBar
T035  DocumentHeaderTest
```

---

## Implementation Strategy

### MVP primero (solo US1)

1. Fase 1: Setup. **No pasar de T003 sin haber visto un PDF en pantalla.** Es el riesgo con más
   probabilidad de morder y el que justifica la enmienda de la constitución.
2. Fase 2: Foundational. Bloquea todo.
3. Fase 3: US1.
4. **PARAR Y VALIDAR**: pasos 1, 2, 3 y 4 de `quickstart.md`.

### Entrega incremental

1. Setup + Foundational → cimientos y la certeza de que el visor sirve.
2. US1 → del titular al documento. **MVP.**
3. US2 → lo que se lee es el documento oficial, o no se lee.
4. US3 → compartir entrega el PDF.
5. US4 → sin callejones sin salida.
6. Polish → documentación coherente y las cuatro puertas.

### Riesgos anotados

- **T003 es la tarea que decide la feature.** Si `pdf-compose` no funcionara con el BOM del proyecto
  o exigiera algo que la constitución prohíbe, hay que parar y replantear con el propietario, no
  buscar un apaño.
- **La API del visor está en beta.** Solo `ui/pdf` la conoce; una versión nueva es un fichero que se
  toca.
- **La escritura de ficheros es donde se corrompen las cosas.** Temporal y renombrado atómico, y
  borrado en todos los caminos de salida. Las pruebas de T041 son las que lo sostienen.
- **Pruebas instrumentadas**: montar el componible con `createComposeRule()` en lugar de atravesar el
  arranque, y `testGraphOverrides()` obligatorio porque el grafo es de `single`. Ambas trampas están
  documentadas en `CLAUDE.md`.

---

## Notes

- `[P]` significa ficheros distintos y sin dependencias entre sí.
- Cada tarea cita el requisito o la decisión que la justifica, para que la trazabilidad sea
  auditable.
- Se compromete el trabajo tras cada tarea o grupo lógico, con mensaje en español.
- Ninguna tarea se da por terminada sin su prueba en verde. Prohibido `@Ignore`.
