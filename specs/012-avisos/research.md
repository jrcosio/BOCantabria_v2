# Research: Avisos

**Feature**: `012-avisos` | **Fecha**: 6 de septiembre de 2026

Las decisiones de esta feature empiezan en **D-401** (007 → D-0xx, 009 → D-1xx, 010 → D-2xx,
011 → D-3xx).

Lo que esta feature **no** decide, porque ya está decidido y comprobado: cómo se descargan y analizan
las diecinueve fuentes, cómo se identifica una publicación (`externalKey`), cómo se normaliza el texto
(`SearchText`), cómo se obtiene el tiempo (`TimeProvider`) y cómo se navega al detalle
(`Route.Detail(externalKey)`). Todo eso se consume tal cual.

Cuatro decisiones las tomó el propietario antes de escribir la especificación y aquí solo se anotan
con su consecuencia técnica: dos pestañas (D-430), WorkManager en esta feature (D-419 a D-423), borrado
real (D-412) y vista previa (D-437).

---

## 1. Qué es «nuevo» y cuándo se evalúa

### D-401 — Las claves nuevas salen de `upsertAll`, que ya las conoce y las tiraba

**Decisión**: `UpsertCounts` gana `insertedKeys: List<String>`. `PublicationDao.upsertAll` ya
particiona `toInsert`/`toUpdate`; ahora conserva las claves de `toInsert` cuyo `rowId` devuelto por
`insert` no sea `-1`.

**Por qué**: es el único sitio del proyecto que sabe, dentro de la misma transacción, qué filas no
existían. Cualquier otra forma —comparar `first_seen_at` con la hora de inicio, consultar después—
sería una segunda lectura con una carrera dentro.

**El filtro por `rowId` no es opcional**: `insert` es `OnConflictStrategy.IGNORE` y el índice único
de `blob_id` puede rechazar una fila cuya `external_key` no existía. Esa fila **no** es nueva, y sin el
filtro se evaluaría contra los avisos.

**Alternativa descartada**: devolver las entidades insertadas enteras. Las claves bastan y el
repositorio ya tiene `byKeys` para lo que haga falta después.

### D-402 — `SyncSummary` transporta las claves y la marca de línea base

**Decisión**: `SyncSummary` gana `newKeys: Set<String> = emptySet()` e `isBaseline: Boolean = false`;
`plus` une los conjuntos y hace OR de la bandera. Los valores por defecto mantienen intactos `SKIPPED`,
los fakes y las pruebas existentes.

**Por qué**: el resumen es lo que `refresh()` devuelve, y quien orquesta la sincronización solo ve
eso. Meter las claves ahí es la forma de no abrir un segundo canal entre `data` y el caso de uso.

**Lo que no cambia**: `toEvent()` sigue emitiendo solo recuentos. Las claves nunca van a analítica.

### D-403 — La línea base es «ninguna sincronización correcta previa», decidida una vez y antes

**Decisión**: `PublicationRepositoryImpl.refresh()` evalúa
`isBaseline = feedSyncStateDao.lastSuccessAt() == null` **antes** de lanzar los diecinueve feeds, y si
es línea base devuelve el resumen con `newKeys` vacío e `isBaseline = true`.

**Por qué una vez y antes**: con cuatro feeds en paralelo, el segundo que terminase ya vería el
`last_success_at` que escribió el primero, y una decisión por feed marcaría trece como nuevos y seis
como línea base.

**Por qué en `data` y no en el caso de uso**: para que ningún consumidor futuro de `refresh()` pueda
olvidarse de vaciar las claves. La bandera se conserva para las pruebas y para analítica
(`boc_sync` gana `baseline`).

**Alternativas descartadas**:
- `publicationDao.count() == 0`: falla si la primera sincronización correcta trae las diecinueve
  fuentes vacías (la 8.1 lo está a menudo) y la segunda trae contenido: avalancha. `lastSuccessAt`
  no tiene ese agujero, porque `markSuccess` lo escribe aunque el feed venga vacío.
- Una bandera «línea base hecha» en preferencias: estado duplicado del que ya vive en `feed_sync_state`.

Cubre los dos casos de la especificación: primera instalación, e instalación sin red cuyo primer
éxito llega días después (`markFailure` conserva `lastSuccessAt = null`).

### D-404 — Un único ciclo para Inicio y para el segundo plano: `RunSyncCycleUseCase`

**Decisión**: se crea `domain/usecase/RunSyncCycleUseCase(force)` y lo usan `HomeViewModel` y
`AlertSyncWorker`. Orden fijo:

1. Instantánea de las reglas **activas** (antes de sincronizar; ver D-405).
2. `refreshPublications(force)`. `Failure` → devolver el fallo sin evaluar (FR-041).
3. Si `isBaseline`, o `newKeys` vacío, o sin reglas → `releaseUnusedDocuments()` y salir con
   `AlertDelivery.NONE`.
