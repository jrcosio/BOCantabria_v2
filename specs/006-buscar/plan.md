# Implementation Plan: Buscar

**Branch**: `006-buscar` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-buscar/spec.md`

## Summary

Retirar las dos entradas de búsqueda que hoy no llevan a ninguna parte —la lupa de Inicio, que
lanza un `Toast`, y la pestaña Buscar, que pinta un «Próximamente»— y convertirlas en dos búsquedas
distintas con un puente entre ellas.

Tres cosas definen este plan.

La primera es **dónde se pliegan las tildes**. `LIKE` de SQLite compara byte a byte y no ignora los
diacríticos; Android no trae la extensión ICU que lo arreglaría. La salida es normalizar **al
escribir**: cada publicación guarda una columna `search_text` con su título, su organismo, su
jerarquía de organismos, su referencia y el **nombre** de su sección y subsección, todo en
minúsculas y sin tildes. La consulta se normaliza igual y se compara con un `LIKE` corriente. Eso
mantiene la semántica literal que pide FR-004 —`ielagos` sigue encontrando `Piélagos`, cosa que un
índice de texto completo no haría— y deja la parte cara hecha una sola vez, cuando el anuncio entra.

La segunda es **el relleno**. La columna obliga a subir la base de datos a la versión 3, y tras la
migración toda fila existente queda con `search_text` vacío. Una sincronización solo refresca los
últimos cien anuncios de cada fuente, así que sin relleno el archivo anterior sería invisible para
siempre y FR-027 quedaría incumplido de una forma que **no se ve en una instalación limpia**. El
relleno corre por lotes dentro de la propia sincronización, usando `search_text = ''` como marcador
de «aún sin rellenar»: idempotente, sin bandera que guardar y gratis en una instalación nueva.

La tercera es **que son dos búsquedas de verdad, no una con dos entradas**. La de Inicio filtra en
memoria lo que la pantalla ya tiene y no toca el almacén; la global consulta el almacén y no sabe
nada de lo que Inicio esté mostrando. Lo único que comparten es la normalización del texto. El
puente entre ellas es una ruta con argumento, y su riesgo real —que el estado guardado de la
pestaña se trague el término traspasado— se resuelve navegando sin restauración de estado y se fija
con una prueba.

Lo que **no** hace, y se dice en la especificación y no en una nota al pie: no hay filtro de
municipio, porque el dato no existe; no hay ordenación por relevancia; y no hay resaltado de
coincidencias, búsquedas recientes ni tarjeta compacta.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (aplicado de forma integrada por AGP 9.3.2)

**Primary Dependencies**: **ninguna nueva y ninguna versión que subir** (research.md D-020). Jetpack
Compose (BOM 2026.02.01) con Material 3 —de donde salen `ModalBottomSheet`, `DatePicker` y
`FilterChip`, ya presentes—, Navigation Compose 2.10.0, Koin 4.2.2, Room 2.8.4 con KSP, OkHttp BOM
5.5.0, Firebase BOM 34.18.0, corrutinas 1.11.0

**Storage**: Room. **Cambio de esquema**: `publications` gana una columna `search_text TEXT NOT NULL
DEFAULT ''`; la base de datos pasa a la **versión 3** con `AutoMigration(2, 3)` y el `3.json` se
versiona (D-004). No se toca `bocDatabase()`: sin `fallbackToDestructiveMigration`. **Sin índice**
sobre la columna: un `LIKE '%…%'` no puede usarlo (D-006)

**Network**: **ninguna**. Las dos búsquedas se resuelven con lo que hay en el dispositivo (FR-012,
FR-026). No se toca la capa remota salvo para construir el texto buscable al normalizar

**Testing**: JUnit 4, MockK 1.14.11, Turbine 1.2.1, `kotlinx-coroutines-test` 1.11.0, Robolectric
4.16.1, `koin-test`, Compose UI Test, Konsist 0.17.3. La prueba de migración 2→3 se escribe con el
mismo patrón hecho a mano que la 1→2, que corre sin emulador (D-005)

**Target Platform**: Android, `minSdk 28`, `compileSdk`/`targetSdk` 37, solo vertical en teléfonos

**Performance Goals**: la búsqueda rápida recorta la lista dentro del mismo fotograma en el que se
escribe (SC-001); la global devuelve resultados en menos de un segundo desde que se deja de
escribir, con un año de boletines almacenados (SC-008)

**Constraints**: `domain` sin dependencias de plataforma; ninguna sentencia de borrado en todo el
proyecto; `saved_at` y `first_seen_at` siguen fuera de la lista blanca del `UPDATE` de
sincronización (D-009); pruebas deterministas con el tiempo inyectado; ningún color, tamaño ni
espaciado literal fuera del tema; el texto de las consultas nunca sale en analítica ni en trazas
(FR-046)

**Scale/Scope**: 1 pantalla reescrita, 1 pantalla y 1 barra superior modificadas, 4 componibles
nuevos. ~26 ficheros de producción nuevos o modificados y ~19 de prueba

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluado contra la constitución **1.1.0**. Esta feature **no** la enmienda: ninguna norma cambia.

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | La feature recorre el ciclo completo. La rama `006-buscar` la creó la extensión git de Spec Kit desde `/speckit-specify`. Ni una línea de producto antes de `tasks.md` | ✅ |
| **II. Arquitectura limpia** | `SearchQuery`, `SearchSort` y `SearchResults` son Kotlin puro; `SearchRepository` es una interfaz de `domain` y su implementación vive en `data`. La entidad de Room no cruza a `ui`: el DAO devuelve entidades y el repositorio las mapea. `search_text` es una columna, **no** un campo de dominio (D-001). `SearchText` va en `core/util`, que ya es de donde `domain` toma `AppVersionProvider` | ✅ |
| **III. MVVM** | `SearchScreen` + `SearchViewModel` + `SearchUiState` inmutable, con `SearchContent` sin estado para poder montarlo en su prueba. El filtrado de la búsqueda rápida ocurre en `HomeViewModel`, **nunca** en un `@Composable`. Los cuatro componibles nuevos son sin estado, con el estado izado | ✅ |
| **IV. Koin** | DAO, repositorio, tres casos de uso y el modelo de pantalla se registran en `core/di`. `KoinModulesTest` fallará hasta que estén. Nada se instancia a mano | ✅ |
| **V. Testing exigente** | Las tres capas. El DAO se prueba contra una base de datos real en memoria, como manda la costumbre del proyecto. La migración 2→3 tiene su prueba, y el relleno la suya —es el fallo invisible en instalación limpia—. `SearchQuery`, `SearchSort` y `SearchResults` traen su fichero de prueba porque la regla de Konsist lo exige para toda clase de dominio de nivel superior | ✅ |
| **VI. Observabilidad desacoplada** | `trackScreenView("search")` y un evento de búsqueda **sin el texto**, con solo un tramo del número de resultados (D-021). Ningún SDK nuevo, ninguna llamada a Firebase fuera de `data` | ✅ |
| **Restricciones tecnológicas** | Ninguna dependencia nueva, así que `libs.versions.toml` no se toca. Compose y Material 3, sin Fragments ni XML de layouts. Corrutinas y `Flow`. Código en inglés, documentación en español | ✅ |
| **Flujo y puertas de calidad** | Las cuatro puertas, en orden. Las pruebas de migración y de relleno caen en la puerta 2, que es la que CI ejecuta en cada empujón | ✅ |

**Resultado de la puerta previa a la fase 0**: pasa. Ninguna violación que justificar.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño no introdujo desviaciones. Las
decisiones que añaden algo que la constitución no exigía explícitamente quedan en *Complexity
Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/006-buscar/
├── spec.md                        # 5 historias, 46 requisitos, 12 criterios de éxito
├── plan.md                        # Este fichero
├── research.md                    # Fase 0: 24 decisiones con alternativas descartadas
├── data-model.md                  # Fase 1: el texto buscable, la consulta y la migración
├── quickstart.md                  # Fase 1: pasos de validación de extremo a extremo
├── contracts/
│   └── internal-contracts.md      # Fase 1: contratos, etiquetas de prueba y contrato visual
├── checklists/
│   └── requirements.md            # Checklist de calidad de la especificación
└── tasks.md                       # Fase 2 (/speckit-tasks — NO lo crea /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── core/
│   ├── di/DataModule.kt · DomainModule.kt · UiModule.kt          # AMPLIADOS
│   └── util/SearchText.kt                                        # NUEVO: normalización compartida
├── data/
│   ├── repository/SearchRepositoryImpl.kt                        # NUEVO
│   │   repository/PublicationRepositoryImpl.kt                   # MODIFICADO: texto buscable + relleno
│   └── source/local/PublicationSearchDao.kt                      # NUEVO: solo lectura
│       source/local/PublicationSearchText.kt                     # NUEVO: constructor del texto
│       source/local/LikePattern.kt                               # NUEVO: escapado de % _ \
│       source/local/PublicationEntity.kt                         # MODIFICADO: columna search_text
│       source/local/PublicationDao.kt                            # MODIFICADO: lista blanca + relleno
│       source/local/BocDatabase.kt                               # MODIFICADO: versión 3
├── domain/
│   ├── model/SearchQuery.kt · SearchSort.kt · SearchResults.kt   # NUEVOS
│   ├── repository/SearchRepository.kt                            # NUEVO
│   └── usecase/SearchPublicationsUseCase.kt ·
│               GetSearchIssuersUseCase.kt ·
│               FilterPublicationsUseCase.kt                      # NUEVOS
└── ui/
    ├── search/SearchScreen.kt                                    # REESCRITO (era el marcador)
    │   search/SearchViewModel.kt · SearchUiState.kt              # NUEVOS
    │   search/component/SearchField.kt · SearchFiltersSheet.kt ·
    │                    ActiveFilterChips.kt · SortSelector.kt   # NUEVOS
    ├── home/component/HomeTopBar.kt                              # MODIFICADO: modo de búsqueda
    │   home/HomeScreen.kt · HomeViewModel.kt · HomeUiState.kt    # MODIFICADOS
    ├── navigation/Routes.kt                                      # MODIFICADO: Search con argumento
    └── main/MainShell.kt                                         # MODIFICADO: la lupa y el puente

app/src/main/res/
├── drawable/ic_close.xml · ic_filter_list.xml · ic_sort.xml      # NUEVOS
└── values/strings.xml                                            # MODIFICADO: textos de la 006

app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/3.json   # NUEVO, se versiona

app/src/test/java/com/jrblanco/boccantabria/
├── core/util/SearchTextTest.kt
├── data/source/local/PublicationSearchDaoTest.kt · BocDatabaseMigrationTest.kt   # 2º AMPLIADO
│   data/source/local/PublicationSearchTextTest.kt
├── data/repository/SearchRepositoryImplTest.kt ·
│   data/repository/PublicationRepositoryImplTest.kt              # AMPLIADO: el relleno
├── domain/model/SearchQueryTest.kt · SearchSortTest.kt · SearchResultsTest.kt
├── domain/usecase/  (los tres casos de uso)
├── ui/search/SearchViewModelTest.kt
├── ui/home/HomeViewModelTest.kt                                  # AMPLIADO
├── di/KoinModulesTest.kt                                         # AMPLIADO
└── integration/SearchFlowIntegrationTest.kt

app/src/androidTest/java/com/jrblanco/boccantabria/
├── ui/search/SearchContentTest.kt · SearchFiltersSheetTest.kt    # NUEVOS
├── ui/home/HomeSearchTest.kt                                     # NUEVO
├── ui/SearchHandoffTest.kt                                       # NUEVO: el puente
├── ui/BottomBarNavigationTest.kt                                 # MODIFICADO: Buscar ya no es promesa
└── fake/TestGraph.kt                                             # AMPLIADO: DAO y repositorio nuevos

docs/diseno/especificaciones-diseno.md      # MODIFICADO: enmienda del apartado 17
CLAUDE.md                                   # MODIFICADO: árbol, almacén y la búsqueda
```

