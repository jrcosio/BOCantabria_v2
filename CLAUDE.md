# CLAUDE.md

Guía operativa para Claude Code en este repositorio.

> Este fichero es la **guía operativa**; la **constitución** (`.specify/memory/constitution.md`)
> es la norma. Si enmiendas una, actualiza la otra en el mismo cambio.

---

## Regla número uno: SDD obligatorio

Este proyecto usa **Spec-Driven Development** con **GitHub Spec Kit**. Toda feature recorre el
ciclo completo antes de tocar código de producto:

```
/speckit-specify  →  /speckit-plan  →  /speckit-tasks  →  /speckit-implement
```

- **No escribas código de producto sin un `tasks.md` aprobado** en `specs/<NNN>-<slug>/`.
  Si te piden una feature directamente, arranca por `/speckit-specify`.
- `/speckit-specify` crea también la rama `NNN-slug` (extensión git de Spec Kit).
- Opcionales pero recomendados: `/speckit-clarify` antes de planificar, `/speckit-analyze`
  antes de implementar.
- **Exentos del ciclo**: arreglos de build, subidas de versión, erratas y documentación.

Las normas del proyecto viven en `.specify/memory/constitution.md`.

### Comandos Spec Kit disponibles

| Comando | Cuándo |
|---|---|
| `/speckit-specify` | Arranque de toda feature. Crea rama + `specs/NNN-slug/spec.md` |
| `/speckit-clarify` | Opcional, **antes** de `/speckit-plan`. Resuelve ambigüedades |
| `/speckit-plan` | Diseño técnico → `plan.md` |
| `/speckit-tasks` | Descomposición ejecutable → `tasks.md` |
| `/speckit-checklist` | Opcional, tras `/speckit-plan`. Calidad de requisitos |
| `/speckit-analyze` | Opcional, **antes** de `/speckit-implement`. Coherencia spec/plan/tasks |
| `/speckit-implement` | Ejecuta `tasks.md`. Único punto donde se escribe código de producto |
| `/speckit-constitution` | Enmendar las normas del proyecto |

---

## Comandos

Java **no está en el `PATH`**; usa el JBR que trae Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

| Tarea | Comando |
|---|---|
| Compilar debug | `./gradlew :app:assembleDebug` |
| Tests unitarios + integración | `./gradlew :app:testDebugUnitTest` |
| Tests de UI (requiere emulador) | `./gradlew :app:connectedDebugAndroidTest` |
| Lint | `./gradlew :app:lintDebug` |
| Un solo test | `./gradlew :app:testDebugUnitTest --tests "*HomeViewModelTest*"` |
| Instalar en dispositivo | `./gradlew :app:installDebug` |
| Limpiar | `./gradlew clean` |

Informes de tests: `app/build/reports/tests/testDebugUnitTest/index.html`.
Informe de lint: `app/build/reports/lint-results-debug.html`.

---

## Arquitectura

Arquitectura limpia + MVVM, módulo único `:app`, separación por paquetes bajo
`com.jrblanco.boccantabria`:

