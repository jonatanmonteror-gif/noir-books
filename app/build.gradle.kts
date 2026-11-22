plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.noirbooks.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noirbooks.app"
        minSdk = 23
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Firmas. Las claves vienen de los secretos de GitHub (el flujo de Actions las deja en un archivo temporal).
    //  - release: la del APK de GitHub (siempre la misma, para que las actualizaciones se instalen encima).
    //  - upload: la clave de subida a Google Play (Google firma la app final con la suya).
    // Mientras no existan los secretos, se usa la clave antigua del repositorio para no romper las compilaciones.
    fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    val ghKey = env("SIGNING_KEY_GITHUB_FILE")
    val playKey = env("SIGNING_KEY_PLAY_FILE")
    signingConfigs {
        create("release") {
            storeFile = file(ghKey ?: "release.jks")
            storePassword = (if (ghKey != null) env("SIGNING_PASS_GITHUB") else null) ?: "noirbooks2026"
            keyAlias = "noirbooks"
            keyPassword = storePassword
        }
        create("upload") {
            if (playKey != null) {
                storeFile = file(playKey)
                storePassword = env("SIGNING_PASS_PLAY")
                keyAlias = "noirbooks-upload"
                keyPassword = storePassword
            } else {
                storeFile = file(ghKey ?: "release.jks")
                storePassword = (if (ghKey != null) env("SIGNING_PASS_GITHUB") else null) ?: "noirbooks2026"
                keyAlias = "noirbooks"
                keyPassword = storePassword
            }
        }
    }

    // Dos versiones del mismo código:
    //  - github: el APK de siempre, con su propio actualizador.
    //  - play: para Google Play (Play no permite que la app se actualice por otra vía).
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            // Solo móviles y tablets ARM (todos los Android reales). Quita las copias para emuladores x86,
            // que eran casi la mitad del APK (el traductor sin conexión trae 17 MB por arquitectura).
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = signingConfigs.getByName("release")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            signingConfig = signingConfigs.getByName("upload")
        }
    }

    buildTypes {
        release {
            // Sin R8: prioridad a la estabilidad sobre el tamaño.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("woff2", "otf")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.webkit:webkit:1.11.0")
    // Bloqueo con huella (BiometricPrompt necesita FragmentActivity)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jsoup:jsoup:1.18.1")
    // Inicio de sesión con Google solo para la carpeta privada de la app en Drive (copias de seguridad).
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    // Escáner de código de barras de Google (su propia pantalla, sin permiso de cámara)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // Reconocer el texto de una foto en el propio móvil (vía Google Play services)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    // Traducir en el propio móvil (el idioma se descarga una vez; el texto no sale del móvil)
    implementation("com.google.mlkit:translate:17.0.3")
}
