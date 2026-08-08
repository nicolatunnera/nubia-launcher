package com.nubia.launcher.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MotionEvent
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
import com.nubia.launcher.CrashReportActivity
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
import com.nubia.launcher.notification.NotificationBadgeHelper
import com.nubia.launcher.settings.SettingsActivity
import com.nubia.launcher.theme.ThemeManager
import com.nubia.launcher.util.ShortcutUtils
import com.nubia.launcher.widget.WidgetManager
import java.io.File
import java.io.PrintWriter
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

    private var homeReady = false

    private val clockFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())

    private var lastApps: List<AppInfo> = emptyList()
    private val homeItems: MutableList<HomeItem> = mutableListOf()

    private var lastThemeKey: Pair<Int, Int>? = null
    private var lastGrid: Pair<Int, Int>? = null

    private var badges: Map<String, Int> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (crashReportExists()) {
            try {
                super.onCreate(savedInstanceState)
            } catch (_: Throwable) {
            }
            try {
                startActivity(Intent(this, CrashReportActivity::class.java))
            } catch (_: Throwable) {
            }
            try {
                finish()
            } catch (_: Throwable) {
            }
            return
        }

        try {
            val app = application as LauncherApplication
            settings = app.settings
            appManager = app.appManager

            ThemeManager.apply(this, settings.get())
            super.onCreate(savedInstanceState)

            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

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
            setupSearchBar()
            requestNotificationPermission()

            homeReady = true

            lifecycleScope.launch { appManager.load() }
            lifecycleScope.launch { appManager.apps.collect(::onAppsChanged) }
            lifecycleScope.launch { settings.settings.collect(::onSettingsChanged) }
        } catch (t: Throwable) {
            saveCrash(t)
            try {
                startActivity(Intent(this, CrashReportActivity::class.java))
            } catch (_: Throwable) {
            }
            try {
                finish()
            } catch (_: Throwable) {
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (homeReady) widgetManager.startListening()
    }

    override fun onStop() {
        super.onStop()
        if (homeReady) widgetManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (homeReady) widgetManager.close()
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
        binding.dock.onDrawerClick = { openDrawer() }
    }

    private fun setupGestures() {
        gestureController = GestureController(this)
        gestureController.onSwipeUp = {
            if (settings.get().gestureDrawer) openDrawer()
        }

        binding.root.setOnLongClickListener {
            showEmptyMenu(it)
            true
        }
    }

    /** Inoltra tutti i tocchi al riconoscitore gesti (vede anche i tocchi sulle icone). */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gestureController.isInitialized) gestureController.dispatch(ev)
        return super.dispatchTouchEvent(ev)
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

    private fun setupSearchBar() {
        binding.searchBar.setOnClickListener {
            openDrawer(focusSearch = true)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
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
        refreshBadges()
        buildHomeItems(apps)
        refreshWorkspace()
    }

    private fun refreshBadges() {
        badges = if (settings.get().notificationBadges) {
            NotificationBadgeHelper.refresh(this)
        } else {
            emptyMap()
        }
        binding.workspace.badges = badges
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
        binding.searchBar.visibility = if (s.searchBar) View.VISIBLE else View.GONE
        binding.dock.applyIconSettings(s.dockIconSizeDp, s.showLabels)

        val grid = s.columns to s.rows
        if (lastGrid != grid) {
            lastGrid = grid
            buildHomeItems(lastApps)
            refreshBadges()
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

    private fun openDrawer(focusSearch: Boolean = false) {
        if (supportFragmentManager.findFragmentByTag(AllAppsFragment.TAG) != null) return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.fade_out
            )
            .add(android.R.id.content, AllAppsFragment.newInstance(focusSearch), AllAppsFragment.TAG)
            .commit()
    }

    /** Aggiunge un'app alla home (dalla prima pagina con spazio). */
    fun addAppToHome(app: AppInfo) {
        if (!::settings.isInitialized) return
        if (homeItems.any { it is HomeItem.App && it.appInfo.key == app.key }) {
            Toast.makeText(this, R.string.menu_add_to_home, Toast.LENGTH_SHORT).show()
            return
        }
        val perPage = settings.get().cellCount
        val pages = settings.get().pages.coerceAtLeast(1)
        var targetIndex = homeItems.size
        for (page in 0 until pages) {
            val start = page * perPage
            val pageItems = homeItems.drop(start).take(perPage)
            if (pageItems.size < perPage) {
                targetIndex = start + pageItems.size
                break
            }
        }
        homeItems.add(targetIndex, HomeItem.App(app.key, app))
        refreshWorkspace()
        Toast.makeText(this, R.string.menu_add_to_home, Toast.LENGTH_SHORT).show()
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
                menu.menu.add(Menu.NONE, MENU_OPEN, 0, R.string.menu_open)
                    .setIcon(android.R.drawable.ic_menu_view)
                menu.menu.add(Menu.NONE, MENU_APP_INFO, 1, R.string.menu_app_info)
                    .setIcon(android.R.drawable.ic_menu_info_details)
                menu.menu.add(Menu.NONE, MENU_UNINSTALL, 2, R.string.menu_uninstall)
                    .setIcon(android.R.drawable.ic_menu_delete)
                menu.menu.add(Menu.NONE, MENU_REMOVE, 3, R.string.menu_remove)
                    .setIcon(R.drawable.ic_close)
                menu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        MENU_OPEN -> appManager.launch(item.appInfo)
                        MENU_APP_INFO -> appManager.openAppInfo(item.appInfo)
                        MENU_UNINSTALL -> appManager.uninstall(item.appInfo)
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

    /** Vero se esiste un errore registrato da un avvio precedente. */
    private fun crashReportExists(): Boolean {
        return try {
            val dir = getExternalFilesDir(null) ?: filesDir
            File(dir, "crash.log").exists()
        } catch (_: Throwable) {
            false
        }
    }

    private fun saveCrash(t: Throwable) {
        try {
            val dir = getExternalFilesDir(null) ?: filesDir
            PrintWriter(File(dir, "crash.log")).use { writer ->
                writer.println("=== Crash ${System.currentTimeMillis()} ===")
                t.printStackTrace(writer)
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val REQUEST_PICK_WIDGET = 1001
        private const val REQ_NOTIFICATIONS = 1002

        private const val MENU_WIDGET = 1
        private const val MENU_WALLPAPER = 2
        private const val MENU_SETTINGS = 3
        private const val MENU_SHORTCUT = 4
        private const val MENU_REMOVE = 5
        private const val MENU_OPEN = 6
        private const val MENU_APP_INFO = 7
        private const val MENU_UNINSTALL = 8
    }
}
