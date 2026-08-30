# Tasks: Publicaciones guardadas

**Input**: Design documents from `/specs/005-publicaciones-guardadas/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: OBLIGATORIOS. El principio V de la constitución los declara no negociables y la
especificación los exige en SC-004, SC-005, SC-006 y SC-010. Dentro de cada historia, las pruebas se
escriben **antes** que la implementación y deben fallar antes de hacerlas pasar.

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

**Purpose**: los dos recursos que todo lo demás necesita. No hay dependencias nuevas que añadir
(research.md D-015): si en la revisión aparece una coordenada en `libs.versions.toml`, hay que
preguntar por qué.

- [X] T001 [P] Añadir `RES/drawable/ic_bookmark_filled.xml`: Material Symbols **relleno**, con el
      trazado tomado de la fuente oficial sin modificarlo, el mismo lienzo de 960 y el grupo
      trasladado que usan los otros diecinueve iconos del proyecto (research.md D-009) (FR-003)
- [X] T002 [P] En `RES/values/strings.xml`: añadir `saved_title` («Guardados»), `saved_empty_title`
      («Aún no has guardado publicaciones»), `saved_empty_body`, `saved_empty_action` («Explorar el
      BOC»), `publication_unsave` y `detail_unsave` («Quitar de guardados») y `save_failed`. **Retirar
      `coming_soon_saved`**: se queda sin usar y lint señala los recursos muertos (FR-004, FR-009,
      FR-016, FR-017)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: la marca, su almacén y la actualización de la base de datos. Aquí viven las dos pruebas
que sostienen la historia 3 —que una sincronización no pierde la marca y que una instalación anterior
no pierde el boletín—, porque tienen que existir antes de que se pueda construir cualquier pantalla.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta que esta fase esté completa.

- [X] T003 Escribir `TEST/data/source/local/SavedPublicationDaoTest.kt` **antes** que el DAO
      (Robolectric + base de datos en memoria, como el resto de este proyecto, donde **nunca** se
      falsea un DAO): orden por instante de guardado descendente con desempate por clave; desmarcar
      deja `saved_at` en `NULL` y **la publicación sigue almacenada**; marcar una clave que no está
      almacenada afecta a **cero** filas y no falla; y **la regresión que importa**: un `upsertAll`
      que vuelve a traer una publicación ya guardada **no borra la marca** (FR-011, FR-020, FR-021,
      SC-004, SC-005)
- [X] T004 Añadir a `MAIN/data/source/local/PublicationEntity.kt` la columna
      `@ColumnInfo(name = "saved_at") val savedAt: Long? = null` —última y con valor por defecto— y el
      índice `Index(value = ["saved_at"])`. `Publication.toEntity(seenAt)` **no la rellena**: una
      inserción venida de la fuente no puede inventarse una marca (data-model.md §4.1)
- [X] T005 Crear `MAIN/data/source/local/SavedPublicationDao.kt` con las tres sentencias de
      data-model.md §4.2. **Sin `@Delete` ni `DELETE FROM`**: desmarcar es `setSavedAt(key, null)`.
      `setSavedAt` devuelve el número de filas afectadas (depende de T004)
- [X] T006 En `MAIN/data/source/local/BocDatabase.kt`: `version = 2`,
      `autoMigrations = [AutoMigration(from = 1, to = 2)]` y `abstract fun savedPublicationDao()`.
      **No se toca `bocDatabase()`**: sigue siendo un `.build()` limpio, sin `addMigrations` —las
      automáticas no lo necesitan— y **sin `fallbackToDestructiveMigration` en ningún caso**. Compilar
      y **versionar** el `app/schemas/…BocDatabase/2.json` generado (research.md D-002) (FR-023)
      (depende de T005)
- [X] T007 Escribir `TEST/data/source/local/BocDatabaseMigrationTest.kt`, la primera prueba de
      migración del proyecto (research.md D-003): crear un fichero de base de datos **en la versión
      1** ejecutando las sentencias `CREATE` que exporta `schemas/…/1.json` —transcritas literalmente
      y con un comentario que diga de dónde vienen—, insertar una publicación, cerrar, y abrir después
      con `Room.databaseBuilder(...).build()`, que es el camino de producción; comprobar que la
      publicación sigue ahí y que `saved_at` existe y vale `NULL` (FR-023, SC-006) (depende de T006)
- [X] T008 Declarar `MAIN/domain/repository/SavedPublicationRepository.kt` según
      `contracts/internal-contracts.md` §1.1. En el KDoc: **nada lanza**, `CancellationException` se
      repropaga, los flujos **no terminan con error** —un fallo de lectura emite vacío—, vacío no es
      fallo, y **el orden lo pone el almacén**, no la pantalla
- [X] T009 Escribir `TEST/data/repository/SavedPublicationRepositoryImplTest.kt` antes que la
      implementación (Robolectric + base de datos en memoria): las entidades se mapean a dominio y
      llegan ordenadas; el conjunto de claves; `setSaved` en los dos sentidos con el **tiempo
      inyectado**, que es lo que hace comprobable el orden sin esperar milisegundos reales; un fallo
      de lectura emite vacío sin terminar el flujo; y que el evento de analítica **no** lleva la clave,
      el título ni la sección (FR-025)
- [X] T010 Crear `MAIN/data/repository/SavedPublicationRepositoryImpl.kt` hasta hacer pasar T009:
      `flowOn(dispatchers.io)`, el mismo `.catch` que el repositorio de publicaciones, escritura del
      instante con `TimeProvider`, `AppResult.Failure(DomainError.Unknown)` al fallar —`DomainError`
      **no crece** (D-013)— y el evento `publication_save` con un único parámetro booleano
      (depende de T009)
- [X] T011 [P] Escribir las pruebas de los tres casos de uso en `TEST/domain/usecase/`:
      `ObserveSavedPublicationsUseCaseTest.kt`, `ObserveSavedKeysUseCaseTest.kt` y
      `SetPublicationSavedUseCaseTest.kt`. **No son opcionales**: la regla de Konsist falla la build si
      una clase de dominio no tiene su fichero de prueba (SC-010)
- [X] T012 [P] Crear los tres casos de uso en `MAIN/domain/usecase/`, con un único
      `operator fun invoke()` cada uno y sin lógica propia: son la frontera que impide que `ui` vea
      `data` (depende de T008)
- [X] T013 Registrar en Koin: `SavedPublicationDao` y `SavedPublicationRepository` en
      `MAIN/core/di/DataModule.kt`, los tres casos de uso en `MAIN/core/di/DomainModule.kt`; y ampliar
      `TEST/di/KoinModulesTest.kt` para que la verificación del grafo los cubra (depende de T010, T012)
- [X] T014 Ampliar `ATEST/fake/TestGraph.kt`: `testGraphOverrides()` debe reconstruir también
      `savedPublicationDao` y `SavedPublicationRepository`. Sin esto, las pruebas de interfaz no
      resuelven el grafo y además se filtraría estado de una prueba a la siguiente (depende de T013)

**Checkpoint**: `./gradlew :app:testDebugUnitTest` en verde, con la marca almacenada, la migración
probada y el grafo verificado.

---

## Phase 3: User Story 1 - Guardar un anuncio y volver a encontrarlo (Priority: P1) 🎯 MVP

**Goal**: marcar una publicación en el boletín y encontrarla en Guardados, con la misma tarjeta y con
el mismo comportamiento al pulsarla.

**Independent Test**: marcar una publicación en Inicio, entrar en Guardados y comprobar que aparece
esa y solo esa, y que al pulsarla se llega a su detalle.

### Tests for User Story 1 ⚠️

> Se escriben **antes** que la implementación y deben fallar antes de hacerlas pasar.

- [X] T015 [P] [US1] Mudar `ATEST/ui/home/PublicationCardTest.kt` a
      `ATEST/core/ui/component/PublicationCardTest.kt` y ampliarla: marcador **contorneado** cuando la
      publicación no está guardada y **relleno** cuando sí; la descripción accesible cambia entre
      «Guardar» y «Quitar de guardados»; y que guardar emite su evento **sin** abrir la publicación
      —ese caso ya existe y hay que conservarlo— (FR-003, FR-004, FR-007)
- [X] T016 [P] [US1] Escribir `TEST/ui/saved/SavedViewModelTest.kt` con `runTest` y Turbine: sin nada
      guardado el contenido es `Empty`; con contenido es `Publications` **en el orden que da el caso de
      uso, sin reordenar**; `onToggleSaved` delega en el caso de uso con el valor contrario al actual;
      compartir pasa por `Preparing` y llega a `Ready`; y registra la vista de pantalla `saved`
- [X] T017 [P] [US1] Escribir `ATEST/ui/saved/SavedContentTest.kt` montando el componible sin estado
      con `createComposeRule()` —no `createAndroidComposeRule`, para no atravesar la portada—: la lista
      dibuja una tarjeta por publicación, pulsar una emite `onOpenPublication`, y compartir y desmarcar
      emiten los suyos (FR-012, FR-013, FR-014, FR-015)

### Implementation for User Story 1

- [X] T018 [US1] Mudar `MAIN/ui/home/component/PublicationCard.kt` a
      `MAIN/core/ui/component/PublicationCard.kt` y añadirle `isSaved: Boolean`, que elige el vector
      —`ic_bookmark` o `ic_bookmark_filled`— y el texto accesible. `onSave` **sigue sin parámetros**:
      quien la coloca cierra sobre la publicación, igual que ya hace con `onShare`. Los valores de las
      tres etiquetas de prueba **no cambian**; sí cambian los `import` de quien la usa.
      `PublicationCardSkeleton` **no** se muda (research.md D-008) (depende de T001, T015)
- [X] T019 [P] [US1] Crear `MAIN/ui/saved/SavedUiState.kt` con `SavedUiState` y `SavedContentState`
      (`Publications`, `Empty`) según data-model.md §5.1. **Sin `Skeleton` ni `Error`**: la lectura es
      local e inmediata y un fallo ya emite vacío por contrato. `share` va **fuera** del sellado
- [X] T020 [US1] Crear `MAIN/ui/saved/SavedViewModel.kt` hasta hacer pasar T016: `MutableStateFlow`
      privado, `StateFlow` de solo lectura, `combine` de los flujos que necesite, guardas de `Job` para
      que un segundo toque no lance una segunda escritura ni un segundo compartir (depende de T012,
      T016, T019)
- [X] T021 [US1] Reescribir `MAIN/ui/saved/SavedScreen.kt`: `SavedScreen` con estado
      (`koinViewModel()`) y `SavedContent` sin estado. Barra superior `Primary` con el título
      «Guardados» y **sin acciones** —ordenar y selección múltiple quedan fuera—; lista con los mismos
      márgenes, separación y holgura inferior que Inicio, `key = { it.externalKey }` y la etiqueta
      `TAG_SAVED_LIST`. Retirar aquí `coming_soon_saved` de `RES/values/strings.xml`, que es donde deja
      de usarse (depende de T018, T020)
- [X] T022 [US1] Extraer a `MAIN/ui/share/ShareEffect.kt` el efecto que avisa, entrega al sistema y
      confirma el consumo, y usarlo desde Inicio y desde Guardados. **El detalle no se toca**: su
      comportamiento no es el mismo, dibuja su propia línea de progreso (research.md D-011) (FR-014)
- [X] T023 [US1] Ampliar `MAIN/ui/home/HomeUiState.kt` con `savedKeys: Set<String> = emptySet()` y
      `MAIN/ui/home/HomeViewModel.kt` con `ObserveSavedKeysUseCase`, `SetPublicationSavedUseCase` y
      `onToggleSaved(publication)`; el `combine` pasa de cuatro flujos a cinco. `HomeScreen`
      **pierde** el parámetro `onSave`: guardar deja de ser algo que el armazón resuelve con un aviso y
      pasa a ser un evento del propio modelo, como compartir (FR-001, FR-005) (depende de T012)
- [X] T024 [US1] Ampliar `TEST/ui/home/HomeViewModelTest.kt`: las claves guardadas llegan al estado, y
      `onToggleSaved` delega en el caso de uso con el valor contrario (depende de T023)
- [X] T025 [US1] En `MAIN/ui/main/MainShell.kt`: pasar a `composable<Route.Saved>` la **misma** lambda
      de apertura que usa Inicio y un `onExplore` que lleve a `Route.Home()`; retirar
      `onSave = ::showComingSoon` de `HomeScreen`. `showComingSoon` se queda solo para Buscar
      (FR-010, FR-013) (depende de T021, T023)
- [X] T026 [US1] Actualizar las dos pruebas instrumentadas que afirmaban lo contrario:
      `ATEST/ui/home/HomeContentTest.kt` por el parámetro nuevo —más un caso: una tarjeta cuya clave
      está en `savedKeys` se dibuja marcada— y `ATEST/ui/BottomBarNavigationTest.kt`, donde Guardados
      **ya no dice «Próximamente»**. Es la prueba que demuestra que el marcador de posición se ha
      retirado de verdad (FR-010, SC-008) (depende de T025)

**Checkpoint**: guardar desde el boletín, verlo en Guardados y abrirlo desde ahí funciona de extremo a
extremo. Pasos 1 a 4 y 7 del quickstart.

---

## Phase 4: User Story 2 - Quitar de guardados, y que el estado sea el mismo en todas partes (Priority: P1)

**Goal**: el mismo marcador enciende y apaga desde los tres sitios, y lo que muestra la tarjeta y lo
que muestra el detalle coinciden siempre.

**Independent Test**: marcar desde el detalle y comprobar que la tarjeta del boletín ya aparece
marcada al volver; desmarcar desde la lista y comprobar que el elemento desaparece.

### Tests for User Story 2 ⚠️

- [X] T027 [P] [US2] Ampliar `TEST/ui/detail/PublicationDetailViewModelTest.kt`: `isSaved` refleja si
      la clave está en el conjunto de guardados; `onToggleSaved` delega con el valor contrario; y un
      fallo de escritura deja el estado **sin** presentar la publicación como guardada (FR-005, FR-009)
- [X] T028 [P] [US2] Ampliar `ATEST/ui/detail/PublicationDetailContentTest.kt`: el icono de la barra
      superior es relleno cuando `isSaved` y contorneado cuando no, y **no se dibuja** cuando el estado
      dice que la publicación ya no está almacenada (FR-003, FR-008)

### Implementation for User Story 2

- [X] T029 [P] [US2] Añadir `isSaved: Boolean = false` a
      `MAIN/ui/detail/PublicationDetailUiState.kt`
- [X] T030 [US2] Ampliar `MAIN/ui/detail/PublicationDetailViewModel.kt` con `ObserveSavedKeysUseCase`
      y `SetPublicationSavedUseCase` y un `onToggleSaved()`; el `combine` pasa de cuatro flujos a
      cinco. `isSaved` se **deriva** del conjunto: no hay un flujo propio para un booleano
      (research.md D-004) (depende de T012, T027, T029)
- [X] T031 [US2] En `MAIN/ui/detail/PublicationDetailScreen.kt`: el icono de guardar elige vector y
      descripción según `state.isSaved`, y **no se dibuja** si no hay publicación que guardar
      (depende de T029)
- [X] T032 [US2] En `MAIN/ui/detail/PublicationDetailRoute.kt`: cambiar `onSave = ::showComingSoon`
      por `onSave = viewModel::onToggleSaved`. Si con eso `showComingSoon` se queda sin uso en el
      fichero, retirarlo junto con la cadena que leía (depende de T030, T031)
- [X] T033 [US2] Cumplir FR-009 en los tres sitios con una sola pieza: añadir a los tres estados de
      pantalla una señal de fallo de escritura y su función de consumo, y crear
      `MAIN/core/ui/component/SaveFailureToast.kt`, que las tres pantallas usan. La segunda mitad del
      requisito sale gratis: `isSaved` se deriva de lo almacenado, así que una escritura fallida deja
      el icono como estaba (depende de T020, T023, T030)

**Checkpoint**: pasos 5, 6 y 8 del quickstart. El estado coincide en tarjeta y detalle (SC-003).

---

## Phase 5: User Story 3 - Que lo guardado no se pierda (Priority: P1)

**Goal**: ni una sincronización, ni la muerte del proceso, ni una actualización de la aplicación
pierden una marca ni el boletín almacenado.

**Independent Test**: guardar, forzar una sincronización, matar el proceso y volver a entrar; y
actualizar la aplicación sobre una instalación que ya tenía boletín.

> Las dos pruebas que sostienen esta historia —la regresión del DAO (T003) y la de migración (T007)—
> están en la fase 2 porque hacen falta para poder construir cualquier cosa. Aquí se cierra con la
> prueba que atraviesa todas las capas y con el único paso que ninguna prueba automática cubre.

- [X] T034 [P] [US3] Escribir `TEST/integration/SavedFlowIntegrationTest.kt` con el grafo real, base de
      datos en memoria y la fuente remota falsa —el patrón de `BulletinFlowIntegrationTest`, incluido
      el `setQueryExecutor`/`setTransactionExecutor` con el despachador de prueba, sin el cual
      `advanceUntilIdle()` vuelve antes de que la base de datos haya terminado—: guardar aparece en la
      lista, una sincronización posterior **no** la pierde, y desmarcar la retira de la lista dejando
      la publicación almacenada (US1, US2, US3, SC-004, SC-005)
- [X] T035 [US3] Recorrer a mano el **paso 10 del quickstart**: instalar el APK de `main`, dejar que
      sincronice, y instalar el de la rama **encima, sin desinstalar**. Comprobar que arranca y que el
      boletín almacenado sigue ahí. Un fallo de migración solo se ve en un dispositivo que ya tenía
      `boc.db`: en una instalación limpia es invisible (FR-023, SC-006) (depende de T007)

**Checkpoint**: paso 9 y paso 10 del quickstart.

---

## Phase 6: User Story 4 - Una lista vacía que explica qué hacer (Priority: P3)

**Goal**: quien entra en Guardados sin nada guardado entiende qué falta y tiene por dónde salir.

**Independent Test**: entrar en Guardados sin nada guardado y comprobar que se explica y que el botón
lleva a Inicio.

### Tests for User Story 4 ⚠️

- [X] T036 [P] [US4] Ampliar `ATEST/ui/saved/SavedContentTest.kt` con el estado vacío: se ven el icono,
      el título «Aún no has guardado publicaciones» y el texto de apoyo, y «Explorar el BOC» emite
      `onExplore` (FR-017)

### Implementation for User Story 4

> **Adelantadas a la fase 3.** El `when` sobre `SavedContentState` es exhaustivo, así que la pantalla
> no compila sin su rama vacía: T037 y T038 se ejecutaron inmediatamente después de T021, que es
> justo la dependencia que ya declaraba esta fase. La prueba (T036) sigue aquí.

- [X] T037 [US4] Crear `MAIN/core/ui/component/IllustratedMessage.kt` con
      `(iconRes, title, description, action)` y los tokens del apartado 26.3, y hacer que
      `ComingSoonMessage` **delegue** en él **sin cambiar su firma ni su etiqueta de prueba**: hay dos
      pruebas instrumentadas que dependen de ella (research.md D-010)
- [X] T038 [US4] Dibujar el estado vacío en `SavedContent` con `IllustratedMessage`, con las etiquetas
      `TAG_SAVED_EMPTY` y `TAG_SAVED_EMPTY_ACTION`, y comprobar que `onExplore` llega desde
      `MainShell` hasta el botón (depende de T036, T037)

**Checkpoint**: paso 11 del quickstart.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T039 [P] Enmendar `docs/diseno/especificaciones-diseno.md`: una cita en bloque en el apartado 22
      —y una nota en el 12.2— con la convención que ya usaron las features 003 y 004, diciendo qué
      queda aplazado y por qué: los chips `Todos`/`Sin conexión`/`Con resumen`, la acción de ordenar,
      la selección múltiple, la tarjeta compacta, el indicador de descarga y la fecha de guardado como
      metadato visible (SC-012)
- [X] T040 [P] Actualizar `CLAUDE.md`: el árbol de paquetes —`saved/` ya no es marcador de posición y
      `PublicationCard` vive en `core/ui/component`—; en la capa de datos, la versión 2 con migración
      automática, la columna `saved_at` y **por qué la sincronización no la puede pisar**; el matiz de
      que desmarcar es un `UPDATE` a `NULL` y no un borrado, para que la regla de «nunca se borra una
      publicación» siga leyéndose bien; y la frase de que Guardados **todavía no** conserva el
      documento para leer sin conexión (FR-024, SC-012)
- [X] T041 Repasar que `gradle/libs.versions.toml` **no ha cambiado** (research.md D-015), que no hay
      ningún color, tamaño ni espaciado literal nuevo fuera del tema, y que no ha aparecido ninguna
      sentencia de borrado en ningún DAO (FR-021)
- [X] T042 Comprobar SC-007 con **doscientas** publicaciones guardadas: marcarlas por el camino más
      corto que exista —`adb shell` sobre la base de datos, o una tanda desde una prueba
      instrumentada— y comprobar que Guardados se abre y se desplaza sin saltos. Es el único criterio
      de éxito que no cae de ninguna otra tarea (SC-007)
- [X] T043 Las cuatro puertas de calidad, en este orden: `assembleDebug`, `testDebugUnitTest`,
      `connectedDebugAndroidTest` —con navegación de tres botones:
      `adb shell settings put secure navigation_mode 0`— y `lintDebug` (SC-011)
- [X] T044 Recorrer el quickstart completo en un dispositivo, incluidos el paso 13 de accesibilidad
      con TalkBack y el tamaño de letra al 200 % (FR-004, SC-008, SC-009)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias. T001 y T002 son ficheros distintos y van en paralelo.
- **Foundational (fase 2)**: depende de la fase 1 solo para T018 (el icono). **Bloquea todas las
  historias.**
- **US1 (fase 3)**: depende de la fase 2 completa. Es el MVP.
- **US2 (fase 4)**: depende de la fase 2 y, en la práctica, de T018 y T023 de US1 —el mismo marcador y
  el mismo conjunto de claves—. No se puede desmarcar lo que no se puede marcar.
- **US3 (fase 5)**: sus dos pruebas críticas ya están en la fase 2. T034 depende de US1 y US2 para
  poder atravesar las tres capas; T035 solo de T007.
- **US4 (fase 6)**: depende de T021 (la pantalla existe). Independiente de US2 y US3.
- **Polish (fase 7)**: depende de todo lo anterior.

### Dentro de cada historia

- Las pruebas se escriben antes y deben **fallar** antes de hacerlas pasar.
- Almacén antes que dominio, dominio antes que presentación.
- La tarjeta (T018) antes que las dos pantallas que la usan.

### Un orden que no se puede invertir

T004 → T005 → T006 → T007. La columna antes que el DAO, el DAO antes que la base de datos, y la base
de datos antes que la prueba de migración, que necesita el `2.json` generado.

---

## Parallel Example: User Story 1

```bash
# Las tres tandas de pruebas de US1 son ficheros distintos y no dependen entre sí:
T015  ATEST/core/ui/component/PublicationCardTest.kt
T016  TEST/ui/saved/SavedViewModelTest.kt
T017  ATEST/ui/saved/SavedContentTest.kt

