# Implementation Plan: Inicio y panel lateral — que cada control diga lo que hace

**Branch**: `013-inicio-secciones-y-panel` | **Date**: 6 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-inicio-secciones-y-panel/spec.md`

---

## Summary

Cinco correcciones de comprensión sobre Inicio y su panel lateral. **Toda la feature vive en la capa de
presentación**: ni una consulta nueva, ni un modelo de dominio nuevo, ni una migración, ni una
dependencia nueva en el catálogo. El resumen técnico cabe en cinco frases:

1. **El chip «Todo» pasa a llamarse «Boletín de hoy»** y la fecha de la cabecera gana un rótulo que
   depende de la selección. Es un cambio de `strings.xml` más un `if` de una línea en `BulletinHeader`,
   que ya recibe `BulletinHeaderData.isTodaysBulletin`.
2. **La fila de filtros gana una segunda fila con las subsecciones**, derivada de la selección vigente
   y **sin estado nuevo**: la selección ya viaja como argumento de `Route.Home(sectionCode,
   subsectionCode)`, así que sobrevive al giro y a la muerte del proceso sin escribir una línea.
   `HomeViewModel` la calcula con el `GetBocSectionsUseCase` que ya tiene inyectado; `MainShell` **no se
   toca**, porque `onSelectSection(code: String?)` ya resuelve un código de subsección correctamente.
3. **El panel lateral pierde el filtro y gana cabecera.** `SectionsUiState.query`,
   `SectionsViewModel.onQueryChanged` y toda la lógica de coincidencia y auto-apertura desaparecen;
   `SectionsDrawerContent` recibe un `onClose` que `MainShell` cablea al `drawerState` que ya tiene.
4. **La lupa de Inicio conserva íntegro su mecanismo** y cambia cuatro cadenas.
5. **Nada más se toca.** Ninguna clase de `domain`, ninguna de `data`, ningún módulo de Koin, ningún
   fichero de `gradle/`.

La decisión de diseño que sostiene el punto 2 —y que es la única con enjundia— está en
[research.md](./research.md) D-501: **la segunda fila se deriva, no se recuerda**.

---

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11, `minSdk 28` / `targetSdk` 37

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01) con Material 3, Navigation Compose 2.10.0,
Koin 4.2.2 (BOM). **Ninguna nueva.** El catálogo `gradle/libs.versions.toml` no se toca

**Storage**: ninguno. Room queda en la **versión 5** sin cambios: ni tabla, ni columna, ni migración, ni
esquema exportado nuevo. Ninguna `@Query` se modifica

**Testing**: JUnit 4, MockK, `kotlinx-coroutines-test`, Turbine, Konsist (`ArchitectureRulesTest`),
`createComposeRule` para lo que no necesita la actividad real

**Target Platform**: Android 9 (API 28) en adelante, teléfono, vertical, tema claro único

**Project Type**: aplicación Android de módulo único (`:app`), arquitectura limpia + MVVM

**Performance Goals**: la segunda fila se calcula una vez en el `init` del modelo de pantalla sobre una
lista compilada de 23 elementos. Coste nulo. No se añade ninguna emisión al `combine` de `HomeViewModel`

**Constraints**: ni un color, tamaño o espaciado literal; todo texto en `strings.xml`; ningún dato nuevo a
analítica ni a Crashlytics; el comportamiento observable del listado, el recuento y el orden **no pueden
cambiar** (FR-002, FR-003, FR-032, FR-037)

**Scale/Scope**: cero clases de dominio nuevas; cero casos de uso nuevos; 1 modelo de pantalla ampliado
(`HomeViewModel`), 1 simplificado (`SectionsViewModel`), 4 componibles tocados, 1 estado ampliado, 1
estado reducido; ~10 cadenas nuevas o modificadas y 2 retiradas; ~6 clases de prueba nuevas o modificadas

---

## Constitution Check

*Puerta obligatoria antes de la fase 0 y revisada de nuevo tras la fase 1.*

| Principio | Cómo se cumple | Veredicto |
|---|---|---|
| **I — SDD, no negociable** | `specify` → `plan` → `tasks` → `implement`. Rama `013-inicio-secciones-y-panel` creada por Spec Kit sobre `main` con la 012 fusionada (`e929cae`). Ninguna línea de producto antes de `tasks.md` | ✅ |
| **II — Arquitectura limpia por capas** | No se añade ni se modifica nada en `domain` ni en `data`. `HomeViewModel` sigue hablando solo con casos de uso —`GetBocSectionsUseCase`, ya inyectado— y `BocSection` ya es un modelo de dominio. La segunda fila se construye con `SectionChip`, que ya vive en `ui/home` | ✅ |
| **III — MVVM** | La lista de subsecciones se calcula en `HomeViewModel`, no en el componible; `SectionFilterChips` sigue siendo tonto y stateless, y recibe la copia de interfaz («Boletín de hoy», «Toda la sección») desde la vista, exactamente como ya hacía con el chip actual —un modelo de pantalla que busca un `stringResource` es un modelo que deja de poder probarse sin dispositivo—. `HomeUiState` y `SectionsUiState` siguen siendo `data class` inmutables tras un `StateFlow` de solo lectura | ✅ |
| **IV — Koin** | El grafo no cambia: ninguna dependencia nueva, ningún constructor con parámetros distintos. `KoinModulesTest` no necesita retoque | ✅ |
| **V — Testing exigente, no negociable** | La regla novena de Konsist exige fichero de prueba por modelo de pantalla: `HomeViewModelTest` y `SectionsViewModelTest` existen y se amplían/podan. Instrumentadas nuevas montadas con `createComposeRule()` para no cruzar la portada. Las pruebas de filtrado del panel **se retiran porque desaparece la funcionalidad que probaban**, no para pasar la build, y la especificación lo declara superado | ✅ |
| **VI — Observabilidad desacoplada** | No se añade ni un evento. Nada nuevo a Firebase; el texto del filtro rápido sigue sin registrarse (FR-036) | ✅ |

**Restricciones tecnológicas**: sin dependencias nuevas; Compose y Material 3 con los componentes que ya
usa el proyecto (`FilterChip`, `LazyRow`, `Image`, `Icon`, `HorizontalDivider`); tokens del sistema de
diseño en todo lo que se dibuja; `java.time` nativo para el formato de la fecha, con el
`DateTimeFormatter` que ya existe en `BulletinHeader`.

**Konsist**: las nueve reglas siguen en verde sin trabajo extra. En particular, ningún fichero fuera de
`core/ui/theme` importa `androidx.compose.ui.graphics.Color`, y no se introduce ninguna clase de dominio
de nivel superior que quedara sin prueba.

**Puertas de calidad**: las cuatro de siempre, en orden, con `navigation_mode 0` antes de la tanda
instrumentada y un único dispositivo conectado.

**Sin violaciones que justificar.** La sección de complejidad queda vacía a propósito.

---

## Project Structure

### Documentation (this feature)

```text
specs/013-inicio-secciones-y-panel/
├── spec.md                        37 FR, 8 SC, 5 historias
├── plan.md                        este fichero
├── research.md                    D-501 … D-512
├── data-model.md                  qué estado cambia y qué NO cambia
├── contracts/
│   └── internal-contracts.md      las firmas de componible y las cadenas, antes y después
├── quickstart.md                  las cuatro puertas y el recorrido manual
├── checklists/
│   └── requirements.md            calidad de la especificación
└── tasks.md                       lo genera /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── ui/home/
│   ├── HomeUiState.kt             MODIFICADO  + subsections, + isWholeSectionSelected
│   ├── HomeViewModel.kt           MODIFICADO  + buildSubsectionChips()
│   └── component/
│       ├── SectionFilterChips.kt  MODIFICADO  segunda fila + etiqueta del primer chip
│       └── BulletinHeader.kt      MODIFICADO  rótulo de la fecha
├── ui/sections/
│   ├── SectionsUiState.kt         MODIFICADO  − query
│   ├── SectionsViewModel.kt       MODIFICADO  − onQueryChanged, − filtrado, − auto-apertura
│   └── SectionsDrawerContent.kt   MODIFICADO  − campo de texto, + cabecera, + onClose
├── ui/main/
│   └── MainShell.kt               MODIFICADO  cablea onClose; se actualiza un comentario
└── ui/home/HomeScreen.kt          MODIFICADO  pasa la segunda fila a SectionFilterChips

