package com.myfun.funclass.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myfun.funclass.core.model.CalendarEvent
import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.CourseColorPalette
import com.myfun.funclass.core.model.CourseSession
import com.myfun.funclass.core.model.RepeatType
import com.myfun.funclass.core.model.TimeSlot
import com.myfun.funclass.core.model.WeekSet
import com.myfun.funclass.ui.MainUiState
import com.myfun.funclass.ui.components.MaterialDatePickerModal
import com.myfun.funclass.ui.components.MaterialTimePickerModal
import com.myfun.funclass.ui.components.PickerFieldCard
import com.myfun.funclass.ui.theme.funClassCardColors
import com.myfun.funclass.ui.theme.funClassScaffoldContainerColor
import com.myfun.funclass.ui.theme.funClassTopBarColors
import com.myfun.funclass.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class WeekNoteEditRequest(
    val sessionIndex: Int,
    val initialWeek: Int,
    val initialNote: String
)

private data class TimePickRequest(
    val title: String,
    val initialTime: String,
    val onPicked: (String) -> Unit
)

private data class DatePickRequest(
    val title: String,
    val initialDate: LocalDate,
    val onPicked: (LocalDate) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    state: MainUiState,
    initialModeCalendar: Boolean,
    editingCourseId: Long?,
    editingCalendarId: Long?,
    prefillDay: Int?,
    prefillNode: Int?,
    onSaveCourse: (Course) -> Unit,
    onSaveCalendar: (CalendarEvent) -> Unit,
    onBack: () -> Unit
) {
    val existingCourse = remember(editingCourseId, state.courses) {
        editingCourseId?.let { id -> state.courses.firstOrNull { it.id == id } }
    }
    val existingCalendar = remember(editingCalendarId, state.calendarEvents) {
        editingCalendarId?.let { id -> state.calendarEvents.firstOrNull { it.id == id } }
    }

    var isCalendarMode by remember {
        mutableStateOf(
            when {
                existingCourse != null -> false
                existingCalendar != null -> true
                else -> initialModeCalendar
            }
        )
    }

    // ---- Course Form State ----
    var courseName by remember { mutableStateOf(existingCourse?.name.orEmpty()) }
    var courseColor by remember { mutableStateOf(existingCourse?.colorHex ?: CourseColorPalette.macaronColors.first()) }
    var courseCredit by remember { mutableStateOf(if ((existingCourse?.credit ?: 0f) > 0f) existingCourse!!.credit.toString() else "") }
    var courseNote by remember { mutableStateOf(existingCourse?.note.orEmpty()) }
    val sessions = remember {
        mutableStateListOf<CourseSession>().apply {
            if (existingCourse != null && existingCourse.sessions.isNotEmpty()) {
                addAll(existingCourse.sessions)
            } else {
                add(
                    CourseSession(
                        tableId = state.activeTable.id,
                        dayOfWeek = prefillDay ?: LocalDate.now().dayOfWeek.value,
                        startNode = prefillNode ?: 1,
                        nodeCount = 2,
                        weeks = WeekSet.range(1, 16)
                    )
                )
            }
        }
    }
    var weekPickerSessionIdx by remember { mutableStateOf<Int?>(null) }
    var weekNoteEditRequest by remember { mutableStateOf<WeekNoteEditRequest?>(null) }

    // ---- Calendar Form State ----
    val zone = ZoneId.systemDefault()
    val defaultStart = existingCalendar?.startDateTime(zone) ?: LocalDateTime.now().withMinute(0).plusHours(1)
    val defaultEnd = existingCalendar?.endDateTime(zone) ?: defaultStart.plusHours(1)
    var calTitle by remember { mutableStateOf(existingCalendar?.title.orEmpty()) }
    var calLocation by remember { mutableStateOf(existingCalendar?.location.orEmpty()) }
    var calColor by remember { mutableStateOf(existingCalendar?.colorHex ?: "#7262FD") }
    var calAllDay by remember { mutableStateOf(existingCalendar?.isAllDay ?: false) }
    var calDate by remember { mutableStateOf(defaultStart.toLocalDate()) }
    var calStartTimeStr by remember { mutableStateOf(defaultStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var calEndTimeStr by remember { mutableStateOf(defaultEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var calRepeat by remember { mutableStateOf(existingCalendar?.repeatType ?: RepeatType.NONE) }
    var calEndRepeatDate by remember { mutableStateOf(existingCalendar?.endRepeatLocalDate()) }
    var calRemindMin by remember { mutableIntStateOf(existingCalendar?.remindMinutesBefore ?: 15) }
    var calCountdown by remember { mutableStateOf(existingCalendar?.countdownEnabled ?: true) }
    var calNote by remember { mutableStateOf(existingCalendar?.note.orEmpty()) }

    var pendingDatePicker by remember { mutableStateOf<DatePickRequest?>(null) }
    var pendingTimePicker by remember { mutableStateOf<TimePickRequest?>(null) }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = {
                    Text(
                        when {
                            existingCourse != null -> "编辑课程"
                            existingCalendar != null -> "编辑日程"
                            else -> "新建安排"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (!isCalendarMode) {
                                if (courseName.isBlank()) return@TextButton
                                val course = (existingCourse ?: Course(tableId = state.activeTable.id, name = courseName.trim())).copy(
                                    name = courseName.trim(),
                                    colorHex = courseColor,
                                    credit = courseCredit.toFloatOrNull() ?: 0f,
                                    note = courseNote.trim(),
                                    sessions = sessions.toList()
                                )
                                onSaveCourse(course)
                                onBack()
                            } else {
                                if (calTitle.isBlank()) return@TextButton
                                val st = runCatching { LocalTime.parse(TimeSlot.normalizeTime(calStartTimeStr)) }.getOrElse { LocalTime.of(9, 0) }
                                val et = runCatching { LocalTime.parse(TimeSlot.normalizeTime(calEndTimeStr)) }.getOrElse { st.plusHours(1) }
                                val startMillis = LocalDateTime.of(calDate, if (calAllDay) LocalTime.MIN else st).atZone(zone).toInstant().toEpochMilli()
                                val endMillis = LocalDateTime.of(calDate, if (calAllDay) LocalTime.of(23, 59) else et).atZone(zone).toInstant().toEpochMilli()
                                val event = (existingCalendar ?: CalendarEvent(
                                    title = calTitle.trim(),
                                    startEpochMillis = startMillis,
                                    endEpochMillis = endMillis,
                                    effectiveDate = calDate.toString()
                                )).copy(
                                    title = calTitle.trim(),
                                    location = calLocation.trim(),
                                    colorHex = calColor,
                                    isAllDay = calAllDay,
                                    startEpochMillis = startMillis,
                                    endEpochMillis = endMillis.coerceAtLeast(startMillis + 15 * 60_000L),
                                    repeatType = calRepeat,
                                    effectiveDate = calDate.toString(),
                                    endRepeatDate = if (calRepeat == RepeatType.NONE) "" else calEndRepeatDate?.toString().orEmpty(),
                                    note = calNote.trim(),
                                    remindMinutesBefore = calRemindMin,
                                    countdownEnabled = calCountdown
                                )
                                onSaveCalendar(event)
                                onBack()
                            }
                        }
                    ) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }
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
            if (existingCourse == null && existingCalendar == null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isCalendarMode,
                        onClick = { isCalendarMode = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("添加课程") }
                    SegmentedButton(
                        selected = isCalendarMode,
                        onClick = { isCalendarMode = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("添加日程 / 待办") }
                }
            }

            if (!isCalendarMode) {
                // Course Form
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("课程名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ColorPaletteRow(selectedHex = courseColor, onSelect = { courseColor = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = courseCredit,
                        onValueChange = { courseCredit = it },
                        label = { Text("学分 (可选)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = courseNote,
                        onValueChange = { courseNote = it },
                        label = { Text("课程备注") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("上课时间段 (${sessions.size})", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        sessions.add(
                            CourseSession(
                                tableId = state.activeTable.id,
                                dayOfWeek = 1,
                                startNode = 1,
                                nodeCount = 2,
                                weeks = WeekSet.range(1, 16)
                            )
                        )
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("添加时段")
                    }
                }

                sessions.forEachIndexed { idx, session ->
                    val defaultTimeRange = state.activeTimeScheme.timeRangeForNodes(session.startNode, session.nodeCount)
                    val displayStart = session.customStartTime.ifBlank { defaultTimeRange.first.format(DateTimeFormatter.ofPattern("HH:mm")) }
                    val displayEnd = session.customEndTime.ifBlank { defaultTimeRange.second.format(DateTimeFormatter.ofPattern("HH:mm")) }

                    Card(
                        colors = funClassCardColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("时段 #${idx + 1}", fontWeight = FontWeight.SemiBold)
                                if (sessions.size > 1) {
                                    IconButton(onClick = { sessions.removeAt(idx) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除时段")
                                    }
                                }
                            }

                            // Day of week chips
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..7).forEach { d ->
                                    FilterChip(
                                        selected = session.dayOfWeek == d,
                                        onClick = { sessions[idx] = session.copy(dayOfWeek = d) },
                                        label = { Text(dayLabel(d)) }
                                    )
                                }
                            }

                            // Start / End nodes
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = session.startNode.toString(),
                                    onValueChange = { v ->
                                        val s = v.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, state.activeTable.maxNodes) ?: return@OutlinedTextField
                                        sessions[idx] = session.copy(startNode = s)
                                    },
                                    label = { Text("开始节次") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = session.nodeCount.toString(),
                                    onValueChange = { v ->
                                        val c = v.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 8) ?: return@OutlinedTextField
                                        sessions[idx] = session.copy(nodeCount = c)
                                    },
                                    label = { Text("持续节数") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Optional custom start/end time using Material 3 TimePicker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "自定义该时段上下课时间",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                                )
                                Switch(
                                    checked = session.useCustomTime,
                                    onCheckedChange = { checked ->
                                        sessions[idx] = session.copy(
                                            useCustomTime = checked,
                                            customStartTime = if (checked && session.customStartTime.isBlank()) displayStart else session.customStartTime,
                                            customEndTime = if (checked && session.customEndTime.isBlank()) displayEnd else session.customEndTime
                                        )
                                    }
                                )
                            }
                            if (session.useCustomTime) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PickerFieldCard(
                                        label = "自定义开始",
                                        value = displayStart,
                                        icon = Icons.Default.AccessTime,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            pendingTimePicker = TimePickRequest("选择上课时间", displayStart) { picked ->
                                                sessions[idx] = sessions[idx].copy(customStartTime = picked)
                                            }
                                        }
                                    )
                                    PickerFieldCard(
                                        label = "自定义结束",
                                        value = displayEnd,
                                        icon = Icons.Default.AccessTime,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            pendingTimePicker = TimePickRequest("选择下课时间", displayEnd) { picked ->
                                                sessions[idx] = sessions[idx].copy(customEndTime = picked)
                                            }
                                        }
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { weekPickerSessionIdx = idx },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("上课周次：${session.weeks.toDisplayString()}（点击自由点选）")
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = session.room,
                                    onValueChange = { sessions[idx] = session.copy(room = it) },
                                    label = { Text("教室") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = session.teacher,
                                    onValueChange = { sessions[idx] = session.copy(teacher = it) },
                                    label = { Text("教师") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = session.sessionNote,
                                onValueChange = { sessions[idx] = session.copy(sessionNote = it) },
                                label = { Text("该时段常规备注 (每周通用，可选)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Per-week independent notes (weekNotes) section
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "按周独立备注 (${session.weekNotes.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(onClick = {
                                    val defaultWeek = session.weeks.weeks.sorted().firstOrNull() ?: state.selectedWeek
                                    weekNoteEditRequest = WeekNoteEditRequest(
                                        sessionIndex = idx,
                                        initialWeek = defaultWeek,
                                        initialNote = session.weekNotes[defaultWeek].orEmpty()
                                    )
                                }) {
                                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("添加单周备注")
                                }
                            }
                            if (session.weekNotes.isEmpty()) {
                                Text(
                                    text = "可为某一周单独备注（如第8周期中考试、第12周带电脑），不会影响其他周次。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    session.weekNotes.entries.sortedBy { it.key }.forEach { (w, note) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    weekNoteEditRequest = WeekNoteEditRequest(
                                                        sessionIndex = idx,
                                                        initialWeek = w,
                                                        initialNote = note
                                                    )
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "第 ${w} 周：$note",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val updated = session.weekNotes.toMutableMap().apply { remove(w) }
                                                        sessions[idx] = session.copy(weekNotes = updated)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "删除单周备注", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Calendar Event Form with Material 3 DatePicker & TimePicker
                OutlinedTextField(
                    value = calTitle,
                    onValueChange = { calTitle = it },
                    label = { Text("日程 / 待办标题 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ColorPaletteRow(selectedHex = calColor, onSelect = { calColor = it })
                OutlinedTextField(
                    value = calLocation,
                    onValueChange = { calLocation = it },
                    label = { Text("地点 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("全天日程", modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Switch(checked = calAllDay, onCheckedChange = { calAllDay = it })
                }

                PickerFieldCard(
                    label = "日程日期",
                    value = calDate.toString(),
                    icon = Icons.Default.CalendarToday,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        pendingDatePicker = DatePickRequest("选择日程日期", calDate) { calDate = it }
                    }
                )

                if (!calAllDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerFieldCard(
                            label = "开始时间",
                            value = calStartTimeStr,
                            icon = Icons.Default.AccessTime,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                pendingTimePicker = TimePickRequest("选择开始时间", calStartTimeStr) { picked ->
                                    calStartTimeStr = picked
                                }
                            }
                        )
                        PickerFieldCard(
                            label = "结束时间",
                            value = calEndTimeStr,
                            icon = Icons.Default.AccessTime,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                pendingTimePicker = TimePickRequest("选择结束时间", calEndTimeStr) { picked ->
                                    calEndTimeStr = picked
                                }
                            }
                        )
                    }
                }

                Text("重复规则", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RepeatType.entries.forEach { rt ->
                        FilterChip(
                            selected = calRepeat == rt,
                            onClick = { calRepeat = rt },
                            label = { Text(rt.label) }
                        )
                    }
                }

                if (calRepeat != RepeatType.NONE) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PickerFieldCard(
                            label = "重复截止日期 (可选)",
                            value = calEndRepeatDate?.toString() ?: "永久重复",
                            icon = Icons.Default.CalendarToday,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                pendingDatePicker = DatePickRequest(
                                    "选择重复截止日期",
                                    calEndRepeatDate ?: calDate.plusMonths(3)
                                ) { calEndRepeatDate = it }
                            }
                        )
                        if (calEndRepeatDate != null) {
                            TextButton(onClick = { calEndRepeatDate = null }) { Text("清除") }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("开启倒计时提醒通知", modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Switch(checked = calCountdown, onCheckedChange = { calCountdown = it })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-1 to "不提醒", 0 to "准时", 10 to "提前10分", 15 to "提前15分", 30 to "提前30分").forEach { (min, lbl) ->
                        FilterChip(
                            selected = calRemindMin == min,
                            onClick = { calRemindMin = min },
                            label = { Text(lbl) }
                        )
                    }
                }
                OutlinedTextField(
                    value = calNote,
                    onValueChange = { calNote = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Visual Discrete Week Picker Dialog
    weekPickerSessionIdx?.let { idx ->
        val session = sessions[idx]
        val maxW = state.activeTable.maxWeeks.coerceIn(1, 30)
        var selectedWeeks by remember(idx) { mutableStateOf(session.weeks.weeks) }

        AlertDialog(
            onDismissRequest = { weekPickerSessionIdx = null },
            title = { Text("选择上课周次（支持任意不规则周）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedWeeks = (1..maxW).toSet() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("全选", maxLines = 1) }
                        OutlinedButton(
                            onClick = { selectedWeeks = (1..maxW).filter { it % 2 == 1 }.toSet() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("单周", maxLines = 1) }
                        OutlinedButton(
                            onClick = { selectedWeeks = (1..maxW).filter { it % 2 == 0 }.toSet() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("双周", maxLines = 1) }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (w in 1..maxW) {
                            val active = w in selectedWeeks
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        selectedWeeks = if (active) selectedWeeks - w else selectedWeeks + w
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$w",
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (selectedWeeks.isNotEmpty()) {
                        sessions[idx] = session.copy(weeks = WeekSet(selectedWeeks))
                    }
                    weekPickerSessionIdx = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { weekPickerSessionIdx = null }) { Text("取消") }
            }
        )
    }

    // Per-Week Independent Note Editor Dialog
    weekNoteEditRequest?.let { req ->
        val session = sessions.getOrNull(req.sessionIndex)
        if (session != null) {
            val candidateWeeks = remember(session) {
                session.weeks.weeks.sorted().ifEmpty { (1..state.activeTable.maxWeeks.coerceIn(1, 30)).toList() }
            }
            var chosenWeek by remember(req) { mutableIntStateOf(req.initialWeek) }
            var noteText by remember(req) { mutableStateOf(req.initialNote) }

            AlertDialog(
                onDismissRequest = { weekNoteEditRequest = null },
                title = { Text("编辑按周独立备注") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("选择周次：", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(candidateWeeks) { w ->
                                FilterChip(
                                    selected = chosenWeek == w,
                                    onClick = {
                                        chosenWeek = w
                                        noteText = session.weekNotes[w].orEmpty()
                                    },
                                    label = { Text("第${w}周") }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text("第 $chosenWeek 周备注内容") },
                            placeholder = { Text("留空确定将清除该周备注") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val map = session.weekNotes.toMutableMap()
                        val trimmed = noteText.trim()
                        if (trimmed.isEmpty()) map.remove(chosenWeek) else map[chosenWeek] = trimmed
                        sessions[req.sessionIndex] = session.copy(weekNotes = map)
                        weekNoteEditRequest = null
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { weekNoteEditRequest = null }) { Text("取消") }
                }
            )
        }
    }

    // Shared Material 3 Date & Time Picker Modals
    pendingDatePicker?.let { req ->
        MaterialDatePickerModal(
            initialDate = req.initialDate,
            title = req.title,
            onDismiss = { pendingDatePicker = null },
            onConfirm = { picked ->
                req.onPicked(picked)
                pendingDatePicker = null
            }
        )
    }

    pendingTimePicker?.let { req ->
        MaterialTimePickerModal(
            initialTime = req.initialTime,
            title = req.title,
            onDismiss = { pendingTimePicker = null },
            onConfirm = { picked ->
                req.onPicked(picked)
                pendingTimePicker = null
            }
        )
    }
}

@Composable
private fun ColorPaletteRow(selectedHex: String, onSelect: (String) -> Unit) {
    val palette = remember { (CourseColorPalette.macaronColors + CourseColorPalette.colors).distinct() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(palette) { hex ->
            val selected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex))
                    .then(
                        if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
