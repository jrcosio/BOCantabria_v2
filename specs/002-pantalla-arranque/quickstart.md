# Quickstart: validar la pantalla de arranque

**Feature**: `002-pantalla-arranque` | **Fase**: 1 | **Fecha**: 2026-08-28

Ejecuta los bloques en orden: cada uno depende del anterior.

## Prerrequisitos

```bash
cd /Users/jrblanco/Develop/apps/BOCantabria
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB devices    # debe listar al menos un "device"
```

---

## 1. Compila

```bash
./gradlew :app:assembleDebug
```

---

## 2. Pruebas sin dispositivo

```bash
./gradlew :app:testDebugUnitTest
```

Informe en `app/build/reports/tests/testDebugUnitTest/index.html`. Cubre:

| Qué se valida | Requisitos |
|---|---|
| Precedencia del arranque: sin conexión manda; versión obsoleta manda sobre mantenimiento | FR-003, FR-012, FR-013 |
| Traducción de la configuración remota, mensaje vacío → nulo, y que ninguna excepción escapa de `data` | FR-014, FR-024 |
| Los cuatro estados de la portada y sus acciones, observando el flujo de estado | FR-009, FR-010 |
| Que el tiempo mínimo se respeta y que **no** se suma al trabajo real, con tiempo virtual | FR-005, FR-027, SC-002 |
| Que superar el límite de espera produce un error recuperable | FR-006 |
| Que reintentar durante una preparación en curso no lanza una segunda | FR-011 |
| Que desde acceso bloqueado no hay salida a Home | FR-012 |
| Que el grafo de dependencias completo resuelve | — |
| Que `domain` no importa plataforma ni proveedores, y que toda pieza nueva tiene prueba | SC-004 |

**Comprobar que las reglas de arquitectura siguen mordiendo:**

```bash
echo 'import android.content.Context' >> app/src/main/java/com/jrblanco/boccantabria/domain/model/AppConfig.kt
./gradlew :app:testDebugUnitTest --tests '*ArchitectureRulesTest*'   # DEBE fallar
git checkout -- app/src/main/java/com/jrblanco/boccantabria/domain/model/AppConfig.kt
./gradlew :app:testDebugUnitTest --tests '*ArchitectureRulesTest*'   # DEBE pasar
```

---

## 3. Pruebas de interfaz

```bash
./gradlew :app:connectedDebugAndroidTest
```

Valida el render de los cuatro estados, que los botones responden, y que tras el arranque el
retroceso no devuelve a la portada (FR-025, FR-026).

---

## 4. Comprobación visual y de comportamiento

```bash
./gradlew :app:installDebug
$ADB shell am force-stop com.jrblanco.boccantabria
$ADB shell am start -W -n com.jrblanco.boccantabria/.MainActivity
```

**Esperado**:

1. **Sin destello blanco** entre el arranque del sistema y la portada de la aplicación (FR-002).
   Míralo despacio, o grábalo: `$ADB shell screenrecord /sdcard/arranque.mp4` y revisa los primeros
   fotogramas.
2. La portada se ve al menos un segundo y **no parpadea** (SC-002).
3. Pasa sola al contenido principal (FR-004).
4. `TotalTime` de la salida por debajo de 3000 ms (SC-001). Anota la cifra.

**Comparar con la imagen de referencia** (SC-007):

```bash
$ADB shell am force-stop com.jrblanco.boccantabria
$ADB shell am start -n com.jrblanco.boccantabria/.MainActivity
$ADB exec-out screencap -p > /tmp/arranque_real.png
```

Contrastar contra `docs/diseno/Screen_Inicial_y_carga.png`: proporciones del escudo, jerarquía de
`BOC`, las dos líneas de la denominación, la línea divisoria y **los dos colores de la autoría**.
El texto de autoría es el acordado en la especificación, no el de la imagen.

**Retroceso** (FR-007):

