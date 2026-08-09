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

        return try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(icon)
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val inputStream = ByteArrayInputStream(outputStream.toByteArray())
            
            WebResourceResponse("image/png", "UTF-8", inputStream)
        } catch (@Suppress("unused") e: PackageManager.NameNotFoundException) {
            null
        } catch (@Suppress("unused") e: Exception) {
            null
        }
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
