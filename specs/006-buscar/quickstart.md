# Quickstart: Buscar

**Feature**: `006-buscar` | **Fase**: 1 | **Fecha**: 2026-08-31

Cómo comprobar que la feature funciona, de extremo a extremo. Primero lo que corre solo; después lo
que hay que ver con los ojos, que aquí es más de lo habitual porque **la migración y el relleno solo
se manifiestan en un dispositivo que ya tenía la aplicación instalada**.

---

## 0. Requisitos previos

Java no está en el `PATH`; usa el JBR de Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Para la tanda instrumentada, un emulador o un dispositivo con **navegación de tres botones**: hay
comprobaciones de márgenes que con gestos no muerden.

```bash
adb shell settings put secure navigation_mode 0
```

Y **un solo dispositivo conectado**. Con dos, Gradle reparte la tanda entre ambos, y el que tenga la
pantalla bloqueada falla en bloque con `No compose hierarchies found in the app`. Se fija el destino
con `export ANDROID_SERIAL=emulator-5554`.

---

## 1. Las cuatro puertas, en orden

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Informes: `app/build/reports/tests/testDebugUnitTest/index.html` y
`app/build/reports/lint-results-debug.html`.

Comprobaciones sueltas mientras se trabaja:

```bash
./gradlew :app:testDebugUnitTest --tests "*SearchTextTest*"
./gradlew :app:testDebugUnitTest --tests "*PublicationSearchDaoTest*"
./gradlew :app:testDebugUnitTest --tests "*BocDatabaseMigrationTest*"
./gradlew :app:testDebugUnitTest --tests "*SearchViewModelTest*"
./gradlew :app:connectedDebugAndroidTest --tests "*SearchHandoffTest*"
```

---

## 2. Lo que las pruebas automáticas ya afirman

| Requisito | Prueba |
|---|---|
| FR-001…FR-005 · normalización | `SearchTextTest` |
| FR-010, FR-011, FR-013 · filtrado en memoria | `FilterPublicationsUseCaseTest`, `HomeViewModelTest` |
| FR-024…FR-027 · búsqueda global | `PublicationSearchDaoTest`, `SearchRepositoryImplTest`, `SearchFlowIntegrationTest` |
| FR-027 · el archivo anterior también se encuentra | `BocDatabaseMigrationTest` + la prueba del relleno en `PublicationRepositoryImplTest` |
| FR-032 · tope de 300 | `SearchPublicationsUseCaseTest` |
| SC-008 · rapidez con el archivo lleno | **a mano**, paso 31 (ver el motivo allí) |
| FR-034…FR-042 · filtros y orden | `PublicationSearchDaoTest`, `SearchQueryTest`, `SearchFiltersSheetTest` |
| FR-043…FR-045 · conservación del estado | `SearchViewModelTest` |
| FR-046 · sin el texto en telemetría | `SearchViewModelTest` |
| SC-010 · `100%` no devuelve el archivo | `PublicationSearchDaoTest` |
| El puente | `SearchHandoffTest` |

---

## 3. Lo que hay que comprobar a mano

### 3.1. La actualización sobre una instalación anterior — **lo más importante**

Es el único camino que ninguna prueba recorre sobre datos reales, y el que tiene peores
consecuencias si falla. **Hazlo antes que nada.**

> **Ejecutado el 1 de septiembre de 2026 sobre un dispositivo real, y en verde.** El móvil tenía una
> build anterior con la base de datos en la **versión 1** —de la época de la feature 003/004— y
> **1773 publicaciones desde 2018**. Al instalar encima, sin desinstalar:
>
> - La base saltó de la **versión 1 a la 3 de una vez**, ejercitando las dos migraciones
>   automáticas encadenadas. Es el camino que `BocDatabaseMigrationTest` cubre sintéticamente y que
>   aquí se confirmó sobre datos reales.
> - **Las 1773 publicaciones siguen ahí**, exactamente el mismo recuento antes y después.
> - Ningún cierre inesperado, y el registro sin excepciones.
> - El relleno terminó: **cero filas sin texto buscable**.
> - Y lo que demuestra que sirvió de algo: buscar `muprespa` devuelve tres anuncios de **2018 y
>   2021**, descargados por la versión anterior y que no tenían texto buscable hasta la migración.
>
> **Trampa al comprobarlo**: leer solo `databases/boc.db` con `adb exec-out cat` da recuentos
> **falsos**, porque las escrituras recientes viven en el `-wal`. Durante esta comprobación parecía
> haber seis filas sin rellenar, y no había ninguna. Hay que llevarse los tres ficheros —`boc.db`,
> `boc.db-wal` y `boc.db-shm`— o el diagnóstico miente.

