# Quickstart — Feature 014: Estabilidad tras la auditoría

Cómo se comprueba que esta feature hace lo que dice. Tres partes: las cuatro puertas automáticas, las
reproducciones de la auditoría convertidas en pruebas —que aquí son la comprobación principal, porque
los cinco defectos viven en caminos de fallo que no se recorren a mano— y un recorrido manual corto en
el emulador para los dos que sí pueden provocarse.

---

## 0. Preparación

Java no está en el `PATH`; se usa el JBR de Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Antes de la tanda instrumentada, y **solo** para ella: navegación de tres botones y **un solo
dispositivo** conectado (o `export ANDROID_SERIAL=emulator-5554`).

```bash
adb shell settings put secure navigation_mode 0
```

---

## 1. Las cuatro puertas, en orden

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Informes: `app/build/reports/tests/testDebugUnitTest/index.html` y
`app/build/reports/lint-results-debug.html`. La tanda instrumentada tarda **unas dos horas**, no trece
minutos; en segundo plano. Esta feature no añade ninguna prueba instrumentada —no cambia ninguna
pantalla—, así que la tanda solo confirma que nada se rompió.

Para iterar sobre las pruebas unitarias de un hallazgo:

```bash
./gradlew :app:testDebugUnitTest --tests "*DocumentRepositoryImplTest*" --tests "*FileDocumentCacheTest*"
./gradlew :app:testDebugUnitTest --tests "*RunSyncCycleUseCaseTest*" --tests "*AlertFlowIntegrationTest*"
./gradlew :app:testDebugUnitTest --tests "*ReadRecoveryTest*"
./gradlew :app:testDebugUnitTest --tests "*CancellableCallTest*" --tests "*OkHttpGeminiDocumentUploaderTest*"
```

---

## 2. El esquema exportado de la versión 6

La columna nueva sube Room a la versión 6. El esquema lo genera la compilación y **se versiona**:

```bash
./gradlew :app:kspDebugKotlin
ls app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/   # debe listar 1.json … 6.json
git add app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/6.json
```

Sin `6.json` versionado, `BocDatabaseMigrationTest` lanza al abrir y el defecto solo se vería en
dispositivos actualizados. Comprobar en `6.json` que `publications` lleva `pending_alert_evaluation`
con `notNull: true`, `defaultValue: "0"` y su índice.

---

## 3. Las reproducciones de la auditoría, ahora como pruebas

La auditoría dejó en `docs/auditoria/diagnostico-*.log` cuatro líneas que demuestran los defectos.
Cada una tiene ahora una prueba que **falla antes del arreglo y pasa después**; ejecutarla en rojo sobre
`main` y en verde sobre la rama es la comprobación de FR-040.

| Línea del diagnóstico (antes) | Prueba que lo convierte en regresión |
|---|---|
| `Excepción escapada con checksum truncado: java.lang.IllegalArgumentException` | `DocumentRepositoryImplTest.a stored document whose checksum sidecar was truncated opens without downloading again` y `FileDocumentCacheTest.a sidecar that is present but invalid reads back as the unknown checksum instead of throwing` |
| `Estado observado tras fallo put: {write-failure=Downloading(...)}` | `DocumentRepositoryImplTest.a cache that cannot store the document fails visibly and can be retried` |
| `Intentos de registrar coincidencias: 1; entregas: 0` | `RunSyncCycleUseCaseTest.what could not be recorded is kept pending and delivered exactly once by the next cycle` y `AlertFlowIntegrationTest.a match the store could not record is delivered by the next cycle, once` |
| `Tras cancelar Job: Call.isCanceled=false, Job.isCompleted=false` | `CancellableCallTest.cancelling the coroutine cancels the call and returns before the response` y las cuatro de cancelación de los data sources |
| *(sin línea: la auditoría lo confirmó por semántica de `Flow`)* | `ReadRecoveryTest` y las reescrituras de `SavedPublicationRepositoryImplTest` / `SearchRepositoryImplTest` |

Para verlas fallar antes del arreglo: `git stash` de los cambios de producto (no de las pruebas) o, más
simple, escribir primero la prueba en cada tarea y ejecutarla antes de tocar el código, que es lo que
`tasks.md` ordena.

**Los diagnósticos originales.** `python3 docs/auditoria/ejecutar-diagnosticos.py` sigue en el disco del
propietario (no se versiona). `DiagnosticoDocumentos.java` y `DiagnosticoRed.java` usan APIs que esta
feature **no** cambia (`ensureLocalCopy`, `releaseUnused`, `download`) y deberían volver a compilar y
dejar de imprimir «Excepción escapada…», «Downloading» y `isCanceled=false`. `DiagnosticoAvisos.java`
construye el ciclo con proxies de `PublicationRepository` y `AlertRepository`, cuyos contratos **sí**
cambian (D-611, D-613), así que necesitará ajuste para compilar; la prueba de integración con Room real
es la comprobación durable.

---

