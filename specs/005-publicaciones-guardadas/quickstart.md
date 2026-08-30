# Quickstart: Publicaciones guardadas

**Feature**: `005-publicaciones-guardadas` | **Fase**: 1 | **Fecha**: 2026-08-30

Cómo comprobar que la feature funciona de extremo a extremo. Los pasos van en el orden en que se
recorren a mano; entre paréntesis, lo que cada uno demuestra.

---

## Requisitos previos

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

- Un emulador o dispositivo con **API 28 o superior**.
- Conexión a internet para la primera sincronización. A partir del paso 6 se puede cortar.
- Para la tanda instrumentada, **navegación de tres botones**:
  `adb shell settings put secure navigation_mode 0`.
- Para el paso 10 hace falta el APK de `main` **además** del de la rama.

```bash
./gradlew :app:installDebug
```

---

## Paso 1 — Guardar desde el boletín (US1, FR-001, FR-003, SC-001)

1. Abrir la aplicación y esperar a que el boletín cargue.
2. Tocar el marcador de la primera tarjeta.

**Esperado**: el marcador pasa de contorneado a **relleno** en el mismo toque. La publicación **no** se
abre: guardar no navega (FR-007).

---

## Paso 2 — La lista de guardados (US1, FR-010, FR-012, FR-016)

1. Tocar «Guardados» en la barra inferior.

**Esperado**: una barra superior azul con el título «Guardados»; debajo, esa publicación y **solo** esa,
con la misma tarjeta que en el boletín —organismo, título, sección, fecha, guardar y compartir— y su
marcador relleno. Ni «Próximamente», ni pantalla en blanco.

---

## Paso 3 — El orden es el del guardado (FR-011, US1)

1. Volver a Inicio y guardar dos publicaciones más, una a una y en orden.
2. Volver a Guardados.

**Esperado**: las tres, con **la última que se guardó arriba**. No el orden del boletín.

---

## Paso 4 — Abrir, y volver donde estabas (FR-013, FR-018)

1. Desplazar la lista hasta el final si hay suficientes elementos.
2. Tocar una tarjeta.
3. Retroceder.

**Esperado**: se abre el detalle de esa publicación, con el marcador **relleno** en la barra superior.
Al retroceder, la lista aparece en la misma posición de desplazamiento en la que estaba.

---

## Paso 5 — El estado es el mismo en todas partes (US2, FR-005, SC-003)

1. Desde el detalle de una publicación guardada, tocar el marcador para quitarla.
2. Retroceder hasta el boletín y buscar esa misma publicación.
3. Entrar en Guardados.

**Esperado**: en el boletín su tarjeta muestra el marcador **contorneado**, sin haber recargado nada; en
Guardados ya no está. Y a la inversa: guardar desde el detalle deja la tarjeta del boletín marcada al
volver.

---

## Paso 6 — Desmarcar desde la propia lista (FR-002, FR-015)

1. En Guardados, tocar el marcador relleno de una tarjeta.

**Esperado**: la tarjeta desaparece de la lista en el acto. No hay «Deshacer» y no se pide confirmación:
es la decisión tomada. Volver a guardarla es un toque en el boletín.

---

## Paso 7 — Compartir desde una tarjeta guardada (FR-014)

1. En Guardados, tocar el icono de compartir de una tarjeta.

**Esperado**: exactamente lo mismo que en Inicio. Si el documento no está descargado, avisa de que se
está preparando y luego ofrece el documento; sin conexión, ofrece el enlace y lo explica.

---

## Paso 8 — Sin conexión, guardar sigue funcionando (FR-006)

1. Activar el modo avión.
2. Guardar y desmarcar un par de publicaciones, en Inicio y en Guardados.

**Esperado**: funciona con normalidad. Guardar no habla con la red.

**Nota de alcance (FR-024)**: abrir el documento de una publicación guardada **sí** puede necesitar red,
porque guardar marca y no descarga. Es la promesa aplazada, y está en la especificación.

