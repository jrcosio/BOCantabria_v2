# BOC Cantabria — Especificaciones de diseño UI

**Documento visual para el rediseño de la aplicación Android**  
**Versión:** 2.0  
**Fecha:** 27 de agosto de 2026  
**Autor de la aplicación:** José Ramón Blanco  
**Alcance:** identidad visual, sistema de diseño, componentes y pantallas

---

## 1. Propósito de este documento

Este documento define exclusivamente el nuevo diseño visual de la aplicación BOC Cantabria.

Incluye:

- Dirección artística.
- Paleta cromática.
- Tipografía.
- Retícula y espaciado.
- Formas, bordes y elevaciones.
- Iconografía.
- Navegación visual.
- Componentes reutilizables.
- Composición de cada pantalla.
- Estados visuales.
- Modo oscuro.
- Accesibilidad visual.
- Animaciones y transiciones.
- Reglas de diseño adaptable.
- Criterios visuales de aceptación.

No incluye arquitectura, API, modelos de datos, seguridad, persistencia ni lógica interna de las funcionalidades.

---

## 2. Visión visual

La nueva aplicación debe transmitir tres cualidades:

1. **Oficial:** el usuario debe reconocer inmediatamente que consulta información institucional.
2. **Clara:** el contenido administrativo debe resultar ordenado, legible y fácil de explorar.
3. **Actual:** la interfaz debe sentirse propia de una aplicación Android moderna, no de una web antigua adaptada al móvil.

El estilo visual se apoyará en el azul institucional, el escudo de Cantabria, fondos claros, tipografía limpia y componentes sobrios inspirados en Material 3.

La aplicación no deberá adoptar una estética excesivamente tecnológica por incorporar IA. Las funciones de IA tendrán un tratamiento visual propio, pero siempre integrado dentro de la identidad institucional.

### 2.1. Concepto creativo

**“El boletín oficial, claro y accesible.”**

La interfaz combinará:

- La autoridad visual de una publicación oficial.
- La claridad de una aplicación editorial.
- La fluidez de una herramienta móvil contemporánea.

### 2.2. Palabras que deben definir el diseño

- Institucional.
- Sobrio.
- Editorial.
- Ordenado.
- Cercano.
- Fiable.
- Contemporáneo.
- Accesible.

### 2.3. Palabras que no deben definir el diseño

- Antiguo.
- Recargado.
- Burocrático.
- Infantil.
- Estridente.
- Futurista.
- Genérico.
- Decorativo.

---

## 3. Principios de diseño UI

### 3.1. El contenido es el protagonista

El diseño debe ordenar el contenido, no competir con él. Los títulos de las publicaciones, el organismo y la sección serán los elementos principales de las tarjetas.

### 3.2. Jerarquía antes que decoración

La diferenciación se realizará mediante tamaño, peso tipográfico, espaciado y agrupación. Las sombras, colores y bordes se usarán con moderación.

### 3.3. Identidad institucional sin rigidez

El escudo y el azul oficial estarán presentes, pero no se repetirán de forma excesiva. Se evitarán grandes cabeceras permanentes que resten espacio al contenido.

### 3.4. Interfaz tranquila

El usuario debe poder leer durante varios minutos sin fatiga visual. Los fondos serán neutros, los contrastes controlados y las superficies amplias.

### 3.5. IA visualmente diferenciada

Los resúmenes y respuestas generadas utilizarán una etiqueta, un icono y un color secundario discreto. Nunca tendrán la misma apariencia que el texto oficial.

### 3.6. Consistencia total

Una misma acción utilizará siempre el mismo icono, color, posición y comportamiento visual.

---

## 4. Identidad cromática

## 4.1. Paleta principal — modo claro

| Token | Valor | Uso visual |
| --- | --- | --- |
| `Primary` | `#063B5C` | Azul institucional, títulos principales y botones destacados |
| `OnPrimary` | `#FFFFFF` | Texto e iconos sobre azul institucional |
| `PrimaryPressed` | `#042C45` | Estado pulsado del color principal |
| `PrimaryContainer` | `#DCEEF6` | Fondos informativos y selecciones suaves |
| `OnPrimaryContainer` | `#082F45` | Texto sobre contenedores azules claros |
| `Secondary` | `#087EA4` | Controles seleccionados, enlaces e iconos activos |
| `SecondaryPressed` | `#056686` | Estado pulsado secundario |
| `SecondaryContainer` | `#DDF3FA` | Chips y superficies secundarias |
| `AccentOfficial` | `#C62828` | Acento oficial, avisos y pequeños detalles |
| `AiAccent` | `#6650A4` | Identificación visual de IA |
| `AiContainer` | `#F1EDFA` | Fondo de tarjetas de IA |
| `Background` | `#F6F8FA` | Fondo general de pantallas |
| `Surface` | `#FFFFFF` | Tarjetas, paneles y hojas |
| `SurfaceSoft` | `#F0F4F7` | Bloques secundarios y fondos agrupados |
| `SurfaceStrong` | `#E6EDF1` | Elementos deshabilitados o divisiones visuales |
| `TextPrimary` | `#122B3A` | Texto principal |
| `TextSecondary` | `#536873` | Metadatos y textos secundarios |
| `TextMuted` | `#778993` | Información de menor jerarquía |
| `Outline` | `#B8C4CB` | Bordes de controles y tarjetas |
| `Divider` | `#D9E0E4` | Separadores |
| `Success` | `#2E7D32` | Confirmaciones visuales |
| `Warning` | `#ED6C02` | Avisos |
| `Error` | `#BA1A1A` | Errores |

### 4.2. Proporción de uso del color

- 65 % fondos blancos o gris muy claro.
- 20 % azul institucional.
- 10 % azul atlántico y contenedores claros.
- 5 % acentos, estados, IA y advertencias.

El rojo no debe usarse como color dominante de navegación.

### 4.3. Aplicación del color principal

Usar `Primary` en:

- Barra superior de pantallas secundarias.
- Botón principal.
- Títulos destacados.
- Línea de categoría de las tarjetas.
- Iconos seleccionados cuando sea necesario.
- Cabecera editorial de la edición.

No usar `Primary` como fondo de todas las tarjetas.

### 4.4. Color de secciones

Las secciones pueden incorporar un pequeño indicador cromático, siempre acompañado de texto:

| Grupo | Color orientativo |
| --- | --- |
| Disposiciones generales | `#1565C0` |
| Autoridades y personal | `#6A4C93` |
| Contratación administrativa | `#00838F` |
| Economía y Hacienda | `#2E7D32` |
| Anuncios y notificaciones | `#AD5B00` |

El color de sección se utilizará únicamente en:

- Línea vertical de 4 dp en la tarjeta.
- Punto o icono pequeño.
- Etiqueta de sección.

No se utilizará como fondo completo de la tarjeta.

---

## 5. Modo oscuro — SUPERADO

> **Este apartado queda sin efecto.** Por decisión del propietario (28 de agosto de 2026), la
> aplicación tiene un **único aspecto, el claro**, y no responde al ajuste de tema del sistema.
> Motivo: una publicación oficial debe verse igual en todos los dispositivos, y mantener un segundo
> aspecto duplicaría el coste de diseñar y verificar cada pantalla sin aportar valor.
>
> El contenido se conserva por si la decisión se revisara en el futuro, pero **no se implementa**.
> El único valor que sobrevive es `#8FD3EE`, que no se usa como color de modo oscuro sino como
> acento *sobre* el azul institucional (línea divisoria y autoría de la portada).

