package com.lub.app.archive

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractedArchiveResult(
    val extractedFolder: File,
    val extractedCount: Int,
    val totalBytes: Long,
    val warnings: List<String>
)

object ArchiveExtractor {

    private val ARABIC_CHARSET = Charset.forName("windows-1256")
    private val IBM437_CHARSET = Charset.forName("IBM437")

    suspend fun extractArchive(
        context: Context,
        archiveUri: Uri,
        targetDir: File,
        onProgress: (extractedFiles: Int, bytesWritten: Long) -> Unit
    ): ExtractedArchiveResult = withContext(Dispatchers.IO) {
        val fileName = getUriFileName(context, archiveUri)
        val ext = fileName.lowercase()

        if (ext.endsWith(".rar")) {
            throw IllegalArgumentException("صيغة RAR غير مدعومة حالياً. يرجى تحويل الأرشيف إلى ZIP أو 7z")
        }

        targetDir.mkdirs()
        var extractedCount = 0
        var totalBytes = 0L
        val warnings = mutableListOf<String>()

        when {
            ext.endsWith(".zip") -> {
                val (count, bytes) = extractZip(context, archiveUri, targetDir, onProgress)
                extractedCount = count
                totalBytes = bytes
            }
            ext.endsWith(".tar.gz") || ext.endsWith(".tgz") -> {
                context.contentResolver.openInputStream(archiveUri)?.use { input ->
                    val gzIn = GzipCompressorInputStream(BufferedInputStream(input))
                    val tarIn = TarArchiveInputStream(gzIn)
                    val (count, bytes) = extractTarStream(tarIn, targetDir, onProgress)
                    extractedCount = count
                    totalBytes = bytes
                }
            }
            ext.endsWith(".tar.bz2") || ext.endsWith(".tbz2") -> {
                context.contentResolver.openInputStream(archiveUri)?.use { input ->
                    val bzIn = BZip2CompressorInputStream(BufferedInputStream(input))
                    val tarIn = TarArchiveInputStream(bzIn)
                    val (count, bytes) = extractTarStream(tarIn, targetDir, onProgress)
                    extractedCount = count
                    totalBytes = bytes
                }
            }
            ext.endsWith(".tar") -> {
                context.contentResolver.openInputStream(archiveUri)?.use { input ->
                    val tarIn = TarArchiveInputStream(BufferedInputStream(input))
                    val (count, bytes) = extractTarStream(tarIn, targetDir, onProgress)
                    extractedCount = count
                    totalBytes = bytes
                }
            }
            ext.endsWith(".7z") -> {
                // 7z requires random-access file, copy to cache temp file
                val tempFile = File(context.cacheDir, "temp_extract.7z")
                try {
                    context.contentResolver.openInputStream(archiveUri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val (count, bytes) = extract7z(tempFile, targetDir, onProgress)
                    extractedCount = count
                    totalBytes = bytes
                } finally {
                    tempFile.delete()
                }
            }
            ext.endsWith(".gz") -> {
                // Single gzipped file
                context.contentResolver.openInputStream(archiveUri)?.use { input ->
                    val gzIn = GzipCompressorInputStream(BufferedInputStream(input))
                    val outName = fileName.removeSuffix(".gz")
                    val outFile = ZipSafety.validateAndResolveTarget(targetDir, outName)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        val bytes = gzIn.copyTo(out)
                        extractedCount = 1
                        totalBytes = bytes
                    }
                }
            }
            else -> {
                // Default try ZIP
                val (count, bytes) = extractZip(context, archiveUri, targetDir, onProgress)
                extractedCount = count
                totalBytes = bytes
            }
        }

        ExtractedArchiveResult(
            extractedFolder = targetDir,
            extractedCount = extractedCount,
            totalBytes = totalBytes,
            warnings = warnings
        )
    }

    private fun extractZip(
        context: Context,
        uri: Uri,
        targetDir: File,
        onProgress: (Int, Long) -> Unit
    ): Pair<Int, Long> {
        var count = 0
        var totalBytes = 0L

        // Try UTF-8 first
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val zipIn = ZipInputStream(BufferedInputStream(stream), Charsets.UTF_8)
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                if (count >= ZipSafety.MAX_ENTRIES) {
                    throw SecurityException("تم تجاوز الحد الأقصى للملفات داخل الأرشيف (${ZipSafety.MAX_ENTRIES})")
                }

                if (!entry.isDirectory) {
                    val targetFile = ZipSafety.validateAndResolveTarget(targetDir, entry.name)
                    targetFile.parentFile?.mkdirs()

                    FileOutputStream(targetFile).use { out ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (zipIn.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            totalBytes += read
                            ZipSafety.checkZipBomb(entry.compressedSize, entry.size, totalBytes)
                        }
                    }
                    count++
                    if (count % 25 == 0) {
                        onProgress(count, totalBytes)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        return Pair(count, totalBytes)
    }

    private fun extractTarStream(
        tarIn: TarArchiveInputStream,
        targetDir: File,
        onProgress: (Int, Long) -> Unit
    ): Pair<Int, Long> {
        var count = 0
        var totalBytes = 0L
        var entry = tarIn.nextEntry

        while (entry != null) {
            if (count >= ZipSafety.MAX_ENTRIES) break

            if (!entry.isDirectory && !entry.isSymbolicLink) {
                val targetFile = ZipSafety.validateAndResolveTarget(targetDir, entry.name)
                targetFile.parentFile?.mkdirs()

                FileOutputStream(targetFile).use { out ->
                    val bytes = tarIn.copyTo(out)
                    totalBytes += bytes
                }
                count++
                if (count % 25 == 0) {
                    onProgress(count, totalBytes)
                }
            }
            entry = tarIn.nextEntry
        }

        return Pair(count, totalBytes)
    }

    private fun extract7z(
        file7z: File,
        targetDir: File,
        onProgress: (Int, Long) -> Unit
    ): Pair<Int, Long> {
        var count = 0
        var totalBytes = 0L

        SevenZFile.builder().setFile(file7z).get().use { sevenZFile ->
            var entry = sevenZFile.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                if (!entry.isDirectory) {
                    val targetFile = ZipSafety.validateAndResolveTarget(targetDir, entry.name)
                    targetFile.parentFile?.mkdirs()

                    FileOutputStream(targetFile).use { out ->
                        var read: Int
                        while (sevenZFile.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            totalBytes += read
                        }
                    }
                    count++
                    if (count % 25 == 0) {
                        onProgress(count, totalBytes)
                    }
                }
                entry = sevenZFile.nextEntry
            }
        }

        return Pair(count, totalBytes)
    }

    private fun getUriFileName(context: Context, uri: Uri): String {
        var name = "archive.zip"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    name = it.getString(idx)
                }
            }
        }
        return name
    }
}
