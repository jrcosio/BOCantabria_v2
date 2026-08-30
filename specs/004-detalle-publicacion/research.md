# Research: Detalle de publicación y visor del PDF oficial

**Feature**: `004-detalle-publicacion` | **Fase**: 0 | **Fecha**: 2026-08-30

Dos cosas que resolver y que se condicionan entre sí: **con qué se lee un PDF dentro de una
aplicación Compose**, y **cómo se consigue el fichero sin que la aplicación acabe presentando como
oficial algo que no lo es**. Todo lo demás sale de ahí.

El apartado 13 del fichero `Datos_modelo/BOC_Cantabria_Consumo_Feeds_RSS.md` fija las reglas de
descarga y no se repiten aquí: se citan.

---

## D-001: El visor oficial de Jetpack, al precio de subir `minSdk` a 28

**Decisión**: `androidx.pdf:pdf-compose` 1.0.0-beta01. Exige `minSdk 28`, así que la constitución se
enmendó a 1.1.0 **antes** de escribir este plan.

**Rationale**: la constitución prohíbe Fragments y XML de layouts. Eso descarta de entrada
`androidx.pdf:pdf-viewer-fragment`, que es la forma en que la mayoría integra el visor de Jetpack.
`pdf-compose` es el mismo motor expuesto como componible:

```text
PdfViewer(document: PdfDocument, state: PdfViewerState, modifier, …)
rememberPdfViewerState(): PdfViewerState
    firstVisiblePage · zoom · scrollToPage(page) · currentSelection · gestureState
```

Trae de serie lo que en un visor propio son semanas: zoom por gestos, desplazamiento rápido,
selección de texto, enlaces internos y reciclado de páginas con control de memoria. Y es la
biblioteca oficial: si el formato o el renderizador cambian, lo arregla quien mantiene la
plataforma.

**Alternativas descartadas**:
- **`android.graphics.pdf.PdfRenderer` de la plataforma**, con el visor escrito a mano. Cero
  dependencias y `minSdk` intacto. Se descarta porque el zoom, el desplazamiento, el reciclado de
  páginas y el control de memoria los escribiríamos nosotros, y saldrían peores justo en la parte
  más delicada de la aplicación: leer el documento es el acto para el que existe.
- **`io.github.grizzi91:bouquet` 2.0.0** (`minSdk 23`, compatible). Menos código que escribir, pero
  es un visor de un solo mantenedor en el camino crítico de leer un documento oficial. La
  dependencia oficial de Jetpack tiene un compromiso de mantenimiento que ésta no puede dar.
- **Delegar en otra aplicación con un `Intent`**. Es lo que la feature viene a evitar: el propietario
  pidió expresamente que el PDF se lea dentro.

**Coste aceptado y consecuencia**: quedan fuera Android 7 y 8, en torno al 2 % de los dispositivos
en 2026. Y `java.time` pasa a ser nativo, así que el azucarado de la biblioteca estándar se retira
(D-013).

---

## D-002: Un documento no es oficial hasta que se comprueba que lo es

**Decisión**: antes de guardar nada se valida, en este orden: esquema `https`, host
`boc.cantabria.es`, `Content-Type` `application/pdf`, y **los primeros bytes del cuerpo son `%PDF`**.
Si cualquiera falla, se rechaza y no se escribe nada.

**Rationale**: el apartado 13.2 del documento de feeds lo pide, y el 27.8 nombra el caso concreto:
«PDF devuelve HTML». Un servicio público sin compromiso de disponibilidad puede responder un día con
una página de error y código 200. Fiarse de la cabecera no basta —una cabecera es lo más fácil de
equivocar— así que se comprueba también el contenido. Es la diferencia entre mostrar el boletín y
mostrar lo que el servidor tuviera a mano.

**Consecuencias**: la descarga va en *streaming* con corte por tamaño, se calcula SHA-256 de lo
recibido, y se escribe primero a un fichero temporal que solo se renombra al destino final cuando
todas las comprobaciones pasan. Una descarga interrumpida no deja nada (FR-019).

---

## D-003: Es caché, no biblioteca

**Decisión**: `cacheDir/documents/<externalKey>.pdf`, con retirada por antigüedad de uso y tope de
tamaño.

**Rationale**: `cacheDir` es el directorio que el sistema puede vaciar cuando falta espacio, y eso
es exactamente lo que queremos que ocurra: un documento que desaparece se vuelve a descargar y no
se pierde nada. Guardar para leer sin conexión es la funcionalidad de **Guardados**, que es futura y
tiene su propia decisión que tomar —qué se guarda, cuánto, quién lo borra—. Meterla aquí a medias
sería adelantar esa decisión sin tener la información.

