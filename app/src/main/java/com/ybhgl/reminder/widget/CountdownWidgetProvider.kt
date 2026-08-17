package com.ybhgl.reminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CountdownWidgetScheduler.schedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CountdownWidgetScheduler.schedule(context)
        updateWidgets(context, appWidgetIds, appWidgetManager)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, intArrayOf(appWidgetId), appWidgetManager)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (appWidgetId in appWidgetIds) WidgetConfigStore.deleteConfig(context, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_USER_PRESENT -> {
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
            if (ids.isNotEmpty()) updateWidgets(context, ids, manager)
        }

        private fun updateWidgets(context: Context, appWidgetIds: IntArray, appWidgetManager: AppWidgetManager) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = (context.applicationContext as ReminderApplication).container.reminderRepository
                    val reminders = repository.getAllRemindersStream().first()

                    for (widgetId in appWidgetIds) {
                        val configuredId = WidgetConfigStore.get1x2Or2x2Config(context, widgetId)
                        val reminder = if (configuredId != -1) reminders.find { it.id == configuredId }
                            else WidgetUpdateHelper.getFeaturedReminder(reminders)

                        val layoutMode = resolveLayoutMode(appWidgetManager.getAppWidgetOptions(widgetId))
                        val layoutRes = when (layoutMode) {
                            WidgetLayoutMode.SMALL -> R.layout.widget_countdown_small
                            WidgetLayoutMode.COMPACT -> R.layout.widget_countdown_compact
                            WidgetLayoutMode.FULL -> R.layout.widget_countdown_full
                        }

                        val views = RemoteViews(context.packageName, layoutRes).apply {
                            setInt(R.id.widget_countdown_root, "setBackgroundResource", R.drawable.widget_background_countdown)

                            if (reminder != null) {
                                val displayInfo = WidgetUpdateHelper.getDisplayInfo(context, reminder)

                                setTextViewText(R.id.widget_countdown_days_value, displayInfo.days)
                                setTextColor(R.id.widget_countdown_days_value, context.getColor(R.color.widget_text_primary))

                                val detailIntent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("reminderId", reminder.id)
                                }
                                val pi = PendingIntent.getActivity(context, reminder.id + 30000, detailIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                                setOnClickPendingIntent(R.id.widget_countdown_root, pi)

                                when (layoutMode) {
                                    WidgetLayoutMode.SMALL -> {}
                                    WidgetLayoutMode.COMPACT -> bindCompact(this, context, reminder, displayInfo)
                                    WidgetLayoutMode.FULL -> bindFull(this, context, widgetId, reminder, displayInfo)
                                }
                            } else {
                                setTextViewText(R.id.widget_countdown_days_value, "0")
                                setTextColor(R.id.widget_countdown_days_value, context.getColor(R.color.widget_text_primary))

                                when (layoutMode) {
                                    WidgetLayoutMode.COMPACT, WidgetLayoutMode.FULL -> {
                                        setTextViewText(R.id.widget_countdown_title, "\u6682\u65e0\u65e5\u7a0b")
                                        setTextViewText(R.id.widget_countdown_unit, "\u5929")
                                        setTextViewText(R.id.widget_countdown_target_date, "\u2014\u2014")
                                    }
                                    else -> {}
                                }

                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                val pi = PendingIntent.getActivity(context, 30000, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                                setOnClickPendingIntent(R.id.widget_countdown_root, pi)
                            }
                        }
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun bindCompact(views: RemoteViews, context: Context, reminder: ReminderItem, displayInfo: WidgetDisplayInfo) {
            views.setTextViewText(R.id.widget_countdown_title, displayInfo.title)
            views.setTextViewText(R.id.widget_countdown_unit, displayInfo.label + displayInfo.unit)
            views.setTextViewText(R.id.widget_countdown_target_date, displayInfo.dateString)
            views.setTextColor(R.id.widget_countdown_title, context.getColor(R.color.widget_accent_annual))
            views.setTextColor(R.id.widget_countdown_unit, context.getColor(R.color.widget_text_secondary))
            views.setTextColor(R.id.widget_countdown_target_date, context.getColor(R.color.widget_text_secondary))
        }

        private fun bindFull(views: RemoteViews, context: Context, widgetId: Int, reminder: ReminderItem, displayInfo: WidgetDisplayInfo) {
            val accentColor = WidgetConfigStore.getWidgetAccentColor(context, widgetId)
            views.setInt(R.id.widget_countdown_accent_bar, "setBackgroundColor", accentColor)
            views.setTextViewText(R.id.widget_countdown_title, displayInfo.title)
            views.setTextViewText(R.id.widget_countdown_days_label, displayInfo.label)
            views.setTextViewText(R.id.widget_countdown_unit, displayInfo.unit)
            views.setTextViewText(R.id.widget_countdown_target_date, displayInfo.dateString)
            views.setTextColor(R.id.widget_countdown_title, -0x1)
            views.setTextColor(R.id.widget_countdown_days_label, context.getColor(R.color.widget_text_secondary))
            views.setTextColor(R.id.widget_countdown_unit, context.getColor(R.color.widget_text_primary))
            views.setTextColor(R.id.widget_countdown_target_date, context.getColor(R.color.widget_text_secondary))

            if (reminder.notes.isNotBlank()) {
                views.setViewVisibility(R.id.widget_countdown_description, View.VISIBLE)
                views.setTextViewText(R.id.widget_countdown_description, reminder.notes)
            } else {
                views.setViewVisibility(R.id.widget_countdown_description, View.GONE)
            }

            if (reminder.tag.isNotBlank() && reminder.tag != "\u9ed8\u8ba4") {
                views.setViewVisibility(R.id.widget_countdown_extra, View.VISIBLE)
                views.setTextViewText(R.id.widget_countdown_extra, "\u6807\u7b7e: ${reminder.tag}")
            } else {
                views.setViewVisibility(R.id.widget_countdown_extra, View.GONE)
            }
        }

        private enum class WidgetLayoutMode { SMALL, COMPACT, FULL }

        private fun resolveLayoutMode(options: Bundle?): WidgetLayoutMode {
            val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
            val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
            return when {
                w <= 150 && h <= 150 -> WidgetLayoutMode.SMALL
                w <= 220 || h <= 150 -> WidgetLayoutMode.COMPACT
                else -> WidgetLayoutMode.FULL
            }
        }
    }
}
