package com.myfun.funclass.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Size
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myfun.funclass.core.parser.JsPluginBridgeProtocol
import com.myfun.funclass.core.parser.ParsedScheduleResult
import com.myfun.funclass.core.parser.ParserRegistry
import com.myfun.funclass.ui.components.QrCodeBitmapHelper
import com.myfun.funclass.ui.components.QrCodeCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure native AndroidX WebView academic importer.
 * Supports built-in 5-system parsers + custom JS script injection via FunClassJsBridge.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewImportScreen(
    initialUrl: String,
    preferredParserId: String,
    onScheduleParsed: (ParsedScheduleResult) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    var urlInput by remember { mutableStateOf(initialUrl.ifBlank { "https://" }) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customJsScript by remember { mutableStateOf("") }
    var showJsEditor by remember { mutableStateOf(false) }

    val jsBridge = remember(preferredParserId) {
        object {
            @JavascriptInterface
            fun onHtmlExtracted(html: String) {
                val parsedResult = runCatching {
                    ParserRegistry.autoParse(html, preferredParserId.ifBlank { null })
                }
                webViewRef?.post {
                    parsedResult.onSuccess { parsed ->
                        if (parsed.courses.isNotEmpty()) {
                            onScheduleParsed(parsed)
                            onBack()
                        } else {
                            onShowMessage(parsed.warnings.firstOrNull() ?: "当前页面未检测到课表，请先登录并进入课表查询页面")
                        }
                    }.onFailure { err ->
                        onShowMessage("解析页面课表失败：${err.message ?: "页面格式暂不兼容"}")
                    }
                }
            }

            @JavascriptInterface
            fun onScheduleParsed(jsonPayload: String) {
                val parsedResult = runCatching {
                    JsPluginBridgeProtocol.tryParsePayload(jsonPayload)
                        ?: ParserRegistry.autoParse(jsonPayload, preferredParserId.ifBlank { null })
                }
                webViewRef?.post {
                    parsedResult.onSuccess { parsed ->
                        if (parsed.courses.isNotEmpty()) {
                            onScheduleParsed(parsed)
                            onBack()
                        } else {
                            onShowMessage("自定义脚本未返回有效课程数据")
                        }
                    }.onFailure { err ->
                        onShowMessage("脚本数据解析失败：${err.message ?: "格式错误"}")
                    }
                }
            }

            @JavascriptInterface
            fun onError(msg: String) {
                webViewRef?.post { onShowMessage("提取脚本错误：$msg") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教务网页导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showJsEditor = true }) {
                        Icon(Icons.Default.Code, contentDescription = "自定义 JS 脚本")
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val scriptToRun = customJsScript.ifBlank { JsPluginBridgeProtocol.DEFAULT_WEBVIEW_EXTRACTOR_JS }
                    webViewRef?.evaluateJavascript(scriptToRun, null)
                },
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                text = { Text("一键提取课表") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("教务系统网址") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val target = if (urlInput.startsWith("http://") || urlInput.startsWith("https://")) {
                        urlInput
                    } else {
                        "https://$urlInput"
                    }
                    webViewRef?.loadUrl(target)
                }) {
                    Text("前往")
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        addJavascriptInterface(jsBridge, JsPluginBridgeProtocol.JS_INTERFACE_NAME)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (!url.isNullOrBlank() && url != "about:blank") {
                                    urlInput = url
                                }
                            }
                        }
                        webChromeClient = WebChromeClient()
                        if (initialUrl.startsWith("http://") || initialUrl.startsWith("https://")) {
                            loadUrl(initialUrl)
                        } else {
                            loadDataWithBaseURL(
                                null,
                                "<html><body style='font-family:sans-serif;padding:24px;color:#555;'><h3>请输入学校教务系统网址</h3><p>登录并切换到【我的课表】页面后，点击右下角「一键提取课表」即可自动识别正方、强智、新 URP、金智、青果或通用 HTML 课表。</p></body></html>",
                                "text/html",
                                "utf-8",
                                null
                            )
                        }
                        webViewRef = this
                    }
                }
            )
        }
    }

    if (showJsEditor) {
        AlertDialog(
            onDismissRequest = { showJsEditor = false },
            title = { Text("自定义 JS 适配脚本（可选）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "留空则使用内置通用提取脚本；自定义脚本可调用 FunClassJsBridge.onScheduleParsed(JSON.stringify({funclassPluginVersion:1, courses:[...]}))。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customJsScript,
                        onValueChange = { customJsScript = it },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showJsEditor = false }) { Text("保存脚本") }
            },
            dismissButton = {
                TextButton(onClick = {
                    customJsScript = ""
                    showJsEditor = false
                }) { Text("恢复默认") }
            }
        )
    }
}

