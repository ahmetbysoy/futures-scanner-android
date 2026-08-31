package com.predator.futures

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bridge: AndroidBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val ctrl = WindowInsetsControllerCompat(window, window.decorView)
        ctrl.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        bridge = AndroidBridge(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF7c3aed)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color(0xFF050508)) {
                    var loading by remember { mutableStateOf(true) }
                    var progress by remember { mutableStateOf(0f) }

                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                    isVerticalScrollBarEnabled = false
                                    isHorizontalScrollBarEnabled = false
                                    overScrollMode = WebView.OVER_SCROLL_NEVER
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        mediaPlaybackRequiresUserGesture = false
                                        setSupportZoom(false)
                                        builtInZoomControls = false
                                        displayZoomControls = false
                                        userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36 FuturesScanner/1.0"
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress / 100f
                                            if (newProgress >= 100) {
                                                view?.postDelayed({ loading = false }, 300)
                                            }
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                        }
                                    }
                                    addJavascriptInterface(bridge, "Android")
                                    loadUrl("file:///android_asset/web/index.html")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (loading) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Futures Scanner",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(0.7f).height(6.dp),
                                    color = androidx.compose.ui.graphics.Color(0xFF7c3aed),
                                    trackColor = androidx.compose.ui.graphics.Color(0xFF2a3038),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Veriler yükleniyor...",
                                    color = androidx.compose.ui.graphics.Color(0xFF8b949e),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Poll connection status periodically and surface via bridge
        lifecycleScope.launch {
            while (true) {
                delay(5000)
                // keep-alive wake lock handled via system
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Swallow back press — app is single-screen; back shouldn't exit accidentally
        moveTaskToBack(false)
    }
}
