# Research: Resumen IA

**Feature**: `007-resumen-ia` | **Fase**: 0 | **Fecha**: 1 de septiembre de 2026

Las decisiones de esta fase resuelven las incógnitas técnicas de `spec.md`. Todo lo que aquí se
afirma sobre bibliotecas o sobre el servicio externo está **comprobado contra la fuente**, no
supuesto: los contratos de `androidx.pdf` se leyeron del `.aar` que ya está en la caché de Gradle del
proyecto, y las capacidades y límites del servicio, de su documentación vigente.

---

## Cómo se obtiene el texto del documento

### D-001 — El texto lo extrae `androidx.pdf`, que ya está en el proyecto

**Decisión**: usar `androidx.pdf` —la misma biblioteca con la que se dibuja el documento desde la
feature 004— para extraer el texto. **No se añade ninguna dependencia de PDF.**

**Motivo**: la biblioteca expone exactamente lo que hace falta, y lo hace en el sitio correcto.

```kotlin
public interface PdfDocument : Closeable {
    public val pageCount: Int
    public suspend fun getPageContent(pageNumber: Int): PdfPageContent?   // 0-based
}
public class PdfPageContent(
    public val textContents: List<PdfPageTextContent>,
    public val imageContents: List<PdfPageImageContent>,
)
public class PdfPageTextContent(bounds: List<RectF>, public val text: String)
```

Lo decisivo no es que exista el método, sino **dónde se ejecuta**. `javap` sobre
`pdf-document-service-1.0.0-beta01.aar` confirma que
`SandboxedPdfDocument.getPageContent(int)` está implementado y delega en `PdfDocumentRemote`: el
análisis del PDF ocurre **en el proceso aislado**, que es la razón por la que se eligió este visor en
la feature 004. Los documentos vienen de un servicio público por internet y uno malformado no debe
poder tumbar la aplicación.

**Alternativas descartadas**:

- **`com.tom-roush:pdfbox-android:2.0.27.0`**, que es lo que pedía la especificación técnica del
  propietario. Se descarta por tres razones, en este orden: (a) analizaría el PDF **dentro** del
  proceso de la aplicación, perdiendo el aislamiento que ya se tenía; (b) añade una dependencia de
  varios megas con recursos de fuentes propios y una inicialización obligatoria
  (`PDFBoxResourceLoader.init`) en `Application`; (c) haría convivir dos motores de PDF distintos en
  la misma aplicación. Su única ventaja real —ser Kotlin/JVM puro y por tanto probable sin
  emulador— se paga con un test instrumentado, que es un precio menor.
- **`PdfRenderer` de la plataforma**: no extrae texto, solo dibuja.
- **Reconocimiento óptico sobre la página renderizada**: fuera de alcance por decisión de producto, y
  además sería renderizar imágenes justo cuando lo que se quiere es no hacerlo.

**Riesgo asumido y su salida**: `PdfFeature.TEXT_EXTRACTION` existe en la biblioteca, pero
`isFeatureSupported` es `@RestrictTo(LIBRARY_GROUP)`, así que **no se puede preguntar de antemano** si
un dispositivo concreto lo soporta. La mitigación es doble: «sin texto utilizable» es un estado de
primera clase que cubre por igual el PDF escaneado, el contenido nulo y la excepción (D-004); y el
extractor es una interfaz, de modo que si en dispositivos reales resultara devolver vacío, se cambia
la implementación por PdfBox sin tocar ninguna otra pieza. Esa es toda la utilidad de la costura.

### D-002 — La extracción vive en `data`, y `androidx.pdf` pasa a tener dos fronteras

**Decisión**: `PdfTextExtractor` y su implementación viven en `data/source/local/`. A partir de esta
feature, `androidx.pdf` se toca en **dos** sitios: `ui/pdf` para dibujar y `data/source/local` para
extraer texto. El párrafo de `CLAUDE.md` que dice que `ui/pdf` es la única frontera se enmienda en
este mismo cambio.

**Motivo**: extraer texto de un fichero es una **fuente de datos**, no presentación. Ponerlo en `ui`
obligaría a que el `ViewModel` orquestara la tubería —descarga, extracción, presupuesto, consulta,
validación—, y eso es lógica de negocio en la capa de presentación: incumple el principio III.

**Alternativas descartadas**:

- **Mover `PdfDocumentLoader` a `data` y ampliarlo con `extractText`.** Rompería la regla Konsist «ui
  no depende de data», porque `ui/pdf` y `ui/detail/component/DocumentPreview` lo importan. La
  interfaz de dibujo **se queda donde está**.
- **Una interfaz en `domain` implementada en `data`.** Innecesario: el extractor es un detalle interno
  del repositorio, igual que `DocumentDownloader` y `DocumentCache`, que tampoco están en `domain`. El
  contrato que sí sube a `domain` es `AiSummaryRepository`.

### D-003 — Página a página, numeradas desde 1 hacia fuera

**Decisión**: se extrae una página cada vez y se conserva su número. La biblioteca numera desde 0; el
dominio, la interfaz y el texto enviado al servicio numeran **desde 1**. La conversión ocurre una sola
vez, en el extractor.

