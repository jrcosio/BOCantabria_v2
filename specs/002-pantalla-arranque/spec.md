# Feature Specification: Pantalla de arranque y sistema de diseño institucional

**Feature Branch**: `002-pantalla-arranque`

**Created**: 2026-08-28

**Status**: Draft

**Input**: User description: "La primera feature será la screen de inicio, donde se cargan los datos al iniciar la app; cuando cargue y compruebe todo, pasa a Home. Los colores y el estilo están definidos en el documento de especificaciones de diseño, con una imagen de referencia y el escudo de Cantabria en XML. El texto de autoría es «Diseñada y desarrollada por» y, en otro color, «José Ramón Blanco Gutiérrez». La aplicación solo debe poder verse en vertical."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Abrir la aplicación y entrar sin fricción (Priority: P1)

Una persona toca el icono de BOCantabria. Aparece de inmediato una portada azul con el escudo de
Cantabria y las siglas del boletín, sin ningún destello blanco previo. Mientras la mira, la
aplicación prepara por su cuenta todo lo que necesita para funcionar. En cuanto está lista, la
portada da paso al contenido. La persona no ha tenido que tocar nada.

**Why this priority**: es el camino que recorre el 100 % de las aperturas de la aplicación. Si
falla, no hay producto. Es además la primera impresión: determina si la aplicación se percibe como
oficial y cuidada o como improvisada.

**Independent Test**: instalar la aplicación en un dispositivo limpio y abrirla. Debe mostrarse la
portada y, sin intervención alguna, dar paso al contenido principal.

**Acceptance Scenarios**:

1. **Given** la aplicación instalada y con conexión, **When** la persona la abre, **Then** ve la
   portada institucional y a continuación, sin tocar nada, el contenido principal.
2. **Given** el arranque en curso, **When** la preparación termina antes de que la portada haya
   sido legible, **Then** la portada permanece visible el tiempo mínimo necesario para poder
   leerse, en lugar de parpadear.
3. **Given** que la persona ya está en el contenido principal, **When** pulsa el botón Atrás del
   sistema, **Then** la aplicación se cierra en lugar de volver a la portada.
4. **Given** la aplicación abriéndose, **When** se observa la transición desde el icono del sistema,
   **Then** no aparece ningún destello blanco ni salto de color.

---

### User Story 2 - Enterarse de que algo va mal, y poder seguir (Priority: P1)

Una persona abre la aplicación en el metro, sin cobertura. En lugar de quedarse mirando un
indicador que gira eternamente, ve un mensaje breve que le explica qué ocurre y dos salidas:
reintentar, o entrar de todas formas para consultar lo que ya tenga guardado. Elija lo que elija,
nunca se queda atrapada en la portada.

**Why this priority**: sin esto, cualquier fallo de red convierte la aplicación en una pantalla
muerta. Es tan crítico como el camino feliz, porque es el que hace que la aplicación sea confiable.

**Independent Test**: activar el modo avión y abrir la aplicación. Debe aparecer el mensaje con sus
dos acciones; «continuar» debe llevar al contenido principal.

**Acceptance Scenarios**:

1. **Given** el dispositivo sin conexión, **When** la persona abre la aplicación, **Then** ve un
   mensaje comprensible y las acciones de reintentar y de continuar sin conexión.
2. **Given** el mensaje de error visible, **When** la persona recupera la conexión y pulsa
   reintentar, **Then** la preparación se completa y pasa al contenido principal.
3. **Given** el mensaje de error visible, **When** la persona pulsa continuar sin conexión,
   **Then** entra al contenido principal, que mostrará su propio estado.
4. **Given** la preparación en curso, **When** la persona pulsa reintentar repetidamente,
   **Then** no se lanzan preparaciones simultáneas.
5. **Given** que la versión instalada ha dejado de estar soportada, **When** la persona abre la
   aplicación, **Then** se le informa de que debe actualizar, y esa situación no se puede saltar
   con «continuar».
6. **Given** que el servicio está en mantenimiento, **When** la persona abre la aplicación,
   **Then** ve el mensaje de mantenimiento que se haya publicado.

---

### User Story 3 - Una aplicación que se ve institucional y siempre igual (Priority: P2)

Una persona que consulta el boletín reconoce de inmediato que está ante información oficial: el
azul institucional, el escudo y una tipografía sobria y ordenada. Ese aspecto es idéntico en su
móvil y en el de cualquier otra persona, con independencia del fondo de pantalla que cada cual
tenga configurado. Al girar el teléfono, la aplicación no rota: se lee siempre en vertical.

**Why this priority**: la identidad institucional es un requisito del producto, no un adorno.
Hoy la aplicación se pinta con colores tomados del fondo de pantalla del usuario, lo que resulta
incompatible con una publicación oficial. Va después de las historias 1 y 2 porque el arranque debe
funcionar antes de poder juzgar cómo se ve.

