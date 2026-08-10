package com.yongsheeth.weblauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class SettingsActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsManager = SettingsManager(this)

        setContent {
            SettingsScreen(
                onSave = { type, url -> saveSettings(type, url) },
                onDownload = { url -> downloadAndUnzip(url) }
            )
        }
    }

    private fun saveSettings(type: SourceType, url: String) {
        lifecycleScope.launch {
            settingsManager.updateSource(type, url = url)
            Toast.makeText(this@SettingsActivity, "Settings Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveLocalProject(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        
        val folderName = DocumentFile.fromTreeUri(this, uri)?.name ?: "Unknown Project"
        val project = ProjectItem(folderName, uri.toString())
        
        lifecycleScope.launch {
            settingsManager.addProjectToHistory(project)
            settingsManager.updateSource(SourceType.LOCAL, uri = uri.toString())
            Toast.makeText(this@SettingsActivity, "Project Loaded", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadAndUnzip(url: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Transform standard GitHub URLs to robust API URLs
                    var finalUrl = url
                    if (url.contains("github.com") && !url.contains("api.github.com")) {
                        val regex = "github\\.com/([^/]+)/([^/]+)".toRegex()
                        val match = regex.find(url)
                        if (match != null) {
                            val user = match.groupValues[1]
                            val repo = match.groupValues[2].removeSuffix(".git")
                            finalUrl = "https://api.github.com/repos/$user/$repo/zipball"
                            android.util.Log.d("WebLauncher", "Transformed URL to API (default branch): $finalUrl")
                        }
                    }

                    android.util.Log.d("WebLauncher", "Starting download: $finalUrl")
                    val request = Request.Builder()
                        .url(finalUrl)
                        .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
                        .header("Accept", "application/vnd.github+json")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        android.util.Log.e("WebLauncher", "Download failed: ${response.code} ${response.message}")
                        throw Exception("Failed to download: ${response.code} ${response.message}")
                    }

                    val destDir = File(filesDir, "ui_source")
                    if (destDir.exists()) {
                        android.util.Log.d("WebLauncher", "Clearing existing ui_source")
                        destDir.deleteRecursively()
                    }
                    destDir.mkdirs()

                    ZipInputStream(response.body?.byteStream()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val newFile = File(destDir, entry.name)
                            if (entry.isDirectory) {
                                newFile.mkdirs()
                            } else {
                                newFile.parentFile?.mkdirs()
                                FileOutputStream(newFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                    android.util.Log.d("WebLauncher", "Unzip complete, checking structure...")
                    flattenDirectory(destDir)
                }
                settingsManager.updateSource(SourceType.GITHUB, url = url)
                Toast.makeText(this@SettingsActivity, "Download Successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("WebLauncher", "Extraction error", e)
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun flattenDirectory(baseDir: File) {
        val files = baseDir.listFiles() ?: return
        if (files.size == 1 && files[0].isDirectory) {
            val innerDir = files[0]
            val innerFiles = innerDir.listFiles() ?: return
            for (file in innerFiles) {
                val target = File(baseDir, file.name)
                file.renameTo(target)
            }
            innerDir.delete()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(
        onSave: (SourceType, String) -> Unit,
        onDownload: (String) -> Unit
    ) {
        var sourceType by remember { mutableStateOf(SourceType.ASSETS) }
        var githubUrl by remember { mutableStateOf("") }
        val recentProjects by settingsManager.recentProjects.collectAsState(initial = emptyList())
        val selectedUri by settingsManager.selectedProjectUri.collectAsState(initial = null)
        var isDiagnosticVisible by remember { mutableStateOf(false) }
        
        val folderPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { saveLocalProject(it) }
        }

        LaunchedEffect(Unit) {
            sourceType = settingsManager.sourceType.first()
            githubUrl = settingsManager.githubUrl.first() ?: ""
        }

        Scaffold(
            topBar = { 
                TopAppBar(
                    title = { Text("Launcher Settings") },
                    actions = {
                        IconButton(onClick = { isDiagnosticVisible = true }) {
                            Icon(Icons.Default.BugReport, contentDescription = "Diagnostics")
                        }
                    }
                ) 
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select Web UI Source:", style = MaterialTheme.typography.titleMedium)
                
                SourceOption("Internal Assets", SourceType.ASSETS, sourceType) { sourceType = it }
                SourceOption("Local Folder (SAF)", SourceType.LOCAL, sourceType) { sourceType = it }
                SourceOption("GitHub Zip", SourceType.GITHUB, sourceType) { sourceType = it }

                if (sourceType == SourceType.LOCAL) {
                    Button(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Project Folder")
                    }
                    
                    Text("Recent Projects:", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentProjects) { project ->
                            RecentProjectItem(
                                project = project,
                                isSelected = project.uri == selectedUri,
                                onSelect = {
                                    lifecycleScope.launch {
                                        settingsManager.updateSource(SourceType.LOCAL, uri = project.uri)
                                    }
                                },
                                onDelete = {
                                    lifecycleScope.launch {
                                        settingsManager.removeProjectFromHistory(project.uri)
                                    }
                                }
                            )
                        }
                    }
                }

                if (sourceType == SourceType.GITHUB) {
                    OutlinedTextField(
                        value = githubUrl,
                        onValueChange = { githubUrl = it },
                        label = { Text("GitHub Zip URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onDownload(githubUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download & Unzip")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (sourceType == SourceType.ASSETS) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                HorizontalDivider()
                Text("Safety Features:", style = MaterialTheme.typography.titleMedium)
                
                val isFloatingButtonEnabled by settingsManager.isFloatingButtonEnabled.collectAsState(initial = false)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floating Safety Button")
                        Text("Adds an overlay button to return to settings", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isFloatingButtonEnabled,
                        onCheckedChange = { 
                            lifecycleScope.launch { settingsManager.toggleFloatingButton(it) } 
                        }
                    )
                }

                Text(
                    "Note: Custom Web UIs MUST include a way to reach settings. If you get stuck, press Volume Up + Volume Down simultaneously.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                Button(
                    onClick = { onSave(sourceType, githubUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Settings")
                }
            }
        }

        if (isDiagnosticVisible) {
            ModalBottomSheet(
                onDismissRequest = { isDiagnosticVisible = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                DiagnosticDashboard(settingsManager)
            }
        }
    }

    @Composable
    fun DiagnosticDashboard(settingsManager: SettingsManager) {
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("📱 System", "💻 Console", "🌐 Network", "💾 Storage")

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                        Text(title, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                    }
                }
            }

            when (selectedTab) {
                0 -> SystemTab()
                1 -> ConsoleTab()
                2 -> NetworkTab()
                3 -> StorageTab(settingsManager)
            }
        }
    }

    @Composable
    fun SystemTab() {
        val context = LocalContext.current
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Engine Status: RUNNING", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            InfoRow("OS Version", "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            InfoRow("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            InfoRow("Total RAM", "${memInfo.totalMem / (1024 * 1024)} MB")
            InfoRow("Avail RAM", "${memInfo.availMem / (1024 * 1024)} MB")
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("WebView Implementation:", style = MaterialTheme.typography.titleSmall)
            Text("User Agent: ${WebView(context).settings.userAgentString}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }

    @Composable
    fun ConsoleTab() {
        val logs by DiagnosticRepository.consoleLogs.collectAsState()
        
        LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
            items(logs) { log ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    val color = when (log.level) {
                        android.webkit.ConsoleMessage.MessageLevel.ERROR -> Color(0xFFEF4444)
                        android.webkit.ConsoleMessage.MessageLevel.WARNING -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }
                    Text("[${log.level}] ${log.message}", color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("${log.sourceId}:${log.lineNumber}", color = Color.Gray, fontSize = 10.sp)
                }
            }
            if (logs.isEmpty()) {
                item { Text("No logs yet...", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
            }
        }
    }

    @Composable
    fun NetworkTab() {
        val requests by DiagnosticRepository.networkLogs.collectAsState()
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(requests) { req ->
                ListItem(
                    headlineContent = { Text(req.url, fontSize = 12.sp, maxLines = 1) },
                    supportingContent = { Text(java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(req.timestamp), fontSize = 10.sp) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                HorizontalDivider()
            }
        }
    }

    @Composable
    fun StorageTab(settingsManager: SettingsManager) {
        val persistentData by settingsManager.persistentData.collectAsState(initial = emptyMap())
        val isRemoteDebug by settingsManager.isRemoteDebuggingEnabled.collectAsState(initial = false)

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Chrome Remote Debugging", modifier = Modifier.weight(1f))
                Switch(checked = isRemoteDebug, onCheckedChange = {
                    lifecycleScope.launch { settingsManager.toggleRemoteDebugging(it) }
                })
            }
            
            Text("Web Persistent Data (KV Store):", style = MaterialTheme.typography.titleSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    persistentData.forEach { (k, v) ->
                        Text("$k: $v", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    if (persistentData.isEmpty()) Text("Empty", color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { 
                    lifecycleScope.launch {
                        settingsManager.clearAll()
                        Toast.makeText(this@SettingsActivity, "All settings cleared. Please restart.", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("FACTORY RESET (Wipe All Settings)")
            }
        }
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }

    @Composable
    fun RecentProjectItem(
        project: ProjectItem,
        isSelected: Boolean,
        onSelect: () -> Unit,
        onDelete: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onSelect() },
            colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) 
                     else CardDefaults.cardColors()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.bodyLarge)
                    Text(project.uri, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }

    @Composable
    fun SourceOption(
        label: String,
        type: SourceType,
        selectedType: SourceType,
        onSelect: (SourceType) -> Unit
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = selectedType == type, onClick = { onSelect(type) })
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
