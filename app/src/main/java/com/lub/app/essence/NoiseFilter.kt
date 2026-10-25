package com.lub.app.essence

/**
 * فلترة كلمات الواجهة الشائعة وأزرار المنصات (Copy, Run, Share, نسخ...)
 */
object NoiseFilter {

    private val NOISE_WORDS = setOf(
        "copy", "copied", "copy code", "download", "run", "edit", "retry", "share",
        "نسخ", "تم النسخ", "نسخ الكود", "تحميل", "تنزيل", "تشغيل", "تعديل", "إعادة", "مشاركة",
        "raw", "blame", "history", "expand", "collapse", "preview", "code", "terminal",
        "bash", "powershell", "output", "result", "مخرجات", "النتيجة"
    )

    fun isNoise(text: String): Boolean {
        val clean = text.trim().lowercase()
        return clean in NOISE_WORDS || (clean.length < 25 && NOISE_WORDS.any { it == clean })
    }

    fun cleanSegment(text: String): String {
        val lines = text.split('\n')
        val filtered = lines.filterNot { isNoise(it) }
        return filtered.joinToString("\n")
    }
}