```
core/
  di/         Módulos Koin (coreModule, dataModule, domainModule, uiModule)
              y appModules, único punto de entrada del grafo
  telemetry/  Contratos AnalyticsTracker y CrashReporter + AnalyticsEvent
  ui/theme/       Sistema de diseño: Color, Type, Spacing, Shape, Elevation, Theme
  ui/component/   Componibles compartidos sin estado, incluida PublicationCard —la usan Inicio y
                  Guardados— e IllustratedMessage, del que ComingSoonMessage es un caso
  util/       DispatcherProvider, AppVersionProvider y utilidades transversales
data/
  repository/     Implementaciones de las interfaces de domain
  source/local/   Room: BocDatabase, entidades, DAOs y Converters
  source/remote/  OkHttp, el catálogo de las 19 fuentes, el analizador y el normalizador
  telemetry/      Implementaciones de Firebase. ÚNICO sitio que toca el SDK
domain/
  model/          Modelos de dominio, Kotlin puro (AppResult, DomainError, Publication,
                  BocSection, HomeSelection, SyncSummary…)
  repository/     Interfaces de repositorio (contratos)
  usecase/        Casos de uso, una operación por clase
ui/
  splash/         Arranque: SplashScreen + SplashViewModel + SplashUiState
  main/           MainShell: panel lateral + barra inferior alrededor del NavHost interno
  home/           Inicio: HomeScreen + HomeViewModel + HomeUiState + component/
  sections/       Panel lateral de secciones del BOC
  detail/         Detalle de la publicación + component/ (cabecera, pestañas, ficha)
  pdf/            Visor del documento. ÚNICO sitio que toca androidx.pdf
  share/          ShareState y el envío por FileProvider, común a las tres pantallas
  ask/            Preguntar sobre el documento. Marcador de posición «Próximamente»
  search/         Marcador de posición «Próximamente»
  saved/          Guardados: la lista de lo que la persona ha marcado
  navigation/     Rutas tipadas, NavHost exterior y barra inferior
BOCantabriaApp    Application: arranca Koin
MainActivity      Anfitrión de la navegación Compose
```

**Regla de dependencias**: `ui → domain ← data`. Siempre hacia dentro.

- `domain` es Kotlin puro: **cero** `import android.*`, cero Compose, cero Firebase, cero
  referencias a `data` o `ui`.
- `data` implementa lo que `domain` declara. Los DTOs/entidades de `data` **no** cruzan a
  `ui`: se mapean a modelos de `domain`.
- `ui` solo habla con casos de uso. Un `ViewModel` **nunca** importa nada de `data`.
- `core` es transversal y no contiene lógica de negocio.

### Flujo de una operación

```
Composable → ViewModel → UseCase → Repository (interfaz en domain)
                                        ↓
                          RepositoryImpl (data) → DataSource
```

---

## Convenciones

### Presentación (MVVM)

- Una pantalla = `XxxScreen.kt` + `XxxViewModel.kt` + `XxxUiState.kt`.
- `MutableStateFlow` **privado**; se expone `StateFlow<XxxUiState>` de solo lectura.
- `UiState` inmutable (`data class` o `sealed interface`). Eventos = funciones públicas del
  `ViewModel`.
- Composables tontos: renderizan estado y emiten eventos. Cero lógica de negocio en un
  `@Composable`.
- Componentes reutilizables **stateless**, con *state hoisting*.

### Inyección de dependencias

- Todo el grafo en `core/di`. Nunca instanciar dependencias a mano.
- `ViewModel`s vía `koinViewModel()` en Compose.
- Al añadir una dependencia, actualiza su módulo Koin **y** el test de verificación del grafo.

### Corrutinas

- Los `Dispatchers` se **inyectan** (`DispatcherProvider`), nunca se referencian
  estáticamente. Es lo que hace deterministas los tests.
- **Una única excepción, documentada en el código**: `BOCantabriaNavHost` fija la navegación desde
  la portada con `Dispatchers.Main.immediate`. Navegar mueve el ciclo de vida de las entradas de la
  pila, y eso solo es legal en el hilo principal. En un dispositivo siempre lo es; bajo el entorno
  de pruebas de Compose la misma continuación puede reanudarse en el hilo que bombea los
  fotogramas, y entonces lanza. No es lógica de negocio: es un requisito de plataforma de la
  llamada, e inyectarlo solo movería la constante de sitio.

### Sistema de diseño

- **La aplicación tiene un único tema, el claro.** No responde al ajuste claro/oscuro del sistema.
  `BOCantabriaTheme` **no tiene** parámetro `darkTheme` ni de color dinámico, y no debe tenerlo: los
  mecanismos están eliminados, no puestos a un valor seguro. Una regla de Konsist falla la build si
  alguien importa `isSystemInDarkTheme`, `darkColorScheme` o los esquemas dinámicos. El apartado 5
  del documento de diseño está marcado como superado.
