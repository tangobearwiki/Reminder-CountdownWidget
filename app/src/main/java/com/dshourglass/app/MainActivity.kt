package com.dshourglass.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.dshourglass.app.reminder.DeepSpaceAlarmScheduler
import com.ybhgl.reminder.data.NotificationTime
import com.ybhgl.reminder.data.ReminderDatabase
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderNotificationConfig
import com.ybhgl.reminder.data.ReminderType
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF070B18)
private val Blue = Color(0xFF14244C)
private val Cyan = Color(0xFF72D9FF)
private val Violet = Color(0xFF9C8BFF)
private val Rose = Color(0xFFFF91AE)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { DeepSpaceApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeepSpaceApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ReminderDatabase.getDatabase(context) }
    val reminders by db.reminderDao().getAllReminders().collectAsState(initial = emptyList())
    val prefs = remember { context.getSharedPreferences("deep_space_ui", 0) }
    var background by rememberSaveable { mutableStateOf(prefs.getString("background", null)) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        background = uri.toString()
        prefs.edit().putString("background", background).apply()
    }
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        .92f, 1.05f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "pulse"
    ).value

    Box(Modifier.fillMaxSize()) {
        SpaceBackground(background, pulse)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Column { Text("深空沙漏", fontWeight = FontWeight.Bold); Text("把重要的日子，安静地放在星海里", fontSize = 11.sp, color = Color.White.copy(alpha = .65f)) } },
                    actions = {
                        IconButton(onClick = { picker.launch(arrayOf("image/*")) }) { Icon(Icons.Default.Image, "背景") }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "设置") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = Cyan, contentColor = Navy) { Icon(Icons.Default.Add, "新建") } },
            bottomBar = {
                Row(Modifier.fillMaxWidth().background(Color(0xDD0A1020)).navigationBarsPadding().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BottomTab("星轨", Icons.Default.CalendarMonth, tab == 0) { tab = 0 }
                    BottomTab("时间线", Icons.Default.NotificationsActive, tab == 1) { tab = 1 }
                    BottomTab("工具箱", Icons.Default.Settings, tab == 2) { tab = 2 }
                }
            }
        ) { pad ->
            AnimatedContent(tab, transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = .985f)) togetherWith fadeOut(tween(120)) }, modifier = Modifier.padding(pad), label = "tab") { page ->
                when (page) {
                    0 -> Home(reminders, db, scope, context)
                    1 -> Timeline(reminders)
                    else -> Toolbox(reminders, context)
                }
            }
        }
        if (showAdd) {
            AddDialog(onDismiss = { showAdd = false }) { item ->
                showAdd = false
                scope.launch {
                    val id = db.reminderDao().insert(item).toInt()
                    DeepSpaceAlarmScheduler.schedule(context, item.copy(id = id))
                }
            }
        }
        if (showSettings) {
            AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("深空设置") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("背景图")
                    Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("选择自定义背景") }
                    TextButton(onClick = { background = null; prefs.edit().remove("background").apply() }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认星海") }
                    Text("建议使用低对比度图片，让日期与提醒保持清晰。", fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
                }
            }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("完成") } })
        }
    }
}

@Composable
private fun SpaceBackground(uri: String?, pulse: Float) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = uri?.let { withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(it))?.use { stream -> BitmapFactory.decodeStream(stream) } }.getOrNull() } }
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Navy, Blue, Color(0xFF211943))))) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().alpha(.36f), contentScale = ContentScale.Crop) }
        Box(Modifier.size(250.dp).scale(pulse).align(Alignment.TopEnd).clip(RoundedCornerShape(125.dp)).background(Brush.radialGradient(listOf(Cyan.copy(alpha = .22f), Color.Transparent))))
        Box(Modifier.size(290.dp).align(Alignment.BottomStart).clip(RoundedCornerShape(145.dp)).background(Brush.radialGradient(listOf(Violet.copy(alpha = .18f), Color.Transparent))))
    }
}

@Composable
private fun BottomTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) Cyan else Color.White.copy(alpha = .55f)
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp)); Text(label, color = c, fontSize = 11.sp)
    }
}

