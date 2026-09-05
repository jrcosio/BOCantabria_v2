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
                  Guardados—, IllustratedMessage, del que ComingSoonMessage es un caso, y AiNoticeSheet,
                  que desde la feature 011 abren dos pantallas y una sola aceptación cubre las dos
  util/       DispatcherProvider, AppVersionProvider, SearchText —la normalización de texto que
              usan las tres capas— y demás utilidades transversales
data/
  repository/     Implementaciones de las interfaces de domain
  source/local/   Room: BocDatabase, entidades, DAOs y Converters
  source/remote/  OkHttp, el catálogo de las 19 fuentes, el analizador, el normalizador, y desde la
                  feature 010 la Files API del servicio de IA —subida reanudable escrita a mano— y el
                  almacén de la sesión del documento. Desde la 011, AiDocumentPreparer: los cuatro
                  pasos que resumen y conversación comparten, y el ÚNICO sitio donde se decide que
                  las páginas se cuentan antes de subir
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
  info/           Acerca de: InfoScreen + InfoViewModel + InfoUiState; enlaces HTTPS delegados al sistema
  sections/       Panel lateral de secciones del BOC
  detail/         Detalle de la publicación + component/ (cabecera, pestañas, ficha)
  pdf/            Visor del documento. ÚNICO sitio que toca androidx.pdf
  share/          ShareState y el envío por FileProvider, común a las tres pantallas
  ask/            Preguntar: la conversación sobre el documento. AskRoute + AskScreen + AskViewModel
                  + AskUiState + component/. Se apila ENCIMA del detalle
  search/         Buscar: la búsqueda global sobre todo lo almacenado, con filtros y orden
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
- **El texto de la búsqueda se normaliza al escribir, no al consultar.** `LIKE` de SQLite solo pliega
  mayúsculas para ASCII y **nunca** pliega tildes, y Android no trae la colación de ICU que lo
  arreglaría. Así que cada publicación guarda una columna `search_text` —título, organismo, jerarquía,
  referencia y **nombre** de sección y subsección, en minúsculas y sin tildes— y la consulta se
  normaliza igual antes de compararse. `core/util/SearchText` es el único sitio que decide qué
  significa normalizar: si cambia, lo ya escrito deja de concordar con lo que se busca y hay que
  reconstruir la columna entera.
- **`search_text` sí entra en la lista blanca de `PublicationDao.updateColumns`**, y conviene decirlo
  alto porque es justo el `UPDATE` que esta guía protege. Es un dato **derivado de la fuente**: si la
  fuente corrige un título, el texto buscable tiene que corregirse con él. `saved_at` y
  `first_seen_at` siguen fuera, y `SavedPublicationDaoTest` es la prueba que lo vigila.
- **Una columna nueva deja sin rellenar las filas anteriores, y eso no se ve en una instalación
  limpia.** Tras migrar, todo lo ya almacenado queda con `search_text` vacío, y una sincronización
  solo refresca los últimos cien anuncios de cada fuente: sin relleno, el archivo anterior sería
  inbuscable para siempre, y solo en el móvil de quien ya tenía la aplicación. `refresh()` rellena por
  lotes usando `search_text = ''` como marcador —`buildSearchText` nunca devuelve vacío, porque el
  título nunca está en blanco—, de modo que el estado vive en la propia columna y no hay bandera que
  guardar.
- **`PublicationSearchDao` es de solo lectura.** La línea es: `PublicationDao` escribe todo lo que se
  deriva de la fuente, incluido el relleno; `SavedPublicationDao` escribe lo de la persona; y el de
  búsqueda solo lee. Lleva **dos** sentencias, una por sentido de ordenación, porque Room no
  parametriza la dirección de un `ORDER BY`.
- **La base de datos está en la versión 4**, con `AutoMigration(1, 2)`, `(2, 3)` y `(3, 4)` contra los
  esquemas exportados. Las anteriores se conservan: quien se salte dos versiones tiene que poder llegar
  de la 1 a la 4 de una vez. La 3→4 añade la tabla `ai_summaries`, y a diferencia de la 3 **no tiene
  relleno**: una tabla nueva nace vacía y no tener resumen es el estado normal de una publicación.
  `bocDatabase()` es un `.build()` limpio a propósito: las migraciones automáticas no necesitan
  `addMigrations`, y `fallbackToDestructiveMigration()` no entra aquí ni como último recurso —pasaría
  la puerta de compilación y vaciaría el boletín de quien ya tiene la aplicación instalada—. Los
  esquemas de `app/schemas/` **se versionan**: son el material de la migración siguiente.
- **Sigue sin haber ninguna sentencia de borrado en el proyecto**, en ninguno de los **cinco** DAO.
  `AiSummaryDao` solo lee y hace `upsert`: regenerar un resumen sustituye la fila, no la borra y la
  vuelve a insertar, porque entre las dos operaciones no habría resumen ninguno.
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

