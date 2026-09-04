# Research: Resumen IA con proveedor nuevo

**Feature**: `009-resumen-gemini` | **Fase**: 0 | **Fecha**: 4 de septiembre de 2026

Las decisiones de esta fase resuelven las incógnitas técnicas de `spec.md`. Todo lo que aquí se
afirma sobre el servicio nuevo está **comprobado contra su documentación vigente**, no supuesto, y
donde la documentación no dice lo que hace falta, se dice que no lo dice.

**Numeración**: estas decisiones van de **D-101 a D-118**, y no de D-038 en adelante, a propósito. La
feature 007 tiene 37 decisiones (D-001 a D-037) que `CLAUDE.md` cita por número; empezar en 101 hace
imposible confundir «D-011, el esquema de Groq» con «D-105, el esquema de Gemini». Cuando una decisión
de aquí sustituye a una de allí, se dice explícitamente.

---

## El proveedor y el cable

### D-101 — Gemini Developer API con `gemini-3.5-flash-lite`

> **Sustituye a D-010** de la feature 007.

**Decisión**: el servicio pasa a ser la API de Gemini Developer, modelo `gemini-3.5-flash-lite`. El
identificador sigue viviendo en `AiSummaryConstants.MODEL_ID` y el acceso sigue detrás de una
interfaz, ahora `GeminiSummaryDataSource`.

**Motivo**: comprobado en la documentación del proveedor. El modelo se publicó el **21 de julio de
2026**, está en disponibilidad general —no en Preview, a diferencia de `qwen/qwen3.8-27b`—, admite
**1.048.576 tokens de entrada y 65.536 de salida**, y el propio proveedor lo anuncia como optimizado
para *document processing*, que es literalmente lo que esta funcionalidad hace. El propietario ya
tiene credencial de plan gratuito.

**Lo que esto compra, y es el motivo de la feature**: con 1.048.576 tokens de entrada, **cualquier
publicación del boletín entra completa**. La feature 007 estaba construida alrededor de 8.000 tokens
por minuto, y de ese techo salían el troceado por páginas, el presupuesto de 4.500 contra 1.800, y
tres de sus defectos históricos: JSON cortado, resúmenes en blanco y reintentos que chocaban con la
cuota del mismo minuto.

**Consecuencia inmediata y deseada**: cambiar `MODEL_ID` marca **obsoletos, no borrados**, todos los
resúmenes ya guardados, vía `AiSummaryRepositoryImpl.isStale()`. Es FR-035 de la 007 funcionando
como se diseñó. Ver D-114.

**Alternativa descartada**: seguir en Groq con un modelo mayor. No resuelve nada: el techo que
estorbaba era la **cuota por minuto de la organización**, no el contexto del modelo.

### D-102 — REST con OkHttp, y ningún SDK

**Decisión**: se habla con el servicio escribiendo la petición HTTP a mano, con el `OkHttpClient`
compartido y `kotlinx-serialization-json`. **Cero dependencias nuevas**; `gradle/libs.versions.toml`
no se toca.

**Motivo**: tres razones, en orden de peso.

1. **La frontera sigue siendo HTTP, y por eso las pruebas sobreviven.** Las veintiuna pruebas de
   contrato de `OkHttpGroqSummaryDataSourceTest` corren sobre MockWebServer con TLS. Cambiando de URL
   y de cuerpos JSON siguen valiendo. Con un SDK, la frontera pasaría a ser una superficie de tipos y
   habría que rehacerlas con dobles.
2. **No hace falta nada.** OkHttp 5.5.0 y `kotlinx-serialization-json` 1.8.1 ya están declarados.
3. **La restricción del catálogo.** Toda dependencia nueva exige justificarse en `plan.md`. Aquí no
   habría con qué: la petición es un `POST` con un cuerpo JSON.

**Alternativa descartada — el SDK que traía la documentación aportada**:
`com.google.ai.client.generativeai:generativeai:0.9.0`. **Está deprecado**; su repositorio se llama
`google-gemini/deprecated-generative-ai-android` y Google redirige a Firebase AI Logic. Descartado sin
más análisis.

**Alternativa descartada, y esta sí era viable — Firebase AI Logic**: `com.google.firebase:firebase-ai`
está dentro del Firebase BOM 34.18.0 que el proyecto ya usa, así que entraría **sin versión**, admite
`gemini-3.5-flash-lite` y funciona en plan Spark gratuito. Y tiene una ventaja real que conviene dejar
escrita: **la credencial no viajaría en el APK**. Se descarta por el coste: convierte la frontera en
una de SDK y obliga a rehacer las veintiuna pruebas con dobles sobre tipos de Firebase —que además,
como ya anota `CLAUDE.md`, necesitan un `FirebaseApp` real bajo Robolectric—, más habilitar la API en
consola y configurar App Check. Queda anotada como el camino a seguir **si algún día la credencial
dentro del APK deja de ser aceptable**.

