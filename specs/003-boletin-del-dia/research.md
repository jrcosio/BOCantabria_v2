# Research: Boletín del día — lectura del BOC y pantalla de Inicio

**Feature**: `003-boletin-del-dia` | **Fase**: 0 | **Fecha**: 2026-08-29

Esta es la feature que la constitución estaba esperando. Su apartado de Restricciones Tecnológicas
dice, literalmente, que «la elección de cliente HTTP y de persistencia queda deliberadamente abierta
y DEBE decidirse y justificarse en el `plan.md` de la primera feature que la necesite». Aquí se
deciden ambas, y con ellas todo lo que arrastran: cómo se lee un XML que no cumple RSS del todo, cómo
se identifica una publicación que no trae identificador, y cómo se comprueba todo eso sin red y sin
emulador.

El documento `Datos_modelo/BOC_Cantabria_Consumo_Feeds_RSS.md` es la fuente de verdad del formato.
No se repite aquí: se citan sus apartados.

---

## D-001: Persistencia con Room

**Decisión**: Room 2.8.4 con KSP, esquema exportado a `app/schemas/` desde la versión 1.

**Rationale**: lo que hay que guardar no es un ajuste, es un corpus. Unas mil novecientas
publicaciones en la primera sincronización, que hay que insertar-o-actualizar por identificador
externo, consultar por fecha y por sección, ordenar de forma estable y observar de manera reactiva
para que la pantalla se repinte sola conforme van llegando las fuentes. Room da las cinco cosas:
clave primaria con índice único, `@Upsert`, consultas SQL comprobadas en tiempo de compilación,
`Flow` como tipo de retorno del DAO y migraciones versionadas. Exportar el esquema desde el primer
día es lo que permitirá escribir la prueba de migración cuando llegue la versión 2, y cuesta una
línea hacerlo ahora frente a una arqueología después.

**Alternativas descartadas**:
- **DataStore (Preferences o Proto)**: no consulta. Para mostrar «las publicaciones de la sección
  7.1 ordenadas por fecha» habría que leer el blob entero en memoria y filtrarlo a mano en cada
  emisión. Es la herramienta de las preferencias, no la de un corpus.
- **SQLite a pelo con `SQLiteOpenHelper`**: exactamente lo mismo que Room, escrito a mano, sin
  comprobación de las consultas en compilación y sin `Flow`. Más código y más superficie de error
  para ahorrar una dependencia.
- **Ficheros JSON en el almacenamiento interno**: sin índices, sin actualización parcial, y reescribir
  el fichero completo en cada sincronización. Se descarta por rendimiento y por integridad.

**Coste asumido**: obliga a introducir el plugin KSP, que hoy no existe en el proyecto. Es
inevitable con Room, y KSP es además más rápido y está mejor soportado que kapt en Kotlin 2.2.

---

## D-002: Cliente HTTP: OkHttp a secas, sin Retrofit

**Decisión**: `okhttp-bom` 5.5.0 con el artefacto `okhttp` sin versión, siguiendo la convención de
BOM del proyecto. Sin Retrofit y sin Ktor.

**Rationale**: la aplicación hace exactamente una cosa contra la red: diecinueve peticiones GET que
devuelven XML crudo. Retrofit existe para mapear APIs tipadas mediante conversores, y aquí no hay
API tipada que mapear —habría que enchufarle un conversor de XML y seguir parseando a mano igual—.
Lo que sí hace falta es justo lo que OkHttp da de serie y el apartado 17 del documento de feeds
exige: límites de espera diferenciados de conexión y de lectura, cabeceras `User-Agent` y `Accept`
puestas una sola vez por interceptor, cuerpo en *streaming* para poder cortar por tamaño antes de
cargarlo entero, y reutilización de conexiones para no abrir diecinueve sockets nuevos.

Elegir el BOM, y no la coordenada con versión, mantiene la regla de la constitución: las familias con
BOM no llevan versión en sus artefactos.

**Alternativas descartadas**:
- **`HttpURLConnection`**: cero dependencias, pero hay que escribir a mano los límites de espera, los
  reintentos, el corte por tamaño y el manejo de la codificación. Se ahorra una dependencia a costa
  de escribir peor la parte más delicada.