- **`androidx.pdf` se toca en exactamente dos sitios**, y desde la feature 007 ya no en uno solo:
  `ui/pdf` para **dibujar** el documento y `data/source/local/AndroidxPdfPageCounter` para **contar sus
  páginas**. Hasta la feature 010 el segundo extraía el texto entero; ahora el documento se envía al
  servicio y lo único que hace falta saber en el dispositivo es cuántas páginas tiene —para descartar
  una cita a una página que no existe— y si está protegido con contraseña. Es una fuente de datos, no
  presentación; ponerlo en `ui` obligaría al modelo de pantalla a orquestar la tubería entera y eso
  incumple el principio III. La interfaz
  `PdfDocumentLoader` **se queda en `ui/pdf`**: moverla a `data` rompería la regla Konsist «ui no
  depende de data», porque el visor y la vista previa la importan. La biblioteca está en **beta** y su API
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
- **Buscar existe desde la feature 006 y son dos búsquedas, no una.** La lupa de la barra superior de
  Inicio filtra **en memoria** lo que la pantalla ya tiene, sin tocar el almacén ni la red; la pestaña
  Buscar consulta todo lo almacenado, con filtros y orden. Lo único que comparten es
  `core/util/SearchText`. Entre las dos hay un puente: sin coincidencias en la edición, se ofrece la
  misma consulta en el buscador global, que la recibe por el argumento de `Route.Search`.
- **Acerca de existe desde la feature 008 y es un destino exterior.** `Route.Info` queda fuera de
  `MainShell`, no muestra navegación inferior y vuelve con Atrás. Sus URL públicas se abren con el
  `UriHandler` de Compose: Android decide entre la aplicación asociada y el navegador. Solo se
  registra el destino enumerado (`linkedin` o `github`), nunca la URL ni datos personales.
- **Resumen IA existe desde la feature 007, y su regla número uno es que no se genera solo.** Solo al
  pulsar el botón: la cuota del servicio es gratuita, compartida por toda la organización y diaria, y
  resumir lo que nadie ha pedido la vaciaría en una tarde. Y la advertencia «Comprueba siempre el texto
  oficial» va **dentro** del texto al copiar o compartir, porque fuera de la aplicación el resumen
  pierde la tarjeta, el icono y la pantalla que lo enmarcaba.
- **Desde la feature 010 no se envía el texto del documento: se envía el documento.** La cadena
  `extraer → limpiar → renderizar con marcas de página` se retiró entera, y con ella
  `AndroidxPdfTextExtractor`, `PdfTextNormalizer`, `DocumentText` y `PdfCorpus`. El PDF se sube a la
  **Files API** con el protocolo de subida reanudable, y la petición lleva una referencia. Dos
  consecuencias que conviene tener presentes: **un PDF escaneado ya se resume** —era imposible antes, y
  el invariante «un documento sin texto utilizable no llega nunca al servicio» queda **superado, no
  incumplido**: existía porque lo que viajaba era el texto—; y el juicio sobre si el documento sirve lo
  hace ahora el servicio, así que un documento ilegible cuesta una petición.
- **El documento subido tiene dueño y tiene final.** `AiDocumentSessionStore` mantiene **como mucho
  uno** vivo en todo el proceso: se sube en la primera acción de IA de la visita, se reutiliza mientras
  se esté en esa publicación —regenerar no vuelve a subir— y se borra en `onCleared()` del modelo de
  pantalla del detalle. Ese punto no es casual: Preguntar y el visor se apilan **encima** del detalle,
  así que su entrada sigue viva mientras se usan. Y `release()` **no es una función suspendida** a
  propósito: en `onCleared()` el `viewModelScope` ya está cancelado y lanzar el borrado ahí no borraría
  nada, así que el almacén tiene un ámbito propio. Si el proceso muere sin borrar, el servicio caduca el
  fichero por su cuenta; eso es la red de seguridad, no el mecanismo.
- **Preguntar existe desde la feature 011, y la promesa de que solo se hable del documento es un
  mecanismo y no una esperanza.** Hay cinco capas —instrucción de sistema, pregunta delimitada,
  documento declarado como datos, ámbito declarado en la respuesta, e higiene de longitud y blancos—,
  y **solo la cuarta se puede comprobar con una prueba automática**: las otras viven al otro lado de la
  frontera con el servicio, que todas las pruebas de esta casa doblan. Lo que sí es demostrable es que
  **cuando la respuesta se declara fuera de ámbito, lo que se pinta es texto nuestro y ni un carácter
  del suyo**. Esa sustitución es **una línea de `AiChatRepositoryImpl`**, y está ahí y no en la pantalla
  para que ninguna pantalla futura pueda saltársela por descuido. La batería de intentos contra el
  servicio real —siete preguntas y un PDF con una instrucción inyectada dentro— es obligatoria y vive en
  `specs/011-preguntar-al-boc/quickstart.md` §3 bis.
- **En el esquema del chat, `scope` va PRIMERO por la misma razón por la que el resumen va el último.**
  El orden de las propiedades es el orden de generación, y lo declarado después del campo largo se
  vacía si la generación se corta. En el resumen eso vaciaba una tarjeta; aquí dejaría el ámbito en
  blanco, que es la defensa caída sin hacer ruido. Lo vigila `ChatAnswerSchemaTest`. Un `scope`
  desconocido o ausente se trata como **fuera de ámbito**: ante la duda, texto nuestro.
