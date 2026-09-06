# Investigación — Feature 014: Estabilidad tras la auditoría

Decisiones D-601 a D-623. Formato: **Decisión / Razón / Alternativas descartadas**. La numeración sigue
la convención del proyecto (007 → D-0xx, 009 → D-1xx, 010 → D-2xx, 011 → D-3xx, 012 → D-4xx, 013 →
D-5xx).

Esta feature no tiene biblioteca nueva ni servicio nuevo. Lo que se investiga aquí es **por qué cada
promesa documentada dejaba de cumplirse en un camino de fallo, y cuál es la forma más pequeña de hacerla
verdad sin abrir otra grieta**. Cada decisión cita el hallazgo de
[`docs/auditoria/01-hallazgos.md`](../../docs/auditoria/01-hallazgos.md) que resuelve. Dos agentes de
planificación independientes revisaron el diseño antes de fijarlo; los agujeros que encontraron
—tres en los avisos, dos en la recuperación de lecturas, uno en la cancelación— están incorporados y
señalados como tales.

---

## Parte A — La copia local del documento (STAB-001, STAB-002)

### D-601 · La lectura de la caché entra en la frontera de errores, y la caché además valida

**Decisión.** Dos capas, las dos necesarias. (1) `FileDocumentCache.readChecksum()` valida lo leído y
devuelve `null` si el lateral existe pero no es una huella válida, de modo que el `?: EMPTY_CHECKSUM`
que ya existe cubre por fin «presente pero inválido» igual que «ausente». (2)
`DocumentRepositoryImpl.ensureLocalCopy` mete `cache.get(key)` dentro del manejo de errores: si lanza
—cualquier `Throwable` que no sea cancelación— se reporta y se trata como ausente, y la descarga
posterior repara la entrada.

**Razón.** La auditoría demostró que `takeIf { it.isFile }` solo produce `null` cuando el fichero no
existe: un lateral vacío devuelve `""`, que no es `null`, así que el `EMPTY_CHECKSUM` que el comentario
promete no se aplica nunca al caso que importa. La capa (1) arregla el dato. Pero la capa (2) es la que
cumple el contrato de `DocumentRepository` («aquí nada lanza»): hay **seis** puntos de llamada en la
interfaz sin `try` —`PdfViewerViewModel.ensureFetched`, `PublicationDetailViewModel.onDocumentTabShown`
y `onShare`, `HomeViewModel`/`SavedViewModel`/`SearchViewModel.onShare`— más `AiDocumentPreparer`, y
arreglar en el repositorio los cubre todos de golpe. Arreglar solo en la caché dejaría el contrato roto
para el siguiente fallo de lectura que a nadie se le ocurra hoy.

**Alternativas descartadas.** *Un `try` en cada modelo de pantalla*: seis copias del mismo `catch`, y la
séptima se olvida. *Que `DocumentCache.get` devuelva un tipo con «dañado»*: cambia una interfaz que no
lo necesita, porque la respuesta correcta a «dañado» es la misma que a «ausente»: descargar.

### D-602 · Un lateral inválido es «huella perdida», no «copia inválida», y se dice en el registro

**Decisión.** La regla de validez vive en el dominio y se usa desde la caché:
`OfficialDocument.isValidChecksum(value)` y `OfficialDocument.UNKNOWN_CHECKSUM` (los sesenta y cuatro
ceros que hoy son `EMPTY_CHECKSUM`, privados en la caché) pasan al `companion` público del modelo. Un
lateral vacío, truncado, en mayúsculas o con espacios se trata **igual que uno ausente**: la copia se
sirve con huella desconocida. Y cuando el repositorio recibe una copia con `UNKNOWN_CHECKSUM`, registra
`document: checksum sidecar unreadable, served without checksum` —la caché no tiene `CrashReporter`; el
repositorio sí—, para que el incidente conste (FR-005) sin datos de la publicación.

**Razón.** Los bytes del documento se verificaron al descargar y el fichero solo se hace visible
completo, porque se renombra atómicamente desde `.part`. Lo único que se perdió es la huella. Tratarlo
como copia inválida obligaría a volver a descargar un documento correcto. Y la regla de validez tiene que
ser **una**: hoy la regex es privada en `OfficialDocument` y la caché no la conoce; si la caché
escribiera la suya, el día que una cambiara la otra seguiría lanzando.

