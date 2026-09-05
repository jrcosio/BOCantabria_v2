---

description: "Task list for feature 010 — El documento se envía entero, no su texto"
---

# Tasks: El documento se envía entero, no su texto

**Input**: Design documents from `/specs/010-gemini-sdk-oficial/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/internal-contracts.md`,
`quickstart.md`

**Tests**: **obligatorios**, no opcionales. El principio V de la constitución dice que ninguna tarea
se da por terminada sin su test en verde, y prohíbe `@Ignore`, comentar o borrar un test para que
pase la build. Las cinco pruebas que se borran aquí se borran porque **desaparece el código que
probaban**, no porque estorben.

**Organization**: agrupadas por historia de usuario, con una fase 2 bloqueante que ninguna historia
puede saltarse.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ir en paralelo (fichero distinto, sin dependencias pendientes)
- **[Story]**: a qué historia de usuario pertenece (US1..US5)

## Path Conventions

Módulo único `:app`. Rutas abreviadas en este documento:

- `MAIN/` = `app/src/main/java/com/jrblanco/boccantabria/`
- `TEST/` = `app/src/test/java/com/jrblanco/boccantabria/`
- `ATEST/` = `app/src/androidTest/java/com/jrblanco/boccantabria/`

---

## Aviso sobre la forma de esta lista

**Esta feature vuelve a ser una sustitución, y además borra más de lo que escribe.** Eso cambia la
estructura habitual de tres maneras:

1. **La fase 2 es grande y bloqueante de verdad.** No se puede entregar «medio cambio de transporte»:
   hasta que la fuente de datos nueva compile y el grafo resuelva, la aplicación no genera ni un
   resumen. Las cinco historias se verifican **después**, y lo que aportan es la comprobación de que
   cada una quedó cumplida.
2. **El orden dentro de la fase 2 está elegido para que la build esté roja el menor tiempo posible**:
   primero el build y lo que nadie referencia, después lo nuevo, luego los consumidores, y los
   borrados al final.
3. **Hay una puerta más que en cualquier feature anterior.** `assembleRelease` no se ha ejecutado
   nunca en este proyecto. La fase 7 no es trámite.

---

## Phase 1: Setup

- [ ] T001 Dejar constancia del punto de partida: `./gradlew :app:testDebugUnitTest` en verde **antes** de tocar nada, y anotar aquí el número de pruebas y de fallos
- [ ] T002 [P] Comprobar que `local.properties` contiene `GEMINI_API_KEY` con una clave válida y que la línea no está versionada (`git check-ignore -v local.properties`)
- [ ] T003 [P] Comprobar que ninguna clave viaja en el repositorio, buscando **los dos formatos**: `git grep -nE 'AIza[0-9A-Za-z_-]{30,}|AQ\.[0-9A-Za-z_-]{30,}'`. Buscar solo uno es cómo se da por limpio un repositorio que no lo está
- [ ] T004 Comprobar que el entorno de compilación es Java 17 o superior (`"$JAVA_HOME/bin/java" -version`). El JBR de Android Studio es 21; CI usa Temurin 21

**Checkpoint**: la base está verde, hay credencial, no hay fugas y el entorno da la talla.

---

## Phase 2: Foundational — el transporte nuevo y el borrado del viejo (BLOQUEANTE)

### 2.1 Build

- [ ] T005 Añadir a `gradle/libs.versions.toml` la entrada `googleGenai = "1.0.0"` en `[versions]` y `google-genai-kotlin = { group = "com.google.genai", name = "google-genai-kotlin", version.ref = "googleGenai" }` en `[libraries]`, con un comentario que diga por qué entra (Files API, D-201) y que su README desaconseja la clave en cliente móvil (D-202)
- [ ] T006 Declarar `implementation(libs.google.genai.kotlin)` en `app/build.gradle.kts`, en un bloque propio con comentario. **Ninguna coordenada literal**
- [ ] T007 Subir `compileOptions` a `JavaVersion.VERSION_17` en origen y destino, y el `jvmTarget` de Kotlin a 17, con comentario citando que el bytecode de la librería es *major 61* (D-219)
- [ ] T008 Añadir el bloque `packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/{AL2.0,LGPL2.1}", "META-INF/NOTICE*", "META-INF/LICENSE*") }` a `app/build.gradle.kts`, con comentario (D-221)
- [ ] T009 Ejecutar `./gradlew :app:assembleDebug` y confirmar que la dependencia resuelve y el empaquetado no se queja de entradas duplicadas. Si aparece `NoSuchMethodError` o un fallo de Ktor/OkHttp, aplicar la salida de D-223 y **anotarlo en `research.md`**

