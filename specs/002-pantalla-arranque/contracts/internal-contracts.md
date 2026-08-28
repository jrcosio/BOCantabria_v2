# Contratos internos

**Feature**: `002-pantalla-arranque` | **Fase**: 1 | **Fecha**: 2026-08-28

La aplicación no expone interfaces externas. Los contratos que importan son los límites entre capas
—los que la regla de dependencias protege— más el contrato visual de la portada, que es lo que
hace verificable el criterio SC-007. Se documentan en pseudo-Kotlin, sin cuerpos.

---

## 1. `domain` → resto del mundo

```kotlin
package com.jrblanco.boccantabria.domain.usecase

class PrepareStartupUseCase(
    connectivity: ConnectivityRepository,
    appConfig: AppConfigRepository,
    appVersion: AppVersionProvider,
) {
    suspend operator fun invoke(): AppResult<StartupStatus>
}
```

**Contrato**:
- **Nunca** lanza. Todo fallo llega como `AppResult.Failure`.
- Aplica la precedencia documentada en `data-model.md`: sin conexión manda sobre todo; versión
  obsoleta manda sobre mantenimiento.
- Es idempotente: invocarla dos veces no produce efectos secundarios observables.
- **No** impone el tiempo mínimo en pantalla: eso es una decisión de presentación y vive en el
  modelo de pantalla.

---

## 2. `data` → `domain`

```kotlin
package com.jrblanco.boccantabria.domain.repository

interface AppConfigRepository {
    /** Nunca lanza: los fallos vienen en AppResult.Failure. */
    suspend fun loadConfig(): AppResult<AppConfig>
}

interface ConnectivityRepository {
    fun isOnline(): Boolean
}
```

```kotlin
package com.jrblanco.boccantabria.data.source.remote

interface RemoteConfigDataSource {
    /** Puede lanzar. El repositorio es quien captura y traduce. */
    suspend fun fetchValues(): RemoteConfigValues
}
```

```kotlin
package com.jrblanco.boccantabria.data.source.local

interface ConnectivityDataSource {
    fun isOnline(): Boolean
}
```

**Política de `AppConfigRepositoryImpl`**, verificable con pruebas:

| Situación | Resultado |
|---|---|
| El servicio responde | Se traduce a `AppConfig` y se devuelve `Success` |
| El servicio lanza | `Failure(DomainError.Network)` |
| El servicio no tiene valores publicados | `Success` con los valores por defecto empaquetados |
| El mensaje de mantenimiento llega vacío | `Success` con `maintenanceMessage = null` |

Todo el trabajo ocurre en el despachador de entrada/salida que provee `DispatcherProvider`.

**Contrato de `ConnectivityRepository`**: síncrono y barato. Responde si el dispositivo tiene una
red **con acceso validado a internet**, no si hay una interfaz activa: un móvil conectado a una red
sin salida debe considerarse sin conexión.

---

## 3. Transversal: `core` → `data`

```kotlin
package com.jrblanco.boccantabria.core.util

interface AppVersionProvider {
    val versionCode: Int
}
```

---

## 4. Presentación

```kotlin
package com.jrblanco.boccantabria.ui.splash

class SplashViewModel(
    prepareStartup: PrepareStartupUseCase,
    analytics: AnalyticsTracker,
    crashReporter: CrashReporter,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    val uiState: StateFlow<SplashUiState>
    fun onRetry()
    fun onContinueOffline()
}
```

**Contrato**:
- `uiState` es de solo lectura y siempre tiene valor; el inicial es `Loading`.
- La preparación se dispara al construirse, **una sola vez**.
- `Ready` no se emite antes de 1.200 ms desde el inicio de la preparación, aunque ésta termine
  antes. La espera mínima y el trabajo real corren **en paralelo**, no en serie.
- La preparación se abandona a los 8.000 ms y se trata como `Error`.
- `onRetry()` no hace nada si ya hay una preparación en curso.
- `onContinueOffline()` solo tiene efecto desde `Error`; desde `Blocked` se ignora.
- El evento de pantalla vista se registra exactamente una vez por instancia; los fallos de
  preparación se reportan al servicio de errores.