**Alternativa descartada — `pdfbox-android`**, que también traía la documentación aportada: no hace
falta. El texto se extrae desde la feature 007 con `androidx.pdf`, en proceso aislado, y añadir un
segundo lector de PDF sería duplicar una capacidad que ya está y probada.

**Alternativa descartada — el `secrets-gradle-plugin`**: el patrón de `providers.fileContents` +
`buildConfigField` que ya existe hace exactamente lo mismo sin plugin, y además es el que respeta la
caché de configuración.

### D-103 — Interactions API, no `generateContent`

**Decisión**: `POST https://generativelanguage.googleapis.com/v1beta/interactions`.

**Motivo**: comprobado en la documentación. La Interactions API está en **disponibilidad general
desde junio de 2026** y es lo que el proveedor recomienda para proyectos nuevos; `generateContent`
queda etiquetada como **legado**, aunque sigue plenamente soportada. Además da dos cosas que la otra
no: `store: false` (ver D-107) y un `status` de la interacción —`completed`, `incomplete`,
`budget_exceeded`, `failed`— que diagnostica mejor que el `finish_reason` que se usa hoy (ver D-117).

**Lo que hay que saber para no tropezar**: la Interactions API tuvo **cambios incompatibles en mayo de
2026** y el esquema anterior se retiró el **8 de junio de 2026**. Lo que hoy existe es el esquema
nuevo: la respuesta trae un array `steps` —no `outputs`—, y el texto vive en
`steps[].content[0].text` del paso de tipo `model_output`. La cabecera `Api-Revision` que gobernaba
aquella migración ya no hace falta y **no se envía**.

**Alternativa descartada**: `POST /v1beta/models/{model}:generateContent`. Más plana de parsear y con
más ejemplos por ahí, y la propia documentación la sigue llamando estable para producción. Se descarta
porque está marcada como legado y porque no ofrece retención cero. El coste de equivocarse es bajo y
está acotado a propósito: cambiar de superficie es reescribir `GeminiDtos.kt` y el cuerpo de
`OkHttpGeminiSummaryDataSource`, dos ficheros, sin tocar nada más.

### D-110 — El techo de salida sube a 8.000 tokens, y ahora eso es gratis

> **Sustituye a la mitad de D-033** de la feature 007.

**Decisión**: `generation_config.max_output_tokens = 8000`.

**Motivo**: es la corrección de raíz de un defecto de la 007, y depende de un detalle de facturación
que cambia con el proveedor. **Groq cobraba `entrada + max_completion_tokens` al pedir, se gastara o
no** —su propio 429 lo decía: «Limit 8000, Used 7346, Requested 6475»—, así que subir el techo de la
respuesta acercaba el límite y había que dejarlo en 1.800. **Gemini cobra la salida realmente usada.**
Con 65.536 disponibles, un techo holgado no cuesta nada y cierra para siempre la familia de fallos en
la que el JSON llegaba cortado, no parseaba, y el lector leía «no se ha podido construir un resumen
fiable» — un problema nuestro disfrazado de fallo del servicio.

**Por qué 8.000 y no 65.536**: un resumen real de la 007 llegó a 1.625 tokens. Ocho mil es casi cinco
veces eso; dejar el techo en el máximo del modelo no compra nada más y desactiva un aviso útil: si una
respuesta llegara a tocar 8.000, es que algo va mal en el prompt y conviene que se note.

---

## Lo que se envía

### D-104 — Se retira el presupuesto de tokens; queda un guardarraíl de caracteres

> **Sustituye a D-007 y a la otra mitad de D-033** de la feature 007.

**Decisión**: se envía el texto de **todas** las páginas con texto. Desaparecen
`SummaryBudget.MAX_DOCUMENT_TOKENS`, `TARGET_REQUEST_TOKENS`, `estimateTokens()`,
`CHARACTERS_PER_TOKEN` y `PROMPT_OVERHEAD_TOKENS`. Se conserva **un único tope duro**:
`DocumentText.MAX_CHARACTERS = 480_000`.

