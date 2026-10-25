package com.lub.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lub.app.App
import com.lub.app.QuickPushActivity
import com.lub.app.archive.ArchiveExtractor
import com.lub.app.capture.ClipActivity
import com.lub.app.folder.FolderScanner
import com.lub.app.service.CaptureBus
import com.lub.app.service.LubAccessibilityService
import com.lub.app.ui.theme.StatusError
import com.lub.app.ui.theme.StatusSuccess
import com.lub.app.ui.theme.StatusWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun HomeScreen(
    onNavigateToBasket: () -> Unit,
    onNavigateToReview: (String) -> Unit,
    onNavigateToGitHub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isConnected by CaptureBus.isServiceConnected.collectAsState()
    val basketCount by App.instance.database.basketDao().getBasketCount().collectAsState(initial = 0)
    var githubUser by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val user = App.instance.settingsStore.githubUsernameFlow.first()
        githubUser = user
    }

    // Folder selection launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            isProcessing = true
            processingMessage = "جاري مسح المجلد المختار..."

            scope.launch {
                try {
                    val scanResult = FolderScanner.scanTree(context, treeUri) { count, bytes ->
                        processingMessage = "تم مسح $count ملفات (${bytes / 1024} KB)..."
                    }

                    if (scanResult.files.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "لم يتم العثور على ملفات صالحة في المجلد", Toast.LENGTH_SHORT).show()
                            isProcessing = false
                        }
                        return@launch
                    }

                    // Convert files to project
                    val filesList = mutableListOf<Pair<String, String>>()
                    for (f in scanResult.files) {
                        if (f.isText) {
                            val content = context.contentResolver.openInputStream(f.uri)?.use {
                                it.bufferedReader(Charsets.UTF_8).readText()
                            } ?: ""
                            filesList.add(Pair(f.relativePath, content))
                        }
                    }

                    val projectId = App.instance.projectStore.saveProject(
                        name = scanResult.folderName,
                        description = "مشروع محلي مستورد من مدير الملفات",
                        techStack = "مجلد مستورد",
                        files = filesList
                    )

                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        onNavigateToReview(projectId)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        Toast.makeText(context, "فشل مسح المجلد: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Archive file launcher
    val archivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { archiveUri ->
        if (archiveUri != null) {
            isProcessing = true
            processingMessage = "جاري فك ضغط الأرشيف..."

            scope.launch {
                try {
                    val targetDir = File(context.filesDir, "extracted_${UUID.randomUUID().toString().take(6)}")
                    val result = ArchiveExtractor.extractArchive(context, archiveUri, targetDir) { count, _ ->
                        processingMessage = "تم استخراج $count ملفات..."
                    }

                    // Read extracted files and create project
                    val filesList = mutableListOf<Pair<String, String>>()
                    targetDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relPath = file.relativeTo(targetDir).path.replace('\\', '/')
                        val content = file.readText(Charsets.UTF_8)
                        filesList.add(Pair(relPath, content))
                    }

                    val projectId = App.instance.projectStore.saveProject(
                        name = "ارشيف_مستخرج",
                        description = "مشروع مستخرج من أرشيف مضغوط",
                        techStack = "أرشيف",
                        files = filesList
                    )

                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        onNavigateToReview(projectId)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        Toast.makeText(context, "خطأ أثناء فك الأرشيف: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // App Identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ل",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "لُب (Lub)",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "استخلاص مشاريع الأكواد والرفع إلى GitHub",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isConnected) StatusSuccess.copy(alpha = 0.5f) else StatusWarning.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isConnected) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("تفعيل الخدمة")
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(StatusSuccess.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isConnected) "خدمة إمكانية الوصول: نشطة ومتاحة ✅" else "الخدمة غير مفعّلة ⚠️",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) StatusSuccess else StatusWarning
                        )
                        Text(
                            text = if (isConnected) "الفقاعة العائمة جاهزة فوق التطبيقات" else "اضغط لتفعيل الخدمة من إعدادات الهاتف",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // GitHub Connection Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToGitHub() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (githubUser.isNotEmpty()) Icons.Default.Public else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (githubUser.isNotEmpty()) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (githubUser.isNotEmpty()) "حساب GitHub: @$githubUser ✅" else "حساب GitHub غير مهيأ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (githubUser.isNotEmpty()) "جاهز لإنشاء المستودعات والرفع بضغطة واحدة" else "اضغط لإدخال التوكن وربط الحساب",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Processing Banner
            if (isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = processingMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // PRIMARY HERO ACTION: ⚡ المسار السريع
            Text(
                text = "المسار السريع (التقاط ورفع):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ التقط وارفع الآن",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "افتح المحادثة في المتصفح أو تطبيق الذكاء الاصطناعي واضغط الفقاعة، أو انقر هنا لقراءة الحافظة فوراً ورفعها بـ commit واحد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(context, ClipActivity::class.java).apply {
                                    putExtra(ClipActivity.EXTRA_FAST_PUSH, true)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("من الحافظة ⚡")
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, QuickPushActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ورقة التأكيد")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Secondary Import Options
            Text(
                text = "وظائف إضافية للاستيراد:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Folder upload card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { folderPickerLauncher.launch(null) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "رفع مجلد من الهاتف",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "اختيار مجلد عبر SAF مع استبعاد node_modules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Archive extract card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            archivePickerLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-tar",
                                    "application/gzip",
                                    "application/x-7z-compressed"
                                )
                            )
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "فك أرشيف ورفعه",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "يدعم ZIP و TAR و 7z بالأسماء العربية",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Basket Shortcut
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBasket() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$basketCount",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "سلة المقتطفات والملفات الملتقطة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ShoppingBasket, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
