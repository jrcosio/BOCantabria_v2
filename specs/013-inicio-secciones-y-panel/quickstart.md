# Quickstart — Feature 013: Inicio y panel lateral

Cómo se comprueba que esta feature hace lo que dice. Dos partes: las cuatro puertas automáticas y el
recorrido manual, que aquí es donde de verdad se juzga, porque lo que se ha corregido es comprensión.

---

## 0. Preparación

Java no está en el `PATH`; se usa el JBR de Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Antes de la tanda instrumentada, y **solo** para ella:

```bash
adb shell settings put secure navigation_mode 0   # tres botones; con gestos algunas medidas dan cero
```

Y **un solo dispositivo conectado**. Con un móvil enchufado además del emulador, Gradle reparte la
tanda entre los dos y, si el móvil tiene la pantalla bloqueada, todo cae con
`No compose hierarchies found in the app`. O se desconecta, o se deja desbloqueado, o se fija el
destino:

```bash
export ANDROID_SERIAL=emulator-5554
```

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

**La tanda instrumentada tarda unas dos o tres horas, no trece minutos.** Está medido y anotado en
`CLAUDE.md`: el coste es un suelo fijo de ~46 s por prueba que monta Compose, independientemente de lo
que la prueba haga. Lánzala en segundo plano; quien espere trece minutos dará por colgado algo que va
bien.

Para iterar sobre una sola clase —`--tests` **no existe** en `connectedDebugAndroidTest`, falla con
`Unknown command-line option`—:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jrblanco.boccantabria.ui.home.SectionFilterChipsTest
```

Y para un solo método, añadiendo `#nombreDelMetodo` al final.

---

## 2. Lo que las pruebas automáticas cubren

| Qué | Dónde | Tipo |
|---|---|---|
| Las subsecciones que corresponden a cada selección | `HomeViewModelTest` | unitaria |
| La sección sin hijas no ofrece segunda fila | `HomeViewModelTest` | unitaria |
| `isWholeSectionSelected` con y sin subsección elegida | `HomeViewModelTest` | unitaria |
| La sección padre sigue marcada con una subsección elegida | `HomeViewModelTest` | unitaria |
| El panel compone las nueve filas con todas sus hijas | `SectionsViewModelTest` | unitaria |
| Desplegar y contraer una sección | `SectionsViewModelTest` | unitaria |
| La segunda fila aparece, va debajo, y sus chips emiten el código correcto | `SectionFilterChipsTest` | instrumentada |
| «Toda la sección» emite el código de la sección padre | `SectionFilterChipsTest` | instrumentada |
| Los dos rótulos de la fecha, y que sin fecha no hay rótulo | `BulletinHeaderTest` | instrumentada |
| La cabecera del panel y que la flecha invoca el cierre | `SectionsDrawerTest` | instrumentada |
| Que el panel ya no tiene campo de texto | `SectionsDrawerTest` | instrumentada |
| Navegar a una subsección desde los chips | `HomeNavigationTest` | instrumentada |

Las tres clases instrumentadas nuevas se montan con `createComposeRule()`, no con
`createAndroidComposeRule<MainActivity>()`: lo que se comprueba no es la portada, y cruzarla cuesta un
mínimo de 1,2 s por prueba además de acoplarlas a una pantalla que no es la del caso.

**Trampas ya pagadas que aplican aquí** (todas en `CLAUDE.md`):

- `setContent` **solo se llama una vez por prueba**. Si una prueba necesita dos escenarios, van dentro
  de la misma llamada.
- Tocar un destino y teclear a continuación es una carrera que solo aparece con la suite llena. Aquí
  no se teclea, pero si alguna prueba navega y luego afirma, hay que afirmar primero que la pantalla
  está montada.
- Un `waitUntil` que se agota no dice nada. Si una espera puede fallar por más de un motivo, se
  envuelve y se afirma cuál fue.
- El chevrón del panel usa `animateFloatAsState`, que sí llega a reposo. No hay animación infinita en
  lo que esta feature toca, así que `assertIsDisplayed()` es seguro.

---

## 3. El recorrido manual *(obligatorio)*

Se hace en un dispositivo o emulador con datos ya sincronizados. Es lo único que comprueba de verdad
lo que esta feature promete, porque lo que se corrige es **qué entiende quien mira**.

### 3.1 El primer chip y la fecha (US1, US2)

1. Abrir la aplicación y esperar a que pase la portada.
2. **El primer chip dice «Boletín de hoy»**, no «Todo».
3. **La cabecera azul dice de qué fecha es**: `Edición del <fecha>`, con su icono de calendario.
4. Anotar el recuento del distintivo.
5. Tocar «Disposiciones». El recuento sube mucho —debe—, y la cabecera pasa a
   `Última publicación: <fecha>`. **Ese contraste ya no debe leerse como un fallo.**
6. Volver al primer chip: el recuento vuelve a ser exactamente el de antes.

> Si el BOC no ha publicado hoy, «Boletín de hoy» mostrará la última edición disponible —el viernes,
> en domingo—. Es correcto, y el rótulo de la fecha es justamente lo que lo explica.

### 3.2 Las subsecciones desde los chips (US3)

