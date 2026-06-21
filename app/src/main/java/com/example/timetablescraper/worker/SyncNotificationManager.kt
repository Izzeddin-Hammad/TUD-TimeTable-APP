package com.example.timetablescraper.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.timetablescraper.MainActivity
import com.example.timetablescraper.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages sync-related notifications:
 *
 * 1. **In-progress notification** — shown while WorkManager is syncing
 *    (via [buildForegroundNotification] for [androidx.work.CoroutineWorker.setForeground]).
 * 2. **Completion notification** — posted after sync finishes, with success/fail
 *    status and a timestamp.
 *
 * Notification channel is created once in [com.example.timetablescraper.TimetableApplication.onCreate].
 *
 * On API 33+ (Android 13+), the completion notification requires
 * `POST_NOTIFICATIONS` permission. If not granted, it's silently skipped.
 */
object SyncNotificationManager {

    // ── Constants ──────────────────────────────────────────────────────

    const val CHANNEL_ID = "timetable_sync"
    private const val COMPLETION_NOTIFICATION_ID = 1002

    private const val CHANNEL_NAME = "Timetable Sync"
    private const val CHANNEL_DESC = "Background timetable sync status"

    // ── Channel setup ─────────────────────────────────────────────────

    /** Create the notification channel (idempotent — safe to call multiple times). */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    // ── Completion notification ───────────────────────────────────────

    /**
     * Post a notification after a sync attempt completes.
     *
     * @param context      Android context
     * @param success      `true` if the sync completed without errors
     * @param summary      Short result string (e.g. "Updated 3 courses")
     * @param errorMessage Optional error detail (shown on failure)
     */
    fun postCompletionNotification(
        context: Context,
        success: Boolean,
        summary: String?,
        errorMessage: String? = null
    ) {
        // On API 33+, skip if permission not granted
        if (!hasNotificationPermission(context)) return

        val timestamp = formatTimestamp(System.currentTimeMillis())

        val title = if (success) "Sync Complete" else "Sync Failed"
        val text = summary ?: if (success) "Timetables updated" else "Could not refresh timetable"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = if (errorMessage != null) {
            "$text\n$timestamp — $errorMessage"
        } else {
            "$text\n$timestamp"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context)
            .notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    // ── Formatting ────────────────────────────────────────────────────

    private fun formatTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    // ── Permission check ──────────────────────────────────────────────

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