## 5.1. Paleta oscura

| Token | Valor | Uso visual |
| --- | --- | --- |
| `PrimaryDark` | `#8FD3EE` | Acciones y elementos seleccionados |
| `OnPrimaryDark` | `#003549` | Texto sobre azul claro |
| `PrimaryContainerDark` | `#064A6B` | Contenedores destacados |
| `BackgroundDark` | `#0F171C` | Fondo general |
| `SurfaceDark` | `#182229` | Tarjetas y superficies |
| `SurfaceSoftDark` | `#202D35` | Agrupaciones secundarias |
| `TextPrimaryDark` | `#E7F0F4` | Texto principal |
| `TextSecondaryDark` | `#B5C5CD` | Metadatos |
| `OutlineDark` | `#647680` | Bordes |
| `AiContainerDark` | `#29243A` | Tarjetas de IA |

## 5.2. Reglas del modo oscuro

- No usar negro puro como fondo general. *(No aplicable: ver la nota del apartado 5.)*
- No invertir el escudo ni modificar sus colores.
- Reducir la intensidad de las sombras y sustituirlas por diferencias de superficie.
- Evitar blanco puro en grandes bloques de texto.
- Mantener el color de sección con saturación moderada.
- El contenido generado por IA conservará una diferenciación violeta muy suave.
- La barra del sistema deberá integrarse con el fondo de cada pantalla.

---

## 6. Tipografía

## 6.1. Familia

Se recomienda **Roboto**, **Roboto Flex** o la tipografía estándar de Android.

No se combinarán varias familias tipográficas. El carácter editorial se conseguirá mediante escala, peso y espaciado.

## 6.2. Escala tipográfica

| Token | Tamaño | Interlineado | Peso | Uso |
| --- | ---: | ---: | ---: | --- |
| `DisplayLarge` | 56 sp | 64 sp | 400 | Siglas BOC en portada |
| `DisplaySmall` | 40 sp | 48 sp | 400 | Título editorial excepcional |
| `HeadlineLarge` | 30 sp | 38 sp | 650 | Título de publicación |
| `HeadlineMedium` | 26 sp | 34 sp | 650 | Título principal de pantalla |
| `HeadlineSmall` | 22 sp | 28 sp | 650 | Título de bloque |
| `TitleLarge` | 20 sp | 26 sp | 600 | Título de tarjeta destacada |
| `TitleMedium` | 17 sp | 23 sp | 600 | Título de publicación en listado |
| `TitleSmall` | 15 sp | 20 sp | 600 | Organismos o cabeceras pequeñas |
| `BodyLarge` | 16 sp | 24 sp | 400 | Texto principal de lectura |
| `BodyMedium` | 14 sp | 21 sp | 400 | Descripciones y resúmenes |
| `BodySmall` | 12 sp | 18 sp | 400 | Información auxiliar |
| `LabelLarge` | 14 sp | 20 sp | 600 | Botones y pestañas |
| `LabelMedium` | 12 sp | 17 sp | 600 | Chips y categorías |
| `LabelSmall` | 11 sp | 15 sp | 600 | Metadatos muy breves |

## 6.3. Reglas tipográficas

- Alineación izquierda en títulos y párrafos.
- No justificar el texto.
- No usar cuerpos inferiores a 12 sp.
- No utilizar mayúsculas en párrafos completos.
- Reservar mayúsculas para categorías cortas.
- Los títulos de publicaciones pueden ocupar varias líneas.
- Evitar cortar títulos con puntos suspensivos en la pantalla de detalle.
- Usar cifras tabulares en fechas y números de boletín si la fuente lo permite.
- Mantener un máximo aproximado de 70 caracteres por línea en tabletas.

---

## 7. Retícula y espaciado

## 7.1. Unidad base

La retícula utilizará una unidad base de 4 dp.

| Token | Valor | Uso |
| --- | ---: | --- |
| `Space1` | 4 dp | Ajustes mínimos |
| `Space2` | 8 dp | Separación entre icono y texto |
| `Space3` | 12 dp | Espaciado interno compacto |
| `Space4` | 16 dp | Margen estándar y padding de tarjetas |
| `Space5` | 20 dp | Separación de bloques relacionados |
| `Space6` | 24 dp | Separación de secciones |
| `Space8` | 32 dp | Separación grande |
| `Space10` | 40 dp | Áreas editoriales |
| `Space12` | 48 dp | Grandes zonas de respiración |

## 7.2. Márgenes de pantalla

- Teléfono compacto: 16 dp.
- Teléfono grande: 20 dp.
- Tableta: 32 dp.
- Contenido centrado en tableta con ancho máximo de 840 dp.
- Texto de lectura en tableta con ancho máximo de 680 dp.

## 7.3. Separación vertical

- Entre barra superior y primer bloque: 16–24 dp.
- Entre título y metadatos: 12–16 dp.
- Entre tarjetas: 12 dp.
- Entre secciones principales: 24–32 dp.
- Entre párrafos: 12 dp.
- Entre una tarjeta de IA y su aviso: 12 dp.

---

## 8. Formas, bordes y elevación

## 8.1. Radios

| Componente | Radio |
| --- | ---: |
| Tarjeta estándar | 14 dp |
| Tarjeta destacada | 18 dp |
| Botón | 12 dp |
| Campo de texto | 14 dp |
| Hoja inferior | 28 dp en esquinas superiores |
| Diálogo | 24 dp |
| Chip | Radio completo |
| Banner | 12 dp |

## 8.2. Elevación

| Nivel | Elevación | Uso |
| --- | ---: | --- |
| `Level0` | 0 dp | Fondos y tarjetas delimitadas por borde |
| `Level1` | 1 dp | Tarjetas estándar |
| `Level2` | 3 dp | Barra inferior y elementos flotantes |
| `Level3` | 6 dp | Hojas inferiores y menús |
| `Level4` | 8 dp | Diálogos |

Las sombras serán suaves, amplias y de baja opacidad. Se preferirá separar superficies mediante contraste de fondo.

## 8.3. Bordes

- Tarjetas: borde opcional de 1 dp con `Divider`.
- Campos: borde de 1 dp; 2 dp al recibir foco.
- Botones secundarios: borde de 1 dp con `Primary`.
- Tarjetas de cita: borde de 1 dp con `Outline` a baja opacidad.

---

## 9. Iconografía y recursos gráficos

## 9.1. Estilo de iconos

- Material Symbols Outlined o Rounded.
- Grosor visual uniforme.
- Tamaño estándar: 24 dp.
- Tamaño compacto: 20 dp.
- Tamaño destacado: 32 dp.
- Área táctil mínima visualmente reservada: 48 × 48 dp.

## 9.2. Iconos principales

| Acción | Icono sugerido |
| --- | --- |
| Inicio | `home` |
| Buscar | `search` |
| Guardados | `bookmark` |
| Avisos | `notifications` |
| Secciones | `category` o `list_alt` |
| Fecha | `calendar_month` |
| Compartir | `share` |
| PDF | `picture_as_pdf` o `description` |
| Resumen IA | `auto_awesome` |
| Preguntar | `chat_bubble_outline` |
| Fuente | `article` |
| Organismo | `account_balance` |
| Descargar | `download` |
| Sin conexión | `cloud_off` |
| Ajustes | `settings` |

