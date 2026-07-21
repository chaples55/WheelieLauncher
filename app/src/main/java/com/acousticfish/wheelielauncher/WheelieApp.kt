package com.acousticfish.wheelielauncher

import android.app.Application
import com.acousticfish.wheelielauncher.data.AppContainer

class WheelieApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