app/src/main/res/values/strings.xml   MODIFICADO  ~10 cadenas, 2 retiradas

app/src/test/java/com/jrblanco/boccantabria/
├── ui/home/HomeViewModelTest.kt        AMPLIADO   subsecciones por selección
└── ui/sections/SectionsViewModelTest.kt PODADO    caen los de filtrado

app/src/androidTest/java/com/jrblanco/boccantabria/ui/
├── home/SectionFilterChipsTest.kt      NUEVO      la segunda fila, con createComposeRule
├── home/BulletinHeaderTest.kt          NUEVO      los dos rótulos de la fecha
├── home/HomeContentTest.kt             AJUSTADO   copia del primer chip
├── sections/SectionsDrawerTest.kt      REESCRITO  cabecera y cierre; fuera los de filtro
└── HomeNavigationTest.kt               AJUSTADO   copia del primer chip
```

**Structure Decision**: módulo único `:app` con separación por paquetes, la del proyecto desde la feature
001. Esta feature no crea ningún paquete: toca cuatro que ya existen (`ui/home`, `ui/home/component`,
`ui/sections`, `ui/main`) y los recursos de cadenas. **No hay ficheros nuevos de producto**, solo de
prueba.

---

## Fases

### Fase 0 — Investigación *(hecha)*

Doce decisiones en [research.md](./research.md), D-501 a D-512. Las tres que de verdad deciden la forma
del código:

- **D-501**: la segunda fila **se deriva de la selección**, no se guarda. Alternativa descartada: un
  `expandedSection` en el estado de pantalla, que habría que sincronizar con la navegación, guardar en
  `SavedStateHandle` y volver a poner al restaurar. Cero estado gana.
- **D-503**: el chip «Toda la sección» lo añade la **vista**, no el modelo de pantalla, por la misma
  razón documentada en `SectionFilterChips` para el chip actual: es copia de interfaz.
- **D-507**: el rótulo de la fecha se decide con `BulletinHeaderData.isTodaysBulletin`, que ya existe y
  ya distingue exactamente los dos casos. No hace falta ningún dato nuevo en el modelo.

### Fase 1 — Diseño *(hecha)*

- [data-model.md](./data-model.md) — qué campos gana `HomeUiState`, qué pierde `SectionsUiState`, y una
  lista explícita de **lo que no cambia**, que en esta feature es la mitad del diseño.
- [contracts/internal-contracts.md](./contracts/internal-contracts.md) — las firmas de los cuatro
  componibles antes y después, las etiquetas de prueba que se conservan y las que nacen, y la tabla
  completa de cadenas con su valor propuesto.
- [quickstart.md](./quickstart.md) — las cuatro puertas y el recorrido manual de siete pasos, incluido
  cómo evitar las trampas ya pagadas del emulador.

### Fase 2 — Tareas

`/speckit-tasks` descompone por historia de usuario. El orden natural es el de las prioridades de la
especificación, y las cinco historias son **independientes entre sí**: cada una se puede implementar,
probar y demostrar sola. La US3 (subsecciones) es la única con lógica; las otras cuatro son copia y
composición.

### Fase 3 — Implementación

`/speckit-implement`. Cierre con las cuatro puertas en verde, actualización de `docs/diseno/` —cambian
la fila de filtros, la cabecera editorial y el panel lateral, y ese documento es la fuente de verdad de
la interfaz— y de `CLAUDE.md` en lo que quede desactualizado (el panel ya no tiene «un campo de filtro,
las nueve secciones y sus subsecciones»).

---

## Riesgos, con su salida

| Riesgo | Salida |
|---|---|
| «Boletín de hoy» no cabe en el chip en pantallas estrechas | La fila es un `LazyRow` y se desplaza, así que no rompe nada; si estéticamente chirría, la alternativa acordada con el propietario es «Hoy» (`spec.md`, Assumptions). Un cambio de una cadena |
| Dos filas de chips comen altura y empujan el listado | Se mide en el recorrido manual. La segunda fila solo existe con una sección con subsecciones elegida, que es justo cuando aporta; con el boletín del día —el arranque— la pantalla es idéntica a hoy |
| Retirar el filtro del panel deja las 23 filas difíciles de recorrer | El panel ya es un `LazyColumn`; lo que se añade encima es una cabecera de una fila. Se comprueba en el recorrido manual con las cuatro secciones desplegadas a la vez (FR-026) |
| Las pruebas instrumentadas nuevas encarecen la tanda | Se montan con `createComposeRule()`, no con `createAndroidComposeRule<MainActivity>()`: se ahorra cruzar la portada, pero el suelo de ~46 s por prueba que documenta `CLAUDE.md` sigue ahí. Tres clases nuevas ≈ +2,5 min |
| Podar `SectionsViewModelTest` se lea como «borrar pruebas para pasar la build» | La especificación declara el filtro **superado** con requisito propio (FR-024) y este plan lo repite. Las pruebas que caen son las de una funcionalidad retirada; ninguna que siga describiendo comportamiento vigente se toca |
| Alguien reordene las dos filas y la segunda quede arriba | El contrato de `SectionFilterChips` fija el orden y hay prueba instrumentada que afirma que la fila de secciones precede a la de subsecciones |

---

## Complexity Tracking

Sin violaciones de la constitución. Tabla vacía a propósito.
