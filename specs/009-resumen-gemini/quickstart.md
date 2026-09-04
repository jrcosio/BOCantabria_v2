# Quickstart: Resumen IA con proveedor nuevo

**Feature**: `009-resumen-gemini` | **Fase**: 1 | **Fecha**: 4 de septiembre de 2026

Cómo se valida esta feature de extremo a extremo. Lo que aquí se pide es lo que hay que haber pasado
antes de darla por terminada.

---

## 0. Requisitos previos

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Java no está en el `PATH`; se usa el JBR que trae Android Studio.

En `local.properties`, en la raíz del repositorio y **fuera de git**:

```properties
GEMINI_API_KEY=<la clave>
```

La clave ya está puesta en la máquina del propietario. La línea `GROQ_API_KEY` puede quedarse: dejará
de leerse. Como respaldo para integración continua sirve la variable de entorno homónima.

**Sin la clave la build sigue en verde** y las pruebas pasan: el campo queda en cadena vacía y la
pantalla lo traduce en «el servicio de resúmenes no está configurado en esta aplicación». Eso es
FR-033 y se comprueba en el §3, punto 12.

### 0 bis. Confirmar los límites del plan gratuito — hazlo antes de nada

`GeminiRateLimitCoordinator` lleva dos constantes, `REQUESTS_PER_MINUTE = 30` y
`REQUESTS_PER_DAY = 1_500`, que son **los valores documentados históricamente para Flash-Lite, no
los confirmados de esta cuenta**. Google ya no publica los límites del plan gratuito en su
documentación: remite al panel del proyecto en <https://aistudio.google.com/rate-limit>.

Ábrelo con la cuenta del propietario, mira las cifras de `gemini-3.5-flash-lite` y **ajusta las dos
constantes si no coinciden**. Es una línea cada una. Si son más generosas de lo previsto no se rompe
nada; si son más estrictas, el contador dejaría pasar peticiones que el servicio va a rechazar, y la
persona vería un error evitable.

**Y mira una tercera cifra: los tokens por minuto.** El guardarraíl `DocumentText.MAX_CHARACTERS =
480_000` son unos **109.000 tokens** —4,39 caracteres por token, medido contra el servicio real el 4 de
septiembre de 2026, no supuesto—. Si el límite por minuto del plan gratuito estuviera **por debajo de
110.000**, hay que bajar el tope: un documento sentado en el techo sería irresumible para siempre y
**ninguna prueba lo detectaría**, porque todas se quedan de este lado de la frontera.

---

## 1. Las cuatro puertas

En este orden, y las cuatro en verde:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

**Avisos sobre la tercera**: la tanda instrumentada completa tarda **casi tres horas**, no trece
minutos. Lánzala en segundo plano. Y antes:

```bash
adb shell settings put secure navigation_mode 0   # tres botones: MainShellBottomInsetTest lo exige
export ANDROID_SERIAL=emulator-5554               # con más de un dispositivo, la tanda se reparte
```

