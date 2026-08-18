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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.util.PeriodCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 生理期专属页面
 * 显示周期预测、倒数提醒、排卵日、安全期等
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
    val statusText = remember(reminder, today) { reminder?.let { PeriodCalculator.statusText(it, today) } ?: "未记录" }
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
            if (reminder == null) {
                // 无记录状态
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
                            tint = Color(0xFFEC407A),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "还没有生理期记录",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击下方按钮记录上次经期开始日期，\n即可自动预测下次周期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 状态总览卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 暖话提醒
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFE4EC)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "周期小贴士",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD81B60)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = warmMessage(reminder, prediction),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFAD1457)
                        )
                    }
                }

                // 预测卡片
                if (prediction != null) {
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
                            PredictionRow("距下次还有", "${prediction.daysUntilNext} 天")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            PredictionRow("周期第", "${prediction.dayInCycle} 天")
                            if (prediction.ovulationDate != null) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                PredictionRow("排卵日", prediction.ovulationDate.format(dateFmt))
                            }
                        }
                    }

                    // 设置卡片
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
                            PredictionRow("经期天数", "${reminder.periodLength} 天")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            PredictionRow("周期天数", "${reminder.cycleLength} 天")
                        }
                    }
                }
            }

            // 通知开关
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "经期提醒",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "提前通知预计经期开始",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = reminder?.notificationConfig?.isEnabled ?: false,
                        onCheckedChange = onPeriodNotificationToggle
                    )
                }
            }

            // 记录今天按钮
            Button(
                onClick = onRecordPeriodStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("记录本次经期开始")
            }
        }
    }
}

private fun warmMessage(reminder: ReminderItem?, prediction: PeriodCalculator.PeriodPrediction?): String {
    @Suppress("UNUSED_VALUE")
    val today = LocalDate.now()
    return when {
        prediction == null -> "记录上次日期后即可查看周期预测"
        prediction.isInPeriodNow -> {
            if (prediction.dayInCycle <= 2) "注意保暖，注意休息"
            else "注意保暖，避免生冷"
        }
        prediction.daysUntilNext <= 3 -> "近期注意保暖"
        prediction.daysUntilNext <= 7 -> "留意周期，注意休息"
        else -> "祝身体安康"
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
            fontWeight = FontWeight.SemiBold
        )
    }
}
/**
 * 生理期首页分支 - 开门见山的一级页面，无需二级跳转
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
    val statusText = remember(reminder, today) { reminder?.let { PeriodCalculator.statusText(it, today) } ?: "未记录" }
    val dateFmt = DateTimeFormatter.ofPattern("M月d日")

    var showDatePicker by remember { mutableStateOf(false) }

    // 弹窗里的状态
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var periodLen by remember { mutableIntStateOf(5) }
    var cycleLen by remember { mutableIntStateOf(28) }

    if (showDatePicker) {
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("记录经期开始日期") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "选择上次经期开始的日期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 简单的日期选择：用当前日期交互
                    Text(
                        text = selectedDate.toString(),
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
                        OutlinedButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                            Text("后一天")
                        }
                    }
                    HorizontalDivider()
                    // 经期天数
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("经期天数", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (periodLen > 1) periodLen-- }) {
                                Text("-", style = MaterialTheme.typography.titleLarge)
                            }
                            Text("$periodLen 天", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { if (periodLen < 15) periodLen++ }) {
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    // 周期天数
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("周期天数", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (cycleLen > 15) cycleLen-- }) {
                                Text("-", style = MaterialTheme.typography.titleLarge)
                            }
                            Text("$cycleLen 天", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { if (cycleLen < 60) cycleLen++ }) {
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRecordPeriodStart(selectedDate, periodLen, cycleLen)
                    showDatePicker = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = dynamicTopPadding + 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 暖心提醒卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFE4EC)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "周期小贴士",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD81B60)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = warmMessage(reminder, prediction),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAD1457)
                )
            }
        }

        if (reminder == null) {
            EmptyPeriodCard { showDatePicker = true }
        } else if (prediction != null) {
            PeriodOverviewCard(reminder, statusText, dateFmt, prediction)
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
                tint = Color(0xFFEC407A),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "还没有生理期记录",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "点击下方按钮记录上次经期开始日期，即可自动预测下次周期",
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
private fun PeriodOverviewCard(
    reminder: ReminderItem,
    statusText: String,
    dateFmt: DateTimeFormatter,
    prediction: PeriodCalculator.PeriodPrediction
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            PredictionRow("上次开始", prediction.lastStart.format(dateFmt))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PredictionRow("下次预计", prediction.nextStart.format(dateFmt))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PredictionRow("距下次还有", "${prediction.daysUntilNext} 天")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PredictionRow("周期第", "${prediction.dayInCycle} 天")
            if (prediction.ovulationDate != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                PredictionRow("排卵日", prediction.ovulationDate.format(dateFmt))
            }
        }
    }
}
