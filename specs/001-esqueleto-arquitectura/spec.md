# Feature Specification: Esqueleto de arquitectura de la aplicación

**Feature Branch**: `001-esqueleto-arquitectura`

**Created**: 2026-08-28

**Status**: Draft

**Input**: User description: "Esqueleto de arquitectura limpia con MVVM, Koin y Firebase: estructura de capas, grafo de dependencias, pantalla inicial que atraviese todas las capas, telemetría desacoplada y batería de tests completa."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - La aplicación arranca y muestra contenido (Priority: P1)

Una persona instala BOCantabria y la abre por primera vez. La aplicación arranca sin
bloquearse, muestra brevemente un indicador de que está cargando y a continuación presenta la
pantalla inicial con su contenido. Si la carga falla, en lugar de una pantalla en blanco o un
cierre inesperado ve un mensaje claro de error y una forma de reintentar.

**Why this priority**: es el mínimo producto viable observable. Sin una pantalla que arranque
y pinte contenido real, ninguna otra pieza del proyecto es demostrable ni verificable.

**Independent Test**: se instala la aplicación en un dispositivo, se abre y se comprueba que
transita de «cargando» a «contenido» sin intervención. El escenario de error se provoca
haciendo que el origen de datos falle y se comprueba que aparece el mensaje y el reintento.

**Acceptance Scenarios**:

1. **Given** la aplicación recién instalada, **When** la persona la abre, **Then** aparece la
   pantalla inicial con un indicador de carga y después el contenido, sin cierres inesperados.
2. **Given** que el origen de datos devuelve un error, **When** la persona abre la pantalla
   inicial, **Then** ve un mensaje de error comprensible y una acción de reintentar.
3. **Given** la pantalla inicial en estado de error, **When** la persona pulsa reintentar y el
   origen de datos responde correctamente, **Then** la pantalla muestra el contenido.
4. **Given** la pantalla inicial mostrando contenido, **When** la persona gira el dispositivo,
   **Then** el contenido se conserva sin volver a cargarse desde cero.

---

### User Story 2 - Patrón reproducible para añadir features (Priority: P2)

Una persona del equipo recibe el encargo de añadir una funcionalidad nueva. Abre el
repositorio y encuentra una organización del código explícita, con un ejemplo completo y
funcionando que va de la pantalla al origen de datos. Copiando ese patrón sabe exactamente
qué piezas debe crear, dónde colocarlas y cómo conectarlas, sin tener que decidir la
arquitectura por su cuenta.

**Why this priority**: el valor principal de esta feature es de proyecto, no de producto.
Determina la velocidad y la consistencia de todo el trabajo posterior, pero solo es útil una
vez existe la rebanada de la historia 1 que sirve de ejemplo.

**Independent Test**: una persona ajena al desarrollo inicial añade una segunda pantalla
trivial siguiendo únicamente lo que ve en el repositorio y la guía operativa, sin preguntar,
y sin tener que modificar código de otras capas para lograrlo.

**Acceptance Scenarios**:

1. **Given** el repositorio, **When** se examina la organización del código, **Then** cada
   pieza (contenidos, reglas de negocio, orígenes de datos, pantallas, cableado) tiene un
   lugar único y evidente, coherente con la constitución del proyecto.
2. **Given** una pieza nueva mal ubicada que rompe la regla de dependencias entre capas,
   **When** se ejecuta la batería de comprobaciones del proyecto, **Then** el fallo se detecta
   automáticamente y no puede integrarse.
3. **Given** una dependencia nueva no registrada en el cableado de la aplicación, **When** se
   ejecuta la batería de comprobaciones, **Then** falla indicando qué dependencia no se puede
   resolver, en lugar de fallar al abrir la aplicación en el dispositivo.

---

### User Story 3 - Visibilidad de uso y de fallos (Priority: P3)

La persona responsable del producto necesita saber si la aplicación se usa y si falla. Sin
tocar el dispositivo de nadie, consulta un panel donde aparecen los eventos de uso básicos
(aperturas, pantallas vistas) y los cierres inesperados con su traza, para poder priorizar
correcciones.

**Why this priority**: es imprescindible desde el primer día en producción, pero no bloquea la
demostración de la arquitectura ni el desarrollo de features.

