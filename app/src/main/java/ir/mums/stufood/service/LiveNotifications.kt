package ir.mums.stufood.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.text.BidiFormatter
import ir.mums.stufood.R
import ir.mums.stufood.service.AutoSearchBridge.Phase
import ir.mums.stufood.service.AutoSearchBridge.Snapshot
import java.util.Locale

/**
 * Builds the notifications.
 *
 * LIVE (ongoing) - a ProgressStyle notification that asks for Android 16 "promoted" treatment
 * (status-bar chip, lock screen, Samsung Now Bar). Promotion forbids custom views, so the layout
 * is done with the standard slots, each carrying exactly one kind of information:
 *
 *      [badge]  TITLE      what is happening + which meal      (Watching lunch)
 *               text       the live numbers                    (Next check in 3s · 12 done)
 *               sub        date and auto-buy state             (Sat 12 Dec · Auto-buy on)
 *      [Search ●━━━━━━━○──────────○──────── ✓ Done]            progress tracker
 *      chip:    countdown to the next check, chronometer in the header
 *
 * RESULT (food found / purchased / failed) - not promoted, so it uses a custom "ticket" card:
 * a coloured badge, status line, food name, price and a 3x2 grid with everything you would
 * otherwise open the app for (meal, date, self, time taken, checks, how it was bought).
 */
object LiveNotifications {

    const val CH_LIVE = "auto_search_live"
    const val CH_RESULT = "auto_search_result"
    const val LIVE_ID = 4101
    const val RESULT_ID = 4102

    private const val SEG = 100 // three segments: Search | Buy | Done  (total 300)

    private val BLUE = 0xFF3B6FF0.toInt()
    private val ORANGE = 0xFFF97316.toInt()
    private val GOLD = 0xFFF2A900.toInt()
    private val GREEN = 0xFF22A55B.toInt()
    private val RED = 0xFFE5484D.toInt()
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

    // ------------------------------------------------------------------------------------------
    // LIVE
    // ------------------------------------------------------------------------------------------

    /**
     * [hosted] = shown by the foreground service. When the app process itself posts it
     * (Live on, Run-in-background off) it gets a timeout so it can never get stuck.
     */
    fun buildLive(ctx: Context, s: Snapshot, hosted: Boolean = true): Notification {
        val now = SystemClock.elapsedRealtime()
        val meal = s.mealLabel.ifBlank { "food" }.let(::bidi)
        val left = if (s.intervalMs <= 0) 0L
        else ((s.intervalMs - (now - s.cycleStartedAt)).coerceAtLeast(0) + 999) / 1000

        val title: String
        val text: String
        val chip: String
        when (s.phase) {
            Phase.SEARCHING -> {
                title = "Searching $meal"
                text = "Check #${s.searches} running · every ${s.intervalMs / 1000}s"
                chip = "#${s.searches}"
            }
            Phase.WAITING -> {
                title = "Watching $meal"
                text = "Next check in ${left}s · ${s.searches} done"
                chip = "${left}s"
            }
            Phase.BUYING -> {
                title = "Buying now"
                text = bidi(s.foodName ?: "Sending purchase request")
                chip = "Buying"
            }
            else -> {
                title = "Starting auto-search"
                text = "Getting ready"
                chip = "…"
            }
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

        val subText = listOfNotNull(
            s.dateLabel.takeIf { it.isNotBlank() }?.let(::bidi),
            if (s.autoBuy) "Auto-buy on" else null
        ).joinToString(" · ")

        val b = NotificationCompat.Builder(ctx, CH_LIVE)
            .setSmallIcon(iconFor(s.phase))
            .setLargeIcon(badge(ctx, s.phase))
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText.ifBlank { null })
            .setColor(colorFor(s.phase))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // visible on the lock screen
            .setContentIntent(openAppIntent(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setWhen(started).setShowWhen(true).setUsesChronometer(true) // ticks by itself in the header
            .addAction(0, "Stop & close", stopIntent(ctx))
        if (s.fancy) {
            // Live Updates: progress tracker + request for the status chip / Now Bar / Super Island.
            b.setRequestPromotedOngoing(true)
                .setShortCriticalText(chip) // the chip shows the countdown instead of a bare timer
                .setStyle(progressStyle(ctx, s.phase, progress))
        }
        if (!hosted) b.setTimeoutAfter(ORPHAN_TIMEOUT_MS)
        return b.build()
    }

    private fun progressStyle(ctx: Context, phase: Phase, progress: Int) =
        NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(SEG).setColor(BLUE),
                    NotificationCompat.ProgressStyle.Segment(SEG).setColor(ORANGE),
                    NotificationCompat.ProgressStyle.Segment(SEG).setColor(GREEN)
                )
            )
            .setProgressPoints(
                listOf(
                    NotificationCompat.ProgressStyle.Point(SEG).setColor(WHITE),
                    NotificationCompat.ProgressStyle.Point(2 * SEG).setColor(WHITE)
                )
            )
            .setProgressStartIcon(IconCompat.createWithResource(ctx, R.drawable.ic_stat_search)) // where it starts
            .setProgressEndIcon(IconCompat.createWithResource(ctx, R.drawable.ic_stat_check))    // where it ends
            .setProgressTrackerIcon(IconCompat.createWithResource(ctx, iconFor(phase)))          // where it is now
            .setProgress(progress)

