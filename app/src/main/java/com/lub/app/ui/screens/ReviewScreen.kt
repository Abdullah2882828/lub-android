package com.lub.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lub.app.App
import com.lub.app.data.ProjectEntity
import com.lub.app.data.ProjectFileEntity
import com.lub.app.ui.theme.StatusError
import com.lub.app.ui.theme.StatusSuccess
import com.lub.app.ui.theme.StatusWarning
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigateToUpload: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    val files by App.instance.database.projectDao().getFilesForProjectFlow(projectId).collectAsState(initial = emptyList())

    var selectedFile by remember { mutableStateOf<ProjectFileEntity?>(null) }
    var editedContent by remember { mutableStateOf("") }
    var editedPath by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        project = App.instance.database.projectDao().getProjectById(projectId)
    }

    LaunchedEffect(files) {
        if (selectedFile == null && files.isNotEmpty()) {
            selectedFile = files.first()
            editedContent = files.first().content
            editedPath = files.first().relativePath
        }
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
            Spacer(modifier = Modifier.height(14.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = project?.name ?: "مراجعة ملفات المشروع",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${files.size} ملفات | ${project?.techStack ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Upload Button
            Button(
                onClick = { onNavigateToUpload(projectId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("المتابعة إلى الرفع لـ GitHub 🚀", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Editor / Tree Split
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Pane: Code Preview & Editor
                Card(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        val curr = selectedFile
                        if (curr != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                App.instance.database.projectDao().updateFile(
                                                    curr.copy(
                                                        relativePath = editedPath,
                                                        content = editedContent
                                                    )
                                                )
                                                Toast.makeText(context, "تم حفظ التعديلات", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = "حفظ", tint = StatusSuccess)
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                App.instance.database.projectDao().deleteFileById(curr.id)
                                                selectedFile = null
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = StatusError)
                                    }
                                }

                                Text(
                                    text = curr.relativePath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (curr.warnings.isNotEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.12f))
                                ) {
                                    Text(
                                        text = curr.warnings,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusWarning,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = editedContent,
                                onValueChange = { editedContent = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("اختر ملفاً لمعاينته أو تعديله", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Right Pane: Files Tree with confidence badges
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(files, key = { it.id }) { file ->
                            val isSelected = selectedFile?.id == file.id
                            val confColor = when {
                                file.confidenceScore >= 0.80f -> StatusSuccess
                                file.confidenceScore >= 0.55f -> StatusWarning
                                else -> StatusError
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selectedFile = file
                                        editedContent = file.content
                                        editedPath = file.relativePath
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(confColor)
                                )

                                Text(
                                    text = file.relativePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )

                                Checkbox(
                                    checked = file.isIncluded,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            App.instance.database.projectDao().updateFile(file.copy(isIncluded = checked))
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
