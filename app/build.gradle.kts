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
            if (!storePath.isNullOrEmpty()) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Kept as false per your original config
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            val keystorePath = System.getenv("KEYSTORE_FILE")
            val keystoreExists = !keystorePath.isNullOrEmpty() && file(keystorePath).exists()
            val isCI = System.getenv("CI") == "true" // GitHub Actions sets this automatically
            
            signingConfig = if (keystoreExists) {
                // Valid keystore found: use it (works for both CI and local if configured)
                signingConfigs.getByName("release")
            } else if (isCI) {
                // We are in CI, but the keystore is missing: FAIL LOUDLY
                throw GradleException(
                    "⛔ CI RELEASE BUILD ABORTED: Valid keystore not found.\n" +
                    "Expected path: $keystorePath\n" +
                    "Ensure KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD are set in GitHub Secrets."
                )
            } else {
                // Local development without a keystore: gracefully fallback to debug signing
                // so `./gradlew assembleRelease` still produces a testable APK.
                signingConfigs.getByName("debug")
            }
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)

    implementation("androidx.security:security-crypto:1.1.0-alpha06") // Or latest stable
}