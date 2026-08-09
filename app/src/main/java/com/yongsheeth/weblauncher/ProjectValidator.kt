package com.yongsheeth.weblauncher

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object ProjectValidator {

    private const val SETTINGS_CALL = "LauncherEngine.openLauncherSettings()"

    /**
     * Scans the project for the required settings entry point.
     * Supports Internal Assets, Local SAF folders, and Internal Extraction directories.
     */
    fun validateProject(context: Context, type: SourceType, uriString: String? = null): Boolean {
        return when (type) {
            SourceType.ASSETS -> true // Default assets are always safe
            SourceType.LOCAL -> validateSafFolder(context, uriString)
            SourceType.GITHUB -> validateInternalFolder(context)
        }
    }

    private fun validateSafFolder(context: Context, uriString: String?): Boolean {
        if (uriString == null) return false
        val root = DocumentFile.fromTreeUri(context, uriString.toUri()) ?: return false
        val indexHtml = root.findFile("index.html") ?: return false
        
        val content = context.contentResolver.openInputStream(indexHtml.uri)?.use { 
            it.bufferedReader().readText() 
        } ?: ""
        
        return content.contains(SETTINGS_CALL) || scanLinkedJsSaf(context, root, content)
    }

    private fun validateInternalFolder(context: Context): Boolean {
        val destDir = File(context.filesDir, "ui_source")
        val indexHtml = File(destDir, "index.html")
        if (!indexHtml.exists()) return false
        
        val content = indexHtml.readText()
        return content.contains(SETTINGS_CALL) || scanLinkedJsInternal(destDir, content)
    }

    private fun scanLinkedJsSaf(context: Context, root: DocumentFile, htmlContent: String): Boolean {
        val jsRegex = """<script.*src=["'](.*\.js)["']""".toRegex()
        val matches = jsRegex.findAll(htmlContent)
        
        for (match in matches) {
            val jsPath = match.groupValues[1]
            val jsFile = root.findFile(jsPath)
            if (jsFile != null) {
                val jsContent = context.contentResolver.openInputStream(jsFile.uri)?.use { 
                    it.bufferedReader().readText() 
                } ?: ""
                if (jsContent.contains(SETTINGS_CALL)) return true
            }
        }
        return false
    }

    private fun scanLinkedJsInternal(baseDir: File, htmlContent: String): Boolean {
        val jsRegex = """<script.*src=["'](.*\.js)["']""".toRegex()
        val matches = jsRegex.findAll(htmlContent)
        
        for (match in matches) {
            val jsPath = match.groupValues[1]
            val jsFile = File(baseDir, jsPath)
            if (jsFile.exists()) {
                if (jsFile.readText().contains(SETTINGS_CALL)) return true
            }
        }
        return false
    }
}