**Motivo del cambio**: el presupuesto existía para no pasarse de 8.000 tokens por minuto. Ese límite
ya no está. La estimación además nunca fue buena —`CHARACTERS_PER_TOKEN = 3.2` estaba calibrado a ojo
para el tokenizador de Qwen, no medido— y mantenerla sería mantener una aproximación que ya no decide
nada.

**Motivo de conservar el tope**, que es la parte discutible y por eso va escrita: 480.000 caracteres
son unos **109.000 tokens**, el **10 %** de la ventana de entrada. Y a diferencia del presupuesto que
sustituye, ese número está **medido**: 4,39 caracteres por token sobre texto del BOC en español, contra el servicio real el 4 de
septiembre de 2026 —6 036 caracteres cobrados como 1 376 tokens—. El proveedor anterior se estimaba a
3,2 y nunca se midió. Cubre unas ciento noventa páginas de boletín a 2.500
caracteres por página, contra las cien que SC-001 declara como envolvente ordinaria. Es decir: **en uso
normal no se alcanza nunca**.

**Lo que queda sin confirmar** es si una petición en el techo cabe en el límite de **tokens por minuto**
del plan gratuito, porque ese límite tampoco se publica. Si estuviera por debajo de 110.000, el tope hay
que bajarlo: un documento en el techo sería irresumible para siempre y ninguna prueba lo vería. Es la
tarea T001a. No es racionamiento; es lo que evita que una publicación
patológica de mil páginas tire la petición entera.

**Y compra algo concreto**: mantiene **vivo y probado** el camino de cobertura parcial —FR-028,
FR-029, FR-030 y FR-031 de la 007, el estado `Generating` parcial, los dos `plurals` de `strings.xml`
y tres pruebas instrumentadas de `AiSummaryTabTest`—. Retirarlo del todo habría significado borrar
todo eso.

**Consecuencia de forma**: `SummaryBudget` se renombra a `DocumentText` y `SelectedText` a
`RenderedDocument`. Se conserva el bucle que toma **páginas enteras** mientras quepan, el corte de la
primera página por límite natural del texto, y el marcador `[PÁGINA n]`. Se va solo la aritmética.
`SummaryBudgetTest` pasa a `DocumentTextTest` y baja de once afirmaciones a seis: las cinco que
comprobaban techos de tokens y la estimación conservadora dejan de tener objeto.

### D-111 — Los nombres dicen de quién es cada cosa

**Decisión**: dentro de `data/source/remote/`, **los ficheros que describen al proveedor llevan su
nombre y los que describen nuestro formato, no**. `GeminiDtos.kt` y
`OkHttpGeminiSummaryDataSource.kt` hablan de Gemini. `SummarySchema.kt`, `SummaryPayloadDtos.kt`,
`SummaryPromptFactory.kt`, `SummaryValidator.kt` y `DocumentText.kt` hablan del BOC.

**Motivo**: `GroqDtos.kt` mezclaba dos cosas de vida muy distinta. Los tipos de cable —petición,
respuesta, `usage`— son del proveedor y mueren con él. `GroqSummaryPayload` y sus cinco sub-DTO son
**nuestro** formato: son lo que se serializa en la columna `summary_json` y lo que se vuelve a leer al
decodificar una fila guardada. Tenerlos en el mismo fichero hacía que un cambio de proveedor
**pareciera** tocar el formato almacenado, cuando no lo toca.

**Regla que se deriva y hay que respetar**: al renombrar `GroqSummaryPayload` a `SummaryPayload`,
**ni un nombre de propiedad puede cambiar**. kotlinx serializa por nombre de propiedad, así que
renombrar la clase es inocuo y renombrar un campo dejaría ilegibles todas las filas guardadas. Lo
vigila una prueba que decodifica un `summary_json` escrito por la versión anterior.

### D-112 — Tope de diez elementos por sección, y el validador como última puerta

**Decisión**: `maxItems: 10` en las seis propiedades de lista del esquema, una frase en el prompt
pidiendo priorizar lo relevante cuando haya más, y **el validador recorta a diez de todas formas**
añadiendo una advertencia a `warnings`.

**Motivo**: es un problema **nuevo**, creado por esta feature. Hasta ahora solo se enviaban las
primeras páginas que cabían, así que ninguna ficha podía crecer demasiado; con el documento completo,
un presupuesto de treinta páginas puede sustentar decenas de puntos clave y la tarjeta del §20 del
documento de diseño no está pensada para eso. Diez por sección deja la ficha en un desplazamiento
razonable.

