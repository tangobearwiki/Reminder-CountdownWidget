package com.ybhgl.reminder.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ybhgl.reminder.MainActivity
import com.ybhgl.reminder.R
import com.ybhgl.reminder.ReminderApplication
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "reminder_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        // 整个处理流程都在 goAsync 锁内：避免通知发送前进程被回收导致提醒丢失
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ReminderApplication
                val repository = app.container.reminderRepository

                // 优先从数据库读取最新数据：闹钟携带的 Intent extras 只是调度时的快照，
                // 用户编辑事件后旧数据会过期；同时可自愈"幽灵闹钟"。
                val item = if (reminderId > 0) {
                    runCatching { repository.getReminderById(reminderId) }
                        .onFailure { Log.e(TAG, "查询事件失败，回退到 Intent 快照数据", it) }
                        .getOrNull()
                } else null

                if (reminderId > 0 && item == null) {
                    // 事件已被删除但闹钟残留：静默丢弃，不再打扰用户
                    Log.w(TAG, "事件 $reminderId 已不存在，丢弃残留闹钟")
                    return@launch
                }

                if (item != null &&
                    (!item.notificationConfig.isEnabled || !item.notificationConfig.useAppNotification)
                ) {
                    // 通知已被关闭但闹钟残留：清理残留并丢弃
                    Log.w(TAG, "事件 ${item.id} 的应用内提醒已关闭，清理残留闹钟")
                    ReminderScheduler.cancelReminder(context, item)
                    return@launch
                }

                showNotification(context, intent, item)

                // 触发后为下一次重复/下一周期重新调度
                if (item != null) {
                    when {
                        item.repeatInfo != null -> {
                            Log.d(TAG, "重复事件 ${item.id}，重调度下一次")
                            ReminderScheduler.scheduleReminder(app, item, forceNext = true)
                        }
                        item.type == ReminderType.PERIOD -> {
                            Log.d(TAG, "生理期事件 ${item.id}，为下一周期重调度")
                            ReminderScheduler.scheduleReminder(app, item)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理提醒闹钟时发生异常", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 构建并发出通知。有最新 [item] 时全部使用数据库数据；否则（DB 查询失败或无 id 的旧闹钟）
     * 回退到 Intent 快照——宁可显示稍旧的内容也不丢失提醒。
     */
    private fun showNotification(context: Context, intent: Intent, item: ReminderItem?) {
        val reminderId = item?.id ?: intent.getIntExtra("REMINDER_ID", -1)
        val title = item?.title ?: intent.getStringExtra("REMINDER_TITLE") ?: "提醒"
        val notes = item?.notes ?: intent.getStringExtra("REMINDER_NOTES") ?: ""
        val subtitle = if (item != null) {
            NotificationTextBuilder.buildSubtitle(item)
        } else {
            legacySubtitleFromExtras(intent)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "提醒通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "应用提醒通知"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("reminderId", reminderId)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            if (reminderId > 0) reminderId else 0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (notes.isNotBlank()) {
            val titleWithStatus = "$title ($subtitle)"
            builder.setContentTitle(titleWithStatus)
            builder.setContentText(notes)
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(titleWithStatus)
                    .bigText(notes)
            )
        } else {
            builder.setContentTitle(title)
            builder.setContentText(subtitle)
        }

        val notifyId = if (reminderId > 0) reminderId else System.currentTimeMillis().toInt()
        notificationManager.notify(notifyId, builder.build())
    }

    /** 旧快照数据的兜底文案计算（仅在 DB 不可用时使用） */
    private fun legacySubtitleFromExtras(intent: Intent): String {
        val reminderType = intent.getStringExtra("REMINDER_TYPE")
        val startDateStr = intent.getStringExtra("REMINDER_START_DATE")
        val targetDateStr = intent.getStringExtra("REMINDER_TARGET_DATE")
        if (reminderType == null || startDateStr == null || targetDateStr == null) {
            return NotificationTextBuilder.DEFAULT_SUBTITLE
        }
        return runCatching {
            val today = LocalDate.now()
            val startDate = LocalDate.parse(startDateStr)
            val targetDate = LocalDate.parse(targetDateStr)
            when (reminderType) {
                "COUNT_UP" -> {
                    val includeStartDay = intent.getBooleanExtra("INCLUDE_START_DAY", true)
                    val days = ChronoUnit.DAYS.between(startDate, today).toInt()
                    val displayDays = if (includeStartDay) days + 1 else days
                    "第${displayDays.coerceAtLeast(1)}天"
                }
                "ANNUAL", "BIRTHDAY", "PERIOD" -> {
                    val days = ChronoUnit.DAYS.between(today, targetDate).toInt()
                    if (days == 0) "就是今天" else "还有${days}天"
                }
                else -> NotificationTextBuilder.DEFAULT_SUBTITLE
            }
        }.getOrDefault(NotificationTextBuilder.DEFAULT_SUBTITLE)
    }

}
