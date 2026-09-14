@file:OptIn(ExperimentalSerializationApi::class)

package com.ybhgl.reminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.serialization.ExperimentalSerializationApi
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReminderItem::class, TagItem::class], version = 8, exportSchema = false)
@TypeConverters(com.ybhgl.reminder.data.TypeConverters::class)
abstract class ReminderDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: ReminderDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN repeatInfo TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN notificationConfig TEXT NOT NULL DEFAULT '{\"isEnabled\":false,\"useAppNotification\":true,\"useSystemCalendar\":false,\"isContinuous\":false,\"notificationTimes\":[]}'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` TEXT NOT NULL DEFAULT '#2196F3',
                        `sortOrder` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                db.execSQL("""
                    INSERT OR IGNORE INTO `tags` (name, color, sortOrder)
                    SELECT DISTINCT category, '#2196F3', 0
                    FROM reminders
                    WHERE category IS NOT NULL AND category != ''
                """)
            }
        }

        /**
         * minSdk 28 的 SQLite 是 3.22，不支持 RENAME COLUMN（需要 3.25+）。
         * 用建新表 + 拷贝的方式把 category 重命名为 tag，避免 Android 9/10 升级直接崩溃。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `isLunar` INTEGER NOT NULL,
                        `tag` TEXT NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `repeatInfo` TEXT,
                        `notificationConfig` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `reminders_new` (
                        `id`, `title`, `date`, `type`, `isLunar`, `tag`, `isPinned`, `repeatInfo`, `notificationConfig`
                    )
                    SELECT
                        `id`, `title`, `date`, `type`, `isLunar`, `category`, `isPinned`, `repeatInfo`, `notificationConfig`
                    FROM `reminders`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `reminders`")
                db.execSQL("ALTER TABLE `reminders_new` RENAME TO `reminders`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN isCustomized INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN customHeaderColor TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN customFont TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN periodLength INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE reminders ADD COLUMN cycleLength INTEGER NOT NULL DEFAULT 28")
                db.execSQL("ALTER TABLE reminders ADD COLUMN lastPeriodStart TEXT")
            }
        }

        fun getDatabase(context: Context): ReminderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReminderDatabase::class.java,
                    "reminder_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    // Never silently erase user reminders when a migration is missing.
                    // A future schema change must ship with an explicit migration.
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
