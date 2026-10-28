plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.surair.driverprofit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.surair.driverprofit"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
    }

    // Clave fija para que todas las compilaciones (locales y de GitHub Actions) tengan la misma
    // firma y el APK nuevo se instale encima del anterior sin desinstalar. Es una clave autofirmada
    // para distribuir por fuera de la tienda; si el repo pasa a ser público, mejor moverla a un secreto.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("app/keystore/driver-profit.jks")
            storePassword = "driverprofit"
            keyAlias = "driverprofit"
            keyPassword = "driverprofit"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
