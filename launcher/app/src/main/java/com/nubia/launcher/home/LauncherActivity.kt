package com.nubia.launcher.home

import android.Manifest
import android.app.WallpaperManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.nubia.launcher.CrashReportActivity
import com.nubia.launcher.LauncherApplication
import com.nubia.launcher.R
import com.nubia.launcher.databinding.ActivityLauncherBinding
import com.nubia.launcher.data.AppManager
import com.nubia.launcher.data.HomeItemsStore
import com.nubia.launcher.data.LauncherSettings
import com.nubia.launcher.data.ParsedHomeItem
import com.nubia.launcher.data.SettingsStore
import com.nubia.launcher.data.WallpaperStore
import com.nubia.launcher.home.dock.DockView
import com.nubia.launcher.home.drawer.AllAppsFragment
import com.nubia.launcher.home.gesture.GestureController
import com.nubia.launcher.home.workspace.DragData
import com.nubia.launcher.home.workspace.Workspace
import com.nubia.launcher.home.workspace.toHomeScreenConfig
import com.nubia.launcher.model.AppInfo
import com.nubia.launcher.model.HomeItem
import com.nubia.launcher.notification.NotificationBadgeHelper
import com.nubia.launcher.notification.NotificationPanelFragment
import com.nubia.launcher.settings.SettingsActivity
import com.nubia.launcher.theme.ThemeManager
import com.nubia.launcher.util.ShortcutUtils
import com.nubia.launcher.widget.WidgetManager
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var lastDock: Int? = null
    private var lastWallpaper: String? = null

    private var badges: Map<String, Int> = emptyMap()

    private val homeStore: HomeItemsStore by lazy { HomeItemsStore(this) }
    private val dockComponents: MutableList<String> = mutableListOf()
    private var persistedDockLoaded = false
    private var lastDragIndex = -1
    private var lastDragAnchor: View? = null

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (WallpaperStore.save(this, uri)) {
                settings.setCustomWallpaper(WallpaperStore.path(this))
                Toast.makeText(this, R.string.toast_wallpaper_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_wallpaper_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

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

            // Padding delle barre di sistema solo sul contenuto: lo sfondo
            // resta full-bleed sotto status/navigation bar.
            ViewCompat.setOnApplyWindowInsetsListener(binding.contentRoot) { v, insets ->
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
            applyWallpaper()

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
                persistHomeItems()
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
                is HomeItem.Folder -> openFolder(item)
            }
        }
        binding.workspace.onItemDragStart = { index, item, view ->
            if (item is HomeItem.Widget) {
                showItemMenu(item, view)
                false
            } else {
                lastDragIndex = index
                lastDragAnchor = view
                val pkg = (item as? HomeItem.App)?.appInfo?.packageName.orEmpty()
                val data = DragData(index, pkg)
                view.startDragAndDrop(
                    ClipData.newPlainText(Workspace.DRAG_MIME, ""),
                    View.DragShadowBuilder(view),
                    data,
                    View.DRAG_FLAG_GLOBAL
                )
                true
            }
        }
        binding.workspace.onItemDrop = { source, target ->
            handleDrop(source, target)
        }
        binding.workspace.onItemRemove = { index ->
            removeHomeItem(index)
        }
        binding.workspace.onItemDropFailed = { index ->
            val item = homeItems.getOrNull(index)
            if (item != null) {
                showItemMenu(item, lastDragAnchor ?: binding.workspace)
            }
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
        binding.dock.onDockDrop = { pkg -> handleDockDrop(pkg) }
    }

    private fun setupGestures() {
        gestureController = GestureController(this)
        gestureController.onSwipeUp = {
            if (settings.get().gestureDrawer) openDrawer()
        }
        gestureController.onSwipeDown = {
            val drawerOpen = supportFragmentManager.findFragmentByTag(AllAppsFragment.TAG) != null
            val panelOpen = supportFragmentManager.findFragmentByTag(NotificationPanelFragment.TAG) != null
            if (!drawerOpen && !panelOpen && settings.get().panelSwipe) {
                openNotificationPanel()
            }
        }

        binding.root.setOnLongClickListener {
            showEmptyMenu(it)
            true
        }
    }

    private fun openNotificationPanel() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.fade_out
            )
            .add(android.R.id.content, NotificationPanelFragment(), NotificationPanelFragment.TAG)
            .commit()
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
        loadDockIfNeeded(apps)
        refreshDock()
        refreshBadges()
        ensureHomeItemsPopulated()
        refreshWorkspace()
    }

    /** Carica il dock persistito (o lo inizializza con le app predefinite). */
    private fun loadDockIfNeeded(apps: List<AppInfo>) {
        if (persistedDockLoaded) return
        persistedDockLoaded = true
        val persisted = homeStore.loadDock()
        dockComponents.clear()
        if (persisted.isNotEmpty()) {
            dockComponents.addAll(persisted)
        } else {
            DockView.pickDockApps(apps, settings.get().dockItems)
                .forEach { dockComponents.add(it.key) }
        }
    }

    private fun refreshDock() {
        val s = settings.get()
        val apps = dockComponents.mapNotNull { c -> lastApps.firstOrNull { it.key == c } }
        binding.dock.setApps(apps, s.dockIconSizeDp, s.showLabels, s.iconShape, s.dockItems)
    }

    private fun persistDock() {
        homeStore.saveDock(dockComponents)
    }

    /** Aggiunge un'app al dock (dalla home tramite drag). */
    private fun handleDockDrop(pkg: String) {
        if (pkg in dockComponents) return
        dockComponents.add(pkg)
        persistDock()
        refreshDock()
        val idx = homeItems.indexOfFirst { it is HomeItem.App && it.appInfo.key == pkg }
        if (idx >= 0) {
            homeItems.removeAt(idx)
            persistHomeItems()
            refreshWorkspace()
        }
        Toast.makeText(this, R.string.toast_added_dock, Toast.LENGTH_SHORT).show()
    }

    private fun refreshBadges() {
        badges = if (settings.get().notificationBadges) {
            NotificationBadgeHelper.refresh(this)
        } else {
            emptyMap()
        }
        binding.workspace.badges = badges
    }

    /** Popola la home una sola volta (da persistenza o auto-riempimento). */
    private fun ensureHomeItemsPopulated() {
        if (homeItems.isNotEmpty()) return
        val persisted = homeStore.loadItems()
        if (persisted.isNotEmpty()) {
            resolvePersisted(persisted)
        } else {
            autoFillHome()
        }
        persistHomeItems()
    }

    private fun autoFillHome() {
        val s = settings.get()
        val dockKeys = dockComponents.toSet()
        val perPage = s.cellCount
        val fill = (lastApps.filterNot { it.key in dockKeys } + lastApps)
            .distinctBy { it.key }
            .take(perPage)
        fill.forEach { homeItems.add(HomeItem.App(it.key, it)) }
    }

    private fun resolvePersisted(items: List<ParsedHomeItem>) {
        items.forEach { parsed ->
            when (parsed.type) {
                "app" -> {
                    lastApps.firstOrNull { it.key == parsed.component }
                        ?.let { homeItems.add(HomeItem.App(it.key, it)) }
                }
                "widget" -> {
                    widgetManager.restoreWidget(parsed.appWidgetId)?.let { homeItems.add(it) }
                }
                "folder" -> {
                    val folder = HomeItem.Folder("folder_${System.currentTimeMillis()}")
                    folder.name = parsed.name.ifBlank { "Cartella" }
                    parsed.children.forEach { child ->
                        lastApps.firstOrNull { it.key == child }?.let { folder.apps.add(it) }
                    }
                    if (folder.apps.isNotEmpty()) homeItems.add(folder)
                }
            }
        }
    }

    private fun persistHomeItems() {
        val parsed = homeItems.map { item ->
            when (item) {
                is HomeItem.App -> ParsedHomeItem("app", component = item.appInfo.key)
                is HomeItem.Widget -> ParsedHomeItem("widget", appWidgetId = item.appWidgetId)
                is HomeItem.Folder -> ParsedHomeItem(
                    "folder",
                    name = item.name,
                    children = item.apps.map { it.key }
                )
            }
        }
        homeStore.saveItems(parsed)
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
        binding.dock.applyIconSettings(s.dockIconSizeDp, s.showLabels, s.iconShape)

        if (lastDock != s.dockItems) {
            lastDock = s.dockItems
            refreshDock()
        }

        if (lastWallpaper != s.customWallpaper) {
            lastWallpaper = s.customWallpaper
            applyWallpaper()
        }

        val grid = s.columns to s.rows
        if (lastGrid != grid) {
            lastGrid = grid
            refreshBadges()
        }
        refreshWorkspace()
    }

    /**
     * Applica lo sfondo della home: prima l'immagine personalizzata, poi lo
     * sfondo di sistema, infine un gradiente di fallback (già visibile a
     * partire dal layout). Il decode avviene fuori dal main thread.
     */
    private fun applyWallpaper() {
        val path = settings.get().customWallpaper
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                if (path.isNotEmpty()) decodeCustomWallpaper(path) else decodeSystemWallpaper()
            }
            if (bmp != null) {
                binding.wallpaperBg.setImageBitmap(bmp)
                binding.wallpaperBg.visibility = View.VISIBLE
                binding.wallpaperBg.animate().cancel()
                binding.wallpaperBg.alpha = 0f
                binding.wallpaperBg.animate().alpha(1f).setDuration(300).start()
            }
        }
    }

    private fun decodeCustomWallpaper(path: String): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) {
                null
            } else {
                var sample = 1
                val maxDim = maxOf(
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels
                ).coerceAtLeast(1080)
                while (w / (sample * 2) >= maxDim && h / (sample * 2) >= maxDim) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, opts)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeSystemWallpaper(): Bitmap? {
        return try {
            val drawable = WallpaperManager.getInstance(this).drawable
            if (drawable == null) {
                null
            } else {
                val dm = resources.displayMetrics
                drawable.toBitmap(dm.widthPixels.coerceAtLeast(1), dm.heightPixels.coerceAtLeast(1))
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun refreshWorkspace() {
        val s = settings.get()
        binding.workspace.config = s.toHomeScreenConfig()
        val perPage = s.cellCount
        val neededPages = ((homeItems.size + perPage - 1) / perPage).coerceAtLeast(s.pages.coerceAtLeast(1))
        val pages = (0 until neededPages).map { page ->
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
            val dot = TextView(this)
            val size = dp(6)
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6FFFFFF.toInt())
            }
            dot.setShadowLayer(dp(1).toFloat(), 0f, 1f, 0x66000000)
            dot.alpha = if (i == binding.workspace.currentItem) 1f else 0.4f
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
        persistHomeItems()
        refreshWorkspace()
        Toast.makeText(this, R.string.menu_add_to_home, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------ drag & drop

    private fun handleDrop(sourceIndex: Int, targetIndex: Int) {
        if (sourceIndex !in homeItems.indices || targetIndex !in homeItems.indices) return
        if (sourceIndex == targetIndex) return
        val source = homeItems[sourceIndex]
        val target = homeItems[targetIndex]
        val targetAdj = if (sourceIndex < targetIndex) targetIndex - 1 else targetIndex
        when {
            source is HomeItem.App && target is HomeItem.App -> {
                val folder = HomeItem.Folder("folder_${System.currentTimeMillis()}")
                folder.apps.add(target.appInfo)
                folder.apps.add(source.appInfo)
                if (sourceIndex < targetIndex) {
                    homeItems.removeAt(sourceIndex)
                    homeItems[targetIndex - 1] = folder
                } else {
                    homeItems.removeAt(sourceIndex)
                    homeItems[targetIndex] = folder
                }
            }
            source is HomeItem.App && target is HomeItem.Folder -> {
                target.apps.add(source.appInfo)
                homeItems.removeAt(sourceIndex)
            }
            else -> {
                val moved = homeItems.removeAt(sourceIndex)
                homeItems.add(targetAdj, moved)
            }
        }
        persistHomeItems()
        refreshWorkspace()
    }

    private fun removeHomeItem(index: Int) {
        if (index !in homeItems.indices) return
        val item = homeItems.removeAt(index)
        if (item is HomeItem.Widget) widgetManager.removeWidget(item)
        persistHomeItems()
        refreshWorkspace()
        Toast.makeText(this, R.string.toast_removed, Toast.LENGTH_SHORT).show()
    }

    private fun openFolder(folder: HomeItem.Folder) {
        val names = folder.apps.map { it.label }
        if (names.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(names.toTypedArray()) { _, which ->
                appManager.launch(folder.apps[which])
            }
            .setNegativeButton(R.string.drawer_close, null)
            .show()
    }

    private fun renameFolder(folder: HomeItem.Folder) {
        val input = EditText(this)
        input.setText(folder.name)
        input.hint = getString(R.string.menu_folder_name)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                folder.name = input.text.toString().trim().ifBlank { "Cartella" }
                persistHomeItems()
                refreshWorkspace()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun removeItemFromHome(item: HomeItem) {
        val index = homeItems.indexOfFirst { it === item }
        if (index >= 0) removeHomeItem(index)
    }

    private fun showDockMenu(app: AppInfo, anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(Menu.NONE, MENU_OPEN, 0, R.string.menu_open)
            .setIcon(android.R.drawable.ic_menu_view)
        menu.menu.add(Menu.NONE, MENU_SHORTCUT, 1, R.string.menu_add_shortcut)
            .setIcon(R.drawable.ic_shortcut)
        menu.menu.add(Menu.NONE, MENU_DOCK_REMOVE, 2, R.string.menu_dock_remove)
            .setIcon(R.drawable.ic_close)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_OPEN -> appManager.launch(app)
                MENU_SHORTCUT -> {
                    ShortcutUtils.createAppShortcut(this, app)
                    Toast.makeText(this, R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show()
                }
                MENU_DOCK_REMOVE -> {
                    dockComponents.remove(app.key)
                    persistDock()
                    refreshDock()
                    Toast.makeText(this, R.string.toast_removed_dock, Toast.LENGTH_SHORT).show()
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
                        MENU_REMOVE -> removeItemFromHome(item)
                    }
                    true
                }
            }
            is HomeItem.Widget -> {
                menu.menu.add(Menu.NONE, MENU_REMOVE, 0, R.string.menu_remove)
                    .setIcon(R.drawable.ic_close)
                menu.setOnMenuItemClickListener { menuItem ->
                    if (menuItem.itemId == MENU_REMOVE) {
                        removeItemFromHome(item)
                    }
                    true
                }
            }
            is HomeItem.Folder -> {
                menu.menu.add(Menu.NONE, MENU_OPEN, 0, R.string.menu_open)
                    .setIcon(android.R.drawable.ic_menu_view)
                menu.menu.add(Menu.NONE, MENU_RENAME, 1, R.string.menu_rename)
                    .setIcon(android.R.drawable.ic_menu_edit)
                menu.menu.add(Menu.NONE, MENU_REMOVE, 2, R.string.menu_remove)
                    .setIcon(R.drawable.ic_close)
                menu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        MENU_OPEN -> openFolder(item)
                        MENU_RENAME -> renameFolder(item)
                        MENU_REMOVE -> removeItemFromHome(item)
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
        menu.menu.add(Menu.NONE, MENU_WALLPAPER_SYSTEM, 2, R.string.menu_wallpaper_system)
            .setIcon(R.drawable.ic_wallpaper)
        menu.menu.add(Menu.NONE, MENU_SETTINGS, 3, R.string.menu_settings)
            .setIcon(R.drawable.ic_settings)
        menu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                MENU_WIDGET -> startActivityForResult(widgetManager.buildPickIntent(), REQUEST_PICK_WIDGET)
                MENU_WALLPAPER -> wallpaperPicker.launch("image/*")
                MENU_WALLPAPER_SYSTEM -> startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
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
        private const val MENU_WALLPAPER_SYSTEM = 9
        private const val MENU_DOCK_REMOVE = 10
        private const val MENU_RENAME = 11
    }
}
