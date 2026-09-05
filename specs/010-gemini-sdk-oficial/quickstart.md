# Quickstart: El documento se envía entero, no su texto

**Feature**: `010-gemini-sdk-oficial` | **Fase**: 1 | **Fecha**: 5 de septiembre de 2026

Cómo se valida esta feature: las cuatro puertas de siempre y una travesía de la frontera que ninguna
prueba puede sustituir.

> Este documento pedía una **quinta** puerta, `assembleRelease`, porque la librería que se iba a
> adoptar arrastraba varios megas de dependencias y había que activar la optimización. La librería no
> se puede usar en Android (`research.md` D-227) y la quinta puerta se fue con ella.

---

## 0. Requisitos previos

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Java **no está en el `PATH`**; el JBR de Android Studio sirve. El proyecto sigue compilando a Java 11.

Para las comprobaciones manuales hace falta una credencial en `local.properties`:

```properties
GEMINI_API_KEY=<la clave>
```

`local.properties` está en `.gitignore` y la clave **no puede llegar al repositorio**. Antes de
cualquier commit conviene comprobarlo, y **hay que buscar los dos formatos**: el clásico empieza por
`AIza` y el que se emite hoy por `AQ.`. Buscar solo uno es exactamente cómo se da por limpio un
repositorio que no lo está.

```bash
git grep -nE 'AIza[0-9A-Za-z_-]{30,}|AQ\.[0-9A-Za-z_-]{30,}' -- . ':!app/google-services.json' \
  && echo "¡PARA!" || echo "limpio"
```

**`app/google-services.json` se excluye a conciencia, y conviene saber por qué.** Contiene un
`current_key` que empieza por `AIza` y que **está versionado desde el commit base a propósito**: es la
clave de Android de Firebase, restringida en la consola por nombre de paquete y huella de firma, y el
fichero tiene que estar en el repositorio para que la build funcione. No es la credencial de Gemini
—comprobado: la de Gemini son 53 caracteres empezando por `AQ.A`, la de Firebase 39 empezando por
`AIza`—. Sin esta exclusión la comprobación da un acierto **siempre**, y una comprobación que siempre
falla es una comprobación que se deja de mirar.

**Sin credencial la build sigue en verde** y las cuatro primeras puertas pasan enteras (FR-043,
SC-010). Solo el §3 y el §3 bis la necesitan.

### 0 bis. Datos pendientes de confirmar

| Qué | Valor | Estado |
|---|---|---|
| Conservación del fichero subido | **48 h exactas** | **CONFIRMADO** el 5 de septiembre de 2026: el servicio devuelve `expirationTime` en la propia respuesta de la subida, exactamente 48 h después de `createTime` |
| Tamaño máximo por fichero | 2 GB | No se comprueba: el descargador ya capa en 25 MB |
| Peticiones por minuto / por día | 30 / 1.500 | Panel del proveedor. Heredado de la 009, **sigue sin confirmar** |
| ¿La subida cuenta como petición de cuota? | Se supone que no | Sin confirmar. No cambia el diseño si resulta falso: bastaría con envolver también la subida |
| ¿`gemini-3.1-flash-lite` acepta `file_data` y `responseJsonSchema` a la vez? | Sí | **CONFIRMADO**, §3 bis puntos 1 y 2 |

---

## 1. Las cuatro puertas

En este orden, las de la constitución.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

**Avisos que cuestan tiempo si se ignoran:**

- **La tanda instrumentada tarda casi tres horas, no trece minutos.** Medido: 154 pruebas en 116-161
  minutos, mediana de unos 46 s por prueba. Lánzala en segundo plano.
- Antes de la tanda instrumentada: `adb shell settings put secure navigation_mode 0`. Las pruebas de
  márgenes solo muerden con navegación de tres botones.
- **Con más de un dispositivo conectado, la tanda se reparte entre todos.** Si hay un móvil enchufado
  con la pantalla bloqueada, fallan en bloque con `No compose hierarchies found in the app`. O se
  desconecta, o se deja desbloqueado, o `ANDROID_SERIAL=emulator-5554`.
- `--tests` **no existe** en `connectedDebugAndroidTest`. Para una clase suelta:
  `-Pandroid.testInstrumentationRunnerArguments.class=com.jrblanco.boccantabria.<Clase>`, y
  opcionalmente `#<metodo>`. Es la diferencia entre iterar en un minuto o en tres horas.

---

## 2. Lo que las pruebas ya afirman