### 2.2 Dominio (Kotlin puro, nadie lo referencia todavía)

- [ ] T010 [P] En `MAIN/domain/model/AiSummaryConstants.kt`, cambiar `PROMPT_VERSION` a `"boc-summary-es-v5"`, dejar `SCHEMA_VERSION` intacto, y dejar `MODEL_ID` con un comentario que diga que se fija en **T081** tras la travesía real (D-213). Reescribir el KDoc explicando por qué dos de las tres cambian y qué implica
- [ ] T011 [P] En `MAIN/domain/model/AiSummaryError.kt`, sustituir `NoExtractableText` por `UnreadableDocument` conservando `isRetryable = false`, y reescribir su KDoc: ya no significa «no pudimos sacar texto» sino «el servicio no ha podido leerlo» (D-217)
- [ ] T012 [P] En `MAIN/domain/model/AiSummaryStatus.kt`, renombrar `Preparing.Phase.EXTRACTING_TEXT` a `UPLOADING_DOCUMENT`, y reducir `Generating(analysedPages, totalPages)` a `Generating(totalPages)` eliminando `isPartial` y su `require`. Documentar en el KDoc por qué desaparece el aviso **previo** de lectura parcial y por qué la cobertura **posterior** se queda
- [ ] T013 Borrar `MAIN/domain/model/PdfCorpus.kt` y `TEST/domain/model/PdfCorpusTest.kt`
- [ ] T014 Crear `MAIN/domain/usecase/ReleaseAiDocumentSessionUseCase.kt` con un `operator fun invoke(externalKey: String)` **no suspendido y sin `AppResult`**, con KDoc explicando ambas cosas (data-model §3.4)
- [ ] T015 Añadir `fun releaseDocumentSession(externalKey: String)` a `MAIN/domain/repository/AiSummaryRepository.kt`
- [ ] T016 [P] Actualizar `TEST/domain/model/AiSummaryErrorTest.kt` y `TEST/domain/model/AiSummaryStatusTest.kt` a los nombres nuevos
- [ ] T017 [P] Crear `TEST/domain/usecase/ReleaseAiDocumentSessionUseCaseTest.kt` — lo exige la octava regla de Konsist, y comprueba que delega en el repositorio con la clave recibida

### 2.3 Lo nuevo en `data` (nadie lo referencia todavía)