/**
 * Pure open-source CameraX + ZXing QR scanner with album photo picker support
 * (zero Google Play Services / MLKit dependency, zero storage permissions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onQrScanned: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val scanCompleted = remember { AtomicBoolean(false) }
    var isDecodingGalleryImage by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null || scanCompleted.get()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isDecodingGalleryImage = true
            val decoded = withContext(Dispatchers.IO) {
                QrCodeBitmapHelper.decodeFromUri(context, uri)
            }
            isDecodingGalleryImage = false
            if (!decoded.isNullOrBlank()) {
                if (scanCompleted.compareAndSet(false, true)) {
                    onQrScanned(decoded)
                    onBack()
                }
            } else {
                onShowMessage("未在图片中识别到二维码，请选择更清晰的课表二维码图片")
            }
        }
    }

    val openGalleryPicker = {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(cameraRef, torchEnabled) {
        runCatching { cameraRef?.cameraControl?.enableTorch(torchEnabled) }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProviderRef?.unbindAll() }
            executor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码导入课表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = openGalleryPicker) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("相册")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (!hasCameraPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text("需要相机权限以实时扫描课表二维码，您也可以直接从相册选图识别")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("授予相机权限")
                    }
                    OutlinedButton(onClick = openGalleryPicker) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("从相册选择二维码")
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val provider = runCatching { cameraProviderFuture.get() }.getOrNull()
                                ?: return@addListener
                            cameraProviderRef = provider

                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val resolutionSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(resolutionSelector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            analysis.setAnalyzer(executor) { imageProxy ->
                                try {
                                    if (!scanCompleted.get()) {
                                        val yPlane = imageProxy.planes[0]
                                        val tightY = QrCodeCodec.extractTightYPlane(
                                            yBuffer = yPlane.buffer,
                                            width = imageProxy.width,
                                            height = imageProxy.height,
                                            rowStride = yPlane.rowStride,
                                            pixelStride = yPlane.pixelStride
                                        )
                                        val decodedText = QrCodeCodec.decodeTightYuv(
                                            yBytes = tightY,
                                            width = imageProxy.width,
                                            height = imageProxy.height
                                        )
                                        if (!decodedText.isNullOrBlank() && scanCompleted.compareAndSet(false, true)) {
                                            previewView.post {
                                                onQrScanned(decodedText)
                                                onBack()
                                            }
                                        }
                                    }
                                } finally {
                                    imageProxy.close()
                                }
                            }

                            runCatching {
                                provider.unbindAll()
                                val camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                                cameraRef = camera
                                hasFlashUnit = camera.cameraInfo.hasFlashUnit()

                                val initialPoint = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
                                val initialAction = FocusMeteringAction.Builder(
                                    initialPoint,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                )
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                camera.cameraControl.startFocusAndMetering(initialAction)

                                previewView.setOnTouchListener { view, event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN -> true
                                        MotionEvent.ACTION_UP -> {
                                            val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                                            val action = FocusMeteringAction.Builder(
                                                point,
                                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                            )
                                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                                .build()
                                            camera.cameraControl.startFocusAndMetering(action)
                                            view.performClick()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                // Viewfinder Mask & Corner Brackets
                val accentColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSize = minOf(size.width, size.height) * 0.66f
                    val left = (size.width - boxSize) / 2f
                    val top = (size.height - boxSize) / 2f - size.height * 0.04f
                    val right = left + boxSize
                    val bottom = top + boxSize
                    val scrim = Color.Black.copy(alpha = 0.45f)

                    // 4 outer dim bands leaving the viewfinder completely clear
                    drawRect(scrim, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, top.coerceAtLeast(0f)))
                    drawRect(
                        scrim,
                        topLeft = Offset(0f, bottom),
                        size = androidx.compose.ui.geometry.Size(size.width, (size.height - bottom).coerceAtLeast(0f))
                    )
                    drawRect(scrim, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left.coerceAtLeast(0f), boxSize))
                    drawRect(
                        scrim,
                        topLeft = Offset(right, top),
                        size = androidx.compose.ui.geometry.Size((size.width - right).coerceAtLeast(0f), boxSize)
                    )

                    // Corner brackets
                    val cornerLen = boxSize * 0.12f
                    val strokeW = 4.dp.toPx()
                    val corners = listOf(
                        Offset(left, top) to Pair(Offset(left + cornerLen, top), Offset(left, top + cornerLen)),
                        Offset(right, top) to Pair(Offset(right - cornerLen, top), Offset(right, top + cornerLen)),
                        Offset(left, bottom) to Pair(Offset(left + cornerLen, bottom), Offset(left, bottom - cornerLen)),
                        Offset(right, bottom) to Pair(Offset(right - cornerLen, bottom), Offset(right, bottom - cornerLen))
                    )
                    for ((origin, arms) in corners) {
                        drawLine(accentColor, origin, arms.first, strokeWidth = strokeW, cap = StrokeCap.Round)
                        drawLine(accentColor, origin, arms.second, strokeWidth = strokeW, cap = StrokeCap.Round)
                    }
                }

                // Bottom guidance + Album & Torch pill buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Text(
                            text = "将课表分享二维码置于框内，轻触画面可对焦",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = openGalleryPicker) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("相册选图")
                        }
                        if (hasFlashUnit) {
                            FilledTonalButton(onClick = { torchEnabled = !torchEnabled }) {
                                Icon(
                                    if (torchEnabled) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (torchEnabled) "关闭手电筒" else "手电筒")
                            }
                        }
                    }
                }
            }

            if (isDecodingGalleryImage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text("正在识别图片中的二维码...", color = Color.White)
                    }
                }
            }
        }
    }
}
