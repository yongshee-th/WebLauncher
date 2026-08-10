package com.yongsheeth.weblauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.WallpaperManager
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.webkit.JavascriptInterface
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.*

@Serializable
data class AppInfo(
    val name: String,
    val packageName: String,
    val iconUrl: String,
    val category: String = "Unknown"
)

@Serializable
data class AppUsage(
    val packageName: String,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long
)

@Serializable
data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean,
    val health: String = "Unknown",
    val temperature: Float = 0f,
    val pluggedType: String = "None"
)

@Serializable
data class StorageMetrics(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val totalRAM: Long,
    val availRAM: Long,
)

@Serializable
data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val screenWidth: Int,
    val screenHeight: Int
)

@Serializable
data class NetworkStatus(
    val type: String, // WIFI, MOBILE, NONE
    val ssid: String = "",
    val signalStrength: Int = 0,
    val isConnected: Boolean = false
)

@Serializable
data class VolumeLevels(
    val media: Int,
    val ring: Int,
    val alarm: Int,
    val notification: Int
)

@Serializable
data class PlaybackState(
    val title: String = "",
    val artist: String = "",
    val albumArt: String = "", // Base64
    val isPlaying: Boolean = false,
    val duration: Long = 0
)

@Suppress("unused")
class LauncherEngine(private val activity: Activity) {

    private val settingsManager = SettingsManager(activity)

    @JavascriptInterface
    fun getAppsList(): String {
        val pm = activity.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val apps = pm.queryIntentActivities(mainIntent, 0)
            .map { resolveInfo ->
                val name = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                AppInfo(name, pkg, "https://appassets.androidplatform.net/app-icon/$pkg")
            }
        
        return Json.encodeToString(apps)
    }

