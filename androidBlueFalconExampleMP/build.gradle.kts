plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.bluefalconcomposemultiplatform.android"
    compileSdk = 34
    defaultConfig {
        // What phones recognise the app by; once people have installed it, keep it
        applicationId = "io.github.sedsrdasu.gooberconnect"
        minSdk = 24
        targetSdk = 34
        // GitHub's workflow passes its run number, so each build counts as an update. Builds on a Mac stay 1 / "1.0"
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"
    }
    // The release signing key, from the environment: GitHub's workflow sets these from the repo's secrets.
    // Without them, release builds stay unsigned, as before.
    val keystore = System.getenv("GOOBER_KEYSTORE_FILE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = project.file(keystore)
                storePassword = System.getenv("GOOBER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GOOBER_KEY_ALIAS")
                // A PKCS12 key file has one password for the file and the key
                keyPassword = System.getenv("GOOBER_KEYSTORE_PASSWORD")
            }
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.compose.ui:ui:1.4.0")
    implementation("androidx.compose.ui:ui-tooling:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.4.0")
    implementation("androidx.compose.foundation:foundation:1.4.0")
    implementation("androidx.compose.material:material:1.4.0")
    implementation("androidx.activity:activity-compose:1.7.0")
}