- [ ] T018 [P] Crear `MAIN/data/source/local/PdfPageCounter.kt` con la interfaz y `PageCountResult` (`Success`/`Encrypted`/`Failure`), según contracts §1.1
- [ ] T019 Crear `MAIN/data/source/local/AndroidxPdfPageCounter.kt` y su función fábrica, tomando de `AndroidxPdfTextExtractor` la apertura con `SandboxedPdfLoader`, el mapeo de `PdfPasswordException` y el cierre en `withContext(dispatchers.io)` dentro de `runCatching`. Devuelve **solo** `pageCount`
- [ ] T020 [P] Crear `MAIN/data/source/remote/GenAiClientProvider.kt`: construye el `Client` perezosamente con la credencial de `GeminiApiKeyProvider`, devuelve `null` sin credencial, acepta un `baseUrl` opcional, y su `toString()` no revela nada (contracts §1.5)
- [ ] T021 Crear `MAIN/data/source/remote/AiDocumentUploader.kt` con la interfaz, `UploadedDocument` y `UploadResult`, según data-model §3.2
- [ ] T022 Implementar `GenAiDocumentUploader` en el mismo fichero o en `MAIN/data/source/remote/GenAiDocumentUploader.kt`: `files.upload` con `application/pdf`, sondeo `PROCESSING → ACTIVE` **con tope de intentos** (FR-012) — fijar el número y el intervalo al implementar y anotarlos en `research.md` D-210, `FAILED` → `Rejected(Malformed)`, `delete` envuelto en `runCatching`, y sin credencial `Rejected(NotConfigured)` sin red
- [ ] T023 Crear `MAIN/data/source/remote/AiDocumentSessionStore.kt` con las siete invariantes de contracts §1.3: como mucho una sesión, `open` idempotente por clave+checksum, relevo, `Mutex`, `release` no suspendida sobre ámbito propio, `release` de clave ajena inocua, y `Rejected` sin sesión abierta
- [ ] T024 Crear `MAIN/data/source/remote/GenAiSummaryDataSource.kt`: `models.generateContent` con un `Content` de rol `user` que lleva la parte de fichero y la de texto; `GenerateContentConfig` con instrucción de sistema, `thinkingLevel` mínimo, `maxOutputTokens = 8000`, `responseMimeType = "application/json"` y `responseJsonSchema = SummarySchema.value`. Conserva `coordinator.serialised { }`, `verdict()` antes de pedir **y antes de cada reintento**, los tres intentos con backoff, y `currentCoroutineContext().ensureActive()` como **primera** línea del `catch (IOException)` (D-218)
- [ ] T025 En `GenAiSummaryDataSource`, mapear los errores a los siete `GeminiRefusal` según research D-216: `ClientException` 401/403 → `NotConfigured`, 429 → cuota, otros 4xx y 5xx → `HttpError`, `IOException` → `Network`, fallo de deserialización → `Malformed`, y `finishReason != STOP` o prosa vacía → `BlankSummary`
- [ ] T026 En `GenAiSummaryDataSource`, obtener los segundos de espera de un 429 del **mensaje** de `GenAiApiException` con `GeminiRateLimitCoordinator.parseRetryDelaySeconds`, y si no aparecen, caer en la ventana propia del coordinador (D-214). El coordinador **no se toca**
- [ ] T027 En `GenAiSummaryDataSource`, rellenar `GeminiSummaryResult.Success` desde `usageMetadata` (`promptTokenCount`, `candidatesTokenCount`, `totalTokenCount`) y `systemFingerprint` desde `modelVersion` (data-model §8)
- [ ] T028 En `GenAiSummaryDataSource`, escribir el registro de fases de contracts §1.9 con `crashReporter.log`, registrando **la forma** de la respuesta y nunca su contenido. **Ningún interceptor de cuerpo, en ningún cliente**

### 2.4 Los consumidores

- [ ] T029 Cambiar la firma de `MAIN/data/source/remote/GeminiSummaryDataSource.kt` a `summarise(system, user, document: UploadedDocument)`. `GeminiSummaryResult` y los siete `GeminiRefusal` **no cambian**
- [ ] T030 Cambiar `MAIN/data/source/remote/SummaryValidator.kt` a `validate(raw: SummaryPayload, totalPages: Int)`, sustituyendo el conjunto de páginas admisibles por `1..totalPages`. El resto —recorte de prosa, filtrado, tope de diez con aviso, y el **recálculo** de `coverage`— no cambia
- [ ] T031 Cambiar `MAIN/data/source/remote/SummaryPromptFactory.kt` a `userMessage(publication, totalPages)`, retirar el hueco `{{documentWithPageMarkers}}` y su constante, y reescribir el mensaje de sistema para decir que el documento va adjunto. **La sustitución sigue haciéndose después de `trimIndent()`**
- [ ] T032 Reescribir la tubería de `MAIN/data/repository/AiSummaryRepositoryImpl.kt` a `ensureLocalCopy → pageCount → session.open → summarise → validate → store`, con `pageCount` **antes** de la subida para que un documento protegido no salga del dispositivo (FR-004). Publicar `Preparing(UPLOADING_DOCUMENT)` mientras se sube y `Generating(totalPages)` mientras se genera
- [ ] T033 En `AiSummaryRepositoryImpl`, implementar `releaseDocumentSession` delegando en `AiDocumentSessionStore.release`, y actualizar el mapeo de `GeminiRefusal` para que `Malformed` procedente de la **subida** se traduzca en `UnreadableDocument` y el procedente de la **generación** siga siendo `InvalidResponse`
- [ ] T034 Actualizar el KDoc de cabecera de `AiSummaryRepositoryImpl`: la tubería nueva y las tres garantías, con la segunda sustituida por «un documento protegido nunca sale del dispositivo» (contracts §1.6)
- [ ] T035 En `AiSummaryRepositoryImpl`, quitar de la analítica los parámetros que dejan de tener sentido (`pages_analyzed`, `partial`) en vez de mantenerlos mintiendo, y conservar `cached`, `total_pages` y `total_tokens`
- [ ] T036 En `MAIN/ui/detail/PublicationDetailViewModel.kt`, recibir `ReleaseAiDocumentSessionUseCase` y llamarlo en `onCleared()`, con un comentario explicando por qué es este el punto —Preguntar y el visor se apilan encima— y por qué la llamada no puede ser suspendida
- [ ] T037 [P] En `MAIN/ui/detail/component/AiSummaryTab.kt`, cambiar la línea del mapa `messageRes()` de `NoExtractableText` a `UnreadableDocument` → `R.string.ai_error_unreadable`, y retirar la rama del aviso **previo** de lectura parcial
- [ ] T038 Actualizar `MAIN/core/di/DataModule.kt`: fuera `PdfTextExtractor` y `PdfTextNormalizer`; dentro `PdfPageCounter`, `GenAiClientProvider`, `AiDocumentUploader`, `AiDocumentSessionStore`, y `GeminiSummaryDataSource` apuntando a `GenAiSummaryDataSource`. El `Client` se construye en una función fábrica de `data/source/remote/`, **nunca** en el módulo
- [ ] T039 Añadir `factory { ReleaseAiDocumentSessionUseCase(repository = get()) }` a `MAIN/core/di/DomainModule.kt`