**Independent Test**: cambiar el fondo de pantalla del dispositivo por uno de color llamativo y
abrir la aplicación: los colores no deben cambiar. Girar el dispositivo: la interfaz permanece
vertical.

**Acceptance Scenarios**:

1. **Given** un dispositivo con un fondo de pantalla de color intenso, **When** la persona abre la
   aplicación, **Then** los colores de la interfaz son los institucionales, sin verse alterados.
2. **Given** la aplicación abierta en un teléfono, **When** la persona lo gira, **Then** la
   interfaz permanece en vertical.
3. **Given** el dispositivo en modo oscuro, **When** la persona abre la aplicación, **Then** la
   portada conserva el azul institucional y el resto de la aplicación adopta la paleta oscura.
4. **Given** el tamaño de texto del sistema al 200 %, **When** la persona abre la aplicación,
   **Then** la portada conserva su jerarquía y ningún texto queda recortado.

---

### Edge Cases

- **Preparación muy rápida**: en un dispositivo veloz y con buena red, la preparación puede
  terminar en milisegundos. La portada debe permanecer visible un tiempo mínimo legible; un
  parpadeo se percibe como un fallo.
- **Preparación muy lenta**: si la red responde con extrema lentitud, la espera no puede ser
  indefinida. Pasado un límite razonable se trata como un fallo y se ofrecen las mismas salidas.
- **Sin configuración publicada**: la primera vez, el servicio de configuración remota puede no
  tener ningún valor definido. La aplicación debe arrancar igualmente con valores por defecto
  propios, nunca quedarse bloqueada.
- **Giro durante el arranque**: girar el dispositivo mientras se prepara no debe reiniciar la
  preparación ni perderla.
- **Salir y volver**: si la persona sale de la aplicación durante el arranque y vuelve, no debe
  encontrarse con una preparación duplicada ni con un estado incoherente.
- **Versión obsoleta y sin conexión a la vez**: si no hay forma de comprobar la versión, la falta
  de conexión manda y se ofrecen sus salidas habituales.

## Requirements *(mandatory)*

### Functional Requirements

**Arranque**

- **FR-001**: La aplicación MUST mostrar una pantalla de arranque como primera pantalla, antes que
  cualquier otra.
- **FR-002**: La transición desde el arranque del sistema hasta la pantalla de arranque de la
  aplicación MUST ser continua, sin destellos ni cambios bruscos de color.
- **FR-003**: La pantalla de arranque MUST preparar la aplicación sin intervención de la persona
  usuaria, comprobando la disponibilidad de conexión, obteniendo la configuración remota del
  servicio y verificando que la versión instalada sigue estando soportada.
- **FR-004**: Cuando la preparación termina correctamente, la aplicación MUST pasar al contenido
  principal automáticamente.
- **FR-005**: La pantalla de arranque MUST permanecer visible un tiempo mínimo suficiente para
  poder leerse, aunque la preparación termine antes.
- **FR-006**: La preparación MUST tener un límite de espera; superarlo se trata como fallo.
- **FR-007**: La pantalla de arranque MUST NOT quedar accesible mediante el gesto o el botón de
  retroceso una vez se ha pasado al contenido principal.
- **FR-008**: La preparación MUST sobrevivir a un cambio de configuración del dispositivo sin
  reiniciarse ni duplicarse.

**Estados y salidas**

- **FR-009**: La pantalla de arranque MUST representar de forma explícita y mutuamente excluyente
  los estados: preparando, listo, error recuperable y acceso bloqueado.
- **FR-010**: Ante un error recuperable, la pantalla MUST ofrecer reintentar y continuar sin
  conexión.
- **FR-011**: Reintentar mientras hay una preparación en curso MUST NOT lanzar una segunda.
- **FR-012**: Cuando la versión instalada sea inferior a la mínima soportada, la aplicación MUST
  informar de ello y MUST NOT permitir continuar al contenido principal.
- **FR-013**: Cuando el servicio publique un mensaje de mantenimiento, la aplicación MUST
  mostrarlo.
- **FR-014**: Si el servicio de configuración remota no tiene valores publicados, la aplicación
  MUST arrancar con valores por defecto propios.
- **FR-015**: La visita a la pantalla de arranque MUST registrarse como evento de uso, y los
  fallos de preparación MUST reportarse al servicio de errores.

**Identidad visual**

- **FR-016**: La aplicación MUST usar la paleta institucional definida en el documento de diseño,
  en modo claro y oscuro.
- **FR-017**: Los colores de la aplicación MUST NOT verse alterados por la personalización
  cromática del sistema operativo.
- **FR-018**: La aplicación MUST disponer de la escala tipográfica completa, del sistema de
  espaciado, de las formas y de los niveles de elevación definidos en el documento de diseño,
  accesibles como valores con nombre y no como cifras sueltas.
- **FR-019**: La pantalla de arranque MUST componerse, de arriba abajo, con el escudo oficial, las
  siglas del boletín, su denominación completa en dos líneas, una línea divisoria, la autoría y un
  indicador de progreso discreto, sobre fondo azul institucional a pantalla completa.
