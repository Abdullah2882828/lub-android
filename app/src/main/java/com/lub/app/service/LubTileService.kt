package com.lub.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lub.app.QuickPushActivity

/**
 * زر الإعدادات السريعة (Quick Settings Tile): "لُب: التقط وارفع"
 */
class LubTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val isServiceActive = LubAccessibilityService.isServiceRunning()
        tile.state = if (isServiceActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "لُب: التقط وارفع"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, QuickPushActivity::class.java).apply {
            putExtra(QuickPushActivity.EXTRA_SOURCE, "TILE")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
