# Research: El documento se envía entero, no su texto

**Feature**: `010-gemini-sdk-oficial` | **Fase**: 0 | **Fecha**: 5 de septiembre de 2026

Este documento recoge lo que se decidió antes de escribir código y **por qué**, con lo que se
comprobó de verdad y lo que se dio por supuesto marcado como tal.

La numeración empieza en **D-201** y no continúa la de la 009 (D-101…D-119) ni la de la 007
(D-001…D-037). El motivo es el mismo que dio la 009: dentro de un año, «D-105, el esquema de Gemini»
y «D-214, el esquema por la librería» tienen que poder distinguirse sin abrir los dos ficheros.

Varias de estas decisiones **sustituyen** a decisiones anteriores. Cuando es así, se dice arriba y
con el número exacto, porque una decisión revocada en silencio es peor que una decisión mala.

---

## El transporte

### D-201 — La librería oficial de Kotlin, en lugar del cliente escrito a mano

> **Sustituye a D-102** de la feature 009.

**Decisión**: `com.google.genai:google-genai-kotlin:1.0.0`, la librería oficial de Google, en el
lugar que hoy ocupa `OkHttpGeminiSummaryDataSource`.

**Motivo**: la feature 009 descartó los SDK por tres razones, y hoy solo una sigue en pie.

1. *«El SDK de la documentación está deprecado»* — cierto y sigue siéndolo, pero se refería a
   `com.google.ai.client.generativeai`, que no es este. `google-genai-kotlin` es el que Google
   publica hoy: **1.0.0, subido a Maven Central el 2 de septiembre de 2026**, tres días antes de
   escribir esto, con variantes `-android` y `-jvm`. Comprobado en su `maven-metadata.xml`.
2. *«Firebase AI Logic convierte la frontera HTTP en una frontera de SDK»* — sigue siendo verdad de
   Firebase AI Logic, y además **no expone la Files API**, que es justo lo que esta feature necesita.
   Ver D-202.
3. *«Obligaría a rehacer las veintiuna pruebas de contrato que corren sobre MockWebServer»* — **esta
   ya no se sostiene.** El constructor de la librería acepta
   `HttpOptions(baseUrl = …)`, así que MockWebServer sigue siendo la frontera de prueba. Es la
   diferencia concreta entre entonces y ahora, y es la que desbloquea la decisión.

Y por encima de las tres, un motivo nuevo que en la 009 no existía: **la Files API**. Es la que
permite subir el documento una vez y referenciarlo después, y sin ella la pantalla Preguntar
—la feature siguiente— reenviaría el boletín entero en cada pregunta.

**Alternativa descartada**: seguir a mano y escribir nosotros la Files API. La subida reanudable son
dos llamadas con cabeceras `X-Goog-Upload-*` más el sondeo de estado; unas ciento cincuenta líneas.
Se descartó porque el coste de mantenerlas es permanente y el beneficio —cero dependencias— es real
pero se paga una sola vez. Se documenta como el plan B si D-224 sale mal.

**Consecuencia que no se puede maquillar**: la librería arrastra `google-auth-library-oauth2-http`
como dependencia obligatoria, y con ella Guava. Ver D-205 y D-223.

---

### D-202 — Firebase AI Logic sigue descartado, y ahora por un motivo distinto

**Decisión**: no.

**Motivo**: en la 009 se descartó por el coste de rehacer las pruebas. Hoy el motivo es más simple y
más duro: **Firebase AI Logic no tiene Files API**. El PDF viaja *inline*, dentro de la petición, con
un tope de 20 MB. Para un resumen sería suficiente; para un chat significa reenviar el documento en
cada turno, porque el historial que el cliente reenvía lleva el fichero dentro. Diez preguntas, diez
subidas del boletín. Eso hace inviable la feature 011.

**Lo que sí tenía a favor, y que se pierde a conciencia**: Firebase AI Logic mantiene la credencial
fuera del APK y aplica App Check. Es la vía que Google recomienda para aplicaciones públicas, y el
propio README de la librería que sí adoptamos desaconseja explícitamente incrustar la clave en un
cliente móvil. Se asume con conocimiento del propietario, exactamente igual que hasta hoy: una
credencial dentro de un APK distribuido es recuperable. No es un descuido nuevo, es el mismo asumido
desde la 007.

---

### D-203 — El cliente de la librería es un `single`, y acepta una URL base

