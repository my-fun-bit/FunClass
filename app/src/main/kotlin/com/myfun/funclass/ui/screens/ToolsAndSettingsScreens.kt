package com.myfun.funclass.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.myfun.funclass.BuildConfig
import com.myfun.funclass.reminder.FluidCloudNotifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.myfun.funclass.ui.theme.BackgroundColorExtractor
import com.myfun.funclass.ui.theme.FunClassColorEngine
import com.myfun.funclass.ui.theme.FunClassThemePreset
import com.myfun.funclass.ui.theme.LocalFunClassVisualStyle
import com.myfun.funclass.ui.theme.funClassCardColors
import com.myfun.funclass.ui.theme.funClassScaffoldContainerColor
import com.myfun.funclass.ui.theme.funClassTopBarColors
import com.myfun.funclass.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import com.myfun.funclass.core.model.DarkModePolicy
import com.myfun.funclass.core.model.HolidayAdjustType
import com.myfun.funclass.core.model.HolidayAdjustment
import com.myfun.funclass.core.model.ReminderSettings
import com.myfun.funclass.core.model.ScheduleAppearance
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.TimeScheme
import com.myfun.funclass.core.model.TimeSchemeGenerator
import com.myfun.funclass.core.model.TimeSchemeGeneratorConfig
import com.myfun.funclass.core.model.TimeSlot
import com.myfun.funclass.core.model.TimeSlotType
import com.myfun.funclass.ui.MainUiState
import com.myfun.funclass.ui.components.MaterialDatePickerModal
import com.myfun.funclass.ui.components.MaterialTimePickerModal
import com.myfun.funclass.ui.components.PickerFieldCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class SettingsSubRoute {
    TABLES, TIME_SCHEMES, APPEARANCE, HOLIDAYS, BATCH_TOOLS, REMINDERS, ABOUT_LICENSES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    state: MainUiState,
    onToggleHelperDescriptions: (Boolean) -> Unit = {},
    onNavigate: (SettingsSubRoute) -> Unit
) {
    val showHelp = state.showHelperDescriptions
    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("设置") }, colors = funClassTopBarColors()) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SettingsTile(
                    icon = Icons.Default.TableRows,
                    title = "多课表与学期",
                    subtitle = if (showHelp) "当前：${state.activeTable.name}（共 ${state.allTables.size} 个课表）" else null
                ) {
                    onNavigate(SettingsSubRoute.TABLES)
                }
            }
            item {
                SettingsTile(
                    icon = Icons.Default.AccessTime,
                    title = "作息时间与午休",
                    subtitle = if (showHelp) "当前：${state.activeTimeScheme.name}（大小课间、早中晚节数、午休调整）" else null
                ) {
                    onNavigate(SettingsSubRoute.TIME_SCHEMES)
                }
            }
            item {
                SettingsTile(
                    icon = Icons.Default.Palette,
                    title = "外观定制",
                    subtitle = if (showHelp) "马卡龙配色、背景取色、透明度、卡片风格与显示开关" else null
                ) {
                    onNavigate(SettingsSubRoute.APPEARANCE)
                }
            }
            item {
                SettingsTile(
                    icon = Icons.Default.EventBusy,
                    title = "节假日调休",
                    subtitle = if (showHelp) "已配置 ${state.holidayAdjustments.size} 条规则，含 2026/2027 法定节假日预设" else null
                ) {
                    onNavigate(SettingsSubRoute.HOLIDAYS)
                }
            }
            item {
                SettingsTile(
                    icon = Icons.Default.SwapHoriz,
                    title = "批量调课",
                    subtitle = if (showHelp) "全表周次平移、任意两星期课程一键对调" else null
                ) {
                    onNavigate(SettingsSubRoute.BATCH_TOOLS)
                }
            }
            item {
                SettingsTile(
                    icon = Icons.Default.NotificationsActive,
                    title = "提醒与静音",
                    subtitle = if (showHelp) {
                        if (state.reminderSettings.enabled) "已开启 · 提前 ${state.reminderSettings.courseMinutesBefore} 分钟" else "已关闭"
                    } else null
                ) {
                    onNavigate(SettingsSubRoute.REMINDERS)
                }
            }
            item {
                Card(
                    colors = funClassCardColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        SwitchRow("显示页面辅助说明", showHelp, onToggleHelperDescriptions)
                        if (showHelp) {
                            Text(
                                text = "关闭后将隐藏设置及导入导出页面的小字解释，界面更精简",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                SettingsTile(
                    icon = Icons.Default.Info,
                    title = "关于与开源许可",
                    subtitle = if (showHelp) "版本信息、MIT 协议与第三方开源组件许可" else null
                ) {
                    onNavigate(SettingsSubRoute.ABOUT_LICENSES)
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "FunClass v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Crafted by MyFun · 专注每一堂课",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SettingsTile(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Card(
        colors = funClassCardColors(),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// =========================================================================
// 1. Multi-Table Management
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableManageScreen(
    state: MainUiState,
    onSwitchTable: (Long) -> Unit,
    onSaveTable: (ScheduleTable) -> Unit,
    onDeleteTable: (Long) -> Unit,
    onBack: () -> Unit
) {
    var editingTable by remember { mutableStateOf<ScheduleTable?>(null) }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = { Text("多课表与学期管理") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        editingTable = ScheduleTable(
                            name = "新课表 ${state.allTables.size + 1}",
                            timeSchemeId = state.activeTimeScheme.id,
                            termStartDate = LocalDate.now().toString()
                        )
                    }) { Icon(Icons.Default.Add, contentDescription = "新建课表") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.allTables, key = { it.id }) { table ->
                val isCurrent = table.id == state.activeTable.id
                Card(
                    colors = funClassCardColors(),
                    modifier = Modifier.fillMaxWidth().clickable { onSwitchTable(table.id) }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (if (isCurrent) "★ " else "") + table.name,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "开学日：${table.termStartDate} · 共 ${table.maxWeeks} 周 · 每天 ${table.maxNodes} 节",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { editingTable = table }) { Text("编辑") }
                        if (state.allTables.size > 1) {
                            IconButton(onClick = { onDeleteTable(table.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        }
    }

    editingTable?.let { tbl ->
        var name by remember(tbl) { mutableStateOf(tbl.name) }
        var startDate by remember(tbl) { mutableStateOf(tbl.termStartLocalDate()) }
        var showStartDatePicker by remember { mutableStateOf(false) }
        var maxWeeks by remember(tbl) { mutableStateOf(tbl.maxWeeks.toString()) }
        var maxNodes by remember(tbl) { mutableStateOf(tbl.maxNodes.toString()) }
        var sundayFirst by remember(tbl) { mutableStateOf(tbl.sundayFirst) }

        AlertDialog(
            onDismissRequest = { editingTable = null },
            title = { Text(if (tbl.id == 0L) "新建课表" else "编辑课表设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课表名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    PickerFieldCard(
                        label = "第一周开学日期",
                        value = startDate.toString(),
                        icon = Icons.Default.CalendarToday,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showStartDatePicker = true }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = maxWeeks,
                            onValueChange = { maxWeeks = it.filter(Char::isDigit).take(2) },
                            label = { Text("总周数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxNodes,
                            onValueChange = { maxNodes = it.filter(Char::isDigit).take(2) },
                            label = { Text("每日节数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("以周日作为每周第一天", modifier = Modifier.weight(1f).padding(end = 12.dp))
                        Switch(checked = sundayFirst, onCheckedChange = { sundayFirst = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveTable(
                        tbl.copy(
                            name = name.ifBlank { "我的课表" },
                            termStartDate = startDate.toString(),
                            maxWeeks = maxWeeks.toIntOrNull()?.coerceIn(1, 30) ?: 20,
                            maxNodes = maxNodes.toIntOrNull()?.coerceIn(4, 20) ?: 12,
                            sundayFirst = sundayFirst
                        )
                    )
                    editingTable = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingTable = null }) { Text("取消") } }
        )

        if (showStartDatePicker) {
            MaterialDatePickerModal(
                initialDate = startDate,
                title = "选择第一周开学日期",
                onDismiss = { showStartDatePicker = false },
                onConfirm = { picked ->
                    startDate = picked
                    showStartDatePicker = false
                }
            )
        }
    }
}

// =========================================================================
// 2. Time Schemes & Custom Breaks
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSchemeScreen(
    state: MainUiState,
    onSaveTimeScheme: (TimeScheme, TimeSchemeGeneratorConfig?) -> Unit,
    onBack: () -> Unit
) {
    val scheme = state.activeTimeScheme
    val initialGen = state.activeTimeSchemeGeneratorConfig

    var schemeName by remember(scheme) { mutableStateOf(scheme.name) }
    var courseLen by remember(scheme, initialGen) { mutableStateOf(initialGen.courseMinutes.toString()) }
    var smallBreakLen by remember(scheme, initialGen) { mutableStateOf(initialGen.smallBreakMinutes.toString()) }
    var bigBreakLen by remember(scheme, initialGen) { mutableStateOf(initialGen.bigBreakMinutes.toString()) }

    var morningNodes by remember(scheme, initialGen) { mutableIntStateOf(initialGen.morningNodes) }
    var morningStart by remember(scheme, initialGen) { mutableStateOf(initialGen.morningStartTime) }
    var afternoonNodes by remember(scheme, initialGen) { mutableIntStateOf(initialGen.afternoonNodes) }
    var afternoonStart by remember(scheme, initialGen) { mutableStateOf(initialGen.afternoonStartTime) }
    var eveningNodes by remember(scheme, initialGen) { mutableIntStateOf(initialGen.eveningNodes) }
    var eveningStart by remember(scheme, initialGen) { mutableStateOf(initialGen.eveningStartTime) }
    var includeLunchBreak by remember(scheme, initialGen) { mutableStateOf(initialGen.includeLunchBreak) }

    val slots = remember(scheme) { mutableStateListOf<TimeSlot>().apply { addAll(scheme.slots) } }

    var showBreakDialog by remember { mutableStateOf(false) }
    var editingBreakTarget by remember { mutableStateOf<TimeSlot?>(null) }
    var pendingTimePicker by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }

    fun currentGeneratorConfig(): TimeSchemeGeneratorConfig {
        val lunchTitle = slots.firstOrNull {
            it.slotType == TimeSlotType.CUSTOM_BREAK && (it.title.contains("午休") || it.title.contains("中午"))
        }?.title ?: initialGen.lunchBreakTitle
        return TimeSchemeGeneratorConfig(
            courseMinutes = courseLen.toIntOrNull()?.coerceIn(15, 120) ?: 45,
            smallBreakMinutes = smallBreakLen.toIntOrNull()?.coerceIn(0, 60) ?: 10,
            bigBreakMinutes = bigBreakLen.toIntOrNull()?.coerceIn(0, 60) ?: 20,
            morningNodes = morningNodes,
            morningStartTime = TimeSlot.normalizeTime(morningStart),
            afternoonNodes = afternoonNodes,
            afternoonStartTime = TimeSlot.normalizeTime(afternoonStart),
            eveningNodes = eveningNodes,
            eveningStartTime = TimeSlot.normalizeTime(eveningStart),
            includeLunchBreak = includeLunchBreak,
            lunchBreakTitle = lunchTitle
        )
    }

    fun applyGeneratedSlots(andSave: Boolean) {
        val cfg = currentGeneratorConfig()
        val generated = TimeSchemeGenerator.generateSlots(
            schemeId = scheme.id,
            config = cfg,
            existingCustomBreaks = slots.filter { it.slotType == TimeSlotType.CUSTOM_BREAK }
        )
        slots.clear()
        slots.addAll(generated)
        if (andSave) {
            onSaveTimeScheme(
                scheme.copy(
                    name = schemeName.trim().ifBlank { "默认作息" },
                    defaultCourseMinutes = cfg.courseMinutes,
                    defaultBreakMinutes = cfg.smallBreakMinutes,
                    slots = generated
                ),
                cfg
            )
        }
    }

    fun saveCurrent() {
        val cfg = currentGeneratorConfig()
        val normalizedSlots = slots.map {
            it.copy(
                schemeId = scheme.id,
                startTime = TimeSlot.normalizeTime(it.startTime),
                endTime = TimeSlot.normalizeTime(it.endTime)
            )
        }
        val hasLunchNow = normalizedSlots.any {
            it.slotType == TimeSlotType.CUSTOM_BREAK && (it.title.contains("午休") || it.title.contains("中午"))
        }
        onSaveTimeScheme(
            scheme.copy(
                name = schemeName.trim().ifBlank { "默认作息" },
                defaultCourseMinutes = cfg.courseMinutes,
                defaultBreakMinutes = cfg.smallBreakMinutes,
                slots = normalizedSlots
            ),
            cfg.copy(includeLunchBreak = hasLunchNow)
        )
    }

    val totalConfiguredNodes = morningNodes + afternoonNodes + eveningNodes
    val regularSlots = slots.filter { it.slotType == TimeSlotType.REGULAR }.sortedBy { it.nodeIndex }
    val customBreaks = slots.filter { it.slotType == TimeSlotType.CUSTOM_BREAK }
        .sortedWith(compareBy<TimeSlot> { it.nodeIndex }.thenBy { it.sortOrder })

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = { Text("作息时间与午休设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = { saveCurrent() }) { Text("保存", fontWeight = FontWeight.Bold) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = schemeName,
                    onValueChange = { schemeName = it },
                    label = { Text("作息时间表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Section 1: Smart Period & Break Generator
            item {
                Card(
                    colors = funClassCardColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("一键分时段生成作息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (state.showHelperDescriptions) {
                            Text(
                                "支持将「同大节两小节之间」的小课间与「不同两大节之间」的大课间分开设置，并独立设定上午/下午/晚上的节数与开始时间。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = courseLen,
                                onValueChange = { courseLen = it.filter(Char::isDigit).take(3) },
                                label = { Text("单节/分", maxLines = 1, softWrap = false) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = smallBreakLen,
                                onValueChange = { smallBreakLen = it.filter(Char::isDigit).take(2) },
                                label = { Text("小课间", maxLines = 1, softWrap = false) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = bigBreakLen,
                                onValueChange = { bigBreakLen = it.filter(Char::isDigit).take(2) },
                                label = { Text("大课间", maxLines = 1, softWrap = false) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (state.showHelperDescriptions) {
                            Text(
                                "单位均为分钟；小课间用于第1-2节、3-4节之间，大课间用于第2-3节、6-7节之间",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider()

                        PeriodConfigRow(
                            label = "上午",
                            nodeCount = morningNodes,
                            startTime = morningStart,
                            canDecrease = morningNodes > 0 && totalConfiguredNodes > 4,
                            canIncrease = morningNodes < 10 && totalConfiguredNodes < 20,
                            onDecrease = { morningNodes-- },
                            onIncrease = { morningNodes++ },
                            onPickTime = {
                                pendingTimePicker = Triple("上午第一节开始时间", morningStart) { morningStart = it }
                            }
                        )
                        PeriodConfigRow(
                            label = "下午",
                            nodeCount = afternoonNodes,
                            startTime = afternoonStart,
                            canDecrease = afternoonNodes > 0 && totalConfiguredNodes > 4,
                            canIncrease = afternoonNodes < 10 && totalConfiguredNodes < 20,
                            onDecrease = { afternoonNodes-- },
                            onIncrease = { afternoonNodes++ },
                            onPickTime = {
                                pendingTimePicker = Triple("下午第一节开始时间", afternoonStart) { afternoonStart = it }
                            }
                        )
                        PeriodConfigRow(
                            label = "晚上",
                            nodeCount = eveningNodes,
                            startTime = eveningStart,
                            canDecrease = eveningNodes > 0 && totalConfiguredNodes > 4,
                            canIncrease = eveningNodes < 10 && totalConfiguredNodes < 20,
                            onDecrease = { eveningNodes-- },
                            onIncrease = { eveningNodes++ },
                            onPickTime = {
                                pendingTimePicker = Triple("晚上第一节开始时间", eveningStart) { eveningStart = it }
                            }
                        )

                        Text(
                            text = "共 $totalConfiguredNodes 节（上午 $morningNodes · 下午 $afternoonNodes · 晚上 $eveningNodes），保存时自动同步当前课表每日节数",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SwitchRow(
                            label = "生成时自动包含午休（插在上午与下午之间）",
                            checked = includeLunchBreak,
                            onCheckedChange = { includeLunchBreak = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { applyGeneratedSlots(andSave = false) },
                                modifier = Modifier.weight(1f)
                            ) { Text("预览生成") }
                            Button(
                                onClick = { applyGeneratedSlots(andSave = true) },
                                modifier = Modifier.weight(1.3f)
                            ) { Text("一键生成并保存") }
                        }
                    }
                }
            }

            // Section 2: Lunch Break & Custom Breaks (Prominent, near top)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "午休与自定义时段",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            editingBreakTarget = null
                            showBreakDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("+ 添加时段", maxLines = 1, softWrap = false) }
                }
            }

            if (customBreaks.isEmpty()) {
                item {
                    Card(
                        colors = funClassCardColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "当前未设置午休或自定义时段，课表网格不会显示午休标签。如需添加请点击右上角「+ 添加时段」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                items(customBreaks) { brk ->
                    Card(
                        colors = funClassCardColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${brk.title}（第 ${brk.nodeIndex} 节后）", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${brk.startTime} - ${brk.endTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                editingBreakTarget = brk
                                showBreakDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑时段")
                            }
                            IconButton(onClick = {
                                slots.remove(brk)
                                if (brk.title.contains("午休") || brk.title.contains("中午")) {
                                    includeLunchBreak = false
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除时段", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Section 3: Individual Regular Node Fine-Tuning
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "各节次具体时间（共 ${regularSlots.size} 节）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    TextButton(onClick = {
                        val dur = courseLen.toIntOrNull()?.coerceIn(15, 120) ?: 45
                        for (i in slots.indices) {
                            val s = slots[i]
                            if (s.slotType == TimeSlotType.REGULAR) {
                                val st = s.startLocalTime()
                                val et = st.plusMinutes(dur.toLong())
                                slots[i] = s.copy(endTime = et.format(DateTimeFormatter.ofPattern("HH:mm")))
                            }
                        }
                    }) { Text("统一课长", maxLines = 1, softWrap = false) }
                }
            }

            items(regularSlots, key = { "reg_${it.nodeIndex}" }) { slot ->
                val idx = slots.indexOf(slot)
                val periodHeader = if (regularSlots.size == totalConfiguredNodes) {
                    when (slot.nodeIndex) {
                        1 -> if (morningNodes > 0) "上午（第 1-$morningNodes 节）" else null
                        morningNodes + 1 -> if (afternoonNodes > 0) "下午（第 ${morningNodes + 1}-${morningNodes + afternoonNodes} 节）" else null
                        morningNodes + afternoonNodes + 1 -> if (eveningNodes > 0) "晚上（第 ${morningNodes + afternoonNodes + 1}-$totalConfiguredNodes 节）" else null
                        else -> null
                    }
                } else null

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (periodHeader != null) {
                        Text(
                            text = periodHeader,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("第 ${slot.nodeIndex} 节", modifier = Modifier.width(56.dp), fontWeight = FontWeight.Medium)
                        OutlinedButton(
                            onClick = {
                                pendingTimePicker = Triple("第 ${slot.nodeIndex} 节开始时间", slot.startTime) { picked ->
                                    if (idx >= 0) {
                                        val dur = courseLen.toIntOrNull()?.coerceIn(15, 120) ?: 45
                                        val st = runCatching { LocalTime.parse(picked) }.getOrNull()
                                        val newEnd = st?.plusMinutes(dur.toLong())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: slot.endTime
                                        slots[idx] = slot.copy(startTime = picked, endTime = newEnd)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(slot.startTime)
                        }
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = {
                                pendingTimePicker = Triple("第 ${slot.nodeIndex} 节结束时间", slot.endTime) { picked ->
                                    if (idx >= 0) slots[idx] = slot.copy(endTime = picked)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(slot.endTime)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { saveCurrent() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存作息时间表")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Add / Edit Custom Break Dialog
    if (showBreakDialog) {
        val editing = editingBreakTarget
        val defaultAfter = (editing?.nodeIndex ?: morningNodes.coerceAtLeast(1)).toString()
        var title by remember(editing) { mutableStateOf(editing?.title ?: if (customBreaks.none { it.title.contains("午休") }) "午休" else "晚间休息") }
        var afterNode by remember(editing) { mutableStateOf(defaultAfter) }
        var start by remember(editing) { mutableStateOf(editing?.startTime ?: "12:00") }
        var end by remember(editing) { mutableStateOf(editing?.endTime ?: "13:50") }
        val maxAfterNode = (regularSlots.size - 1).coerceAtLeast(1)

        AlertDialog(
            onDismissRequest = {
                showBreakDialog = false
                editingBreakTarget = null
            },
            title = { Text(if (editing == null) "新增午休 / 自定义时段" else "编辑时段「${editing.title}」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("时段名称（如 午休、晚休）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = afterNode,
                        onValueChange = { afterNode = it.filter(Char::isDigit).take(2) },
                        label = { Text("插入在第几节之后 (1-$maxAfterNode)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pendingTimePicker = Triple("时段开始时间", start) { start = it } },
                            modifier = Modifier.weight(1f)
                        ) { Text("开始 $start") }
                        OutlinedButton(
                            onClick = { pendingTimePicker = Triple("时段结束时间", end) { end = it } },
                            modifier = Modifier.weight(1f)
                        ) { Text("结束 $end") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newSlot = TimeSlot(
                        schemeId = scheme.id,
                        slotType = TimeSlotType.CUSTOM_BREAK,
                        nodeIndex = afterNode.toIntOrNull()?.coerceIn(1, maxAfterNode) ?: morningNodes.coerceIn(1, maxAfterNode),
                        title = title.trim().ifBlank { "午休" },
                        startTime = TimeSlot.normalizeTime(start),
                        endTime = TimeSlot.normalizeTime(end)
                    )
                    if (editing != null) {
                        val idx = slots.indexOf(editing)
                        if (idx >= 0) slots[idx] = newSlot else slots.add(newSlot)
                    } else {
                        slots.add(newSlot)
                    }
                    if (newSlot.title.contains("午休") || newSlot.title.contains("中午")) {
                        includeLunchBreak = true
                    }
                    showBreakDialog = false
                    editingBreakTarget = null
                }) { Text(if (editing == null) "添加" else "确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBreakDialog = false
                    editingBreakTarget = null
                }) { Text("取消") }
            }
        )
    }

    // Material 3 24-hour TimePicker Dialog (clock dial + keyboard toggle)
    pendingTimePicker?.let { (dialogTitle, initialStr, onPicked) ->
        MaterialTimePickerModal(
            initialTime = initialStr,
            title = dialogTitle,
            onDismiss = { pendingTimePicker = null },
            onConfirm = { formatted ->
                onPicked(formatted)
                pendingTimePicker = null
            }
        )
    }
}

@Composable
private fun PeriodConfigRow(
    label: String,
    nodeCount: Int,
    startTime: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onPickTime: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, modifier = Modifier.width(38.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        IconButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "减少节数")
        }
        Text(
            text = "$nodeCount 节",
            modifier = Modifier.widthIn(min = 46.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
        IconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Add, contentDescription = "增加节数")
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onPickTime,
            enabled = nodeCount > 0,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (nodeCount > 0) "开始 $startTime" else "无课",
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// =========================================================================
// 3. Appearance Customization (Macaron Themes, Wallpaper Extraction & Alpha)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    state: MainUiState,
    onUpdateAppearance: (ScheduleAppearance) -> Unit,
    onOpenTimeScheme: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = state.activeTable.appearance
    val latestApp by rememberUpdatedState(app)
    val visual = LocalFunClassVisualStyle.current
    val coroutineScope = rememberCoroutineScope()

    var extractedSwatches by remember(app.backgroundUri) { mutableStateOf<List<String>>(emptyList()) }
    var isExtracting by remember(app.backgroundUri) { mutableStateOf(false) }
    var pendingAutoApplyUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(app.backgroundUri) {
        val bgUri = app.backgroundUri
        if (bgUri.isNullOrBlank()) {
            extractedSwatches = emptyList()
            isExtracting = false
            return@LaunchedEffect
        }
        isExtracting = true
        val result = BackgroundColorExtractor.extractFromUri(context, Uri.parse(bgUri))
        isExtracting = false
        if (result != null) {
            extractedSwatches = result.swatchHexList
            if (pendingAutoApplyUri == bgUri) {
                pendingAutoApplyUri = null
                if (latestApp.autoExtractThemeFromBg) {
                    onUpdateAppearance(
                        latestApp.copy(
                            themePreset = "custom",
                            customThemeColorHex = result.primaryHex,
                            useDynamicColor = false
                        )
                    )
                }
            }
        } else if (pendingAutoApplyUri == bgUri) {
            pendingAutoApplyUri = null
        }
    }

    val pickBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val uriStr = uri.toString()
            pendingAutoApplyUri = uriStr
            onUpdateAppearance(latestApp.copy(backgroundUri = uriStr))
        }
    }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = { Text("外观定制") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            // Real-time Mini Preview
            AppearanceMiniPreview(
                tableName = state.activeTable.name,
                weekNumber = state.currentAcademicWeek,
                appearance = app,
                isDark = visual.isDarkTheme
            )

            // Section 1: Fresh Macaron & Classic Themes
            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("清新配色与主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("清新马卡龙", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FunClassColorEngine.macaronPresets().forEach { preset ->
                            ThemePresetOrb(
                                preset = preset,
                                selected = !app.useDynamicColor && app.themePreset == preset.id,
                                isDark = visual.isDarkTheme,
                                onClick = {
                                    onUpdateAppearance(
                                        app.copy(themePreset = preset.id, useDynamicColor = false)
                                    )
                                }
                            )
                        }
                    }

                    Text("经典色系", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FunClassColorEngine.classicPresets().forEach { preset ->
                            ThemePresetOrb(
                                preset = preset,
                                selected = !app.useDynamicColor && app.themePreset == preset.id,
                                isDark = visual.isDarkTheme,
                                onClick = {
                                    onUpdateAppearance(
                                        app.copy(themePreset = preset.id, useDynamicColor = false)
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider()
                    Text("深浅模式", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkModePolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = app.darkModePolicy == policy,
                                onClick = { onUpdateAppearance(app.copy(darkModePolicy = policy)) },
                                label = {
                                    Text(
                                        when (policy) {
                                            DarkModePolicy.SYSTEM -> "跟随系统"
                                            DarkModePolicy.LIGHT -> "浅色"
                                            DarkModePolicy.DARK -> "深色"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    SwitchRow("Android 12+ 动态取色（叠加柔和淡底）", app.useDynamicColor) {
                        onUpdateAppearance(app.copy(useDynamicColor = it))
                    }
                }
            }

            // Section 2: Wallpaper, Smart Extraction & Translucent Chrome
            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("背景图、智能取色与透明度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickBgLauncher.launch(arrayOf("image/*")) }) {
                            Text(if (app.backgroundUri.isNullOrBlank()) "选择自定义背景图" else "更换背景图")
                        }
                        if (!app.backgroundUri.isNullOrBlank()) {
                            OutlinedButton(onClick = { onUpdateAppearance(app.copy(backgroundUri = null)) }) {
                                Text("清除背景")
                            }
                        }
                    }

                    if (!app.backgroundUri.isNullOrBlank()) {
                        SwitchRow("选图时自动从背景提取配色", app.autoExtractThemeFromBg) {
                            onUpdateAppearance(app.copy(autoExtractThemeFromBg = it))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("背景提取推荐配色", style = MaterialTheme.typography.labelLarge)
                            TextButton(
                                onClick = {
                                    val bgUri = app.backgroundUri ?: return@TextButton
                                    coroutineScope.launch {
                                        isExtracting = true
                                        val res = BackgroundColorExtractor.extractFromUri(context, Uri.parse(bgUri))
                                        isExtracting = false
                                        if (res != null) {
                                            extractedSwatches = res.swatchHexList
                                            onUpdateAppearance(
                                                latestApp.copy(
                                                    themePreset = "custom",
                                                    customThemeColorHex = res.primaryHex,
                                                    useDynamicColor = false
                                                )
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isExtracting) "提取中..." else "一键取色")
                            }
                        }

                        val displaySwatches = remember(extractedSwatches, app.customThemeColorHex) {
                            val list = extractedSwatches.toMutableList()
                            val cur = app.customThemeColorHex
                            if (!cur.isNullOrBlank() && list.none { it.equals(cur, ignoreCase = true) }) {
                                list.add(0, cur)
                            }
                            list.distinct()
                        }
                        if (displaySwatches.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                displaySwatches.forEach { hex ->
                                    val isSelected = !app.useDynamicColor &&
                                        app.themePreset == "custom" &&
                                        hex.equals(app.customThemeColorHex, ignoreCase = true)
                                    val swatchColor = parseHexColor(hex)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.7f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                onUpdateAppearance(
                                                    app.copy(
                                                        themePreset = "custom",
                                                        customThemeColorHex = hex,
                                                        useDynamicColor = false
                                                    )
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            val bestInk = Color(FunClassColorEngine.bestOnColorArgb(swatchColor.toArgb()))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = bestInk, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
                        Text("背景图融合遮罩浓度：${(app.backgroundDimAlpha * 100).toInt()}%")
                        Slider(
                            value = app.backgroundDimAlpha,
                            onValueChange = { onUpdateAppearance(app.copy(backgroundDimAlpha = it)) },
                            valueRange = 0f..0.75f
                        )

                        Text("顶栏 / 底栏与时间轴不透明度：${(app.chromeSurfaceAlpha * 100).toInt()}%")
                        Slider(
                            value = app.chromeSurfaceAlpha,
                            onValueChange = { onUpdateAppearance(app.copy(chromeSurfaceAlpha = it)) },
                            valueRange = 0f..1.0f
                        )
                        if (state.showHelperDescriptions) {
                            Text(
                                text = "调低不透明度可让顶栏、星期栏、左侧时间栏与底部导航栏呈现通透融合质感，不再生硬遮挡背景图。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SwitchRow("其余页面也柔和显示背景图", app.applyBackgroundGlobally) {
                            onUpdateAppearance(app.copy(applyBackgroundGlobally = it))
                        }
                    } else if (state.showHelperDescriptions) {
                        Text(
                            text = "选择壁纸后，可自动从图片提取主题配色，并独立调节顶底栏透明度与融合遮罩浓度。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 3: Course Card Style & Geometry
            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("课程卡片样式与尺寸", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("柔和马卡龙课程卡片", app.pastelCourseStyle) {
                        onUpdateAppearance(app.copy(pastelCourseStyle = it))
                    }
                    if (state.showHelperDescriptions) {
                        Text(
                            text = "开启后课程方块使用粉彩软糖底色与精致柔边框，搭配浅色系和背景图更清新和谐；关闭则为高饱和纯色白字。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text("格子高度：${app.itemHeightDp} dp")
                    Slider(
                        value = app.itemHeightDp.toFloat(),
                        onValueChange = { onUpdateAppearance(app.copy(itemHeightDp = it.toInt())) },
                        valueRange = 42f..120f
                    )

                    Text("课程卡片不透明度：${(app.itemAlpha * 100).toInt()}%")
                    Slider(value = app.itemAlpha, onValueChange = { onUpdateAppearance(app.copy(itemAlpha = it)) }, valueRange = 0.35f..1.0f)

                    Text("非本周课程不透明度：${(app.otherWeekCourseAlpha * 100).toInt()}%")
                    Slider(value = app.otherWeekCourseAlpha, onValueChange = { onUpdateAppearance(app.copy(otherWeekCourseAlpha = it)) }, valueRange = 0.1f..0.7f)

                    Text("卡片圆角：${app.cornerRadiusDp} dp")
                    Slider(value = app.cornerRadiusDp.toFloat(), onValueChange = { onUpdateAppearance(app.copy(cornerRadiusDp = it.toInt())) }, valueRange = 0f..20f)

                    Text("课程字号：${app.itemTextSizeSp} sp")
                    Slider(value = app.itemTextSizeSp.toFloat(), onValueChange = { onUpdateAppearance(app.copy(itemTextSizeSp = it.toInt())) }, valueRange = 9f..16f)
                }
            }

            // Section 4: Card Line Limits
            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("卡片文字行数上限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.showHelperDescriptions) {
                        Text(
                            "拉高格子高度后，放宽课程名称与上课地点的行数上限（或选择「不限」）即可充分利用格子空间显示完整信息。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("课程名称最大行数：${if (app.courseNameMaxLines <= 0) "不限" else "${app.courseNameMaxLines} 行"}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2 to "2行", 3 to "3行", 4 to "4行", 5 to "5行", 0 to "不限").forEach { (lines, label) ->
                            FilterChip(
                                selected = app.courseNameMaxLines == lines,
                                onClick = { onUpdateAppearance(app.copy(courseNameMaxLines = lines)) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text("上课地点最大行数：${if (app.locationMaxLines <= 0) "不限" else "${app.locationMaxLines} 行"}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "1行", 2 to "2行", 3 to "3行", 4 to "4行", 5 to "5行", 0 to "不限").forEach { (lines, label) ->
                            FilterChip(
                                selected = app.locationMaxLines == lines,
                                onClick = { onUpdateAppearance(app.copy(locationMaxLines = lines)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Section 5: Grid & Display Toggles
            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("显示开关", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("显示周六", app.showSaturday) { onUpdateAppearance(app.copy(showSaturday = it)) }
                    SwitchRow("显示周日", app.showSunday) { onUpdateAppearance(app.copy(showSunday = it)) }
                    SwitchRow("显示非本周课程（灰显）", app.showOtherWeekCourse) { onUpdateAppearance(app.copy(showOtherWeekCourse = it)) }
                    SwitchRow("显示左侧节次栏", app.showTimeBar) { onUpdateAppearance(app.copy(showTimeBar = it)) }
                    SwitchRow("显示每节上下课时间", app.showTime) { onUpdateAppearance(app.copy(showTime = it)) }
                    SwitchRow("显示午休/自定义时段标识", app.showCustomBreaks) { onUpdateAppearance(app.copy(showCustomBreaks = it)) }
                    OutlinedButton(onClick = onOpenTimeScheme, modifier = Modifier.fillMaxWidth()) {
                        Text("调整或删除午休时间（作息时间设置）")
                    }
                    SwitchRow("显示网格线", app.showGrid) { onUpdateAppearance(app.copy(showGrid = it)) }
                    SwitchRow("网格使用虚线", app.useDottedLine) { onUpdateAppearance(app.copy(useDottedLine = it)) }
                    SwitchRow("显示上课教室", app.showLocation) { onUpdateAppearance(app.copy(showLocation = it)) }
                    SwitchRow("教室前加 @ 前缀", app.showRoomPrefix) { onUpdateAppearance(app.copy(showRoomPrefix = it)) }
                    SwitchRow("显示任课教师", app.showTeacher) { onUpdateAppearance(app.copy(showTeacher = it)) }
                    SwitchRow("显示课程备注", app.showRemark) { onUpdateAppearance(app.copy(showRemark = it)) }
                    SwitchRow("文字水平居中", app.centerHorizontal) { onUpdateAppearance(app.copy(centerHorizontal = it)) }
                    SwitchRow("文字垂直居中", app.centerVertical) { onUpdateAppearance(app.copy(centerVertical = it)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemePresetOrb(
    preset: FunClassThemePreset,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = parseHexColor(preset.primaryHex)
    val tintArgb = FunClassColorEngine.parseHexArgb(preset.surfaceTintHex)
    val haloColor = Color(
        FunClassColorEngine.lerpArgb(
            if (isDark) 0xFF2B2734.toInt() else 0xFFFFFFFF.toInt(),
            tintArgb,
            0.30f
        )
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(haloColor)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(primaryColor),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    val ink = Color(FunClassColorEngine.bestOnColorArgb(primaryColor.toArgb()))
                    Icon(Icons.Default.Check, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = preset.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun AppearanceMiniPreview(
    tableName: String,
    weekNumber: Int,
    appearance: ScheduleAppearance,
    isDark: Boolean
) {
    val hasBg = !appearance.backgroundUri.isNullOrBlank()
    val topBarBg = if (hasBg) {
        MaterialTheme.colorScheme.surface.copy(alpha = appearance.chromeSurfaceAlpha.coerceIn(0f, 1f))
    } else {
        MaterialTheme.colorScheme.surface
    }
    val bottomBarBg = if (hasBg) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = appearance.chromeSurfaceAlpha.coerceIn(0f, 1f))
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val sideBg = if (hasBg) {
        MaterialTheme.colorScheme.surface.copy(alpha = (appearance.chromeSurfaceAlpha * 0.78f).coerceIn(0f, 1f))
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        if (hasBg) {
            AsyncImage(
                model = Uri.parse(appearance.backgroundUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = appearance.backgroundDimAlpha.coerceIn(0f, 0.85f)))
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Mini Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(topBarBg)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tableName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "第 $weekNumber 周 · 预览",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Mini Timetable Grid
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxHeight()
                        .background(sideBg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("1", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("2", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val sampleCourses = listOf(
                    "高等数学" to "#F48FB1",
                    "大学物理" to "#7AB8F5",
                    "数据结构" to "#A990E8"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sampleCourses.forEach { (title, hex) ->
                        val colors = FunClassColorEngine.resolveCourseCardColors(
                            baseColorArgb = FunClassColorEngine.parseHexArgb(hex),
                            isDark = isDark,
                            pastelStyle = appearance.pastelCourseStyle,
                            isMutedOtherWeek = false,
                            alpha = appearance.itemAlpha
                        )
                        val shape = RoundedCornerShape(appearance.cornerRadiusDp.coerceIn(0, 14).dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(shape)
                                .background(colors.background)
                                .then(
                                    if (colors.border != null) Modifier.border(0.8.dp, colors.border, shape)
                                    else Modifier
                                )
                                .padding(6.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Mini Bottom Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(bottomBarBg),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp).size(14.dp)
                    )
                }
                Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// =========================================================================
// 4. Holiday Adjustments (Non-destructive Overlay + Statutory Presets)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HolidayAdjustScreen(
    state: MainUiState,
    onApplyPreset: (Int) -> Unit,
    onClearPreset: (Int) -> Unit,
    onSaveAdjustment: (HolidayAdjustment) -> Unit,
    onDeleteAdjustment: (Long) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = { Text("节假日调休") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "新增调休") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.showHelperDescriptions) {
                item {
                    Text(
                        "调休规则不会改动原始课程数据，课表、桌面小组件、提醒与 .ics 导出均自动生效，随时可撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(onClick = { onApplyPreset(2026) }) { Text("导入 2026 法定调休", maxLines = 1) }
                    OutlinedButton(onClick = { onApplyPreset(2027) }) { Text("导入 2027 预设", maxLines = 1) }
                    TextButton(onClick = { onClearPreset(2026) }) { Text("清除预设", maxLines = 1) }
                }
            }
            items(state.holidayAdjustments, key = { it.id }) { adj ->
                Card(
                    colors = funClassCardColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${adj.targetDate} · ${adj.label.ifBlank { if (adj.adjustType == HolidayAdjustType.OFF) "放假停课" else "调休补课" }}", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (adj.adjustType == HolidayAdjustType.OFF) "类型：停课放假" else "类型：补上 ${adj.sourceDate} 的课程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteAdjustment(adj.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "撤销")
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var targetDate by remember { mutableStateOf(LocalDate.now()) }
        var isShift by remember { mutableStateOf(false) }
        var sourceDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
        var label by remember { mutableStateOf("学校临时调休") }
        var pickingHolidayDate by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加自定义调休规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isShift, onClick = { isShift = false }, label = { Text("放假停课") })
                        FilterChip(selected = isShift, onClick = { isShift = true }, label = { Text("补上某日课程") })
                    }
                    PickerFieldCard(
                        label = "生效日期",
                        value = targetDate.toString(),
                        icon = Icons.Default.CalendarToday,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { pickingHolidayDate = "target" }
                    )
                    if (isShift) {
                        PickerFieldCard(
                            label = "补上哪一天的课",
                            value = sourceDate.toString(),
                            icon = Icons.Default.CalendarToday,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { pickingHolidayDate = "source" }
                        )
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("备注标签") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveAdjustment(
                        HolidayAdjustment(
                            tableId = 0L,
                            targetDate = targetDate.toString(),
                            adjustType = if (isShift) HolidayAdjustType.SHIFT else HolidayAdjustType.OFF,
                            sourceDate = if (isShift) sourceDate.toString() else "",
                            label = label.trim(),
                            isPreset = false
                        )
                    )
                    showAddDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )

        pickingHolidayDate?.let { field ->
            MaterialDatePickerModal(
                initialDate = if (field == "target") targetDate else sourceDate,
                title = if (field == "target") "选择调休生效日期" else "选择要补上的原课程日期",
                onDismiss = { pickingHolidayDate = null },
                onConfirm = { picked ->
                    if (field == "target") targetDate = picked else sourceDate = picked
                    pickingHolidayDate = null
                }
            )
        }
    }
}

// =========================================================================
// 5. Batch Modify Tools
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatchToolsScreen(
    showHelperDescriptions: Boolean = true,
    onShiftWeeks: (Int) -> Unit,
    onSwapDays: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    var dayA by remember { mutableIntStateOf(1) }
    var dayB by remember { mutableIntStateOf(5) }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = { Text("批量调课") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("全表课程周次平移", fontWeight = FontWeight.Bold)
                    if (showHelperDescriptions) {
                        Text("开学推迟或提前时，一键将当前课表所有课程整体提前或顺延 1 周。", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { onShiftWeeks(-1) }, modifier = Modifier.weight(1f)) { Text("整体提前 1 周") }
                        Button(onClick = { onShiftWeeks(+1) }, modifier = Modifier.weight(1f)) { Text("整体顺延 1 周") }
                    }
                }
            }

            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("对调两日课程", fontWeight = FontWeight.Bold)
                    if (showHelperDescriptions) {
                        Text("将星期 A 与星期 B 的全部常规课程互换（若仅为临时放假调休，请使用「节假日调休」）。", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("星期 A：")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d -> FilterChip(selected = dayA == d, onClick = { dayA = d }, label = { Text(dayLabel(d)) }) }
                    }
                    Text("星期 B：")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d -> FilterChip(selected = dayB == d, onClick = { dayB = d }, label = { Text(dayLabel(d)) }) }
                    }
                    Button(
                        onClick = { onSwapDays(dayA, dayB) },
                        enabled = dayA != dayB,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("对调 ${dayLabel(dayA)} 与 ${dayLabel(dayB)}") }
                }
            }
        }
    }
}

// =========================================================================
// 6. Reminder, Android 16+ Live Updates & Auto-Mute Settings
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderSettingsScreen(
    state: MainUiState,
    checkCanScheduleExact: () -> Boolean,
    checkIsIgnoringBatteryOptimizations: () -> Boolean,
    checkHasDndPermission: () -> Boolean,
    onUpdateSettings: (ReminderSettings) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s = state.reminderSettings

    var hasNotifPerm by remember { mutableStateOf(FluidCloudNotifier.hasNotificationPermission(context)) }
    var canPostFluidCloud by remember { mutableStateOf(FluidCloudNotifier.canPostFluidCloud(context)) }
    var canScheduleExact by remember { mutableStateOf(checkCanScheduleExact()) }
    var isIgnoringBattery by remember { mutableStateOf(checkIsIgnoringBatteryOptimizations()) }
    var hasDndPermission by remember { mutableStateOf(checkHasDndPermission()) }
    var lastTestResult by remember { mutableStateOf<String?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        canPostFluidCloud = FluidCloudNotifier.canPostFluidCloud(context)
    }

    // Auto-refresh ALL permission statuses when user returns from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = FluidCloudNotifier.hasNotificationPermission(context)
                canPostFluidCloud = FluidCloudNotifier.canPostFluidCloud(context)
                canScheduleExact = checkCanScheduleExact()
                isIgnoringBattery = checkIsIgnoringBatteryOptimizations()
                hasDndPermission = checkHasDndPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = { Text("提醒与静音") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Android 16+ Live Updates Card
            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("状态栏实时胶囊（Android 16+ 实时更新）", fontWeight = FontWeight.Bold)
                    if (state.showHelperDescriptions) {
                        Text(
                            "基于 Android 16+ 标准 Live Updates 协议，课前提醒可直接以状态栏实时胶囊和锁屏卡片展示；Android 15 及以下机型自动降级为倒计时通知。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SwitchRow("开启实时活动胶囊", s.fluidCloudEnabled) { onUpdateSettings(s.copy(fluidCloudEnabled = it)) }
                    SwitchRow("胶囊显示实时倒计时（关则显示教室短文本）", s.fluidCloudShowCountdown) {
                        onUpdateSettings(s.copy(fluidCloudShowCountdown = it))
                    }

                    Text(
                        text = "系统支持：${if (FluidCloudNotifier.isLiveUpdatesSupported()) "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ✓" else "当前系统低于 Android 16（将自动降级为倒计时通知）"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "实时通知权限：${if (canPostFluidCloud) "已开启 ✓" else "未开启（请在系统设置开启本应用的实时更新/实况通知权限）"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (canPostFluidCloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("授予通知权限", maxLines = 1, softWrap = false) }
                        }
                        OutlinedButton(
                            onClick = { FluidCloudNotifier.openFluidCloudPermissionSettings(context) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("实时通知权限设置", maxLines = 1, softWrap = false) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@Button
                                }
                                val sampleCourse = state.courses.firstOrNull()
                                val sampleSession = sampleCourse?.sessions?.firstOrNull()
                                val title = sampleCourse?.name ?: "高等数学"
                                val room = sampleSession?.room?.takeIf { it.isNotBlank() } ?: "教A-101"
                                val startMillis = System.currentTimeMillis() + 15 * 60_000L
                                val res = FluidCloudNotifier.postLiveReminder(
                                    context = context,
                                    notificationId = FluidCloudNotifier.TEST_NOTIFICATION_ID,
                                    courseTitle = title,
                                    subtitle = "15分钟后开始上课",
                                    shortChipText = room.take(7),
                                    isCourse = true,
                                    eventStartMillis = startMillis,
                                    countdown = true,
                                    useFluidCloud = s.fluidCloudEnabled,
                                    showCountdownInChip = s.fluidCloudShowCountdown,
                                    location = room
                                )
                                lastTestResult = res.message
                                onShowMessage(res.message)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("发送测试胶囊") }
                        OutlinedButton(
                            onClick = {
                                FluidCloudNotifier.dismiss(context, FluidCloudNotifier.TEST_NOTIFICATION_ID)
                                lastTestResult = "已关闭测试胶囊通知"
                            }
                        ) { Text("关闭测试") }
                    }

                    lastTestResult?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SwitchRow("开启上课与日程提醒", s.enabled) { onUpdateSettings(s.copy(enabled = it)) }
            SwitchRow("开启课前倒计时通知", s.countdownNotificationEnabled) { onUpdateSettings(s.copy(countdownNotificationEnabled = it)) }
            SwitchRow("同步提醒个人日程待办", s.calendarRemindersEnabled) { onUpdateSettings(s.copy(calendarRemindersEnabled = it)) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SwitchRow("强力防漏提醒（闹钟通道）", s.useAlarmClockChannel) { onUpdateSettings(s.copy(useAlarmClockChannel = it)) }
                if (state.showHelperDescriptions) {
                    Text(
                        "使用系统闹钟高优先级通道触发下一节提醒，可穿透休眠限制（状态栏可能会显示小闹钟图标）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SwitchRow("上课自动开启勿扰静音（下课恢复）", s.autoMuteDuringClass) { onUpdateSettings(s.copy(autoMuteDuringClass = it)) }

            Text("课前提前提醒分钟数：${s.courseMinutesBefore} 分钟")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { m ->
                    FilterChip(
                        selected = s.courseMinutesBefore == m,
                        onClick = { onUpdateSettings(s.copy(courseMinutesBefore = m)) },
                        label = { Text("${m}分钟") }
                    )
                }
            }

            Card(colors = funClassCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("系统权限与后台存活", fontWeight = FontWeight.Bold)
                    Text("通知权限：${if (hasNotifPerm) "已授权 ✓" else "未授权"}")
                    Text("精确闹钟权限：${if (canScheduleExact) "已授权（精准触发）✓" else "未授权（当前优雅降级为非精确提醒）"}")
                    if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        }) { Text("前往授予精确闹钟权限") }
                    }
                    Text(
                        text = "电池优化白名单：${if (isIgnoringBattery) "已放行（后台可稳定触发）✓" else "未放行（息屏或后台可能延迟提醒）"}",
                        color = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = {
                        FluidCloudNotifier.openBatteryOptimizationSettings(context)
                    }) { Text("电池与后台设置") }
                    Text("勿扰策略权限：${if (hasDndPermission) "已授权 ✓" else "未授权（开启自动静音前需要）"}")
                    if (!hasDndPermission) {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }) { Text("前往授予勿扰控制权限") }
                    }
                    if (state.showHelperDescriptions) {
                        Text(
                            "日常切到后台或被内存回收仍可按时唤醒；请勿在系统设置点击「强行停止」，并建议在电池/自启动设置中允许后台运行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
