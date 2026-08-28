# Research: Pantalla de arranque y sistema de diseño institucional

**Feature**: `002-pantalla-arranque` | **Fase**: 0 | **Fecha**: 2026-08-28

La constitución fija el marco y el documento de diseño fija la estética. Lo que hay que resolver
aquí son los huecos: cómo se expresa el sistema de diseño en código, cómo se encadena el arranque
del sistema con el de la aplicación, y cómo se hace todo eso comprobable sin esperas reales.

---

## D-001: Tokens de diseño que Material 3 no contempla

**Decisión**: los tokens que tienen equivalente en Material 3 se mapean sobre su rol
(`Primary` → `primary`, `Background` → `background`…). Los diez que no lo tienen —`TextMuted`,
`SurfaceSoft`, `SurfaceStrong`, `Divider`, `AccentOfficial`, `AiAccent`, `AiContainer`, `Success`,
`Warning` y los cinco colores de sección— viajan en un `BocExtendedColors` inmutable expuesto por un
`CompositionLocal`, accesible como `BocTheme.colors`.

**Rationale**: forzar diez tokens propios dentro de roles de Material 3 que significan otra cosa
—meter `AiAccent` en `tertiary`, por ejemplo— hace que el código mienta: quien lea
`MaterialTheme.colorScheme.tertiary` no sabrá que está pintando contenido de IA. Un contenedor
aparte mantiene ambos vocabularios intactos y deja evidente cuál es cuál en el punto de uso.

**Alternativas descartadas**:
- Estirar los roles de Material 3: no hay huecos suficientes y los que hay significan otra cosa.
- Constantes de color sueltas importadas directamente: pierde el cambio automático entre claro y
  oscuro, que es justo lo que un tema aporta.

---

## D-002: Eliminar el color dinámico, no desactivarlo

**Decisión**: se retira el parámetro `dynamicColor` de `BOCantabriaTheme`, en lugar de dejarlo con
valor `false`.

**Rationale**: el requisito FR-017 dice que los colores no pueden verse alterados por la
personalización del sistema. Un parámetro con valor por defecto seguro sigue siendo un interruptor:
tarde o temprano alguien lo activa «para probar» y se queda activado. Si el parámetro no existe, la
regla no depende de la disciplina de nadie. Es la misma lógica por la que las reglas de arquitectura
son un test y no una convención.

**Alternativas descartadas**:
- `dynamicColor: Boolean = false`: sobrevive a la primera revisión pero no al primer despiste.

---

## D-003: Encadenado del arranque del sistema con el de la aplicación

**Decisión**: se configura el arranque de Android 12+ mediante un tema propio —fondo azul
institucional e icono el escudo— usando la biblioteca de compatibilidad `core-splashscreen` 1.2.0
para que se comporte igual desde `minSdk 24`. La condición de permanencia se libera en cuanto la
primera composición está lista, y a partir de ahí manda la pantalla de Compose.

**Rationale**: FR-002 exige una transición sin destellos. El sistema **siempre** muestra su propio
arranque; si no se configura, lo pinta con el fondo del tema, que hoy es blanco, y se ve un
parpadeo blanco → azul en cada apertura. Pintar el arranque del sistema del mismo azul convierte la
transición en invisible. La biblioteca de compatibilidad evita tener dos comportamientos distintos
según la versión de Android.

**Riesgo y mitigación**: retener el arranque del sistema hasta que termine la preparación completa
dejaría al usuario ante una pantalla estática sin indicador ni salidas. Por eso se libera pronto y
la preparación ocurre ya en la pantalla de Compose, que sí puede mostrar progreso y errores.

**Alternativas descartadas**:
- Solo pantalla de Compose, sin configurar el arranque del sistema: el destello blanco permanece.
- Retener el arranque del sistema durante toda la preparación: no admite indicador ni acciones de
  recuperación, así que las historias 2 quedaría sin sitio donde ocurrir.

---

## D-004: Tiempo mínimo en pantalla, y cómo probarlo

**Decisión**: 1.200 ms de permanencia mínima. Se implementa esperando en paralelo a la preparación
—no en serie— de modo que el mínimo y el trabajo real se solapen. El reloj llega inyectado, así que
en pruebas el tiempo es virtual.

**Rationale**: FR-005 y SC-002 piden que la portada sea legible. En un dispositivo rápido con buena
red la preparación puede acabar en 50 ms, y un parpadeo se percibe como un error de la aplicación,
no como velocidad. Esperar en serie (primero el mínimo, luego el trabajo) sumaría los dos tiempos y
haría el arranque artificialmente lento; esperar en paralelo hace que el mínimo solo se note cuando
de verdad sobra tiempo.

