package com.yaad.reminder.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yaad.reminder.MainActivity
import com.yaad.reminder.R

/**
 * BroadcastReceiver triggered at the exact scheduled time to post a high-priority Persian notification.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.yaad.reminder.ACTION_TRIGGER_REMINDER"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_TEXT = "extra_item_text"

        const val CHANNEL_ID = "yaad_high_priority_reminder_channel"
        private const val CHANNEL_NAME = "یادآوری‌های یاد"
        private const val CHANNEL_DESCRIPTION = "اعلان‌های با اولویت بالا برای یادآوری‌های زمان‌بندی‌شده"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, System.currentTimeMillis())
        val reminderText = intent.getStringExtra(EXTRA_ITEM_TEXT)?.takeIf { it.isNotBlank() }
            ?: "زمان یادآوری شما فرا رسیده است"

        ensureHighPriorityChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            itemId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("یادآوری · یاد")
            .setContentText(reminderText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        NotificationManagerCompat.from(context).notify(itemId.toInt(), notification)
    }

    private fun ensureHighPriorityChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
