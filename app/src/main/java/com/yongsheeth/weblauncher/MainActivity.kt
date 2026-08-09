package com.yongsheeth.weblauncher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var assetLoader: WebViewAssetLoader? = null
    private lateinit var settingsManager: SettingsManager
    
    private val pressedKeys = mutableSetOf<Int>()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            val event = if (intent.action == Intent.ACTION_PACKAGE_ADDED) "appInstalled" else "appUninstalled"
            val payload = buildJsonObject { put("packageName", packageName) }.toString()
            emitEvent(event, payload)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val payload = buildJsonObject {
                put("level", pct)
                put("isCharging", isCharging)
            }.toString()
            emitEvent("batteryStateChanged", payload)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val payload = buildJsonObject { put("available", true) }.toString()
            emitEvent("networkStateChanged", payload)
        }
        override fun onLost(network: Network) {
            val payload = buildJsonObject { put("available", false) }.toString()
            emitEvent("networkStateChanged", payload)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        settingsManager = SettingsManager(this)
        
        webView = WebView(this)
        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
        })

        setContent {
            val isFloatingButtonEnabled by settingsManager.isFloatingButtonEnabled.collectAsState(initial = false)
            
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
                
                if (isFloatingButtonEnabled) {
                    FloatingActionButton(
                        onClick = { openLauncherSettings() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .size(56.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Safety Settings")
                    }
                }
            }
        }
        
        registerListeners()

        lifecycleScope.launch {
            combine(
                settingsManager.sourceType,
                settingsManager.selectedProjectUri,
                settingsManager.githubUrl
            ) { type, uri, _ ->
                type to uri
            }.collect { (type, uri) ->
                // Per instructions, we validate but don't block yet
                val isValid = ProjectValidator.validateProject(this@MainActivity, type, uri)
                if (!isValid && type != SourceType.ASSETS) {
                    android.util.Log.w("WebLauncher", "Project might be missing Settings entry point!")
                }
                
                updateAssetLoader(type, uri)
                loadSource(type)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerListeners() {
        // Package changes
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, pkgFilter)

        // Battery changes
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Network changes
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
        unregisterReceiver(batteryReceiver)
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun emitEvent(name: String, payload: String) {
        webView.post {
            val js = "if(window.LauncherEngine && window.LauncherEngine.emitEvent) { window.LauncherEngine.emitEvent('$name', $payload); }"
            webView.evaluateJavascript(js, null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        
        webView.addJavascriptInterface(LauncherEngine(this), "LauncherEngine")
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader?.shouldInterceptRequest(request.url)
            }
        }
    }

    private fun updateAssetLoader(type: SourceType, treeUriString: String?) {
        val builder = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPathHandler(this))
            .addPathHandler("/app-icon/", AppIconPathHandler(this))
        
        if (type == SourceType.LOCAL && treeUriString != null) {
            val treeUri = treeUriString.toUri()
            builder.addPathHandler("/local_project/", DocumentTreePathHandler(this, treeUri))
        } else if (type == SourceType.GITHUB) {
             builder.addPathHandler("/local_project/", WebViewAssetLoader.InternalStoragePathHandler(this, File(filesDir, "ui_source")))
        }
        
        assetLoader = builder.build()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        pressedKeys.add(keyCode)
        if (pressedKeys.contains(KeyEvent.KEYCODE_VOLUME_UP) && 
            pressedKeys.contains(KeyEvent.KEYCODE_VOLUME_DOWN)) {
            openLauncherSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        pressedKeys.remove(keyCode)
        return super.onKeyUp(keyCode, event)
    }

    private fun openLauncherSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun loadSource(type: SourceType) {
        val url = when (type) {
            SourceType.ASSETS -> "https://appassets.androidplatform.net/assets/www/index.html"
            SourceType.LOCAL, SourceType.GITHUB -> "https://appassets.androidplatform.net/local_project/index.html"
        }
        android.util.Log.d("WebLauncher", "Loading URL: $url")
        webView.loadUrl(url)
    }

    fun reloadWebView() {
        lifecycleScope.launch {
            val type = settingsManager.sourceType.first()
            loadSource(type)
        }
    }
}