---

## Paso 9 — Nada se pierde (US3, FR-019, FR-020, FR-022, SC-004)

1. Con dos o tres publicaciones guardadas, tirar de la lista de Inicio para forzar una sincronización.
2. Comprobar Guardados.
3. Matar el proceso: `adb shell am kill com.jrblanco.boccantabria`.
4. Volver a abrir la aplicación y entrar en Guardados.

**Esperado**: las mismas publicaciones, en el mismo orden, después de la sincronización y después de la
muerte del proceso.

---

## Paso 10 — La actualización sobre una instalación real (FR-023, SC-006)

Este es el paso que ninguna prueba automática cubre del todo, y el que peores consecuencias tiene si
falla.

```bash
git switch main && ./gradlew :app:installDebug     # la versión anterior, con la base de datos v1
# abrir la aplicación, dejar que sincronice, cerrarla
git switch 005-publicaciones-guardadas && ./gradlew :app:installDebug   # SIN desinstalar
```

**Esperado**: la aplicación arranca con normalidad y **el boletín almacenado sigue ahí**. Un
`IllegalStateException` de migración solo aparece en un dispositivo que ya tenía `boc.db`: en una
instalación limpia este fallo es invisible.

---

## Paso 11 — La lista vacía (US4, FR-017)

1. Desmarcar todo, o desinstalar e instalar de nuevo.
2. Entrar en Guardados.

**Esperado**: marcador grande, «Aún no has guardado publicaciones», un texto que explica cómo se
guarda, y un botón «Explorar el BOC» que lleva al boletín.

---

## Paso 12 — Doscientas guardadas (SC-007)

1. Guardar doscientas publicaciones por el camino más corto que exista: una tanda desde una prueba
   instrumentada, o escribiendo la columna directamente con `adb shell`.
2. Abrir Guardados y recorrer la lista de arriba abajo.

**Esperado**: la pantalla se abre sin espera perceptible y el desplazamiento no da saltos. Si los da,
el sospechoso es el índice de `saved_at`, no la lista.

---

## Paso 13 — Accesibilidad (FR-004, SC-008)

1. Activar TalkBack.
2. Recorrer una tarjeta no guardada y otra guardada.

**Esperado**: la acción se anuncia como «Guardar» en la primera y como «Quitar de guardados» en la
segunda. El estado no se transmite **solo** por el relleno del icono.

3. Subir el tamaño de letra del sistema al 200 % y volver a Guardados.

**Esperado**: las tarjetas siguen legibles y los dos iconos de acción siguen pulsables.

---

## Puertas de calidad

En este orden, y las cuatro en verde antes de dar la feature por terminada:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Informes: `app/build/reports/tests/testDebugUnitTest/index.html` y
`app/build/reports/lint-results-debug.html`.

Las pruebas que hay que ver pasar de forma explícita, porque son las que sostienen los requisitos
difíciles:

| Prueba | Qué sostiene |
|---|---|
| `SavedPublicationDaoTest` — una sincronización no borra la marca | FR-020, SC-004 |
| `SavedPublicationDaoTest` — desmarcar no borra la publicación | FR-021, SC-005 |
| `BocDatabaseMigrationTest` | FR-023, SC-006 |
| `SavedFlowIntegrationTest` | US1, US2, US3 de extremo a extremo |
| `PublicationCardTest` — relleno frente a contorneado | FR-003, SC-003 |
| `BottomBarNavigationTest` — Guardados ya no dice «Próximamente» | FR-010, SC-008 |

---

## Resumen de aceptación

La feature está terminada cuando los trece pasos se cumplen, las cuatro puertas están en verde, el
apartado 22 del documento de diseño lleva su enmienda con lo aplazado, y `CLAUDE.md` dice que Guardados
**todavía no** conserva el documento para leer sin conexión.
