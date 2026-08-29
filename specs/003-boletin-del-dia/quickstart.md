# Quickstart: Boletín del día

**Feature**: `003-boletin-del-dia` | **Fase**: 1 | **Fecha**: 2026-08-29

Cómo comprobar, de extremo a extremo, que la feature hace lo que la especificación dice. Cada paso
cita el requisito o el criterio que valida. Lo que no se pueda comprobar aquí, no está terminado.

## Requisitos previos

Java no está en el `PATH`; se usa el JBR que trae Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Hace falta un emulador o un dispositivo con conexión real, porque parte de lo que se valida es el
comportamiento contra el servicio oficial del BOC.

```bash
./gradlew :app:installDebug
```

---

## Paso 1 — Instalación limpia con conexión (US1, FR-001, FR-034, SC-002)

```bash
adb uninstall com.jrblanco.boccantabria || true
./gradlew :app:installDebug
```

Abrir la aplicación con el cronómetro en marcha.

**Se espera**: tras la portada aparece Inicio con esqueletos —no más de cinco, sin ruleta grande— y a
continuación el boletín del día. Desde el toque en el icono hasta ver publicaciones deben pasar
**menos de quince segundos** en un dispositivo de gama media.

**Comprobar en la cabecera**: dice «Boletín de hoy», la fecha en formato largo español, y a la derecha
un distintivo perfilado con el número de publicaciones. **No** debe aparecer ningún «N.º» de boletín
(FR-033).

**Comprobar en una tarjeta**: organismo en mayúsculas y color apagado, título completo en azul,
fecha con icono de calendario, y las acciones de guardar y compartir abajo a la derecha. A la
izquierda, la línea vertical de 4 dp del color de su sección (FR-037).

---

## Paso 2 — El contenido queda guardado (US2, FR-019, SC-001)

Cerrar la aplicación por completo, activar el modo avión y volver a abrirla.

**Se espera**: el mismo contenido, **de inmediato** —menos de un segundo desde que Inicio aparece—,
más un aviso de falta de conexión que no tapa las tarjetas (FR-041).

**No se espera**: pantalla vacía, ruleta indefinida ni mensaje de error.

---

## Paso 3 — Actualizar a mano (FR-024, FR-025, FR-026)

Con conexión, deslizar hacia abajo sobre el listado.

**Se espera**: indicador de progreso discreto y **el contenido sigue visible durante toda la
actualización**. Al terminar sin novedades, la lista queda igual y no aparece ningún error.

Deslizar tres veces seguidas muy rápido.

**Se espera**: una sola sincronización. Ninguna publicación duplicada (FR-025, SC-004).

---

## Paso 4 — La caché no se re-sincroniza sin motivo (FR-023)

Cerrar y reabrir la aplicación antes de que pasen treinta minutos.

**Se espera**: el contenido aparece al instante y **no** se lanza una descarga nueva. Se verifica en
el registro de red del dispositivo o con `adb logcat`, no a ojo.

---

## Paso 5 — El panel de secciones (US3, FR-043 … FR-048, SC-008)

Tocar el icono de menú de la barra superior.

**Se espera**: se abre el panel con las nueve secciones, cada una con su número y su nombre, en el
orden oficial. **Sin campanas y sin tarjeta de alertas** (FR-047).

1. Escribir `oposi` en el campo de filtro → queda visible «Cursos, oposiciones y concursos», con su
   sección padre desplegada para que se vea.
2. Limpiar el filtro y desplegar «2 · Autoridades y personal» → aparecen sus tres subsecciones sobre
   fondo suave, sangradas respecto a la principal.
3. Elegir «Cursos, oposiciones y concursos» → el panel se cierra, la cabecera pasa a nombrar la
   subsección y el listado muestra sus publicaciones **sin limitarse a la fecha de hoy** (FR-035).
4. Tocar el chip «Todo» → vuelve el boletín del día.

Contar los toques del paso 3: deben ser **tres o menos** desde Inicio (SC-008).

---

## Paso 6 — Las anomalías conocidas del servicio (FR-009, SC-007)

Desde el panel, elegir:

- **8.1 Subastas** — la fuente devuelve cero publicaciones.
- **4.3 Actuaciones en materia de Seguridad Social** — sin publicar desde 2021, con las categorías
  desordenadas.
- **9 Elecciones** — sin publicar desde 2024.

**Se espera**: en 8.1, estado vacío con mensaje propio. En 4.3 y 9, sus publicaciones reales con sus
fechas reales.