**Decisión**: `GenAiClientProvider`, un `single` de Koin en `data/source/remote/`, que construye
perezosamente el `Client` con la credencial y lo devuelve, o devuelve `null` si no hay credencial.
Acepta un `baseUrl` opcional que en producción va vacío.

**Motivo**: tres cosas a la vez. Una, el `Client` lleva dentro un cliente HTTP con su pool de
conexiones; construir uno por petición sería tirarlo y rehacerlo. Dos, `null` cuando no hay
credencial es lo que mantiene vivo `GeminiRefusal.NotConfigured` **sin ninguna petición**, que es
FR-030 y SC-010. Y tres, el `baseUrl` es el que hace que D-226 sea posible.

**Alternativa descartada**: declarar `single<Client>` directamente. Obligaría a un binding nullable o
a construir el cliente con una credencial vacía, y en el segundo caso el «no configurado» pasaría a
descubrirse con un 401 del servicio en vez de sin salir del dispositivo.

**Consecuencia**: `Client` es `AutoCloseable` y este nunca se cierra, porque vive lo que el proceso.
En las pruebas sí hay que cerrarlo.

---

## Lo que se envía

### D-204 — El documento se sube; no se extrae su texto

> **Sustituye a D-013 y D-018** de la feature 007, y al supuesto que sostenía **D-104** de la 009.

**Decisión**: el PDF ya descargado y validado se sube por la Files API y se referencia en la petición
como una parte de tipo fichero. Deja de extraerse texto en el dispositivo.

**Motivo**: es lo que hace posible FR-002 —que un escaneado se resuma— y lo que hace barata la
feature 011. Además retira de golpe una cadena de cuatro clases y sus pruebas.

**Lo que esto supera, dicho en voz alta**: el invariante «un documento sin texto utilizable no llega
nunca al servicio» era una regla de la 007 con prueba propia. No se incumple: **deja de aplicar**.
Existía porque lo que enviábamos era el texto y de un escaneado no salía ninguno. Cuando lo que se
envía es el documento, un escaneado deja de ser un caso imposible.

**Lo que se pierde**: la garantía local de que una petición condenada no sale. Antes, contar
caracteres decidía en el móvil si valía la pena gastar cuota. Ahora ese juicio lo hace el servicio, y
un documento ilegible cuesta una petición. Se acepta: el descargador ya comprueba los bytes mágicos
`%PDF-`, el tipo de contenido y el tamaño, así que lo que llega es un PDF de verdad; lo único que no
sabemos de antemano es si el servicio sabrá leerlo.

---

### D-205 — Se conserva un contador de páginas local, y con él sobrevive el PDF protegido

**Decisión**: `AndroidxPdfTextExtractor` no se sustituye por nada más grande que
`AndroidxPdfPageCounter`: abre el documento en el proceso aislado y devuelve **solo** el número de
páginas, o `Encrypted` ante `PdfPasswordException`.

**Motivo**: quitarlo del todo era tentador, y habría costado dos cosas que no queremos pagar.

- **`SummaryValidator` dejaría de poder descartar una cita a la página 99 de un documento de doce.**
  Ese descarte no es cosmético: cada cita es un enlace que abre el visor por esa página, y un enlace
  que lleva a ninguna parte en una aplicación de boletín oficial es peor que no tener el enlace. El
  validador tiene por norma no fiarse del recuento que declara el propio modelo (es la razón de ser
  de su recálculo de `coverage`), así que el número de páginas tiene que venir de otro sitio.
- **`AiSummaryError.EncryptedPdf` desaparecería.** Un documento con contraseña no puede resumirse ni
  aquí ni allí, y detectarlo en el móvil cuesta una excepción y ahorra una petición **y** el envío de
  un documento que no sirve para nada.

**Consecuencia sobre el recuento de sitios**: `CLAUDE.md` dice que `androidx.pdf` se toca en
exactamente dos sitios. Sigue siendo cierto: `ui/pdf` para dibujar, y este para contar. Lo que cambia
es el nombre del segundo.

**Alternativa descartada**: pedirle el número de páginas al servicio. Es información que el modelo
*declara*, no que se mide, y el proyecto ya tiene escrito que del modelo no se acepta un recuento sin
comprobarlo.

---

### D-206 — El documento se sube en memoria, no por trozos

**Decisión**: leer el fichero local con `readBytes()` y usar la sobrecarga de subida que toma un
`ByteArray`.

