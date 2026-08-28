plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.jrblanco.boccantabria"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jrblanco.boccantabria"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // java.time exige API 26 y minSdk es 24. El azucarado cubre ese hueco sin tocar minSdk,
        // que la constitución fija (research.md D-004).
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        // BOCantabriaApp usa BuildConfig.DEBUG para el nivel de log de Koin.
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

    // --- Azucarado de la biblioteca estándar ---
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
