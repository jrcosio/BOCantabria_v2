# Quickstart: Resumen IA

**Feature**: `007-resumen-ia` | **Fase**: 1 | **Fecha**: 2 de septiembre de 2026

Cómo se comprueba que esta feature está terminada. Primero lo que la máquina afirma sola, después lo
que hay que mirar con el móvil en la mano.

---

## 0. Requisitos previos

**Java no está en el `PATH`.** Usa el JBR que trae Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

**La credencial del servicio.** En `local.properties`, que está en `.gitignore`:

```properties
GROQ_API_KEY=<la clave>
```

Comprueba **las dos cosas**, porque las dos tienen que funcionar:

- **Con clave**: la generación funciona de verdad. Es lo que hace falta para la comprobación manual.
- **Sin clave**: borra o comenta la línea, `./gradlew clean` y compila. **La build tiene que seguir en
  verde** y la pestaña anunciar que el servicio no está configurado. Es lo que mantiene viva la
  integración continua sin secretos (FR-042, D-017).

**Un solo dispositivo conectado.** Con más de uno, Gradle reparte la tanda instrumentada entre todos,
y un móvil con la pantalla bloqueada la tumba entera con `No compose hierarchies found in the app`.
O lo desconectas, o lo dejas desbloqueado, o fijas el destino:

```bash
export ANDROID_SERIAL=emulator-5554
```

**Navegación de tres botones**, que otras pruebas del proyecto necesitan para morder:

```bash
adb shell settings put secure navigation_mode 0
```

---

## 1. Las cuatro puertas, en este orden

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Informe de pruebas: `app/build/reports/tests/testDebugUnitTest/index.html`.
Informe de lint: `app/build/reports/lint-results-debug.html`.

Ninguna se salta. Ningún `@Ignore`, ningún test comentado, ninguna prueba borrada para pasar.

Comprobación aparte, que ninguna puerta hace y aquí importa mucho:

```bash
# La credencial no puede estar en el repositorio, ni ahora ni en el historial.
git log -p --all -S 'gsk_' | head        # debe salir vacío
grep -rn 'GROQ_API_KEY' --include='*.kt' --include='*.xml' app/src/   # solo BuildConfig, nunca un valor
```

---

## 2. Lo que las pruebas automáticas ya afirman

| Requisito | Prueba |
|---|---|
| FR-002, SC-004 — nada se genera solo | `PublicationDetailViewModelTest`, `AiSummaryRepositoryImplTest` |
| FR-005 — dos pulsaciones, una consulta | `AiSummaryRepositoryImplTest` |
| FR-006 — abandonar no es un error | `AiSummaryRepositoryImplTest` |
| FR-011 — la limpieza no destruye | `PdfTextNormalizerTest` |
| FR-012, SC-005 — sin texto, sin consulta | `AiSummaryRepositoryImplTest`, `AndroidxPdfTextExtractorTest` |
| FR-015 — las secciones vacías se ocultan | `AiSummaryTabTest` |
| FR-018 — el documento no da órdenes | `SummaryPromptFactoryTest` |
| FR-020, FR-022 — referencias válidas | `SummaryValidatorTest`, `AiSummaryTest` |
| FR-021 — el chip abre la página | `AiSummaryPageHandoffTest` |
| FR-023, FR-024, SC-006 — la advertencia, en tres canales | `AiSummaryTabTest` |
| FR-025 — la advertencia viaja con el texto | `PublicationDetailViewModelTest` |
| FR-027, FR-028 — presupuesto y aviso previo | `SummaryBudgetTest`, `AiSummaryTabTest` |
| FR-029, FR-030, SC-012 — cobertura honesta | `SummaryValidatorTest`, `AiSummaryTest` |
| FR-031 — la primera página que no cabe | `SummaryBudgetTest` |
| FR-032, FR-033, SC-002 — guardado y sin red | `AiSummaryDaoTest`, `AiSummaryFlowIntegrationTest` |
| FR-035 — obsoleto, no borrado | `AiSummaryRepositoryImplTest` |
| FR-036 — una respuesta vacía no se guarda | `SummaryValidatorTest` |
| FR-037 a FR-039 — cuota | `GroqRateLimitCoordinatorTest`, `OkHttpGroqSummaryDataSourceTest` |
| FR-041 — reintentar solo si procede | `AiSummaryErrorTest`, `AiSummaryTabTest` |
| FR-042 — sin credencial, sin drama | `GroqApiKeyProviderTest` |
| FR-043 a FR-045 — el aviso, una vez | `AiPreferencesTest`, `PublicationDetailViewModelTest` |
| Migración 1→4 y 3→4 | `BocDatabaseMigrationTest` |
| El grafo resuelve | `KoinModulesTest` |
| Las capas siguen separadas | `ArchitectureRulesTest`, sin tocar |

---

## 3. Lo que hay que comprobar a mano

Ninguna de estas la cubre una prueba automática, y todas pueden fallar en silencio.

1. **Una publicación corta.** Abre el detalle, ve a **Resumen IA**. Aparece la explicación y el botón,
   y **no ha pasado nada más**.