### 2.5 Los textos

- [ ] T040 En `app/src/main/res/values/strings.xml`: borrar `ai_summary_phase_extracting`, `ai_error_no_text` y el plural `ai_summary_partial_before`; añadir `ai_summary_phase_uploading` («Preparando el documento…») y `ai_error_unreadable`. Conservar `ai_summary_partial_after` (contracts §3)

### 2.6 Los borrados

- [ ] T041 Borrar `MAIN/data/source/remote/OkHttpGeminiSummaryDataSource.kt` y `TEST/data/source/remote/OkHttpGeminiSummaryDataSourceTest.kt` — **solo después de T049a y T049b**, que portan sus dos pruebas de regresión
- [ ] T041a Antes de borrar `GeminiDtos.kt`, **mover `GeminiUsage` a `GeminiSummaryDataSource.kt` renombrado a `SummaryUsage`**, sin `@Serializable` ni `@SerialName` —ya no se deserializa de un cuerpo, se construye desde `usageMetadata`— y conservando `totalThoughtTokens`, que es el diagnóstico que dice si el razonamiento está apagado. Actualizar `GeminiSummaryResult.Success` y los tres ficheros de prueba que lo nombran. Sin esto, T042 deja el proyecto sin compilar
- [ ] T042 [P] Borrar `MAIN/data/source/remote/GeminiDtos.kt`, que tras T041a ya no contiene nada vivo
- [ ] T043 [P] Borrar `MAIN/data/source/remote/DocumentText.kt` y `TEST/data/source/remote/DocumentTextTest.kt`
- [ ] T044 [P] Borrar `MAIN/data/source/local/PdfTextNormalizer.kt` y `TEST/data/source/local/PdfTextNormalizerTest.kt`
- [ ] T045 Borrar `MAIN/data/source/local/PdfTextExtractor.kt`, `MAIN/data/source/local/AndroidxPdfTextExtractor.kt` y `ATEST/data/source/local/AndroidxPdfTextExtractorTest.kt`

### 2.7 Que vuelva a compilar y a estar probado