- **Las barras del sistema** llevan apariencia clara fijada en `MainActivity`, para que los iconos
  sean oscuros aunque el móvil esté en tema oscuro. La portada azul los invierte mientras está
  visible y los devuelve a oscuros al salir.
- **Nunca escribas un color, un tamaño o un espaciado literal.** Los tokens con equivalente en
  Material 3 se consumen por `MaterialTheme`; los propios (`textMuted`, `surfaceSoft`, `aiAccent`,
  los de sección…), por `BocTheme.colors`. También `BocTheme.spacing` y `BocTheme.elevation`.
- Hay una regla de Konsist que **falla la build** si un fichero fuera de `core/ui/theme` importa
  `androidx.compose.ui.graphics.Color`.
- El azul institucional no cambia entre pantallas ni entre dispositivos.
- El único color declarado en XML es el fondo del arranque del sistema, en `colors.xml`, porque se
  configura antes de que Compose exista. Debe mantenerse sincronizado con `BocPrimary`.
- Los pesos 650 del documento se implementan como `SemiBold` (600), el peso real más cercano.

### Resultados y errores

- Las operaciones de dominio devuelven `AppResult<T>` (`Success` / `Failure`), **no**
  `kotlin.Result`: el error viaja como `DomainError` sellado, así el `when` de la pantalla es
  exhaustivo y el compilador avisa al añadir un caso.
- Una lista vacía es `Success(emptyList())`, no un fallo. «Vacío» y «error» se distinguen en la
  capa de presentación.
- Las excepciones **no** salen de `data`: se capturan y se traducen ahí. `CancellationException`
  se repropaga siempre.

### Capa de datos

- **Persistencia: Room.** `BocDatabase` es la única fuente de verdad de lo que la pantalla
  muestra. La pantalla observa la base de datos; la sincronización solo escribe.
- **Red: OkHttp a secas**, sin Retrofit. Diecinueve GET de XML crudo, máximo cuatro simultáneos.
- **El XML se analiza con DOM de `javax.xml.parsers`**, no con `XmlPullParser`, para que el
  analizador sea Kotlin puro y sus pruebas corran sin emulador. Va endurecido en dos capas: una
  guarda de texto contra `<!DOCTYPE` y `<!ENTITY` —portátil— y el endurecimiento de la fábrica,
  cada bandera dentro de un `runCatching`, porque la JVM y Android no aceptan las mismas.
- **Nunca se borra una publicación.** Ningún DAO del proyecto declara una sentencia de borrado, y eso
  es deliberado: una fuente solo publica sus últimos cien anuncios. Si aparece un `@Query` de borrado
  en una revisión, hay que rechazarlo. **Desmarcar tampoco borra**: es un `UPDATE ... SET saved_at =
  NULL`, así que la regla se cumple literalmente y no reinterpretada.
- **La marca de guardado es una columna `saved_at` nullable de la tabla `publications`**, y una
  sincronización **no puede pisarla**. No porque nadie la llame: porque `PublicationDao.updateColumns`
  es una lista blanca de columnas y `saved_at` no está en ella, igual que `first_seen_at`. Si alguien
  la añade a ese `UPDATE`, la prueba de regresión de `SavedPublicationDaoTest` se pone roja, que es
  exactamente para lo que está. La escriben solo `SavedPublicationDao` y su repositorio.
- **La base de datos está en la versión 2**, con `AutoMigration(1, 2)` contra el esquema exportado.
  `bocDatabase()` es un `.build()` limpio a propósito: las migraciones automáticas no necesitan
  `addMigrations`, y `fallbackToDestructiveMigration()` no entra aquí ni como último recurso —pasaría
  la puerta de compilación y vaciaría el boletín de quien ya tiene la aplicación instalada—. Los
  esquemas de `app/schemas/` **se versionan**: son el material de la migración siguiente.
- **La sección la manda la fuente**, no el campo `categorias`, que se guarda en crudo y solo sirve
  para enriquecer y verificar. Razón: el feed 4.3 trae entradas con los componentes permutados.
