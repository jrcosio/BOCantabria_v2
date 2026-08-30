# Implementation Plan: Detalle de publicación y visor del PDF oficial

**Branch**: `004-detalle-publicacion` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-detalle-publicacion/spec.md`

## Summary

Cerrar el recorrido que la feature 003 dejó a medias: del titular al documento. Pulsar una tarjeta
lleva a una pantalla que presenta lo que la aplicación sabe del anuncio, y desde ahí el PDF oficial
se abre **dentro de la aplicación**.

Dos cosas la definen, y son inseparables. Una es el visor: el que mejor funciona en Compose es el
oficial de Jetpack y exige `minSdk 28`, así que la constitución se enmendó a 1.1.0 antes de escribir
este plan. La otra es la desconfianza: el enlace del anuncio devuelve un PDF, pero un servicio sin
compromiso de disponibilidad puede devolver una página de error con código 200, y una aplicación que
consulta un boletín oficial no puede presentar eso como oficial. De ahí que la validación —esquema,
host, tipo declarado y **los bytes que realmente llegan**— sea la mitad del trabajo.

Las dos funciones de IA y la de guardar quedan dichas y con su sitio hecho, sin fingir que funcionan.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (aplicado de forma integrada por AGP 9.3.2)

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01) con Material 3, Navigation Compose 2.10.0,
Koin 4.2.2, Room 2.8.4 con KSP, OkHttp BOM 5.5.0, Firebase BOM 34.18.0, corrutinas 1.11.0. **Se
añade**: `androidx.pdf:pdf-compose` 1.0.0-beta01. **Se retira**: `com.android.tools:desugar_jdk_libs`
y `isCoreLibraryDesugaringEnabled` (FR-040)

**Storage**: Room, sin cambios de esquema. La copia local del PDF **no** entra en la base de datos:
vive en `cacheDir/documents/` y su estado se deriva del sistema de ficheros (research.md D-003)

**Network**: el `OkHttpClient` que ya existe, **derivado** con `newBuilder()` para darle límites más
largos: un documento tarda más que un feed (research.md D-004)

**Testing**: JUnit 4, MockK 1.14.11, Turbine 1.2.1, `kotlinx-coroutines-test` 1.11.0, Robolectric
4.16.1, `koin-test`, Compose UI Test, Konsist 0.17.3, MockWebServer 3 con `okhttp-tls`

**Target Platform**: Android, **`minSdk 28`** desde la enmienda 1.1.0 de la constitución,
`compileSdk`/`targetSdk` 37, solo vertical en teléfonos

**Performance Goals**: documento ya consultado en menos de 1 s sin red (SC-002); documento nuevo en
menos de 10 s con conexión normal (SC-003); un documento de cincuenta páginas no agota la memoria de
un dispositivo de gama media (SC-006)

**Constraints**: `domain` sin dependencias de plataforma —la ruta del fichero viaja como `String`—;
pruebas deterministas sin red real ni reloj del sistema; ningún color, tamaño ni espaciado literal
fuera del tema; nada se presenta como documento oficial sin haberlo comprobado

**Scale/Scope**: 2 pantallas nuevas, 1 pantalla y 1 armazón modificados. Documentos de hasta el tope
de tamaño configurado. ~28 ficheros de producción y ~16 de prueba

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluado contra la constitución **1.1.0**, ya enmendada en esta misma rama.

| Principio | Cómo lo satisface este plan | Puerta |
|---|---|---|
| **I. SDD obligatorio** | La feature recorre el ciclo completo. La rama la creó la extensión git de Spec Kit. La enmienda de la constitución se hizo **antes** que este plan, precisamente para que esta puerta se evalúe contra el texto vigente | ✅ |
| **II. Arquitectura limpia** | `OfficialDocument`, `DocumentStatus`, `ShareTarget` y `DetailTab` son Kotlin puro; la ruta del fichero viaja como `String` para que `domain` no vea `java.io.File` (D-007). El descargador y la caché son de `data`; el tipo `PdfDocument` del visor no sale de `ui/pdf` (D-014) | ✅ |
| **III. MVVM** | Dos pantallas con su `Screen`, su `ViewModel` y su `UiState` inmutable. `document` y `share` van fuera de un sellado único porque son ejes independientes, como en Inicio. Todos los componibles dibujables son sin estado | ✅ |
| **IV. Koin** | Descargador, caché, repositorio de documentos, cuatro casos de uso y dos modelos de pantalla se registran en `core/di`. El cargador de PDF se construye con una función factoría en `data`/`ui/pdf`, no en `core.di`, que no puede importar el SDK. El test del grafo fallará hasta que estén | ✅ |
| **V. Testing exigente** | Las tres capas. La validación del documento se prueba con MockWebServer sobre TLS —incluidas las respuestas fabricadas para engañar— y la caché con ficheros temporales. El tiempo se inyecta para poder comprobar la retirada por antigüedad | ✅ |
| **VI. Observabilidad desacoplada** | `document_opened` y `document_share` con recuentos y banderas. **Ningún dato personal**: ni título, ni URL, ni clave. Ningún SDK nuevo fuera de `data` | ✅ |
| **Restricciones tecnológicas** | `pdf-compose` va al catálogo de versiones. **Sin Fragments ni XML de layouts en nuestro código**: se eligió `pdf-compose` en lugar de `pdf-viewer-fragment` justo por eso. Corrutinas y `Flow`. Código en inglés, documentación en español. `minSdk 28` conforme a la constitución 1.1.0 | ✅ |

**Resultado de la puerta previa a la fase 0**: pasa. Ninguna violación que justificar.

**Re-evaluación posterior al diseño de la fase 1**: pasa. El diseño no introdujo desviaciones. Las
decisiones que añaden algo no exigido explícitamente quedan en *Complexity Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/004-detalle-publicacion/
├── spec.md                        # 4 historias, 48 requisitos, 12 criterios de éxito
├── plan.md                        # Este fichero
├── research.md                    # Fase 0: 16 decisiones con alternativas descartadas
├── data-model.md                  # Fase 1: la copia local, su estado y las transiciones
├── quickstart.md                  # Fase 1: 12 pasos de validación de extremo a extremo
├── contracts/
│   └── internal-contracts.md      # Fase 1: contratos, etiquetas de prueba y contrato visual
├── checklists/
│   └── requirements.md            # Checklist de calidad de la especificación
└── tasks.md                       # Fase 2 (/speckit-tasks — NO lo crea /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/jrblanco/boccantabria/
├── core/di/                                # Se amplían Data, Domain y Ui
├── data/
│   ├── repository/DocumentRepositoryImpl.kt                    # NUEVO
│   ├── source/remote/DocumentDownloader.kt · OkHttpDocumentDownloader.kt   # NUEVO
│   └── source/local/DocumentCache.kt · FileDocumentCache.kt · PublicationDao.kt  # NUEVO y AMPLIADO
├── domain/
│   ├── model/OfficialDocument.kt · DocumentStatus.kt · ShareTarget.kt · DetailTab.kt
│   ├── repository/DocumentRepository.kt · PublicationRepository.kt    # NUEVO y AMPLIADO
│   └── usecase/ObservePublicationUseCase.kt · ObserveOfficialDocumentUseCase.kt ·
│               OpenOfficialDocumentUseCase.kt · ShareOfficialDocumentUseCase.kt
├── ui/
│   ├── detail/PublicationDetailScreen.kt · PublicationDetailViewModel.kt · PublicationDetailUiState.kt
│   ├── detail/component/DocumentHeader.kt · DetailTabs.kt · DocumentTab.kt ·
│   │                    ComingSoonTab.kt · DetailActionBar.kt · DocumentPreview.kt
│   ├── pdf/PdfViewerScreen.kt · PdfViewerViewModel.kt · PdfViewerUiState.kt · PdfDocumentLoader.kt
│   ├── home/component/PublicationCard.kt   # MODIFICADO: la tarjeta navega
│   ├── main/MainShell.kt                   # MODIFICADO: compartir sale de aquí
│   └── navigation/Routes.kt · BOCantabriaNavHost.kt            # MODIFICADOS
└── core/util/                              # Sin cambios: TimeProvider y DispatcherProvider ya están

app/src/main/res/
├── drawable/                               # ~6 vectores de Material Symbols
├── xml/file_paths.xml                      # NUEVO: acotado a cache/documents
└── values/strings.xml                      # Textos del apartado 32

app/src/main/AndroidManifest.xml            # MODIFICADO: <provider> de FileProvider
app/build.gradle.kts                        # MODIFICADO: minSdk 28, pdf-compose, sin azucarado

app/src/test/java/com/jrblanco/boccantabria/
├── data/source/remote/OkHttpDocumentDownloaderTest.kt
├── data/source/local/FileDocumentCacheTest.kt
├── data/repository/DocumentRepositoryImplTest.kt
├── domain/model/  (los cuatro modelos nuevos)
├── domain/usecase/  (los cuatro casos de uso)
├── ui/detail/PublicationDetailViewModelTest.kt · ui/pdf/PdfViewerViewModelTest.kt
└── integration/DocumentFlowIntegrationTest.kt

app/src/androidTest/java/com/jrblanco/boccantabria/
├── ui/detail/PublicationDetailContentTest.kt · DocumentHeaderTest.kt · ComingSoonTabTest.kt
├── ui/pdf/PdfViewerContentTest.kt
└── ui/DetailNavigationTest.kt
```