- **La conversación vive en memoria, como mucho una, y la petición no corre en el ámbito de la
  pantalla.** `AiChatRepositoryImpl` tiene un `SupervisorJob` propio, y eso es deliberado: **cancelar no
  devuelve la cuota** —se cuenta al pedir—, así que salir a mitad costaría lo mismo que terminar y
  encima perdería la respuesta. Dejándola correr, quien vuelve se la encuentra hecha, y el requisito de
  que salir no sea un fallo se cumple sin escribir nada porque no hay cancelación que reportar. Lo único
  que cancela de verdad es salir de la publicación. **Y son dos limpiezas en `onCleared()` del detalle,
  no una**: el documento lo suelta un caso de uso y la conversación otro, porque son dos repositorios y
  uno solo escondería que hay dos dueños.
- **`AiDocumentPreparer` es el único sitio donde se prepara el documento, y por eso se tocó código de la
  010.** Encierra copia local → cuenta de páginas → apertura de sesión, con el invariante dentro: **se
  cuentan las páginas antes de subir**, que es lo que mantiene un PDF protegido dentro del dispositivo.
  Copiar esos treinta líneas en el chat habría copiado el invariante, y un invariante duplicado se
  cumple hasta que alguien arregla una de las dos copias.
- **El chat usa `AiSummaryConstants.MODEL_ID` a propósito.** Dos constantes que tienen que valer lo
  mismo son dos constantes que un día valdrán cosas distintas, y la escapatoria ante una caída de
  capacidad tiene que seguir siendo **una línea**. Las otras dos —versión de prompt y de esquema— siguen
  siendo solo del resumen, porque son la procedencia de una fila almacenada y el chat no almacena nada.
- **Preguntar y resumir se estorban, y es correcto.** `serialised { }` mantiene **una** petición a la
  vez en toda la aplicación, así que pedir un resumen mientras hay una pregunta en el aire pone a la
  segunda a esperar. La cuota es del plan, no de la funcionalidad. Se anota porque en el chat la espera
  se nota más y se diagnostica mal como cuelgue.
- **La librería oficial de Kotlin de Google NO se puede usar en esta aplicación, y conviene saberlo
  antes de volver a intentarlo.** `com.google.genai:google-genai-kotlin` llegó a 1.0.0 el 2 de
  septiembre de 2026 y la feature 010 la adoptó: catálogo, Java 17, exclusiones de empaquetado, tres
  clases escritas contra ella y el APK compilando. El primer test que construyó el cliente reveló que
  su artefacto de Android lleva un guardián que **lanza siempre**:
  `IllegalStateException: SECURITY FATAL: Initializing the Client with an API Key or Credentials on
  Android is blocked to prevent credential leaks`. No es el aviso del README —eso ya se había leído y
  asumido—: es un `throw` incondicional en `androidMain/SecurityContext.kt`. Sin un servidor propio
  detrás, en Android no hay forma de construir el cliente. Se retiró entera y la Files API se escribió
  a mano sobre OkHttp. **Forzar el artefacto `-jvm` para saltarse el guardián se descartó a
  conciencia**: es rodear un control de seguridad del proveedor sobre una variante no compilada para
  Android. La vía correcta el día que haya backend, o el día que Firebase AI Logic exponga ficheros,
  es Firebase AI Logic — hoy no tiene Files API y por eso no vale.
- **Ya no hay presupuesto de tokens, y por qué había uno es la mitad de la historia de esta
  funcionalidad.** Hasta la feature 009 el proveedor daba 8.000 tokens por minuto compartidos, y de ahí
  salía todo: 4.500 de documento contra 1.800 de respuesta, un troceado que elegía qué páginas caben, y
  tres defectos —JSON cortado, resúmenes en blanco, reintentos que chocaban con la cuota del mismo
  minuto—. Ese techo cobraba `entrada + max_completion_tokens` **al pedir**, se gastara o no; su propio
  429 lo decía: «Limit 8000, Used 7346, Requested 6475». Con **1.048.576 tokens de entrada** cualquier
  publicación del BOC entra completa, así que se retiraron `SummaryBudget`, la estimación a 3,2
  caracteres por token y el reparto entero. Queda **un solo tope duro**: `DocumentText.MAX_CHARACTERS =
  480_000`, unas ciento noventa páginas, que en uso normal no se alcanza nunca y solo existe para que
  una publicación patológica no tire la petición.
- **El techo de salida sube a 8.000 y ahora eso es gratis, porque el proveedor nuevo cobra la salida
  usada y no la reservada.** Es al revés que el anterior, y es lo que cierra para siempre la familia de
  fallos en la que el JSON llegaba cortado, no parseaba, y el lector leía «no se ha podido construir un
  resumen fiable» — un problema nuestro disfrazado de fallo del servicio. No se pone en 65.536 a
  propósito: si una respuesta llegara a tocar 8.000, es que algo va mal en el prompt y conviene que se
  note.
- **Los dos valores por defecto del servicio nuevo que hay que apagar a mano, y ninguno se ve.**
  `thinking_level` vale `medium` y el razonamiento **se factura**: es exactamente la lección de
  `reasoning_effort` con otro nombre. Y `store` vale `true`: el servicio conserva la interacción, un día
  en cuenta gratuita. Los dos se envían explícitamente, y los dos **dependen de `encodeDefaults = true`**
  porque coinciden con su valor por defecto de Kotlin. Sin esa bandera no viajan y el proveedor aplica
  los suyos, que son los contrarios.