4. `publications.byKeys(newKeys)`.
5. `matchRule(rule, publication)` por cada par; candidatos `AlertMatch(ruleId, externalKey, now)`.
6. `alerts.recordMatches(candidates)` → solo las **realmente insertadas** (D-410, unicidad).
7. Agrupar por publicación → `List<AlertNotification(publication, ruleNames)>`.
8. `appVisibility.isAppVisible()` decide **una vez por ciclo**: `SYSTEM` → `notifier.post(groups)`;
   `IN_APP` → `inAppAlerts.publish(InAppAlert(...))`.
9. `releaseUnusedDocuments()` (sale de `HomeViewModel`, que hoy lo llama tras sincronizar).
10. `Success(SyncCycleOutcome(summary, groups, delivery))`.

**Por qué un caso de uso y no el repositorio**: `PublicationRepositoryImpl` ya toma diez dependencias y
responde a la fuente; los avisos responden a la persona. Es la misma frontera que separó
`SavedPublicationRepository` de `PublicationRepository` en la 005.

**Por qué no en `HomeViewModel`**: solo cubriría la sincronización manual. El Worker no tiene modelo de
pantalla.

**Alternativa descartada**: que el orquestador devuelva las coincidencias y cada pantalla pinte su
Snackbar. Solo Inicio sincroniza; Buscar, Guardados y el propio Avisos también tienen que enterarse
(D-416).

### D-405 — `activeSince` se cumple por el orden del ciclo, no comparando fechas

**Decisión**: la instantánea de reglas se toma **antes** de `refresh()`. Toda publicación nueva del
ciclo tiene `first_seen_at` posterior al inicio del ciclo, y por tanto posterior al `active_since` de
cualquier regla de la instantánea. Una regla creada, editada o reactivada **durante** el ciclo no se
evalúa hasta el siguiente, y entonces solo contra lo nuevo de ese ciclo.

**Consecuencia**: `Publication` (dominio) **no** gana `firstSeenAt`. Su constructor de catorce
argumentos aparece en decenas de fixtures y la omisión en `toDomain()` es deliberada. `active_since`
se guarda igualmente: es lo que la tarjeta muestra y la guardia que un futuro `discoveredAt` podría usar.

**Alternativa descartada**: pasar `first_seen_at` al comparador. Exigía tocar el modelo de dominio y
todas sus fábricas de prueba para ganar una comprobación que el orden ya garantiza.

---

## 2. El comparador

### D-406 — `MatchAlertRuleUseCase`, puro, con el catálogo de secciones inyectado

**Decisión**: `domain/usecase/MatchAlertRuleUseCase(sections: BocSectionRepository)` con
`operator fun invoke(rule: AlertRule, publication: Publication): Boolean`. Es el `AlertMatcher` que
el documento funcional llama así (§19), con el nombre que marca la guía del proyecto. Lo reutilizan el
ciclo (D-404) y la vista previa (D-437).

**Texto de coincidencia** (§7): título, `rawCategories` como texto plano, `organizationPath`,
`issuer`, y **nombre** de sección y de subsección resueltos con el catálogo inyectado. Todo pasa por
`SearchText.normalise`. Se **excluye** `blobId` —está en `search_text` de Buscar porque alguien puede
teclear un número de anuncio; aquí sería ruido— y se **incluye** `rawCategories`, que Buscar excluye
por venir permutado en el feed 4.3: para una subcadena el orden es irrelevante (§23).

**Por qué inyectar el catálogo y no pasar un mapa**: cambia la firma de §19 y obliga a orquestador y
vista previa a construirlo cada uno. `BocSectionRepository` es una interfaz de dominio sin coste
(datos compilados), y `FilterPublicationsUseCase` ya marcó el precedente de un comparador puro en
`domain/usecase`.

**Orden de las comprobaciones**: `isEnabled` → secciones → organismo → palabras, como el pseudocódigo
de §19. Se conserva `if (!rule.isEnabled) return false` aunque el ciclo ya filtre: es el caso «regla
pausada» de §24 y protege a la vista previa.

### D-407 — Las secciones se guardan como códigos hoja, y un padre se expande al guardar

**Decisión**: `AlertRule.sectionCodes: Set<String>` contiene **códigos de clasificación hoja** (`1`,
`2.1`, …, diecinueve en total, uno por feed). `SectionSelection.expandToLeaves(selected, sections)`
sustituye un padre marcado por sus hijas **al guardar**. El comparador acepta
`publication.classificationCode in rule.sectionCodes || publication.sectionCode in rule.sectionCodes`:
el segundo término es cinturón y tirantes por si una regla almacenada trajera un padre.

**Por qué códigos y no `feedId`**: el `feedId` es procedencia y vive en
`data/source/remote/BocFeedCatalog`, que `domain` no puede importar (regla Konsist). El código es la
identidad de `BocSection` y `Publication.classificationCode` ya lo lleva.

**Por qué expandir al guardar**: §14 prohíbe duplicar por tener marcadas sección e hija a la vez, y un
conjunto de hojas hace imposible la duplicidad por construcción. `SectionSelection.summary` reconstruye
«Autoridades y personal (todas)» cuando el conjunto contiene todas las hijas de un padre.

### D-408 — Las palabras se guardan tal como se escribieron y se normalizan al comparar

**Decisión**: `alert_rules.keywords` guarda el texto de la persona. La normalización ocurre en el
comparador, en cada evaluación.

