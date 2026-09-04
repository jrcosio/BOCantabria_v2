plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

/**
 * The Gemini credential, read at configuration time.
 *
 * `providers.fileContents` and `providers.environmentVariable` and not `File.readText`: this build
 * has the configuration cache on (`gradle.properties`), and reading a file directly at configuration
 * time is an undeclared input. Both of these are provider APIs, so Gradle tracks them.
 *
 * When the key is absent the build **stays green** and the field is an empty string, which the app
 * reports as "not configured". That is what lets CI compile and test without secrets (009
 * research.md D-102, FR-029, FR-033). `local.properties` is git-ignored; the value must never reach
 * the repository.
 *
 * Gemini keys come in **two** formats and neither is the previous provider's `gsk_`: the classic one
 * begins with `AIza`, the one issued today with `AQ.`. Grepping for only one of them is how a leak
 * gets declared clean — checked against a real key on 4 September 2026.
 */
val geminiApiKey: Provider<String> = providers
    .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText
    .map { contents ->
        contents.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("GEMINI_API_KEY=") }
            ?.substringAfter('=')
            ?.trim()
            .orEmpty()
    }
    .orElse(providers.environmentVariable("GEMINI_API_KEY"))
    .orElse("")

android {
    namespace = "com.jrblanco.boccantabria"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jrblanco.boccantabria"
        minSdk = 28
        targetSdk = 37
        versionCode = 4
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Entrecomillado a mano porque buildConfigField emite el literal tal cual.
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey.get()}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BOCantabriaApp usa BuildConfig.DEBUG para el nivel de log de Koin, y la feature 009
        // necesita BuildConfig.GEMINI_API_KEY.
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric necesita los recursos de Android en los tests de src/test.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// El esquema de Room se exporta desde la versión 1: es lo que permitirá escribir la prueba de
// migración cuando llegue la 2, y cuesta una línea hacerlo ahora.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // --- Compose (BOM gobierna las versiones) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // --- AndroidX ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // --- Persistencia (Room) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Red (BOM gobierna las versiones) ---
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    // --- Visor de PDF ---
    // El oficial de Jetpack, expuesto como componible. Se eligió frente a pdf-viewer-fragment
    // porque la constitución prohíbe Fragments, y exige minSdk 28: de ahí la enmienda 1.1.0.
    implementation(libs.androidx.pdf.compose)
    implementation(libs.androidx.pdf.document.service)

    // --- Serialización ---
    // El cuerpo y la respuesta del servicio de resúmenes son JSON. La biblioteca ya llegaba por
    // transitividad desde navigation-compose, pero se declara porque el código la importa: depender
    // por accidente de lo que arrastra otro es depender de una decisión ajena que puede cambiar.
    implementation(libs.kotlinx.serialization.json)

    // --- Corrutinas ---
    implementation(libs.kotlinx.coroutines.android)
    // Provides Task.await(), used to consume the Firebase Remote Config API from a coroutine.
    // Declared explicitly rather than relied on transitively: the code imports it directly.
    implementation(libs.kotlinx.coroutines.play.services)

    // --- Firebase (BOM gobierna las versiones) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)

    // --- Inyección de dependencias (BOM gobierna las versiones) ---
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // --- Tests unitarios y de integración (src/test) ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.konsist)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.mockwebserver)
    // El catálogo de fuentes exige https, así que el servidor de pruebas también lo habla.
    testImplementation(libs.okhttp.tls)

    // --- Tests instrumentados y de UI (src/androidTest) ---
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.koin.bom))
    androidTestImplementation(libs.koin.test)
    androidTestImplementation(libs.koin.android.test)

    // --- Solo debug ---
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