**Alternativas descartadas**:
- `filesDir`: sobrevive a la limpieza del sistema, que es justo lo que **no** queremos todavía.
- No cachear y descargar en cada apertura: incumpliría SC-002 y castigaría el servicio oficial y
  los datos de la persona.

---

## D-004: Se deriva el cliente HTTP existente, no se crea otro

**Decisión**: el descargador toma el `OkHttpClient` del grafo y lo deriva con `newBuilder()` para
darle límites de espera más largos.

**Rationale**: un documento tarda más que un feed de 40 KB, así que los 45 s de lectura de la
feature 003 no sirven. Pero crear un cliente nuevo duplicaría el *pool* de conexiones y el caché de
DNS por nada. Derivar comparte la infraestructura y cambia solo la paciencia.

---

## D-005: El detalle y el visor viven fuera del armazón

**Decisión**: `Route.Detail` y `Route.PdfViewer` van en el `NavHost` **exterior**, junto al arranque,
no dentro de `MainShell`.

**Rationale**: `MainShell` existe para dar panel lateral y barra inferior a los tres destinos que los
comparten. El detalle tiene su propia barra de acciones (apartado 18.5) y el visor ocupa la pantalla
entera (apartado 24). Meterlos dentro obligaría a dibujar una barra inferior para luego esconderla,
que es la clase de solución que se paga tres pantallas después. Es la misma razón por la que la
portada está fuera.

---

## D-006: La publicación viaja por clave, nunca por objeto

**Decisión**: `Route.Detail(externalKey)` y `Route.PdfViewer(externalKey)`. El detalle observa la
base de datos por esa clave.

**Rationale**: serializar una `Publication` entera en la ruta crearía una segunda copia que envejece:
si una sincronización corrige el título mientras el detalle está abierto, la ruta seguiría llevando
el viejo. Observando la base de datos, la pantalla se corrige sola, sobrevive a la muerte del proceso
y —de propina— FR-004 sale gratis: si la publicación ya no está, el flujo emite `null`.

---

## D-007: `OfficialDocument` es dominio, y su ruta es una `String`

**Decisión**: `OfficialDocument(externalKey, localPath: String, byteCount, checksum)`.

**Rationale**: `domain` no puede importar `java.io.File` sin arrastrar la plataforma a unas pruebas
que hoy corren sin emulador, y el visor solo necesita saber dónde está el fichero. La `String` es la
frontera: `data` la produce a partir de un `File`, `ui` la convierte en `Uri`. Ninguna de las dos
capas obliga a la otra a conocer su tipo.

---

## D-008: Compartir un fichero necesita `FileProvider`

**Decisión**: se declara un `FileProvider` con `authorities` propio y un `file_paths.xml` acotado al
subdirectorio de documentos de la caché.

**Rationale**: desde Android 7 pasar un `file://` a otra aplicación lanza `FileUriExposedException`.
`FileProvider` da una `content://` con permiso temporal de lectura para la aplicación que recibe, y
solo para ese fichero. Acotar `file_paths.xml` al subdirectorio —y no a la caché entera— es lo que
impide que un fallo futuro exponga algo que no toca.

---

## D-009: El documento abierto se cierra, y lo cierra quien lo abrió

**Decisión**: `PdfDocument` es `Closeable`. Lo abre y lo cierra el modelo de pantalla del visor, en
`onCleared()`.

**Rationale**: mantiene abierto un descriptor de fichero. Dejarlo a la composición sería atarlo al
ciclo de vida equivocado: una recomposición no debe cerrar el documento y un cambio de configuración
no debe reabrirlo.

---

## D-010: La página y la ampliación se conservan a mano

**Decisión**: `firstVisiblePage` se guarda con `rememberSaveable` y se restaura con
`scrollToPage()` cuando el documento vuelve a estar listo.

**Rationale**: `rememberPdfViewerState()` **no** es `rememberSaveable`: su estado se pierde en un
cambio de configuración. FR-029 exige conservarlo, así que se guarda el dato mínimo que permite
reconstruirlo. Es una limitación de la versión beta y por eso queda escrita: si una versión posterior
lo hace por su cuenta, esto sobra.

---

## D-011: La previsualización reutiliza el mismo documento abierto

**Decisión**: la primera página de la pestaña Documento se obtiene con `PdfDocument.BitmapSource`,
el mismo tipo que el visor usa por dentro.

