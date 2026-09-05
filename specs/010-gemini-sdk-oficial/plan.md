# Implementation Plan: El documento se envía entero, no su texto

**Branch**: `010-gemini-sdk-oficial` | **Date**: 5 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-gemini-sdk-oficial/spec.md`

## Summary

El Resumen IA deja de leer el PDF en el móvil. La cadena
`extraer → limpiar → renderizar con marcas de página → enviar texto` se retira entera, y en su lugar
el documento oficial **se sube al servicio** por la Files API y se referencia en la petición. El
cliente HTTP escrito a mano se sustituye por la librería oficial de Kotlin de Google,
`com.google.genai:google-genai-kotlin:1.0.0`, publicada hace tres días.

**Cinco cosas definen este plan, y las cinco son elecciones.**

La primera: **entra una dependencia, y con ella su cola**. Es lo contrario de la 009, que se
enorgullecía de no añadir ninguna. La decisión D-102 de aquella feature descartó los SDK por tres
razones y hoy solo una sigue en pie; la que la desbloquea es concreta y comprobable:
`HttpOptions(baseUrl = …)` permite seguir probando contra MockWebServer, así que la frontera de
prueba no se pierde (D-201). Lo que se gana a cambio es la **Files API**, que es el cimiento de la
feature 011: sin ella, cada pregunta del chat reenviaría el boletín entero.

La segunda: **se borra más de lo que se escribe**. Desaparecen `AndroidxPdfTextExtractor`,
`PdfTextExtractor`, `PdfTextNormalizer`, `DocumentText`, `GeminiDtos`, `PdfCorpus` y
`OkHttpGeminiSummaryDataSource`, con sus pruebas. Lo único que sobrevive de la extracción es un
contador de páginas de treinta líneas, y sobrevive por dos motivos que se pagan solos: sin el número
real de páginas, el validador no puede descartar una cita a una página que no existe; y sin abrir el
documento en local, `EncryptedPdf` deja de detectarse antes de gastar cuota (D-205).

La tercera: **el esquema no se toca**. `responseJsonSchema` acepta un `JsonElement`, así que
`SummarySchema.value` viaja tal cual. Reescribirlo con los tipos de la librería habría significado
traducir a mano doce propiedades cuyo **orden es carga útil** —la lección de `plainLanguageSummary` la
última— a cambio de nada (D-211). `SummaryPayloadDtos` tampoco cambia ni un nombre: es lo que se
persiste en `summary_json`.

La cuarta: **el documento preparado tiene dueño y tiene final**. Un `single` guarda como mucho una
sesión; la abre quien la necesite primero, y la cierra el modelo de pantalla del detalle en
`onCleared()`, donde el `viewModelScope` ya está cancelado y por eso hace falta un ámbito propio
(D-207, D-208). La caducidad del servicio es red de seguridad, no mecanismo.

La quinta, y la que no tiene nada que ver con inteligencia artificial: **por primera vez se compila
la versión que se distribuye**. La cola de dependencias obliga a activar la optimización, y activarla
sin ejecutar el resultado sería declarar verde algo que nadie ha visto correr. De ahí una quinta
puerta de calidad, manual, en el `quickstart.md` (D-222).

**Lo que este plan no hace**: no construye la pantalla Preguntar —solo le deja el asiento hecho—, no
cambia la forma ni el contenido del resumen, no toca la base de datos, no toca el visor ni el resto
de pantallas, y no cambia el diseño de la tarjeta.

## Technical Context

**Language/Version**: Kotlin 2.2.10, AGP 9.3.2, KSP 2.2.10-2.0.2. **Java sube de 11 a 17** en origen,
destino y `jvmTarget`, porque el bytecode de la librería es *major 61* (D-219). Entorno local: JBR de
Android Studio; CI: Temurin 21. Ninguno de los dos necesita cambio.

**Primary Dependencies**: **una nueva y su cola.** `com.google.genai:google-genai-kotlin:1.0.0`,
declarada en `gradle/libs.versions.toml` como manda la constitución. Arrastra, sin posibilidad de
excluirla (D-220), `com.google.auth:google-auth-library-oauth2-http:1.33.0` —y con ella Guava,
`google-http-client`, `gson`, `slf4j-api`— más Ktor 2.3.8 (`client-core`, `client-okhttp`,
`client-websockets`). Todo lo demás se queda como está: Room 2.8.4, Koin por BOM 4.2.2, Compose por
BOM 2026.02.01, `androidx.pdf` 1.0.0-beta01, OkHttp por BOM 5.5.0 —que **se conserva**: sigue
descargando los feeds y el documento—, `kotlinx-serialization-json` 1.8.1, corrutinas 1.11.0.

**Storage**: Room **se queda en la versión 4**. Ninguna migración, ningún esquema exportado nuevo,
ninguna columna nueva. `ai_summaries` ya guarda la procedencia en columnas agnósticas
—`model_id`, `prompt_version`, `schema_version`— y es exactamente lo que hace que este cambio no la
toque. Sigue sin haber ninguna sentencia de borrado en ninguno de los cinco DAO. Lo único que cambia
en almacenamiento local es la **clave** de la preferencia del aviso, que se versiona de `_v2` a `_v3`
para que el aviso reescrito se lea una vez (FR-033). El documento preparado en el servicio **no se
persiste en ninguna parte**: vive en memoria y muere con la visita.

**Network**: dos operaciones contra el servicio, ambas por la librería. Una subida de fichero
(`files.upload` con el PDF y `application/pdf`, seguida del sondeo `PROCESSING → ACTIVE` con tope de
espera) y una generación (`models.generateContent` con un `Content` de rol `user` que lleva una parte
de fichero y una de texto). Configuración: instrucción de sistema, `thinkingLevel` mínimo,
`maxOutputTokens` 8.000, `responseMimeType` `application/json` y `responseJsonSchema` con el esquema
que ya existe. Credencial en el constructor del cliente, nunca en el cuerpo ni en la URL. Sin
respuesta progresiva. **Ningún interceptor de registro a nivel de cuerpo, en ningún cliente.** El
`OkHttpClient` compartido del proyecto sigue existiendo y sigue descargando el PDF; lo que deja de
usar es el camino de inteligencia artificial.

**Testing**: sin herramientas nuevas. JUnit 4.13.2, MockK 1.14.11, Turbine 1.2.1,
`kotlinx-coroutines-test` 1.11.0, Robolectric 4.16.1 con `@Config(sdk = [36])`, `koin-test`,
MockWebServer, Compose UI Test y Konsist 0.17.3. **La frontera de prueba sigue siendo HTTP**, apuntada
con `HttpOptions(baseUrl = …)`; lo que cambia es que estas pruebas hablan HTTP en claro y no TLS, y el
motivo está razonado en D-224. `okhttp-tls` se conserva: lo siguen usando las pruebas de los feeds.

**Target Platform**: Android, `minSdk 28`, `compileSdk` y `targetSdk` 37. Vertical fijo, tema claro
único. La librería declara `minSdk 21`, así que no fuerza nada.

**Project Type**: aplicación Android nativa, módulo único `:app`, arquitectura limpia + MVVM.

**Performance Goals**: un resumen ya guardado sigue mostrándose en menos de un segundo y sin red
(SC-002). La preparación del documento —subida más sondeo— no debería pasar de diez segundos en red
normal para un boletín ordinario; la generación, de veinte. **Regenerar dentro de la misma visita
ahorra la preparación entera** (SC-005), que es la única mejora de tiempo que esta feature promete.

**Constraints**: el tope de tamaño deja de ser una constante nuestra y pasa a ser el que ya aplica el
descargador, **25 MB**, muy por debajo de lo que el servicio admite. Límites de uso: 30 peticiones por
minuto y 1.500 por día, **valores documentados pendientes de confirmar**, contados por la propia
aplicación porque el proveedor no los informa. Cero consultas al servicio sin que alguien pulse el
botón. Ni la credencial ni el contenido del documento pueden aparecer en registros. **Como mucho un
documento preparado a la vez** en todo el proceso.

**Scale/Scope**: 7 ficheros de producción borrados, 5 nuevos, 10 modificados; 5 ficheros de prueba
borrados, 3 nuevos, 9 modificados; 3 ficheros de build modificados y 1 nuevo. Ninguna pantalla nueva,
ningún componible nuevo. Es una feature de sustitución con una capacidad nueva dentro
—preparar un documento— que no se ve pero que sostiene la siguiente.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluado contra `.specify/memory/constitution.md` **versión 1.1.0**. La constitución deja
explícitamente abierta la elección de cliente de red —«la elección de cliente HTTP y de persistencia
queda deliberadamente abierta y DEBE decidirse y justificarse en el `plan.md` de la primera feature
que la necesite»—, así que adoptar una librería **no requiere enmienda**; requiere justificarla, y
está en D-201.

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | El cambio toca código de producto en las tres capas, así que no cae en la exención de «arreglos de build, subidas de versión, erratas y documentación» —que la propia constitución acota diciendo que «cubre configuración; NO cubre código de producto que se cuele bajo esa etiqueta»—. La subida a Java 17 y la activación de la optimización **sí** serían exentas por separado, pero se hacen dentro porque sin ellas la feature no compila ni se empaqueta. Ciclo completo: `spec.md` con 43 requisitos y su lista de calidad en 16 de 16, este `plan.md`, `research.md` con 26 decisiones, `data-model.md`, `contracts/` y `quickstart.md`; ninguna línea de producto hasta que `/speckit-tasks` produzca `tasks.md`. Rama `010-gemini-sdk-oficial` creada por la extensión git | ✅ |
| **II. Arquitectura limpia** | La librería vive **solo en `data/source/remote/`**, y eso deja de ser una promesa para pasar a ser una regla comprobada: la novena regla de Konsist falla la build si `com.google.genai` se importa fuera de `data` (D-225). De `domain` cambian tres cosas y ninguna importa nada: dos literales de `AiSummaryConstants`, un caso de `AiSummaryError`, un valor de un `enum` anidado, más un caso de uso nuevo. `PdfCorpus` se va de `domain`, que queda más pequeño. `ui` no conoce ni un tipo nuevo | ✅ |
| **III. MVVM** | Los diez componibles de la pestaña siguen recibiendo `AiSummaryStatus` y devolviendo eventos. `PublicationDetailViewModel` gana **una** llamada en `onCleared()` y ningún trozo de orquestación: quién sube el documento, cuándo se reutiliza y cuándo se retira es del repositorio y del almacén de sesión, no de la pantalla. El componible sigue siendo tonto | ✅ |
| **IV. Koin** | Cinco declaraciones nuevas y dos que desaparecen en `DataModule`; un `factory` nuevo en `DomainModule`. Ninguna instanciación a mano: el `Client` de la librería se construye dentro de una función fábrica en `data/source/remote/`, nunca en el módulo, igual que se hace con Room, OkHttp y `androidx.pdf`. `KoinModulesTest` se actualiza **en sus dos listas**, `CROSS_MODULE_TYPES` y la resolución explícita, que es lo que el principio exige cuando el grafo cambia | ✅ |
| **V. Testing exigente** | Ninguna prueba se desactiva, se comenta ni se borra para poner la build en verde. Las cinco que se borran lo hacen porque **desaparece el código que probaban**, no porque estorben; las nueve cuya frontera cambia se reescriben. Los dos comportamientos que esta feature podría romper en silencio llevan prueba propia: que regenerar no vuelve a subir, y que salir retira. Y se reconoce por escrito que la frontera con el servicio hay que atravesarla de verdad, ahora por partida doble | ⚠️ ver abajo |
| **VI. Observabilidad desacoplada** | El registro sigue pasando por `CrashReporter` inyectado y gana una fase —`upload:`— que hoy no existe porque hoy no hay subida. Ningún SDK de Firebase se nombra fuera de `data`. Nunca la credencial, nunca el contenido del documento; de una respuesta se registra su forma y sus tamaños. La analítica pierde los parámetros que dejan de tener sentido (`pages_analyzed`, `partial`) en vez de mantenerlos mintiendo | ✅ |
| **Restricciones tecnológicas** | La dependencia nueva se declara en `gradle/libs.versions.toml` con su `[versions]` y su `[libraries]`; ni una coordenada literal en un `build.gradle.kts`. Compose y Material 3 sin XML ni Fragments; Koin; corrutinas y `Flow`. Código y comentarios en inglés, documentación y commits en español | ✅ |
| **Flujo y puertas de calidad** | Trabajo en la rama, nunca en `main`. Commits en español con prefijo convencional. Las cuatro puertas en orden, **más una quinta** | ⚠️ ver abajo |

**Las dos advertencias, dichas enteras.**

**Principio V.** FR-042 pide una comprobación **manual** de la versión optimizada, y el principio V
exige pruebas automáticas. No es una excepción cómoda: es que ninguna prueba de este proyecto se
ejecuta sobre el artefacto optimizado —`testDebugUnitTest` y `connectedDebugAndroidTest` corren sobre
`debug`, que no se optimiza—, así que no existe la prueba automática que se estaría saltando.
Automatizarla querría decir montar una variante instrumentada de release firmada, que es una feature
en sí misma. Lo que se hace en su lugar es dejarlo escrito como puerta obligatoria y con su lista de
pantallas, en el `quickstart.md`. Es el mismo razonamiento del §3 bis de la 009, que la constitución
ya toleró por el mismo motivo.

**Puertas de calidad.** Se añade una quinta, `:app:assembleRelease` más instalación y recorrido. No
sustituye a ninguna de las cuatro; se ejecuta después.

**Resultado de la puerta previa a la fase 0**: pasa, con las dos advertencias registradas en
*Complexity Tracking*.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño detallado no introdujo desviación
nueva, y los tres puntos que merecían una segunda mirada se resolvieron a favor de la norma:

- **La extracción de texto se borra en vez de quedarse desactivada.** Dejarla «por si acaso» habría
  significado mantener cinco clases y sus pruebas para un camino que ya nadie recorre, y el principio
  V prohíbe precisamente el código de prueba que no prueba nada.
- **Pero el contador de páginas se queda**, y no por nostalgia: sin él, `SummaryValidator` tendría que
  fiarse del recuento que declara el modelo, y todo el fichero existe justamente para no fiarse
  (D-205).
- **El almacén de sesión no se convirtió en un mapa** aunque era la tentación obvia. Con «como mucho
  una sesión», FR-010 es comprobable; con un mapa, sería una intención.

## Project Structure

### Documentation (this feature)

```text
specs/010-gemini-sdk-oficial/
├── spec.md                     # Qué y por qué. 43 requisitos, 12 criterios de éxito
├── plan.md                     # Este fichero
├── research.md                 # Fase 0. 26 decisiones, D-201 a D-226
├── data-model.md               # Fase 1. Qué cambia de forma y qué no
├── quickstart.md               # Fase 1. Cómo se valida, con las cinco puertas y el §3 bis
├── contracts/
│   └── internal-contracts.md   # Fase 1. Las fronteras internas que cambian
├── checklists/
│   └── requirements.md         # Calidad de la especificación. 16 de 16
└── tasks.md                    # Fase 2. Lo crea /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── core/di/
│   └── DataModule.kt                                MODIFICADO  −2 declaraciones, +4
│   └── DomainModule.kt                              MODIFICADO  +1 factory
├── domain/
│   ├── model/
│   │   ├── AiSummaryConstants.kt                    MODIFICADO  MODEL_ID y PROMPT_VERSION
│   │   ├── AiSummaryError.kt                        MODIFICADO  NoExtractableText → UnreadableDocument
│   │   ├── AiSummaryStatus.kt                       MODIFICADO  EXTRACTING_TEXT → UPLOADING_DOCUMENT
│   │   └── PdfCorpus.kt                             BORRADO     ya no hay texto extraído
│   ├── repository/AiSummaryRepository.kt            MODIFICADO  + releaseDocumentSession
│   └── usecase/
│       └── ReleaseAiDocumentSessionUseCase.kt       NUEVO
├── data/
│   ├── repository/AiSummaryRepositoryImpl.kt        MODIFICADO  la tubería, de 5 pasos a 4
│   └── source/
│       ├── local/
│       │   ├── AiPreferences.kt                     MODIFICADO  clave _v2 → _v3 (FR-033)
│       │   ├── PdfPageCounter.kt                    NUEVO       reemplaza a PdfTextExtractor
│       │   ├── AndroidxPdfPageCounter.kt            NUEVO       reemplaza a AndroidxPdfTextExtractor
│       │   ├── PdfTextExtractor.kt                  BORRADO
│       │   ├── AndroidxPdfTextExtractor.kt          BORRADO
│       │   └── PdfTextNormalizer.kt                 BORRADO
│       └── remote/
│           ├── GenAiClientProvider.kt               NUEVO       el Client, perezoso y con baseUrl
│           ├── AiDocumentUploader.kt                NUEVO       interfaz + GenAiDocumentUploader
│           ├── AiDocumentSessionStore.kt            NUEVO       como mucho una sesión viva
│           ├── GenAiSummaryDataSource.kt            NUEVO       reemplaza al de OkHttp
│           ├── GeminiSummaryDataSource.kt           MODIFICADO  la firma toma el documento subido
│           ├── SummaryValidator.kt                  MODIFICADO  toma totalPages, no RenderedDocument
│           ├── SummaryPromptFactory.kt              MODIFICADO  sin el hueco del documento
│           ├── SummarySchema.kt                     SIN CAMBIOS ni una línea (D-211)
│           ├── SummaryPayloadDtos.kt                SIN CAMBIOS ni un nombre de propiedad
│           ├── GeminiRateLimitCoordinator.kt        SIN CAMBIOS
│           ├── GeminiApiKeyProvider.kt              SIN CAMBIOS
│           ├── DocumentText.kt                      BORRADO
│           └── OkHttpGeminiSummaryDataSource.kt     BORRADO
│           └── GeminiDtos.kt                        BORRADO     la librería es dueña del cable
│
├── ui/detail/PublicationDetailViewModel.kt          MODIFICADO  onCleared() suelta la sesión
└── ui/detail/component/AiSummaryTab.kt              MODIFICADO  una línea del mapa de mensajes

