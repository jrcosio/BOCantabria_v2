# Research: Preguntar al BOC

**Feature**: `011-preguntar-al-boc` | **Fecha**: 5 de septiembre de 2026

Las decisiones de esta feature empiezan en **D-301** para no confundirse con las series anteriores
(007 → D-0xx, 009 → D-1xx, 010 → D-2xx).

Lo que esta feature **no** decide, porque ya está decidido y comprobado: cómo se sube el documento,
cuánto vive, quién lo borra y con qué modelo se habla. Todo eso es la feature 010 y aquí solo se
consume.

---

## 1. La frontera con el servicio

### D-301 — Un origen de datos propio para el chat, no una segunda función en el del resumen

**Decisión**: se crea `GeminiChatDataSource` (interfaz) con `OkHttpGeminiChatDataSource`, hermano de
`GeminiSummaryDataSource`. **No** se añade un segundo método a la interfaz del resumen.

**Por qué**: la firma del resumen es `summarise(system, user, document)` y devuelve un
`SummaryPayload`. El chat necesita **una conversación** —varios turnos— y devuelve otra cosa. Meterlo
en la misma interfaz obligaría a que el doble de pruebas del resumen implementara un método que no le
importa, y al revés. Son dos operaciones, no dos modos de una.

**Lo que sí se comparte, y es casi todo**: `OkHttpClient`, `GeminiApiKeyProvider`,
`GeminiRateLimitCoordinator`, los DTO de la petición y la respuesta (`GeminiGenerateRequest`,
`GeminiContent`, `GeminiPart`, `GeminiFileData`, `GeminiGenerationConfig`, `GeminiGenerateResponse`,
`GeminiErrorEnvelope`), el vocabulario `GeminiRefusal` y la disciplina entera del transporte: la
credencial en cabecera, el conteo al pedir, `ensureActive()` como primera línea del `catch (IOException)`,
tres intentos con retroceso y el registro que dice la forma y nunca el contenido.

**Alternativa descartada**: reutilizar `GeminiSummaryDataSource` con un parámetro que cambiara el
esquema. Habría convertido una interfaz clara en un interruptor, y el `SummaryPayload` del tipo de
retorno no encaja de ninguna manera con una respuesta de chat.

### D-302 — El historial es nuestro y viaja entero en cada turno

**Decisión**: la conversación se guarda como nuestra lista de mensajes y en cada pregunta se arma un
`contents` con todos los turnos que caben en la ventana (D-303). No se guarda ningún objeto de
conversación del proveedor.

**Por qué**: `generateContent` no tiene estado. Cualquier «objeto conversación» de un cliente es una
lista replicada del lado del cliente, y tenerla en nuestro tipo nos da tres cosas que no tendríamos:
cancelar es tirar la lista; el turno del resumen —que es JSON con esquema estricto— no contamina el
chat; y lo que se muestra en pantalla y lo que se envía salen de la misma estructura, así que no
pueden desincronizarse.

**Consecuencia que hay que aceptar en voz alta**: cada pregunta reenvía el documento por referencia y
el historial. El documento se factura como entrada en **cada** turno. Con 1.048.576 tokens de entrada
y una publicación del BOC eso no es un problema de capacidad, y el control de consumo cuenta
**peticiones**, no tokens (009 D-108), así que tampoco lo es de cuota. Pero conviene que esté escrito:
una conversación de diez preguntas cuesta diez lecturas del documento, no una.

### D-303 — La ventana de historial es de doce mensajes, y no es por dinero

**Decisión**: se envían como mucho los **12 últimos mensajes** (seis intercambios). La conversación en
pantalla no se recorta: se recorta lo que viaja.

**Por qué**: no por ahorrar —el documento domina la entrada y doce turnos de texto corto no se notan—
sino porque una lista sin cota es una petición sin cota. Poner el límite es barato; descubrir dónde
estaba es caro.

**Por qué doce y no más**: seis intercambios cubren de sobra el uso real —se pregunta el plazo, se
matiza, se pregunta el importe— y el documento sigue estando entero en el contexto, que es lo que de
verdad sostiene la respuesta.