Para iterar sobre una sola clase, `--tests` **no existe** en `connectedDebugAndroidTest`:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jrblanco.boccantabria.ui.detail.AiSummaryTabTest
```

Informes: `app/build/reports/tests/testDebugUnitTest/index.html` y
`app/build/reports/lint-results-debug.html`.

### Dos comprobaciones de secretos

```bash
git log -p --all -S 'AIza' | head          # formato clásico
git log -p --all -S 'AQ.A' | head          # formato que se emite hoy
grep -rn 'GEMINI_API_KEY' --include='*.kt' --include='*.xml' app/src/
```

Las dos primeras deben salir vacías. La tercera debe devolver **exactamente un** resultado:
`BuildConfigGeminiApiKeyProvider`. Cualquier otro sitio que la nombre es un sitio de más.

> **Hay dos formatos de clave y hace falta buscar los dos.** Comprobado contra la clave del
> propietario el 4 de septiembre de 2026: tiene 53 caracteres y empieza por `AQ.A`, no por `AIza`.
> Ambos existen. Y ninguno es el `gsk_` del proveedor anterior: buscar el prefijo equivocado es
> exactamente cómo se declara limpio un repositorio que no lo está.

---

## 2. Lo que las pruebas ya afirman

No hace falta comprobar a mano nada de esto. Está cubierto y falla la build si se rompe.

| Qué | Dónde |
|---|---|
| La petición pide el modelo, el esquema y los ajustes acordados, incluidos `store: false` y `thinking_level: "minimal"` | `OkHttpGeminiSummaryDataSourceTest` |
| La credencial viaja en la cabecera `x-goog-api-key` y **no** en el cuerpo | `OkHttpGeminiSummaryDataSourceTest` |
| Sin credencial no sale ni una petición | `OkHttpGeminiSummaryDataSourceTest` |
| 401 y 403 no se reintentan; los 5xx sí, y se rinde tras tres intentos | `OkHttpGeminiSummaryDataSourceTest` |
| El texto se busca en el paso `model_output`, no en `steps[0]` | `OkHttpGeminiSummaryDataSourceTest` |
| Un `status` `incomplete` sin contenido se traduce a `Malformed` y **el registro dice cuál era** | `OkHttpGeminiSummaryDataSourceTest` |
| Un resumen en blanco se reintenta **una sola vez** | `OkHttpGeminiSummaryDataSourceTest` |
| Un reintento sin margen de cuota conserva el motivo **original** | `OkHttpGeminiSummaryDataSourceTest` |
| Nada de lo registrado contiene la credencial ni el texto del documento | `OkHttpGeminiSummaryDataSourceTest` |
| Un 429 con retraso corto es minuto; con retraso de escala diaria, día | `GeminiRateLimitCoordinatorTest` |
| El contador propio distingue el límite del minuto del diario, sin cabeceras | `GeminiRateLimitCoordinatorTest` |
| El backoff crece, está acotado y lleva dispersión | `GeminiRateLimitCoordinatorTest` |
| `parseRetryDelaySeconds` acepta `56` y `56s` y devuelve `null` ante lo demás, sin lanzar | `GeminiRateLimitCoordinatorTest` |
| La prosa se genera **la última** y las seis listas antes; `maxItems: 10` en las seis | `SummarySchemaTest` |
| El tope de caracteres no se cruza nunca; solo la primera página se corta por dentro | `DocumentTextTest` |
| Se envían **todas** las páginas con texto de un documento normal | `DocumentTextTest` |
| El validador recorta a diez y **lo advierte** | `SummaryValidatorTest` |
| El validador sustituye la cobertura que afirma el servicio por la real | `SummaryValidatorTest` |
| El prompt pide priorizar cuando una sección tiene más de diez | `SummaryPromptFactoryTest` |
| El prompt conserva la cláusula antiinyección y no envía nada de la persona | `SummaryPromptFactoryTest` |
| Un `summary_json` escrito por la versión anterior sigue decodificándose | `AiSummaryRepositoryImplTest` |
| Un resumen con el modelo anterior se marca obsoleto, **no** se borra | `AiSummaryRepositoryImplTest` |
| La clave antigua del aviso **no** se lee | `AiPreferencesTest` |
| El grafo de Koin resuelve con los tipos renombrados | `KoinModulesTest` |
| Ningún mensaje de la interfaz nombra al proveedor ni la tecnología | `AiErrorMessagesTest` |
| Los veintiún estados de la pestaña siguen dibujándose igual | `AiSummaryTabTest` (sin cambios) |

---

## 3. Comprobación manual

Con la aplicación instalada en un dispositivo o emulador, y la clave configurada.

1. **Un documento largo cubre entero.** Abre una publicación con documento de más de diez páginas y
   pulsa *Generar resumen*. **No debe aparecer** el aviso «Documento de N páginas. Se analizarán las M
   primeras», ni antes ni junto al resultado.
2. **La ficha llega hasta el final del documento.** Comprueba que hay elementos con páginas de la
   segunda mitad. Pulsa un chip de página: debe abrir el documento oficial por esa página.
3. **Ninguna sección pasa de diez elementos.** En una publicación larga —un presupuesto, un listado—,
   cuenta. Si el documento sustentaba más, debe haber un aviso en la sección *Advertencias*.
4. **Nada se genera solo.** Abre la pestaña Resumen IA de una publicación sin resumen y no toques
   nada. No debe pasar nada.
5. **El aviso de envío externo reaparece una vez.** Si ya lo habías aceptado antes de actualizar, debe
   volver a salir, y ahora con la frase de que el servicio puede usar el texto de ese documento
   público para mejorar sus modelos. *Cancelar* no debe enviar nada. Tras aceptar, no vuelve a salir.
6. **Un resumen viejo sigue ahí, marcado.** Con un resumen generado antes de actualizar: se muestra
   completo, sin red, y con «Este resumen se hizo con una versión anterior del documento».
7. **Regenerar lo sustituye.** Pulsa *Volver a generar* sobre ese resumen: en ningún momento la
   pantalla debe quedarse sin resumen que mostrar.
8. **Un resumen guardado no consulta el servicio.** Sal de la publicación, vuelve a entrar y mira el
   registro: cero líneas `gemini:`.
9. **Copiar y compartir llevan la advertencia dentro del texto.** Pégalo en cualquier sitio: la frase
   «Resumen generado por inteligencia artificial. Puede contener errores. Consulta siempre el PDF
   oficial.» tiene que ir la primera.
10. **Sin conexión.** Activa el modo avión y pulsa generar: «No hay conexión…», con opción de
    reintentar y de abrir el documento oficial.
11. **Un documento sin texto.** Sobre una publicación con PDF escaneado: «Este documento no contiene
    texto que la aplicación pueda analizar», **sin** botón de reintentar, y **cero** líneas `gemini:`
    en el registro.
12. **Sin credencial.** Quita `GEMINI_API_KEY` de `local.properties`, recompila e instala. La build
    debe seguir en verde, las pruebas pasar, y al pulsar generar debe decir que el servicio no está
    configurado, sin ofrecer reintento.
13. **Abandonar durante la generación.** Pulsa generar y sal de la pantalla antes de que termine. No
    debe quedarse trabajo en marcha ni aparecer nada al volver a otra publicación.
14. **Con lectores de pantalla.** Activa TalkBack y recorre la ficha: la advertencia debe anunciarse,
    no solo dibujarse.

### Resultado de la comprobación manual: 4 de septiembre de 2026

En `emulator-5554` (API 37, tres botones, animaciones a cero), con la aplicación de depuración y la
credencial del propietario, sobre publicaciones **reales** del boletín del 4 de septiembre.

| Punto | Resultado |
|---|---|
| 1. Documento largo sin aviso de parcialidad | ✅ `summary: sending pages 8/8, 14643 chars` en un anuncio del Ayuntamiento de Laredo. **Ese documento tiene 14 643 caracteres y el techo del proveedor anterior era 14 400: se habría leído a medias.** Ningún aviso de cobertura en pantalla |
| 2. Elementos de todo el documento y chips que abren la página | ✅ Chips de **Página 1 a Página 8**, los ocho, con su descripción accesible «Abrir en el documento oficial la página N» |
| 3. Ninguna sección pasa de diez | ✅ Tres puntos clave, dos afectados, un plazo, una actuación, un recurso |
| 4. Nada se genera solo | ✅ Abrir la pestaña Resumen IA deja el registro **vacío** |
| 5. El aviso reaparece una vez, con la frase nueva | ✅ Sembrada solo la clave antigua `ai_notice_accepted`, el aviso vuelve a salir con «El servicio puede usar el texto de este documento público para mejorar sus modelos». Cancelar no envía nada. Tras aceptar quedan **las dos** claves: la antigua intacta y `ai_notice_accepted_v2` |
| 9. La advertencia va dentro y es anunciable | ✅ Texto visible «Comprueba siempre el texto oficial» **y** descripción accesible propia |
| 11. Documento sin texto | Pendiente: no apareció ninguna publicación escaneada en el boletín del día |
| 13. Abandonar durante la generación | ✅ **Encontró un defecto y está arreglado.** Antes, pulsar Atrás registraba `network: SocketException` y `summary failed: Offline`, y al volver se leía «No hay conexión» de un fallo inexistente (D-119). Ahora no registra **nada**: irse no es un fallo (FR-006) |
| 12. Sin credencial | ✅ Comentada la clave, `assembleDebug` y `testDebugUnitTest` siguen en verde, y la pantalla dice «El servicio de resúmenes no está configurado en esta aplicación» **sin botón de reintentar** y con **cero** peticiones |
| Secciones vacías ocultas | ✅ El anuncio de Laredo no trae importes y la sección no aparece |
| Plazos relativos literales | ✅ «diez días hábiles desde el día siguiente a la publicación de este anuncio en el Boletín Oficial de Cantabria» |

**Y el camino de fallo se vio sin provocarlo**, que es lo más valioso de la pasada. Sobre otra
publicación el servicio dio dos tiempos agotados y después un 500:

```
summary: sending pages 1/1, 1866 chars
gemini: network: InterruptedIOException: timeout
gemini: network: InterruptedIOException: timeout
gemini: HTTP 500: gemini-3.5-flash-lite is currently experiencing high demand, spikes in demand
        are usually temporary. Please try again later.
