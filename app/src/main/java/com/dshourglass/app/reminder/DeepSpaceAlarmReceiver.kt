package com.dshourglass.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dshourglass.app.MainActivity
import com.ybhgl.reminder.data.ReminderDatabase
import com.ybhgl.reminder.data.ReminderType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeepSpaceAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("reminder_id", -1)
        if (id <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db = ReminderDatabase.getDatabase(context)
                val item = db.reminderDao().getReminderById(id) ?: return@runCatching
                createChannel(context)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), item.date)
                    val title = if (item.type == ReminderType.PERIOD) "周期照顾提醒 · ${item.title}" else "重要日子 · ${item.title}"
                    val body = when {
                        days > 0 -> "还有 $days 天。别急，深空沙漏会替你记着。"
                        days == 0L -> "就是今天。愿今天的你，轻松一点。"
                        else -> "这个日子已经到过了。"
                    }
                    val open = PendingIntent.getActivity(
                        context, id,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    NotificationManagerCompat.from(context).notify(
                        id,
                        NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_popup_reminder)
                            .setContentTitle(title)
                            .setContentText(body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setAutoCancel(true)
                            .setContentIntent(open)
                            .build()
                    )
                }
            }
            pending.finish()
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "深空沙漏 · 重要提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "重要日子与周期照顾提醒"
            enableVibration(true)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object { private const val CHANNEL_ID = "deep_space_important" }
}