**Por qué el validador además recorta**: porque el esquema es una petición, no una garantía. La
decisión de la casa es que el validador es la última puerta antes de mostrar, y ya corrige la
cobertura que el servicio afirma. Recortar ahí es una línea y se prueba sin servicio.

**Por qué se advierte y no se descarta en silencio**: descartar veintiocho de treinta y ocho puntos
clave de un boletín oficial sin decirlo sería la misma media verdad que esta feature viene a eliminar.
Lo exige FR-007 y lo mide SC-013.

**Alternativas descartadas**: un «ver más» plegable —interacción nueva, textos nuevos, estado que
recordar al girar el móvil y tres o cuatro pruebas instrumentadas a 46 s cada una—; y no poner tope,
que contradice «entender una publicación de un vistazo», la User Story 1 de la feature 007.

### D-116 — El saneado de sustitutos UTF-16 se queda, aunque su causa fuera de Groq

> **Revisa D-035** de la feature 007 y lo confirma.

**Decisión**: `PdfTextNormalizer.stripControlCharacters` sigue eliminando los sustitutos UTF-16 sin
pareja. No se toca.

**Motivo**: la 007 lo introdujo porque Groq devolvía un HTTP 400 determinista con un documento
concreto, y anotó honestamente que era «la hipótesis que mejor encaja con lo observado, no una causa
confirmada». Sea o no ésa la causa, **el hecho subyacente no depende del proveedor**: un code unit
UTF-16 sin pareja produce UTF-8 inválido al serializar el cuerpo, y ningún servicio HTTP tiene por qué
aceptarlo. Retirarlo para «ver si con Gemini hace falta» sería cambiar un defecto conocido por uno
intermitente. Se queda, y sus dieciocho pruebas con él.

---

## El esquema

### D-105 — El esquema se conserva verbatim, sin el envoltorio

> **Sustituye a D-011** de la feature 007. **Conserva D-030 intacta.**

**Decisión**: el objeto `schema` de hoy se conserva **tal cual** —doce propiedades, las cinco
definiciones en `$defs` con `$ref`, `additionalProperties: false`, `required` con las doce, y
`plainLanguageSummary` **la última** con `maxLength: 900`— y se coloca dentro de
`response_format: { type: "text", mime_type: "application/json", schema: { … } }`. Se retira solo el
envoltorio de estilo OpenAI: `{"type":"json_schema","json_schema":{"name":…,"strict":true}}`.
`SCHEMA_VERSION` sube a `boc-summary-schema-v3`. Se añade `maxItems: 10` (D-112).

**Motivo**: comprobado en la documentación. Gemini admite **`$defs`, `$ref` y `additionalProperties`**
desde el anuncio de soporte de JSON Schema de noviembre de 2025, y en ese mismo anuncio incorporó
**ordenación implícita de propiedades**: el orden de generación es el de declaración. Eso significa
que la invariante que la 007 pagó con una medición en móvil real —la prosa la última, porque cuando se
cortaba en 1024 caracteres **todo lo declarado después venía vacío**— sobrevive sin escribir nada
nuevo.

**Este es el punto de mayor riesgo del plan, y lleva plan B.** Hay informes de rechazos de
`$defs`/`$ref` en algunos contextos de la API. Si el servicio devuelve un 400 por el esquema:

1. **Aplanar las cinco definiciones en línea.** Se usan dos o tres veces cada una; cuesta unas
   cuarenta líneas y **no cambia ni un nombre de campo**, así que no afecta a lo guardado ni a
   `SummaryPayload`.
2. **Si además el orden no se respetara**, añadir `propertyOrdering` con las doce propiedades en el
   orden actual.

Nada de esto se puede comprobar con un doble: va en `quickstart.md` §3 bis (ver D-118).

**Consecuencia que se pierde y no se echa de menos**: `strict: true` era de Groq y no tiene
equivalente literal. Su papel —que las secciones vacías se pudieran ocultar con confianza y que las
referencias se pudieran validar antes de mostrarlas— lo cubren igual `required` con las doce
propiedades y `additionalProperties: false`, más el validador, que no se fía del servicio en ningún
caso.

**Lo que no cambia y conviene decir alto**: `GroqSummarySchemaTest` afirmaba que la prosa va la última
y que las seis listas van antes. Esa prueba se conserva, renombrada a `SummarySchemaTest`, porque lo
que vigila —que nadie ordene esas propiedades alfabéticamente y vacíe la ficha— sigue siendo cierto
con el proveedor nuevo.

---

## Los ajustes del modelo

### D-106 — `thinking_level: "minimal"`, y ningún parámetro de muestreo

