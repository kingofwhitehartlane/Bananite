package ir.mums.stufood.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import ir.mums.stufood.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer Bananite version and points the user at the
 * release in their browser.
 *
 * The app NEVER downloads or installs APKs itself — that's left entirely to the
 * browser and the system. No REQUEST_INSTALL_PACKAGES permission, no FileProvider,
 * no cached APKs (Play Protect flags in-app APK download+install as dropper behavior).
 */
class UpdateManager(private val context: Context, private val prefs: UserPrefs) {

    data class ReleaseInfo(
        val versionName: String,
        val notes: String,
        /** GitHub page for this release (notes + assets). */
        val pageUrl: String,
        /** Direct browser_download_url of the APK asset — opened in the browser. */
        val apkUrl: String
    )

    sealed class State {
        object Idle : State()
        object Checking : State()
        object UpToDate : State()
        /** [auto] = triggered by the launch-time check (drives the popup dialog). */
        data class Available(val release: ReleaseInfo, val auto: Boolean) : State()
        data class Error(val message: String) : State()
    }

    private val owner = "kingofwhitehartlane"
    private val repo = "Bananite"

    // Separate client: no stufood cookies, no stufood user-agent.
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var autoChecked = false

    init {
        // Clean up any APKs cached by older versions of the app (pre-browser updater).
        scope.launch { runCatching { File(context.cacheDir, "updates").deleteRecursively() } }
    }

    /** Called once per process from MainActivity. Does nothing if the toggle is off. */
    fun autoCheckOnLaunch() {
        if (autoChecked) return
        autoChecked = true
        scope.launch { if (prefs.autoUpdateEnabled.first()) check(auto = true) }
    }

    fun check(auto: Boolean = false) {
        if (_state.value is State.Checking) return
        scope.launch {
            _state.value = State.Checking
            try {
                val release = fetchLatest()
                _state.value = if (isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                    State.Available(release, auto)
                } else {
                    State.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("UpdateManager", "check failed", t)
                // A failed background check stays quiet; a manual one shows an error.
                _state.value = if (auto) State.Idle
                else State.Error("Couldn't check for updates. Check your connection and try again.")
            }
        }
    }

    /** Opens the direct APK download link in the user's browser. */
    fun openDownload(release: ReleaseInfo) =
        openInBrowser(release.apkUrl.ifBlank { release.pageUrl })

    /** Opens the release page (notes + all assets) in the browser. */
    fun openReleasePage(release: ReleaseInfo) = openInBrowser(release.pageUrl)

    private fun openInBrowser(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Log.e("UpdateManager", "No browser available to open $url", e)
        }
    }

    // ------------------------------------------------------------------

    private fun fetchLatest(): ReleaseInfo {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Bananite/${BuildConfig.VERSION_NAME}")
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("GitHub HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())

            var best: JSONObject? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        if (best == null || a.optString("name").startsWith("Bananite")) best = a
                    }
                }
            }

            return ReleaseInfo(
                versionName = json.getString("tag_name").trim().removePrefix("v"),
                notes = json.optString("body").substringBefore("\n---").trim(),
                pageUrl = json.optString("html_url")
                    .ifBlank { "https://github.com/$owner/$repo/releases/latest" },
                apkUrl = best?.optString("browser_download_url").orEmpty()
            )
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.')
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    companion object {
        private val bulletPrefixRegex = Regex("""^([-*•+]|\d+[.)])\s+""")

        fun extractUpdateHighlights(releaseNotes: String, knownLines: Set<String> = emptySet()): List<String> {
            val cleanedLines = releaseNotes
                .replace("\r\n", "\n")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.replace(bulletPrefixRegex, "").trim() }
                .filter { it.isNotEmpty() }

            if (cleanedLines.isEmpty()) return emptyList()

            val normalizedKnown = knownLines.map { it.trim() }.toSet()
            val diffLines = cleanedLines.filter { it !in normalizedKnown }
            return if (diffLines.isNotEmpty()) diffLines else listOf(cleanedLines.last())
        }
    }
}