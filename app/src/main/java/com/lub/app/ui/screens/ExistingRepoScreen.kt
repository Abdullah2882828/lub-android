package com.lub.app.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lub.app.App
import com.lub.app.github.GitHubClient
import com.lub.app.github.GitHubRepo
import com.lub.app.github.PushEngine
import com.lub.app.github.PushItem
import com.lub.app.parser.PathSanitizer
import com.lub.app.ui.theme.StatusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExistingRepoScreen(
    projectId: String,
    onBack: () -> Unit,
    onSuccess: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRepo by remember { mutableStateOf<GitHubRepo?>(null) }
    var branchName by remember { mutableStateOf("main") }
    var subfolder by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("Update from Lub") }

    var isLoadingRepos by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val token = App.instance.settingsStore.getGitHubToken()
        if (token.isEmpty()) {
            errorMessage = "يرجى ربط حساب GitHub أولاً من تبويب GitHub"
            isLoadingRepos = false
            return@LaunchedEffect
        }

        try {
            val client = GitHubClient(token)
            repos = client.listUserRepos()
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoadingRepos = false
        }
    }

    val filteredRepos = repos.filter { it.name.contains(searchQuery, ignoreCase = true) }

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

            // Header
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
                        text = "الرفع إلى مستودع موجود",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "اختر المستودع والفرع والمجلد المستهدف",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedRepo == null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("بحث في مستودعاتي...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoadingRepos) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (errorMessage != null) {
                    Text(text = errorMessage ?: "", color = StatusError, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRepos, key = { it.id }) { repo ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedRepo = repo
                                        branchName = repo.defaultBranch
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = repo.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "الفرع الافتراضي: ${repo.defaultBranch}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val repo = selectedRepo!!

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { selectedRepo = null }, shape = RoundedCornerShape(8.dp)) {
                            Text("تغيير المستودع")
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = repo.fullName, fontWeight = FontWeight.Bold)
                            Text(
                                text = "مستودع ${if (repo.isPrivate) "خاص" else "عام"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it.trim() },
                    label = { Text("الفرع (Branch)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = subfolder,
                    onValueChange = { subfolder = it.trim() },
                    label = { Text("مجلد فرعي اختياري (مثال: packages/core)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("رسالة الـ Commit") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isUploading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uploadStatus)
                    }
                } else {
                    Button(
                        onClick = {
                            isUploading = true
                            scope.launch {
                                try {
                                    val token = App.instance.settingsStore.getGitHubToken()
                                    val client = GitHubClient(token)
                                    val pushEngine = PushEngine(client)

                                    val files = App.instance.database.projectDao().getFilesForProject(projectId)
                                    val pushItems = files.filter { it.isIncluded }.map { f ->
                                        val finalPath = if (subfolder.isNotBlank()) {
                                            PathSanitizer.sanitize("$subfolder/${f.relativePath}")
                                        } else {
                                            f.relativePath
                                        }
                                        PushItem(relativePath = finalPath, textContent = f.content)
                                    }

                                    val parts = repo.fullName.split('/')
                                    val owner = parts[0]
                                    val rName = parts[1]

                                    val repoUrl = pushEngine.pushProject(
                                        owner = owner,
                                        repoName = rName,
                                        branch = branchName,
                                        commitMessage = commitMessage,
                                        items = pushItems,
                                        isNewRepo = false
                                    ) { p -> uploadStatus = p.stage }

                                    withContext(Dispatchers.Main) {
                                        isUploading = false
                                        onSuccess(repoUrl, repo.name)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isUploading = false
                                        uploadStatus = "خطأ: ${e.message}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رفع الملفات إلى ${repo.name}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
