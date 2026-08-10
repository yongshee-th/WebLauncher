package com.yongsheeth.weblauncher

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.webkit.WebResourceResponse
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AppIconPathHandler(private val context: Context) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        // Path format: packageName
        val packageName = path.trim('/')
        if (packageName.isEmpty()) return null

        android.util.Log.d("AppIconPathHandler", "Requesting icon for: $packageName")

        return try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(icon)
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val inputStream = ByteArrayInputStream(outputStream.toByteArray())
            
            WebResourceResponse("image/png", "UTF-8", inputStream)
        } catch (e: PackageManager.NameNotFoundException) {
            android.util.Log.e("AppIconPathHandler", "Package not found: $packageName")
            // Return a transparent 1x1 pixel to avoid ERR_NAME_NOT_RESOLVED
            createEmptyIconResponse()
        } catch (e: Exception) {
            android.util.Log.e("AppIconPathHandler", "Error loading icon: ${e.message}")
            createEmptyIconResponse()
        }
    }

    private fun createEmptyIconResponse(): WebResourceResponse {
        val bitmap = createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return WebResourceResponse("image/png", "UTF-8", ByteArrayInputStream(outputStream.toByteArray()))
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        
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
}
