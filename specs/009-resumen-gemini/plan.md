# Implementation Plan: Resumen IA con proveedor nuevo

**Branch**: `009-resumen-gemini` | **Date**: 4 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-resumen-gemini/spec.md`

## Summary

El Resumen IA deja de hablar con Groq y habla con la API de Gemini Developer, modelo
`gemini-3.5-flash-lite`. Y con el proveedor se va la escasez alrededor de la que estaba construido:
el troceado por páginas, la estimación de tokens y el presupuesto de 4.500 contra 1.800 desaparecen,
porque el modelo nuevo admite 1.048.576 tokens de entrada y cualquier publicación del boletín entra
completa.

**Cuatro cosas definen este plan, y las cuatro son elecciones.**

La primera: **no entra ninguna dependencia**. OkHttp y `kotlinx-serialization-json` ya están en el
catálogo, así que la petición se escribe a mano contra la API REST igual que hoy. Se descartó el SDK
que traía la documentación aportada —está deprecado— y se descartó Firebase AI Logic, que sí sería
viable y quitaría la credencial del APK, pero convertiría la frontera con el servicio en una frontera
de SDK y obligaría a rehacer las veintiuna pruebas de contrato que hoy corren sobre MockWebServer
(D-102).

La segunda: **casi nada del subsistema se toca**. Los ocho casos de `AiSummaryError`, los seis de
`AiSummaryStatus`, `AiSummary` entero, el contrato del repositorio, los cuatro casos de uso, los diez
componibles de la pestaña y las veintiuna pruebas instrumentadas quedan **intactos**. El cambio cabe
en `data/source/remote/` más tres constantes de dominio. Eso no es suerte: es lo que D-010 y D-017 de
la feature 007 prometieron por escrito, y esta feature es la factura que lo comprueba.

La tercera: **el esquema JSON se conserva verbatim**, sin el envoltorio de OpenAI. Gemini admite
`$defs`, `$ref` y `additionalProperties`, y respeta el orden de declaración de forma implícita, así
que la decisión D-030 de la 007 —`plainLanguageSummary` la última, acotada— sobrevive sin esfuerzo. Es
el punto de mayor riesgo del plan y lleva plan B escrito (D-105).

La cuarta: **el coordinador de cuota cambia de fuente, no de oficio**. Gemini no manda cabeceras de
cuota; la aplicación lleva la cuenta ella misma con ventanas deslizantes en memoria, y el 429 se
clasifica por el retraso que pide, no por el texto que trae (D-108, D-109).

**Lo que este plan no hace**: no envía el PDF en crudo aunque el modelo lo admita, no construye el
chat «Preguntar», no toca el diseño de la tarjeta, no cambia la extensión de la prosa, no migra la
base de datos y no añade capacidades nuevas del servicio.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11 de origen y destino, AGP 9.3.2, Gradle 9.5.0, KSP
2.2.10-2.0.2. Sin cambios.

**Primary Dependencies**: **ninguna nueva y ninguna que subir.** Se reutiliza todo en las versiones
que ya hay: OkHttp por BOM 5.5.0, `kotlinx-serialization-json` 1.8.1, Room 2.8.4, Koin por BOM 4.2.2,
Compose por BOM 2026.02.01, `androidx.pdf` 1.0.0-beta01, corrutinas 1.11.0. `gradle/libs.versions.toml`
**no se toca** (D-102).

**Storage**: Room **se queda en la versión 4**. Ninguna migración, ningún esquema exportado nuevo,
ninguna columna nueva: `ai_summaries` ya guarda la procedencia en columnas agnósticas del proveedor
—`model_id`, `prompt_version`, `schema_version`— y eso es exactamente lo que hace que este cambio no
toque la base de datos (D-114). Sigue sin haber ninguna sentencia de borrado en ninguno de los cinco
DAO. Lo único que cambia en almacenamiento local es la **clave** de la preferencia del aviso, que se
versiona para que el aviso ampliado se lea una vez (D-113).

**Network**: una petición `POST` a
`https://generativelanguage.googleapis.com/v1beta/interactions` con el modelo
`gemini-3.5-flash-lite`. Credencial en la cabecera `x-goog-api-key`, nunca en el cuerpo ni en la URL.
Cuerpo con `store: false`, `generation_config.thinking_level: "minimal"`, `max_output_tokens: 8000` y
`response_format` de tipo texto con `mime_type: "application/json"` y el esquema. **Sin** `temperature`,
`top_p` ni `top_k`: la documentación del modelo dice que no se cambien y Flash-Lite no admite valores
propios (D-106). Sin respuesta progresiva. El cliente se deriva del `OkHttpClient` compartido con
`newBuilder()`, como hoy. Sin Retrofit. La credencial se lee de `GEMINI_API_KEY` en `local.properties`
con API de proveedor de Gradle, con respaldo por variable de entorno; si falta, la compilación sigue
en verde y la función se anuncia como no configurada.

