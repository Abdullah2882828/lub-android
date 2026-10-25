package com.lub.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * إدارة حفظ المشاريع والملفات محلياً على مسار filesDir/projects/<id>/...
 * مع مزامنة الجداول في Room وتصدير المشاريع عبر SAF أو ZIP
 */
class ProjectStore(
    private val context: Context,
    private val projectDao: ProjectDao
) {

    private val projectsRoot: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    fun getProjectDirectory(projectId: String): File {
        return File(projectsRoot, projectId).apply { if (!exists()) mkdirs() }
    }

    suspend fun saveProject(
        name: String,
        description: String,
        techStack: String,
        files: List<Pair<String, String>>, // relativePath -> content
        scores: Map<String, Float> = emptyMap(),
        warnings: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val projectDir = getProjectDirectory(projectId)

        var totalBytes = 0L
        val fileEntities = mutableListOf<ProjectFileEntity>()

        for ((relPath, content) in files) {
            val targetFile = File(projectDir, relPath)
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(content, Charsets.UTF_8)
            val byteCount = content.toByteArray(Charsets.UTF_8).size.toLong()
            totalBytes += byteCount

            fileEntities.add(
                ProjectFileEntity(
                    projectId = projectId,
                    relativePath = relPath,
                    content = content,
                    confidenceScore = scores[relPath] ?: 0.9f,
                    isIncluded = true,
                    inferredLanguage = inferLangFromExt(relPath),
                    warnings = warnings[relPath] ?: ""
                )
            )
        }

        val projectEntity = ProjectEntity(
            id = projectId,
            name = name,
            description = description,
            techStack = techStack,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            repoUrl = null,
            isUploaded = false,
            fileCount = files.size,
            totalBytes = totalBytes
        )

        projectDao.insertProject(projectEntity)
        projectDao.insertFiles(fileEntities)

        projectId
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val projectDir = File(projectsRoot, projectId)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
        projectDao.deleteFilesForProject(projectId)
        projectDao.deleteProjectById(projectId)
    }

    /**
     * تصدير المشروع كملف ZIP إلى مسار Uri محدد عبر SAF
     */
    suspend fun exportProjectToZipUri(projectId: String, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectsRoot, projectId)
            if (!projectDir.exists()) return@withContext false

            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                ZipOutputStream(outStream).use { zipOut ->
                    projectDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relPath = file.relativeTo(projectDir).path.replace('\\', '/')
                        zipOut.putNextEntry(ZipEntry(relPath))
                        file.inputStream().use { input -> input.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * تصدير المشروع إلى مجلد يختاره المستخدم عبر SAF
     */
    suspend fun exportProjectToFolder(projectId: String, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectsRoot, projectId)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            val projectEntity = projectDao.getProjectById(projectId)
            val folderName = projectEntity?.name ?: "LubProject_$projectId"
            val targetFolder = rootDoc.createDirectory(folderName) ?: return@withContext false

            val files = projectDao.getFilesForProject(projectId)
            for (f in files) {
                val segments = f.relativePath.split("/")
                var currentDir = targetFolder
                for (i in 0 until segments.size - 1) {
                    val seg = segments[i]
                    val existing = currentDir.findFile(seg)
                    currentDir = existing ?: currentDir.createDirectory(seg) ?: currentDir
                }
                val fileName = segments.last()
                val docFile = currentDir.createFile("text/plain", fileName)
                docFile?.uri?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(f.content.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun inferLangFromExt(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "js", "jsx", "mjs" -> "JavaScript"
            "ts", "tsx" -> "TypeScript"
            "py" -> "Python"
            "html", "htm" -> "HTML"
            "css", "scss" -> "CSS"
            "json" -> "JSON"
            "xml" -> "XML"
            "yaml", "yml" -> "YAML"
            "md" -> "Markdown"
            "rs" -> "Rust"
            "go" -> "Go"
            "sh", "bash" -> "Shell"
            "c", "cpp", "h" -> "C/C++"
            "sql" -> "SQL"
            else -> "Text"
        }
    }
}