**Alternativas descartadas.** *Borrar la entrada y volver a descargar*: cuesta red por un fichero que
está bien. *Validar solo en el repositorio*: dejaría la caché devolviendo un `OfficialDocument` que no
puede construirse.

### D-603 · El lateral se escribe atómicamente y ANTES de renombrar el PDF

**Decisión.** `writeChecksum` escribe en `<nombre>.sha256.part` y renombra, igual que el documento. Y en
`put()` el orden pasa a ser: lateral primero, documento después; si el rename del documento falla, se
borra el lateral. `evict` retira también `.sha256.part` huérfanos.

**Razón.** Es el mecanismo que fabricaba el lateral truncado: un `writeText` a pelo que la muerte del
proceso o un disco lleno dejan a medias. Y el orden importa porque **el checksum tiene consumidor**:
`AiSummaryRepositoryImpl` lo compara con el `pdfSha256` que guardó con cada resumen para decidir si el
resumen está obsoleto. Con el orden actual hay una ventana —documento visible, lateral aún no escrito—
en la que una lectura devuelve `UNKNOWN_CHECKSUM`, marca obsoleto un resumen bueno, y regenerarlo cuesta
cuota. Lateral primero garantiza «documento visible ⇒ lateral válido» en toda entrada fresca; la única
ventana que queda (caer entre los dos renames al **sustituir** una entrada) es inalcanzable en la
práctica, porque `put` solo corre cuando `get` no encontró nada.

**Alternativas descartadas.** *Guardar la huella en la base de datos*: el KDoc de la caché ya explica
por qué no —una fila que sobrevive a su fichero es una mentira que alguien tiene que reconciliar—, y el
sistema puede vaciar la caché sin avisar.

### D-604 · Todo camino de error publica un estado terminal; la limpieza va en `settle()` bajo `NonCancellable`

**Decisión.** El `catch (unexpected: Throwable)` de `ensureLocalCopy` pasa a hacer lo mismo que su
hermano `AiSummaryRepositoryImpl.generate` (líneas 137-149): `log("document: fetch threw: <Clase>:
<mensaje>")`, `recordNonFatal`, `publish(Failed(Unknown))`, `Failure(Unknown)`. Y la limpieza posterior
—descartar el temporal, quitar la entrada de `inFlight`, completar o cancelar el `pending`— se extrae a
un `settle()` que corre bajo `withContext(NonCancellable)`, con `discardTemporary` dentro de un
`runCatching` que registra `document: cleanup failed: <Clase>` si falla.

**Razón.** La auditoría reprodujo el estado: `Failure(Unknown)` devuelto y `Downloading(0, null)`
publicado, para siempre. `PdfViewerViewModel` y `DocumentPreview` no leen el `AppResult`; solo observan
el estado, así que solo `publish(Failed)` llega a la pantalla. Y `NonCancellable` cierra un cuelgue
latente que la auditoría no listó: hoy el camino de cancelación hace `lock.withLock` **dentro de una
corrutina ya cancelada**; si tuviera que suspender por contención, lanzaría, `inFlight.remove` y
`pending.cancel` no correrían, y todo `ensureLocalCopy(key)` posterior esperaría un `Deferred` que nadie
completa, el resto del proceso. No es reproducible de forma determinista —el lock es privado y se
sostiene microsegundos—, así que es endurecimiento, no regresión reclamada; se anota como tal.

**Alternativas descartadas.** *Un `finally`*: no distingue éxito, fallo y cancelación, y el `pending`
necesita `complete` en dos casos y `cancel` en el tercero.

### D-605 · La cancelación publica `Absent`, guardado con `===`

**Decisión.** Cuando el dueño de la descarga se cancela, `settle()` publica `DocumentStatus.Absent`
**solo si** `inFlight[key] === pending` (es decir, si ningún dueño nuevo ha registrado ya su propia
descarga) y el estado vigente es `Downloading`. Nunca `Failed`: cancelar no es un fallo. KDoc de
`Absent`: «nunca pedido, expulsado de la caché, o su descarga se canceló».

