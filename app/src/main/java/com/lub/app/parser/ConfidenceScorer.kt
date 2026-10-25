package com.lub.app.parser

/**
 * تقييم سلامة الكود واكتماله واستخراج التحذيرات المتعلقة بالتنسيق أو الأجزاء المبتورة
 */
object ConfidenceScorer {

    fun evaluate(filePath: String, content: String, inferenceSource: InferenceSource): Pair<Float, List<String>> {
        val warnings = mutableListOf<String>()
        var score = when (inferenceSource) {
            InferenceSource.HEADER -> 0.95f
            InferenceSource.FIRST_LINE_COMMENT -> 0.92f
            InferenceSource.PREVIOUS_HINT -> 0.85f
            InferenceSource.TREE_MATCH -> 0.75f
            InferenceSource.DEFAULT_FALLBACK -> 0.40f
        }

        val lines = content.split('\n')
        val ext = filePath.substringAfterLast('.', "").lowercase()

        // 1. Check for abrupt truncated code or ellipsis placeholders
        if (content.contains("...") || content.contains("// rest of code") || content.contains("# rest of code") || content.contains("/* rest of code */")) {
            score -= 0.20f
            warnings.add("يحتوي الكود على نقاط حذف (...) أو عبارات تشير إلى كود غير مكتمل")
        }

        // 2. Bracket balance check for C/JS/Kotlin/Java/Rust
        if (ext in setOf("js", "ts", "jsx", "tsx", "kt", "java", "rs", "c", "cpp", "json")) {
            val openBraces = content.count { it == '{' }
            val closeBraces = content.count { it == '}' }
            if (openBraces != closeBraces) {
                score -= 0.15f
                warnings.add("عدم تطابق في إغلاق الأقواس المعقوفة { } ($openBraces مفتوح مقابل $closeBraces مغلق)")
            }
        }

        // 3. Indentation loss warning for Python / YAML
        if (ext in setOf("py", "yaml", "yml")) {
            val indentedLines = lines.count { it.startsWith("  ") || it.startsWith("\t") }
            if (lines.size > 5 && indentedLines == 0) {
                score -= 0.35f
                warnings.add("غالباً فُقدت المسافات البادئة في لغة تعتمد على الإزاحة. يُنصح باستعمال التقاط الحافظة")
            }
        }

        // 4. JSON validity check
        if (ext == "json") {
            try {
                org.json.JSONObject(content)
            } catch (e: Exception) {
                try {
                    org.json.JSONArray(content)
                } catch (e2: Exception) {
                    score -= 0.25f
                    warnings.add("تنسيق JSON غير صالح أو به خطأ تركيبي")
                }
            }
        }

        val finalScore = score.coerceIn(0.1f, 1.0f)
        return Pair(finalScore, warnings)
    }
}
