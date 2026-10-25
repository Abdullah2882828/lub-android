package com.lub.app.parser

/**
 * تطبيع مسارات الملفات وحمايتها من الثغرات الأمنية (Zip Slip, Path Traversal)
 * وحذف الأحرف غير الصالحة والمسارات المطلقة
 */
object PathSanitizer {

    private const val MAX_PATH_LENGTH = 200

    fun sanitize(rawPath: String): String {
        var p = rawPath.trim()

        // Replace backslashes with standard forward slashes
        p = p.replace('\\', '/')

        // Remove drive letters like C:/ or D:/
        p = p.replace(Regex("""^[a-zA-Z]:[/]+"""), "")

        // Remove leading slashes and ./
        p = p.trimStart('/')
        while (p.startsWith("./")) {
            p = p.substring(2)
        }

        // Split into segments and filter path traversal
        val rawSegments = p.split('/')
        val safeSegments = mutableListOf<String>()

        for (seg in rawSegments) {
            val cleanSeg = seg.filter { it >= ' ' && it != ':' && it != '*' && it != '?' && it != '"' && it != '<' && it != '>' && it != '|' }
                .trim()
            if (cleanSeg.isEmpty() || cleanSeg == ".") {
                continue
            }
            if (cleanSeg == "..") {
                // Prevent traversing outside root
                if (safeSegments.isNotEmpty()) {
                    safeSegments.removeAt(safeSegments.size - 1)
                }
                continue
            }
            safeSegments.add(cleanSeg)
        }

        var result = safeSegments.joinToString("/")

        // Limit length
        if (result.length > MAX_PATH_LENGTH) {
            val ext = result.substringAfterLast('.', "")
            val name = result.substringBeforeLast('.')
            val maxNameLen = MAX_PATH_LENGTH - ext.length - 1
            result = "${name.take(maxNameLen)}.$ext"
        }

        return if (result.isBlank()) "unnamed_file.txt" else result
    }

    /**
     * إزالة المجلد الحاوي المغلّف إذا كانت جميع الملفات تشترك في نفس المجلد الأب الأول
     */
    fun stripCommonRootFolder(paths: List<String>): List<String> {
        if (paths.size < 2) return paths
        val firstSegments = paths.map { it.split('/').firstOrNull() ?: "" }
        val commonRoot = firstSegments.first()
        if (commonRoot.isNotEmpty() && firstSegments.all { it == commonRoot }) {
            return paths.map { path ->
                if (path.startsWith("$commonRoot/")) path.substring(commonRoot.length + 1) else path
            }
        }
        return paths
    }
}