**Motivo**: `OkHttpDocumentDownloader` ya rechaza cualquier documento de más de **25 MB**, así que el
peor caso está acotado y es perfectamente manejable en un móvil con `minSdk 28`. La sobrecarga por
canal existe y sube en trozos de 8 MB sin cargar todo, pero obliga a importar tipos de Ktor
(`toByteReadChannel`) en nuestro código, y acoplarnos al motor HTTP interno de la librería es
exactamente lo que la librería debería evitarnos.

**Si el tope de descarga subiera algún día**, esta decisión hay que revisarla, y por eso queda
escrita con su número.

---

## La sesión del documento

### D-207 — Una sola sesión viva, y el `data` es quien la guarda

**Decisión**: `AiDocumentSessionStore`, un `single` en `data/source/remote/`, mantiene **como mucho
un** documento preparado. `open(...)` es idempotente: si la clave y el checksum coinciden con la
sesión actual, devuelve la que hay; si no, retira la anterior y sube el nuevo. Un `Mutex` serializa
las aperturas.

**Motivo**: es la forma más simple que cumple FR-007 a FR-010 y deja el asiento hecho para la 011. El
`Mutex` no es adorno: en la 011, el resumen y la primera pregunta pueden pedir el documento a la vez,
y sin él serían dos subidas del mismo fichero.

**Por qué «como mucho una» y no un mapa**: porque el enunciado que hay que poder comprobar es
FR-010, «abrir otra publicación retira el documento de la anterior». Sobre un conjunto de tamaño
desconocido eso no es una afirmación, es una intención.

**Alternativa descartada**: colgar la sesión de un grafo de navegación anidado con su propio
`ViewModelStoreOwner`, que es la respuesta idiomática de Compose Navigation. Habría exigido
reestructurar el `NavHost` exterior para meter Detalle, Visor y Preguntar en un subgrafo, con el
riesgo que eso tiene sobre una navegación que ya funciona, y a cambio de una propiedad —el ámbito
automático— que aquí se consigue con una llamada explícita.

---

### D-208 — Quien cierra la sesión es el modelo de pantalla del detalle, y necesita su propio ámbito

**Decisión**: `PublicationDetailViewModel.onCleared()` llama a `ReleaseAiDocumentSessionUseCase`, y
`AiDocumentSessionStore.release()` es una función **normal, no suspendida**, que lanza el borrado
sobre un `CoroutineScope(SupervisorJob() + dispatchers.io)` propio del `single`.

**Motivo**: es el único punto del ciclo de vida que significa «se ha salido de la publicación».
Preguntar y el visor se apilan **encima** del detalle, así que su entrada en la pila sigue viva
mientras se usan y solo se limpia al hacer *pop*. Que Preguntar solo sea alcanzable a través del
detalle es lo que hace que este punto sea suficiente.

**Y el detalle que se olvida siempre**: en `onCleared()` el `viewModelScope` **ya está cancelado**.
Lanzar el borrado ahí no borra nada. De ahí el ámbito propio, que vive lo que el `single`, es decir
lo que el proceso.

**Alternativa descartada**: hacer `release` una función suspendida y llamarla desde un
`DisposableEffect` del componible. Se ejecutaría también en cada recomposición estructural y en cada
cambio de configuración, y borraría el documento cuando nadie ha salido de ninguna parte.

---

### D-209 — La caducidad del servicio es una red de seguridad, no el mecanismo

**Decisión**: se confía en el borrado explícito (FR-009). La caducidad automática del servicio cubre
únicamente el caso en que el proceso muere sin poder borrar.

**Motivo**: un proceso de Android puede morir sin avisar, y entonces no hay `onCleared()` que valga.
Que el fichero desaparezca solo evita que eso se convierta en un fichero abandonado para siempre.

**Dato pendiente de confirmar**: la documentación del proveedor dice **48 horas** de conservación,
2 GB por fichero y 20 GB por proyecto, sin coste. Ninguno de los tres se ha comprobado contra el
servicio real todavía; queda como paso del `quickstart.md`. El plazo exacto no cambia el diseño: lo
que importa es que sea finito y que no sea el mecanismo principal.

---

### D-210 — La preparación tiene un tope de espera

**Decisión**: el sondeo del estado del fichero espera un número acotado de intentos con un intervalo
fijo; superado, se devuelve el rechazo que la pantalla traduce en «no se ha podido preparar este
documento».

**Motivo**: FR-012. Un bucle `while (state == PROCESSING)` sin tope es una pantalla girando para
siempre, y el proyecto ya sabe lo que cuesta un fallo que se ve igual que un cuelgue.

