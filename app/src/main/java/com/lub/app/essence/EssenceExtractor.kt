package com.lub.app.essence

import android.util.Base64
import com.lub.app.capture.CapturedSegment
import com.lub.app.parser.BlockParser
import com.lub.app.parser.ConfidenceScorer
import com.lub.app.parser.FilenameInference
import com.lub.app.parser.InferenceSource
import com.lub.app.parser.PathSanitizer
import com.lub.app.parser.StackDetector
import com.lub.app.parser.TreeParser
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object EssenceExtractor {

    data class ExtractedFile(
        val filePath: String,
        val content: String,
        val language: String,
        val confidence: Float,
        val warnings: List<String>
    )

    data class ExtractionResult(
        val extractedFiles: List<ExtractedFile>,
        val techStack: String
    )

    /**
     * استخلاص الملفات النقية من مقاطع الشاشة الملتقطة حرفياً دون أي تعديل على بنية الكود
     */
    fun extract(segments: List<CapturedSegment>): ExtractionResult {
        // First check for FITNA-IMPORT payload in any segment
        for (seg in segments) {
            val fitna = parseFitnaPayload(seg.text)
            if (fitna != null && fitna.isNotEmpty()) {
                val stack = StackDetector.detect(fitna.map { it.filePath }, fitna.associate { it.filePath to it.content })
                return ExtractionResult(fitna, stack)
            }
        }

        // 1. Filter noise and classify segments
        var lastHint = ""
        var candidateTreePaths = emptyList<String>()

        val rawBlocks = mutableListOf<RawBlockWithContext>()

        for (seg in segments) {
            val cleanText = NoiseFilter.cleanSegment(seg.text)
            if (cleanText.isBlank()) continue

            // Check if segment is an ASCII directory tree
            if (TreeParser.isTreeBlock(cleanText)) {
                candidateTreePaths = TreeParser.parseTreePaths(cleanText)
                continue
            }

            // Check for Markdown blocks or separators within the segment
            val parsedBlocks = BlockParser.parseRawBlocks(cleanText)
            if (parsedBlocks.isNotEmpty()) {
                for (b in parsedBlocks) {
                    rawBlocks.add(
                        RawBlockWithContext(
                            header = b.header,
                            language = b.language,
                            content = b.content,
                            hint = b.hint ?: lastHint
                        )
                    )
                }
                lastHint = ""
                continue
            }

            val classification = SegmentClassifier.classify(seg)
            when (classification) {
                SegmentType.FILE_NAME_HINT -> {
                    lastHint = cleanText.take(160)
                }
                SegmentType.EXPLANATION -> {
                    // Keep last 240 chars of explanation as hint for next block
                    lastHint = cleanText.takeLast(240)
                }
                SegmentType.CODE -> {
                    rawBlocks.add(
                        RawBlockWithContext(
                            header = null,
                            language = "auto",
                            content = cleanText,
                            hint = lastHint
                        )
                    )
                    lastHint = ""
                }
                SegmentType.NOISE -> {}
            }
        }

        // 2. Process blocks: strip line numbers, infer filename, score confidence
        val filesByPath = linkedMapOf<String, ExtractedFile>()
        var unnamedIndex = 1

        for (block in rawBlocks) {
            // Strip sequential line numbers if present
            val (codeWithoutLineNums, hadLineNums) = LineNumberStripper.stripLineNumbers(block.content)

            val firstLine = codeWithoutLineNums.lines().firstOrNull() ?: ""
            val inferred = FilenameInference.inferFilename(
                header = block.header,
                firstLine = firstLine,
                hint = block.hint,
                language = block.language,
                candidateTreePaths = candidateTreePaths,
                index = unnamedIndex
            )

            if (inferred.source == InferenceSource.DEFAULT_FALLBACK) {
                unnamedIndex++
            }

            val (score, warnings) = ConfidenceScorer.evaluate(
                filePath = inferred.filePath,
                content = codeWithoutLineNums,
                inferenceSource = inferred.source
            )

            val allWarnings = warnings.toMutableList()
            if (hadLineNums) {
                allWarnings.add("تمت إزالة أرقام الأسطر المتسلسلة تلقائياً")
            }
            if (inferred.warning != null) {
                allWarnings.add(inferred.warning)
            }

            // Rule: "آخر نسخة" - overwrite with latest occurrence if the same file is repeated
            filesByPath[inferred.filePath] = ExtractedFile(
                filePath = inferred.filePath,
                content = codeWithoutLineNums,
                language = block.language,
                confidence = score,
                warnings = allWarnings
            )
        }

        // Apply path sanitation and strip wrapping common root
        val sanitizedPaths = PathSanitizer.stripCommonRootFolder(filesByPath.keys.toList())
        val finalFiles = filesByPath.values.mapIndexed { index, file ->
            val cleanPath = sanitizedPaths.getOrElse(index) { file.filePath }
            file.copy(filePath = cleanPath)
        }

        val stack = StackDetector.detect(
            finalFiles.map { it.filePath },
            finalFiles.associate { it.filePath to it.content }
        )

        return ExtractionResult(finalFiles, stack)
    }

    /**
     * دعم حمولة سحّاب الفطنة:
     * FITNA-IMPORT:F0.<base64> (بلا ضغط)
     * FITNA-IMPORT:F1.<base64> (gzip)
     * JSON: {"blocks":[{"lang":"...","hdr":"...","hint":"...","code":"..."}]}
     */
    fun parseFitnaPayload(text: String): List<ExtractedFile>? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("FITNA-IMPORT:")) return null

        try {
            val payload = trimmed.removePrefix("FITNA-IMPORT:").trim()
            val isGzip = payload.startsWith("F1.")
            val rawB64 = payload.removePrefix("F1.").removePrefix("F0.")

            val jsonString = if (isGzip) {
                val compressedBytes = Base64.decode(rawB64, Base64.URL_SAFE or Base64.NO_WRAP)
                GZIPInputStream(ByteArrayInputStream(compressedBytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val decodedBytes = Base64.decode(rawB64, Base64.URL_SAFE or Base64.NO_WRAP)
                String(decodedBytes, Charsets.UTF_8)
            }

            val jsonRoot = JSONObject(jsonString)
            val blocksArray = jsonRoot.getJSONArray("blocks")
            val result = mutableListOf<ExtractedFile>()

            for (i in 0 until blocksArray.length()) {
                val b = blocksArray.getJSONObject(i)
                val lang = b.optString("lang", "text")
                val hdr = b.optString("hdr", "")
                val hint = b.optString("hint", "")
                val code = b.getString("code")

                val inferred = FilenameInference.inferFilename(
                    header = if (hdr.isNotEmpty()) hdr else null,
                    firstLine = code.lines().firstOrNull(),
                    hint = if (hint.isNotEmpty()) hint else null,
                    language = lang,
                    index = i + 1
                )

                val (score, warnings) = ConfidenceScorer.evaluate(inferred.filePath, code, inferred.source)

                result.add(
                    ExtractedFile(
                        filePath = inferred.filePath,
                        content = code,
                        language = lang,
                        confidence = score,
                        warnings = warnings
                    )
                )
            }
            return result
        } catch (e: Exception) {
            return null
        }
    }

    private data class RawBlockWithContext(
        val header: String?,
        val language: String,
        val content: String,
        val hint: String?
    )
}
