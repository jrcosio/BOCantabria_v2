# Data Model: Pantalla de arranque y sistema de diseño institucional

**Feature**: `002-pantalla-arranque` | **Fase**: 1 | **Fecha**: 2026-08-28

## Cadena del arranque

```
RemoteConfigValues ──map──> AppConfig ──┐
     (data/remote)          (domain)    │
                                        ├──> StartupStatus ──> SplashUiState
ConnectivityDataSource ──> isOnline ────┤      (domain)            (ui)
     (data/local)           (domain)    │
                                        │
AppVersionProvider ──────> versionCode ─┘
        (core)
```

---

## Dominio

### `AppConfig` (`domain/model`)

Los parámetros que condicionan el arranque. Portador de datos sin comportamiento.

| Campo | Tipo | Reglas |
|---|---|---|
| `minSupportedVersionCode` | `Int` | ≥ 0. Versión mínima de la aplicación que el servicio admite |
| `maintenanceMessage` | `String?` | Nulo o vacío significa «sin mantenimiento». Se normaliza a nulo si viene vacío, para que nadie tenga que comprobar las dos cosas |

Tiene valores por defecto propios (`minSupportedVersionCode = 0`, `maintenanceMessage = null`), que
equivalen a «todo permitido»: si no hay nada publicado, la aplicación no se bloquea a sí misma.

### `StartupStatus` (`domain/model`)

Conclusión de la preparación. Sellada: el `when` que decide qué pintar es exhaustivo.

| Variante | Significado |
|---|---|
| `Ready` | Se puede continuar al contenido principal |
| `UpdateRequired` | La versión instalada es inferior a la mínima soportada |
| `Maintenance(message)` | El servicio ha publicado un mensaje de mantenimiento |

**Precedencia**, resuelta en el caso de uso y verificable con pruebas:

1. Sin conexión → `AppResult.Failure(DomainError.Network)`. Manda sobre todo lo demás: sin red no
   se puede saber ni la versión mínima ni si hay mantenimiento.
2. Con conexión pero sin poder obtener la configuración → `Failure`.
3. Versión por debajo del mínimo → `UpdateRequired`. Manda sobre el mantenimiento: de nada sirve
   informar de una incidencia temporal a quien no puede usar la aplicación de todos modos.
4. Mensaje de mantenimiento presente → `Maintenance`.
5. En cualquier otro caso → `Ready`.

Reutiliza `AppResult` y `DomainError` de la feature 001: el arranque no necesita un vocabulario de
errores propio.

---

## Capa de datos

### `RemoteConfigValues` (`data/source/remote`)

Lo que el servicio de configuración remota entrega, con sus nombres, no con los del dominio.

| Campo | Tipo | Clave publicada |
|---|---|---|
| `minSupportedVersionCode` | `Long` | `min_supported_version_code` |
| `maintenanceMessage` | `String` | `maintenance_message` |

El servicio devuelve `Long` para los números y cadena vacía para los textos ausentes; la traducción
al dominio ajusta el tipo y convierte la cadena vacía en nulo. Es una traducción real, no una copia.

Valores por defecto empaquetados: `min_supported_version_code = 0`, `maintenance_message = ""`.

---

## Presentación (`ui/splash`)

### `SplashUiState`

Sellada: los cuatro estados son mutuamente excluyentes por construcción (FR-009).

| Variante | Contenido | Acciones que ofrece |
|---|---|---|
| `Loading` | — | Ninguna |
| `Ready` | — | Ninguna: la pantalla navega |
| `Error` | `error: DomainError` | Reintentar · Continuar sin conexión |
| `Blocked` | `reason: BlockReason` | Reintentar únicamente |

`BlockReason` es sellada, con `UpdateRequired` y `Maintenance(message)`.

`Blocked` es un estado propio y no un `Error` con una bandera: un acceso bloqueado **nunca** puede
ofrecer «continuar sin conexión», porque saltárselo anula su propósito. Como tipos distintos, la
combinación incoherente no se puede escribir (research.md, D-007).

**Transiciones**

```
   [inicial] ──prepare()──> Loading ──Ready────────────> Ready ──> navega a Home
                               │
                               ├──UpdateRequired/──────> Blocked ──onRetry()──┐
                               │  Maintenance                                 │
                               │                                              │
                               └──Failure──────────────> Error ──onRetry()────┤
                                                            │                 │
                                                            └─onContinueOffline()──> navega a Home
                                                                                     │
   └─────────────────────────────────────────────────────────────────────────────────┘
```

Reglas:

- El estado inicial es `Loading` y la preparación se dispara sola al construirse el modelo.
- `onRetry()` se ignora si ya hay una preparación en curso (FR-011).
- `onContinueOffline()` solo existe desde `Error`; desde `Blocked` no hay salida (FR-012).
- El estado vive en el modelo de pantalla, así que sobrevive a un cambio de configuración sin
  reiniciar la preparación (FR-008).
- `Ready` se alcanza como pronto a los 1.200 ms desde el inicio, aunque la preparación termine
  antes (FR-005).

---

## Transversal

### `AppVersionProvider` (`core/util`)

| Miembro | Tipo | Notas |
|---|---|---|
| `versionCode` | `Int` | Número de versión de la aplicación instalada |

Interfaz para que la comparación con el mínimo soportado sea comprobable sin dispositivo (D-009).

### Tokens de diseño (`core/ui/theme`)

No son datos de la aplicación, pero sí un vocabulario cerrado que el resto del código consume:

| Contenedor | Contiene | Se accede como |
|---|---|---|
| `ColorScheme` de Material 3 | Los tokens con equivalente en Material 3 | `MaterialTheme.colorScheme.*` |
| `BocExtendedColors` | Los diez tokens sin equivalente, más los cinco de sección | `BocTheme.colors.*` |
| `Typography` de Material 3 | Los 14 estilos del documento | `MaterialTheme.typography.*` |
| `BocSpacing` | Los nueve valores de espaciado | `BocTheme.spacing.*` |
| `BocElevation` | Los cinco niveles de elevación | `BocTheme.elevation.*` |

`BocExtendedColors` es inmutable y tiene una instancia por modo, clara y oscura.
