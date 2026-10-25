package com.lub.app.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    val email: String?,
    val avatarUrl: String?
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val htmlUrl: String,
    val description: String?
)

class GitHubApiException(
    val statusCode: Int,
    val arabicMessage: String,
    val rawError: String? = null
) : Exception(arabicMessage)

class GitHubClient(private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun newRequestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .addHeader("User-Agent", "Lub-Android-App")
    }

    suspend fun getAuthenticatedUser(): GitHubUser = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("https://api.github.com/user").get().build()
        executeCall(request) { responseBody ->
            val json = JSONObject(responseBody)
            val id = json.getLong("id")
            val login = json.getString("login")
            val name = json.optString("name", null)
            val rawEmail = json.optString("email", null)
            val email = if (!rawEmail.isNullOrBlank()) rawEmail else "$id+$login@users.noreply.github.com"
            GitHubUser(id, login, name, email, json.optString("avatar_url", null))
        }
    }

    suspend fun checkRepoExists(owner: String, repoName: String): Boolean = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("https://api.github.com/repos/$owner/$repoName").get().build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> true
                    404 -> false
                    401 -> throw parseError(401, response.body?.string())
                    else -> false
                }
            }
        } catch (e: Exception) {
            if (e is GitHubApiException) throw e
            false
        }
    }

    suspend fun createRepository(
        name: String,
        description: String,
        isPrivate: Boolean,
        autoInit: Boolean = true
    ): GitHubRepo = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("private", isPrivate)
            put("auto_init", autoInit)
        }
        val request = newRequestBuilder("https://api.github.com/user/repos")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        executeCall(request) { body ->
            val res = JSONObject(body)
            GitHubRepo(
                id = res.getLong("id"),
                name = res.getString("name"),
                fullName = res.getString("full_name"),
                isPrivate = res.getBoolean("private"),
                defaultBranch = res.optString("default_branch", "main"),
                htmlUrl = res.getString("html_url"),
                description = res.optString("description", null)
            )
        }
    }

    suspend fun listUserRepos(): List<GitHubRepo> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("https://api.github.com/user/repos?sort=updated&per_page=100")
            .get()
            .build()
        executeCall(request) { body ->
            val array = JSONArray(body)
            val list = mutableListOf<GitHubRepo>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    GitHubRepo(
                        id = item.getLong("id"),
                        name = item.getString("name"),
                        fullName = item.getString("full_name"),
                        isPrivate = item.getBoolean("private"),
                        defaultBranch = item.optString("default_branch", "main"),
                        htmlUrl = item.getString("html_url"),
                        description = item.optString("description", null)
                    )
                )
            }
            list
        }
    }

    suspend fun getBranchHeadSha(owner: String, repo: String, branch: String): String? = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext null
                if (!response.isSuccessful) throw parseError(response.code, response.body?.string())
                val json = JSONObject(response.body?.string() ?: "{}")
                json.getJSONObject("object").getString("sha")
            }
        } catch (e: Exception) {
            if (e is GitHubApiException && e.statusCode == 404) null else throw e
        }
    }

    suspend fun getCommitTreeSha(owner: String, repo: String, commitSha: String): String = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("https://api.github.com/repos/$owner/$repo/git/commits/$commitSha")
            .get()
            .build()
        executeCall(request) { body ->
            val json = JSONObject(body)
            json.getJSONObject("tree").getString("sha")
        }
    }

    suspend fun createBlob(owner: String, repo: String, content: String, encoding: String = "utf-8"): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("content", content)
            put("encoding", encoding)
        }
        val request = newRequestBuilder("https://api.github.com/repos/$owner/$repo/git/blobs")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()
        executeCall(request) { body ->
            val res = JSONObject(body)
            res.getString("sha")
        }
    }

    suspend fun createTree(
        owner: String,
        repo: String,
        baseTreeSha: String?,
        treeNodes: List<TreeNodeItem>
    ): String = withContext(Dispatchers.IO) {
        val treeArray = JSONArray()
        for (node in treeNodes) {
            val nodeObj = JSONObject().apply {
                put("path", node.path)
                put("mode", "100644")
                put("type", "blob")
                put("sha", node.blobSha)
            }
            treeArray.put(nodeObj)
        }

        val json = JSONObject().apply {
            if (!baseTreeSha.isNullOrEmpty()) {
                put("base_tree", baseTreeSha)
            }
            put("tree", treeArray)
        }

        val request = newRequestBuilder("https://api.github.com/repos/$owner/$repo/git/trees")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()
        executeCall(request) { body ->
            val res = JSONObject(body)
            res.getString("sha")
        }
    }

    suspend fun createCommit(
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parentCommitShas: List<String>,
        authorName: String,
        authorEmail: String
    ): String = withContext(Dispatchers.IO) {
        val parentsArray = JSONArray()
        for (parent in parentCommitShas) {
            parentsArray.put(parent)
        }

        val authorObj = JSONObject().apply {
            put("name", authorName)
            put("email", authorEmail)
        }

        val json = JSONObject().apply {
            put("message", message)
            put("tree", treeSha)
            put("parents", parentsArray)
            put("author", authorObj)
        }

        val request = newRequestBuilder("https://api.github.com/repos/$owner/$repo/git/commits")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()
        executeCall(request) { body ->
            val res = JSONObject(body)
            res.getString("sha")
        }
    }

    suspend fun updateBranchRef(
        owner: String,
        repo: String,
        branch: String,
        commitSha: String,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("sha", commitSha)
            put("force", force)
        }
        val request = newRequestBuilder("https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch")
            .patch(json.toString().toRequestBody(jsonMediaType))
            .build()
        executeCall(request) { true }
    }

    /**
     * إنشاء ملف أولي عبر Contents API في حال كان المستودع فارغاً تماماً
     */
    suspend fun initializeEmptyRepoWithReadme(owner: String, repo: String): String = withContext(Dispatchers.IO) {
        val contentBase64 = android.util.Base64.encodeToString(
            "# $repo\n\nInitialized by Lub.".toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val json = JSONObject().apply {
            put("message", "Initial commit from Lub")
            put("content", contentBase64)
        }
        val request = newRequestBuilder("https://api.github.com/repos/$owner/$repo/contents/README.md")
            .put(json.toString().toRequestBody(jsonMediaType))
            .build()
        executeCall(request) { body ->
            val res = JSONObject(body)
            res.getJSONObject("commit").getString("sha")
        }
    }

    private inline fun <T> executeCall(request: Request, parser: (String) -> T): T {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw parseError(response.code, body)
                }
                return parser(body)
            }
        } catch (e: IOException) {
            throw GitHubApiException(-1, "تعذر الاتصال بـ GitHub. يرجى التحقق من اتصال الإنترنت", e.message)
        }
    }

    private fun parseError(code: Int, rawBody: String?): GitHubApiException {
        val message = when (code) {
            401 -> "رمز الوصول (Token) غير صالح أو منتهي الصلاحية. يرجى تحديث التوكن في إعدادات GitHub"
            403 -> "تم تجاوز حد الطلبات المسموح (Rate Limit) أو الصلاحيات غير كافية لإنشاء المستودعات"
            404 -> "المستودع أو المورد المطلوب غير موجود"
            409 -> "تعارض في حالة المستودع (Git Conflict)"
            422 -> "اسم المستودع موجود مسبقاً أو غير صالح طبقاً لقواعد GitHub"
            else -> "حدث خطأ من GitHub (رمز الخطأ: $code)"
        }
        return GitHubApiException(code, message, rawBody)
    }

    data class TreeNodeItem(val path: String, val blobSha: String)
}
