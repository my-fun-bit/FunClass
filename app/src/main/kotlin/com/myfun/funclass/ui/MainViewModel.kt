package com.myfun.funclass.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfun.funclass.core.database.preferences.AppPreferencesRepository
import com.myfun.funclass.core.database.repository.ScheduleRepository
import com.myfun.funclass.core.model.CalendarCompletion
import com.myfun.funclass.core.model.CalendarEvent
import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.model.HolidayAdjustment
import com.myfun.funclass.core.model.ReminderSettings
import com.myfun.funclass.core.model.ScheduleAppearance
import com.myfun.funclass.core.model.ScheduleDateResolver
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.TimeScheme
import com.myfun.funclass.core.model.TimeSchemeGenerator
import com.myfun.funclass.core.model.TimeSchemeGeneratorConfig
import com.myfun.funclass.core.model.TimeSlot
import com.myfun.funclass.core.model.TimeSlotType
import com.myfun.funclass.core.parser.CsvExcelImporter
import com.myfun.funclass.core.parser.IcsExporter
import com.myfun.funclass.core.parser.IcsImporter
import com.myfun.funclass.core.parser.ParsedScheduleResult
import com.myfun.funclass.core.parser.ParserRegistry
import com.myfun.funclass.core.parser.WakeupScheduleCodec
import com.myfun.funclass.core.sync.ShareCodeRepository
import com.myfun.funclass.reminder.ReminderScheduler
import com.myfun.funclass.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PendingScheduleImport(
    val sourceLabel: String,
    val tableName: String,
    val termStartDate: LocalDate,
    val preview: ParsedScheduleResult,
    val rawIcsContent: String? = null,
    val rawFileName: String = ""
)