### D-304 — El documento va adjunto solo en el primer turno de usuario

**Decisión**: la parte `fileData` se pone en el **primer** `contents` de rol `user` de la petición; los
turnos siguientes son solo texto.

**Por qué**: repetir la referencia en cada turno no añade contexto —el documento ya está en la
conversación— y sí añade una segunda forma de que la petición sea inválida. Cuando la ventana de D-303
descarta el turno que llevaba el fichero, la referencia se vuelve a colocar en el primer turno que
quede: **el documento nunca puede faltar**, y eso es un invariante con prueba.

### D-305 — El identificador del modelo se comparte con el resumen, a conciencia

**Decisión**: el chat usa `AiSummaryConstants.MODEL_ID`. No se crea una segunda constante.

**Por qué**: la escapatoria documentada en la 009 para una caída de capacidad es **una línea**. Dos
constantes que tienen que valer lo mismo son dos líneas que un día valdrán cosas distintas, y el día
que pase nadie lo notará hasta que una de las dos funciones deje de responder.

**Lo que chirría, y se asume**: el nombre `AiSummaryConstants` ya no describe del todo lo que contiene.
Renombrarlo a `AiConstants` tocaría diez ficheros de la 010 recién fusionada para ganar un nombre mejor;
se deja anotado como deuda con nombre, no como error. Las otras dos constantes —versión de prompt y de
esquema— **siguen siendo solo del resumen**, porque son la procedencia de una fila almacenada y el chat
no almacena nada.

### D-306 — La respuesta no se escribe progresivamente

**Decisión**: sin streaming, igual que el resumen.

**Por qué**: un esquema estricto y la escritura progresiva no conviven —hasta que el objeto no está
completo no hay nada válido que mostrar—, y el esquema es justamente lo que sostiene la defensa de
ámbito (D-310). Cambiar una por la otra sería cambiar el mecanismo por la apariencia del mecanismo.

Se paga con una espera sin texto, y por eso hay un indicador de que el asistente está trabajando.

---

## 2. Que solo se hable del documento

### D-307 — Cinco capas, y solo una es demostrable

Ordenadas de la más floja a la más firme:

1. **Instrucción de sistema.** En español, con prueba de presencia de sus cláusulas, como ya hace
   `SummaryPromptFactoryTest`.
2. **La pregunta viaja delimitada.** Entre marcas explícitas, y el prompt dice que lo de dentro es
   texto que responder y nunca una orden.
3. **El documento se declara datos.** Es el vector realista: un PDF del boletín con una instrucción
   escrita dentro, no una persona lista. La cláusula ya existe en el prompt del resumen y aquí se
   repite con las mismas palabras.
4. **El esquema obliga a declarar el ámbito.** Y cuando el ámbito es «ajeno al documento», lo que se
   pinta es **texto nuestro**.
5. **Higiene barata.** Pregunta vacía rechazada, longitud acotada, y nada de la persona en la petición.

**Solo la cuarta es una prueba automática posible.** Las tres primeras viven al otro lado de la
frontera con el servicio, y todas las pruebas de esta casa doblan esa frontera. La quinta se comprueba
sin salir de casa pero no defiende de nada por sí sola.

**Y hay que decir dónde falla la cuarta**: si alguien consiguiera que el modelo escribiera un poema y
lo etiquetara como `FROM_DOCUMENT`, la capa no lo detendría. Lo que impide es lo contrario —que una
respuesta correctamente etiquetada como ajena llegue a pintarse—, que es el caso que de verdad se da:
un modelo obediente que cumple la petición ajena y lo reconoce. Es una barrera, no una demostración,
y la especificación lo dice con esas palabras.

### D-308 — Tres valores de ámbito, y solo uno se sustituye

**Decisión**: `AiAnswerScope { FROM_DOCUMENT, NOT_IN_DOCUMENT, OUT_OF_SCOPE }`.

