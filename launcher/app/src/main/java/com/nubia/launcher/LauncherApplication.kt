package com.nubia.launcher

import android.app.Application
import com.nubia.launcher.data.AppManager
import com.nubia.launcher.data.SettingsStore

class LauncherApplication : Application() {

    lateinit var settings: SettingsStore
        private set

    lateinit var appManager: AppManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        appManager = AppManager(this)
    }
}
