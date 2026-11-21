package com.myfun.funclass.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfun.funclass.core.parser.CsvExcelImporter
import com.myfun.funclass.core.parser.ParserRegistry
import com.myfun.funclass.core.parser.SchoolAdapterInfo
import com.myfun.funclass.core.sync.ShareCodeRepository
import com.myfun.funclass.ui.MainUiState
import com.myfun.funclass.ui.components.QrCodeBitmapHelper
import com.myfun.funclass.ui.components.decodeTextSmart
import com.myfun.funclass.ui.theme.funClassCardColors
import com.myfun.funclass.ui.theme.funClassScaffoldContainerColor
import com.myfun.funclass.ui.theme.funClassTopBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCenterScreen(
    state: MainUiState,
    onOpenWebViewImport: (url: String, parserId: String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onImportRawFile: (fileName: String, content: String) -> Unit,
    onImportShareCode: (String) -> Unit,
    onGenerateShare: ((ShareCodeRepository.GeneratedShareOutput) -> Unit) -> Unit,
    onExportWakeupString: () -> String,
    onExportCsvString: () -> String,
    onExportIcsString: () -> String,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSchoolDialog by remember { mutableStateOf(false) }
    var showShareCodeImportDialog by remember { mutableStateOf(false) }
    var shareOutput by remember { mutableStateOf<ShareCodeRepository.GeneratedShareOutput?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // File import launcher (.funclass, .wakeup_schedule, .ics, .csv, .html)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = queryDisplayName(context, uri) ?: "imported.funclass"
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { decodeTextSmart(it.readBytes()) }
            }.getOrNull()
            if (text != null) {
                onImportRawFile(fileName, text)
            } else {
                onShowMessage("读取文件失败")
            }
        }
    }

    // Export SAF launchers
    var pendingExportContent by remember { mutableStateOf("") }
    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null && pendingExportContent.isNotEmpty()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(pendingExportContent)
                }
            }.onSuccess {
                onShowMessage("导出成功！")
            }.onFailure {
                onShowMessage("导出失败：${it.message}")
            }
        }
    }

    val createQrPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        val bmp = qrBitmap
        if (uri != null && bmp != null) {
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } ?: false
                    }.getOrDefault(false)
                }
                onShowMessage(if (ok) "二维码图片已保存" else "保存二维码失败")
            }
        }
    }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("课表导入与导出") }, colors = funClassTopBarColors()) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("导入方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                ChannelCard(
                    icon = Icons.Default.Language,
                    title = "教务网页导入",
                    subtitle = if (state.showHelperDescriptions) "登录学校教务网页，一键提取课表" else null,
                    onClick = { showSchoolDialog = true }
                )
            }
            item {
                ChannelCard(
                    icon = Icons.Default.Share,
                    title = "离线口令导入",
                    subtitle = if (state.showHelperDescriptions) "粘贴分享的离线口令 (FUNCLASS1:...) 导入" else null,
                    onClick = { showShareCodeImportDialog = true }
                )
            }
            item {
                ChannelCard(
                    icon = Icons.Default.FileOpen,
                    title = "本地文件导入",
                    subtitle = if (state.showHelperDescriptions) "自动识别 .funclass / .wakeup_schedule / .ics / .csv / .html" else null,
                    onClick = { openFileLauncher.launch(arrayOf("*/*")) }
                )
            }
            item {
                ChannelCard(
                    icon = Icons.Default.QrCodeScanner,
                    title = "二维码导入",
                    subtitle = if (state.showHelperDescriptions) "相机实时扫码，或从相册选择二维码图片" else null,
                    onClick = onOpenQrScanner
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text("分享与导出当前课表（${state.activeTable.name}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onGenerateShare { output ->
                                shareOutput = output
                                qrBitmap = QrCodeBitmapHelper.generate(output.portableToken)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("口令 / 二维码", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            pendingExportContent = onExportIcsString()
                            createFileLauncher.launch("${state.activeTable.name}.ics")
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导出 .ics 日历", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pendingExportContent = onExportWakeupString()
                            createFileLauncher.launch("${state.activeTable.name}.funclass")
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出 FunClass 文件", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            pendingExportContent = onExportCsvString()
                            createFileLauncher.launch("${state.activeTable.name}.csv")
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出 CSV 表格", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item {
                TextButton(
                    onClick = {
                        copyToClipboard(context, "FunClass CSV 模板", CsvExcelImporter.sampleTemplateCsv())
                        onShowMessage("标准 CSV 模板已复制到剪贴板")
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制标准 CSV 导入模板到剪贴板")
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // School & Academic System Picker Dialog
    if (showSchoolDialog) {
        var query by remember { mutableStateOf("") }
        var customUrl by remember { mutableStateOf("https://") }
        val filtered = remember(query) {
            if (query.isBlank()) ParserRegistry.builtInSchools
            else ParserRegistry.builtInSchools.filter {
                it.name.contains(query, ignoreCase = true) || it.systemFamily.contains(query, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showSchoolDialog = false },
            title = { Text("选择学校或教务类型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索学校或教务系统") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("或直接输入教务网址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(filtered) { school: SchoolAdapterInfo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSchoolDialog = false
                                        val targetUrl = school.loginUrl.ifBlank { customUrl.takeIf { it.length > 8 } ?: "about:blank" }
                                        onOpenWebViewImport(targetUrl, school.parserId)
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(school.name, fontWeight = FontWeight.Medium)
                                Text(school.parserId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSchoolDialog = false
                    onOpenWebViewImport(customUrl, "")
                }) { Text("打开自定义网址") }
            },
            dismissButton = {
                TextButton(onClick = { showSchoolDialog = false }) { Text("取消") }
            }
        )
    }

    // Share Code Import Dialog
    if (showShareCodeImportDialog) {
        var inputCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showShareCodeImportDialog = false },
            title = { Text("离线口令导入课表") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        label = { Text("粘贴离线口令 (FUNCLASS1:...)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.showHelperDescriptions) {
                        Text(
                            text = "粘贴好友发送的离线口令（FUNCLASS1:...），纯本地解码无需联网。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (inputCode.isNotBlank()) {
                        onImportShareCode(inputCode)
                        showShareCodeImportDialog = false
                    }
                }) { Text("立即解析导入") }
            },
            dismissButton = {
                TextButton(onClick = { showShareCodeImportDialog = false }) { Text("取消") }
            }
        )
    }

    // Generated Share Code + QR Dialog
    shareOutput?.let { out ->
        AlertDialog(
            onDismissRequest = { shareOutput = null },
            title = { Text("分享当前课表") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val bmp = qrBitmap
                    if (bmp != null) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 1.dp
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "课表分享二维码",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(200.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "当前课表课程数量较多，已超出单张二维码容量上限，请使用下方「复制离线口令」或导出 .funclass 文件分享。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (state.showHelperDescriptions) {
                        Text(
                            "离线口令与二维码已包含完整课表数据，无需服务器即可直接分享给好友。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (bmp != null) {
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        coroutineScope.launch {
                                            val fileName = "${state.activeTable.name}_课表二维码_${System.currentTimeMillis()}.png"
                                            val ok = withContext(Dispatchers.IO) {
                                                QrCodeBitmapHelper.saveBitmapToGallery(context, bmp, fileName)
                                            }
                                            onShowMessage(if (ok) "二维码已保存到系统相册（Pictures/FunClass）" else "保存二维码失败")
                                        }
                                    } else {
                                        createQrPngLauncher.launch("${state.activeTable.name}_课表二维码.png")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("保存二维码", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Button(
                            onClick = {
                                copyToClipboard(context, "FunClass 离线口令", out.shareText)
                                onShowMessage("离线分享口令已复制")
                                shareOutput = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制离线口令", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { shareOutput = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun ChannelCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Card(
        colors = funClassCardColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0)
    }
    return uri.lastPathSegment
}