- **Ktor Client**: capaz y multiplataforma, pero trae su propio motor, su propia configuración y su
  propio modelo de excepciones para el mismo GET. Más piezas móviles sin nada a cambio en un
  proyecto que no es multiplataforma.
- **Retrofit + conversor XML de terceros**: añade dos dependencias para acabar parseando a mano un
  formato que no es RSS estándar.

---

## D-003: El XML se lee con DOM de `javax.xml.parsers`, no con `XmlPullParser`

**Decisión**: el analizador es una clase Kotlin pura que usa `DocumentBuilderFactory`. No toca
`android.util.Xml` ni ninguna API de la plataforma.

**Rationale**: el apartado 24.2 del documento recomienda `XmlPullParser` por consumo de memoria, y
tiene razón para feeds grandes. Pero `XmlPullParser` en Android sale de `android.util.Xml`, y eso
significa que las **treinta y tantas pruebas de analizador que exige el apartado 28** tendrían que
correr bajo Robolectric. `DocumentBuilderFactory` existe igual en la JVM y en Android, así que el
analizador queda como clase pura, comprobable con JUnit a secas y ficheros de muestra, sin emulador y
sin Robolectric. El principio V exige pruebas deterministas y rápidas: esta decisión es la que hace
que la parte más quisquillosa de la feature sea barata de probar.

El argumento de memoria no aplica: el tope de respuesta son 5 MB y el de publicaciones por fuente
son 500 (apartado 19.2). Un árbol DOM de eso cabe de sobra.

**Riesgo real y cómo se mitiga**: la JVM y Android **no** soportan el mismo juego de banderas de
seguridad. Android usa una implementación sobre Expat que lanza `ParserConfigurationException` ante
banderas que no conoce, y `disallow-doctype-decl` es una de las candidatas. Por eso el endurecimiento
va en dos capas:

1. **Guarda previa, portátil y comprobable**: antes de construir el árbol se rechaza el cuerpo si
   contiene una declaración de tipo de documento o de entidad. Es una comprobación de texto, funciona
   igual en las dos plataformas y se prueba con un fichero de muestra.
2. **Endurecimiento de la fábrica en la medida en que la plataforma lo permita**: procesamiento
   seguro, prohibición de doctype, acceso externo a DTD y a esquema vacíos, sin XInclude y sin
   expandir referencias de entidad. **Cada bandera se aplica dentro de un `runCatching`**: que una
   plataforma no conozca una bandera no puede tumbar el analizador.

Una prueba instrumentada de humo confirma que en un dispositivo real también analiza correctamente.
Sin ella, esta decisión sería una suposición.

**Alternativas descartadas**:
- **`XmlPullParser` + Robolectric**: funciona, pero mete toda la matriz de pruebas del apartado 28 en
  el entorno que `CLAUDE.md` ya documenta como el más frágil del proyecto.
- **`xmlutil` con kotlinx-serialization**: elegante y declarativo, pero el feed no es RSS estándar
  —`categorias` no tiene *namespace*, `pubDate` no es RFC 822— así que habría que personalizarlo
  igual, y añade una dependencia de terceros para ahorrar ochenta líneas.
- **Expresiones regulares sobre el XML**: no. Un formato con marcado no se analiza con expresiones
  regulares.

---

## D-004: Fechas con `java.time` y *desugaring* de la biblioteca estándar

**Decisión**: se activa `isCoreLibraryDesugaringEnabled` con `com.android.tools:desugar_jdk_libs`
2.1.5 y se usa `java.time.LocalDate` en todas las capas. En Room se guarda como texto ISO mediante un
`TypeConverter`.

**Rationale**: `pubDate` viene como `AAAA-MM-DD` (apartado 8.1) y `LocalDate` es exactamente el tipo
que representa eso: una fecha sin hora y sin zona. El problema es que `java.time` exige API 26 y la
constitución fija `minSdk 24`. El *desugaring* resuelve ese hueco con una línea de configuración y
una dependencia, **sin tocar `minSdk` y sin enmendar la constitución**, que es lo que habría que
hacer para subirlo.

Guardarlo como texto ISO y no como número de días tiene una ventaja concreta: el orden lexicográfico
coincide con el cronológico, así que `ORDER BY publication_date DESC` funciona sin conversión, y una
inspección de la base de datos es legible por un humano.