**No se espera**: ni un mensaje de error, en ninguna de las tres (FR-040).

---

## Paso 7 — Las publicaciones no desaparecen (FR-021, SC-005)

Anotar el título de la publicación más antigua de una sección con mucho movimiento, por ejemplo
2.2 Cursos, oposiciones y concursos. Forzar varias sincronizaciones a lo largo de días, o comprobarlo
directamente sobre la base de datos:

```bash
adb shell "run-as com.jrblanco.boccantabria ls databases/"
```

**Se espera**: el número de publicaciones guardadas **nunca disminuye** entre sincronizaciones.

---

## Paso 8 — Ninguna acción deja sin respuesta (US4, FR-049 … FR-056, SC-009)

| Acción | Se espera |
|---|---|
| Lupa de la barra superior | Aviso breve: «Próximamente» |
| Información de la barra superior | Nada. Está presente y no falla |
| Barra inferior → Buscar | Pantalla con el aspecto de la aplicación y «Próximamente» |
| Barra inferior → Guardados | Ídem |
| Barra inferior → Inicio | Vuelve a Inicio; la barra marca el destino activo |
| Guardar en una tarjeta | Aviso: «Próximamente» |
| Compartir en una tarjeta | Se abre la hoja del sistema con el enlace del documento oficial |
| Pulsar el cuerpo de una tarjeta | **No pasa nada**. El detalle es la feature siguiente (FR-056) |

**Comprobar además**: la barra inferior tiene **tres** destinos. No hay Avisos (FR-049).

---

## Paso 9 — Sin conexión desde la primera vez (FR-027)

```bash
adb uninstall com.jrblanco.boccantabria
./gradlew :app:installDebug
```

Activar el modo avión **antes** de abrir, y abrir.

**Se espera**: la portada ofrece continuar sin conexión; al llegar a Inicio, un mensaje comprensible
con la acción de reintentar. Al desactivar el modo avión y tocar reintentar, aparece el boletín.

**No se espera**: pantalla en blanco sin explicación.

---

## Paso 10 — Accesibilidad y rotación (FR-042, SC-010)

1. Ajustes del sistema → tamaño de letra al **200 %**. Volver a la aplicación.
   **Se espera**: las tarjetas crecen; no se recorta el organismo, el título ni la fecha.
2. Desplazarse hasta media lista y provocar una recreación (cambiar el tamaño de letra, o
   `adb shell am start -a android.intent.action.MAIN`).
   **Se espera**: se conserva la posición de lectura y la sección seleccionada.
3. Girar el dispositivo. **Se espera**: sigue en vertical.

---

## Paso 11 — El retroceso (FR-057)

Desde Inicio, con y sin sección seleccionada, pulsar Atrás.

**Se espera**: la aplicación se cierra. **No** reaparece la portada.

---

## Paso 12 — El documento de diseño está al día (FR-060)

```bash
grep -n "panel lateral\|tres destinos\|recuento de publicaciones" docs/diseno/especificaciones-diseno.md
```

**Se espera**: los apartados 10.1, 10.2, 14.2, 14.3 y 16 reflejan las cuatro desviaciones acordadas.
Si el documento sigue diciendo cuatro destinos y «N.º 165», la feature no está terminada.

---

## Puertas de calidad

En este orden, y las cuatro en verde:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Informes: `app/build/reports/tests/testDebugUnitTest/index.html` y
`app/build/reports/lint-results-debug.html`.

---

## Apéndice — comprobación contra el servicio real (29 de agosto de 2026)

Las pruebas usan muestras conservadas para ser deterministas, así que **por separado** se contrastó
la implementación contra las diecinueve fuentes en vivo, aplicando las mismas reglas que el
normalizador. Resultado:

| | |
|---|---|
| Fuentes que respondieron | 19 de 19, todas con `text/xml;charset=UTF-8` |
| Publicaciones recibidas | 1.709 |
| **Aceptadas** | **1.709** — ninguna rechazada por título, enlace o fecha |
| Sin identificador en el enlace | 0 |
| Clasificación que no corresponde a su fuente | 0 |
| **Con orden anómalo de componentes** | **8**, las ocho en el feed 4.3 |
| Identificadores repetidos entre fuentes | 0 (1.709 únicos) |
| Fuente vacía | 8.1 Subastas, `size` 0 y cero items, respuesta válida |

