package com.myfun.funclass.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myfun.funclass.core.model.ResolvedCourseItem
import com.myfun.funclass.core.model.ResolvedDaySchedule
import com.myfun.funclass.core.model.WidgetStyleConfig
import java.time.format.DateTimeFormatter

private val dayHeaderFmt = DateTimeFormatter.ofPattern("M月d日")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun parseColorHex(hex: String, fallback: Color = Color(0xFF5B8FF9)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { fallback }

/**
 * Widget 1: 今日课表 (Jetpack Glance)
 */
class TodayGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val snapshot = WidgetDataLoader.loadSnapshot(context, appWidgetId)
        provideContent {
            GlanceTheme {
                DayListWidgetContent(
                    title = "${snapshot.table.name} · 第${snapshot.today.weekNumber}周",
                    day = snapshot.today,
                    style = snapshot.style
                )
            }
        }
    }
}

class TodayGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayGlanceWidget()
}

/**
 * Widget 2: 今日 + 明日双日课表 (Jetpack Glance)
 */
class TodayTomorrowGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val snapshot = WidgetDataLoader.loadSnapshot(context, appWidgetId)
        provideContent {
            GlanceTheme {
                val bg = parseColorHex(snapshot.style.backgroundColorHex, Color.White)
                    .copy(alpha = snapshot.style.backgroundAlpha)
                val textColor = parseColorHex(snapshot.style.textColorHex, Color(0xFF1C1B1F))
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(bg)
                        .cornerRadius(snapshot.style.cardCornerRadiusDp.dp)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "${snapshot.table.name} · 今日与明日",
                        style = TextStyle(color = ColorProvider(textColor), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxSize()) {
                        Column(modifier = GlanceModifier.defaultWeight().padding(end = 6.dp)) {
                            DayCompactColumn("今天 ${snapshot.today.date.format(dayHeaderFmt)}", snapshot.today, snapshot.style, textColor)
                        }
                        Column(modifier = GlanceModifier.defaultWeight().padding(start = 6.dp)) {
                            DayCompactColumn("明天 ${snapshot.tomorrow.date.format(dayHeaderFmt)}", snapshot.tomorrow, snapshot.style, textColor)
                        }
                    }
                }
            }
        }
    }
}

class TodayTomorrowGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTomorrowGlanceWidget()
}

/**
 * Widget 3: 周课表网格 (Jetpack Glance)
 */
class WeekGridGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val snapshot = WidgetDataLoader.loadSnapshot(context, appWidgetId)
        provideContent {
            GlanceTheme {
                val bg = parseColorHex(snapshot.style.backgroundColorHex, Color.White)
                    .copy(alpha = snapshot.style.backgroundAlpha)
                val textColor = parseColorHex(snapshot.style.textColorHex, Color(0xFF1C1B1F))
                val visibleDays = snapshot.week.days.filter { d ->
                    when (d.date.dayOfWeek.value) {
                        6 -> snapshot.table.appearance.showSaturday
                        7 -> snapshot.table.appearance.showSunday
                        else -> true
                    }
                }
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(bg)
                        .cornerRadius(snapshot.style.cardCornerRadiusDp.dp)
                        .padding(8.dp)
                ) {
                    if (snapshot.style.showDateHeader) {
                        Text(
                            text = "${snapshot.table.name} · 第${snapshot.week.weekNumber}周",
                            style = TextStyle(color = ColorProvider(textColor), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    Row(modifier = GlanceModifier.fillMaxSize()) {
                        for (day in visibleDays) {
                            Column(
                                modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dayOfWeekShort(day.date.dayOfWeek.value),
                                    style = TextStyle(color = ColorProvider(textColor), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "${day.date.dayOfMonth}",
                                    style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.7f)), fontSize = 9.sp)
                                )
                                Spacer(GlanceModifier.height(3.dp))
                                if (day.isHolidayOff) {
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .background(Color(0x22E8684A))
                                            .cornerRadius(4.dp)
                                            .padding(3.dp)
                                    ) {
                                        Text(
                                            text = "休",
                                            style = TextStyle(color = ColorProvider(Color(0xFFE8684A)), fontSize = 9.sp)
                                        )
                                    }
                                } else {
                                    for (course in day.courses.take(5)) {
                                        val cBg = parseColorHex(course.course.colorHex).copy(alpha = 0.85f)
                                        Column(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .padding(bottom = 3.dp)
                                                .background(cBg)
                                                .cornerRadius(4.dp)
                                                .padding(3.dp)
                                        ) {
                                            Text(
                                                text = course.course.name,
                                                maxLines = 2,
                                                style = TextStyle(color = ColorProvider(Color.White), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                            )
                                            if (snapshot.style.showLocation && course.session.room.isNotBlank()) {
                                                Text(
                                                    text = course.session.room,
                                                    maxLines = 1,
                                                    style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.9f)), fontSize = 8.sp)
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

class WeekGridGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekGridGlanceWidget()
}

@Composable
private fun DayListWidgetContent(
    title: String,
    day: ResolvedDaySchedule,
    style: WidgetStyleConfig
) {
    val bg = parseColorHex(style.backgroundColorHex, Color.White).copy(alpha = style.backgroundAlpha)
    val textColor = parseColorHex(style.textColorHex, Color(0xFF1C1B1F))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(style.cardCornerRadiusDp.dp)
            .padding(12.dp)
    ) {
        if (style.showDateHeader) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = TextStyle(color = ColorProvider(textColor), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = "${day.date.format(dayHeaderFmt)} ${dayOfWeekShort(day.date.dayOfWeek.value)}",
                    style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.7f)), fontSize = 11.sp)
                )
            }
            Spacer(GlanceModifier.height(8.dp))
        }

        if (day.isHolidayOff) {
            Text(
                text = "🎉 ${day.holidayAdjustment?.label ?: "节假日放假"}，今日无常规课程",
                style = TextStyle(color = ColorProvider(Color(0xFFE8684A)), fontSize = 12.sp)
            )
            Spacer(GlanceModifier.height(6.dp))
        }

        if (day.courses.isEmpty() && (!style.showCalendarEvents || day.calendarEvents.isEmpty())) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "今天没有安排，好好休息吧",
                    style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.6f)), fontSize = 12.sp)
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(day.courses) { item ->
                    CourseWidgetRow(item, style, textColor)
                }
                if (style.showCalendarEvents) {
                    items(day.calendarEvents) { occ ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp)
                                .background(Color(0x187262FD))
                                .cornerRadius(8.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (occ.isCompleted) "✓ " else "• ",
                                style = TextStyle(color = ColorProvider(Color(0xFF7262FD)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = occ.event.title,
                                maxLines = 1,
                                style = TextStyle(color = ColorProvider(textColor), fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCompactColumn(
    header: String,
    day: ResolvedDaySchedule,
    style: WidgetStyleConfig,
    textColor: Color
) {
    Text(
        text = header,
        style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.8f)), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    )
    Spacer(GlanceModifier.height(4.dp))
    if (day.isHolidayOff) {
        Text(
            text = "放假停课",
            style = TextStyle(color = ColorProvider(Color(0xFFE8684A)), fontSize = 11.sp)
        )
    } else if (day.courses.isEmpty()) {
        Text(
            text = "无课程",
            style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.5f)), fontSize = 11.sp)
        )
    } else {
        for (item in day.courses.take(4)) {
            CourseWidgetRow(item, style, textColor)
        }
    }
}

@Composable
private fun CourseWidgetRow(
    item: ResolvedCourseItem,
    style: WidgetStyleConfig,
    textColor: Color
) {
    val accent = parseColorHex(item.course.colorHex)
    val rowBg = if (style.useColorfulCards) accent.copy(alpha = 0.16f) else Color(0x11000000)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
            .background(rowBg)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(28.dp)
                .background(accent)
                .cornerRadius(2.dp)
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.course.name,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(textColor), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
            val meta = buildList {
                if (style.showTime) add("${item.startTime.format(timeFmt)}-${item.endTime.format(timeFmt)}")
                if (style.showLocation && item.session.room.isNotBlank()) add(item.session.room)
                if (style.showTeacher && item.session.teacher.isNotBlank()) add(item.session.teacher)
            }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.7f)), fontSize = 10.sp)
                )
            }
        }
    }
}

private fun dayOfWeekShort(dow: Int): String = when (dow) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> ""
}