**Razón.** `Downloading` debe significar que hay una descarga en vuelo. Uno rancio hace que la siguiente
visita pinte un indicador de progreso por trabajo que nadie hace, hasta que `onDocumentTabShown`
dispara. El guardián `===` evita pisar el `Downloading` de un dueño nuevo si la limpieza del viejo llega
tarde.

**Alternativas descartadas.** *Dejar el estado como está*: aceptable, pero entonces el guardián sobra y
la promesa «el estado es coherente con lo que pasa» (FR-012) queda a medias.

### D-606 · Quien espera no hereda la cancelación del dueño

**Decisión.** `ensureLocalCopy` se convierte en un bucle: quien no es dueño hace `pending.await()`; si
recibe `CancellationException`, llama a `currentCoroutineContext().ensureActive()` —si la cancelación
es la suya, se repropaga; si era la del dueño, vuelve al `lock` y se convierte en dueño—. El dueño quita
su entrada de `inFlight` **antes** de cancelar `pending`, para que el bucle encuentre el hueco.

**Razón.** No es sobreingeniería: los dos `onRetry` (`PdfViewerViewModel.kt:100`,
`PublicationDetailViewModel.kt:255`) cancelan y relanzan, y la corrutina nueva y la limpieza del dueño
viejo compiten en IO. Si la nueva gana el `lock`, se hace *waiter* de un `Deferred` a punto de
cancelarse; `await()` lanza; `viewModelScope` se traga la `CancellationException`; `fetchJob.isActive`
queda `false`; el estado es `Absent`/`Downloading` → `PdfViewerUiState.Loading`, **que no tiene botón de
reintento**. Pantalla atascada hasta salir. Son seis líneas. Y responde a la pregunta que la auditoría
dejó abierta en PERF-002: «cómo afecta cancelar un consumidor a descargas compartidas».

**Alternativas descartadas.** *Una descarga con dueño propio (un `SupervisorJob` del repositorio) que
sobreviva a las pantallas*, como hace el chat: cambia quién cancela y cuándo, que FR-037 prohíbe, y
FR-023 de la 004 dice explícitamente que abandonar la pantalla cancela. *Cuenta de referencias de
consumidores*: más estado para el mismo resultado.

---

## Parte B — Los avisos (STAB-003)

### D-607 · El trabajo pendiente vive en una columna de `publications`, no en memoria ni en una tabla-cola

**Decisión.** Nueva columna `pending_alert_evaluation INTEGER NOT NULL DEFAULT 0`, con índice, en
`publications`. Base de datos **versión 6**, `AutoMigration(5, 6)`, `6.json` exportado y versionado.
`PublicationEntity.pendingAlertEvaluation: Boolean = false`. La sincronización inserta las filas nuevas
con la marca a `true`; `updateColumns` **no** la incluye, así que una publicación que ya existía nunca se
re-marca. Las filas anteriores a la migración quedan a `0`: la historia no es novedad.

**Razón.** Sigue el precedente de `search_text`: el estado vive en la fila y «un proceso que muere a
medias retoma donde estaba». Y la protege la misma lista blanca que protege `saved_at` y
`first_seen_at`. Es contabilidad de la sincronización, como `first_seen_at`, no dato de la persona; el
límite «`PublicationDao` escribe lo que deriva de la sincronización» se respeta. Frente a una tabla-cola:
la cola tendría que escribirse en la **misma transacción** que el INSERT o la grieta reaparece, y un
`@Transaction` de Room solo ve su propio DAO, así que `PublicationDao.upsertAll` la escribiría igual;
vaciarla sería el segundo `DELETE` del proyecto (o una marca `processed` que crece sin fin); y cada
lectura de pendientes, un JOIN. Frente a memoria —lo de hoy—: es exactamente lo que se pierde.

**Alternativas descartadas.** *Un cursor escalar* («evaluado hasta el instante T» con
`first_seen_at > T`): necesita tabla propia para un escalar y se rompe si el reloj retrocede; la marca
por fila no depende de la monotonía del reloj.

### D-608 · La línea base no marca nada; no se limpia después