**Por qué**: la lección de `search_text` (CLAUDE.md): si `SearchText.normalise` cambiara, lo
almacenado normalizado dejaría de concordar con lo que se compara. Diez términos por regla se
normalizan en microsegundos. Y la tarjeta muestra lo que la persona escribió, con sus tildes.

**Duplicados**: se rechazan **al añadir** comparando normalizados (FR-018), en `AlertRuleDraft`.

### D-409 — El organismo es una subcadena normalizada sobre la jerarquía y el emisor

**Decisión**: `normalise(query)` contenido en `normalise(organizationPath.joinToString(" "))` o en
`normalise(issuer)`. Vacío o en blanco → cualquier organismo.

**Por qué**: §5.5 pide coincidencia parcial normalizada, no igualdad. «Piélagos» debe encontrar
«Ayuntamiento de Piélagos» y «AYUNTAMIENTO DE PIELAGOS».

---

## 3. Persistencia

### D-410 — Dos tablas, versión 5, migración automática, y una unicidad que hace la deduplicación

**Decisión**: `alert_rules` y `alert_matches` (columnas en `data-model.md` §2). `BocDatabase` pasa a
`version = 5` con `AutoMigration(from = 4, to = 5)`; se versiona `app/schemas/.../5.json`. Las
anteriores se conservan: de la 1 a la 5 de una vez.

`alert_matches` lleva `UNIQUE(rule_id, external_key)` y `FOREIGN KEY(rule_id) REFERENCES
alert_rules(id) ON DELETE CASCADE`. **Sin** FK a `publications`: nunca se borran, y la FK solo añadiría
coste a cada escritura de la sincronización.

**Por qué la unicidad es la deduplicación**: `insert` con `OnConflictStrategy.IGNORE` sobre ese índice
devuelve `-1` para lo que ya existía, así que «la misma pareja no se registra dos veces» (FR-042) lo
garantiza la base de datos y no un `if`. Y el repositorio devuelve solo las filas realmente insertadas,
que es lo único que se entrega.

**Precedente**: la 3→4 añadió `ai_summaries` de la misma forma; una tabla nueva nace vacía y no hay
relleno que hacer.

### D-411 — Las listas se guardan con el conversor que ya existe, no en JSON

**Decisión**: `keywords` y `section_codes` son `List<String>` con `Converters.listToString`
(separador ``).

**Por qué**: el conversor está registrado, probado y en uso para `organization_path`. Un serializador
JSON sería una segunda forma de guardar lo mismo, y el separador ya se eligió porque no aparece en el
texto del boletín. Una palabra clave la escribe una persona; si escribe un carácter de control, se
descarta al validar (FR-018 exige 2–60 caracteres imprimibles).

### D-412 — La primera sentencia de borrado del proyecto, y la doctrina que la acompaña

**Decisión**: `AlertRuleDao.delete(id): Int` con `@Query("DELETE FROM alert_rules WHERE id = :id")`.
El CASCADE borra sus coincidencias. Es la **única** sentencia de borrado del proyecto.

**Por qué es correcto y no una grieta**: la regla «ningún DAO declara borrado» existe porque una fuente
solo publica sus últimos cien anuncios y borrar publicaciones destruiría el archivo. Una regla de aviso
es un dato de la persona, y la persona pide borrarla con un diálogo de confirmación delante. El
propietario lo decidió a conciencia frente al borrado lógico.

**Lo que se hace en la misma entrega**: reescribir la regla en CLAUDE.md como «**nunca se borra una
publicación**; ningún DAO sobre `publications` declara borrado; `AlertRuleDao.delete` borra reglas de
la persona y sus coincidencias por CASCADE»; actualizar las cabeceras de `PublicationDao`,
`SavedPublicationDao` y `AiSummaryDao` que dicen «como los otros DAO»; y añadir en `AlertRuleDaoTest`
la regresión que demuestra que borrar una regla deja `publications` con las mismas filas.

**Alternativa descartada**: borrado lógico con `deleted_at`. Mantenía la regla literal a costa de filas
muertas, de un `WHERE deleted_at IS NULL` en cada consulta, y de que «eliminar» no eliminara.

### D-413 — Las novedades se leen agrupadas por publicación desde la base de datos

**Decisión**: `AlertMatchDao.observeNews()` hace el `JOIN` con `publications` y `alert_rules`, agrupa
por `external_key`, concatena los nombres de las reglas con `GROUP_CONCAT(r.name, '')`, toma
`MIN(m.matched_at)` como momento de detección y `MAX(CASE WHEN read_at IS NULL THEN 1 ELSE 0 END)`
como no leída. `observeUnreadCount()` es `COUNT(DISTINCT external_key) WHERE read_at IS NULL`.

**Por qué en SQL**: FR-003 exige contar publicaciones y no coincidencias, y hacerlo en Kotlin sobre
todas las coincidencias en cada emisión sería recalcular lo que la base sabe. Room observa las tres
tablas del `JOIN`, así que la pestaña y el badge se actualizan solos.