**Testing**: JUnit 4.13.2, MockK 1.14.11, Turbine 1.2.1, `kotlinx-coroutines-test` 1.11.0,
Robolectric 4.16.1 con `@Config(sdk = [36])`, `koin-test`, MockWebServer sobre TLS con `okhttp-tls`,
Compose UI Test y Konsist 0.17.3. Sin cambios en herramientas: la frontera sigue siendo HTTP y por eso
las pruebas de contrato se reescriben en vez de rehacerse.

**Target Platform**: Android, `minSdk 28`, `compileSdk` y `targetSdk` 37. Vertical fijo, tema claro
único. Sin cambios.

**Performance Goals**: un resumen ya guardado se muestra en menos de un segundo y sin red (SC-002 de
la 007, aquí SC-004). La generación de un documento de hasta cuarenta páginas no debería pasar de
veinte segundos en red normal — más texto que antes, pero un modelo optimizado para
*document processing*. La extracción de texto sigue sin bloquear el hilo principal.

**Constraints**: el techo ya no es la cuota, es el **guardarraíl**: `DocumentText.MAX_CHARACTERS =
480_000`, unos **109.000 tokens**, el 10 % de la ventana de entrada del modelo. Y ese número está
**medido**: 4,39 caracteres por token sobre texto del BOC en español, contra el servicio real el 4 de
septiembre de 2026 —6 036 caracteres cobrados como 1 376 tokens—. El proveedor anterior se estimaba a
3,2 y nunca se midió. Cubre unas ciento noventa páginas de boletín a 2.500 caracteres por página,
contra las cien que SC-001 declara como envolvente ordinaria. Lo que sigue sin confirmar es el límite
de tokens por minuto, que el proveedor tampoco publica. Límites de uso: 30 peticiones por minuto y 1.500 por día,
**valores documentados pendientes de confirmar en el panel del proveedor** (D-115). Cero consultas al
servicio sin que alguien pulse el botón. Ni la credencial ni el contenido del documento pueden
aparecer en registros.