**Decisión.** `isBaseline` —que ya se decide **una vez, antes** de lanzar las diecinueve fuentes— baja a
`syncFeed(definition, isBaseline)` y de ahí a `toEntity(now, searchText, pendingAlertEvaluation =
!isBaseline)`. No existe ninguna sentencia «poner a cero toda la tabla».

**Razón.** Agujero encontrado en la revisión del diseño: el primer borrador limpiaba la marca al terminar
la línea base. Si el proceso muere a mitad de la primera sincronización, `markSuccess` ya corrió para los
feeds terminados, así que `lastSuccessAt != null` y el siguiente ciclo **ya no es línea base**: habría
replicado cientos de filas históricas como novedades. Decidirlo al insertar no deja ventana.

**Alternativas descartadas.** *Limpiar al terminar* (la ventana descrita). *Limpiar al empezar el
siguiente ciclo si el anterior fue línea base*: necesita recordar que lo fue, es decir, otro estado.

### D-609 · «Nunca retroactivo» se declara una vez: `AlertCandidate.isVisibleTo(rule)`

**Decisión.** Nuevo modelo de dominio `AlertCandidate(publication: Publication, storedAt: Long)` con
`fun isVisibleTo(rule: AlertRule): Boolean = rule.activeSince <= storedAt`. `storedAt` es
`first_seen_at`. El ciclo evalúa cada pareja con `candidate.isVisibleTo(rule) && matchRule(rule,
candidate.publication)`. Con `<=`, no `<`.

**Razón.** Hoy «nunca retroactivo» se cumple **por orden**: las reglas se leen antes del refresh, así
que toda publicación insertada es posterior al `activeSince` de cualquier regla de la instantánea
(012 D-405). Ese argumento no vale para un resto de un ciclo anterior: entre los dos ciclos alguien pudo
crear una regla, y evaluarla contra la publicación pendiente sería avisar de algo que ya estaba en el
archivo cuando la regla nació, rompiendo FR-040 de la 012 y los tests de integración que lo codifican.
La comparación de fechas generaliza el orden: en el camino normal `activeSince < inicio del refresh <
first_seen_at` se cumple solo, y en la recuperación excluye justo las reglas más nuevas que la
publicación. `<=` es obligatorio porque los tests de integración usan un reloj congelado en el que
`activeSince == first_seen_at`; con `<` nada dispararía jamás. `Publication` **no** gana `firstSeenAt`:
el candidato lo lleva al lado, y el modelo que usan cinco pantallas no cambia.

**Caveat, a conciencia.** El filtro introduce una dependencia del reloj que el argumento de orden no
tenía: si el reloj del dispositivo retrocede entre guardar una regla y sincronizar, una publicación
fresca podría quedar oculta a una regla legítima. Se acepta a cambio de una regla única; si algún día
importa, eximir del filtro las claves de `summary.newKeys` es una línea. **Los tests nuevos avanzan el
reloj entre ciclos**, porque con el reloj congelado el filtro es inerte y no se comprueba nada.

**Alternativas descartadas.** *Guardar el instante de la instantánea de reglas junto a la marca*: es
`first_seen_at` con otro nombre. *Dos caminos —orden para lo nuevo, fechas para lo pendiente—*: dos
reglas para un invariante son dos reglas que un día divergen.

### D-610 · Un solo camino de evaluación; se marca exactamente lo leído, también con cero coincidencias

**Decisión.** `evaluate()` lee los pendientes, calcula las coincidencias visibles, registra si hay
alguna, y **marca como evaluadas exactamente las claves que leyó** —también cuando no hubo ninguna
coincidencia, también cuando no había reglas—. Si `recordMatches` falla, **no** marca y termina con
`AlertDelivery.NONE`. Si el marcado falla tras registrar, **entrega igualmente** y lo registra: las
coincidencias ya están grabadas y saltarse la entrega las haría inentregables para siempre (el siguiente
ciclo vería la pareja ya registrada); el siguiente ciclo reintenta el marcado y `recordMatches` no
devuelve nada nuevo. No existe ninguna sentencia «limpiar todo lo pendiente».

