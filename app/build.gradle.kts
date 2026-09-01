import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) load(FileInputStream(f))
}
val majorMinor = versionProps.getProperty("majorMinor", "1.0")

// CI passes -PappVersionName=1.0.7 -PappVersionCode=42. Local builds get sane defaults.
val ciVersionName = (project.findProperty("appVersionName") as String?) ?: "$majorMinor.0-dev"
val ciVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "ir.mums.stufood"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.mums.stufood"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Falls back to debug signing only when no keystore secret is present
            // (e.g. a local `assembleRelease` on your machine) so the build never
            // just fails — but CI releases will always use the real key.
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // unchanged
}