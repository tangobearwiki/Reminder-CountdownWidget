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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.ybhgl.reminder.data.ReminderDatabase
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderNotificationConfig
import com.ybhgl.reminder.data.ReminderType
import com.ybhgl.reminder.data.RepeatInfo
import com.ybhgl.reminder.data.RepeatUnit
import com.ybhgl.reminder.data.NotificationTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val DeepNavy = Color(0xFF070B18)
private val DeepBlue = Color(0xFF12234A)
private val DeepCyan = Color(0xFF67D7FF)
private val DeepViolet = Color(0xFF9B8CFF)
private val WarmRose = Color(0xFFFF8FAE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { DeepSpaceHourglassApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeepSpaceHourglassApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ReminderDatabase.getDatabase(context) }
    val reminders by db.reminderDao().getAllReminders().collectAsState(initial = emptyList())
    val prefs = remember { context.getSharedPreferences("deep_space_ui", 0) }
    var backgroundUri by rememberSaveable { mutableStateOf(prefs.getString("background", null)) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var darkMode by rememberSaveable { mutableStateOf(prefs.getBoolean("dark", true)) }
    var selected by rememberSaveable { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        backgroundUri = uri.toString()
        prefs.edit().putString("background", backgroundUri).apply()
    }

    val infinite = rememberInfiniteTransition(label = "stars")
    val glow by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(Modifier.fillMaxSize()) {
        DeepBackground(backgroundUri, glow)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("深空沙漏", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text("把重要的日子，安静地放在星海里", style = MaterialTheme.typography.labelMedium, modifier = Modifier.alpha(.75f))
                        }
                    },
                    actions = {
                        IconButton(onClick = { launcher.launch(arrayOf("image/*")) }) { Icon(Icons.Default.Image, "背景") }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "设置") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = DeepCyan,
                    contentColor = DeepNavy,
                    modifier = Modifier.navigationBarsPadding()
                ) { Icon(Icons.Default.Add, "新建") }
            },
            bottomBar = {
                NavigationBarGlass(selected) { selected = it }
            }
        ) { padding ->
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(220)) + scaleIn(initialScale = .98f) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.padding(padding),
                label = "tab"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(reminders, scope, db, context)
                    1 -> CalendarScreen(reminders)
                    else -> CompanionScreen(reminders) { context.startActivity(Intent().setClassName(context, "com.ybhgl.reminder.MainActivity")) }
                }
            }
        }

        if (showAdd) {
            ReminderEditorDialog(
                onDismiss = { showAdd = false },
                onSave = { item ->
                    showAdd = false
                    scope.launch {
                        val id = db.reminderDao().insert(item).toInt()
                        DeepSpaceAlarmScheduler.schedule(context, item.copy(id = id))
                    }
                }
            )
        }
        if (showSettings) {
            SettingsDialog(
                darkMode = darkMode,
                onDarkModeChanged = {
                    darkMode = it
                    prefs.edit().putBoolean("dark", it).apply()
                },
                onPickBackground = { launcher.launch(arrayOf("image/*")) },
                onClearBackground = {
                    backgroundUri = null
                    prefs.edit().remove("background").apply()
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun DeepBackground(uri: String?, glow: Float) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = uri?.let {
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(Uri.parse(it))?.use(BitmapFactory::decodeStream) }.getOrNull()
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(DeepNavy, DeepBlue, Color(0xFF261B49))))) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize().alpha(.38f), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.size(240.dp).scale(glow).align(Alignment.TopEnd).clip(CircleShape)
                .background(Brush.radialGradient(listOf(DeepCyan.copy(alpha = .22f), Color.Transparent)))
        )
        Box(
            Modifier.size(260.dp).align(Alignment.BottomStart).clip(CircleShape)
                .background(Brush.radialGradient(listOf(DeepViolet.copy(alpha = .18f), Color.Transparent)))
        )
    }
}

@Composable
private fun NavigationBarGlass(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = Color(0xCC0A1020), tonalElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            NavItem("倒数", Icons.Default.CalendarMonth, selected == 0) { onSelect(0) }
            NavItem("时间线", Icons.Default.NotificationsActive, selected == 1) { onSelect(1) }
            NavItem("更多", Icons.Default.Settings, selected == 2) { onSelect(2) }
        }
    }
}

