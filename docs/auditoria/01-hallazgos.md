# Hallazgos técnicos — Fase 1

Baseline: `ee88d240f5b62c56e25d8233196be542d94e1509`, branch `main`, 2026-09-06. Referencias al working tree, sin modificaciones de fuentes, configuración o tests de la aplicación.

## Matriz de hallazgos

| ID | Severidad | Categoría | Resumen | Confianza |
|---|---|---|---|---|
| STAB-001 | Alta | Estabilidad | Un checksum de caché truncado escapa como excepción hasta el ViewModel | Confirmado |
| SEC-001 | Alta | Seguridad | La credencial Gemini se incorpora al cliente distribuible | Confirmado |
| STAB-003 | Media | Estabilidad | Un ciclo interrumpido o un fallo de coincidencias pierde avisos sin recuperación | Confirmado |
| STAB-002 | Media | Estabilidad | Fallos de escritura PDF dejan la pantalla en estado de descarga | Confirmado |
| SEC-002 | Media | Privacidad | Regenerar un resumen omite la aceptación vigente del aviso IA | Confirmado |
| SEC-003 | Media | Seguridad | La comprobación de host del PDF admite URLs hacia otros dominios | Confirmado |
| STAB-004 | Media | Estabilidad | Los Flows de lectura terminan tras el fallback vacío y no recuperan la observación | Confirmado |
| PERF-001 | Media | Almacenamiento | Cada PDF visitado queda protegido indefinidamente de la evicción durante el proceso | Confirmado |
| PERF-002 | Media | Red / Asincronía | Cancelar la corrutina no cancela la llamada bloqueante de OkHttp | Confirmado |

**Totales:** 9 hallazgos; 0 críticos, 2 altos, 7 medios, 0 bajos. Los nueve tienen evidencia de código; seis tienen además reproducción aislada sobre clases compiladas. No se incluyen sospechas ni hipótesis de rendimiento en esta matriz. La magnitud de consumo, frecuencia y latencia en dispositivos sigue sin medirse.

## Evidencia, herramientas y límites

- Se recuperó `PROGRESO.md` y `00-mapa.md`, y se revalidó HEAD/working tree. Se intentó reanudar los tres subagentes GPT-6 Astra `ultra`; los tres fallaron por límite de uso antes de aportar revisión de Fase 1. Esta fase la completó directamente el agente principal, usando también el reconocimiento anterior.
- `:app:lintDebug`: **0 errores y 17 warnings**, ejecución satisfactoria. Nueve avisos de versiones, cinco de recursos sin uso y tres de iconos XML/bitmap. No se convierten automáticamente en defectos.
- `:app:testDebugUnitTest`: primero UP-TO-DATE; después repetido con `--rerun`: **1.193 tests, 159 suites, 0 fallos, 0 errores, 0 omitidos** según XML generados. Son pruebas JVM, incluidas las que usan Robolectric; no instrumentadas.
- `:app:compileReleaseKotlin`: **correcto**. No se ensambló, firmó, instaló ni publicó una release. Compilar Kotlin no equivale a verificar un APK release completo.
- `:app:dependencies --configuration debugRuntimeClasspath`: correcto en modo offline. El inventario está en `gradle-verificacion.log`; por ejemplo, stdlib se resuelve a 2.3.20, OkHttp Android a 5.5.0, Okio JVM a 3.18.1, coroutines a 1.11.0 y serialization JSON a 1.8.1. La stdlib resuelta no debe confundirse con la versión del compilador.
- Se intentó `:app:compileReleaseKotlin -Pandroidx.enableComposeCompilerMetrics=true -Pandroidx.enableComposeCompilerReports=true`. La compilación terminó, pero no aparecieron informes/métricas. El script actual aplica el plugin Compose 2.2.10 sin destinos de métricas configurados (`app/build.gradle.kts:3`; `gradle/libs.versions.toml:136`). **Compose Compiler metrics: no disponibles mediante la configuración actual.** No se modificó Gradle para habilitarlas. La configuración del plugin se contrastó con la [documentación Android](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).
- Los diagnósticos `DiagnosticoDocumentos.java`, `DiagnosticoRed.java` y `DiagnosticoAvisos.java` ejecutan clases ya compiladas de la app. Solo escriben archivos aislados dentro de `docs/auditoria/`; no son modificaciones de las suites del proyecto. Red usa interceptores locales: no abre sockets. Avisos usa los casos de uso reales con repositorios simulados: no es una prueba end-to-end de SQLite.
- Repetición: `python3 docs/auditoria/ejecutar-diagnosticos.py`. Usa el JDK de Android Studio y dependencias ya presentes en la caché, incluido un JAR OkHttp cuyo hash se compara con el AAR 5.5.0. Los logs separan resultados de advertencias del compilador del diagnóstico.
- Fallos de herramientas resueltos: Java no localizado inicialmente; se usó `JAVA_HOME` de Android Studio. El sandbox denegó el lock de Gradle; se obtuvo permiso y se repitió. El diagnóstico Java necesitó ajustar el classpath al artefacto Android real y al android.jar de pruebas con valores predeterminados. No se alteró la app para resolverlos.
- No se ejecutaron instrumentadas ni recorridos UI en dispositivo, Perfetto, profiler, Macrobenchmark o mediciones SQL sobre instalaciones reales. No se afirma haber observado un crash completo de la Activity: en STAB-001 se reproduce la excepción del repositorio y se verifica estáticamente su llegada sin captura al `viewModelScope`.

