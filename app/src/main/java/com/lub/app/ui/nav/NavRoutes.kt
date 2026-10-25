package com.lub.app.ui.nav

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Basket : Screen("basket")
    object Library : Screen("library")
    object GitHubSetup : Screen("github_setup")
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")

    object Review : Screen("review/{projectId}") {
        fun createRoute(projectId: String) = "review/$projectId"
    }

    object ExistingRepo : Screen("existing_repo/{projectId}") {
        fun createRoute(projectId: String) = "existing_repo/$projectId"
    }

    object Progress : Screen("progress/{jobId}") {
        fun createRoute(jobId: String) = "progress/$jobId"
    }

    object Done : Screen("done?repoUrl={repoUrl}&repoName={repoName}") {
        fun createRoute(repoUrl: String, repoName: String) =
            "done?repoUrl=${android.net.Uri.encode(repoUrl)}&repoName=${android.net.Uri.encode(repoName)}"
    }
}