- `FROM_DOCUMENT`: se muestra el texto del modelo y sus fuentes.
- `NOT_IN_DOCUMENT`: **se muestra el texto del modelo**. Que el documento no fije un plazo de
  alegaciones es una respuesta útil, y decirla con nuestras palabras genéricas sería peor información.
- `OUT_OF_SCOPE`: se muestra **nuestro texto** y se descarta el del modelo por completo. Cero
  caracteres suyos llegan a pantalla (FR-021, SC-004).

**Alternativa descartada**: dos valores, dentro y fuera. Habría metido «el documento no lo dice» en el
mismo saco que «escríbeme un poema», y son cosas opuestas: la primera es la respuesta correcta a una
pregunta legítima.

### D-309 — Una respuesta «del documento» sin ninguna cita no se rechaza

**Decisión**: no se exige al menos una fuente válida cuando el ámbito es `FROM_DOCUMENT`.

**Por qué se consideró**: una respuesta que dice venir del documento y no señala ni una página es
sospechosa, y rechazarla sería una sexta capa gratis.

**Por qué se descarta**: «¿de qué trata este documento?» es una pregunta legítima cuya respuesta no
cita ninguna página en particular. Rechazarla convertiría una defensa en un fallo visible para quien
no ha hecho nada raro. Queda anotado como candidato si alguna vez se observa el patrón contrario.

### D-310 — El esquema del chat: `scope`, `sources`, y `answer` la última

**Decisión**: `ChatAnswerSchema` con las propiedades **en ese orden**, `additionalProperties: false`,
las tres en `required`, `answer` acotada con `maxLength` y `sources` con `maxItems`.

**Por qué el orden**: es la lección de `SummarySchema`, medida y no supuesta. En una respuesta con
esquema estricto el orden de las propiedades es el orden de generación, y todo lo declarado **después**
del campo largo se vacía si la generación se corta. Con `answer` en primera posición, una respuesta
larga dejaría el ámbito y las fuentes en blanco — y el ámbito en blanco es precisamente la defensa
caída. Lo vigila `ChatAnswerSchemaTest`, calcado de `SummarySchemaTest`.

### D-311 — El validador no se fía de nada

**Decisión**: `ChatAnswerValidator` descarta las citas fuera de `1..totalPages`, recorta el texto a la
última frase completa si venía cortado, y devuelve `null` si el cuerpo queda en blanco.

**Por qué el número de páginas viene del dispositivo**: es la razón por la que la 010 conservó
`PdfPageCounter` en vez de borrarlo con el extractor. Una cita a la página 14 de un documento de 9 no
es un error del modelo que se pueda ignorar: es un enlace que lleva a ninguna parte.

---

## 3. Dónde vive la conversación

### D-312 — En memoria, en un repositorio, y como mucho una

**Decisión**: `AiChatRepositoryImpl` es un `single` que mantiene **como mucho una** conversación viva
en todo el proceso, con su clave de publicación. Abrir otra publicación descarta la anterior.

**Por qué un repositorio y no el modelo de pantalla**: la conversación tiene que sobrevivir a ir al
detalle y volver (FR-008), y el modelo de pantalla de Preguntar muere al hacer *pop*. El dueño del
ciclo de vida es la visita a la publicación, no la pantalla.

**Por qué una y no un mapa**: es exactamente el argumento de la D-207 de la 010 para el documento. «Al
abrir otra publicación, la conversación anterior desaparece» es una afirmación comprobable sobre un
único hueco; sobre un mapa de tamaño desconocido es una intención. Y hace que FR-011 —no mezclar
conversaciones— sea estructural en vez de una promesa.

**Por qué no en Room**: persistirla exigiría tabla nueva, migración a la versión 5 y, tarde o temprano,
la primera sentencia de borrado del proyecto. Está fuera de alcance por decisión del propietario y esta
decisión solo registra el motivo técnico que la respalda.

### D-313 — La pregunta se resuelve en un ámbito del repositorio, no de la pantalla

**Decisión**: `ask()` **no es una función suspendida**. Lanza el trabajo en un ámbito propio del
repositorio (`SupervisorJob() + dispatchers.io`) y el resultado llega por el flujo que la pantalla
observa. Ese ámbito se cancela al descartar la conversación.