**Razón.** Agujero encontrado en la revisión: el primer borrador limpiaba en bloque cuando no había
reglas. Eso tenía una carrera con un ciclo concurrente —Inicio y el Worker pueden coincidir— que
hubiera insertado filas después de nuestra lectura, y se habría disparado también cuando
`enabledRules` fallara y devolviera lista vacía, que es exactamente la clase de pérdida que se está
arreglando. Marcar solo lo leído no tiene carrera. Y marcar con cero coincidencias es necesario: si no,
las publicaciones que no coincidieron con nadie quedarían pendientes para siempre y solo el filtro de
fechas las protegería de una regla futura.

### D-611 · `recordMatches` sin troceado, y las dos operaciones del ciclo devuelven `AppResult`

**Decisión.** `AlertRepositoryImpl.recordMatches` hace **una** llamada a `matchDao.insert(todos)` y
devuelve `AppResult<List<AlertMatch>>`; `enabledRules()` devuelve `AppResult<List<AlertRule>>`. Se retira
el `chunked(SQLITE_VARIABLE_LIMIT)` de ese método.

**Razón.** Hallazgo de la revisión: `@Insert(List)` ya corre dentro de una transacción de Room, así que
una sola llamada es todo-o-nada; el bucle de trozos de 900 era **justamente lo que rompía la
atomicidad** (el trozo 1 confirmaba, el 3 lanzaba, y las parejas del 1 quedaban registradas pero sin
entregar, y el índice único las escondía para siempre). El límite de 900 protege las listas `IN (...)`,
no las inserciones. No hace falta un `@Transaction` nuevo. Y el `AppResult` es lo que permite al ciclo
distinguir «no hubo coincidencias» de «no pude registrarlas»: hoy las dos son `emptyList()`.

**Regresión que lo demuestra.** 901 candidatos, el último con una `rule_id` inexistente. `INSERT OR
IGNORE` no ignora violaciones de clave ajena, y Room las tiene activadas (la prueba de cascada de
`AlertRuleDaoTest:117` lo demuestra). Antes: 900 filas y `emptyList()`. Después: cero filas y `Failure`.
Si en la primera ejecución resultara que la FK no lanza bajo `OR IGNORE`, la alternativa es afirmar solo
el `Failure` con un DAO que lanza.

### D-612 · Un refresh omitido evalúa restos; si las reglas no se leen, el refresh sigue

**Decisión.** Cuando `RefreshPublicationsUseCase` devuelve `SKIPPED` (copia fresca, sin red), el ciclo
**sí** lee y evalúa los pendientes. Y si `enabledRules()` falla, el ciclo hace el refresh de todos modos
—el boletín tiene que actualizarse— y aplaza la evaluación sin tocar las marcas, registrando `cycle:
rules unreadable, evaluation deferred`.

**Razón.** El ciclo con copia fresca es el más frecuente —abrir Inicio dentro de la media hora—, es
exactamente el camino «reabrir tras la muerte del proceso», cuesta una consulta indexada y nunca puede
ser línea base (`SKIPPED` implica un éxito anterior). Y una lectura de reglas fallida no es motivo para
dejar el boletín viejo.

**Consecuencia sobre pruebas existentes.** `a skipped refresh evaluates nothing` sigue verde (el fake no
tiene nada pendiente) pero pasa a llamarse por lo que hace: evalúa solo lo que quedó pendiente.

### D-613 · `byKeys` se retira; `newKeys` se queda como recuento e historia

**Decisión.** `PublicationRepository.byKeys` desaparece del contrato, la implementación, el DAO y el
fake: su único consumidor en producción era el ciclo. `SyncSummary.newKeys` se conserva —lo usan la
analítica, los tests y el registro— pero su KDoc deja de decir que los avisos se evalúan contra ellas.

**Razón.** Código muerto que dice lo contrario del código vivo es la peor documentación posible.

---

## Parte C — Las listas observadas (STAB-004)

### D-614 · Un operador de recuperación con presupuesto que un éxito repone