Logs conservados: `gradle-diagnostico.log`, `gradle-diagnostico-jdk.log`, `gradle-verificacion.log`, `diagnostico-documentos.log`, `diagnostico-red.log`, `diagnostico-avisos.log`. Los resultados de Fase 0 sobre ausencia de ejecución quedan superados por estos diagnósticos de Fase 1.

## STAB-001 — Un checksum truncado de caché provoca una excepción no capturada

**Severidad:** Alta  
**Categoría:** Estabilidad  
**Confianza:** Confirmado  
**Ubicación:** `app/src/main/java/com/jrblanco/boccantabria/data/repository/DocumentRepositoryImpl.kt:58`

### Evidencia

```kotlin
cache.get(key)?.let { stored ->
    publish(key, DocumentStatus.Available(stored))
    return@withContext AppResult.Success(stored)
}
// El try empieza más abajo, en la línea 74.
```

`app/src/main/java/com/jrblanco/boccantabria/data/source/local/FileDocumentCache.kt:49` pasa `readChecksum(externalKey) ?: EMPTY_CHECKSUM` a `OfficialDocument`. Una cadena vacía no es null. `app/src/main/java/com/jrblanco/boccantabria/domain/model/OfficialDocument.kt:27` exige un hash hexadecimal de 64 caracteres y lanza `IllegalArgumentException` si no lo es.

### Problema

La lectura de caché queda fuera de la frontera que convierte errores en `AppResult`. El checksum lateral se escribe después de mover el PDF (`FileDocumentCache.kt:60`, `:68`, `:106`); no hay escritura atómica conjunta ni validación recuperable al leer. Un fallo/interrupción de esa escritura puede dejar PDF y sidecar vacío o parcial.

### Impacto

Abrir esa publicación puede cerrar la aplicación. `PdfViewerViewModel.kt:114` y `PublicationDetailViewModel.kt:252` llaman al caso de uso desde `viewModelScope.launch` sin capturar esta excepción; el caso de uso delega directamente (`app/src/main/java/com/jrblanco/boccantabria/domain/usecase/OpenOfficialDocumentUseCase.kt:17`). Reabrir vuelve a leer la misma entrada inválida.

### Escenario de fallo

El diagnóstico guarda una entrada real con `FileDocumentCache`, trunca únicamente su `.sha256` aislado y llama al repositorio real. Resultado: `Excepción escapada con checksum truncado: java.lang.IllegalArgumentException`. No se ha medido la frecuencia de escrituras interrumpidas en dispositivos.

### Corrección propuesta

Incluir la lectura de caché en la frontera de errores y validar los metadatos antes de construir el modelo. Una entrada incompleta debe invalidarse/repararse o tratarse como caché ausente. Hacer atómica la escritura de metadatos y garantizar un resultado de error recuperable si no puede reconstruirse la copia.

### Verificación

Cubrir sidecar vacío, truncado, ausente y fallo de lectura; el repositorio no debe propagar excepciones ordinarias de caché. Probar después la apertura/reintento en el visor con la entrada inválida. Mantener propagación de cancelación.

### Notas

El sidecar totalmente ausente ya tiene un fallback; el caso demostrado es **presente pero inválido**. No confundirlos. La reproducción se encuentra en `DiagnosticoDocumentos.java` y `diagnostico-documentos.log`.

## SEC-001 — La credencial Gemini se incorpora al cliente distribuible

**Severidad:** Alta  
**Categoría:** Seguridad  
**Confianza:** Confirmado  
**Ubicación:** `app/build.gradle.kts:56`

### Evidencia

```kotlin
buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey.get()}\"")
```

El campo se declara en `defaultConfig`, incluyendo release. `app/src/main/java/com/jrblanco/boccantabria/data/source/remote/GeminiApiKeyProvider.kt:30` lo consume y `OkHttpGeminiSummaryDataSource.kt:137` lo envía en `x-goog-api-key`.

### Problema

