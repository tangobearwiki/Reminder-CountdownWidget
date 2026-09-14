package com.ybhgl.reminder.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ybhgl.reminder.ReminderApplication
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderNotificationConfig
import com.ybhgl.reminder.data.NotificationTime
import com.ybhgl.reminder.util.PeriodCalculator
import com.ybhgl.reminder.util.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val QiyuSeaTint = Color(0xFF2F93AA)
private val QiyuIceTint = Color(0xFF9EDDEA)

/**
 * 生理期专属页面。
 * 重点是可靠、克制和有陪伴感：预测仅作日常记录参考，不替代医疗建议。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodScreen(
    reminder: ReminderItem?,
    isDark: Boolean,
    onBack: () -> Unit,
    onPeriodNotificationToggle: (Boolean) -> Unit,
    onRecordPeriodStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val prediction = remember(reminder, today) { reminder?.let { PeriodCalculator.predict(it, today) } }
    val statusText = remember(reminder, today) { reminder?.let { PeriodCalculator.statusText(it, today) } ?: "还没有记录" }
    val dateFmt = DateTimeFormatter.ofPattern("M月d日")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("生理期") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PeriodHeroCard(
                reminder = reminder,
                prediction = prediction,
                statusText = statusText,
                dateFmt = dateFmt
            )

            PeriodCareCard(reminder = reminder, prediction = prediction)

            if (prediction != null) {
                PeriodPredictionCard(prediction = prediction, dateFmt = dateFmt)
                PeriodSettingsCard(reminder = reminder)
            } else if (reminder == null) {
                EmptyPeriodCard(onAdd = onRecordPeriodStart)
            } else {
                EmptyPeriodCard(onAdd = onRecordPeriodStart)
            }

            PeriodNotificationCard(reminder = reminder)

            OutlinedButton(
                onClick = onRecordPeriodStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (reminder == null) "记录经期开始" else "更新本次经期记录")
            }

            Text(
                text = "周期预测会随着实际记录变化而调整，仅供日常安排参考。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PeriodHeroCard(
    reminder: ReminderItem?,
    prediction: PeriodCalculator.PeriodPrediction?,
    statusText: String,
    dateFmt: DateTimeFormatter
) {
    val title = reminder?.title?.takeIf { it.isNotBlank() } ?: "深海守护"
    val headline = when {
        prediction?.isInPeriodNow == true -> "今天，先照顾好自己"
        prediction?.daysUntilNext in 1..3 -> "快到了，提前留一点从容"
        prediction?.daysUntilNext in 4..7 -> "这几天，留意一下自己的节奏"
        prediction != null -> "一切按自己的节奏来"
        else -> "先记录一次，就能开始陪你记住周期"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                        )
                    ),
                    shape = MaterialTheme.shapes.large
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "海青守护",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = QiyuSeaTint
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (prediction != null) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "下次预计 ${prediction.nextStart.format(dateFmt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = QiyuSeaTint
                    )
                } else {
                    Text(
                        text = "记录最近一次开始日期，就可以看到周期预测。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodCareCard(
    reminder: ReminderItem?,
    prediction: PeriodCalculator.PeriodPrediction?
) {
    val message = careMessage(reminder, prediction)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = QiyuSeaTint,
                    modifier = Modifier
                        .size(42.dp)
                        .padding(9.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今天也照顾好自己",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

private fun careMessage(
    reminder: ReminderItem?,
    prediction: PeriodCalculator.PeriodPrediction?
): String {
    return when {
        reminder == null || prediction == null -> "不用急，先把最近一次开始日期记下来，后面我会帮你把节奏理清。"
        prediction.isInPeriodNow && prediction.dayInCycle <= 2 -> "这几天可以把安排放松一点，按自己舒服的节奏来。"
        prediction.isInPeriodNow -> "还在经期里，记得留一点休息时间给自己。"
        prediction.daysUntilNext <= 3 -> "预计很快就到了，可以提前准备好需要的东西，少一点临时慌张。"
        prediction.daysUntilNext <= 7 -> "这周留意一下周期，也给自己留一点缓冲空间。"
        else -> "现在不用特别做什么，按平常的节奏生活就好。"
    }
}

@Composable
private fun PeriodPredictionCard(
    prediction: PeriodCalculator.PeriodPrediction,
    dateFmt: DateTimeFormatter
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "周期预测",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            PredictionRow("上次开始", prediction.lastStart.format(dateFmt))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PredictionRow("下次预计", prediction.nextStart.format(dateFmt))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PredictionRow("预计结束", prediction.nextEnd.format(dateFmt))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PredictionRow("距下次还有", "${prediction.daysUntilNext.coerceAtLeast(0)} 天")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PredictionRow("周期第", "${prediction.dayInCycle.coerceAtLeast(1)} 天")
            prediction.ovulationDate?.let { ovulationDate ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                PredictionRow("预计排卵日", ovulationDate.format(dateFmt))
            }
            prediction.safePeriodStart?.let { safeStart ->
                prediction.safePeriodEnd?.let { safeEnd ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PredictionRow("参考窗口", "${safeStart.format(dateFmt)}–${safeEnd.format(dateFmt)}")
                }
            }
        }
    }
}

@Composable
private fun PeriodSettingsCard(reminder: ReminderItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "周期设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            PredictionRow("经期天数", "${reminder.periodLength.coerceAtLeast(1)} 天")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PredictionRow("周期天数", "${reminder.cycleLength.coerceAtLeast(1)} 天")
        }
    }
}

@Composable
private fun PredictionRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 生理期首页分支 - 一级页面直接展示周期状态与贴心提醒。
 */