## 9.3. Escudo oficial

- Utilizar el recurso oficial, no una recreación generada.
- Mantener proporciones originales.
- No añadir sombra fuerte.
- No colocar dentro de círculos o formas arbitrarias.
- Tamaño aproximado en barra superior: 32–36 dp de alto.
- Tamaño aproximado en portada: 96–112 dp de alto.
- Dejar espacio libre mínimo equivalente al 20 % de su ancho.

## 9.4. Ilustraciones

Las pantallas vacías pueden utilizar ilustraciones lineales muy sencillas basadas en documentos, búsqueda o campanas.

No utilizar:

- Personajes 3D.
- Robots para representar la IA.
- Dibujos infantiles.
- Fotografías de edificios institucionales como fondo.
- Marcas de agua repetidas con el escudo.

---

## 10. Navegación visual

## 10.1. Barra inferior

> **Enmienda (29 de agosto de 2026, feature 003).** La barra tiene **tres** destinos, no cuatro.
> `Avisos` se aplaza junto con el resto del trabajo de notificaciones: un cuarto destino que solo
> pudiera prometer algo sería peor que tres que llevan a alguna parte. Cuando existan las
> notificaciones se recuperará como cuarto destino y el resto de este apartado seguirá valiendo.

La navegación principal tendrá tres destinos:

- `Inicio`.
- `Buscar`.
- `Guardados`.

### Medidas

- Altura visual: 80 dp más el área segura del sistema.
- Icono: 24 dp.
- Texto: `LabelMedium`.
- Separación icono-texto: 4 dp.

### Estado activo

- Icono relleno o contenido en una cápsula azul muy clara.
- Texto en `Secondary` o `Primary`.
- Peso tipográfico 600.

### Estado inactivo

- Icono outlined.
- Texto en `TextSecondary`.
- Sin fondo de cápsula.

### Reglas

- Fondo blanco en modo claro.
- Fondo `SurfaceDark` en modo oscuro.
- Borde superior de 1 dp o elevación mínima.
- No usar una franja roja.
- No colocar un botón circular central de actualización.
- No mostrar más de cuatro destinos. Hoy son tres (ver la enmienda del encabezado).

## 10.2. Barra superior principal

> **Enmienda (29 de agosto de 2026, feature 003).** La barra principal empieza por el control que
> abre el panel de secciones y termina en Buscar e Información. La campana desaparece mientras no
> existan las notificaciones: un icono presente que no hace nada es peor que un icono que todavía
> no está.

Composición:

- Menú, que abre el panel de secciones.
- Escudo.
- Texto `BOC Cantabria`.
- Espaciador flexible.
- Buscar.
- Información.

Medidas:

- Altura: 64 dp más área segura.
- Padding horizontal: 16 dp.
- Separación escudo-título: 10 dp.

La barra puede ser blanca en Inicio para dar ligereza y azul institucional en pantallas secundarias.

## 10.3. Barra superior secundaria

- Fondo `Primary`.
- Flecha Atrás.
- Escudo opcional.
- Título de pantalla.
- Una o dos acciones como máximo.
- Texto e iconos blancos.

---

## 11. Componentes base

## 11.1. Botón principal

- Fondo `Primary`.
- Texto `OnPrimary`.
- Altura: 52 dp.
- Padding horizontal: 20 dp.
- Radio: 12 dp.
- Icono opcional de 20–24 dp.
- Texto `LabelLarge`.

Estados:

- Normal: `Primary`.
- Pulsado: `PrimaryPressed`.
- Deshabilitado: `SurfaceStrong` con texto `TextMuted`.
- Cargando: mantener ancho y mostrar indicador de 20 dp.

## 11.2. Botón secundario

- Fondo transparente o `Surface`.
- Borde de 1 dp con `Primary`.
- Texto e icono `Primary`.
- Mismas medidas que el botón principal.

## 11.3. Botón de texto

- Sin fondo ni borde.
- Altura mínima visual: 48 dp.
- Texto `Secondary`.
- Usar para acciones de baja jerarquía.

## 11.4. Botón de icono

- Icono de 24 dp.
- Contenedor táctil de 48 dp.
- Fondo transparente en reposo.
- Fondo circular `PrimaryContainer` al pulsar.
- Tooltip o etiqueta accesible cuando el significado no sea evidente.

## 11.5. Chip de filtro

- Altura: 36–40 dp.
- Padding horizontal: 14 dp.
- Borde: 1 dp.
- Texto `LabelLarge`.

Estado seleccionado:

- Fondo `Secondary`.
- Texto blanco.
- Borde `Secondary`.

Estado no seleccionado:

- Fondo `Surface`.
- Texto `TextPrimary`.
- Borde `Outline`.

## 11.6. Campo de búsqueda

- Altura: 56 dp.
- Radio: 16 dp.
- Fondo `Surface`.
- Borde de 1 dp con `Outline` o sombra Level1.
- Icono de búsqueda a la izquierda.
- Texto de entrada `BodyLarge`.
- Placeholder `TextMuted`.
- Acción de borrar a la derecha cuando contiene texto.

## 11.7. Pestañas

- Altura: 56 dp.
- Texto e icono opcional.
- Indicador inferior de 3 dp.
- Fondo de superficie, sin cápsulas grandes.
- Pestaña activa en `Primary`.
- Pestaña inactiva en `TextSecondary`.

## 11.8. Divisor

- Grosor: 1 dp.
- Color `Divider`.
- No atravesar iconos o márgenes internos.
- Utilizarlo solo cuando el espaciado no sea suficiente.

---

## 12. Tarjeta de publicación

## 12.1. Estructura visual

La tarjeta es el componente central de la aplicación.

Orden de lectura:

1. Organismo emisor.
2. Título de la publicación.
3. Fecha o metadatos.
4. Acciones secundarias.

### Composición

- Fondo `Surface`.
- Radio de 14 dp.
- Padding de 16 dp.
- Elevación Level1 o borde fino.
- Línea vertical de sección de 4 dp.
- Separación de 12 dp entre la línea y el contenido.
- Guardar y compartir alineados en la zona inferior derecha.

### Organismo

- Estilo `LabelMedium` o `TitleSmall`.
- Color `TextSecondary`.
- Mayúsculas opcionales si el texto es corto.
- Máximo dos líneas.

### Título

- Estilo `TitleMedium`.
- Color `TextPrimary`.
- Interlineado amplio.
- Máximo cuatro líneas en listado.

### Metadatos

- Icono de calendario de 18–20 dp.
- Estilo `BodySmall`.
- Color `TextSecondary`.

### Acciones

- Iconos outlined de 24 dp.
- Separación de 8 dp.
- Guardado activo con icono relleno y color `Primary`.

## 12.2. Variantes

### Tarjeta estándar

Utilizada en Inicio y secciones.

### Tarjeta compacta

Utilizada en búsqueda y guardados:

- Padding de 12 dp.
- Título máximo tres líneas.
- Menos separación vertical.