7. Desde el boletín del día, tocar **«Personal»**. Dos cosas a la vez: la lista pasa a toda la sección
   **y** aparece una segunda fila debajo.
8. La segunda fila muestra **«Toda la sección», «Nombramientos», «Oposiciones», «Otros de personal»**,
   con «Toda la sección» marcada y con un estilo claramente secundario respecto de la fila de arriba.
9. Tocar «Oposiciones»: la lista y la cabecera pasan a esa subsección, «Oposiciones» queda marcada,
   «Personal» sigue marcada arriba, y la segunda fila no desaparece.
10. Tocar «Toda la sección»: vuelve la sección completa con el recuento del paso 5.
11. Tocar **«Disposiciones»** (sin subsecciones): la segunda fila **desaparece** y no deja hueco.
12. Tocar **«Boletín de hoy»**: la segunda fila desaparece y la pantalla queda idéntica al paso 2.
13. Repetir 7-11 con **Economía** (4 subsecciones), **Anuncios** (5) y **Judicial** (2). En Judicial,
    entrar en **«Subastas»**: está vacía y debe verse el estado vacío de sección, **nunca** un error.
14. Con una subsección elegida, **girar el móvil**: la selección y la segunda fila se conservan.
15. Con una subsección elegida, matar el proceso y volver:
    `adb shell am kill com.jrblanco.boccantabria` y reabrir. Se conservan.
16. Elegir una subsección **desde el panel lateral** y comprobar que en Inicio aparece la segunda fila
    igual, con esa subsección marcada. Es FR-014 y FR-018.
17. Con una subsección elegida, pulsar **Atrás**: la aplicación se cierra, no vuelve al chip anterior.
    Pasear por los chips no construye pila de retroceso.

### 3.3 El panel lateral (US4)

18. Abrir el panel con el botón de menú. **Lo primero es el escudo de Cantabria y «BOC Cantabria»**.
19. **No hay ningún campo de texto.**
20. Al final de esa fila hay una flecha. Tocarla: el panel se recoge y la pantalla de debajo queda
    como estaba, sin navegar a ninguna parte.
21. Reabrir y comprobar que los dos gestos de siempre siguen funcionando: deslizar y tocar fuera.
22. **Desplegar las cuatro secciones con subsecciones a la vez** y recorrer el panel de arriba abajo:
    la cabecera y las veintitrés filas deben ser alcanzables (FR-026).
23. Elegir una subsección desde el panel: se comporta exactamente como antes de esta feature.

### 3.4 La lupa (US5)

24. Tocar la lupa. El texto de ayuda **dice que filtra lo que está en pantalla**, no que busca en todo
    el BOC.
25. Escribir algo que sí exista: la lista se acota mientras se escribe y aparece el recuento de
    coincidencias.
26. Escribir algo que no exista: el mensaje habla de **esta lista** y sigue ofreciendo
    **«Buscar en todo el BOC»**.
27. Aceptar la oferta: el buscador global se abre **con el término ya escrito**. Es el puente que la
    feature 006 construyó y esta feature promete no romper.

> **Trampa al automatizar este apartado**: `adb shell input text` con espacios corta en el primer
> espacio, y el resultado parece un defecto de la aplicación. Los espacios van como `%s`:
> `adb shell input text 'plazo%sde%srecurso'`.

### 3.5 Que nada más ha cambiado

28. Abrir una publicación desde la lista: el detalle es el de siempre.
29. Guardar y desmarcar desde una tarjeta: funciona, y compartir desde la tarjeta **no** abre además
    la publicación.
30. Ir a Buscar, a Guardados y a Avisos y volver: sin franja muerta sobre la barra inferior y sin
    cambios.
31. Deslizar para refrescar: la línea de progreso aparece bajo la cabecera y el contenido no
    desaparece.

---

## 4. Comprobación de que no se ha tocado lo que no había que tocar

```bash
# Ni migraciones ni esquemas nuevos
git diff --stat main -- app/schemas/                       # debe salir vacío

# Ni capa de datos ni de dominio
git diff --stat main -- app/src/main/java/com/jrblanco/boccantabria/data/ \
                        app/src/main/java/com/jrblanco/boccantabria/domain/   # debe salir vacío

# Ni dependencias nuevas
git diff --stat main -- gradle/libs.versions.toml app/build.gradle.kts        # debe salir vacío

# Ningún color literal fuera del tema (lo vigila Konsist, pero conviene verlo)
grep -rn "androidx.compose.ui.graphics.Color" app/src/main/java/com/jrblanco/boccantabria/ui/ || echo "limpio"
```

Los cuatro deben salir vacíos o «limpio». Si alguno no lo hace, o hay un cambio que la especificación
no contempla, o hay que ampliarla.

---

## 5. Qué NO se puede comprobar aquí

- **Que la gente lo entienda.** SC-001 y SC-002 hablan de comprensión, y eso no lo mide una aserción.
  Se comprueba enseñándoselo a alguien que no haya visto la aplicación y preguntándole de qué fecha
  cree que es la lista. Si duda, el rótulo no está bien redactado y se cambia una cadena.
- **Que «Boletín de hoy» quepa bien en todos los tamaños.** Se mira en el recorrido manual, en la
  pantalla más estrecha disponible. La alternativa acordada es «Hoy».