---

## El esquema y el prompt

### D-211 — El esquema se pasa tal cual, sin reescribirlo con los tipos de la librería

**Decisión**: `SummarySchema.value` —el `JsonElement` que ya existe— se entrega en
`GenerateContentConfig.responseJsonSchema`, que es de tipo `JsonElement?`. El fichero **no se toca**.

**Motivo**: la librería ofrece dos caminos, `responseSchema` con su tipo `Schema` propio, y
`responseJsonSchema` con JSON crudo. El primero obligaría a traducir a mano un esquema con `$defs`,
`$ref` y doce propiedades, y **el orden de declaración es carga útil** en este proyecto: con
`plainLanguageSummary` en cuarta posición, la prosa se cortaba y todo lo declarado después venía
vacío. `SummarySchemaTest` existe para impedir que alguien las ordene alfabéticamente. Reescribir el
esquema sería volver a poner esa bomba en la mesa a cambio de nada.

**Refuerzo disponible y descartado por ahora**: el tipo `Schema` tiene un campo `propertyOrdering`
explícito. No se usa porque no se usa `Schema`. Queda anotado por si algún día el orden implícito del
JSON dejara de respetarse.

---

### D-212 — El prompt pierde el hueco del documento, y sube de versión

**Decisión**: `SummaryPromptFactory.userMessage(publication, totalPages)` — sin `RenderedDocument` y
sin el hueco `{{documentWithPageMarkers}}`. El mensaje de sistema pasa a decir que el documento va
adjunto. `AiSummaryConstants.PROMPT_VERSION` → `boc-summary-es-v5`.

**Motivo**: el hueco del documento ya no existe, y el modelo tiene que saber dónde está el documento.

**Se conserva íntegra la regla de la 007 sobre `trimIndent()`**: la sustitución se hace **después** de
recortar la sangría, nunca antes. Un valor multilínea sin sangría arrastra el indent común a cero y
el mensaje entero sale con ocho espacios por línea, pagados de la cuota. Hay prueba que lo afirma y
se conserva.

**Consecuencia**: al cambiar `PROMPT_VERSION` y `MODEL_ID`, **todo lo ya resumido queda obsoleto**.
Es FR-015, y es por diseño: un resumen hecho leyendo un texto extraído no se hizo en las mismas
condiciones que uno hecho leyendo el documento.

---

### D-213 — El modelo se elige al atravesar la frontera, no aquí

**Decisión**: `AiSummaryConstants.MODEL_ID` sigue siendo el único sitio donde vive el identificador.
El valor concreto se fija tras la comprobación del `quickstart.md` §3 bis, no antes.

**Motivo**: el proyecto ya tiene una cicatriz aquí. El 4 de septiembre de 2026 `gemini-3.5-flash-lite`
tuvo una caída de capacidad sostenida y hoy la constante está apuntando a `gemini-3.1-flash-lite` como
apaño. Elegir un modelo por escrito en un plan y descubrir tres días después que no acepta ficheros o
que no está disponible sería repetir el problema con más ceremonia. Lo que sí se decide aquí es qué
hay que comprobar antes de fijarlo: que acepta una parte de tipo fichero, que respeta
`responseJsonSchema`, y que sabe leer un PDF escaneado (SC-001).

**Lo que no se hace, y está escrito para que no se haga**: una cadena de reserva entre modelos. El
`model_id` que se guarda con cada resumen es la columna que decide qué está obsoleto, y dejaría de ser
determinista.

---

## La cuota

### D-214 — El coordinador no cambia de oficio, pero sí de fuente para el retraso

**Decisión**: `GeminiRateLimitCoordinator` **no se toca**. Lo que cambia es de dónde sale el número
de segundos que se le pasa a `recordExhaustion`: primero se intenta extraer del mensaje de la
excepción, y si no aparece, se usa la ventana deslizante que el coordinador ya lleva por su cuenta.

**Motivo**: la librería **no expone las cabeceras de una respuesta de error** —cuando hay error,
lanza—, así que se acabó leer `Retry-After` de la cabecera. Pero `GenAiApiException` construye su
mensaje a partir de `error.message` **y de `error.details`**, y el `retryDelay` del proveedor viaja
justo ahí. `parseRetryDelaySeconds` ya recibe un `String?` y no le importa de dónde salga.

**Y el respaldo no es un apaño**: el coordinador lleva dos ventanas deslizantes propias precisamente
porque «Gemini no manda cabeceras de cuota». Que el retraso exacto falte degrada la precisión del
mensaje, no su corrección.

