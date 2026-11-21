package com.myfun.funclass.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfun.funclass.core.model.CalendarOccurrence
import com.myfun.funclass.core.model.CourseOverlapLayout
import com.myfun.funclass.core.model.PlacedCourseBlock
import com.myfun.funclass.core.model.ResolvedCourseItem
import com.myfun.funclass.core.model.ScheduleAppearance
import com.myfun.funclass.core.model.ScheduleDateResolver
import com.myfun.funclass.core.model.TimeSlot
import com.myfun.funclass.core.model.TimeSlotType
import com.myfun.funclass.ui.MainUiState
import com.myfun.funclass.ui.components.MaterialTimePickerModal
import com.myfun.funclass.ui.components.PickerFieldCard
import com.myfun.funclass.ui.theme.FunClassColorEngine
import com.myfun.funclass.ui.theme.LocalFunClassVisualStyle
import com.myfun.funclass.ui.theme.funClassCardColors
import com.myfun.funclass.ui.theme.funClassScaffoldContainerColor
import com.myfun.funclass.ui.theme.funClassTopBarColors
import com.myfun.funclass.ui.theme.parseHexColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortTimeFmt = DateTimeFormatter.ofPattern("HH:mm")

private data class CourseDetailSelection(
    val item: ResolvedCourseItem,
    val conflictPeers: List<ResolvedCourseItem> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    state: MainUiState,
    onSelectWeek: (Int) -> Unit,
    onJumpCurrentWeek: () -> Unit,
    onSwitchTable: (Long) -> Unit,
    onAddClick: (prefillDay: Int?, prefillNode: Int?) -> Unit,
    onEditCourse: (Long) -> Unit,
    onDeleteCourse: (Long) -> Unit,
    onUpdateSessionWeekNote: (courseId: Long, sessionId: Long, week: Int, note: String) -> Unit,
    onEditCalendar: (Long) -> Unit,
    onToggleCalendarDone: (Long, String, Boolean) -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenTimeScheme: () -> Unit,
    onSaveCustomBreak: (TimeSlot?, TimeSlot) -> Unit,
    onDeleteCustomBreak: (TimeSlot) -> Unit
) {
    val table = state.activeTable
    val appearance = table.appearance
    val scheme = state.activeTimeScheme
    val maxWeeks = table.maxWeeks.coerceIn(1, 30)

    val pagerState = rememberPagerState(
        initialPage = (state.selectedWeek - 1).coerceIn(0, maxWeeks - 1),
        pageCount = { maxWeeks }
    )
    val coroutineScope = rememberCoroutineScope()
    var lastTableId by remember { mutableStateOf(table.id) }
    val latestSelectedWeek by rememberUpdatedState(state.selectedWeek)

    // Sync ViewModel selectedWeek -> HorizontalPager
    LaunchedEffect(state.selectedWeek, table.id, maxWeeks) {
        val target = (state.selectedWeek - 1).coerceIn(0, maxWeeks - 1)
        if (lastTableId != table.id) {
            lastTableId = table.id
            if (pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        } else if (pagerState.currentPage != target && pagerState.targetPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Sync HorizontalPager settled swipe -> ViewModel selectedWeek
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val week = page + 1
                if (week != latestSelectedWeek) {
                    onSelectWeek(week)
                }
            }
    }

    val displayWeek = (pagerState.currentPage + 1).coerceIn(1, maxWeeks)

    var showTableMenu by remember { mutableStateOf(false) }
    var showWeekStrip by remember { mutableStateOf(false) }
    var selectedCourseDetail by remember { mutableStateOf<CourseDetailSelection?>(null) }
    var conflictPickerItems by remember { mutableStateOf<List<ResolvedCourseItem>?>(null) }
    var editingWeekNoteTarget by remember { mutableStateOf<ResolvedCourseItem?>(null) }
    var selectedCalendarDetail by remember { mutableStateOf<CalendarOccurrence?>(null) }
    var editingBreakSlot by remember { mutableStateOf<TimeSlot?>(null) }

    val today = LocalDate.now()
    val noFontPad = PlatformTextStyle(includeFontPadding = false)
    val visual = LocalFunClassVisualStyle.current

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showTableMenu = true }
                        ) {
                            Text(
                                text = table.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "切换课表")
                            DropdownMenu(expanded = showTableMenu, onDismissRequest = { showTableMenu = false }) {
                                state.allTables.forEach { t ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (t.id == table.id) "✓ ${t.name}" else t.name,
                                                fontWeight = if (t.id == table.id) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            showTableMenu = false
                                            onSwitchTable(t.id)
                                        }
                                    )
                                }
                            }
                        }
                        val isCurrentWeek = displayWeek == state.currentAcademicWeek
                        Surface(
                            onClick = { showWeekStrip = !showWeekStrip },
                            shape = RoundedCornerShape(99.dp),
                            color = if (isCurrentWeek) {
                                MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = if (visual.hasVisibleBackground) 0.85f else 1f
                                )
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(
                                    alpha = if (visual.hasVisibleBackground) 0.88f else 1f
                                )
                            }
                        ) {
                            val weekLabel = if (isCurrentWeek) {
                                "第 $displayWeek 周 · 本周"
                            } else {
                                "第 $displayWeek 周 · 非本周"
                            }
                            Text(
                                text = weekLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrentWeek) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (displayWeek != state.currentAcademicWeek) {
                        TextButton(onClick = {
                            onJumpCurrentWeek()
                            val target = (state.currentAcademicWeek - 1).coerceIn(0, maxWeeks - 1)
                            if (pagerState.currentPage != target) {
                                coroutineScope.launch { pagerState.animateScrollToPage(target) }
                            }
                        }) { Text("回本周") }
                    }
                    IconButton(onClick = onOpenAppearance) {
                        Icon(Icons.Default.Palette, contentDescription = "外观定制")
                    }
                    IconButton(onClick = { onAddClick(null, null) }) {
                        Icon(Icons.Default.Add, contentDescription = "添加课程或日程")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Expandable Week Selector Strip
                if (showWeekStrip) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items((1..maxWeeks).toList()) { w ->
                            FilterChip(
                                selected = w == displayWeek,
                                onClick = {
                                    onSelectWeek(w)
                                    showWeekStrip = false
                                    val target = (w - 1).coerceIn(0, maxWeeks - 1)
                                    if (pagerState.currentPage != target) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(target) }
                                    }
                                },
                                label = {
                                    Text(if (w == state.currentAcademicWeek) "第${w}周(今)" else "第${w}周")
                                }
                            )
                        }
                    }
                }

                // Swipeable HorizontalPager for Weekly Timetable
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    val pageWeek = pageIndex + 1
                    val resolvedWeek = remember(
                        table,
                        scheme,
                        pageWeek,
                        state.courses,
                        state.calendarEvents,
                        state.calendarCompletions,
                        state.holidayAdjustments
                    ) {
                        ScheduleDateResolver.resolveWeek(
                            table = table,
                            timeScheme = scheme,
                            weekNumber = pageWeek,
                            courses = state.courses,
                            calendarEvents = state.calendarEvents,
                            completions = state.calendarCompletions,
                            adjustments = state.holidayAdjustments
                        )
                    }

                    val visibleDays = remember(resolvedWeek, appearance.showSaturday, appearance.showSunday) {
                        resolvedWeek.days.filter { d ->
                            when (d.date.dayOfWeek.value) {
                                6 -> appearance.showSaturday
                                7 -> appearance.showSunday
                                else -> true
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Weekday & Date Header Row
                        val timeColWidth = when {
                            !appearance.showTimeBar -> 0.dp
                            appearance.showTime -> 48.dp
                            else -> 32.dp
                        }
                        val headerBgColor = if (visual.hasVisibleBackground) {
                            MaterialTheme.colorScheme.surface.copy(alpha = (visual.chromeAlpha * 0.88f).coerceIn(0f, 1f))
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(headerBgColor)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (appearance.showTimeBar) {
                                Box(
                                    modifier = Modifier.width(timeColWidth),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${visibleDays.firstOrNull()?.date?.monthValue ?: today.monthValue}月",
                                        style = TextStyle(
                                            fontSize = appearance.headerTextSizeSp.sp,
                                            lineHeight = (appearance.headerTextSizeSp + 2).sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            platformStyle = noFontPad
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            for (day in visibleDays) {
                                val isToday = day.date == today
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(
                                                alpha = if (visual.hasVisibleBackground) 0.86f else 1f
                                            )
                                            else Color.Transparent
                                        )
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = dayLabel(day.date.dayOfWeek.value),
                                        style = TextStyle(
                                            fontSize = appearance.headerTextSizeSp.sp,
                                            lineHeight = (appearance.headerTextSizeSp + 2).sp,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            platformStyle = noFontPad
                                        )
                                    )
                                    Text(
                                        text = "${day.date.dayOfMonth}",
                                        style = TextStyle(
                                            fontSize = (appearance.headerTextSizeSp - 2).coerceAtLeast(9).sp,
                                            lineHeight = (appearance.headerTextSizeSp).coerceAtLeast(11).sp,
                                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            platformStyle = noFontPad
                                        )
                                    )
                                    if (day.isHolidayOff) {
                                        Text(
                                            text = "休",
                                            style = TextStyle(
                                                fontSize = 9.sp,
                                                lineHeight = 10.sp,
                                                color = Color(0xFFE8684A),
                                                fontWeight = FontWeight.Bold,
                                                platformStyle = noFontPad
                                            )
                                        )
                                    } else if (day.isHolidayShift) {
                                        Text(
                                            text = "调休",
                                            style = TextStyle(
                                                fontSize = 9.sp,
                                                lineHeight = 10.sp,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Bold,
                                                platformStyle = noFontPad
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // All-day calendar event strip (if any this week)
                        val hasAllDay = visibleDays.any { d -> d.calendarEvents.any { it.event.isAllDay } }
                        if (hasAllDay) {
                            val allDayBg = if (visual.hasVisibleBackground) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = (visual.chromeAlpha * 0.72f).coerceIn(0f, 1f))
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(allDayBg)
                                    .padding(vertical = 3.dp)
                            ) {
                                if (appearance.showTimeBar) {
                                    Box(modifier = Modifier.width(timeColWidth), contentAlignment = Alignment.Center) {
                                        Text("全天", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                for (day in visibleDays) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 1.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        day.calendarEvents.filter { it.event.isAllDay }.take(2).forEach { occ ->
                                            val occColors = FunClassColorEngine.resolveCourseCardColors(
                                                baseColorArgb = parseHexColor(occ.event.colorHex).toArgb(),
                                                isDark = visual.isDarkTheme,
                                                pastelStyle = appearance.pastelCourseStyle,
                                                isMutedOtherWeek = false,
                                                alpha = if (occ.isCompleted) 0.40f else 0.88f
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(occColors.background)
                                                    .then(
                                                        if (occColors.border != null) {
                                                            Modifier.border(0.7.dp, occColors.border, RoundedCornerShape(6.dp))
                                                        } else Modifier
                                                    )
                                                    .clickable { selectedCalendarDetail = occ }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = (if (occ.isCompleted) "✓ " else "") + occ.event.title,
                                                    color = occColors.primaryText,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Scrollable Timetable Grid
                        val totalNodes = table.maxNodes.coerceIn(4, 20)
                        val nodeHeight = appearance.itemHeightDp.dp
                        val gridTotalHeight = nodeHeight * totalNodes
                        val regularSlots = scheme.regularSlots().associateBy { it.nodeIndex }
                        val customBreaks = scheme.slots.filter { it.slotType == TimeSlotType.CUSTOM_BREAK }
                        val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = if (visual.hasVisibleBackground) 0.55f else 0.72f
                        )
                        val timeBarBgColor = if (visual.hasVisibleBackground) {
                            MaterialTheme.colorScheme.surface.copy(alpha = (visual.chromeAlpha * 0.78f).coerceIn(0f, 1f))
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Left Node & Time Axis
                            if (appearance.showTimeBar) {
                                Column(
                                    modifier = Modifier
                                        .width(timeColWidth)
                                        .height(gridTotalHeight)
                                        .background(timeBarBgColor)
                                ) {
                                    for (node in 1..totalNodes) {
                                        val slot = regularSlots[node]
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(nodeHeight),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                                        ) {
                                            Text(
                                                text = "$node",
                                                style = TextStyle(
                                                    fontSize = 12.sp,
                                                    lineHeight = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    platformStyle = noFontPad
                                                ),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            if (appearance.showTime && slot != null) {
                                                Text(
                                                    text = slot.startTime,
                                                    style = TextStyle(
                                                        fontSize = 9.sp,
                                                        lineHeight = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        platformStyle = noFontPad
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                Text(
                                                    text = slot.endTime,
                                                    style = TextStyle(
                                                        fontSize = 9.sp,
                                                        lineHeight = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        platformStyle = noFontPad
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Course & Calendar Columns Area
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(gridTotalHeight)
                            ) {
                                // Background Grid Lines
                                if (appearance.showGrid) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val colCount = visibleDays.size.coerceAtLeast(1)
                                        val colW = size.width / colCount
                                        val rowH = size.height / totalNodes
                                        val effect = if (appearance.useDottedLine) {
                                            PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                        } else null
                                        for (r in 1 until totalNodes) {
                                            val y = r * rowH
                                            drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = effect)
                                        }
                                        for (c in 1 until colCount) {
                                            val x = c * colW
                                            drawLine(gridLineColor, Offset(x, 0f), Offset(x, size.height), 1f, pathEffect = effect)
                                        }
                                    }
                                }

                                // Interactive Day Columns
                                Row(modifier = Modifier.fillMaxSize()) {
                                    for (day in visibleDays) {
                                        val placedActive = remember(day.courses) {
                                            CourseOverlapLayout.layoutDay(day.courses, maxSideBySideColumns = 2)
                                        }
                                        val placedOther = remember(day.otherWeekCourses, appearance.showOtherWeekCourse) {
                                            if (appearance.showOtherWeekCourse) {
                                                CourseOverlapLayout.layoutDay(day.otherWeekCourses, maxSideBySideColumns = 2)
                                            } else {
                                                emptyList()
                                            }
                                        }

                                        BoxWithConstraints(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            val dayColWidth = maxWidth

                                            // Empty clickable slots for quick course add
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                for (node in 1..totalNodes) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(nodeHeight)
                                                            .clickable { onAddClick(day.date.dayOfWeek.value, node) }
                                                    )
                                                }
                                            }

                                            // Non-current week courses (grayed out, behind active courses)
                                            for (block in placedOther) {
                                                val topOffset = nodeHeight * (block.startNode - 1)
                                                val cardHeight = nodeHeight * block.nodeSpan
                                                val blockW = dayColWidth / block.columnCount
                                                val leftOffset = blockW * block.columnIndex
                                                CourseBlockCard(
                                                    block = block,
                                                    appearance = appearance,
                                                    isMutedOtherWeek = true,
                                                    modifier = Modifier
                                                        .width(blockW)
                                                        .offset(x = leftOffset, y = topOffset)
                                                        .height(cardHeight)
                                                        .padding(if (block.columnCount > 1) 1.dp else 1.5.dp),
                                                    onClick = {
                                                        if (block.isFolded) {
                                                            conflictPickerItems = block.foldedItems
                                                        } else {
                                                            selectedCourseDetail = CourseDetailSelection(block.primary, block.conflictPeers)
                                                        }
                                                    }
                                                )
                                            }

                                            // Active week courses (with side-by-side or folded conflict handling)
                                            for (block in placedActive) {
                                                val topOffset = nodeHeight * (block.startNode - 1)
                                                val cardHeight = nodeHeight * block.nodeSpan
                                                val blockW = dayColWidth / block.columnCount
                                                val leftOffset = blockW * block.columnIndex
                                                CourseBlockCard(
                                                    block = block,
                                                    appearance = appearance,
                                                    isMutedOtherWeek = false,
                                                    modifier = Modifier
                                                        .width(blockW)
                                                        .offset(x = leftOffset, y = topOffset)
                                                        .height(cardHeight)
                                                        .padding(if (block.columnCount > 1) 1.dp else 1.5.dp),
                                                    onClick = {
                                                        if (block.isFolded) {
                                                            conflictPickerItems = block.foldedItems
                                                        } else {
                                                            selectedCourseDetail = CourseDetailSelection(block.primary, block.conflictPeers)
                                                        }
                                                    }
                                                )
                                            }

                                            // Timed CalendarEvent overlay cards mapped to nearest node
                                            day.calendarEvents.filter { !it.event.isAllDay }.forEach { occ ->
                                                val mappedNode = mapTimeToNode(occ.startDateTime.toLocalTime(), scheme, totalNodes)
                                                val topOffset = nodeHeight * (mappedNode - 1)
                                                val calColors = FunClassColorEngine.resolveCourseCardColors(
                                                    baseColorArgb = parseHexColor(occ.event.colorHex).toArgb(),
                                                    isDark = visual.isDarkTheme,
                                                    pastelStyle = appearance.pastelCourseStyle,
                                                    isMutedOtherWeek = false,
                                                    alpha = if (occ.isCompleted) 0.35f else 0.88f
                                                )
                                                val calShape = RoundedCornerShape(appearance.cornerRadiusDp.dp)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .offset(y = topOffset)
                                                        .height(nodeHeight)
                                                        .padding(2.dp)
                                                        .clip(calShape)
                                                        .background(calColors.background)
                                                        .border(
                                                            width = 1.dp,
                                                            color = calColors.border ?: Color.White.copy(alpha = 0.6f),
                                                            shape = calShape
                                                        )
                                                        .clickable { selectedCalendarDetail = occ }
                                                        .padding(4.dp)
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = (if (occ.isCompleted) "✓ " else "● ") + occ.event.title,
                                                            color = calColors.primaryText,
                                                            fontSize = (appearance.itemTextSizeSp - 1).coerceAtLeast(9).sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = occ.startDateTime.toLocalTime().format(shortTimeFmt),
                                                            color = calColors.secondaryText,
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Clickable custom break indicators (e.g. 午休 after node 4)
                                if (appearance.showCustomBreaks) {
                                    customBreaks.forEach { brk ->
                                        if (brk.nodeIndex in 1 until totalNodes) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = nodeHeight * brk.nodeIndex - 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Surface(
                                                    onClick = { editingBreakSlot = brk },
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
                                                    shape = RoundedCornerShape(99.dp),
                                                    tonalElevation = 1.dp
                                                ) {
                                                    Text(
                                                        text = "${brk.title} ${brk.startTime}-${brk.endTime}",
                                                        style = TextStyle(
                                                            fontSize = 9.sp,
                                                            lineHeight = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            platformStyle = noFontPad
                                                        ),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Conflict Courses Picker BottomSheet (when 3+ overlapping courses are folded)
    conflictPickerItems?.let { conflictList ->
        ModalBottomSheet(
            onDismissRequest = { conflictPickerItems = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("时间冲突课程（共 ${conflictList.size} 门）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "以下课程在此时段存在重叠，点击任意课程查看详情或编辑：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                conflictList.forEach { item ->
                    Card(
                        colors = funClassCardColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val peers = conflictList.filter { it != item }
                                conflictPickerItems = null
                                selectedCourseDetail = CourseDetailSelection(item, peers)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(item.course.colorHex))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.course.name, fontWeight = FontWeight.SemiBold)
                                val meta = buildList {
                                    add("第${item.session.startNode}-${item.session.endNode}节 (${item.startTime.format(shortTimeFmt)}-${item.endTime.format(shortTimeFmt)})")
                                    if (item.session.room.isNotBlank()) add(item.session.room)
                                    if (item.session.teacher.isNotBlank()) add(item.session.teacher)
                                }.joinToString(" · ")
                                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Course Detail Bottom Sheet
    selectedCourseDetail?.let { sel ->
        // Resolve freshest session from state.courses in case weekNote was just updated
        val freshCourse = state.courses.firstOrNull { it.id == sel.item.course.id } ?: sel.item.course
        val freshSession = freshCourse.sessions.firstOrNull { it.id == sel.item.session.id } ?: sel.item.session
        val detail = sel.item.copy(course = freshCourse, session = freshSession)
        val weekSpecificNote = detail.session.weekNotes[detail.effectiveWeek].orEmpty()

        ModalBottomSheet(
            onDismissRequest = { selectedCourseDetail = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(detail.course.colorHex))
                    )
                    Text(detail.course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                if (sel.conflictPeers.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "同时间存在 ${sel.conflictPeers.size} 门冲突课程（点击快速切换）：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                sel.conflictPeers.forEach { peer ->
                                    AssistChip(
                                        onClick = {
                                            val newPeers = (sel.conflictPeers - peer) + detail
                                            selectedCourseDetail = CourseDetailSelection(peer, newPeers)
                                        },
                                        label = { Text("${peer.course.name} (第${peer.session.startNode}-${peer.session.endNode}节)") }
                                    )
                                }
                            }
                        }
                    }
                }

                if (detail.isShiftedFromHoliday) {
                    AssistChip(onClick = {}, label = { Text("节假日调休：${detail.shiftLabel}") })
                }
                Text("周次：${detail.session.weeks.toDisplayString()}")
                Text("时间：${dayLabel(detail.session.dayOfWeek)} 第${detail.session.startNode}-${detail.session.endNode}节 (${detail.startTime.format(shortTimeFmt)} - ${detail.endTime.format(shortTimeFmt)})")
                if (detail.session.room.isNotBlank()) Text("教室：${detail.session.room}")
                if (detail.session.teacher.isNotBlank()) Text("教师：${detail.session.teacher}")
                if (detail.course.credit > 0f) Text("学分：${detail.course.credit}")
                if (weekSpecificNote.isNotBlank()) {
                    Text(
                        "第 ${detail.effectiveWeek} 周独立备注：$weekSpecificNote",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (detail.session.sessionNote.isNotBlank()) Text("时段常规备注：${detail.session.sessionNote}")
                if (detail.course.note.isNotBlank() && detail.course.note != detail.activeNote) {
                    Text("课程说明：${detail.course.note}")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { editingWeekNoteTarget = detail },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (weekSpecificNote.isBlank()) "添加第 ${detail.effectiveWeek} 周独立备注"
                        else "修改第 ${detail.effectiveWeek} 周独立备注"
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val id = detail.course.id
                            selectedCourseDetail = null
                            onDeleteCourse(id)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("删除")
                    }
                    OutlinedButton(
                        onClick = {
                            val id = detail.course.id
                            selectedCourseDetail = null
                            onEditCourse(id)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("编辑课程")
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Quick Per-Week Independent Note (weekNotes) Editor Dialog
    editingWeekNoteTarget?.let { target ->
        var noteText by remember(target) {
            mutableStateOf(target.session.weekNotes[target.effectiveWeek].orEmpty())
        }
        AlertDialog(
            onDismissRequest = { editingWeekNoteTarget = null },
            title = { Text("${target.course.name} · 第 ${target.effectiveWeek} 周备注") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "仅在第 ${target.effectiveWeek} 周生效（如：期中测验、临时换教室、携带实验报告等），不影响其他周次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("第 ${target.effectiveWeek} 周独立备注") },
                        placeholder = { Text("留空保存可清除本周备注") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateSessionWeekNote(
                        target.course.id,
                        target.session.id,
                        target.effectiveWeek,
                        noteText
                    )
                    editingWeekNoteTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (noteText.isNotBlank()) {
                        TextButton(onClick = {
                            onUpdateSessionWeekNote(
                                target.course.id,
                                target.session.id,
                                target.effectiveWeek,
                                ""
                            )
                            editingWeekNoteTarget = null
                        }) { Text("清除", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { editingWeekNoteTarget = null }) { Text("取消") }
                }
            }
        )
    }

    // Calendar Detail Bottom Sheet
    selectedCalendarDetail?.let { occ ->
        ModalBottomSheet(
            onDismissRequest = { selectedCalendarDetail = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(occ.event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("日期：${occ.date} · ${if (occ.event.isAllDay) "全天" else "${occ.startDateTime.toLocalTime().format(shortTimeFmt)} - ${occ.endDateTime.toLocalTime().format(shortTimeFmt)}"}")
                Text("重复：${occ.event.repeatType.label}")
                if (occ.event.location.isNotBlank()) Text("地点：${occ.event.location}")
                if (occ.event.note.isNotBlank()) Text("备注：${occ.event.note}")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onToggleCalendarDone(occ.event.id, occ.date.toString(), !occ.isCompleted)
                            selectedCalendarDetail = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (occ.isCompleted) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckCircle,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (occ.isCompleted) "标记未完成" else "打卡完成")
                    }
                    OutlinedButton(
                        onClick = {
                            val id = occ.event.id
                            selectedCalendarDetail = null
                            onEditCalendar(id)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("编辑日程")
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Quick Edit / Delete Custom Break (e.g. 午休) Dialog with Material 3 TimePicker
    editingBreakSlot?.let { brk ->
        var title by remember(brk) { mutableStateOf(brk.title) }
        var afterNode by remember(brk) { mutableStateOf(brk.nodeIndex.toString()) }
        var start by remember(brk) { mutableStateOf(brk.startTime) }
        var end by remember(brk) { mutableStateOf(brk.endTime) }
        var pickingTimeField by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingBreakSlot = null },
            title = { Text("编辑时段「${brk.title}」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("时段名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = afterNode,
                        onValueChange = { afterNode = it.filter(Char::isDigit).take(2) },
                        label = { Text("显示在第几节之后 (1-${(table.maxNodes - 1).coerceAtLeast(1)})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerFieldCard(
                            label = "开始时间",
                            value = start,
                            icon = Icons.Default.AccessTime,
                            modifier = Modifier.weight(1f),
                            onClick = { pickingTimeField = "start" }
                        )
                        PickerFieldCard(
                            label = "结束时间",
                            value = end,
                            icon = Icons.Default.AccessTime,
                            modifier = Modifier.weight(1f),
                            onClick = { pickingTimeField = "end" }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            editingBreakSlot = null
                            onDeleteCustomBreak(brk)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("删除该时段", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = {
                            editingBreakSlot = null
                            onOpenTimeScheme()
                        }) {
                            Text("更多作息设置")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val maxAfter = (table.maxNodes - 1).coerceAtLeast(1)
                    val updated = brk.copy(
                        title = title.trim().ifBlank { "午休" },
                        nodeIndex = afterNode.toIntOrNull()?.coerceIn(1, maxAfter) ?: brk.nodeIndex,
                        startTime = TimeSlot.normalizeTime(start),
                        endTime = TimeSlot.normalizeTime(end)
                    )
                    editingBreakSlot = null
                    onSaveCustomBreak(brk, updated)
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingBreakSlot = null }) { Text("取消") }
            }
        )

        pickingTimeField?.let { field ->
            MaterialTimePickerModal(
                initialTime = if (field == "start") start else end,
                title = if (field == "start") "选择开始时间" else "选择结束时间",
                onDismiss = { pickingTimeField = null },
                onConfirm = { picked ->
                    if (field == "start") start = picked else end = picked
                    pickingTimeField = null
                }
            )
        }
    }
}

@Composable
private fun CourseBlockCard(
    block: PlacedCourseBlock,
    appearance: ScheduleAppearance,
    isMutedOtherWeek: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val visual = LocalFunClassVisualStyle.current
    val item = block.primary
    val rawColorArgb = if (isMutedOtherWeek) {
        MaterialTheme.colorScheme.outline.toArgb()
    } else {
        parseHexColor(item.course.colorHex).toArgb()
    }
    val alpha = if (isMutedOtherWeek) appearance.otherWeekCourseAlpha else appearance.itemAlpha
    val cardColors = FunClassColorEngine.resolveCourseCardColors(
        baseColorArgb = rawColorArgb,
        isDark = visual.isDarkTheme,
        pastelStyle = appearance.pastelCourseStyle,
        isMutedOtherWeek = isMutedOtherWeek,
        alpha = alpha
    )
    val isCompactSideBySide = block.columnCount > 1
    val hasWeekSpecificNote = !isMutedOtherWeek && !item.session.weekNotes[item.effectiveWeek].isNullOrBlank()
    val cardShape = RoundedCornerShape(appearance.cornerRadiusDp.dp)
    val hasConflict = block.isFolded || block.conflictPeers.isNotEmpty()

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(cardColors.background)
            .then(
                when {
                    hasConflict -> Modifier.border(
                        width = 1.2.dp,
                        color = if (appearance.pastelCourseStyle && !isMutedOtherWeek) {
                            cardColors.primaryText.copy(alpha = 0.55f)
                        } else {
                            Color.White.copy(alpha = 0.78f)
                        },
                        shape = cardShape
                    )
                    cardColors.border != null -> Modifier.border(
                        width = 0.8.dp,
                        color = cardColors.border,
                        shape = cardShape
                    )
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (isCompactSideBySide) 3.dp else 5.dp, vertical = 4.dp)
    ) {
        val textAlign = if (appearance.centerHorizontal) TextAlign.Center else TextAlign.Start
        val noFontPad = PlatformTextStyle(includeFontPadding = false)
        val nameMaxLines = if (appearance.courseNameMaxLines <= 0) Int.MAX_VALUE else appearance.courseNameMaxLines
        val locationMaxLines = if (appearance.locationMaxLines <= 0) Int.MAX_VALUE else appearance.locationMaxLines
        val baseTitleSp = if (isCompactSideBySide) (appearance.itemTextSizeSp - 2).coerceAtLeast(8) else appearance.itemTextSizeSp
        val baseMetaSp = if (isCompactSideBySide) (appearance.itemTextSizeSp - 3).coerceAtLeast(8) else (appearance.itemTextSizeSp - 2).coerceAtLeast(9)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = when {
                appearance.centerHorizontal && appearance.centerVertical -> Alignment.Center
                appearance.centerVertical -> Alignment.CenterStart
                appearance.centerHorizontal -> Alignment.TopCenter
                else -> Alignment.TopStart
            }
        ) {
            Column(
                horizontalAlignment = if (appearance.centerHorizontal) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = (if (isMutedOtherWeek) "[非本周]" else "") + item.course.name,
                    style = TextStyle(
                        color = cardColors.primaryText,
                        fontSize = baseTitleSp.sp,
                        lineHeight = (baseTitleSp + 2).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = textAlign,
                        lineBreak = LineBreak.Simple,
                        platformStyle = noFontPad
                    ),
                    maxLines = nameMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                if (appearance.showLocation && item.session.room.isNotBlank()) {
                    val roomText = if (appearance.showRoomPrefix) "@${item.session.room}" else item.session.room
                    Text(
                        text = roomText,
                        style = TextStyle(
                            color = cardColors.secondaryText,
                            fontSize = baseMetaSp.sp,
                            lineHeight = (baseMetaSp + 2).sp,
                            textAlign = textAlign,
                            lineBreak = LineBreak.Simple,
                            platformStyle = noFontPad
                        ),
                        maxLines = locationMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (appearance.showTeacher && !isCompactSideBySide && item.session.teacher.isNotBlank()) {
                    Text(
                        text = item.session.teacher,
                        style = TextStyle(
                            color = cardColors.tertiaryText,
                            fontSize = baseMetaSp.sp,
                            lineHeight = (baseMetaSp + 2).sp,
                            textAlign = textAlign,
                            lineBreak = LineBreak.Simple,
                            platformStyle = noFontPad
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if ((appearance.showRemark || hasWeekSpecificNote) && !isCompactSideBySide && item.activeNote.isNotBlank()) {
                    Text(
                        text = item.activeNote,
                        style = TextStyle(
                            color = cardColors.secondaryText,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            textAlign = textAlign,
                            lineBreak = LineBreak.Simple,
                            platformStyle = noFontPad
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Corner badge for folded conflicts (+N) or per-week independent note indicator (注)
        if (block.isFolded && block.extraFoldedCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = "+${block.extraFoldedCount}",
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = noFontPad
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        } else if (hasWeekSpecificNote) {
            Surface(
                color = if (appearance.pastelCourseStyle) {
                    cardColors.primaryText.copy(alpha = 0.16f)
                } else {
                    Color.Black.copy(alpha = 0.36f)
                },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = "注",
                    style = TextStyle(
                        color = if (appearance.pastelCourseStyle) cardColors.primaryText else Color.White,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = noFontPad
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private fun mapTimeToNode(
    time: java.time.LocalTime,
    scheme: com.myfun.funclass.core.model.TimeScheme,
    maxNodes: Int
): Int {
    val regular = scheme.regularSlots()
    if (regular.isEmpty()) return 1
    val closest = regular.minByOrNull { slot ->
        kotlin.math.abs(slot.startLocalTime().toSecondOfDay() - time.toSecondOfDay())
    }
    return (closest?.nodeIndex ?: 1).coerceIn(1, maxNodes)
}

fun dayLabel(dow: Int): String = when (dow) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> ""
}