**Independent Test**: se abre la aplicación y se comprueba que el evento de pantalla vista
llega al panel de analítica; se provoca un cierre inesperado y se comprueba que la traza
aparece en el panel de errores.

**Acceptance Scenarios**:

1. **Given** la aplicación instalada, **When** la persona abre la pantalla inicial, **Then**
   se registra un evento de pantalla vista con su identificador.
2. **Given** la aplicación en ejecución, **When** se produce un cierre inesperado, **Then** la
   traza del fallo queda registrada y es consultable.
3. **Given** una prueba automatizada de cualquier capa, **When** se ejecuta, **Then** no
   necesita conexión con los servicios de telemetría ni los invoca realmente.

---

### Edge Cases

- **Origen de datos vacío**: la pantalla inicial debe distinguir «sin contenido» de «error» y
  mostrar un mensaje específico para cada caso, nunca un espacio en blanco sin explicación.
- **Carga lenta**: si la carga tarda, el indicador debe permanecer visible y la pantalla no
  debe quedar congelada ni permitir lanzar cargas duplicadas pulsando repetidamente.
- **Reintentos encadenados**: pulsar reintentar varias veces seguidas no debe provocar cargas
  simultáneas ni estados inconsistentes.
- **Cambio de configuración durante la carga**: girar el dispositivo mientras se carga no debe
  reiniciar la operación ni perder el resultado que ya estaba en curso.
- **Fallo al arrancar el cableado de dependencias**: si una dependencia no puede resolverse,
  el fallo debe ser evidente y atribuible, no un cierre silencioso sin causa identificable.

## Requirements *(mandatory)*

### Functional Requirements

**Arranque y pantalla inicial**

- **FR-001**: La aplicación MUST arrancar hasta la pantalla inicial sin cierres inesperados en
  una instalación limpia.
- **FR-002**: La pantalla inicial MUST representar de forma explícita y mutuamente excluyente
  los estados: cargando, contenido disponible, sin contenido y error.
- **FR-003**: El estado de error MUST ofrecer una acción de reintento que vuelva a solicitar
  los datos.
- **FR-004**: El contenido mostrado MUST proceder de un origen de datos a través de la cadena
  completa de capas, no estar escrito directamente en la pantalla.
- **FR-005**: El estado de la pantalla MUST sobrevivir a los cambios de configuración del
  dispositivo sin recargar los datos.
- **FR-006**: La aplicación MUST disponer de un mecanismo de navegación entre pantallas
  preparado para incorporar destinos nuevos sin rediseñarlo.

**Organización del código**

- **FR-007**: El código MUST organizarse en las capas y ubicaciones que define la constitución
  del proyecto, con un lugar único e inequívoco para cada tipo de pieza.
- **FR-008**: Las reglas de negocio MUST ser independientes de la plataforma y de cualquier
  proveedor externo, de modo que puedan verificarse sin dispositivo ni emulador.
- **FR-009**: Los modelos internos de los orígenes de datos MUST NOT llegar a la capa de
  presentación; deben traducirse a los modelos propios de las reglas de negocio.
- **FR-010**: Todas las dependencias de la aplicación MUST declararse en un cableado central
  único, sin construcciones manuales dispersas por el código.
- **FR-011**: El cableado de dependencias MUST poder verificarse automáticamente, fallando si
  alguna dependencia declarada no se puede resolver.
- **FR-012**: Debe existir al menos un ejemplo completo y funcionando que recorra todas las
  capas y sirva de patrón de referencia para features futuras.

**Telemetría**

- **FR-013**: La aplicación MUST registrar eventos de uso, incluyendo al menos la visita a la
  pantalla inicial.
- **FR-014**: La aplicación MUST reportar automáticamente los cierres inesperados junto con su
  traza.
- **FR-015**: Los servicios de telemetría MUST consumirse a través de una abstracción propia,
  sustituible por un doble en pruebas, y no invocarse desde las reglas de negocio ni desde la
  capa de presentación.
- **FR-016**: Los datos enviados a telemetría MUST NOT incluir información personal
  identificable.

**Verificación**

- **FR-017**: Cada pieza de las reglas de negocio y cada modelo de pantalla MUST tener pruebas
  automáticas que se ejecuten sin dispositivo.
- **FR-018**: MUST existir una prueba automática que verifique el cableado de dependencias
  completo.
- **FR-019**: MUST existir al menos una prueba automática que recorra la cadena completa de
  capas de extremo a extremo con el cableado real.