**Rationale**: no hace falta un segundo mecanismo para pintar una página. Y obliga a que la
previsualización y el visor coincidan siempre: si una se ve bien, la otra también.

**Cuándo se descarga**: al entrar en la pestaña Documento, **no** al abrir el detalle. Una persona
que solo quería ver de qué iba el anuncio no debería gastar sus datos en un PDF que no va a leer.

---

## D-012: Una sola descarga por documento, aunque la pidan dos veces

**Decisión**: las peticiones concurrentes del mismo documento se agrupan tras un mismo trabajo en
curso; la segunda espera el resultado de la primera en lugar de lanzar otra descarga.

**Rationale**: es fácil que ocurra —la pestaña Documento pide la previsualización y la persona toca
«Compartir» acto seguido—. Dos descargas simultáneas escribiendo el mismo fichero es corrupción
garantizada, además de tráfico duplicado. Lo exige FR-022.

---

## D-013: Se retira el azucarado, no se deja por inercia

**Decisión**: se eliminan `isCoreLibraryDesugaringEnabled` y `desugar_jdk_libs`, y se comprueba que
la build sigue verde.

**Rationale**: se puso en la feature 003 con un motivo escrito al lado —`java.time` con `minSdk 24`—
y ese motivo ya no existe. Una dependencia que sobrevive a su razón es una dependencia que nadie
sabe por qué está dentro de un año. Lo exige FR-040.

**Salvedad**: si al retirarlo algo más deja de compilar, se queda y se anota **para qué**. Lo que no
puede quedarse es sin explicación.

---

## D-014: `pdf-compose` está en beta, y se encapsula

**Decisión**: `PdfViewerScreen` es el **único** fichero que conoce el tipo `PdfDocument` y el
componible `PdfViewer`. Todo lo demás habla de rutas de fichero.

**Rationale**: una API en beta puede cambiar antes de la estable. Encapsulada, una versión nueva es
un fichero que se toca; repartida por la aplicación, es una tarde.

**Lo que arrastra, y conviene saberlo**: `pdf-compose` depende de `pdf-viewer`, que a su vez trae
`pdf-viewer-fragment` y la biblioteca Material de vistas. No somos nosotros quienes introducimos
Fragments —nuestro código no los usa y la regla de la constitución se refiere a lo que escribimos—
pero entran en el binario. Queda anotado en *Complexity Tracking*.

---

## D-015: `DomainError` sigue sin crecer

**Decisión**: dos casos, `Network` y `Unknown`. Un fallo de red al descargar es `Network`; un
documento que no supera la validación es `Unknown`.

**Rationale**: mismo criterio que en la feature 003. La tentación era añadir `InvalidDocument`, pero
la pantalla hace lo mismo con los dos casos —explicar y ofrecer reintentar— y el sellado se rompería
para todos los `when` existentes sin que nadie aprovechara la distinción. Si algún día el mensaje
tiene que cambiar según la causa, se añade entonces y con motivo.

---

## D-016: Versiones y compatibilidad

| Dependencia | Versión | Notas |
|---|---|---|
| `androidx.pdf:pdf-compose` | `1.0.0-beta01` | `minSdk 28`. Arrastra `pdf-viewer` y `pdf-core` |

| `androidx.pdf:pdf-document-service` | `1.0.0-beta01` | **Hay que declararlo**: ver abajo |

**Confirmado en T003, no supuesto.** La prueba de humo abre un PDF de tres páginas y lo renderiza en
un dispositivo real. La enmienda de `minSdk` queda justificada. Dos cosas que la comprobación
descubrió y que el plan daba por resolver:

1. **`pdf-document-service` hay que declararlo.** `pdf-compose` solo lo arrastra con ámbito de
   *runtime*, así que `SandboxedPdfLoader` —el cargador— no está en el classpath de compilación.
   Sin declararlo, el código que lo nombra no compila.
2. **La API no está solo en beta: está marcada como experimental.** `PdfViewer` y
   `rememberPdfViewerState` exigen `@OptIn(ExperimentalPdfApi::class)`. Refuerza D-014: cuanto menos
   código la conozca, mejor. La anotación vive únicamente en `ui/pdf`.

3. **El BOM de Compose del proyecto convive sin problema** con lo que el artefacto pide.

**Ventaja que no se buscaba y conviene aprovechar**: `SandboxedPdfLoader` renderiza en un **proceso
separado**. Los documentos vienen de un servicio público por internet, así que un PDF malformado no
puede tumbar el proceso de la aplicación. Es un argumento más frente al visor propio sobre
`PdfRenderer`, que habría corrido dentro.
