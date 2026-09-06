---

description: "Task list for feature 013 — Inicio y panel lateral"
---

# Tasks: Inicio y panel lateral — que cada control diga lo que hace

**Input**: Design documents from `/specs/013-inicio-secciones-y-panel/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/internal-contracts.md](./contracts/internal-contracts.md),
[quickstart.md](./quickstart.md)

**Tests**: **obligatorios**. No es una opción de esta feature: el principio V de la constitución es no
negociable y SC-008 lo recoge. Ninguna tarea se da por terminada sin su prueba en verde, y está
prohibido `@Ignore`, comentar o borrar una prueba para que pase la build.

**Organization**: por historia de usuario. Las cinco son independientes entre sí: cada una se
implementa, se prueba y se enseña sola.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ir en paralelo (fichero distinto, sin dependencias pendientes)
- **[Story]**: US1 … US5, la historia de `spec.md` a la que sirve
- Toda tarea lleva la ruta exacta del fichero

## Path Conventions

Aplicación Android de módulo único. Producto en
`app/src/main/java/com/jrblanco/boccantabria/`, recursos en `app/src/main/res/`, pruebas unitarias en
`app/src/test/java/com/jrblanco/boccantabria/` e instrumentadas en
`app/src/androidTest/java/com/jrblanco/boccantabria/`.

---

## Phase 1: Setup

**Purpose**: partir de una línea base conocida, para que cualquier rojo posterior sea de esta feature.

- [X] T001 Confirmar que la rama activa es `013-inicio-secciones-y-panel` y que parte de `main` con la feature 012 integrada, con `git branch --show-current` y `git log --oneline -1`
- [X] T002 Dejar la línea base en verde antes de tocar nada: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y después `./gradlew :app:assembleDebug` y `./gradlew :app:testDebugUnitTest`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: ninguno.

**Esta fase está vacía a propósito y conviene decir por qué.** Esta feature no introduce ninguna
entidad, ningún caso de uso, ninguna dependencia y ningún cambio en el grafo de Koin
(`data-model.md` §1 y §5). No hay ningún prerrequisito que bloquee a las historias: las cinco pueden
arrancar en cuanto la fase 1 esté hecha. Inventar aquí una tarea «preparatoria» sería crear una
dependencia artificial entre historias que la especificación describe como independientes.

**Checkpoint**: con T001 y T002 hechas, las cinco historias pueden empezar, en paralelo si hay manos.

---

## Phase 3: User Story 1 — El primer filtro se llama por su nombre (Priority: P1) 🎯 MVP

**Goal**: que el primer chip de la fila de filtros deje de prometer la totalidad del archivo y nombre
lo que de verdad muestra: el boletín del día.

**Independent Test**: abrir Inicio, leer el primer chip, tocar una sección con mucho archivo y volver
al primer chip; el contenido y el recuento son idénticos a los de antes de la feature.

**Requisitos que cierra**: FR-001, FR-002, FR-003.

### Implementación

- [X] T003 [US1] Renombrar el recurso `chip_all` a `chip_todays_bulletin` con valor `Boletín de hoy` en `app/src/main/res/values/strings.xml`, dentro del bloque «Filtros rápidos»
- [X] T004 [US1] Consumir el recurso nuevo en el primer chip de `app/src/main/java/com/jrblanco/boccantabria/ui/home/component/SectionFilterChips.kt`, **conservando la constante `TAG_CHIP_ALL` con su valor `home_chip_all`** (contrato §1: renombrarla convertiría un cambio de copia en un cambio de tres clases de prueba)
- [X] T005 [US1] Verificar que no queda ninguna referencia a `R.string.chip_all` con `grep -rn "chip_all" app/src/` — solo debe aparecer la etiqueta de prueba

### Pruebas

- [X] T006 [US1] Crear `app/src/androidTest/java/com/jrblanco/boccantabria/ui/home/SectionFilterChipsTest.kt` montando `SectionFilterChips` con `createComposeRule()` —no con `createAndroidComposeRule<MainActivity>()`, que obliga a cruzar la portada— y afirmar que el primer chip muestra «Boletín de hoy» y que tocarlo emite `null`
- [X] T007 [US1] Ejecutar `./gradlew :app:assembleDebug` y la clase nueva con `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jrblanco.boccantabria.ui.home.SectionFilterChipsTest`

**Checkpoint**: US1 completa y demostrable sola. Ninguna otra historia depende de ella.

---

## Phase 4: User Story 2 — La fecha de la cabecera se explica sola (Priority: P1)

**Goal**: que quien lea la fecha de la cabecera azul sepa de qué fecha se trata, con un rótulo distinto
según se esté viendo el boletín del día o una sección.

**Independent Test**: abrir Inicio y leer la cabecera; elegir una sección y volver a leerla; el rótulo
cambia y en los dos casos dice qué se está mirando.

**Requisitos que cierra**: FR-004, FR-005, FR-006, FR-007.

### Implementación

- [X] T008 [P] [US2] Añadir en `app/src/main/res/values/strings.xml`, junto a la cabecera editorial, `home_header_date_bulletin` = `Edición del %1$s` y `home_header_date_section` = `Última publicación: %1$s`
- [X] T009 [US2] En `app/src/main/java/com/jrblanco/boccantabria/ui/home/component/BulletinHeader.kt`, elegir entre las dos cadenas con `header.isTodaysBulletin` e interpolar la fecha con el `SPANISH_LONG_DATE` que ya vive en el fichero; **no** crear un `DateTimeFormatter` nuevo (research D-509)
- [X] T010 [US2] Mantener el rótulo **dentro** del bloque `header.date?.let { … }` que ya existe, para que sin fecha no se pinte un rótulo huérfano (FR-006, research D-508)
- [X] T011 [US2] Añadir la constante `TAG_HEADER_DATE = "home_header_date"` y aplicarla al `Text` de la fecha en el mismo fichero

### Pruebas

- [X] T012 [US2] Crear `app/src/androidTest/java/com/jrblanco/boccantabria/ui/home/BulletinHeaderTest.kt` con `createComposeRule()` y tres casos: rótulo del boletín del día, rótulo de una sección, y que con `date = null` no se dibuja `TAG_HEADER_DATE`
- [X] T013 [US2] Ejecutar la clase nueva con `-Pandroid.testInstrumentationRunnerArguments.class=com.jrblanco.boccantabria.ui.home.BulletinHeaderTest`

**Checkpoint**: US2 completa y demostrable sola.

---

## Phase 5: User Story 3 — Las subsecciones, sin abrir el panel lateral (Priority: P1)

**Goal**: que tocar el chip de una sección con subsecciones muestre la sección completa **y** despliegue
debajo una segunda fila con «Toda la sección» y sus subsecciones.

**Independent Test**: recorrer las cuatro secciones con subsecciones y las cinco sin ellas,
comprobando que la segunda fila aparece y desaparece y que cada chip lleva a lo que dice.

**Requisitos que cierra**: FR-008 a FR-018.

**Es la única historia con lógica.** Las otras cuatro son copia y composición.

### Implementación

- [X] T014 [US3] Añadir a `HomeUiState` los campos `subsections: List<SectionChip> = emptyList()` e `isWholeSectionSelected: Boolean = false` en `app/src/main/java/com/jrblanco/boccantabria/ui/home/HomeUiState.kt`, documentando en el KDoc que se **derivan** de la selección y no son estado recordado (research D-501)
- [X] T015 [US3] Añadir `buildSubsectionChips()` a `app/src/main/java/com/jrblanco/boccantabria/ui/home/HomeViewModel.kt`: si `selection is HomeSelection.Section`, filtrar `getSections()` por `parentCode == selection.sectionCode` y marcar `isSelected` comparando con `selection.subsectionCode`; en otro caso, lista vacía
- [X] T016 [US3] Calcular `isWholeSectionSelected` en el mismo modelo de pantalla: `selection is HomeSelection.Section && selection.subsectionCode == null`
- [X] T017 [US3] Publicar los dos valores en `HomeUiState`, tanto en el bloque del `combine` como en el `initialValue`, **sin añadir un sexto flujo**: se calculan una vez como `chips` y no dependen del almacén (data-model §2; con seis flujos `combine` cae en la sobrecarga de `vararg`, trampa ya documentada en `CLAUDE.md`)
- [X] T018 [P] [US3] Añadir `chip_whole_section` = `Toda la sección` en `app/src/main/res/values/strings.xml`
- [X] T019 [US3] Ampliar `SectionFilterChips` en `app/src/main/java/com/jrblanco/boccantabria/ui/home/component/SectionFilterChips.kt` con los tres parámetros del contrato §1 —`subsections`, `sectionCode`, `isWholeSectionSelected`, todos con valor por defecto— y un segundo `LazyRow` **debajo** del actual, visible solo si `subsections` no está vacía
- [X] T020 [US3] Añadir en ese fichero las constantes `TAG_SUBCHIPS = "home_subchips"` y `TAG_CHIP_WHOLE_SECTION = "home_chip_whole_section"`, y reutilizar `chipTag(code)` para los chips de subsección
- [X] T021 [US3] Dar a la segunda fila el estilo secundario de research D-505 —fondo `BocTheme.colors.surfaceSoft` en reposo y tipografía un punto menor—, **sin divisor y sin sangría**, y con los espaciados de `BocTheme.spacing`; ni un color, tamaño o espaciado literal (FR-033)
- [X] T022 [US3] Reenviar los tres valores desde `HomeContent` en `app/src/main/java/com/jrblanco/boccantabria/ui/home/HomeScreen.kt`, sacando `sectionCode` de `(state.selection as? HomeSelection.Section)?.sectionCode` (contrato §2). `MainShell.kt` **no se toca** en esta historia (research D-506)

### Pruebas

- [X] T023 [US3] Ampliar `app/src/test/java/com/jrblanco/boccantabria/ui/home/HomeViewModelTest.kt` con los cuatro casos de `data-model.md` §2: boletín del día → sin subsecciones; sección 1 → sin subsecciones pero `isWholeSectionSelected`; sección 2 → las tres hijas y `isWholeSectionSelected`; sección 2 con subsección 2.2 → la 2.2 marcada e `isWholeSectionSelected = false`
- [X] T024 [US3] Añadir a esa misma clase la regresión de FR-012: con la subsección 2.2 elegida, el chip de la sección 2 de la primera fila sigue marcado
- [X] T025 [US3] Ampliar `app/src/androidTest/java/com/jrblanco/boccantabria/ui/home/SectionFilterChipsTest.kt` (creada en T006) con: la segunda fila aparece con subsecciones y no aparece sin ellas; `TAG_CHIPS` precede a `TAG_SUBCHIPS`; «Toda la sección» emite el código de la sección padre; y un chip de subsección emite su propio código
- [X] T026 [US3] Ampliar `app/src/androidTest/java/com/jrblanco/boccantabria/ui/HomeNavigationTest.kt` con el recorrido completo: tocar el chip de la sección 2, comprobar que aparece la segunda fila, tocar una subsección y comprobar que la cabecera la nombra. Afirmar que la pantalla está montada **antes** de interactuar tras cada navegación
- [X] T027 [US3] Ejecutar `./gradlew :app:testDebugUnitTest --tests "*HomeViewModelTest*"` y las dos clases instrumentadas con `-Pandroid.testInstrumentationRunnerArguments.class=…` (recordatorio: `--tests` **no existe** en `connectedDebugAndroidTest`)

**Checkpoint**: US3 completa. Es la historia que más valor añade y la única que crea capacidad nueva.

---

## Phase 6: User Story 4 — El panel lateral se presenta y se recoge (Priority: P2)

**Goal**: que el panel lateral abra con el escudo, el nombre de la aplicación y una flecha que lo
recoge, y que pierda el campo de filtro que hoy confunde.

**Independent Test**: abrir el panel, comprobar la cabecera y la ausencia del campo, tocar la flecha y
ver que se recoge sin cambiar de pantalla.

**Requisitos que cierra**: FR-019 a FR-027, y FR-024 en particular, que es el que declara superado el
filtro de la feature 003.

### Implementación — retirar el filtro

- [X] T028 [US4] Retirar `query` de `SectionsUiState` en `app/src/main/java/com/jrblanco/boccantabria/ui/sections/SectionsUiState.kt` y actualizar su KDoc: `expanded` deja de ser «el conjunto efectivo» y pasa a ser exactamente lo que la persona ha desplegado. **`selection` se queda**: no tiene relación con el filtro (research D-512)
- [X] T029 [US4] En `app/src/main/java/com/jrblanco/boccantabria/ui/sections/SectionsViewModel.kt`, retirar `onQueryChanged`, el flujo `query`, el predicado privado `BocSection.matches` y la auto-apertura por coincidencia; dejar `stateFor` componiendo las nueve filas con **todas** sus hijas
- [X] T030 [P] [US4] Retirar de `app/src/main/res/values/strings.xml` los recursos `sections_search_hint` y `sections_empty`

### Implementación — la cabecera

- [X] T031 [P] [US4] Añadir `sections_close` = `Recoger el panel` en `app/src/main/res/values/strings.xml`
- [X] T032 [US4] En `app/src/main/java/com/jrblanco/boccantabria/ui/sections/SectionsDrawerContent.kt`, retirar el `OutlinedTextField` y el bloque de estado vacío con su `return@Column`, y con ellos las constantes `TAG_SECTIONS_QUERY` y `TAG_SECTIONS_EMPTY`
- [X] T033 [US4] Añadir en ese fichero la cabecera del contrato §4: `Row` con `Image(R.drawable.ic_escudo_cantabria)`, `Text(R.string.app_bar_title)` con `weight(1f)` y un `IconButton` con `R.drawable.ic_arrow_back` al final de la fila que invoca `onClose`, seguida de un `HorizontalDivider`
- [X] T034 [US4] Fijar en esa `Image` **tanto `height` como `aspectRatio(79f / 137f)`**: con solo `height`, un `Image` toma el ancho intrínseco del vector —32 dp— y el escudo sale diminuto. Es la trampa que documenta `CLAUDE.md` y que `SplashScreen.Emblem()` ya sortea (research D-511)
- [X] T035 [US4] Añadir las constantes `TAG_SECTIONS_HEADER = "sections_header"` y `TAG_SECTIONS_CLOSE = "sections_close"`, y dar al `IconButton` el `contentDescription` de `R.string.sections_close` (FR-023)
- [X] T036 [US4] Cambiar la firma de `SectionsDrawerContent` al contrato §4: fuera `onQueryChanged`, dentro `onClose: () -> Unit`
- [X] T037 [US4] En `app/src/main/java/com/jrblanco/boccantabria/ui/main/MainShell.kt`, cablear `onClose = { scope.launch { drawerState.close() } }` y retirar el paso de `onQueryChanged`
- [X] T038 [US4] Reescribir en ese mismo fichero el comentario de la línea 86 sobre por qué se usa el árbol completo y no `sectionsState.rows`: su motivo —«están filtradas por lo que se teclea en el panel»— deja de existir, pero la conclusión sigue siendo correcta. Se actualiza la razón, no se borra la nota

### Pruebas

- [X] T039 [US4] Podar `app/src/test/java/com/jrblanco/boccantabria/ui/sections/SectionsViewModelTest.kt`: caen los casos de filtrado, auto-apertura, mayúsculas y acentos, filtro por número, filtro vacío y limpiar filtro. **Sobreviven** los de las nueve secciones, los expandibles `["2","4","7","8"]`, el toggle y `onSelectionChanged`. Dejar una nota en la clase diciendo que la funcionalidad se retiró en la feature 013, no que las pruebas estorbaban
- [X] T040 [US4] Reescribir `app/src/androidTest/java/com/jrblanco/boccantabria/ui/sections/SectionsDrawerTest.kt`: fuera los dos casos de `TAG_SECTIONS_QUERY` y el de `TAG_SECTIONS_EMPTY`; **nuevos**: la cabecera muestra el escudo y «BOC Cantabria», la flecha invoca `onClose`, y el panel ya no tiene ningún campo de texto. Se conservan los de las nueve secciones, expandir/contraer y seleccionar sección y subsección
- [X] T041 [US4] Comprobar que `app/src/androidTest/java/com/jrblanco/boccantabria/ui/HomeNavigationTest.kt` sigue en verde: usa `TAG_MENU` y `sectionToggleTag("2")`, que no cambian, pero la cabecera desplaza las filas
- [X] T042 [US4] Ejecutar `./gradlew :app:testDebugUnitTest --tests "*SectionsViewModelTest*"` y las dos clases instrumentadas afectadas

**Checkpoint**: US4 completa. El panel lateral queda con cabecera, sin filtro y con cierre explícito.

---

## Phase 7: User Story 5 — La lupa dice qué filtra (Priority: P3)

**Goal**: que quien toca la lupa entienda que acota lo que ya tiene delante, conservando intacto el
puente hacia el buscador global.

**Independent Test**: tocar la lupa, leer el texto de ayuda, escribir algo que no exista y comprobar
que el mensaje y la salida hacia Buscar siguen ahí.

**Requisitos que cierra**: FR-028 a FR-032.

### Implementación

- [X] T043 [US5] En `app/src/main/res/values/strings.xml`, cambiar `home_search_hint` de `Buscar en esta edición…` a `Filtrar lo que estás viendo…` (FR-028)
- [X] T044 [US5] En el mismo fichero, cambiar `app_bar_search` de `Buscar` a `Filtrar esta lista` (FR-029)
- [X] T045 [US5] En el mismo fichero, cambiar `home_no_results_title` de `Nada en esta edición` a `Nada en esta lista` (FR-030)
- [X] T046 [US5] Comprobar que **no se toca nada más**: `home_no_results_body` ya dice «en lo que estás viendo», y `home_search_globally` (`Buscar en todo el BOC`) es el puente y se conserva literal (FR-031)

### Pruebas

- [X] T047 [US5] Actualizar los dos literales de `app/src/androidTest/java/com/jrblanco/boccantabria/ui/home/HomeSearchTest.kt`: la línea 44 afirma `"Buscar en esta edición…"` y la 139 afirma `"Nada en esta edición"`. El resto de la clase —incluido el caso del puente hacia Buscar— no se toca
- [X] T048 [US5] Comprobar que `app/src/androidTest/java/com/jrblanco/boccantabria/ui/SearchHandoffTest.kt` sigue en verde sin modificarse: es la prueba de que el puente no se ha roto
- [X] T049 [US5] Ejecutar ambas clases con `-Pandroid.testInstrumentationRunnerArguments.class=…`

**Checkpoint**: las cinco historias completas.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T050 [P] Actualizar `docs/diseno/` — es la fuente de verdad de la interfaz y aquí cambian la fila de filtros rápidos, la cabecera editorial y el panel lateral
- [X] T051 [P] Actualizar `CLAUDE.md`: el panel lateral ya no contiene «un campo de filtro, las nueve secciones y sus subsecciones», y conviene anotar que «Todo» pasó a llamarse «Boletín de hoy» y por qué, para que nadie vuelva a plantear la misma pregunta dentro de un año
- [X] T052 Ejecutar las comprobaciones de no-regresión de `quickstart.md` §4: `git diff --stat main` sobre `app/schemas/`, `data/`, `domain/`, `gradle/libs.versions.toml` y `app/build.gradle.kts` debe salir **vacío** en los cinco
- [X] T053 Puerta 1: `./gradlew :app:assembleDebug`
- [X] T054 Puerta 2: `./gradlew :app:testDebugUnitTest`
- [X] T055 Puerta 3: `adb shell settings put secure navigation_mode 0` y después `./gradlew :app:connectedDebugAndroidTest` con **un solo dispositivo** conectado o `ANDROID_SERIAL` fijado. Tarda dos o tres horas; lanzarla en segundo plano
- [X] T056 Puerta 4: `./gradlew :app:lintDebug`
- [X] T057 Recorrido manual completo de `quickstart.md` §3, los treinta y un pasos, en dispositivo o emulador con datos sincronizados. Es lo único que comprueba de verdad lo que esta feature promete, porque lo que se corrige es comprensión
- [X] T058 Comprobar en la pantalla más estrecha disponible que «Boletín de hoy» cabe en el chip; si chirría, cambiar la cadena a `Hoy`, que es la alternativa acordada en `spec.md` (Assumptions)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias.
- **Foundational (fase 2)**: vacía a propósito. No bloquea nada.
- **Historias (fases 3-7)**: todas dependen solo de la fase 1. Entre ellas, **ninguna dependencia**.
- **Polish (fase 8)**: depende de que estén hechas las historias que se quieran entregar.

### Dependencias entre historias

Ninguna. Las cinco tocan zonas distintas:

| Historia | Ficheros de producto | Solapamiento |
|---|---|---|
| US1 | `strings.xml`, `SectionFilterChips.kt` | con US3 en los dos |
| US2 | `strings.xml`, `BulletinHeader.kt` | con el resto solo en `strings.xml` |
| US3 | `strings.xml`, `SectionFilterChips.kt`, `HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt` | con US1 en los dos primeros |
| US4 | `strings.xml`, los tres de `ui/sections`, `MainShell.kt` | solo `strings.xml` |
| US5 | `strings.xml` | solo `strings.xml` |

**El único acoplamiento real es US1 → US3**: las dos tocan `SectionFilterChips.kt` y la clase de prueba
`SectionFilterChipsTest.kt`, que crea US1 (T006) y amplía US3 (T025). Si se hacen en paralelo, US3
espera a T006. Hacerlas en orden —que es el de las prioridades— evita el conflicto por completo.

`strings.xml` lo tocan las cinco, pero en bloques distintos del fichero y sin solapamiento de líneas.

### Dentro de cada historia

Implementación antes que prueba **salvo cuando se corrige un defecto**, que no es el caso aquí: esta
feature no arregla ningún fallo, corrige comprensión. Dentro de la implementación: estado → modelo de
pantalla → componible → pantalla.

### Parallel Opportunities

- T008 (US2), T018 (US3), T030 y T031 (US4) y T043-T045 (US5) son cadenas en bloques distintos de
  `strings.xml`: si las hace una sola persona, van seguidas; si van en paralelo, hay que resolver el
  fichero a mano.
- US2, US4 y US5 son **completamente independientes** entre sí y de US1/US3. Tres personas pueden
  llevarlas a la vez sin tocarse.
- Dentro de US4, T028-T030 (retirar) y T031 (la cadena nueva) son independientes de T033-T035 (la
  cabecera).

---

## Parallel Example: reparto entre tres

```text
Persona A: US1 → US3     (los chips; en este orden, por SectionFilterChips.kt)
Persona B: US2 → US5     (la cabecera y la lupa; solo copia y un if)
Persona C: US4           (el panel lateral entero)
```

---

## Implementation Strategy

### MVP (US1 sola)

1. Fase 1 (T001-T002).
2. Fase 3 (T003-T007).
3. **Parar y validar**: la pregunta que originó esta feature —«¿por qué 39?»— ya no se hace.
4. Con US2 encima, queda contestada del todo.

### Entrega incremental

1. Fase 1 → línea base.
2. US1 → el chip deja de mentir → demostrable.
3. US2 → la fecha se explica → demostrable. **Con estas dos, el malentendido del propietario está
   cerrado.**
4. US3 → las subsecciones al alcance → demostrable. Es lo único que añade capacidad.
5. US4 → el panel se presenta y se recoge → demostrable.
6. US5 → la lupa dice qué filtra → demostrable.
7. Fase 8 → documentación, las cuatro puertas y el recorrido manual.

---

## Notes

- **Ni una consulta, ni una migración, ni una dependencia.** Si en la implementación aparece la
  necesidad de tocar `data/`, `domain/`, `app/schemas/` o `gradle/libs.versions.toml`, hay que parar:
  o es un error de ejecución, o la especificación se quedó corta y hay que ampliarla antes de seguir.
- **Prohibido `@Ignore`.** Las pruebas que caen en T039 y T040 caen porque desaparece la funcionalidad
  que describían, y la especificación lo declara superado con requisito propio (FR-024). Ninguna otra
  se toca.
- Un commit por tarea o por grupo lógico, en español, imperativo, con prefijo Conventional Commits.
- Se puede parar en cualquier checkpoint y validar la historia sola.

---

## Cierre — 6 de septiembre de 2026

**Las cuatro puertas en verde**, con un solo dispositivo (`emulator-5554`, Pixel 10, API 37) y
navegación de tres botones:

| Puerta | Resultado |
|---|---|
| `assembleDebug` | ✅ |
| `testDebugUnitTest` | ✅ **1.193** pruebas, 0 fallos (159 clases) |
| `connectedDebugAndroidTest` | ✅ **229** pruebas, 0 fallos, en 1 min 35 s |
| `lintDebug` | ✅ **17** incidencias, las **mismas 17** que en `main`: esta feature no añade ninguna |

**Recorrido manual hecho sobre el emulador con datos reales sincronizados.** Lo que se vio, en el
orden de `quickstart.md` §3: la cabecera dice `Edición del 4 de septiembre de 2026` sobre `39
anuncios` —los treinta y nueve de la pregunta que originó la feature, ahora explicados—; «Personal»
da `Última publicación: 4 de septiembre de 2026` sobre `300 anuncios` y despliega
`Toda la sección · Nombramientos · Oposiciones · Otros de personal`; «Oposiciones» lleva a
`Cursos, oposiciones y concursos` con `100 anuncios` y la segunda fila se queda; «Disposiciones»
—sin hijas— la hace desaparecer sin dejar hueco; 8.1 «Subastas» está vacía y muestra el estado vacío,
**sin fecha y sin rótulo huérfano**; matar el proceso y volver conserva la subsección y su fila;
Atrás desde una subsección cierra la aplicación; el panel abre con escudo, `BOC Cantabria` y la
flecha, sin ningún campo de texto, se recorre entero con las cuatro secciones desplegadas y la flecha
lo recoge sin navegar; y la lupa dice `Filtrar lo que estás viendo…`, el vacío dice `Nada en esta
lista` y el puente entrega `zzzqqq` escrito al buscador global.

**T058 resuelto**: «Boletín de hoy» mide 228 px de los 1.080 de ancho. Cabe de sobra; no hace falta
la alternativa «Hoy».

**Lo que el recorrido NO pudo comprobar, y por qué.** El paso 14 de `quickstart.md` pide girar el
dispositivo: **la aplicación está bloqueada en vertical** por decisión de producto, así que no
aplica en teléfono. El invariante que ese paso protege —que la selección y la segunda fila
sobrevivan a la reconstrucción— queda cubierto por el paso 15, la muerte del proceso, que es el caso
más duro de los dos y **sí** se ejecutó.

**Un hallazgo ajeno a esta feature, arreglado porque el principio V no admite intermitencias.** La
tanda completa sacó en rojo `AskScreenTest.the_failure_text_carries_no_code_and_no_provider_name`,
de la feature 011, que esta feature no toca. Pasaba en aislado tres de tres y fallaba en la tanda.
Causa: afirmaba sobre `fetchSemanticsNode().config.toString()`, un volcado que lleva identidades de
objeto impresas como `@1f429ac`, y una de ellas contenía `429`. Al arreglarlo apareció el segundo
defecto: el nodo etiquetado **no tiene texto propio** —cuelga de sus hijos—, así que la comprobación
llevaba desde la 011 pasando sin mirar nunca el mensaje que decía proteger. Ahora recorre el
subárbol. Queda anotado en `CLAUDE.md`.