1. Instala la versión **anterior** de la aplicación (rama `main`, antes de esta feature):
   `git stash && git checkout main && ./gradlew :app:installDebug`
2. Ábrela y deja que sincronice hasta ver el boletín. Anota **cuántos anuncios** dice la cabecera y
   el título de uno cualquiera.
3. Vuelve a esta rama e instala **encima, sin desinstalar**:
   `git checkout 006-buscar && ./gradlew :app:installDebug`
4. Abre la aplicación.
   - ✅ El boletín sigue ahí, con los mismos anuncios. **Si aparece vacío, la migración ha borrado
     datos y hay que parar.**
   - ✅ No hay ningún cierre inesperado al abrir.
5. Deja que termine una sincronización —tira hacia abajo para forzarla— y ve a **Buscar**.
6. Escribe una palabra del título que anotaste en el paso 2.
   - ✅ Aparece. **Esto es el relleno funcionando**: esa publicación se descargó con la versión
     anterior y no tenía texto buscable.

### 3.2. Búsqueda rápida en Inicio

7. En Inicio, toca la **lupa**.
   - ✅ La barra superior se transforma en el campo con `Buscar en esta edición…`.
   - ✅ El teclado sube solo.
8. Escribe `pielagos`, sin tilde.
   - ✅ Quedan las publicaciones de Piélagos. `Piélagos`, con tilde, encuentra lo mismo.
   - ✅ Junto a la lista se ve el número de coincidencias.
   - ✅ La cabecera azul **no** cambia: sigue diciendo los anuncios de la edición.
9. Borra el texto.
   - ✅ Vuelve la lista completa, sin parpadeos ni recarga.
10. Cierra el buscador.
    - ✅ Vuelve la barra normal, con el escudo y el título.
    - ✅ La posición de lectura es la que era.
11. Gira el dispositivo con texto escrito.
    - ✅ El texto y los resultados siguen ahí.

### 3.3. La búsqueda respeta el contexto

12. Abre el panel lateral, elige la sección **Contratación** y vuelve a la lista.
13. Toca la lupa y escribe algo que sepas que existe en **otra** sección.
    - ✅ No aparece nada de fuera de Contratación. Esto es FR-011.
14. Con el buscador abierto, toca el chip **Todo**.
    - ✅ El buscador se cierra y se ve el boletín entero.

### 3.4. El puente

15. En Inicio, busca algo que **no** esté en la edición actual pero sí en el archivo.
    - ✅ El mensaje dice que no hay resultados **en la edición actual**, no «no hay resultados».
    - ✅ Hay una acción para buscar en todo el BOC.
16. Tócala.
    - ✅ Aterrizas en Buscar con el término ya escrito y los resultados ya en pantalla. **Cero
      pulsaciones de teclado.**
17. **El caso que casi se escapa**: vuelve a Inicio, busca **otro** término distinto que tampoco
    esté, y usa el puente otra vez.
    - ✅ Llega el término **nuevo**, no el anterior. Si aparece el anterior, es que la navegación
      está restaurando estado y hay que quitar `restoreState`.

### 3.5. Buscador global

18. Ve a Buscar desde la barra inferior.
    - ✅ La barra superior dice `Buscar` —**no** `Buscar publicaciones`, que es lo que dice el
      campo de debajo—, **sin flecha atrás ni tres puntos**.
    - ✅ Antes de escribir, el estado inicial; no una lista de todo.
19. Escribe una sola letra.
    - ✅ No pasa nada todavía. Al segundo carácter empiezan los resultados.
20. Escribe `contratacion`, sin tilde.
    - ✅ Salen publicaciones de la sección Contratación **aunque la palabra no esté en su título**.
      Esto es el nombre de la sección indexado.
