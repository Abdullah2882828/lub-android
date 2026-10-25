package com.lub.app.capture

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lub.app.App
import com.lub.app.QuickPushActivity
import com.lub.app.data.BasketItemEntity
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * نشاط شفاف وسريع لقراءة الحافظة بأمان عند حصوله على التركيز طبقاً لقيود أندرويد 10+
 */
class ClipActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isFastPush = intent.getBooleanExtra(EXTRA_FAST_PUSH, false)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip

        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isNotBlank()) {
                val item = BasketItemEntity(
                    id = UUID.randomUUID().toString(),
                    source = "CLIPBOARD",
                    capturedAt = System.currentTimeMillis(),
                    header = "محتوى الحافظة (${text.take(30)}...)",
                    code = text,
                    language = "auto",
                    targetPath = null,
                    confidence = 1.0f,
                    isSelected = true
                )

                lifecycleScope.launch {
                    App.instance.database.basketDao().insertItem(item)
                    Toast.makeText(this@ClipActivity, "تم حفظ نص الحافظة في السلة 🧺", Toast.LENGTH_SHORT).show()

                    if (isFastPush) {
                        val pushIntent = Intent(this@ClipActivity, QuickPushActivity::class.java).apply {
                            putExtra(QuickPushActivity.EXTRA_RAW_TEXT, text)
                            putExtra(QuickPushActivity.EXTRA_SOURCE, "CLIPBOARD")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(pushIntent)
                    }
                    finish()
                }
                return
            }
        }

        Toast.makeText(this, "الحافظة فارغة أو لا تحتوي نصاً", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_FAST_PUSH = "extra_fast_push"
    }
}