- **Gemini no manda cabeceras de cuota**, y el anterior sí. `GeminiRateLimitCoordinator` lleva la cuenta
  él mismo con dos ventanas deslizantes en memoria —sesenta segundos y veinticuatro horas—, la diaria
  deslizante y no de calendario porque el proveedor repone en su zona horaria y no en la del móvil. **No
  se persiste, a conciencia**: mil quinientas peticiones al día son una cada cincuenta y siete segundos
  durante veinticuatro horas, y eso no se alcanza pulsando un botón. Y un 429 se clasifica por **el
  retraso que pide**, no por el texto que trae: el texto cambia, está en inglés, y FR-027 prohíbe
  mostrarlo. **Las dos cifras del plan gratuito están pendientes de confirmar en el panel del
  proveedor**, que es donde Google las publica ahora.
- **Cuando un prompt enumera qué rellenar, lo que no está en la lista es lo que se pierde.** La versión
  v2 decía que un análisis parcial «no exime de rellenar **los campos estructurados**». El modelo
  obedeció al pie de la letra: rellenó los estructurados y dejó **el resumen** en blanco, con
  `finish_reason=stop` —no se quedó sin sitio, terminó por su cuenta—. Desde v3, `plainLanguageSummary`
  se declara obligatorio siempre, y lo que falte va en `coverage` y `warnings`, nunca en un campo vacío.
- **El saneado del texto de pdfium se fue con la extracción, y el problema que resolvía también.** Un
  sustituto UTF-16 sin pareja producía UTF-8 inválido en el cuerpo JSON y el servicio rechazaba la
  petición entera con un 400, siempre para el mismo documento. Desde la feature 010 no viaja texto
  nuestro en el cuerpo, sino un fichero binario y unos metadatos que salen de la base de datos, así que
  el defecto **deja de ser posible por construcción**. Se anota porque si algún día vuelve a enviarse
  texto extraído, vuelve con él.
- **Un arreglo que convierte un error en otro es peor que no arreglar nada.** El reintento automático de
  un resumen vacío salía disparado, chocaba con la cuota del mismo minuto y el lector acababa leyendo
  «se ha alcanzado el límite». Ahora se consulta al coordinador antes de reintentar y, si no hay margen,
  se devuelve el rechazo original.
- **Buscar la credencial en el repositorio da un acierto que NO es una fuga.** `app/google-services.json`
  lleva un `current_key` que empieza por `AIza` y está versionado a propósito desde el commit base: es
  la clave de Android de Firebase, restringida en la consola por paquete y huella de firma, y el
  fichero tiene que estar ahí para que la build funcione. La de Gemini son 53 caracteres empezando por
  `AQ.A`; la de Firebase, 39 empezando por `AIza`. La comprobación va con
  `':!app/google-services.json'`, porque una comprobación que falla siempre es una comprobación que se
  deja de mirar.
- **La credencial del servicio de IA se lee de `local.properties` y se expone por `BuildConfig`.** Con
  API de proveedor de Gradle (`providers.fileContents`), no con `File.readText`: la caché de
  configuración está activada y leer un fichero a pelo en tiempo de configuración es una entrada no
  declarada. **Si la clave falta, la build sigue en verde** y el valor es cadena vacía, que la pantalla
  traduce en «no configurado»; es lo que permite compilar y pasar las pruebas sin secretos. Se asume, con
  conocimiento del propietario, que una credencial dentro de un APK distribuido es recuperable. Nunca en
  Logcat, ni en Crashlytics, ni en analítica: ni la clave ni el contenido del documento. **Nunca un
  interceptor de registro a nivel de cuerpo en el cliente de IA.**
- **El identificador del modelo vive en `AiSummaryConstants` y el acceso va detrás de
  `GeminiSummaryDataSource`, y la feature 009 es la factura que lo comprobó.** Cambiar de proveedor
  —de Groq con Qwen a `gemini-3.5-flash-lite`— costó `data/source/remote/` más tres constantes de
  dominio: ni el dominio del resumen, ni `ui/`, ni `strings.xml`, ni la base de datos, ni las veintiuna
  pruebas instrumentadas de la pestaña se tocaron. Esas tres constantes —modelo, versión de prompt,
  versión de esquema— se guardan con cada resumen; si alguna deja de coincidir, lo guardado queda
  **obsoleto, no borrado**, y al cambiar las tres a la vez **todo lo guardado quedó obsoleto por
  diseño**. Los ficheros que describen al proveedor llevan su nombre (`GeminiDtos`,
  `OkHttpGeminiSummaryDataSource`); los que describen nuestro formato, no (`SummarySchema`,
  `SummaryPayloadDtos`, `SummaryValidator`, `DocumentText`). **`SummaryPayload` es lo que se serializa
  en `summary_json`, así que ni un nombre de propiedad puede cambiar**: hay prueba de regresión.
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

**Reglas de arquitectura** (`ArchitectureRulesTest`, Konsist): **nueve** reglas —eran seis, la
feature 002 añadió las dos del tema y la 010 la novena—. Hacen cumplir la separación de capas, que
solo `data` toque Firebase, que **solo `data` nombre los tipos del servicio de IA**
(`com.google.genai`), que solo `core/ui/theme` importe `Color`, que nada dependa del tema del
sistema, y que toda clase de dominio de nivel superior y todo `ViewModel` tenga su fichero de prueba.
Si añades una clase de dominio sin test, la build falla.

