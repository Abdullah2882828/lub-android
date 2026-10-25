package com.lub.app.archive

import com.lub.app.parser.PathSanitizer
import java.io.File

object ZipSafety {

    const val MAX_ENTRIES = 100_000
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 4L * 1024L * 1024L * 1024L // 4 GB
    const val MAX_COMPRESSION_RATIO = 100 // Zip bomb indicator

    /**
     * التحقق من سلامة مسار الملف المستخرج لمنع هجمات Zip Slip
     */
    fun validateAndResolveTarget(targetDir: File, rawEntryName: String): File {
        val sanitized = PathSanitizer.sanitize(rawEntryName)
        val targetFile = File(targetDir, sanitized)

        // Ensure canonical path is strictly inside target directory
        val canonicalDest = targetDir.canonicalPath
        val canonicalTarget = targetFile.canonicalPath

        if (!canonicalTarget.startsWith(canonicalDest)) {
            throw SecurityException("محاولة اختراق مسار (Zip Slip): المسار $rawEntryName يقع خارج المجلد الهدف")
        }

        return targetFile
    }

    /**
     * فحص مؤشرات قنابل الأرشيف (Zip Bomb)
     */
    fun checkZipBomb(entryCompressedSize: Long, entryUncompressedSize: Long, cumulativeBytes: Long) {
        if (cumulativeBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw SecurityException("الأرشيف يتجاوز الحد الأقصى المسموح للحجم بعد فك الضغط (4GB)")
        }

        if (entryCompressedSize > 0 && entryUncompressedSize > 1024 * 1024) {
            val ratio = entryUncompressedSize / entryCompressedSize
            if (ratio > MAX_COMPRESSION_RATIO) {
                throw SecurityException("نسبة ضغط مشبوهة جداً (احتمال Zip Bomb) في الملف")
            }
        }
    }
}
