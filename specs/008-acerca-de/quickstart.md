# Quickstart: validar «Acerca de»

## Automatizado

Usar el JBR de Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

## Manual en dispositivo

1. Abrir Inicio y pulsar Información.
2. Confirmar barra azul `Acerca de`, flecha Atrás y ausencia de barra inferior.
3. Comparar con `Datos_modelo/screen_info.png`: foto circular sin deformar, jerarquía, bloques y
   tarjetas; GitHub aparece como segundo botón.
4. Aumentar el tamaño de fuente y comprobar que todo sigue alcanzable mediante desplazamiento.
5. Pulsar LinkedIn con su aplicación instalada: debe abrir el perfil indicado.
6. Repetir sin aplicación asociada: debe abrir el navegador.
7. Pulsar GitHub: debe abrir el repositorio indicado.
8. Volver desde cada destino: «Acerca de» permanece disponible y Atrás devuelve a Inicio.
9. Comprobar al final el aviso de independencia, la fuente y la versión real de la APK.
10. Con un entorno sin manejador de URL, comprobar el Snackbar y que la app no se cierra.
