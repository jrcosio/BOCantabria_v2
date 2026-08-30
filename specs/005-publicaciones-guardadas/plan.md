# Implementation Plan: Publicaciones guardadas

**Branch**: `005-publicaciones-guardadas` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-publicaciones-guardadas/spec.md`

## Summary

Retirar el último «Próximamente» de la aplicación. Marcar una publicación la guarda, desmarcarla la
quita, y el destino Guardados pasa a mostrar la lista de lo guardado con las mismas tarjetas que
Inicio.

Dos cosas la definen. La primera es **dónde vive la marca**: una columna nullable en la tabla de
publicaciones, porque el `UPDATE` de la sincronización es una lista blanca de columnas escrita justo
para que un dato local sobreviva a una sincronización posterior —el precedente es `first_seen_at`, y
tiene su prueba—. Eso convierte «una sincronización no puede perder una marca» en una propiedad de la
sentencia, no en una promesa. La segunda es **la actualización del almacén**: la base de datos sube a
la versión 2 con migración automática contra el esquema exportado, y sin borrado destructivo, porque
un atajo ahí le vacía el boletín a quien ya tiene la aplicación instalada.

Lo que **no** hace, y se dice en un requisito y no en una nota al pie (FR-024): guardar no descarga ni
conserva el documento. La guía operativa y la decisión D-003 de la feature 004 prometieron que
Guardados sería *también* leer sin conexión; esta feature marca, y esa promesa queda aplazada con su
motivo escrito.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (aplicado de forma integrada por AGP 9.3.2)

**Primary Dependencies**: **ninguna nueva y ninguna versión que subir** (research.md D-015). Jetpack
Compose (BOM 2026.02.01) con Material 3, Navigation Compose 2.10.0, Koin 4.2.2, Room 2.8.4 con KSP,
OkHttp BOM 5.5.0, Firebase BOM 34.18.0, corrutinas 1.11.0

**Storage**: Room. **Cambio de esquema**: `publications` gana una columna `saved_at INTEGER` nullable
con su índice; la base de datos pasa a la **versión 2** con `AutoMigration(1, 2)` y el `2.json` se
versiona (D-001, D-002). No se toca `bocDatabase()`: sin `fallbackToDestructiveMigration`

**Network**: sin cambios. Guardar no habla con la red (FR-006)

**Testing**: JUnit 4, MockK 1.14.11, Turbine 1.2.1, `kotlinx-coroutines-test` 1.11.0, Robolectric
4.16.1, `koin-test`, Compose UI Test, Konsist 0.17.3. La prueba de migración es propia y corre bajo
Robolectric, sin emulador (D-003)

**Target Platform**: Android, `minSdk 28`, `compileSdk`/`targetSdk` 37, solo vertical en teléfonos

**Performance Goals**: el efecto de marcar se ve en el mismo toque (SC-001); con doscientas
publicaciones guardadas la lista se abre y se desplaza sin saltos (SC-007)

**Constraints**: `domain` sin dependencias de plataforma; ninguna sentencia de borrado en todo el
proyecto (FR-021); pruebas deterministas con el tiempo inyectado; ningún color, tamaño ni espaciado
literal fuera del tema; el estado de guardado idéntico en tarjeta y detalle en todo momento (SC-003)

**Scale/Scope**: 1 pantalla nueva, 2 pantallas y 1 componente compartido modificados, 1 componente
mudado de paquete. ~16 ficheros de producción nuevos o modificados y ~13 de prueba

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluado contra la constitución **1.1.0**. Esta feature **no** la enmienda: ninguna norma cambia.

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | La feature recorre el ciclo completo. La rama `005-publicaciones-guardadas` la creó la extensión git de Spec Kit desde `/speckit-specify`. Ni una línea de producto antes de `tasks.md` | ✅ |
| **II. Arquitectura limpia** | No hay modelo de dominio nuevo: `Publication` no cambia (D-004). `SavedPublicationRepository` es una interfaz de `domain`, su implementación es de `data`, y la entidad de Room no cruza a `ui` —el DAO devuelve entidades y el repositorio las mapea—. `saved_at` es una columna, no un campo de dominio | ✅ |
| **III. MVVM** | `SavedScreen` + `SavedViewModel` + `SavedUiState` inmutable. `share` va fuera del sellado de contenido porque es un eje independiente, igual que en Inicio y en el detalle. `SavedContent` es sin estado y se monta solo en su prueba | ✅ |
| **IV. Koin** | DAO, repositorio, tres casos de uso y el modelo de pantalla se registran en `core/di`. `KoinModulesTest` fallará hasta que estén. Nada se instancia a mano | ✅ |
| **V. Testing exigente** | Las tres capas. El DAO y el repositorio se prueban contra una base de datos real en memoria, como manda la costumbre del proyecto —aquí nunca se falsea un DAO—. La regresión que importa (una sincronización no pierde la marca) es una prueba de DAO. La migración tiene la suya, y es la primera del proyecto | ✅ |
| **VI. Observabilidad desacoplada** | Un evento `publication_save` con un único parámetro booleano, emitido desde `data` (D-012). **Ningún dato personal**: ni clave, ni título, ni sección. Ningún SDK nuevo | ✅ |
| **Restricciones tecnológicas** | Ninguna dependencia nueva, así que `libs.versions.toml` no se toca. Compose y Material 3, sin Fragments ni XML de layouts. Corrutinas y `Flow`. Código en inglés, documentación en español | ✅ |
| **Flujo y puertas de calidad** | Las cuatro puertas, en orden. La prueba de migración cae en la puerta 2, que es la que CI ejecuta en cada empujón | ✅ |

**Resultado de la puerta previa a la fase 0**: pasa. Ninguna violación que justificar.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño no introdujo desviaciones. Las
decisiones que añaden algo que la constitución no exigía explícitamente quedan en *Complexity
Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/005-publicaciones-guardadas/
├── spec.md                        # 4 historias, 25 requisitos, 12 criterios de éxito
├── plan.md                        # Este fichero
├── research.md                    # Fase 0: 15 decisiones con alternativas descartadas
├── data-model.md                  # Fase 1: la marca, el almacén y la migración
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
│   └── ui/component/PublicationCard.kt                           # MUDADO desde ui/home/component
│       ui/component/IllustratedMessage.kt                        # NUEVO
│       ui/component/SaveFailureToast.kt                          # NUEVO
│       ui/component/ComingSoonMessage.kt                         # MODIFICADO: delega
├── data/
│   ├── repository/SavedPublicationRepositoryImpl.kt              # NUEVO
│   └── source/local/SavedPublicationDao.kt                       # NUEVO
│       source/local/PublicationEntity.kt · BocDatabase.kt        # MODIFICADOS
├── domain/
│   ├── repository/SavedPublicationRepository.kt                  # NUEVO
│   └── usecase/ObserveSavedPublicationsUseCase.kt ·
│               ObserveSavedKeysUseCase.kt ·
│               SetPublicationSavedUseCase.kt                     # NUEVOS
└── ui/
    ├── saved/SavedScreen.kt · SavedViewModel.kt · SavedUiState.kt  # NUEVOS (sustituyen el marcador)
    ├── share/ShareEffect.kt                                      # NUEVO: extraído (D-011)
    ├── home/HomeScreen.kt · HomeViewModel.kt · HomeUiState.kt     # MODIFICADOS
    ├── detail/PublicationDetailScreen.kt · PublicationDetailRoute.kt ·
    │          PublicationDetailViewModel.kt · PublicationDetailUiState.kt   # MODIFICADOS
    └── main/MainShell.kt                                         # MODIFICADO: cablea Guardados

app/src/main/res/
├── drawable/ic_bookmark_filled.xml                               # NUEVO
└── values/strings.xml                                            # MODIFICADO: textos del apartado 22

app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/2.json   # NUEVO, se versiona

app/src/test/java/com/jrblanco/boccantabria/
├── data/source/local/SavedPublicationDaoTest.kt · BocDatabaseMigrationTest.kt
├── data/repository/SavedPublicationRepositoryImplTest.kt
├── domain/usecase/  (los tres casos de uso)
├── ui/saved/SavedViewModelTest.kt
├── ui/home/HomeViewModelTest.kt · ui/detail/PublicationDetailViewModelTest.kt   # AMPLIADOS
├── di/KoinModulesTest.kt                                          # AMPLIADO
└── integration/SavedFlowIntegrationTest.kt

app/src/androidTest/java/com/jrblanco/boccantabria/
├── core/ui/component/PublicationCardTest.kt                       # MUDADO y ampliado
├── ui/saved/SavedContentTest.kt                                   # NUEVO
├── ui/home/HomeContentTest.kt · ui/BottomBarNavigationTest.kt      # AMPLIADOS
└── fake/TestGraph.kt                                              # AMPLIADO: DAO y repositorio nuevos

docs/diseno/especificaciones-diseno.md      # MODIFICADO: enmienda del apartado 22
CLAUDE.md                                   # MODIFICADO: árbol, almacén y la promesa aplazada
```