**Por qué**: si la petición viviera en el `viewModelScope` de Preguntar, salir de la pantalla la
cancelaría. Y cancelar **no devuelve la cuota**: la petición se cuenta al salir, no al volver. Así
que cancelar cuesta lo mismo que terminar y encima pierde la respuesta. Dejándola correr, quien vuelve
a entrar se encuentra la respuesta hecha, que es lo que cabe esperar de algo que ya se ha pagado.

**Y de paso resuelve FR-037 sin escribir nada**: no hay error que mostrar al volver porque no hay
cancelación que reportar. El único caso que cancela de verdad es salir de la publicación, y entonces
no hay pantalla a la que informar.

**Precedente**: `AiDocumentSessionStore` ya tiene un ámbito propio por una razón hermana, y
`ReleaseAiDocumentSessionUseCase` ya es un caso de uso no suspendido.

### D-314 — Quien descarta la conversación es el detalle, en `onCleared()`

**Decisión**: `PublicationDetailViewModel.onCleared()` llama a **dos** casos de uso no suspendidos:
el que ya libera el documento y un `DiscardAiConversationUseCase` nuevo.

**Por qué dos y no uno que haga las dos cosas**: son dos repositorios distintos y cada uno limpia lo
suyo. Un caso de uso que llamara a los dos escondería que hay dos dueños, y el día que el chat cambie
de sitio habría que desenredarlo.

**Por qué en el detalle y no en Preguntar**: Preguntar y el visor se apilan **encima** del detalle, así
que la entrada del detalle sigue viva mientras se conversa. Es el mismo razonamiento por el que el
documento se libera ahí, y ahora hay dos cosas que confirman que el sitio era ese.

---

## 4. Lo que se comparte con el Resumen IA

### D-315 — La preparación del documento se extrae a una clase que usan los dos

**Decisión**: se crea `AiDocumentPreparer` en `data/source/remote/`, y `AiSummaryRepositoryImpl` **se
modifica** para usarla. Encierra los cuatro pasos comunes: copia local → cuenta de páginas → apertura
de sesión → resultado, informando de la fase por una función que recibe.

```
prepare(publication, onPhase) -> Ready(document, totalPages)
                               | Unreachable(DomainError)     el documento no se pudo obtener
                               | Encrypted                    protegido con contraseña
                               | Refused(GeminiRefusal)       el servicio no lo aceptó
                               | Broken(Throwable)            fallo al leerlo en el dispositivo
```

**Por qué se toca código de la 010 recién fusionada**: la alternativa es copiar treinta líneas que
contienen un invariante —**un documento protegido con contraseña no sale nunca del dispositivo**—.
Un invariante duplicado es un invariante que se cumple hasta que alguien arregla una de las dos
copias. Las pruebas de la 010 existen y son la red que hace barato este cambio.

**Por qué devuelve casos y no un `AppResult`**: cada repositorio traduce a **su** vocabulario de error
—`AiSummaryError` y `AiChatError` dicen frases distintas—, y esa traducción tiene que quedarse donde
está, en cada repositorio.

### D-316 — El aviso de envío externo es el mismo y se acepta una sola vez

**Decisión**: se reutiliza la preferencia `ai_notice_accepted_v3` y el componible `AiNoticeSheet`, que
**se mueve** de `ui/detail/component/` a `core/ui/component/`.

**Por qué no se sube la clave a `_v4`**: el texto de la 010 ya dice lo que pasa —se envía el documento
oficial completo, el servicio lo conserva un tiempo limitado, la aplicación lo retira al salir—. Eso
es exactamente lo que hace el chat. No hay hecho nuevo que contar, y volver a preguntar por algo ya
aceptado enseña a que se acepte sin leer.

**Por qué se mueve el componible**: dos pantallas lo usan y `core/ui/component` es donde esta casa pone
los componibles compartidos sin estado. Dejarlo en `ui/detail` obligaría a Preguntar a importar de otra
pantalla, que es la clase de dependencia que acaba en un enredo.