**Structure Decision**: se mantiene el módulo único `:app`. Dos paquetes nuevos en `ui`, y ambos por
la misma razón: son pantallas, y en este proyecto cada pantalla tiene el suyo. `ui/pdf` además hace
de frontera —es lo único que conoce la API en beta del visor (research.md D-014)—. En `data` no se
inventa nada: el descargador es una fuente remota más y la caché una local más, exactamente como el
lector de feeds y la base de datos de la feature 003.

## Complexity Tracking

> La puerta de la constitución pasa sin violaciones. Se registran aquí, por transparencia, las
> decisiones que añaden algo que la constitución no exigía explícitamente.

| Decisión | Por qué es necesaria | Alternativa más simple y por qué se descartó |
|---|---|---|
| **Enmienda de `minSdk` 24 → 28** | Es el precio del visor oficial de Jetpack para Compose, y la constitución prohíbe Fragments, que es la otra forma de integrarlo. Aprobada por el propietario y registrada en el Sync Impact Report | Visor propio sobre `PdfRenderer`: `minSdk` intacto, pero el zoom, el reciclado y la memoria escritos por nosotros, y peores, justo en la parte para la que existe la aplicación. Biblioteca de terceros: un solo mantenedor en el camino crítico de leer un documento oficial |
| **Dependencia en beta: `pdf-compose` 1.0.0-beta01** | No hay versión estable todavía y la feature no puede esperar. Queda encapsulada en `ui/pdf` | Esperar a la estable: aplaza la feature sin fecha. Usar `pdf-viewer-fragment`, que sí es más maduro: prohibido por la constitución |
| **Arrastra `pdf-viewer` y la biblioteca Material de vistas** | Es transitivo de `pdf-compose` y no hay forma de evitarlo | Excluir la transitiva: rompería el visor. Se acepta y se anota: nuestro código no usa Fragments, pero entran en el binario |
| **`FileProvider` nuevo** | Compartir un fichero exige una `content://`; un `file://` lanza `FileUriExposedException` desde Android 7 | Seguir compartiendo el enlace: es justo lo que el propietario pidió cambiar |
| **La copia local no entra en la base de datos** | Es caché: el sistema puede borrarla cuando quiera, y una fila que sobrevive al fichero es una mentira que hay que reconciliar | Una tabla de documentos: obliga a mantener sincronizadas base de datos y sistema de ficheros para un dato que se deriva del segundo |
| **`ShareTarget` con su motivo** | FR-033 exige explicar por qué se comparte el enlace en lugar del documento. Un booleano no puede decir por qué | Devolver solo la `Uri`: la pantalla no podría distinguir el caso normal del degradado |
| **La página visible se guarda a mano** | `rememberPdfViewerState()` no es `rememberSaveable` y FR-029 exige conservarla | Aceptar que se pierda: incumple el requisito. Envolverlo en un `Saver` propio: más código para el mismo efecto, y hay que rehacerlo cuando la biblioteca lo resuelva |
