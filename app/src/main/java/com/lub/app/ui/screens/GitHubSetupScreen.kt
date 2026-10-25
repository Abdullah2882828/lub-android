package com.lub.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lub.app.App
import com.lub.app.github.GitHubClient
import com.lub.app.github.GitHubUser
import com.lub.app.ui.theme.StatusError
import com.lub.app.ui.theme.StatusSuccess
import kotlinx.coroutines.launch

@Composable
fun GitHubSetupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tokenInput by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<GitHubUser?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun checkAndLoadExistingToken() {
        scope.launch {
            val token = App.instance.settingsStore.getGitHubToken()
            if (token.isNotEmpty()) {
                tokenInput = token
                isChecking = true
                try {
                    val client = GitHubClient(token)
                    currentUser = client.getAuthenticatedUser()
                    errorMessage = null
                } catch (e: Exception) {
                    errorMessage = e.message
                } finally {
                    isChecking = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkAndLoadExistingToken()
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

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ربط حساب GitHub ☁️",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "تخزين مشفر بـ Android Keystore مباشرة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connected user card if valid
            if (currentUser != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    App.instance.settingsStore.clearGitHubToken()
                                    currentUser = null
                                    tokenInput = ""
                                    Toast.makeText(context, "تم إلغاء ربط الحساب", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف التوكن", tint = StatusError)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "متصل بنجاح: @${currentUser?.login} ✅",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusSuccess
                            )
                            Text(
                                text = "البريد: ${currentUser?.email ?: "مخفي"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "كيف أحصل على التوكن؟",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "١. افتح GitHub ← Settings ← Developer Settings ← Personal access tokens (classic).\n" +
                               "٢. أنشئ توكناً جديداً وحدد صلاحية repo (وصلاحية workflow إن رغبت بإضافة أكشنز).\n" +
                               "٣. الصق التوكن أدناه (يبدأ بـ ghp_).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح صفحة التوكنات في GitHub")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Token input field
            OutlinedTextField(
                value = tokenInput,
                onValueChange = {
                    tokenInput = it.trim()
                    errorMessage = null
                },
                label = { Text("رمز الوصول الشخصي (Token)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                        Icon(
                            imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isTokenVisible) "إخفاء" else "إظهار"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save / Test Button
            Button(
                onClick = {
                    val raw = tokenInput.trim()
                    if (raw.isEmpty()) {
                        Toast.makeText(context, "يرجى إدخال التوكن أولاً", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isChecking = true
                    errorMessage = null

                    scope.launch {
                        try {
                            val client = GitHubClient(raw)
                            val user = client.getAuthenticatedUser()
                            App.instance.settingsStore.saveGitHubToken(raw, user.login)
                            currentUser = user
                            Toast.makeText(context, "تم التحقق وحفظ التوكن بنجاح! 🎉", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "فشل التحقق من التوكن"
                        } finally {
                            isChecking = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري التحقق من GitHub...")
                } else {
                    Icon(Icons.Default.CloudDone, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("التحقق والحفظ المشفر", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
