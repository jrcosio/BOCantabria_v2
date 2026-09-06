# Implementation Plan: Estabilidad tras la auditoría — lo prometido, cumplido también cuando algo falla

**Branch**: `014-estabilidad-auditoria` | **Date**: 6 de septiembre de 2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-estabilidad-auditoria/spec.md`

---

## Summary

Cinco correcciones de estabilidad derivadas de la auditoría técnica
([`docs/auditoria/01-hallazgos.md`](../../docs/auditoria/01-hallazgos.md)). **Toda la feature vive en
`data` y en `domain`; `ui` no se toca**: ni una pantalla, ni un texto, ni una ruta. Hay una migración de
base de datos (versión 6, una columna) y ninguna dependencia nueva. El resumen técnico cabe en cinco
frases:

1. **La copia local del documento deja de poder cerrar la aplicación** (STAB-001). La caché valida el
   lateral de verificación con la regla del modelo y lo escribe atómicamente **antes** de hacer visible
   el PDF; el repositorio mete la lectura de la caché en su frontera de errores. Dos capas, y la segunda
   cubre de golpe los seis puntos de la interfaz que hoy llaman sin `try`.
2. **Todo camino de error del documento publica un estado terminal** (STAB-002). El `catch` genérico
   publica `Failed`, la limpieza corre bajo `NonCancellable`, la cancelación deja `Absent`, y quien
   espera una descarga que otro canceló la retoma en vez de morir en silencio.
3. **El trabajo pendiente de los avisos vive en el almacén** (STAB-003). Una columna
   `pending_alert_evaluation` que nace con la publicación (a `0` en la línea base), se lee al empezar la
   evaluación y se retira **solo** cuando las coincidencias quedaron registradas. Un modelo nuevo,
   `AlertCandidate.isVisibleTo(rule)`, declara «nunca retroactivo» una vez y lo extiende a lo recuperado.
   `recordMatches` pierde el troceado que rompía su atomicidad.
4. **Un operador sustituye a los nueve `.catch`** (STAB-004). `recoverReads` reintenta tres veces con
   espera creciente, repone el presupuesto con cada éxito y completa en silencio al agotarse. Va siempre
   después de `flowOn`.
5. **La cancelación llega al socket** (PERF-002). `Call.await` sobre `enqueue` +
   `invokeOnCancellation { cancel() }`, con el cuerpo consumido dentro del callback y un `catch` total
   que impide que un error nuestro mate el proceso. Ocho sitios migran; la semántica de quién cancela no
   cambia.

Las veintitrés decisiones están en [research.md](./research.md), D-601 a D-623. Dos agentes de
planificación independientes revisaron el diseño antes de fijarlo; los seis agujeros que encontraron
(D-604, D-608, D-610, D-611, D-615, D-618) están incorporados.

---

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11, `minSdk 28` / `targetSdk` 37

**Primary Dependencies**: Room 2.8.4 (KSP), OkHttp 5.5.0 (BOM), kotlinx-coroutines 1.11.0, Koin 4.2.2
(BOM). **Ninguna nueva.** `com.squareup.okhttp3:okhttp-coroutines` se evaluó y se descartó (D-617). El
catálogo `gradle/libs.versions.toml` no se toca

**Storage**: Room pasa de la **versión 5 a la 6**: una columna `pending_alert_evaluation INTEGER NOT
NULL DEFAULT 0` con índice en `publications`, `AutoMigration(5, 6)` contra los esquemas exportados,
`6.json` versionado. Sin relleno: las filas anteriores quedan a `0` por el `DEFAULT`, que es exactamente
lo que significa «la historia no es novedad». La caché de documentos en disco gana un `.sha256.part`
transitorio y cambia el orden de escritura

**Testing**: JUnit 4, MockK, `kotlinx-coroutines-test`, Turbine, Robolectric con Room en memoria
(`@Config(sdk = [36])`), MockWebServer con TLS (`okhttp-tls`), Konsist (`ArchitectureRulesTest`). **Sin
pruebas instrumentadas nuevas**: no cambia ninguna pantalla

**Target Platform**: Android 9 (API 28) en adelante

**Project Type**: aplicación Android de módulo único (`:app`), arquitectura limpia + MVVM

**Performance Goals**: cancelar libera la conexión en el orden de un segundo (hoy hasta 180 s); una
lista se recupera de un fallo transitorio en ≤ 2 s; el ciclo de avisos añade una consulta indexada
(`WHERE pending_alert_evaluation = 1`) y un `UPDATE … IN (…)` troceado a 900

**Constraints**: ningún cambio visible; **por decisión del propietario, STAB-004 solo recupera la
observación** —la pantalla sigue mostrando vacío mientras dura el fallo (D-616)—; ningún dato personal
en registros (títulos, claves, palabras, nombres de reglas, texto, credenciales); `updateColumns` **no**
gana la columna nueva; ninguna sentencia `DELETE` nueva; ningún constructor cambia (el grafo de Koin no
se toca); `CancellationException` siempre repropagada; `consume` de `Call.await` no suspende;
`recoverReads` siempre el último operador

**Scale/Scope**: 1 modelo de dominio nuevo (`AlertCandidate`), 2 interfaces de dominio modificadas, 1 caso
de uso modificado, 2 operadores nuevos en `data`, 4 repositorios y 1 caché modificados, 5 data sources
remotos modificados, 2 DAO y 1 entidad modificados, 1 migración; ~13 clases de prueba modificadas, 5
nuevas, 4 dobles nuevos o ampliados; ~45 pruebas nuevas

---

## Constitution Check

*Puerta obligatoria antes de la fase 0 y revisada de nuevo tras la fase 1.*

| Principio | Cómo se cumple | Veredicto |
|---|---|---|
| **I — SDD, no negociable** | `specify` → `plan` → `tasks` → `implement`. Rama `014-estabilidad-auditoria` creada por Spec Kit sobre `main` con la 013 fusionada (`ee88d24`). Ninguna línea de producto antes de `tasks.md`. La preparación exenta (`.gitignore` de la carpeta de auditoría) es configuración | ✅ |
| **II — Arquitectura limpia por capas** | `AlertCandidate` es Kotlin puro en `domain/model`; los cambios de contrato son en interfaces de `domain/repository`; todo lo que toca Room, OkHttp y ficheros vive en `data`. `ui` no se toca, así que «ui no depende de data» sigue trivialmente. `ReadRecovery` y `CancellableCall` son `internal` en `data` | ✅ |
| **III — MVVM** | Ningún modelo de pantalla, estado ni componible cambia. Los seis puntos de llamada sin `try` de la interfaz se protegen **desde el repositorio**, no añadiendo lógica a los `ViewModel` | ✅ |
| **IV — Koin** | Ningún constructor cambia: `DataModule`, `DomainModule` y `KoinModulesTest` no se tocan. Si al implementar hiciera falta un parámetro, va al módulo **y** al test del grafo en la misma tarea | ✅ |
| **V — Testing exigente, no negociable** | Cada uno de los cinco defectos se reproduce **como lo reprodujo la auditoría** en una prueba que falla antes del arreglo (FR-040; tabla en `contracts/internal-contracts.md` §6). Konsist exige `AlertCandidateTest` y se añade. `OkHttpGeminiDocumentUploader`, hoy sin pruebas, gana su clase. Ninguna prueba se desactiva; las que se renombran o reescriben cambian porque cambia lo que describen, y `research.md` D-612 y D-622 lo dejan escrito | ✅ |
| **VI — Observabilidad desacoplada** | Solo `CrashReporter.log`/`recordNonFatal` desde `data` y `domain/usecase` (el ciclo ya lo hacía). Prefijos nuevos `document:` y `reads:`, líneas nuevas `cycle:`; siempre recuentos, clases de excepción y enumerados. Cinco pruebas de privacidad existentes siguen vigilando la cola `match(es) on … delivery=` | ✅ |

**Restricciones tecnológicas**: sin dependencias nuevas; corrutinas y `Flow` (nada de callbacks crudos en
APIs internas: `Call.await` **envuelve** el callback de OkHttp en una función suspendida, que es
exactamente lo que la constitución pide); Room como persistencia, decidida en la 003; catálogo intacto.

**Konsist**: las nueve reglas siguen en verde. Toda clase de dominio de nivel superior nueva tiene su
prueba (`AlertCandidateTest`); ningún fichero fuera de `data` nombra OkHttp ni Room.

**Puertas de calidad**: las cuatro de siempre, en orden, con `navigation_mode 0` antes de la tanda
instrumentada y un único dispositivo conectado.

**Sin violaciones que justificar.** Una migración de base de datos no es una desviación: es el mecanismo
que la propia guía operativa prescribe para una columna nueva. La sección de complejidad queda vacía a
propósito.

---

## Project Structure

### Documentation (this feature)

```text
specs/014-estabilidad-auditoria/
├── spec.md                        44 FR, 10 SC, 5 historias
├── plan.md                        este fichero
├── research.md                    D-601 … D-623
├── data-model.md                  la columna, la migración, los contratos, la caché en disco
├── contracts/
│   └── internal-contracts.md      interfaces antes/después, operadores, SQL, registro, dobles, pruebas
├── quickstart.md                  las cuatro puertas, las reproducciones y el recorrido manual
├── checklists/
│   └── requirements.md            calidad de la especificación
└── tasks.md                       lo genera /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── domain/model/
│   ├── AlertCandidate.kt              NUEVO       publication + storedAt; isVisibleTo(rule)
│   ├── OfficialDocument.kt            MODIFICADO  companion público: isValidChecksum, UNKNOWN_CHECKSUM
│   ├── DocumentStatus.kt              MODIFICADO  KDoc de Absent
│   └── SyncSummary.kt                 MODIFICADO  KDoc de newKeys
├── domain/repository/
│   ├── PublicationRepository.kt       MODIFICADO  − byKeys; + pendingAlertCandidates, + markAlertsEvaluated
│   ├── AlertRepository.kt             MODIFICADO  enabledRules y recordMatches → AppResult; KDoc
│   └── DocumentRepository.kt          MODIFICADO  KDoc: dos invariantes nuevos
├── domain/usecase/
│   └── RunSyncCycleUseCase.kt         MODIFICADO  evaluate() lee pendientes; marca tras registrar
├── data/repository/
│   ├── ReadRecovery.kt                NUEVO       recoverReads()
│   ├── DocumentRepositoryImpl.kt      MODIFICADO  frontera, settle(), Absent, bucle del waiter
│   ├── PublicationRepositoryImpl.kt   MODIFICADO  pending al insertar; dos métodos nuevos; − byKeys; recoverReads
│   ├── AlertRepositoryImpl.kt         MODIFICADO  AppResult; una sola inserción; recoverReads
│   ├── SavedPublicationRepositoryImpl.kt  MODIFICADO  recoverReads
│   └── SearchRepositoryImpl.kt        MODIFICADO  recoverReads
├── data/source/local/
│   ├── FileDocumentCache.kt           MODIFICADO  readChecksum valida; writeChecksum atómico y primero
│   ├── DocumentCache.kt               MODIFICADO  KDoc de get
│   ├── PublicationEntity.kt           MODIFICADO  + pendingAlertEvaluation; + índice; toEntity(…, pending)
│   ├── PublicationDao.kt              MODIFICADO  + pendingAlertEvaluation(), + markAlertsEvaluated(); − byKeys
│   └── BocDatabase.kt                 MODIFICADO  version = 6; AutoMigration(5, 6)
├── data/source/remote/
│   ├── CancellableCall.kt             NUEVO       Call.await()
│   ├── OkHttpDocumentDownloader.kt    MODIFICADO  await; ensureActive(); sin truncar al cancelar
│   ├── OkHttpPublicationRemoteDataSource.kt  MODIFICADO  await; un solo catch IOException con ensureActive
│   ├── OkHttpGeminiSummaryDataSource.kt      MODIFICADO  await
│   ├── OkHttpGeminiChatDataSource.kt         MODIFICADO  await
│   ├── OkHttpGeminiDocumentUploader.kt       MODIFICADO  await ×4; delete repropaga cancelación
│   └── AiDocumentUploader.kt          MODIFICADO  KDoc de delete
app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/
└── 6.json                             NUEVO       exportado por la build