### D-317 — El coordinador de cuota es el mismo, y eso significa que preguntar y resumir se estorban

**Decisión**: `GeminiRateLimitCoordinator` se comparte sin cambios. No se le añade ningún concepto de
«tipo de petición».

**Por qué**: la cuota es del plan, no de la funcionalidad. Un contador por funcionalidad dejaría pasar
el doble de peticiones de las que hay.

**Consecuencia visible que conviene conocer**: `serialised { }` mantiene **una** petición a la vez en
toda la aplicación, así que pedir un resumen mientras hay una pregunta en el aire pone a la segunda a
esperar. Es correcto y es lo que ya pasaba entre dos resúmenes; se anota porque en el chat la espera se
nota más.

---

## 5. Errores y estados

### D-318 — `AiChatError` es propio y tiene ocho casos

**Decisión**: jerarquía sellada propia, no reutilizar `AiSummaryError`.

```
Offline | QuotaMinute(secondsRemaining) | QuotaDay | NotConfigured
UnreadableDocument | EncryptedPdf | InvalidResponse | Unknown
```

**Por qué propia**: los textos en pantalla son distintos. «No se ha podido generar el resumen» no es
«no se ha podido responder», y un enumerado compartido acaba obligando a un `when` con ramas
imposibles en una de las dos pantallas. Lo que **sí** se comparte es `GeminiRefusal`, que es el
vocabulario del transporte y no el de la pantalla.

**`isRetryable`**: reintentables `Offline`, `QuotaMinute`, `InvalidResponse` y `Unknown`; no lo son
`QuotaDay`, `NotConfigured`, `UnreadableDocument` y `EncryptedPdf`. Mismo criterio que el resumen:
ofrecer reintentar donde no puede ayudar es su propia forma de mentir.

### D-319 — No hay estado «esperando cuota» que se reanude solo

**Decisión**: `AiChatStatus { Idle, Preparing(phase), Thinking, Failed(error) }`. Una cuota agotada es
un `Failed(QuotaMinute)` con su cuenta atrás en el texto y su botón de reintentar.

**Por qué no se copia el `WaitingForQuota` del resumen**: ahí tiene sentido porque el resumen es una
operación que se pidió una vez y puede completarse sola. Una pregunta es una conversación: reanudarla
sola un minuto después, cuando quien preguntó a lo mejor ya se ha ido, es contestar a destiempo.

### D-320 — La pregunta que falló se queda en la lista, y reintentar la reenvía

**Decisión**: al fallar, la burbuja de la pregunta **permanece**, y debajo aparece una fila de error con
«Reintentar». Reintentar reenvía esa misma pregunta sin que haya que reescribirla (FR-033).

**Por qué no borrarla**: quien preguntó ya escribió; hacerle escribir otra vez porque el servicio falló
es cobrarle a él un fallo ajeno.

### D-321 — La publicación sin documento obtenible es un fallo, no un estado nuevo

**Hallazgo que corrige la especificación**: `Publication.documentUrl` **no es nulable**. Toda
publicación del boletín trae su documento, así que el estado «esta publicación no tiene documento» que
FR-030 describía **no existe**. Lo que sí existe es que el documento no se pueda obtener —sin conexión,
o un fallo de la descarga—, y eso ya son `Offline` y `Unknown`.

**Consecuencia**: FR-030 se reescribe en la especificación en vez de implementarse como estaba. La
regla de la casa es que un requisito que describe un estado imposible se corrige, no se cumple a medias.

### D-320b — Saber que no hay credencial **antes** de preguntar, no después

**Hallazgo del análisis**: FR-036 pide que al abrir Preguntar se diga que no está disponible y no se
deje enviar. El Resumen IA no tiene esa costura: descubre «no configurado» **después** de pulsar,
porque le llega como un rechazo del transporte. Copiar su forma incumpliría el requisito.

**Decisión**: `AiChatRepository` gana `observeAvailability(): Flow<Boolean>`, respaldado por el
`GeminiApiKeyProvider` que ya existe, y un `ObserveAiAvailabilityUseCase`.