- [ ] T046 Crear `TEST/fake/FakeAiDocumentUploader.kt`: cuenta subidas y borrados, y permite programar un rechazo
- [ ] T047 Actualizar `TEST/fake/FakeGeminiSummaryDataSource.kt` a la firma nueva, registrando el `UploadedDocument` recibido
- [ ] T047a Añadir a `TEST/data/source/remote/AiDocumentSessionStoreTest.kt` la aserción de que el `displayName` que viaja al servicio se compone **solo de datos públicos de la publicación** —nada de la persona, ni rutas locales, ni identificadores de dispositivo— (FR-006)
- [ ] T048 Crear `TEST/data/source/remote/AiDocumentSessionStoreTest.kt` con **una prueba por cada una de las siete invariantes** de contracts §1.3, incluida la de las dos aperturas concurrentes
- [ ] T049 Crear `TEST/data/source/remote/GenAiSummaryDataSourceTest.kt` contra MockWebServer **en HTTP plano**, apuntado con `HttpOptions(baseUrl = …)` (D-224). Cubre: la petición lleva `file_data` y **no** el texto; el esquema viaja; se pide salida JSON; 401 → `NotConfigured`; 429 → cuota con el retraso sacado del mensaje; 5xx → reintento; `finishReason=MAX_TOKENS` → `BlankSummary`; prosa vacía → `BlankSummary`; sin credencial → cero peticiones
- [ ] T049a **Portar la regresión de la cancelación** desde `OkHttpGeminiSummaryDataSourceTest` a `GenAiSummaryDataSourceTest`: salir mientras hay una petición en vuelo es una cancelación y **no** un error de conexión (FR-023, D-218). El defecto no era del cliente HTTP sino de cualquier llamada bloqueante dentro de una corrutina, así que sigue siendo posible. Necesita dispatchers reales: en tiempo virtual no hay diferencia entre un hilo bloqueado y una cancelación
- [ ] T049b **Portar la regresión de la cuota en el reintento** desde `OkHttpGeminiSummaryDataSourceTest` a `GenAiSummaryDataSourceTest`: un reintento sin margen conserva el rechazo original en vez de convertirlo en uno de cuota (FR-027). Es la prueba del defecto «un arreglo que convierte un error en otro es peor que no arreglar nada»
- [ ] T050 En `GenAiSummaryDataSourceTest`, añadir las pruebas de registro: con una generación completa, **ninguna** línea contiene la credencial y **ninguna** contiene texto del documento (FR-035, FR-036, SC-009). Añadir también que dos fallos que en pantalla comparten mensaje —`UnreadableDocument` y `Unknown`— dejan líneas **distintas** en el registro (FR-040)
- [ ] T051 [P] Actualizar `TEST/data/source/remote/SummaryValidatorTest.kt` y `TEST/data/source/remote/SummaryPromptFactoryTest.kt` a las firmas nuevas, añadiendo en el segundo una aserción de que el mensaje de sistema dice que el documento va adjunto —comparando sobre el mensaje con los espacios colapsados, no sobre fragmentos elegidos para caber en una línea—
- [ ] T052 Actualizar `TEST/data/repository/AiSummaryRepositoryImplTest.kt` a la tubería nueva, conservando las pruebas que afirman peticiones que **no** deben ocurrir
- [ ] T053 [P] Actualizar `TEST/integration/AiSummaryFlowIntegrationTest.kt` a la cadena nueva
- [ ] T054 Actualizar `TEST/di/KoinModulesTest.kt` en sus **dos** listas: `CROSS_MODULE_TYPES` y la resolución uno a uno, con los cinco bindings nuevos y sin los dos que se van
- [ ] T055 [P] Actualizar `TEST/ui/detail/AiErrorMessagesTest.kt` al mensaje renombrado, y comprobar que el nuevo tampoco nombra proveedor ni modelo
- [ ] T056 [P] Crear `ATEST/data/source/local/AndroidxPdfPageCounterTest.kt`: cuenta las páginas de un PDF de muestra y devuelve `Encrypted` ante uno protegido
- [ ] T057 [P] Actualizar `ATEST/ui/detail/AiSummaryTabTest.kt`: los dos literales que cambian, **las tres construcciones de `Generating(analysedPages, totalPages)`** (líneas 97, 107 y 116) a `Generating(totalPages)`, y **retirar la prueba del aviso previo de lectura parcial**, que deja de existir. Son trece estados y pasan a doce

**Checkpoint**: `./gradlew :app:assembleDebug` y `./gradlew :app:testDebugUnitTest` en verde. La
aplicación vuelve a generar resúmenes, ahora subiendo el documento.

---

## Phase 3: User Story 1 — Un documento escaneado también se resume (Priority: P1) 🎯 MVP

**Objetivo**: que una publicación que hoy no tiene resumen posible pase a tenerlo.

**Prueba independiente**: abrir una publicación cuyo PDF sea un escaneado sin capa de texto y pedir
su resumen.