`LocalDate` es `java.*`, no `android.*`, así que puede vivir en `domain` sin violar la regla de
Konsist.

**Alternativas descartadas**:
- **Subir `minSdk` a 26**: enmienda de la constitución y pérdida de dispositivos, para ahorrar una
  línea de configuración.
- **`SimpleDateFormat` y `Calendar`**: `SimpleDateFormat` no es seguro entre hilos y la
  sincronización es concurrente. Además ensucia el dominio con una API de 1997.
- **Guardar la fecha como cadena y no modelarla**: el dominio dejaría de saber qué es una fecha y
  toda comparación se volvería una comparación de texto por accidente.

---

## D-005: Iconos como vectores propios, no `material-icons-extended`

**Decisión**: **todos** los iconos son recursos vectoriales propios, con los trazados tomados de las
fuentes oficiales de Material Symbols Outlined y **sin modificarlos**. Son diecinueve: menú, lupa,
información, inicio, compartir, calendario, chevron, marcador, sin conexión y los nueve de sección.

> **Corregido al implementar.** Esta decisión decía que el conjunto básico de iconos vendría con
> Material 3 y solo habría que dibujar once. No es así con este BOM: `androidx.compose.material.icons`
> **no está en el classpath**, ni siquiera el conjunto básico. La alternativa era añadir
> `material-icons-core`; se descartó por coherencia —un solo mecanismo para todos los iconos— y para
> no añadir una dependencia cuando ya existía la tubería de conversión.

**Rationale**: `material-icons-extended` mete varios miles de iconos en el binario y el proyecto tiene
hoy `optimization { enable = false }` en `release`, así que **no se recorta nada**. Añadir megabytes
de trazados para usar once es desproporcionado. Once ficheros XML es una tarea acotada y mecánica, y
además es lo que pide el apartado 9.1 del documento de diseño, que habla de Material Symbols
Outlined con grosor uniforme.

**Alternativas descartadas**:
- **`material-icons-extended`**: la más cómoda de escribir y la peor de enviar, mientras no haya R8.
- **Activar R8 para poder usar la biblioteca**: es algo que el proyecto debería hacer antes de
  publicar, pero es una decisión de build con su propio riesgo de regresión. No se cuela dentro de
  una feature de producto.
- **Recrear los iconos a mano**: prohibido por la misma razón que el escudo se usa tal cual. Un icono
  aproximado se nota.

---

## D-006: La sección la manda la fuente; `categorias` enriquece y verifica

**Decisión**: la sección y la subsección de una publicación se toman del catálogo de la fuente de la
que se obtuvo. El campo `categorias` se guarda íntegro y sin modificar, y solo sirve para deducir el
organismo, detectar el tipo de edición y **contrastar**: si la sección que declara no coincide con la
de la fuente, se anota una advertencia y se conserva la clasificación de la fuente.

**Rationale**: es la regla esencial del apartado 10.3 del documento, y no es un capricho: el feed 4.3
contiene publicaciones antiguas con los componentes permutados (apartado 9.7), de modo que confiar en
posiciones fijas produce clasificaciones erróneas silenciosas. La fuente, en cambio, es autoritativa
por construcción: la URL 6802097 **es** Urbanismo, siempre.

**Consecuencia sobre el análisis de `categorias`**: se separa por barra vertical, se recortan los
espacios, se descartan los componentes vacíos, se busca `ORD` o `EXT` **en cualquier posición**, se
identifican los componentes que empiezan por código de sección con el patrón `^\d+(?:\.\d+)?\.`, y lo
que queda es la ruta del organismo. Ninguna posición es fija.

**Alternativas descartadas**:
- **Clasificar por `categorias`**: rompe con el feed 4.3 y con cualquier publicación sin ese campo.
- **Descartar las publicaciones con orden anómalo**: perdería contenido legítimo. El documento lo
  prohíbe expresamente: «No descartar el item».

---

## D-007: Identificador externo por expresión regular, no con `android.net.Uri`

**Decisión**: el identificador se extrae del enlace con una expresión regular sobre el parámetro
`idAnuBlob`. La cascada de respaldo del apartado 12.2 se implementa entera: identificador del enlace,
si no la URL canónica, y si no una huella SHA-256 de fuente, fecha, título y clasificación. Se guarda
cuál de los tres se usó.