**Detalle que sí es una mejora**: en las respuestas **correctas** la librería expone
`sdkHttpResponse.headers`. No sirve para el 429, pero sí para diagnóstico.

---

### D-215 — Subir el documento no pasa por el coordinador; generar sí

**Decisión**: `coordinator.serialised { }` y `verdict()` envuelven la **generación**, no la subida.

**Motivo**: los límites que se cuentan son de generación de contenido. La Files API es gratuita y no
comparte ese contador según la documentación del proveedor. Se marca como **supuesto** (D-209) y no
cambia el diseño si resulta falso: bastaría con envolver también la subida.

**Lo que sí importa y no cambia**: un 500 cuenta en el contador diario porque **se apunta al pedir**.
Tres 500 gastan tres peticiones del cupo para cero resúmenes. Sigue siendo así.

---

## Los errores

### D-216 — Los siete rechazos se conservan, y salen de excepciones tipadas

**Decisión**: `GeminiRefusal` mantiene sus siete casos. El mapeo pasa a ser:

| Lo que lanza la librería | Rechazo |
|---|---|
| `ClientException` con `code` 401 o 403 | `NotConfigured` |
| `ClientException` con `code` 429 | `QuotaMinute(segundos)` o `QuotaDay` |
| `ClientException` con cualquier otro `code` | `HttpError(code)` |
| `ServerException` (5xx) | `HttpError(code)`, reintentable |
| `IOException` de la capa de red | `Network` |
| fallo al deserializar el JSON del resumen | `Malformed` |
| respuesta sin prosa, o `finishReason` distinto de `STOP` | `BlankSummary` |

**Motivo**: los siete alimentan `AiSummaryTab.messageRes()` y las veintiuna pruebas instrumentadas de
la pestaña. Conservarlos uno a uno es lo que hace que este cambio no llegue a la capa de
presentación, igual que en la 009.

**Mejora que la librería regala**: `Candidate.finishReason` es un valor tipado, así que
`MAX_TOKENS` —el corte por techo de salida, que es la familia de fallos que más costó cerrar— se
distingue ahora explícitamente en vez de deducirse de un JSON que no parsea. Y
`GenerateContentResponse.text` **ya salta las partes marcadas como `thought`**, que es exactamente el
problema que en la 009 obligó a buscar el paso de salida por tipo y nunca por posición.

---

### D-217 — `NoExtractableText` se sustituye por `UnreadableDocument`

**Decisión**: el caso de `AiSummaryError` que hoy significa «este PDF no tiene texto que podamos
sacar» pasa a significar «el servicio no ha podido leer este documento». Sigue habiendo ocho casos y
sigue sin ser reintentable.

**Motivo**: el primero deja de poder ocurrir (D-204) y el segundo empieza a poder ocurrir. Sustituir
en vez de añadir mantiene el `when` exhaustivo del mismo tamaño y toca una línea en el mapa de
mensajes y una en su prueba.

**Alternativa descartada**: reutilizar `Unknown`. Ya cubre cuatro situaciones distintas y meterle una
quinta que además **no es reintentable** —al contrario que `Unknown`— habría hecho mentir a
`isRetryable`.

---

### D-218 — El agujero de la cancelación sigue abierto, y con la librería también

**Decisión**: `currentCoroutineContext().ensureActive()` como **primera** línea del `catch` que
atrapa fallos de red, igual que hoy.

**Motivo**: es la lección de la 009 medida en un móvil. Cancelar una corrutina no interrumpe una
llamada bloqueante con una `CancellationException`: le rompe el socket, y lo que sale es una
`IOException`. Alguien pulsó Atrás mientras se generaba un resumen y el registro dijo
«SocketException: Software caused connection abort» y la pantalla, al volver, «No hay conexión» de un
fallo que no existió. Cambiar de cliente HTTP **no arregla esto**: es una propiedad de cualquier
llamada bloqueante dentro de una corrutina. La prueba de regresión se conserva.

---

## El empaquetado

### D-219 — Java 17

**Decisión**: `sourceCompatibility` y `targetCompatibility` suben de 11 a 17, y con ellos el
`jvmTarget` de Kotlin.

**Motivo**: no es una preferencia. El bytecode de la librería es **major 61**, es decir Java 17,
comprobado sobre las clases del propio AAR. Con 11 no compila.