app/src/main/res/values/strings.xml                  MODIFICADO  aviso, fase nueva, error renombrado
app/src/main/keepRules/genai.keep                    NUEVO       reglas de R8 (D-222)
app/build.gradle.kts                                 MODIFICADO  Java 17, packaging, optimization
gradle/libs.versions.toml                            MODIFICADO  la dependencia nueva
app/schemas/                                         SIN CAMBIOS la base sigue en la versión 4

app/src/test/java/com/jrblanco/boccantabria/
├── architecture/ArchitectureRulesTest.kt            MODIFICADO  la novena regla
├── di/KoinModulesTest.kt                            MODIFICADO  las dos listas
├── domain/model/
│   ├── AiSummaryErrorTest.kt                        MODIFICADO  el caso renombrado
│   ├── AiSummaryStatusTest.kt                       MODIFICADO  la fase renombrada
│   └── PdfCorpusTest.kt                             BORRADO
├── domain/usecase/ReleaseAiDocumentSessionUseCaseTest.kt   NUEVO   (lo exige la regla 8)
├── data/repository/AiSummaryRepositoryImplTest.kt   MODIFICADO  la tubería nueva
├── data/source/local/
│   ├── AiPreferencesTest.kt                         MODIFICADO  la clave vieja no se lee
│   └── PdfTextNormalizerTest.kt                     BORRADO
├── data/source/remote/
│   ├── GenAiSummaryDataSourceTest.kt                NUEVO       21 pruebas, MockWebServer en claro
│   ├── AiDocumentSessionStoreTest.kt                NUEVO       reutiliza, releva y retira
│   ├── SummaryValidatorTest.kt                      MODIFICADO  firma
│   ├── SummaryPromptFactoryTest.kt                  MODIFICADO  firma y el documento adjunto
│   ├── SummarySchemaTest.kt                         SIN CAMBIOS sigue vigilando el orden
│   ├── DocumentTextTest.kt                          BORRADO
│   └── OkHttpGeminiSummaryDataSourceTest.kt         BORRADO
├── integration/AiSummaryFlowIntegrationTest.kt      MODIFICADO  la cadena nueva
├── fake/FakeGeminiSummaryDataSource.kt              MODIFICADO  la firma nueva
├── fake/FakeAiDocumentUploader.kt                   NUEVO
└── ui/detail/AiErrorMessagesTest.kt                 MODIFICADO  el mensaje renombrado

