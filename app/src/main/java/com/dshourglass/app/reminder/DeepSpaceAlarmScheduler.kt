package com.dshourglass.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ybhgl.reminder.data.ReminderItem
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object DeepSpaceAlarmScheduler {
    fun schedule(context: Context, item: ReminderItem) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val config = item.notificationConfig
        if (!config.isEnabled || !config.useAppNotification) return
        val time = config.notificationTimes.firstOrNull()?.time ?: LocalTime.of(8, 0)
        val trigger = LocalDateTime.of(item.date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        if (trigger <= System.currentTimeMillis()) return
        val intent = Intent(context, DeepSpaceAlarmReceiver::class.java).apply { putExtra(EXTRA_ID, item.id) }
        val pending = PendingIntent.getBroadcast(
            context,
            item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
            .onFailure { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
    }

    fun cancel(context: Context, id: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, DeepSpaceAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pending)
    }

    private const val EXTRA_ID = "reminder_id"
}
