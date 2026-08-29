# Implementation Plan: Boletín del día — lectura del BOC y pantalla de Inicio

**Branch**: `003-boletin-del-dia` | **Date**: 2026-08-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-boletin-del-dia/spec.md`

## Summary

Dar contenido real a la aplicación y enseñarlo bien. Son dos mitades acopladas: leer las diecinueve
fuentes oficiales del Boletín Oficial de Cantabria, normalizarlas y guardarlas en el dispositivo; y
reescribir Inicio para que muestre ese contenido con el sistema de diseño que la feature 002 dejó
implantado, más un panel lateral con las secciones del BOC y la barra de navegación inferior.

Esta feature ejerce la reserva que la constitución dejó abierta: la elección de cliente de red y de
persistencia. Se deciden aquí y se justifican en `research.md`.

Hay una consecuencia menos obvia que conviene anticipar: el marcador de posición de la feature 001
—`ContentItem` y toda su cadena— desaparece. Se declaró explícitamente sustituible por la primera
entidad de negocio, y `Publication` lo es. Dejar las dos conviviendo produciría un grafo con dos
repositorios de contenido, uno alimentado por datos inventados.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (aplicado de forma integrada por AGP 9.3.2)

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01) con Material 3, Navigation Compose 2.10.0,
Koin 4.2.2, Firebase BOM 34.18.0, corrutinas 1.11.0. Se añaden en esta feature: plugin KSP
`2.2.10-2.0.2`, Room 2.8.4 (`room-runtime`, `room-ktx`, `room-compiler`, `room-testing`), OkHttp
BOM 5.5.0 (`okhttp`, `mockwebserver3-junit4` sin versión) y `com.android.tools:desugar_jdk_libs` 2.1.5

**Storage**: **Room** (research.md, D-001). Base de datos local `boc.db`, versión 1, esquema exportado
a `app/schemas/`. Dos tablas: publicaciones y estado de sincronización por fuente. Es la única fuente
de verdad de lo que la pantalla muestra

**Network**: **OkHttp a secas**, sin Retrofit (research.md, D-002). Diecinueve GET de XML crudo,
máximo cuatro simultáneos. El XML se analiza con DOM de `javax.xml.parsers` para que el analizador
sea Kotlin puro y comprobable sin emulador (D-003)

**Testing**: JUnit 4, MockK 1.14.11, Turbine 1.2.1, `kotlinx-coroutines-test` 1.11.0, Robolectric
4.16.1, `koin-test`, Compose UI Test, Konsist 0.17.3, MockWebServer 3 y `room-testing`

**Target Platform**: Android, `minSdk 24` con *desugaring* de la biblioteca estándar para `java.time`,
`compileSdk`/`targetSdk` 37, solo vertical en teléfonos

**Project Type**: Aplicación móvil Android, módulo Gradle único (`:app`) con separación por paquetes

**Performance Goals**: con contenido guardado, publicaciones en pantalla en menos de 1 s (SC-001); en
instalación limpia, boletín del día en menos de 15 s (SC-002); suite sin dispositivo por debajo de
3 min

**Constraints**: `domain` sin dependencias de plataforma —`java.time` sí, `android.*` no—; pruebas
deterministas sin red real y sin reloj del sistema; ningún color, tamaño ni espaciado literal fuera
del tema; nunca se borra una publicación guardada

**Scale/Scope**: ~1.900 publicaciones en la primera sincronización, 19 fuentes, 9 secciones y 14
subsecciones. 2 pantallas nuevas de relleno, 1 pantalla reescrita, 1 panel lateral. ~40 ficheros de
producción y ~20 de prueba

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | La feature recorre el ciclo completo. La rama `003-boletin-del-dia` la creó la extensión git de Spec Kit. No se escribe código de producto hasta tener `tasks.md` | ✅ |
| **II. Arquitectura limpia** | `Publication`, `BocSection`, `HomeSelection`, `SyncSummary`, los dos contratos de repositorio y los cuatro casos de uso son Kotlin puro. Los DTO y las entidades de Room no cruzan a `ui`. El catálogo de direcciones vive en `data`, no en `domain`, porque una URL es procedencia y no negocio (D-012). `java.time` es `java.*`, no `android.*`: la regla de Konsist lo permite | ✅ |
| **III. MVVM** | Tres pantallas nuevas o reescritas con su `Screen`, su `ViewModel` y su `UiState` inmutable. `HomeUiState` es `data class` con un sellado dentro para el contenido; `isRefreshing` e `isOffline` van fuera porque son ejes independientes. Todos los componibles dibujables son sin estado | ✅ |
| **IV. Koin** | Base de datos, DAO, cliente HTTP, fuentes, repositorios, casos de uso y los dos modelos de pantalla se registran en `core/di`. Room y OkHttp se construyen con funciones factoría en `data`, igual que Firebase, porque una regla de arquitectura impide que `core.di` importe un SDK. El test del grafo fallará hasta que estén | ✅ |
| **V. Testing exigente** | Las tres capas de la pirámide. La matriz de analizador del apartado 28 del documento de feeds corre sin emulador gracias a D-003; MockWebServer cubre la fuente remota; `room-testing` con base en memoria cubre el DAO; Turbine, los modelos de pantalla. La aleatoriedad del reintento se inyecta para no romper el determinismo | ✅ |
| **VI. Observabilidad desacoplada** | Inicio registra su vista de pantalla; la sincronización emite `boc_sync` con recuentos, y los fallos van a `CrashReporter`. Ningún SDK nuevo fuera de `data`. Ningún dato personal: los feeds no traen ninguno y los parámetros pasan por el filtro de `AnalyticsEvent` | ✅ |
| **Restricciones tecnológicas** | Las cuatro dependencias nuevas van al catálogo de versiones; OkHttp entra por BOM y por tanto sin versión en sus artefactos. Sin XML de layouts ni Fragments. Corrutinas y `Flow`. Código en inglés, documentación en español. **La reserva sobre red y persistencia se ejerce aquí, que es exactamente donde la constitución dijo que debía ejercerse** | ✅ |

**Resultado de la puerta previa a la fase 0**: pasa. Ninguna violación que justificar.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño no introdujo desviaciones. Las
decisiones que añaden algo no exigido explícitamente quedan en *Complexity Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/003-boletin-del-dia/
├── spec.md                        # 4 historias, 65 requisitos, 12 criterios de éxito
├── plan.md                        # Este fichero
├── research.md                    # Fase 0: 17 decisiones con alternativas descartadas
├── data-model.md                  # Fase 1: los tres vocabularios, el catálogo y las transiciones
├── quickstart.md                  # Fase 1: 12 pasos de validación de extremo a extremo
├── contracts/
│   └── internal-contracts.md      # Fase 1: contratos entre capas, etiquetas de prueba y contrato visual
├── checklists/
│   └── requirements.md            # Checklist de calidad de la especificación
└── tasks.md                       # Fase 2 (/speckit-tasks — NO lo crea /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── core/
│   ├── di/                                 # Se amplían Data, Domain, Ui y Core
│   └── util/
│       ├── RandomProvider.kt               # NUEVO: aleatoriedad inyectada para los reintentos
│       └── Clock.kt                        # NUEVO: tiempo inyectado para la caducidad de la caché
├── data/
│   ├── repository/
│   │   ├── PublicationRepositoryImpl.kt    # NUEVO: Room como única fuente de verdad
│   │   └── BocSectionRepositoryImpl.kt     # NUEVO
│   ├── source/local/
│   │   ├── BocDatabase.kt · PublicationDao.kt · FeedSyncStateDao.kt
│   │   ├── PublicationEntity.kt · FeedSyncStateEntity.kt · Converters.kt
│   │   └── (SE ELIMINAN ContentItemEntity, ContentLocalDataSource, InMemoryContentLocalDataSource)
│   ├── source/remote/
│   │   ├── BocFeedCatalog.kt               # NUEVO: las 19 definiciones, URLs literales
│   │   ├── BocRssParser.kt                 # NUEVO: DOM seguro, Kotlin puro
│   │   ├── PublicationNormalizer.kt        # NUEVO: algoritmo del apartado 10, Kotlin puro
│   │   ├── RssChannelDto.kt · RssItemDto.kt
│   │   ├── PublicationRemoteDataSource.kt · OkHttpPublicationRemoteDataSource.kt
│   │   ├── OkHttpFactory.kt                # NUEVO: función factoría, para que core.di no vea el SDK
│   │   └── (SE ELIMINAN ContentItemDto, ContentRemoteDataSource, StubContentRemoteDataSource)
├── domain/
│   ├── model/Publication.kt · BocSection.kt · EditionType.kt · IdSource.kt ·
│   │         ParserWarning.kt · SectionColorGroup.kt · HomeSelection.kt · SyncSummary.kt ·
│   │         BulletinHeaderData.kt
│   │         (SE ELIMINA ContentItem.kt)
│   ├── repository/PublicationRepository.kt · BocSectionRepository.kt
│   │         (SE ELIMINA ContentRepository.kt)
│   └── usecase/ObservePublicationsUseCase.kt · ObserveBulletinHeaderUseCase.kt ·
│             RefreshPublicationsUseCase.kt · GetBocSectionsUseCase.kt
│             (SE ELIMINA GetContentItemsUseCase.kt)
├── ui/
│   ├── main/MainShell.kt                   # NUEVO: panel lateral + barra inferior + NavHost interno
│   ├── home/HomeScreen.kt · HomeViewModel.kt · HomeUiState.kt      # REESCRITOS
│   ├── home/component/BulletinHeader.kt · SectionFilterChips.kt ·
│   │                  PublicationCard.kt · PublicationCardSkeleton.kt · HomeTopBar.kt
│   ├── sections/SectionsDrawerContent.kt · SectionsViewModel.kt · SectionsUiState.kt
│   ├── search/SearchScreen.kt              # NUEVO: marcador de posición
│   ├── saved/SavedScreen.kt                # NUEVO: marcador de posición
│   └── navigation/Routes.kt · BOCantabriaNavHost.kt · BocBottomBar.kt   # MODIFICADOS y NUEVO
└── core/ui/component/OfflineBanner.kt · ComingSoonMessage.kt          # NUEVOS

app/src/main/res/
├── drawable/                                # 11 vectores de Material Symbols
└── values/strings.xml                       # Textos nuevos

app/src/test/java/com/jrblanco/boccantabria/
├── data/source/remote/BocRssParserTest.kt · PublicationNormalizerTest.kt ·
│                      BocFeedCatalogTest.kt · OkHttpPublicationRemoteDataSourceTest.kt
├── data/source/local/PublicationDaoTest.kt        # Robolectric, base en memoria
├── data/repository/PublicationRepositoryImplTest.kt · BocSectionRepositoryImplTest.kt
├── domain/model/PublicationTest.kt · BocSectionTest.kt · SyncSummaryTest.kt · HomeSelectionTest.kt
├── domain/usecase/  (los cuatro)
├── ui/home/HomeViewModelTest.kt · ui/sections/SectionsViewModelTest.kt
├── integration/BulletinFlowIntegrationTest.kt     # SUSTITUYE a ContentFlowIntegrationTest
└── resources/fixtures/*.xml                       # 10 muestras del apartado 29

app/src/androidTest/java/com/jrblanco/boccantabria/
├── ui/home/HomeContentTest.kt · PublicationCardTest.kt
├── ui/sections/SectionsDrawerTest.kt
├── ui/HomeNavigationTest.kt · BottomBarNavigationTest.kt
├── ui/BocRssParserDeviceTest.kt                   # humo: el analizador también funciona en Android
└── fake/TestGraph.kt                              # AMPLIADO: base en memoria + fuente falsa

app/schemas/                                        # NUEVO: esquema exportado de Room
docs/diseno/especificaciones-diseno.md              # MODIFICADO: las cuatro desviaciones
```

