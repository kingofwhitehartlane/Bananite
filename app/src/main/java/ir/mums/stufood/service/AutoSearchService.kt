package ir.mums.stufood.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that exists only while an auto-search session is running. It:
 *  - raises the process priority so the ViewModel's search loop keeps running with the screen off,
 *  - holds a partial wake lock (CPU stays on),
 *  - shows/updates the live notification and posts the final result.
 * The search loop itself stays in BuyFoodViewModel.
 */
class AutoSearchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val nm get() = getSystemService(NotificationManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LiveNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AutoSearchBridge.requestStopAndClose() // VM stops the loop, screen closes the app
            nm.cancel(LiveNotifications.RESULT_ID)
            nm.cancel(LiveNotifications.LIVE_ID)
            shutDown()
            return START_NOT_STICKY
        }
        if (job != null) return START_NOT_STICKY // already running

        // Must happen right away after startForegroundService().
        startInForeground(LiveNotifications.buildLive(this, AutoSearchBridge.snapshot.value))
        acquireWakeLock()

        job = scope.launch {
            NotificationDriver.drive(this@AutoSearchService, hosted = true)
            shutDown()
        }
        return START_NOT_STICKY // the loop lives in the ViewModel, so a restart couldn't resume it
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(LiveNotifications.LIVE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(LiveNotifications.LIVE_ID, n)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bananite:AutoSearch").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun shutDown() {
        job?.cancel(); job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "ir.mums.stufood.action.STOP_AUTO_SEARCH"
        private const val WAKE_LOCK_TIMEOUT_MS = 8 * 60 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AutoSearchService::class.java))
        }
    }
}
