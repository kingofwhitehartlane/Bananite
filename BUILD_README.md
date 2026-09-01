# Bananite — Comprehensive Build & Development Guide

A step-by-step technical guide for cloning, compiling, configuring, and building **Bananite** — a native Android application engineered with Kotlin, Jetpack Compose, Material 3, OkHttp, and Jsoup.

This document is aimed at developers, contributors, and self-hosters who wish to build the application from source, set up local development environments, or automate release builds via CI/CD.

---

## 📋 Table of Contents

1. [Prerequisites & System Requirements](#-prerequisites--system-requirements)
2. [Project Structure Overview](#-project-structure-overview)
3. [Local Development Setup](#-local-development-setup)
   - [Environment Variables](#environment-variables)
   - [Configuring `local.properties`](#configuring-localproperties)
4. [Building from the Command Line (CLI)](#-building-from-the-command-line-cli)
   - [Debug Builds](#debug-build)
   - [Release Builds](#release-build)
   - [Gradle Clean & Sync](#clean-and-lint)
5. [Building with Android Studio](#-building-with-android-studio)
6. [Signing & Release Configuration](#-signing--release-configuration)
7. [CI/CD & GitHub Actions Workflow](#-cicd--github-actions-workflow)
8. [Troubleshooting & Common Issues](#-troubleshooting--common-issues)
9. [Architecture & Key Dependencies](#-architecture--key-dependencies)

---

## 🛠 Prerequisites & System Requirements

Before building the project, ensure your host machine meets the following environment requirements:

| Tool / Requirement | Minimum Version | Recommended / Tested | Notes |
| :--- | :--- | :--- | :--- |
| **Operating System** | Windows 10, macOS 11, Linux (64-bit) | Modern 64-bit OS | - |
| **JDK (Java Development Kit)** | JDK 17 | OpenJDK 17 / Eclipse Temurin 17 | Gradle target is configured for Java 17 |
| **Android SDK** | API Level 35 (`compileSdk`) | Android SDK 35, Build Tools 35.0.0 | Minimum runtime target is API 26 (Android 8.0) |
| **Gradle** | 8.x (via Gradle Wrapper) | Gradle 8.x (bundled via wrapper) | Pre-configured in `gradle/wrapper/` |
| **Android Studio** | Ladybug / Koala (2024.1+) | Latest Stable Android Studio | Optional for CLI builds; recommended for UI dev |

> **Note on Java Version:**  
> Bananite uses Kotlin targeting JVM 17. Ensure your `JAVA_HOME` points to a JDK 17 installation or that Android Studio's embedded JDK (Java 17) is selected in the build tools settings.

---

## 📂 Project Structure Overview

```
kingofwhitehartlane-stufoodapp/
├── .github/
│   └── workflows/
│       └── build.yml               # Automated GitHub Actions CI workflow
├── app/
│   ├── build.gradle.kts            # App module build script (dependencies, SDK versions)
│   ├── proguard-rules.pro          # ProGuard / R8 code obfuscation & optimization rules
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # Permissions, Application class, Activity declarations
│           ├── java/ir/mums/stufood/
│           │   ├── MainActivity.kt # Main host Activity with Jetpack Compose entry point
│           │   ├── StufoodApp.kt   # Application singleton & dependency container
│           │   ├── data/           # Repository, CookieJar session state, DataStore prefs
│           │   └── ui/             # Composables, screens, ViewModels, Material 3 theme
│           └── res/                # XML drawables, vector assets, raw CA certs, typography
├── gradle/
│   ├── libs.versions.toml          # Centralized Version Catalog for dependencies
│   └── wrapper/                    # Gradle Wrapper binaries and property configuration
├── build.gradle.kts                # Top-level build script (plugins definition)
├── gradle.properties               # JVM tuning options & AndroidX configurations
├── settings.gradle.kts             # Dependency resolution repositories & project inclusion
├── gradlew                         # POSIX shell executable for Gradle Wrapper
└── gradlew.bat                     # Windows batch executable for Gradle Wrapper
```

---

## ⚙️ Local Development Setup

### 1. Clone the Repository

Clone the repository to your local workspace:

```bash
git clone https://github.com/kingofwhitehartlane/stufoodapp.git
cd kingofwhitehartlane-stufoodapp
```

> **Important Path Tip:** Avoid cloning into paths containing spaces or non-ASCII characters (e.g., `C:\Users\John Doe\...` or special characters) as build tools like `aapt2` can experience path resolution issues on Windows.

### 2. Environment Variables

Set your `JAVA_HOME` and `ANDROID_HOME` environment variables if building from the command line:

- **Linux / macOS (`~/.bashrc` or `~/.zshrc`):**
  ```bash
  export JAVA_HOME=/path/to/jdk-17
  export ANDROID_HOME=$HOME/Android/Sdk
  export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
  ```

- **Windows (Command Prompt / PowerShell):**
  ```cmd
  setx JAVA_HOME "C:\Program Files\Java\jdk-17"
  setx ANDROID_HOME "C:\Users\%USERNAME%\AppData\Local\Android\Sdk"
  ```

### 3. Configuring `local.properties`

If Android Studio does not generate `local.properties` automatically, create a file named `local.properties` in the root directory of the project and specify your Android SDK location:

- **Windows:**
  ```properties
  sdk.dir=C\:\Users\YourUsername\AppData\Local\Android\Sdk
  ```

- **macOS:**
  ```properties
  sdk.dir=/Users/YourUsername/Library/Android/sdk
  ```

- **Linux:**
  ```properties
  sdk.dir=/home/yourusername/Android/Sdk
  ```

---

## 💻 Building from the Command Line (CLI)

You can build the project without opening an IDE using the provided Gradle Wrapper script (`gradlew` on Unix-like systems or `gradlew.bat` on Windows).

Make sure the wrapper script is executable (macOS/Linux):
```bash
chmod +x gradlew
```

### Debug Build

To compile and produce an unsigned Debug APK:

- **Linux / macOS:**
  ```bash
  ./gradlew assembleDebug
  ```

- **Windows:**
  ```cmd
  gradlew.bat assembleDebug
  ```

Upon completion, the output APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To install the debug build directly onto a connected device or running emulator:
```bash
./gradlew installDebug
```

### Release Build

To generate a Release APK:

- **Linux / macOS:**
  ```bash
  ./gradlew assembleRelease
  ```

- **Windows:**
  ```cmd
  gradlew.bat assembleRelease
  ```

The unaligned/unsigned (or signed, if configured) output will be available at:
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

### Clean and Lint

To clean previous build outputs and run code checks:

```bash
# Clean build artifacts
./gradlew clean

# Run Android Lint analysis
./gradlew lint

# Run unit tests (if configured)
./gradlew test
```

---

## 🎨 Building with Android Studio

1. **Launch Android Studio** (Koala / Ladybug or newer recommended).
2. Click **Open** and select the root directory of the project (`kingofwhitehartlane-stufoodapp`).
3. Allow Android Studio to initiate and complete the **Gradle Sync**.
4. Ensure the JDK is set to Java 17:
   - Go to **Settings / Preferences** → **Build, Execution, Deployment** → **Build Tools** → **Gradle**.
   - Verify **Gradle JDK** is set to **Embedded JDK (Java 17)** or a installed JDK 17.
5. Connect a physical Android device via USB (with **USB Debugging** enabled) or launch an **Android Virtual Device (AVD)** (API 26+).
6. Click the green **Run (▶)** button in the top toolbar or press `Shift + F10`.

---

## 🔑 Signing & Release Configuration

To sign your release APK for distribution, you can configure keystore credentials directly in `app/build.gradle.kts` or via environment variables / `gradle.properties`.

### 1. Generate a Keystore File

If you don't already have a signing key, generate one using `keytool`:

```bash
keytool -genkeypair -v   -keystore release.keystore   -alias stufood-key   -keyalg RSA   -keysize 2048   -validity 10000
```

Place `release.keystore` in a secure location (or inside the project root, keeping it excluded from version control via `.gitignore`).

### 2. Configure Gradle Signing Credentials

Add the following properties to your global or local `gradle.properties` file:

```properties
KEYSTORE_FILE=../release.keystore
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=stufood-key
KEY_PASSWORD=your_key_password
```

Update `app/build.gradle.kts` to apply signing configurations:

```kotlin
android {
    ...
    signingConfigs {
        create("release") {
            storeFile = file(project.properties["KEYSTORE_FILE"] as String)
            storePassword = project.properties["KEYSTORE_PASSWORD"] as String
            keyAlias = project.properties["KEY_ALIAS"] as String
            keyPassword = project.properties["KEY_PASSWORD"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Now running `./gradlew assembleRelease` will generate a fully signed, aligned release APK ready for production distribution.

---

## 🤖 CI/CD & GitHub Actions Workflow

The repository includes a GitHub Actions workflow defined in `.github/workflows/build.yml` to automatically build and verify code on every pull request or push to the main branch.

### Workflow Summary (`build.yml`):
- **OS Runner:** `ubuntu-latest`
- **JDK Setup:** `actions/setup-java@v4` with Java 17 (Temurin)
- **Gradle Caching:** Enabled automatically to speed up dependency downloading.
- **Commands Executed:**
  ```yaml
  - name: Build Debug APK
    run: ./gradlew assembleDebug --no-daemon
  ```

---

## 🚨 Troubleshooting & Common Issues

### Issue 1: `JAVA_HOME is not set` or Invalid Java Version
- **Cause:** Gradle cannot locate Java or is running on an unsupported Java version (e.g., Java 8, 11, or 21+ incompatibility).
- **Solution:** Verify your Java version using `java -version`. Ensure `JAVA_HOME` points to JDK 17. In Android Studio, check **File → Settings → Build Tools → Gradle → Gradle JDK**.

### Issue 2: `SDK location not found`
- **Cause:** Missing `local.properties` or unset `ANDROID_HOME` environment variable.
- **Solution:** Create `local.properties` in the project root with `sdk.dir=/path/to/android/sdk` as shown in the [Setup Section](#configuring-localproperties).

### Issue 3: `Network Security Exception` / Campus SSL Connection Failures
- **Cause:** The targeted portal (`stufood.mums.ac.ir`) utilizes university-specific SSL certificates or requires specific CA certificates.
- **Solution:** Custom CA certificates (`stufood_ca1.pem`, `stufood_ca2.pem`, `stufood_ca3.pem`) are declared under `app/src/main/res/raw/` and referenced in `network_security_config.xml`. Ensure cleartext/HTTPS settings match target campus server endpoints.

### Issue 4: ProGuard / R8 Obfuscation Issues
- **Cause:** Reflection-based JSON serializations or Jsoup HTML parsing rules being stripped during release builds (`isMinifyEnabled = true`).
- **Solution:** Add required preserve rules in `app/proguard-rules.pro` for OkHttp, Jsoup, DataStore, and Kotlin Serialization models:
  ```proguard
  -keepclassmembers class * {
      @kotlinx.serialization.Serializable <fields>;
  }
  ```

---

## 🏗 Architecture & Key Dependencies

Bananite is built using modern Android development practices and library stacks:

- **Language:** 100% Kotlin with Coroutines and Flow for reactive state management.
- **UI Framework:** Jetpack Compose with Material 3 components and dynamic dark/light theme support.
- **Networking & Scraping:**
  - **OkHttp 4.x:** Interceptor-based HTTP client, custom cookie handling via `InMemoryCookieJar` for sticky ASP.NET sessions.
  - **Jsoup:** High-performance HTML parsing for extracting WebForms inputs, dynamicViewState values, and tables.
- **Storage:** AndroidX DataStore (Preferences) for persistent user credentials and theme configuration.
- **Dependency Versioning:** Managed through Gradle Version Catalog (`gradle/libs.versions.toml`).

---

## 📄 License & Maintainers

Maintained by **kingofwhitehartlane**.  
*This project is an independent client and is not officially affiliated with or endorsed by MUMS.*