- **FR-020**: La autoría MUST mostrar «Diseñada y desarrollada por» y «José Ramón Blanco Gutiérrez»
  en dos colores distintos, con el nombre destacado sobre la etiqueta.
- **FR-021**: El escudo MUST ser el recurso oficial aportado, sin recrearlo ni alterar sus
  proporciones ni sus colores.
- **FR-022**: El fondo de la pantalla de arranque MUST extenderse tras las barras del sistema, con
  los iconos de éstas en color claro.
- **FR-023**: La aplicación MUST mostrarse únicamente en orientación vertical en teléfonos.

**Verificación**

- **FR-024**: Cada pieza de reglas de negocio y cada modelo de pantalla que introduzca esta feature
  MUST tener pruebas automáticas que se ejecuten sin dispositivo.
- **FR-025**: MUST existir pruebas automáticas de interfaz que validen los cuatro estados de la
  pantalla de arranque y sus acciones.
- **FR-026**: MUST existir una prueba automática que verifique que, tras completarse el arranque,
  el retroceso no devuelve a la pantalla de arranque.
- **FR-027**: Las pruebas MUST ser deterministas: el tiempo mínimo en pantalla se verifica con
  tiempo virtual, sin esperas reales.

### Key Entities

- **Configuración de la aplicación**: los parámetros que el servicio remoto publica y que
  condicionan el arranque. Incluye la versión mínima soportada y un mensaje de mantenimiento
  opcional. Tiene valores por defecto propios para el caso de que no haya nada publicado.
- **Resultado del arranque**: la conclusión de la preparación. Es una de tres: la aplicación puede
  continuar, la versión instalada ha quedado obsoleta, o el servicio está en mantenimiento.
- **Estado de la pantalla de arranque**: la representación completa de lo que la portada muestra en
  un instante dado —preparando, lista, error recuperable o acceso bloqueado—. Es inmutable y es la
  única fuente de verdad de lo que se dibuja.
- **Versión instalada**: el número de versión de la aplicación en el dispositivo, que se compara
  con el mínimo soportado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En una instalación limpia y con conexión, la persona llega al contenido principal en
  menos de 3 segundos desde que toca el icono, en un dispositivo de gama media. El tiempo se mide,
  no se estima.
- **SC-002**: La pantalla de arranque es legible: permanece visible al menos 1 segundo en cualquier
  circunstancia, y nunca parpadea.
- **SC-003**: Con el dispositivo sin conexión, la persona alcanza el contenido principal en dos
  toques como máximo, sin quedar bloqueada en ningún momento.
- **SC-004**: Toda pieza de reglas de negocio y todo modelo de pantalla que introduce esta feature
  tiene su propia prueba automática. Se verifica de forma mecánica: una comprobación automatizada
  falla si alguno carece de fichero de prueba asociado.
- **SC-005**: Ningún color de la interfaz cambia al modificar el fondo de pantalla del dispositivo,
  comprobado con dos fondos de colores opuestos.
- **SC-006**: La composición de la pantalla de arranque conserva su jerarquía y no recorta ningún
  texto con el tamaño de letra del sistema al 200 %.
- **SC-007**: La pantalla de arranque implementada es indistinguible de la imagen de referencia en
  proporciones, jerarquía y color, salvo en el texto de autoría, que es el acordado en esta
  especificación.
- **SC-008**: Las cuatro comprobaciones de calidad del proyecto —compilación, pruebas sin
  dispositivo, pruebas de interfaz y análisis estático— terminan en verde.

## Assumptions

- El marco arquitectónico viene fijado por la constitución del proyecto y por el esqueleto de la
  feature 001; esta feature no lo revisa.
- **No hay todavía fuente de datos del boletín.** Se acordó explícitamente con el propietario que el
  arranque de esta feature comprueba conexión, configuración remota y versión soportada, pero no
  descarga el boletín: esa decisión, junto con la del cliente de red y la persistencia, corresponde
  a la feature que publique el boletín.
- **No se incluyen preferencias de usuario en el arranque.** No existe todavía ninguna preferencia
  real que cargar, e introducir un almacén de preferencias sin nada que guardar sería anticipar una
  decisión sin información.
- El documento de diseño de referencia es la fuente de verdad de la identidad visual. Su texto de
  autoría está desactualizado respecto a lo acordado aquí y se corregirá en el mismo cambio, para
  que documento y aplicación no se contradigan.
- El escudo aportado es el recurso oficial y se usa tal cual.
- El bloqueo vertical se aplica a teléfonos. En pantallas de 600 dp o más el sistema operativo
  ignora las restricciones de orientación y no existe forma soportada de imponerlas; queda fuera de
  alcance y no se intentará.
- El proyecto de telemetría ya está enlazado y verificado desde la feature 001.
- Los textos visibles están en español.