**Scale/Scope**: 8 ficheros de producción renombrados o reescritos, 8 modificados, ninguno nuevo de
cero; 9 ficheros de prueba reescritos y 5 modificados. Ninguna pantalla nueva, ningún componible
nuevo, ningún literal de interfaz nuevo salvo la frase del aviso. Es deliberadamente una feature de
sustitución, no de crecimiento.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluado contra `.specify/memory/constitution.md` **versión 1.1.0** (enmendada el 30 de agosto de
2026). La constitución **no nombra a Groq** en ninguna parte: la elección de proveedor fue una
decisión de `plan.md` de la feature 007, así que sustituirla **no requiere enmienda**.

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | El cambio toca código de producto —fuente de datos, esquema, presupuesto, coordinador de cuota, DTO y constantes de dominio—, así que **no cae en la exención** de «arreglos de build, subidas de versión, erratas y documentación», que la propia constitución acota diciendo que «cubre configuración; NO cubre código de producto que se cuele bajo esa etiqueta». Recorre el ciclo completo: `spec.md` con 35 requisitos y su lista de calidad en 16 de 16, este `plan.md`, `research.md`, `data-model.md`, `contracts/` y `quickstart.md`; ninguna línea de producto hasta que `/speckit-tasks` produzca `tasks.md`. La rama `009-resumen-gemini` la creó la extensión git | ✅ |
| **II. Arquitectura limpia** | El cambio está **confinado a `data/source/remote/`**. De `domain` solo cambian tres literales de `AiSummaryConstants`, que es Kotlin puro y sigue sin importar nada. `ui` no se toca en absoluto: es la comprobación práctica de que la regla «los DTO de `data` no cruzan a `ui`» se cumplía de verdad, porque si algún componible hubiera conocido un tipo de Groq esta feature lo habría descubierto. Ningún SDK entra: la única frontera externa sigue siendo HTTP | ✅ |
| **III. MVVM** | `PublicationDetailViewModel`, `PublicationDetailUiState` y los diez componibles de la pestaña **no se modifican**. La orquestación sigue en el repositorio. Que un cambio de proveedor no llegue a la capa de presentación es el resultado que el principio persigue | ✅ |
| **IV. Koin** | Cuatro declaraciones renombradas en `DataModule`; ninguna nueva, ninguna instanciación a mano. `KoinModulesTest` se actualiza en los dos sentidos —`CROSS_MODULE_TYPES` y la resolución explícita—, que es lo que exige el principio cuando cambia una dependencia. Ninguna función fábrica nueva: no entra ningún SDK de terceros que haya que envolver | ✅ |
| **V. Testing exigente** | Ninguna prueba se desactiva, se comenta ni se borra para poner la build en verde. Las nueve pruebas cuya frontera cambia **se reescriben**, no se retiran; `DocumentTextTest` se reduce a las seis afirmaciones que siguen teniendo sentido en lugar de desaparecer con el presupuesto. Los dos defectos que se corrigen de raíz —techo de salida y clasificación del 429— llevan prueba de regresión. Y se reconoce por escrito, en `quickstart.md` §3 bis, que la frontera con el servicio hay que atravesarla **de verdad** al menos una vez, porque ninguna prueba con dobles puede verla | ✅ |
| **VI. Observabilidad desacoplada** | El registro sigue pasando por `CrashReporter` inyectado, cambia de prefijo a `gemini:` y **gana** el `status` de la respuesta, que es diagnóstico que con Groq hubo que instrumentar a posteriori (D-117). Ningún SDK de Firebase se nombra fuera de `data`. Nunca la credencial, nunca el contenido del documento | ✅ |
| **Restricciones tecnológicas** | Sin dependencias nuevas, así que `libs.versions.toml` no se toca y no hay ninguna coordenada literal que colar en un `build.gradle.kts`. Compose y Material 3 sin XML ni Fragments; Koin; corrutinas y `Flow`. Código y comentarios en inglés, documentación y commits en español | ✅ |
| **Flujo y puertas de calidad** | Trabajo en la rama `009-resumen-gemini`, nunca en `main`. Commits en español con prefijo convencional. Las cuatro puertas en orden antes de dar la feature por terminada, y así está escrito en `quickstart.md` | ✅ |

**Resultado de la puerta previa a la fase 0**: pasa sin violaciones.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño detallado no introdujo ninguna
desviación nueva, y los tres puntos que merecían una segunda mirada se resolvieron a favor de la
norma:

- **El guardarraíl no se borró aunque el propietario había pedido «sin racionar».** Retirarlo del todo
  habría obligado a eliminar el estado `Generating` parcial, dos `plurals`, tres pruebas
  instrumentadas y cuatro requisitos de la 007. Conservar un tope que en uso normal no se alcanza
  cuesta una constante y mantiene ese camino **vivo y probado** (D-104).