> **Lo que las pruebas de esta casa no pueden ver, y cómo se ve.** Los dos defectos que de verdad
> rompían el Resumen IA en un móvil —el modelo dejando el resumen vacío, y el techo de salida cortando
> el JSON— **no los podía encontrar ninguna prueba automática**, porque todas usan dobles en la frontera
> con el servicio y el defecto estaba justo al otro lado. Los encontró el registro en un dispositivo
> real, y solo después de instrumentar seis `catch` que tragaban en silencio. La conclusión no es
> escribir menos pruebas: es que **una frontera con un servicio ajeno hay que atravesarla de verdad al
> menos una vez**, y dejar registrado lo suficiente para saber qué pasó cuando falle. Está en
> `quickstart.md` §3 bis y en `research.md` D-034.

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
- **El repositorio de Material Symbols mezcla dos convenciones de lienzo, y copiar la equivocada no
  falla: simplemente no dibuja nada.** La mayoría de los símbolos vienen con
  `viewBox="0 -960 960 960"` y coordenadas negativas, que es lo que espera el envoltorio con
  `viewportWidth="960"` y el grupo trasladado. Pero otros —`auto_awesome`, por ejemplo— siguen llegando
  en escala 24 y **sin `viewBox`**. `ic_ai` estuvo desde la feature 004 con un trazado de 24 dentro de
  la plantilla de 960: se dibujaba en una esquina diminuta y el traslado lo mandaba fuera del lienzo.
  **No se vio nunca**, en ninguno de sus cuatro usos, y nada falló. Antes de usar el envoltorio de 960,
  comprueba que el trazado lleve coordenadas negativas.
- **El conjunto básico de iconos de Material no está en el classpath** con este BOM: no existe
  `androidx.compose.material.icons`. Los cuarenta iconos son vectores propios en `RES/drawable`,
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
- **Con más de un dispositivo conectado, la tanda instrumentada se reparte entre todos.** Si hay un
  móvil enchufado además del emulador, Gradle ejecuta las pruebas también allí, y si tiene la
  pantalla bloqueada fallan en bloque con `No compose hierarchies found in the app`: la actividad no
  llega a lanzarse. No es un fallo del código. O se desconecta, o se deja desbloqueado, o se fija el
  destino con `ANDROID_SERIAL=emulator-5554`.
- **`padding(innerPadding)` reserva el sitio, pero no consume el margen de ventana**, y son dos cosas
  distintas. `MainShell` aplica con `padding` el alto de la barra inferior —que ya incluye el margen
  del sistema, porque `NavigationBar` se lo aplica por dentro—, pero el `Scaffold` de cada destino,
  que no lleva barra inferior y toma los `systemBars` de por defecto, lo vuelve a aplicar por su
  cuenta. El resultado era una franja muerta del alto exacto de la barra de navegación entre la lista
  y la barra inferior, **en los tres destinos**. Se arregla con `consumeWindowInsets(innerPadding)`
  junto al `padding`, y en un solo sitio: quien aplica el espacio es quien debe declararlo servido.
  Lo fija `MainShellBottomInsetTest`, que sin el arreglo mide 63 px de franja y 63 px de margen del
  sistema —el mismo número, que es la firma del problema—.
- **Esa prueba solo muerde con navegación de tres botones.** Con gestos el margen puede ser cero.
  `adb shell settings put secure navigation_mode 0` antes de la tanda instrumentada.
- **La tanda instrumentada completa tarda casi tres horas, no trece minutos.** Medido el 4 de
  septiembre de 2026: **154 pruebas en 161 minutos**, mediana de 46,4 s por prueba, y medido otra vez
  ese mismo día tras la feature 009: **154 pruebas en 116 minutos**, 45,3 s por prueba. El coste por
  prueba es el mismo; lo que varía es la sobrecarga de la tanda. La cifra importa
  porque quien espere trece minutos dará por colgado algo que va bien; lánzala en segundo plano.
  Lo llamativo no es que sea lenta, es que el coste es un **suelo fijo y no depende de lo que la
  prueba haga**: las dos clases que no montan Compose —`BocRssParserDeviceTest` y
  `AndroidxPdfTextExtractorTest`— tardan de 0,0 a 0,2 s, y las veintinueve que sí montan Compose dan
  46,2 s de mediana **todas**. `MainShellBottomInsetTest` mide un margen y tarda 46,3 s;
  `PageChipsTest` dibuja dos chips y tarda 46,2 s; `AiSummaryTabTest`, que monta la pestaña entera
  veintiuna veces, 46,2 s también. **No son las animaciones**: con las tres escalas a cero la misma
  clase da 45,5 s. **Ni el tamaño de la suite**: una tanda de 24 pruebas da la misma media. No hay
  causa raíz identificada —el emulador es una imagen con Google Play en API 37 y entre prueba y
  prueba se ve arrancar Finsky, GMS y Docs, pero eso es una sospecha, no un diagnóstico—. Queda
  anotado a propósito, como la intermitencia del final: inventar una explicación sería peor que
  reconocer que falta.