**Motivo**: sin procedencia no hay referencias verificables (FR-020), y sin referencias verificables
el resumen es una afirmación sin respaldo. Numerar desde 1 hacia fuera es lo que espera cualquiera que
mire un documento, y tener un único punto de conversión evita el error clásico de desfase por uno.

**Alternativas descartadas**: extraer el documento entero de una vez —no existe esa llamada, y aunque
existiera perdería la página de origen—.

### D-004 — «Sin texto utilizable» se decide con un umbral, no esperando una excepción

**Decisión**: tras extraer, se cuenta el texto realmente aprovechable. Se declara `NoExtractableText`
si los caracteres alfanuméricos totales son menos de 40 por página de media, **o** si más de la mitad
de las páginas tienen menos de 20. Un `PdfPasswordException` da `EncryptedPdf`. Cualquier otra
excepción da `Failure`. En los tres casos **no se consulta el servicio**.

**Motivo**: un PDF escaneado no falla al extraerse: devuelve cadenas vacías o cuatro caracteres de
ruido. Si solo se comprobara «¿ha lanzado excepción?», se enviaría un contexto vacío al servicio, que
gastaría cuota y devolvería invención — exactamente lo que FR-012 prohíbe. Los umbrales son los de la
especificación técnica del propietario, que están calibrados para documento administrativo.

**Alternativas descartadas**: exigir texto no vacío en **todas** las páginas —un boletín legítimo puede
traer una página de solo tabla o de solo sello—; y no comprobar nada, que es el fallo silencioso.

### D-005 — La normalización es no destructiva y Kotlin puro

**Decisión**: `PdfTextNormalizer` normaliza saltos de línea, colapsa espacios repetidos, elimina
líneas vacías consecutivas y caracteres de control, une palabras partidas por guion al final de línea
**solo** si la siguiente línea empieza en minúscula, y elimina encabezados y pies que se repiten en al
menos el 60 % de las páginas. Es Kotlin puro, sin `import android.*`.

**Motivo**: cada carácter que se limpia es presupuesto que se libera para contenido real, pero en un
documento jurídico limpiar de más cambia el significado. La regla del guion es la más delicada:
«sub-vención» al final de línea se une, «Real Decreto-Ley» no, porque la siguiente parte va en
mayúscula. Ser Kotlin puro hace que sus pruebas corran sin emulador, que es donde de verdad se
comprueban estas reglas.

**Prohibido explícitamente**: reescribir, traducir, corregir cifras o nombres, eliminar párrafos por
parecer poco importantes, unir columnas con heurísticas, e interpretar abreviaturas. Lo que se manda
al servicio tiene que seguir siendo el documento.

---

## Qué se envía, y cuánto

### D-006 — Una sola consulta por publicación

**Decisión**: resumen directo. Si el documento no cabe entero, se analiza la parte que quepa y se
declara la cobertura (FR-028, FR-029). **No** hay resumen por fragmentos.

**Motivo**: decisión de producto del propietario, y bien tomada. El `map-reduce` reanudable de la
especificación técnica exige entidad de resultados parciales, orquestador con estado persistido,
reanudación tras muerte de proceso y sus pruebas: más código que todo el resto de la feature junta,
para un caso —publicaciones de decenas de páginas— que es minoritario en el boletín. Cuando se
compruebe que hace falta, el asiento está hecho: la selección de páginas ya devuelve el rango
analizado, y `coverage` ya modela lo que faltaría fundir.

**Alternativas descartadas**: enviar el documento entero confiando en el contexto de 131 K del
modelo. El contexto no es el límite: el límite es la **cuota de 8.000 tokens por minuto**, que es
mucho más estrecha y se comparte con toda la organización.

### D-007 — El presupuesto, y cuál de los dos límites manda

**Decisión**:

```text
Prompt fijo y metadatos     ~700 tokens
Texto del documento        4.500 tokens máx.  /  14.400 caracteres máx.
Respuesta                  1.800 tokens máx.
                          ───────
Objetivo por consulta      7.000 tokens
```

> **Corregido tras medirlo contra el servicio real.** El reparto original —5.000 de documento contra
> 1.200 de respuesta— se escribió a ojo y estaba mal en las dos mitades. Ver **D-033**.

Con `estimateTokens(text) = ceil(text.length / 3.2)`. **El tope de caracteres es el guardarraíl**; la
estimación decide el corte y alimenta las métricas.

**Motivo**: no hay tokenizador ligero del modelo para Android, y no merece la pena traerlo para esto.
Una estimación conservadora más un tope duro de caracteres cumple el objetivo real, que es no pasarse
de 8.000 TPM. El resultado práctico: unos 7 K por resumen, es decir **un resumen por minuto
sostenido**, que es lo que promete SC-011 y lo que el plan gratuito da de verdad.

**Alternativas descartadas**: fiarse solo de la estimación —un error del 15 % en el conteo se convierte
en un 429 evitable—; y llevar un tokenizador real, que es peso y mantenimiento para afinar un margen
que ya está cubierto.

### D-008 — Se cortan páginas enteras; partir una página es la excepción