- `java.time` es **nativo**: desde la enmienda 1.1.0 de la constitución `minSdk` es 28. El azucarado
  de la biblioteca estándar que lo cubría con `minSdk 24` se retiró en la feature 004 porque dejó de
  hacer falta; si algún día vuelve a necesitarse, hay que decir para qué.

### Dependencias Gradle

- **Todas** en `gradle/libs.versions.toml`. Nunca una coordenada o versión literal dentro de
  un `build.gradle.kts`.
- Familias con BOM (Compose, Firebase, Koin, OkHttp): sus artefactos van **sin versión**.
- La versión de KSP lleva el Kotlin del proyecto como prefijo (`2.2.10-2.0.2`): plugin y compilador
  van atados. Al subir Kotlin hay que subir KSP en el mismo cambio.
- `gradle.properties` lleva `android.disallowKotlinSourceSets=false`. AGP 9 prohíbe que un plugin
  añada fuentes por `kotlin.sourceSets` y KSP hace justo eso con sus directorios generados; es la
  vía que AGP documenta. Cuando KSP migre a `android.sourceSets`, la bandera sobra.

### El documento oficial

- **`ui/pdf` es la única frontera con `androidx.pdf`.** La biblioteca está en **beta** y su API
  puede cambiar: fuera de ese paquete nadie la nombra. `PdfDocumentLoader` es el seam —abrir un
  fichero y dibujar su primera página— y devuelve tipos de Compose, no de la biblioteca.
  `PdfViewer` y `rememberPdfViewerState` exigen `@OptIn(ExperimentalPdfApi::class)`.
- El visor **renderiza en otro proceso** (`SandboxedPdfLoader`). No es un detalle: los documentos
  vienen de un servicio público por internet y uno malformado no debe poder tumbar la aplicación.
  El `PdfDocument` es `Closeable` y lo cierra el `ViewModel` en `onCleared()`; olvidarlo mantiene
  vivo ese proceso reteniendo un fichero que la caché quiere poder liberar.
- `pdf-compose` arrastra `pdf-document-service` solo en ámbito de ejecución: hay que declararlo
  **explícitamente** en el catálogo, o `SandboxedPdfLoader` no resuelve.
- El PDF se guarda en `cacheDir/documents/`, con **caché y no almacén**. La purga corre al terminar
  una sincronización.
- **Guardados existe desde la feature 005, pero solo marca: no conserva el documento.** Esta guía
  prometía que guardar para leer sin conexión sería la funcionalidad de Guardados, y esa mitad queda
  **aplazada** por decisión del propietario, no olvidada: el requisito FR-024 de
  `specs/005-publicaciones-guardadas/spec.md` lo dice en voz alta. Consecuencia aceptada: el documento
  de una publicación guardada puede retirarse de la caché y volver a descargarse al abrirla. Cuando
  llegue la feature de lectura sin conexión, el asiento ya está hecho: `DocumentCache.evict` recibe un
  conjunto `inUse`, y el de claves guardadas es lo que hay que pasarle.
- Compartir un fichero exige una `content://`: hay un `FileProvider` con autoridad
  `${applicationId}.documents`, acotado en `res/xml/file_paths.xml` a `cache-path documents/`.
  Nunca amplíes ese ámbito para resolver un caso concreto.
- Nada que venga de la red se da por bueno sin validar: HTTPS, host del boletín, tipo de contenido,
  bytes mágicos `%PDF-`, tope de tamaño y SHA-256. Una página de error con HTTP 200 no puede acabar
  guardada como documento oficial.

### Firebase

- Los SDK solo se tocan desde `data`. Nunca desde `ui`, `domain` ni un `ViewModel`.
- Se usan a través de abstracciones propias (`AnalyticsTracker`, `CrashReporter`) inyectadas
  por Koin, para poder sustituirlas por dobles en test.
- Nunca registres datos personales identificables en eventos ni en trazas.

### Nombres e idioma