    // ------------------------------------------------------------------------------------------
    // RESULT
    // ------------------------------------------------------------------------------------------

    private class Cell(val label: String, val value: String?)

    fun buildResult(ctx: Context, s: Snapshot): Notification {
        val accent = colorFor(s.phase)
        val badge = badge(ctx, s.phase)
        val took = if (s.tookMs > 0) fmt(s.tookMs) else null
        val checks = s.searches.takeIf { it > 0 }?.toString()
        val meal = s.mealLabel.takeIf { it.isNotBlank() }?.let(::bidi)
        val date = s.dateLabel.takeIf { it.isNotBlank() }?.let(::bidi)
        val self = s.selfName?.takeIf { it.isNotBlank() }?.let(::bidi)
        val food = s.foodName?.takeIf { it.isNotBlank() }?.let(::bidi)

        val status: String
        val headline: String
        val price: String?
        val note: String?
        val cells: List<Cell>
        val title: String
        when (s.phase) {
            Phase.SUCCESS -> {
                status = "FOOD PURCHASED"
                title = "Food purchased"
                headline = food ?: "Purchase successful"
                price = s.price
                note = null
                cells = listOf(
                    Cell("MEAL", meal), Cell("DATE", date), Cell("SELF", self),
                    Cell("TOOK", took), Cell("CHECKS", checks),
                    Cell("BOUGHT", if (s.autoBuy) "Auto-buy" else "Manual")
                )
            }
            Phase.FOUND -> {
                status = "NEW FOOD AVAILABLE"
                title = "Food available!"
                headline = food ?: "A new food was listed"
                price = null
                note = "Open Bananite and tap Buy before it's gone."
                cells = listOf(
                    Cell("MEAL", meal), Cell("DATE", date), Cell("SELF", self),
                    Cell("FOUND AFTER", took), Cell("CHECKS", checks), Cell("", null)
                )
            }
            else -> {
                val stopped = s.foodName.isNullOrBlank()
                status = if (stopped) "AUTO-SEARCH STOPPED" else "PURCHASE NOT CONFIRMED"
                title = if (stopped) "Auto-search stopped" else "Purchase not confirmed"
                headline = food ?: if (stopped) "Search stopped" else "The purchase failed"
                price = null
                note = s.message?.takeIf { it.isNotBlank() }?.let(::bidi)
                    ?: "The site didn't confirm the purchase."
                cells = listOf(
                    Cell("MEAL", meal), Cell("DATE", date), Cell("SELF", self),
                    Cell("TOOK", took), Cell("CHECKS", checks), Cell("", null)
                )
            }
        }

        val collapsed = RemoteViews(ctx.packageName, R.layout.notif_result_collapsed).apply {
            setImageViewBitmap(R.id.badge, badge)
            setTextViewText(R.id.status, status)
            setTextColor(R.id.status, accent)
            setTextViewText(R.id.line, listOfNotNull(headline, price).joinToString("  ·  "))
        }

        val expanded = RemoteViews(ctx.packageName, R.layout.notif_result_expanded).apply {
            setImageViewBitmap(R.id.badge, badge)
            setTextViewText(R.id.status, status)
            setTextColor(R.id.status, accent)
            setTextViewText(R.id.food, headline)
            if (price != null) {
                setTextViewText(R.id.price, price)
                setTextColor(R.id.price, accent)
                setViewVisibility(R.id.price, View.VISIBLE)
            }
            val slots = listOf(
                Triple(R.id.c1, R.id.c1_label, R.id.c1_value),
                Triple(R.id.c2, R.id.c2_label, R.id.c2_value),
                Triple(R.id.c3, R.id.c3_label, R.id.c3_value),
                Triple(R.id.c4, R.id.c4_label, R.id.c4_value),
                Triple(R.id.c5, R.id.c5_label, R.id.c5_value),
                Triple(R.id.c6, R.id.c6_label, R.id.c6_value)
            )
            slots.forEachIndexed { i, (box, label, value) ->
                val c = cells[i]
                if (c.value.isNullOrBlank()) {
                    setViewVisibility(box, View.INVISIBLE) // keep the grid columns aligned
                } else {
                    setViewVisibility(box, View.VISIBLE)
                    setTextViewText(label, c.label)
                    setTextViewText(value, c.value)
                }
            }
            setViewVisibility(
                R.id.row2,
                if (cells.drop(3).all { it.value.isNullOrBlank() }) View.GONE else View.VISIBLE
            )
            if (note != null) {
                setTextViewText(R.id.note, note)
                setViewVisibility(R.id.note, View.VISIBLE)
            }
        }

        val summary = listOfNotNull(headline, price).joinToString(" · ")
        val b = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(iconFor(s.phase))
            .setContentTitle(title)
            .setContentText(summary)
            .setSubText(sub(s).ifBlank { null })
            .setColor(accent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(ctx))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(System.currentTimeMillis()).setShowWhen(true)
        if (s.phase == Phase.FOUND) b.addAction(0, "Open & buy", openAppIntent(ctx))
        return b.build()
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private val badgeCache = HashMap<Phase, Bitmap>()

    /** Round, phase-coloured badge with a white glyph: the same icon in the live and result notifications. */
    private fun badge(ctx: Context, phase: Phase): Bitmap = badgeCache.getOrPut(phase) {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawCircle(
            size / 2f, size / 2f, size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFor(phase) }
        )
        ContextCompat.getDrawable(ctx, glyphFor(phase))?.mutate()?.let { d ->
            val inset = (size * 0.25f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.setTint(WHITE)
            d.draw(canvas)
        }
        bmp
    }

    private fun glyphFor(p: Phase) = when (p) {
        Phase.BUYING -> R.drawable.ic_stat_cart
        Phase.FOUND -> R.drawable.ic_stat_bell
        Phase.SUCCESS -> R.drawable.ic_glyph_check
        Phase.FAILED -> R.drawable.ic_glyph_alert
        else -> R.drawable.ic_stat_search
    }

    private fun iconFor(p: Phase) = when (p) {
        Phase.BUYING -> R.drawable.ic_stat_cart
        Phase.FOUND -> R.drawable.ic_stat_bell
        Phase.SUCCESS -> R.drawable.ic_stat_check
        Phase.FAILED -> R.drawable.ic_stat_error
        else -> R.drawable.ic_stat_search
    }

    private fun colorFor(p: Phase) = when (p) {
        Phase.BUYING -> ORANGE
        Phase.FOUND -> GOLD
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