## 4. Recorrido manual en el emulador

Solo los dos hallazgos que pueden provocarse a mano. Registro en otra terminal:

```bash
adb logcat -s BOC:V
```

### 4.1 STAB-001 — el lateral dañado

1. Abrir una publicación y su pestaña Documento; esperar a que se muestre. Volver a Inicio.
2. Dañar el lateral de esa copia (el nombre es un hash; el más reciente es el que se acaba de abrir):

   ```bash
   adb shell run-as com.jrblanco.boccantabria sh -c 'cd cache/documents && ls -t *.sha256 | head -1'
   adb shell run-as com.jrblanco.boccantabria sh -c 'cd cache/documents && : > "$(ls -t *.sha256 | head -1)"'
   ```

3. Volver a abrir la misma publicación y su documento.
   - **Esperado**: el documento se muestra; la aplicación no se cierra; en el registro aparece
     `document: checksum sidecar unreadable, served without checksum` y **ninguna** línea con el título
     ni la clave de la publicación.
   - **Antes de la feature**: la aplicación se cerraba, y volvía a cerrarse en cada intento.
4. Repetir con el lateral a medias (`printf 'abc' > …`) y en mayúsculas. Mismo resultado.

### 4.2 STAB-002 — el almacenamiento que falla

1. Con una publicación cuyo documento **no** esté aún descargado, hacer que el directorio de la caché
   no admita escrituras:

   ```bash
   adb shell run-as com.jrblanco.boccantabria sh -c 'mkdir -p cache/documents && chmod 555 cache/documents'
   ```

2. Abrir la pestaña Documento.
   - **Esperado**: en menos de un segundo, el estado de error con «Reintentar» (no el indicador de
     carga); en el registro, `document: fetch threw: …`.
   - **Antes de la feature**: indicador de carga indefinido.
3. Restaurar permisos y reintentar:

   ```bash
   adb shell run-as com.jrblanco.boccantabria sh -c 'chmod 755 cache/documents'
   ```

   - **Esperado**: el documento se descarga y se muestra.

### 4.3 STAB-003, STAB-004 y PERF-002 — qué mirar aunque no se provoquen

- **Ciclo de avisos**: con al menos un aviso activo, refrescar Inicio y comprobar en el registro la línea
  nueva del ciclo, por ejemplo `cycle: 0 new, 0 pending from earlier, 1 rule(s), nothing to evaluate`.
  Nunca un título, una palabra clave ni un nombre de aviso. El camino de recuperación se demuestra en
  `AlertFlowIntegrationTest` con Room real: no hay forma limpia de hacer fallar una escritura de Room a
  mano en el emulador.
- **Listas**: comportamiento idéntico al de siempre; la recuperación es invisible por diseño (D-616).
- **Cancelación**: abrir el documento más pesado de la edición y pulsar Atrás en cuanto empiece la
  descarga. En el registro **no** debe aparecer ninguna línea `network:` ni `summary failed: Network` a
  raíz de esa salida. Con un resumen IA: pedirlo, salir del detalle a los dos segundos, volver a entrar y
  pedirlo otra vez — la segunda petición no debe esperar a la primera (antes esperaba a que el servicio
  respondiera o agotara el tiempo).

---

## 5. Comprobaciones de privacidad y de contrato

```bash
# Ninguna línea de registro nueva lleva títulos, claves ni credenciales
grep -rn 'crashReporter.log(' app/src/main/java/com/jrblanco/boccantabria/data/repository/DocumentRepositoryImpl.kt \
  app/src/main/java/com/jrblanco/boccantabria/data/repository/ReadRecovery.kt \
  app/src/main/java/com/jrblanco/boccantabria/domain/usecase/RunSyncCycleUseCase.kt

# La columna nueva NO está en la lista blanca de updateColumns
grep -n "pending_alert_evaluation" app/src/main/java/com/jrblanco/boccantabria/data/source/local/PublicationDao.kt
# → debe aparecer solo en pendingAlertEvaluation() y markAlertsEvaluated(), nunca dentro del UPDATE de updateColumns

# Sigue sin haber DELETE sobre publications
grep -rn "DELETE" app/src/main/java/com/jrblanco/boccantabria/data/source/local/*Dao.kt
# → una sola coincidencia: AlertRuleDao.delete

# Ningún execute() bloqueante queda en los data sources
grep -rn "\.execute()" app/src/main/java/com/jrblanco/boccantabria/data/source/remote/
# → sin resultados

# Ningún .catch sobre un Flow de Room queda en los repositorios
grep -rn "\.catch {" app/src/main/java/com/jrblanco/boccantabria/data/repository/
# → solo dentro de ReadRecovery.kt
```

---

## 6. Cierre

Al terminar, `tasks.md` recibe la sección «Cierre» con la tabla de las cuatro puertas (comando, resultado,
recuento de pruebas), lo que se comprobó a mano según §4 y lo que no pudo comprobarse y por qué, como en
las features 012 y 013.