> **Enmienda (30 de agosto de 2026, feature 005).** Guardados usa la **tarjeta estándar**, la misma
> que Inicio. La variante compacta queda aplazada, no descartada. El motivo: la tarjeta pasa a ser un
> componible compartido entre las dos pantallas, y añadirle un parámetro de densidad para diferenciar
> dos listas que muestran lo mismo habría sido más superficie de la que la diferencia justifica. Si
> Buscar la necesita cuando llegue, se añade entonces y con su motivo.

### Tarjeta destacada

Utilizada para una publicación relevante:

- Fondo `PrimaryContainer` muy suave.
- Etiqueta superior.
- Sin aumentar excesivamente la sombra.

### Tarjeta con IA disponible

- Pequeña etiqueta con icono `auto_awesome`.
- Texto `Resumen disponible`.
- Color `AiAccent`.
- No convertir toda la tarjeta en violeta.

---

## 13. Pantalla de arranque

## 13.1. Composición

- Fondo azul institucional a pantalla completa.
- Escudo centrado ligeramente por encima del centro óptico.
- Siglas `BOC` debajo del escudo.
- Texto `BOLETÍN OFICIAL` y `DE CANTABRIA` en dos líneas.
- Línea horizontal fina en azul atlántico.
- Autoría en la parte inferior.
- Indicador de carga pequeño y discreto.

## 13.2. Medidas orientativas

- Escudo: 104 dp de alto.
- Separación escudo-BOC: 24 dp.
- `BOC`: `DisplayLarge` en blanco.
- Subtítulo: 20 sp, peso 500, tracking amplio.
- Línea: 120 dp de ancho y 2 dp de grosor.
- Autoría en dos líneas y dos colores: etiqueta a 13 sp en blanco al 70 %, y nombre a 15 sp con
  peso 600 en azul claro `#8FD3EE`, el mismo de la línea divisoria.
- El bloque de autoría y el indicador de carga se anclan a la parte inferior respetando el área
  segura, con el indicador debajo del nombre.

## 13.3. Texto

```text
BOC
BOLETÍN OFICIAL
DE CANTABRIA

Diseñada y desarrollada por
José Ramón Blanco Gutiérrez
```

## 13.4. Elementos que se eliminan

- Retrato del autor.
- Llaves decorativas.
- Usuario de Twitter.
- Varias tonalidades intensas de azul compitiendo entre sí.
- Logotipos adicionales.

---

## 14. Pantalla Inicio — Boletín del día

## 14.1. Estructura vertical

1. Barra superior blanca.
2. Cabecera editorial azul.
3. Fila de filtros.
4. Listado de tarjetas.
5. Barra inferior.

## 14.2. Barra superior

- Icono de menú al inicio, que abre el panel de secciones.
- Escudo de 34 dp.
- Título `BOC Cantabria` en `TitleLarge`.
- Icono Buscar.
- Icono Información.
- Altura aproximada: 64 dp.

> **Enmienda (29 de agosto de 2026, feature 003).** Sustituye a la composición anterior, que
> llevaba el icono de Avisos con punto rojo. Ver la enmienda del apartado 10.2.

## 14.3. Cabecera editorial

- Fondo `Primary`.
- Altura aproximada: 150–170 dp.
- Padding: 24 dp horizontal y 24 dp vertical.
- Icono editorial pequeño en `Secondary` claro.
- Título `Boletín de hoy` en blanco, `HeadlineLarge`. Al elegir una sección, su nombre.
- Fecha debajo en `BodyLarge`.
- **Número de publicaciones** en un contenedor outlined alineado a la derecha.

> **Enmienda (29 de agosto de 2026, feature 003).** El contenedor mostraba el número de boletín.
> Los feeds oficiales que la aplicación consume **no lo publican** —el apartado 7.4 del documento
> de consumo de feeds lo dice expresamente— y escribirlo junto al escudo sería presentar un dato
> inventado como oficial. Se sustituye por el recuento de anuncios, que es un dato real y útil. Si
> algún día se obtiene el número del PDF, este contenedor es su sitio.

### Ejemplo de contenido

```text
Boletín de hoy
27 de agosto de 2026
48 anuncios
```

La cabecera debe sentirse editorial, no promocional. No usar fotografía, degradado ni ilustración de fondo.

## 14.4. Filtros rápidos

- Fila horizontal con scroll.
- Padding exterior: 16 dp.
- Separación: 8 dp.
- Margen superior e inferior: 16 dp.

Etiquetas: `Todo` y las **nueve secciones** con su nombre corto, en orden oficial:

- `Todo`.
- `Disposiciones`.
- `Personal`.
- `Contratación`.
- `Economía`.
- `Expropiación`.
- `Subvenciones`.
- `Anuncios`.
- `Judicial`.
- `Elecciones`.

> **Enmienda (29 de agosto de 2026, feature 003).** El documento listaba cuatro etiquetas
> iniciales. Se completan las nueve para que el filtro rápido y el panel lateral ofrezcan lo mismo:
> si un chip lleva a una sección y otra sección solo es alcanzable por el panel, la persona tiene
> que aprender dos vocabularios distintos para lo mismo.

## 14.5. Listado

- Fondo general `Background`.
- Margen horizontal de 16 dp.
- Tarjetas separadas 12 dp.
- Padding inferior suficiente para que la barra de navegación no tape contenido.

## 14.6. Apariencia al desplazarse

- La cabecera editorial sale de la pantalla de forma natural.
- La barra superior puede mantenerse fija.
- Los filtros pueden fijarse bajo la barra superior con fondo sólido y una línea inferior.
- No usar cambios bruscos de color.

---

## 15. Selector visual de fecha

## 15.1. Formato

Hoja inferior de gran altura o pantalla completa en dispositivos compactos.

## 15.2. Cabecera

- Título `Seleccionar fecha`.
- Mes y año.
- Flechas anterior y siguiente.
- Botón `Hoy` como acción de texto.

## 15.3. Calendario

- Siete columnas.
- Celdas de 44–48 dp.
- Día seleccionado dentro de un círculo `Primary`.
- Día actual con borde `Secondary`.
- Días sin edición en `TextMuted`.
- Días fuera del mes con baja opacidad.
- Indicador pequeño bajo los días con boletín disponible.

## 15.4. Pie

- Botón secundario `Cancelar`.
- Botón principal `Ver boletín`.
- Separación superior mediante divisor.

---

## 16. Panel de secciones

> **Enmienda (29 de agosto de 2026, feature 003).** Esto era una pantalla propia con barra azul y
> flecha Atrás; por decisión del propietario es un **panel lateral** (*navigation drawer*) que se
> abre desde el icono de menú de la barra superior de Inicio. El motivo es de uso: elegir sección
> es un filtro sobre lo que se está leyendo, no un viaje a otro sitio, y un panel lo deja claro y
> devuelve a la lista de un toque.
>
> No choca con el apartado 33, que elimina el «menú lateral con gran cabecera de autor»: lo que
> aquel eliminaba era la cabecera de autor, y este panel no la lleva. Empieza directamente por el
> campo de filtro.
>
> Se elimina además la tarjeta inferior de alertas y las campanas de cada fila, junto con el resto
> del trabajo de notificaciones. El apartado 16.5 queda en suspenso hasta entonces.

## 16.1. Estructura

1. Campo `Buscar una sección`.
2. Grupos expandibles.

