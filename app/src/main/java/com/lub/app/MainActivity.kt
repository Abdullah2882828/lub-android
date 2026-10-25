package com.lub.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lub.app.archive.ArchiveExtractor
import com.lub.app.ui.screens.BasketScreen
import com.lub.app.ui.screens.DiagnosticsScreen
import com.lub.app.ui.screens.DoneScreen
import com.lub.app.ui.screens.ExistingRepoScreen
import com.lub.app.ui.screens.GitHubSetupScreen
import com.lub.app.ui.screens.HomeScreen
import com.lub.app.ui.screens.LibraryScreen
import com.lub.app.ui.screens.OnboardingScreen
import com.lub.app.ui.screens.ReviewScreen
import com.lub.app.ui.screens.SettingsScreen
import com.lub.app.ui.theme.LubTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("الرئيسية", Icons.Default.Home),
    BASKET("السلة", Icons.Default.ShoppingBasket),
    LIBRARY("المكتبة", Icons.Default.LibraryBooks),
    GITHUB("GitHub", Icons.Default.Cloud),
    SETTINGS("الإعدادات", Icons.Default.Settings)
}

sealed class ActiveSubScreen {
    object None : ActiveSubScreen()
    data class Review(val projectId: String) : ActiveSubScreen()
    data class ExistingRepo(val projectId: String) : ActiveSubScreen()
    data class Done(val repoUrl: String, val repoName: String) : ActiveSubScreen()
    object Diagnostics : ActiveSubScreen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settingsStore = App.instance.settingsStore
            val scope = rememberCoroutineScope()

            val savedThemeMode by settingsStore.themeModeFlow.collectAsState(initial = "SYSTEM")
            val systemDark = isSystemInDarkTheme()
            val isDark = when (savedThemeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }

            var isOnboardingCompleted by remember { mutableStateOf<Boolean?>(null) }
            var currentTab by remember { mutableStateOf(MainTab.HOME) }
            var subScreen by remember { mutableStateOf<ActiveSubScreen>(ActiveSubScreen.None) }

            val basketCount by App.instance.database.basketDao().getBasketCount().collectAsState(initial = 0)

            LaunchedEffect(Unit) {
                isOnboardingCompleted = settingsStore.onboardingCompletedFlow.first()

                // Check intent extras
                intent.getStringExtra("START_TAB")?.let { tab ->
                    if (tab == "BASKET") currentTab = MainTab.BASKET
                }

                intent.getStringExtra("START_ROUTE")?.let { route ->
                    when {
                        route.startsWith("review/") -> {
                            val id = route.removePrefix("review/")
                            subScreen = ActiveSubScreen.Review(id)
                        }
                        route.startsWith("done?") -> {
                            val uri = Uri.parse(route)
                            val url = uri.getQueryParameter("repoUrl") ?: ""
                            val name = uri.getQueryParameter("repoName") ?: ""
                            subScreen = ActiveSubScreen.Done(url, name)
                        }
                    }
                }

                intent.getStringExtra("EXTRA_ARCHIVE_URI")?.let { rawUri ->
                    val uri = Uri.parse(rawUri)
                    handleImportedArchive(uri) { projectId ->
                        subScreen = ActiveSubScreen.Review(projectId)
                    }
                }
            }

