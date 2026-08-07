package com.nubia.launcher

import android.app.Application
import com.nubia.launcher.data.AppManager
import com.nubia.launcher.data.SettingsStore
import java.io.File
import java.io.PrintWriter

class LauncherApplication : Application() {

    lateinit var settings: SettingsStore
        private set

    lateinit var appManager: AppManager
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashCatcher()
        settings = SettingsStore(this)
        appManager = AppManager(this)
    }

    /** Salva lo stack trace dei crash in crash.log per poterli leggere senza PC. */
    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                PrintWriter(File(dir, "crash.log")).use { writer ->
                    writer.println("=== Crash ${System.currentTimeMillis()} ===")
                    throwable.printStackTrace(writer)
                }
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