## 16.2. Campo de búsqueda

- Margen de 16 dp.
- Ancho completo.
- Fondo blanco.

## 16.3. Fila de sección principal

- Altura mínima: 72 dp.
- Icono lineal de 28 dp, en el color de grupo de la sección.
- Número y nombre en `TitleMedium`, con la forma `1 · Disposiciones generales`.
- Chevron expandir/contraer, con rotación al desplegarse.
- Divisor inferior.

*(La campana de la zona derecha queda suspendida: ver la enmienda del encabezado del apartado 16.)*

## 16.4. Subsecciones expandidas

- Fondo `SurfaceSoft`.
- Radio de 12 dp.
- Margen horizontal de 16 dp.
- Sangría visual de 24 dp respecto a la sección principal.
- Punto azul o línea breve antes del texto.
- Divisores internos con margen.

*(La campana de la derecha queda suspendida: ver la enmienda del encabezado del apartado 16.)*

## 16.5. Tarjeta de alertas — SUSPENDIDA

> **Sin efecto mientras no existan las notificaciones (feature 003).** El contenido se conserva
> para cuando se retome.

- Fondo `PrimaryContainer`.
- Icono de campana dentro de círculo `Primary`.
- Título `Alertas personalizadas`.
- Descripción en dos líneas.
- Radio de 18 dp.
- Margen de 16 dp.

La tarjeta es informativa y visualmente secundaria respecto al listado.

---

## 17. Pantalla Buscar

## 17.1. Estado inicial

- Barra superior con título `Buscar`.
- Campo de búsqueda prominente.
- Botón de filtros a la derecha del campo o integrado.
- Bloque `Búsquedas recientes`.
- Bloque `Explorar por` con chips de sección.

## 17.2. Estado con resultados

- Campo de búsqueda fijo en la parte superior.
- Fila con número de resultados y ordenación.
- Chips de filtros activos.
- Tarjetas compactas.
- Coincidencias resaltadas con fondo amarillo muy suave, nunca con negrita excesiva.

## 17.3. Filtros avanzados

Hoja inferior con:

- Título `Filtrar resultados`.
- Grupos visuales separados.
- Selectores y chips.
- Botón de texto `Limpiar`.
- Botón principal `Aplicar filtros`.

## 17.4. Estado sin resultados

- Ilustración lineal de documento y lupa.
- Título `No encontramos resultados`.
- Texto explicativo breve.
- Botón secundario `Limpiar filtros`.
- Mucho espacio en blanco.

---

## 18. Pantalla Detalle de publicación

## 18.1. Barra superior

- Fondo `Primary`.
- Flecha Atrás.
- Escudo de 32 dp.
- Título `Detalle de publicación`.
- Guardar.
- Compartir.

En móviles estrechos puede omitirse el escudo para asegurar espacio suficiente.

## 18.2. Cabecera del documento

- Fondo `Surface`.
- Padding horizontal de 20 dp.
- Padding vertical de 24 dp.
- Etiqueta de sección en `LabelLarge`, color `Primary`.
- Título completo en `HeadlineLarge`.
- Organismo con icono institucional.
- Fecha con icono de calendario.
- Distintivo outlined `Documento oficial`.

> **Enmienda (30 de agosto de 2026, feature 004).** El título va en **`HeadlineSmall`**, no en
> `HeadlineLarge`. A 30 sp un título real del BOC —los hay de ciento treinta caracteres— llenaba seis
> líneas y dejaba la ficha de metadatos en una franja estrecha al pie de la pantalla. Se sigue
> mostrando **completo, sin recortar**.
>
> Además, la cabecera **se desplaza con el contenido** y las pestañas quedan fijas bajo la barra
> superior. Así, con cualquier título, el contenido acaba disponiendo de la pantalla entera; sin eso,
> encoger la letra solo aplaza el problema hasta el siguiente título más largo.

## 18.3. Jerarquía

La secuencia visual será:

1. Sección.
2. Título.
3. Organismo.
4. Fecha.
5. Distintivo oficial.
6. Pestañas.

## 18.4. Pestañas

- `Documento`.
- `Resumen IA`.
- `Preguntar`.

La pestaña `Resumen IA` incluye el icono `auto_awesome`.

> **Enmienda (30 de agosto de 2026, feature 004).** Son **dos** pestañas: `Documento` y `Resumen IA`.
> `Preguntar` deja de ser pestaña y pasa a ser **pantalla propia**, abierta desde el botón de la
> barra de acciones. Una conversación sobre un boletín de cuarenta páginas necesita la pantalla
> entera y su sitio en la pila de retroceso, que no es lo que da una pestaña junto a una ficha de
> metadatos.

## 18.5. Barra de acciones inferior

- Fondo `Surface`.
- Borde superior.
- Dos botones alineados horizontalmente en pantallas estándar.
- `Abrir PDF oficial` como botón principal.
- `Preguntar` como botón secundario.
- Padding de 12–16 dp más área segura.

En pantallas muy estrechas, los botones pueden apilarse.

> **Enmienda (30 de agosto de 2026, feature 004).** «Más área segura» no era un adorno: la barra
> aplica el margen de la barra de navegación **dentro** de su propia superficie, igual que hace
> `NavigationBar` de Material. Un `Scaffold` con barra inferior descarta su margen inferior y ancla
> la barra al borde crudo de la ventana, así que la barra es lo único que puede mantenerse por
> encima de los tres botones del sistema.

---

## 19. Pestaña Documento

## 19.1. Composición

- Fondo general `Background`.
- Tarjeta de metadatos en superficie blanca.
- Descripción principal con `BodyLarge`.
- Separadores entre bloques.
- Acción para abrir el PDF destacada.

## 19.2. Bloques visuales

- `Descripción`.
- `Organismo`.
- `Sección`.
- `Fecha de publicación`.
- `Referencia`.
- `Documento oficial`.

Las etiquetas usarán `LabelMedium` y los valores `BodyLarge`.

> **Enmienda (30 de agosto de 2026, feature 004).** La pestaña muestra la ficha de metadatos y,
> debajo, la **primera página del PDF** como previsualización. No hay bloques de texto extraído
> porque del RSS no llega texto: llega el enlace a un documento. El apartado 19.3 describe cómo
> presentar ese texto y se conserva para cuando exista la extracción que lo produzca, pero **no se
> implementa** en esta feature.

## 19.3. Modo lectura

Si se muestra texto extraído:

- Fondo blanco o sepia muy suave.
- Ancho de lectura controlado.
- Cuerpo mínimo de 16 sp.
- Interlineado mínimo de 1,5.
- Encabezados claramente diferenciados.
- Barra superior compacta con controles de tamaño de texto.

---

## 20. Pestaña Resumen IA

## 20.1. Identidad visual

La IA debe reconocerse sin dominar el diseño.

Elementos identificativos:

- Icono `auto_awesome`.
- Etiqueta `Resumen generado por IA`.
- Color `AiAccent`.
- Fondo `AiContainer`.

## 20.2. Tarjeta principal

- Fondo violeta muy claro.
- Radio de 18 dp.
- Padding de 20 dp.
- Icono dentro de círculo de 48 dp.
- Título `TitleLarge`.
- Lista de puntos con viñetas azules.
- Texto `BodyLarge`.
- Separación de 12–16 dp entre puntos.

