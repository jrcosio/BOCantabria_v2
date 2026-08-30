# Quickstart: Detalle de publicación y visor del PDF oficial

**Feature**: `004-detalle-publicacion` | **Fase**: 1 | **Fecha**: 2026-08-30

Cómo comprobar, de extremo a extremo, que la feature hace lo que la especificación dice. Cada paso
cita el requisito que valida.

## Requisitos previos

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug
```

Hace falta un dispositivo o emulador **con API 28 o superior** —la versión mínima subió en esta
feature— y con conexión real, porque parte de lo que se valida es la descarga desde el servicio del
BOC.

---

## Paso 1 — Entrar en la publicación (US1, FR-001, FR-005, FR-007, FR-008)

Abrir la aplicación, esperar al boletín y **tocar una tarjeta**.

**Se espera**: se llega al detalle en un solo toque. La cabecera muestra, en este orden: etiqueta de
sección, título, organismo con su icono, fecha con el suyo y el distintivo `Documento oficial`. La
barra superior es azul, con Atrás, escudo, `Detalle de publicación`, guardar y compartir.

**Comprobar además**: **no hay barra de navegación inferior** (FR-006), y el título de la publicación
se lee **entero**, sin puntos suspensivos (FR-008). Elegir a propósito una de las publicaciones con
título largo.

Pulsar Atrás. **Se espera**: se vuelve al boletín **en la misma posición** y con la misma sección
seleccionada, no al principio de la lista.

---

## Paso 2 — La pestaña Documento (FR-012, FR-013)

Entrar en una publicación y quedarse en la primera pestaña.

**Se espera**: una ficha con descripción, organismo, sección, fecha de publicación, referencia y
documento oficial. Debajo, la **primera página del PDF**, que tarda unos segundos la primera vez y
aparece de inmediato a partir de entonces.

**Comprobar**: al **abrir el detalle** de una publicación nueva y **no** entrar en la pestaña
Documento, no debe haber descarga. Se descarga al mostrarla (D-011 de `research.md`).

---

## Paso 3 — Leer el documento dentro de la aplicación (US1, FR-026, FR-027, FR-028)

Tocar `Abrir PDF oficial`.

**Se espera**: el documento se abre **dentro de la aplicación**, en su propia pantalla, con Atrás,
el título abreviado y compartir. Se puede ampliar con dos dedos y recorrer con el desplazamiento.

**No se espera**: que se abra el navegador ni ninguna otra aplicación.

---

## Paso 4 — La segunda vez es inmediata (FR-021, SC-002)

Volver atrás y abrir el mismo documento otra vez, cronómetro en mano.

**Se espera**: aparece en **menos de un segundo**. Repetirlo en modo avión: sigue abriéndose.

---

## Paso 5 — Que fallar se note (US2, FR-017, FR-019, FR-025, SC-004, SC-005)

Con modo avión y una publicación **nunca abierta**, entrar en la pestaña Documento.

**Se espera**: un mensaje comprensible con la acción de reintentar. Al desactivar el modo avión y
reintentar, el documento aparece.

**Comprobar que no queda basura**:

```bash
adb shell "run-as com.jrblanco.boccantabria ls -l cache/documents/" 2>/dev/null
```

**Se espera**: ningún fichero con extensión `.part`, y ningún `.pdf` de tamaño cero.

---

## Paso 6 — Un documento que no es un documento (FR-016, FR-017, SC-004)

Es la comprobación que da sentido a la historia 2 y no puede hacerse contra el servicio real, así
que se hace con la suite: `OkHttpDocumentDownloaderTest` cubre respuesta con código 200 y cuerpo
HTML, tipo declarado inesperado, primeros bytes que no son `%PDF`, cuerpo por encima del tope,
enlace que no usa `https` y host que no es el del boletín.

**Se espera**: los seis casos rechazados, y ninguno deja fichero.

```bash
./gradlew :app:testDebugUnitTest --tests "*OkHttpDocumentDownloaderTest*"
```

---

## Paso 7 — Compartir envía el documento (US3, FR-031, FR-032, FR-033, FR-034)

| Situación | Se espera |
|---|---|
| Documento ya abierto antes | Se ofrece **el PDF** de inmediato a las aplicaciones del sistema |
| Documento nunca abierto, con conexión | Se indica que se está preparando y después se ofrece el PDF |
| Documento nunca abierto, en modo avión | Se ofrece **el enlace**, explicando por qué |

**Comprobar en el primer caso**: elegir una aplicación que reciba ficheros —correo, mensajería— y
verificar que el adjunto **se abre** y es el anuncio (FR-034).

---

## Paso 8 — Las funciones aplazadas (US4, FR-014, FR-035, FR-036, FR-038)

| Acción | Se espera |
|---|---|
| Pestaña `Resumen IA` | Dice que llegará próximamente, con el icono y la etiqueta de IA |
| Pestaña `Preguntar` | Ídem |
| Botón `Preguntar` de la barra inferior | Ídem |
| Guardar de la barra superior | Avisa de que llegará próximamente |

**Comprobar**: `Abrir PDF oficial` sigue siendo la acción **más destacada** de la pantalla (FR-037),
y ninguna de las cuatro deja sin respuesta.

---

## Paso 9 — Cambios de configuración (FR-015, FR-029)

1. En el detalle, cambiar a la pestaña `Resumen IA` y modificar el tamaño de letra del sistema.
   **Se espera**: sigue en `Resumen IA`, no vuelve a la primera.
2. En el visor, desplazarse hasta la página 3, ampliar, y provocar una recreación.
   **Se espera**: sigue en la página 3. **No** se vuelve a descargar el documento.

---

## Paso 10 — Accesibilidad (FR-043, SC-008)

Ajustes del sistema → tamaño de letra al **200 %**.

**Se espera**: el título del detalle se lee completo y no se recorta; los dos botones de la barra de
acciones **se apilan** si no caben en una fila; ningún control queda por debajo de 48 × 48 dp.

---

## Paso 11 — La publicación que ya no está (FR-004)

Difícil de provocar a mano; lo cubre `PublicationDetailViewModelTest`. Si se quiere ver en el
dispositivo, borrar los datos de la aplicación con el detalle abierto.

**Se espera**: se explica que la publicación ya no está disponible y se ofrece volver. **No** una
pantalla en blanco.

---

## Paso 12 — La enmienda quedó registrada y sin restos (FR-039, FR-040, SC-012)

```bash
grep -n "minSdk" .specify/memory/constitution.md CLAUDE.md app/build.gradle.kts
grep -rn "desugar\|CoreLibraryDesugaring" app/build.gradle.kts gradle/libs.versions.toml
```

**Se espera**: `minSdk 28` en los tres primeros, la constitución en versión **1.1.0** con su Sync
Impact Report, y **ninguna** referencia al azucarado. Si quedara alguna, tiene que llevar escrito
para qué sirve ahora.

---

## Puertas de calidad

En este orden, y las cuatro en verde:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

---

## Resumen de aceptación

| # | Qué demuestra | Requisitos |
|---|---|---|
| 1 | Se entra en la publicación y se vuelve sin perder el sitio | FR-001, FR-005 … FR-008 |
| 2 | La pestaña Documento da algo que leer sin salir | FR-012, FR-013 |
| 3 | El documento oficial se lee dentro de la aplicación | FR-026 … FR-028 |
| 4 | Lo ya consultado se abre al instante y sin red | FR-021, SC-002 |
| 5 | Un fallo se explica y no deja restos | FR-019, FR-025, SC-005 |
| 6 | Lo que no es el documento oficial no se presenta como tal | FR-016, FR-017, SC-004 |
| 7 | Compartir entrega el documento, y degrada con explicación | FR-031 … FR-034 |
| 8 | Ninguna acción deja sin respuesta | FR-014, FR-035 … FR-038 |
| 9 | Nada se pierde al cambiar la configuración | FR-015, FR-029 |
| 10 | Accesible con la letra al 200 % | FR-043, SC-008 |
| 11 | Una publicación retirada se explica | FR-004 |
| 12 | La enmienda quedó registrada y sin restos | FR-039, FR-040, SC-012 |