Las ocho anomalías son exactamente las que documenta el fichero de consumo de feeds. La aplicación
las marca con `CATEGORY_ORDER_UNRELIABLE` y **no descarta ninguna**, que es lo que exige FR-015.

Reproducible con:

```bash
for f in 6802081 6802084 6802085 6802086 6802087 6802089 6802090 6802091 6802092 \
         6802094 6802095 6802097 6802098 6802099 6802100 6802301 7479572 6802303 7293890; do
  curl -s -o "/tmp/boc/$f.xml" -w "$f %{http_code} %{content_type} %{size_download}\n" \
    "https://www.cantabria.es/o/BOC/feed/$f"
done
```

---

## Apéndice — recorrido en dispositivo (29 de agosto de 2026, emulador Pixel 10, API 37)

Instalación limpia contra el servicio real. La base de datos se extrajo con los tres ficheros
—`boc.db`, `-wal` y `-shm`—: copiar solo el primero deja fuera lo que aún está en el registro de
escritura anticipada y da un recuento corto. Es un error de medición, no de la aplicación, y
conviene no repetirlo.

| Medida | Resultado |
|---|---|
| Arranque en frío, instalación limpia | **747 ms** *(SC-002: < 15 s hasta el boletín)* |
| Arranque con contenido guardado | **682 ms** *(SC-001: < 1 s)* |
| Fuentes sincronizadas | **19 de 19**, cero fallos |
| Publicaciones guardadas | **1.709** — las mismas que acepta la comprobación independiente |
| Fecha más reciente | 2026-08-28, con **33 anuncios** en el boletín del día |
| Secciones con contenido | 9 de 9 · subsecciones 13 de 14 *(8.1 está vacía en origen)* |
| Publicaciones con aviso de orden anómalo | **8**, todas del feed 4.3 |
| `blob_id` repetidos | 0 |
| Enlaces que no son HTTPS | 0 |
| Publicaciones sin organismo | 0 |

**Tres defectos que solo aparecieron al ejecutar** y que ninguna prueba había podido ver: el
organismo salía dos veces en la tarjeta, había un hueco del alto de la barra de estado sobre el
escudo, y el panel de secciones se veía con un tinte lila. Los tres están corregidos y los dos
últimos tienen prueba que los impide volver.

---

## Apéndice — una intermitencia encontrada y atajada

`SplashBackStackTest` falló de forma intermitente durante la verificación con
`IllegalStateException: Method setCurrentState must be called on the main thread`. Un test
intermitente incumple el principio V, así que se buscó la causa en lugar de anotarla.

**Causa**: la portada navega desde un `LaunchedEffect`. Navegar mueve el ciclo de vida de las
entradas de la pila de retroceso, y eso solo es legal en el hilo principal. En un dispositivo
siempre lo es —los efectos de composición corren en el despachador de interfaz—, pero bajo el
entorno de pruebas de Compose la misma continuación puede reanudarse en el hilo que bombea los
fotogramas.

**Arreglo**: la navegación desde la portada queda fijada al hilo principal en
`BOCantabriaNavHost`. En producción no cambia nada —`Dispatchers.Main.immediate` se ejecuta en
línea cuando ya se está en el principal— y elimina la carrera.

**Comprobación**: cinco tandas consecutivas de las tres pruebas que atraviesan el arranque, las
cinco en verde con 3 de 3.

---

## Resumen de aceptación

| # | Qué demuestra | Requisitos |
|---|---|---|
| 1 | El BOC real llega a la pantalla | FR-001, FR-034, FR-037, SC-002 |
| 2 | Lo descargado queda guardado y sirve sin conexión | FR-019, FR-041, SC-001, SC-003 |
| 3 | La actualización manual no rompe la lectura | FR-024 … FR-026, SC-004 |
| 4 | No se castiga al servicio oficial sin motivo | FR-023 |
| 5 | El BOC es navegable por secciones | FR-043 … FR-048, SC-008 |
| 6 | Las anomalías del servicio no son errores | FR-009, FR-040, SC-007 |
| 7 | El histórico propio no se pierde | FR-021, SC-005 |
| 8 | Ninguna acción deja a la persona sin respuesta | FR-049 … FR-056, SC-009 |
| 9 | La primera vez sin conexión tiene salida | FR-027 |
| 10 | Accesible y estable ante recreaciones | FR-042, SC-010 |
| 11 | El retroceso sigue comportándose igual | FR-057 |
| 12 | Documento y aplicación no se contradicen | FR-060 |
