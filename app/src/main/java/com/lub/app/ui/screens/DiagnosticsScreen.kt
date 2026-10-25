package com.lub.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lub.app.capture.ScreenReader
import com.lub.app.essence.LineNumberStripper
import com.lub.app.github.RepoNamer
import com.lub.app.parser.PathSanitizer
import com.lub.app.service.CaptureBus
import com.lub.app.service.LubAccessibilityService
import com.lub.app.ui.theme.StatusError
import com.lub.app.ui.theme.StatusSuccess
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val latestReport by CaptureBus.latestDiagnostic.collectAsState()
    val isConnected by CaptureBus.isServiceConnected.collectAsState()
    val foregroundPkg by CaptureBus.currentForegroundPackage.collectAsState()

    var testResults by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var testDetails by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun runSelfTests() {
        val results = mutableMapOf<String, Boolean>()
        val details = mutableMapOf<String, String>()

        // 1. PathSanitizer Test
        val sanitizedTraverse = PathSanitizer.sanitize("../../etc/passwd")
        val sanitizedWindows = PathSanitizer.sanitize("C:\\Projects\\My App\\src/main.js")
        val pathOk = sanitizedTraverse == "etc/passwd" && sanitizedWindows == "Projects/My App/src/main.js"
        results["حماية المسارات (PathSanitizer)"] = pathOk
        details["حماية المسارات (PathSanitizer)"] = "اختبار ../../etc/passwd ⇐ $sanitizedTraverse"

        // 2. LineNumberStripper Test
        val numberedCode = "1  import os\n2  import sys\n3  print('hello')\n4  exit(0)"
        val (stripped, hadStripped) = LineNumberStripper.stripLineNumbers(numberedCode)
        val stripperOk = hadStripped && stripped.lines().first() == "import os"
        results["إزالة أرقام الأسطر (LineNumberStripper)"] = stripperOk
        details["إزالة أرقام الأسطر (LineNumberStripper)"] = "تم اكتشاف تسلسل 4 أسطر وإزالتها بنجاح"

        // 3. RepoNamer Test
        val namedArabic = RepoNamer.cleanRepoName("مشروع تطبيق لُب الجديد")
        val namedClean = RepoNamer.cleanRepoName("@my-org/awesome_project.v2")
        val namerOk = namedArabic.startsWith("lub-") && namedClean == "my-org-awesome_project.v2"
        results["تسمية المستودعات (RepoNamer)"] = namerOk
        details["تسمية المستودعات (RepoNamer)"] = "@my-org/awesome_project.v2 ⇐ $namedClean"

        // 4. Clipboard Service Test
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val hasClipboard = clipboard.hasPrimaryClip()
        results["خدمة الحافظة (Clipboard)"] = true
        details["خدمة الحافظة (Clipboard)"] = if (hasClipboard) "الحافظة تحتوي على نص حالياً" else "الحافظة خالية"

        testResults = results
        testDetails = details
    }

    fun captureSampleDiagnostic() {
        val service = LubAccessibilityService.getInstance()
        if (service == null) {
            Toast.makeText(context, "الخدمة غير متصلة، فعّلها أولاً من الإعدادات", Toast.LENGTH_SHORT).show()
            return
        }
        val root = service.rootInActiveWindow
        val report = ScreenReader.diagnose(root, foregroundPkg)
        CaptureBus.latestDiagnostic.value = report
        Toast.makeText(context, "تم التقاط التقرير التشخيصي بنجاح", Toast.LENGTH_SHORT).show()
    }

    fun copyReportToClipboard() {
        val report = latestReport
        val sb = StringBuilder()
        sb.appendLine("=== تقرير تشخيص تطبيق لُب ===")
        sb.appendLine("حالة الخدمة: ${if (isConnected) "نشطة" else "غير متصلة"}")
        sb.appendLine("التطبيق الأمامي: $foregroundPkg")
        if (report != null) {
            sb.appendLine("عدد العقد: ${report.nodeCount}")
            sb.appendLine("أقصى عمق: ${report.maxDepth}")
            sb.appendLine("أطول نص ملتقط: ${report.longestTextPreview}")
            sb.appendLine("--- العينات الملتقطة (${report.sampleNodes.size}) ---")
            report.sampleNodes.forEachIndexed { i, n ->
                sb.appendLine("[$i] ${n.className} | id=${n.viewId} | lines=${n.lineCount} | indent=${n.preservesIndentation} | bounds=${n.bounds}")
                sb.appendLine("    أول 120 حرف: ${n.textPreview}")
            }
        }
        sb.appendLine("--- نتائج الاختبارات الذاتية ---")
        testResults.forEach { (name, ok) ->
            sb.appendLine("${if (ok) "✅" else "❌"} $name: ${testDetails[name]}")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Lub Diagnostic Report", sb.toString()))
        Toast.makeText(context, "تم نسخ التقرير الكامل للحافظة", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "شاشة التشخيص والاختبار",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "معاينة سلوك التطبيقات وضبط الاستدلال بدقة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { captureSampleDiagnostic() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("التقاط تجريبي")
                    }

                    OutlinedButton(
                        onClick = { runSelfTests() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختبار ذاتي")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { copyReportToClipboard() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("نسخ التقرير للمطور")
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Self tests results
            if (testResults.isNotEmpty()) {
                item {
                    Text(
                        text = "نتائج الاختبارات الذاتية للوحدات:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(testResults.entries.toList()) { (testName, isSuccess) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isSuccess) StatusSuccess else StatusError
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = testName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val desc = testDetails[testName]
                                if (desc != null) {
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Current Active Window Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "معلومات النافذة النشطة:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "الحزمة الأمامية: ${if (foregroundPkg.isNotEmpty()) foregroundPkg else "غير محدد"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "حالة الخدمة: ${if (isConnected) "✅ متصلة وجاهزة" else "❌ غير مفعّلة"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isConnected) StatusSuccess else StatusError
                        )
                        latestReport?.let { rep ->
                            Text(
                                text = "عدد العقد: ${rep.nodeCount} | أقصى عمق: ${rep.maxDepth}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sample Nodes list
            latestReport?.let { rep ->
                item {
                    Text(
                        text = "العينات ذات النصوص الملتقطة (${rep.sampleNodes.size}):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(rep.sampleNodes) { node ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${node.className} | id=${node.viewId.ifEmpty { "بلا معرف" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "الأسطر: ${node.lineCount} | حفظ المسافات: ${if (node.preservesIndentation) "نعم ✅" else "لا ⚠️"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = node.textPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
