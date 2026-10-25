package com.lub.app.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

data class PushItem(
    val relativePath: String,
    val textContent: String? = null,
    val localFile: File? = null,
    val isBinary: Boolean = false
)

data class PushProgress(
    val stage: String,
    val completedItems: Int,
    val totalItems: Int,
    val percentage: Float
)

class PushEngine(
    private val client: GitHubClient
) {

    suspend fun pushProject(
        owner: String,
        repoName: String,
        branch: String,
        commitMessage: String,
        items: List<PushItem>,
        isNewRepo: Boolean,
        onProgress: (PushProgress) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            throw IllegalArgumentException("لا توجد ملفات لرفعها")
        }

        // 1. Get user identity for commit author
        onProgress(PushProgress("التحقق من هوية المستخدم...", 0, items.size, 0.05f))
        val user = client.getAuthenticatedUser()
        val authorName = user.name ?: user.login
        val authorEmail = user.email ?: "${user.id}+${user.login}@users.noreply.github.com"

        // 2. Resolve Head SHA and Base Tree SHA
        onProgress(PushProgress("تجهيز شجرة المستودع...", 0, items.size, 0.10f))
        var headSha = client.getBranchHeadSha(owner, repoName, branch)
        if (headSha == null) {
            // Empty repo with no commits yet - initialize with initial README
            headSha = client.initializeEmptyRepoWithReadme(owner, repoName)
        }

        val baseTreeSha = client.getCommitTreeSha(owner, repoName, headSha)

        // 3. Create Blobs with concurrency limit (4-6)
        val semaphore = Semaphore(5)
        var uploadedBlobCount = 0
        val totalItems = items.size

        val treeNodes = coroutineScope {
            items.map { item ->
                async {
                    semaphore.withPermit {
                        val blobSha = if (item.isBinary && item.localFile != null) {
                            // Binary file base64
                            val bytes = item.localFile.readBytes()
                            if (bytes.size > 100 * 1024 * 1024) {
                                throw IllegalArgumentException("الملف ${item.relativePath} يتجاوز 100MB وهو غير مسموح على GitHub")
                            }
                            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            client.createBlob(owner, repoName, b64, "base64")
                        } else {
                            // Text file
                            val content = item.textContent ?: item.localFile?.readText(Charsets.UTF_8) ?: ""
                            client.createBlob(owner, repoName, content, "utf-8")
                        }

                        synchronized(this@PushEngine) {
                            uploadedBlobCount++
                            val pct = 0.10f + (uploadedBlobCount.toFloat() / totalItems.toFloat()) * 0.70f
                            onProgress(
                                PushProgress(
                                    "رفع الكتل البرمجية ($uploadedBlobCount من $totalItems)...",
                                    uploadedBlobCount,
                                    totalItems,
                                    pct
                                )
                            )
                        }

                        GitHubClient.TreeNodeItem(path = item.relativePath, blobSha = blobSha)
                    }
                }
            }.awaitAll()
        }

        // 4. Create Trees in batches of <= 500 entries
        onProgress(PushProgress("إنشاء شجرة الملفات...", totalItems, totalItems, 0.85f))
        var currentBaseTree: String? = baseTreeSha
        val chunks = treeNodes.chunked(500)

        for (chunk in chunks) {
            currentBaseTree = client.createTree(owner, repoName, currentBaseTree, chunk)
        }
        val finalTreeSha = currentBaseTree ?: throw IllegalStateException("فشل بناء شجرة الملفات")

        // 5. Create Commit
        onProgress(PushProgress("تسجيل الـ Commit...", totalItems, totalItems, 0.92f))
        val commitSha = client.createCommit(
            owner = owner,
            repo = repoName,
            message = commitMessage,
            treeSha = finalTreeSha,
            parentCommitShas = listOf(headSha),
            authorName = authorName,
            authorEmail = authorEmail
        )

        // 6. Update Branch Ref
        onProgress(PushProgress("تحديث فرع $branch...", totalItems, totalItems, 0.98f))
        client.updateBranchRef(
            owner = owner,
            repo = repoName,
            branch = branch,
            commitSha = commitSha
        )

        onProgress(PushProgress("اكتمل الرفع بنجاح! 🚀", totalItems, totalItems, 1.0f))
        "https://github.com/$owner/$repoName"
    }
}