**Decisión**: se acumulan páginas completas desde la primera mientras quepan. Solo si la **primera
página sola** ya no cabe se corta dentro de ella, por el último final de párrafo que entre.

**Motivo**: una referencia de página solo significa algo si la página se envió entera; media página
enviada produce citas que no se pueden comprobar. Cortar por página mantiene intacta la relación
entre lo que el resumen afirma y lo que se puede abrir y leer.

**Alternativas descartadas**: llenar el presupuesto al máximo troceando la última página —gana unos
cientos de caracteres y estropea la trazabilidad—; y elegir páginas «relevantes» en vez de las
iniciales, que exigiría entender el documento antes de resumirlo.

### D-009 — Marcadores de página en el texto enviado

**Decisión**: el texto va delimitado y con un marcador por página:

```text
<documento_boc>
[PÁGINA 1]
…
[PÁGINA 2]
…
</documento_boc>
```

**Motivo**: es lo que permite al modelo citar páginas, y a la validación posterior comprobar que solo
cita las que recibió. Los marcadores son datos de trazabilidad, no contenido oficial, y así se le dice.

---

## El servicio externo

### D-010 — Groq con `qwen/qwen3.8-27b`, y el identificador en una constante

**Decisión**: `POST https://api.groq.com/openai/v1/chat/completions` con el modelo
`qwen/qwen3.8-27b`, que es el que pide el propietario. El identificador vive en una constante junto a
la versión del prompt y la del esquema, y el acceso va detrás de la interfaz
`GroqSummaryDataSource`.

**Motivo**: verificado en la documentación del proveedor — el modelo existe, tiene 131 042 tokens de
contexto y 16 384 de salida máxima, y admite modo esquema JSON. Pero está en **Preview, no en
producción**, y los modelos en Preview se retiran. Que el identificador sea una constante y el acceso
una interfaz es lo que convierte esa retirada en un cambio de una línea más una implementación nueva,
en vez de en una refactorización.

**Consecuencia práctica**: las tres constantes de procedencia —modelo, versión de prompt, versión de
esquema— se guardan con cada resumen. Cambiar cualquiera de ellas marca lo guardado como obsoleto
(FR-035) sin borrarlo.

### D-011 — Esquema JSON estricto, y por eso no hay respuesta progresiva

**Decisión**: `response_format` de tipo `json_schema` con `strict: true`, el esquema del apartado 12
de la especificación técnica —todos los campos en `required`, todo objeto con
`additionalProperties: false`, subesquemas con `$defs`/`$ref`—. `stream: false`.

**Motivo**: es lo que permite ocultar secciones vacías con confianza (FR-015) y validar las
referencias antes de mostrarlas (FR-022): si la respuesta fuera prosa libre habría que analizarla, y
un analizador de prosa falla justo con los documentos raros. Verificado: `qwen/qwen3.8-27b` es uno de
los tres modelos del proveedor que admiten `strict: true`, y admite `$defs`/`$ref`. También
verificado: **Structured Outputs y streaming no son compatibles hoy**, así que no se puede tener las
dos cosas y se elige la fiabilidad.

**Consecuencia para la interfaz**: como no hay texto apareciendo poco a poco, la espera **tiene que**
contar algo. De ahí que FR-004 exija mostrar la fase en curso.

### D-012 — Sin razonamiento expuesto, y temperatura baja

**Decisión**: `reasoning_effort: "none"`, `temperature: 0.2`, `max_completion_tokens: 1200`. No se
envía `top_p`.

**Motivo**: `"none"` está verificado como valor válido para los modelos Qwen del proveedor, y un
resumen factual no necesita exponer razonamiento: son tokens pagados de la misma cuota que no se
muestran. La temperatura baja reduce variabilidad e invención, que en un boletín oficial no es una
preferencia de estilo. El proveedor recomienda mover `temperature` **o** `top_p`, no los dos.

### D-013 — OkHttp derivado del cliente compartido, sin Retrofit

**Decisión**: el cliente de Groq se construye con `client.newBuilder()` a partir del `OkHttpClient`
que ya inyecta Koin, con tiempos propios: conexión 15 s, lectura 90 s, escritura 30 s. Sin Retrofit.

**Motivo**: es exactamente lo que ya hace `OkHttpDocumentDownloader` (`OkHttpDocumentDownloader.kt:37`)
y por la misma razón: comparte el pool de conexiones en vez de duplicarlo. Retrofit sería una
dependencia nueva para **una** llamada POST; la constitución deja abierta la elección de cliente HTTP
y el proyecto ya la tomó.

**Nunca**: un interceptor de registro a nivel de cuerpo. Expondría la credencial y el documento
entero en el registro del sistema, que es justo lo que prohíben FR-047 y SC-009.

### D-014 — `kotlinx-serialization-json`, declarada explícitamente

**Decisión**: única dependencia nueva de la feature. El plugin de serialización ya está aplicado
—lo usan las rutas tipadas de navegación— y la biblioteca ya llega por transitividad desde
`navigation-compose`; se declara en el catálogo de versiones porque el código la importa directamente.