## 20.3. Aviso

Debajo de la tarjeta:

- Icono de advertencia outlined en rojo.
- Texto `Comprueba siempre el texto oficial`.
- Fondo transparente.
- No usar un gran bloque rojo.

## 20.4. Fuentes

- Cabecera `Fuentes del resumen`.
- Tarjetas o chips outlined.
- Icono de documento.
- Texto `Página 1`, `Página 2`, etc.
- Altura mínima: 48 dp.
- Color principal azul, no violeta.

## 20.5. Estado visual de carga

- Esqueleto de título y tres líneas.
- Icono IA estático.
- Texto breve debajo del esqueleto.
- No utilizar una animación futurista o partículas.

---

## 21. Pantalla Pregunta al BOC

> **Nota (30 de agosto de 2026, feature 004).** La pantalla **existe ya**, como marcador de posición:
> barra superior con Atrás y el título, y de contenido el aviso de que la función llegará
> próximamente conservando el icono y la etiqueta de IA del apartado 20.1. Se llega desde el botón
> `Preguntar` de la barra de acciones del detalle.
>
> Lo que este apartado describe —tarjeta de contexto, banner, conversación— es lo que la funcionalidad
> de IA rellenará. La estructura queda hecha, que era el encargo.

## 21.1. Barra superior

- Fondo `Primary`.
- Flecha Atrás.
- Escudo pequeño.
- Título `Pregunta al BOC`.
- Menú de opciones.

## 21.2. Tarjeta de contexto

- Fondo `Surface`.
- Borde `Divider`.
- Icono PDF o documento a la izquierda.
- Título de la publicación, máximo tres líneas.
- Fecha debajo.
- Guardar opcional a la derecha.
- Radio de 16 dp.
- Margen de 16 dp.

## 21.3. Banner informativo

- Fondo `PrimaryContainer`.
- Icono de información.
- Texto `Las respuestas se basan solo en este documento`.
- Radio de 10–12 dp.
- Sin sombra.

## 21.4. Preguntas sugeridas

- Chips outlined en fila horizontal desplazable.
- Altura de 40 dp.
- Texto `BodyMedium` o `LabelLarge`.
- Separación de 8 dp.
- Fondo blanco.

## 21.5. Burbuja del usuario

- Alineada a la derecha.
- Fondo `Primary`.
- Texto blanco.
- Radio general de 18 dp.
- Esquina inferior derecha de 6 dp.
- Ancho máximo del 78 % de la pantalla.
- Padding de 14 dp.

## 21.6. Respuesta de IA

No utilizar una burbuja de chat genérica completa.

Composición:

- Avatar circular pequeño con texto `BOC` e indicador IA.
- Tarjeta `SurfaceSoft` alineada a la izquierda.
- Texto principal `BodyLarge`.
- Bloque interno `Fuentes` separado por divisor.
- Filas de citas con icono, página, apartado y chevron.
- Ancho máximo del 86 %.

## 21.7. Campo de escritura

- Situado en la parte inferior.
- Fondo `Surface`.
- Borde `Outline`.
- Radio de 28 dp.
- Altura mínima de 56 dp.
- Placeholder `Escribe una pregunta…`.
- Botón Enviar circular de 44 dp dentro del campo.
- Padding inferior adaptado al teclado y área segura.

## 21.8. Enlace al documento

Debajo o encima del campo:

- Icono de libro o PDF.
- Texto `Ver PDF oficial`.
- Botón de texto azul.

---

## 22. Pantalla Guardados

## 22.1. Cabecera

- Barra superior blanca o azul, consistente con Buscar y Avisos.
- Título `Guardados`.
- Acción de ordenar.
- Acción de selección múltiple opcional.

## 22.2. Contenido

- Chips de clasificación: `Todos`, `Sin conexión`, `Con resumen`.
- Tarjetas compactas.
- Indicador de descarga mediante icono pequeño.
- Fecha de guardado como metadato secundario si se muestra.

## 22.3. Estado vacío

- Icono grande de marcador outlined.
- Título `Aún no has guardado publicaciones`.
- Texto de apoyo.
- Botón secundario `Explorar el BOC`.

> **Enmienda (30 de agosto de 2026, feature 005).** La pantalla se construye con la cabecera del
> 22.1 —barra `Primary` y el título `Guardados`—, la lista y el estado vacío del 22.3 tal como están
> escritos. Lo que **no** entra, y por qué:
>
> - **La acción de ordenar.** La lista tiene un solo orden, el instante de guardado con lo último
>   arriba, así que un menú de ordenación sería un menú de una opción.
> - **Los chips `Sin conexión` y `Con resumen`.** El primero necesita que guardar conserve el
>   documento, que es una feature propia y todavía no existe; el segundo, el resumen de inteligencia
>   artificial, que sigue siendo un marcador de posición. Un chip que no puede filtrar nada es peor
>   que su ausencia. El chip `Todos` sin los otros dos no filtra nada tampoco.
> - **El indicador de descarga.** Depende de lo mismo: hoy ningún elemento de la lista está
>   descargado de forma permanente.
> - **La fecha de guardado como metadato visible.** El instante se almacena —es lo que ordena la
>   lista— pero no se muestra: la tarjeta ya lleva la fecha de publicación, y dos fechas juntas
>   confunden más de lo que informan.
> - **La selección múltiple**, que el propio apartado 22.1 ya marcaba como opcional.
> - **La tarjeta compacta**, aplazada en el apartado 12.2 con su motivo.
>
> Y una advertencia que conviene no perder: **guardar marca, no descarga**. El documento de una
> publicación guardada sigue viviendo en la caché y puede retirarse de ella. Leer sin conexión es una
> feature futura con sus propias decisiones que tomar.

---

## 23. Pantalla Avisos

## 23.1. Cabecera

- Título `Avisos`.
- Acción de ajustes.
- Acción `Marcar como leído` dentro del menú.

## 23.2. Agrupación

- Separadores por fecha: `Hoy`, `Ayer`, `Esta semana`.
- Tarjetas o filas sobre fondo blanco.
- Punto azul para aviso no leído.
- Icono de sección.
- Título de publicación.
- Nombre corto de la alerta en un chip pequeño.
- Hora o fecha alineada a la derecha.

## 23.3. Reglas visuales de alerta

- Campana rellena azul para activa.
- Campana outlined gris para inactiva.
- Interruptor estándar de Material 3.
- Las palabras clave se muestran como chips.
- Los bloques de configuración se separan mediante tarjetas discretas.

## 23.4. Estado vacío

- Ilustración lineal de campana.
- Título `No tienes avisos nuevos`.
- Texto secundario.
- Botón `Crear una alerta` si se desea mostrar.

---

## 24. Visor del PDF

## 24.1. Barra superior

- Fondo `Primary` o negro azulado para maximizar contraste.
- Flecha Atrás.
- Título abreviado del documento.
- Buscar en documento.
- Compartir.
- Más opciones.

> **Enmienda (30 de agosto de 2026, feature 004).** La barra lleva **tres** controles: Atrás, el
> título abreviado y compartir. Buscar dentro del documento queda explícitamente fuera del alcance
> de la feature, y un menú de «Más opciones» sin nada que ofrecer sería un botón que no hace nada.
> Ambos vuelven en cuanto tengan contenido que justificarlos.
>
> El título abreviado es el del anuncio **sin el organismo**, que ya va implícito en el propio
> documento y dejaría sin sitio a la parte que dice de qué trata.

