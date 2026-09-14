package com.ybhgl.reminder

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import com.tyme.lunar.LunarDay
import com.tyme.solar.SolarDay
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderType
import com.ybhgl.reminder.data.RepeatInfo
import com.ybhgl.reminder.data.RepeatUnit
import com.ybhgl.reminder.util.BirthdayCalculator
import com.ybhgl.reminder.util.CalendarUtil

class ExampleUnitTest {
    @Test
    fun testLunarBirthdayLeapYearRules() {
        // 2023年闰二月初十 出生
        val ldLeap10 = LunarDay.fromYmd(2023, -2, 10)
        val birthDate = LocalDate.of(ldLeap10.getSolarDay().getYear(), ldLeap10.getSolarDay().getMonth(), ldLeap10.getSolarDay().getDay())

        // 1. 2023年（出生年/0岁），有闰二月，生日应该在闰二月初十 (2023-03-31)
        val bday0 = BirthdayCalculator.getLunarBirthdayInYear(birthDate, 0)
        val bdayLunar0 = SolarDay.fromYmd(bday0.year, bday0.monthValue, bday0.dayOfMonth).getLunarDay()
        assertEquals(2023, bdayLunar0.getYear())
        assertEquals(-2, bdayLunar0.getMonth())
        assertEquals(10, bdayLunar0.getDay())

        // 2. 2024年（1岁），无闰二月，应该在前一个月（即二月）的对应日期二月初十过 (2024-03-19)
        val bday1 = BirthdayCalculator.getLunarBirthdayInYear(birthDate, 1)
        val bdayLunar1 = SolarDay.fromYmd(bday1.year, bday1.monthValue, bday1.dayOfMonth).getLunarDay()
        assertEquals(2024, bdayLunar1.getYear())
        assertEquals(2, bdayLunar1.getMonth())
        assertEquals(10, bdayLunar1.getDay())

        // 3. 2025年（2岁），无闰二月，在二月初十过 (2025-03-09)
        val bday2 = BirthdayCalculator.getLunarBirthdayInYear(birthDate, 2)
        val bdayLunar2 = SolarDay.fromYmd(bday2.year, bday2.monthValue, bday2.dayOfMonth).getLunarDay()
        assertEquals(2025, bdayLunar2.getYear())
        assertEquals(2, bdayLunar2.getMonth())
        assertEquals(10, bdayLunar2.getDay())
    }

    @Test
    fun solarAgeIsWesternZhouSui() {
        val birth = LocalDate.of(2000, 6, 1)
        assertEquals(26, BirthdayCalculator.calculate(birth, isLunar = false, today = LocalDate.of(2026, 6, 1)).age)
        assertEquals(26, BirthdayCalculator.calculate(birth, isLunar = false, today = LocalDate.of(2026, 6, 2)).age)
        assertEquals(25, BirthdayCalculator.calculate(birth, isLunar = false, today = LocalDate.of(2026, 5, 31)).age)
        assertEquals(0, BirthdayCalculator.calculate(birth, isLunar = false, today = LocalDate.of(2000, 6, 1)).age)
    }

    @Test
    fun birthdayWithoutRepeatStillRecursYearly() {
        val item = ReminderItem(
            id = 1,
            title = "小明",
            date = LocalDate.of(2000, 6, 1),
            type = ReminderType.BIRTHDAY,
            isLunar = false,
            tag = "",
            isPinned = false,
            repeatInfo = null
        )
        val next = CalendarUtil.calculateNextTargetDate(item, LocalDate.of(2026, 6, 2))
        assertEquals(LocalDate.of(2027, 6, 1), next)
    }

    @Test
    fun dailyRepeatJumpsInsteadOfLooping() {
        val item = ReminderItem(
            id = 2,
            title = "每日",
            date = LocalDate.of(2010, 1, 1),
            type = ReminderType.ANNUAL,
            isLunar = false,
            tag = "",
            isPinned = false,
            repeatInfo = RepeatInfo(1, RepeatUnit.DAY)
        )
        val next = CalendarUtil.calculateNextTargetDate(item, LocalDate.of(2026, 9, 14))
        assertEquals(LocalDate.of(2026, 9, 14), next)
    }
}
