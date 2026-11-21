package com.myfun.funclass.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myfun.funclass.core.database.repository.ScheduleRepository
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.WidgetBinding
import com.myfun.funclass.core.model.WidgetStyleConfig
import com.myfun.funclass.core.model.WidgetType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Independent AppWidget configuration Activity allowing per-widget table binding and visual styling.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                var tables by remember { mutableStateOf<List<ScheduleTable>>(emptyList()) }
                var selectedTableId by remember { mutableLongStateOf(0L) }
                var bgAlpha by remember { mutableFloatStateOf(0.92f) }
                var showLocation by remember { mutableStateOf(true) }
                var showTeacher by remember { mutableStateOf(false) }
                var showCalendarEvents by remember { mutableStateOf(true) }
                var useColorfulCards by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    scheduleRepository.ensureInitialized()
                    tables = scheduleRepository.getAllTables()
                    scheduleRepository.getWidgetBinding(appWidgetId)?.let { existing ->
                        selectedTableId = existing.tableId
                        bgAlpha = existing.styleConfig.backgroundAlpha
                        showLocation = existing.styleConfig.showLocation
                        showTeacher = existing.styleConfig.showTeacher
                        showCalendarEvents = existing.styleConfig.showCalendarEvents
                        useColorfulCards = existing.styleConfig.useColorfulCards
                    }
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("配置桌面小组件") }) }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("绑定课表", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = selectedTableId == 0L,
                                onClick = { selectedTableId = 0L },
                                label = { Text("跟随当前课表") }
                            )
                            tables.forEach { t ->
                                FilterChip(
                                    selected = selectedTableId == t.id,
                                    onClick = { selectedTableId = t.id },
                                    label = { Text(t.name, maxLines = 1) }
                                )
                            }
                        }

                        Text("背景不透明度：${(bgAlpha * 100).toInt()}%")
                        Slider(value = bgAlpha, onValueChange = { bgAlpha = it }, valueRange = 0.2f..1.0f)

                        ToggleRow("显示上课教室", showLocation) { showLocation = it }
                        ToggleRow("显示任课教师", showTeacher) { showTeacher = it }
                        ToggleRow("叠加显示日程待办", showCalendarEvents) { showCalendarEvents = it }
                        ToggleRow("彩色课程卡片", useColorfulCards) { useColorfulCards = it }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val providerClass = AppWidgetManager.getInstance(this@WidgetConfigActivity)
                                    .getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
                                val detectedType = when {
                                    providerClass.contains("WeekGrid") -> WidgetType.WEEK_GRID
                                    providerClass.contains("TodayTomorrow") -> WidgetType.TODAY_TOMORROW
                                    else -> WidgetType.TODAY
                                }
                                scope.launch {
                                    scheduleRepository.saveWidgetBinding(
                                        WidgetBinding(
                                            appWidgetId = appWidgetId,
                                            widgetType = detectedType,
                                            tableId = selectedTableId,
                                            styleConfig = WidgetStyleConfig(
                                                backgroundAlpha = bgAlpha,
                                                showLocation = showLocation,
                                                showTeacher = showTeacher,
                                                showCalendarEvents = showCalendarEvents,
                                                useColorfulCards = useColorfulCards
                                            )
                                        )
                                    )
                                    WidgetRefresher.refreshAll(this@WidgetConfigActivity)
                                    setResult(
                                        RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    )
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("保存并应用小组件")
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
