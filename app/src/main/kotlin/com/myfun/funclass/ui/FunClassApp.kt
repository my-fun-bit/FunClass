package com.myfun.funclass.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.myfun.funclass.ui.theme.FunClassVisualStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myfun.funclass.reminder.FluidCloudNotifier
import com.myfun.funclass.ui.components.MaterialDatePickerModal
import com.myfun.funclass.ui.components.PickerFieldCard
import com.myfun.funclass.ui.screens.AboutAndLicensesScreen
import com.myfun.funclass.ui.screens.AddEditScreen
import com.myfun.funclass.ui.screens.AppearanceScreen
import com.myfun.funclass.ui.screens.BatchToolsScreen
import com.myfun.funclass.ui.screens.CalendarScreen
import com.myfun.funclass.ui.screens.HolidayAdjustScreen
import com.myfun.funclass.ui.screens.ImportCenterScreen
import com.myfun.funclass.ui.screens.QrScannerScreen
import com.myfun.funclass.ui.screens.ReminderSettingsScreen
import com.myfun.funclass.ui.screens.ScheduleScreen
import com.myfun.funclass.ui.screens.SettingsHubScreen
import com.myfun.funclass.ui.screens.SettingsSubRoute
import com.myfun.funclass.ui.screens.TableManageScreen
import com.myfun.funclass.ui.screens.TimeSchemeScreen
import com.myfun.funclass.ui.screens.WebViewImportScreen
import com.myfun.funclass.ui.theme.FunClassTheme

private object Routes {
    const val SCHEDULE = "schedule"
    const val CALENDAR = "calendar"
    const val IMPORT = "import"
    const val SETTINGS = "settings"
    const val ADD_EDIT = "add_edit?cal={cal}&courseId={courseId}&calId={calId}&day={day}&node={node}"
    const val WEBVIEW = "webview?url={url}&parser={parser}"
    const val QR_SCAN = "qr_scan"
    const val SUB_TABLES = "sub/tables"
    const val SUB_TIME = "sub/time"
    const val SUB_APPEARANCE = "sub/appearance"
    const val SUB_HOLIDAYS = "sub/holidays"
    const val SUB_BATCH = "sub/batch"
    const val SUB_REMINDERS = "sub/reminders"
    const val SUB_ABOUT = "sub/about"
}

