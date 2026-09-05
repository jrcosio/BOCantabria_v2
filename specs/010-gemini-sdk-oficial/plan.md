# Implementation Plan: El documento se envía entero, no su texto

**Branch**: `010-gemini-sdk-oficial` | **Date**: 5 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-gemini-sdk-oficial/spec.md`

## Summary

El Resumen IA deja de leer el PDF en el móvil. La cadena
`extraer → limpiar → renderizar con marcas de página → enviar texto` se retira entera, y en su lugar
el documento oficial **se sube al servicio** por la Files API y se referencia en la petición. El
cliente HTTP escrito a mano se sustituye por la librería oficial de Kotlin de Google,
`com.google.genai:google-genai-kotlin:1.0.0`, publicada hace tres días.

> **Este resumen se escribió antes de implementar, y una de sus cinco elecciones no sobrevivió al
> primer test.** La librería oficial de Google **no se puede usar en Android**: su artefacto lanza
> una excepción en cuanto se le da una credencial. Se retiró, y con ella Java 17, las exclusiones de
> empaquetado y la activación de R8. El texto de abajo está corregido; el relato completo está en
> `research.md` D-227, y merece leerse porque explica por qué el resto del plan no se movió.

**Cuatro cosas definen este plan, y las cuatro son elecciones.**

La primera: **no entra ninguna dependencia**, y no por elegancia sino porque la que iba a entrar está
vetada por su propio autor. La Files API se escribe sobre el `OkHttpClient` que el proyecto ya tiene:
tres llamadas del protocolo de subida reanudable, unas ciento cincuenta líneas. Es exactamente el
plan B que D-201 había dejado escrito, y lo que se gana sigue siendo lo mismo: subir el documento una
vez, que es el cimiento de la feature 011 — sin ello, cada pregunta del chat reenviaría el boletín
entero (D-201, D-227).

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

**Lo que este plan no hace**: no construye la pantalla Preguntar —solo le deja el asiento hecho—, no
cambia la forma ni el contenido del resumen, no toca la base de datos, no toca el visor ni el resto
de pantallas, y no cambia el diseño de la tarjeta.

## Technical Context

**Language/Version**: Kotlin 2.2.10, **Java 11** en origen y destino, AGP 9.3.2, KSP 2.2.10-2.0.2.
Sin cambios: la subida a 17 la exigía la librería y se fue con ella (D-219, D-227).

**Primary Dependencies**: **ninguna nueva.** `gradle/libs.versions.toml` no se toca. Todo se
reutiliza en las versiones que ya hay: Room 2.8.4, Koin por BOM 4.2.2, Compose por
BOM 2026.02.01, `androidx.pdf` 1.0.0-beta01, OkHttp por BOM 5.5.0 —que **se conserva**: sigue
descargando los feeds y el documento—, `kotlinx-serialization-json` 1.8.1, corrutinas 1.11.0.

**Storage**: Room **se queda en la versión 4**. Ninguna migración, ningún esquema exportado nuevo,
ninguna columna nueva. `ai_summaries` ya guarda la procedencia en columnas agnósticas
—`model_id`, `prompt_version`, `schema_version`— y es exactamente lo que hace que este cambio no la
toque. Sigue sin haber ninguna sentencia de borrado en ninguno de los cinco DAO. Lo único que cambia
en almacenamiento local es la **clave** de la preferencia del aviso, que se versiona de `_v2` a `_v3`
para que el aviso reescrito se lea una vez (FR-033). El documento preparado en el servicio **no se
persiste en ninguna parte**: vive en memoria y muere con la visita.

**Network**: cinco llamadas contra el servicio, todas sobre el `OkHttpClient` compartido, derivado
con `newBuilder()` como hasta ahora. Tres son la subida —`POST /upload/v1beta/files` con
`X-Goog-Upload-Command: start`, `POST` a la dirección que devuelve la cabecera `x-goog-upload-url`
con los bytes, y `GET /v1beta/files/<nombre>` hasta que el estado deja de ser `PROCESSING`, con tope
de sondeos—. La cuarta es la generación, `POST /v1beta/models/<modelo>:generateContent`, con un
contenido de rol `user` que lleva una parte `file_data` y una de texto, `thinkingLevel` mínimo,
`maxOutputTokens` 8.000, `responseMimeType` `application/json` y `responseJsonSchema` con el esquema
que ya existe. La quinta es el borrado, `DELETE /v1beta/files/<nombre>`. Credencial en la cabecera
`x-goog-api-key`, nunca en el cuerpo ni en la URL. Sin respuesta progresiva. **Ningún interceptor de
registro a nivel de cuerpo, en ningún cliente.**

**Testing**: sin herramientas nuevas y sin cambios de patrón. JUnit 4.13.2, MockK 1.14.11, Turbine
1.2.1, `kotlinx-coroutines-test` 1.11.0, Robolectric 4.16.1 con `@Config(sdk = [36])`, `koin-test`,
MockWebServer **sobre TLS** con `okhttp-tls`, Compose UI Test y Konsist 0.17.3. La frontera sigue
siendo HTTP, así que las pruebas de contrato se reescriben en vez de rehacerse — que es exactamente
el argumento que la 009 dio para escribir la petición a mano, y que esta feature intentó superar y no
pudo.

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

**Scale/Scope**: 6 ficheros de producción borrados, 4 nuevos, 11 modificados; 4 ficheros de prueba
borrados, 3 nuevos, 11 modificados; **cero ficheros de build**. Ninguna pantalla nueva, ningún
componible nuevo. Es una feature de sustitución con una capacidad nueva dentro —preparar un
documento— que no se ve pero que sostiene la siguiente.

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
| **V. Testing exigente** | Ninguna prueba se desactiva, se comenta ni se borra para poner la build en verde. Las cuatro que se borran lo hacen porque **desaparece el código que probaban**, no porque estorben, y las **dos pruebas de regresión** que vivían en el fichero retirado —la cancelación mal clasificada y el reintento sin cuota— se portan antes de borrarlo, porque los dos defectos siguen siendo posibles. Los dos comportamientos que esta feature podría romper en silencio llevan prueba propia: que regenerar no vuelve a subir, y que salir retira. Y se reconoce por escrito que la frontera con el servicio hay que atravesarla de verdad, ahora por partida doble | ✅ |
| **VI. Observabilidad desacoplada** | El registro sigue pasando por `CrashReporter` inyectado y gana una fase —`upload:`— que hoy no existe porque hoy no hay subida. Ningún SDK de Firebase se nombra fuera de `data`. Nunca la credencial, nunca el contenido del documento; de una respuesta se registra su forma y sus tamaños. La analítica pierde los parámetros que dejan de tener sentido (`pages_analyzed`, `partial`) en vez de mantenerlos mintiendo | ✅ |
| **Restricciones tecnológicas** | La dependencia nueva se declara en `gradle/libs.versions.toml` con su `[versions]` y su `[libraries]`; ni una coordenada literal en un `build.gradle.kts`. Compose y Material 3 sin XML ni Fragments; Koin; corrutinas y `Flow`. Código y comentarios en inglés, documentación y commits en español | ✅ |
| **Flujo y puertas de calidad** | Trabajo en la rama, nunca en `main`. Commits en español con prefijo convencional. Las cuatro puertas de siempre, en orden | ✅ |

> **Este apartado llevaba dos advertencias y ya no lleva ninguna.** Las dos —una comprobación manual
> de la versión optimizada, y una quinta puerta de calidad— existían por la cola de dependencias de
> la librería. Sin librería no hay cola, sin cola no hay optimización que activar, y sin
> optimización no hay artefacto nuevo que nadie haya ejecutado. Se retiran con FR-041 y FR-042
> (D-227).

**Resultado de la puerta previa a la fase 0**: pasa sin violaciones.

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

**Re-evaluación posterior a la implementación**: pasa, y con una comprobación que no estaba prevista.
Retirar la librería tocó **cuatro ficheros de `data/source/remote/` y ni uno solo de `domain` o de
`ui`**. Que un cambio de transporte quepa ahí es lo que D-010 de la feature 007 prometió por escrito,
lo que la 009 cobró una vez, y lo que esta feature ha cobrado dos veces en el mismo día — una al
adoptar la librería y otra al retirarla. La novena regla de Konsist se añade para que siga siendo
así.

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
│           ├── AiDocumentUploader.kt                NUEVO       interfaz + UploadedDocument
│           ├── OkHttpGeminiDocumentUploader.kt      NUEVO       la subida reanudable, a mano
│           ├── AiDocumentSessionStore.kt            NUEVO       como mucho una sesión viva
│           ├── OkHttpGeminiSummaryDataSource.kt     REESCRITO   de Interactions a generateContent
│           ├── GeminiDtos.kt                        REESCRITO   las formas de Files y generateContent
│           ├── GeminiSummaryDataSource.kt           MODIFICADO  la firma toma el documento subido, y SummaryUsage
│           ├── SummaryValidator.kt                  MODIFICADO  toma totalPages, no RenderedDocument
│           ├── SummaryPromptFactory.kt              MODIFICADO  sin el hueco del documento
│           ├── SummarySchema.kt                     SIN CAMBIOS ni una línea (D-211)
│           ├── SummaryPayloadDtos.kt                SIN CAMBIOS ni un nombre de propiedad
│           ├── GeminiRateLimitCoordinator.kt        SIN CAMBIOS
│           ├── GeminiApiKeyProvider.kt              SIN CAMBIOS
│           └── DocumentText.kt                      BORRADO
│
├── ui/detail/PublicationDetailViewModel.kt          MODIFICADO  onCleared() suelta la sesión
└── ui/detail/component/AiSummaryTab.kt              MODIFICADO  una línea del mapa de mensajes

app/src/main/res/values/strings.xml                  MODIFICADO  aviso, fase nueva, error renombrado
app/build.gradle.kts                                 SIN CAMBIOS
gradle/libs.versions.toml                            SIN CAMBIOS ninguna dependencia nueva (D-227)
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
│   ├── OkHttpGeminiSummaryDataSourceTest.kt         REESCRITO   26 pruebas, MockWebServer sobre TLS
│   ├── AiDocumentSessionStoreTest.kt                NUEVO       reutiliza, releva y retira
│   ├── SummaryValidatorTest.kt                      MODIFICADO  firma
│   ├── SummaryPromptFactoryTest.kt                  MODIFICADO  firma y el documento adjunto
│   ├── SummarySchemaTest.kt                         SIN CAMBIOS sigue vigilando el orden
│   └── DocumentTextTest.kt                          BORRADO
├── integration/AiSummaryFlowIntegrationTest.kt      MODIFICADO  la cadena nueva
├── fake/FakeGeminiSummaryDataSource.kt              MODIFICADO  la firma nueva
├── fake/FakeAiDocumentUploader.kt                   NUEVO
└── ui/detail/AiErrorMessagesTest.kt                 MODIFICADO  el mensaje renombrado

app/src/androidTest/java/com/jrblanco/boccantabria/
├── data/source/local/AndroidxPdfTextExtractorTest.kt  BORRADO
├── data/source/local/AndroidxPdfPageCounterTest.kt    NUEVO     cuenta y detecta protegido
└── ui/detail/AiSummaryTabTest.kt                      MODIFICADO  dos literales

CLAUDE.md                                            MODIFICADO  tubería, reglas (8→9), la librería vetada
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
| **El protocolo de subida reanudable se escribe a mano** (D-201, D-227) | La librería oficial, que lo evitaría, **no se puede usar en Android**: su artefacto lanza en cuanto se le da una credencial. No es una preferencia, es que la alternativa no existe | Firebase AI Logic, que es lo que el propio mensaje de error recomienda: no expone la Files API, y sin ella la feature 011 reenvía el boletín en cada pregunta. Forzar el artefacto `-jvm` para saltarse el guardián: rodear a mano un control de seguridad del proveedor, sobre una variante no compilada para Android, que cualquier parche puede romper |
| **Se conserva un contador de páginas local** (D-205) | Sin el número real de páginas, el validador tendría que creerse el que declara el modelo —y existe para no creérselo—, y una cita a una página inexistente sería un enlace roto. Además mantiene vivo `EncryptedPdf` sin gastar cuota | Borrar toda la lectura local del PDF: más simple, y deja dos agujeros comprobables. Treinta líneas no son precio para eso |