app/src/test/java/com/jrblanco/boccantabria/
├── domain/model/AlertCandidateTest.kt                 NUEVO
├── domain/model/OfficialDocumentTest.kt               AMPLIADO
├── domain/usecase/RunSyncCycleUseCaseTest.kt          AMPLIADO (6 nuevas, 4 renombradas)
├── data/repository/ReadRecoveryTest.kt                NUEVO
├── data/repository/DocumentRepositoryImplTest.kt      AMPLIADO (7 nuevas, 1 reescrita)
├── data/repository/PublicationRepositoryImplTest.kt   AMPLIADO
├── data/repository/AlertRepositoryImplTest.kt         AMPLIADO
├── data/repository/SavedPublicationRepositoryImplTest.kt  REESCRITA la de lectura fallida
├── data/repository/SearchRepositoryImplTest.kt        REESCRITAS dos; fake con failFor
├── data/source/local/FileDocumentCacheTest.kt         AMPLIADO
├── data/source/local/PublicationDaoTest.kt            AMPLIADO
├── data/source/local/BocDatabaseMigrationTest.kt      AMPLIADO (5→6, 1→6)
├── data/source/remote/CancellableCallTest.kt          NUEVO
├── data/source/remote/OkHttpGeminiDocumentUploaderTest.kt  NUEVO
├── data/source/remote/OkHttp*DataSourceTest.kt, OkHttpDocumentDownloaderTest.kt  AMPLIADOS (cancelación)
├── integration/AlertFlowIntegrationTest.kt            AMPLIADO (2 nuevas)
└── fake/
    ├── FakePublicationRepository.kt                   AMPLIADO
    ├── FakeAlertRepository.kt                         AMPLIADO
    ├── FailingOnceAlertMatchDao.kt                    NUEVO
    └── TlsMockWebServer.kt                            NUEVO

