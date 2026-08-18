package com.ybhgl.reminder.util

import com.ybhgl.reminder.data.ReminderItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 生理期计算工具
 * 根据上次开始日期、经期天数、周期天数预测下一次
 */
object PeriodCalculator {

    data class PeriodPrediction(
        val lastStart: LocalDate,
        val nextStart: LocalDate,
        val nextEnd: LocalDate,
        val dayInCycle: Int,
        val daysUntilNext: Long,
        val isInPeriodNow: Boolean,
        val daysLeftInPeriod: Int,
        val ovulationDate: LocalDate? = null,
        val safePeriodStart: LocalDate? = null,
        val safePeriodEnd: LocalDate? = null
    )

    /**
     * 计算下一次生理期预测
     */
    fun predict(reminder: ReminderItem, today: LocalDate = LocalDate.now()): PeriodPrediction? {
        val lastStart = reminder.lastPeriodStart ?: return null
        if (lastStart.isAfter(today)) return null

        val cycleLength = reminder.cycleLength.coerceAtLeast(1)
        val periodLength = reminder.periodLength.coerceAtLeast(1)

        // 计算上次开始到今天过了多少个周期
        val elapsedDays = ChronoUnit.DAYS.between(lastStart, today)
        val cycleIndex = (elapsedDays / cycleLength)
        val thisCycleStart = lastStart.plusDays(cycleIndex * cycleLength.toLong())
        val nextStart = thisCycleStart.plusDays(cycleLength.toLong())
        val nextEnd = nextStart.plusDays((periodLength - 1).toLong())

        val isInPeriodNow = !today.isBefore(thisCycleStart) && !today.isAfter(thisCycleStart.plusDays((periodLength - 1).toLong()))
        val daysLeftInPeriod = if (isInPeriodNow) {
            ChronoUnit.DAYS.between(today, thisCycleStart.plusDays((periodLength - 1).toLong())).toInt() + 1
        } else 0

        val dayInCycle = ChronoUnit.DAYS.between(thisCycleStart, today).toInt() + 1

        // 排卵日：下次开始前14天
        val ovulationDate = nextStart.minusDays(14)

        // 安全期（简易算法）：经期结束后5天 & 下次开始前8天
        val safePeriodStart = thisCycleStart.plusDays(periodLength.toLong())
        val safePeriodEnd = safePeriodStart.plusDays(4)

        return PeriodPrediction(
            lastStart = lastStart,
            nextStart = nextStart,
            nextEnd = nextEnd,
            dayInCycle = dayInCycle,
            daysUntilNext = ChronoUnit.DAYS.between(today, nextStart),
            isInPeriodNow = isInPeriodNow,
            daysLeftInPeriod = daysLeftInPeriod,
            ovulationDate = ovulationDate,
            safePeriodStart = safePeriodStart,
            safePeriodEnd = safePeriodEnd
        )
    }

    /**
     * 获取周期内状态描述
     */
    fun statusText(reminder: ReminderItem, today: LocalDate = LocalDate.now()): String {
        val prediction = predict(reminder, today) ?: return "未设置上次日期"
        return when {
            prediction.isInPeriodNow -> "经期第 ${prediction.dayInCycle} 天，还有 ${prediction.daysLeftInPeriod} 天结束"
            prediction.daysUntilNext == 0L -> "今天就是经期开始"
            prediction.daysUntilNext <= 7 -> "预计 $prediction.daysUntilNext 天后开始"
            prediction.ovulationDate != null && today == prediction.ovulationDate -> "今天是排卵日"
            prediction.ovulationDate != null && today.isAfter(prediction.ovulationDate.minusDays(5)) && today.isBefore(prediction.ovulationDate) -> "接近排卵日"
            prediction.safePeriodStart != null && !today.isBefore(prediction.safePeriodStart) && !today.isAfter(prediction.safePeriodEnd) -> "安全期"
            else -> "周期第 ${prediction.dayInCycle} 天"
        }
    }
}