**Decisión.** `data/repository/ReadRecovery.kt` expone `internal fun <T> Flow<T>.recoverReads(fallback:
T, name: String, crashReporter: CrashReporter, delays: List<Long> = READ_RETRY_DELAYS)` con
`READ_RETRY_DELAYS = listOf(1_000L, 5_000L, 30_000L)`. Construido como `flow { emitAll(upstream.onEach {
consecutive = 0 }.retryWhen { … }.catch { … }) }`: al fallar, si es `CancellationException` la
repropaga **antes de nada**; si no, `recordNonFatal`, `emit(fallback)`, `log("reads: <name> failed:
<Clase>, retry in <ms>ms")`, `delay` y **se vuelve a suscribir** al upstream. Un éxito pone el contador a
cero. Agotados los tres reintentos, `log("reads: <name> gave up after 3 retries")` y el flujo **completa
en silencio** con el fallback ya emitido. Sustituye los nueve `.catch` y los tres
`emitEmptyAfterReporting`/`emitEmptyAfterRecording`.

**Razón.** `catch` se ejecuta cuando el upstream ya ha terminado; emitir un fallback no reanuda el
Flow de Room. Solo `retryWhen` re-colecciona. El `flow { emitAll }` exterior es necesario porque el
mismo objeto `Flow` se colecciona muchas veces —cada rearranque de `WhileSubscribed`—: el contador tiene
que vivir **por colección**. La transparencia de excepciones se respeta porque `retryWhen` y `catch`
comparten `catchImpl`, que repropaga intactas las excepciones del colector; un `try/catch` casero
alrededor de `collect` sería ilegal. La guarda de `CancellationException` es necesaria: `retryWhen` solo
omite la cancelación **del colector**; una lanzada por el upstream mientras el colector sigue activo
(Room cerrando su `withContext`, un doble de prueba) sí llega al predicado y no debe ni reportarse ni
reintentarse. Y completar en silencio, frente a quedarse suspendido: para `combine`/`stateIn` es
indistinguible —una entrada completada conserva su último valor—, es observable en una prueba con
`awaitComplete()`, y no deja ninguna corrutina aparcada para siempre. Acotado a tres: la auditoría pide
no reintentar sin fin una corrupción permanente.

**Alternativas descartadas.** *Reintento sin fin con espera tope*: una consulta por minuto para siempre
sobre una base corrupta. *Resuscribirse al escribir* (una «generación» que cada escritura incrementa y un
`flatMapLatest`): determinista, pero no cubre un fallo transitorio sin escritura posterior, y los
organismos de Buscar los escribe otro repositorio. *Cambiar el tipo a `Flow<AppResult<…>>`*: ver D-616.

### D-615 · `recoverReads` va SIEMPRE después de `flowOn(dispatchers.io)`

**Decisión.** El operador es el último de la cadena, tras `.flowOn(dispatchers.io)`.

**Razón.** Así el `delay` corre en el contexto del **colector** —`viewModelScope` en producción, el
planificador de `runTest` en las pruebas—. `TestDispatcherProvider()` construye un
`UnconfinedTestDispatcher()` con su **propio** `TestCoroutineScheduler`: un `delay` bajo `flowOn(io)`
viviría en un planificador que `runTest` nunca avanza, `advanceTimeBy` no le afectaría y Turbine
agotaría su tiempo de espera real. Es exactamente por lo que el backoff de
`OkHttpPublicationRemoteDataSource` funciona hoy: su `delay` está fuera del `withContext(io)`. La
resuscripción sigue ejecutando el DAO en io, porque `retryWhen` re-colecciona el upstream con su `flowOn`
incluido.

### D-616 · No se distingue en pantalla «vacío» de «fallo de lectura»

**Decisión.** Del propietario (6 de septiembre de 2026). Los tipos de los nueve Flows no cambian; la
pantalla sigue mostrando vacío mientras dura el fallo.

**Lo que costaría, por si algún día se quiere.** Un `Flow<AppResult<T>>` (o un `ReadState<T>`) en nueve
flujos de cuatro repositorios; los `combine` de seis modelos de pantalla y un campo `readError` en seis
estados; cinco pantallas con un aviso «no se ha podido leer» y su reintento; un disparador de
resuscripción explícito; y unos catorce fakes y pruebas, varias instrumentadas a 46 s cada una.

