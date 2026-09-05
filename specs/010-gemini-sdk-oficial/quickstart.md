# Quickstart: El documento se envía entero, no su texto

**Feature**: `010-gemini-sdk-oficial` | **Fase**: 1 | **Fecha**: 5 de septiembre de 2026

Cómo se valida esta feature. Cinco puertas, no cuatro, y una travesía de la frontera que ninguna
prueba puede sustituir.

---

## 0. Requisitos previos

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Java **no está en el `PATH`**; el JBR de Android Studio es 21 y cubre de sobra el 17 que esta feature
exige.

Para las comprobaciones manuales hace falta una credencial en `local.properties`:

```properties
GEMINI_API_KEY=<la clave>
```

`local.properties` está en `.gitignore` y la clave **no puede llegar al repositorio**. Antes de
cualquier commit conviene comprobarlo, y **hay que buscar los dos formatos**: el clásico empieza por
`AIza` y el que se emite hoy por `AQ.`. Buscar solo uno es exactamente cómo se da por limpio un
repositorio que no lo está.

```bash
git grep -nE 'AIza[0-9A-Za-z_-]{30,}|AQ\.[0-9A-Za-z_-]{30,}' -- . && echo "¡PARA!" || echo "limpio"
```

**Sin credencial la build sigue en verde** y las cuatro primeras puertas pasan enteras (FR-043,
SC-010). Solo el §3 y el §3 bis la necesitan.

### 0 bis. Datos pendientes de confirmar

| Qué | Valor documentado | Cómo se confirma |
|---|---|---|
| Conservación del fichero subido | 48 h | §3 bis, punto 6 |
| Tamaño máximo por fichero | 2 GB | No se comprueba: el descargador ya capa en 25 MB |
| Peticiones por minuto / por día | 30 / 1.500 | Panel del proveedor. Heredado de la 009, sigue sin confirmar |
| ¿La subida cuenta como petición de cuota? | Se supone que no | §3 bis, punto 7 |

---

## 1. Las cinco puertas

En este orden. Las cuatro primeras son las de la constitución; la quinta la trae esta feature.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease          # nueva
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
- **La quinta puerta es nueva y puede fallar de formas que nadie ha visto**, porque nunca se ha
  compilado release en este proyecto. Si R8 se queja, la salida es la keep rule concreta en
  `app/src/main/keepRules/genai.keep`, no desactivar la optimización.

---

## 2. Lo que las pruebas ya afirman

| Qué | Dónde |
|---|---|
| La petición lleva una referencia a fichero y **no** el texto del documento | `GenAiSummaryDataSourceTest` |
| El esquema viaja y se pide salida JSON | `GenAiSummaryDataSourceTest` |
| 401/403 → «no configurado»; 429 → cuota con su retraso; 5xx → reintento | `GenAiSummaryDataSourceTest` |
| `finishReason` distinto de `STOP` → resumen no fiable, nunca un texto cortado en pantalla | `GenAiSummaryDataSourceTest` |
| **Ni la credencial ni el contenido del documento aparecen en el registro** | `GenAiSummaryDataSourceTest` |
| Sin credencial no se hace ninguna llamada de red | `GenAiSummaryDataSourceTest`, `GeminiApiKeyProviderTest` |
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
| Nada fuera de `data` importa la librería | `ArchitectureRulesTest` (regla 9) |
| El grafo de Koin resuelve entero | `KoinModulesTest` |
| Los trece estados de la pestaña se dibujan | `AiSummaryTabTest` |

---

## 3. Comprobación manual

Con credencial, en un dispositivo o emulador.

1. Abrir una publicación con documento y pulsar **Generar resumen**. Debe aparecer el aviso
   **reescrito** —aunque ya se hubiera aceptado el anterior—, hablando del documento completo.
2. Aceptar. Deben verse en orden «Obteniendo el documento oficial…», «Preparando el documento…» y
   «Generando el resumen…». **No** debe aparecer «Leyendo el texto del documento…».
3. El resumen sale con su prosa, sus secciones y sus chips de página. Tocar un chip abre el visor por
   esa página.
4. Pulsar **Volver a generar** sin salir. La fase «Preparando el documento…» **no** debe reaparecer:
   el documento ya está preparado (SC-005).
5. Salir al boletín y volver a entrar. El resumen aparece al instante y sin red.
6. Abrir una publicación ya resumida **antes** de actualizar. El resumen se ve, marcado como hecho con
   una versión anterior.
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

### Puerta 5, manual: la versión optimizada