**Riesgo**: bajo. El entorno local es el JBR de Android Studio (21) y CI usa Temurin 21. Ninguna
dependencia del proyecto exige 11 como máximo.

---

### D-220 — `google-auth-library` no se puede excluir

**Decisión**: entra, con toda su cola.

**Motivo**: se intentó descartarla sobre el papel —solo usamos autenticación por clave, no por IAM—
y no se puede: `Client`, `ApiClient` y `Files` referencian `GoogleCredentials` en el propio AAR
(comprobado sobre el `classes.jar`). El constructor del cliente la nombra en su firma, así que
excluirla sería un `NoClassDefFoundError` al resolver el constructor, no un fallo diferido en una
rama que nunca se ejecuta.

**Lo que arrastra, medido y no supuesto.** Comparando el `debugRuntimeClasspath` con y sin la
dependencia: **31 artefactos nuevos**. Los que pesan son Ktor 2.3.8 entero —`client-core`, `io`,
`http`, `utils`, `client-okhttp`, `websockets`, `serialization`, `events`—, el propio
`google-genai-kotlin-android` (2,7 MB), `google-auth-library-oauth2-http` con
`google-http-client` y `google-http-client-gson`, **`org.apache.httpcomponents:httpclient` y
`httpcore`**, `io.opencensus`, `io.grpc:grpc-api`, `commons-codec` y `gson`.

**Y una corrección a lo que este documento dijo primero: Guava no es nueva.** Estaba ya en el
classpath en `31.1-android`, arrastrada por `firebase-analytics`. Lo que hace el SDK es **subirla** a
`33.4.0-android`. Atribuirle a esta feature tres megas que ya estaban habría sido inflar el argumento
a favor de la decisión siguiente, y eso es peor que no tener argumento.

**Consecuencia directa**: D-222. **El APK de debug pasa de unos 50 MB a 57,7 MB, del orden de +7 MB**,
medido el 5 de septiembre de 2026 compilando con y sin la dependencia. La cifra es **aproximada a
propósito**: las dos medidas se tomaron sobre compilaciones incrementales, no limpias, y una medición
posterior sin la dependencia dio 52,4 MB en lugar de los 50,2 de la primera. El orden de magnitud
—varios megas— es el que sostiene la decisión; el decimal no, y decirlo exacto sería fingir una
precisión que no se tomó. Sin optimización, ese peso acaba entero en el APK que se distribuye.

---

### D-221 — Exclusiones de empaquetado

**Decisión**: `packaging { resources.excludes += … }` con `META-INF/DEPENDENCIES`,
`META-INF/INDEX.LIST`, `META-INF/{AL2.0,LGPL2.1}` y los `NOTICE`/`LICENSE` duplicados.

**Motivo**: `google-auth-library` y `google-http-client` traen esos ficheros de metadatos y el
empaquetado falla con entradas duplicadas. Es la vía documentada.

---

### D-222 — Se activa la optimización de la versión de release

**Decisión**: `buildTypes { release { optimization { enable = true } } }`. Las reglas propias van en
`app/src/main/keepRules/*.keep`.

**Motivo**: FR-041, y un número medido: **del orden de +7 MB en el APK de debug** (D-220). El grueso
—Ktor entero, `httpclient`, `opencensus`, `grpc-api`— son clases que esta aplicación no llega a
ejecutar: el SDK las arrastra porque cubre casos de uso que aquí no se usan. Es exactamente el reparto
que R8 sabe deshacer.

**Lo que no se sabe todavía, y se sabrá en T079**: cuánto de esos 7,5 MB sobrevive a la optimización.
El número de debug es el peor caso, no la previsión.

**Cómo, exactamente**: AGP 9.3 —el proyecto está en 9.3.2— sustituyó `minifyEnabled` /
`shrinkResources` / `proguardFiles` por el bloque `optimization`, que activa código y recursos a la
vez e **incluye por defecto** el equivalente a `proguard-android-optimize.txt`. Las reglas propias no
van en un `proguard-rules.pro`: van en ficheros con extensión `.keep` dentro del conjunto de fuentes
`keepRules`.

**Lo que hay que escribir a mano**: el AAR de la librería **no trae reglas de consumidor** —se
comprobó: solo `R.txt`, `AndroidManifest.xml` y `classes.jar`—, así que los `-dontwarn` de las
dependencias opcionales de google-auth y google-http-client son nuestros.