---

## Parte D — La cancelación de red (PERF-002)

### D-617 · `Call.await`: `enqueue` + `invokeOnCancellation { cancel() }`, con el cuerpo consumido dentro del callback

**Decisión.** `data/source/remote/CancellableCall.kt` expone `internal suspend fun <T>
Call.await(consume: (Response) -> T): T`, sobre `suspendCancellableCoroutine`: registra
`invokeOnCancellation { cancel() }` y llama a `enqueue`; en `onResponse` hace `response.use(consume)` y
reanuda con el resultado; en `onFailure` reanuda con la `IOException`. Los ocho sitios que hoy hacen
`client.newCall(request).execute().use { … }` pasan a `client.newCall(request).await { … }`. `consume`
no es `suspend`; los cuatro cuerpos `.use {}` actuales ya cumplen.

**Razón.** Cambiar de dispatcher no convierte una E/S bloqueante en cancelable: la auditoría midió
`Call.isCanceled=false` y `Job.isCompleted=false` tras cancelar. Solo `Call.cancel()` cierra el socket, y
solo `invokeOnCancellation` lo llama en el momento justo. Consumir el cuerpo **dentro** de `onResponse`
—que es donde OkHttp documenta que hay que leerlo— es lo que hace que la cancelación cubra también la
lectura: en el descargador el cuerpo son hasta 25 MB a disco, y `cancel()` hace que el `read` lance en
cuanto el socket se cierra. `cont.resume` tras la cancelación es un no-op silencioso (una vez, y OkHttp
llama a exactamente uno de los dos callbacks), así que no hace falta comprobar `isActive` ni el `resume`
de dos argumentos: `T` no lleva recursos, porque la `Response` ya se cerró dentro del `use`.

**Alternativas descartadas.** *`com.squareup.okhttp3:okhttp-coroutines` y su `executeAsync()`*: cubre
solo la fase de cabeceras —devuelve la `Response` con el cuerpo sin leer—, así que la descarga de 25 MB
seguiría sin cancelarse; y añade una dependencia por treinta líneas. *`runInterruptible`*: OkHttp no
responde a la interrupción del hilo. *`Job.invokeOnCompletion(onCancelling = true)`*: es API interna de
corrutinas.

### D-618 · Nada puede escapar de `onResponse`

**Decisión.** Dentro de `onResponse`, `response.use(consume)` va en un `try { … } catch (t: Throwable) {
cont.resumeWithException(t); return }`. Tiene su prueba: una excepción al consumir llega al llamante y
**nunca** al hilo de OkHttp (la prueba instala temporalmente un `Thread.setDefaultUncaughtExceptionHandler`
que registra).

**Razón.** `RealCall.AsyncCall.run` trata una excepción que no sea `IOException` saliendo del callback
como fatal: cancela la llamada y **la relanza en el hilo del executor**, que en Android es una excepción
no capturada y un cierre del proceso. El `catch` no es defensivo: es lo que impide que un error de
parseo nuestro mate la aplicación. Si alguien lo retira, la prueba se pone roja.

### D-619 · La semántica de cancelación no cambia; el límite por servidor se acepta

**Decisión.** Quién cancela y cuándo sigue igual: el detalle y el visor al salir (004 FR-023), el
resumen al salir (007 FR-006), la conversación solo al salir de la publicación (011, con su
`SupervisorJob` propio), la sesión del documento con su ámbito de limpieza. Esta feature hace **real**
la cancelación que ya existía. Y se acepta un cambio de comportamiento benigno: `execute()` ignora los
límites del `Dispatcher` de OkHttp; `enqueue` respeta `maxRequestsPerHost = 5` del cliente compartido (los
derivados con `newBuilder()` lo conservan). Peor caso contra `boc.cantabria.es`: cuatro fuentes más un
documento; un segundo documento durante una sincronización **espera en cola** unos instantes en vez de
fallar. No se sube el límite ahora.

**Razón.** FR-037. Y subir un límite sin haber medido que hace falta es exactamente lo que la
auditoría pide no hacer.

### D-620 · `ensureActive()` también en el descargador y la RSS; `delete()` repropaga

