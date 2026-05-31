package com.edu.audioprobe

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

/**
 * AudioProbe — minimal diagnostic APK for verifying the getUserMedia + ASR pipeline
 * on real hardware (GP15 Android 14 projector).
 *
 * Architecture:
 *   WebView (WebViewAssetLoader, https://appassets.androidplatform.net)
 *     → local index.html (secure context for getUserMedia)
 *       → JS: getUserMedia → MediaRecorder (WebM/Opus)
 *       → POST multipart to VPS /asr-probe endpoint
 *       → Display transcript + PASS/FAIL on screen
 *
 * WO-AUDIO-PROBE-v1: Independent from WonderBear commercial code.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AudioProbe"
        private const val PERMISSION_REQUEST_CODE = 2001
        // Default VPS endpoint — configurable at runtime via JS bridge
        private const val DEFAULT_SERVER_URL = "https://eagent.edu-aliyun.com"
    }

    private lateinit var webView: WebView
    private var serverUrl: String = DEFAULT_SERVER_URL

    // WebViewAssetLoader: serves local assets/web/* via virtual host
    // https://appassets.androidplatform.net/ → android_asset/web/
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler(
                "/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Fullscreen immersive
            setupFullscreen()

            // Keep screen on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // FLAG_FULLSCREEN for older API
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            }

            // Create WebView
            webView = WebView(this).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                    databaseEnabled = true
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = false
                }

                // Add JS bridge — exposes server URL + logging to WebView
                addJavascriptInterface(AudioProbeBridge(), "AudioProbe")

                // WebViewClient: serve local assets, no external browser
                webViewClient = createWebViewClient()

                // WO-AUDIO-PROBE-v1 坑 #1: MUST grant RESOURCE_AUDIO_CAPTURE
                // or getUserMedia fails with NotAllowedError
                webChromeClient = createWebChromeClient()
            }

            setContentView(webView)

            // Request RECORD_AUDIO at runtime (坑 #3: ROM may swallow dialog)
            requestAudioPermission()

            // Load local index.html via WebViewAssetLoader (secure context)
            Log.i(TAG, "Loading audio probe UI via WebViewAssetLoader")
            webView.loadUrl("https://appassets.androidplatform.net/index.html")

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate", e)
            // Show error on screen
            setContentView(android.widget.TextView(this).apply {
                text = "AudioProbe 启动失败\n${e.message}\n\n请重启应用"
                textSize = 24f
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.BLACK)
                gravity = android.view.Gravity.CENTER
                setPadding(48, 48, 48, 48)
            })
        }
    }

    // ── Permission ────────────────────────────────────────────

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Requesting RECORD_AUDIO permission")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            Log.i(TAG, "RECORD_AUDIO already granted")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "RECORD_AUDIO permission result: granted=$granted")

            // Notify JS side of permission result
            val jsGranted = if (granted) "true" else "false"
            webView.evaluateJavascript(
                "window.__audioProbeOnPermissionResult($jsGranted)",
                null
            )
        }
    }

    // ── WebViewClient ─────────────────────────────────────────

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(TAG, "WebView page loaded: $url")
            }

            // Keep all navigation in WebView
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null

                // Serve local assets via WebViewAssetLoader
                if (url.host == "appassets.androidplatform.net") {
                    return assetLoader.shouldInterceptRequest(url)
                }
                return null
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                Log.e(TAG, "WebView error: ${error?.description} for ${request?.url}")
            }
        }
    }

    // ── WebChromeClient ───────────────────────────────────────

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {

            // WO-AUDIO-PROBE-v1 坑 #1: 头号杀手!
            // WebView has its own permission layer on top of Android system permissions.
            // Even if RECORD_AUDIO is granted at system level, getUserMedia will fail
            // with NotAllowedError unless we explicitly grant RESOURCE_AUDIO_CAPTURE here.
            override fun onPermissionRequest(request: PermissionRequest?) {
                Log.i(TAG, "WebView permission requested: ${request?.resources?.joinToString()}")
                if (request != null) {
                    val resources = request.resources
                    // Grant audio capture explicitly — this is the critical line
                    if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        Log.i(TAG, "Granting RESOURCE_AUDIO_CAPTURE to WebView")
                    }
                    request.grant(resources)
                }
            }

            // Capture JS console → logcat (useful when ADB is available)
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d("AudioProbe-JS", "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                }
                return true
            }
        }
    }

    // ── Fullscreen ────────────────────────────────────────────

    private fun setupFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.systemBars())
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (e: Exception) {
                Log.w(TAG, "WindowInsetsController failed, using legacy flags", e)
                applyLegacyImmersiveFlags()
            }
        } else {
            applyLegacyImmersiveFlags()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyImmersiveFlags() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullscreen()
    }

    // ── Key handling ──────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Exit on double back
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000) {
                finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private var lastBackPressTime = 0L

    // ── JS Bridge ─────────────────────────────────────────────

    /**
     * JavaScript bridge exposed as window.AudioProbe.
     * Provides the VPS endpoint URL and a log-to-logcat channel.
     */
    inner class AudioProbeBridge {

        /** Get the configured ASR endpoint base URL */
        @android.webkit.JavascriptInterface
        fun getServerUrl(): String = serverUrl

        /** Update the ASR endpoint URL at runtime */
        @android.webkit.JavascriptInterface
        fun setServerUrl(url: String) {
            serverUrl = url
            Log.i(TAG, "Server URL updated to: $url")
        }

        /** Log a message from JS to logcat (for ADB debugging when available) */
        @android.webkit.JavascriptInterface
        fun logcat(tag: String, message: String) {
            Log.d("AudioProbe-$tag", message)
        }
    }
}