> **Sustituye a D-012** de la feature 007.

**Decisión**: `generation_config.thinking_level = "minimal"`. **No** se envían `temperature`,
`top_p` ni `top_k`.

**Motivo del `thinking_level`**: es exactamente la misma lección que `reasoning_effort: "none"` costó
en la 007, con otro nombre. Comprobado en la documentación: en Gemini 3.x el valor por defecto de
`thinking_level` es **`medium`**, los niveles son `minimal`, `low`, `medium` y `high`, y el
razonamiento se factura. Resumir un boletín no necesita razonamiento extendido, así que no enviarlo
sería pagar tokens que nadie ve. La documentación advierte además que en Flash-Lite «`minimal` no
garantiza que el razonamiento esté apagado; el modelo puede razonar mínimamente en tareas complejas»
— aceptado, es el mínimo disponible.

**Y sigue haciendo falta `encodeDefaults = true`** en el `Json`, por el mismo motivo que en la 007:
kotlinx omite por defecto los valores iguales al default, y `store = false` y
`thinkingLevel = "minimal"` son justamente eso. Sin la bandera no se enviarían y el proveedor aplicaría
sus propios valores por defecto, que son los contrarios en ambos casos.

**Motivo de retirar los parámetros de muestreo**: la documentación de Gemini 3.5 lo dice
literalmente —«recomendamos encarecidamente no cambiar los valores por defecto»— y la ficha de
`gemini-3.5-flash-lite` añade que no admite valores propios de temperatura, top-K ni top-P. Enviar
`temperature: 0.2`, que es lo que hace hoy, sería en el mejor caso ruido y en el peor un 400.

### D-107 — `store: false`

**Decisión**: `store: false` en cada petición.

**Motivo**: comprobado en la documentación. **`store` vale `true` por defecto**: el servicio conserva
el objeto de la interacción para habilitar conversación con estado y ejecución en segundo plano, y en
cuenta gratuita lo retiene **un día**. Esta funcionalidad no usa ninguna de las dos cosas —una
petición por publicación, sin hilo—, así que la retención sería coste sin beneficio. Ponerlo a `false`
es retención cero.

**Lo que `store: false` desactiva y no se echa de menos**: `previous_interaction_id` y
`background: true`. Ninguno se usa.

**Lo que `store: false` no arregla, y por eso está en la especificación y no solo aquí**: en el plan
gratuito el proveedor puede usar lo enviado para mejorar sus modelos. Eso es independiente de la
retención de la interacción. Lo que viaja es un documento **público y oficial**, ya publicado por el
Gobierno de Cantabria, así que no expone a nadie; pero el propietario decidió decirlo en el aviso de
todas formas. Ver FR-031 y D-113.

---

## La cuota

### D-108 — Contador propio, con ventanas deslizantes en memoria

> **Sustituye a D-015** de la feature 007.

**Decisión**: `GeminiRateLimitCoordinator` deja de leer cabeceras y lleva la cuenta él mismo: dos
ventanas deslizantes de marcas de tiempo sobre `TimeProvider`, una de sesenta segundos contra
`REQUESTS_PER_MINUTE` y una de veinticuatro horas contra `REQUESTS_PER_DAY`. `verdict()` deja de
recibir argumentos.

**Motivo**: Gemini **no manda cabeceras de cuota**. Groq las mandaba en cada respuesta —con nombres
engañosos que la 007 tuvo que documentar: `x-ratelimit-limit-requests` era por día y
`x-ratelimit-limit-tokens` por minuto—, y de ahí salía el margen disponible. Sin ellas, o la
aplicación lleva la cuenta o incumple FR-020 —no lanzar consultas cuando ya sabe que no hay margen— y
FR-022 —distinguir el límite corto del diario—. Y sin FR-022, el estado `QuotaDay` de la pestaña se
queda sin camino real, con su prueba instrumentada sin cubrir.

**Por qué la ventana diaria es deslizante de veinticuatro horas y no un día de calendario**: el
proveedor repone su cupo diario en su propia zona horaria, que no es la del móvil. Una ventana
deslizante **nunca permite más de lo que el proveedor permite**, cualquiera que sea su momento de
reposición, y no exige suponer ninguna zona horaria. Es más conservadora que el reposo real en el
caso peor, y se corrige sola en veinticuatro horas. El mensaje que ve la persona —«Se ha alcanzado el
límite de resúmenes de hoy. Inténtalo mañana»— sigue siendo cierto bajo esa regla.