**Marcar leída** escribe `read_at` en **todas** las coincidencias de esa publicación
(`UPDATE ... WHERE external_key = :key AND read_at IS NULL`): una novedad es la publicación, no la pareja.

**Límite aceptado**: `GROUP_CONCAT` no garantiza el orden de los nombres. Para «Coincide con A y B» da
igual.

### D-414 — Tiempos como `Long` de `TimeProvider`, identificador de regla como UUID

**Decisión**: `created_at`, `updated_at`, `active_since`, `matched_at`, `read_at` son epoch-millis de
`TimeProvider.nowMillis()`, nunca de `System.currentTimeMillis()` en el punto de uso. El `id` de una
regla es `UUID.randomUUID().toString()` generado en `AlertRepositoryImpl`.

**Por qué `Long` y no `Instant`** (§18 del documento funcional pedía `Instant`): el proyecto tiene un
único reloj inyectable y todas sus marcas —`first_seen_at`, `saved_at`, `created_at` de los
resúmenes— son `Long`. Introducir `Instant` obligaría a un segundo reloj inyectable.

**Por qué UUID y no autoincremental**: `Route.AlertForm(ruleId)` viaja como argumento de navegación y
sobrevive a la muerte del proceso; un texto opaco no invita a nadie a hacer aritmética con él.

---

## 4. Cómo se entrega una coincidencia

### D-415 — Saber si la aplicación está en pantalla: `ProcessLifecycleOwner`, decidido una vez por ciclo

**Decisión**: `core/util/AppVisibilityProvider { fun isAppVisible(): Boolean }` implementado por
`ProcessLifecycleAppVisibilityProvider`, que lee
`ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)`. Requiere
`androidx.lifecycle:lifecycle-process` (D-419). El ciclo lo consulta **una vez**, en el paso 8, sobre
el conjunto entero de coincidencias del ciclo: cada coincidencia sale por exactamente un canal (FR-052).

**Por qué no una bandera publicada por el shell**: `Detail`, `PdfViewer`, `Ask` e `Info` viven fuera
del shell; con la bandera a `false` mientras alguien lee un documento saldría una notificación con la
aplicación en pantalla, contra FR-050. Y sería un singleton mutable escrito desde un componible, justo
lo que el principio IV prohíbe.

**Solo lectura**: no se registra ningún observador. `currentState` se puede leer desde cualquier hilo,
y el Worker corre fuera del principal.

### D-416 — El aviso interno es un estado pendiente que consume el shell, no un evento

**Decisión**: `domain/repository/InAppAlertStore { observePending(): Flow<InAppAlert?>; publish(alert);
consume() }`, implementado en memoria (`InMemoryInAppAlertStore`, `single`). Un nuevo
`ui/main/MainShellViewModel` combina el contador de no leídas y el aviso pendiente en
`MainShellUiState`; `MainShell` añade `snackbarHost` a su `Scaffold` y, en un `LaunchedEffect`, si el
destino actual **no** es Avisos muestra el Snackbar con «VER» (→ `Route.Alerts(NOVEDADES)` sin
`restoreState`, la trampa conocida de la 006) y en cualquier caso consume el pendiente (FR-050, FR-051).

**Por qué `StateFlow` y no `SharedFlow`**: si el ciclo termina con `Detail` encima del shell, el shell
no está compuesto; con `SharedFlow` sin repetición el aviso se perdería, con estado pendiente se
muestra al volver (caso límite de la especificación).

**Por qué en el shell**: es el único `Scaffold` que envuelve a los cuatro destinos, así el Snackbar sale
encima de la barra inferior en Inicio, Buscar, Guardados o Avisos sin que cada pantalla lo sepa. Y
funciona igual si el ciclo lo lanzó Inicio o el Worker con la aplicación abierta.

**`SyncCycleOutcome.delivery`** queda para pruebas y para el registro del Worker.

### D-417 — `AlertNotifier` en dominio, `AndroidAlertNotifier` en `data/notification`

**Decisión**: `domain/repository/AlertNotifier { fun post(notifications: List<AlertNotification>) }`.
`data/notification/AndroidAlertNotifier`:
- crea el canal `boc_alerts` («Avisos del BOC», `IMPORTANCE_DEFAULT`) en cada `post()`:
  `createNotificationChannel` es idempotente y barato;
- comprueba `NotificationManagerCompat.areNotificationsEnabled()` antes de publicar; si no, no publica
  nada —las coincidencias ya están guardadas (FR-062)— y el lint `MissingPermission` queda cubierto;
- una notificación por publicación: `id = externalKey.hashCode()`, `BigTextStyle`, `setGroup(GROUP)`,
  `setGroupAlertBehavior(GROUP_ALERT_SUMMARY)`, `setAutoCancel(true)`, `setColor` con el azul de
  `colors.xml` (`splash_background`, sincronizado con `BocPrimary`), título «Nueva publicación: <aviso>»
  o «Nueva publicación del BOC» según cuántas reglas, cuerpo con el título y, si son varias, «Coincide
  con A y B» (FR-046);
- con dos o más publicaciones, además un resumen `InboxStyle` con `setGroupSummary(true)` e id constante,
  «N publicaciones nuevas coinciden con tus avisos» (FR-047). El comportamiento de alerta del grupo
  hace que suene una vez.