21. Escribe `100%`.
    - ✅ Solo lo que contiene `100%`. Si sale el archivo entero, falta el escapado.
22. Toca un resultado.
    - ✅ Se abre el detalle, exactamente como desde Inicio.
23. Vuelve.
    - ✅ La consulta, los filtros, el orden **y la posición de desplazamiento** están como los
      dejaste.
24. Marca un resultado como guardado y ve a **Guardados**.
    - ✅ Ahí está. Vuelve a Buscar: el marcador sigue relleno.

### 3.6. Filtros y orden

25. Abre los filtros.
    - ✅ Hoja inferior `Filtrar resultados` con fecha desde, fecha hasta, sección, subsección y
      organismo. **No hay municipio**, y es lo esperado.
26. Elige una sección y mira las subsecciones.
    - ✅ Solo ofrece las de esa sección.
27. Pon «desde» posterior a «hasta».
    - ✅ `Aplicar filtros` queda inhabilitado. No hay forma de aplicar la combinación imposible.
28. Aplica un par de filtros.
    - ✅ Aparecen como chips sobre los resultados, cada uno con su aspa.
29. Quita un chip. Después usa `Limpiar todo`.
    - ✅ Los resultados se recalculan y **el texto escrito sigue donde estaba**. Esto es FR-040 y es
      lo que más fácil se rompe.
30. Cambia el orden a `Más antiguas` y vuelve a `Más recientes`.
    - ✅ La lista se invierte y vuelve.
31. Busca algo con miles de coincidencias, por ejemplo `de`.
    - ✅ Se ve el aviso de que hay más de los que caben, con la sugerencia de acotar.
    - ✅ **Y responde de inmediato.** Esto es SC-008, y se comprueba aquí y no con una prueba
      automática por un motivo concreto: sembrar decenas de miles de filas dentro de un test de
      Robolectric deja al JVM de pruebas sin poder responder a la adjunción del agente de ByteBuddy, y
      entonces **todas** las clases que usan MockK caen con
      `Could not initialize class io.mockk.impl.JvmMockKGateway`. Se diagnosticó en esta feature con
      seiscientas filas. Una prueba de volumen aquí costaría la suite entera.

### 3.7. Sin conexión

32. Pon el dispositivo en **modo avión**.
33. Busca en Inicio y en Buscar.
    - ✅ Las dos funcionan igual. Ni un aviso de red, ni una espera.

### 3.8. Lo que hay que ver que **no** pasa

34. En Buscar, sin escribir nada, mira la barra inferior.
    - ✅ Buscar ya no dice «Próximamente» en ningún sitio.
35. Recorre Inicio, Buscar y Guardados con la barra inferior.
    - ✅ Ninguna pantalla pierde su estado al volver.
36. Comprueba que ninguna publicación ha desaparecido tras todo lo anterior.
    - ✅ El recuento de la cabecera no ha bajado. **Aquí no se borra nada, nunca.**

---

## 4. Si algo falla

| Síntoma | Dónde mirar |
|---|---|
| La aplicación se cierra al abrir tras actualizar | La migración 2→3. Mira el hash de identidad y `app/schemas/3.json` |
| El boletín aparece vacío tras actualizar | Alguien ha metido `fallbackToDestructiveMigration()`. Quítalo |
| Lo antiguo no se encuentra, lo nuevo sí | El relleno no corre o no termina. Mira `refresh()` en `PublicationRepositoryImpl` |
| `pielagos` no encuentra `Piélagos` | La consulta y la columna se normalizan distinto. Las dos tienen que pasar por `SearchText.normalise` |
| `100%` devuelve todo | Falta el escapado o el `ESCAPE '\'` en el SQL |
| El puente trae el término anterior | La navegación está restaurando estado. Quita `restoreState` de ese salto |
| `SavedPublicationDaoTest` en rojo | `saved_at` se ha colado en `updateColumns`. Quítalo; **no ajustes la prueba** |
| Una prueba instrumentada se cuelga en `assertIsDisplayed` | Hay una animación infinita en pantalla. Conduce el reloj a mano con `mainClock.autoAdvance = false` |
