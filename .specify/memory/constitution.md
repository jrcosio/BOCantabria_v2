<!--
Sync Impact Report
==================

--- Enmienda 1.1.0 (2026-08-30) ---
Version change: 1.0.0 → 1.1.0
Motivo del bump: MINOR. Cambia materialmente una restricción vinculante de la sección
«Restricciones Tecnológicas» sin eliminar ni redefinir ningún principio.

Principios modificados: ninguno.
Secciones añadidas: ninguna.
Secciones eliminadas: ninguna.

Cambio único, en «Restricciones Tecnológicas» → Plataforma:
- `minSdk 24` → `minSdk 28`. `compileSdk`/`targetSdk` siguen en 37.

Motivo: la feature 004 abre el PDF oficial dentro de la aplicación. El visor que mejor funciona
en el marco de interfaz que esta constitución impone —Jetpack Compose, y con Fragments y XML de
layouts prohibidos— es `androidx.pdf:pdf-compose`, que exige `minSdk 28`. Las alternativas eran
escribir el visor sobre `PdfRenderer` de la plataforma, con peor resultado en la parte más
delicada de la aplicación, o depender de una biblioteca de un solo mantenedor en el camino
crítico de leer un documento oficial.

Coste aceptado: deja fuera Android 7 y 8, en torno al 2 % de los dispositivos en 2026.

Consecuencia que hay que ejecutar, no arrastrar: con `minSdk 28`, `java.time` es nativo y el
azucarado de la biblioteca estándar deja de ser necesario para las fechas. Se retira. Queda
recogido en el requisito FR-040 de `specs/004-detalle-publicacion/spec.md`.

Requisitos de la enmienda, los tres cumplidos en este mismo cambio:
(a) aprobación explícita del propietario — dada antes de redactar la especificación;
(b) `CLAUDE.md` actualizado para que la guía operativa no contradiga a la norma;
(c) este registro.

Follow-up TODOs: ninguno.

--- Ratificación inicial 1.0.0 (2026-08-28) ---
Version change: (ninguna) → 1.0.0
Motivo del bump: ratificación inicial de la constitución del proyecto.

Principios definidos (nuevos):
- I.   Desarrollo Dirigido por Especificación (SDD) — NO NEGOCIABLE
- II.  Arquitectura Limpia por Capas
- III. MVVM en la Capa de Presentación
- IV.  Inyección de Dependencias con Koin
- V.   Testing Exigente — NO NEGOCIABLE
- VI.  Observabilidad Desacoplada (Firebase)

Secciones añadidas:
- Restricciones Tecnológicas (SECTION_2)
- Flujo de Trabajo y Puertas de Calidad (SECTION_3)
- Governance

Secciones eliminadas: ninguna.

Notas: la plantilla base define 5 principios; este proyecto adopta 6 según
lo acordado con el propietario del repositorio.

Follow-up TODOs: ninguno. No quedan tokens sin sustituir.
-->

# Constitución de BOCantabria

## Core Principles

### I. Desarrollo Dirigido por Especificación (SDD) — NO NEGOCIABLE

Toda feature DEBE recorrer el ciclo completo de GitHub Spec Kit antes de que se escriba una
sola línea de código de producto:

`/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`

- PROHIBIDO escribir código de producto sin un `tasks.md` aprobado en `specs/<NNN>-<slug>/`.
- Si se solicita una feature directamente, la respuesta correcta es arrancar por
  `/speckit-specify`, nunca implementar.
- `/speckit-specify` crea además la rama `NNN-slug` mediante la extensión git de Spec Kit.
  Todo el trabajo de la feature vive en esa rama.
- RECOMENDADOS pero opcionales: `/speckit-clarify` antes de planificar y `/speckit-analyze`
  antes de implementar.
- EXENTOS del ciclo: arreglos de build, subidas de versión, erratas y documentación. La
  exención cubre configuración; NO cubre código de producto que se cuele bajo esa etiqueta.

*Rationale*: la especificación es la fuente de verdad del proyecto. Sin ella, la intención
queda solo en la conversación y se pierde; con ella, cada decisión queda auditable y el
trabajo es reproducible por cualquiera.

### II. Arquitectura Limpia por Capas

El código se organiza en tres capas y las dependencias apuntan SIEMPRE hacia dentro:

`ui` → `domain` ← `data`

- `domain` DEBE ser Kotlin puro: sin `import android.*`, sin Compose, sin Firebase, sin
  Retrofit/Room, sin referencias a `data` ni a `ui`. Contiene modelos, interfaces de
  repositorio y casos de uso.
