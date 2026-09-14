package com.ybhgl.reminder.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ybhgl.reminder.ReminderApplication
import com.ybhgl.reminder.widget.CountdownWidgetProvider
import com.ybhgl.reminder.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val shouldReschedule = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        if (shouldReschedule) {
            val pendingResult = goAsync()
            val job = SupervisorJob()
            CoroutineScope(Dispatchers.IO + job).launch {
                try {
                    val app = context.applicationContext as ReminderApplication
                    val repository = app.container.reminderRepository
                    val items = repository.getAllRemindersList()
                    var successCount = 0
                    var failCount = 0
                    items.forEach { item ->
                        try {
                            ReminderScheduler.scheduleReminder(app, item)
                            CalendarManager.addOrUpdateEvent(app, item)
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                            Log.e(TAG, "重调度提醒 ${item.id}（${item.title}）失败", e)
                        }
                    }
                    try {
                        WidgetUpdateHelper.updateAllWidgets(app)
                        CountdownWidgetProvider.updateAllWidgets(app)
                    } catch (e: Exception) {
                        Log.e(TAG, "刷新小组件失败", e)
                    }
                    Log.i(TAG, "开机/时区重调度完成：成功 $successCount 条，失败 $failCount 条")
                } catch (e: Exception) {
                    Log.e(TAG, "开机重调度整体异常", e)
                } finally {
                    pendingResult.finish()
                    job.cancel()
                }
            }
        }
    }
}