**Motivo**: la misma norma que se aplicó a `kotlinx-coroutines-play-services`, y por el mismo motivo:
depender por accidente de lo que arrastra otro es depender de una decisión ajena que puede cambiar
sin avisar.

### D-015 — Las cabeceras de cuota dicen algo distinto de lo que parece

**Decisión**: `GroqRateLimitCoordinator` lee las cabeceras reales de cada respuesta y son **la fuente
de verdad**, por delante de cualquier valor configurado.

**Hallazgo que corrige a la especificación técnica**: los nombres engañan.

| Cabecera | Qué es realmente |
|---|---|
| `x-ratelimit-limit-requests` | Peticiones por **día** (RPD), no por minuto |
| `x-ratelimit-limit-tokens` | Tokens por **minuto** (TPM) |
| `x-ratelimit-remaining-requests` | Lo que queda del día |
| `x-ratelimit-remaining-tokens` | Lo que queda del minuto |
| `x-ratelimit-reset-requests` | Duración hasta el reinicio diario (`2m59.56s`) |
| `x-ratelimit-reset-tokens` | Duración hasta el reinicio del minuto (`7.66s`) |
| `retry-after` | Solo en 429. **Segundos enteros**, no duración |

**Confirmado contra el servicio real.** Una llamada de verificación devolvió exactamente:
`x-ratelimit-limit-requests: 1000` (mil al **día**), `x-ratelimit-limit-tokens: 8000` (por **minuto**),
`x-ratelimit-reset-requests: 1m26.4s` y `x-ratelimit-reset-tokens: 10.514s` — los dos formatos de
duración que el analizador tiene que entender.

**Motivo para escribirlo aquí**: confundir RPD con RPM haría a la aplicación creer que tiene treinta
peticiones por minuto cuando lo que tiene son mil al día, y la diferencia solo se nota cuando el
límite ya se ha roto. El analizador de duraciones debe aceptar `7.66s`, `2m59.56s` y segundos
enteros. Los límites son **por organización**, no por persona: no se pueden repartir por usuario.

### D-016 — Qué hacer con cada respuesta

**Decisión**:

| Situación | Acción |
|---|---|
| 200 | Analizar, validar páginas y cobertura, guardar |
| 400 por tamaño o esquema | Recortar el texto un 15 % y reintentar **una** vez |
| 401 / 403 | Sin reintento. Es configuración, no un fallo pasajero |
| 413 | Recortar y reintentar una vez |
| 429 | Respetar `retry-after`; como máximo 2 reintentos |
| 5xx | Espera creciente 1 s / 2 s / 4 s con dispersión; como máximo 3 intentos |
| Sin red | Conservar el estado y permitir reintentar |
| Cancelación | Detener; **no** es un error |

La dispersión usa el `RandomProvider` que ya existe, el mismo que el reintento de los canales de
novedades. Las consultas se serializan con un `Mutex`: nunca hay dos en vuelo.

**Motivo**: reintentar a ciegas un 401 gasta cuota sin arreglar nada, y reintentar un 429 sin respetar
`retry-after` lo empeora. Que la cancelación no sea un error es lo que hace posible FR-006: quien sale
de la pantalla no ve un fallo por haberse ido.

### D-017 — La credencial: `local.properties` a `BuildConfig`, tras una abstracción

**Decisión**: `build.gradle.kts` lee `GROQ_API_KEY` de `local.properties` —que ya lo contiene y está
en `.gitignore`— y la expone con `buildConfigField`. La lectura usa
`providers.fileContents(...)` y `providers.environmentVariable(...)`, ambas API de proveedor de
Gradle. Todo el consumo pasa por `GroqApiKeyProvider`.

**Motivo**: es la opción C de la especificación técnica, elegida por el propietario a sabiendas de su
límite, que está escrito en los supuestos de `spec.md`: una credencial dentro de una aplicación
distribuida es recuperable. Es aceptable mientras la aplicación no se publique. La abstracción es lo
que hace que pasar a un servicio intermedio propio sea cambiar una implementación y una URL.

Dos detalles que no son opcionales:

- **API de proveedor, no `File.readText`.** El proyecto tiene la caché de configuración de Gradle
  activada (`org.gradle.configuration-cache=true`); leer un fichero a pelo en tiempo de configuración
  es una entrada no declarada.
- **Si la credencial falta, la compilación sigue en verde** y el valor es cadena vacía, que se traduce
  en `NotConfigured` (FR-042). Es lo que permite que la integración continua compile y pase las
  pruebas sin secretos.

**Alternativas descartadas**: un servicio intermedio propio, que es lo correcto para publicar pero
exige desplegar y mantener algo que hoy no existe; y pedir su propia credencial a cada persona, que
deja fuera a cualquiera que no sea técnico.

---

## Que lo que se muestre sea de fiar

### D-018 — Se valida la respuesta antes de mostrarla y antes de guardarla

**Decisión**: `SummaryValidator` rechaza o corrige antes de que nada llegue a la pantalla:

- el JSON debe deserializar y `choices` no puede venir vacío;
- el resumen en lenguaje llano no puede estar en blanco → `InvalidResponse`;
- toda página citada debe estar entre 1 y el total del documento **y** haber sido enviada; las que no,
  se descartan del elemento;