## 24.2. Zona de documento

- Fondo gris neutro `#D9DEE2` en modo claro.
- Página blanca con sombra suave.
- Separación de 12 dp entre páginas.
- Indicador flotante de página: `2 / 6`.

## 24.3. Controles

- Aparecen al tocar la pantalla.
- Desaparecen durante lectura prolongada.
- Zoom mediante gestos estándar.
- Barra inferior opcional para miniaturas, página y modo lectura.

---

## 25. Pantalla Ajustes

## 25.1. Estructura visual

- Barra superior azul con Atrás y título `Ajustes`.
- Grupos separados mediante encabezados pequeños.
- Filas de 56–64 dp.
- Icono a la izquierda.
- Título y descripción opcional.
- Control o chevron a la derecha.

## 25.2. Grupos visuales

- `Apariencia`.
- `Lectura`.
- `Notificaciones`.
- `Privacidad`.
- `Acerca de`.

Este documento solo define su presentación; el contenido exacto puede adaptarse posteriormente.

## 25.3. Acerca de

- Escudo pequeño.
- Nombre `BOC Cantabria`.
- Versión.
- Texto `Diseñada y desarrollada por José Ramón Blanco Gutiérrez`.
- Enlaces en color `Secondary`.
- Sin retrato ni elementos decorativos adicionales.

---

## 26. Estados visuales globales

## 26.1. Carga inicial

- Utilizar esqueletos con formas similares al contenido final.
- Color base `SurfaceStrong`.
- Brillo muy suave y lento.
- No mostrar más de cinco tarjetas esqueleto.
- Evitar indicadores giratorios grandes en el centro.

## 26.2. Actualización

- Indicador lineal fino bajo la barra superior o gesto de refresco estándar.
- Mantener visible el contenido existente.
- No introducir un botón flotante central permanente.

## 26.3. Estado vacío

Composición centrada verticalmente en el área disponible:

- Ilustración lineal de 96–120 dp.
- Título `TitleLarge`.
- Texto `BodyMedium` con ancho limitado.
- Acción secundaria.

## 26.4. Error

- Icono outlined en `Error`.
- Título claro.
- Explicación breve en `TextSecondary`.
- Botón `Reintentar`.
- No usar pantallas completamente rojas.

## 26.5. Sin conexión

- Banner superior con icono `cloud_off`.
- Fondo gris azulado o naranja muy suave.
- Texto breve.
- El banner no debe ocultar el contenido.

## 26.6. Snackbar

- Fondo gris azulado muy oscuro.
- Texto blanco.
- Radio de 8 dp.
- Acción en azul claro.
- Margen de 16 dp sobre la barra inferior.
- Duración visual estándar.

## 26.7. Diálogo de confirmación

- Ancho máximo de 320–360 dp en teléfono.
- Radio de 24 dp.
- Título `HeadlineSmall`.
- Texto `BodyMedium`.
- Acciones alineadas a la derecha.
- Acción destructiva en `Error`.

---

## 27. Hojas inferiores

## 27.1. Apariencia

- Fondo `Surface`.
- Esquinas superiores de 28 dp.
- Tirador de 32 × 4 dp.
- Padding horizontal de 20 dp.
- Padding superior de 12 dp.
- Título `HeadlineSmall`.
- Área inferior adaptada a la navegación del sistema.

## 27.2. Uso visual

Adecuadas para:

- Filtros.
- Ordenación.
- Selector de fecha compacto.
- Opciones de compartir.
- Ajustes rápidos de lectura.

No introducir una cadena de varias hojas inferiores consecutivas.

---

## 28. Animaciones y transiciones

## 28.1. Principios

- Breves.
- Naturales.
- Funcionales.
- Consistentes.
- Reducibles desde la preferencia del sistema.

## 28.2. Duraciones

| Animación | Duración |
| --- | ---: |
| Cambio de estado de botón | 100–150 ms |
| Selección de chip | 150 ms |
| Expansión de sección | 200–250 ms |
| Cambio de pestaña | 200–250 ms |
| Entrada de hoja inferior | 250–300 ms |
| Aparición de snackbar | 200 ms |

## 28.3. Transiciones concretas

- Tarjeta a detalle: transición estándar con fundido y desplazamiento leve.
- Guardar: cambio de icono outlined a relleno con escala muy pequeña.
- Activar alerta: campana rellena con respuesta háptica ligera.
- Expandir sección: animación de altura y rotación del chevron.
- Cambio de pestaña: desplazamiento del indicador inferior.
- Mensaje de chat: aparición con fundido y desplazamiento vertical de 8 dp.

## 28.4. Animaciones que deben evitarse

- Partículas alrededor del icono de IA.
- Logotipo animado durante varios segundos.
- Rebotes grandes.
- Rotaciones decorativas.
- Parallax en cabeceras.
- Transiciones que impidan interactuar rápidamente.

---

## 29. Diseño adaptable

## 29.1. Teléfono compacto

Anchura inferior a 360 dp:

- Margen horizontal de 12–16 dp.
- Títulos ligeramente reducidos dentro de la escala definida.
- Botones inferiores apilados si no caben.
- Ocultar el escudo en barras secundarias si falta espacio.
- Chips con desplazamiento horizontal.

## 29.2. Teléfono estándar

Anchura aproximada de 360–599 dp:

- Composición base del diseño.
- Margen de 16–20 dp.
- Barra inferior de tres destinos *(ver la enmienda del apartado 10.1)*.
- Una columna de tarjetas.

## 29.3. Tableta

Anchura igual o superior a 600 dp:

- Contenido centrado con ancho máximo.
- Navegación lateral compacta en lugar de barra inferior cuando resulte apropiado.
- Listado y detalle en dos paneles en orientación horizontal.
- Dos columnas de tarjetas solo cuando conserven una anchura cómoda.
- Texto de lectura limitado a 680 dp.
- Diálogos centrados, no a pantalla completa.

## 29.4. Orientación horizontal

- Mantener cabeceras más compactas.
- Reducir la altura de la cabecera editorial.
- En chat, reservar un ancho máximo al contenido.
- En PDF, priorizar el área del documento.

---

## 30. Áreas seguras y barras del sistema

- Respetar recortes, cámara y bordes redondeados.
- El contenido no debe quedar bajo la barra de navegación.
- En pantalla de arranque, el fondo se extiende detrás de las barras.
- En pantallas claras, usar iconos oscuros en la barra de estado.
- En cabeceras azules, usar iconos blancos.
- El campo de chat debe desplazarse correctamente sobre el teclado.

---

## 31. Accesibilidad visual

## 31.1. Contraste

- Texto normal: mínimo 4,5:1.
- Texto grande: mínimo 3:1.
- Controles y bordes esenciales: mínimo 3:1.
- Validar la paleta en modo claro y oscuro.

## 31.2. Tamaños

- Texto principal mínimo: 14 sp.
- Lectura: 16 sp.
- Área táctil: 48 × 48 dp.
- Separación suficiente entre acciones próximas.

## 31.3. Escalado de texto