- Código, nombres y comentarios en **inglés**.
- Specs, documentación, mensajes de commit y comunicación con el propietario, en **español**.
- Interfaz `XxxRepository` en `domain`, implementación `XxxRepositoryImpl` en `data`.
- Casos de uso en imperativo: `GetBulletinsUseCase`, con un único `operator fun invoke()`.

---

## Testing

Ninguna tarea se da por terminada sin su test en verde. **Prohibido** `@Ignore`, comentar o
borrar un test para que pase la build.

| Tipo | Ubicación | Herramientas |
|---|---|---|
| Unitario | `app/src/test` | JUnit 4, MockK, `kotlinx-coroutines-test`, Turbine |
| Integración | `app/src/test` | Koin `verify()`/`checkModules()`, grafo real con dobles en la frontera |
| UI | `app/src/androidTest` | `createAndroidComposeRule`, Koin con módulos de test |
| Sin emulador con contexto Android | `app/src/test` | Robolectric |

- ViewModels: `runTest` + Turbine observando el `StateFlow`.
- Todo bug corregido lleva un test de regresión que falla **antes** del arreglo.
- Tests deterministas: sin red real, sin reloj del sistema, sin depender del orden.

**Reglas de arquitectura** (`ArchitectureRulesTest`, Konsist): **ocho** reglas —el texto decía seis
y quedó desfasado cuando la feature 002 añadió las dos del tema—. Hacen cumplir la separación de
capas, que solo `data` toque Firebase, que solo `core/ui/theme` importe `Color`, que nada dependa
del tema del sistema, y que toda clase de dominio de nivel superior y todo `ViewModel` tenga su
fichero de prueba. Si añades una clase de dominio sin test, la build falla.

**Trampas conocidas al escribir tests** — estas costaron tiempo, no las repitas:

- Los tests instrumentados **comparten proceso** y el grafo es de `single`. Una caché o un
  repositorio ya resuelto se filtran de una prueba a la siguiente. Usa `testGraphOverrides()`
  (androidTest), que reconstruye la cadena entera por prueba.
- Declarar un módulo dos veces dentro del mismo `koinApplication { }` **no** sustituye nada.
  Para sustituir, `koin.loadModules(listOf(...), allowOverride = true)`.
- `ActivityScenario.recreate()` **conserva el ViewModel** por diseño. No sirve para forzar una
  recarga; para eso, haz que el origen falle desde el arranque.
- En Robolectric usa `@Config(application = Application::class)`: la `BOCantabriaApp` real
  arranca el Koin global, que sobrevive entre tests del mismo JVM y hace fallar al segundo.
- Robolectric aún no tiene descriptor para la API 37: los tests usan `@Config(sdk = [36])`.
- `unloadKoinModules` **elimina** las definiciones, no restaura las que tapaba. `KoinOverrideRule`
  recarga `appModules` al terminar; si escribes otra regla que cargue módulos, haz lo mismo o
  dejarás agujeros en el grafo para las clases de prueba siguientes.
- Toda pantalla queda detrás del arranque, así que una prueba instrumentada de cualquier pantalla
  pasa por él. Usa `testGraphOverrides()`, que ya sustituye la cadena de arranque por dobles y
  mantiene la prueba fuera de la red.
- Firebase (Analytics, Crashlytics, Remote Config) necesita un `FirebaseApp` real: bajo Robolectric
  hay que sustituirlo por dobles.
- Un `Image` con solo `height` ajusta al ancho intrínseco del vector y la altura pedida no se
  aplica. Fija también `aspectRatio`.
- El splash del sistema recorta el icono a un círculo: el escudo debe ir inscrito en la zona segura
  (192 dp dentro de un lienzo de 288). Para eso existe `ic_splash_emblem`.
- **MockWebServer habla HTTP y `BocFeedDefinition` exige HTTPS.** Relajar la invariante para que
  encajara la prueba sería comprobar algo que la aplicación no hace, así que el servidor de pruebas
  sirve TLS con `okhttp-tls` y un certificado que el cliente de la prueba confía.