app/src/androidTest/java/com/jrblanco/boccantabria/
├── data/source/local/AndroidxPdfTextExtractorTest.kt  BORRADO
├── data/source/local/AndroidxPdfPageCounterTest.kt    NUEVO     cuenta y detecta protegido
└── ui/detail/AiSummaryTabTest.kt                      MODIFICADO  dos literales

CLAUDE.md                                            MODIFICADO  tubería, reglas (8→9), quinta puerta
```

**Structure Decision**: no cambia. Módulo único `:app` con separación por paquetes y cada pieza en la
capa que le corresponde por lo que **es**. Dos criterios gobiernan dónde va lo nuevo:

- **La extracción de texto era una fuente de datos, y contar páginas también.** Por eso
  `AndroidxPdfPageCounter` hereda el sitio de `AndroidxPdfTextExtractor`, en `data/source/local/`, y
  no se acerca a `ui/pdf`. La regla de que `androidx.pdf` se toca en exactamente dos sitios se
  mantiene: uno dibuja, el otro cuenta.
- **Los ficheros que describen al proveedor llevan su nombre; los que describen nuestro formato, no.**
  `GenAiClientProvider`, `GenAiDocumentUploader` y `GenAiSummaryDataSource` hablan de la librería;
  `SummarySchema`, `SummaryPayloadDtos`, `SummaryPromptFactory`, `SummaryValidator` y
  `AiDocumentSessionStore` hablan del BOC y sobrevivirán al próximo cambio sin que nadie los toque.
  Es la misma norma que fijó la 009, y esta feature es la segunda vez que se cobra.

## Complexity Tracking

> La puerta constitucional pasa con dos advertencias. Se registran aquí, con las cuatro decisiones que
> un revisor podría discutir. Cada una remite a `research.md`.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| **Entra una dependencia que arrastra Guava** (D-201, D-220) | Es la única vía a la Files API que no obliga a escribir y mantener el protocolo de subida reanudable, y sin Files API la feature 011 no sale a cuenta. `GoogleCredentials` está en la firma del constructor del cliente, así que la cola no se puede podar | Escribir la Files API a mano sobre el OkHttp que ya está: cero dependencias, cero Java 17, cero R8. Se descartó porque el coste de mantener ~150 líneas de protocolo ajeno es permanente. **Queda como plan B explícito si D-223 sale mal** |
| **Se activa la optimización de release** (D-222) | Sin ella la cola de la dependencia entra entera en el APK distribuido. Es la contrapartida directa de la decisión anterior, no un extra | Dejarla desactivada y aceptar el peso: es lo que hay hoy, y sería tolerable si la dependencia fuera pequeña. Con Guava dentro deja de serlo |
| **Una puerta de calidad manual** (FR-042) | Ninguna prueba del proyecto se ejecuta sobre el artefacto optimizado, así que activarlo sin ejecutarlo es declarar verde algo que nadie ha visto correr | Automatizarla: exige una variante instrumentada de release firmada, que es una feature en sí misma. Se descarta por desproporción, no por comodidad |
| **Se conserva un contador de páginas local** (D-205) | Sin el número real de páginas, el validador tendría que creerse el que declara el modelo —y existe para no creérselo—, y una cita a una página inexistente sería un enlace roto. Además mantiene vivo `EncryptedPdf` sin gastar cuota | Borrar toda la lectura local del PDF: más simple, y deja dos agujeros comprobables. Treinta líneas no son precio para eso |