**Y el precio, dicho antes de pagarlo**: hasta hoy **nadie ha compilado nunca la versión de release
de esta aplicación**, y ninguna prueba del proyecto se ejecuta sobre ella. Activar R8 sin ejecutar
una es declarar verde algo que nadie ha visto correr. De ahí FR-042 y la quinta puerta del
`quickstart.md`.

**Salida si sale mal**: la keep rule concreta que pida el fallo. Volver a `enable = false` es el
último recurso, y deja un APK gordo pero funcionando.

---

### D-223 — Ktor 2.3.8 y OkHttp 5.5.0 en el mismo proyecto

**Decisión**: se deja que Gradle resuelva a OkHttp 5.5.0 y se comprueba.

**Motivo**: la librería trae `ktor-client-okhttp-jvm:2.3.8`, compilado contra OkHttp 4. El proyecto
usa OkHttp por BOM 5.5.0 y Gradle resolverá hacia arriba. El motor de Ktor solo usa API pública de
OkHttp —`OkHttpClient.Builder`, `Request`, `Call`, `Dispatcher`, `Protocol`—, que 5.x conserva, así
que lo más probable es que funcione. Pero es una probabilidad, no una comprobación.

**Se comprueba**: en el primer `assembleDebug` y en la primera petición real.

**Salida si falla**: excluir `ktor-client-okhttp` y sustituirlo por `ktor-client-cio` o
`ktor-client-android`, que no dependen de OkHttp. Es un cambio de dos líneas en el catálogo.

---

## Cómo se comprueba

### D-224 — MockWebServer en HTTP plano, y por qué aquí sí

> **Matiza a D-009** de la feature 007, que impuso TLS en las pruebas de red.

**Decisión**: las pruebas de la fuente de datos apuntan el cliente a un MockWebServer que habla
**HTTP sin cifrar**, mediante `HttpOptions(baseUrl = …)`.

**Motivo**: la razón por la que las pruebas de las fuentes RSS hablan TLS es que
`BocFeedDefinition` **exige** HTTPS, y un servidor de pruebas en claro estaría comprobando algo que
la aplicación no hace. Esa invariante es del catálogo de fuentes del boletín, no del cliente de
inteligencia artificial. Y hay una razón práctica encima: no se puede inyectar un `SSLSocketFactory`
en el motor Ktor que la librería construye por dentro, así que el certificado autofirmado no habría
por dónde confiarlo.

**Lo que esto no relaja**: en producción la URL base va vacía y la librería usa la suya, que es
HTTPS. La prueba no cambia el comportamiento del producto.

---

### D-225 — Una regla de arquitectura más: solo `data` importa la librería

**Decisión**: novena regla de Konsist en `ArchitectureRulesTest`, calcada de la de Firebase: ningún
fichero fuera del paquete `data` importa `com.google.genai`.

**Motivo**: la quinta regla existe porque «solo `data` toca Firebase» era una norma que un despiste
podía romper en silencio. Aquí pasa lo mismo y con más motivo: la librería está en su **1.0.0**, tres
días vieja, y su propio README avisa de que dentro de la línea `1.x` garantiza compatibilidad de
fuentes pero **no binaria** en los tipos de datos. Que ninguno de esos tipos pueda asomar por `ui` ni
por `domain` es lo que hace que una futura subida de versión siga siendo un cambio de un paquete.

**Consecuencia documental**: `CLAUDE.md` dice «ocho reglas» y pasa a decir nueve. Ya estaba desfasado
una vez por el mismo motivo; se corrige en el mismo cambio.

---

### D-226 — La frontera hay que atravesarla de verdad, y ahora son dos

**Decisión**: el `quickstart.md` §3 bis es obligatorio y no sustituible por pruebas, y en esta
feature cubre **dos** fronteras nuevas: la subida del fichero y la generación con una referencia a
fichero.

**Motivo**: es la lección más cara de la 009, y está escrita en `CLAUDE.md`. Los dos defectos que de
verdad rompían el Resumen IA en un móvil —el resumen en blanco y el JSON cortado— no los podía
encontrar ninguna prueba automática, porque todas ponen dobles en la frontera y el defecto estaba al
otro lado. Los encontró el registro en un dispositivo real. Esta feature vuelve a mover esa frontera,
y además añade una segunda: subir un fichero es una operación nueva, con su propio ciclo de estados y
sus propios modos de fallo.