**Por qué un flujo y no una función suspendida**: la pantalla lo combina con lo demás en su estado, y
un valor que se consulta una vez obliga a decidir cuándo consultarlo. Hoy la credencial no cambia en
caliente —viene de `BuildConfig`—, así que el flujo emitirá una vez; eso no lo hace incorrecto, lo hace
barato.

**Por qué no se le añade también al resumen**: sería cambiar la pantalla del detalle en una feature que
no va de eso. Queda anotado: el día que se haga, la costura ya está.

### D-327b — El modelo de pantalla combina seis flujos, y eso no compila como uno espera

**Hallazgo del análisis**: `AskViewModel` observa publicación, guardados, conversación, aviso aceptado,
disponibilidad, borrador y aviso pendiente. `combine` con más de cinco flujos cae en la sobrecarga de
`vararg`, que **exige que todos tengan el mismo tipo** y devuelve `Array<Any?>`.

**Decisión**: agrupar lo que viene del repositorio en un tipo propio —como hizo
`PublicationDetailViewModel` con `PersonalState`— y quedarse en cinco.

**Por qué se anota en vez de descubrirse al compilar**: ya costó tiempo una vez. Un plan que sabe dónde
está la piedra y no lo dice es un plan que la deja para el que venga detrás.

---

## 6. La pantalla

### D-322 — La estructura de la casa, y el mockup es orientativo

**Decisión**: `ui/ask/` pasa de un fichero a `AskRoute.kt` + `AskScreen.kt` + `AskViewModel.kt` +
`AskUiState.kt` + `component/`. Los componibles del paquete `component/` son tontos y sin estado.

**Del mockup se conserva**: la cabecera con la publicación y la estrella, el aviso de ámbito, los tres
chips de preguntas sugeridas, las burbujas con la hora, el bloque «Fuentes» con página y etiqueta, el
compositor con su botón redondo y el enlace «Ver PDF oficial» al pie.

**Del mockup se descarta a propósito**: el doble check de leído —aquí no significa nada, no hay nadie
al otro lado que lea— y el menú de tres puntos, que no tendría nada dentro.

**Del mockup se corrige**: los colores, la tipografía y los espaciados son los de la aplicación. El
mockup usa un azul distinto y una tipografía que no es la nuestra.

### D-323 — Falta un icono y hay que copiarlo con cuidado

**Decisión**: se añade `ic_send`. Los demás ya existen: `ic_robot`, `ic_ai`, `ic_document`, `ic_info`,
`ic_arrow_back`, `ic_bookmark`, `ic_bookmark_filled`.

**El cuidado**: el repositorio de Material Symbols mezcla dos convenciones de lienzo. La mayoría llegan
con `viewBox="0 -960 960 960"` y coordenadas negativas, que es lo que espera la plantilla de esta casa;
otros llegan en escala 24 y sin `viewBox`. Copiar el equivocado **no falla**: dibuja fuera del lienzo y
no se ve nada. Le pasó a `ic_ai` durante cuatro features. Antes de meter el trazado, se comprueba que
lleva coordenadas negativas.

### D-324 — El compositor es `bottomBar` y por eso hay que aplicarle el margen a mano

**Decisión**: el compositor va como `bottomBar` de un `Scaffold`, con
`windowInsetsPadding(systemBars.only(Horizontal + Bottom))` **dentro** de su `Surface`, más
`imePadding()`.

**Por qué**: un `Scaffold` con `bottomBar` **descarta** su margen de ventana inferior y lo sustituye por
la altura medida de la barra, anclada al borde crudo. Poner `contentWindowInsets` no cambia nada. Es
exactamente lo que le pasó a la barra de acciones del detalle, y hay una prueba —
`DetailActionBarInsetTest`— que lo fija. Aquí habrá otra igual.

**Y la prueba solo muerde con navegación de tres botones**: con gestos el margen puede ser cero. Antes
de la tanda instrumentada, `adb shell settings put secure navigation_mode 0`.

### D-325 — Las preguntas sugeridas son fijas