2. **El aviso de la primera vez.** Pulsa **Generar resumen** en una instalación limpia. Sale la hoja.
   **Cancela**: no se envía nada y vuelves al estado inicial. Vuelve a pulsar, **continúa**: se genera.
   Genera un segundo resumen en otra publicación: la hoja **no** vuelve a salir.
3. **La ficha.** Comprueba que la tarjeta encabeza, que la advertencia está debajo sin bloque rojo, y
   que no aparece ninguna sección vacía. Contrasta un par de datos con el PDF: que los importes y los
   plazos sean los que pone el documento, y que un plazo relativo siga siendo relativo.
4. **Los chips.** Pulsa «Página 3». El visor abre **en la página 3**, no en la primera.
5. **Volver.** Sal del detalle, vuelve a entrar. El resumen sale **al instante**. Pon el móvil en modo
   avión y repite: sigue saliendo.
6. **Compartir.** Comparte el resumen a una nota o a un chat contigo. El texto empieza diciendo que lo
   ha generado una IA y que hay que consultar el documento oficial. Haz lo mismo con **Copiar**.
7. **Un documento largo.** Busca una publicación de más de diez páginas —los presupuestos y los
   listados de personal suelen serlo—. **Antes** de pulsar, el botón ya avisa de cuántas páginas se
   analizarán. Después, el resultado dice qué páginas cubre.
8. **La cuota.** Genera tres o cuatro resúmenes seguidos. Al chocar con el límite del minuto debe
   verse una cuenta atrás y el proceso **continuar solo**, no un error.
9. **Sin conexión.** Modo avión, publicación sin resumen previo, pulsa generar: mensaje claro y
   posibilidad de reintentar. Quita el modo avión y reintenta: funciona.
10. **Sin credencial.** Quita `GROQ_API_KEY` de `local.properties`, `./gradlew clean :app:installDebug`.
    Mensaje de no configurado, **sin traza y sin código HTTP**, y sin botón de reintentar.
11. **Lo que se registra.** Con `adb logcat` abierto durante una generación completa, busca la
    credencial y busca un párrafo del documento. **No puede aparecer ninguno de los dos** (FR-047,
    SC-009).
12. **Rotar y volver de la muerte del proceso.** Con un resumen a la vista, rota el móvil: sigue ahí.
    Activa «No conservar actividades» en opciones de desarrollador, sal y vuelve: sigue ahí y la
    pestaña seleccionada se conserva.
13. **Lector de pantalla.** Con TalkBack, recorre el resumen: la advertencia **se anuncia** y los chips
    dicen que se pueden abrir.
14. **Un PDF sin texto.** Si encuentras uno escaneado en el boletín, compruébalo. Si no aparece
    ninguno, vale con lo que afirma `AndroidxPdfTextExtractorTest`, pero anótalo como no verificado en
    campo — es el caso que más depende del dispositivo real (D-001).

---

## 3 bis. Lo que ya se ha verificado contra el servicio real

Hecho una vez durante la implementación, con una llamada mínima y el esquema tomado del propio
fichero de producción. **No hace falta repetirlo salvo que cambie el esquema o el modelo**:

- El modelo `qwen/qwen3.8-27b` **acepta el esquema estricto** tal y como está escrito: HTTP 200.
- La respuesta trae los doce campos que el DTO espera, y `system_fingerprint`.
- El modelo **conserva literalmente** un plazo relativo: devolvió «Quince días hábiles desde la
  publicación» sin convertirlo en fecha (FR-016).
- Las cabeceras de cuota son las documentadas en D-015: `limit-requests` es por día, `limit-tokens`
  por minuto, y las duraciones llegan como `1m26.4s` y `10.514s`.
- **Y devolvió una cobertura imposible** —completa sobre cero páginas— que el validador corrige. Hay
  prueba de regresión con esa respuesta literal.

## 4. Si algo falla

- **`No compose hierarchies found in the app`** en bloque: hay más de un dispositivo conectado y uno
  tiene la pantalla bloqueada. No es del código. Fija `ANDROID_SERIAL`.
- **Una espera instrumentada se agota sin decir por qué**: envuélvela y afirma cuál de los motivos
  fue —pantalla no montada, estado no alcanzado, consulta sin resultado—. Subir el tiempo de espera
  **no** lo arregla; ya se intentó en la feature 006 con 45 segundos.
- **`assertIsDisplayed()` se cuelga en el estado de carga**: el esqueleto se está animando en bucle y
  la composición no llega a reposo. Conduce el reloj a mano (`mainClock.autoAdvance = false`).
- **`Could not initialize class io.mockk.impl.JvmMockKGateway`** en clases que no tienen nada que ver:
  alguna prueba está sembrando volumen bajo Robolectric. El volumen se comprueba a mano, no en la
  suite.
- **429 constantes durante la comprobación manual**: es el plan gratuito haciendo su trabajo. Espera
  al minuto siguiente. Si ocurre sin haber generado nada, revisa que no haya quedado una consulta en
  bucle.
- **La extracción devuelve vacío en un PDF que sí tiene texto**: es el riesgo declarado en D-001.
  `PdfTextExtractor` es una interfaz precisamente para esto: se cambia la implementación por PdfBox
  sin tocar el resto. Antes de hacerlo, comprueba en más de un dispositivo y en más de una versión de
  Android.
