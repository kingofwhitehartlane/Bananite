package ir.mums.stufood.data

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads this app's own log output from logcat. An app can only ever see its own
 * process's lines, so this needs no permission and nothing is stored on disk.
 *
 * Intentionally small: this is the hook the Releases & Issues log box builds on.
 */
object AppLogs {

    enum class Level(val label: String, val flag: String) {
        DEBUG("All", "D"),
        WARN("Warnings", "W"),
        ERROR("Errors", "E")
    }

    suspend fun read(level: Level, maxLines: Int = 400): String = withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder(
                "logcat", "-d", "-v", "time",
                "--pid=${Process.myPid()}",
                "*:${level.flag}"
            ).redirectErrorStream(true).start()

            val lines = proc.inputStream.bufferedReader().useLines { seq ->
                seq.filterNot { it.startsWith("--------- beginning of") }
                    .toList()
                    .takeLast(maxLines)
            }
            proc.destroy()
            lines.joinToString("\n")
        } catch (t: Throwable) {
            "Couldn't read logs: ${t.message}"
        }
    }
}