| Qué | Dónde |
|---|---|
| La petición lleva una referencia a fichero y **no** el texto del documento | `OkHttpGeminiSummaryDataSourceTest` |
| El esquema viaja y se pide salida JSON | `OkHttpGeminiSummaryDataSourceTest` |
| 401/403 → «no configurado»; 429 → cuota con su retraso; 5xx → reintento | `OkHttpGeminiSummaryDataSourceTest` |
| `finishReason` distinto de `STOP` → resumen no fiable, nunca un texto cortado en pantalla | `OkHttpGeminiSummaryDataSourceTest` |
| Una parte marcada como razonamiento se salta **por su marca y no por su posición** | `OkHttpGeminiSummaryDataSourceTest` |
| Irse de la pantalla es una cancelación y **no** un error de conexión | `OkHttpGeminiSummaryDataSourceTest` |
| Un reintento sin cuota conserva el rechazo original | `OkHttpGeminiSummaryDataSourceTest` |
| **Ni la credencial ni el contenido del documento aparecen en el registro** | `OkHttpGeminiSummaryDataSourceTest` |
| Sin credencial no se hace ninguna llamada de red | `OkHttpGeminiSummaryDataSourceTest`, `GeminiApiKeyProviderTest` |
| El nombre con el que el documento viaja no lleva nada de la persona | `AiDocumentSessionStoreTest` |
| Nada fuera de `data` nombra los tipos del servicio de IA | `ArchitectureRulesTest` (regla 9) |
| Dos aperturas de la misma clave → **una** subida | `AiDocumentSessionStoreTest` |
| Abrir otra publicación retira la anterior | `AiDocumentSessionStoreTest` |
| `release` de una clave que no es la actual no hace nada | `AiDocumentSessionStoreTest` |
| Dos aperturas concurrentes → **una** subida | `AiDocumentSessionStoreTest` |
| Un documento protegido produce `Encrypted` y no se sube | `AndroidxPdfPageCounterTest`, `AiSummaryRepositoryImplTest` |
| El orden de las propiedades del esquema, con la prosa la última | `SummarySchemaTest` |
| La sustitución del prompt ocurre **después** de `trimIndent()` | `SummaryPromptFactoryTest` |
| Citas fuera de `1..totalPages` se descartan | `SummaryValidatorTest` |
| Observar la pestaña no genera nada | `AiSummaryRepositoryImplTest`, `AiSummaryFlowIntegrationTest` |
| Un resumen guardado no cuesta una segunda generación | `AiSummaryFlowIntegrationTest` |
| La clave `_v2` del aviso no se lee | `AiPreferencesTest` |
| Ningún mensaje nombra proveedor, modelo ni jerga técnica | `AiErrorMessagesTest` |
| El grafo de Koin resuelve entero | `KoinModulesTest` |
| Los trece estados de la pestaña se dibujan | `AiSummaryTabTest` |

---

## 3. Comprobación manual

Con credencial, en un dispositivo o emulador.

**Recorrido del 5 de septiembre de 2026 en el emulador**, contra el boletín real del día (39
anuncios) y el servicio real. Los puntos 1 a 6 están comprobados; el resto queda para el propietario.

1. ✅ Abrir una publicación con documento y pulsar **Generar resumen**. Aparece el aviso **reescrito**
   —«envía el documento oficial completo… lo conserva un tiempo limitado… lo retira al salir»—.
2. ✅ Aceptar. Se ve «Preparando el documento…» y después «Generando el resumen…». **No** aparece
   «Leyendo el texto del documento…», que ya no existe.
3. ✅ El resumen sale con su prosa, sus secciones —importes incluidos— y sus chips de página, con la
   descripción accesible «Abrir en el documento oficial la página 1».
4. ✅ Pulsar **Volver a generar** sin salir: el registro dice `session: reusing document`, sin subida.
5. ✅ Salir al boletín y volver a entrar. El resumen aparece al instante.
6. ✅ Un documento de **54 páginas y 981 KB** sube y queda `ACTIVE` sin un solo sondeo.
7. Abrir una publicación ya resumida **antes** de actualizar. El resumen se ve, marcado como hecho con
   una versión anterior. _Pendiente: exige una instalación previa a esta versión._
7. Abrir una publicación **sin** documento. El botón no ofrece generar nada.
8. Pulsar Generar y salir de la pantalla a mitad. Al volver, ningún mensaje de error.
9. Poner el móvil en modo avión y pulsar Generar. Mensaje de sin conexión, con reintento.
10. Abrir una publicación cuyo PDF sea un **escaneado**. Debe resumirse (SC-001, la comprobación más
    importante de esta feature).
11. Generar varios resúmenes seguidos hasta tocar la cuota. Debe distinguirse «espera unos segundos»
    de «vuelve mañana».
12. Quitar `GEMINI_API_KEY` de `local.properties`, recompilar y pulsar Generar: «no está configurado»,
    sin ningún intento de red.
13. Copiar y compartir un resumen: la advertencia debe ir **dentro** del texto.
14. Recorrer el resto de la aplicación —arranque, boletín, panel de secciones, búsqueda, guardados,
    detalle, visor, acerca de— y comprobar que nada se ha movido.

---

## 3 bis. La travesía real de la frontera — obligatoria en esta feature

