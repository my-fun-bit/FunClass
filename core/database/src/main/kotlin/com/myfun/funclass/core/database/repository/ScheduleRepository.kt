package com.myfun.funclass.core.database.repository

import com.myfun.funclass.core.database.dao.CalendarEventDao
import com.myfun.funclass.core.database.dao.CourseDao
import com.myfun.funclass.core.database.dao.HolidayAdjustmentDao
import com.myfun.funclass.core.database.dao.ScheduleTableDao
import com.myfun.funclass.core.database.dao.TimeSchemeDao
import com.myfun.funclass.core.database.dao.WidgetBindingDao
import com.myfun.funclass.core.database.entity.CalendarCompletionEntity
import com.myfun.funclass.core.database.mapper.toDomain
import com.myfun.funclass.core.database.mapper.toEntity
import com.myfun.funclass.core.database.preferences.AppPreferencesRepository
import com.myfun.funclass.core.model.CalendarCompletion
import com.myfun.funclass.core.model.CalendarEvent
import com.myfun.funclass.core.model.ChinaHolidayPresets
import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.model.HolidayAdjustment
import com.myfun.funclass.core.model.ScheduleAppearance
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.TimeScheme
import com.myfun.funclass.core.model.WeekSet
import com.myfun.funclass.core.model.WidgetBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val timeSchemeDao: TimeSchemeDao,
    private val tableDao: ScheduleTableDao,
    private val courseDao: CourseDao,
    private val holidayDao: HolidayAdjustmentDao,
    private val calendarDao: CalendarEventDao,
    private val widgetDao: WidgetBindingDao,
    private val prefs: AppPreferencesRepository
) {
    /**
     * Ensures at least one TimeScheme and one ScheduleTable exist on first launch.
     */
    suspend fun ensureInitialized(): Long {
        val schemes = timeSchemeDao.getAll()
        val defaultSchemeId = if (schemes.isEmpty()) {
            val defaultScheme = DefaultTimeSchemes.standardCollege(0L)
            timeSchemeDao.upsertSchemeWithSlots(
                defaultScheme.toEntity(),
                defaultScheme.slots.map { it.toEntity(0L) }
            )
        } else {
            schemes.first().scheme.id
        }

        val tables = tableDao.getAll()
        val activeId = prefs.getActiveTableIdOnce()
        if (tables.isEmpty()) {
            val mondayOfThisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val table = ScheduleTable(
                name = "本学期课表",
                timeSchemeId = defaultSchemeId,
                termStartDate = mondayOfThisWeek.toString()
            )
            val newId = tableDao.insert(table.toEntity())
            prefs.setActiveTableId(newId)
            return newId
        }
        val validActive = tables.firstOrNull { it.id == activeId }?.id ?: tables.first().id
        if (validActive != activeId) {
            prefs.setActiveTableId(validActive)
        }
        return validActive
    }

    // ---- Time Schemes ----
    fun observeTimeSchemes(): Flow<List<TimeScheme>> =
        timeSchemeDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTimeScheme(id: Long): Flow<TimeScheme?> =
        timeSchemeDao.observeById(id).map { it?.toDomain() }

    suspend fun getTimeScheme(id: Long): TimeScheme? = timeSchemeDao.getById(id)?.toDomain()

    suspend fun saveTimeScheme(scheme: TimeScheme): Long {
        val updated = scheme.copy(updatedAt = System.currentTimeMillis())
        return timeSchemeDao.upsertSchemeWithSlots(
            updated.toEntity(),
            updated.slots.map { it.toEntity(updated.id) }
        )
    }

    suspend fun deleteTimeScheme(id: Long) {
        val schemes = timeSchemeDao.getAll()
        if (schemes.size <= 1) return
        timeSchemeDao.deleteSchemeById(id)
    }

    // ---- Schedule Tables ----
    fun observeTables(): Flow<List<ScheduleTable>> =
        tableDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTable(id: Long): Flow<ScheduleTable?> =
        tableDao.observeById(id).map { it?.toDomain() }

    suspend fun getTable(id: Long): ScheduleTable? = tableDao.getById(id)?.toDomain()

    suspend fun getAllTables(): List<ScheduleTable> = tableDao.getAll().map { it.toDomain() }

    suspend fun saveTable(table: ScheduleTable): Long {
        val updated = table.copy(updatedAt = System.currentTimeMillis())
        return if (updated.id == 0L) {
            tableDao.insert(updated.toEntity())
        } else {
            tableDao.update(updated.toEntity())
            updated.id
        }
    }

    suspend fun updateTableAppearance(tableId: Long, appearance: ScheduleAppearance) {
        val current = tableDao.getById(tableId)?.toDomain() ?: return
        saveTable(current.copy(appearance = appearance))
    }

    suspend fun deleteTable(tableId: Long) {
        val all = tableDao.getAll()
        if (all.size <= 1) return
        tableDao.softDelete(tableId, System.currentTimeMillis())
        val remaining = all.firstOrNull { it.id != tableId }
        if (remaining != null && prefs.getActiveTableIdOnce() == tableId) {
            prefs.setActiveTableId(remaining.id)
        }
    }

    // ---- Courses ----
    fun observeCourses(tableId: Long): Flow<List<Course>> =
        courseDao.observeCoursesByTable(tableId).map { list -> list.map { it.toDomain() } }

    suspend fun getCourses(tableId: Long): List<Course> =
        courseDao.getCoursesByTable(tableId).map { it.toDomain() }

    suspend fun getCourse(courseId: Long): Course? =
        courseDao.getCourseById(courseId)?.toDomain()

    suspend fun saveCourse(course: Course): Long {
        val now = System.currentTimeMillis()
        val updated = course.copy(updatedAt = now)
        val sessions = updated.sessions.map { it.copy(tableId = updated.tableId, updatedAt = now) }
        return courseDao.upsertCourseWithSessions(
            updated.toEntity(),
            sessions.map { it.toEntity(updated.id, updated.tableId) }
        )
    }

    suspend fun deleteCourse(courseId: Long) {
        courseDao.softDeleteCourse(courseId, System.currentTimeMillis())
    }

    /**
     * Imports a full parsed schedule either into a brand-new table or replacing/appending an existing one.
     */
    suspend fun importSchedule(
        tableName: String,
        schoolName: String,
        courses: List<Course>,
        timeScheme: TimeScheme? = null,
        termStartDate: String? = null,
        maxWeeks: Int = 20,
        maxNodes: Int = 12,
        sundayFirst: Boolean = false,
        targetTableId: Long? = null,
        replaceExistingCourses: Boolean = false
    ): Long {
        val schemeId = if (timeScheme != null) {
            saveTimeScheme(timeScheme.copy(id = 0L))
        } else {
            val active = tableDao.getById(prefs.getActiveTableIdOnce())
            active?.timeSchemeId ?: timeSchemeDao.getAll().first().scheme.id
        }

        val tableId = if (targetTableId != null && targetTableId > 0L) {
            if (replaceExistingCourses) {
                courseDao.deleteAllByTable(targetTableId)
            }
            targetTableId
        } else {
            val monday = ScheduleTable.parseFlexibleDate(termStartDate)?.toString()
                ?: LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
            val newTable = ScheduleTable(
                name = tableName.ifBlank { "导入课表" },
                schoolName = schoolName,
                timeSchemeId = schemeId,
                termStartDate = monday,
                maxWeeks = maxWeeks,
                maxNodes = maxNodes,
                sundayFirst = sundayFirst
            )
            saveTable(newTable)
        }

        for (course in courses) {
            saveCourse(
                course.copy(
                    id = 0L,
                    tableId = tableId,
                    sessions = course.sessions.map { it.copy(id = 0L, courseId = 0L, tableId = tableId) }
                )
            )
        }
        prefs.setActiveTableId(tableId)
        return tableId
    }

    // ---- Batch Schedule Modify Tools ----
    /**
     * Shifts all courses in [tableId] by [weekDelta] weeks (e.g. +1 to postpone entire semester by 1 week).
     */
    suspend fun batchShiftWeeks(tableId: Long, weekDelta: Int, maxWeeks: Int = 30): Int {
        if (weekDelta == 0) return 0
        val courses = getCourses(tableId)
        var modifiedCount = 0
        for (course in courses) {
            val newSessions = course.sessions.map { session ->
                val shifted = session.weeks.weeks
                    .map { it + weekDelta }
                    .filter { it in 1..maxWeeks }
                    .toSet()
                if (shifted != session.weeks.weeks) modifiedCount++
                session.copy(weeks = WeekSet(shifted))
            }.filter { !it.weeks.isEmpty }
            saveCourse(course.copy(sessions = newSessions))
        }
        return modifiedCount
    }

    /**
     * Swaps all courses between [dayA] and [dayB] (1..7) inside [tableId].
     */
    suspend fun batchSwapDays(tableId: Long, dayA: Int, dayB: Int): Int {
        if (dayA == dayB) return 0
        val courses = getCourses(tableId)
        var modifiedCount = 0
        for (course in courses) {
            var changed = false
            val newSessions = course.sessions.map { session ->
                when (session.dayOfWeek) {
                    dayA -> {
                        changed = true; modifiedCount++
                        session.copy(dayOfWeek = dayB)
                    }
                    dayB -> {
                        changed = true; modifiedCount++
                        session.copy(dayOfWeek = dayA)
                    }
                    else -> session
                }
            }
            if (changed) saveCourse(course.copy(sessions = newSessions))
        }
        return modifiedCount
    }

    // ---- Holiday Adjustments ----
    fun observeHolidayAdjustments(tableId: Long): Flow<List<HolidayAdjustment>> =
        holidayDao.observeForTable(tableId).map { list -> list.map { it.toDomain() } }

    suspend fun getHolidayAdjustments(tableId: Long): List<HolidayAdjustment> =
        holidayDao.getForTable(tableId).map { it.toDomain() }

    suspend fun saveHolidayAdjustment(adjustment: HolidayAdjustment): Long =
        holidayDao.upsert(adjustment.toEntity())

    suspend fun deleteHolidayAdjustment(id: Long) = holidayDao.deleteById(id)

    suspend fun applyChinaHolidayPresets(year: Int, tableId: Long = 0L): Int {
        val presets = ChinaHolidayPresets.presetsForYear(year, tableId)
        if (presets.isNotEmpty()) {
            holidayDao.upsertAll(presets.map { it.toEntity() })
        }
        return presets.size
    }

    suspend fun clearHolidayPresets(year: Int) {
        holidayDao.deletePresetsByYear("$year-%")
    }

    // ---- Calendar Events & Completions ----
    fun observeCalendarEvents(): Flow<List<CalendarEvent>> =
        calendarDao.observeAllEvents().map { list -> list.map { it.toDomain() } }

    suspend fun getCalendarEvents(): List<CalendarEvent> =
        calendarDao.getAllEvents().map { it.toDomain() }

    suspend fun getCalendarEvent(id: Long): CalendarEvent? =
        calendarDao.getById(id)?.toDomain()

    suspend fun saveCalendarEvent(event: CalendarEvent): Long =
        calendarDao.upsertEvent(event.copy(updatedAt = System.currentTimeMillis()).toEntity())

    suspend fun deleteCalendarEvent(id: Long) =
        calendarDao.softDeleteEvent(id, System.currentTimeMillis())

    fun observeCalendarCompletions(): Flow<List<CalendarCompletion>> =
        calendarDao.observeAllCompletions().map { list -> list.map { it.toDomain() } }

    suspend fun getCalendarCompletions(): List<CalendarCompletion> =
        calendarDao.getAllCompletions().map { it.toDomain() }

    suspend fun toggleCalendarCompletion(eventId: Long, occurrenceDate: String, completed: Boolean) {
        if (completed) {
            calendarDao.insertCompletion(
                CalendarCompletionEntity(
                    eventId = eventId,
                    occurrenceDate = occurrenceDate,
                    completedAt = System.currentTimeMillis()
                )
            )
        } else {
            calendarDao.deleteCompletion(eventId, occurrenceDate)
        }
    }

    // ---- Widget Bindings ----
    fun observeWidgetBindings(): Flow<List<WidgetBinding>> =
        widgetDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getWidgetBinding(appWidgetId: Int): WidgetBinding? =
        widgetDao.getByWidgetId(appWidgetId)?.toDomain()

    suspend fun saveWidgetBinding(binding: WidgetBinding) =
        widgetDao.upsert(binding.toEntity())

    suspend fun deleteWidgetBinding(appWidgetId: Int) =
        widgetDao.deleteByWidgetId(appWidgetId)
}
