# Bananite — Build & Development Guide

How to clone, build, sign and release **Bananite**, a native Android app written in Kotlin with Jetpack Compose, Material 3, OkHttp and Jsoup.

This guide is for contributors and anyone who wants to build the app from source. If you only want to *use* the app, grab an APK from the [Releases page](https://github.com/kingofwhitehartlane/Bananite/releases) (see the main [README](README.md)).

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project structure](#project-structure)
3. [Local setup](#local-setup)
4. [Building from the command line](#building-from-the-command-line)
5. [Building with Android Studio](#building-with-android-studio)
6. [Signing](#signing)
7. [Versioning & releases](#versioning--releases)
8. [CI/CD workflows](#cicd-workflows)
9. [How the app works](#how-the-app-works)
10. [Troubleshooting](#troubleshooting)
11. [License](#license)

---

## Prerequisites

| Tool | Version | Notes |
| :--- | :--- | :--- |
| **JDK** | 17 | Eclipse Temurin 17 / OpenJDK 17. Gradle and Kotlin both target Java 17. |
| **Android SDK** | API 35 (`compileSdk` / `targetSdk`) | Build Tools 35.x. The app runs on Android 8.0+ (`minSdk` 26). |
| **Gradle** | 8.10.2 | Provided by the Gradle Wrapper (`./gradlew`); no separate install needed. |
| **Android Studio** | Ladybug (2024.2) or newer | Optional. Command-line builds work without it. |

Versions of everything else (AGP, Kotlin, Compose BOM, OkHttp, …) live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

---

## Project structure

```
Bananite/
├── .github/workflows/
│   └── build.yml                 # Builds, signs and publishes a release on every push to main
├── app/
│   ├── build.gradle.kts          # App module: SDK levels, signing, dependencies
│   ├── proguard-rules.pro        # R8 rules (R8 is currently disabled)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/ir/mums/stufood/
│       │   ├── BananiteApp.kt    # Application: holds the shared repository, cookie jar, prefs
│       │   ├── MainActivity.kt   # Single activity; screen switching + update dialog
│       │   ├── data/             # StufoodRepository (HTTP + HTML parsing), cookie jar,
│       │   │                     #   UserPrefs, UpdateManager, ReleaseNotes, AppLogs
│       │   ├── ui/
│       │   │   ├── components/   # Reusable composables (date picker, haptics, cards, …)
│       │   │   ├── navigation/   # Screen.kt — the list of screens
│       │   │   ├── screens/      # One *Screen + *ViewModel pair per feature
│       │   │   └── theme/        # Colors, typography (fonts), Material 3 theme
│       │   └── util/             # JalaliCalendar, AppLinks, Obtainium helpers
│       └── res/
│           ├── font/             # Latin + Persian fonts
│           ├── raw/              # Bundled TLS certificates for stufood.mums.ac.ir
│           └── xml/              # network_security_config, backup rules
├── release-notes/                # <major>.<minor>.md — bundled into the APK AND used as GitHub release body
├── gradle/
│   ├── libs.versions.toml        # Version catalog
│   └── wrapper/                  # Gradle Wrapper
├── version.properties            # majorMinor=1.3 (the patch number is added by CI)
├── Obtainium.md                  # End-user guide for auto-updates via Obtainium
├── build.gradle.kts              # Root build script (plugin versions)
└── settings.gradle.kts
```

---

## Local setup

```bash
git clone https://github.com/kingofwhitehartlane/Bananite.git
cd Bananite
```

> **Tip:** avoid paths with spaces or non-ASCII characters (especially on Windows); `aapt2` can fail to resolve them.

Point Gradle at your Android SDK — either set `ANDROID_HOME`, or create a `local.properties` file in the project root (Android Studio normally does this for you; the file is git-ignored):

```properties
# Linux
sdk.dir=/home/<you>/Android/Sdk
# macOS
sdk.dir=/Users/<you>/Library/Android/sdk
# Windows
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Make sure `JAVA_HOME` points at a JDK 17 (`java -version` should say 17).

---

## Building from the command line

On macOS/Linux make the wrapper executable once: `chmod +x gradlew`. On Windows use `gradlew.bat` instead of `./gradlew`.

| Goal | Command | Output |
| :--- | :--- | :--- |
| Debug APK | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` (signed with the debug key) |
| Install on a device/emulator | `./gradlew installDebug` | — |
| Release APK | `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` |
| Clean | `./gradlew clean` | — |
| Lint | `./gradlew lint` | `app/build/reports/lint-results-*.html` |

A local release build **without** a keystore is signed with the debug key so you can still test it. Such a build cannot be installed over an official release (different signing key).

Local builds are versioned `<majorMinor>.0-dev` with version code `1`. To override: `./gradlew assembleRelease -PappVersionName=1.3.99 -PappVersionCode=99`.

---

## Building with Android Studio

1. Open the project root and let Gradle sync finish.
2. Check **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** is a JDK 17 (the embedded JDK is fine).
3. Pick a device (USB debugging on, or an emulator running API 26+) and press **Run ▶**.

---

## Signing

Release signing is configured in `app/build.gradle.kts` and reads **environment variables** — nothing is stored in the repository:

| Variable | Meaning |
| :--- | :--- |
| `KEYSTORE_FILE` | Path to the keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Password of that key |

Behaviour of `assembleRelease`:

* **Keystore found** → signed with it.
* **No keystore, local machine** → falls back to the debug key.
* **No keystore, and `CI=true`** → the build **fails on purpose**, so CI can never publish a debug-signed release.

Create a keystore (keep it, and its passwords, safe — losing it means users can't update in place):

```bash
keytool -genkeypair -v -keystore release.keystore -alias bananite-key \
        -keyalg RSA -keysize 2048 -validity 10000
```

Use it locally:

```bash
export KEYSTORE_FILE="$PWD/release.keystore"
export KEYSTORE_PASSWORD='...'
export KEY_ALIAS=bananite-key
export KEY_PASSWORD='...'
./gradlew assembleRelease
```

Keep `*.keystore` / `*.jks` out of git (add them to `.gitignore`). **Never commit a keystore.**

---

## Versioning & releases

* `version.properties` holds only `majorMinor` (e.g. `1.3`). Bump it by hand for a new feature line.
* CI picks the **patch** number automatically: the highest existing tag `v<majorMinor>.N` plus one (starting at `0`). The result is the `versionName`, e.g. `1.3.17`.
* The Android `versionCode` is the GitHub Actions **run number**. It only ever increases, but it would reset if the workflow were deleted and recreated — Android refuses to install a lower `versionCode` over a higher one, so don't do that.
* Release notes: put them in `release-notes/<majorMinor>.md` (one bullet per line, `- …`). That file is
  1. bundled into the APK (shown in the app's *Releases & Issues* screen), and
  2. used as the body of the GitHub release.

  Keep `release-notes/<major>.<minor>.md` as the exact file name — the app only recognises that pattern.

To cut a release: merge to `main`. The workflow does the rest.

---

## CI/CD workflows

### `build.yml` — release (push to `main`, or manual run)

1. Checks out the repo with full history and tags.
2. Sets up JDK 17 and a Gradle cache.
3. Reads `majorMinor`, computes the next patch number, composes `versionName` / `versionCode` / tag.
4. Decodes the signing keystore from the `KEYSTORE_BASE64` secret into `release.keystore`.
5. Runs `./gradlew assembleRelease` with the version properties.
6. Renames the APK to `Bananite-v<version>.apk` (the name Obtainium looks for).
7. Creates the git tag and GitHub release with the notes and the APK.

Every push to `main` publishes a release, including docs-only pushes. Avoid pushing two commits in quick succession: the patch number is computed from existing tags, so two overlapping runs could pick the same number.

**Repository secrets required:** `KEYSTORE_BASE64` (`base64 -w0 release.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

---

## How the app works

* **Networking** — `StufoodRepository` talks to `https://stufood.mums.ac.ir` with OkHttp. The site is an ASP.NET WebForms app, so the repository downloads each page, parses it with Jsoup, and replays the *whole* form (ViewState, EventValidation, every field) on each action, the way a browser would. Read the long comment at the top of `StufoodRepository.kt` before changing anything there.
* **Session** — `InMemoryCookieJar` keeps the session cookies in memory only; killing the app logs you out. `StufoodRepository.isLoggedIn()` is true only after a successful login.
* **Credentials** — saved (if "remember me" is ticked) in `EncryptedSharedPreferences`, backed by the Android Keystore. UI settings live in a Preferences DataStore. Neither is included in backups (`backup_rules.xml`, `data_extraction_rules.xml`).
* **Updates** — `UpdateManager` asks the GitHub API for the latest release and offers to open it in the browser or Obtainium. The app never downloads or installs APKs itself.
* **Navigation** — a plain `Screen` list switched in `MainActivity`; the current screen survives rotation (`rememberSaveable`).
* **Dates** — the site uses the Jalali (Solar Hijri) calendar; `JalaliCalendar` converts with pure arithmetic and no extra dependency.

### Adding a screen

1. Add an `object` to `ui/navigation/Screen.kt` **and** list it in `Screen.all`.
2. Create `FooScreen.kt` (+ `FooViewModel.kt` if it has state).
3. Add a branch for it in the `when` in `MainActivity.App()` and, if needed, in the back-button handler.

---

## Troubleshooting

**`JAVA_HOME is not set` / wrong Java version** — run `java -version`; it must be 17. In Android Studio check the *Gradle JDK* setting.

**`SDK location not found`** — create `local.properties` with `sdk.dir=…` or set `ANDROID_HOME`.

**`Could not find or load main class org.gradle.wrapper.GradleWrapperMain`** — `gradle/wrapper/gradle-wrapper.jar` is missing. Regenerate it with a local Gradle install: `gradle wrapper --gradle-version 8.10.2`, then commit the jar.

**TLS / certificate errors when talking to the university server** — the bundled certificates in `app/src/main/res/raw/` (`stufood_ca1..3.pem`) are trusted in addition to the system store, via `network_security_config.xml`. `stufood_ca1` is the server's own leaf certificate and expires on **2027-02-24**; `ca2`/`ca3` are the Certum intermediate and root.

**`CI RELEASE BUILD ABORTED: Valid keystore not found`** — one of the signing secrets is missing or `KEYSTORE_BASE64` didn't decode to a valid keystore.

**Update says "conflicts with an existing package"** — the installed copy was signed with a different key (for example a local debug build). Uninstall it first.

**The site changed and the app shows empty/odd pages** — the app parses server-rendered HTML, so layout changes can break it. Capture a HAR of the working flow in a browser, compare field names against `StufoodRepository.kt`, and open an issue. (Don't attach the HAR publicly — it contains your session cookies.)

---

## License

Bananite is released under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

Maintained by **kingofwhitehartlane**. This is an independent project, not affiliated with or endorsed by Mashhad University of Medical Sciences.