- **FR-020**: La pantalla inicial MUST tener pruebas automáticas de interfaz que validen los
  cuatro estados y la acción de reintento.
- **FR-021**: Las pruebas MUST ser deterministas: sin conexiones reales a red o telemetría, sin
  depender del reloj del sistema ni del orden de ejecución.
- **FR-022**: Las plantillas de prueba de ejemplo que trae el proyecto generado MUST
  eliminarse, para no confundirlas con pruebas reales.
- **FR-023**: MUST existir una prueba automática de interfaz que verifique que el contenido
  sobrevive a un cambio de configuración del dispositivo sin volver a cargarse.

### Key Entities

- **Elemento de contenido**: la unidad mínima de información que muestra la pantalla inicial.
  Tiene un identificador estable y un texto visible. Su naturaleza definitiva se concretará
  cuando se especifique la primera feature de negocio; aquí actúa como portador del ejemplo
  que demuestra el recorrido entre capas.
- **Estado de pantalla**: la representación completa de lo que la pantalla inicial debe
  mostrar en un instante dado (cargando, contenido, vacío o error con su mensaje). Es
  inmutable y es la única fuente de verdad para lo que se dibuja.
- **Evento de telemetría**: un hecho relevante de uso, con nombre y atributos no
  identificativos, que se envía al servicio de analítica.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En una instalación limpia, la aplicación llega a mostrar la pantalla inicial en
  menos de 2 segundos en un dispositivo de gama media, sin cierres inesperados. El tiempo se
  mide, no se estima: la guía de validación indica cómo obtener la cifra.
- **SC-002**: Toda pieza de reglas de negocio y todo modelo de pantalla tiene su propia prueba
  automática que se ejecuta sin dispositivo. Se verifica de forma mecánica: una comprobación
  automatizada falla si alguna de esas piezas carece de fichero de prueba asociado.
- **SC-003**: La batería completa de pruebas sin dispositivo termina en menos de 2 minutos, de
  modo que ejecutarla en cada cambio sea sostenible.
- **SC-004**: Un error deliberado en el cableado de dependencias o una violación de la regla de
  dependencias entre capas es detectado por las comprobaciones automáticas antes de llegar a
  ejecutarse en un dispositivo, en el 100 % de los casos.
- **SC-005** *(seguimiento posterior a la entrega, no es puerta de aceptación)*: Una persona del
  equipo que no participó en esta feature añade una pantalla nueva siguiendo el patrón existente
  en menos de 30 minutos y sin necesitar aclaraciones. Solo puede medirse cuando llegue la
  primera feature de negocio, por lo que no bloquea el cierre de esta.
- **SC-006**: Tras abrir la aplicación, el evento de pantalla vista y, en su caso, la traza de
  un cierre inesperado son consultables en sus paneles respectivos en menos de 24 horas.
- **SC-007**: Las cuatro comprobaciones de calidad del proyecto (compilación, pruebas sin
  dispositivo, pruebas de interfaz y análisis estático) terminan en verde.

## Assumptions

- El marco arquitectónico no se decide en esta feature: viene fijado por la constitución del
  proyecto (`.specify/memory/constitution.md`), que ya establece capas, patrón de
  presentación, mecanismo de inyección de dependencias, exigencias de prueba y tratamiento de
  la telemetría.
- No hay todavía ninguna fuente de datos real definida. Se ha acordado explícitamente con el
  propietario que el origen de datos de esta feature será en memoria, y que la decisión sobre
  acceso a red y persistencia se tomará y justificará en el plan de la primera feature de
  negocio que lo necesite.
- El contenido de la pantalla inicial es deliberadamente trivial: su propósito es demostrar y
  proteger el recorrido entre capas, no aportar valor funcional. Se sustituirá cuando llegue
  la primera feature de negocio.
- El proyecto de telemetría ya existe y está enlazado con la aplicación; la configuración
  necesaria ya está presente en el repositorio y verificada.
- El identificador de aplicación actual se mantiene sin cambios, porque la configuración de
  telemetría está registrada contra él.
- Las pruebas de interfaz se ejecutan en local contra un emulador; la integración continua
  ejecuta el resto.
- El idioma de los textos visibles de la pantalla de ejemplo es el español, coherente con el
  público objetivo de la aplicación.
