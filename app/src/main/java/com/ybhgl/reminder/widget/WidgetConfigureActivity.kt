package com.ybhgl.reminder.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import com.ybhgl.reminder.R
import com.ybhgl.reminder.ReminderApplication
import com.ybhgl.reminder.data.ReminderItem
import com.ybhgl.reminder.data.ReminderType
import com.ybhgl.reminder.ui.theme.ReminderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class WidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val providerClassName = info?.provider?.className ?: ""

        val isSingleSelection = providerClassName.contains("ReminderWidget1x2") || providerClassName.contains("ReminderWidget2x2")
        val isCountdownWidget = providerClassName.contains("CountdownWidgetProvider")
        val repository = (applicationContext as ReminderApplication).container.reminderRepository

        val initialOpacity = WidgetConfigStore.getWidgetOpacity(this, appWidgetId)
        val initialSelectedId = WidgetConfigStore.get1x2Or2x2Config(this, appWidgetId)
        val initialFilterType = if (!isSingleSelection) WidgetConfigStore.get4x2FilterType(this, appWidgetId) else "all"
        val initialCustomIds = if (!isSingleSelection) WidgetConfigStore.get4x2CustomIds(this, appWidgetId) else emptySet()
        val initialPhotoPaths = if (isCountdownWidget) WidgetConfigStore.getWidgetPhotoPaths(this, appWidgetId) else emptyList()
        val initialRotationHours = if (isCountdownWidget) WidgetConfigStore.getWidgetRotationHours(this, appWidgetId) else 24
        val initialAccentColor = if (isCountdownWidget) WidgetConfigStore.getWidgetAccentColor(this, appWidgetId) else 0xFF76E4F7.toInt()

        setContent {
            ReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WidgetConfigureScreen(
                        isSingleSelection = isSingleSelection,
                        isCountdownWidget = isCountdownWidget,
                        initialOpacity = initialOpacity,
                        initialSelectedId = initialSelectedId,
                        initialFilterType = initialFilterType,
                        initialCustomIds = initialCustomIds,
                        initialPhotoPaths = initialPhotoPaths,
                        initialRotationHours = initialRotationHours,
                        initialAccentColor = initialAccentColor,
                        onCancel = { finish() },
                        onSave = { selectedId, filterType, customIds, opacity, items, photoPaths, rotationHours, accentColor ->
                            WidgetConfigStore.saveWidgetOpacity(this@WidgetConfigureActivity, appWidgetId, opacity)
                            if (isCountdownWidget) {
                                WidgetConfigStore.save1x2Or2x2Config(this@WidgetConfigureActivity, appWidgetId, selectedId)
                                WidgetConfigStore.saveWidgetPhotoPaths(this@WidgetConfigureActivity, appWidgetId, photoPaths)
                                WidgetConfigStore.saveWidgetRotationHours(this@WidgetConfigureActivity, appWidgetId, rotationHours)
                                WidgetConfigStore.saveWidgetAccentColor(this@WidgetConfigureActivity, appWidgetId, accentColor)
                                CountdownWidgetProvider.updateAllWidgets(this@WidgetConfigureActivity)
                            } else if (isSingleSelection) {
                                WidgetConfigStore.save1x2Or2x2Config(this@WidgetConfigureActivity, appWidgetId, selectedId)
                                
                                val is1x2 = providerClassName.contains("ReminderWidget1x2")
                                if (is1x2) {
                                    WidgetUpdateHelper.update1x2WidgetWithData(this@WidgetConfigureActivity, appWidgetManager, appWidgetId, opacity, selectedId, items)
                                } else {
                                    WidgetUpdateHelper.update2x2WidgetWithData(this@WidgetConfigureActivity, appWidgetManager, appWidgetId, opacity, selectedId, items)
                                }
                                val updateIntent = Intent(this@WidgetConfigureActivity, if (is1x2) ReminderWidget1x2::class.java else ReminderWidget2x2::class.java).apply {
                                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                }
                                sendBroadcast(updateIntent)
                            } else {
                                WidgetConfigStore.save4x2Config(this@WidgetConfigureActivity, appWidgetId, filterType, customIds)
                                
                                val updateIntent = Intent(this@WidgetConfigureActivity, ReminderWidget4x2::class.java).apply {
                                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                }
                                sendBroadcast(updateIntent)
                                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
                            }

                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, resultValue)
                            finish()
                        },
                        loadReminders = { repository.getAllRemindersStream().first() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigureScreen(
    isSingleSelection: Boolean,
    isCountdownWidget: Boolean = false,
    initialOpacity: Int = 100,
    initialSelectedId: Int = -1,
    initialFilterType: String = "all",
    initialCustomIds: Set<Int> = emptySet(),
    initialPhotoPaths: List<String> = emptyList(),
    initialRotationHours: Int = 24,
    initialAccentColor: Int = 0xFF76E4F7.toInt(),
    onCancel: () -> Unit,
    onSave: (selectedId: Int, filterType: String, customIds: Set<Int>, opacity: Int, reminders: List<ReminderItem>, photoPaths: List<String>, rotationHours: Int, accentColor: Int) -> Unit,
    loadReminders: suspend () -> List<ReminderItem>
) {
    var reminders by remember { mutableStateOf<List<ReminderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val widgetCardShape = RoundedCornerShape(16.dp)
    // Transparency State (0-100)
    var opacity by remember { mutableStateOf(initialOpacity.toFloat()) }

    // Selection States for 1x2 / 2x2 / Countdown
    var selectedReminderId by remember { mutableIntStateOf(initialSelectedId) }
    // Selection States for 4x2
    var filterType by remember { mutableStateOf(initialFilterType) }
    var customSelectedIds by remember { mutableStateOf(initialCustomIds) }
    // Countdown Widget States
    var photoPaths by remember { mutableStateOf(initialPhotoPaths) }
    var rotationHours by remember { mutableStateOf(initialRotationHours.toFloat()) }
    var accentColor by remember { mutableIntStateOf(initialAccentColor) }
    val context = LocalContext.current
    val photoStorage = remember { WidgetPhotoStorage(context) }
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.IO) {
                val newPaths = photoStorage.replacePhotos(uris.toList(), photoPaths)
                withContext(Dispatchers.Main) {
                    photoPaths = newPaths
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reminders = loadReminders()
        if (reminders.isNotEmpty() && selectedReminderId == -1) {
            selectedReminderId = reminders.first().id
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSingleSelection) "配置桌面小部件" else "配置列表展示") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无任何提醒，请先在应用内添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onCancel) {
                        Text("返回")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                // Opacity Selection Card
                Card(
                    shape = widgetCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "背景不透明度",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${opacity.toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0f..100f,
                            steps = 19
                        )
                        Text(
                            text = "调整背景的不透明度，数值越低越透明",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                // Countdown Widget 专属配置
                if (isCountdownWidget) {
                    // 背景照片卡片
                    Card(
                        shape = widgetCardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "背景照片",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (photoPaths.isEmpty()) "未选择" else "已选 ${photoPaths.size} 张",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { pickMediaLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("选择照片")
                                }
                                if (photoPaths.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                        GlobalScope.launch(Dispatchers.IO) {
                                            photoStorage.clearPhotos(photoPaths)
                                            withContext(Dispatchers.Main) {
                                                photoPaths = emptyList()
                                            }
                                        }
                                    },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("清除照片")
                                    }
                                }
                            }
                            if (photoPaths.size > 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "轮换间隔",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${rotationHours.toInt()} 小时",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = rotationHours,
                                    onValueChange = { rotationHours = it },
                                    valueRange = 1f..168f,
                                    steps = 166
                                )
                                Text(
                                    text = "多张照片时按此间隔自动轮换",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // 主题色条选择卡片
                    Card(
                        shape = widgetCardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "主题色条",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val accentColors = listOf(
                                    0xFF76E4F7.toInt() to "极光",
                                    0xFFFF8A5B.toInt() to "余烬",
                                    0xFFC5A3FF.toInt() to "兰花",
                                    0xFF4ADE80.toInt() to "翠绿",
                                    0xFFFBBF24.toInt() to "琥珀",
                                    0xFF60A5FA.toInt() to "天蓝"
                                )
                                accentColors.forEach { (color, label) ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable { accentColor = color }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    color = androidx.compose.ui.graphics.Color(color),
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                )
                                                .border(
                                                    width = if (accentColor == color) 3.dp else 0.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (accentColor == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isCountdownWidget || isSingleSelection) {
                    Text(
                        text = "选择要显示的提醒：",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reminders) { reminder ->
                            val isSelected = reminder.id == selectedReminderId
                            Card(
                                shape = widgetCardShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(widgetCardShape)
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { selectedReminderId = reminder.id }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reminder.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = when (reminder.type) {
                                                ReminderType.ANNUAL -> "倒数日 · ${reminder.date}"
                                                ReminderType.COUNT_UP -> "正数日 · ${reminder.date}"
                                                ReminderType.BIRTHDAY -> "生日 · ${reminder.date}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedReminderId = reminder.id }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "选择展示哪些提醒：",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val filterOptions = listOf(
                        "all" to "展示所有提醒",
                        "countdown" to "仅展示倒数日",
                        "countup" to "仅展示正数日",
                        "birthday" to "仅展示生日",
                        "custom" to "自由选择"
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                shape = widgetCardShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    filterOptions.forEach { (optionType, label) ->
                                        val itemShape = RoundedCornerShape(8.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(itemShape)
                                                .clickable { filterType = optionType }
                                                .padding(vertical = 10.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = filterType == optionType,
                                                onClick = { filterType = optionType }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (filterType == optionType) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (filterType == "custom") {
                            item {
                                Text(
                                    text = "请勾选要显示的提醒（可多选）：",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                )
                            }

                            items(reminders) { reminder ->
                                val isChecked = customSelectedIds.contains(reminder.id)
                                Card(
                                    shape = widgetCardShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(widgetCardShape)
                                        .clickable {
                                            customSelectedIds = if (isChecked) {
                                                customSelectedIds - reminder.id
                                            } else {
                                                customSelectedIds + reminder.id
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = reminder.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = when (reminder.type) {
                                                    ReminderType.ANNUAL -> "倒数日"
                                                    ReminderType.COUNT_UP -> "正数日"
                                                    ReminderType.BIRTHDAY -> "生日"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                customSelectedIds = if (checked == true) {
                                                    customSelectedIds + reminder.id
                                                } else {
                                                    customSelectedIds - reminder.id
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (isCountdownWidget) {
                                onSave(selectedReminderId, "", emptySet(), opacity.toInt(), reminders, photoPaths, rotationHours.toInt(), accentColor)
                            } else if (isSingleSelection) {
                                onSave(selectedReminderId, "", emptySet(), opacity.toInt(), reminders, emptyList(), 24, 0)
                            } else {
                                onSave(-1, filterType, customSelectedIds, opacity.toInt(), reminders, emptyList(), 24, 0)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = if (isSingleSelection || isCountdownWidget) selectedReminderId != -1 else (filterType != "custom" || customSelectedIds.isNotEmpty())
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
}
