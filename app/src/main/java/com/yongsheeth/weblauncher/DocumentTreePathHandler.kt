package com.yongsheeth.weblauncher

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import java.io.InputStream

class DocumentTreePathHandler(
    private val context: Context,
    private val rootTreeUri: Uri,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        android.util.Log.d("DocumentTreePathHandler", "Handling path: $path")
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: run {
            android.util.Log.e("DocumentTreePathHandler", "Failed to get DocumentFile from URI: $rootTreeUri")
            return null
        }
        
        // Split path and traverse
        val segments = path.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile = root
        
        for (segment in segments) {
            current = current.findFile(segment) ?: run {
                android.util.Log.e("DocumentTreePathHandler", "File not found: $segment in ${current.name}")
                return null
            }
        }
        
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(current.uri) ?: return null
            
            // Robust MIME Type detection
            val extension = MimeTypeMap.getFileExtensionFromUrl(path)
            val mimeType = when {
                path.endsWith(".css", ignoreCase = true) -> "text/css"
                path.endsWith(".js", ignoreCase = true) -> "text/javascript"
                path.endsWith(".json", ignoreCase = true) -> "application/json"
                path.endsWith(".png", ignoreCase = true) -> "image/png"
                path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                path.endsWith(".webp", ignoreCase = true) -> "image/webp"
                path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            }

            android.util.Log.d("DocumentTreePathHandler", "Serving $path as $mimeType")
            WebResourceResponse(mimeType, "UTF-8", inputStream)
        } catch (@Suppress("unused") e: Exception) {
            android.util.Log.e("DocumentTreePathHandler", "Error reading file: ${e.message}")
            null
        }
    }
}
