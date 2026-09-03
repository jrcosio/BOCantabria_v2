# Investigación: Pantalla «Acerca de»

## D-001 — Destino en el grafo exterior

**Decisión**: `Route.Info` vive junto a Detalle, Visor y Preguntar, fuera de `MainShell`.

**Motivo**: es una pantalla secundaria con Atrás y no un destino de la barra inferior. `MainShell`
solo eleva `onOpenInfo`, igual que eleva la apertura de una publicación.

## D-002 — Enlaces HTTPS resueltos por Android

**Decisión**: usar el manejador de URI de Compose con las URL HTTPS públicas.

**Motivo**: un enlace web permite que Android abra la aplicación asociada si existe y caiga al
navegador sin mantener dos esquemas ni incorporar WebView. La apertura se captura para que la
ausencia de manejador produzca un mensaje y no una excepción visible.

## D-003 — Modelo de pantalla mínimo

**Decisión**: `InfoViewModel` expone `InfoUiState` con `versionName` y el aviso transitorio de fallo.

**Motivo**: respeta la convención una pantalla–un estado–un modelo, permite leer la versión mediante
el seam existente y registrar telemetría con dobles. La apertura del URI sigue siendo UI.

## D-004 — Versión mediante AppVersionProvider

**Decisión**: ampliar `AppVersionProvider` con `versionName`.

**Motivo**: ya es la frontera entre BuildConfig y el resto de la aplicación. Crear otra interfaz o
leer BuildConfig desde el Composable duplicaría la misma responsabilidad.

## D-005 — Fotografía optimizada

**Decisión**: conservar la referencia fuera de versión y añadir un JPEG cuadrado de 640 px en
`drawable-nodpi`, recortado circularmente en Compose.

**Motivo**: la fuente pesa 1,8 MB y 1254 px por lado; 640 px cubre el tamaño dibujado incluso en alta
densidad con un coste de APK muy inferior. El entorno de construcción no dispone de un codificador
WebP, mientras que el JPEG resultante mide 66 KB. El recorte no debe hornearse para mantener
flexibilidad.

## D-006 — Sin dependencia de iconos

**Decisión**: añadir vectores locales de persona, código, apertura externa, propósito, comunidad,
móvil e infraestructura siguiendo el sistema actual, sin una biblioteca de iconos ni logos de marca.

**Motivo**: el texto identifica inequívocamente LinkedIn y GitHub; los símbolos genéricos evitan
nuevas dependencias y mantienen el lenguaje visual del proyecto.

## D-007 — Contenido desplazable y ancho acotado

**Decisión**: una única `LazyColumn`, con contenido centrado y ancho máximo de lectura en pantallas
grandes.

**Motivo**: la referencia supera la altura de un teléfono y debe sobrevivir a fuentes grandes. Una
lista evita recortes y conserva el estado de desplazamiento al volver de otra aplicación.
