# Feature Specification: Pantalla «Acerca de»

**Feature Branch**: `008-acerca-de`

**Created**: 3 de septiembre de 2026

**Status**: Aprobada por el propietario

**Input**: El botón Información de la barra superior debe abrir una pantalla «Acerca de» basada en
`Datos_modelo/screen_info.png`, con la fotografía `Datos_modelo/yoandroid.png`, enlace al perfil de
LinkedIn y enlace al repositorio abierto de la aplicación en GitHub.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conocer la aplicación y a su autor (Priority: P1)

Una persona abre Información desde el boletín y encuentra una pantalla clara, coherente con el
resto de BOCantabria, que identifica al autor, explica el propósito del proyecto y deja claro que la
aplicación es independiente y no oficial.

**Why this priority**: Es el propósito principal del botón que hoy no hace nada y da contexto y
transparencia a la aplicación.

**Independent Test**: Abrir Información, leer todo el contenido desplazándose hasta el final y
volver al boletín con la flecha Atrás.

**Acceptance Scenarios**:

1. **Given** que se muestra Inicio, **When** se pulsa Información, **Then** aparece «Acerca de» sin
   barra inferior y con Atrás.
2. **Given** que se muestra «Acerca de», **When** se recorre la pantalla, **Then** aparecen el
   retrato, el nombre «José Ramón Blanco», los cargos, la presentación, el ámbito de trabajo, el
   aviso de independencia, la fuente del contenido y la versión instalada.
3. **Given** que se muestra «Acerca de», **When** se pulsa Atrás, **Then** se vuelve a Inicio en el
   mismo estado y «Acerca de» deja de estar en la pila.

---

### User Story 2 - Abrir los perfiles externos (Priority: P2)

Una persona puede visitar tanto el perfil profesional de LinkedIn como el código fuente de la
aplicación sin copiar direcciones manualmente.

**Why this priority**: Convierte las afirmaciones de trayectoria profesional y código abierto en
destinos comprobables.

**Independent Test**: Pulsar cada botón y comprobar que Android entrega la dirección correcta a una
aplicación compatible o al navegador.

**Acceptance Scenarios**:

1. **Given** que LinkedIn puede gestionar enlaces web, **When** se pulsa «Ver perfil en LinkedIn»,
   **Then** se abre `https://www.linkedin.com/in/jr-blanco/` en LinkedIn o en el navegador.
2. **Given** que hay un navegador disponible, **When** se pulsa «Ver proyecto en GitHub», **Then**
   se abre `https://github.com/jrcosio/BOCantabria_v2.git`.
3. **Given** que ningún programa puede abrir un enlace, **When** se pulsa su botón, **Then** la
   aplicación permanece en «Acerca de» y explica el fallo sin cerrarse.

### Edge Cases

- En una pantalla baja, todo el contenido sigue siendo alcanzable mediante desplazamiento.
- En pantallas grandes, el contenido conserva un ancho de lectura cómodo y queda centrado.
- Volver desde la aplicación externa conserva «Acerca de» y su posición razonablemente.
- La ausencia de una aplicación asociada al enlace no provoca un cierre inesperado.
- La fotografía mantiene su proporción y no desplaza el texto cuando cambia la escala de fuente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST abrir una pantalla real titulada `Acerca de` desde Información.
- **FR-002**: La pantalla MUST ser un destino secundario con Atrás y sin barra inferior.
- **FR-003**: La pantalla MUST mostrar la fotografía facilitada por el propietario con recorte
  circular y sin deformación.
- **FR-004**: La cabecera MUST mostrar literalmente `José Ramón Blanco`,
  `Tech Lead I+D · AI Engineer` y `AI-Driven Developer`.
- **FR-005**: La pantalla MUST reproducir literalmente el contenido de los bloques `Tecnología con
  propósito`, `Compartir, aprender y aportar` y `Mi ámbito de trabajo` de la referencia.
- **FR-006**: La pantalla MUST incluir el aviso `Aplicación independiente y no oficial. No está
  vinculada al Gobierno de Cantabria.` y citar como fuente al Boletín Oficial de Cantabria.
- **FR-007**: La pantalla MUST mostrar el nombre de versión realmente instalado, no un literal
  duplicado en la interfaz.
- **FR-008**: La pantalla MUST ofrecer un botón ancho `Ver perfil en LinkedIn`.
- **FR-009**: La pantalla MUST ofrecer debajo un segundo botón ancho `Ver proyecto en GitHub`.
- **FR-010**: Los botones MUST usar exactamente las URL proporcionadas por el propietario.
- **FR-011**: Android MUST poder resolver cada URL mediante una aplicación asociada o navegador,
  sin incorporar un navegador dentro de BOCantabria.
- **FR-012**: Un fallo al abrir un enlace MUST comunicarse sin abandonar ni cerrar la pantalla.
- **FR-013**: Todo el contenido MUST ser desplazable y legible con escalas de fuente de
  accesibilidad.
- **FR-014**: Los controles MUST tener nombres accesibles y los adornos no MUST duplicar la lectura.
- **FR-015**: La apariencia MUST usar el único tema claro y los tokens del sistema de diseño.
- **FR-016**: La aplicación MUST registrar la vista de pantalla y los clics de enlace sin incluir
  datos personales.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde Inicio se llega a «Acerca de» con una sola pulsación y se vuelve con una sola
  pulsación.
- **SC-002**: El 100 % del contenido de la referencia y los dos enlaces queda accesible en el ancho
  de teléfono mínimo soportado sin recortes horizontales.
- **SC-003**: Ambos botones entregan la URL acordada en el 100 % de las pruebas automatizadas.
- **SC-004**: Una persona puede identificar autor, propósito, independencia, fuente y versión en
  menos de un minuto.
- **SC-005**: Si no hay manejador de enlaces, el 100 % de los intentos permanece dentro de la
  aplicación y muestra una explicación.
- **SC-006**: Las cuatro puertas de calidad del proyecto terminan en verde.

## Assumptions

- La maqueta fija el texto definitivo y prevalece sobre la anterior sección 25.3 del documento de
  diseño, que prohibía el retrato.
- `José Ramón Blanco` es el nombre deliberadamente abreviado para esta pantalla; no se sustituye por
  el nombre completo del arranque.
- LinkedIn se presenta como acción principal rellena y GitHub como acción secundaria outlined.
- La versión actual no cambia por esta feature; solo se lee dinámicamente.
- No hay datos persistentes, llamadas de red propias, WebView, permisos ni dependencias nuevas.