@Composable
private fun Home(reminders: List<ReminderItem>, db: ReminderDatabase, scope: kotlinx.coroutines.CoroutineScope, context: android.content.Context) {
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xBB0B1222))) {
                Column(Modifier.padding(22.dp)) {
                    Text("TODAY · ${today.dayOfWeek}", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp)); Text("时间不会催促你。", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("只提醒你，重要的人和重要的日子，都值得被认真记住。", color = Color.White.copy(alpha = .68f))
                }
            }
        }
        item { Text("你的星轨", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(reminders, key = { it.id }) { item ->
            ReminderCard(item, today, { scope.launch { db.reminderDao().delete(item) } }, { DeepSpaceAlarmScheduler.schedule(context, item) })
        }
        if (reminders.isEmpty()) item { EmptyCard() }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun ReminderCard(item: ReminderItem, today: LocalDate, onDelete: () -> Unit, onRemind: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, item.date)
    val period = item.type == ReminderType.PERIOD
    val accent = if (period) Rose else Cyan
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1222))) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(item.tag.ifBlank { if (period) "周期照顾" else "重要日子" }, color = accent, fontSize = 12.sp) }
                if (item.isPinned) Text("PIN", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            val big = if (item.type == ReminderType.COUNT_UP) "已过去 ${kotlin.math.abs(days)} 天" else when { days > 0 -> "$days 天"; days == 0L -> "就是今天"; else -> "已过去 ${kotlin.math.abs(days)} 天" }
            Text(big, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text(item.date.toString(), color = Color.White.copy(alpha = .5f), fontSize = 12.sp)
            if (item.notes.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(item.notes, color = Color.White.copy(alpha = .68f), fontSize = 13.sp) }
            Divider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = .08f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("提醒", Icons.Default.NotificationsActive, onRemind); Action("删除", Icons.Default.Delete, onDelete)
            }
        }
    }
}

@Composable
private fun Action(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .06f)) { Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(text, fontSize = 12.sp) } }
}

@Composable
private fun EmptyCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0x990B1222))) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("还没有星轨", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("先放一个重要日子进去，深空沙漏会替你守着它。", color = Color.White.copy(alpha = .62f)) } }
}

@Composable
private fun Timeline(reminders: List<ReminderItem>) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("时间线", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("按日期看见接下来值得留意的节点", color = Color.White.copy(alpha = .62f)); Spacer(Modifier.height(8.dp)) }
        items(reminders.sortedBy { it.date }, key = { "tl-${it.id}" }) { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(item.date.monthValue.toString().padStart(2, '0'), color = Cyan); Text(item.date.dayOfMonth.toString().padStart(2, '0'), fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp)); Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xAA0B1222)), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(15.dp)) { Text(item.title, fontWeight = FontWeight.Bold); Text(item.notes.ifBlank { "安静地等它来到。" }, color = Color.White.copy(alpha = .6f), fontSize = 12.sp) } }
            }
        }
    }
}

@Composable
private fun Toolbox(reminders: List<ReminderItem>, context: android.content.Context) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("深空控制台", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("新界面负责质感，原有成熟能力继续保留。", color = Color.White.copy(alpha = .65f))
        ToolCard("提醒总数", reminders.size.toString())
        ToolCard("周期记录", reminders.count { it.type == ReminderType.PERIOD }.toString())
        ToolCard("自定义背景", "支持图片背景与玻璃卡片")
        Button(onClick = { context.startActivity(Intent().setClassName(context, "com.ybhgl.reminder.MainActivity")) }, modifier = Modifier.fillMaxWidth()) { Text("打开完整工具箱") }
    }
}

@Composable
private fun ToolCard(a: String, b: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xAA0B1222)), shape = RoundedCornerShape(22.dp)) { Row(Modifier.padding(18.dp)) { Column(Modifier.weight(1f)) { Text(a, fontWeight = FontWeight.Bold); Text(b, color = Cyan, fontSize = 13.sp) } } } }

@Composable
private fun AddDialog(onDismiss: () -> Unit, onSave: (ReminderItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ReminderType.ANNUAL) }
    var pinned by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("放入星轨") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("事件名称") }, singleLine = true)
            OutlinedTextField(date, { date = it }, label = { Text("日期 yyyy-MM-dd") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("一句备注（可选）") })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(type == ReminderType.ANNUAL, { type = ReminderType.ANNUAL }, label = { Text("重要日") })
                FilterChip(type == ReminderType.PERIOD, { type = ReminderType.PERIOD }, label = { Text("周期照顾") })
            }
            FilterChip(pinned, { pinned = !pinned }, label = { Text(if (pinned) "已置顶" else "置顶") })
        }
    }, confirmButton = { Button(onClick = {
        val d = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now().plusDays(7) }
        onSave(ReminderItem(title = title.ifBlank { "未命名星轨" }, date = d, type = type, isLunar = false, tag = if (type == ReminderType.PERIOD) "周期照顾" else "重要日子", isPinned = pinned, notificationConfig = ReminderNotificationConfig(isEnabled = true, useAppNotification = true, notificationTimes = listOf(NotificationTime(0, LocalTime.of(8, 0)))), notes = notes))
    }, enabled = title.isNotBlank()) { Text("放入星轨") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