- **`--tests` no existe en `connectedDebugAndroidTest`.** Falla con `Unknown command-line option`.
  Para ejecutar una sola clase o un solo método:
  `-Pandroid.testInstrumentationRunnerArguments.class=<paquete>.<Clase>` y, opcionalmente,
  `#<metodo>`. Es la diferencia entre iterar en un minuto o en tres horas.
- **Una pestaña guardada se restaura por nombre, nunca con `valueOf`.** `Preguntar` fue pestaña y hoy
  es pantalla; un nombre guardado que ya no existe tumbaría el detalle al volver de la muerte del
  proceso, en el único camino que nadie recorre a mano.
- **Sembrar cientos de filas en un test de Robolectric tumba MockK, y el fallo aparece en clases que
  no tienen nada que ver.** Una prueba que insertaba seiscientas publicaciones dejaba al JVM de
  pruebas sin responder a la señal de adjunción del agente de ByteBuddy; MockK agotaba su espera de
  diez segundos y **todas** las clases que usan un doble caían con `Could not initialize class
  io.mockk.impl.JvmMockKGateway`. Diagnosticado en la feature 006 y arreglado en el origen: el tamaño
  de lote del relleno se inyecta, y la prueba demuestra que el bucle da otra vuelta con un lote de
  dos, no con un archivo entero. **Si hace falta comprobar volumen, se comprueba a mano.**
- **Tocar un destino de la barra inferior y teclear a continuación es una carrera, y solo se ve con
  la suite llena.** Una prueba de la feature 006 escribía en el campo de Buscar justo después de
  pulsar su pestaña; el texto entraba en una composición que la navegación aún estaba descartando, el
  modelo de pantalla no llegaba a verlo, y la espera de resultados se agotaba **sin error**. Aislada
  pasaba siempre; en la tanda completa caía una de las dos pruebas de la clase, y no siempre la
  misma. El arreglo es afirmar que la pantalla está montada **antes** de interactuar
  —`onNodeWithTag(TAG_SEARCH_SCREEN).assertIsDisplayed()`— y comprobar después que el texto entró de
  verdad. Subir el tiempo de espera **no** lo arregla: se probó con 45 segundos y falló igual.
- **Un `waitUntil` que se agota no dice nada.** Cuando una espera pueda fallar por más de un motivo,
  hay que envolverla y afirmar cuál fue —pantalla no montada, texto no introducido, o consulta que
  devolvió cero—. Esa distinción es la que convirtió el fallo anterior de misterio en hecho, y es
  barata: se escribe una vez y sirve para siempre.
- **`LIKE` de SQLite no ignora las tildes.** Por eso la normalización se hace al escribir, en la
  columna `search_text`, y no en la consulta. Y por eso `%` y `_` hay que escaparlos con
  `ESCAPE '\'`: sin ello, buscar `100%` devuelve el archivo entero, y no falla —miente—.
- **Navegar con `restoreState` se traga el argumento de la ruta.** La barra inferior navega con
  `popUpTo(start) { saveState = true }` y `restoreState = true`, así que el estado guardado de una
  pestaña gana al argumento con el que se navega. El puente de Inicio a Buscar navega **sin**
  restauración por eso; con ella, el término traspasado se perdía sin error, llegando a Buscar con el
  campo vacío. Lo fija `SearchHandoffTest`.
- **En una respuesta con esquema estricto, el orden de las propiedades es el orden de generación.** No
  es cosmético: con `plainLanguageSummary` en cuarta posición, la prosa se cortaba en 1024 caracteres y
  **todo lo declarado después venía vacío** —una convocatoria con plazos e importes salía en blanco—.
  Va la última, acotada con `maxLength`. El orden de la pantalla es otro y no depende de este: la
  tarjeta sigue mostrando la prosa arriba. Si alguien ordena esas propiedades alfabéticamente, la ficha
  se vacía otra vez; lo vigila `SummarySchemaTest`.
- **Un `catch` que no escribe nada convierte un fallo en un misterio.** Los tres sitios que abren un PDF
  se tragaban la excepción sin dejar rastro, así que un proceso aislado muerto y un fichero ilegible se
  veían igual: nada en pantalla y nada en el registro. Ahora informan por `CrashReporter.log`, y
  `FirebaseCrashReporter` **también escribe en logcat cuando `BuildConfig.DEBUG`**, con etiqueta `BOC`.
  Nunca el contenido del documento ni la clave: solo el tipo de fallo y de dónde viene.
- **Cancelar una corrutina NO interrumpe un `Call.execute()` de OkHttp con una
  `CancellationException`: le rompe el socket, y lo que sale es una `IOException`.** Visto en un móvil:
  alguien pulsó Atrás mientras se generaba un resumen y el registro dijo `gemini: network:
  SocketException: Software caused connection abort` y `summary failed: Offline`. No había ningún
  problema de conexión —la persona se fue—, y el `catch (CancellationException)` de al lado **no llega a
  dispararse nunca**. Peor: `fail()` publica ese estado, y en `observeSummary` el estado en curso
  **gana** al resumen almacenado, así que al volver se lee «No hay conexión» de un fallo que no existió.
  El arreglo es `currentCoroutineContext().ensureActive()` como **primera** línea del `catch (IOException)`;
  `generate()` ya sabía qué hacer con una cancelación (FR-006), lo que faltaba era que llegara hasta allí.
  Cualquier llamada bloqueante dentro de una corrutina tiene este agujero.