**Por qué los contadores NO se persisten**, y esto es una decisión, no un olvido: un reinicio del
proceso olvida la cuenta. Guardarla en disco sería trabajo y una fuente de estado más para proteger un
límite que **una persona pulsando un botón no puede alcanzar**: 1.500 peticiones en un día son una cada
cincuenta y siete segundos durante veinticuatro horas seguidas. La ventana del minuto, que es la que
sí protege de una ráfaga —alguien machacando «volver a generar»—, no necesita sobrevivir a un
reinicio, porque un reinicio tarda más que el minuto. Y el 429 del proveedor sigue siendo la autoridad
final y **sí** se recuerda durante el resto del proceso. «Ante la duda, gana la opción más simple.»

**Lo que se conserva de la clase anterior, y es la mayor parte**: `serialised()` con `Mutex` —una
petición a la vez en toda la aplicación—, el sellado `QuotaVerdict` con sus tres casos, el backoff de
1 s / 2 s / 4 s con dispersión de hasta 500 ms, y la regla de D-036 de la 007: antes de reintentar se
vuelve a consultar el veredicto, y si ya no hay margen se devuelve **el rechazo original** en lugar de
convertir un fallo en otro.

**Lo que se retira**: `parseDurationMillis` con sus tres formas (`7.66s`, `2m59.56s`, número desnudo),
las cinco constantes de cabecera, y el parámetro `estimatedTokens` de `verdict()`, que ya no existe.

**Lo que no se hace, y se dice para que conste**: no se lleva cuenta de **tokens** por minuto. Hacerlo
resucitaría la estimación que D-104 acaba de retirar. El guardarraíl garantiza que una petición cabe
en el minuto, y una segunda petición muy grande en el mismo minuto se llevaría un 429 que se respeta.

### D-109 — El 429 se clasifica por el retraso que pide, no por el texto que trae

> **Sustituye a la parte de D-016** de la feature 007 que trataba los códigos de respuesta.

**Decisión**: ante un `429`, el retraso pedido se lee en este orden —cabecera `retry-after`;
`error.details[]` con un campo de retraso si viene; y si no hay ninguno, sesenta segundos por
defecto—. Si ese retraso supera **quince minutos**, se trata como `QuotaDay`; si no, como
`QuotaMinute`. El veredicto del contador propio manda si ya declaraba el día agotado.

**Motivo**: el 429 de Gemini es `RESOURCE_EXHAUSTED` tanto si lo agotado es el cupo del minuto como
el del día, y la documentación **no** especifica ni una cabecera `retry-after` ni la forma exacta del
cuerpo con el detalle de reintento. Hay que decidir con lo que llegue. Un retraso es un **número**:
no depende del idioma ni de la redacción del proveedor, y se prueba fijando un valor. Buscar palabras
en `error.message` sería frágil ante cualquier cambio de redacción, y además construir sobre un texto
que FR-027 prohíbe mostrar.

**Los demás códigos**, sin cambio de criterio respecto a la 007: `401` y `403` → `NotConfigured`, sin
reintento. `5xx` → `HttpError` reintentable, máximo tres intentos con el backoff. Cualquier otro →
`HttpError` sin reintento. `IOException` → `Network`. `CancellationException` se **repropaga** siempre.

### D-115 — Los límites del plan gratuito: valores documentados, marcados como pendientes

**Decisión**: `REQUESTS_PER_MINUTE = 30` y `REQUESTS_PER_DAY = 1_500`, en el companion de
`GeminiRateLimitCoordinator`, con un comentario que dice de dónde salen y que hay que confirmarlos.

**Motivo, y hay que ser honesto con él**: **Google ya no publica los límites del plan gratuito en su
documentación**. Su página de límites remite al panel de AI Studio del propio proyecto, que exige
credenciales y no se puede leer desde aquí. Los valores elegidos son los documentados históricamente
para los modelos Flash-Lite. Son plausibles y conservadores frente a los de Groq (1.000 peticiones por
día), pero **no están confirmados contra la cuenta del propietario**.

**Cómo se mitiga**: (a) las dos cifras son constantes en un único sitio, así que corregirlas es una
línea; (b) el 429 del proveedor es la autoridad final y se respeta pase lo que pase, así que unas
cifras demasiado generosas no rompen nada —solo hacen que la primera negativa venga del servicio en
lugar del contador—; y (c) `quickstart.md` §0 pide leerlas en el panel y ajustarlas si no coinciden.

**Efecto en SC-007** («al menos un resumen por minuto de forma sostenida»): con treinta peticiones por
minuto se cumple con enorme margen. Con Groq el margen era exactamente uno.

