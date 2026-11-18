plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bestie.lectoraao3"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bestie.lectoraao3"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    // Firma fija: así cada versión nueva se instala encima de la anterior sin perder tu biblioteca
    signingConfigs {
        create("lectora") {
            storeFile = file("lectora.jks")
            storePassword = "lectora-fics"
            keyAlias = "lectora"
            keyPassword = "lectora-fics"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lectora")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("lectora")
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
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
}
