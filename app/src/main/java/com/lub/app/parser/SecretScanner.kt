package com.lub.app.parser

data class SecretFinding(
    val filePath: String,
    val secretType: String,
    val linePreview: String,
    val isCritical: Boolean
)

/**
 * فحص الملفات والأكواد للتأكد من خلوها من المفاتيح السرية والتوكنات وملفات .env قبل الرفع
 */
object SecretScanner {

    private val GITHUB_PAT_REGEX = Regex("""\b(?:ghp|gho|ghu|ghs|ghr)_[a-zA-Z0-9]{36,255}\b""")
    private val GOOGLE_API_KEY_REGEX = Regex("""\bAIzaSy[a-zA-Z0-9_\-]{33}\b""")
    private val OPENAI_KEY_REGEX = Regex("""\bsk-(?:proj-)?[a-zA-Z0-9]{20,}\b""")
    private val AWS_KEY_REGEX = Regex("""\bAKIA[0-9A-Z]{16}\b""")
    private val GENERIC_SECRET_REGEX = Regex("""(?i)(?:api_key|apikey|secret|password|token)\s*[:=]\s*["']([a-zA-Z0-9_\-]{16,})["']""")

    fun scan(files: List<Pair<String, String>>): List<SecretFinding> {
        val findings = mutableListOf<SecretFinding>()

        for ((path, content) in files) {
            val fileName = path.substringAfterLast('/')

            // 1. Check if file is .env
            if (fileName.equals(".env", ignoreCase = true) || fileName.startsWith(".env.")) {
                findings.add(
                    SecretFinding(
                        filePath = path,
                        secretType = "ملف بيئة حساس (.env)",
                        linePreview = "ملف يحتوي غالباً على متغيرات سرية وقيم محلية",
                        isCritical = true
                    )
                )
            }

            // 2. Scan content lines
            val lines = content.split('\n')
            for (line in lines) {
                if (GITHUB_PAT_REGEX.containsMatchIn(line)) {
                    findings.add(
                        SecretFinding(
                            filePath = path,
                            secretType = "توكن GitHub سري",
                            linePreview = maskLine(line),
                            isCritical = true
                        )
                    )
                } else if (GOOGLE_API_KEY_REGEX.containsMatchIn(line)) {
                    findings.add(
                        SecretFinding(
                            filePath = path,
                            secretType = "مفتاح Google / Gemini API",
                            linePreview = maskLine(line),
                            isCritical = true
                        )
                    )
                } else if (OPENAI_KEY_REGEX.containsMatchIn(line)) {
                    findings.add(
                        SecretFinding(
                            filePath = path,
                            secretType = "مفتاح OpenAI / AI API",
                            linePreview = maskLine(line),
                            isCritical = true
                        )
                    )
                } else if (AWS_KEY_REGEX.containsMatchIn(line)) {
                    findings.add(
                        SecretFinding(
                            filePath = path,
                            secretType = "مفتاح وصول AWS",
                            linePreview = maskLine(line),
                            isCritical = true
                        )
                    )
                } else if (GENERIC_SECRET_REGEX.containsMatchIn(line)) {
                    findings.add(
                        SecretFinding(
                            filePath = path,
                            secretType = "مفتاح أو رمز مرور مبرمج مباشرة",
                            linePreview = maskLine(line),
                            isCritical = false
                        )
                    )
                }
            }
        }

        return findings
    }

    private fun maskLine(line: String): String {
        val trimmed = line.trim()
        return if (trimmed.length > 50) {
            "${trimmed.take(20)}***${trimmed.takeLast(10)}"
        } else {
            trimmed
        }
    }
}
