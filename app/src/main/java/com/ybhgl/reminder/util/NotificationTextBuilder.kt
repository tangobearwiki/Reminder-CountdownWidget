package com.ybhgl.reminder.util

import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 统一生成提醒通知的副标题。
 * 经期提醒采用克制、关怀式文案，避免制造焦虑，也不把预测结果包装成医疗结论。
 */
object NotificationTextBuilder {

    const val DEFAULT_SUBTITLE = "来自深空沙漏的提醒"

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
            ReminderType.PERIOD -> buildPeriodSubtitle(item, today)
        }
    }

    private fun buildPeriodSubtitle(item: ReminderItem, today: LocalDate): String {
        val prediction = PeriodCalculator.predict(item, today) ?: return "记得照顾好自己"

        return when {
            prediction.isInPeriodNow && prediction.dayInCycle <= 2 ->
                "今天先慢一点，记得休息"
            prediction.isInPeriodNow ->
                "这几天照顾好自己，别太赶"
            prediction.daysUntilNext == 0L ->
                "今天开始，记得照顾好自己"
            prediction.daysUntilNext in 1L..3L ->
                "快到了，提前留一点舒服的时间"
            prediction.daysUntilNext in 4L..7L ->
                "这周留意一下周期，别忘了照顾自己"
            else ->
                "还有${prediction.daysUntilNext}天，按自己的节奏就好"
        }
    }
}