- `data` implementa las interfaces declaradas en `domain`. Aquí viven las fuentes de datos
  (`source/local`, `source/remote`) y los SDK de terceros. Los modelos de `data` (DTOs,
  entidades) NO cruzan hacia `ui`: se mapean a modelos de `domain`.
- `ui` DEBE hablar exclusivamente con casos de uso de `domain`. PROHIBIDO que un
  `ViewModel` o un Composable dependa de una clase de `data`.
- `core` aloja lo transversal (DI, tema y componentes compartidos, utilidades) y NO DEBE
  contener lógica de negocio.

*Rationale*: aislar el dominio de los detalles técnicos es lo que permite cambiar de fuente
de datos, de SDK o de framework de UI sin reescribir las reglas de negocio, y lo que hace
que el dominio sea testeable sin emulador.

### III. MVVM en la Capa de Presentación

- Una pantalla = un Composable `Screen` + un `ViewModel` + un `UiState` inmutable.
- El estado se expone como `StateFlow<UiState>` de solo lectura; el `MutableStateFlow`
  permanece privado. PROHIBIDO exponer estado mutable.
- `UiState` DEBE ser una `data class` o `sealed interface` inmutable. Los eventos de usuario
  se modelan como funciones públicas del `ViewModel`.
- Los Composables DEBEN ser tontos: renderizan estado y emiten eventos. PROHIBIDA la lógica
  de negocio dentro de un `@Composable`.
- Los Composables reutilizables DEBEN ser stateless y recibir el estado por parámetro
  (*state hoisting*), para poder previsualizarse y testearse aislados.

*Rationale*: un estado único e inmutable elimina las inconsistencias de UI y hace que cada
pantalla sea verificable con un test que solo observa un flujo.

### IV. Inyección de Dependencias con Koin

- Todo el grafo de dependencias DEBE declararse en módulos Koin bajo `core/di`.
- PROHIBIDO instanciar dependencias a mano (`SomeRepositoryImpl()`), usar objetos singleton
  mutables o *service locators* caseros fuera de Koin.
- Los `ViewModel` se obtienen con `koinViewModel()`; nunca se construyen directamente en un
  Composable.
- El grafo DEBE ser verificable: existe un test que ejecuta `verify()` / `checkModules()`
  sobre todos los módulos y falla si alguna dependencia no resuelve.

*Rationale*: si el grafo se declara en un único sitio y se verifica automáticamente, los
fallos de cableado se detectan en CI y no en tiempo de ejecución en el móvil del usuario.

### V. Testing Exigente — NO NEGOCIABLE

Ningún elemento de `tasks.md` se considera terminado sin su test correspondiente en verde.

Pirámide obligatoria:

1. **Unitarios** (`src/test`) — casos de uso, repositorios y `ViewModel`s. Los `ViewModel`s
   se testean con `runTest` y Turbine sobre el `StateFlow`. Deben correr sin emulador.
2. **Integración** (`src/test`) — verificación del grafo Koin completo y flujos que
   atraviesan varias capas (`ViewModel → UseCase → Repository → Source`) usando el grafo
   real y dobles únicamente en la frontera externa.
3. **UI** (`src/androidTest`) — cada pantalla tiene al menos un test de Compose que valida
   render y navegación, arrancando Koin con módulos de test.

Reglas adicionales:

- Todo *bug* corregido DEBE incorporar un test de regresión que falle antes del arreglo.
- PROHIBIDO desactivar, ignorar (`@Ignore`) o comentar un test para hacer pasar la build.
  Si un test estorba, se arregla el código o se cambia la especificación.
- Los tests DEBEN ser deterministas: sin dependencias de red real, de reloj del sistema ni
  de orden de ejecución. Los `Dispatchers` se inyectan, nunca se referencian estáticamente.

*Rationale*: la única garantía real de que una arquitectura limpia sigue siendo limpia es
que esté cubierta por tests que se ejecuten en cada cambio.

### VI. Observabilidad Desacoplada (Firebase)

- La app DEBE reportar analítica (Firebase Analytics) y errores (Firebase Crashlytics).
- Los SDK de Firebase SOLO pueden invocarse desde implementaciones en `data`. PROHIBIDO
  llamar a `FirebaseAnalytics` o `FirebaseCrashlytics` desde un `ViewModel`, un Composable,
  un caso de uso o cualquier clase de `domain`.
- El acceso se hace a través de abstracciones propias (p. ej. `AnalyticsTracker`,
  `CrashReporter`) declaradas fuera de `data` e inyectadas por Koin, de modo que en tests se
  sustituyan por dobles sin necesidad de Firebase.