**Obligatoria (FR-042).** Nunca se ha ejecutado una versión optimizada de esta aplicación, así que
esto no es rutina.

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk   # o el nombre que salga
```

Recorrer, con la aplicación instalada desde ese APK: arranque, boletín del día, panel lateral de
secciones, búsqueda con filtros, guardados, detalle de publicación, visor del PDF, resumen IA
completo, acerca de. Prestar atención a lo que R8 rompe típicamente: pantallas en blanco, listas
vacías, cierres al abrir un destino, y cualquier cosa que dependa de reflexión.

| Fecha | Resultado | Notas |
|---|---|---|
| _pendiente_ | | |

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

| # | Riesgo | Qué se comprueba | Resultado |
|---|---|---|---|
| 1 | El modelo elegido no acepta una parte de tipo fichero | Un resumen completo de un PDF normal, de punta a punta | _pendiente_ |
| 2 | El modelo no respeta `responseJsonSchema` | El JSON llega y decodifica; la prosa **no** viene vacía; `finishReason=STOP` | _pendiente_ |
| 3 | **El servicio no sabe leer un escaneado** (SC-001) | Un PDF sin capa de texto produce un resumen con prosa y al menos una página citada | _pendiente_ |
| 4 | La subida falla o se queda colgada | El registro muestra `upload: ready` y cuántos sondeos hicieron falta | _pendiente_ |
| 5 | Se sube dos veces sin querer | Regenerar dentro de la misma visita **no** produce una segunda línea `upload:` | _pendiente_ |
| 6 | El fichero no se retira | Salir del detalle produce `session: released`; el fichero deja de estar disponible | _pendiente_ |
| 7 | La subida consume cuota de generación | Varias subidas seguidas sin generar: ¿aparece un 429? | _pendiente_ |
| 8 | Se filtra la credencial o el documento | `logcat` completo de una generación: cero apariciones de la clave y cero de texto del documento | _pendiente_ |
| 9 | Ktor 2.3.8 y OkHttp 5.5.0 no conviven (D-223) | Cualquier petición real funciona; ninguna `NoSuchMethodError` | _pendiente_ |
| 10 | La cancelación se clasifica como fallo de red (D-218) | Pulsar Atrás mientras genera: **ningún** «No hay conexión» al volver | _pendiente_ |

**Si el punto 3 falla**, SC-001 no se cumple y hay que decirlo en `spec.md` y en el informe, no
disimularlo. Es el único criterio de éxito de esta feature que depende de algo que no controlamos.

**Si el punto 9 falla**, la salida es D-223: excluir `ktor-client-okhttp` y sustituirlo por
`ktor-client-cio` o `ktor-client-android`.

---

## 4. Si algo falla

| Síntoma | Causa probable | Qué mirar |
|---|---|---|
| `NoClassDefFoundError: com/google/auth/...` | Alguien intentó excluir `google-auth-library` | D-220: no se puede. Está en la firma del constructor del cliente |
| Falla el empaquetado por entradas duplicadas en `META-INF` | Faltan las exclusiones | D-221, y el bloque `packaging` de `app/build.gradle.kts` |
| `NoSuchMethodError` en una llamada de red | Ktor 2.3.8 contra OkHttp 5.5.0 | D-223 y su salida |
| No compila: «class file has wrong version 61.0» | Sigue en Java 11 | D-219 |
| `assembleRelease` se queja de reglas que faltan | AGP 9 falla si un fichero de keep rules referenciado no existe | Crear `app/src/main/keepRules/genai.keep` |
| La app de release arranca y una pantalla sale vacía | R8 ha quitado algo que hace falta | Añadir la keep rule concreta. **No** desactivar la optimización |
| «No se ha podido leer este documento» siempre | El servicio rechaza el fichero | `upload:` en el registro; puede ser el modelo (D-213) |
| «No se ha podido generar el resumen» a menudo | Tiempos agotados del servicio | Es conocido: los tres intentos con backoff se ganan el sueldo. Si persiste, `MODEL_ID` |
| «No hay conexión» al volver de haber salido | La cancelación se clasificó mal | D-218: `ensureActive()` como primera línea del `catch (IOException)` |
| El aviso no reaparece tras actualizar | La clave de la preferencia no subió a `_v3` | `AiPreferences`, y `AiPreferencesTest` debería estar rojo |
| Las pruebas instrumentadas fallan en bloque | Hay un móvil conectado con la pantalla bloqueada | `ANDROID_SERIAL=emulator-5554` |
