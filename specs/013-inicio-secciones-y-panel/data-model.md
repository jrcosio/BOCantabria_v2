# Modelo de datos — Feature 013

**Esta feature no toca datos.** Ni una entidad, ni una columna, ni una migración, ni una consulta.
Room se queda en la versión 5 y `app/schemas/` no gana ningún fichero. Lo que sigue describe el
**estado de presentación**, que es lo único que cambia, y —con el mismo detalle— lo que **no** cambia,
porque en esta feature esa lista es la mitad del diseño.

---

## 1. Lo que NO cambia

Se enumera a propósito: cualquier cambio aquí sería un defecto introducido por esta feature.

| Elemento | Estado |
|---|---|
| `PublicationDao.observeTodaysBulletin()` — `WHERE publication_date = (SELECT MAX(...))` | **intacta** |
| `PublicationDao.observeBySection()` / `observeBySubsection()` | **intactas** |
| `PublicationRepositoryImpl.observeHeader()` y su recuento | **intacto** |
| `BulletinHeaderData` (`date`, `publicationCount`, `sectionName`, `isTodaysBulletin`) | **intacto** |
| `HomeSelection`, `BocSection`, `SectionColorGroup` | **intactos** |
| `ObservePublicationsUseCase`, `ObserveBulletinHeaderUseCase`, `GetBocSectionsUseCase`, `FilterPublicationsUseCase` | **intactos** |
| `Route.Home(sectionCode, subsectionCode)` | **intacta** |
| `MainShell.openSection()` y el cableado de `onSelectSection` | **intactos** |
| Los módulos de Koin | **intactos** |
| El catálogo de versiones de Gradle | **intacto** |

---

## 2. `HomeUiState` — dos campos nuevos

```
HomeUiState
├── selection            HomeSelection          (sin cambio)
├── header               BulletinHeaderData     (sin cambio)
├── chips                List<SectionChip>      (sin cambio: las nueve de primer nivel)
├── subsections          List<SectionChip>      ← NUEVO
├── isWholeSectionSelected  Boolean             ← NUEVO
├── content              HomeContentState       (sin cambio)
├── isRefreshing         Boolean                (sin cambio)
├── isOffline            Boolean                (sin cambio)
├── share                ShareState             (sin cambio)
├── savedKeys            Set<String>            (sin cambio)
├── saveFailed           Boolean                (sin cambio)
└── search               HomeSearchState        (sin cambio)
```

**`subsections`** — las subsecciones de la sección seleccionada, en el orden del catálogo oficial.
Vacía cuando la selección es el boletín del día o una sección sin hijas. Reutiliza `SectionChip`, que
ya existe; no se crea ningún tipo nuevo.

**`isWholeSectionSelected`** — `true` cuando hay una sección elegida y **no** hay subsección elegida.
Es lo que marca el chip «Toda la sección». Se separa de `subsections` por la misma razón por la que
`isTodaySelected` está separado de `chips` en la fila de arriba: la entrada que representa «todo el
grupo» no es un elemento del grupo, y meterla dentro obligaría a inventarle un código.

### Cómo se calculan

Las dos se derivan de `selection` una sola vez, en la construcción del modelo de pantalla, igual que
`chips`. **No entran en el `combine`**: no dependen de la base de datos ni de la sincronización, y
sumarlas allí obligaría a pasar de cinco a seis flujos —la sobrecarga de `vararg` que `CLAUDE.md`
documenta como trampa, y que `HomeViewModel` ya esquivó una vez agrupando estado local—.

```
selection = TodaysBulletin        →  subsections = []          isWholeSectionSelected = false
selection = Section("1")          →  subsections = []          isWholeSectionSelected = true
selection = Section("2")          →  subsections = [2.1, 2.2, 2.3]   isWholeSectionSelected = true
selection = Section("2", "2.2")   →  subsections = [2.1, 2.2*, 2.3]  isWholeSectionSelected = false
```

(`*` = `isSelected = true`.)

### El árbol, para tenerlo a mano

| Sección | Subsecciones | Segunda fila |
|---|---|---|
| 1 Disposiciones generales | — | no |
| **2 Autoridades y personal** | 2.1 Nombramientos · 2.2 Oposiciones · 2.3 Otros | **sí (3)** |
| 3 Contratación administrativa | — | no |
| **4 Economía, Hacienda y Seguridad Social** | 4.1 Presupuestos · 4.2 Fiscal · 4.3 Seguridad Social · 4.4 Otros | **sí (4)** |
| 5 Expropiación forzosa | — | no |
| 6 Subvenciones y ayudas | — | no |
| **7 Otros anuncios** | 7.1 Urbanismo · 7.2 Medio ambiente · 7.3 Convenios · 7.4 Particulares · 7.5 Varios | **sí (5)** |
| **8 Procedimientos judiciales** | 8.1 Subastas · 8.2 Otros judiciales | **sí (2)** |
| 9 Elecciones | — | no |

Catorce subsecciones en cuatro secciones. La etiqueta de cada chip es el `shortName` del catálogo, que
existe precisamente porque el nombre oficial no cabe en un chip.

---

## 3. `SectionsUiState` — un campo menos

```
SectionsUiState
├── query        String            ← SE RETIRA
├── rows         List<SectionRow>  (sin cambio en el tipo; deja de filtrarse)
├── expanded     Set<String>       (sin cambio; deja de incluir la auto-apertura)
└── selection    HomeSelection     (sin cambio)
```

`SectionRow(section, children)` y su `isExpandable` se quedan exactamente como están.

**`rows`** pasa a ser siempre las nueve secciones con **todas** sus hijas. Antes podía venir podada por
el texto tecleado y con las hijas reducidas a las coincidentes.

**`expanded`** pasa a ser exactamente lo que la persona ha desplegado. Antes era el conjunto
«efectivo», que sumaba las secciones abiertas automáticamente por una coincidencia en sus hijas.

`selection` **se queda**. No tiene relación con el filtro: es el canal por el que el panel —que vive
por encima del anfitrión de navegación— se entera de qué está elegido.

---

## 4. El recorrido de un toque en un chip de subsección

Ningún tramo es nuevo. Se documenta para dejar constancia de que la ruta ya existía y de por qué
FR-018 se cumple por construcción.

```
Chip «Oposiciones» (2.2)
   └─ onSelect("2.2")
        └─ HomeScreen.onSelectSection("2.2")
             └─ MainShell: sections.firstOrNull { it.code == "2.2" }
                  └─ openSection(section)          ← la MISMA función que usa el panel lateral
                       ├─ drawerState.close()      (no-op: el panel está cerrado)
                       ├─ !isTopLevel → Route.Home(sectionCode = "2", subsectionCode = "2.2")
                       ├─ sectionsViewModel.onSelectionChanged(...)
                       └─ navigate { popUpTo<Route.Home> { inclusive = true } }
                            └─ HomeViewModel nuevo, selection reconstruida del SavedStateHandle
                                 ├─ chips:       la 2 marcada (startsWith "2.")
                                 ├─ subsections: [2.1, 2.2*, 2.3]
                                 └─ isWholeSectionSelected = false
```

El `popUpTo<Route.Home> { inclusive = true }` es lo que garantiza que pasear por los chips **no**
construya pila de retroceso, exactamente igual que pasear por el panel. El gesto de volver sigue
cerrando la aplicación desde Inicio.

---

## 5. Entidades de dominio

**Ninguna nueva y ninguna modificada.** Se anota explícitamente para que la regla novena de Konsist
—toda clase de dominio de nivel superior necesita su fichero de prueba— no tenga nada que exigir en
esta feature.
