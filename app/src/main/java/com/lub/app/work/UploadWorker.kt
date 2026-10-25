package com.lub.app.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lub.app.App
import com.lub.app.MainActivity
import com.lub.app.R
import com.lub.app.github.GitHubClient
import com.lub.app.github.PushEngine
import com.lub.app.github.PushItem
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class UploadWorker(
    private val context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    companion object {
        const val KEY_PROJECT_ID = "key_project_id"
        const val KEY_REPO_NAME = "key_repo_name"
        const val KEY_REPO_DESC = "key_repo_desc"
        const val KEY_IS_PRIVATE = "key_is_private"
        const val KEY_COMMIT_MSG = "key_commit_msg"
        const val KEY_BRANCH = "key_branch"
        const val KEY_RESULT_REPO_URL = "key_result_repo_url"

        val activeUploadProgress = MutableStateFlow<String>("")
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT_ID) ?: return Result.failure()
        val repoName = inputData.getString(KEY_REPO_NAME) ?: return Result.failure()
        val repoDesc = inputData.getString(KEY_REPO_DESC) ?: ""
        val isPrivate = inputData.getBoolean(KEY_IS_PRIVATE, true)
        val commitMsg = inputData.getString(KEY_COMMIT_MSG) ?: "Commit from Lub"
        val branch = inputData.getString(KEY_BRANCH) ?: "main"

        val token = App.instance.settingsStore.getGitHubToken()
        if (token.isEmpty()) {
            return Result.failure(workDataOf("error" to "توكن GitHub غير متوفر"))
        }

        val client = GitHubClient(token)
        val pushEngine = PushEngine(client)

        setForeground(createForegroundInfo("بدء الرفع إلى GitHub...", 0))

        try {
            val user = client.getAuthenticatedUser()
            val owner = user.login

            // Check if repo exists, create if not
            val exists = client.checkRepoExists(owner, repoName)
            if (!exists) {
                client.createRepository(
                    name = repoName,
                    description = repoDesc,
                    isPrivate = isPrivate,
                    autoInit = true
                )
            }

            // Load files from ProjectStore / Room
            val projectFiles = App.instance.database.projectDao().getFilesForProject(projectId)
            val pushItems = projectFiles.filter { it.isIncluded }.map {
                PushItem(
                    relativePath = it.relativePath,
                    textContent = it.content,
                    isBinary = false
                )
            }

            val repoUrl = pushEngine.pushProject(
                owner = owner,
                repoName = repoName,
                branch = branch,
                commitMessage = commitMsg,
                items = pushItems,
                isNewRepo = !exists
            ) { progress ->
                activeUploadProgress.value = progress.stage
                notificationManager.notify(
                    1002,
                    buildNotification(progress.stage, (progress.percentage * 100).toInt())
                )
            }

            // Update database
            val project = App.instance.database.projectDao().getProjectById(projectId)
            if (project != null) {
                App.instance.database.projectDao().updateProject(
                    project.copy(
                        repoUrl = repoUrl,
                        isUploaded = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            activeUploadProgress.value = "اكتمل الرفع!"
            return Result.success(workDataOf(KEY_RESULT_REPO_URL to repoUrl))
        } catch (e: Exception) {
            activeUploadProgress.value = "فشل: ${e.message}"
            return Result.failure(workDataOf("error" to (e.message ?: "خطأ غير معروف")))
        }
    }

    private fun createForegroundInfo(progressText: String, progressPct: Int): ForegroundInfo {
        val notification = buildNotification(progressText, progressPct)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1002, notification)
        }
    }

    private fun buildNotification(progressText: String, progressPct: Int): android.app.Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, App.CHANNEL_ID_UPLOAD)
            .setContentTitle("لُب — جاري الرفع إلى GitHub")
            .setContentText(progressText)
            .setSmallIcon(R.drawable.ic_lub)
            .setProgress(100, progressPct, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
