package ir.mums.stufood.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Patch notes bundled into the APK. app/build.gradle.kts adds the repo's
 * `release-notes/` folder as an assets directory, so every `<major>.<minor>.md`
 * file there (1.0.md, 1.1.md, ...) shows up here automatically on the next build.
 */
object ReleaseNotes {

    data class Entry(val version: String, val lines: List<String>)

    private val fileName = Regex("""^(\d+)\.(\d+)\.md$""")

    suspend fun load(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        try {
            val assets = context.assets
            (assets.list("") ?: emptyArray())
                .mapNotNull { name ->
                    val m = fileName.matchEntire(name) ?: return@mapNotNull null
                    Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), name)
                }
                .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })
                .map { (major, minor, name) ->
                    val text = assets.open(name).bufferedReader().use { it.readText() }
                    Entry(
                        version = "$major.$minor",
                        lines = text.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") } // the title is shown by the UI
                            .map { it.removePrefix("- ").removePrefix("* ") }
                    )
                }
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