**Rationale**: `android.net.Uri` obligaría a Robolectric para probar la normalización, exactamente el
mismo problema que D-003. La forma del enlace está documentada y es estable; una expresión regular
sobre el parámetro es suficiente y mantiene la normalización en Kotlin puro.

Guardar el origen del identificador no es adorno: si mañana una publicación identificada por huella
de contenido aparece con identificador real, hay que saber que ese registro es sustituible.

**Alternativas descartadas**:
- **`Uri.getQueryParameter`**: correcto, pero arrastra la plataforma a una clase que no la necesita.
- **Identificar por título**: prohibido por el apartado 12.3 del documento, y con razón: los títulos
  se repiten entre ayuntamientos.

---

## D-008: `DomainError` no se amplía

**Decisión**: se mantienen los dos casos existentes, `Network` y `Unknown`. El estado «no hay
conexión pero sí hay contenido guardado» **no es un error**: es un resultado correcto con una bandera
de conectividad en el estado de la pantalla.

**Rationale**: la tentación era añadir un caso para «todas las fuentes fallaron». Pero si hay
contenido guardado, la persona ve el boletín: eso es un éxito, no un fallo, y modelarlo como error
obligaría a la pantalla a decidir cuáles de sus errores se pintan como contenido. Solo hay fallo real
cuando no hay nada que mostrar, y eso ya es `Failure(Network)`.

Ampliar el sellado tiene además un coste concreto: rompe todos los `when` exhaustivos existentes.
Ante la duda, gana la opción más simple.

**Alternativas descartadas**:
- **`DomainError.PartialSync`**: mezcla dos ejes distintos —si hay contenido y si hay conexión— en un
  solo tipo.

---

## D-009: La pantalla observa la base de datos; la sincronización solo escribe

**Decisión**: el repositorio expone `Flow` que emiten desde Room, y `refresh()` no devuelve
publicaciones: escribe en la base de datos y devuelve un resumen. La pantalla nunca lee de la red.

**Rationale**: es lo que hace posible SC-002. Con diecinueve fuentes y un máximo de cuatro
simultáneas, esperar a que terminen todas antes de pintar algo son muchos segundos mirando
marcadores. Si cada fuente escribe en cuanto termina y la pantalla observa la base de datos, el
contenido **aparece conforme llega**. Es además lo que hace que la aplicación funcione igual sin
conexión sin escribir ni una rama extra: la pantalla no sabe si hay red.

**Matiz sobre el arranque en frío**: cuando la base de datos está vacía, la pantalla mantiene los
marcadores hasta que la primera sincronización termina, en lugar de ir mostrando resultados parciales
que reordenarían la lista y cambiarían la fecha de la cabecera dos o tres veces. Con contenido ya
guardado ocurre lo contrario: se pinta al instante y se actualiza por detrás.

**Alternativas descartadas**:
- **`refresh()` que devuelve la lista**: obliga a la pantalla a conocer el ciclo de sincronización y
  a duplicar el estado que ya está en la base de datos.

---

## D-010: Concurrencia, límites de espera y reintentos

**Decisión**: cuatro fuentes simultáneas como máximo mediante un semáforo; límite de conexión 10 s,
de lectura 45 s y total por fuente 60 s; tres intentos con esperas de 2, 5 y 15 segundos **más un
componente aleatorio**, y solo ante fallos que pueden resolverse solos: agotamiento de espera, error
de conexión, 408, 429 y 5xx. Ante 400, 401, 403, 404 o XML inválido no se reintenta. Se respeta
`Retry-After` cuando viene.

**Rationale**: son los valores del apartado 17 del documento, y están razonados allí: en la
observación algunas respuestas tardaron varios segundos, así que los límites agresivos de 3 a 5
segundos producirían falsos fallos. El semáforo evita caerle encima al servicio oficial con
diecinueve conexiones a la vez, que es exactamente lo que un servicio sin compromiso de
disponibilidad no necesita.

**Sobre el componente aleatorio**: introduce no determinismo, que el principio V prohíbe en pruebas.
Se resuelve inyectando la fuente de aleatoriedad, igual que se inyectan los `Dispatchers`: en
producción es real, en pruebas es fija.

---

## D-011: Detección de fuentes sin cambios por huella del cuerpo

