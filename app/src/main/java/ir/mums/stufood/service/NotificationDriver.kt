package ir.mums.stufood.service

import android.app.NotificationManager
import android.content.Context
import ir.mums.stufood.service.AutoSearchBridge.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Draws the notifications of one auto-search session. Shared by the service and [InProcessNotifier]. */
internal object NotificationDriver {

    /** Returns when the session is over: stopped (IDLE) or finished with a result. */
    suspend fun drive(ctx: Context, hosted: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        coroutineScope {
            // Repaint once a second: moves the progress bar and the "next check in" text.
            val heartbeat = launch {
                while (true) {
                    delay(1_000)
                    val s = AutoSearchBridge.snapshot.value
                    if (s.phase.isOngoing) nm.notify(LiveNotifications.LIVE_ID, LiveNotifications.buildLive(ctx, s, hosted))
                }
            }
            // Important transitions are drawn immediately.
            var last: Phase? = null
            AutoSearchBridge.snapshot.first { s ->
                if (s.phase == last) return@first false
                last = s.phase
                when {
                    s.phase == Phase.IDLE -> true
                    s.phase.isTerminal -> {
                        nm.notify(LiveNotifications.RESULT_ID, LiveNotifications.buildResult(ctx, s))
                        true
                    }
                    else -> {
                        if (s.phase == Phase.BUYING) nm.notify(LiveNotifications.LIVE_ID, LiveNotifications.buildLive(ctx, s, hosted))
                        false
                    }
                }
            }
            heartbeat.cancel()
        }
    }
}

/**
 * Live notification WITHOUT the background service ("Live notifications" on, "Run in the background" off).
 * Lives and dies with the app process; the notification times out by itself if the process stops updating it.
 */
object InProcessNotifier {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    fun start(context: Context) {
        val app = context.applicationContext
        job?.cancel()
        LiveNotifications.ensureChannels(app)
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.notify(LiveNotifications.LIVE_ID, LiveNotifications.buildLive(app, AutoSearchBridge.snapshot.value, hosted = false))
        job = scope.launch {
            try {
                NotificationDriver.drive(app, hosted = false)
            } finally {
                nm.cancel(LiveNotifications.LIVE_ID)
            }
        }
    }
}
