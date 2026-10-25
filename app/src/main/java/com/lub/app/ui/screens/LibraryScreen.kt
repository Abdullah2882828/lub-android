package com.lub.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lub.app.App
import com.lub.app.data.ProjectEntity
import com.lub.app.ui.theme.StatusError
import com.lub.app.ui.theme.StatusSuccess
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    onSelectProject: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val projects by App.instance.database.projectDao().getAllProjects().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var exportProjectId by remember { mutableStateOf<String?>(null) }

    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val id = exportProjectId
        if (uri != null && id != null) {
            scope.launch {
                val ok = App.instance.projectStore.exportProjectToZipUri(id, uri)
                Toast.makeText(context, if (ok) "تم تصدير ملف ZIP بنجاح" else "فشل التصدير", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val folderExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val id = exportProjectId
        if (treeUri != null && id != null) {
            scope.launch {
                val ok = App.instance.projectStore.exportProjectToFolder(id, treeUri)
                Toast.makeText(context, if (ok) "تم تصدير المجلد بنجاح" else "فشل التصدير", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredProjects = projects.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.techStack.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "مكتبة المشاريع المحفوظة 📚",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${projects.size} مشاريع محفوظة محلياً",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث في المشاريع...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (projects.isEmpty()) "لا توجد مشاريع بعد. التقط مشروعك الأول ⚡" else "لا توجد نتائج مطابقة للبحث",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProjects, key = { it.id }) { prj ->
                        ProjectLibraryCard(
                            project = prj,
                            onClick = { onSelectProject(prj.id) },
                            onExportZip = {
                                exportProjectId = prj.id
                                zipExportLauncher.launch("${prj.name}.zip")
                            },
                            onExportFolder = {
                                exportProjectId = prj.id
                                folderExportLauncher.launch(null)
                            },
                            onDelete = {
                                scope.launch { App.instance.projectStore.deleteProject(prj.id) }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProjectLibraryCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onExportZip: () -> Unit,
    onExportFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(project.updatedAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.End) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = StatusError, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onExportZip) {
                        Icon(Icons.Default.Archive, contentDescription = "تصدير ZIP", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onExportFolder) {
                        Icon(Icons.Default.Folder, contentDescription = "تصدير لمجلد", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "التقنية: ${project.techStack} | ${project.fileCount} ملفات (${project.totalBytes / 1024} KB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (!project.repoUrl.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(project.repoUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = project.repoUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusSuccess,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(14.dp))
                }
            } else {
                Text(
                    text = "محفوظ محلياً فقط — لم يُرفع إلى GitHub بعد",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "آخر تحديث: $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
