package com.yongsheeth.weblauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

@Serializable
data class AppInfo(
    val name: String,
    val packageName: String,
    val iconUrl: String // Changed from iconBase64
)

@Serializable
data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean
)

@Serializable
data class StorageMetrics(
    val totalBytes: Long,
    val freeBytes: Long
)

class LauncherEngine(private val activity: Activity) {

    private val settingsManager = SettingsManager(activity)

    @JavascriptInterface
    fun getAppsList(): String {
        android.util.Log.d("LauncherEngine", "getAppsList called")
        val pm = activity.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val apps = pm.queryIntentActivities(mainIntent, 0)
            .map { resolveInfo ->
                val name = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                AppInfo(name, pkg, "https://appassets.androidplatform.net/app-icon/$pkg")
            }
        
        return Json.encodeToString(apps)
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
        if (launchIntent != null) {
            activity.startActivity(launchIntent)
        }
    }

    @JavascriptInterface
    fun getBatteryStatus(): String {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = activity.registerReceiver(null, filter)
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = (level * 100 / scale.toFloat()).toInt()
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        
        return Json.encodeToString(BatteryStatus(batteryPct, isCharging))
    }

    @JavascriptInterface
    fun getStorageMetrics(): String {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        return Json.encodeToString(
            StorageMetrics(
                totalBlocks * blockSize,
                availableBlocks * blockSize
            )
        )
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

    private fun encodeIconToBase64(drawable: Drawable): String {
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val bmp = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