- `coverage.pagesAnalyzed` se **sustituye** por las páginas realmente enviadas;
- `coverage.complete` se pone a falso si no se analizaron todas las páginas con texto, **aunque el
  servicio afirme lo contrario**.

**Motivo**: FR-022, FR-030 y SC-012 no pueden depender de que el modelo se porte bien. El esquema
estricto garantiza la **forma** de la respuesta, no su **verdad**. La cobertura es el caso más
delicado: un resumen parcial que se declara completo es peor que no tener resumen, porque induce a
confiar.

**Comprobado contra el servicio real, y no era hipotético.** La primera respuesta que devolvió Groq con
este esquema traía `coverage: {pagesAnalyzed: [], totalPages: 1, complete: true}`: cobertura completa
sobre una lista de páginas vacía. Sin esta corrección, el `require` del modelo de dominio habría lanzado
y el primer resumen que alguien generase habría tumbado la pantalla. El esquema estricto garantiza la
**forma** de la respuesta, nunca su **sentido**. Hay prueba de regresión con esa respuesta literal.

**Lo que no se hace**: comprobar cada afirmación buscándola literalmente en el texto. Un resumen es
una paráfrasis; esa comprobación daría falsos negativos constantes. La garantía es la suma del prompt,
la procedencia por páginas y poder abrir el original.

### D-030 — El orden del esquema es el orden de generación, y la prosa va la última

**Decisión**: en `GroqSummarySchema`, `plainLanguageSummary` es la **última** propiedad, después de las
seis listas estructuradas, de `warnings` y de `coverage`. Y va acotada con `maxLength: 900`. El prompt
baja su objetivo de 120–220 palabras a 90–150, y añade un párrafo diciendo que un análisis parcial **no
exime** de rellenar los campos estructurados.

**Motivo, medido en un móvil real.** Los cuatro primeros resúmenes generados de verdad lo enseñaron:

| Publicación | Prosa | ¿Acaba la frase? | Secciones |
|---|---:|---|---:|
| Presupuesto de Cosío | 1024 | no | 21 |
| Aspirantes de Castro Urdiales | 1008 | sí | 13 |
| Corrección de errores | 1024 | no | **0** |
| Becas del ICANE | 1024 | no | **0** |

La prosa se cortaba en **1024 caracteres exactos**, a media palabra, y cuando eso pasaba **todo lo
declarado después venía vacío**. No lo cortaba nuestro código ni el tope de 1.200 tokens de salida, que
no se alcanzó (569, 601, 986, 1129). Con decodificación restringida, el orden de las propiedades es el
orden en que el modelo las emite: la prosa iba cuarta y se llevaba por delante las seis listas.

El propio modelo lo confesó en `warnings`: «los importes y plazos aparecen en las páginas 2 y 3, pero
**no se han incluido en los campos estructurados** debido a la incompletitud del análisis». Ante una
cobertura parcial se inhibía en vez de contar lo que sí veía.

**Comprobado tras el cambio**, con el mismo tipo de documento —una convocatoria de becas—: prosa de
**662 caracteres terminando la frase**, y la ficha con dos plazos, un importe, una actuación exigida y
un recurso. Antes: 1024 cortados y cero de todo.

**Alternativas descartadas**: subir `max_completion_tokens` —no era el límite que se alcanzaba—; y
recortar la prosa solo en el validador, que arregla la frase pero no devuelve las secciones perdidas.
El recorte se hace **además**, como red (D-018).

### D-031 — Nada de lo que el servicio manda se dibuja sin valor

**Decisión**: `SummaryValidator` descarta las entradas cuyo propio valor viene en blanco —un importe sin
cifra, un punto clave sin texto, un plazo sin plazo— antes de construir el modelo.

**Motivo**: la primera respuesta con el esquema nuevo trajo `amounts: [{amount: "", concept: "Dotación
mensual de cada beca"}]`. En pantalla eso es una viñeta con un hueco donde debería ir el dinero: parece
un fallo y no informa de nada. Una lista vacía ya significa «el documento no lo dice», y es lo honesto
que signifique también aquí. La descripción sí puede faltar: el valor es lo que lleva el sentido.

### D-032 — El resumen en lenguaje llano es obligatorio, y hay que decírselo

**Decisión**: el prompt declara que `plainLanguageSummary` es **siempre** obligatorio, incluso ante un
documento leído en parte, y que lo que falte se dice en `coverage` y en `warnings`, nunca dejando un
campo vacío. `PROMPT_VERSION` sube a `boc-summary-es-v3`.

**Motivo, y es una lección sobre cómo se redacta un prompt.** La versión v2 decía que un análisis
parcial «no exime de rellenar **los campos estructurados**». El modelo obedeció exactamente eso: ante
un documento del que solo se le enviaron 3 de 9 páginas, rellenó título, organismo, dos plazos y
cuatro advertencias, y dejó **el resumen** en blanco. El registro capturó la forma exacta:

```
documentTitle=207  datesAndDeadlines=2  warnings=4
keyPoints=0  plainLanguageSummary=0   finish_reason=stop
```