- [ ] T058 [US1] Comprobar que en `AiSummaryRepositoryImpl` **no queda ninguna comprobación de texto extraíble** antes de enviar: ni un `hasUsableText`, ni un recuento de caracteres, ni un `MIN_USABLE_CHARACTERS`. Que un escaneado se resuma depende de que ese juicio ya no exista (FR-002)
- [ ] T059 [US1] Añadir a `TEST/data/repository/AiSummaryRepositoryImplTest.kt` una prueba de que un documento cuyo contador de páginas devuelve `Success` **siempre** llega a la subida, sin ninguna condición sobre su contenido
- [ ] T060 [US1] Añadir a `TEST/data/repository/AiSummaryRepositoryImplTest.kt` la prueba complementaria: un documento `Encrypted` **no** llega a la subida y produce `AiSummaryError.EncryptedPdf` (FR-004, SC-007)
- [ ] T061 [US1] Añadir la prueba de que un `Rejected(Malformed)` de la **subida** produce `AiSummaryError.UnreadableDocument`, que no es reintentable (FR-029)
- [ ] T062 [US1] Comprobar a mano, con credencial, los puntos 1, 2, 3 y 10 del `quickstart.md` §3, y **el punto 3 del §3 bis**, que es la comprobación que decide si SC-001 se cumple

**Checkpoint**: US1 verificada. Si el punto 3 del §3 bis falla, **decirlo en `spec.md` y en el informe**, no disimularlo.

---

## Phase 4: User Story 2 — Lo que ya estaba resumido no se pierde (Priority: P1)

**Objetivo**: que actualizar no borre nada.

**Prueba independiente**: generar resúmenes con la versión actual, actualizar, y verificar que siguen
apareciendo marcados y que rehacerlos funciona.

- [ ] T063 [US2] Comprobar que `isStale()` en `AiSummaryRepositoryImpl` sigue comparando las tres constantes y el checksum, y que **ninguna ruta borra** una fila de `ai_summaries`. Confirmar por búsqueda que sigue sin haber ninguna sentencia de borrado en ninguno de los cinco DAO
- [ ] T064 [US2] Añadir a `TEST/data/repository/AiSummaryRepositoryImplTest.kt` una prueba de que una fila guardada con `prompt_version = "boc-summary-es-v4"` se muestra como `Ready(isStale = true)` y **no** se borra (FR-014, FR-015)
- [ ] T065 [US2] Comprobar que `generate(force = false)` con una fila guardada devuelve sin tocar el contador de páginas, la subida ni el servicio, y que hay prueba que lo afirma (FR-016, SC-002)
- [ ] T066 [US2] Comprobar a mano los puntos 5 y 6 del `quickstart.md` §3

**Checkpoint**: US2 verificada.

---

## Phase 5: User Story 3 — El documento se prepara una vez y se retira al salir (Priority: P2)

**Objetivo**: que la sesión gobierne el coste.

**Prueba independiente**: regenerar sin salir y comprobar que no hay segunda preparación; salir y
comprobar que el documento se retira.

- [ ] T067 [US3] Añadir a `TEST/data/repository/AiSummaryRepositoryImplTest.kt` una prueba de que `generate(force = true)` dos veces seguidas sobre la misma publicación produce **una sola** subida (FR-008, SC-005)
- [ ] T068 [US3] Añadir una prueba de que `releaseDocumentSession` provoca el borrado remoto, y otra de que hacerlo con una clave que no es la de la sesión actual **no** borra nada (FR-009, SC-006)
- [ ] T069 [US3] Añadir a `TEST/ui/detail/PublicationDetailViewModelTest.kt` una prueba de que `onCleared()` invoca el caso de uso de liberación con la clave de la publicación. `onCleared()` es `protected`: se invoca por reflexión sobre la superclase, como ya se hace para el visor
- [ ] T070 [US3] Comprobar a mano el punto 4 del `quickstart.md` §3 y los puntos 4, 5 y 6 del §3 bis, leyendo el registro con `adb logcat -s BOC:V`

**Checkpoint**: US3 verificada.

---

## Phase 6: User Story 4 — Volver a ver el aviso, una sola vez (Priority: P2)

**Objetivo**: que nadie dé por aceptado un texto que no ha leído.

**Prueba independiente**: aceptar con la versión anterior, actualizar, y ver que reaparece una vez.