CLAUDE.md                              MODIFICADO  lecciones de la feature (ver Fase 3)
specs/012-avisos/research.md           MODIFICADO  nota: D-401 y D-405 superadas por D-607 y D-609
.gitignore                             MODIFICADO  (preparación) solo los .md de docs/auditoria
```

**Structure Decision**: módulo único `:app` con separación por paquetes, la del proyecto desde la feature
001. No se crea ningún paquete. Los dos ficheros de producto nuevos en `data` son operadores
transversales de su capa: `ReadRecovery.kt` junto a los repositorios que lo usan, `CancellableCall.kt`
junto a los data sources que lo usan. `AlertCandidate` va a `domain/model` porque el ciclo —un caso de
uso— es quien lo consume.

---

## Fases

### Fase 0 — Investigación *(hecha)*

Veintitrés decisiones en [research.md](./research.md), D-601 a D-623. Las seis que salieron de la
revisión del diseño y cambiaron el borrador:

- **D-604**: la limpieza de `ensureLocalCopy` va bajo `NonCancellable`; cierra un cuelgue latente del
  camino de cancelación que la auditoría no listó.
- **D-608**: la línea base **no marca** en vez de limpiar después; limpiar después tenía una ventana de
  muerte del proceso que habría convertido el histórico en novedades.
- **D-610**: un solo camino de evaluación, sin sentencia masiva «si no hay reglas, limpiar»: tenía una
  carrera con el ciclo concurrente y se disparaba cuando `enabledRules` fallaba.
- **D-611**: el troceado de 900 en `recordMatches` **era** el defecto de atomicidad; una sola inserción ya
  es una transacción.
- **D-615**: `recoverReads` después de `flowOn`, o el `delay` vive en un planificador que `runTest` no
  avanza.
- **D-618**: el `catch` total dentro de `onResponse` es obligatorio; sin él un error de parseo nuestro
  mata el proceso.

### Fase 1 — Diseño *(hecha)*

- [data-model.md](./data-model.md) — la columna y su semántica tabulada (quién la escribe y cuándo), la
  migración, los contratos de dominio, las transiciones de `DocumentStatus` con las aristas nuevas, la
  caché en disco con su orden de escritura, el presupuesto de recuperación por colección y los ocho
  sitios de red.
- [contracts/internal-contracts.md](./contracts/internal-contracts.md) — las interfaces antes y después,
  las firmas de los dos operadores con sus invariantes, el SQL nuevo, el pseudocódigo del ciclo, las
  líneas de registro, los dobles de prueba y la tabla de las ~45 pruebas con lo que cada una demuestra.
- [quickstart.md](./quickstart.md) — las cuatro puertas, cómo exportar `6.json`, la tabla que empareja
  cada línea de los diagnósticos de la auditoría con la prueba que la convierte en regresión, y el
  recorrido manual de los dos hallazgos que se pueden provocar a mano.

### Fase 2 — Tareas

`/speckit-tasks` descompone por historia de usuario. Orden recomendado **US1+US2 → US3 → US4 → US5**:
de menos a más ficheros, y US5 es el único que cambia el hilo en que corre el código. Las cinco historias
son independientes; US1 y US2 comparten `DocumentRepositoryImpl` y `FileDocumentCache` y se implementan
seguidas. En cada tarea, **la prueba se escribe y se ve fallar antes** que el código (FR-040).

### Fase 3 — Implementación

`/speckit-implement`. Cierre con las cuatro puertas en verde y la actualización de `CLAUDE.md`:

- Capa de datos: BD en versión 6; la columna `pending_alert_evaluation` **fuera** de `updateColumns` y el
  test que lo guarda; «nunca retroactivo» por orden **y** por `isVisibleTo`; `recordMatches` es una sola
  inserción; el ciclo evalúa lo pendiente del almacén, no `newKeys`.
- El documento oficial: lateral validado con la regla del modelo y escrito atómicamente antes que el PDF;
  `settle()` bajo `NonCancellable`; `Absent` tras cancelar; el *waiter* retoma.
- Lecturas: `recoverReads`, tres reintentos, siempre después de `flowOn`; prefijo `reads:`.
- Red: `Call.await`, «nada escapa de `onResponse`», `ensureActive()` en los cinco, la cola de
  `maxRequestsPerHost`; y la lección de la 009/010 completada.
- Trampas de prueba nuevas: `retryWhen` re-colecciona el mismo objeto (los dobles cuentan
  suscripciones); un reloj congelado hace inerte el filtro de fechas; `.first()` no ve la terminación de
  un `Flow`.
- Las líneas de ejemplo de `cycle:` y las nuevas de `document:` y `reads:`.

---

## Riesgos, con su salida

| Riesgo | Salida |
|---|---|
| La migración 5→6 falla en un dispositivo con datos | `AutoMigration` con `defaultValue` es el caso que Room resuelve entero; `BocDatabaseMigrationTest` la prueba desde la 5 y desde la 1. Nunca `fallbackToDestructiveMigration` |
| `6.json` no se exporta o se versiona obsoleto | `quickstart.md` §2: se genera con `kspDebugKotlin`, se inspecciona y se añade al commit; la prueba de migración lanza al abrir si falta |
| Los 30 tests de `AlertFlowIntegrationTest` y `RunSyncCycleUseCaseTest` codifican «nunca retroactivo» con reloj congelado | D-609 exige `<=`, que los deja verdes; los tests nuevos **avanzan el reloj** para que el filtro se compruebe de verdad |
| La prueba de atomicidad por FK (901 candidatos) no lanza bajo `INSERT OR IGNORE` | Alternativa lista: afirmar el `Failure` con un DAO que lanza (D-611) |
| `recoverReads` bajo `flowOn` y Turbine agotando su espera real | D-615: siempre el último operador; `ReadRecoveryTest` lo detecta al primer intento |
| `enqueue` respeta `maxRequestsPerHost = 5` y un segundo PDF durante la sincronización espera | Benigno y documentado (D-619); no se sube el límite sin medir |
| Aserciones corriendo en el hilo de OkHttp bajo `UnconfinedTestDispatcher` | MockWebServer es seguro entre hilos; los tests de cancelación usan dispatchers reales, como los dos que ya existen |
| Alguien retira el `catch` total de `onResponse` | `CancellableCallTest` instala un manejador de excepciones no capturadas y se pone rojo |
| Alguien añade `pending_alert_evaluation` a `updateColumns` y toda corrección re-avisa | La guarda nueva de `PublicationDaoTest`, nombrada en `CLAUDE.md` junto a la de `saved_at` |
| Los diagnósticos Java de la auditoría dejan de compilar (`DiagnosticoAvisos` usa `byKeys`) | Esperado y anotado en `quickstart.md` §3: no se versionan; la prueba de integración con Room real es la comprobación durable |
| La pérdida residual entre registrar y entregar | Sin cambio respecto a hoy: la novedad y el badge están grabados; solo la notificación del sistema se pierde. Documentado en la spec (edge cases) |

---

## Complexity Tracking

Sin violaciones de la constitución. Tabla vacía a propósito.
