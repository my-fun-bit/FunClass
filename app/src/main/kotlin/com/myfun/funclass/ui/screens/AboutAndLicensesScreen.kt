package com.myfun.funclass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfun.funclass.BuildConfig
import com.myfun.funclass.ui.theme.funClassCardColors
import com.myfun.funclass.ui.theme.funClassScaffoldContainerColor
import com.myfun.funclass.ui.theme.funClassTopBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAndLicensesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var thirdPartyNotices by remember { mutableStateOf("正在加载第三方开源许可声明...") }
    var mitLicense by remember { mutableStateOf("正在加载 MIT 开源协议...") }

    LaunchedEffect(Unit) {
        val (loadedNotices, loadedMit) = withContext(Dispatchers.IO) {
            val notices = runCatching {
                context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse {
                "未能读取内置 THIRD_PARTY_NOTICES.md，请前往项目源码仓库根目录查看。"
            }
            val mit = runCatching {
                context.assets.open("LICENSE").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse {
                "未能读取内置 LICENSE 文件，请前往项目源码仓库根目录查看。"
            }
            notices to mit
        }
        thirdPartyNotices = loadedNotices
        mitLicense = loadedMit
    }

    val activeParagraphs = remember(selectedTab, thirdPartyNotices, mitLicense) {
        val raw = if (selectedTab == 0) thirdPartyNotices else mitLicense
        raw.split(Regex("\\r?\\n\\r?\\n"))
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
    }

    Scaffold(
        containerColor = funClassScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = funClassTopBarColors(),
                title = { Text("关于与开源许可") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "app_info") {
                Card(
                    colors = funClassCardColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "FunClass v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "应用包名：${BuildConfig.APPLICATION_ID}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "纯本地开源课表应用 · 零广告 · 零追踪 · 无云端依赖 · MIT License",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item(key = "license_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("第三方开源许可声明") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("本项目协议 (MIT)") }
                    )
                }
            }

            itemsIndexed(
                items = activeParagraphs,
                key = { idx, _ -> "p_${selectedTab}_$idx" }
            ) { _, paragraph ->
                SelectionContainer {
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
