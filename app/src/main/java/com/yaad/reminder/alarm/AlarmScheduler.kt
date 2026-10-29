package com.yaad.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yaad.reminder.data.local.ItemCategory
import com.yaad.reminder.data.local.ItemEntity

/**
 * Schedules exact wake-up alarms using [AlarmManager.setExactAndAllowWhileIdle]
 * for saved [ItemEntity] records with category [ItemCategory.REMINDER] and a valid [ItemEntity.extractedTime].
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules an exact alarm for [item] if it is a [ItemCategory.REMINDER] and has a future timestamp.
     * @return true if the alarm was scheduled, false otherwise.
     */
    fun scheduleExactReminder(item: ItemEntity): Boolean {
        val triggerAtMillis = item.extractedTime ?: return false
        if (item.category != ItemCategory.REMINDER) return false
        if (triggerAtMillis <= System.currentTimeMillis()) return false

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_REMINDER
            putExtra(AlarmReceiver.EXTRA_ITEM_ID, item.id)
            putExtra(AlarmReceiver.EXTRA_ITEM_TEXT, item.originalText)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Cancels a previously scheduled alarm by its [itemId].
     */
    fun cancelReminder(itemId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            itemId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