**Por qué `data` y no `ui`**: la notificación la publica el Worker, que no tiene pantalla, y
`NotificationManagerCompat` es un SDK de plataforma que la regla de capas quiere en una sola capa.

### D-418 — El icono pequeño de la notificación es un vector monocromo dedicado

**Decisión**: `ic_notification_bell` (campana rellena, solo alpha) como `setSmallIcon`. El escudo
multicolor **no** sirve: Android lo pinta como silueta blanca irreconocible.

---

## 5. La comprobación periódica

### D-419 — Tres artefactos nuevos en el catálogo, y cuatro con el de pruebas

**Decisión** (`gradle/libs.versions.toml`): `work = "2.11.1"`; `androidx-work-runtime-ktx`,
`androidx-work-testing`, `androidx-lifecycle-process` (con `version.ref = "lifecycle"`, ya `2.11.0`)
y `koin-androidx-workmanager` **sin versión**, gobernado por `koin-bom`. Comprobado que el BOM 4.2.2
lo incluye y que `work-runtime` 2.11.1 y `lifecycle-process` 2.11.0 ya están en la caché de Gradle:
no se descarga nada nuevo.

### D-420 — WorkManager se inicializa desde Koin, y el inicializador por defecto se retira

**Decisión**: `BOCantabriaApp`: `startKoin { …; workManagerFactory(); modules(appModules) }`. En el
manifest se retira `androidx.work.WorkManagerInitializer` del `InitializationProvider` con
`tools:node="remove"` y el `provider` con `tools:node="merge"`, para que el
`ProcessLifecycleInitializer` de `lifecycle-process` **se conserve**.

**Por qué**: `AlertSyncWorker` necesita `RunSyncCycleUseCase` y `CrashReporter`. `workerOf(::AlertSyncWorker)`
los inyecta por constructor y `KoinModulesTest.verify()` los comprueba; un `KoinComponent` con
`by inject()` los escondería. Con el inicializador por defecto vivo, WorkManager arrancaría antes que
Koin y con su fábrica propia.

**Riesgo anotado**: cualquier `WorkManager.getInstance(context)` antes de `startKoin` lanza. El único
acceso está dentro de los métodos de `WorkManagerBackgroundSyncScheduler`, nunca en su constructor.

**Corrección tras la tanda instrumentada (6 de septiembre de 2026).** `workManagerFactory()` va detrás de
`if (!Process.isIsolated())`. El visor de PDF renderiza en un proceso **aislado**, que ejecuta
`Application.onCreate` pero no tiene servicios del sistema: inicializar WorkManager ahí lanzaba un
`NullPointerException` sobre `ConnectivityManager` y el proceso moría antes de dibujar una página
(`PdfViewerSmokeTest` colgada, `AndroidxPdfPageCounterTest` agotando su minuto). El inicializador por
defecto nunca lo sufrió: un `ContentProvider` no corre en procesos aislados. Ninguna prueba unitaria podía
verlo; es exactamente el tipo de frontera que el `quickstart.md` obliga a cruzar.

### D-421 — Periódico cada cuatro horas, con red, y `UPDATE`

**Decisión**: `PeriodicWorkRequestBuilder<AlertSyncWorker>(4, HOURS, 30, MINUTES)`,
`Constraints(NetworkType.CONNECTED)`, `enqueueUniquePeriodicWork("boc_alert_sync",
ExistingPeriodicWorkPolicy.UPDATE, request)`.

**Por qué cuatro horas**: el BOC publica una vez por día laborable a primera hora. Seis comprobaciones
al día dan una latencia máxima razonable para una «comprobación periódica» (FR-065 prohíbe prometer
tiempo real), y están muy por encima del TTL de 30 minutos de la caché, así que `force = false` nunca
se salta la red. Sin `requiresBatteryNotLow`: son diecinueve GET de XML.

**Por qué `UPDATE` y no `KEEP`**: si una versión futura cambia el intervalo o una restricción, `KEEP`
dejaría la petición vieja viva para siempre en los móviles ya instalados. `UPDATE` conserva el siguiente
instante de ejecución si la especificación no cambió, así que no cuesta nada.

### D-422 — Se encola al existir la primera regla activa, y se cancela al quedar cero

**Decisión**: `domain/repository/BackgroundSyncScheduler { ensureScheduled(); cancel() }`.
`SaveAlertRuleUseCase` y `SetAlertRuleEnabledUseCase` llaman a `ensureScheduled()` si tras escribir hay
alguna regla activa y a `cancel()` si no; `DeleteAlertRuleUseCase` igual. `ReconcileBackgroundSyncUseCase`
en `MainShellViewModel.init` rehace la cuenta en cada arranque del shell: cubre actualizaciones de la
aplicación y reinstalaciones con copia de seguridad.

**Por qué no al arrancar la `Application`**: sin reglas no hay nada que evaluar y no se gasta batería
(FR-066). Y `Application.onCreate` no debería tocar la base de datos.

### D-423 — El Worker siempre devuelve `success`

