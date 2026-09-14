package com.ybhgl.reminder.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 启遇海青语义色：与小组件 / 系统主题保持同一套色板，
 * 卡片标头、空状态图标、类型筛选共用，避免各处硬编码漂移。
 */
object ReminderTypeColors {
    val countdownLight = Color(0xFF2F93AA)
    val countdownDark = Color(0xFF78C9DD)
    val countUpLight = Color(0xFFD4885F)
    val countUpDark = Color(0xFFF7A03A)
    val birthdayLight = Color(0xFFC76883)
    val birthdayDark = Color(0xFFE8719A)
    val periodLight = Color(0xFF5B86B6)
    val periodDark = Color(0xFF90CAF9)
    val annualLight = Color(0xFFC9A227)
    val annualDark = Color(0xFFE8C547)
}

// 兼容旧引用
val CountdownAccent = ReminderTypeColors.countdownLight
val CountUpAccent = ReminderTypeColors.countUpLight
val BirthdayAccent = ReminderTypeColors.birthdayLight
val PeriodAccent = ReminderTypeColors.periodLight
val AnnualAccent = ReminderTypeColors.annualLight