**Decisión**: se calcula SHA-256 del cuerpo recibido y se guarda por fuente. Si coincide con el de la
sincronización anterior, no se vuelve a analizar ni a escribir. Se admiten además `ETag` y
`Last-Modified` si algún día aparecen, enviando las cabeceras condicionales correspondientes.

**Rationale**: el apartado 18.1 dice que hoy el servicio no publica ni `ETag` ni `Last-Modified`, así
que la huella del cuerpo es lo único disponible. Ahorra analizar y escribir cien publicaciones
idénticas diecinueve veces al día, que es el caso normal: el BOC publica una vez.

Admitir las cabeceras condicionales desde ahora cuesta poco y evita tener que volver aquí si el
servicio mejora.

---

## D-012: El catálogo de fuentes vive en `data`; las secciones, en `domain`

**Decisión**: `BocSection` —código, nombre, sección padre y orden— es un modelo de dominio, porque es
conocimiento del negocio: las nueve secciones del BOC y sus subsecciones. El catálogo que asocia cada
sección con su identificador de fuente y su dirección vive en `data/source/remote`, porque una URL es
un detalle de procedencia.

**Rationale**: es la línea que separa qué es el BOC de cómo se obtiene el BOC. Si mañana existe un
servicio propio, las secciones no cambian y el catálogo de direcciones desaparece. Poner las URL en
`domain` obligaría a reescribir el dominio el día que cambie la procedencia, que es justo lo que la
arquitectura limpia evita.

Las direcciones se escriben **literales**, una a una, como exige el apartado 11.1: prohibido
construirlas sumando números.

---

## D-013: Las nueve secciones sobre cinco colores

**Decisión**: se mantienen los cinco tokens de sección que el documento de diseño define y ya están
implementados, y se asignan así:

| Color | Secciones |
|---|---|
| `sectionGeneral` | 1 Disposiciones Generales · 9 Elecciones |
| `sectionPersonnel` | 2 Autoridades y Personal |
| `sectionContracting` | 3 Contratación Administrativa |
| `sectionEconomy` | 4 Economía, Hacienda y Seguridad Social · 6 Subvenciones y Ayudas |
| `sectionAnnouncements` | 5 Expropiación Forzosa · 7 Otros Anuncios · 8 Procedimientos Judiciales |

**Rationale**: el apartado 4.4 del documento define cinco grupos cromáticos, no nueve. Inventar
cuatro colores más rompería una paleta que ya está validada en contraste y diluiría el significado:
con nueve colores, el color deja de agrupar y pasa a ser ruido. La agrupación elegida sigue el
sentido del contenido —lo económico junto, lo que son anuncios junto—.

**Por qué no pierde información**: FR-038 obliga a que el indicador de color vaya siempre acompañado
de texto. El color agrupa; el texto identifica. Es además lo que exige el apartado 31.4 de
accesibilidad.

---

## D-014: Selección de sección por argumento de ruta

**Decisión**: `Route.Home` pasa a llevar sección y subsección opcionales. El panel lateral navega a
`Route.Home(código)` reemplazando la entrada anterior, y `HomeViewModel` lee la selección del
`SavedStateHandle`.

**Rationale**: el panel vive por encima del `NavHost` —lo envuelve— así que no puede compartir el
`ViewModel` de Inicio sin inventar un canal entre modelos de pantalla, que es justo el tipo de
acoplamiento que MVVM evita. La navegación tipada que el proyecto ya usa resuelve esto sin nada
nuevo: la selección viaja como argumento, y de propina sobrevive a la muerte del proceso, que es lo
que exige FR-048.

Reemplazar la entrada en lugar de apilarla mantiene la pila de retroceso con una sola entrada de
Inicio, de modo que el comportamiento del retroceso **no cambia** respecto a hoy y
`SplashBackStackTest` sigue afirmando lo mismo. Volver al boletín del día es un toque en el chip
«Todo», no un gesto de retroceso.

**Alternativas descartadas**:
- **Estado compartido entre el panel y la pantalla**: exige un `ViewModel` de ámbito superior o un
  contenedor mutable global, prohibido por el principio IV.
- **Apilar una entrada por selección**: la pila crecería indefinidamente al pasear por el panel.

---

## D-015: Retirar la cadena de relleno de la feature 001