**Decisión**: tres, en `strings.xml`, iguales para toda publicación.

**Por qué no adaptadas al documento**: generarlas exigiría una petición al servicio **antes** de que
nadie haya preguntado nada, es decir, gastar cuota por abrir una pantalla. Eso es justo lo que la regla
número uno del Resumen IA prohíbe.

**Por qué tres**: entran en una línea y no empujan el compositor fuera de la pantalla.

### D-326 — El indicador de que el asistente trabaja es una animación infinita, y eso rompe las pruebas si no se sabe

**Decisión**: se anima, y las pruebas instrumentadas que lo atraviesen conducen el reloj a mano:
`composeRule.mainClock.autoAdvance = false` y `advanceTimeByFrame()`.

**Por qué**: una animación infinita impide que la composición llegue a reposo, y `assertIsDisplayed()`
espera reposo: **se cuelga en vez de fallar**. Ya pasó con el esqueleto de carga.

### D-327 — La pregunta se acota en 500 caracteres

**Decisión**: 500, con el contador visible a partir de 400.

**Por qué 500**: una pregunta a un boletín cabe de sobra; el límite existe para que un pegado
accidental de media publicación no se convierta en una petición. Se ve **antes** de enviar, no después.

---

## 7. Pruebas y registro

### D-328 — Lo que se registra, y lo que no

**Decisión**: prefijo `chat:` en `CrashReporter.log`. Se registran la fase, el número de mensajes que
viajan, el ámbito declarado por la respuesta y el motivo del fallo. **Nunca** la credencial, ni el
contenido del documento, ni el texto de la pregunta, ni el de la respuesta.

**Por qué el ámbito sí**: es lo único que permite saber, sobre un móvil de verdad, si la defensa está
actuando. Y es un enumerado de tres valores: no puede filtrar nada.

```
chat: preparing document for boc:440124
chat: asking with 3 message(s)
chat: answer scope=OUT_OF_SCOPE, 0 source(s)
chat: 2 of 3 citation(s) dropped, document has 9 pages
chat: HTTP 429, retry in 37s
chat: network: SocketException: Software caused connection abort
```

### D-329 — La analítica cuenta, no escucha

**Decisión**: un evento `ai_question_asked` con el ámbito de la respuesta y nada más. Sin texto, sin
identificador, sin clave de publicación.

**Por qué el ámbito sí y la clave no**: el reparto entre los tres ámbitos dice si la defensa se está
usando; la clave de publicación, cruzada con poco más, dice qué está leyendo una persona.

### D-330 — La batería de inyección es manual y obligatoria

**Decisión**: va al `quickstart.md` §3 bis, con al menos cinco intentos distintos y un PDF preparado
con una instrucción inyectada dentro. **No se puede automatizar**, y la especificación lo dice.

**Por qué se escribe igualmente como criterio de éxito (SC-009)**: el criterio existe aunque la prueba
sea a mano. Quitarlo por no ser automatizable sería fingir que la feature está más cubierta de lo que
está — que es exactamente la lección que la 007 y la 009 dejaron escrita sobre atravesar una frontera
ajena al menos una vez.

### D-331 — Cero dependencias nuevas

**Decisión**: nada entra en `libs.versions.toml`.

**Por qué se dice**: la 010 midió lo que cuesta una dependencia que parecía obvia, y acabó
retirándola entera. Todo lo que esta feature necesita —OkHttp, kotlinx-serialization, Compose, Koin,
Turbine, MockK, MockWebServer con TLS— ya está.

---

## 8. Lo que queda sin resolver

- **Las dos cifras del plan gratuito** (30 peticiones por minuto, 1.500 al día) **siguen sin
  confirmar**. Se heredan sin resolver desde la feature 009 y exigen el panel del proveedor con las
  credenciales del propietario. El coordinador sigue siendo una estimación propia; el 429 real lo
  corrige cuando llega.
- **Cuánto aguanta la defensa de ámbito contra un atacante decidido.** No se puede acotar desde aquí.
  Lo que sí se sabe, y está escrito, es qué caso cubre y qué caso no (D-307).