- PROHIBIDO registrar datos personales identificables en eventos de analítica o en trazas de
  Crashlytics.

*Rationale*: envolver el SDK mantiene el dominio testeable y deja la puerta abierta a
cambiar de proveedor de telemetría sin tocar la lógica de la aplicación.

## Restricciones Tecnológicas

Estas decisiones son vinculantes; cambiarlas requiere una enmienda de esta constitución.

- **Plataforma**: Android nativo, Kotlin. `minSdk 28`, `compileSdk`/`targetSdk` 37.
  `minSdk 28` desde la enmienda 1.1.0: es lo que exige el visor de PDF oficial de Jetpack para
  Compose, y sin él no hay forma de leer el documento dentro de la aplicación sin recurrir a
  Fragments —prohibidos aquí— o a un visor propio peor. Ver el Sync Impact Report.
- **UI**: Jetpack Compose con Material 3 y Navigation Compose. PROHIBIDO introducir XML de
  layouts o Fragments.
- **DI**: Koin (BOM). PROHIBIDOS Hilt/Dagger y cualquier otro contenedor.
- **Asincronía**: Corrutinas y `Flow`. PROHIBIDOS RxJava, `AsyncTask` y callbacks crudos en
  APIs internas.
- **Dependencias**: TODAS se declaran en `gradle/libs.versions.toml`. PROHIBIDO escribir una
  coordenada o una versión literal dentro de un `build.gradle.kts`. Las familias con BOM
  (Compose, Firebase, Koin) NO llevan versión en sus artefactos.
- **Capa de datos**: la elección de cliente HTTP y de persistencia queda deliberadamente
  abierta y DEBE decidirse y justificarse en el `plan.md` de la primera feature que la
  necesite. Hasta entonces `data` opera con fuentes en memoria.
- **Idioma**: el código, los nombres y los comentarios se escriben en inglés; las
  especificaciones, la documentación y la comunicación con el propietario, en español.

## Flujo de Trabajo y Puertas de Calidad

- **Ramas**: `main` es la rama estable. Cada feature vive en su rama `NNN-slug` creada por
  Spec Kit. PROHIBIDO implementar una feature directamente sobre `main`.
- **Commits**: mensaje en español, imperativo, con prefijo tipo Conventional Commits
  (`feat:`, `fix:`, `test:`, `refactor:`, `chore:`, `docs:`).
- **Puertas de calidad** — antes de dar una feature por terminada DEBEN pasar, en este orden:
  1. `./gradlew :app:assembleDebug`
  2. `./gradlew :app:testDebugUnitTest`
  3. `./gradlew :app:connectedDebugAndroidTest`
  4. `./gradlew :app:lintDebug`
- **CI**: GitHub Actions ejecuta build, tests unitarios y lint en cada push y pull request.
  Una feature con CI en rojo NO se integra en `main`.
- **Revisión**: toda integración en `main` DEBE verificar el cumplimiento de esta
  constitución. Cualquier desviación se documenta y se justifica de forma explícita en el
  `plan.md` de la feature, en su sección de complejidad.

## Governance

Esta constitución PREVALECE sobre cualquier otra práctica, convención heredada o preferencia
puntual. En caso de conflicto entre `CLAUDE.md` y este documento, manda este documento.

- **Enmiendas**: modificar esta constitución requiere (a) la aprobación explícita del
  propietario del repositorio, (b) actualizar `CLAUDE.md` en el mismo cambio para que la guía
  operativa no contradiga a la norma, y (c) registrar el cambio en el Sync Impact Report de
  la cabecera de este fichero.
- **Versionado semántico** de la constitución:
  - **MAJOR**: se elimina o se redefine un principio de forma incompatible con lo anterior.
  - **MINOR**: se añade un principio o una sección, o se amplía materialmente la guía.
  - **PATCH**: aclaraciones, redacción y correcciones sin cambio de significado.
- **Cumplimiento**: cada `/speckit-plan` DEBE incluir una comprobación de alineamiento con
  estos principios, y `/speckit-analyze` DEBE señalar cualquier incoherencia entre spec, plan
  y tasks respecto a esta constitución.
- **Complejidad**: toda desviación (una capa extra, una dependencia nueva, un patrón no
  contemplado) DEBE justificarse por escrito. Ante la duda, gana la opción más simple.
- **Guía operativa**: `CLAUDE.md` en la raíz del repositorio traduce estos principios a
  comandos y convenciones del día a día.

**Version**: 1.1.0 | **Ratified**: 2026-08-28 | **Last Amended**: 2026-08-30
