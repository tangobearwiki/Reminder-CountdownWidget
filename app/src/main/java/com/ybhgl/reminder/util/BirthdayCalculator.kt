package com.ybhgl.reminder.util

import com.tyme.solar.SolarDay
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BirthdayInfo(
    val age: Int,
    val chineseZodiac: String,
    val zodiac: String
)

data class BirthdayListItem(
    val age: Int,
    val dayCount: Int,
    val isPast: Boolean,
    val targetDate: LocalDate
)

object BirthdayCalculator {

    private val CHINESE_ZODIAC = arrayOf(
        "鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"
    )

    private val ZODIAC_SIGNS = listOf(
        "摩羯座" to (1 to 19),
        "水瓶座" to (1 to 20),
        "双鱼座" to (2 to 19),
        "白羊座" to (3 to 21),
        "金牛座" to (4 to 20),
        "双子座" to (5 to 21),
        "巨蟹座" to (6 to 22),
        "狮子座" to (7 to 22),
        "处女座" to (8 to 22),
        "天秤座" to (9 to 23),
        "天蝎座" to (10 to 23),
        "射手座" to (11 to 22),
        "摩羯座" to (12 to 22)
    )

    /**
     * 周岁：生日当天起算新的一岁，生日前仍是上一岁。
     * 旧实现在生日次日就把年份差 +1，导致平时年龄偏大一岁。
     */
    fun calculate(birthDate: LocalDate, isLunar: Boolean = false, today: LocalDate = LocalDate.now()): BirthdayInfo {
        val age = if (isLunar) {
            val birthSolar = SolarDay.fromYmd(birthDate.year, birthDate.monthValue, birthDate.dayOfMonth)
            val birthLunar = birthSolar.getLunarDay()
            val todaySolar = SolarDay.fromYmd(today.year, today.monthValue, today.dayOfMonth)
            val todayLunar = todaySolar.getLunarDay()
            val lunarYearDiff = todayLunar.getYear() - birthLunar.getYear()
            val birthdayThisYear = getLunarBirthdayInYear(birthDate, lunarYearDiff)
            val completedYears = if (today.isBefore(birthdayThisYear)) lunarYearDiff - 1 else lunarYearDiff
            completedYears.coerceAtLeast(0)
        } else {
            val birthThisYear = solarBirthdayInYear(birthDate, today.year)
            val baseAge = today.year - birthDate.year
            val completedYears = if (today.isBefore(birthThisYear)) baseAge - 1 else baseAge
            completedYears.coerceAtLeast(0)
        }

        val zodiac = getZodiacSign(birthDate.monthValue, birthDate.dayOfMonth)
        val chineseZodiac = getChineseZodiac(birthDate)

        return BirthdayInfo(
            age = age,
            chineseZodiac = chineseZodiac,
            zodiac = zodiac
        )
    }

    /** 公历生日落到指定年份；闰年 2/29 在平年记为 2/28。 */
    fun solarBirthdayInYear(birthDate: LocalDate, year: Int): LocalDate {
        return try {
            birthDate.withYear(year)
        } catch (_: DateTimeException) {
            LocalDate.of(year, 2, 28)
        }
    }

    private fun getZodiacSign(month: Int, day: Int): String {
        val threshold = ZODIAC_SIGNS[month - 1].second.second
        return if (day <= threshold) {
            ZODIAC_SIGNS[month - 1].first
        } else {
            if (month < 12) ZODIAC_SIGNS[month].first else "摩羯座"
        }
    }

    private fun getChineseZodiac(birthDate: LocalDate): String {
        val solar = SolarDay.fromYmd(birthDate.year, birthDate.monthValue, birthDate.dayOfMonth)
        val lunar = solar.getLunarDay()
        val lunarYear = lunar.getYear()
        val index = Math.floorMod(lunarYear - 4, 12)
        return CHINESE_ZODIAC[index]
    }

    /**
     * Generates birthday list items from age 0 to 150.
     * For lunar birthdays, the actual birthday date is recalculated for each year.
     */
    fun generateBirthdayList(birthDate: LocalDate, isLunar: Boolean): List<BirthdayListItem> {
        val today = LocalDate.now()
        val items = mutableListOf<BirthdayListItem>()

        for (age in 0..150) {
            val targetDate = if (isLunar) {
                getLunarBirthdayInYear(birthDate, age)
            } else {
                solarBirthdayInYear(birthDate, birthDate.year + age)
            }

            val dayCount = ChronoUnit.DAYS.between(today, targetDate).toInt()
            val isPast = dayCount < 0

            items.add(
                BirthdayListItem(
                    age = age,
                    dayCount = dayCount,
                    isPast = isPast,
                    targetDate = targetDate
                )
            )
        }

        return items
    }

    fun getLunarBirthdayInYear(birthDate: LocalDate, yearsToAdd: Int): LocalDate {
        val solar = SolarDay.fromYmd(birthDate.year, birthDate.monthValue, birthDate.dayOfMonth)
        val lunar = solar.getLunarDay()
        val targetLunarYear = lunar.getYear() + yearsToAdd
        val birthMonth = lunar.getMonth()
        var result: com.tyme.lunar.LunarDay? = null

        if (birthMonth < 0) {
            // 出生于闰月 (例如：birthMonth = -2 表示闰二月)
            val normalMonth = kotlin.math.abs(birthMonth)

            // 1. 先尝试在目标年份寻找对应的闰月生日 (例如 闰二月初十)
            var targetDay = lunar.getDay()
            while (result == null && targetDay > 0) {
                try {
                    result = com.tyme.lunar.LunarDay.fromYmd(targetLunarYear, birthMonth, targetDay)
                } catch (_: IllegalArgumentException) {
                    targetDay--
                }
            }

            // 2. 如果在目标年份没找到对应的闰月，则“无闰过前”：找对应的正常月份生日 (例如 二月初十)
            if (result == null) {
                targetDay = lunar.getDay()
                while (result == null && targetDay > 0) {
                    try {
                        result = com.tyme.lunar.LunarDay.fromYmd(targetLunarYear, normalMonth, targetDay)
                    } catch (_: IllegalArgumentException) {
                        targetDay--
                    }
                }
            }
        } else {
            // 出生于正常月份
            var targetDay = lunar.getDay()
            while (result == null && targetDay > 0) {
                try {
                    result = com.tyme.lunar.LunarDay.fromYmd(targetLunarYear, birthMonth, targetDay)
                } catch (_: IllegalArgumentException) {
                    targetDay--
                }
            }
        }

        if (result == null) {
            return solarBirthdayInYear(birthDate, birthDate.year + yearsToAdd)
        }

        val nextSolar = result.getSolarDay()
        return LocalDate.of(nextSolar.getYear(), nextSolar.getMonth(), nextSolar.getDay())
    }
}
