package com.ybhgl.reminder.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import java.time.LocalDate

class TypeConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @TypeConverter
    fun fromString(value: String?): LocalDate? {
        return value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    @TypeConverter
    fun localDateToString(date: LocalDate?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun toReminderType(value: String) = runCatching { enumValueOf<ReminderType>(value) }
        .getOrDefault(ReminderType.ANNUAL)

    @TypeConverter
    fun fromReminderType(value: ReminderType) = value.name

    @TypeConverter
    fun fromRepeatInfo(repeatInfo: RepeatInfo?): String? {
        return repeatInfo?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toRepeatInfo(raw: String?): RepeatInfo? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<RepeatInfo>(raw) }.getOrNull()
    }

    @TypeConverter
    fun fromReminderNotificationConfig(config: ReminderNotificationConfig): String {
        return json.encodeToString(config)
    }

    @TypeConverter
    fun toReminderNotificationConfig(raw: String): ReminderNotificationConfig {
        return try {
            json.decodeFromString<ReminderNotificationConfig>(raw)
        } catch (_: Exception) {
            ReminderNotificationConfig()
        }
    }
}