**Qué hay que comprobar en concreto**: que el modelo acepta una parte de tipo fichero; que respeta el
esquema; que **sabe leer un PDF escaneado** (SC-001, el único criterio de éxito que depende de algo
que no controlamos); que el fichero desaparece del servicio al salir; y que el registro dice la fase y
el tamaño y no dice la credencial ni el contenido.

---

## La corrección

### D-227 — La librería oficial **no se puede usar** en Android, y lo dice ella misma

> **Sustituye a D-201, D-203, D-219, D-220, D-221, D-222 y D-223** de esta misma feature.

**Decisión**: se retira `com.google.genai:google-genai-kotlin`. La Files API se implementa sobre el
`OkHttpClient` que el proyecto ya tiene, que es exactamente la alternativa que D-201 había descartado
y dejado escrita como plan B.

**Motivo**: no es una preferencia ni un riesgo estimado. El artefacto `-android` de la librería lleva
un guardián que **lanza siempre**:

```kotlin
// androidMain/kotlin/com/google/genai/kotlin/SecurityContext.kt
internal actual fun validateSecurityContext(hasApiKey: Boolean, hasCredentials: Boolean) {
  if (hasApiKey || hasCredentials) {
    throw IllegalStateException(
      "SECURITY FATAL: Initializing the Client with an API Key or Credentials on Android is " +
        "blocked to prevent credential leaks. For mobile applications, please use Firebase AI " +
        "Logic (https://firebase.google.com/docs/ai-logic) with Firebase App Check."
    )
  }
}
```

`Client.<init>` lo llama antes de nada, así que en Android **no existe forma de construir el cliente
con una credencial**. Con credencial y sin credencial son los dos únicos casos que esta aplicación
tiene, y uno de los dos lanza.

**Cómo se descubrió, y por qué no antes.** El README de la librería dice *«we strongly discourage
embedding API keys… into public mobile client applications»*, y eso se leyó, se citó en D-202 y se
asumió como una recomendación —igual que la aplicación asume desde la feature 007 que una credencial
dentro de un APK es recuperable—. **Una recomendación se puede asumir; un `throw` no.** La diferencia
no está en la documentación: está en el bytecode, y salió al ejecutar la primera prueba que construye
el cliente de verdad. Es, en pequeño, la misma lección que `CLAUDE.md` ya tenía escrita: *una frontera
con un servicio ajeno hay que atravesarla de verdad al menos una vez*.

**Alternativas descartadas, en orden de peso:**

- **Forzar el artefacto `-jvm` en Android** para saltarse el guardián. El guardián está en
  `androidMain`, así que técnicamente funcionaría. Se descarta y conviene que quede escrito por qué:
  es **rodear a mano un control de seguridad del proveedor**, sobre una variante que no está
  compilada para Android, y que cualquier versión de parche puede romper sin aviso. Ganaríamos unas
  ciento cincuenta líneas y perderíamos el derecho a decir que la aplicación usa la librería como su
  autor la publica.
- **Firebase AI Logic**. Es lo que el propio mensaje de error recomienda y quitaría la credencial del
  APK, que es una mejora real. Pero **no expone la Files API** (D-202), y sin ella la feature 011
  reenviaría el boletín entero en cada pregunta. Sigue siendo la vía correcta el día que exista un
  backend propio o el día que Firebase AI Logic dé acceso a ficheros; hoy no cumple el requisito que
  motiva esta feature.

**Lo que sobrevive intacto, que es casi todo.** La forma de la feature no dependía de la librería:
el documento se sube una vez, la sesión se comparte durante la visita y se suelta al salir, la
extracción de texto local desaparece, el contador de páginas se queda, el prompt pierde el hueco del
documento y el validador acota las citas a las páginas que existen. Solo cambia **el cable**: tres
ficheros de `data/source/remote/` se reescriben contra OkHttp. Que el cambio quepa ahí es, otra vez,
lo que el diseño de la 007 prometió por escrito.

**Lo que se revierte con la librería**: Java 17 (D-219), las exclusiones de empaquetado (D-221), la
activación de R8 (D-222) y el riesgo Ktor/OkHttp (D-223). Los cuatro existían **por la cola de
dependencias**, y sin dependencia no hay cola. El APK de debug queda en **52,4 MB**, dentro del ruido
de medición de la línea de partida.

**Y una que sí se pierde**: `HttpOptions(baseUrl = …)` era el argumento que desbloqueaba adoptar un
SDK. Sin SDK vuelve a ser irrelevante, y las pruebas vuelven a MockWebServer **sobre TLS**, como el
resto del proyecto, con lo que D-224 también se retira.
