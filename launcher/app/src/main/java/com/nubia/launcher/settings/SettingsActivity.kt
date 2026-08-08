package com.nubia.launcher.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.nubia.launcher.BuildConfig
import com.nubia.launcher.LauncherApplication
import com.nubia.launcher.R
import com.nubia.launcher.data.WallpaperStore
import com.nubia.launcher.databinding.ActivitySettingsBinding
import com.nubia.launcher.notification.QuickSettingsActivity
import com.nubia.launcher.theme.ThemeManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Schermata di personalizzazione del launcher. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as LauncherApplication
        ThemeManager.apply(this, app.settings.get())
        super.onCreate(savedInstanceState)

        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, SettingsFragment())
                .commit()
        }

        var lastThemeKey: Pair<Int, Int>? = null
        lifecycleScope.launch {
            app.settings.settings.collect { s ->
                val key = s.darkMode to s.accent
                if (lastThemeKey != null && lastThemeKey != key) {
                    lastThemeKey = key
                    recreate()
                } else {
                    lastThemeKey = key
                }
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val context = context ?: return@registerForActivityResult
                if (WallpaperStore.save(context, uri)) {
                    (requireActivity().application as LauncherApplication)
                        .settings.setCustomWallpaper(WallpaperStore.path(context))
                    Toast.makeText(context, R.string.toast_wallpaper_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.toast_wallpaper_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>("about_version")?.summary =
                "${getString(R.string.pref_about_build)} · v${BuildConfig.VERSION_NAME}"
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            when (preference.key) {
                "wallpaper" -> {
                    startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
                    return true
                }
                "custom_wallpaper" -> {
                    wallpaperPicker.launch("image/*")
                    return true
                }
                "remove_wallpaper" -> {
                    val context = context
                    if (context != null) {
                        WallpaperStore.clear(context)
                        (requireActivity().application as LauncherApplication)
                            .settings.setCustomWallpaper("")
                        Toast.makeText(context, R.string.toast_wallpaper_removed, Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                "notif_access" -> {
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    return true
                }
                "qs_order" -> {
                    startActivity(Intent(requireContext(), QuickSettingsActivity::class.java))
                    return true
                }
            }
            return super.onPreferenceTreeClick(preference)
        }
    }
}
