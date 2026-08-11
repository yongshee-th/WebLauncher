package com.yongsheeth.weblauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.session.MediaSessionManager
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AlbumArtPathHandler(private val context: Context) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        val packageName = path.trim('/')
        if (packageName.isEmpty()) return null

        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(context, WebLauncherNotificationListener::class.java)
            val controllers = msm.getActiveSessions(component)
            
            val controller = controllers.find { it.packageName == packageName } ?: controllers.firstOrNull()
            val bitmap = controller?.metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: controller?.metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)

            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                WebResourceResponse("image/jpeg", "UTF-8", ByteArrayInputStream(outputStream.toByteArray()))
            } else {
                null
            }
        } catch (e: SecurityException) {
            android.util.Log.w("AlbumArtPathHandler", "Notification Access not granted for media")
            null
        } catch (e: Exception) {
            null
        }
    }
}
