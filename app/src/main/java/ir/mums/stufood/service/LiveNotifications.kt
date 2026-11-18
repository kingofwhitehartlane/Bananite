package ir.mums.stufood.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.text.BidiFormatter
import ir.mums.stufood.R
import ir.mums.stufood.service.AutoSearchBridge.Phase
import ir.mums.stufood.service.AutoSearchBridge.Snapshot
import java.util.Locale

/**
 * Builds the notifications. The live one is an ongoing ProgressStyle notification that
 * requests Android 16 "promoted" treatment (status-bar chip, lock screen, Samsung Now Bar,
 * Xiaomi Super Island). Below Android 16 it is a normal ongoing notification.
 *
 * Promotion rules: ongoing + title + standard/Progress style + no custom RemoteViews + not colorized.
 */
object LiveNotifications {

    const val CH_LIVE = "auto_search_live"
    const val CH_RESULT = "auto_search_result"
    const val LIVE_ID = 4101
    const val RESULT_ID = 4102

    private const val SEG = 100 // three segments: Search | Buy | Done  (total 300)
    private val BLUE = 0xFF1E88E5.toInt()
    private val AMBER = 0xFFFFB300.toInt()
    private val GREEN = 0xFF43A047.toInt()
    private val RED = 0xFFE53935.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Auto-search (live)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live status of the running auto-search"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Auto-search result", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Food found / purchase result"
                setSound(null, null) // the app plays its own sound (the in-app bell toggle)
                enableVibration(true)
            }
        )
    }

    /** An ongoing notification not tied to a foreground service vanishes by itself if updates stop (process died). */
    private const val ORPHAN_TIMEOUT_MS = 15_000L

    /**
     * [hosted] = shown by the foreground service. When the app process itself posts it
     * (Live on, Run-in-background off) it gets a timeout so it can never get stuck.
     */
    fun buildLive(ctx: Context, s: Snapshot, hosted: Boolean = true): Notification {
        val now = SystemClock.elapsedRealtime()
        val (title, text) = when (s.phase) {
            Phase.SEARCHING -> "Searching for food…" to "Check #${s.searches} · every ${s.intervalMs / 1000}s"
            Phase.WAITING -> {
                val left = ((s.intervalMs - (now - s.cycleStartedAt)).coerceAtLeast(0) + 999) / 1000
                "Watching for food" to "Next check in ${left}s · ${s.searches} done"
            }
            Phase.BUYING -> "Buying…" to bidi(s.foodName ?: "Sending purchase request")
            else -> "Starting auto-search…" to "Getting ready"
        }
        val progress = when (s.phase) {
            Phase.SEARCHING, Phase.WAITING -> {
                val f = if (s.intervalMs <= 0) 1f
                else ((now - s.cycleStartedAt).toFloat() / s.intervalMs).coerceIn(0f, 1f)
                (f * SEG).toInt()
            }
            Phase.BUYING -> SEG + SEG / 2
            else -> 0
        }
        val started = if (s.startedAtWallMs > 0) s.startedAtWallMs else System.currentTimeMillis()

        val b = base(ctx, CH_LIVE, s.phase, title, text, sub(s))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setWhen(started).setShowWhen(true).setUsesChronometer(true) // ticks by itself, also in the status chip
            .addAction(0, "Stop & close", stopIntent(ctx))
        if (s.fancy) {
            // Live Updates: progress tracker + request for the status chip / Now Bar / Super Island.
            b.setRequestPromotedOngoing(true).setStyle(progressStyle(ctx, s.phase, progress))
        }
        if (!hosted) b.setTimeoutAfter(ORPHAN_TIMEOUT_MS)
        return b.build()
    }

    fun buildResult(ctx: Context, s: Snapshot): Notification {
        val took = fmt(s.tookMs)
        val (title, text) = when (s.phase) {
            Phase.SUCCESS -> "Food purchased ✓" to
                listOfNotNull(s.foodName?.let(::bidi), s.price).joinToString(" · ").ifBlank { "Purchase successful" }
            Phase.FOUND -> "Food available!" to bidi(s.foodName ?: "A new food was listed")
            else -> "Purchase not confirmed" to bidi(s.message ?: "The site didn't confirm the purchase")
        }
        val subText = if (s.phase == Phase.FOUND) "Open Bananite to buy it · after $took" else "After $took"
        return base(ctx, CH_RESULT, s.phase, title, text, subText)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(System.currentTimeMillis()).setShowWhen(true)
            .build()
    }

    private fun base(ctx: Context, channel: String, phase: Phase, title: String, text: String, sub: String) =
        NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(iconFor(phase))
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(sub)
            .setColor(colorFor(phase))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // visible on the lock screen
            .setContentIntent(openAppIntent(ctx))

    private fun progressStyle(ctx: Context, phase: Phase, progress: Int) =
        NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(SEG).setColor(BLUE),
                    NotificationCompat.ProgressStyle.Segment(SEG).setColor(AMBER),
                    NotificationCompat.ProgressStyle.Segment(SEG).setColor(GREEN)
                )
            )
            .setProgressPoints(
                listOf(
                    NotificationCompat.ProgressStyle.Point(SEG).setColor(WHITE),
                    NotificationCompat.ProgressStyle.Point(2 * SEG).setColor(WHITE)
                )
            )
            .setProgressTrackerIcon(IconCompat.createWithResource(ctx, iconFor(phase)))
            .setProgress(progress)

    private fun iconFor(p: Phase) = when (p) {
        Phase.BUYING -> R.drawable.ic_stat_cart
        Phase.FOUND -> R.drawable.ic_stat_bell
        Phase.SUCCESS -> R.drawable.ic_stat_check
        Phase.FAILED -> R.drawable.ic_stat_error
        else -> R.drawable.ic_stat_search
    }

    private fun colorFor(p: Phase) = when (p) {
        Phase.BUYING -> AMBER
        Phase.SUCCESS -> GREEN
        Phase.FAILED -> RED
        else -> BLUE
    }

    private fun sub(s: Snapshot) =
        listOf(s.mealLabel, s.dateLabel).filter { it.isNotBlank() }.joinToString(" · ") { bidi(it) }

    private fun bidi(t: String) = BidiFormatter.getInstance().unicodeWrap(t)

    private fun fmt(ms: Long): String {
        val t = ms / 1000
        val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    private fun openAppIntent(ctx: Context): PendingIntent? {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        } ?: return null
        return PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun stopIntent(ctx: Context): PendingIntent =
        PendingIntent.getService(
            ctx, 1,
            Intent(ctx, AutoSearchService::class.java).setAction(AutoSearchService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
