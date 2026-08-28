# Quickstart: validar el esqueleto de arquitectura

**Feature**: `001-esqueleto-arquitectura` | **Fase**: 1 | **Fecha**: 2026-08-28

Guía para comprobar de extremo a extremo que la feature hace lo que la especificación dice.
Ejecuta los bloques en orden: cada uno depende del anterior.

## Prerrequisitos

Java no está en el `PATH` del sistema; se usa el que trae Android Studio:

```bash
cd /Users/jrblanco/Develop/apps/BOCantabria
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Para los pasos 3 y 4 hace falta un emulador o dispositivo conectado:

```bash
~/Library/Android/sdk/platform-tools/adb devices   # debe listar al menos un "device"
```

---

## 1. Compila

```bash
./gradlew :app:assembleDebug
```

**Esperado**: `BUILD SUCCESSFUL`. Valida que el grafo de tipos es coherente y que los plugins
de Firebase y de serialización conviven con AGP 9.

---

## 2. Pruebas sin dispositivo (unitarias, integración y arquitectura)

```bash
./gradlew :app:testDebugUnitTest
```

**Esperado**: `BUILD SUCCESSFUL` con todas las pruebas en verde. Informe navegable en
`app/build/reports/tests/testDebugUnitTest/index.html`.

Cubre:

| Qué se valida | Requisitos |
|---|---|
| El caso de uso propaga éxito y fallo sin alterarlos | FR-017 |
| La política del repositorio: remoto, respaldo local, fallo y lista vacía | FR-004, FR-009 |
| Los cuatro estados de la pantalla y el reintento, observando el flujo de estado | FR-002, FR-003, FR-017 |
| Que pulsar reintentar durante una carga no lanza una segunda | Caso límite de reintentos |
| Que el grafo de dependencias completo resuelve | FR-011, FR-018 |
| El recorrido completo entre capas con el grafo real | FR-012, FR-019 |
| Que `domain` no importa plataforma ni proveedores, y que `ui` no importa `data` | FR-007, FR-008, FR-009 |
| Que la analítica no envía parámetros sensibles | FR-016 |

**Comprobación de que las reglas de arquitectura muerden de verdad** (el criterio SC-004 exige
que una violación se detecte automáticamente; conviene verificar que la red no tiene agujeros):

```bash
# Introduce una violación deliberada y comprueba que falla
echo 'import android.content.Context' >> app/src/main/java/com/jrblanco/boccantabria/domain/model/ContentItem.kt
./gradlew :app:testDebugUnitTest --tests '*ArchitectureRulesTest*'   # DEBE fallar
git checkout -- app/src/main/java/com/jrblanco/boccantabria/domain/model/ContentItem.kt
./gradlew :app:testDebugUnitTest --tests '*ArchitectureRulesTest*'   # DEBE pasar
```

---

## 3. Pruebas de interfaz

```bash
./gradlew :app:connectedDebugAndroidTest
```

**Esperado**: `BUILD SUCCESSFUL`. Valida el render de los cuatro estados, que el botón de
reintentar responde y que la pantalla se compone dentro de la actividad real con el grafo de
pruebas (FR-020).

---

## 4. Comprobación manual en el dispositivo

```bash
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.jrblanco.boccantabria/.MainActivity
```

**Esperado**:

1. La aplicación abre sin cerrarse (FR-001).
2. Aparece brevemente el indicador de carga y después la lista de contenido (FR-002).
3. Girando el dispositivo, el contenido permanece y **no** reaparece el indicador de carga
   (FR-005).

Comprueba que Firebase quedó inicializado:

```bash
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -i "FirebaseApp initialization"
```

**Esperado**: una línea de inicialización correcta.

---

## 5. Análisis estático

```bash
./gradlew :app:lintDebug
```

**Esperado**: `BUILD SUCCESSFUL`. Informe en
`app/build/reports/lint-results-debug.html`.

---

## 6. Telemetría (diferido)

Los eventos de analítica y los cierres inesperados tardan en aparecer en los paneles de
Firebase (hasta 24 h para analítica; minutos para errores). El criterio SC-006 se verifica
fuera de esta sesión, en la consola del proyecto `bocantabria-6e90f`:

- **Analytics → DebugView** con la depuración activada:
  ```bash
  ~/Library/Android/sdk/platform-tools/adb shell setprop debug.firebase.analytics.app com.jrblanco.boccantabria
  ```
  Al abrir la pantalla inicial debe aparecer el evento de pantalla vista.
- **Crashlytics**: forzar un cierre inesperado desde una compilación de depuración y confirmar
  que la traza llega al panel.

---

## Resumen de aceptación

La feature está lista cuando los pasos 1, 2, 3 y 5 terminan en verde, el paso 4 se confirma a
mano en el emulador, y la comprobación de la violación deliberada del paso 2 falla y vuelve a
pasar como se describe.
