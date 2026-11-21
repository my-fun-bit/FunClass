package com.myfun.funclass.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.myfun.funclass.core.database.entity.CalendarCompletionEntity
import com.myfun.funclass.core.database.entity.CalendarEventEntity
import com.myfun.funclass.core.database.entity.CourseEntity
import com.myfun.funclass.core.database.entity.CourseSessionEntity
import com.myfun.funclass.core.database.entity.HolidayAdjustmentEntity
import com.myfun.funclass.core.database.entity.ScheduleTableEntity
import com.myfun.funclass.core.database.entity.TimeSchemeEntity
import com.myfun.funclass.core.database.entity.TimeSlotEntity
import com.myfun.funclass.core.database.entity.WidgetBindingEntity
import kotlinx.coroutines.flow.Flow

data class TimeSchemeWithSlots(
    @Embedded val scheme: TimeSchemeEntity,
    @Relation(parentColumn = "id", entityColumn = "schemeId")
    val slots: List<TimeSlotEntity>
)

data class CourseWithSessions(
    @Embedded val course: CourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val sessions: List<CourseSessionEntity>
)

@Dao
interface TimeSchemeDao {
    @Transaction
    @Query("SELECT * FROM time_schemes WHERE isDeleted = 0 ORDER BY id ASC")
    fun observeAll(): Flow<List<TimeSchemeWithSlots>>

    @Transaction
    @Query("SELECT * FROM time_schemes WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TimeSchemeWithSlots?>

    @Transaction
    @Query("SELECT * FROM time_schemes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TimeSchemeWithSlots?

    @Transaction
    @Query("SELECT * FROM time_schemes WHERE isDeleted = 0 ORDER BY id ASC")
    suspend fun getAll(): List<TimeSchemeWithSlots>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheme(scheme: TimeSchemeEntity): Long

    @Update
    suspend fun updateScheme(scheme: TimeSchemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<TimeSlotEntity>)

    @Query("DELETE FROM time_slots WHERE schemeId = :schemeId")
    suspend fun deleteSlotsByScheme(schemeId: Long)

    @Query("DELETE FROM time_schemes WHERE id = :id")
    suspend fun deleteSchemeById(id: Long)

    @Transaction
    suspend fun upsertSchemeWithSlots(scheme: TimeSchemeEntity, slots: List<TimeSlotEntity>): Long {
        val id = if (scheme.id == 0L) {
            insertScheme(scheme)
        } else {
            updateScheme(scheme)
            scheme.id
        }
        deleteSlotsByScheme(id)
        insertSlots(slots.map { it.copy(id = 0L, schemeId = id) })
        return id
    }
}

@Dao
interface ScheduleTableDao {
    @Query("SELECT * FROM schedule_tables WHERE isDeleted = 0 ORDER BY displayOrder ASC, id ASC")
    fun observeAll(): Flow<List<ScheduleTableEntity>>

    @Query("SELECT * FROM schedule_tables WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ScheduleTableEntity?>

    @Query("SELECT * FROM schedule_tables WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduleTableEntity?

    @Query("SELECT * FROM schedule_tables WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ScheduleTableEntity?

    @Query("SELECT * FROM schedule_tables WHERE isDeleted = 0 ORDER BY displayOrder ASC, id ASC")
    suspend fun getAll(): List<ScheduleTableEntity>

    @Query("SELECT * FROM schedule_tables")
    suspend fun getAllIncludingDeleted(): List<ScheduleTableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(table: ScheduleTableEntity): Long

    @Update
    suspend fun update(table: ScheduleTableEntity)

    @Query("UPDATE schedule_tables SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("DELETE FROM schedule_tables WHERE id = :id")
    suspend fun hardDelete(id: Long)
}

@Dao
interface CourseDao {
    @Transaction
    @Query("SELECT * FROM courses WHERE tableId = :tableId AND isDeleted = 0 ORDER BY id ASC")
    fun observeCoursesByTable(tableId: Long): Flow<List<CourseWithSessions>>

    @Transaction
    @Query("SELECT * FROM courses WHERE tableId = :tableId AND isDeleted = 0 ORDER BY id ASC")
    suspend fun getCoursesByTable(tableId: Long): List<CourseWithSessions>

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :courseId LIMIT 1")
    suspend fun getCourseById(courseId: Long): CourseWithSessions?

    @Query("SELECT * FROM courses WHERE syncId = :syncId LIMIT 1")
    suspend fun getCourseBySyncId(syncId: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<CourseSessionEntity>)

    @Update
    suspend fun updateSession(session: CourseSessionEntity)

    @Query("DELETE FROM course_sessions WHERE courseId = :courseId")
    suspend fun deleteSessionsByCourse(courseId: Long)

    @Query("DELETE FROM course_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("UPDATE courses SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :courseId")
    suspend fun softDeleteCourse(courseId: Long, updatedAt: Long)

    @Query("DELETE FROM courses WHERE tableId = :tableId")
    suspend fun deleteAllByTable(tableId: Long)

    @Transaction
    suspend fun upsertCourseWithSessions(course: CourseEntity, sessions: List<CourseSessionEntity>): Long {
        val cid = if (course.id == 0L) {
            insertCourse(course)
        } else {
            updateCourse(course)
            course.id
        }
        deleteSessionsByCourse(cid)
        insertSessions(sessions.map { it.copy(id = 0L, courseId = cid, tableId = course.tableId) })
        return cid
    }
}

@Dao
interface HolidayAdjustmentDao {
    @Query("SELECT * FROM holiday_adjustments WHERE tableId = 0 OR tableId = :tableId ORDER BY targetDate ASC")
    fun observeForTable(tableId: Long): Flow<List<HolidayAdjustmentEntity>>

    @Query("SELECT * FROM holiday_adjustments WHERE tableId = 0 OR tableId = :tableId ORDER BY targetDate ASC")
    suspend fun getForTable(tableId: Long): List<HolidayAdjustmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<HolidayAdjustmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HolidayAdjustmentEntity): Long

    @Query("DELETE FROM holiday_adjustments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM holiday_adjustments WHERE isPreset = 1 AND targetDate LIKE :yearPrefix")
    suspend fun deletePresetsByYear(yearPrefix: String)
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE isDeleted = 0 ORDER BY startEpochMillis ASC")
    fun observeAllEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE isDeleted = 0 ORDER BY startEpochMillis ASC")
    suspend fun getAllEvents(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events")
    suspend fun getAllEventsIncludingDeleted(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: CalendarEventEntity): Long

    @Query("UPDATE calendar_events SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteEvent(id: Long, updatedAt: Long)

    @Query("SELECT * FROM calendar_completions")
    fun observeAllCompletions(): Flow<List<CalendarCompletionEntity>>

    @Query("SELECT * FROM calendar_completions")
    suspend fun getAllCompletions(): List<CalendarCompletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: CalendarCompletionEntity): Long

    @Query("DELETE FROM calendar_completions WHERE eventId = :eventId AND occurrenceDate = :occurrenceDate")
    suspend fun deleteCompletion(eventId: Long, occurrenceDate: String)
}

@Dao
interface WidgetBindingDao {
    @Query("SELECT * FROM widget_bindings")
    fun observeAll(): Flow<List<WidgetBindingEntity>>

    @Query("SELECT * FROM widget_bindings WHERE appWidgetId = :widgetId LIMIT 1")
    suspend fun getByWidgetId(widgetId: Int): WidgetBindingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: WidgetBindingEntity)

    @Query("DELETE FROM widget_bindings WHERE appWidgetId = :widgetId")
    suspend fun deleteByWidgetId(widgetId: Int)
}