- **`gemini-3.5-flash-lite` tuvo una caída de capacidad sostenida el 4 de septiembre de 2026, y el
  modelo hermano no.** Medido, no supuesto: una petición **mínima** de 150 bytes sin esquema devolvía
  `HTTP 500: currently experiencing high demand` una y otra vez, mientras la misma petición a
  `gemini-3.5-flash` daba **HTTP 200 en 3,2 s** con la misma clave y desde la misma máquina. Eso
  descarta el tamaño de la petición, el esquema, la credencial y la red: es capacidad del modelo. El
  mismo cuerpo había funcionado a dos segundos tres horas antes.
  **Qué hacer si vuelve**: nada, primero. El proveedor dice que los picos son temporales, en pantalla
  ya se lee «Inténtalo de nuevo» y los tres intentos con backoff cubren un pico corto. Si persiste, la
  escapatoria es **una línea** en `AiSummaryConstants.MODEL_ID` —que está ahí exactamente para esto—,
  a cambio de dejar obsoletos todos los resúmenes guardados. **No** montes una cadena de reserva entre
  modelos: el `model_id` que se guarda con cada resumen dejaría de ser determinista, y es la columna
  que decide qué está obsoleto. Y ten presente que cada 500 cuenta en el contador de cuota, porque se
  apunta al pedir: tres 500 gastan tres peticiones del cupo diario para cero resúmenes.
- **El servicio se agota por tiempo con cierta frecuencia, y el reintento salva la mayoría de las
  veces.** Medido el 4 de septiembre de 2026 en el emulador: dos generaciones de tres se llevaron un
  `InterruptedIOException: timeout` en el primer intento y salieron en el segundo; una tercera acumuló
  dos tiempos agotados y un `HTTP 500: gemini-3.5-flash-lite is currently experiencing high demand`.
  Es decir: el `MAX_ATTEMPTS = 3` con backoff **se gana el sueldo**, no es decoración. Y en pantalla
  todo eso es «No se ha podido generar el resumen» con reintento, que es lo correcto (FR-027).
- **`adb shell input text` con espacios corta en el primer espacio, y el resultado parece un defecto de
  la aplicación.** Tecleando «Que plazo hay para recurrir» en el compositor de Preguntar llegó al chat
  la palabra «Que» a secas, y el modelo respondió con toda la razón que el documento no contenía una
  pregunta clara. Se pierden diez minutos buscando el fallo en el sitio equivocado. Los espacios van
  como `%s`: `adb shell input text 'Que%splazo%shay'`.
- **Conducir la interfaz con coordenadas fijas no funciona en el detalle de publicación.** La altura de
  la cabecera cambia con la longitud del título, así que la fila de pestañas se mueve entre una
  publicación y otra: unas coordenadas que valen para un anuncio caen en «Descripción» en el siguiente.
  Si automatizas una comprobación manual con `adb shell input`, lee la pantalla con
  `uiautomator dump` **entre toque y toque**. Y cuidado con `input swipe` sobre una pantalla corta: un
  gesto sobre la zona no desplazable puede acabar pulsando el botón que haya debajo —a mí me disparó un
  «Reintentar» y me hizo creer un momento que la pantalla se había quedado colgada en «Generando…»—.
- **El paso de razonamiento de Gemini se llama `thought`, no `model_thoughts` como dice su
  documentación, y llega SIEMPRE antes que la respuesta.** Comprobado contra el servicio real: tomar
  `steps[0]` habría fallado en el cien por cien de las respuestas. `OkHttpGeminiSummaryDataSource`
  busca `model_output` **por tipo** y nunca por posición, y hay una prueba con el nombre real.
- **El proceso aislado del PDF nace y muere en cada documento, y eso es normal.** `androidx.pdf` genera
  un `Intent` con identificador único por documento, así que cada `openDocument()` arranca su propio
  proceso; al cerrar, muere. En Logcat sale como `PROCESS STARTED`/`PROCESS ENDED` **con el nombre de
  paquete de la app**, porque lo comparte. No es un cierre inesperado. Y
  `AconfigStorageReadException: android.graphics.pdf.flags` es ruido de la plataforma, capturado dentro
  de la librería.
- **`close()` de un `PdfDocument` es `@WorkerThread` y hace una llamada binder síncrona.** Cerrarlo
  desde `produceState` lo ejecutaba en el hilo principal; y cerrarlo en `onCleared()` sin capturar es un
  cierre de la aplicación en cuanto el proceso aislado ya haya muerto por su cuenta, que es lo normal.
  Va en `withContext(dispatchers.io)` y dentro de `runCatching`.
- **Una aserción sobre el prompt que dependa de dónde cae un salto de línea se rompe al reformatear,
  sin que nada esté mal.** La plantilla va envuelta a cien columnas y `trimIndent()` conserva los saltos,
  así que cualquier frase de más de unas palabras los cruza: `"resume lo que has leído"` no está en el
  mensaje, `"resume lo que\nhas leído"` sí. Se comprueba sobre el mensaje con los espacios colapsados
  —`message.replace(Regex("\\s+"), " ")`—, no sobre fragmentos elegidos para caber en una línea.
