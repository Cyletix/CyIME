package com.kingzcheung.xime.ui.caption

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import com.kingzcheung.xime.platform.AndroidPlatform

/** Quick-settings tile: pull down the shade, tap "字幕", the caption page opens. */
class CaptionTileService : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = Intent(this, CaptionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (AndroidPlatform.tilePendingIntent) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }
}