`finish_reason=stop` con 402 tokens: no se quedó sin sitio, **terminó por su cuenta**. Y como la app
rechaza un resumen sin texto llano, eso llegaba a la pantalla como «no se ha podido construir un
resumen fiable», culpando al servicio de una instrucción mal escrita.

El fallo no fue del modelo: **le pedí una cosa y me la dio**. Le pedí los campos estructurados y
protegí los campos estructurados; sacrificó lo único que no había nombrado. Cuando un prompt enumera
qué rellenar, lo que no aparece en la lista es lo que se pierde.

**Verificado**: el mismo documento, `boc:440025`, pasó de resumen vacío tres veces seguidas a **856
caracteres de prosa y 14 secciones** en el móvil del propietario.

**Alternativa descartada**: aceptar un resumen sin texto llano y mostrar solo las secciones. Un
resumen que no resume no es un resumen, y `AiSummary` lo exige en su `init` por esa razón.

### D-033 — El reparto de tokens, medido en vez de estimado

**Decisión**: 4.500 tokens de documento y **1.800** de respuesta, en lugar de 5.000 y 1.200.

**Dos hechos medidos, ninguno de los cuales estaba en la especificación técnica de partida:**

1. **El proveedor cobra `entrada + max_completion_tokens` contra la cuota del minuto, al pedir y no al
   responder.** Su propio error lo dice: `Limit 8000, Used 7346, Requested 6475`. El techo de la
   respuesta se paga se use o no, así que subirlo sin bajar el documento acerca el 429.
2. **1.200 tokens de respuesta se quedaban cortos.** Un resumen real de unas bases reguladoras llegó a
   **1.625**; otro a 1.129. Pasado el techo, el JSON llega cortado, no parsea, y el lector lee «no se
   ha podido construir un resumen fiable» — un problema nuestro disfrazado de fallo del servicio.

**Alternativas descartadas**: subir solo el techo de la respuesta, que empuja la petición contra los
8.000 por minuto; y dejarlo en 1.200 confiando en que el recorte del validador lo salve, que arregla
la frase pero no devuelve las secciones que nunca llegaron.

### D-034 — Un `catch` que no escribe nada convierte un fallo en un misterio

**Decisión**: todo camino que traga una excepción o rechaza una respuesta informa por
`CrashReporter.log`, y `FirebaseCrashReporter` **además escribe en `logcat` cuando `BuildConfig.DEBUG`**,
con etiqueta `BOC`. Nunca el contenido del documento ni la credencial: solo el tipo de fallo, el código
de estado, el motivo que da el servicio y la **forma** de una respuesta —nombres de campo y tamaños—.

**Motivo**: la primera vez que la feature corrió en un móvil real, el propietario vio dos pantallas de
error y **el registro no decía absolutamente nada**. Seis sitios tragaban en silencio: los tres que
abren un PDF, la fuente de datos, el validador y el orquestador. Un proceso aislado muerto, un fichero
ilegible, un 400 del servicio y un resumen vacío se veían todos igual: una frase genérica en pantalla y
nada en el log.

Las dos causas que se acabaron encontrando —**D-032** y **D-033**— se encontraron **por el registro**,
no por las pruebas. Ninguna prueba automática podía verlas: todas usan dobles en la frontera con el
servicio, y el defecto estaba justo al otro lado.

**Lo que se registra y por qué es seguro**: los nombres de campo son nuestro esquema, no el documento.
`error.message` del proveedor habla de nuestra petición, no del contenido. Hay tres pruebas que
comprueban que una credencial `gsk_...` no aparece en ningún mensaje y que el cuerpo no se cita nunca.

### D-035 — El texto extraído se sanea de sustitutos sueltos

**Decisión**: `PdfTextNormalizer` elimina los sustitutos UTF-16 sin pareja, además de los caracteres de
control.

**Motivo**: un documento concreto devolvía **HTTP 400** del servicio, siempre el mismo, de forma
determinista — y el mismo documento con el mismo prompt y el mismo tamaño respondía 200 al enviarlo
desde `curl` con texto extraído por otra vía. La única diferencia era el extractor: pdfium puede
devolver, con fuentes poco habituales, un sustituto UTF-16 sin su pareja. Eso no es un carácter: al
serializarse produce UTF-8 inválido y el servicio rechaza la petición entera. Un código suelto en la
página cuatro basta para que un documento sea irresumible para siempre.

**Honestidad sobre el estado**: es la hipótesis que mejor encaja con lo observado, **no una causa
confirmada**. No se ha podido reproducir el 400 desde fuera de la aplicación. Lo que sí está hecho es
que, si vuelve a ocurrir, el registro imprime el motivo que da el servicio y lo zanja. Sanear medio
carácter antes de meterlo en un cuerpo JSON es correcto en cualquier caso.

**Descartado por comprobación, no por intuición**: que el 400 fuera un límite de cuota disfrazado. El
proveedor devuelve **429** para los límites; comprobado dos veces provocándolo a propósito.

### D-036 — Un reintento que no puede ejecutarse no cambia el error

