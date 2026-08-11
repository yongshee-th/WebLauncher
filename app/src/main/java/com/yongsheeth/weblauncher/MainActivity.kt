package com.yongsheeth.weblauncher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        reloadWebView()
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            val action = if (intent.action == Intent.ACTION_PACKAGE_ADDED) "added" else "removed"
            val payload = buildJsonObject { 
                put("action", action)
                put("packageName", packageName) 
            }.toString()
            emitEvent("onAppListChanged", payload)
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
            emitEvent("onBatteryStatusChanged", payload)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> emitEvent("onScreenStateChanged", "{\"isScreenOn\": true}")
                Intent.ACTION_SCREEN_OFF -> emitEvent("onScreenStateChanged", "{\"isScreenOn\": false}")
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val type = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "WIFI" else "MOBILE"
            val payload = buildJsonObject { 
                put("isOnline", isOnline)
                put("connectionType", type)
            }.toString()
            emitEvent("onNetworkStateChanged", payload)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        settingsManager = SettingsManager(this)
        NotificationRepository.setEventEmitter { name, payload -> emitEvent(name, payload) }
        
        webView = WebView(this)
        setupWebView()

        registerListeners()
        updateMediaSessionObserver()
        checkPermissions()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
        })

        setContent {
            val isFloatingButtonEnabled by settingsManager.isFloatingButtonEnabled.collectAsState(initial = false)
            val isRemoteDebug by settingsManager.isRemoteDebuggingEnabled.collectAsState(initial = false)
            
            LaunchedEffect(isRemoteDebug) {
                WebView.setWebContentsDebuggingEnabled(isRemoteDebug)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { 
                        webView.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
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
                loadSource(type, uri)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    @SuppressLint("MissingPermission", "InlinedApi")
    private fun registerListeners() {
        // Package changes
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, pkgFilter, RECEIVER_EXPORTED)

        // Battery changes
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), RECEIVER_EXPORTED)

        // System state
        val sysFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(systemReceiver, sysFilter, RECEIVER_EXPORTED)

        // Network changes
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationRepository.setEventEmitter(null)
        unregisterReceiver(packageReceiver)
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(systemReceiver)
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            emitMediaUpdate()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            emitMediaUpdate()
        }
    }

    private var activeMediaController: MediaController? = null

    private fun emitEvent(name: String, payload: String) {
        webView.post {
            val js = "if(window.LauncherEngine && window.LauncherEngine.emitEvent) { window.LauncherEngine.emitEvent('$name', $payload); }"
            webView.evaluateJavascript(js, null)
        }
    }

    private fun emitMediaUpdate() {
        lifecycleScope.launch {
            val engine = LauncherEngine(this@MainActivity)
            emitEvent("onPlaybackStateChanged", engine.getPlaybackState())
        }
    }

    private fun updateMediaSessionObserver() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, WebLauncherNotificationListener::class.java)
            
            val sessionsListener = object : MediaSessionManager.OnActiveSessionsChangedListener {
                override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
                    activeMediaController?.unregisterCallback(mediaCallback)
                    activeMediaController = controllers?.firstOrNull()
                    activeMediaController?.registerCallback(mediaCallback)
                    emitMediaUpdate()
                }
            }
            
            msm.addOnActiveSessionsChangedListener(sessionsListener, component)
            
            // Initial bind
            activeMediaController = msm.getActiveSessions(component).firstOrNull()
            activeMediaController?.registerCallback(mediaCallback)
        } catch (e: SecurityException) {
            android.util.Log.w("WebLauncher", "Media Access not granted")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // Set to false to avoid desktop-style scaling which breaks mobile % height
            loadWithOverviewMode = false
            useWideViewPort = false
            
            setSupportZoom(false)
            displayZoomControls = false
            
            // Critical for consistent percentage layouts
            textZoom = 100 
            
            // Caching & Performance
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        
        // Remove scrollbars for a cleaner look
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        
        // Use HARDWARE layer for smooth cyberpunk animations and glow effects
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        WebView.setWebContentsDebuggingEnabled(true)
        
        webView.addJavascriptInterface(LauncherEngine(this), "LauncherEngine")
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                DiagnosticRepository.addConsoleLog(consoleMessage)
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                DiagnosticRepository.addNetworkRequest(request.url.toString())
                return assetLoader?.shouldInterceptRequest(request.url)
            }
        }
    }

    private fun updateAssetLoader(type: SourceType, treeUriString: String?) {
        val builder = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPathHandler(this))
            .addPathHandler("/app-icon/", AppIconPathHandler(this))
            .addPathHandler("/album-art/", AlbumArtPathHandler(this))
        
        if (type == SourceType.LOCAL && treeUriString != null) {
            val treeUri = treeUriString.toUri()
            builder.addPathHandler("/local_project/", DocumentTreePathHandler(this, treeUri))
        } else if (type == SourceType.GITHUB) {
             builder.addPathHandler("/local_project/", WebViewAssetLoader.InternalStoragePathHandler(this, File(filesDir, "ui_source")))
        }
        
        assetLoader = builder.build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            emitEvent("onHomeButtonPressed", "{}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        pressedKeys.add(keyCode)
        
        // Volume Events
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            emitEvent("onVolumeChanged", "{\"streamType\": \"MEDIA\", \"action\": \"up\"}")
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            emitEvent("onVolumeChanged", "{\"streamType\": \"MEDIA\", \"action\": \"down\"}")
        }

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

    private fun loadSource(type: SourceType, uri: String?) {
        val url = when (type) {
            SourceType.ASSETS -> "https://appassets.androidplatform.net/assets/www/index.html"
            SourceType.LOCAL, SourceType.GITHUB -> {
                val isValid = if (type == SourceType.GITHUB) {
                    File(filesDir, "ui_source/index.html").exists()
                } else {
                    !uri.isNullOrEmpty()
                }
                
                if (!isValid) {
                    android.util.Log.e("WebLauncher", "Source invalid or index.html missing for $type, falling back to assets")
                    return loadSource(SourceType.ASSETS, null)
                }
                "https://appassets.androidplatform.net/local_project/index.html"
            }
        }
        android.util.Log.d("WebLauncher", "Loading URL: $url")
        webView.loadUrl(url)
    }

    fun reloadWebView() {
        lifecycleScope.launch {
            val type = settingsManager.sourceType.first()
            val uri = settingsManager.selectedProjectUri.first()
            loadSource(type, uri)
        }
    }
}
