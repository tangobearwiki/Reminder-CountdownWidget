package com.ybhgl.reminder.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ybhgl.reminder.ReminderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as ReminderApplication
                    val repository = app.container.reminderRepository
                    val items = repository.getAllRemindersList()
                    var successCount = 0
                    var failCount = 0
                    items.forEach { item ->
                        // 每条提醒独立处理：单条失败不影响其余提醒的重调度
                        try {
                            ReminderScheduler.scheduleReminder(app, item)
                            CalendarManager.addOrUpdateEvent(app, item)
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                            Log.e(TAG, "重调度提醒 ${item.id}（${item.title}）失败", e)
                        }
                    }
                    Log.i(TAG, "开机/升级重调度完成：成功 $successCount 条，失败 $failCount 条")
                } catch (e: Exception) {
                    Log.e(TAG, "开机重调度整体异常", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}