@Composable
fun PeriodTabContent(
    reminders: List<ReminderItem>,
    isDark: Boolean,
    topBarHeightDp: androidx.compose.ui.unit.Dp,
    dynamicTopPadding: androidx.compose.ui.unit.Dp,
    onRecordPeriodStart: (LocalDate, Int, Int) -> Unit
) {
    val today = LocalDate.now()
    val reminder = reminders.firstOrNull()
    val prediction = remember(reminder, today) { reminder?.let { PeriodCalculator.predict(it, today) } }
    val statusText = remember(reminder, today) { reminder?.let { PeriodCalculator.statusText(it, today) } ?: "还没有记录" }
    val dateFmt = DateTimeFormatter.ofPattern("M月d日")

    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember(reminder?.id) { mutableStateOf(reminder?.lastPeriodStart ?: LocalDate.now()) }
    var periodLen by remember(reminder?.id) { mutableIntStateOf(reminder?.periodLength ?: 5) }
    var cycleLen by remember(reminder?.id) { mutableIntStateOf(reminder?.cycleLength ?: 28) }

    if (showDatePicker) {
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("记录经期开始") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "选择最近一次经期开始的日期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                            Text("前一天")
                        }
                        TextButton(onClick = { selectedDate = LocalDate.now() }) {
                            Text("今天")
                        }
                        OutlinedButton(onClick = {
                            val tomorrow = selectedDate.plusDays(1)
                            if (!tomorrow.isAfter(LocalDate.now())) selectedDate = tomorrow
                        }) {
                            Text("后一天")
                        }
                    }
                    HorizontalDivider()
                    CycleLengthAdjuster(
                        label = "经期天数",
                        value = periodLen,
                        range = 1..15,
                        onChange = { periodLen = it }
                    )
                    CycleLengthAdjuster(
                        label = "周期天数",
                        value = cycleLen,
                        range = 15..60,
                        onChange = { cycleLen = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val date = selectedDate.coerceAtMost(LocalDate.now())
                    onRecordPeriodStart(date, periodLen, cycleLen)
                    showDatePicker = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = dynamicTopPadding + 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PeriodHeroCard(reminder, prediction, statusText, dateFmt)
        PeriodCareCard(reminder, prediction)

        if (prediction != null) {
            PeriodPredictionCard(prediction, dateFmt)
            PeriodSettingsCard(reminder!!)
            FilledTonalButton(
                onClick = {
                    selectedDate = LocalDate.now()
                    periodLen = reminder.periodLength
                    cycleLen = reminder.cycleLength
                    showDatePicker = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("记录本次经期开始")
            }
        } else {
            EmptyPeriodCard { showDatePicker = true }
        }

        PeriodNotificationCard(reminder)
    }
}

@Composable
private fun CycleLengthAdjuster(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1).coerceAtLeast(range.first)) }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "$value 天",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { onChange((value + 1).coerceAtMost(range.last)) }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun EmptyPeriodCard(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = QiyuSeaTint,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "还没有生理期记录",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "记下最近一次开始日期，就可以看到后续周期。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAdd) {
                Text("记录本次经期开始")
            }
        }
    }
}

@Composable
private fun PeriodNotificationCard(reminder: ReminderItem?) {
    val context = LocalContext.current
    val notifConfig = reminder?.notificationConfig ?: ReminderNotificationConfig()
    var enabled by remember(reminder?.id, notifConfig.isEnabled) { mutableStateOf(notifConfig.isEnabled) }
    var daysBefore by remember(reminder?.id) {
        mutableIntStateOf(notifConfig.notificationTimes.firstOrNull()?.daysBefore?.coerceIn(0, 14) ?: 1)
    }
    var hour by remember(reminder?.id) { mutableIntStateOf(notifConfig.notificationTimes.firstOrNull()?.time?.hour ?: 9) }
    var minute by remember(reminder?.id) { mutableIntStateOf(notifConfig.notificationTimes.firstOrNull()?.time?.minute ?: 0) }
    val repository = (context.applicationContext as ReminderApplication).container.reminderRepository
    val scope = rememberCoroutineScope()

    fun persist(enabledValue: Boolean = enabled) {
        if (reminder == null) return
        val time = java.time.LocalTime.of(hour, minute)
        val updatedConfig = notifConfig.copy(
            isEnabled = enabledValue,
            useAppNotification = true,
            notificationTimes = listOf(NotificationTime(daysBefore, time))
        )
        scope.launch {
            val updated = reminder.copy(notificationConfig = updatedConfig)
            repository.updateReminder(updated)
            ReminderScheduler.scheduleReminder(context, updated)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "经期提醒",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (enabled) "提醒会提前告诉你，不必一直记着日期" else "需要时再打开，不会打扰平时的节奏",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        persist(checked)
                    }
                )
            }

            if (enabled && reminder != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CycleLengthAdjuster(
                    label = "提前提醒",
                    value = daysBefore,
                    range = 0..14,
                    onChange = { daysBefore = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("提醒时间", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { hour = (hour - 1 + 24) % 24 }) { Text("调早") }
                        Text(
                            text = String.format("%02d:%02d", hour, minute),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TextButton(onClick = { hour = (hour + 1) % 24 }) { Text("调晚") }
                    }
                }
                TextButton(
                    onClick = { persist(true) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("保存提醒")
                }
            }
        }
    }
}