- [ ] T071 [US4] Reescribir `ai_notice_body` en `app/src/main/res/values/strings.xml`: se envía el **documento oficial completo**, el servicio lo conserva un tiempo limitado, la aplicación lo retira al salir de la publicación, y no se envía nada de la persona (FR-032)
- [ ] T072 [US4] Cambiar `KEY_NOTICE_ACCEPTED` de `"ai_notice_accepted_v2"` a `"ai_notice_accepted_v3"` en `MAIN/data/source/local/AiPreferences.kt`, con comentario citando FR-033 y el precedente FR-031a de la 009. La clave vieja **no se lee, no se migra y no se borra**
- [ ] T073 [US4] Actualizar `TEST/data/source/local/AiPreferencesTest.kt` con la prueba de regresión: una instalación que tiene `_v2` a `true` observa `false` en `_v3` (SC-008)
- [ ] T074 [US4] Comprobar a mano el punto 1 del `quickstart.md` §3, y que aceptándolo no reaparece en otra publicación

**Checkpoint**: US4 verificada. El aviso dice toda la verdad y se lee una vez.

---

## Phase 7: User Story 5 — Compilable sin credencial, y empaquetable (Priority: P3)

**Objetivo**: que la aplicación se pueda construir sin secretos y que la versión que se distribuye
funcione. La segunda mitad es nueva y no es trámite.

- [ ] T075 [US5] Comprobar que sin `GEMINI_API_KEY` la build sigue en verde, `GenAiClientProvider.client()` devuelve `null` y la pantalla dice «no configurado» sin ninguna petición (FR-043, SC-010). Punto 12 del `quickstart.md` §3
- [ ] T076 [US5] Activar la optimización: `buildTypes { release { optimization { enable = true } } }` en `app/build.gradle.kts`, con comentario citando FR-041 y D-222
- [ ] T077 [US5] Crear `app/src/main/keepRules/genai.keep` con los `-dontwarn` de las dependencias opcionales de google-auth y google-http-client (`org.apache.**`, `javax.naming.**`, `com.google.appengine.**`, `org.slf4j.impl.**`) y un comentario diciendo que el AAR de la librería **no trae reglas de consumidor**
- [ ] T078 [US5] Ejecutar `./gradlew :app:assembleRelease` e ir añadiendo a `genai.keep` **solo** las reglas que el fallo pida, una a una, anotando cada una con su motivo
- [ ] T079 [US5] Instalar el APK de release en un dispositivo y recorrer las nueve pantallas de la lista del `quickstart.md` §3 «Puerta 5». Rellenar la tabla de resultados con la fecha. **Esta es FR-042 y no es sustituible por ninguna prueba automática**

**Checkpoint**: US5 verificada. Por primera vez alguien ha ejecutado la versión que se distribuye.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T080 [P] Añadir la **novena** regla a `TEST/architecture/ArchitectureRulesTest.kt`, calcada de la de Firebase: ningún fichero fuera de `data` importa `com.google.genai` (D-225)
- [ ] T081 Fijar `AiSummaryConstants.MODEL_ID` al modelo confirmado en el `quickstart.md` §3 bis, y anotar en `research.md` D-213 cuál se eligió y qué se comprobó de él
- [ ] T082 Rellenar en `quickstart.md` §0 bis los cuatro valores «pendientes de confirmar» con lo medido, y las dos tablas de resultados —§3 bis y Puerta 5— con su fecha
- [ ] T083 Actualizar `CLAUDE.md`: la tubería del Resumen IA (ya no hay extracción de texto), que las reglas de Konsist pasan de **ocho a nueve**, que `androidx.pdf` se toca en `ui/pdf` y en el contador de páginas, la quinta puerta de calidad, Java 17, y la trampa nueva de que `GoogleCredentials` no se puede excluir
- [ ] T084 [P] Revisar que ningún literal de `strings.xml` quedó huérfano y que ninguno nuevo nombra proveedor, modelo ni código
- [ ] T085 Las cinco puertas en orden, con la instrumentada en segundo plano y `adb shell settings put secure navigation_mode 0` antes: `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug`, `assembleRelease`
- [ ] T086 Última comprobación de fugas antes de cerrar: `git grep -nE 'AIza[0-9A-Za-z_-]{30,}|AQ\.[0-9A-Za-z_-]{30,}'` sobre la rama entera, incluidos los ficheros de `specs/`

**Checkpoint**: feature terminada.

---

## Dependencies & Execution Order