            LubTheme(darkTheme = isDark) {
                if (isOnboardingCompleted == false) {
                    OnboardingScreen(
                        settingsStore = settingsStore,
                        onFinish = { isOnboardingCompleted = true },
                        onNavigateToGithub = {
                            isOnboardingCompleted = true
                            currentTab = MainTab.GITHUB
                        }
                    )
                } else if (isOnboardingCompleted == true) {
                    BackHandler(enabled = subScreen !is ActiveSubScreen.None) {
                        subScreen = ActiveSubScreen.None
                    }

                    Scaffold(
                        bottomBar = {
                            if (subScreen is ActiveSubScreen.None) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 6.dp
                                ) {
                                    MainTab.values().forEach { tab ->
                                        val isSelected = currentTab == tab
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                if (tab == MainTab.BASKET && basketCount > 0) {
                                                    BadgedBox(
                                                        badge = {
                                                            Badge { Text("$basketCount") }
                                                        }
                                                    ) {
                                                        Icon(tab.icon, contentDescription = tab.title)
                                                    }
                                                } else {
                                                    Icon(tab.icon, contentDescription = tab.title)
                                                }
                                            },
                                            label = {
                                                Text(tab.title, style = MaterialTheme.typography.labelSmall)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (val screen = subScreen) {
                                is ActiveSubScreen.Review -> {
                                    ReviewScreen(
                                        projectId = screen.projectId,
                                        onBack = { subScreen = ActiveSubScreen.None },
                                        onNavigateToUpload = { pId ->
                                            val pushIntent = Intent(this@MainActivity, QuickPushActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            startActivity(pushIntent)
                                        }
                                    )
                                }
                                is ActiveSubScreen.ExistingRepo -> {
                                    ExistingRepoScreen(
                                        projectId = screen.projectId,
                                        onBack = { subScreen = ActiveSubScreen.None },
                                        onSuccess = { repoUrl, repoName ->
                                            subScreen = ActiveSubScreen.Done(repoUrl, repoName)
                                        }
                                    )
                                }
                                is ActiveSubScreen.Done -> {
                                    DoneScreen(
                                        repoUrl = screen.repoUrl,
                                        repoName = screen.repoName,
                                        onNavigateHome = {
                                            subScreen = ActiveSubScreen.None
                                            currentTab = MainTab.HOME
                                        }
                                    )
                                }
                                is ActiveSubScreen.Diagnostics -> {
                                    DiagnosticsScreen()
                                }
                                ActiveSubScreen.None -> {
                                    Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                                        when (tab) {
                                            MainTab.HOME -> HomeScreen(
                                                onNavigateToBasket = { currentTab = MainTab.BASKET },
                                                onNavigateToReview = { pId -> subScreen = ActiveSubScreen.Review(pId) },
                                                onNavigateToGitHub = { currentTab = MainTab.GITHUB }
                                            )
                                            MainTab.BASKET -> BasketScreen(
                                                onNavigateToReview = { pId -> subScreen = ActiveSubScreen.Review(pId) }
                                            )
                                            MainTab.LIBRARY -> LibraryScreen(
                                                onSelectProject = { pId -> subScreen = ActiveSubScreen.Review(pId) }
                                            )
                                            MainTab.GITHUB -> GitHubSetupScreen()
                                            MainTab.SETTINGS -> SettingsScreen(
                                                onNavigateToDiagnostics = { subScreen = ActiveSubScreen.Diagnostics },
                                                onToggleTheme = {
                                                    scope.launch {
                                                        val nextMode = if (isDark) "LIGHT" else "DARK"
                                                        settingsStore.setThemeMode(nextMode)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleImportedArchive(uri: Uri, onDone: (String) -> Unit) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val targetDir = File(filesDir, "imported_archive_${UUID.randomUUID().toString().take(6)}")
                ArchiveExtractor.extractArchive(this@MainActivity, uri, targetDir) { _, _ -> }

                val filesList = mutableListOf<Pair<String, String>>()
                targetDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relPath = file.relativeTo(targetDir).path.replace('\\', '/')
                    val content = file.readText(Charsets.UTF_8)
                    filesList.add(Pair(relPath, content))
                }

                val projectId = App.instance.projectStore.saveProject(
                    name = "مستورد_${UUID.randomUUID().toString().take(4)}",
                    description = "مشروع مستورد من أرشيف خارجي",
                    techStack = "أرشيف",
                    files = filesList
                )

                withContext(Dispatchers.Main) {
                    onDone(projectId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "خطأ أثناء استيراد الأرشيف: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