**Decisión**: `ContentItem`, `ContentRepository`, `GetContentItemsUseCase`, `ContentItemDto`,
`ContentItemEntity`, `StubContentRemoteDataSource`, `InMemoryContentLocalDataSource` y
`ContentRepositoryImpl` **se eliminan**, junto con las pruebas que solo existían para ellos. Su lugar
lo ocupa la cadena real de publicaciones.

**Rationale**: la especificación de la feature 001 declaró `ContentItem` como marcador «sustituible
por la primera entidad de negocio». Esta es esa entidad. Dejar las dos cadenas conviviendo produciría
un grafo con dos repositorios de contenido, uno de ellos alimentado por datos inventados, y una
pantalla que tendría que elegir. Mantener el marcador después de que llegue lo real es cómo se
acumulan las capas muertas.

**Sobre borrar pruebas**: el principio V prohíbe borrar una prueba **para hacer pasar la build**. Aquí
se borra el código de producción y su prueba a la vez, y se sustituyen por las de la cadena real, que
cubren lo mismo y más. Es sustitución, no supresión, y queda registrado aquí para que se pueda
auditar.

**Lo que sí se conserva**: `AppResult`, `DomainError`, `DispatcherProvider`, la telemetría y todo el
sistema de diseño. La cadena de arranque de la feature 002 no se toca.

---

## D-016: Qué se compone y qué se prueba en la interfaz

**Decisión**: el armazón —panel lateral y barra inferior— vive en un componible propio que envuelve
el `NavHost`, y el arranque queda **fuera** de él. Cada pieza de Inicio (cabecera, chips, tarjeta,
esqueleto) es un componible sin estado que recibe todo por parámetro.

**Rationale**: el arranque no lleva barra inferior ni panel lateral, así que envolver el `NavHost`
entero obligaría a dibujarlos y esconderlos. Envolver solo los tres destinos mantiene el arranque
intacto y su prueba de pila de retroceso también.

Que las piezas sean sin estado es lo que permite probarlas con `createComposeRule()`, sin grafo, sin
red y sin pasar por el mínimo de 1,2 s del arranque —que, como advierte `CLAUDE.md`, atraviesa toda
prueba instrumentada que arranque `MainActivity`—. Solo las pruebas de navegación pagan ese peaje.

---

## D-017: Versiones concretas y su compatibilidad

Comprobadas contra los repositorios en la fecha de este documento:

| Dependencia | Versión | Notas |
|---|---|---|
| Plugin KSP | `2.2.10-2.0.2` | La numeración de KSP ata plugin y compilador: el prefijo **debe** ser el Kotlin del proyecto, 2.2.10 |
| Room | `2.8.4` | `room-runtime`, `room-ktx`, `room-compiler` (KSP) y `room-testing` |
| OkHttp | BOM `5.5.0` | `okhttp` y `mockwebserver3-junit4` sin versión, gobernados por el BOM |
| `desugar_jdk_libs` | `2.1.5` | Requiere `isCoreLibraryDesugaringEnabled = true` |

**Hallazgo al configurar**: AGP 9 prohíbe que un plugin añada fuentes por `kotlin.sourceSets`, y KSP
registra ahí sus directorios generados, así que la build falla con
*«Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin»*. La vía que
el propio AGP documenta es `android.disallowKotlinSourceSets=false` en `gradle.properties`. Queda
puesta con el motivo escrito al lado; cuando KSP migre a `android.sourceSets`, sobra.

**Otro hallazgo**: `javax.xml.XMLConstants` en Android **no declara** `ACCESS_EXTERNAL_DTD` ni
`ACCESS_EXTERNAL_SCHEMA` —son de JAXP 1.5—, así que referenciarlas no compila. Los valores se
escriben literales; una propiedad desconocida la traga el `runCatching` de D-003.

`settings.gradle.kts` resuelve los plugins desde `google()`, `mavenCentral()` y `gradlePluginPortal()`.
KSP se publica en Maven Central y en el portal, así que el filtro por expresión regular de `google()`
no lo bloquea: la resolución cae en los siguientes repositorios.

**Riesgo**: el proyecto va por delante en algunas familias (AGP 9.3.2, BOM de Compose de 2026.02) y
las versiones de Room y KSP hay que confirmarlas contra ese AGP al compilar. La tarea de configuración
del build es, por eso, la primera de todas y su punto de control es que `assembleDebug` pase.
