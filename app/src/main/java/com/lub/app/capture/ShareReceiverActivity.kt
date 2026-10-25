package com.lub.app.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lub.app.App
import com.lub.app.MainActivity
import com.lub.app.QuickPushActivity
import com.lub.app.data.BasketItemEntity
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * استقبال المحتوى المشارك من التطبيقات الأخرى (نصوص، ملفات، أرشيفات مضغوطة)
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        val type = intent.type

        when (action) {
            Intent.ACTION_SEND -> {
                if (type?.startsWith("text/") == true) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    handleIncomingText(sharedText)
                } else {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) {
                        handleIncomingUri(uri)
                    } else {
                        finish()
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    handleIncomingMultipleUris(uris)
                } else {
                    finish()
                }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
                handleIncomingText(text)
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    handleIncomingUri(uri)
                } else {
                    finish()
                }
            }
            else -> finish()
        }
    }

    private fun handleIncomingText(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "النص المشارك فارغ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val item = BasketItemEntity(
                id = UUID.randomUUID().toString(),
                source = "SHARE",
                capturedAt = System.currentTimeMillis(),
                header = "مشاركة نصية (${text.take(30)}...)",
                code = text,
                language = "auto",
                targetPath = null,
                confidence = 1.0f,
                isSelected = true
            )
            App.instance.database.basketDao().insertItem(item)
            Toast.makeText(this@ShareReceiverActivity, "تمت إضافة النص المشارك للسلة 🧺", Toast.LENGTH_SHORT).show()

            val openIntent = Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(openIntent)
            finish()
        }
    }

    private fun handleIncomingUri(uri: Uri) {
        // Open Lub with archive import target
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_ARCHIVE_URI", uri.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(openIntent)
        finish()
    }

    private fun handleIncomingMultipleUris(uris: List<Uri>) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putParcelableArrayListExtra("EXTRA_MULTIPLE_URIS", ArrayList(uris))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(openIntent)
        finish()
    }
}