```bash
$ADB shell input keyevent KEYCODE_BACK
$ADB shell dumpsys activity activities | grep -m1 'ResumedActivity'
```

**Esperado**: la aplicación se cierra. No debe volver a la portada.

**Orientación** (FR-023):

```bash
$ADB shell settings put system accelerometer_rotation 0
$ADB shell settings put system user_rotation 1     # horizontal
$ADB exec-out screencap -p > /tmp/girado.png
$ADB shell settings put system user_rotation 0
```

**Esperado**: la captura sigue en vertical.

---

## 5. Sin conexión

```bash
$ADB shell svc wifi disable && $ADB shell svc data disable
$ADB shell am force-stop com.jrblanco.boccantabria
$ADB shell am start -n com.jrblanco.boccantabria/.MainActivity
```

**Esperado**: mensaje de error con «Reintentar» y «Continuar sin conexión» (FR-010). Pulsar
continuar lleva al contenido principal en dos toques como máximo (SC-003).

```bash
$ADB shell svc wifi enable && $ADB shell svc data enable
```

Y con la conexión restaurada, «Reintentar» debe completar el arranque.

---

## 6. Identidad visual constante

**El color no depende del fondo de pantalla** (FR-017, SC-005): cambiar el fondo del dispositivo por
uno de color intenso, abrir la aplicación y comprobar que ni la portada ni el contenido principal
cambian de color. Repetir con un fondo de color opuesto.

**El tema del sistema no altera la aplicación** (FR-016b, SC-005): capturar la pantalla con el tema
claro y con el oscuro, y comprobar que son idénticas.

```bash
$ADB shell "cmd uimode night no"
$ADB shell am force-stop com.jrblanco.boccantabria && $ADB shell am start -n com.jrblanco.boccantabria/.MainActivity
sleep 3 && $ADB exec-out screencap -p > /tmp/claro.png
$ADB shell "cmd uimode night yes"
$ADB shell am force-stop com.jrblanco.boccantabria && $ADB shell am start -n com.jrblanco.boccantabria/.MainActivity
sleep 3 && $ADB exec-out screencap -p > /tmp/oscuro.png
$ADB shell "cmd uimode night no"
cmp /tmp/claro.png /tmp/oscuro.png && echo "IDÉNTICAS (correcto)"
```

**Esperado**: las dos capturas son idénticas, iconos de las barras del sistema incluidos.

**Texto al 200 %** (SC-006):

```bash
$ADB shell settings put system font_scale 2.0
$ADB shell am force-stop com.jrblanco.boccantabria && $ADB shell am start -n com.jrblanco.boccantabria/.MainActivity
$ADB exec-out screencap -p > /tmp/texto_200.png
$ADB shell settings put system font_scale 1.0
```

**Esperado**: la jerarquía se conserva y ningún texto queda recortado.

---

## 7. Análisis estático

```bash
./gradlew :app:lintDebug
```

Sin avisos nuevos atribuibles a esta feature. Informe en
`app/build/reports/lint-results-debug.html`.

---

## 8. Configuración remota (diferido)

Los parámetros `min_supported_version_code` y `maintenance_message` **todavía no existen** en la
consola de Firebase; la aplicación arranca con los valores por defecto empaquetados (FR-014). Para
que la comprobación sirva en producción hay que darlos de alta en el proyecto `bocantabria-6e90f`.
Comprobación con la consola ya configurada:

- Publicar `min_supported_version_code` por encima de la versión instalada → la aplicación debe
  mostrar el aviso de actualización y **no** dejar continuar.
- Publicar un `maintenance_message` → la aplicación debe mostrarlo.

---

## Resumen de aceptación

La feature está lista cuando los pasos 1, 2, 3 y 7 terminan en verde, los pasos 4, 5 y 6 se
confirman a mano en el emulador, y la comprobación de la violación deliberada del paso 2 falla y
vuelve a pasar como se describe. El paso 8 queda pendiente de la consola de Firebase y no bloquea.