**Decisión**: antes de reintentar una respuesta vacía, la fuente de datos consulta al coordinador de
cuota; si no hay margen, devuelve el rechazo **original**.

**Motivo**: al añadir el reintento de D-032 apareció en el registro esta secuencia:

```
20:52:36  groq: blank summary …
20:52:37  summary failed: QuotaMinute
```

El reintento salía disparado, chocaba con la cuota del mismo minuto —el proveedor la cobra al pedir— y
el lector acababa leyendo «se ha alcanzado el límite» cuando lo que había pasado era un resumen vacío.
Un arreglo que convierte un error en otro distinto es peor que no arreglar nada: manda a quien lo lee a
buscar en el sitio equivocado.

### D-037 — Un robot para el avatar, y la chispa para la función

**Decisión**: el círculo de la tarjeta lleva `ic_robot` (`smart_toy`); `ic_ai` (`auto_awesome`) se queda
en la pestaña y en los botones.

**Motivo**: ese círculo es el avatar de quien habla, y quien habla es una máquina. La chispa marca una
funcionalidad —«esto es la parte de IA»—, que es lo que hace falta en una pestaña o un botón. El
propietario lo propuso viendo la pantalla, y tiene razón: en el sitio donde uno espera una cara, una
chispa no dice nada.

**Y al ir a ponerlo apareció que `ic_ai` no se dibujaba desde la feature 004.** El trazado estaba en
escala 24 dentro del envoltorio de 960 con el grupo trasladado, así que se pintaba en una esquina
diminuta y el traslado lo sacaba del lienzo. No se veía en **ninguno** de sus cuatro usos —pestaña,
tarjeta y dos botones— y nada fallaba: no había icono y ya está. El origen del error está en la fuente:
el repositorio de Material Symbols mezcla las dos convenciones, y `auto_awesome` llega en escala 24 sin
`viewBox` mientras que `smart_toy` llega ya en 960.

**Comprobado en pantalla**, que es la única forma de comprobar esto: la chispa aparece ahora en la
pestaña y en el botón, y el robot en el círculo.

### D-019 — La defensa contra instrucciones incrustadas vive en el prompt, y se prueba

**Decisión**: el mensaje de sistema declara que el texto del documento es contenido no confiable, que
puede contener frases que parezcan instrucciones, y que no deben ejecutarse ni pueden cambiar el
formato de salida. Se toma **literal** del apartado 13 de la especificación técnica. Hay una prueba
con un documento que contiene una frase de ese tipo.

**Motivo**: los documentos los escribe un tercero y son públicos. FR-018 lo exige. Que haya una prueba
es lo que impide que la frase del prompt se erosione en una edición futura sin que nadie se entere.

---

## Dónde se guarda lo generado

### D-020 — La clave es la publicación, no un identificador derivado

**Decisión**: la tabla de resúmenes tiene como clave primaria la **clave externa de la publicación**.
El hash del documento, el modelo, la versión de prompt y la de esquema se guardan como columnas de
procedencia.

**Motivo**: se aparta de la especificación técnica, que proponía una clave derivada de todo eso a la
vez. Dos razones. Primera, la pantalla necesita **observar** el resumen de una publicación desde que
se abre, y en ese momento todavía no se conoce el hash del documento —puede no estar descargado—; con
una clave derivada no habría nada que observar. Segunda, un resumen por publicación es exactamente lo
que la interfaz quiere mostrar, y lo que dicen los supuestos de `spec.md`. Las columnas de procedencia
dan la misma garantía: si alguna no coincide con la actual, la fila está obsoleta (FR-035).

### D-021 — No se guarda el texto del documento

**Decisión**: solo se guarda el resumen. Ni tabla de páginas, ni tabla de búsqueda de texto completo.

**Motivo**: el texto solo hace falta durante la generación; regenerar vuelve a extraerlo, que es local
y no cuesta cuota. Guardarlo tendría dos costes reales: crecería sin tope —cientos de documentos en
texto son decenas de megas— y para acotarlo haría falta **la primera sentencia de borrado del
proyecto**, cuando la guía dice que ningún DAO declara una. La especificación técnica lo recomendaba
para no repetir trabajo en «Preguntar»; pero «Preguntar» es una feature aparte que decidirá su propio
almacén, y para entonces el documento seguirá en la caché.

**Alternativas descartadas**: crear ya la tabla de búsqueda de texto completo «para evitar una
migración conceptual». Sería código muerto sin ningún lector, y este proyecto tiene el precedente
contrario: `ComingSoonMessage` se eliminó al quedarse sin llamadores en vez de conservarse por si
acaso.

### D-022 — Base de datos a la versión 4, con migración automática

**Decisión**: `version = 4` y `AutoMigration(3, 4)`, con el esquema exportado a `app/schemas/`. Se
conservan las migraciones 1→2 y 2→3.

**Motivo**: añadir una tabla nueva es automigrable sin escribir SQL. Se conservan las anteriores
porque quien se salte versiones tiene que poder llegar de la 1 a la 4 de una vez. `build()` sigue
pelado: `fallbackToDestructiveMigration` vaciaría el boletín de quien ya tiene la aplicación.

