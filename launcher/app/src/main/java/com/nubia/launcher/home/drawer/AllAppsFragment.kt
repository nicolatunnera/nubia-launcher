package com.nubia.launcher.home.drawer

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.nubia.launcher.LauncherApplication
import com.nubia.launcher.R
import com.nubia.launcher.databinding.FragmentAllAppsBinding
import com.nubia.launcher.model.AppInfo
import com.nubia.launcher.notification.NotificationBadgeHelper
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Cassetto delle app a schermo intero, con ricerca, badge e menu contestuali. */
class AllAppsFragment : Fragment() {

    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!

    private var focusSearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focusSearch = arguments?.getBoolean(ARG_FOCUS_SEARCH, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as LauncherApplication
        val settings = app.settings
        val appManager = app.appManager
        val current = settings.get()

        val adapter = AllAppsAdapter(current.iconSizeDp, current.showLabels)
        adapter.onItemClick = { info ->
            appManager.launch(info)
            dismiss()
        }
        adapter.onItemLongClick = { info, anchor ->
            showAppMenu(info, anchor)
            true
        }

        binding.allAppsList.layoutManager = GridLayoutManager(requireContext(), current.columns)
        binding.allAppsList.adapter = adapter

        binding.closeButton.setOnClickListener { dismiss() }
        binding.searchInput.doAfterTextChanged { text ->
            adapter.filter(text?.toString().orEmpty())
            val empty = binding.allAppsList.adapter?.itemCount ?: 0
            binding.emptyText.visibility = if (empty == 0) View.VISIBLE else View.GONE
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appManager.apps.collect { apps ->
                if (settings.get().notificationBadges) {
                    adapter.badges = NotificationBadgeHelper.refresh(requireContext())
                } else {
                    adapter.badges = emptyMap()
                }
                adapter.submit(apps)
                binding.emptyText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        if (focusSearch) {
            binding.searchInput.post {
                binding.searchInput.requestFocus()
                showKeyboard()
            }
        }

        installSwipeDownToClose()
    }

    private fun showAppMenu(app: AppInfo, anchor: View) {
        val appManager = (requireActivity().application as LauncherApplication).appManager
        val menu = PopupMenu(requireContext(), anchor)
        menu.menu.add(Menu.NONE, MENU_OPEN, 0, R.string.menu_open)
            .setIcon(android.R.drawable.ic_menu_view)
        menu.menu.add(Menu.NONE, MENU_ADD_HOME, 1, R.string.menu_add_to_home)
            .setIcon(android.R.drawable.ic_menu_add)
        menu.menu.add(Menu.NONE, MENU_INFO, 2, R.string.menu_app_info)
            .setIcon(android.R.drawable.ic_menu_info_details)
        menu.menu.add(Menu.NONE, MENU_UNINSTALL, 3, R.string.menu_uninstall)
            .setIcon(android.R.drawable.ic_menu_delete)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_OPEN -> appManager.launch(app)
                MENU_ADD_HOME -> addToHome(app)
                MENU_INFO -> appManager.openAppInfo(app)
                MENU_UNINSTALL -> appManager.uninstall(app)
            }
            true
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            menu.setForceShowIcon(true)
        }
        menu.show()
    }

    private fun addToHome(app: AppInfo) {
        val activity = requireActivity()
        if (activity is com.nubia.launcher.home.LauncherActivity) {
            activity.addAppToHome(app)
        }
    }

    private fun installSwipeDownToClose() {
        val detector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val dy = e2.y - e1.y
                    if (velocityY > 900f && dy > 120f) {
                        dismiss()
                        return true
                    }
                    return false
                }
            }
        )
        binding.root.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun showKeyboard() {
        try {
            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
        }
    }

    fun dismiss() {
        hideKeyboard()
        parentFragmentManager.beginTransaction().remove(this).commit()
    }

    private fun hideKeyboard() {
        try {
            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "all_apps_drawer"
        private const val ARG_FOCUS_SEARCH = "focus_search"

        private const val MENU_OPEN = 1
        private const val MENU_ADD_HOME = 2
        private const val MENU_INFO = 3
        private const val MENU_UNINSTALL = 4

        fun newInstance(focusSearch: Boolean = false): AllAppsFragment =
            AllAppsFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_FOCUS_SEARCH, focusSearch) }
            }
    }
}