# En la fase 2, las pruebas de los tres casos de uso y sus clases, también:
T011  TEST/domain/usecase/  (los tres ficheros)
T012  MAIN/domain/usecase/  (los tres ficheros)
```

---

## Implementation Strategy

### MVP primero (solo US1)

1. Fase 1: Setup.
2. Fase 2: Foundational — **crítica**, bloquea todo.
3. Fase 3: US1.
4. **PARAR Y VALIDAR**: pasos 1 a 4 y 7 del quickstart.

Con eso ya se guarda y se consulta lo guardado, que es la feature.

### Entrega incremental

1. Setup + Foundational → la marca existe y está probada, incluida la migración.
2. US1 → guardar y consultar. Demostrable.
3. US2 → el interruptor completo y el estado coherente en las tres pantallas.
4. US3 → la prueba de extremo a extremo y la comprobación de actualización a mano.
5. US4 → el estado vacío.
6. Polish → documento de diseño, guía operativa y las cuatro puertas.

### Riesgos anotados

- **La migración es el riesgo con peores consecuencias.** No se ve en una instalación limpia, así que
  el paso 10 del quickstart (T035) no es opcional. Si la huella del esquema no cuadra, la aplicación
  se cae al abrir en el móvil de quien ya la tenía.
- **La mudanza de `PublicationCard` toca ficheros de prueba existentes.** Los valores de las etiquetas
  no cambian, así que lo único que se rompe son los `import`; si algo más se rompe, es señal de que la
  tarjeta llevaba dentro algo que no era de la tarjeta.
- **T026 no se puede aplazar.** En cuanto Guardados deja de ser un marcador de posición,
  `BottomBarNavigationTest` afirma algo falso y la puerta 3 se pone roja. Va dentro de US1, no en el
  pulido.
- **`ComingSoonMessage` la usa Buscar.** Al generalizarla (T037) hay que dejar su firma y su etiqueta
  intactas, o se rompen pruebas que no tienen nada que ver con esta feature.
- **El `combine` de dos modelos pasa a cinco flujos.** Hay sobrecarga para cinco; si algún día hace
  falta un sexto, habrá que pasar a la forma de lista y eso cambia el tipo del bloque.

---

## Notes

- `[P]` = ficheros distintos, sin dependencias entre sí.
- Cada tarea nombra su fichero. Ninguna tarea se da por terminada sin su prueba en verde: el principio
  V no es negociable y `@Ignore` está prohibido.
- Commits en español, imperativo, con prefijo de tipo. Uno por tarea o por grupo lógico.
- Si una tarea obliga a desviarse de `plan.md`, se anota en `plan.md` en su sección de complejidad
  **antes** de seguir.

---

## Registro de ejecución (31 de agosto de 2026)

Las cuatro puertas, en orden:

| Puerta | Resultado |
|---|---|
| `assembleDebug` | ✅ |
| `testDebugUnitTest` | ✅ **405 pruebas en 56 clases, 0 fallos** |
| `connectedDebugAndroidTest` | ✅ **85 pruebas, 0 fallos** (Pixel 10, API 37, `navigation_mode 0`) |
| `lintDebug` | ✅ 0 errores. 7 avisos, todos preexistentes: dos vectores del icono de lanzador que lint no ve usados y cinco sugerencias de subir versiones |

Comprobado a mano en el emulador:

- **T035, la migración sobre una instalación real.** Se construyó el APK de `main` en un árbol de
  trabajo aparte, se instaló, se dejó sincronizar —**1709 publicaciones**, base de datos en la
  versión 1 con huella `477bff42…`— y se instaló el APK de la rama **encima, sin desinstalar**.
  Resultado: versión 2, huella `1f93c864…`, **las 1709 publicaciones conservadas**, la columna
  `saved_at` y su índice presentes, y ninguna excepción en el registro.
- **T042, SC-007.** Con **200** marcas escritas directamente en el almacén: la pantalla abre sin
  espera perceptible y doce desplazamientos completos dan 213 fotogramas con un 5,6 % irregulares,
  percentil 99 en 23 ms y **cero vsync perdidos**.
- **Pasos 1 a 6, 8, 9, 11 y 13 del quickstart**: guardar desde el boletín deja el marcador relleno y
  el de la tarjeta siguiente contorneado; Guardados lista lo guardado con la tarjeta estándar;
  pulsar abre el detalle con el marcador relleno; desmarcar desde la lista retira la tarjeta en el
  acto y desde el detalle apaga el icono; las cuentas del almacén siguieron 200 → 199 → 198 y las
  1709 publicaciones no se movieron; guardar y desmarcar funcionan **sin conexión**; la marca hecha
  sin red sobrevivió a matar el proceso y a la sincronización siguiente; el estado vacío muestra
  icono, título, texto y «Explorar el BOC», que lleva a Inicio; y al 200 % de tamaño de letra la
  tarjeta sigue legible con sus dos acciones pulsables.
- **Paso 7, compartir, no se condujo a mano**: abre el selector del sistema y su mecánica es la de la
  feature 004, sin cambios. Lo que esta feature añade —que la tarjeta de Guardados emite el evento—
  lo fija `SavedContentTest`.

Una nota de honestidad sobre el orden: la tanda instrumentada **ya estaba en marcha** cuando se
retiró la cadena `coming_soon_saved` de `strings.xml`, que era lo último que quedaba de T021, así que
el APK que se probó todavía la llevaba. Es la eliminación de un recurso sin ninguna referencia
—comprobado por búsqueda antes de borrarlo, y es justo lo que lint señalaba—, de modo que no hay
camino por el que pueda alterar el comportamiento instrumentado. Se deja dicho en lugar de
presentarlo como si la tanda hubiera corrido después.
