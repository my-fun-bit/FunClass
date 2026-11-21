package com.myfun.funclass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myfun.funclass.ui.FunClassApp
import com.myfun.funclass.ui.MainViewModel
import com.myfun.funclass.ui.components.decodeTextSmart
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private var pendingImportFile by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        // Hold the first drawn frame briefly until active table & current week are loaded from Room/DataStore,
        // preventing any Week-1 flash or pager jump on cold start.
        val contentView = findViewById<View>(android.R.id.content)
        val launchTimeMs = SystemClock.uptimeMillis()
        contentView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = mainViewModel.uiState.value.isReady
                    val timedOut = SystemClock.uptimeMillis() - launchTimeMs >= 800L
                    return if (ready || timedOut) {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )

        setContent {
            FunClassApp(
                incomingImportFile = pendingImportFile,
                onConsumeIncomingFile = { pendingImportFile = null },
                viewModel = mainViewModel
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        if (uri.scheme == "funclass") return
        val name = resolveFileName(uri) ?: "imported.funclass"
        val content = runCatching {
            contentResolver.openInputStream(uri)?.use { decodeTextSmart(it.readBytes()) }
        }.getOrNull() ?: return
        pendingImportFile = name to content
    }

    private fun resolveFileName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }
}