data class MainUiState(
    val isReady: Boolean = false,
    val activeTable: ScheduleTable = ScheduleTable(),
    val activeTimeScheme: TimeScheme = DefaultTimeSchemes.standardCollege(),
    val activeTimeSchemeGeneratorConfig: TimeSchemeGeneratorConfig = TimeSchemeGeneratorConfig(),
    val allTables: List<ScheduleTable> = emptyList(),
    val allTimeSchemes: List<TimeScheme> = emptyList(),
    val courses: List<Course> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val calendarCompletions: List<CalendarCompletion> = emptyList(),
    val holidayAdjustments: List<HolidayAdjustment> = emptyList(),
    val selectedWeek: Int = 1,
    val currentAcademicWeek: Int = 1,
    val reminderSettings: ReminderSettings = ReminderSettings(),
    val snackbarMessage: String? = null,
    val hasPromptedNotificationPermission: Boolean = true,
    val showHelperDescriptions: Boolean = true,
    val pendingImport: PendingScheduleImport? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ScheduleRepository,
    private val prefs: AppPreferencesRepository,
    private val shareCodeRepository: ShareCodeRepository,
    val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val selectedWeekOverride = MutableStateFlow<Int?>(null)
    private val snackbarFlow = MutableStateFlow<String?>(null)
    private val pendingImportFlow = MutableStateFlow<PendingScheduleImport?>(null)

    private val resolvedTableFlow = combine(
        prefs.activeTableId,
        repository.observeTables()
    ) { activeId, allTables ->
        val table = allTables.firstOrNull { it.id == activeId } ?: allTables.firstOrNull()
        table to allTables
    }

    private val coreBundleFlow = resolvedTableFlow.flatMapLatest { (table, allTables) ->
        if (table == null) {
            flowOf(CoreBundle(null, null, allTables, emptyList(), emptyList(), emptyList()))
        } else {
            combine(
                repository.observeTimeScheme(table.timeSchemeId),
                repository.observeTimeSchemes(),
                repository.observeCourses(table.id),
                repository.observeHolidayAdjustments(table.id)
            ) { scheme, allSchemes, courses, holidays ->
                CoreBundle(table, scheme, allTables, allSchemes, courses, holidays)
            }
        }
    }

    private val calendarBundleFlow = combine(
        repository.observeCalendarEvents(),
        repository.observeCalendarCompletions(),
        selectedWeekOverride
    ) { events, completions, weekOverride ->
        CalendarBundle(events, completions, weekOverride)
    }

    private val metaFlow = combine(
        prefs.reminderSettings,
        prefs.showHelperDescriptions,
        snackbarFlow
    ) { reminder, showHelpers, snackbar ->
        MetaBundle(reminder, showHelpers, snackbar)
    }

    private val extrasFlow = combine(
        prefs.timeSchemeGeneratorConfigs,
        prefs.hasPromptedNotificationPermission,
        pendingImportFlow
    ) { genConfigs, notifPrompted, pendingImp ->
        ExtrasBundle(genConfigs, notifPrompted, pendingImp)
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreBundleFlow,
        calendarBundleFlow,
        metaFlow,
        extrasFlow
    ) { core, cal, meta, extras ->
        val table = core.activeTable ?: core.allTables.firstOrNull() ?: ScheduleTable()
        val scheme = core.activeScheme ?: core.allSchemes.firstOrNull() ?: DefaultTimeSchemes.standardCollege()
        val genConfig = extras.genConfigs[scheme.id] ?: TimeSchemeGenerator.inferConfig(scheme)
        val currentWeek = ScheduleDateResolver.computeWeekNumber(
            termStartDate = table.termStartLocalDate(),
            date = LocalDate.now(),
            sundayFirst = table.sundayFirst
        ).coerceIn(1, table.maxWeeks.coerceAtLeast(1))
        val effectiveSelectedWeek = (cal.weekOverride ?: currentWeek).coerceIn(1, table.maxWeeks.coerceAtLeast(1))

        MainUiState(
            isReady = core.activeTable != null,
            activeTable = table,
            activeTimeScheme = scheme,
            activeTimeSchemeGeneratorConfig = genConfig,
            allTables = core.allTables,
            allTimeSchemes = core.allSchemes,
            courses = core.courses,
            calendarEvents = cal.events,
            calendarCompletions = cal.completions,
            holidayAdjustments = core.holidays,
            selectedWeek = effectiveSelectedWeek,
            currentAcademicWeek = currentWeek,
            reminderSettings = meta.reminder,
            snackbarMessage = meta.snackbar,
            hasPromptedNotificationPermission = extras.notifPrompted,
            showHelperDescriptions = meta.showHelperDescriptions,
            pendingImport = extras.pendingImport
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
            reminderScheduler.rescheduleAll()
        }
    }

    fun showMessage(msg: String) {
        snackbarFlow.value = msg
    }

    fun consumeMessage() {
        snackbarFlow.value = null
    }

    fun selectWeek(week: Int) {
        selectedWeekOverride.value = week
    }

    fun jumpToCurrentWeek() {
        selectedWeekOverride.value = null
    }

    // ---- Table Operations ----
    fun switchTable(tableId: Long) = viewModelScope.launch {
        prefs.setActiveTableId(tableId)
        selectedWeekOverride.value = null
        refreshSystemSurfaces()
    }

    fun saveTable(table: ScheduleTable) = viewModelScope.launch {
        val id = repository.saveTable(table)
        prefs.setActiveTableId(id)
        refreshSystemSurfaces()
        showMessage("课表「${table.name}」已保存")
    }

    fun deleteTable(tableId: Long) = viewModelScope.launch {
        repository.deleteTable(tableId)
        refreshSystemSurfaces()
        showMessage("已删除课表")
    }

    fun updateAppearance(appearance: ScheduleAppearance) = viewModelScope.launch {
        val tableId = uiState.value.activeTable.id
        if (tableId > 0L) {
            repository.updateTableAppearance(tableId, appearance)
            WidgetRefresher.refreshAll(appContext)
        }
    }

    // ---- Course Operations ----
    fun saveCourse(course: Course) = viewModelScope.launch {
        val tableId = if (course.tableId > 0L) course.tableId else uiState.value.activeTable.id
        repository.saveCourse(course.copy(tableId = tableId))
        refreshSystemSurfaces()
        showMessage("课程「${course.name}」已保存")
    }

    fun deleteCourse(courseId: Long) = viewModelScope.launch {
        repository.deleteCourse(courseId)
        refreshSystemSurfaces()
        showMessage("课程已删除")
    }

    fun updateSessionWeekNote(courseId: Long, sessionId: Long, week: Int, note: String) = viewModelScope.launch {
        val course = repository.getCourse(courseId) ?: return@launch
        val trimmed = note.trim()
        val updatedSessions = course.sessions.map { s ->
            if (s.id == sessionId) {
                val map = s.weekNotes.toMutableMap()
                if (trimmed.isEmpty()) map.remove(week) else map[week] = trimmed
                s.copy(weekNotes = map)
            } else {
                s
            }
        }
        repository.saveCourse(course.copy(sessions = updatedSessions))
        refreshSystemSurfaces()
        showMessage(if (trimmed.isEmpty()) "已清除第 $week 周独立备注" else "已保存第 $week 周独立备注")
    }

    fun markNotificationPermissionPrompted() = viewModelScope.launch {
        prefs.setNotificationPermissionPrompted(true)
    }

    fun onNotificationPermissionResult(granted: Boolean) = viewModelScope.launch {
        prefs.setNotificationPermissionPrompted(true)
        if (granted) {
            val count = reminderScheduler.rescheduleAll()
            showMessage("已开启通知权限（未来 7 天已排期 $count 条上课提醒）")
        }
    }

    // ---- Calendar Event Operations ----
    fun saveCalendarEvent(event: CalendarEvent) = viewModelScope.launch {
        repository.saveCalendarEvent(event)
        refreshSystemSurfaces()
        showMessage("日程「${event.title}」已保存")
    }

    fun deleteCalendarEvent(eventId: Long) = viewModelScope.launch {
        repository.deleteCalendarEvent(eventId)
        refreshSystemSurfaces()
        showMessage("日程已删除")
    }

    fun toggleCalendarCompletion(eventId: Long, dateIso: String, completed: Boolean) = viewModelScope.launch {
        repository.toggleCalendarCompletion(eventId, dateIso, completed)
        refreshSystemSurfaces()
    }

    // ---- Time Scheme Operations ----
    fun saveTimeScheme(
        scheme: TimeScheme,
        generatorConfig: TimeSchemeGeneratorConfig? = null,
        syncTableMaxNodes: Boolean = true
    ) = viewModelScope.launch {
        val savedId = repository.saveTimeScheme(scheme)
        if (generatorConfig != null) {
            prefs.saveTimeSchemeGeneratorConfig(savedId, generatorConfig)
        }
        val regularCount = scheme.regularSlots().size
        val activeTable = uiState.value.activeTable
        if (syncTableMaxNodes && activeTable.id > 0L && regularCount in 4..20 && activeTable.maxNodes != regularCount) {
            repository.saveTable(activeTable.copy(maxNodes = regularCount))
        }
        refreshSystemSurfaces()
        showMessage("作息时间表「${scheme.name}」已保存（共 $regularCount 节）")
    }

    fun saveCustomBreak(original: TimeSlot?, updated: TimeSlot) = viewModelScope.launch {
        val scheme = uiState.value.activeTimeScheme
        val newSlots = scheme.slots.toMutableList()
        val idx = if (original != null) newSlots.indexOf(original) else -1
        val normalized = updated.copy(
            schemeId = scheme.id,
            slotType = TimeSlotType.CUSTOM_BREAK,
            startTime = TimeSlot.normalizeTime(updated.startTime),
            endTime = TimeSlot.normalizeTime(updated.endTime)
        )
        if (idx >= 0) {
            newSlots[idx] = normalized
        } else {
            newSlots.add(normalized)
        }
        repository.saveTimeScheme(scheme.copy(slots = newSlots))
        refreshSystemSurfaces()
        showMessage("已保存时段「${normalized.title}」")
    }

    fun deleteCustomBreak(target: TimeSlot) = viewModelScope.launch {
        val scheme = uiState.value.activeTimeScheme
        val newSlots = scheme.slots.filterNot { it == target }
        repository.saveTimeScheme(scheme.copy(slots = newSlots))
        val curCfg = uiState.value.activeTimeSchemeGeneratorConfig
        if (target.title.contains("午休") || target.title.contains("中午") || target.nodeIndex == curCfg.morningNodes) {
            val stillHasLunch = newSlots.any {
                it.slotType == TimeSlotType.CUSTOM_BREAK && (it.title.contains("午休") || it.title.contains("中午"))
            }
            if (!stillHasLunch && curCfg.includeLunchBreak) {
                prefs.saveTimeSchemeGeneratorConfig(scheme.id, curCfg.copy(includeLunchBreak = false))
            }
        }
        refreshSystemSurfaces()
        showMessage("已删除时段「${target.title}」")
    }

    // ---- Holiday & Batch Modify Operations ----
    fun saveHolidayAdjustment(adjustment: HolidayAdjustment) = viewModelScope.launch {
        repository.saveHolidayAdjustment(adjustment)
        refreshSystemSurfaces()
        showMessage("调休规则已保存")
    }

    fun deleteHolidayAdjustment(id: Long) = viewModelScope.launch {
        repository.deleteHolidayAdjustment(id)
        refreshSystemSurfaces()
        showMessage("已撤销该条调休规则")
    }

    fun applyChinaHolidayPresets(year: Int) = viewModelScope.launch {
        val count = repository.applyChinaHolidayPresets(year, 0L)
        refreshSystemSurfaces()
        showMessage("已导入 $year 年中国法定节假日调休（共 $count 条）")
    }

    fun clearHolidayPresets(year: Int) = viewModelScope.launch {
        repository.clearHolidayPresets(year)
        refreshSystemSurfaces()
        showMessage("已清除 $year 年内置节假日预设")
    }

    fun batchShiftWeeks(delta: Int) = viewModelScope.launch {
        val table = uiState.value.activeTable
        val count = repository.batchShiftWeeks(table.id, delta, table.maxWeeks)
        refreshSystemSurfaces()
        showMessage("已批量平移 $count 个课程时段（${if (delta > 0) "+$delta" else "$delta"}周）")
    }

    fun batchSwapDays(dayA: Int, dayB: Int) = viewModelScope.launch {
        val count = repository.batchSwapDays(uiState.value.activeTable.id, dayA, dayB)
        refreshSystemSurfaces()
        showMessage("已对调周 $dayA 与周 $dayB 的课程（共调整 $count 个时段）")
    }

    // ---- Reminder Operations ----
    fun updateReminderSettings(settings: ReminderSettings) = viewModelScope.launch {
        prefs.updateReminderSettings(settings)
        val count = reminderScheduler.rescheduleAll()
        showMessage("提醒设置已更新（未来 7 天已排期 $count 条提醒）")
    }

    // ---- Import / Export / Share / Sync Operations ----
    fun importParsedSchedule(
        parsed: ParsedScheduleResult,
        createNewTable: Boolean = true,
        replaceExisting: Boolean = false
    ) = viewModelScope.launch {
        if (parsed.courses.isEmpty()) {
            showMessage(parsed.warnings.firstOrNull() ?: "未解析到任何课程")
            return@launch
        }
        val targetId = if (createNewTable) null else uiState.value.activeTable.id
        repository.importSchedule(
            tableName = parsed.tableName,
            schoolName = parsed.schoolName,
            courses = parsed.courses,
            timeScheme = parsed.timeScheme,
            termStartDate = parsed.termStartDate,
            maxWeeks = parsed.maxWeeks,
            maxNodes = parsed.maxNodes,
            sundayFirst = parsed.sundayFirst,
            targetTableId = targetId,
            replaceExistingCourses = replaceExisting
        )
        selectedWeekOverride.value = null
        refreshSystemSurfaces()
        showMessage("成功导入 ${parsed.courses.size} 门课程！")
    }

    fun stageParsedScheduleForConfirmation(
        parsed: ParsedScheduleResult,
        sourceLabel: String,
        rawIcsContent: String? = null,
        rawFileName: String = ""
    ) {
        if (parsed.courses.isEmpty()) {
            showMessage(parsed.warnings.firstOrNull() ?: "未解析到任何课程")
            return
        }
        val fallbackMonday = uiState.value.activeTable.termStartLocalDate()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val inferredDate = ScheduleTable.parseFlexibleDate(parsed.termStartDate) ?: fallbackMonday
        pendingImportFlow.value = PendingScheduleImport(
            sourceLabel = sourceLabel,
            tableName = parsed.tableName.ifBlank { "导入课表" },
            termStartDate = inferredDate,
            preview = parsed.copy(termStartDate = inferredDate.toString()),
            rawIcsContent = rawIcsContent,
            rawFileName = rawFileName
        )
    }

    fun importFromRawFileContent(fileName: String, content: String) {
        val lower = fileName.lowercase()
        val isIcs = lower.endsWith(".ics") || IcsImporter.isIcsContent(content)
        val parsed = runCatching {
            when {
                isIcs ->
                    IcsImporter.parse(
                        rawText = content,
                        fallbackTableName = fileName.substringBeforeLast('.').ifBlank { "ICS 导入课表" },
                        timeScheme = uiState.value.activeTimeScheme
                    )
                lower.endsWith(".funclass") || lower.endsWith(".wakeup_schedule") || content.lines().filter { it.isNotBlank() }.size == 5 && content.trimStart().startsWith("{") ->
                    WakeupScheduleCodec.decode(content)
                lower.endsWith(".csv") || lower.endsWith(".tsv") ->
                    CsvExcelImporter.parse(content, fileName.substringBeforeLast('.'))
                else ->
                    ParserRegistry.autoParse(content)
            }
        }.getOrElse {
            showMessage("导入解析失败：${it.message}")
            return
        }
        val label = when {
            isIcs -> "ICS 日历文件"
            lower.endsWith(".funclass") -> "FunClass 课表文件"
            lower.endsWith(".wakeup_schedule") -> "兼容课表文件 (.wakeup_schedule)"
            lower.endsWith(".csv") || lower.endsWith(".tsv") -> "CSV 表格文件"
            else -> "课表网页/文件"
        }
        stageParsedScheduleForConfirmation(
            parsed = parsed,
            sourceLabel = label,
            rawIcsContent = if (isIcs) content else null,
            rawFileName = fileName
        )
    }

    fun updatePendingImportTableName(newName: String) {
        val cur = pendingImportFlow.value ?: return
        pendingImportFlow.value = cur.copy(
            tableName = newName,
            preview = cur.preview.copy(tableName = newName.trim().ifBlank { cur.preview.tableName })
        )
    }

    fun updatePendingImportTermStart(newDate: LocalDate) {
        val cur = pendingImportFlow.value ?: return
        val alignedMonday = newDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        if (cur.rawIcsContent != null) {
            val reparsed = runCatching {
                IcsImporter.parse(
                    rawText = cur.rawIcsContent,
                    fallbackTableName = cur.rawFileName.substringBeforeLast('.').ifBlank { cur.tableName },
                    timeScheme = uiState.value.activeTimeScheme,
                    overrideTermStartDate = alignedMonday
                )
            }.getOrElse { return }
            pendingImportFlow.value = cur.copy(
                termStartDate = alignedMonday,
                preview = reparsed.copy(tableName = cur.tableName.trim().ifBlank { reparsed.tableName })
            )
        } else {
            pendingImportFlow.value = cur.copy(
                termStartDate = alignedMonday,
                preview = cur.preview.copy(termStartDate = alignedMonday.toString())
            )
        }
    }

    fun confirmPendingImport() {
        val cur = pendingImportFlow.value ?: return
        pendingImportFlow.value = null
        val finalParsed = cur.preview.copy(
            tableName = cur.tableName.trim().ifBlank { cur.preview.tableName },
            termStartDate = cur.termStartDate.toString()
        )
        importParsedSchedule(finalParsed, createNewTable = true)
    }

    fun dismissPendingImport() {
        pendingImportFlow.value = null
    }

    fun importFromShareCode(codeOrText: String) = viewModelScope.launch {
        runCatching {
            shareCodeRepository.parseShareCode(codeOrText)
        }.onSuccess { parsed ->
            stageParsedScheduleForConfirmation(parsed, sourceLabel = "分享口令 / 二维码")
        }.onFailure { err ->
            showMessage(err.message ?: "口令解析失败")
        }
    }

    fun generateShareCode(onReady: (ShareCodeRepository.GeneratedShareOutput) -> Unit) = viewModelScope.launch {
        runCatching {
            shareCodeRepository.generateShareForTable(uiState.value.activeTable.id)
        }.onSuccess(onReady).onFailure {
            showMessage("生成分享失败：${it.message}")
        }
    }

    fun exportWakeupScheduleString(): String {
        val state = uiState.value
        return WakeupScheduleCodec.encode(state.activeTable, state.activeTimeScheme, state.courses)
    }

    fun exportCsvString(): String = CsvExcelImporter.exportCsv(uiState.value.courses)

    fun exportIcsString(): String {
        val state = uiState.value
        return IcsExporter.export(
            table = state.activeTable,
            timeScheme = state.activeTimeScheme,
            courses = state.courses,
            adjustments = state.holidayAdjustments,
            calendarEvents = state.calendarEvents,
            alarmMinutesBefore = state.reminderSettings.courseMinutesBefore
        )
    }

    fun setShowHelperDescriptions(show: Boolean) = viewModelScope.launch {
        prefs.setShowHelperDescriptions(show)
    }

    private suspend fun refreshSystemSurfaces() {
        runCatching { reminderScheduler.rescheduleAll() }
        runCatching { WidgetRefresher.refreshAll(appContext) }
    }

    private data class CoreBundle(
        val activeTable: ScheduleTable?,
        val activeScheme: TimeScheme?,
        val allTables: List<ScheduleTable>,
        val allSchemes: List<TimeScheme>,
        val courses: List<Course>,
        val holidays: List<HolidayAdjustment>
    )

    private data class CalendarBundle(
        val events: List<CalendarEvent>,
        val completions: List<CalendarCompletion>,
        val weekOverride: Int?
    )

    private data class MetaBundle(
        val reminder: ReminderSettings,
        val showHelperDescriptions: Boolean,
        val snackbar: String?
    )

    private data class ExtrasBundle(
        val genConfigs: Map<Long, TimeSchemeGeneratorConfig>,
        val notifPrompted: Boolean,
        val pendingImport: PendingScheduleImport?
    )
}
