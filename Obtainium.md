# 🍌 Setting up Obtainium for Bananite

**Obtainium** is a free, open-source app that installs and updates other apps straight from their source (here, this repository's GitHub Releases). Once it's set up, new Bananite versions show up in Obtainium automatically, with no need to open GitHub and download APKs by hand.

> [!TIP]
> This takes about 3 minutes and you only do it once.

---

## Why use it?

- **One-tap updates.** New releases appear in Obtainium; tap *Update* and you're done.
- **Background updates (optional).** Obtainium can check on its own and notify you, or even install updates automatically.
- **No Play Store account or Google services needed.** It reads releases directly from GitHub.
- **Same APK as GitHub.** Obtainium downloads the exact file published on the [Releases page](https://github.com/kingofwhitehartlane/Bananite/releases). Nothing is re-packaged.

Bananite's own update check only *tells you* a new version exists and opens your browser. The app itself never downloads or installs anything. Obtainium is the optional tool that handles that part for you.

---

## Before you start

| You need | Notes |
|---|---|
| Android 8.0 or newer | Same requirement as Bananite. |
| An internet connection | To reach GitHub. |
| Permission to install apps | Android will ask the first time (see Step 4). |

---

## Step 1: Install Obtainium

Get Obtainium from **one** of these official sources:

1. **GitHub Releases (recommended):** <https://github.com/ImranR98/Obtainium/releases>. Download the latest `.apk` (the `app-arm64-v8a-release.apk` file fits almost every modern phone; if unsure, pick `app-release.apk`).
2. **F-Droid / IzzyOnDroid:** search for *Obtainium* in your F-Droid client.

Open the downloaded file and install it. If Android says your browser or file manager isn't allowed to install apps, tap **Settings** and allow it just for this install.

---

## Step 2: Add Bananite

1. Open **Obtainium**.
2. Tap **Add App** (bottom navigation bar).
3. In the **App Source URL** field, paste:

   ```text
   https://github.com/kingofwhitehartlane/Bananite
   ```

4. Tap **Add**. Obtainium reads the repository, finds the latest release, and picks the APK named `Bananite-v<version>.apk`.

> [!NOTE]
> Obtainium also understands `obtainium://add/` links. If you prefer, you can paste `obtainium://add/https://github.com/kingofwhitehartlane/Bananite` into your phone's browser and it will open Obtainium with Bananite filled in. (GitHub itself won't make that link clickable, so copy it manually.)

---

## Step 3: Recommended options

Before (or right after) adding the app, open its **Additional Options** and consider these:

### ⚠️ Set a 1-day minimum age for updates (highly recommended)

Look for the option that delays updates until a release has been public for a while (the exact wording can differ between Obtainium versions) and set it to **1 day**.

**Why:** every push to `main` builds a release automatically. If a buggy build ever slips out, a one-day delay means it will usually be fixed or replaced before it reaches your phone.

### Other options

| Option | Suggestion |
|---|---|
| Include pre-releases | **Off.** You only want stable builds. |
| Filter APKs by regex | Not needed. Each release has a single APK. |
| Skip update notifications | Leave **off** so you know when a new version lands. |

---

## Step 4: Install Bananite

1. In the Obtainium **Apps** list, tap **Bananite**.
2. Tap **Install** (or **Update** if it's already installed).
3. The first time, Android asks whether to let **Obtainium** install apps. Tap **Settings → Allow from this source**, then go back and confirm the installation.
4. Open Bananite. Done. 🎉

### Already have Bananite installed from a downloaded APK?

Obtainium will detect it as installed. Because every official release is signed with the same key, updates install **on top** of your current copy and keep your settings and login.

> [!WARNING]
> If Android says the update *conflicts with an existing package*, your installed copy was signed with a different key (for example a local debug build). Uninstall it first and install through Obtainium. Your Bananite settings will reset, but nothing is stored on the server side.

---

## Step 5 (optional): Automatic background updates

1. Open **Obtainium → Settings**.
2. Enable **Background updates** (the check interval is configurable).
3. When Android asks, allow Obtainium to **run in the background / ignore battery optimisations**. Without this, some phones stop it from checking.

**Android 12 and newer:** if Obtainium itself installed Bananite, later updates can often install without an extra confirmation. If Bananite was originally installed from a browser download, the first update through Obtainium will still ask you to confirm, and after that it behaves normally.

---

## Troubleshooting

**"Rate limit exceeded" / GitHub errors**
GitHub limits anonymous requests. In Obtainium go to **Settings → Source-specific → GitHub** and add a personal access token. A token with **no scopes** (read-only public access) is enough.

**Obtainium can't find an APK**
Open the [Releases page](https://github.com/kingofwhitehartlane/Bananite/releases) and check the latest release has a `Bananite-v….apk` attached. A release can appear a few minutes before its APK finishes uploading. Try again shortly.

**Updates never show up in the background**
Your phone's battery saver is probably stopping Obtainium. Exclude it from battery optimisation (see Step 5). Some manufacturers also need *Autostart* enabled in system settings.

**Play Protect warns about the app**
Bananite is an independent, open-source app that isn't on the Play Store, so Play Protect may show a generic "unknown developer" notice. The full source is in this repository, and the APK comes straight from this repo's GitHub Releases.

**I want to go back to updating manually**
Remove Bananite from Obtainium (long-press it → remove) and choose to keep the app installed. Then download updates from the [Releases page](https://github.com/kingofwhitehartlane/Bananite/releases) as before.

---

## Found a problem with this guide?

Open an issue: <https://github.com/kingofwhitehartlane/Bananite/issues>
