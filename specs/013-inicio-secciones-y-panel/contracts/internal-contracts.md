# Contratos internos — Feature 013

Las costuras que esta feature modifica. No hay ninguna frontera externa: ni red, ni almacén, ni
servicio. Los contratos de una aplicación son sus componibles, sus estados y sus cadenas.

---

## 1. `SectionFilterChips` — la fila pasa a ser dos

**Antes**

```kotlin
@Composable
fun SectionFilterChips(
    chips: List<SectionChip>,
    isTodaySelected: Boolean,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Después**

```kotlin
@Composable
fun SectionFilterChips(
    chips: List<SectionChip>,
    isTodaySelected: Boolean,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    subsections: List<SectionChip> = emptyList(),
    sectionCode: String? = null,
    isWholeSectionSelected: Boolean = false,
)
```

**Invariantes del contrato**

- La fila de secciones **siempre** se dibuja primero. La de subsecciones, debajo, y solo si
  `subsections` no está vacía.
- `sectionCode` es el código de la sección padre; es lo que emite el chip «Toda la sección». Con
  `subsections` vacía no se lee.
- `onSelect(null)` sigue significando «boletín del día». Sin cambio.
- El componible no decide **qué** subsecciones hay: las recibe. Sigue siendo tonto.
- Los tres parámetros nuevos tienen valor por defecto para que las pruebas y previsualizaciones que
  solo montan la fila de secciones no cambien.

**Etiquetas de prueba**

| Etiqueta | Estado |
|---|---|
| `home_chips` (`TAG_CHIPS`) | se conserva — la fila de secciones |
| `home_chip_all` (`TAG_CHIP_ALL`) | **se conserva el identificador**, cambia su texto visible |
| `home_chip_<code>` (`chipTag`) | se conserva; sirve igual para `"2"` que para `"2.1"` |
| `home_subchips` (`TAG_SUBCHIPS`) | **nueva** — la fila de subsecciones |
| `home_chip_whole_section` (`TAG_CHIP_WHOLE_SECTION`) | **nueva** — la entrada «Toda la sección» |

Conservar `TAG_CHIP_ALL` es deliberado: `HomeNavigationTest`, `HomeContentTest` y
`BottomBarNavigationTest` lo usan para volver al boletín del día, y renombrar la constante convertiría
un cambio de copia en un cambio de tres clases de prueba sin ganar nada.

---

## 2. `HomeContent` / `HomeScreen` — solo reenvían

`HomeContent` pasa a `SectionFilterChips` los tres valores nuevos, sacados de `state`:

```kotlin
SectionFilterChips(
    chips = state.chips,
    isTodaySelected = state.selection is HomeSelection.TodaysBulletin,
    onSelect = onSelectSection,
    subsections = state.subsections,
    sectionCode = (state.selection as? HomeSelection.Section)?.sectionCode,
    isWholeSectionSelected = state.isWholeSectionSelected,
)
```

Ninguna firma de `HomeScreen` ni de `HomeContent` cambia: todo viaja dentro de `HomeUiState`, que ya
es un parámetro.

---

## 3. `BulletinHeader` — misma firma, un rótulo dentro

```kotlin
@Composable
fun BulletinHeader(header: BulletinHeaderData, modifier: Modifier = Modifier)   // sin cambio
```

Lo que cambia vive dentro del `header.date?.let { }` que ya existe: el `Text` de la fecha pasa de
mostrar la fecha formateada a mostrar una de dos cadenas con la fecha interpolada, elegidas con
`header.isTodaysBulletin`.

| Etiqueta | Estado |
|---|---|
| `home_header` (`TAG_HEADER`) | se conserva |
| `home_header_count` (`TAG_HEADER_COUNT`) | se conserva |
| `home_header_date` (`TAG_HEADER_DATE`) | **nueva** — para poder afirmar el rótulo sin depender del texto exacto |

---

## 4. `SectionsDrawerContent` — pierde el filtro, gana la cabecera

**Antes**

```kotlin
@Composable
fun SectionsDrawerContent(
    state: SectionsUiState,
    onQueryChanged: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onSelect: (BocSection) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Después**

```kotlin
@Composable
fun SectionsDrawerContent(
    state: SectionsUiState,
    onToggleExpanded: (String) -> Unit,
    onSelect: (BocSection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Estructura**

```
Column
├── Row  ← cabecera NUEVA
│   ├── Image  ic_escudo_cantabria   (height + aspectRatio 79/137 — ver research D-511)
│   ├── Text   app_bar_title  «BOC Cantabria»   weight(1f)
│   └── IconButton  ic_arrow_back  →  onClose()   tag: sections_close
├── HorizontalDivider
└── LazyColumn  ← sin cambio: nueve SectionRowItem con su chevrón y sus hijas
```

El bloque de estado vacío desaparece con el filtro: sin nada que filtrar, `rows` nunca puede quedarse
en cero.

| Etiqueta | Estado |
|---|---|
| `sections_drawer` (`TAG_SECTIONS_DRAWER`) | se conserva |
| `section_row_<code>` (`sectionRowTag`) | se conserva |
| `section_toggle_<code>` (`sectionToggleTag`) | se conserva |
| `sections_query` (`TAG_SECTIONS_QUERY`) | **se retira** |
| `sections_empty` (`TAG_SECTIONS_EMPTY`) | **se retira** |
| `sections_header` (`TAG_SECTIONS_HEADER`) | **nueva** |
| `sections_close` (`TAG_SECTIONS_CLOSE`) | **nueva** |

---

## 5. `SectionsViewModel` — un método menos

| Miembro | Estado |
|---|---|
| `uiState: StateFlow<SectionsUiState>` | se conserva |
| `onToggleExpanded(sectionCode: String)` | se conserva |
| `onSelectionChanged(selection: HomeSelection)` | se conserva |
| `onQueryChanged(value: String)` | **se retira** |
| `stateFor(...)` (privado) | se simplifica: compone las nueve filas con sus hijas |
| `BocSection.matches(needle)` (privado) | **se retira** |

---

## 6. `MainShell` — un argumento cableado

```kotlin
ModalDrawerSheet {
    SectionsDrawerContent(
        state = sectionsState,
        onToggleExpanded = sectionsViewModel::onToggleExpanded,
        onSelect = ::openSection,
        onClose = { scope.launch { drawerState.close() } },
    )
}
```

`scope` y `drawerState` ya existen en el ámbito; los usa `openSection()`. Nada más cambia en el
fichero salvo el comentario de la línea 86, que explica por qué se usa el árbol completo y no
`sectionsState.rows`: su razón —«están filtradas por lo que se teclea en el panel»— deja de existir,
pero la conclusión sigue siendo la correcta y hay que reescribir el motivo, no borrar la nota.

---

## 7. Cadenas

### Nuevas

| Recurso | Valor propuesto | Requisito |
|---|---|---|
| `chip_whole_section` | `Toda la sección` | FR-009 |
| `home_header_date_bulletin` | `Edición del %1$s` | FR-004, FR-005 |
| `home_header_date_section` | `Última publicación: %1$s` | FR-004, FR-005 |
| `sections_close` | `Recoger el panel` | FR-020, FR-023 |

### Modificadas

| Recurso | Antes | Después | Requisito |
|---|---|---|---|
| `chip_all` → `chip_todays_bulletin` | `Todo` | `Boletín de hoy` | FR-001 |
| `app_bar_search` | `Buscar` | `Filtrar esta lista` | FR-029 |
| `home_search_hint` | `Buscar en esta edición…` | `Filtrar lo que estás viendo…` | FR-028 |
| `home_no_results_title` | `Nada en esta edición` | `Nada en esta lista` | FR-030 |

### Retiradas

| Recurso | Motivo |
|---|---|
| `sections_search_hint` (`Buscar una sección`) | FR-024: el panel deja de filtrarse |
| `sections_empty` (`Ninguna sección coincide con la búsqueda.`) | ídem: sin filtro no hay vacío posible |

### Sin cambio, y conviene decirlo

`home_bulletin_today` (`Boletín de hoy`, el título de la cabecera), `home_search_globally`
(`Buscar en todo el BOC`, el puente), `home_no_results_body` (ya dice «en lo que estás viendo»),
`home_search_close`, `home_search_clear`, `home_search_match_count`, `app_bar_title`,
`app_bar_open_sections`, `sections_expand`, `sections_collapse`.

---

## 8. Lo que este contrato promete NO cambiar

- Ninguna interfaz de repositorio, ningún caso de uso, ninguna `@Query`.
- Ningún módulo de Koin, ningún constructor de modelo de pantalla.
- Ningún evento de analítica, ninguna traza nueva.
- Ninguna dependencia del catálogo de Gradle.
- El comportamiento de `HomeSearchState`, `FilterPublicationsUseCase` y el puente hacia
  `Route.Search(query)`.
