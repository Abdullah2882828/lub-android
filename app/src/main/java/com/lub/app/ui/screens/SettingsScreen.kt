package com.lub.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
import com.lub.app.App
import com.lub.app.data.SettingsStore
import com.lub.app.ui.theme.StatusError
import com.lub.app.ui.theme.StatusWarning
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToDiagnostics: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsStore = App.instance.settingsStore
    val fastPush by settingsStore.fastPushFlow.collectAsState(initial = false)
    val captureMode by settingsStore.defaultCaptureModeFlow.collectAsState(initial = "FULL_CHAT")
    val scrollLimit by settingsStore.autoScrollLimitFlow.collectAsState(initial = 50)
    val bubbleEnabled by settingsStore.bubbleEnabledFlow.collectAsState(initial = true)
    val denyList by settingsStore.denyListFlow.collectAsState(initial = SettingsStore.DEFAULT_DENY_PACKAGES)
    val biometricLock by settingsStore.biometricLockFlow.collectAsState(initial = false)

    var newDenyPkg by remember { mutableStateOf("") }

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

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleTheme) {
                    Icon(Icons.Default.DarkMode, contentDescription = "تبديل المظهر")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "الإعدادات ⚙️",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "تخصيص سلوك الالتقاط، الخصوصية، والرفع",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostics Shortcut
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onNavigateToDiagnostics,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح التشخيص")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "لوحة التشخيص المتقدمة 🧪", fontWeight = FontWeight.Bold)
                        Text(
                            text = "معاينة شجرة العقد الحية واختبار الوحدات",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capture & Push Settings
            Text(
                text = "سلوك الالتقاط والمسار السريع:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Fast Push Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = fastPush,
                            onCheckedChange = { scope.launch { settingsStore.setFastPush(it) } }
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "رفع فوري بدون ورقة تأكيد", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "يرفع فوراً إلى مستودع مقترح عند ضغط ⚡",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bubble Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = bubbleEnabled,
                            onCheckedChange = { scope.launch { settingsStore.setBubbleEnabled(it) } }
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "إظهار الفقاعة العائمة", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "ظهور الفقاعة فوق التطبيقات الأخرى",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto Scroll Limit
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "الحد الأقصى لصفحات التمرير: $scrollLimit صفحة", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = scrollLimit.toFloat(),
                            onValueChange = { scope.launch { settingsStore.setAutoScrollLimit(it.toInt()) } },
                            valueRange = 10f..200f,
                            steps = 18
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // DenyList Manager
            Text(
                text = "قائمة التطبيقات الممنوعة (DenyList):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "في هذه التطبيقات تختفي الفقاعة تلقائياً ولا يقرأ لُب أي محتوى لحماية خصوصيتك.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.End) {
                    denyList.forEach { pkg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { scope.launch { settingsStore.removeDenyPackage(pkg) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "إزالة", tint = StatusError, modifier = Modifier.size(16.dp))
                            }
                            Text(text = pkg, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (newDenyPkg.isNotBlank()) {
                                    scope.launch {
                                        settingsStore.addDenyPackage(newDenyPkg.trim())
                                        newDenyPkg = ""
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("إضافة")
                        }
                        OutlinedTextField(
                            value = newDenyPkg,
                            onValueChange = { newDenyPkg = it },
                            label = { Text("اسم الحزمة (مثال: com.bank.app)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Emergency Stop & Reset
            Text(
                text = "إجراءات الطوارئ والأمان:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = StatusError,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = {
                    scope.launch {
                        settingsStore.setBubbleEnabled(false)
                        App.instance.database.basketDao().clearBasket()
                        Toast.makeText(context, "تم إيقاف الفقاعة ومسح السلة فوراً 🛑", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusError)
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إيقاف طارئ ومسح السلة فوراً")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Honest statement footer
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "لُب v1.0 — تطبيق للاستخدام الشخصي المستقل",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "يعمل محلياً على جهازك دون أي خوادم وسيطة أو تتبع. يتم الرفع مباشرة من هاتفك إلى GitHub عبر التوكن الخاص بك.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