- **El coordinador de cuota no persiste sus contadores**, y eso se decidió a conciencia en lugar de
  por olvido: guardarlos en disco para un límite que una persona pulsando un botón no puede alcanzar
  sería complejidad sin beneficio. «Ante la duda, gana la opción más simple» (D-108).
- **`SummaryBudget` se renombra y se reduce en vez de borrarse y volver a nacer** con otro nombre. El
  guardarraíl y el recorte por límite natural del texto son el mismo código que ya estaba probado; lo
  que se va es la aritmética de tokens.

## Project Structure

### Documentation (this feature)

```text
specs/009-resumen-gemini/
├── spec.md                     # Qué y por qué. 35 requisitos, 14 criterios de éxito
├── plan.md                     # Este fichero
├── research.md                 # Fase 0. 18 decisiones, D-101 a D-118
├── data-model.md               # Fase 1. Qué cambia de forma y qué no, y el cable nuevo
├── quickstart.md               # Fase 1. Cómo se valida, incluido el §3 bis contra el servicio real
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
│   └── DataModule.kt                            MODIFICADO  4 declaraciones renombradas
├── domain/model/
│   └── AiSummaryConstants.kt                    MODIFICADO  los 3 literales de procedencia
├── data/
│   ├── repository/AiSummaryRepositoryImpl.kt    MODIFICADO  sin presupuesto, refusal renombrado
│   └── source/
│       ├── local/
│       │   └── AiPreferences.kt                 MODIFICADO  clave versionada (FR-031a)
│       └── remote/
│           ├── GeminiSummaryDataSource.kt       RENOMBRADO  desde GroqSummaryDataSource
│           ├── OkHttpGeminiSummaryDataSource.kt REESCRITO   desde OkHttpGroqSummaryDataSource
│           ├── GeminiApiKeyProvider.kt          RENOMBRADO  desde GroqApiKeyProvider
│           ├── GeminiRateLimitCoordinator.kt    REESCRITO   desde GroqRateLimitCoordinator
│           ├── GeminiDtos.kt                    NUEVO       solo los tipos de cable
│           ├── SummaryPayloadDtos.kt            EXTRAÍDO    el payload, con nombre agnóstico
│           ├── SummarySchema.kt                 RENOMBRADO  desde GroqSummarySchema, sin envoltorio
│           ├── DocumentText.kt                  RENOMBRADO  desde SummaryBudget, sin aritmética
│           ├── SummaryPromptFactory.kt          MODIFICADO  documento completo, tope por sección
│           └── SummaryValidator.kt              MODIFICADO  firma y tope de diez como última puerta
│
│           GroqDtos.kt                          BORRADO     partido en los dos de arriba
│
└── ui/                                          SIN CAMBIOS  ni un fichero

app/src/main/res/values/strings.xml              MODIFICADO  una frase en ai_notice_body
app/build.gradle.kts                             MODIFICADO  GEMINI_API_KEY en vez de GROQ_API_KEY
gradle/libs.versions.toml                        SIN CAMBIOS  ninguna dependencia nueva
app/schemas/                                     SIN CAMBIOS  la base sigue en la versión 4

app/src/test/java/com/jrblanco/boccantabria/
├── architecture/ArchitectureRulesTest.kt        SIN CAMBIOS  las 8 reglas siguen valiendo
├── di/KoinModulesTest.kt                        MODIFICADO  las dos listas
├── data/repository/AiSummaryRepositoryImplTest.kt          MODIFICADO  tipos renombrados
├── data/source/local/
│   ├── AiPreferencesTest.kt                     MODIFICADO  la clave vieja no se lee
│   └── AiSummaryDaoTest.kt                      MODIFICADO  literal del modelo
├── data/source/remote/
│   ├── OkHttpGeminiSummaryDataSourceTest.kt     REESCRITO   21 pruebas, cuerpos nuevos
│   ├── GeminiRateLimitCoordinatorTest.kt        REESCRITO   contador propio, sin cabeceras
│   ├── GeminiApiKeyProviderTest.kt              RENOMBRADO
│   ├── SummarySchemaTest.kt                     REESCRITO   sin envoltorio, con maxItems
│   ├── DocumentTextTest.kt                      REDUCIDO    de 11 a 6, desde SummaryBudgetTest
│   ├── SummaryPromptFactoryTest.kt              MODIFICADO  firma y afirmaciones nuevas
│   └── SummaryValidatorTest.kt                  MODIFICADO  firma y tope de diez
├── integration/AiSummaryFlowIntegrationTest.kt  MODIFICADO  el doble renombrado
├── fake/FakeGeminiSummaryDataSource.kt          RENOMBRADO
└── ui/detail/AiErrorMessagesTest.kt             MODIFICADO  «gemini» y «google» a la lista negra

app/src/androidTest/java/com/jrblanco/boccantabria/
└── ui/detail/AiSummaryTabTest.kt                SIN CAMBIOS  las 21 pruebas siguen valiendo

CLAUDE.md                                        MODIFICADO  proveedor, presupuesto y muestra de log
docs/diseno/especificaciones-diseno.md           SIN CAMBIOS  el §20 no nombra al proveedor
```

