package com.nubia.launcher.home

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.nubia.launcher.LauncherApplication
import com.nubia.launcher.R
import com.nubia.launcher.databinding.ActivityLauncherBinding
import com.nubia.launcher.data.AppManager
import com.nubia.launcher.data.LauncherSettings
import com.nubia.launcher.data.SettingsStore
import com.nubia.launcher.home.dock.DockView
import com.nubia.launcher.home.drawer.AllAppsFragment
import com.nubia.launcher.home.gesture.GestureController
import com.nubia.launcher.home.workspace.toHomeScreenConfig
import com.nubia.launcher.model.AppInfo
import com.nubia.launcher.model.HomeItem
import com.nubia.launcher.settings.SettingsActivity
import com.nubia.launcher.theme.ThemeManager
import com.nubia.launcher.util.ShortcutUtils
import com.nubia.launcher.widget.WidgetManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Schermata home: orologio, workspace a pagine, dock e gesti. */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private lateinit var settings: SettingsStore
    private lateinit var appManager: AppManager
    private lateinit var widgetManager: WidgetManager
    private lateinit var gestureController: GestureController

    private val clockFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())

    private var lastApps: List<AppInfo> = emptyList()
    private val homeItems: MutableList<HomeItem> = mutableListOf()

    private var lastThemeKey: Pair<Int, Int>? = null
    private var lastGrid: Pair<Int, Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as LauncherApplication
        settings = app.settings
        appManager = app.appManager

        ThemeManager.apply(this, settings.get())
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        showCrashReportIfPresent()

        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        widgetManager = WidgetManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupWorkspace()
        setupDock()
        setupGestures()
        setupClock()
        setupBack()

        lifecycleScope.launch { appManager.load() }
        lifecycleScope.launch { appManager.apps.collect(::onAppsChanged) }
        lifecycleScope.launch { settings.settings.collect(::onSettingsChanged) }
    }

    override fun onStart() {
        super.onStart()
        widgetManager.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        widgetManager.close()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_WIDGET && resultCode == RESULT_OK) {
            val widget = widgetManager.consumePickedWidget(data)
            if (widget != null) {
                homeItems.add(widget)
                refreshWorkspace()
            }
        }
    }

    // ------------------------------------------------------------------ setup

    private fun setupWorkspace() {
        binding.workspace.onItemClick = { item ->
            when (item) {
                is HomeItem.App -> appManager.launch(item.appInfo)
                is HomeItem.Widget -> Unit
            }
        }
        binding.workspace.onItemLongClick = { item, view ->
            showItemMenu(item, view)
            true
        }
        binding.workspace.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator()
            }
        })
    }

    private fun setupDock() {
        binding.dock.onItemClick = { app -> appManager.launch(app) }
        binding.dock.onItemLongClick = { app, view ->
            showDockMenu(app, view)
            true
        }
    }

    private fun setupGestures() {
        gestureController = GestureController(this)
        gestureController.onSwipeUp = { openDrawer() }
        gestureController.attachTo(binding.root)

        binding.root.setOnLongClickListener {
            showEmptyMenu(it)
            true
        }
    }

    private fun setupClock() {
        lifecycleScope.launch {
            while (isActive) {
                val now = Calendar.getInstance()
                binding.clockText.text = clockFormatter.format(now.time)
                binding.dateText.text = dateFormatter.format(now.time)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                val delay = 60_000L - (now.timeInMillis % 60_000L)
                delay(if (delay <= 0) 60_000L else delay)
            }
        }
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val drawer = supportFragmentManager.findFragmentByTag(AllAppsFragment.TAG)
                if (drawer != null) {
                    supportFragmentManager.beginTransaction().remove(drawer).commit()
                }
                // In un launcher, Back dalla home non chiude l'app.
            }
        })
    }

    // ---------------------------------------------------------------- data

    private fun onAppsChanged(apps: List<AppInfo>) {
        lastApps = apps
        if (apps.isEmpty()) return
        binding.dock.setApps(
            DockView.pickDockApps(apps),
            settings.get().dockIconSizeDp,
            settings.get().showLabels
        )
        buildHomeItems(apps)
        refreshWorkspace()
    }

    private fun buildHomeItems(allApps: List<AppInfo>) {
        val widgets = homeItems.filterIsInstance<HomeItem.Widget>()
        homeItems.clear()

        val s = settings.get()
        val dockPkgs = DockView.pickDockApps(allApps).mapTo(HashSet()) { it.packageName }
        val perPage = s.cellCount

        val fill = (allApps.filterNot { it.packageName in dockPkgs } + allApps)
            .distinctBy { it.key }
            .take(perPage)

        fill.forEach { homeItems.add(HomeItem.App(it.key, it)) }
        homeItems.addAll(widgets)
    }

    private fun onSettingsChanged(s: LauncherSettings) {
        val themeKey = s.darkMode to s.accent
        if (lastThemeKey != null && lastThemeKey != themeKey) {
            lastThemeKey = themeKey
            recreate()
            return
        }
        lastThemeKey = themeKey

        binding.topBar.visibility = if (s.showClock) View.VISIBLE else View.GONE
        binding.dock.applyIconSettings(s.dockIconSizeDp, s.showLabels)

        val grid = s.columns to s.rows
        if (lastGrid != grid) {
            lastGrid = grid
            buildHomeItems(lastApps)
        }
        refreshWorkspace()
    }

    private fun refreshWorkspace() {
        val s = settings.get()
        binding.workspace.config = s.toHomeScreenConfig()
        val perPage = s.cellCount
        val pages = (0 until s.pages.coerceAtLeast(1)).map { page ->
            homeItems.drop(page * perPage).take(perPage)
        }
        binding.workspace.items = pages
        updatePageIndicator()
    }

    // --------------------------------------------------------------- UI

    private fun updatePageIndicator() {
        binding.pageIndicator.removeAllViews()
        val count = binding.workspace.pageCount
        for (i in 0 until count) {
            val dot = View(this)
            val size = dp(6)
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCCFFFFFF.toInt())
            }
            dot.alpha = if (i == binding.workspace.currentItem) 1f else 0.35f
            binding.pageIndicator.addView(dot)
        }
    }

    private fun openDrawer() {
        if (!settings.get().gestureDrawer) return
        if (supportFragmentManager.findFragmentByTag(AllAppsFragment.TAG) != null) return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.fade_out
            )
            .add(android.R.id.content, AllAppsFragment(), AllAppsFragment.TAG)
            .commit()
    }

    private fun showDockMenu(app: AppInfo, anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(Menu.NONE, MENU_SHORTCUT, 0, R.string.menu_add_shortcut)
            .setIcon(R.drawable.ic_shortcut)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SHORTCUT -> {
                    ShortcutUtils.createAppShortcut(this, app)
                    Toast.makeText(this, R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        forceMenuIcons(menu)
        menu.show()
    }

    private fun showItemMenu(item: HomeItem, anchor: View) {
        val menu = PopupMenu(this, anchor)
        when (item) {
            is HomeItem.App -> {
                menu.menu.add(Menu.NONE, MENU_SHORTCUT, 0, R.string.menu_add_shortcut)
                    .setIcon(R.drawable.ic_shortcut)
                menu.menu.add(Menu.NONE, MENU_REMOVE, 1, R.string.menu_remove)
                    .setIcon(R.drawable.ic_close)
                menu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        MENU_SHORTCUT -> {
                            ShortcutUtils.createAppShortcut(this, item.appInfo)
                            Toast.makeText(this, R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show()
                        }
                        MENU_REMOVE -> {
                            homeItems.remove(item)
                            refreshWorkspace()
                            Toast.makeText(this, R.string.toast_removed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
            }
            is HomeItem.Widget -> {
                menu.menu.add(Menu.NONE, MENU_REMOVE, 0, R.string.menu_remove)
                    .setIcon(R.drawable.ic_close)
                menu.setOnMenuItemClickListener { menuItem ->
                    if (menuItem.itemId == MENU_REMOVE) {
                        widgetManager.removeWidget(item)
                        homeItems.remove(item)
                        refreshWorkspace()
                        Toast.makeText(this, R.string.toast_removed, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }
        }
        forceMenuIcons(menu)
        menu.show()
    }

    private fun showEmptyMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(Menu.NONE, MENU_WIDGET, 0, R.string.menu_add_widget)
            .setIcon(R.drawable.ic_widget)
        menu.menu.add(Menu.NONE, MENU_WALLPAPER, 1, R.string.menu_wallpaper)
            .setIcon(R.drawable.ic_wallpaper)
        menu.menu.add(Menu.NONE, MENU_SETTINGS, 2, R.string.menu_settings)
            .setIcon(R.drawable.ic_settings)
        menu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                MENU_WIDGET -> startActivityForResult(widgetManager.buildPickIntent(), REQUEST_PICK_WIDGET)
                MENU_WALLPAPER -> startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
                MENU_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        forceMenuIcons(menu)
        menu.show()
    }

    private fun forceMenuIcons(menu: PopupMenu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            menu.setForceShowIcon(true)
        }
    }

    // -------------------------------------------------------------- helpers

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    /** Se c'è un crash.log (da un avvio fallito) lo mostra e lo cancella. */
    private fun showCrashReportIfPresent() {
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, "crash.log")
        if (!file.exists()) return
        val text = try { file.readText() } catch (_: Exception) { return }
        file.delete()
        android.app.AlertDialog.Builder(this)
            .setTitle("Errore all'avvio")
            .setMessage(text.take(2000))
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show()
    }

    companion object {
        private const val REQUEST_PICK_WIDGET = 1001

        private const val MENU_WIDGET = 1
        private const val MENU_WALLPAPER = 2
        private const val MENU_SETTINGS = 3
        private const val MENU_SHORTCUT = 4
        private const val MENU_REMOVE = 5
    }
}