El diseño debe conservar su jerarquía hasta un escalado del 200 %:

- Permitir que las barras aumenten de altura.
- Permitir títulos multilínea.
- Apilar botones cuando sea necesario.
- Evitar alturas fijas en tarjetas de texto.
- No recortar etiquetas importantes.

## 31.4. Color

- Toda categoría tendrá texto además de color.
- Todo estado seleccionado tendrá forma, icono o peso además de color.
- Los errores incluirán icono y mensaje.
- El contenido de IA incluirá etiqueta e icono, no solo violeta.

---

## 32. Microcopy visible en los mockups

### Inicio

```text
BOC Cantabria
Boletín de hoy
27 de agosto de 2026
48 anuncios
Todo
Disposiciones
Personal
Contratación
```

### Detalle

```text
Detalle de publicación
Documento oficial
Documento
Resumen IA
Preguntar
Abrir PDF oficial
```

### Resumen

```text
Resumen generado por IA
Comprueba siempre el texto oficial
Fuentes del resumen
Página 1
Página 2
```

### Chat

```text
Pregunta al BOC
Las respuestas se basan solo en este documento
Escribe una pregunta…
Fuentes
Ver PDF oficial
```

### Secciones

```text
Secciones
Buscar una sección
Alertas personalizadas
```

---

## 33. Elementos que se eliminan del diseño actual

- Tarjetas turquesas de gran tamaño.
- Barra inferior roja.
- Botón flotante circular central para actualizar.
- Estrellas blancas de favoritos demasiado grandes.
- Cabeceras con múltiples logotipos pequeños.
- Menú lateral con gran cabecera de autor.
- Retrato y usuario de Twitter en la interfaz principal.
- Botón `Descargar PDF` repetido como acción dominante en cada tarjeta.
- Exceso de radios redondeados.
- Diferentes estilos de iconos mezclados.
- Texto con poca separación vertical.
- Colores de baja coherencia institucional.

---

## 34. Reglas de consistencia

- El escudo utiliza siempre el mismo archivo.
- El azul principal no cambia entre pantallas.
- Guardar utiliza siempre el icono de marcador.
- Avisos utiliza siempre la campana.
- IA utiliza siempre `auto_awesome` y la etiqueta correspondiente.
- PDF oficial utiliza siempre icono de documento o PDF.
- Las tarjetas comparten radio, padding y elevación.
- Las barras superiores secundarias comparten altura y color.
- Las acciones principales aparecen en azul institucional.
- Los mensajes de error no utilizan estilos diferentes en cada pantalla.
- Todos los campos comparten altura, borde y radio.

---

## 35. Tokens de diseño resumidos

```text
Color principal:        #063B5C
Color secundario:       #087EA4
Fondo:                  #F6F8FA
Superficie:             #FFFFFF
Texto principal:        #122B3A
Texto secundario:       #536873
Acento oficial:         #C62828
Acento IA:              #6650A4

Margen móvil:           16 dp
Retícula base:          4 dp
Padding de tarjeta:     16 dp
Separación de tarjetas: 12 dp
Radio de tarjeta:       14 dp
Radio de botón:         12 dp
Altura de botón:        52 dp
Altura de campo:        56 dp
Área táctil mínima:     48 × 48 dp
Icono estándar:         24 dp
```

---

## 36. Checklist visual por pantalla

### Portada

- [ ] Fondo azul institucional.
- [ ] Escudo oficial centrado.
- [ ] BOC con gran jerarquía.
- [ ] Autoría discreta.
- [ ] Sin retrato ni redes sociales.

### Inicio

- [ ] Barra superior blanca y compacta, con menú al inicio e Información al final.
- [ ] Cabecera editorial azul.
- [ ] Fecha y recuento de anuncios visibles.
- [ ] Chips de filtro coherentes con el panel de secciones.
- [ ] Tarjetas blancas con indicador de sección **acompañado de texto**.
- [ ] Navegación inferior de tres destinos.

### Secciones (panel lateral)

- [ ] Campo de búsqueda.
- [ ] Filas expandibles.
- [ ] Iconografía uniforme.
- [ ] Subsecciones agrupadas en fondo suave.

### Buscar

- [ ] Campo prominente.
- [ ] Filtros visibles.
- [ ] Resultados con tarjetas compactas.
- [ ] Estado vacío diseñado.

### Detalle

- [ ] Sección, título, organismo y fecha jerarquizados.
- [ ] Distintivo `Documento oficial`.
- [ ] Tres pestañas.
- [ ] Barra inferior de acciones.
- [ ] Título completo sin truncar.

### Resumen IA

- [ ] Etiqueta e icono de IA.
- [ ] Fondo violeta muy suave.
- [ ] Aviso de comprobación.
- [ ] Fuentes claramente visibles.
- [ ] PDF oficial como acción destacada.

### Chat

- [ ] Documento activo visible.
- [ ] Banner informativo.
- [ ] Chips de preguntas.
- [ ] Mensajes diferenciados.
- [ ] Fuentes integradas en la respuesta.
- [ ] Campo de escritura adaptado al teclado.

### Guardados y Avisos

- [ ] Tarjetas compactas. *(Guardados usa la estándar; ver la enmienda del apartado 12.2.)*
- [x] Estados vacíos. *(Guardados, feature 005. Avisos sigue pendiente.)*
- [ ] Filtros mediante chips. *(Aplazados; ver la enmienda del apartado 22.)*
- [x] Iconos activos e inactivos coherentes. *(Marcador relleno y contorneado, feature 005.)*

---

## 37. Criterios visuales de aceptación

El nuevo diseño se considerará correctamente implementado cuando:

- Toda la aplicación utiliza la misma paleta y escala tipográfica.
- No aparecen los antiguos turquesa y rojo como grandes superficies.
- Las tarjetas presentan una jerarquía clara entre organismo, título y metadatos.
- La navegación inferior contiene exactamente tres destinos *(ver la enmienda del apartado 10.1)*.
- Las secciones se presentan en un panel lateral sobrio, sin la cabecera de autor del diseño
  antiguo *(ver la enmienda del apartado 16)*.
- El contenido oficial y el contenido de IA se distinguen visualmente.
- El PDF oficial mantiene la mayor jerarquía de acción en el detalle.
- Los títulos largos no se recortan en la pantalla de detalle.
- Todos los controles respetan un área mínima de 48 dp.
- La interfaz funciona visualmente con texto ampliado.
- La aplicación tiene un único aspecto, el claro, y no cambia con el tema del sistema.
- Los estados de carga, vacío, error y sin conexión están definidos.
- Las animaciones son breves y discretas.
- El escudo utilizado es el recurso oficial.
- La aplicación conserva un estilo institucional, limpio y contemporáneo.

---

## 38. Resultado visual esperado

La nueva BOC Cantabria debe percibirse como una aplicación editorial institucional moderna:

- Clara como un lector de noticias.
- Sobria como un documento oficial.
- Fluida como una aplicación Android actual.
- Accesible para lectura prolongada.
- Coherente en todas sus pantallas.

La modernización no debe ocultar la identidad del BOC. Debe presentarla con más orden, espacio, legibilidad y confianza.

La interfaz final utilizará el escudo, el azul institucional y una composición editorial limpia para transformar la consulta del boletín en una experiencia sencilla y visualmente elegante.
