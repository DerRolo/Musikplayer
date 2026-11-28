plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.musikplayer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.musikplayer.app"
        minSdk = 26
        targetSdk = 34
        // Fortlaufende Build-Nummer aus GitHub Actions (run_number) – die App vergleicht damit beim Update-Check
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Woher Updates kommen (owner/repo), wird vom Workflow gesetzt
        resValue("string", "update_repo", (project.findProperty("updateRepo") as String?) ?: "")
    }

    // Feste Signatur, damit Updates über die alte Installation drüberinstalliert werden können
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("com.google.guava:guava:32.1.3-android")
}