Mantener el archivo de entrada fuera de Git no mantiene la credencial fuera de la aplicación compilada. Se comprobó una cadena no vacía en `local.properties:14` y en `app/build/generated/source/buildConfig/debug/com/jrblanco/boccantabria/BuildConfig.java:13`, sin copiar su valor. Además, se comprobó que el mismo valor aparece literalmente en la clase release recién compilada `BuildConfigGeminiApiKeyProvider.class`; `resultados-verificacion.json` conserva únicamente el booleano de esa comprobación. Representación en este informe: `GEMINI_API_KEY=<REDACTADO>`.

### Impacto

Quien reciba una compilación con esa clave puede extraerla. Su reutilización puede afectar a la cuota o coste del proyecto según vigencia y restricciones del servicio. Google indica que las claves Gemini compiladas en aplicaciones móviles son extraíbles y recomienda mantenerlas fuera del cliente mediante un backend. [Gestión de claves Gemini](https://ai.google.dev/gemini-api/docs/api-key).

### Escenario de fallo

Una build con la configuración local se distribuye; un tercero inspecciona el bytecode o la ejecución HTTP y obtiene el valor. No se ha probado el valor contra Gemini ni confirmado que esa build se haya distribuido.

### Corrección propuesta

Retirar la credencial privilegiada del cliente y canalizar llamadas mediante un servicio que aplique controles de uso o una integración de cliente diseñada para no exponerla. Revisar restricciones y rotar la clave si se confirma distribución o exposición. Ofuscación y ocultación en código nativo no resuelven esta frontera.

### Verificación

Inspeccionar la futura build distribuible sin publicar valores: no debe contener la credencial del proveedor. Verificar limitación de uso del servicio y funcionamiento de IA con/sin configuración. Confirmar el estado de la clave en la consola del propietario.

### Notas

No se encontró la credencial local actual por coincidencia exacta en los cambios de `git log --all -S`. La búsqueda por patrones encontró configuración Firebase y una antigua fixture de test; el commit `852bbc9` documenta esta última como falsa. No se consideran secretos Gemini reales filtrados al historial. La configuración Firebase pública por sí sola tampoco demuestra una vulnerabilidad. Severidad alta, no crítica: explotación, vigencia y exposición pública no verificadas.

## STAB-003 — Los avisos pendientes se pierden después de persistir las publicaciones

**Severidad:** Media  
**Categoría:** Estabilidad  
**Confianza:** Confirmado  
**Ubicación:** `app/src/main/java/com/jrblanco/boccantabria/domain/usecase/RunSyncCycleUseCase.kt:80`

### Evidencia

```kotlin
if (summary.newKeys.isEmpty() || rules.isEmpty()) {
    return SyncCycleOutcome(summary, emptyList(), AlertDelivery.NONE)
}
val fresh = publications.byKeys(summary.newKeys)
// ...
val recorded = alerts.recordMatches(candidates)
```

`app/src/main/java/com/jrblanco/boccantabria/data/repository/PublicationRepositoryImpl.kt:226` confirma el upsert y `:229` el estado del feed antes de devolver las claves nuevas. `app/src/main/java/com/jrblanco/boccantabria/data/repository/AlertRepositoryImpl.kt:128` convierte un fallo de inserción de coincidencias en una lista vacía. No hay registro durable de claves pendientes de evaluar.

### Problema

La frontera entre almacenar publicaciones y registrar coincidencias pierde trabajo. Si la segunda parte falla o el proceso termina entre ambas, la siguiente sincronización devuelve las filas como existentes o el feed como no modificado. Ya no entran en `newKeys`, por lo que no se vuelve a intentar la evaluación perdida.

### Impacto

Faltan entradas en Novedades y notificaciones aunque las publicaciones estén en el archivo. El ciclo puede terminar como success sin avisos. No es pérdida del boletín ni exige rehacer cada notificación ya entregada.

### Escenario de fallo

Instalación ya inicializada y regla activa: se inserta una publicación coincidente, falla la escritura de su coincidencia y se recupera el almacenamiento. El diagnóstico de los casos de uso reales ejecuta dos ciclos con esos resultados de frontera: ambos devuelven success, solo se intenta registrar una vez y hay cero entregas. La variante de muerte del proceso se deduce del mismo orden de persistencia; no se reprodujo en dispositivo.

### Corrección propuesta

Hacer durable el trabajo pendiente junto con la inserción de publicaciones, o usar un cursor de evaluación que avance solo tras completar el registro de coincidencias. Reintentar sin duplicar mediante la unicidad existente regla/publicación. Conservar la exclusión del histórico inicial y la semántica de activar/editar reglas.

### Verificación

Inyectar fallo o cancelación tras el upsert y antes/durante el registro; repetir el ciclo con el feed sin cambios y comprobar recuperación exactamente una vez. Añadir prueba de reinicio real cuando exista la solución durable. `DiagnosticoAvisos.java` y `diagnostico-avisos.log` documentan el escenario actual.

### Notas

La simulación usa repositorios falsos para representar el fallo; el orden y ausencia de recuperación están verificados en el código real. No se atribuye un defecto a WorkManager por devolver success aisladamente: cambiarlo por retry sin conservar las claves no repara este caso.

## STAB-002 — Un fallo al almacenar PDF deja el estado en “descargando”

**Severidad:** Media  
**Categoría:** Estabilidad  
**Confianza:** Confirmado  
**Ubicación:** `app/src/main/java/com/jrblanco/boccantabria/data/repository/DocumentRepositoryImpl.kt:81`

### Evidencia

```kotlin
} catch (unexpected: Throwable) {
    crashReporter.recordNonFatal(unexpected)
    cache.discardTemporary(key)
    AppResult.Failure(DomainError.Unknown)
}
```

`fetch()` publica `DocumentStatus.Downloading` en la línea 94. La rama anterior no publica Failed. La rama normal de rechazo sí lo hace en la línea 108.

### Problema

El resultado devuelto y el estado observable discrepan después de una excepción del downloader o de `cache.put`. Los consumidores PDF esperan el estado observable y no usan el `AppResult` para reparar esa transición (`app/src/main/java/com/jrblanco/boccantabria/ui/pdf/PdfViewerViewModel.kt:60`, `:114`; `ui/detail/PublicationDetailViewModel.kt:252`).

### Impacto

El documento permanece visualmente en carga sin presentar el error/reintento esperado, incluso cuando el job ya terminó.

### Escenario de fallo

Tras descargar, falla el movimiento/escritura del documento o checksum, por ejemplo por falta de espacio. El diagnóstico inyecta un fallo de disco en `put` y ejecuta el repositorio real: resultado `Failure(error=Unknown)` y estado `{write-failure=Downloading(...)}`.

### Corrección propuesta

Publicar un estado terminal coherente en todos los caminos de error, proteger la limpieza para que no eclipse el fallo principal y asegurar la finalización/eliminación de la operación compartida. Mantener separada la cancelación del usuario de un error operativo.

### Verificación

Inyectar excepciones en downloader, put y cleanup; comprobar resultado, emisión terminal, liberación de `inFlight` y posterior reintento. El test existente `app/src/test/java/com/jrblanco/boccantabria/data/repository/DocumentRepositoryImplTest.kt:163` prueba un downloader que lanza, pero solo afirma resultado y temporales; no verifica el estado final.

### Notas

Este caso es distinto de STAB-001: ocurre dentro del try de descarga y no necesita un sidecar previo inválido. Reproducido en `diagnostico-documentos.log`.

## SEC-002 — Regenerar omite la aceptación de la versión vigente del aviso IA

**Severidad:** Media  
**Categoría:** Privacidad  
**Confianza:** Confirmado  
**Ubicación:** `app/src/main/java/com/jrblanco/boccantabria/ui/detail/PublicationDetailViewModel.kt:199`

### Evidencia

```kotlin
fun onGenerateSummary() {
    if (!uiState.value.aiNoticeAccepted) {
        noticePending.value = true
        return
    }
    generate(force = false)
}
fun onRegenerateSummary() = generate(force = true)
```

`app/src/main/java/com/jrblanco/boccantabria/ui/detail/component/AiSummaryTab.kt:87` presenta Ready independientemente del aviso; `:240` conecta sus acciones a regeneración. La aceptación está versionada en `app/src/main/java/com/jrblanco/boccantabria/data/source/local/AiPreferences.kt:70`.

### Problema

La guarda existe solo en la generación inicial. Los resúmenes anteriores sobreviven en Room y pueden estar Ready aunque la nueva clave `ai_notice_accepted_v3` siga en false. La regeneración atraviesa directamente preparación/subida y petición IA.

### Impacto

Se envía el documento al proveedor sin mostrar ni aceptar el aviso actualizado que explica ese envío. Se trata del contrato de consentimiento implementado por la propia app, no de una conclusión jurídica.

### Escenario de fallo

Actualizar desde una versión con resumen almacenado y aceptación de una versión anterior del aviso; abrir el resumen conservado y pulsar Regenerar. No se ejecuta la guarda de `onGenerateSummary`. No se ha hecho una instalación de actualización en dispositivo durante esta auditoría.

### Corrección propuesta

Centralizar la comprobación para toda acción que pueda transmitir datos. Conservar la intención pendiente, incluida la regeneración forzada, y retomarla únicamente tras aceptación vigente. Cancelar el aviso no debe enviar nada.

### Verificación

Resumen persistido + aceptación vigente false: Regenerar debe abrir aviso, con cero llamadas a preparador/uploader/transporte. Tras aceptar, generar una sola vez con force=true. Repetir con aviso cancelado. `app/src/test/java/com/jrblanco/boccantabria/ui/detail/PublicationDetailViewModelTest.kt:426` comprueba que regenerar fuerza una petición, pero no cubre este estado de actualización.

### Notas

El documento es público; no se afirma filtración de documentos privados. El problema es omitir una decisión que la propia aplicación exige al cambiar la versión del aviso.

## SEC-003 — La validación textual del host admite destinos externos

**Severidad:** Media  
**Categoría:** Seguridad  
**Confianza:** Confirmado  
**Ubicación:** `app/src/main/java/com/jrblanco/boccantabria/data/source/remote/OkHttpDocumentDownloader.kt:65`

### Evidencia

```kotlin
val host = url.removePrefix(HTTPS_PREFIX).substringBefore('/').substringBefore(':')
return if (host.equals(allowedHost, ignoreCase = true)) null else DownloadRefusal.UnexpectedHost
```

`app/src/main/java/com/jrblanco/boccantabria/data/source/remote/PublicationNormalizer.kt:29` acepta el enlace por prefijo HTTPS; no elimina userinfo ni comprueba el host real.

### Problema

La función interpreta parte de una URL como hostname sin parsearla con la misma semántica que OkHttp. `https://boc.cantabria.es:443@audit.invalid/documento.pdf` pasa la comprobación, pero su host real es `audit.invalid`: el texto anterior a `@` es userinfo.

### Impacto

La barrera que limita descargas al servicio oficial no se cumple. Un enlace manipulado que llegue desde el feed puede introducir un PDF de otro dominio en el flujo de documento oficial. No se demuestra ejecución de código ni exfiltración de credenciales por esta descarga.

### Escenario de fallo

El diagnóstico ejecuta el downloader real con la URL anterior y un interceptor sin sockets que devuelve un PDF simulado. Registra host `audit.invalid` y resultado `Downloaded`. La explotación requiere conseguir que un enlace así llegue a la app desde una fuente aceptada; no se ha observado en el RSS real.

### Corrección propuesta

Parsear una sola vez con `HttpUrl`, validar scheme/host/credenciales/puerto según política y construir el Request con ese objeto. Revisar también cada destino de redirección: la comprobación inicial no valida los saltos automáticos del cliente compartido.

### Verificación

Casos con userinfo, puerto, host parecido, URL malformada y redirecciones dentro/fuera del dominio. Ninguno de los rechazados debe llegar al transporte. Preservar enlaces oficiales válidos. Evidencia en `DiagnosticoRed.java` y `diagnostico-red.log`.

### Notas

La reproducción confirma el bypass inicial; no se hizo una prueba real de redirección ni se contactó `audit.invalid`. HTTPS sigue funcionando: el fallo es la selección del destino permitido, no una desactivación de TLS.

## STAB-004 — El fallback vacío termina la observación tras un error

**Severidad:** Media  
**Categoría:** Estabilidad  
**Confianza:** Confirmado  
**Ubicación:** `app/src/main/java/com/jrblanco/boccantabria/data/repository/PublicationRepositoryImpl.kt:78`

### Evidencia

```kotlin
.catch { cause ->
    if (cause is CancellationException) throw cause
    crashReporter.recordNonFatal(cause)
    emit(emptyList())
}
```

Mismo mecanismo en `app/src/main/java/com/jrblanco/boccantabria/data/repository/SavedPublicationRepositoryImpl.kt:42`, `SearchRepositoryImpl.kt:52` y `AlertRepositoryImpl.kt:53`. No hay resuscripción/retry en esas ramas.

### Problema

`catch` se ejecuta cuando el upstream ha terminado por excepción. Emitir un fallback no reanuda el Flow de Room. La documentación de [catch en coroutines 1.11.0](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/catch.html) distingue esa emisión del reinicio mediante retry. Los comentarios y un test afirman que la observación sigue viva, pero no lo implementan.

### Impacto

Una lectura fallida puede mostrar archivo/guardados/novedades vacíos o detalle ausente. Aunque se recupere la base y cambien sus tablas, esa suscripción no recibe las actualizaciones. En Home, refrescar modifica `syncState` y Room, pero no sustituye ese upstream terminado (`app/src/main/java/com/jrblanco/boccantabria/ui/home/HomeViewModel.kt:93`).

### Escenario de fallo

Una consulta/mapper lanza durante una suscripción activa. Se emite vacío y se completa. Después se recupera la lectura y se sincronizan o guardan publicaciones: la pantalla sigue combinando el último vacío. Una nueva suscripción —por ejemplo tras el timeout de WhileSubscribed— puede recuperarla; no se afirma que sobreviva al reinicio de la app.

### Corrección propuesta

Representar el fallo sin confundirlo con ausencia de datos y definir recuperación: reintento acotado para fallos transitorios o un disparador explícito de resuscripción. No reintentar indefinidamente corrupción permanente; conservar los últimos datos útiles cuando corresponda.

### Verificación

Fuente que falla una vez y devuelve datos al suscribirse de nuevo: comprobar error, recuperación y nuevas emisiones. El test `app/src/test/java/com/jrblanco/boccantabria/data/repository/SavedPublicationRepositoryImplTest.kt:145` usa únicamente `.first()` sobre el vacío; no verifica continuidad ni recuperación.

### Notas

Confirmado por semántica de Flow y código, sin reproducción de un fallo SQLite físico. No confundir “no propaga excepción al collector” con “sigue observando”.

## PERF-001 — Todos los PDF visitados se consideran en uso hasta terminar el proceso

**Severidad:** Media  
**Categoría:** Rendimiento / Almacenamiento  
**Confianza:** Confirmado  
**Ubicación:** `app/src/main/java/com/jrblanco/boccantabria/data/repository/DocumentRepositoryImpl.kt:116`

### Evidencia

```kotlin
val inUse = statuses.value
    .filterValues { it is DocumentStatus.Available }
    .keys
cache.evict(MAX_CACHE_BYTES, inUse)
```

Las aperturas dejan Available (`:59`, `:100`). El mapa no elimina la entrada al cerrar una pantalla. `app/src/main/java/com/jrblanco/boccantabria/data/source/local/FileDocumentCache.kt:89` excluye esas claves de la expulsión.

### Problema

“Existe copia disponible” se usa como equivalente a “hay un consumidor usando el archivo”. El cierre del visor cierra el PDF (`app/src/main/java/com/jrblanco/boccantabria/ui/pdf/PdfViewerViewModel.kt:142`), pero no libera esa protección en el repositorio. La liberación de sesión IA es otra operación y tampoco la elimina.

### Impacto

El presupuesto de 100 MiB no se aplica a documentos visitados durante el proceso. Una sesión larga que abra suficientes PDF puede acumular más caché de la prevista y conservar sus metadatos en memoria. No se afirma un OOM ni una cantidad concreta observada en dispositivos.

### Escenario de fallo

Visitar documentos diferentes, volver de cada uno y sincronizar. El diagnóstico abre seis claves con el repositorio real y captura la entrada a evict: las seis siguen protegidas. Si sus tamaños superan conjuntamente el presupuesto, el algoritmo omite todas aunque ya no haya visor abierto.

### Corrección propuesta

Separar disponibilidad de retención activa mediante una propiedad explícita de consumidores/leases, o una política equivalente que libere la protección al terminar su uso. Mantener protegidos visor, compartición y preparación IA mientras realmente necesiten el archivo.

### Verificación

Abrir y cerrar varios documentos y comprobar reducción de claves protegidas; exceder el presupuesto con tamaños controlados y verificar evicción de los cerrados, conservando el activo y los guardados si así lo exige el producto. Medir después el uso de disco en una sesión real.

### Notas

Es un fallo verificable de la política de caché, no una propuesta genérica de añadir caché. El reinicio del proceso limpia el mapa; eso limita el alcance, pero no corrige sesiones largas.

## PERF-002 — La cancelación no llega a las llamadas bloqueantes de red

**Severidad:** Media  
**Categoría:** Rendimiento / Red / Asincronía  
**Confianza:** Confirmado  
**Ubicación:** `app/src/main/java/com/jrblanco/boccantabria/data/source/remote/OkHttpDocumentDownloader.kt:46`

### Evidencia

```kotlin
client.newCall(request(url)).execute().use { response ->
    writeVerified(response, into)
}
```

Se ejecuta dentro de `withContext(dispatchers.io)`, sin vincular el Job con `Call.cancel()`. El patrón aparece también en `OkHttpPublicationRemoteDataSource.kt:64`, `OkHttpGeminiSummaryDataSource.kt:145`, `OkHttpGeminiChatDataSource.kt:137` y `OkHttpGeminiDocumentUploader.kt:100`.

### Problema

Cambiar de dispatcher no convierte el I/O bloqueante en cancelable por la corrutina. Los catch de CancellationException y las comprobaciones `ensureActive` al recibir IOException distinguen el resultado posterior, pero no interrumpen por sí solos el socket/lectura en curso.

### Impacto

Salir de una pantalla puede dejar una petición consumiendo red y un hilo hasta respuesta o timeout. En IA puede mantener ocupado el coordinador/sesión y retrasar la siguiente acción. No se estima ahorro de batería ni coste de una solicitud ya aceptada por el servidor.

### Escenario de fallo

El diagnóstico bloquea `execute` mediante un interceptor local, cancela el Job y comprueba `Call.isCanceled=false`, `Job.isCompleted=false`. Solo tras liberar la respuesta termina el Job. En PDF el timeout total declarado es de 180 segundos (`OkHttpDocumentDownloader.kt:146`), pero ese tiempo no se ha medido como latencia real.

### Corrección propuesta

Adaptar Call mediante una API suspendible que registre cancelación y cierre la respuesta correctamente, o una integración equivalente que ejecute `Call.cancel()` cuando se cancele su propietario. Definir cómo afecta cancelar un consumidor a descargas compartidas que aún necesitan otros.

### Verificación

Con servidor lento o interceptor controlado: cancelar a mitad de petición debe cancelar Call y liberar prontamente el trabajo. Repetir en RSS, PDF, subida, sondeo y generación IA, conservando reintentos de fallos auténticos. El test `app/src/test/java/com/jrblanco/boccantabria/data/source/remote/OkHttpGeminiChatDataSourceTest.kt:365` comprueba el resultado tras `join`, pero no que la llamada se cancele ni cuánto tarda.

### Notas

Confirmado con transporte simulado y clases reales. Se necesita dispositivo para cuantificar consumo y una prueba de servidor para comprobar cierre efectivo del socket. No se afirma que una petición cancelada no pueda haber sido procesada ya por el proveedor.

## Cobertura de seguridad y plataforma

### Permisos

El manifest debug fusionado se inspeccionó en `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`. Las dependencias añaden permisos que no aparecen en el manifest fuente.

| Permiso | Uso/procedencia observada | Necesidad/valoración |
|---|---|---|
| INTERNET | Fuente, línea 5; RSS/PDF/Gemini/Firebase | Necesario para esos flujos. |
| ACCESS_NETWORK_STATE | Fuente, línea 6; `AndroidConnectivityDataSource.kt:19` y WorkManager | Necesario para comprobación/constraints. |
| POST_NOTIFICATIONS | Fuente, línea 8; `ui/alerts/form/AlertFormScreen.kt:157` y `data/notification/AndroidAlertNotifier.kt:68` | Uso contextual; se comprueba permiso al entregar. |
| WAKE_LOCK | Fusionado, línea 26; WorkManager/SDKs | Infraestructura de trabajos; no se localizó adquisición manual propia. |
| RECEIVE_BOOT_COMPLETED | Fusionado, línea 31; reprogramación WorkManager | Uso de dependencia coherente con trabajo periódico. |
| FOREGROUND_SERVICE | Fusionado, línea 32; servicio de infraestructura WorkManager | No se encontró trabajo propio que invoque foreground; no se propone quitarlo sin comprobar contrato de dependencia. |
| BIND_GET_INSTALL_REFERRER_SERVICE | Fusionado, línea 27; integración Firebase Analytics | Procedencia del SDK; necesidad de atribución como producto no documentada/verificada. |
| AD_ID, ACCESS_ADSERVICES_ATTRIBUTION, ACCESS_ADSERVICES_AD_ID | Fusionado, líneas 28–30; Analytics y dependencias | No hay publicidad propia localizada. Revisar intención de recopilación; declarar permiso no demuestra acceso/envío en ejecución. No se etiqueta como filtración confirmada. |
| DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | Fusionado, línea 38; permiso propio generado de firma | Protección para receptores dinámicos; no es permiso peligroso solicitado al usuario. |

### Componentes, archivos y backup

- MainActivity exportada es el launcher y procesa extras como claves de publicaciones; no se localizaron operaciones privilegiadas a partir de esos extras. No se considera un defecto por estar exportada.
- FileProvider está no exportado y limitado a `documents/`, con permiso temporal de lectura; PDF usa un servicio aislado no exportado. Los servicios/receptores exportados de WorkManager y ProfileInstaller inspeccionados están protegidos por BIND_JOB_SERVICE/DUMP. No se infiere una vulnerabilidad de exportación de esos componentes.
- El manifest debug contiene actividades de infraestructura de pruebas Compose y es debuggable: son propiedades de debug, no evidencia de release vulnerable. No se generó ni inspeccionó un manifest release fusionado nuevo.
- Backup habilitado con reglas de plantilla (`app/src/main/AndroidManifest.xml:12`; `app/src/main/res/xml/backup_rules.xml:8`; `app/src/main/res/xml/data_extraction_rules.xml:6`). Room contiene archivo público, guardados y criterios personales. Falta decisión documentada sobre backup de estos últimos; no basta para declarar exposición. No se recomienda cifrar todo el boletín sin requisito.
- WebView: no utilizado. No se localizaron TrustManagers/hostname verifiers permisivos ni TLS personalizado en producción. Se mantiene el hallazgo concreto de validación de destino SEC-003.

### Logging y secretos

Los logs de transporte revisados evitan copiar cuerpos completos de respuesta/preguntas. Se transmiten códigos de estado, tamaños y algunos identificadores de publicaciones mediante Crashlytics (`AiDocumentSessionStore.kt:68`, `AiChatRepositoryImpl.kt:153`); no se afirma anonimización absoluta ni ausencia de toda señal de interés personal. Los mensajes se adjuntan a informes según el SDK, y no se inspeccionó la consola remota. La credencial actual no se publicó en las salidas ni informes; los escaneos mostraron únicamente ubicación y existencia.

### Dependencias y CVEs

Se contrastaron las versiones resueltas del log con las declaraciones del catálogo. Las consultas de advisories de [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization/security/advisories) y [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines/security/advisories) no proporcionaron una vulnerabilidad aplicable verificada. La consulta a la ruta de advisories de Square/OkHttp redirigió a otro namespace y no se usó para certificar seguridad. **CVEs del conjunto completo de dependencias: requiere verificación externa adicional.** No hay CVEs afirmados ni una certificación de ausencia de vulnerabilidades. Las advertencias de actualización de lint no son advisories.

## Rendimiento, Compose y mantenibilidad: descartes y medición pendiente

- No se han medido recomposiciones, estabilidad del compilador, fotogramas lentos, arranque, decodificación de imágenes ni tiempos SQL. No se presenta un defecto por falta de `remember`, `contentType`, perfiles o Paging.
- Las listas principales usan layouts lazy y existen claves en las listas revisadas; no se ha demostrado que la ausencia de un contentType concreto produzca degradación. Los filtros/transformaciones en ViewModels y consultas completas del histórico requieren carga representativa antes de proponer un cambio por rendimiento.
- Search usa LIKE sobre texto normalizado y límite; es una zona a medir cuando se conozca el volumen real. No se declara query lenta solo por el SQL.
- Release deshabilita optimización (`app/build.gradle.kts:59`): puede evaluarse posteriormente como oportunidad, pero no hay medición de APK ni defecto funcional demostrado que justifique contarlo aquí.
- No se considera un defecto el monomódulo, la cantidad de casos de uso o el tamaño de ViewModels. Hay duplicación de transporte de IA, pero no se convierte por sí sola en hallazgo de mantenibilidad.
- El borrador de reglas vive en el ViewModel; restauración completa tras muerte de proceso y navegación con varias entradas del mismo documento no están certificadas. Se conservan como cuestiones de cobertura pendiente, sin atribuir crashes o pérdidas adicionales no probadas.
- Escrituras `statuses.value = statuses.value + ...` e `inProgress.value = ...` merecen revisar intercalados multiclave; no se añade otro hallazgo de carrera sin fijar un recorrido reproducible y distinguirlo de los defectos ya demostrados.
- Una fixture histórica con aspecto de clave se descartó usando su cambio de contexto en `852bbc9`. No se promueve una coincidencia regex a secreto real.

# Áreas correctamente resueltas

- Upsert de publicaciones transaccional y con columnas explícitas que preservan datos personales de guardado; no se purga el histórico al salir del feed. Hay pruebas de preservación y migraciones que pasan en esta sesión.
- Esquemas Room 1–5 versionados y migraciones encadenadas sin fallback destructivo. No se deduce falta total de prueba 4→5 por títulos antiguos de tests.
- DI sustituible de tiempo/dispatchers/azar y pruebas de resolución; separación de SDKs y reglas de arquitectura comprobadas por la suite existente.
- Rutas serializables, estado recogido con lifecycle, restauración selectiva de búsqueda y manejo explícito de insets, con pruebas unitarias/instrumentadas existentes. La presencia de instrumentadas no se presenta como ejecución durante esta fase.
- Cierre protegido del PDF y exclusión de inicialización WorkManager en el proceso aislado. Las limitaciones de caché/cancelación descritas no invalidan esos mecanismos.
- RSS limita cuerpo y concurrencia, conserva resultados parciales y normaliza datos antes de persistir. El parser declara defensas contra entidades; no se realizó una batería adversarial completa en cada API Android.
- Resúmenes con procedencia/versionado y validación de referencias de páginas; generación inicial explícita y aviso previo. SEC-002 identifica la ruta que elude ese aviso.
- FileProvider acotado, PendingIntent inmutable y permiso de notificaciones comprobado al entregar.

## Checkpoint 1

Fase 1 terminada. No se implementó ninguna corrección ni se iniciaron las oportunidades de Fase 2. Esperar autorización antes de continuar.
