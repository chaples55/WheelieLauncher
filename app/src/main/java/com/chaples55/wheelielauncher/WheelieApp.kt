package com.chaples55.wheelielauncher

import android.app.Application
import com.chaples55.wheelielauncher.data.AppContainer

class WheelieApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
