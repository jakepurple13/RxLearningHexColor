package com.programmersbox.hexcolor

import android.app.Application
import com.programmersbox.loggingutils.Loged

class ColorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Loged.FILTER_BY_PACKAGE_NAME = "programmersbox"
    }
}