    @JavascriptInterface
    fun launchAppWithData(packageName: String, uriData: String) {
        try {
            val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
            intent?.data = Uri.parse(uriData)
            intent?.let { activity.startActivity(it) }
        } catch (e: Exception) {
            android.util.Log.e("LauncherEngine", "launchAppWithData error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun openAppSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        activity.startActivity(intent)
    }

    @JavascriptInterface
    fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }

    @JavascriptInterface
    fun getAppUsageStats(timeRange: String): String {
        // range: day, week, month, year
        val statsManager = activity.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        when (timeRange) {
            "day" -> calendar.add(Calendar.DAY_OF_YEAR, -1)
            "week" -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
            "month" -> calendar.add(Calendar.MONTH, -1)
            else -> calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        val startTime = calendar.timeInMillis

        val usageStats = statsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
        val result = usageStats?.map {
            AppUsage(it.packageName, it.totalTimeInForeground, it.lastTimeUsed)
        } ?: emptyList()

        return Json.encodeToString(result)
    }

    @JavascriptInterface
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun getDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == activity.packageName
    }

    @JavascriptInterface
    fun openDefaultLauncherSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            activity.startActivity(intent)
        }
    }

    // --- Notification System ---

    @JavascriptInterface
    fun getActiveNotifications(): String {
        return Json.encodeToString(NotificationRepository.activeNotifications.value.values.toList())
    }

    @JavascriptInterface
    fun openNotificationDrawer() {
        val statusBarService = activity.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    }

    @JavascriptInterface
    fun cancelNotification(key: String) {
        val intent = Intent("com.yongsheeth.weblauncher.NOTIFICATION_CONTROL")
        intent.putExtra("action", "cancel")
        intent.putExtra("key", key)
        activity.sendBroadcast(intent)
    }

    @JavascriptInterface
    fun clearAllNotifications() {
        val intent = Intent("com.yongsheeth.weblauncher.NOTIFICATION_CONTROL")
        intent.putExtra("action", "clear_all")
        activity.sendBroadcast(intent)
    }

    @JavascriptInterface
    fun getBluetoothStatus(): Boolean {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.isEnabled ?: false
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun setBluetoothEnabled(enabled: Boolean) {
        try {
            val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } else {
                @Suppress("DEPRECATION")
                if (enabled) adapter.enable() else adapter.disable()
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherEngine", "Bluetooth error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun getAirPlaneModeStatus(): Boolean {
        return Settings.Global.getInt(activity.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }

    @JavascriptInterface
    fun getDisplayOrientation(): String {
        return if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun setSystemWallpaper(base64Image: String, target: String) {
        try {
            val decodedString = Base64.decode(base64Image, Base64.DEFAULT)
            val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            val wm = WallpaperManager.getInstance(activity)
            val flag = when (target.lowercase()) {
                "lock" -> WallpaperManager.FLAG_LOCK
                "home" -> WallpaperManager.FLAG_SYSTEM
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            wm.setBitmap(decodedByte, null, true, flag)
        } catch (e: Exception) {
            android.util.Log.e("LauncherEngine", "Set wallpaper error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun setScreenBrightness(level: Float) {
        activity.runOnUiThread {
            val params = activity.window.attributes
            params.screenBrightness = level.coerceIn(0f, 1f)
            activity.window.attributes = params
        }
    }

    @JavascriptInterface
    fun triggerHapticFeedback(effectType: String) {
        activity.runOnUiThread {
            val view = activity.window.decorView
            when (effectType) {
                "click" -> view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                "heavy" -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                "double_click" -> {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    view.postDelayed({ view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }, 100)
                }
                else -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    @JavascriptInterface
    fun toggleTorch(enabled: Boolean) {
        try {
            val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enabled)
        } catch (e: Exception) {
            android.util.Log.e("LauncherEngine", "Torch error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun setPersistentData(key: String, value: String) {
        runBlocking {
            settingsManager.setPersistentData(key, value)
        }
    }

    @JavascriptInterface
    fun getPersistentData(key: String): String {
        return runBlocking {
            settingsManager.persistentData.first()[key] ?: ""
        }
    }

    @JavascriptInterface
    fun launchApp(packageName: String) {
        val launchIntent = activity.packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            activity.startActivity(it)
        }
    }

    @JavascriptInterface
    fun getBatteryStatus(): String {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = activity.registerReceiver(null, filter) ?: return "{}"
        
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
        
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        
        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        
        val plugInt = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugType = when (plugInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }

        return Json.encodeToString(BatteryStatus(pct, isCharging, health, temp, plugType))
    }

    @JavascriptInterface
    fun getStorageMetrics(): String {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - free

        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        return Json.encodeToString(
            StorageMetrics(
                totalBytes = total,
                freeBytes = free,
                usedBytes = used,
                totalRAM = memInfo.totalMem,
                availRAM = memInfo.availMem
            )
        )
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        val metrics = activity.resources.displayMetrics
        return Json.encodeToString(DeviceInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        ))
    }

    @JavascriptInterface
    fun getWifiStatus(): String {
        val wifiManager = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        return Json.encodeToString(NetworkStatus(
            type = "WIFI",
            ssid = info.ssid.removeSurrounding("\""),
            signalStrength = info.rssi,
            isConnected = info.networkId != -1
        ))
    }

    @JavascriptInterface
    fun setWifiEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val wifiManager = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = enabled
        } else {
            activity.startActivity(Intent(Settings.Panel.ACTION_WIFI))
        }
    }

    @JavascriptInterface
    fun getVolumeLevels(): String {
        val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return Json.encodeToString(VolumeLevels(
            media = am.getStreamVolume(AudioManager.STREAM_MUSIC),
            ring = am.getStreamVolume(AudioManager.STREAM_RING),
            alarm = am.getStreamVolume(AudioManager.STREAM_ALARM),
            notification = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        ))
    }

    @JavascriptInterface
    fun setVolumeLevel(streamType: String, level: Int) {
        val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (streamType.lowercase()) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        am.setStreamVolume(stream, level, AudioManager.FLAG_SHOW_UI)
    }

    @JavascriptInterface
    fun vibrate(duration: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun getSystemWallpaper(): String {
        return try {
            val wm = WallpaperManager.getInstance(activity)
            val drawable = wm.drawable ?: return ""
            val bitmap = drawableToBitmap(drawable)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("LauncherEngine", "Wallpaper error: ${e.message}")
            ""
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val bitmap = createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.applyCanvas {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
        return bitmap
    }

    @JavascriptInterface
    fun isDarkModeEnabled(): Boolean {
        val nightModeFlags = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    @JavascriptInterface
    fun openSystemSettings(type: String) {
        val action = when (type.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        activity.startActivity(Intent(action))
    }

    @JavascriptInterface
    fun openLauncherSettings() {
        val intent = Intent(activity, SettingsActivity::class.java)
        activity.startActivity(intent)
    }

    @JavascriptInterface
    fun reloadEngine() {
        activity.runOnUiThread {
            (activity as? MainActivity)?.reloadWebView()
        }
    }
}
