# Research: Esqueleto de arquitectura de la aplicación

**Feature**: `001-esqueleto-arquitectura` | **Fase**: 0 | **Fecha**: 2026-08-28

La constitución del proyecto ya fija el marco (capas, MVVM, Koin, exigencias de prueba,
telemetría desacoplada), así que no hay incógnitas de arquitectura que resolver. Lo que sí
requiere decisión son los huecos que la constitución deja abiertos deliberadamente y los
detalles de implementación que condicionan la testabilidad.

---

## D-001: Origen de datos de la rebanada de ejemplo

**Decisión**: fuentes en memoria, con la pareja local/remota real del diagrama de referencia.
`ContentRemoteDataSource` devuelve una lista fija tras una latencia simulada;
`ContentLocalDataSource` es una caché en memoria.

**Rationale**: el propietario acordó aplazar la elección de cliente HTTP y de persistencia a la
primera feature de negocio. Mantener las dos fuentes (no una sola) permite que el repositorio
implemente ya la política *remoto con respaldo local*, que es la que heredarán las features
reales; sustituir la implementación después no obliga a tocar `domain` ni `ui`.

**Alternativas descartadas**:
- Una única fuente: más simple, pero deja `source/local` y `source/remote` vacíos y el
  repositorio sin lógica que probar, con lo que el ejemplo no demuestra nada.
- Introducir ya Retrofit o Room: viola el acuerdo con el propietario y ata la decisión sin la
  información que dará la primera feature real.

---

## D-002: Ubicación de las abstracciones de telemetría

**Decisión**: interfaces `AnalyticsTracker` y `CrashReporter` en un paquete nuevo
`core/telemetry`; sus implementaciones sobre Firebase viven en `data/telemetry`.

**Rationale**: la constitución exige que se declaren fuera de `data` y que sean sustituibles
en pruebas. `core` es la ubicación natural de lo transversal. Es la única desviación respecto
al diagrama de referencia aportado por el propietario, y es deliberada: ese diagrama procede de
una aplicación sin telemetría, y meter estos contratos en `core/util` los escondería entre
utilidades genéricas, mientras que ponerlos en `domain` contaminaría el dominio con una
preocupación que no es de negocio.

**Alternativas descartadas**:
- `core/util`: cabe en el diagrama original pero degrada la legibilidad; `util` acabaría siendo
  un cajón de sastre, que es justo lo que la constitución quiere evitar.
- `domain/repository`: modelar la telemetría como repositorio es forzar la metáfora; la
  telemetría no es una fuente de datos del dominio.

---

## D-003: Tipo de resultado de las operaciones de dominio

**Decisión**: `AppResult<out T>` (sealed interface con `Success<T>` y `Failure`) más un
`DomainError` sellado, ambos en el dominio. No se usa `kotlin.Result`.

**Rationale**: `kotlin.Result` obliga a envolver `Throwable`, lo que arrastra excepciones al
dominio y hace el error opaco para la capa de presentación. Con un `DomainError` sellado, la
pantalla puede decidir qué mensaje muestra mediante un `when` exhaustivo que el compilador
verifica. El nombre `AppResult` evita el sombreado de `kotlin.Result`, que es una fuente
habitual de confusión al leer código.

**Alternativas descartadas**:
- `kotlin.Result`: error opaco, y su API de `runCatching` invita a capturar excepciones que
  deberían estar traducidas ya en `data`.
- Excepciones propagadas: obligan a `try/catch` en cada `ViewModel` y no son exhaustivas.

---

## D-004: Forma del estado de pantalla

**Decisión**: `HomeUiState` como *sealed interface* con `Loading`, `Content`, `Empty` y
`Error(error: DomainError)`.

**Rationale**: el requisito FR-002 exige que los cuatro estados sean mutuamente excluyentes.
Una `data class` con banderas (`isLoading`, `items`, `errorMessage`) permite combinaciones
imposibles como «cargando y con error a la vez», que luego hay que impedir por convención. Un
tipo sellado lo hace imposible por construcción y convierte los tests en aserciones directas
sobre el tipo.

**Alternativas descartadas**:
- `data class` con banderas: más flexible para estados compuestos, pero aquí no hay ninguno y
  el coste es perder la exclusividad que pide la especificación.

---

## D-005: Navegación

**Decisión**: Navigation Compose con rutas *type-safe*, lo que exige aplicar el plugin
`org.jetbrains.kotlin.plugin.serialization` (2.2.10, la misma versión de Kotlin).

**Rationale**: las rutas como cadenas de texto fallan en tiempo de ejecución cuando alguien se
equivoca al escribirlas o al pasar argumentos. Las rutas tipadas convierten ese fallo en un
error de compilación, que es exactamente la clase de garantía que persigue el criterio SC-004.
El coste es un plugin de compilador adicional, que además será necesario en cuanto exista
serialización de datos remotos.