**Structure Decision**: se mantiene el módulo único `:app`. `ui/saved` ya existe como paquete de
pantalla y solo cambia de contenido. El único movimiento entre paquetes es la tarjeta, que pasa a
`core/ui/component` porque desde esta feature la usan dos pantallas y ahí es donde la guía del
proyecto pone los componibles compartidos sin estado (D-008). En `data` no se inventa nada: un DAO más
sobre la tabla que ya existe y un repositorio más de los que ya hay.

## Complexity Tracking

> La puerta de la constitución pasa sin violaciones. Se registran aquí, por transparencia, las
> decisiones que añaden algo que la constitución no exigía explícitamente.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| **Un dato de la persona en la tabla de la fuente** | Es lo que hace que la marca sobreviva a una sincronización sin código que la proteja: el `UPDATE` es una lista blanca y la columna no está en ella. El precedente, `first_seen_at`, ya vive ahí con una prueba que lo fija | Tabla aparte: separa mejor los dueños, pero obliga a una unión o a combinar dos flujos en cada pantalla e introduce la primera sentencia de borrado del proyecto (research.md D-001) |
| **Segundo DAO sobre la misma tabla** | El fichero donde vive la regla «aquí no hay borrados» se lee de un tirón, y añadirle la primera escritura que no viene de la fuente lo diluye | Ampliar `PublicationDao`: un fichero menos, una invariante peor contada (D-006) |
| **Repositorio propio en lugar de ampliar el que hay** | `PublicationRepositoryImpl` ya tiene diez dependencias y `@Suppress("LongParameterList")`. La marca es otra responsabilidad | Tres métodos más en `PublicationRepository`: mezcla lo que la persona posee con lo que la fuente publica (D-005) |
| **Subida de versión de la base de datos** | No es opcional: la columna la exige. Se hace con migración automática contra el esquema exportado, que es el caso que Room resuelve entero | `fallbackToDestructiveMigration()`: pasaría la compilación y vaciaría el boletín de quien ya tiene la aplicación. Incumple FR-023 (D-002) |
| **Prueba de migración escrita a mano** | `MigrationTestHelper` exige `Instrumentation` y carga el esquema desde los assets del paquete de pruebas; montarlo obligaría a enviar el esquema en el APK o a pagar un emulador. La versión propia prueba el camino de producción y corre en la puerta 2 | `MigrationTestHelper` en `androidTest`: es la vía documentada, pero mueve la comprobación a la puerta que necesita emulador (D-003) |
| **Mudanza de `PublicationCard`** | Dos pantallas la usan; la guía del proyecto dice dónde van los componibles compartidos | Importarla desde `ui/saved`: cero cambios hoy, una dependencia entre pantallas para siempre (D-008) |
| **`IllustratedMessage` nuevo** | El estado vacío del apartado 22.3 pide icono, título propio, texto y acción, y ninguno de los tres mensajes existentes los tiene todos | Un componible privado en `ui/saved`: la cuarta copia de la misma columna centrada (D-010) |
| **Vector nuevo para el marcador relleno** | El diseño pide trazado relleno, y sobre la barra azul del detalle un cambio de tinte no distingue nada | Cambiar tinte u opacidad: no funciona sobre azul y distingue estados por intensidad de color (D-009) |