- **`trimIndent()` se aplica DESPUÉS de interpolar, y eso rompió el prompt.** Un valor multilínea que
  entra en la plantilla sin sangría arrastra el indent común a cero, así que no se recorta nada y el
  mensaje entero sale con ocho espacios en cada línea, pagados de la cuota de tokens. `SummaryPromptFactory`
  recorta primero y sustituye después, y hay una prueba que lo afirma.
- **kotlinx-serialization omite por defecto los valores iguales al default.** Sin `encodeDefaults = true`,
  `stream: false` y `reasoning_effort: "none"` no se enviaban, y el valor por defecto del proveedor para
  ese modelo es razonamiento **activo**: tokens de la misma cuota que nadie llega a ver.
- **`combine` con seis flujos cae en la sobrecarga de `vararg`**, que exige que todos tengan el mismo
  tipo y devuelve `Array<Any?>`. `PublicationDetailViewModel` agrupa lo de la persona en un tipo propio
  y se queda en cinco.
- **`onCleared()` es `protected`.** Para comprobar que el visor cierra el documento, la prueba lo
  invoca por reflexión sobre la superclase; en producción quien lo llama es el framework.

**Cómo se mira cuando el Resumen IA falla en un móvil.** La pantalla nunca dice códigos, a propósito
(FR-040), así que el registro es el único sitio donde se distingue qué pasó:

```bash
adb -s <serie> logcat -s BOC:V
```

Las líneas van en inglés y dicen la fase, el tamaño de lo enviado y el motivo exacto del fallo:

```
summary: document ready, counting pages
summary: 9 pages
upload: sending 412 KB
upload: ready after 2 poll(s)
session: released boc:439765
gemini: HTTP 400: <lo que conteste el servicio>
gemini: HTTP 429, retry in 37s
gemini: status=incomplete
gemini: no model_output, 1 step(s), status=failed
gemini: blank summary: plainLanguageSummary=0 keyPoints=6 …, status=completed, 240 output tokens
pages failed: DeadObjectException
summary failed: Unknown
```

Y desde la feature 011, la conversación, con el prefijo `chat:`. Se registran la fase, cuántos mensajes
viajan, **el ámbito que declaró la respuesta** y el motivo del fallo. El ámbito sí, y a propósito: es lo
único que permite saber sobre un móvil de verdad si la defensa está actuando, y es un enumerado de tres
valores que no puede filtrar nada. **Nunca el texto de la pregunta ni el de la respuesta.**

```
prepare: document ready, counting pages
prepare: 54 pages
chat: asking with 3 message(s)
chat: answer scope=OUT_OF_SCOPE, 0 source(s)
chat: 2 citation(s) dropped, document has 9 pages
chat: blank answer from the service
chat: HTTP 429, retry in 37s
chat: network: SocketException: Software caused connection abort
chat: discarded boc:440124
```

**Nunca la credencial ni el contenido del documento**: de una respuesta se registra su *forma* —nombres
de campo y tamaños—, y del servicio su `error.message`, que habla de nuestra petición. Cinco pruebas lo
vigilan. **Las claves de Gemini tienen dos formatos y hay que buscar los dos**: el clásico empieza por
`AIza` y el que se emite hoy, por `AQ.` —comprobado contra una clave real: 53 caracteres empezando por
`AQ.A`—. Ninguno es el `gsk_` del proveedor anterior. Buscar un solo prefijo, o el viejo, es exactamente
cómo se da por limpio un repositorio que no lo está. Y **`AiSummaryError.Unknown` cubre cuatro situaciones distintas** —documento que no se descarga,
extracción rota, código HTTP sin mejor sitio, y cualquier excepción del camino—: en pantalla son la
misma frase y en el registro no pueden serlo.

**Y la lección de la feature 010, que es la misma con otro traje.** La librería oficial se adoptó
leyendo su README y su código fuente —API verificada línea por línea, artefacto descargado, bytecode
inspeccionado— y aun así el bloqueo no salió hasta **ejecutarla**. Un `throw` en una función `actual`
de `androidMain` no se ve en la documentación, no se ve en la firma y no se ve en el POM. Lo que lo
encontró fue el primer test que construyó el objeto de verdad. Verificar la forma de una dependencia
no es lo mismo que ejecutarla.

**Intermitencia conocida, y la clase que la sufría ya no existe** — `SplashRestorationTest` falló una
vez en cinco ejecuciones con `Activity never becomes requested state "[DESTROYED]"`: un tiempo de
espera agotado dentro de `recreate()`, no la aserción de la prueba. **Esa clase no está en el
proyecto**: hoy las pruebas de arranque son `SplashBackStackTest`, `SplashNavigationTest` y
`ui/splash/SplashContentTest`, y las tres pasaron en la tanda del 4 de septiembre de 2026 tras la
feature 009 —154 pruebas, cero fallos—. Se conserva el apunte porque el mecanismo sigue siendo
posible: `recreate()` bajo saturación del emulador, agravado porque toda prueba instrumentada
atraviesa el mínimo de 1,2 s del arranque. Si reaparece en cualquier clase, hay que investigarlo de
verdad: un test intermitente incumple el principio V.

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
