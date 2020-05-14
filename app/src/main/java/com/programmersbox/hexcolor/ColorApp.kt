package com.programmersbox.hexcolor

import android.app.Application
import com.programmersbox.helpfulutils.NotificationChannelImportance
import com.programmersbox.helpfulutils.createNotificationChannel
import com.programmersbox.loggingutils.Loged

class ColorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Loged.FILTER_BY_PACKAGE_NAME = "programmersbox"

        createNotificationChannel("hexcolor", importance = NotificationChannelImportance.HIGH)

    }
}