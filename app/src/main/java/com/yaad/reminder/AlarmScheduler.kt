package com.yaad.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yaad.reminder.data.local.ItemEntity
import com.yaad.reminder.domain.nlp.TextProcessor

/**
 * Schedules exact wake-up alarms using [AlarmManager.setExactAndAllowWhileIdle].
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Checks whether the app currently holds permission to schedule exact alarms on Android 12+ (API 31+).
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Helper overload that accepts a formatted date string ("yyyy/MM/dd HH:mm"),
     * converts it to epoch milliseconds via [TextProcessor.parseDateTimeToEpochMillis],
     * and schedules the exact alarm.
     */
    fun schedule(formattedDateTime: String, message: String, alarmId: Long = System.currentTimeMillis()): Boolean {
        val epochMillis = TextProcessor.parseDateTimeToEpochMillis(formattedDateTime) ?: return false
        return schedule(triggerAtMillis = epochMillis, message = message, alarmId = alarmId)
    }

    /**
     * Schedules an exact alarm at [triggerAtMillis] (Long Epoch Time) with [AlarmManager.setExactAndAllowWhileIdle].
     */
    fun schedule(
        triggerAtMillis: Long,
        message: String,
        alarmId: Long = System.currentTimeMillis()
    ): Boolean {
        val now = System.currentTimeMillis()
        // If the extracted minute just started (within the current minute), schedule 5 seconds ahead so it still fires
        val safeTriggerMillis = if (triggerAtMillis <= now) {
            if (now - triggerAtMillis < 60_000L) now + 5_000L else return false
        } else {
            triggerAtMillis
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_REMINDER
            putExtra(AlarmReceiver.EXTRA_ITEM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ITEM_TEXT, message)
        }

        val requestCode = (alarmId xor (alarmId ushr 32)).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    safeTriggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    safeTriggerMillis,
                    pendingIntent
                )
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Convenience wrapper to schedule an alarm directly from a saved [ItemEntity].
     */
    fun scheduleExactReminder(item: ItemEntity): Boolean {
        val triggerAtMillis = item.extractedTime ?: return false
        return schedule(
            triggerAtMillis = triggerAtMillis,
            message = item.originalText,
            alarmId = item.id
        )
    }

    /**
     * Cancels a scheduled alarm by its [itemId].
     */
    fun cancelReminder(itemId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_REMINDER
        }
        val requestCode = (itemId xor (itemId ushr 32)).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
