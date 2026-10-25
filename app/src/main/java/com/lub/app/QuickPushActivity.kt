package com.lub.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lub.app.capture.CapturedSegment
import com.lub.app.essence.EssenceExtractor
import com.lub.app.github.GitHubClient
import com.lub.app.github.PushEngine
import com.lub.app.github.PushItem
import com.lub.app.github.RepoDescriber
import com.lub.app.github.RepoNamer
import com.lub.app.parser.BlockParser
import com.lub.app.parser.SecretFinding
import com.lub.app.parser.SecretScanner
import com.lub.app.parser.StackDetector
import com.lub.app.ui.theme.LubTheme
import com.lub.app.ui.theme.StatusError
import com.lub.app.ui.theme.StatusWarning
import com.lub.app.work.UploadWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QuickPushActivity : ComponentActivity() {

    companion object {
        const val EXTRA_RAW_TEXT = "extra_raw_text"
        const val EXTRA_SOURCE = "extra_source"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LubTheme(darkTheme = true) {
                QuickPushContent(
                    onDismiss = { finish() },
                    onNavigateToReview = { projectId ->
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra("START_ROUTE", "review/$projectId")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
                    },
                    onSuccess = { repoUrl, repoName ->
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra("START_ROUTE", "done?repoUrl=$repoUrl&repoName=$repoName")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun QuickPushContent(
    onDismiss: () -> Unit,
    onNavigateToReview: (String) -> Unit,
    onSuccess: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf("جاري التحضير...") }

    var projectId by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<Pair<String, String>>>(emptyMap<String, String>().toList()) }
    var techStack by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }
    var repoDesc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    var addGitIgnore by remember { mutableStateOf(true) }
    var addReadme by remember { mutableStateOf(true) }

    var secretWarnings by remember { mutableStateOf<List<SecretFinding>>(emptyList()) }
    var githubToken by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var showTokenField by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        githubToken = App.instance.settingsStore.getGitHubToken()
        if (githubToken.isEmpty()) {
            showTokenField = true
        }

        // Load items from Basket
        val basketItems = App.instance.database.basketDao().getAllBasketItemsList().filter { it.isSelected }
        if (basketItems.isNotEmpty()) {
            val rawFiles = basketItems.map {
                val path = it.targetPath ?: "file_${it.id.take(4)}.${if (it.language != "auto") it.language else "txt"}"
                Pair(path, it.code)
            }
            files = rawFiles
            techStack = StackDetector.detect(rawFiles.map { it.first }, rawFiles.toMap())
            repoName = RepoNamer.suggestRepoName(rawFiles)
            repoDesc = RepoDescriber.generateDescription(rawFiles, techStack)
            secretWarnings = SecretScanner.scan(rawFiles)

            // Save to local project
            projectId = App.instance.projectStore.saveProject(
                name = repoName,
                description = repoDesc,
                techStack = techStack,
                files = rawFiles
            )

            val isFastPush = App.instance.settingsStore.fastPushFlow.first()
            if (isFastPush && githubToken.isNotEmpty() && secretWarnings.none { it.isCritical }) {
                // Instant push without confirmation
                executePush(
                    projectId = projectId,
                    name = repoName,
                    desc = repoDesc,
                    isPriv = isPrivate,
                    token = githubToken,
                    rawFiles = rawFiles,
                    onStatus = { uploadStatusText = it },
                    onDone = { url -> onSuccess(url, repoName) }
                )
            }
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                    Text(
                        text = "تأكيد الرفع السريع ⚡",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (files.isEmpty()) {
                    Text(
                        text = "السلة فارغة حالياً. قم بالتقاط كود من أي شاشة أولاً",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 30.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Repo Name Input
                    OutlinedTextField(
                        value = repoName,
                        onValueChange = { repoName = RepoNamer.cleanRepoName(it) },
                        label = { Text("اسم المستودع (GitHub)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Description Input
                    OutlinedTextField(
                        value = repoDesc,
                        onValueChange = { repoDesc = it },
                        label = { Text("وصف المستودع") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isPrivate) "مستودع خاص (Private)" else "مستودع عام (Public)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    }

                    // Tech stack & files count info
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "التقنية: $techStack | ${files.size} ملفات",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val topFiles = files.take(4).map { it.first }
                            Text(
                                text = "أهم الملفات: ${topFiles.joinToString("، ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Secret Warnings Alert
                    if (secretWarnings.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusWarning)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarning)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "تنبيه أمان: تم رصد ${secretWarnings.size} عناصر حساسة",
                                        fontWeight = FontWeight.Bold,
                                        color = StatusWarning
                                    )
                                    Text(
                                        text = "يحتوي المشروع على مفاتيح أو ملفات .env. تأكد قبل رفعها إلى مستودع عام",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    // GitHub Token input if missing
                    if (showTokenField) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "مطلوب توكن GitHub للمتابعة:",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = tokenInput,
                                    onValueChange = { tokenInput = it },
                                    label = { Text("ghp_xxxxxxxxxxxx") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (tokenInput.isNotBlank()) {
                                            scope.launch {
                                                App.instance.settingsStore.saveGitHubToken(tokenInput.trim())
                                                githubToken = tokenInput.trim()
                                                showTokenField = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("حفظ التوكن ومتابعة الرفع")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isUploading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(uploadStatusText, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        // Action Buttons
                        Button(
                            onClick = {
                                if (githubToken.isEmpty()) {
                                    showTokenField = true
                                    return@Button
                                }
                                isUploading = true
                                executePush(
                                    projectId = projectId,
                                    name = repoName,
                                    desc = repoDesc,
                                    isPriv = isPrivate,
                                    token = githubToken,
                                    rawFiles = files,
                                    onStatus = { uploadStatusText = it },
                                    onDone = { url ->
                                        isUploading = false
                                        onSuccess(url, repoName)
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🚀 ارفع الآن إلى GitHub", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { onNavigateToReview(projectId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("مراجعة وتعديل الملفات")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

private fun executePush(
    projectId: String,
    name: String,
    desc: String,
    isPriv: Boolean,
    token: String,
    rawFiles: List<Pair<String, String>>,
    onStatus: (String) -> Unit,
    onDone: (String) -> Unit
) {
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val client = GitHubClient(token)
            val user = client.getAuthenticatedUser()
            val owner = user.login

            onStatus("جاري إنشاء المستودع...")
            val exists = client.checkRepoExists(owner, name)
            if (!exists) {
                client.createRepository(name, desc, isPriv, autoInit = true)
            }

            onStatus("جاري تحضير الملفات...")
            val pushItems = rawFiles.map {
                PushItem(relativePath = it.first, textContent = it.second, isBinary = false)
            }

            val engine = PushEngine(client)
            val repoUrl = engine.pushProject(
                owner = owner,
                repoName = name,
                branch = "main",
                commitMessage = "Initial project commit from Lub",
                items = pushItems,
                isNewRepo = !exists
            ) { progress ->
                onStatus(progress.stage)
            }

            // Update database
            val project = App.instance.database.projectDao().getProjectById(projectId)
            if (project != null) {
                App.instance.database.projectDao().updateProject(
                    project.copy(repoUrl = repoUrl, isUploaded = true, updatedAt = System.currentTimeMillis())
                )
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone(repoUrl)
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onStatus("فشل: ${e.message}")
            }
        }
    }
}
