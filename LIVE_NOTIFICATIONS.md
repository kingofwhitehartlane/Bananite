# 🔔 Live Notifications & Background Running

Everything here lives in the app under **Settings → Notifications → Buy food**. All switches are **off by default**, and Bananite always sends a normal notification when a food is found or a purchase finishes, whatever you choose.

| Setting | What it does |
|---|---|
| **Run in the background** | Keeps auto-search running with the screen off or while you use another app. A small notification (timer + **Stop & close** button) shows while it runs. |
| **Live notifications** | Turns that notification into an Android 16 *Live Update*: animated progress, timer in the status bar, lock screen, and the Samsung Now Bar. |
| **Food found sound** | Plays your phone's notification sound when a new food shows up. Turning it off mutes only this sound. |

---

## ❓ Why are Live notifications Android 16+ only?

Live notifications use Android 16's **Live Updates** API (a progress-style notification that the system can "promote" to the status bar, lock screen and the phone maker's live island). **Android 15 and older have no such API for third-party apps**, so there is nothing for Bananite to call. Samsung's Now Bar on One UI 7 (Android 15) only worked for Samsung's own apps and a few partners through private APIs.

On older phones the switch is greyed out. You can still use **Run in the background**: you get a normal ongoing notification with a timer and a Stop button.

## 🏭 Phone brands that lock third-party apps out

Some makers built their own live island and don't let ordinary apps use it. On these phones the Live notifications switch is greyed out, with the reason shown under it.

| Group | Phones | Why |
|---|---|---|
| Partner-portal only | Xiaomi / Redmi / POCO (HyperOS 3+), Huawei (HarmonyOS), legacy ColorOS 15 | Proprietary JSON/Intent APIs, and the maker has to approve each app through a partner portal. |
| System apps only | Honor (Magic Capsule), Infinix (Magic Ring), TECNO (Dynamic Port), itel (Dynamic Bar), and phones stuck on Android 14/15 | Closed to third-party apps. |

Nothing is broken on these phones: **Run in the background**, the normal "food found" notification and the sound switch all work.

## 📱 Samsung: turn on "Live notifications for all apps"

Samsung's Now Bar (One UI 8, Android 16) can show Live Updates from any app, but on many builds the switch for that is hidden in **Developer options**. If Bananite's live notification doesn't show up in the Now Bar or status bar, do this once:

**A. Turn on Developer options**
1. Open **Settings → About phone → Software information**.
2. Tap **Build number** 7 times in a row.
3. Enter your PIN / pattern / password. You'll see *"Developer mode has been turned on"*.
4. Go back to the main **Settings** list and scroll to the very bottom: **Developer options** is now there.

**B. Turn on the switch**
1. Open **Settings → Developer options**.
2. Scroll **all the way down** the (long) list.
3. Switch on **Live notifications for all apps**.

**C. In Bananite**
1. Open **Settings → Notifications → Buy food** and turn on **Live notifications**, then allow the permissions it asks for.
2. In your phone's **Settings → Notifications**, make sure Live notifications / Now Bar is on and Bananite is allowed (wording differs a little between One UI versions).
3. Start auto-search in **Buy food**, then lock the phone. The timer shows on the lock screen, in the status bar and in the Now Bar.

> One UI 8.5 is reported to add a regular Live notifications setting. If you already see one under **Settings → Notifications**, use that instead of Developer options.

## 🛠️ If searches stop with the screen off

- In Bananite, **Settings → Notifications → Run in the background** must be on, and the battery line under it must not say "restricted". Tap **Fix** to allow it.
- Samsung: **Settings → Apps → Bananite → Battery → Unrestricted**.
- Xiaomi / Redmi / POCO: **Settings → Apps → Manage apps → Bananite → Battery saver → No restrictions**.

Menu names differ slightly between versions. The point is the same everywhere: set Bananite's battery use to **Unrestricted / No restrictions**.