```
Phase 1 (Setup)
   └─> Phase 2 (BLOQUEANTE)
          2.1 Build ──> T009 verifica que la dependencia resuelve
          2.2 Dominio  [independiente de 2.3, puede ir en paralelo]
          2.3 Lo nuevo [independiente de 2.2, puede ir en paralelo]
             └─> 2.4 Consumidores  (necesita 2.2 y 2.3)
                    └─> 2.5 Textos
                           └─> 2.6 Borrados  (al final: la build está roja el menor tiempo posible)
                                  └─> 2.7 Pruebas
   └─> Phase 3 (US1) ─┐
   └─> Phase 4 (US2) ─┤
   └─> Phase 5 (US3) ─┼─> las cuatro son independientes entre sí
   └─> Phase 6 (US4) ─┘
   └─> Phase 7 (US5)   ← depende de que todo lo anterior compile
   └─> Phase 8 (Polish)
```

**Dependencias que no se ven en el árbol:**

- **T081 depende de T062**: el modelo no se fija hasta haber comprobado que acepta ficheros, respeta
  el esquema y lee un escaneado.
- **T078 puede volver a T077 varias veces.** Es un bucle, no un paso.
- **T045 no puede ir antes que T032**: borrar el extractor con el repositorio todavía llamándolo deja
  la build rota más tiempo del necesario.
- **T072 y T073 van juntas.** Cambiar la clave sin la prueba de regresión deja FR-033 sin vigilancia.
- **T041 depende de T049a y T049b**, que están en una fase posterior. Es la única dependencia que va hacia atrás en el documento, y es deliberada: el fichero que T041 borra contiene **dos pruebas de regresión** de defectos que la librería nueva no arregla, y el principio V prohíbe perderlas. Portarlas primero, borrar después.
- **T042 depende de T041a.** `GeminiUsage` vive en el fichero que T042 borra y es parte de la firma de `GeminiSummaryResult.Success`, que sobrevive.

**Paralelismo real dentro de la fase 2**: 2.2 y 2.3 tocan ficheros disjuntos y pueden hacerse a la
vez. Dentro de 2.7, T048, T049, T051, T053, T055, T056 y T057 son ficheros distintos.

---

## Implementation Strategy

**El MVP es la fase 2 más la fase 3.** No se puede entregar menos: hasta que la fase 2 cierre, la
aplicación no genera ni un resumen, y la fase 3 es lo único que una persona nota.

**Orden recomendado**:

1. Fases 1 y 2 de un tirón, sin dejar la build roja de un día para otro.
2. Fase 3 con el §3 bis del `quickstart.md` **antes** de seguir. Si el servicio no lee escaneados,
   SC-001 no se cumple y hay que decirlo ya, no al final.
3. Fases 4, 5 y 6 en cualquier orden.
4. Fase 7 con tiempo: es la primera vez que se compila release y puede dar sorpresas.
5. Fase 8 y las cinco puertas.

**Dónde puede torcerse**, en orden de probabilidad:

- **Ktor 2.3.8 contra OkHttp 5.5.0** (T009). Salida escrita en D-223.
- **R8 rompe algo que solo se ve en release** (T078, T079). Salida: la keep rule concreta.
- **El servicio no lee escaneados** (T062). No hay salida técnica: se cambia lo que promete SC-001.
- **El modelo elegido no acepta `responseJsonSchema` con una parte de fichero** (T081). Salida:
  `MODEL_ID`, que está ahí exactamente para esto.

---

## Notes

- **Ninguna tarea se da por terminada sin su prueba en verde.** Prohibido `@Ignore`, comentar o
  borrar una prueba para que pase la build.
- **Las cinco pruebas que se borran** —`PdfCorpusTest`, `DocumentTextTest`, `PdfTextNormalizerTest`,
  `OkHttpGeminiSummaryDataSourceTest`, `AndroidxPdfTextExtractorTest`— se borran porque desaparece el
  código que probaban. No es lo mismo que retirarlas.
- **La tanda instrumentada tarda casi tres horas.** Lánzala en segundo plano y con un solo
  dispositivo (`ANDROID_SERIAL=emulator-5554`).
- **Commits en español con prefijo convencional**, en la rama `010-gemini-sdk-oficial`, nunca en
  `main`.
- **Código, nombres y comentarios en inglés**; documentación y commits en español.
