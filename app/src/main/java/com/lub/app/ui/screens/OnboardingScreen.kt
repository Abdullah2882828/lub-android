package com.lub.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lub.app.R
import com.lub.app.data.SettingsStore
import com.lub.app.service.LubAccessibilityService
import com.lub.app.ui.theme.StatusSuccess
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settingsStore: SettingsStore,
    onFinish: () -> Unit,
    onNavigateToGithub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isNotificationGranted by remember { mutableStateOf(false) }
    var hasGithubToken by remember { mutableStateOf(false) }

    fun refreshStates() {
        isAccessibilityEnabled = LubAccessibilityService.isServiceRunning()
        isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        scope.launch {
            hasGithubToken = settingsStore.getGitHubToken().isNotEmpty()
        }
    }

    // Check states upon resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        refreshStates()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.End
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "مرحباً بك في لُب",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "إعداد وتشغيل الخدمة خلال دقائق",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 1: Honest Explanation
            StepCard(
                stepNumber = "١",
                title = "مبدأ لُب وخصوصيتك",
                description = "لُب يلتقط ويستخلص مشاريع الأكواد والملفات من شاشة الهاتف فقط عندما تضغط زر الالتقاط. لا توجد مراقبة مستمرة في الخلفية، ولا تسجيل للشاشة، ولا إرسال لأي بيانات خارج حسابك على GitHub.",
                icon = Icons.Default.Info,
                isCompleted = true,
                actionLabel = "أوافق وأتفهم",
                onAction = {}
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 2: Enable Accessibility
            StepCard(
                stepNumber = "٢",
                title = "تفعيل خدمة لُب لإمكانية الوصول",
                description = "الخدمة ضرورية لقراءة شاشة الكود وإظهار الفقاعة العائمة فوق التطبيقات دون الحاجة لتصريح النوافذ الخاصة.",
                icon = Icons.Default.Visibility,
                isCompleted = isAccessibilityEnabled,
                actionLabel = if (isAccessibilityEnabled) "الخدمة مفعلة ✅" else "فتح إعدادات إمكانية الوصول",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 3: Restricted Settings Help (Android 13+)
            StepCard(
                stepNumber = "٣",
                title = "حل حظر «الإعدادات المقيّدة» (أندرويد 13+)",
                description = "إذا ظهر لك تنبيه بمنع تفعيل الخدمة لأنها مقيّدة: افتح معلومات التطبيق بالزر أدناه، اضغط على النقاط الثلاث (⋮) أعلى الشاشة واختر «السماح بالإعدادات المقيّدة»، ثم عُد لتفعيل الخدمة في الخطوة ٢.",
                icon = Icons.Default.Lock,
                isCompleted = isAccessibilityEnabled,
                actionLabel = "معلومات التطبيق (للسماح)",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 4: Notification Permission
            StepCard(
                stepNumber = "٤",
                title = "تصريح الإشعارات",
                description = "مطلوب لإظهار شريط تقدم رفع الملفات للـ GitHub في الخلفية والوصول للحافظة بأمان.",
                icon = Icons.Default.Notifications,
                isCompleted = isNotificationGranted,
                actionLabel = if (isNotificationGranted) "الإشعارات مسموحة ✅" else "السماح بالإشعارات",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 5: Battery Optimization (Honor / Android)
            StepCard(
                stepNumber = "٥",
                title = "ضبط البطارية (مهم لأجهزة Honor)",
                description = "لمنع النظام من إيقاف الفقاعة: اجعل إدارة التشغيل يدوية: اسمح بالتشغيل التلقائي والثانوي والعمل بالخلفية، وثبّت التطبيق في قائمة التطبيقات الأخيرة.",
                icon = Icons.Default.BatteryAlert,
                isCompleted = false,
                actionLabel = "فتح إعدادات البطارية",
                onAction = {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 6: GitHub Token
            StepCard(
                stepNumber = "٦",
                title = "ربط حساب GitHub (توكن التخزين)",
                description = "مطلوب لإنشاء المستودعات ورفع الأكواد تلقائياً. يمكنك إدخاله الآن أو لاحقاً من تبويب GitHub.",
                icon = Icons.Default.Cloud,
                isCompleted = hasGithubToken,
                actionLabel = if (hasGithubToken) "تم ضبط التوكن ✅" else "تهيئة حساب GitHub",
                onAction = onNavigateToGithub
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Done / Proceed Button
            Button(
                onClick = {
                    scope.launch {
                        settingsStore.setOnboardingCompleted(true)
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "🚀 بدء استخدام لُب",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isCompleted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCompleted) StatusSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompleted) StatusSuccess.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "مكتمل",
                            tint = StatusSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = stepNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