---

## Lo que se guarda y lo que se dice

### D-114 — La base de datos no cambia, y los resúmenes quedan obsoletos en masa

**Decisión**: `BocDatabase` **se queda en la versión 4**. Ninguna migración, ninguna columna nueva,
ningún esquema exportado nuevo.

**Motivo**: `ai_summaries` guarda la procedencia en `model_id`, `prompt_version` y `schema_version`,
que son columnas de texto **agnósticas del proveedor**. Nada del cambio necesita una forma distinta.
Que una sustitución de proveedor no toque la base de datos es la consecuencia de haber guardado la
procedencia como datos y no como estructura.

**Consecuencia aceptada y deseada**: al cambiar las tres constantes, `isStale()` marca **todas** las
filas existentes como obsoletas. Lo ve quien tenga resúmenes hechos: aparecen con el aviso «se hizo
con una versión anterior del documento» y con la opción de regenerar. **No se borra ninguna** —el
proyecto no tiene una sola sentencia de borrado en sus cinco DAO y esta feature no la introduce— y no
se regenera nada por cuenta propia, porque eso gastaría cuota en publicaciones que nadie ha pedido.

**Un detalle de compatibilidad que hay que probar**: `AiSummaryEntity.decode()` deserializa
`summary_json` en el DTO del payload. Renombrar la clase a `SummaryPayload` es inocuo porque kotlinx
serializa por nombre de propiedad, pero **una fila escrita por la versión anterior tiene que seguir
leyéndose**. Lleva prueba de regresión.

### D-113 — El aviso reaparece una vez: la clave de la preferencia se versiona

**Decisión**: la clave de `SharedPreferencesAiPreferences` pasa de `ai_notice_accepted` a
`ai_notice_accepted_v2`. La frase nueva se añade **dentro de `ai_notice_body`**, no como un texto
aparte.

**Motivo de versionar**: el aviso cambia de contenido —ahora dice que el servicio puede usar el texto
de ese documento público para mejorar sus modelos— y quien ya lo había aceptado nunca leyó esa frase.
La transparencia era el motivo de la decisión del propietario; dejar la clave la dejaría a medias.
Cuesta una línea, y FR-045 de la 007 sigue cumpliéndose: el aviso se muestra **como máximo una vez**,
por dispositivo y por versión del aviso.

**Motivo de ampliar `ai_notice_body` en vez de añadir un texto nuevo**: así `AiNoticeSheet.kt` **no se
toca** y la feature no modifica ni un componible. Una frase más en una hoja inferior no cambia el
diseño del §20.

**Consecuencia para las pruebas**: `AiPreferencesTest` gana una afirmación de regresión —la clave
antigua no se lee— que falla antes del cambio, como exige el principio V.

### D-117 — El registro cambia de prefijo y gana el `status`

**Decisión**: el prefijo de `CrashReporter.log` pasa de `"groq: "` a `"gemini: "`. Se conservan los
dos helpers de anonimización, `describe()` y `reasonFrom()`. Se **añade** el `status` de la interacción
a las líneas de fallo.

**Motivo**: la lección de D-034 de la 007, escrita en `CLAUDE.md`: los dos defectos que de verdad
rompían el Resumen IA en un móvil no los podía ver ninguna prueba automática —todas usan dobles en la
frontera y el defecto estaba justo al otro lado— y los encontró el registro. Cambiar de proveedor
vuelve a poner esa frontera en juego, así que el registro es lo primero que tiene que funcionar.

**Lo que el proveedor nuevo regala en diagnóstico**: el `status` de la interacción distingue
`completed` de `incomplete`, `budget_exceeded`, `failed` y `cancelled`. Con Groq había que deducirlo de
`finish_reason` y de contar campos vacíos. Un `incomplete` o un `budget_exceeded` con contenido vacío
se traduce a `Malformed` en pantalla —que es lo único que se puede decir en lenguaje corriente— pero
**en el registro se dice cuál era**.

**Lo que sigue prohibido, y es la prohibición más importante**: **ningún interceptor de registro a
nivel de cuerpo** en el cliente de IA. De una respuesta se registra su *forma* —nombres de campo y
tamaños— y del servicio su `error.message`, truncado, que habla de nuestra petición. Nunca la
credencial, nunca el contenido del documento.

---

## Un defecto heredado que salió al probar en dispositivo

### D-119 — Una cancelación no puede llegar como problema de red

