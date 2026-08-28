# Data Model: Esqueleto de arquitectura de la aplicación

**Feature**: `001-esqueleto-arquitectura` | **Fase**: 1 | **Fecha**: 2026-08-28

Este esqueleto no persiste nada en disco ni habla con ninguna red real (ver `research.md`,
D-001). Los tipos de abajo existen para materializar la regla de dependencias y la traducción
entre representaciones, que es lo que heredarán las features reales.

## Regla de traducción entre capas

```
ContentItemDto  ──map──>  ContentItem  <──map──  ContentItemEntity
 (data/remote)             (domain)              (data/local)
                              │
                              ▼
                       HomeUiState.Content
                            (ui)
```

`ContentItemDto` y `ContentItemEntity` **nunca** cruzan hacia `ui`. Es el requisito FR-009 y
lo verifica una regla de arquitectura automatizada (`research.md`, D-006).

---

## Dominio (`domain/model`)

### `ContentItem`

Unidad mínima de información que muestra la pantalla inicial. Marcador de posición deliberado:
se sustituirá por la entidad real cuando se especifique la primera feature de negocio.

| Campo   | Tipo     | Reglas |
|---------|----------|--------|
| `id`    | `String` | No vacío. Estable entre cargas: identifica al elemento, no a su posición |
| `title` | `String` | No vacío. Texto visible para la persona usuaria |

Inmutable. Sin comportamiento. Sin dependencias de plataforma.

### `DomainError`

Clasificación cerrada de los fallos que la capa de presentación debe saber distinguir. Es
sellada para que el `when` que elige el mensaje sea exhaustivo y el compilador avise si se
añade un caso nuevo.

| Variante      | Significado |
|---------------|-------------|
| `Network`     | No se pudo obtener el contenido del origen remoto y no había respaldo local utilizable |
| `Unknown`     | Fallo no clasificado. Cualquier excepción inesperada se traduce a esta variante en `data` |

Las excepciones **no** salen de `data`: se capturan allí y se traducen a `DomainError`.

### `AppResult<out T>`

Resultado de toda operación de dominio. Sellado, con dos variantes:

| Variante            | Contenido |
|---------------------|-----------|
| `Success<T>`        | `data: T` |
| `Failure`           | `error: DomainError` |

Ausencia de contenido **no** es un fallo: una lista vacía es `Success(emptyList())`. La
distinción entre «vacío» y «error» que pide la especificación se resuelve en la capa de
presentación, no aquí.

---

## Capa de datos

### `ContentItemDto` (`data/source/remote`)

Representación tal y como la entrega el origen remoto. Nombres deliberadamente distintos de los
del dominio para que la traducción sea real y no una copia de campos.

| Campo   | Tipo     | Notas |
|---------|----------|-------|
| `id`    | `String` | |
| `label` | `String` | Se traduce a `ContentItem.title` |

### `ContentItemEntity` (`data/source/local`)

Representación almacenada por la fuente local.

| Campo   | Tipo     |
|---------|----------|
| `id`    | `String` |
| `title` | `String` |

---

## Presentación (`ui/home`)

### `HomeUiState`

Sellada: los cuatro estados son mutuamente excluyentes por construcción (FR-002).

| Variante  | Contenido | Cuándo |
|-----------|-----------|--------|
| `Loading` | — | Hay una carga en curso |
| `Content` | `items: List<ContentItem>` (no vacía) | La carga terminó con al menos un elemento |
| `Empty`   | — | La carga terminó correctamente sin ningún elemento |
| `Error`   | `error: DomainError` | La carga falló |

**Transiciones**

```
        ┌──────────────── onRetry() ────────────────┐
        │                                           │
   [inicial] ──load()──> Loading ──éxito, n>0──> Content
                            │
                            ├──éxito, n==0──> Empty
                            │
                            └──fallo────────> Error ──┘
```

Reglas:
- El estado inicial al construirse el modelo de pantalla es `Loading`; la carga se dispara sola.
- `onRetry()` solo tiene efecto desde `Error` o `Empty`; desde `Loading` se ignora, de modo que
  pulsar repetidamente no lanza cargas simultáneas (caso límite de la especificación).
- El estado vive en el modelo de pantalla, por lo que sobrevive a los cambios de configuración
  sin recargar (FR-005).

---

## Telemetría (`core/telemetry`)

### `AnalyticsEvent`

| Campo        | Tipo                  | Reglas |
|--------------|-----------------------|--------|
| `name`       | `String`              | Sólo minúsculas, dígitos y guiones bajos. Máximo 40 caracteres |
| `parameters` | `Map<String, String>` | Vacío por defecto. **Prohibido** incluir información personal identificable (FR-016) |

La restricción de datos personales no es solo documental: la implementación descarta las
claves marcadas como sensibles y existe una prueba que lo verifica.