@Composable
fun FunClassApp(
    incomingImportFile: Pair<String, String>? = null,
    onConsumeIncomingFile: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val context = LocalContext.current

    var showNotifPromptDialog by remember { mutableStateOf(false) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    // Android 13+ (API 33+): proactively guide user to grant POST_NOTIFICATIONS on first launch
    LaunchedEffect(state.isReady, state.hasPromptedNotificationPermission) {
        if (state.isReady && !state.hasPromptedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!FluidCloudNotifier.hasNotificationPermission(context)) {
                showNotifPromptDialog = true
            } else {
                viewModel.markNotificationPermissionPrompted()
            }
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(incomingImportFile) {
        incomingImportFile?.let { (name, content) ->
            viewModel.importFromRawFileContent(name, content)
            onConsumeIncomingFile()
        }
    }

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val topTabs = listOf(Routes.SCHEDULE, Routes.CALENDAR, Routes.IMPORT, Routes.SETTINGS)

    val appearance = state.activeTable.appearance
    val hasBg = !appearance.backgroundUri.isNullOrBlank()
    val showBgOnCurrentRoute = hasBg && (
        currentRoute == null ||
            currentRoute == Routes.SCHEDULE ||
            (appearance.applyBackgroundGlobally &&
                currentRoute != Routes.QR_SCAN &&
                currentRoute != Routes.WEBVIEW)
        )
    val visualStyle = FunClassVisualStyle(
        hasVisibleBackground = showBgOnCurrentRoute,
        chromeAlpha = appearance.chromeSurfaceAlpha.coerceIn(0f, 1f),
        cardAlpha = (appearance.chromeSurfaceAlpha + 0.24f).coerceIn(0.50f, 0.96f),
        pastelCourseStyle = appearance.pastelCourseStyle
    )

    FunClassTheme(
        darkModePolicy = appearance.darkModePolicy,
        themePreset = appearance.themePreset,
        useDynamicColor = appearance.useDynamicColor,
        customThemeColorHex = appearance.customThemeColorHex,
        visualStyle = visualStyle
    ) {
        if (!state.isReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
            return@FunClassTheme
        }

        val navItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(
                alpha = if (showBgOnCurrentRoute) 0.88f else 1f
            ),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showBgOnCurrentRoute && !appearance.backgroundUri.isNullOrBlank()) {
                AsyncImage(
                    model = Uri.parse(appearance.backgroundUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                val effectiveVeilAlpha = if (currentRoute == null || currentRoute == Routes.SCHEDULE) {
                    appearance.backgroundDimAlpha.coerceIn(0f, 0.85f)
                } else {
                    (appearance.backgroundDimAlpha + 0.36f).coerceIn(0.45f, 0.85f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = effectiveVeilAlpha))
                )
            }

            Scaffold(
                containerColor = if (showBgOnCurrentRoute) Color.Transparent else MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (currentRoute in topTabs) {
                        NavigationBar(
                            containerColor = if (showBgOnCurrentRoute) {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = visualStyle.chromeAlpha)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                selected = currentRoute == Routes.SCHEDULE,
                                onClick = { navController.navigate(Routes.SCHEDULE) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "课表") },
                                label = { Text("课表", maxLines = 1, softWrap = false) },
                                colors = navItemColors
                            )
                            NavigationBarItem(
                                selected = currentRoute == Routes.CALENDAR,
                                onClick = { navController.navigate(Routes.CALENDAR) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = "日程") },
                                label = { Text("日程", maxLines = 1, softWrap = false) },
                                colors = navItemColors
                            )
                            NavigationBarItem(
                                selected = currentRoute == Routes.IMPORT,
                                onClick = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Download, contentDescription = "导入") },
                                label = { Text("导入导出", maxLines = 1, softWrap = false) },
                                colors = navItemColors
                            )
                            NavigationBarItem(
                                selected = currentRoute == Routes.SETTINGS,
                                onClick = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                                label = { Text("设置", maxLines = 1, softWrap = false) },
                                colors = navItemColors
                            )
                        }
                    }
                }
            ) { outerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.SCHEDULE,
                    modifier = Modifier.padding(outerPadding)
                ) {
                composable(Routes.SCHEDULE) {
                    ScheduleScreen(
                        state = state,
                        onSelectWeek = viewModel::selectWeek,
                        onJumpCurrentWeek = viewModel::jumpToCurrentWeek,
                        onSwitchTable = viewModel::switchTable,
                        onAddClick = { day, node ->
                            val d = day ?: -1
                            val n = node ?: -1
                            navController.navigate("add_edit?cal=false&courseId=-1&calId=-1&day=$d&node=$n")
                        },
                        onEditCourse = { id ->
                            navController.navigate("add_edit?cal=false&courseId=$id&calId=-1&day=-1&node=-1")
                        },
                        onDeleteCourse = viewModel::deleteCourse,
                        onUpdateSessionWeekNote = viewModel::updateSessionWeekNote,
                        onEditCalendar = { id ->
                            navController.navigate("add_edit?cal=true&courseId=-1&calId=$id&day=-1&node=-1")
                        },
                        onToggleCalendarDone = viewModel::toggleCalendarCompletion,
                        onOpenAppearance = { navController.navigate(Routes.SUB_APPEARANCE) },
                        onOpenTimeScheme = { navController.navigate(Routes.SUB_TIME) },
                        onSaveCustomBreak = viewModel::saveCustomBreak,
                        onDeleteCustomBreak = viewModel::deleteCustomBreak
                    )
                }

                composable(Routes.CALENDAR) {
                    CalendarScreen(
                        state = state,
                        onAddCalendar = {
                            navController.navigate("add_edit?cal=true&courseId=-1&calId=-1&day=-1&node=-1")
                        },
                        onEditCalendar = { id ->
                            navController.navigate("add_edit?cal=true&courseId=-1&calId=$id&day=-1&node=-1")
                        },
                        onDeleteCalendar = viewModel::deleteCalendarEvent,
                        onToggleCompletion = viewModel::toggleCalendarCompletion
                    )
                }

                composable(Routes.IMPORT) {
                    ImportCenterScreen(
                        state = state,
                        onOpenWebViewImport = { url, parserId ->
                            val encUrl = Uri.encode(url)
                            val encParser = Uri.encode(parserId)
                            navController.navigate("webview?url=$encUrl&parser=$encParser")
                        },
                        onOpenQrScanner = { navController.navigate(Routes.QR_SCAN) },
                        onImportRawFile = viewModel::importFromRawFileContent,
                        onImportShareCode = viewModel::importFromShareCode,
                        onGenerateShare = viewModel::generateShareCode,
                        onExportWakeupString = viewModel::exportWakeupScheduleString,
                        onExportCsvString = viewModel::exportCsvString,
                        onExportIcsString = viewModel::exportIcsString,
                        onShowMessage = viewModel::showMessage
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsHubScreen(
                        state = state,
                        onToggleHelperDescriptions = viewModel::setShowHelperDescriptions
                    ) { sub ->
                        val dest = when (sub) {
                            SettingsSubRoute.TABLES -> Routes.SUB_TABLES
                            SettingsSubRoute.TIME_SCHEMES -> Routes.SUB_TIME
                            SettingsSubRoute.APPEARANCE -> Routes.SUB_APPEARANCE
                            SettingsSubRoute.HOLIDAYS -> Routes.SUB_HOLIDAYS
                            SettingsSubRoute.BATCH_TOOLS -> Routes.SUB_BATCH
                            SettingsSubRoute.REMINDERS -> Routes.SUB_REMINDERS
                            SettingsSubRoute.ABOUT_LICENSES -> Routes.SUB_ABOUT
                        }
                        navController.navigate(dest)
                    }
                }

                composable(
                    route = Routes.ADD_EDIT,
                    arguments = listOf(
                        navArgument("cal") { type = NavType.BoolType; defaultValue = false },
                        navArgument("courseId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("calId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("day") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("node") { type = NavType.IntType; defaultValue = -1 }
                    )
                ) { entry ->
                    val args = entry.arguments
                    AddEditScreen(
                        state = state,
                        initialModeCalendar = args?.getBoolean("cal") ?: false,
                        editingCourseId = args?.getLong("courseId")?.takeIf { it > 0L },
                        editingCalendarId = args?.getLong("calId")?.takeIf { it > 0L },
                        prefillDay = args?.getInt("day")?.takeIf { it in 1..7 },
                        prefillNode = args?.getInt("node")?.takeIf { it > 0 },
                        onSaveCourse = viewModel::saveCourse,
                        onSaveCalendar = viewModel::saveCalendarEvent,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.WEBVIEW,
                    arguments = listOf(
                        navArgument("url") { type = NavType.StringType; defaultValue = "" },
                        navArgument("parser") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { entry ->
                    WebViewImportScreen(
                        initialUrl = Uri.decode(entry.arguments?.getString("url").orEmpty()),
                        preferredParserId = Uri.decode(entry.arguments?.getString("parser").orEmpty()),
                        onScheduleParsed = { parsed ->
                            viewModel.stageParsedScheduleForConfirmation(parsed, sourceLabel = "教务网页导入")
                        },
                        onShowMessage = viewModel::showMessage,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.QR_SCAN) {
                    QrScannerScreen(
                        onQrScanned = { text -> viewModel.importFromShareCode(text) },
                        onShowMessage = viewModel::showMessage,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SUB_TABLES) {
                    TableManageScreen(
                        state = state,
                        onSwitchTable = viewModel::switchTable,
                        onSaveTable = viewModel::saveTable,
                        onDeleteTable = viewModel::deleteTable,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SUB_TIME) {
                    TimeSchemeScreen(
                        state = state,
                        onSaveTimeScheme = { scheme, genCfg ->
                            viewModel.saveTimeScheme(scheme, genCfg, syncTableMaxNodes = true)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SUB_APPEARANCE) {
                    AppearanceScreen(
                        state = state,
                        onUpdateAppearance = viewModel::updateAppearance,
                        onOpenTimeScheme = { navController.navigate(Routes.SUB_TIME) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SUB_HOLIDAYS) {
                    HolidayAdjustScreen(
                        state = state,
                        onApplyPreset = viewModel::applyChinaHolidayPresets,
                        onClearPreset = viewModel::clearHolidayPresets,
                        onSaveAdjustment = viewModel::saveHolidayAdjustment,
                        onDeleteAdjustment = viewModel::deleteHolidayAdjustment,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SUB_BATCH) {
                    BatchToolsScreen(
                        showHelperDescriptions = state.showHelperDescriptions,
                        onShiftWeeks = viewModel::batchShiftWeeks,
                        onSwapDays = viewModel::batchSwapDays,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SUB_REMINDERS) {
                    ReminderSettingsScreen(
                        state = state,
                        checkCanScheduleExact = { viewModel.reminderScheduler.canScheduleExactAlarms() },
                        checkIsIgnoringBatteryOptimizations = { viewModel.reminderScheduler.isIgnoringBatteryOptimizations() },
                        checkHasDndPermission = { viewModel.reminderScheduler.hasDndPermission() },
                        onUpdateSettings = viewModel::updateReminderSettings,
                        onShowMessage = viewModel::showMessage,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SUB_ABOUT) {
                    AboutAndLicensesScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            }
        }

        if (showNotifPromptDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AlertDialog(
                onDismissRequest = {
                    showNotifPromptDialog = false
                    viewModel.markNotificationPermissionPrompted()
                },
                icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                title = { Text("开启上课提醒与实时胶囊") },
                text = {
                    Text("授予通知权限后，应用可在课前自动推送上课倒计时提醒；在支持的 Android 16+ 设备上还能以状态栏实时活动胶囊显示，确保不错过每一节课。后续也可在「设置 -> 提醒与静音」中随时调整。")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showNotifPromptDialog = false
                            viewModel.markNotificationPermissionPrompted()
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    ) { Text("立即授权") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotifPromptDialog = false
                            viewModel.markNotificationPermissionPrompted()
                        }
                    ) { Text("暂不开启") }
                }
            )
        }

        // Universal Pre-Import Confirmation Dialog (ICS / WakeUp / CSV / HTML / WebView / ShareCode)
        state.pendingImport?.let { pending ->
            var showStartDatePicker by remember(pending.sourceLabel, pending.rawFileName) { mutableStateOf(false) }
            val allWeeks = pending.preview.courses.flatMap { c -> c.sessions.flatMap { it.weeks.weeks } }
            val minWeek = allWeeks.minOrNull() ?: 1
            val maxWeek = allWeeks.maxOrNull() ?: pending.preview.maxWeeks

            AlertDialog(
                onDismissRequest = viewModel::dismissPendingImport,
                title = { Text("确认导入课表（${pending.sourceLabel}）") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = pending.tableName,
                            onValueChange = viewModel::updatePendingImportTableName,
                            label = { Text("新课表名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PickerFieldCard(
                            label = "第一周开学日期（自动对齐周一）",
                            value = pending.termStartDate.toString(),
                            icon = Icons.Default.CalendarToday,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showStartDatePicker = true }
                        )
                        Text(
                            text = "共 ${pending.preview.courses.size} 门课 · 第 $minWeek - $maxWeek 周 · 每日 ${pending.preview.maxNodes} 节" +
                                if (pending.rawIcsContent != null) "（调整开学日期将实时重算周次）" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (pending.preview.warnings.isNotEmpty()) {
                            Text(
                                text = "提示：${pending.preview.warnings.take(2).joinToString("；")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        HorizontalDivider()
                        Text(
                            text = "解析课程预览：",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        pending.preview.courses.forEach { course ->
                            val sessionSummary = course.sessions.joinToString("；") { s ->
                                val roomPart = s.room.takeIf { it.isNotBlank() }?.let { " @$it" } ?: ""
                                "${s.weeks.toDisplayString()} 第${s.startNode}-${s.endNode}节$roomPart"
                            }
                            Text(
                                text = "• ${course.name}：$sessionSummary",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmPendingImport,
                        enabled = pending.preview.courses.isNotEmpty()
                    ) { Text("确认导入") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPendingImport) { Text("取消") }
                }
            )

            if (showStartDatePicker) {
                MaterialDatePickerModal(
                    initialDate = pending.termStartDate,
                    title = "选择第一周开学日期",
                    onDismiss = { showStartDatePicker = false },
                    onConfirm = { picked ->
                        viewModel.updatePendingImportTermStart(picked)
                        showStartDatePicker = false
                    }
                )
            }
        }
    }
}
