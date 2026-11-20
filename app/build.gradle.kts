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

    // Firma fija del proyecto (la misma en todas las versiones, para que las actualizaciones se instalen encima).
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "noirbooks2026"
            keyAlias = "noirbooks"
            keyPassword = "noirbooks2026"
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
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    buildTypes {
        release {
            // Sin R8: prioridad a la estabilidad sobre el tamaño.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
}