**Decisión.** Los `catch (IOException)` del descargador y de la RSS ganan
`currentCoroutineContext().ensureActive()` como primera línea, como ya tienen los tres de Gemini. En la
RSS, el `catch (SocketTimeoutException)` —subclase de `IOException`— se funde en un solo `catch
(IOException) { ensureActive(); if (error is SocketTimeoutException) TIMEOUT else NETWORK }`.
`OkHttpGeminiDocumentUploader.delete()` sustituye su `runCatching` por un `try` que repropaga
`CancellationException` y registra el resto; el KDoc de `AiDocumentUploader.delete` pasa a decir «nunca
lanza, salvo cancelación» (en ejecución no cambia nada: el ámbito `cleanup` del almacén de sesión nunca se
cancela).

**Razón.** Hoy una cancelación en la RSS se reporta como `NETWORK`, que **es reintentable**: cancelar
puede costar hasta tres intentos. El `callTimeout` de OkHttp sigue saliendo como
`InterruptedIOException("timeout")`: no es nuestra cancelación, `ensureActive()` pasa y se clasifica como
red/timeout, que es lo correcto (FR-035).

### D-621 · Los restos de una descarga cancelada los borra el repositorio, no el descargador

**Decisión.** El descargador **no** trunca el temporal en su `catch (CancellationException)`. Lo borra
`DocumentRepositoryImpl.settle()`, bajo `NonCancellable`.

**Razón.** Tras cancelar, el hilo de OkHttp puede seguir dentro de `writeVerified` unos milisegundos,
hasta que el socket cerrado haga lanzar al `read`. Truncar desde la corrutina mientras otro hilo escribe
es una carrera; borrar el fichero no lo es: en Linux el escritor sigue en un inodo desenlazado y el
fichero desaparece con él. FR-039 y FR-019 de la 004 se cumplen desde el sitio que ya tenía esa
responsabilidad.

---

## Parte E — Pruebas y documentación

### D-622 · Las reproducciones de la auditoría son las pruebas de regresión, y tres dobles nuevos

**Decisión.** Cada defecto se prueba **como lo reprodujo la auditoría**: lateral truncado con la caché
real; `put` que lanza; ciclo con registro fallido seguido de ciclo con fuentes sin cambios; llamada
bloqueada por un interceptor y `Call.isCanceled`. Tres dobles de prueba nuevos: `TlsMockWebServer`
(regla `ExternalResource` con servidor TLS y cliente que confía; el bloque está copiado en cinco tests y
el sexto es el nuevo del uploader), `FailingOnceAlertMatchDao(delegate) by delegate` (falla N veces y
después delega, para el test de integración con Room real) y, dentro de `ReadRecoveryTest`, un
`FlakyFlow` que cuenta suscripciones **dentro** del `flow { }`.

**Razón.** `retryWhen` re-colecciona el **mismo objeto** `Flow`, así que un `returnsMany` de MockK no
puede modelar «falla y luego funciona» y un `flow { throw }` está roto para siempre: los dobles tienen
que contar colecciones. Los tests actuales de la rama `.catch` usan `.first()`, que toma el primer valor
y cancela, y por eso nunca vieron la terminación: se reescriben con Turbine. Y `OkHttpGeminiDocumentUploader`
**no tiene ninguna prueba hoy**; con cuatro llamadas que migran, la gana.

### D-623 · Qué decisiones anteriores quedan superadas, y se dice donde vivían

**Decisión.** Se añade una nota al final de `specs/012-avisos/research.md`: D-401 («las claves las
transporta `SyncSummary.newKeys` y el ciclo las lee con `byKeys`») y D-405 («nunca retroactivo por el
orden, sin comparar fechas») quedan **superadas** por D-607 y D-609 de esta feature. La lección de la 009
(D-119) y la 010 (D-218) —«cancelar una corrutina no interrumpe una llamada bloqueante; `ensureActive()`
como primera línea del `catch`»— **se mantiene y se completa**: decía cómo clasificar una cancelación;
D-617 hace que además la detenga. `CLAUDE.md` se actualiza en la misma implementación.

**Razón.** Una decisión superada que nadie marca como tal es la próxima auditoría.
