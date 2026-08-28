# Contratos internos

**Feature**: `001-esqueleto-arquitectura` | **Fase**: 1 | **Fecha**: 2026-08-28

La aplicación no expone ninguna interfaz externa (no hay API pública, ni CLI, ni servicio). Los
contratos relevantes son los **límites entre capas**: son los que la regla de dependencias
protege y los que las features futuras van a consumir. Se documentan aquí en pseudo-Kotlin,
sin cuerpos.

---

## 1. `domain` → resto del mundo

Todo lo que `ui` puede consumir. Kotlin puro: sin Android, sin proveedores externos.

```kotlin
package com.jrblanco.boccantabria.domain.repository

interface ContentRepository {
    /** Obtiene los elementos de contenido. Nunca lanza: los fallos vienen en AppResult.Failure. */
    suspend fun getContentItems(): AppResult<List<ContentItem>>
}
```

```kotlin
package com.jrblanco.boccantabria.domain.usecase

class GetContentItemsUseCase(private val repository: ContentRepository) {
    suspend operator fun invoke(): AppResult<List<ContentItem>>
}
```

**Contrato**:
- `getContentItems()` **nunca** propaga excepciones. Cualquier fallo se traduce a `Failure`.
- Una lista vacía es `Success(emptyList())`, no `Failure`.
- Es idempotente: llamarla dos veces no produce efectos secundarios observables.
- El caso de uso no añade lógica: existe para que `ui` no conozca los repositorios y para dar
  un lugar evidente donde ubicarla cuando aparezca.

---

## 2. `data` → `domain`

Contratos que `data` está obligada a cumplir.

```kotlin
package com.jrblanco.boccantabria.data.source.remote

interface ContentRemoteDataSource {
    /** Puede lanzar. El repositorio es quien captura y traduce. */
    suspend fun fetchContentItems(): List<ContentItemDto>
}
```

```kotlin
package com.jrblanco.boccantabria.data.source.local

interface ContentLocalDataSource {
    suspend fun readContentItems(): List<ContentItemEntity>
    suspend fun writeContentItems(items: List<ContentItemEntity>)
}
```

**Política del repositorio** (`ContentRepositoryImpl`), verificable con pruebas:

| Situación | Resultado |
|---|---|
| El origen remoto responde | Se traduce a dominio, se guarda en local y se devuelve `Success` |
| El origen remoto falla y local tiene datos | Se devuelve `Success` con lo local (respaldo) |
| El origen remoto falla y local está vacío | Se devuelve `Failure(DomainError.Network)` |
| El origen remoto responde con lista vacía | Se devuelve `Success(emptyList())` y se limpia lo local |

Todo el trabajo se ejecuta en el despachador de entrada/salida que provee `DispatcherProvider`.

---

## 3. Telemetría: `core` → `data`

```kotlin
package com.jrblanco.boccantabria.core.telemetry

interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
    fun trackScreenView(screenName: String)
}

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable)
    fun log(message: String)
}
```

**Contrato**:
- Ambas son operaciones «dispara y olvida»: **nunca** lanzan ni bloquean a quien las llama. Un
  fallo de telemetría jamás puede tumbar una pantalla.
- Las implementaciones sobre Firebase viven exclusivamente en `data/telemetry`.
- Las claves de parámetro consideradas sensibles se descartan antes de enviar (FR-016).
- En pruebas se sustituyen por dobles; ninguna prueba contacta con el servicio real.

---

## 4. Cableado de dependencias

```kotlin
package com.jrblanco.boccantabria.core.di

val appModules: List<Module>   // = coreModule + dataModule + domainModule + uiModule
```

**Contrato**:
- `appModules` es el **único** punto de entrada del grafo. La clase `Application` no conoce los
  módulos individuales.
- Toda dependencia declarada debe resolverse. Lo verifica una prueba automática (FR-011).
- Añadir una clase inyectable obliga a registrarla en su módulo; si no, la prueba falla.

---

## 5. Presentación

```kotlin
package com.jrblanco.boccantabria.ui.home

class HomeViewModel(
    getContentItems: GetContentItemsUseCase,
    analytics: AnalyticsTracker,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState>
    fun onRetry()
}
```

**Contrato**:
- `uiState` es de solo lectura y siempre tiene valor; su valor inicial es `Loading`.
- La carga inicial se dispara al construirse, una sola vez.
- `onRetry()` no hace nada si ya hay una carga en curso.
- El evento de pantalla vista se registra exactamente una vez por instancia.

```kotlin
package com.jrblanco.boccantabria.ui.home

@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel())

@Composable
fun HomeContent(state: HomeUiState, onRetry: () -> Unit)   // sin estado, testeable aislado
```

**Contrato**: `HomeContent` no conoce el modelo de pantalla. Recibe estado y emite eventos, de
modo que las pruebas de interfaz pueden recorrer los cuatro estados sin arrancar el grafo.

### Etiquetas de prueba

Identificadores estables sobre los que se afirman las pruebas de interfaz. Cambiarlos es
romper un contrato:

| Etiqueta | Elemento |
|---|---|
| `home_loading` | Indicador de carga |
| `home_content` | Lista de elementos |
| `home_empty` | Mensaje de «sin contenido» |
| `home_error` | Mensaje de error |
| `home_retry` | Botón de reintentar |
