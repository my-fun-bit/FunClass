package com.myfun.funclass.core.database.di

import android.content.Context
import androidx.room.Room
import com.myfun.funclass.core.database.FunClassDatabase
import com.myfun.funclass.core.database.dao.CalendarEventDao
import com.myfun.funclass.core.database.dao.CourseDao
import com.myfun.funclass.core.database.dao.HolidayAdjustmentDao
import com.myfun.funclass.core.database.dao.ScheduleTableDao
import com.myfun.funclass.core.database.dao.TimeSchemeDao
import com.myfun.funclass.core.database.dao.WidgetBindingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FunClassDatabase =
        Room.databaseBuilder(
            context,
            FunClassDatabase::class.java,
            "funclass.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    fun provideTimeSchemeDao(db: FunClassDatabase): TimeSchemeDao = db.timeSchemeDao()

    @Provides
    fun provideScheduleTableDao(db: FunClassDatabase): ScheduleTableDao = db.scheduleTableDao()

    @Provides
    fun provideCourseDao(db: FunClassDatabase): CourseDao = db.courseDao()

    @Provides
    fun provideHolidayAdjustmentDao(db: FunClassDatabase): HolidayAdjustmentDao = db.holidayAdjustmentDao()

    @Provides
    fun provideCalendarEventDao(db: FunClassDatabase): CalendarEventDao = db.calendarEventDao()

    @Provides
    fun provideWidgetBindingDao(db: FunClassDatabase): WidgetBindingDao = db.widgetBindingDao()
}