summary failed: Unknown
```

Tres intentos, la explicación del proveedor en el registro, y en pantalla «No se ha podido generar el
resumen» con reintento. Es exactamente la disciplina de D-117: la frase que ve quien lee no dice
códigos ni nombra al servicio, y el registro sí. Al reintentar, funcionó.

**Dos avisos para quien repita esto**: el servicio se agota por tiempo con cierta frecuencia y **el
reintento salva la mayoría de las veces** —lo cual demuestra que la lógica de reintento se gana el
sueldo—; y conducir la interfaz con coordenadas fijas no vale, porque la altura de la cabecera cambia
con la publicación. Hay que leer la pantalla entre toque y toque.

---

## 3 bis. La travesía real de la frontera — obligatoria en esta feature

**Esto no es opcional y no lo puede sustituir ninguna prueba automática.** El §3 bis de la feature 007
terminaba diciendo «no hace falta repetirlo salvo que cambie el esquema o el modelo». Esta feature
cambia **los dos**, así que todo lo que allí estaba verificado queda invalidado y hay que verificarlo
de nuevo contra Gemini.

Y hay un motivo escrito para insistir: los dos defectos que de verdad rompían el Resumen IA en un
móvil —el modelo dejando la prosa en blanco, y el techo de salida cortando el JSON— **no los podía
encontrar ninguna prueba automática**, porque todas usan dobles en la frontera con el servicio y el
defecto estaba justo al otro lado. Los encontró el registro en un dispositivo real.

```bash
adb -s <serie> logcat -s BOC:V
```

Genera un resumen de una publicación con plazos e importes y comprueba, uno por uno:

| # | Qué se comprueba | Qué se espera | Si falla |
|---|---|---|---|
| 1 | La secuencia de fases | `summary: document ready, extracting` → `summary: sending …` → resumen en pantalla | Mira dónde se corta: la fase lo dice |
| 2 | **El esquema se acepta tal cual** | HTTP 200, y la ficha con sus doce campos | Es el riesgo principal (D-105). Entra el plan B: aplanar los cinco `$defs` en línea, sin cambiar ningún nombre de campo |
| 3 | **El orden se respeta** | Prosa **y** listas estructuradas llenas | Si la prosa viene y las listas vacías, el orden no se está respetando: añade `propertyOrdering` con las doce propiedades |
| 4 | El razonamiento está al mínimo | `usage.total_thought_tokens` bajo o cero | Revisa que `thinking_level` se serialice: sin `encodeDefaults = true` no se envía (D-106) |
| 5 | La respuesta no llega cortada | `status: completed`, nunca `incomplete` | Con 8.000 de techo no debería pasar. Si pasa, algo va mal en el prompt |
| 6 | Un plazo relativo se conserva literalmente | «Quince días hábiles desde la publicación», no una fecha | Es comportamiento del modelo y cambia con el modelo. Refuerza la cláusula del prompt |
| 7 | Un documento sin texto no llega al servicio | Cero líneas `gemini:` | La puerta de FR-015 se ha soltado |
| 8 | **La credencial no aparece** | `adb logcat -d \| grep -cE 'AIza\|AQ\.A'` → `0`. **Los dos prefijos**: el clásico y el que se emite hoy | Fallo grave: hay algo registrando lo que no debe |
| 9 | El contenido del documento no aparece | Del cuerpo solo nombres de campo y tamaños | Busca un interceptor de registro: está prohibido |

**Anota el resultado en `tasks.md`** cuando lo pases. Es lo único de esta feature que no queda
registrado por una prueba.

### Resultado: pasada el 4 de septiembre de 2026

Contra el servicio real, con la clave del propietario, el esquema tomado del propio
`SummarySchema.kt` y el prompt del propio `SummaryPromptFactory.kt`. Petición de 8 880 bytes sobre un
anuncio municipal de dos páginas con plazos, importes y recursos.

| # | Resultado |
|---|---|
| 1 | **HTTP 200.** `status: completed` |
| 2 | **El esquema se acepta verbatim**, con sus cinco `$defs`, sus `$ref` y `additionalProperties: false`. El plan B de D-105 no hace falta |
| 3 | **El orden se respeta exactamente**: las doce propiedades llegaron en el orden declarado, con `plainLanguageSummary` la última. Prosa de 809 caracteres **y** las seis listas llenas —3 puntos clave, 3 plazos, 2 importes, 1 actuación, 2 recursos—. La invariante de D-030 se conserva sin escribir nada |
| 4 | **`total_thought_tokens = 0`**: `thinking_level: "minimal"` se aplica. Coste total 2 402 tokens (1 376 de entrada, 1 026 de salida) para dos páginas |
| 5 | `status: completed`, nunca `incomplete`. El techo de 8 000 sobra: la respuesta usó 1 026 |
| 6 | **Los plazos relativos se conservan literalmente**: «Treinta días hábiles contados desde el día siguiente al de la publicación de este anuncio en el Boletín Oficial de Cantabria» y «Dos meses». Ninguna fecha calculada (FR-016 de la 007) |
| 7 | No aplicable en esta llamada mínima; lo cubre la prueba unitaria y el punto 11 del §3 |
| 8 | La credencial no se imprimió en ningún momento |
| 9 | Del cuerpo solo se leyeron nombres de campo y tamaños |

**Y encontró dos cosas que estaban mal escritas, que es exactamente para lo que sirve:**

1. **El paso de razonamiento se llama `thought`, no `model_thoughts`** como dice la documentación. Y
   **llega siempre primero**: tomar `steps[0]` habría fallado en el cien por cien de las respuestas.
   El analizador ya buscaba por tipo, así que el código estaba bien y el comentario mal. La prueba
   usa ahora el nombre real.
2. **La clave del propietario empieza por `AQ.A`, no por `AIza`.** Todo lo que había escrito sobre el
   prefijo —el KDoc de la build, la prueba de la credencial, la comprobación de secretos de §1 y
   `CLAUDE.md`— buscaba el prefijo equivocado, que es la única forma de dar por limpio un repositorio
   que no lo está. Corregido en los cuatro sitios, con los dos formatos.

---

## 4. Si algo falla

**«No se ha podido construir un resumen fiable»** — es `InvalidResponse`, y cubre tres cosas
distintas que solo el registro separa: cuerpo que no parsea (`gemini: unparseable answer of N
chars`), respuesta sin paso `model_output` (`gemini: no model_output, status=…`), y prosa en blanco
(`gemini: blank summary: …`). Mira cuál es antes de tocar nada.

**«No se ha podido generar el resumen»** — es `Unknown`, y cubre **cuatro** situaciones: documento
que no se descarga, extracción rota, código HTTP sin mejor sitio, y cualquier excepción del camino. En
pantalla son la misma frase, a propósito (FR-027); en el registro no pueden serlo.

**Un 400 del servicio** — `gemini: HTTP 400: <lo que conteste>`. Las dos causas probables son el
esquema (plan B de D-105) y un parámetro que el modelo no admite: comprueba que no se estén enviando
`temperature`, `top_p` ni `top_k`.

**Un 500 que dice «high demand»** — es capacidad del proveedor, no nuestra. Para confirmarlo sin
adivinar, dos sondas de treinta segundos:

```bash
KEY=$(grep '^GEMINI_API_KEY=' local.properties | cut -d= -f2-)
BODY='{"model":"gemini-3.5-flash-lite","input":"Di solo: hola","store":false,
       "generation_config":{"thinking_level":"minimal","max_output_tokens":50}}'
curl -sS -m 25 -w '\n%{http_code}\n' -X POST https://generativelanguage.googleapis.com/v1beta/interactions \
  -H "x-goog-api-key: $KEY" -H 'Content-Type: application/json' -d "$BODY"
# y lo mismo cambiando flash-lite por flash
```

Si la mínima falla y la del modelo hermano responde, queda descartado todo lo nuestro —tamaño,
esquema, credencial, red— y es el modelo. Pasó el 4 de septiembre de 2026; está anotado en
`CLAUDE.md` con qué hacer y qué no.

**Un 429 antes de tiempo** — si el servicio rechaza cuando el contador propio decía que había margen,
las constantes de §0 bis son demasiado generosas. Ajústalas.

**Nada en el registro** — `FirebaseCrashReporter` escribe en logcat **solo** cuando
`BuildConfig.DEBUG`. Comprueba que estás en una build de depuración y que la etiqueta es `BOC`.

**`PROCESS STARTED` / `PROCESS ENDED` con el nombre del paquete** — es normal: `androidx.pdf` arranca
un proceso aislado por documento y muere al cerrar. No es un cierre inesperado. Y
`AconfigStorageReadException: android.graphics.pdf.flags` es ruido de la plataforma.
