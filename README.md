# StuFood — Android app

A native Android app that replaces the Selenium Python script for reserving food on
`stufood.mums.ac.ir`. Built with Kotlin + Jetpack Compose + Material 3 + OkHttp + Jsoup.

This README walks you through every step from "I just installed Windows" to "the app
is running on my phone." You don't need any prior Android experience.

---

## 0. What you need (one-time setup, ~30 min)

| Thing | Where to get it | Notes |
|---|---|---|
| **Android Studio** | https://developer.android.com/studio | Latest stable (Koala / Ladybug or newer). Bundles the JDK, the Android SDK, the emulator, and Gradle. You do **not** need to install Java separately. |
| **A phone running Android 8.0+** | your phone | Or use the emulator (slower, can't reach your campus wifi). |
| **A USB cable** | your phone's charger | For first-time setup. After that you can go wireless. |
| **Campus wifi / VPN on the phone** | same one your PC uses | The app talks directly to `stufood.mums.ac.ir` — the phone has to be able to resolve that host, same as your PC. |

---

## 1. Install Android Studio

1. Download the installer from https://developer.android.com/studio
2. Run it. Click **Next** through every screen — leave all checkboxes at their defaults
   (Android SDK, Android Virtual Device, etc.).
3. The first launch will show a "Setup Wizard" that downloads the SDK components.
   Let it finish. This can take 10–20 minutes depending on your connection.
4. When you land on the **Welcome to Android Studio** screen, you're done with setup.

> If it complains about `JAVA_HOME` or `JDK not found`: Android Studio comes with a
> bundled JDK. In Android Studio, go to **File → Settings → Build, Execution,
> Deployment → Build Tools → Gradle** and make sure **Gradle JDK** is set to
> "Embedded JDK". Don't install a separate Java.

---

## 2. Open this project

1. Unzip `StuFoodApp.zip` somewhere with no spaces or non-ASCII chars in the path —
   e.g. `C:\Users\YourName\Projects\StuFoodApp`. (Avoid `C:\Program Files\` and
   `C:\Users\Your Name\`.)
2. In Android Studio: **File → Open…** → pick the `StuFoodApp` folder (the one that
   contains `settings.gradle.kts`, not its parent).
3. Android Studio will start a **Gradle sync**. You'll see a progress bar at the
   bottom right. Wait for it to finish — it downloads OkHttp, Jsoup, Compose, etc.

If sync fails with `SDK location not found`:
- Open **File → Project Structure → SDK Location** and let it auto-detect, or
- Open `local.properties` (in the project root) and add a line like
  `sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk`

If sync fails with some other error, copy the full error text and paste it back to me.

---

## 3. Enable USB debugging on your phone

This is a one-time setting. Once enabled, you can plug the phone into your PC and
Android Studio will see it as a "run target."

1. On your phone: **Settings → About phone**
2. Tap **Build number** 7 times. You'll see "You are now a developer!"
3. Go back to **Settings → System → Developer options** (sometimes under
   **Settings → Additional settings** on some phones).
4. Turn on **USB debugging**.
5. Plug the phone into your PC with a USB cable.
6. A popup will appear on the phone asking to "Allow USB debugging?" — tap **Allow**
   (and tick "Always allow from this computer").

To verify: in Android Studio, look at the device dropdown at the top of the window.
Your phone model should show up there.

> **Emulator alternative:** If you don't have a cable, you can use the emulator
> (**Tools → Device Manager → Create Device**). But the emulator can't reach your
> campus wifi — it uses your PC's network, which works if your PC is on the VPN. Pick
> a small phone like the Pixel 7 for fast boot.

---

## 4. Run the app

1. Select your phone in the device dropdown at the top of Android Studio.
2. Click the green **▶ Run** button (or press **Shift+F10**).
3. Android Studio builds the APK, installs it on your phone, and launches it.
4. The first build takes 1–3 minutes (it's compiling Compose). Subsequent builds are
   much faster.

The app icon will appear in your phone's app drawer labeled **StuFood**. You can
launch it like any other app from now on — no need to keep it plugged into the PC.

---

## 5. Using the app

### Login screen
- Student ID and password — same as the Python script.
- **Captcha image** is fetched live from the site. Type the answer into the field.
  If you can't read it, tap the **↻** button to get a fresh one.
- **Remember me** — saves your ID + password on the device so next time you only have
  to solve the captcha. Untick to clear.
- Tap **Log in**. If login fails (wrong captcha, wrong password, etc.) you'll see a
  snackbar at the bottom and a fresh captcha will load automatically.

### Home screen
- **Reserve Food** — go to the reservation screen.
- **Logout** — clears the session cookies and goes back to login.

### Reservation screen
- The page automatically loads the current state from the site.
- **Meal** dropdown — defaults to ناهار (lunch).
- **Cafeteria** dropdown — defaults to سلف پردیس (campus self-service).
- **Reserve week** button — runs the full script: select meal → next week → for each
  day, pick cafeteria + confirm first radio. Status text shows what step we're on.
- Below the action card, the parsed page state is shown (so you can see what the site
  actually returned — useful for debugging if something changes).

---

## 6. Common issues

| Symptom | Fix |
|---|---|
| `Build failed: SDK not configured` | **File → Project Structure → SDK Location → Android SDK Location**. Set it to e.g. `C:\Users\You\AppData\Local\Android\Sdk`. |
| `Could not resolve com.android.tools.build:gradle` | Check your internet. Android Studio downloads the AGP plugin on first sync. |
| App opens but says "Network error" | Your phone is not on the campus wifi/VPN. Connect and try again. |
| Login fails with "captcha" error | Captchas are single-use. Tap ↻ to get a new one before retrying. |
| "Day X not found" on reservation | The site's HTML changed (new cafeteria, new diet, etc.). Send me a screenshot of the page and I'll update the parser. |
| App crashes on launch | **Run → Run** again with the phone plugged in. Android Studio will show the stacktrace in the **Logcat** tab at the bottom. Copy and send. |

---

## 7. Project layout (for when you want to tinker)

```
StuFoodApp/
├── settings.gradle.kts           ← project-level config
├── build.gradle.kts              ← project-level config
├── gradle/libs.versions.toml     ← all dependency versions live here
├── gradle.properties             ← JVM / AndroidX flags
└── app/
    ├── build.gradle.kts          ← app-level config (minSdk, dependencies)
    └── src/main/
        ├── AndroidManifest.xml   ← app permissions + launcher activity
        ├── res/                  ← strings, themes, launcher icon
        └── java/ir/mums/stufood/
            ├── StufoodApp.kt     ← Application class (singletons)
            ├── MainActivity.kt   ← Compose host + navigation
            ├── data/
            │   ├── InMemoryCookieJar.kt   ← keeps ASP.NET session cookies
            │   ├── StufoodRepository.kt   ← HTTP + Jsoup (the script, in Kotlin)
            │   └── UserPrefs.kt           ← saved credentials (DataStore)
            └── ui/
                ├── theme/         ← Material 3 colors, type, theme
                ├── navigation/    ← Screen sealed class
                └── screens/       ← Login / Home / Reservation + ViewModels
```

The file you'll edit most often is **`StufoodRepository.kt`** — that's where every
"talk to the site" function lives. Each new menu page you add later becomes one new
function in that file plus one new screen.

---

## 8. Adding a new menu page later

When you want to add another page from the site (profile, history, dorm stuff, etc.):

1. In `StufoodRepository.kt`: add a `suspend fun fetchXxxPage()` and (if it submits
   something) a `suspend fun submitXxx(...)`. Follow the same pattern as
   `fetchReservationPage()` / `selectMeal()` — GET the page, Jsoup-parse it into a
   data class, POST with the parsed hidden fields.
2. In `ui/navigation/Screen.kt`: add a new entry to the sealed class.
3. In `ui/screens/`: copy `ReservationScreen.kt` + `ReservationViewModel.kt` as a
   template, rename, swap the repo calls.
4. In `MainActivity.kt`: add a new branch to the `when (screen)` block.
5. In `HomeScreen.kt`: add a new `HomeMenuCard` button.

That's it — the architecture is intentionally repetitive so each new screen looks the
same as the last.

---

## 9. Building a release APK (to share with friends)

1. In Android Studio: **Build → Generate Signed App Bundle / APK → APK**.
2. Click **Create new…** to make a keystore (save it somewhere safe — you'll need the
   same keystore for every future update).
3. Fill in the keystore password, alias, alias password, and your name.
4. Pick **release**, click **Finish**.
5. The APK will be at `app/build/outputs/apk/release/app-release.apk`.

To install a release APK on your phone: copy it to the phone, tap it in the file
manager, allow "install from unknown sources" when prompted.

(Debug APKs work too but require `adb install` from the command line — easier to just
hit Run from Android Studio.)

---

## 10. Where the captcha AI solver went

Your Python script used OpenRouter + Gemma to auto-solve the captcha. We deliberately
removed that from the Android app — the captcha is now displayed in the UI and you
type the answer yourself, exactly like a normal login form.

If you want auto-solving back later, the cleanest place to add it is in
`LoginViewModel.submit()` — between `repo.fetchLoginPage()` and `repo.login(...)`,
intercept the captcha image bytes, send them to your AI solver of choice (OpenAI
Vision, Gemini, OpenRouter…), and pass the returned text as the `captcha` argument to
`repo.login()`. Everything else stays the same.