**Alternativas descartadas**:
- Sin mínimo: parpadeo en dispositivos rápidos.
- Espera en serie: penaliza a todo el mundo para resolver un caso que solo afecta a los rápidos.
- Mínimo con esperas reales en las pruebas: cada ejecución sumaría más de un segundo por caso y
  volvería la suite sensible a la carga de la máquina, violando el principio V de la constitución.

---

## D-005: Límite de espera de la preparación

**Decisión**: 8 segundos para el conjunto de la preparación, tras los cuales se trata como fallo
recuperable y se ofrecen las salidas de la historia 2.

**Rationale**: FR-006 exige un límite. Sin él, una red que acepta la conexión pero no responde
—un portal cautivo de hotel, por ejemplo— deja la portada girando indefinidamente, que es
exactamente el escenario que la historia 2 quiere evitar. Ocho segundos son suficientes para una
red lenta pero honesta, y cortos frente al umbral de abandono de una persona ante una pantalla sin
respuesta.

**Alternativas descartadas**:
- Confiar en el tiempo de espera propio del servicio de configuración remota: no cubre el resto de
  la preparación y su valor no está bajo nuestro control.
- Un límite más largo, de 15 a 30 s: técnicamente más tolerante, pero para entonces la persona ya ha
  cerrado la aplicación.

---

## D-006: Un solo caso de uso que orquesta el arranque

**Decisión**: `PrepareStartupUseCase` encadena las tres comprobaciones y devuelve un
`StartupStatus`. La pantalla no conoce los pasos intermedios.

**Rationale**: si el modelo de pantalla orquestara las tres comprobaciones, la lógica de arranque
—qué se comprueba, en qué orden y qué manda si dos cosas fallan a la vez— viviría en la capa de
presentación, donde no puede probarse sin levantar un modelo de pantalla. Con un caso de uso, esa
política es Kotlin puro y se prueba directamente. La regla de precedencia del caso límite «versión
obsoleta y sin conexión a la vez» se resuelve ahí: sin conexión no hay forma de saber la versión
mínima, así que la falta de conexión manda.

**Alternativas descartadas**:
- Tres casos de uso invocados desde el modelo de pantalla: reparte la política entre capas.
- Comprobaciones dentro del repositorio: mezcla obtención de datos con reglas de negocio.

---

## D-007: Estado de pantalla con acceso bloqueado separado del error

**Decisión**: `SplashUiState` sellado con `Loading`, `Ready`, `Error(error)` y
`Blocked(reason)`, donde `reason` distingue versión obsoleta de mantenimiento.

**Rationale**: FR-010 y FR-012 piden comportamientos opuestos. Un error recuperable ofrece
«continuar sin conexión»; una versión obsoleta **no puede** ofrecerlo, porque saltarse el bloqueo
anula su propósito. Si ambos casos compartieran el estado `Error`, la diferencia dependería de un
condicional dentro de la vista, que es donde estas cosas se olvidan. Como estados distintos, cada
uno dibuja sus propias acciones y el compilador obliga a tratarlos por separado.

**Alternativas descartadas**:
- `Error` con una bandera `canContinue`: permite la combinación incoherente «bloqueado pero con
  salida», que es precisamente el fallo que hay que impedir.

---

## D-008: Valores por defecto de la configuración remota empaquetados

**Decisión**: los valores por defecto se declaran en un recurso XML que el cliente de configuración
remota carga al inicializarse.

**Rationale**: FR-014 exige que la aplicación arranque aunque no haya nada publicado en la consola,
que es exactamente el estado del proyecto hoy. Cargar los valores por defecto en el propio cliente
significa que la lectura de un parámetro nunca devuelve vacío, así que ni el repositorio ni el caso
de uso necesitan un camino especial para «todavía no hay configuración».

**Alternativas descartadas**:
- Valores por defecto repartidos por el código con el operador elvis: multiplica el mismo valor por
  varios sitios y es cuestión de tiempo que dejen de coincidir.

---

## D-009: La versión instalada llega inyectada

**Decisión**: `AppVersionProvider` es una interfaz en `core/util`; su implementación lee el número
de versión generado en la compilación.

**Rationale**: FR-024 y el principio V exigen probar sin dispositivo la comparación de versiones. Un
acceso estático al número de versión no se puede sustituir en una prueba, así que comprobar «versión
por debajo del mínimo» obligaría a un emulador o a instrumentación de bytecode. Inyectado, cada
prueba declara la versión que le interesa en una línea.

