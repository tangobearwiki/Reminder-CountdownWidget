package com.ybhgl.reminder.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ybhgl.reminder.MainActivity
import com.ybhgl.reminder.R
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
    /**
     * 单个事件最多调度的闹钟槽位数。
     * requestCode = id * 100 + slot，因此必须远小于 100 以避免侵入其他事件的 requestCode 空间；
     * 取消时会遍历全部槽位，保证不残留幽灵闹钟。
     */
    private const val MAX_ALARM_SLOTS_PER_ITEM = 20
    private const val TAG = "ReminderScheduler"

    fun scheduleReminder(context: Context, item: ReminderItem, forceNext: Boolean = false) {
        // 未持久化（id <= 0）的条目不可调度：requestCode 会退化为纯 slot 索引，导致取消/更新错乱
        if (item.id <= 0) return

        if (!item.notificationConfig.isEnabled || !item.notificationConfig.useAppNotification) {
            cancelReminder(context, item)
            return
        }

        cancelReminder(context, item)

        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val currentTargetDate = resolveTargetDate(item, today) ?: return
        val hasFutureNotificationInCurrentCycle = hasFutureTrigger(item, currentTargetDate, now)

        val targetDate = if (forceNext && !hasFutureNotificationInCurrentCycle) {
            resolveNextCycleTarget(item, currentTargetDate, today) ?: return
        } else {
            currentTargetDate
        }

        val scheduled = scheduleAlarmsForTarget(context, item, targetDate, now)
        if (scheduled == 0) {
            val nextCycle = resolveNextCycleTarget(item, targetDate, today)
            if (nextCycle != null && nextCycle != targetDate) {
                scheduleAlarmsForTarget(context, item, nextCycle, now)
            }
        }
    }

    private fun resolveTargetDate(item: ReminderItem, today: LocalDate): LocalDate? {
        return when (item.type) {
            ReminderType.PERIOD -> PeriodCalculator.predict(item, today)?.nextStart ?: item.date
            else -> CalendarUtil.calculateNextTargetDate(item, today)
                ?.takeUnless { item.repeatInfo?.endDate?.isBefore(it) == true }
                ?: if (item.repeatInfo == null && item.type != ReminderType.BIRTHDAY) item.date else null
        }
    }

    private fun resolveNextCycleTarget(item: ReminderItem, currentTarget: LocalDate, today: LocalDate): LocalDate? {
        return when (item.type) {
            ReminderType.PERIOD -> {
                val cycle = item.cycleLength.coerceAtLeast(1).toLong()
                currentTarget.plusDays(cycle)
            }
            else -> CalendarUtil.calculateNextTargetDate(item, currentTarget.plusDays(1).let { if (it.isBefore(today.plusDays(1))) today.plusDays(1) else it })
                ?.takeUnless { item.repeatInfo?.endDate?.isBefore(it) == true }
        }
    }

    private fun hasFutureTrigger(item: ReminderItem, targetDate: LocalDate, now: Long): Boolean {
        return triggerInstants(item, targetDate).any { it > now }
    }

    private fun triggerInstants(item: ReminderItem, targetDate: LocalDate): List<Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        if (item.notificationConfig.isContinuous && item.type != ReminderType.COUNT_UP) {
            val time = item.notificationConfig.notificationTimes.firstOrNull()?.time ?: LocalTime.of(9, 0)
            val instants = mutableListOf<Long>()
            var day = today
            var slots = 0
            while (!day.isAfter(targetDate) && slots < MAX_ALARM_SLOTS_PER_ITEM) {
                instants += LocalDateTime.of(day, time).atZone(zone).toInstant().toEpochMilli()
                day = day.plusDays(1)
                slots++
            }
            return instants
        }

        return item.notificationConfig.notificationTimes.take(MAX_ALARM_SLOTS_PER_ITEM).map { notifTime ->
            val remindDate = remindDateFor(item, targetDate, notifTime.daysBefore)
            LocalDateTime.of(remindDate, notifTime.time).atZone(zone).toInstant().toEpochMilli()
        }
    }

    private fun remindDateFor(item: ReminderItem, targetDate: LocalDate, daysBefore: Int): LocalDate {
        return if (item.type == ReminderType.COUNT_UP) {
            val daysOffset = if (item.notificationConfig.includeStartDay && daysBefore > 0) {
                daysBefore - 1
            } else {
                daysBefore
            }
            targetDate.plusDays(daysOffset.toLong())
        } else {
            targetDate.minusDays(daysBefore.toLong())
        }
    }

    private fun scheduleAlarmsForTarget(
        context: Context,
        item: ReminderItem,
        targetDate: LocalDate,
        now: Long
    ): Int {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val zone = ZoneId.systemDefault()
        var scheduled = 0

        val slots: List<Pair<LocalDate, LocalTime>> = if (
            item.notificationConfig.isContinuous && item.type != ReminderType.COUNT_UP
        ) {
            val time = item.notificationConfig.notificationTimes.firstOrNull()?.time ?: LocalTime.of(9, 0)
            val today = LocalDate.now()
            val days = mutableListOf<Pair<LocalDate, LocalTime>>()
            var day = today
            while (!day.isAfter(targetDate) && days.size < MAX_ALARM_SLOTS_PER_ITEM) {
                days += day to time
                day = day.plusDays(1)
            }
            days
        } else {
            item.notificationConfig.notificationTimes.take(MAX_ALARM_SLOTS_PER_ITEM).map { notifTime ->
                remindDateFor(item, targetDate, notifTime.daysBefore) to notifTime.time
            }
        }

        slots.forEachIndexed { index, (remindDate, time) ->
            val triggerTime = LocalDateTime.of(remindDate, time).atZone(zone).toInstant().toEpochMilli()
            if (triggerTime < now) return@forEachIndexed

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
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
                scheduled++
            } catch (e: SecurityException) {
                Log.e(TAG, "无法调度闹钟 ${item.id} slot=$index", e)
            }
        }
        return scheduled
    }

    fun cancelReminder(context: Context, item: ReminderItem) {
        if (item.id <= 0) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
