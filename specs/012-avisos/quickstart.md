# Quickstart: Avisos

**Feature**: `012-avisos` | **Fecha**: 6 de septiembre de 2026

Cómo comprobar que esta feature hace lo que dice. Las cuatro puertas de la constitución, y después
—**y esto no es opcional**— lo que ninguna prueba automática de esta casa puede ver: la frontera con
Android (notificaciones, permiso, WorkManager, el toque que abre el detalle).

---

## 1. Preparar

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

No hace falta credencial de IA para nada de esta feature. Sí hace falta un emulador o un móvil con
**navegación de tres botones** para la prueba de márgenes y **API 33 o superior** para ver el permiso de
notificaciones en tiempo de ejecución.

---

## 2. Las cuatro puertas

En este orden:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb shell settings put secure navigation_mode 0
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

La tanda instrumentada tarda **del orden de dos a tres horas** (154 pruebas en 116–161 minutos en las
features 009 y 010; esta añade unas quince). Lánzala en segundo plano.

Para una sola clase (`--tests` **no existe** en `connectedDebugAndroidTest`):

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jrblanco.boccantabria.ui.alerts.AlertsScreenTest
```

Informes: `app/build/reports/tests/testDebugUnitTest/index.html` y
`app/build/reports/lint-results-debug.html`.

**Con un móvil enchufado además del emulador**, la tanda se reparte entre los dos y falla en bloque si
el móvil tiene la pantalla bloqueada. `ANDROID_SERIAL` fija el destino.

---

## 3. Lo que hay que recorrer a mano

Cada apartado dice qué mirar en pantalla y qué debe aparecer en el registro:

```bash
adb logcat -s BOC:V
```

### 3.1 Crear el primer aviso y el permiso en contexto

1. Instalación limpia en API 33+. Abrir la aplicación: **no** debe pedir el permiso de notificaciones.
2. Campana → «Crear mi primer aviso» → escribir `ganadería`, Intro → el chip aparece; el nombre se
   rellena con «Ganadería»; «Así funcionará» dice «Te avisaremos cuando una publicación nueva incluya
   «ganadería»» → «Guardar aviso».
3. Debe aparecer «Activa las notificaciones … [Ahora no] [Continuar]». Pulsar «Continuar» → diálogo de
   Android → Permitir.
4. De vuelta en Avisos: pestaña Mis avisos con la tarjeta «Ganadería · Activo · ganadería · Todas las
   secciones»; cabecera «1 activo»; sin banner.

Registro esperado: nada de la regla (ni «ganadería» ni «Ganadería» aparecen).

### 3.2 La línea base no avisa

En la instalación limpia del 3.1, Inicio ya sincronizó al arrancar (línea base). Comprobar en el
registro `cycle: baseline, N stored, alerts not evaluated` y que **no** hay notificación ni novedad
aunque el archivo tenga publicaciones con «ganadería».

### 3.3 Una publicación nueva con la aplicación cerrada

Necesita que aparezca una publicación nueva que coincida. Dos caminos:

- **Esperar** al siguiente boletín (días laborables por la mañana) con un aviso amplio (`Cantabria`).
- **Forzar** con el Worker: cerrar la aplicación (deslizar de recientes) y ejecutar

  ```bash
  adb shell dumpsys jobscheduler | grep -A2 boccantabria      # localizar el JOB ID del trabajo
  adb shell cmd jobscheduler run -f com.jrblanco.boccantabria <JOB_ID>
  ```

  Como la base ya tiene todo lo del feed, para ver una «nueva» hay que **vaciar una fuente** antes:
  con la aplicación cerrada, `adb shell run-as com.jrblanco.boccantabria sqlite3
  databases/boc.db "DELETE FROM publications WHERE feed_id='6802095'"` (solo en el emulador, solo para
  esta prueba: borra la sección 6 del almacén local, y la siguiente sincronización la trae entera como
  nueva). Con un aviso de sección 6 activo, el Worker debe producir **una** notificación por
  publicación y **un** resumen.

Comprobar: título «Nueva publicación: <aviso>» con el título del anuncio; con ≥ 2, el grupo se
expande con el resumen «N publicaciones nuevas coinciden con tus avisos»; **un** solo sonido.
Registro: `cycle: N new, 1 rule(s), M match(es) on M publication(s), delivery=SYSTEM`,
`alerts: posted M notification(s) + summary`.

### 3.4 El toque abre el detalle y marca leída

Tocar una notificación individual: pasa por la portada, aterriza en el **detalle de esa publicación**,
la notificación desaparece. Volver con Atrás hasta el shell: la campana muestra M−1. Tocar el resumen:
aterriza en Avisos › Novedades.

### 3.5 Con la aplicación abierta, Snackbar y no notificación

Repetir el 3.3 con la aplicación abierta en **Buscar**. Debe aparecer abajo «Una nueva publicación
coincide con «<aviso>»  VER» (o «N nuevas publicaciones…») y **ninguna** notificación en el panel.
VER → Novedades. Registro: `delivery=IN_APP`. Repetir con la aplicación abierta en **Avisos**: sin
Snackbar; la lista y el contador se actualizan solos.

### 3.6 Novedades: leído, no leído, marcar todas

Punto azul y fondo azulado en las no leídas; separadores «Hoy»/«Ayer»/fecha; tocar una la abre y la
deja leída; «Marcar todas como leídas» vacía el contador. Entrar y salir de la pestaña **no** marca nada.

### 3.7 Pausar, editar, duplicar, eliminar

- Interruptor de la tarjeta → «Aviso pausado», «0 activos»; con cero activos el trabajo periódico se
  cancela (`adb shell dumpsys jobscheduler | grep boccantabria` vacío).
- Menú ⋮ → Editar → añadir sección 6 → «Guardar cambios». No aparece ninguna novedad de lo ya almacenado.
- ⋮ → Duplicar → formulario con «Copia de Ganadería», interruptor apagado.
- ⋮ → Eliminar → «¿Eliminar este aviso? Dejarás de recibir novedades que coincidan con «Ganadería»» →
  Eliminar → la tarjeta y sus novedades desaparecen; Inicio sigue mostrando las mismas publicaciones.

### 3.8 Permiso denegado

Ajustes de Android → BOC Cantabria → Notificaciones → apagar. Volver a Avisos: banner «Tus avisos están
configurados, pero Android no permite mostrar notificaciones» con «Abrir ajustes» (que abre esa misma
pantalla). Forzar el Worker: **sin** notificación, pero la novedad aparece y el contador sube.
Registro: `alerts: notifications disabled, M match(es) kept`.

### 3.9 El selector de secciones

Marcar «2 · Autoridades y personal» → 2.1, 2.2 y 2.3 marcadas, contador «3 seleccionadas»; desmarcar
2.3 → la 2 en estado parcial; Aplicar → el formulario dice «Nombramientos…, Cursos…»; volver a marcar
2.3 → «Autoridades y personal (todas)».

### 3.10 Vista previa

Con `Cosío` como palabra: «N publicaciones actuales coinciden con esta configuración» → «Ver
resultados» → lista → abrir una → detalle. Volver: el contador de la campana **no** ha cambiado.

### 3.11 Sin conexión

Modo avión, pull-to-refresh en Inicio: «Sin conexión», y en el registro `cycle: refresh failed:
Network`. Ninguna novedad, ninguna notificación.

---

## 4. Lo que se comprueba a mano porque el entorno no da para más

- **El intervalo real del trabajo periódico.** WorkManager no promete la hora; se observa con
  `dumpsys jobscheduler` que la petición existe y su siguiente ventana, no que dispare a las cuatro
  horas exactas.
- **La primera sincronización correcta tras instalar sin red.** Instalar en modo avión, abrir (Inicio en
  error), cerrar, quitar el modo avión, abrir: el registro debe decir `baseline` y no debe haber avisos.
- **El icono pequeño** en la barra de estado: campana blanca reconocible, no una mancha.
- **La portada bloqueada con un toque pendiente** (FR-049). Con Remote Config publicando una versión
  mínima superior a la instalada, tocar una notificación debe dejar la aplicación en la portada con el
  bloqueo, **sin** navegar al detalle. Se comprueba con la configuración remota, no con una prueba.

---

## 5. Resultados

**6 de septiembre de 2026**, sesión que implementó la feature.

| Puerta | Resultado |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ |
| `./gradlew :app:testDebugUnitTest` | ✅ ~1.200 pruebas, 0 fallos (incluidas las ~40 clases nuevas y `ArchitectureRulesTest` con los tres modelos de pantalla y los doce tipos de dominio nuevos) |
| `./gradlew :app:lintDebug` | ✅ 0 errores tras la guardia explícita de `POST_NOTIFICATIONS` en `AndroidAlertNotifier` (lint no infiere `areNotificationsEnabled()`) |
| `connectedDebugAndroidTest`, solo las 7 clases de la 012 | ✅ 34 pruebas en verde en `emulator-5554` (Pixel_10, API 37, `navigation_mode 0`), tras tres correcciones |
| `connectedDebugAndroidTest`, tanda completa | ✅ **210/210 en 100 s** (13:24, tras la corrección del proceso aislado y con `AlertSyncWorkerKoinTest`); antes, 204/204 sin las dos clases del PDF |

**El defecto más grave de la feature lo destapó esta puerta, y era nuestro.** La primera pasada
completa se quedó **colgada dos horas** en `PdfViewerSmokeTest.the_viewer_renders_the_document` y las
tres pruebas de `AndroidxPdfPageCounterTest` agotaron su minuto. Parecía el emulador; el logcat por
prueba dijo otra cosa: `Unable to create application com.jrblanco.boccantabria.BOCantabriaApp …
NullPointerException: null cannot be cast to non-null type android.net.ConnectivityManager` en el proceso
`com.jrblanco.boccantabria:androidx.pdf.service.PdfDocumentServiceImpl`. El visor renderiza en un
proceso **aislado**, sin servicios del sistema, y `workManagerFactory()` en `Application.onCreate`
inicializaba WorkManager también ahí. El inicializador por defecto nunca lo sufrió porque un
`ContentProvider` no corre en procesos aislados. En un móvil, el visor y el contador de páginas del
Resumen IA habrían muerto en silencio. Arreglo: `if (!Process.isIsolated()) workManagerFactory()`
(research.md D-420, CLAUDE.md). **Ninguna prueba unitaria podía verlo**: Robolectric no tiene procesos
aislados.

### Lo que destaparon las instrumentadas, y no las unitarias

- **El Snackbar «VER» desaparecía en el mismo instante en que aparecía.** `MainShell` consumía el aviso
  pendiente y luego mostraba el Snackbar **dentro del mismo `LaunchedEffect(pendingAlert)`**: consumir
  pone la clave a `null`, el efecto se reinicia y cancela el `showSnackbar` suspendido. Ninguna prueba
  unitaria lo veía —el modelo de pantalla hace lo correcto— y `AlertSnackbarTest` lo cazó al primer
  intento. Ahora el Snackbar corre en el `rememberCoroutineScope()` del shell.
- **Los conectores del resumen «Así funcionará» salían sin espacios** («ganadería»o«subvención»): Android
  recorta los blancos de una cadena de recursos salvo que vaya entrecomillada. `" o "`, `" y "`, `", "`.
- **Dos trampas de la propia prueba**, no del producto: un nodo con `testTag` dentro de una fila
  `clickable` solo existe en el árbol **sin fusionar** (`useUnmergedTree = true`), y un nodo por debajo
  del pliegue de un `verticalScroll` hay que traerlo con `performScrollTo()` antes de afirmar que se ve.

### El recorrido a mano en el emulador (6 de septiembre de 2026, Pixel_10 API 37, conducido con `uiautomator`)

| Paso | Visto |
|---|---|
| §3.1 arranque limpio | **No** se pide el permiso al arrancar |
| §3.2 línea base | `cycle: baseline (1709 inserted), alerts not evaluated`; cero notificaciones, cero novedades |
| §3.1 crear el primer aviso | Campana → «Crear mi primer aviso» → `ganaderia` + «+» → chip y nombre propuesto «Ganaderia» → «Guardar aviso» → diálogo «Activa las notificaciones» → «Continuar» → diálogo de Android → concedido → tarjeta «Ganaderia · Palabra clave · ganaderia · Todas las secciones», cabecera «1 activo» |
| §3.7 trabajo periódico | `dumpsys jobscheduler` muestra `#AlertSyncWorker#@androidx.work.systemjobscheduler` con la primera regla activa; **desaparece** al pausarla («Aviso pausado», «0 activos») y **vuelve** al reactivarla |
| §3.7 duplicar / eliminar | Formulario «Copia de Ganaderia» pausado; diálogo «¿Eliminar este aviso? Dejarás de recibir novedades que coincidan con «Ganaderia».» → «Aún no tienes avisos», sin trabajo, `boc.db` intacta |
| §3.8 permiso revocado | Banner «Tus avisos están configurados, pero Android no permite mostrar notificaciones» y hoja de ajustes con «Última comprobación: 6 de septiembre de 2026, 13:15». **Destapó un defecto**: en Android 13+ apagar las notificaciones revoca el permiso y el estado es `NEEDS_REQUEST`, no `DISABLED`; el banner solo miraba `DISABLED` y no salía. Ahora sale con cualquier estado distinto de concedido (D-427) |
| §3.3 forzar el Worker | `cmd jobscheduler run -f -n androidx.work.systemjobscheduler <pkg> <id>` llega, pero WorkManager **retrasa** un periódico que llega antes de su hora: `WorkerWrapper: Delaying execution for …AlertSyncWorker because it is being executed before schedule`. Sin `root` no se puede adelantar el reloj. Se cubre con `AlertSyncWorkerKoinTest` (instrumentada): construye el Worker con `KoinWorkerFactory` en el proceso real y ejecuta el ciclo hasta `success` |
| Notificación con publicación nueva de verdad | **No comprobada**: exige un boletín nuevo (próximo día laborable) o un dispositivo con control del reloj. El canal, el grupo, el resumen y el `PendingIntent` están cubiertos por `AndroidAlertNotifierTest` con `ShadowNotificationManager`, y el deep link por `AlertDeepLinkTest` en el emulador |

### Lo que queda por hacer a mano

- §3.3 y §3.5 con una publicación nueva de verdad: la notificación y el Snackbar con la aplicación en
  pantalla. La vía más limpia es esperar al siguiente boletín con un aviso amplio (`Cantabria`) y la
  aplicación cerrada; no hay `sqlite3` en la imagen del emulador para vaciar una fuente.
- §4: el intervalo real del trabajo, la línea base tras instalar sin red y el icono pequeño en la barra
  de estado.
