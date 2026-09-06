package com.ybhgl.reminder.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ybhgl.reminder.MainActivity
import com.ybhgl.reminder.R
import com.ybhgl.reminder.data.ReminderItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    /**
     * 单个事件最多调度的闹钟槽位数。
     * requestCode = id * 100 + slot，因此必须远小于 100 以避免侵入其他事件的 requestCode 空间；
     * 取消时会遍历全部槽位，保证不残留幽灵闹钟。
     */
    private const val MAX_ALARM_SLOTS_PER_ITEM = 20

    fun scheduleReminder(context: Context, item: ReminderItem, forceNext: Boolean = false) {
        // 未持久化（id <= 0）的条目不可调度：requestCode 会退化为纯 slot 索引，导致取消/更新错乱
        if (item.id <= 0) return

        if (!item.notificationConfig.isEnabled || !item.notificationConfig.useAppNotification) {
            cancelReminder(context, item)
            return
        }

        cancelReminder(context, item)

        val today = LocalDate.now()
        val currentTargetDate = when (item.type) {
            com.ybhgl.reminder.data.ReminderType.PERIOD -> {
                PeriodCalculator.predict(item)?.nextStart ?: item.date
            }
            else -> CalendarUtil.calculateNextTargetDate(item, today)
                ?.takeUnless { item.repeatInfo?.endDate?.isBefore(it) == true }
                ?: if (item.repeatInfo == null) item.date else return
        }
        val now = System.currentTimeMillis()
        val hasFutureNotificationInCurrentCycle = item.notificationConfig.notificationTimes.any { notifTime ->
            val remindDate = if (item.type == com.ybhgl.reminder.data.ReminderType.COUNT_UP) {
                val daysOffset = if (item.notificationConfig.includeStartDay && notifTime.daysBefore > 0) {
                    notifTime.daysBefore - 1
                } else {
                    notifTime.daysBefore
                }
                currentTargetDate.plusDays(daysOffset.toLong())
            } else {
                currentTargetDate.minusDays(notifTime.daysBefore.toLong())
            }
            LocalDateTime.of(remindDate, notifTime.time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() > now
        }
        val targetDate = if (forceNext && !hasFutureNotificationInCurrentCycle) {
            when (item.type) {
                com.ybhgl.reminder.data.ReminderType.PERIOD -> PeriodCalculator.predict(item)?.nextStart ?: item.date
                else -> CalendarUtil.calculateNextTargetDate(item, today.plusDays(1))
                    ?.takeUnless { item.repeatInfo?.endDate?.isBefore(it) == true }
                    ?: return
            }
        } else {
            currentTargetDate
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        item.notificationConfig.notificationTimes
            .take(MAX_ALARM_SLOTS_PER_ITEM)
            .forEachIndexed { index, notifTime ->
            val remindDate = if (item.type == com.ybhgl.reminder.data.ReminderType.COUNT_UP) {
                val daysOffset = if (item.notificationConfig.includeStartDay && notifTime.daysBefore > 0) {
                    notifTime.daysBefore - 1
                } else {
                    notifTime.daysBefore
                }
                targetDate.plusDays(daysOffset.toLong())
            } else {
                targetDate.minusDays(notifTime.daysBefore.toLong())
            }
            val remindDateTime = LocalDateTime.of(remindDate, notifTime.time)
            
            val triggerTime = remindDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (triggerTime >= now) {
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("REMINDER_ID", item.id)
                    putExtra("REMINDER_TITLE", item.title)
                    putExtra("REMINDER_TYPE", item.type.name)
                    putExtra("REMINDER_START_DATE", item.date.toString())
                    putExtra("REMINDER_TARGET_DATE", targetDate.toString())
                    putExtra("INCLUDE_START_DAY", item.notificationConfig.includeStartDay)
                    putExtra("REMINDER_NOTES", item.notes)
                }
                
                val requestCode = item.id * 100 + index
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun cancelReminder(context: Context, item: ReminderItem) {
        if (item.id <= 0) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 覆盖全部槽位，保证不残留；FLAG_NO_CREATE 避免为不存在的闹钟创建空 PendingIntent
        for (index in 0 until MAX_ALARM_SLOTS_PER_ITEM) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val requestCode = item.id * 100 + index
            val existing = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (existing != null) {
                alarmManager.cancel(existing)
                existing.cancel()
            }
        }
    }

    fun updateActiveNotification(context: Context, item: ReminderItem) {
        if (item.id <= 0) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNotifications = notificationManager.activeNotifications
            val hasActive = activeNotifications.any { it.id == item.id }
            if (hasActive) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("reminderId", item.id)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    item.id,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 与 ReminderReceiver 共用同一套文案逻辑，避免两处漂移
                val subtitle = NotificationTextBuilder.buildSubtitle(item)

                val builder = NotificationCompat.Builder(context, "reminder_channel")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                if (item.notes.isNotBlank()) {
                    val titleWithStatus = "${item.title} ($subtitle)"
                    builder.setContentTitle(titleWithStatus)
                    builder.setContentText(item.notes)
                    builder.setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(titleWithStatus)
                            .bigText(item.notes)
                    )
                } else {
                    builder.setContentTitle(item.title)
                    builder.setContentText(subtitle)
                }

                notificationManager.notify(item.id, builder.build())
            }
        }
    }
}
