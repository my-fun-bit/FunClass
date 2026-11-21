package com.myfun.funclass.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.myfun.funclass.core.database.dao.CalendarEventDao
import com.myfun.funclass.core.database.dao.CourseDao
import com.myfun.funclass.core.database.dao.HolidayAdjustmentDao
import com.myfun.funclass.core.database.dao.ScheduleTableDao
import com.myfun.funclass.core.database.dao.TimeSchemeDao
import com.myfun.funclass.core.database.dao.WidgetBindingDao
import com.myfun.funclass.core.database.entity.CalendarCompletionEntity
import com.myfun.funclass.core.database.entity.CalendarEventEntity
import com.myfun.funclass.core.database.entity.CourseEntity
import com.myfun.funclass.core.database.entity.CourseSessionEntity
import com.myfun.funclass.core.database.entity.HolidayAdjustmentEntity
import com.myfun.funclass.core.database.entity.ScheduleTableEntity
import com.myfun.funclass.core.database.entity.TimeSchemeEntity
import com.myfun.funclass.core.database.entity.TimeSlotEntity
import com.myfun.funclass.core.database.entity.WidgetBindingEntity

@Database(
    entities = [
        TimeSchemeEntity::class,
        TimeSlotEntity::class,
        ScheduleTableEntity::class,
        CourseEntity::class,
        CourseSessionEntity::class,
        HolidayAdjustmentEntity::class,
        CalendarEventEntity::class,
        CalendarCompletionEntity::class,
        WidgetBindingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FunClassDatabase : RoomDatabase() {
    abstract fun timeSchemeDao(): TimeSchemeDao
    abstract fun scheduleTableDao(): ScheduleTableDao
    abstract fun courseDao(): CourseDao
    abstract fun holidayAdjustmentDao(): HolidayAdjustmentDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun widgetBindingDao(): WidgetBindingDao
}
