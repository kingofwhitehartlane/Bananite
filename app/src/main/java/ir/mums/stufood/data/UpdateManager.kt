package ir.mums.stufood.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import ir.mums.stufood.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Checks GitHub Releases for a newer Bananite APK, downloads it, and hands it to the
 * system installer. Lives in [ir.mums.stufood.BananiteApp] with its own scope so a
 * download keeps going when the user leaves the Settings screen.
 */
class UpdateManager(private val context: Context, private val prefs: UserPrefs) {

    data class ReleaseInfo(
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long
    )

    sealed class State {
        object Idle : State()
        object Checking : State()
        object UpToDate : State()
        /** [auto] = triggered by the launch-time check (drives the popup dialogs). */
        data class Available(val release: ReleaseInfo, val auto: Boolean) : State()
        data class Downloading(val release: ReleaseInfo, val progress: Float, val auto: Boolean) : State()
        data class Downloaded(val release: ReleaseInfo, val file: File, val auto: Boolean) : State()
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
    private val updatesDir get() = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Called once per process from MainActivity. Does nothing if the toggle is off. */
    fun autoCheckOnLaunch() {
        if (autoChecked) return
        autoChecked = true
        scope.launch { if (prefs.autoUpdateEnabled.first()) check(auto = true) }
    }

    fun check(auto: Boolean = false) {
        val s = _state.value
        if (s is State.Checking || s is State.Downloading) return
        scope.launch {
            _state.value = State.Checking
            try {
                val release = fetchLatest()
                if (!isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                    updatesDir.listFiles()?.forEach { it.delete() } // leftovers from an old update
                    _state.value = State.UpToDate
                    return@launch
                }
                val cached = fileFor(release)
                when {
                    cached.exists() -> _state.value = State.Downloaded(release, cached, auto)
                    auto && !isMetered() -> download(release, auto = true)
                    else -> _state.value = State.Available(release, auto)
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

    fun startDownload(release: ReleaseInfo, auto: Boolean = false) {
        if (_state.value is State.Downloading) return
        scope.launch {
            try {
                download(release, auto)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("UpdateManager", "download failed", t)
                _state.value = State.Error("Download failed. Please try again.")
            }
        }
    }

    /** Opens the system installer (or the "allow installs" screen the first time). */
    fun install(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                context, "Allow Bananite to install apps, then tap Install again.", Toast.LENGTH_LONG
            ).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
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
            val assets = json.optJSONArray("assets") ?: throw IllegalStateException("No assets")
            var best: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    if (best == null || a.optString("name").startsWith("Bananite")) best = a
                }
            }
            val apk = best ?: throw IllegalStateException("Release has no APK")
            return ReleaseInfo(
                versionName = json.getString("tag_name").trim().removePrefix("v"),
                notes = json.optString("body").substringBefore("\n---").trim(),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size", 0L)
            )
        }
    }

    private suspend fun download(release: ReleaseInfo, auto: Boolean) {
        val dir = updatesDir
        dir.listFiles()?.forEach { it.delete() }
        val target = fileFor(release)
        val part = File(dir, target.name + ".part")
        _state.value = State.Downloading(release, 0f, auto)

        val req = Request.Builder().url(release.apkUrl)
            .header("User-Agent", "Bananite/${BuildConfig.VERSION_NAME}").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("Empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        coroutineContext.ensureActive()
                        val now = SystemClock.elapsedRealtime()
                        if (total > 0 && now - lastEmit > 100) {
                            lastEmit = now
                            _state.value = State.Downloading(release, (read.toFloat() / total).coerceIn(0f, 1f), auto)
                        }
                    }
                }
            }
        }

        val info = context.packageManager.getPackageArchiveInfo(part.path, 0)
        if (info?.packageName != context.packageName) {
            part.delete()
            throw IllegalStateException("Downloaded file isn't a Bananite APK")
        }
        part.renameTo(target)
        _state.value = State.Downloaded(release, target, auto)
    }

    private fun fileFor(release: ReleaseInfo) = File(updatesDir, "Bananite-v${release.versionName}.apk")

    private fun isMetered(): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true

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
}