**Decisión**: `AlertSyncWorker.doWork()` llama a `runSyncCycle(force = false)`, registra el resultado
con `crashReporter.log("cycle: …")` y devuelve `Result.success()` **también** cuando el ciclo falla.

**Por qué**: `retry()` con retroceso se solaparía con el periodo, y un feed caído no mejora
reintentando a los treinta segundos. La siguiente ejecución periódica es el reintento.

---

## 6. Del toque en la notificación al detalle

### D-424 — Un almacén de navegación pendiente que consume el grafo después del arranque

**Decisión**: `ui/navigation/PendingNavigation` (`Publication(externalKey)` | `AlertNews`) y
`PendingNavigationStore` (`StateFlow<PendingNavigation?>`, `set`, `consume`), `single` en `uiModule`.
`MainActivity` lo rellena en `onCreate` **solo si `savedInstanceState == null`** y en `onNewIntent`
(manifest: `launchMode="singleTop"`). `BOCantabriaNavHost` consume `Publication` dentro de
`composable<Route.Home>` —es decir, **después** de que la portada haya navegado— con
`navigate(Route.Detail(key))`. `MainShell` consume `AlertNews` con `Route.Alerts(NOVEDADES)` sin
`restoreState`.

**Por qué no `navDeepLink`**: Navigation resolvería la ruta directamente y **saltaría la portada**, que
hace la comprobación de versión mínima y mantenimiento (FR-049). Y exigiría un `intent-filter`
exportado con un esquema propio. Si la portada bloquea, el pendiente se queda sin consumir: correcto.

**Por qué `singleTop`**: con `standard`, tocar la notificación con la aplicación abierta crearía una
segunda `MainActivity` encima de la primera, con su propia portada.

### D-425 — El `Intent` lo construye `data` sin nombrar la `Activity`

**Decisión**: `AndroidAlertNotifier` usa `packageManager.getLaunchIntentForPackage(packageName)` y
añade extras cuyas claves viven en `core/notification/AlertIntentExtras` (`EXTRA_TARGET` =
`publication` | `news`, `EXTRA_EXTERNAL_KEY`). `PendingIntent.getActivity(context, requestCode =
externalKey.hashCode(), intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)`; el resumen usa un
`requestCode` constante.

**Por qué en `core`**: lo escriben `data` y lo lee `MainActivity`; `core` es lo transversal.
`data` no puede importar `MainActivity` sin invertir la regla de capas.

### D-426 — Abrir el detalle marca la novedad leída, venga de donde venga

**Decisión**: `PublicationDetailViewModel.init` llama a `MarkAlertReadUseCase(externalKey)`,
idempotente (`UPDATE … WHERE read_at IS NULL`).

**Por qué**: FR-056 dice que la novedad pasa a leída al abrir la publicación «desde cualquier otro sitio
de la aplicación». Una bandera `fromAlert` en la ruta duplicaría un dato que el almacén ya conoce y
dejaría sin marcar la apertura desde Inicio.

---

## 7. El permiso

### D-427 — El estado del permiso llega a la pantalla por un caso de uso, no por `NotificationManagerCompat`

**Decisión**: `domain/model/NotificationStatus { GRANTED, NEEDS_REQUEST, DISABLED }`;
`data/source/local/NotificationStatusDataSource` + `AndroidNotificationStatusDataSource`
(`areNotificationsEnabled()`; en SDK ≥ 33 sin permiso → `NEEDS_REQUEST`; con permiso pedido y
denegado o canal apagado → `DISABLED`); `NotificationStatusRepository` + `GetNotificationStatusUseCase`.
Es el mismo patrón que `ConnectivityDataSource`. La pantalla refresca en `onResumed`
(`LifecycleResumeEffect`) porque volver de Ajustes no emite nada.

**Corrección tras el recorrido manual (6 de septiembre de 2026).** En Android 13+ apagar las
notificaciones en Ajustes **revoca el permiso**, así que la plataforma lo describe igual que «nunca se
pidió»: `NEEDS_REQUEST`, y el banner de FR-014 —que solo miraba `DISABLED`— no aparecía. El banner se
muestra ahora con reglas activas y **cualquier estado distinto de `GRANTED`**: para la persona la salida es
la misma, Ajustes. El formulario sigue pidiendo el permiso solo con `NEEDS_REQUEST` y solo en el primer
aviso (D-428).

### D-428 — Cuándo se pide: primer aviso, permiso pendiente, y sin bandera propia

**Decisión**: `AlertFormViewModel` emite `Saved(requestPermission = true)` solo si **antes** de
guardar `countRules() == 0` y el estado es `NEEDS_REQUEST`. No se guarda ninguna bandera «ya pedido».

**Por qué**: Android deja de mostrar el diálogo del sistema tras dos denegaciones, así que «no insistir
en cada apertura» (FR-061) lo cumple la plataforma; y quien borra todos sus avisos y vuelve a empezar
recibe otra vez la explicación, que es razonable.

### D-429 — «Abrir ajustes» lo lanza la pantalla, igual que Info abre sus enlaces