**Decisión**: `currentCoroutineContext().ensureActive()` es la **primera** línea del
`catch (IOException)` de `OkHttpGeminiSummaryDataSource.execute()`, que pasa a ser `suspend` para
poder llamarla.

**El defecto, observado en un móvil el 4 de septiembre de 2026**: alguien pulsó Atrás mientras se
generaba un resumen y el registro dijo

```
gemini: network: SocketException: Software caused connection abort
summary failed: Offline
```

No había ningún problema de conexión: la persona se fue.

**La causa**: `Call.execute()` **bloquea**. Cancelar la corrutina no lo interrumpe con una
`CancellationException` —le rompe el socket, y lo que sale es una `IOException`—, así que el
`catch (CancellationException)` que hay justo al lado no se dispara nunca y el `catch (IOException)`
traduce la marcha de la persona en `Network` → `Offline`.

**Y no se queda en el registro**: `fail()` publica `Failed(Offline)`, y en `observeSummary` el estado
en curso **gana** al resumen almacenado —`running ?: stored ?: Idle`— sobre un repositorio que es
`single`. Al volver a esa publicación en la misma sesión se lee «No hay conexión. El resumen podrá
generarse cuando vuelvas a estar conectado.» de un fallo que nunca ocurrió.

**Es un defecto heredado, no introducido por esta feature**: `OkHttpGroqSummaryDataSource` tenía el
mismo `catch` sin la comprobación. Lo que hizo esta feature fue hacerlo visible, porque el proveedor
nuevo tarda lo suficiente como para que alguien se vaya mientras espera.

**Lo bonito del arreglo es lo poco que hace falta**: `generate()` ya trataba la cancelación
correctamente —limpia el estado con `publish(key, null)`, no la reporta y la repropaga, con FR-006
citado en el comentario—. La máquina estaba construida para este caso y una línea que faltaba la
desactivaba entera.

**Prueba de regresión**: `leaving while a request is in flight is a cancellation and not an offline
error`. Es la única de esa clase que usa `runBlocking` y dispatchers de verdad, a propósito: lo que
comprueba es una carrera entre un hilo bloqueado en un socket y una cancelación, y en tiempo virtual
no existe. Falla antes del arreglo con `network: StreamResetException: stream was reset: CANCEL`.

**Lección general, y por eso está en `CLAUDE.md`**: cualquier llamada bloqueante dentro de una
corrutina tiene este agujero. Un `catch (IOException)` sin `ensureActive()` convierte «me he ido» en
«no hay red».

---

## Cómo se comprueba

### D-118 — La frontera con el servicio se atraviesa de verdad, una vez

**Decisión**: `quickstart.md` lleva un **§3 bis nuevo** con la verificación manual contra el servicio
real, y la anterior queda invalidada por completo.

**Motivo**: el §3 bis de la 007 terminaba diciendo «no hace falta repetirlo salvo que cambie el
esquema o el modelo». Esta feature cambia **los dos**. Y lo que allí se había verificado era
específico de Groq: que `qwen/qwen3.8-27b` aceptaba el esquema estricto, que la respuesta traía los
doce campos, y que las cabeceras de cuota significaban lo que D-015 decía.

**Qué hay que comprobar, y por qué justo eso**: cada punto corresponde a un riesgo que ninguna prueba
con dobles puede ver.

| Qué | Por qué |
|---|---|
| El esquema se acepta tal cual: HTTP 200 | Es el riesgo principal del plan (D-105). Si sale 400, entra el plan B |
| Prosa **y** listas estructuradas llenas en una publicación con plazos e importes | Comprueba la ordenación implícita. El síntoma de D-030 era exactamente lo contrario |
| `usage.total_thought_tokens` bajo o cero | Comprueba que `thinking_level: "minimal"` se aplicó y no se está pagando razonamiento invisible (D-106) |
| Un plazo relativo se conserva literalmente | FR-016 de la 007. Es comportamiento del modelo y cambia con el modelo |
| Cero líneas `gemini:` con un documento sin texto | FR-015, y el modelo nuevo no relaja esa puerta |
| `grep -cE 'AIza\|AQ\.A'` sobre el registro da cero | **Las credenciales de Gemini tienen dos formatos** y hay que buscar los dos: el clásico empieza por `AIza` y el que se emite hoy, por `AQ.` —comprobado contra la clave del propietario—. Ninguno es el `gsk_` de Groq, y buscar el prefijo equivocado es cómo se da por limpio un repositorio que no lo está |

**Lo que esto no sustituye**: las cuatro puertas de calidad. Es lo que se hace **además**.
