package com.myfun.funclass.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfun.funclass.core.model.ScheduleDateResolver
import com.myfun.funclass.ui.MainUiState
import com.myfun.funclass.ui.theme.funClassCardColors
import com.myfun.funclass.ui.theme.funClassScaffoldContainerColor
import com.myfun.funclass.ui.theme.funClassTopBarColors
import com.myfun.funclass.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val calTimeFmt = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    state: MainUiState,
    onAddCalendar: () -> Unit,
    onEditCalendar: (Long) -> Unit,
    onDeleteCalendar: (Long) -> Unit,
    onToggleCompletion: (Long, String, Boolean) -> Unit
) {
    var filterTab by remember { mutableIntStateOf(0) } // 0 = Today, 1 = Next 7 Days, 2 = All Rules
    val today = LocalDate.now()

    val occurrences = remember(filterTab, state.calendarEvents, state.calendarCompletions) {
        when (filterTab) {
            0 -> ScheduleDateResolver.resolveCalendarEventsForDay(today, state.calendarEvents, state.calendarCompletions)
            1 -> (0L..6L).flatMap { offset ->
                ScheduleDateResolver.resolveCalendarEventsForDay(
                    today.plusDays(offset),
                    state.calendarEvents,
                    state.calendarCompletions
                )
            }
            else -> emptyList()
        }
    }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("日程与待办") }, colors = funClassTopBarColors()) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCalendar,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建日程")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(selected = filterTab == 0, onClick = { filterTab = 0 }, label = { Text("今日安排", maxLines = 1) })
                FilterChip(selected = filterTab == 1, onClick = { filterTab = 1 }, label = { Text("未来7天", maxLines = 1) })
                FilterChip(selected = filterTab == 2, onClick = { filterTab = 2 }, label = { Text("全部日程(${state.calendarEvents.size})", maxLines = 1) })
            }
            Spacer(Modifier.height(10.dp))

            if (filterTab != 2) {
                if (occurrences.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无日程待办，点击右下角「+」添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(occurrences, key = { "${it.event.id}_${it.date}" }) { occ ->
                            Card(
                                colors = funClassCardColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditCalendar(occ.event.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = occ.isCompleted,
                                        onCheckedChange = { checked ->
                                            onToggleCompletion(occ.event.id, occ.date.toString(), checked)
                                        }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(occ.event.colorHex))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = occ.event.title,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textDecoration = if (occ.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                        val timeDesc = if (occ.event.isAllDay) "全天" else "${occ.startDateTime.toLocalTime().format(calTimeFmt)}-${occ.endDateTime.toLocalTime().format(calTimeFmt)}"
                                        Text(
                                            text = "${occ.date} · $timeDesc · ${occ.event.repeatType.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (occ.event.location.isNotBlank()) {
                                            Text(
                                                text = "地点：${occ.event.location}",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeleteCalendar(occ.event.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (state.calendarEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有创建过日程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(state.calendarEvents, key = { it.id }) { event ->
                            Card(
                                colors = funClassCardColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditCalendar(event.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(event.colorHex))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = event.title,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "生效日：${event.effectiveDate} · 重复：${event.repeatType.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onDeleteCalendar(event.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除")
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