**Structure Decision**: se mantiene el módulo único `:app`. `ui/search` ya existe como paquete de
pantalla y solo cambia de contenido, igual que le pasó a `ui/saved` en la feature 005. En `data` no
se inventa una capa: un DAO más de solo lectura sobre la tabla que ya existe, un repositorio más de
los que ya hay y dos ficheros de utilidad sin estado. La normalización va a `core/util` y no a
`domain` porque la usan las tres capas, y ese paquete ya es de donde `domain` toma
`AppVersionProvider`.

## Complexity Tracking

> La puerta de la constitución pasa sin violaciones. Se registran aquí, por transparencia, las
> decisiones que añaden algo que la constitución no exigía explícitamente.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| **Columna derivada en la tabla de la fuente** | Es lo único que hace que `LIKE` ignore tildes en Android, donde no hay `COLLATE` con ICU. Y deja la normalización hecha una vez por anuncio en lugar de una vez por pulsación de tecla | Normalizar en la consulta: imposible sin una función SQL propia. Filtrar el archivo entero en memoria: funciona con dos mil filas y deja de funcionar con veinticinco mil (research.md D-001) |
| **Relleno de las filas anteriores dentro de la sincronización** | Sin él, FR-027 se incumple de la peor manera posible: **no se ve en una instalación limpia**, solo en el móvil de quien ya tenía la aplicación | Rellenar en la migración: SQLite no sabe quitar tildes. Rellenar al arrancar: retrasa una pantalla que ya tiene un mínimo de 1,2 s. Rellenar en la primera búsqueda: paga la latencia justo cuando alguien está esperando (D-005) |
| **Tercer DAO sobre la misma tabla, de solo lectura** | Mantiene legible la cabecera de `PublicationDao`, donde vive la regla de que aquí no se borra nada. El precedente es `SavedPublicationDao` | Ampliar `PublicationDao`: un fichero menos y una invariante peor contada (D-007) |
| **Dos sentencias de búsqueda en lugar de una** | Room no parametriza la dirección del `ORDER BY`, y el proyecto prefiere SQL explícito y auditable | `@RawQuery` o un `CASE WHEN` dentro del `ORDER BY`: una sentencia menos, ilegible y fuera del alcance de la verificación en compilación (D-008) |
| **Subida de la base a la versión 3** | No es opcional: la columna la exige. Migración automática contra el esquema exportado, que es el caso que Room resuelve entero | `fallbackToDestructiveMigration()`: pasaría la compilación y vaciaría el boletín de quien ya tiene la aplicación (D-004) |
| **Estado de Buscar en el `SavedStateHandle`** | La barra inferior navega con `saveState`/`restoreState`, y eso **destruye el modelo de pantalla** al cambiar de pestaña. Guardarlo solo en el modelo incumpliría FR-044 y FR-045 | Guardar solo en el `ViewModel`: menos código y la consulta se pierde al ir a Inicio y volver (D-014) |
| **Un caso de uso puro para el filtrado en memoria** | La regla de Konsist obliga a que los casos de uso vivan en `domain/usecase`, y poner la coincidencia en el `ViewModel` la dejaría sin prueba propia y duplicada el día que otra pantalla la necesite | Una función privada en `HomeViewModel`: un fichero menos, la regla de coincidencia sin prueba aislada (D-012) |
| **`Route.Search` con argumento opcional** | Es el puente de la historia 3. Sin argumento, el término habría que teclearlo otra vez | Un estado compartido entre pantallas: rompe que la ruta sea la única fuente de verdad de la navegación, y no sobrevive a la muerte del proceso (D-013) |
| **Tres vectores nuevos** | El conjunto básico de iconos de Material no está en el classpath con este BOM, y cerrar, filtrar y ordenar no tienen equivalente entre los diecinueve que ya hay | Reutilizar iconos existentes con otro significado: un aspa que en realidad es una flecha atrás confunde más de lo que ahorra (D-022) |