**Decisión**: `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(EXTRA_APP_PACKAGE, …)`
desde el componible del banner y de la hoja de ajustes. El modelo de pantalla no toca `Intent`.

---

## 8. La pantalla

### D-430 — Dos rutas: Avisos dentro del shell, el formulario fuera

**Decisión**: `Route.Alerts(tab: String? = null)` en el NavHost **interior** (cuarto destino);
`Route.AlertForm(ruleId: String? = null, duplicateOf: String? = null)` en el **exterior**, apilado como
`Info`, con barra azul y Atrás y sin barra inferior. La pestaña se restaura **por nombre**
(`entries.firstOrNull`), nunca `valueOf` (FR-006, trampa de la 007).

### D-431 — El cuarto destino y su contador

**Decisión**: `BottomDestination.ALERTS(ic_notifications, nav_alerts, "bottom_alerts")` en cuarta
posición. `BocBottomBar` gana `alertBadge: Int`; `NavigationBarItem.icon` envuelve el icono en
`BadgedBox` con `Badge { Text("9+" o el número) }` solo si es mayor que cero. `navigateTo` y
`toDestination` de `MainShell` ganan el caso. Se retira el KDoc «Three destinations».

### D-432 — La última coincidencia: «N coincidencias hoy» con el día local, o relativa

**Decisión**: `AlertRuleDao.observeRules(dayStart)` devuelve por regla `last_matched_at` y
`matches_today` (`SUM(CASE WHEN matched_at >= :dayStart …)`). `ObserveAlertRulesUseCase` calcula
`dayStart` con `core/util/LocalDay.startOf(nowMillis, zone)` (`ZoneId.systemDefault()` por defecto,
inyectable). La tarjeta muestra «N coincidencias hoy» si `matches_today > 0`, si no «Última
coincidencia: <relativo>» con `core/util/RelativeTime` («hace 20 min», «ayer», fecha), y «Aviso pausado»
si está pausada. `RelativeTime` también sirve los separadores Hoy/Ayer/fecha de Novedades.

**Por qué una utilidad nueva**: no existe nada de formateo relativo, y el formateador largo de fecha
está copiado en cinco pantallas. Esta feature no arregla esas copias, pero no añade una sexta.

### D-433 — El selector de secciones se construye en el modelo del formulario

**Decisión**: `AlertFormViewModel` agrupa el catálogo en `List<SectionPickerRow(section, children,
state)>` con `SectionSelection.stateOf` (CHECKED / INDETERMINATE / UNCHECKED, que la pantalla traduce a
`ToggleableState`). Se reutiliza el tipo `SectionRow` de `ui/sections` para la agrupación.
`SectionPickerSheet` es un `ModalBottomSheet` calcado de `SearchFiltersSheet`, con `TriStateCheckbox`
por padre y `Checkbox` por hija.

**Por qué no `SectionsUiState.rows`**: están filtrados por lo que se teclea en el panel lateral, la
misma razón por la que `MainShell` no los usa para los chips.

### D-434 — La barra azul con Atrás no se promociona en esta feature

**Decisión**: `AlertFormScreen` y `AlertsScreen` escriben su `TopAppBar` como Info y Guardados. No sale
gratis: las cuatro copias existentes difieren en `navigationIcon`, `actions` y colores, y unificarlas
tocaría cuatro pantallas con pruebas instrumentadas de 46 s cada una. Queda anotado como deuda en
CLAUDE.md, no resuelto de tapadillo.

### D-435 — Diez iconos nuevos, con el `viewBox` comprobado uno a uno

`ic_notifications`, `ic_notifications_filled`, `ic_notifications_off`, `ic_notification_bell`
(D-418), `ic_tune`, `ic_add`, `ic_more_vert`, `ic_edit`, `ic_delete`, `ic_done_all`. Material Symbols
mezcla lienzos de 960 con coordenadas negativas y de 24 sin `viewBox`; `ic_ai` estuvo cuatro features
sin dibujarse por eso. Antes de meter un trazado en la plantilla de 960 se comprueba que lleva
coordenadas negativas.

### D-436 — Los textos son los de la especificación, y las cifras concuerdan

`plurals` para «N activos», «N coincidencias hoy», «N publicaciones nuevas coinciden con tus avisos»,
«N publicaciones actuales coinciden» y «N seleccionadas». Prefijos `nav_alerts`, `alerts_`,
`alert_form_`, `alert_notification_`, `alert_permission_`.

### D-437 — La vista previa reutiliza el comparador sobre las publicaciones almacenadas

**Decisión**: `PreviewAlertRuleUseCase(publications, matchRule)`: `publications.newest(PREVIEW_LIMIT)`
(nuevo método de lectura de `PublicationRepository`, sobre `PublicationDao.newest(limit)`) filtrado con
`matchRule` sobre un `AlertRule` construido del borrador con `isEnabled = true`. Devuelve la lista;
la pantalla muestra el recuento y, con «Ver resultados», una hoja con `PublicationCard` que abre el
detalle. `PREVIEW_LIMIT = 5_000`: hoy el archivo entero cabe, y la constante existe para que un archivo
de años no congele el formulario. **No escribe nada**: ni coincidencias, ni novedades, ni
`last_matched_at` (FR-068). Se recalcula con un `debounce` de 300 ms sobre el borrador válido.

