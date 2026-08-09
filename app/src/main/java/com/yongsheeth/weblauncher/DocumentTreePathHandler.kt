package com.yongsheeth.weblauncher

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import java.io.InputStream
import java.net.URLConnection

class DocumentTreePathHandler(
    private val context: Context,
    private val rootTreeUri: Uri,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return null
        
        // Split path and traverse
        val segments = path.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile = root
        
        for (segment in segments) {
            current = current.findFile(segment) ?: return null
        }
        
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(current.uri) ?: return null
            val mimeType = URLConnection.guessContentTypeFromName(current.name) ?: "text/html"
            WebResourceResponse(mimeType, "UTF-8", inputStream)
        } catch (@Suppress("unused") e: Exception) {
            null
        }
    }
}