**A diferencia de la feature 006, aquí no hay relleno de filas antiguas**: una tabla nueva empieza
vacía por definición, y no tener resumen es el estado normal de una publicación.

### D-023 — El aviso aceptado va en preferencias, no en la base de datos

**Decisión**: la aceptación del aviso de envío externo (FR-043 a FR-045) se guarda en
`SharedPreferences`, detrás de una interfaz `AiPreferences` en `data/source/local/`.

**Motivo**: es **un booleano por instalación**. Meterlo en Room costaría una tabla, un DAO y un
convertidor para guardar un bit. No es una dependencia nueva: `SharedPreferences` es de la plataforma.

**Alternativas descartadas**: DataStore, que sería una dependencia nueva para el mismo bit; y una
tabla de clave-valor en Room, que es la puerta de entrada a un cajón de sastre.

---

## Cómo se presenta

### D-024 — El resumen vive en el modelo de pantalla del detalle

**Decisión**: se amplía `PublicationDetailViewModel`; no se crea uno nuevo.

**Motivo**: el principio III dice una pantalla, un modelo de pantalla, y la pestaña es parte del
detalle. Además ya existe el precedente exacto: `onDocumentTabShown()` hace justo esto para la
pestaña Documento. `PublicationDetailUiState` gana `summary` y `noticeAccepted`, y el `combine` de
cinco fuentes pasa a seis con un anidamiento más.

**Alternativas descartadas**: un modelo de pantalla propio para la pestaña, como sugería la
especificación técnica. Resolverlo dentro de un elemento de lista es incómodo, necesitaría el mismo
argumento de navegación, y partiría en dos el estado de una sola pantalla.

### D-025 — El contrato copia el de `DocumentRepository`

**Decisión**:

```kotlin
interface AiSummaryRepository {
    fun observeSummary(externalKey: String): Flow<AiSummaryStatus>
    suspend fun generate(publication: Publication, force: Boolean): AppResult<AiSummary>
    fun observeNoticeAccepted(): Flow<Boolean>
    suspend fun acceptNotice()
}
```

**Motivo**: es el mismo problema que el documento oficial ya resolvió —una operación larga con fases,
que la pantalla observa mientras otra cosa la empuja— y el proyecto ya tiene la forma buena:
`DocumentRepositoryImpl` publica el progreso en un mapa de estados
(`DocumentRepositoryImpl.kt:46`) y `ensureLocalCopy` hace el trabajo. Copiar una solución que ya
funciona en la misma aplicación vale más que inventar otra.

**Alternativas descartadas**: que la generación devuelva un `Flow` de progreso, como proponía la
especificación técnica. Mezcla el resultado con el avance y no encaja con observar un resumen ya
guardado desde que se abre la pantalla.

### D-026 — Los fallos de IA tienen su propio tipo; `DomainError` no se toca

**Decisión**: `AiSummaryError` es una jerarquía sellada propia con ocho casos. `DomainError` se queda
con sus dos.

**Motivo**: `DomainError` lo consumen `when` exhaustivos por toda la aplicación; añadirle seis casos
que solo significan algo para esta pestaña obligaría a que la vista previa del documento y las demás
pantallas los contemplaran sin poder decir nada útil. El precedente está en `DocumentStatus.Failed`:
un estado de dominio puede llevar su propio error. Cada caso se corresponde con un mensaje concreto de
FR-040, y ser sellado hace que el compilador avise si mañana se añade uno y la pantalla lo olvida.

### D-027 — Las referencias de página se pueden seguir

**Decisión**: `Route.PdfViewer` gana un parámetro `page: Int = 0` y el visor se posiciona en él al
abrir.

**Motivo**: FR-021. El mecanismo **ya existe**: `PdfViewerScreen` guarda y restaura la página visible
con `scrollToPage()` para no perderla al rotar. Reutilizarlo cuesta un argumento con valor por
defecto, y convierte un chip decorativo en la comprobación que hace fiable a toda la feature.

**Cuidado documentado**: la barra inferior navega con `restoreState = true`, y en la feature 006 eso
se tragó en silencio un argumento de ruta. Aquí no aplica —el visor no es un destino de la barra
inferior— pero conviene no reintroducir ese patrón al añadir el parámetro.

### D-028 — La advertencia viaja dentro del texto compartido

**Decisión**: copiar y compartir anteponen al contenido la frase que identifica el origen y remite al
documento oficial.

**Motivo**: FR-025. Un resumen que sale de la aplicación pierde su marco: llega a un grupo de mensajes
sin la tarjeta, sin el icono y sin la pantalla que lo contextualizaba. Si la advertencia no va dentro
del texto, no va. Cuesta una línea y evita que un texto generado circule como si fuera el boletín.

### D-029 — `DetailTab.isComingSoon` se retira

**Decisión**: con `AI_SUMMARY` implementada, esa propiedad devolvería siempre falso. Se elimina, y se
actualiza `DetailTabTest`.

**Motivo**: una propiedad que ya no puede ser cierta es una afirmación falsa esperando a confundir a
alguien. `ComingSoonTab` **sigue existiendo**: la usa la pantalla Preguntar, que es la feature
siguiente.
