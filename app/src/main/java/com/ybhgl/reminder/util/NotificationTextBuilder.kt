package com.ybhgl.reminder.util

import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 统一生成提醒通知的副标题文案。
 * 供 [ReminderReceiver]（闹钟触发时）与 [ReminderScheduler.updateActiveNotification]（活跃通知刷新）共用，
 * 避免两处逻辑漂移导致同一事件显示不同文案。
 */
object NotificationTextBuilder {

    const val DEFAULT_SUBTITLE = "来自 Reminder 的提醒"

    /**
     * 基于最新的 [ReminderItem] 计算副标题，任何异常都会回退到默认文案而不会抛出。
     */
    fun buildSubtitle(item: ReminderItem, today: LocalDate = LocalDate.now()): String {
        return runCatching { computeSubtitle(item, today) }.getOrDefault(DEFAULT_SUBTITLE)
    }

    private fun computeSubtitle(item: ReminderItem, today: LocalDate): String {
        val startDate = item.date
        val targetDate = when (item.type) {
            ReminderType.PERIOD -> PeriodCalculator.predict(item, today)?.nextStart ?: item.date
            else -> CalendarUtil.calculateNextTargetDate(item, today) ?: item.date
        }

        return when (item.type) {
            ReminderType.COUNT_UP -> {
                val days = ChronoUnit.DAYS.between(startDate, today).toInt()
                val displayDays = if (item.notificationConfig.includeStartDay) days + 1 else days
                "第${displayDays.coerceAtLeast(1)}天"
            }
            ReminderType.ANNUAL, ReminderType.BIRTHDAY -> {
                val days = ChronoUnit.DAYS.between(today, targetDate).toInt()
                when {
                    days == 0 -> "就是今天"
                    days > 0 -> "还有${days}天"
                    else -> "已过${-days}天"
                }
            }
            ReminderType.PERIOD -> PeriodCalculator.statusText(item, today)
        }
    }
}