**Structure Decision**: no cambia. Módulo único `:app` con separación por paquetes, y cada pieza en la
capa que le corresponde por lo que **es**. Lo único que se reordena es dentro de
`data/source/remote/`, y con un criterio: **los ficheros que describen al proveedor llevan su nombre y
los que describen nuestro formato, no**. `GeminiDtos` y `OkHttpGeminiSummaryDataSource` hablan de
Gemini; `SummarySchema`, `SummaryPayloadDtos`, `SummaryPromptFactory`, `SummaryValidator` y
`DocumentText` hablan del BOC y sobrevivirán al próximo cambio de proveedor sin que nadie los toque.
Partir `GroqDtos.kt` en dos no es cosmético: la mitad que se queda es la que se **persiste** en
`summary_json`, y mezclarla con los tipos de cable es lo que hacía que un cambio de proveedor pareciera
tocar el formato almacenado cuando no lo toca (D-111).

## Complexity Tracking

> La puerta constitucional pasa sin violaciones. Se registran aquí, por transparencia, las cuatro
> decisiones que un revisor podría discutir. Cada una remite a su decisión de `research.md`.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| **Conservar un guardarraíl de caracteres** cuando el propietario pidió «sin racionar» (D-104) | Un documento patológico —mil páginas— tiraría la petición entera, y el camino de cobertura parcial está probado y respaldado por cuatro requisitos de la 007 y tres pruebas instrumentadas | Retirarlo del todo: obliga a borrar el estado `Generating` parcial, dos `plurals`, esas tres pruebas y a reescribir FR-028, FR-029, FR-030 y FR-031 de la 007. Más trabajo, menos red de seguridad |
| **Un contador de cuota propio** en vez de confiar solo en el 429 (D-108) | Sin las cabeceras del proveedor es lo único que sostiene FR-020 —no lanzar consultas condenadas— y FR-022 —distinguir minuto de día— | Reaccionar solo al 429: incumple FR-020 tal como está escrito y deja `QuotaDay` sin camino real, con lo que un estado de la pestaña y su prueba quedarían sin cubrir |
| **Clasificar el 429 por el retraso pedido**, no por el texto del error (D-109) | El texto del proveedor cambia y está en inglés; el retraso es un número y no depende del idioma. Y FR-027 prohíbe mostrar mensajes internos del proveedor, así que apoyarse en ellos sería construir sobre algo que no se puede enseñar | Buscar palabras en `error.message`: frágil ante cualquier cambio de redacción del proveedor, e imposible de probar sin fijar su texto literal en una prueba |
| **Versionar la clave de la preferencia del aviso** (D-113) | El aviso cambia de contenido y quien ya lo aceptó nunca leyó la frase nueva. Es una línea | Dejar la clave: quien ya aceptó no vería nunca que el texto de su documento puede usarse para mejorar los modelos del proveedor. La transparencia era el motivo de la decisión, y así quedaría a medias |