- **Comparar un `Long?` con `assertEquals` y un literal sin `L` nunca coincide**: el literal se
  autoboxea a `Integer` y `assertEquals(Object, Object)` falla. Con tipos no nulos no pasa, porque
  ahí resuelve la sobrecarga primitiva.
- **El conjunto básico de iconos de Material no está en el classpath** con este BOM: no existe
  `androidx.compose.material.icons`. Los diecinueve iconos son vectores propios en `RES/drawable`,
  con el trazado tomado de Material Symbols sin modificar. El `android:fillColor` de un vector es
  un marcador de posición que Compose tiñe en el punto de uso; no cuenta como color literal.
- **`ksp { }` es una extensión de proyecto, no de `android { }`.** Ponerla dentro no compila.
- **Atravesar el arranque en una prueba era intermitente, y está arreglado en el origen.** La
  portada navega desde un `LaunchedEffect`; mientras la prueba bombea fotogramas desde su propio
  hilo ese efecto puede reanudarse fuera del principal, y `navigate` toca `Lifecycle`, que lo
  exige: `IllegalStateException: Method setCurrentState must be called on the main thread`. La
  navegación va ahora fijada al hilo principal en `BOCantabriaNavHost`. Aun así, **si lo que se
  comprueba no es la portada, monta el componible que interesa con `createComposeRule()`**: ahorra
  el mínimo de 1,2 s por prueba y evita depender de una pantalla que no es la del caso.
- **Una animación infinita impide que la composición llegue a reposo.** El esqueleto de carga pulsa
  sin fin por diseño, así que `assertIsDisplayed()` —que espera reposo— se **cuelga** en lugar de
  fallar. Se conduce el reloj a mano: `composeRule.mainClock.autoAdvance = false` y
  `advanceTimeByFrame()`.

- **`setContent` solo se llama una vez por prueba.** `createAndroidComposeRule<MainActivity>()`
  lanza la actividad real, que ya pone su contenido: llamar a `composeRule.setContent` encima
  lanza `IllegalStateException`. Si necesitas montar tú la composición —para inyectar un
  `NavHostController` o forzar una configuración— usa `createComposeRule()`, que arranca una
  actividad en blanco. Y si una prueba necesita capturar dos escenarios, hazlo dentro de **una
  sola** llamada a `setContent`.
- **El gesto de Atrás no es comprobable de forma fiable en una tanda larga.** Se intentaron tres
  mecanismos y los tres fallaron por razones distintas: `onBackPressedDispatcher.onBackPressed()`
  solo ejecuta las devoluciones registradas y con retroceso predictivo quien cierra la actividad es
  la plataforma; `Espresso.pressBackUnconditionally()` exige foco de ventana que no siempre llega; y
  la acción global del sistema tampoco alcanzó la app dentro de la suite. Lo que esta aplicación
  controla es la **pila de retroceso**, así que es eso lo que se afirma (`SplashBackStackTest`); el
  cierre efectivo es comportamiento de Android y se comprueba a mano según `quickstart.md`.

- **Un `Card` con `onClick` no traga los toques de sus botones internos.** Se comprueba a
  propósito (`PublicationCardTest`): compartir desde la tarjeta no debe además abrir la
  publicación.
- **El estado del visor no es `rememberSaveable`.** La página visible se guarda a mano y se
  restaura con `scrollToPage()`; rotar el móvil y aterrizar en la página uno de un boletín de
  cuarenta deshace el trabajo de quien lee.
- **Un `Scaffold` con `bottomBar` descarta su margen de ventana inferior.** En cuanto hay barra
  inferior, Material sustituye ese margen por la **altura medida** de la barra y la ancla al borde
  crudo de la ventana: poner `contentWindowInsets` no cambia nada. La barra es la única que puede
  mantenerse por encima de los tres botones del sistema, y lo hace aplicando
  `windowInsetsPadding(systemBars.only(Horizontal + Bottom))` **dentro** de su `Surface`, que es
  justo lo que hace `NavigationBar`. Por eso la barra inferior del boletín nunca se solapó y la de
  acciones del detalle sí (`DetailActionBarInsetTest`).