```kotlin
@Composable
fun SplashScreen(onStartupComplete: () -> Unit, viewModel: SplashViewModel = koinViewModel())

@Composable
fun SplashContent(                                   // sin estado, testeable aislado
    state: SplashUiState,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit,
)
```

**Contrato**: `SplashContent` no conoce el modelo de pantalla. Recibe estado y emite eventos, de
modo que las pruebas de interfaz recorren los cuatro estados sin arrancar el grafo.

### Etiquetas de prueba

Identificadores estables sobre los que se afirman las pruebas de interfaz. Cambiarlos rompe un
contrato.

| Etiqueta | Elemento |
|---|---|
| `splash_root` | Contenedor de la portada |
| `splash_emblem` | Escudo |
| `splash_loading` | Indicador de progreso |
| `splash_error` | Mensaje de error recuperable |
| `splash_blocked` | Mensaje de acceso bloqueado |
| `splash_retry` | Botón de reintentar |
| `splash_continue_offline` | Botón de continuar sin conexión |

---

## 5. Contrato visual de la portada

Es el contrato que hace verificable el criterio SC-007. Valores del documento de diseño §13.2, y
composición según la imagen de referencia.

| Elemento | Especificación |
|---|---|
| Fondo | `#063B5C` a pantalla completa, extendido tras las barras del sistema, con iconos de sistema claros |
| Escudo | Recurso oficial, 104 dp de alto, proporciones intactas, centrado |
| Separación escudo → siglas | 24 dp |
| `BOC` | `DisplayLarge` (56 sp / 64 sp), blanco |
| `BOLETÍN OFICIAL` / `DE CANTABRIA` | Dos líneas, 20 sp, peso 500, tracking amplio, blanco |
| Línea divisoria | 120 × 2 dp, `#8FD3EE`, centrada |
| Etiqueta de autoría | «Diseñada y desarrollada por», 13 sp, blanco al 70 % |
| Nombre | «José Ramón Blanco Gutiérrez», 15 sp, peso 600, `#8FD3EE` |
| Indicador de progreso | Bajo la autoría, discreto, `#8FD3EE` |
| Anclaje inferior | El bloque autoría + indicador se ancla abajo respetando el área segura |

**El documento sitúa la autoría a 72 dp del borde inferior, pero coloca el indicador de progreso
sin posición definida; la imagen de referencia lo sitúa bajo la autoría.** Se sigue la imagen y se
trata el conjunto autoría + indicador como un bloque anclado abajo, que es la lectura que satisface
ambas fuentes.

---

## 6. Navegación

```kotlin
sealed interface Route {
    @Serializable data object Splash : Route
    @Serializable data object Home : Route
}
```

**Contrato**: `Splash` es el destino inicial. Al completarse el arranque se navega a `Home`
descartando `Splash` de la pila, de modo que el retroceso desde `Home` cierre la aplicación en
lugar de volver a la portada (FR-007). Hay una prueba de interfaz que lo comprueba.

---

## 7. Sistema de diseño

```kotlin
package com.jrblanco.boccantabria.core.ui.theme

@Composable
fun BOCantabriaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)

object BocTheme {
    val colors: BocExtendedColors      @Composable @ReadOnlyComposable get
    val spacing: BocSpacing            @Composable @ReadOnlyComposable get
    val elevation: BocElevation        @Composable @ReadOnlyComposable get
}
```

**Contrato**:
- `BOCantabriaTheme` **no** acepta ningún parámetro de color dinámico (research.md, D-002).
- Los tokens con equivalente en Material 3 se consumen por `MaterialTheme`; los propios, por
  `BocTheme`. Ningún componente escribe un color, un tamaño o un espaciado literal.
- `BocTheme` fuera de `BOCantabriaTheme` falla de forma explícita en lugar de devolver valores por
  defecto silenciosos: un componente sin tema es un error de programación, no un caso a tolerar.
