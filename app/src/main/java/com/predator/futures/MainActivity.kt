package com.predator.futures

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {
    private lateinit var bridge: AndroidBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val ctrl = WindowInsetsControllerCompat(window, window.decorView)
        ctrl.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Enable WebView debugging for chrome://inspect (dev builds only)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        bridge = AndroidBridge(this)

        // Asset loader: serve local files under https://appassets.androidplatform.net/...
        // This gives the page a proper HTTPS origin so cross-origin fetches/WS work.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .setDomain("appassets.androidplatform.net")
            .setHttpAllowed(false)
            .build()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF7c3aed)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color(0xFF050508)) {
                    var loading by remember { mutableStateOf(true) }
                    var progress by remember { mutableStateOf(0f) }
                    var errorMsg by remember { mutableStateOf<String?>(null) }

                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    tag = "mainWebView"
                                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                    isVerticalScrollBarEnabled = false
                                    isHorizontalScrollBarEnabled = false
                                    overScrollMode = WebView.OVER_SCROLL_NEVER
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    isClickable = true
                                    isLongClickable = true
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        allowFileAccessFromFileURLs = false
                                        allowUniversalAccessFromFileURLs = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        mediaPlaybackRequiresUserGesture = false
                                        javaScriptCanOpenWindowsAutomatically = true
                                        loadsImagesAutomatically = true
                                        setSupportZoom(false)
                                        builtInZoomControls = false
                                        displayZoomControls = false
                                        userAgentString =
                                            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress / 100f
                                            if (newProgress >= 100) {
                                                view?.postDelayed({ loading = false }, 300)
                                            }
                                        }
                                        override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                            cm?.let {
                                                val tag = "JSConsole[${it.sourceId()?.substringAfterLast('/')}:${it.lineNumber()}]"
                                                when (it.messageLevel()) {
                                                    ConsoleMessage.MessageLevel.ERROR -> Log.e(tag, it.message())
                                                    ConsoleMessage.MessageLevel.WARNING -> Log.w(tag, it.message())
                                                    else -> Log.d(tag, it.message())
                                                }
                                            }
                                            return true
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            if (request == null) return null
                                            // 1) Binance REST'i native OkHttp ile proxy'le (CORS/451/geo-block kalkar)
                                            if (BinanceProxy.isBinanceRest(request.url.toString())) {
                                                return BinanceProxy.intercept(request)
                                            }
                                            // 2) Lokal asset'ler (WebViewAssetLoader)
                                            return assetLoader.shouldInterceptRequest(request.url)
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                errorMsg = error?.description?.toString() ?: "Yükleme hatası"
                                                loading = false
                                            }
                                            Log.e("PredatorWeb", "error ${error?.errorCode} : ${error?.description} @ ${request?.url}")
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            Log.i("PredatorWeb", "page finished: $url")
                                        }
                                    }
                                    addJavascriptInterface(bridge, "Android")
                                    loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
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
                                    errorMsg ?: "Veriler yükleniyor...",
                                    color = if (errorMsg != null) androidx.compose.ui.graphics.Color(0xFFef5350)
                                            else androidx.compose.ui.graphics.Color(0xFF8b949e),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(false)
    }
}