**Structure Decision**: se mantiene el módulo único `:app` con separación por paquetes. Las piezas
nuevas caen en paquetes que ya existen, salvo tres que se crean por razones concretas:
`ui/home/component` porque la pantalla tiene cinco piezas propias que no son reutilizables fuera de
ella y no procede meterlas en `core`; `ui/sections` porque el panel es una pantalla en todo salvo en
que se dibuja encima; y `ui/main` porque el armazón de panel y barra inferior no pertenece a ninguna
pantalla concreta. El catálogo de fuentes vive en `data/source/remote` y no en `domain` por la razón
argumentada en D-012.

## Complexity Tracking

> La puerta de la constitución pasa sin violaciones. Se registran aquí, por transparencia, las
> decisiones que añaden algo que la constitución no exigía explícitamente.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| Plugin KSP nuevo | Room lo exige. No hay forma de usar Room sin procesador de anotaciones | kapt: más lento y desaconsejado en Kotlin 2.2. Escribir el acceso a datos a mano: es la alternativa a Room, descartada en D-001 |
| Dependencia nueva: Room | Ver D-001. Un corpus de ~1.900 publicaciones con inserción-o-actualización por identificador, consultas por fecha y sección, y observación reactiva | DataStore o ficheros: no consultan. SQLite a mano: lo mismo escrito peor |
| Dependencia nueva: OkHttp | Ver D-002. Límites de espera diferenciados, cabeceras por interceptor, cuerpo en streaming y reutilización de conexiones, que es lo que el apartado 17 del documento exige | `HttpURLConnection`: obliga a escribir a mano justo la parte delicada. Retrofit: aporta conversores que aquí no sirven de nada |
| Dependencia nueva: `desugar_jdk_libs` | `java.time.LocalDate` es el tipo correcto para una fecha sin hora y exige API 26, con `minSdk 24` fijado por la constitución | Subir `minSdk`: enmienda constitucional y pérdida de dispositivos. `Calendar`: no es seguro entre hilos y la sincronización es concurrente |
| Paquete nuevo `ui/main` | El panel lateral y la barra inferior envuelven tres destinos pero **no** el arranque. Ponerlos en `ui/navigation` mezclaría el grafo de rutas con el armazón visual | Dibujarlos siempre y esconderlos en el arranque: obliga a que la portada conozca una barra que no le corresponde |
| `RandomProvider` y `Clock` inyectados | Sin ellos, ni los reintentos con espera aleatoria ni la caducidad de treinta minutos serían comprobables de forma determinista, y el principio V lo exige | Usar `Math.random()` y `System.currentTimeMillis()`: pruebas que dependen del azar y del reloj, prohibidas |
| `FeedFetchResult` devuelve el fallo como valor | Es la única interfaz del proyecto que lo hace. Permite que el orquestador siga con las demás fuentes sin anidar capturas, que es lo que exige FR-004 | Lanzar y capturar por fuente: funciona, pero convierte el orquestador en una escalera de `try`/`catch` y hace fácil tragarse una `CancellationException` |
| Eliminar la cadena `ContentItem` | Ver D-015. La feature 001 la declaró sustituible por la primera entidad de negocio | Conservarla: dos repositorios de contenido en el grafo, uno con datos inventados, y una pantalla que tendría que elegir |
