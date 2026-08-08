package com.nubia.launcher.notification

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

/**
 * Lettura/scrittura degli interruttori rapidi e degli slider del pannello.
 * Tutte le operazioni sono difensive: se il sistema rifiuta l'azione,
 * si apre il pannello di sistema corrispondente come fallback.
 */
object QuickToggleActions {

    fun isOn(context: Context, id: String): Boolean = when (id) {
        "wifi" -> wifiOn(context)
        "bluetooth" -> bluetoothOn(context)
        "dnd" -> dndOn(context)
        "flashlight" -> flashlightOn(context)
        else -> false
    }

    fun toggle(context: Context, id: String): Boolean = when (id) {
        "wifi" -> toggleWifi(context)
        "bluetooth" -> toggleBluetooth(context)
        "dnd" -> toggleDnd(context)
        "flashlight" -> toggleFlashlight(context)
        else -> false
    }

    // ------------------------------------------------------------- Wi-Fi

    fun wifiOn(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        return try {
            wm.isWifiEnabled
        } catch (_: Exception) {
            false
        }
    }

    private fun toggleWifi(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        return try {
            @Suppress("DEPRECATION")
            val ok = wm.setWifiEnabled(!wifiOn(context))
            if (!ok) openWirelessSettings(context)
            ok
        } catch (_: Exception) {
            openWirelessSettings(context)
            false
        }
    }

    // --------------------------------------------------------- Bluetooth

    fun bluetoothOn(context: Context): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.isEnabled
        } catch (_: Exception) {
            false
        }
    }

    private fun toggleBluetooth(context: Context): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return try {
            @Suppress("DEPRECATION")
            val target = !adapter.isEnabled
            val ok = if (target) adapter.enable() else adapter.disable()
            if (!ok) openBluetoothSettings(context)
            ok
        } catch (_: Exception) {
            openBluetoothSettings(context)
            false
        }
    }

    // --------------------------------------------------- Non disturbare

    fun dndOn(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager ?: return false
        return try {
            nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE
        } catch (_: Exception) {
            false
        }
    }

    private fun toggleDnd(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager ?: return false
        return try {
            val target = if (dndOn(context)) {
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                android.app.NotificationManager.INTERRUPTION_FILTER_NONE
            }
            nm.setInterruptionFilter(target)
            true
        } catch (_: Exception) {
            openPolicyAccess(context)
            false
        }
    }

    // ----------------------------------------------------------- Torcia

    @Volatile
    private var torchState = false

    fun flashlightOn(context: Context): Boolean = torchState

    private fun toggleFlashlight(context: Context): Boolean {
        val cm = cameraManager(context) ?: return false
        return try {
            val id = cm.cameraIdList.firstOrNull() ?: return false
            torchState = !torchState
            cm.setTorchMode(id, torchState)
            true
        } catch (_: Exception) {
            torchState = false
            false
        }
    }

    private fun cameraManager(context: Context): CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    // ---------------------------------------------- Slider: luminosità

    fun brightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (_: Exception) {
            128
        }
    }

    fun setBrightness(context: Context, value: Int): Boolean {
        if (!Settings.System.canWrite(context)) {
            openWriteSettings(context)
            return false
        }
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        } catch (_: Exception) {
            false
        }
    }

    // --------------------------------------------------- Slider: volume

    fun maxVolume(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 15
        return am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    fun volume(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
        return am.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun setVolume(context: Context, value: Int): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    // --------------------------------------------------------- fallback

    private fun openWirelessSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun openBluetoothSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun openPolicyAccess(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun openWriteSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
        } catch (_: Exception) {
        }
    }
}