**Alternativas descartadas**:
- Leer el número de versión directamente donde se necesita: rápido de escribir e imposible de
  probar en las tres ramas.

---

## D-010: Dónde vive la comprobación de conectividad

**Decisión**: interfaz `ConnectivityRepository` en `domain/repository`, implementada en `data` sobre
la fuente local `ConnectivityDataSource`, que envuelve el servicio de conectividad de Android.

**Rationale**: la conectividad es información que la aplicación **consulta**, igual que el
contenido, y la constitución dice que el dominio no puede depender de la plataforma. Modelarla como
un repositorio la deja disponible para el caso de uso sin que `domain` sepa que Android existe, y
permite simular «sin conexión» en pruebas sin modo avión.

**Alternativas descartadas**:
- Una utilidad en `core/util` que reciba el contexto: metería una dependencia de Android en el
  camino de una regla de negocio, y la regla de arquitectura de la feature 001 lo rechazaría.

---

## D-011: Bloqueo vertical

**Decisión**: se declara la orientación vertical en el manifest, con el alcance limitado a
teléfonos que reconoce la especificación.

**Rationale**: es el mecanismo estándar y el único que el sistema respeta. En anchuras de 600 dp o
más, Android ignora la restricción desde la API 36 y la exención temporal desaparece en la 37, que
es la que compila este proyecto. Intentar sortearlo —recomponer forzando dimensiones, o bloquear la
orientación desde código— produciría una interfaz peor en tablets a cambio de nada.

**Alternativas descartadas**:
- Bloqueo por código en tiempo de ejecución: el sistema lo ignora igual en pantallas grandes.
- Ofrecer un diseño horizontal: el propietario descartó explícitamente que tenga utilidad.

---

## D-012: Pesos tipográficos

**Decisión**: los estilos que el documento define con peso 650 se implementan con `SemiBold` (600).

**Rationale**: la familia tipográfica estándar de Android no ofrece el peso 650; pedirlo produce
o bien el peso 600 real, o bien un engrosado sintético de peor calidad, según el dispositivo. Fijar
600 hace que el resultado sea el mismo en todos. La diferencia entre 600 y 650 es prácticamente
imperceptible en pantalla.

**Alternativas descartadas**:
- Empaquetar una fuente variable: alcanzaría el 650 exacto a cambio de varios cientos de kilobytes
  y de gestionar una fuente propia. Queda anotado por si en revisión se aprecia la diferencia.


---

## D-013: Un único tema, el claro

**Decisión**: se elimina el modo oscuro. `BOCantabriaTheme` no consulta el ajuste del sistema y no
existe un esquema oscuro que consultar. Los tokens de la paleta oscura desaparecen del código, salvo
el azul claro `#8FD3EE`, que se conserva renombrado porque no era un token de modo oscuro sino el
acento que se usa **sobre** el azul institucional, en la línea divisoria y en la autoría.

**Rationale**: decisión de producto del propietario, tomada tras la primera redacción de la
especificación. Una publicación oficial debe verse igual en cualquier dispositivo; un segundo
aspecto duplica el coste de diseñar, revisar y verificar cada pantalla futura, y multiplica por dos
las combinaciones que hay que comprobar en accesibilidad y contraste, a cambio de una preferencia
estética que aquí no aporta valor.

La implementación **elimina** el mecanismo en lugar de fijarlo a claro, por la misma razón que se
eliminó el parámetro de color dinámico (D-002): un interruptor con valor seguro sigue siendo un
interruptor. Si `isSystemInDarkTheme()` no se invoca en ninguna parte y no hay `darkColorScheme`, la
apariencia no puede depender del sistema por accidente.

**Consecuencia sobre las barras del sistema**: `enableEdgeToEdge()` decide por su cuenta el color de
los iconos según el tema del sistema. Con un fondo claro fijo, en un móvil configurado en oscuro los
iconos saldrían claros sobre blanco y serían ilegibles. Se fija explícitamente la apariencia clara
para ambas barras, y la portada azul la invierte mientras está en pantalla.

**Cómo se hace cumplir**: una regla de Konsist falla si algún fichero importa
`isSystemInDarkTheme` o `darkColorScheme`, y una prueba de interfaz comprueba que forzar la
configuración a modo noche no altera los colores. Sin eso, «tema único» sería una intención, no una
propiedad del sistema.

**Alternativas descartadas**:
- Mantener el esquema oscuro y forzar `darkTheme = false`: deja el código muerto a la vista y el
  interruptor a un carácter de distancia de reactivarse.
- Declarar recursos `values-night` idénticos a los claros: obliga a mantener dos copias de todo y a
  recordar sincronizarlas.
