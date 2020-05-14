package com.programmersbox.hexcolor

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import com.programmersbox.helpfulutils.sendNotification
import kotlin.random.Random

class ColorTileService : TileService() {

    override fun onClick() {
        super.onClick()

        startActivityAndCollapse(Intent(this@ColorTileService, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        })

        sendNotification(Random.nextInt(1, 20), "hexcolor", R.mipmap.ic_launcher) {
            title = "Hex Color"
            message = "On the go!"
            pendingIntent(MainActivity::class.java)
            addBubble {
                val target = Intent(this@ColorTileService, MainActivity::class.java)
                val bubbleIntent = PendingIntent.getActivity(this@ColorTileService, 0, target, 0 /* flags */)
                bubbleIntent(bubbleIntent)
                desiredHeight = 600
                icon = Icon.createWithResource(this@ColorTileService, R.mipmap.ic_launcher)
            }
        }
    }
}