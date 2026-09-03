# Modelo de datos: Pantalla «Acerca de»

No se añaden entidades persistentes ni modelos de dominio.

## Estado de presentación

```text
InfoUiState
├── versionName: String
└── linkOpenFailed: Boolean
```

`versionName` nace de la versión instalada y es estable durante el proceso. `linkOpenFailed` es un
aviso transitorio: la pantalla lo consume después de mostrarlo para no repetirlo al recomponer.

## Enlace de información

```text
InfoLink
├── LinkedIn → https://www.linkedin.com/in/jr-blanco/
└── GitHub   → https://github.com/jrcosio/BOCantabria_v2.git
```

Es un tipo exclusivo de presentación: proporciona identidad estable para telemetría y evita que la
URL se replique entre el dibujo, la navegación externa y las pruebas.
