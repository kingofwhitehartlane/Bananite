package ir.mums.stufood.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Every outbound link the app uses, in one place. */
object AppLinks {
    private const val REPO = "https://github.com/kingofwhitehartlane/Bananite"

    /** GitHub redirects this to the newest release page (notes + APK). */
    const val LATEST_RELEASE = "$REPO/releases/latest"
    const val ISSUES = "$REPO/issues"

    /** The Obtainium.md setup guide at the root of the repo. */
    const val OBTAINIUM_GUIDE = "$REPO/blob/main/Obtainium.md"

    fun open(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Log.e("AppLinks", "No browser available to open $url", e)
        }
    }
}
