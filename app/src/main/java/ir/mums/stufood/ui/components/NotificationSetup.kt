package ir.mums.stufood.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

// ---------------------------------------------------------------------------------------------
// Status checks
// ---------------------------------------------------------------------------------------------

fun hasNotificationPermission(c: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun isBatteryUnrestricted(c: Context): Boolean =
    c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

/** Live Updates (status chip / Now Bar / Super Island) exist from Android 16. */
fun liveUpdatesSupported(): Boolean = Build.VERSION.SDK_INT >= 36

fun liveUpdatesAllowed(c: Context): Boolean =
    !liveUpdatesSupported() || c.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

/** Short extra hint for phone brands that kill background apps aggressively; null elsewhere. */
fun oemBackgroundTip(): String? {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        "xiaomi" in m || "redmi" in m || "poco" in m ->
            "Xiaomi / Redmi / POCO: also turn on Autostart for Bananite and set Battery saver to \"No restrictions\"."
        "samsung" in m ->
            "Samsung: also add Bananite to Settings > Battery > Background usage limits > Never sleeping apps."
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// Permission flow
// ---------------------------------------------------------------------------------------------

/**
 * One place that knows how to ask for everything the two Notifications toggles need:
 *  - Android 13+ notification permission,
 *  - the battery "Unrestricted" exemption (background toggle),
 *  - the Android 16 Live Updates switch (live toggle).
 * Steps run one after another; every step is optional, so denying one never blocks the rest.
 */
@Stable
class NotificationSetup internal constructor(
    val notificationsGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val liveUpdatesSupported: Boolean,
    val liveUpdatesAllowed: Boolean,
    private val runner: (Boolean, Boolean, () -> Unit) -> Unit
) {
    /** Asks for whatever is still missing for the given features, then calls [onDone]. */
    fun run(background: Boolean, live: Boolean, onDone: () -> Unit = {}) = runner(background, live, onDone)
}

@Composable
fun rememberNotificationSetup(): NotificationSetup {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val queue = remember { ArrayDeque<() -> Unit>() }
    val holder = remember { object { var onDone: () -> Unit = {}; var next: () -> Unit = {} } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++; holder.next()
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++; holder.next()
    }
    holder.next = {
        val step = queue.removeFirstOrNull()
        if (step != null) step() else holder.onDone()
    }

    fun launchSettings(primary: Intent, fallback: Intent) {
        try {
            settingsLauncher.launch(primary)
        } catch (_: ActivityNotFoundException) {
            try { settingsLauncher.launch(fallback) } catch (_: Exception) { holder.next() }
        } catch (_: SecurityException) {
            holder.next()
        }
    }

    val pkg = context.packageName
    val runner: (Boolean, Boolean, () -> Unit) -> Unit = { background, live, onDone ->
        queue.clear()
        holder.onDone = onDone
        if ((background || live) && !hasNotificationPermission(context)) {
            queue.addLast { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        if (background && !isBatteryUnrestricted(context)) {
            queue.addLast {
                launchSettings(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            }
        }
        if (live && liveUpdatesSupported() && !liveUpdatesAllowed(context)) {
            queue.addLast {
                launchSettings(
                    // = Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS (API 36), written as a string so it can never fail to resolve
                    Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                )
            }
        }
        holder.next()
    }

    // Re-read the statuses whenever a system screen / dialog returns.
    return remember(tick) {
        NotificationSetup(
            notificationsGranted = hasNotificationPermission(context),
            batteryUnrestricted = isBatteryUnrestricted(context),
            liveUpdatesSupported = liveUpdatesSupported(),
            liveUpdatesAllowed = liveUpdatesAllowed(context),
            runner = runner
        )
    }
}

// ---------------------------------------------------------------------------------------------
// First-time popup (shown when auto-search is switched on for the first time)
// ---------------------------------------------------------------------------------------------

@Composable
fun AutoSearchSetupDialog(
    onEnableNow: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit,
    onNeverAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Keep auto-search running?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bananite can keep searching while the screen is off or you're in another app, and show a " +
                        "live status with a timer and a Stop button on your lock screen (and in the Now Bar / " +
                        "Super Island on supported phones).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Both are off by default. You can change them any time in Settings > Notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onEnableNow, modifier = Modifier.fillMaxWidth()) { Text("Enable now") }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Go to settings") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onLater) { Text("Later") }
                    TextButton(onClick = onNeverAgain) { Text("Don't show again") }
                }
            }
        },
        confirmButton = {}
    )
}