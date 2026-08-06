package com.nubia.launcher.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.nubia.launcher.BuildConfig
import com.nubia.launcher.LauncherApplication
import com.nubia.launcher.R
import com.nubia.launcher.databinding.ActivitySettingsBinding
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

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>("about_version")?.summary =
                "${getString(R.string.pref_about_build)} · v${BuildConfig.VERSION_NAME}"
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference.key == "wallpaper") {
                startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
                return true
            }
            return super.onPreferenceTreeClick(preference)
        }
    }
}