@Composable
private fun NavItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) DeepCyan else Color.White.copy(alpha = .55f), label = "navColor")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun HomeScreen(
    reminders: List<ReminderItem>,
    scope: kotlinx.coroutines.CoroutineScope,
    db: ReminderDatabase,
    context: android.content.Context
) {
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Hero(today)
        }
        item {
            Text("你的星轨", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(reminders, key = { it.id }) { item ->
            ReminderCard(item, today,
                onDelete = { scope.launch { db.reminderDao().delete(item) } },
                onNotify = { DeepSpaceAlarmScheduler.schedule(context, item) })
        }
        if (reminders.isEmpty()) {
            item { EmptyState() }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun Hero(today: LocalDate) {
    Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color(0xB30C1428))) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text("TODAY · ${today.dayOfWeek}", color = DeepCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("时间不会催促你。", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("只提醒你，重要的人和重要的日子，都值得被认真记住。", color = Color.White.copy(alpha = .72f), lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ReminderCard(item: ReminderItem, today: LocalDate, onDelete: () -> Unit, onNotify: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, item.date)
    val isPeriod = item.type == ReminderType.PERIOD
    val accent = if (isPeriod) WarmRose else DeepCyan
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1222))) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(item.tag.ifBlank { if (isPeriod) "周期照顾" else "重要日子" }, color = accent, fontSize = 12.sp)
                }
                if (item.isPinned) Text("PIN", color = DeepViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = when {
                        item.type == ReminderType.COUNT_UP -> "已过去 $${kotlin.math.abs(days)} 天".replace("$", "")
                        days > 0 -> "$days 天"
                        days == 0L -> "就是今天"
                        else -> "已过去 ${kotlin.math.abs(days)} 天"
                    },
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(item.date.toString(), color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
            if (item.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(item.notes, color = Color.White.copy(alpha = .68f), fontSize = 13.sp)
            }
            Divider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = .08f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill("提醒", Icons.Default.NotificationsActive, onNotify)
                ActionPill("删除", Icons.Default.Delete, onDelete)
            }
        }
    }
}

@Composable
private fun ActionPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .06f), modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = Color.White.copy(alpha = .75f))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = .75f))
        }
    }
}

@Composable
private fun EmptyState() {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0x990B1222))) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有星轨", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("先放一个重要日子进去，深空沙漏会替你守着它。", color = Color.White.copy(alpha = .62f))
        }
    }
}

@Composable
private fun CalendarScreen(reminders: List<ReminderItem>) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("时间线", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("按日期看见接下来值得留意的节点", color = Color.White.copy(alpha = .62f))
            Spacer(Modifier.height(12.dp))
        }
        items(reminders.sortedBy { it.date }, key = { "timeline-${it.id}" }) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.date.monthValue.toString().padStart(2, '0'), color = DeepCyan, fontWeight = FontWeight.Bold)
                    Text(item.date.dayOfMonth.toString().padStart(2, '0'), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xAA0B1222))) {
                    Column(Modifier.padding(15.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text(item.notes.ifBlank { "安静地等它来到。" }, color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionScreen(reminders: List<ReminderItem>, onLegacy: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("深空控制台", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("新界面负责质感，原项目的成熟能力继续保留。", color = Color.White.copy(alpha = .65f))
        ControlCard("现有提醒", "${reminders.size} 条")
        ControlCard("周期记录", "${reminders.count { it.type == ReminderType.PERIOD }} 条")
        ControlCard("自定义背景", "支持图片背景与玻璃卡片")
        Button(onClick = onLegacy, modifier = Modifier.fillMaxWidth()) {
            Text("打开完整工具箱")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null)
        }
    }
}

@Composable
private fun ControlCard(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xAA0B1222)), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(detail, color = DeepCyan, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun SettingsDialog(darkMode: Boolean, onDarkModeChanged: (Boolean) -> Unit, onPickBackground: () -> Unit, onClearBackground: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("深空设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("深空暗色", fontWeight = FontWeight.SemiBold); Text("低亮度、玻璃质感", fontSize = 12.sp) }
                    FilterChip(selected = darkMode, onClick = { onDarkModeChanged(!darkMode) }, label = { Text(if (darkMode) "开启" else "关闭") })
                }
                Button(onClick = onPickBackground, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("选择自定义背景") }
                TextButton(onClick = onClearBackground, modifier = Modifier.fillMaxWidth()) { Text("恢复星海默认背景") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun ReminderEditorDialog(onDismiss: () -> Unit, onSave: (ReminderItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ReminderType.ANNUAL) }
    var pinned by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("放入星轨") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("事件名称") }, singleLine = true)
                OutlinedTextField(dateText, { dateText = it }, label = { Text("日期 yyyy-MM-dd") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("一句备注（可选）") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == ReminderType.ANNUAL, { type = ReminderType.ANNUAL }, label = { Text("重要日") })
                    FilterChip(type == ReminderType.PERIOD, { type = ReminderType.PERIOD }, label = { Text("周期照顾") })
                    FilterChip(type == ReminderType.COUNT_UP, { type = ReminderType.COUNT_UP }, label = { Text("正计时") })
                }
                FilterChip(pinned, { pinned = !pinned }, label = { Text(if (pinned) "已置顶" else "置顶") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now().plusDays(7) }
                onSave(
                    ReminderItem(
                        title = title.ifBlank { "未命名星轨" },
                        date = date,
                        type = type,
                        isLunar = false,
                        tag = if (type == ReminderType.PERIOD) "周期照顾" else "重要日子",
                        isPinned = pinned,
                        repeatInfo = if (type == ReminderType.ANNUAL) RepeatInfo(1, RepeatUnit.YEAR) else null,
                        notificationConfig = ReminderNotificationConfig(
                            isEnabled = true,
                            useAppNotification = true,
                            notificationTimes = listOf(NotificationTime(0, LocalTime.of(8, 0)))
                        ),
                        notes = notes
                    )
                )
            }, enabled = title.isNotBlank()) { Text("放入星轨") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
