package com.lub.app.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lub.app.parser.PathSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedFolderResult(
    val folderName: String,
    val files: List<ScannedFileItem>,
    val totalBytes: Long
)

data class ScannedFileItem(
    val relativePath: String,
    val uri: Uri,
    val sizeBytes: Long,
    val isText: Boolean
)

object FolderScanner {

    suspend fun scanTree(
        context: Context,
        treeUri: Uri,
        onProgress: (scannedCount: Int, totalBytes: Long) -> Unit
    ): ScannedFolderResult = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("تعذر فتح المجلد المختار عبر مدير الملفات")

        val rootName = rootDoc.name ?: "ProjectFolder"
        val scannedFiles = mutableListOf<ScannedFileItem>()
        var totalBytes = 0L

        fun scanRecursive(doc: DocumentFile, currentPath: String) {
            val list = doc.listFiles()
            for (child in list) {
                val childName = child.name ?: continue
                val relPath = if (currentPath.isEmpty()) childName else "$currentPath/$childName"

                if (IgnoreRules.shouldIgnore(relPath)) {
                    continue
                }

                if (child.isDirectory) {
                    scanRecursive(child, relPath)
                } else if (child.isFile) {
                    val size = child.length()
                    totalBytes += size
                    val isTextFile = isLikelyText(childName, child.type)

                    scannedFiles.add(
                        ScannedFileItem(
                            relativePath = PathSanitizer.sanitize(relPath),
                            uri = child.uri,
                            sizeBytes = size,
                            isText = isTextFile
                        )
                    )

                    if (scannedFiles.size % 20 == 0) {
                        onProgress(scannedFiles.size, totalBytes)
                    }
                }
            }
        }

        scanRecursive(rootDoc, "")
        onProgress(scannedFiles.size, totalBytes)

        ScannedFolderResult(
            folderName = rootName,
            files = scannedFiles,
            totalBytes = totalBytes
        )
    }

    private fun isLikelyText(name: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("text/") == true) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "html", "css", "scss",
            "json", "xml", "yaml", "yml", "md", "rs", "go", "sh", "c", "cpp", "h", "sql",
            "gradle", "properties", "env", "gitignore", "txt", "conf", "toml"
        )
    }
}
