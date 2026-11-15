package ir.mums.stufood.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object Obtainium {
    private val PACKAGES = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

    private fun installedPackage(context: Context): String? = PACKAGES.firstOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isInstalled(context: Context) = installedPackage(context) != null

    /** Returns false if Obtainium isn't installed or can't be launched. */
    fun launch(context: Context): Boolean {
        val pkg = installedPackage(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}