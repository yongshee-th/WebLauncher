package com.yongsheeth.weblauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Failed to download")

                    val destDir = File(filesDir, "ui_source")
                    if (destDir.exists()) destDir.deleteRecursively()
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
                            entry = zis.nextEntry
                        }
                    }
                }
                settingsManager.updateSource(SourceType.GITHUB, url = url)
                Toast.makeText(this@SettingsActivity, "Download Successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
            topBar = { TopAppBar(title = { Text("Launcher Settings") }) }
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