**Riesgo y mitigación**: AGP 9 aplica Kotlin de forma integrada y la compatibilidad del plugin
de serialización en esa configuración no está verificada. Se comprueba con una compilación
inmediatamente después de aplicarlo; si falla, la salida ordenada es usar rutas de texto con
constantes centralizadas y registrar aquí la vuelta atrás.

**Alternativas descartadas**:
- Rutas de texto: cero dependencias nuevas, pero traslada al tiempo de ejecución errores que
  el compilador podría atrapar.
- Navegación propia con un `when` sobre estado: menos dependencias, pero hay que reimplementar
  pila de retroceso y enlaces profundos.

---

## D-006: Cómo se hace cumplir automáticamente la regla de dependencias entre capas

**Decisión**: Konsist 0.17.3 como dependencia de test, con un conjunto de reglas de
arquitectura ejecutadas como pruebas normales de JUnit.

**Rationale**: el criterio SC-004 exige detectar el 100 % de las violaciones de capa antes de
llegar al dispositivo. Ni el compilador de Kotlin ni Android Lint conocen la regla
`ui → domain ← data`; sin una herramienta explícita, ese criterio no sería verificable y la
arquitectura se degradaría silenciosamente. Konsist lee el código como texto Kotlin, no
requiere instrumentación ni bytecode, y sus reglas se leen como frases.

Reglas que se codifican:
1. Ningún fichero de `domain` importa `android.*`, `androidx.*`, `com.google.firebase.*`,
   `org.koin.*` ni nada de `data` o `ui`.
2. Ningún fichero de `ui` importa nada de `data`.
3. Toda clase que termine en `UseCase` reside en `domain.usecase`.
4. Toda clase que termine en `ViewModel` reside en `ui` y extiende `ViewModel`.
5. Ningún fichero fuera de `data` importa `com.google.firebase.*`.
6. Toda clase de `domain` (modelos, casos de uso) y toda clase `*ViewModel` tiene un fichero de
   prueba asociado en `src/test`. Es lo que convierte el criterio SC-002 en algo verificable
   mecánicamente en lugar de una afirmación de buena fe.

**Alternativas descartadas**:
- ArchUnit: maduro, pero trabaja sobre bytecode compilado y su soporte de Kotlin es menos
  natural (los ficheros y las funciones de nivel superior no se modelan bien).
- Módulos Gradle separados por capa: el aislamiento sería estructural y sin herramientas
  extra, pero multiplica la configuración de build y los tiempos de compilación para un
  proyecto de una sola aplicación. Se reconsiderará si el proyecto crece.
- Revisión manual: no cumple «100 % detectado automáticamente».

---

## D-007: Inyección de los despachadores de corrutinas

**Decisión**: interfaz `DispatcherProvider` en `core/util`, implementación por defecto en la
aplicación y una implementación de prueba respaldada por `TestDispatcher`.

**Rationale**: la constitución exige tests deterministas. Referenciar `Dispatchers.IO`
estáticamente dentro de un repositorio hace imposible controlar el tiempo virtual en las
pruebas y produce fallos intermitentes. Inyectarlos permite que cada test decida el planificador.

**Alternativas descartadas**:
- `Dispatchers.setMain()` a secas: resuelve el hilo principal en los `ViewModel`, pero no los
  saltos a `IO` dentro de la capa de datos.

---

## D-008: Verificación del grafo de dependencias

**Decisión**: prueba en `src/test` que arranca Koin con todos los módulos bajo Robolectric
(para disponer de un `Context` real) y ejecuta la comprobación de módulos de `koin-test`.

**Rationale**: FR-011 exige que un cableado incompleto falle en las comprobaciones, no al abrir
la aplicación. Se ejecuta bajo Robolectric porque los módulos de datos necesitan un `Context`
de Android; hacerlo así valida el grafo tal y como se construirá en producción, sin emulador.

**Alternativas descartadas**:
- Verificación puramente JVM declarando tipos externos: más rápida, pero no ejercita el
  `androidContext`, que es justo donde suelen aparecer los fallos de cableado.

---

## D-009: Prueba de la implementación de analítica sobre Firebase

**Decisión**: Robolectric más un doble de MockK sobre el cliente de Firebase, inyectado en el
envoltorio propio.

**Rationale**: FR-015 y FR-021 exigen que las pruebas no invoquen realmente el servicio.
Inicializar Firebase de verdad en un test lo haría dependiente de la red y no determinista.
Robolectric aporta el `Bundle` de Android que necesita el envoltorio para componer el evento;
MockK permite afirmar exactamente qué nombre y qué atributos se envían, que es lo que se quiere
proteger de regresiones.

**Alternativas descartadas**:
- No probar el envoltorio: dejaría sin cubrir la regla de no enviar datos personales.
- Inicializar Firebase real bajo Robolectric: no determinista y lento.