No sustituible por pruebas, y el motivo está escrito en `CLAUDE.md`: los dos defectos que de verdad
rompieron el Resumen IA en un móvil vivían **al otro lado** de la frontera con el servicio, donde
todas las pruebas ponen dobles. Los encontró el registro en un dispositivo real. Esta feature mueve
esa frontera y además **añade una segunda**: subir un fichero es una operación nueva, con su propio
ciclo de estados y sus propios modos de fallo.

```bash
adb -s <serie> logcat -s BOC:V
```

**Travesía del 5 de septiembre de 2026**, hecha con `curl` contra el servicio real y la credencial
del propietario, antes de tocar la interfaz. Los cinco primeros puntos no necesitan la aplicación
instalada: lo que comprueban es el protocolo, y hacerlo así los separa de cualquier defecto de UI.

| # | Riesgo | Qué se comprobó | Resultado |
|---|---|---|---|
| 1 | El modelo no acepta una parte de tipo fichero | `generateContent` con `file_data` sobre un PDF subido | ✅ **HTTP 200 en 3,1 s** |
| 2 | El modelo no respeta `responseJsonSchema` | Esquema estricto con `additionalProperties: false` | ✅ devolvió exactamente los campos pedidos, `finishReason=STOP` |
| 3 | **El servicio no sabe leer un escaneado** (SC-001) | Un PDF **rasterizado a imagen**, sin capa de texto —comprobado: cero operadores `BT`, y el servicio lo cobra como `modality: IMAGE`— | ✅ **resumen completo**: organismo, fecha, plazo de quince días hábiles y páginas citadas, todo leído de los píxeles |
| 4 | El protocolo de subida reanudable no funciona como se implementó | Las tres llamadas: `start` → cabecera `x-goog-upload-url` → `upload, finalize` | ✅ y el fichero llega **directamente a `ACTIVE`** para un PDF pequeño, sin pasar por `PROCESSING` |
| 5 | El fichero no se retira | `DELETE /v1beta/files/<nombre>` sobre los tres de prueba | ✅ HTTP 200 los tres; el listado queda a cero |
| 6 | Se sube dos veces sin querer | Regenerar dentro de la misma visita **no** produce una segunda línea `upload:` | _pendiente: exige la aplicación instalada_ |
| 7 | La subida consume cuota de generación | Varias subidas seguidas sin generar: ¿aparece un 429? | _pendiente_ |
| 8 | Se filtra la credencial o el documento | `logcat` completo de una generación: cero apariciones de la clave y cero de texto del documento | _pendiente: exige la aplicación instalada_ |
| 9 | La cancelación se clasifica como fallo de red (D-218) | Pulsar Atrás mientras genera: **ningún** «No hay conexión» al volver | _pendiente: exige la aplicación instalada_ |

**Un 404 que apareció una vez y no se ha vuelto a reproducir.** El primer intento del punto 1 devolvió
`HTTP 404` en 0,1 s con el cuerpo vacío; el mismo cuerpo, contra la misma URL y con la misma clave,
devolvió 200 un minuto después, y el modelo aparece en `GET /v1beta/models`. No hay explicación
comprobada, así que no se inventa una: queda anotado por si vuelve.

**El punto 3 era el único criterio de éxito que dependía de algo que no controlamos, y se cumple.**
Un boletín escaneado se resume. Si algún día dejara de cumplirse, hay que decirlo en `spec.md` y en el
informe, no disimularlo.

**Si los puntos 1 o 2 fallan**, la salida es `AiSummaryConstants.MODEL_ID`, que está ahí exactamente
para esto (D-213).

---

## 4. Si algo falla

| Síntoma | Causa probable | Qué mirar |
|---|---|---|
| `IllegalStateException: SECURITY FATAL: Initializing the Client with an API Key…` | Alguien ha vuelto a meter `com.google.genai` | D-227: la librería **no se puede usar** en Android. La novena regla de Konsist debería haberlo parado antes |
| «No se ha podido leer este documento» siempre | El servicio rechaza el fichero | `upload:` en el registro; puede ser el modelo (D-213) |
| «No se ha podido generar el resumen» a menudo | Tiempos agotados del servicio | Es conocido: los tres intentos con backoff se ganan el sueldo. Si persiste, `MODEL_ID` |
| «No hay conexión» al volver de haber salido | La cancelación se clasificó mal | D-218: `ensureActive()` como primera línea del `catch (IOException)` |
| El aviso no reaparece tras actualizar | La clave de la preferencia no subió a `_v3` | `AiPreferences`, y `AiPreferencesTest` debería estar rojo |
| La subida se queda colgada | El sondeo no encuentra `ACTIVE` | `upload:` en el registro dice cuántos sondeos hizo. El tope son 20 |
| `upload: start gave no upload url` | El servicio no devolvió la cabecera `x-goog-upload-url` | Es el primer paso del protocolo reanudable; revisar las cabeceras `X-Goog-Upload-*` |
| Las pruebas instrumentadas fallan en bloque | Hay un móvil conectado con la pantalla bloqueada | `ANDROID_SERIAL=emulator-5554` |
