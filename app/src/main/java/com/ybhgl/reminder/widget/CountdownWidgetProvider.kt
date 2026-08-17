package com.ybhgl.reminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.ybhgl.reminder.MainActivity
import com.ybhgl.reminder.R
import com.ybhgl.reminder.ReminderApplication
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderType
import com.ybhgl.reminder.util.CalendarUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * 倒数倒计时桌面小组件。
 * 从 countdown-widget-android 移植，适配 Reminder 的 Room 数据源。
 * 支持自适应布局（small/compact/full）、背景照片轮换、主题色条。
 */
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CountdownWidgetScheduler.schedule(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CountdownWidgetScheduler.schedule(context)
        updateWidgets(context, appWidgetIds, appWidgetManager)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, intArrayOf(appWidgetId), appWidgetManager)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            WidgetConfigStore.deleteConfig(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT -> {
                CountdownWidgetScheduler.schedule(context)
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
                updateWidgets(context, ids, manager)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.ybhgl.reminder.action.COUNTDOWN_WIDGET_REFRESH"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                updateWidgets(context, ids, manager)
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetIds: IntArray,
            appWidgetManager: AppWidgetManager
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = (context.applicationContext as ReminderApplication).container.reminderRepository
                    val reminders = repository.getAllRemindersStream().first()
                    
                    for (widgetId in appWidgetIds) {
                        val configuredId = WidgetConfigStore.get1x2Or2x2Config(context, widgetId)
                        val featured = if (configuredId != -1) {
                            reminders.find { it.id == configuredId }
                                ?: WidgetUpdateHelper.getFeaturedReminder(reminders)
                        } else {
                            WidgetUpdateHelper.getFeaturedReminder(reminders)
                        }

                        val layoutMode = resolveLayoutMode(appWidgetManager.getAppWidgetOptions(widgetId))
                        val layoutRes = when (layoutMode) {
                            WidgetLayoutMode.SMALL -> R.layout.widget_countdown_small
                            WidgetLayoutMode.COMPACT -> R.layout.widget_countdown_compact
                            WidgetLayoutMode.FULL -> R.layout.widget_countdown_full
                        }

                        val views = RemoteViews(context.packageName, layoutRes).apply {
                            setInt(R.id.widget_countdown_root, "setBackgroundResource", R.drawable.widget_background_countdown)

                            if (featured != null) {
                                val displayInfo = WidgetUpdateHelper.getDisplayInfo(context, featured)

                                setTextViewText(R.id.widget_countdown_days_value, displayInfo.days)
                                setTextColor(R.id.widget_countdown_days_value, context.getColor(R.color.widget_text_primary))

                                // 点击跳转到对应提醒
                                val detailIntent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("reminderId", featured.id)
                                }
                                val pendingIntent = PendingIntent.getActivity(
                                    context,
                                    featured.id + 30000,
                                    detailIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                setOnClickPendingIntent(R.id.widget_countdown_root, pendingIntent)

                                // 处理背景照片
                                val photoPaths = WidgetConfigStore.getWidgetPhotoPaths(context, widgetId)
                                val rotationHours = WidgetConfigStore.getWidgetRotationHours(context, widgetId)
                                val activePhotoPath = resolveActivePhotoPath(photoPaths, rotationHours)
                                val backgroundBitmap = activePhotoPath?.let { photoStorage.loadBitmap(it) }

                                if (backgroundBitmap != null) {
                                    setViewVisibility(R.id.widget_countdown_bg_image, View.VISIBLE)
                                    setImageViewBitmap(R.id.widget_countdown_bg_image, backgroundBitmap)
                                    setInt(R.id.widget_countdown_overlay, "setBackgroundColor", Color.argb(136, 0, 0, 0))
                                } else {
                                    setViewVisibility(R.id.widget_countdown_bg_image, View.GONE)
                                    setInt(R.id.widget_countdown_root, "setBackgroundResource", R.drawable.widget_background_countdown)
                                }

                                // 根据布局模式绑定不同内容
                                when (layoutMode) {
                                    WidgetLayoutMode.SMALL -> {
                                        // small 模式只显示数字，不绑定额外内容
                                    }
                                    WidgetLayoutMode.COMPACT -> bindCompact(this, context, featured, displayInfo)
                                    WidgetLayoutMode.FULL -> bindFull(this, context, widgetId, featured, displayInfo)
                                }
                            } else {
                                // 无提醒时显示占位
                                setTextViewText(R.id.widget_countdown_days_value, "0")
                                setTextColor(R.id.widget_countdown_days_value, context.getColor(R.color.widget_text_primary))
                                setViewVisibility(R.id.widget_countdown_bg_image, View.GONE)
                                setInt(R.id.widget_countdown_root, "setBackgroundResource", R.drawable.widget_background_countdown)

                                when (layoutMode) {
                                    WidgetLayoutMode.COMPACT -> {
                                        setTextViewText(R.id.widget_countdown_title, "暂无日程")
                                        setTextViewText(R.id.widget_countdown_unit, "天")
                                        setTextViewText(R.id.widget_countdown_target_date, "——")
                                    }
                                    WidgetLayoutMode.FULL -> {
                                        setTextViewText(R.id.widget_countdown_title, "暂无日程")
                                        setTextViewText(R.id.widget_countdown_unit, "天")
                                        setTextViewText(R.id.widget_countdown_target_date, "——")
                                    }
                                    else -> {}
                                }

                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                val pendingIntent = PendingIntent.getActivity(
                                    context,
                                    30000,
                                    intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                setOnClickPendingIntent(R.id.widget_countdown_root, pendingIntent)
                            }
                        }
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun bindCompact(
            views: RemoteViews,
            context: Context,
            reminder: ReminderItem,
            displayInfo: WidgetDisplayInfo
        ) {
            views.setTextViewText(R.id.widget_countdown_title, displayInfo.title)
            views.setTextViewText(R.id.widget_countdown_unit, compactLabel(displayInfo))
            views.setTextViewText(R.id.widget_countdown_target_date, displayInfo.dateString)
            views.setTextColor(R.id.widget_countdown_title, context.getColor(R.color.widget_accent_annual))
            views.setTextColor(R.id.widget_countdown_unit, context.getColor(R.color.widget_text_secondary))
            views.setTextColor(R.id.widget_countdown_target_date, context.getColor(R.color.widget_text_secondary))
        }

        private fun bindFull(
            views: RemoteViews,
            context: Context,
            widgetId: Int,
            reminder: ReminderItem,
            displayInfo: WidgetDisplayInfo
        ) {
            // 彩色顶栏
            val accentColor = WidgetConfigStore.getWidgetAccentColor(context, widgetId)
            views.setInt(R.id.widget_countdown_accent_bar, "setBackgroundColor", accentColor)

            views.setTextViewText(R.id.widget_countdown_title, displayInfo.title)
            views.setTextViewText(R.id.widget_countdown_days_label, displayInfo.label)
            views.setTextViewText(R.id.widget_countdown_unit, displayInfo.unit)
            views.setTextViewText(R.id.widget_countdown_target_date, displayInfo.dateString)
            views.setTextColor(R.id.widget_countdown_title, Color.WHITE)
            views.setTextColor(R.id.widget_countdown_days_label, context.getColor(R.color.widget_text_secondary))
            views.setTextColor(R.id.widget_countdown_unit, context.getColor(R.color.widget_text_primary))
            views.setTextColor(R.id.widget_countdown_target_date, context.getColor(R.color.widget_text_secondary))

            // 描述（使用 reminder 的 notes）
            if (reminder.notes.isNotBlank()) {
                views.setViewVisibility(R.id.widget_countdown_description, View.VISIBLE)
                views.setTextViewText(R.id.widget_countdown_description, reminder.notes)
            } else {
                views.setViewVisibility(R.id.widget_countdown_description, View.GONE)
            }

            // 额外字段（使用 reminder 的 tag）
            if (reminder.tag.isNotBlank() && reminder.tag != "默认") {
                views.setViewVisibility(R.id.widget_countdown_extra, View.VISIBLE)
                views.setTextViewText(R.id.widget_countdown_extra, "标签: ${reminder.tag}")
            } else {
                views.setViewVisibility(R.id.widget_countdown_extra, View.GONE)
            }
        }

        private fun compactLabel(displayInfo: WidgetDisplayInfo): String {
            return displayInfo.label + displayInfo.unit
        }

        private enum class WidgetLayoutMode {
            SMALL,
            COMPACT,
            FULL
        }

        private fun compactUnitLabel(statusLabel: String): String {
            return when (statusLabel) {
                "days left" -> "天"
                "happening today" -> "今"
                "days since" -> "天"
                else -> "天"
            }
        }

        private fun resolveLayoutMode(options: Bundle?): WidgetLayoutMode {
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
            return when {
                minWidth <= 150 && minHeight <= 150 -> WidgetLayoutMode.SMALL
                minWidth <= 220 || minHeight <= 150 -> WidgetLayoutMode.COMPACT
                else -> WidgetLayoutMode.FULL
            }
        }

        private fun resolveActivePhotoPath(photoPaths: List<String>, rotationHours: Int): String? {
            if (photoPaths.isEmpty()) return null
            if (photoPaths.size == 1) return photoPaths.first()
            val rotationWindow = rotationHours.coerceIn(1, 168)
            val epochHours = Instant.now().atZone(ZoneOffset.UTC).toEpochSecond() / 3600
            val index = ((epochHours / rotationWindow) % photoPaths.size).toInt()
            return photoPaths.getOrNull(index)
        }
    }
}