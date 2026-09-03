# Contratos internos: Pantalla «Acerca de»

## Navegación

```kotlin
Route.Info
MainShell(onOpenInfo: () -> Unit)
InfoScreen(onBack: () -> Unit)
```

Información abre `Route.Info` en el controlador exterior. Atrás hace `popBackStack()` y no altera el
controlador interior ni el estado de Inicio.

## Presentación

```kotlin
data class InfoUiState(
    val versionName: String = "",
    val linkOpenFailed: Boolean = false,
)

enum class InfoLink(val destination: String, val url: String)
```

`InfoContent` recibe estado y callbacks; no resuelve dependencias ni abre actividades.

## Versión instalada

```kotlin
interface AppVersionProvider {
    val versionCode: Int
    val versionName: String
}
```

El código sigue sirviendo al arranque; el nombre sirve a «Acerca de». Los dos proceden del mismo
BuildConfig y son sustituibles en pruebas.

## Semántica estable

- `info_screen`: raíz.
- `info_back`: Atrás.
- `info_portrait`: contenedor visible de la fotografía.
- `info_link_linkedin`: acción LinkedIn.
- `info_link_github`: acción GitHub.
- `info_version`: texto de versión.
- `info_link_error`: Snackbar de apertura fallida.