### D-438 — La analítica cuenta, no escucha

Eventos, todos con recuentos o enumerados y **nunca** nombre, palabras u organismo:
`alert_rule_saved{keywords, sections, has_organization, match_mode, is_edit}`,
`alert_rule_toggled{enabled}`, `alert_rule_deleted`, `alert_matches{recorded, publications, delivery}`,
`alert_read{all}`, `alert_permission{granted}`. `boc_sync` gana `baseline`. Todos se emiten desde
repositorios o casos de uso, nunca desde componibles.

### D-439 — Lo que se registra en el dispositivo, y lo que no

Con `CrashReporter.log`, etiqueta `BOC`, prefijos `cycle:` y `alerts:`:

```
cycle: baseline, 1893 stored, alerts not evaluated
cycle: 14 new, 3 rule(s), 2 match(es) on 2 publication(s), delivery=SYSTEM
cycle: refresh failed: Network
alerts: notifications disabled, 2 match(es) kept
alerts: posted 2 notification(s) + summary
alerts: worker run, delivery=NONE
```

Nunca el título de una publicación, ni una palabra clave, ni el nombre de una regla. Cinco pruebas lo
vigilan, como en la 011.

### D-440 — Documentación que se toca en la misma entrega

CLAUDE.md (arquitectura: `data/background`, `data/notification`, `ui/alerts`, `ui/main/MainShellViewModel`,
`core/notification`; la regla del borrado reescrita; trampas nuevas: inicializador de WorkManager,
`singleTop`, `GROUP_ALERT_SUMMARY`, icono pequeño), `docs/diseno/especificaciones-diseno.md` (§10.1
cuarto destino recuperado, §10.2 campana, §23 pantalla Avisos actualizada a dos pestañas, §36 checklist),
KDoc de `BocBottomBar`, `HomeTopBar` y `SectionsDrawerContent`.

---

## 9. Pruebas

### D-441 — Cada clase de dominio y cada modelo de pantalla con su prueba, sin excepciones nuevas

La regla novena de Konsist lo exige. Los enumerados sin comportamiento (`AlertDelivery`,
`NotificationStatus`) llevan una prueba pequeña que afirma sus valores en vez de añadirse a
`DOMAIN_CLASSES_WITHOUT_BEHAVIOUR`: la lista de excepciones tiene una entrada y conviene que siga así.

### D-442 — Lo que solo se ve en un móvil, y va al `quickstart.md`

La notificación con la aplicación cerrada, la agrupación, el toque que atraviesa la portada, el
Snackbar con la aplicación en Buscar, el permiso denegado y el Worker forzado con `adb`. Ninguna prueba
de esta casa cruza esa frontera; se recorre a mano y se registra.

---

## 10. Lo que queda sin resolver

- **Gestión agresiva de batería** en algunos fabricantes puede espaciar el trabajo periódico más de lo
  que WorkManager promete. No se intenta rodear (§23 del documento funcional).
- **Cambio de zona horaria** entre dos aperturas: «hoy» se recalcula al observar; una coincidencia de
  madrugada puede cambiar de día. Aceptado.
- **Orden de nombres en «Coincide con A y B»**: el de `GROUP_CONCAT`. Si molesta, subconsulta ordenada.

---

## 11. Decisiones superadas por la feature 014 (6 de septiembre de 2026)

La auditoría técnica del 6 de septiembre de 2026 (`docs/auditoria/01-hallazgos.md`, STAB-003) demostró
que el ciclo perdía un aviso para siempre si registrar la coincidencia fallaba o el proceso moría entre
guardar el boletín y registrarla: el siguiente ciclo ya no veía esas publicaciones como nuevas. Dos
decisiones de este fichero quedan **superadas, no incumplidas**, por
`specs/014-estabilidad-auditoria/research.md`:

- **D-401** («las claves nuevas las transporta `SyncSummary.newKeys` y el ciclo las lee con `byKeys`»):
  superada por **D-607**. Las claves siguen viajando en el resumen para los recuentos y el registro, pero
  lo que el ciclo evalúa es la marca `pending_alert_evaluation` que cada fila nueva lleva en el almacén,
  y que solo se retira cuando las coincidencias han quedado registradas. `byKeys` se retiró.
- **D-405** («nunca retroactivo se cumple por el orden del ciclo, sin comparar una sola fecha»): superada
  por **D-609**. El orden sigue garantizándolo para lo que inserta el ciclo en curso; un resto de un ciclo
  anterior puede ser anterior a una regla creada entre los dos, así que `AlertCandidate.isVisibleTo(rule)`
  compara `activeSince` con `first_seen_at` (`<=`, porque con el reloj congelado de estas pruebas los dos
  instantes coinciden). Una sola regla para los dos casos.

Se mantienen tal cual D-402, D-403 (la línea base, que ahora además inserta sus filas con la marca a
cero), D-410 (el índice único deduplica, y es lo que hace que el reintento entregue cada pareja una vez)
y D-423 (el Worker devuelve siempre `success`).