- **Esa prueba solo muerde con navegación de tres botones.** Con gestos el margen puede ser cero.
  `adb shell settings put secure navigation_mode 0` antes de la tanda instrumentada.
- **Una pestaña guardada se restaura por nombre, nunca con `valueOf`.** `Preguntar` fue pestaña y hoy
  es pantalla; un nombre guardado que ya no existe tumbaría el detalle al volver de la muerte del
  proceso, en el único camino que nadie recorre a mano.
- **`onCleared()` es `protected`.** Para comprobar que el visor cierra el documento, la prueba lo
  invoca por reflexión sobre la superclase; en producción quien lo llama es el framework.

**Intermitencia conocida** — `SplashRestorationTest` falló una vez en cinco ejecuciones con
`Activity never becomes requested state "[DESTROYED]"`. Es un tiempo de espera agotado dentro de
`recreate()`, no la aserción de la prueba, y solo ocurrió en una tanda completa de 13 minutos; en
aislamiento y en una segunda tanda completa pasa. No hay causa raíz identificada: apunta a
saturación del emulador, agravada porque ahora toda prueba instrumentada atraviesa el mínimo de
1,2 s del arranque. Queda anotado a propósito en lugar de inventar un arreglo sin diagnóstico. Si
vuelve a fallar, hay que investigarlo de verdad: un test intermitente incumple el principio V.

---

## Git

- `main` es la rama estable. Cada feature vive en su rama `NNN-slug` creada por Spec Kit.
  **Nunca** implementes una feature directamente sobre `main`.
- Commits en español, imperativo, con prefijo Conventional Commits (`feat:`, `fix:`, `test:`,
  `refactor:`, `chore:`, `docs:`).
- Remoto: `https://github.com/jrcosio/BOCantabria_v2.git`.

Antes de dar una feature por terminada, en este orden:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

---

## Notas del proyecto

- **Versión mínima soportada**: `minSdk 28` desde la enmienda 1.1.0 de la constitución
  (30 de agosto de 2026). Subió de 24 porque el visor de PDF oficial de Jetpack para Compose
  —`androidx.pdf:pdf-compose`, el que permite leer el documento dentro de la aplicación sin
  Fragments— lo exige. Deja fuera Android 7 y 8. El motivo completo y las alternativas descartadas
  están en el Sync Impact Report de `.specify/memory/constitution.md`.
- **Orientación**: la aplicación está **bloqueada en vertical** por decisión de producto. En
  pantallas de 600 dp o más Android ignora la restricción desde la API 36 y no se intentará
  sortearlo. Los dos avisos de lint correspondientes están suprimidos a conciencia en el manifest.
- **Documentación de diseño**: `docs/diseno/` contiene las especificaciones visuales y la imagen de
  referencia del arranque. Es la fuente de verdad de la interfaz; si cambias algo acordado, actualiza
  también el documento.
- **Package**: `com.jrblanco.boccantabria` (con doble «c»). Es intencionado: el
  `google-services.json` está registrado con ese package exacto en el proyecto Firebase
  `bocantabria-6e90f`. **No lo renombres** sin registrar antes una app nueva en la consola de
  Firebase.
- **Fuentes del BOC**: `Datos_modelo/BOC_Cantabria_Consumo_Feeds_RSS.md` es la fuente de verdad del
  formato. Las muestras de prueba en `app/src/test/resources/fixtures/` se tomaron del servicio real
  e incluyen las anomalías que importan: el feed 4.3 con las categorías permutadas y el 8.1 vacío.
  Si el servicio cambia de forma, se actualizan las muestras y las pruebas lo dicen.
- `Datos_modelo/` contiene material de referencia y **no se versiona**.
- AGP 9.x aplica Kotlin de forma integrada: no existe ni hace falta el plugin `kotlin-android`.
