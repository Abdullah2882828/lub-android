package com.lub.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.lub.app.data.AppDatabase
import com.lub.app.data.ProjectStore
import com.lub.app.data.SettingsStore

/**
 * فئة التطبيق الرئيسية لـ "لُب"
 * تهيئة قاعدة البيانات والتخزين وقنوات الإشعارات
 */
class App : Application() {

    companion object {
        const val CHANNEL_ID_CAPTURE = "lub_capture_channel"
        const val CHANNEL_ID_UPLOAD = "lub_upload_channel"
        lateinit var instance: App
            private set
    }

    val database by lazy { AppDatabase.getDatabase(this) }
    val projectStore by lazy { ProjectStore(this, database.projectDao()) }
    val settingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val captureChannel = NotificationChannel(
                CHANNEL_ID_CAPTURE,
                "إشعارات الالتقاط والحافظة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات عند التقاط المحتوى أو طلب قراءة الحافظة"
            }

            val uploadChannel = NotificationChannel(
                CHANNEL_ID_UPLOAD,
                "إشعارات الرفع إلى GitHub",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "عرض شريط تقدم رفع الملفات إلى المستودع"
            }

            notificationManager.createNotificationChannel(captureChannel)
            notificationManager.createNotificationChannel(uploadChannel)
        }
    }
}
