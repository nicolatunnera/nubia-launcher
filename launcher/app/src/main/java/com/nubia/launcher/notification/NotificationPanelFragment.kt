package com.nubia.launcher.notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.nubia.launcher.R
import com.nubia.launcher.databinding.FragmentNotificationPanelBinding
import kotlinx.coroutines.launch

/**
 * Pannello notifiche in-app: interruttori rapidi riordinabili + lista
 * notifiche. Aperto con uno swipe verso il basso sulla home.
 */
class NotificationPanelFragment : Fragment() {

    private var _binding: FragmentNotificationPanelBinding? = null
    private val binding get() = _binding!!

    private lateinit var qsAdapter: QuickSettingsAdapter
    private lateinit var notifAdapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss()
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dm = resources.displayMetrics
        binding.shade.layoutParams = binding.shade.layoutParams.apply {
            height = (dm.heightPixels * 0.75f).toInt()
        }

        binding.root.setOnClickListener { dismiss() }
        binding.shade.setOnClickListener { /* consuma il tocco: non chiude */ }

        binding.panelClose.setOnClickListener { dismiss() }
        binding.panelClear.setOnClickListener { NotificationRepository.clearAll() }

        binding.qsGrid.layoutManager = GridLayoutManager(requireContext(), 4)
        qsAdapter = QuickSettingsAdapter(
            requireContext(),
            onToggle = ::toggleQuick,
            onLongPress = ::showToggleMenu
        )
        binding.qsGrid.adapter = qsAdapter
        qsAdapter.submit(QuickSettingsStore.visible(requireContext()))

        binding.notifList.layoutManager = LinearLayoutManager(requireContext())
        notifAdapter = NotificationAdapter(onClick = ::openNotificationApp)
        binding.notifList.adapter = notifAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            NotificationRepository.notifications.collect { notifAdapter.submit(it) }
        }

        setupSliders()
    }

    override fun onResume() {
        super.onResume()
        if (::qsAdapter.isInitialized) qsAdapter.notifyDataSetChanged()
    }

    private fun setupSliders() {
        binding.brightnessSeek.max = 255
        binding.brightnessSeek.progress = QuickToggleActions.brightness(requireContext())
        binding.brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) QuickToggleActions.setBrightness(requireContext(), progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.volumeSeek.max = QuickToggleActions.maxVolume(requireContext())
        binding.volumeSeek.progress = QuickToggleActions.volume(requireContext())
        binding.volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) QuickToggleActions.setVolume(requireContext(), progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun toggleQuick(toggle: QuickToggle) {
        if (toggle.id == "flashlight") {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                return
            }
        }
        QuickToggleActions.toggle(requireContext(), toggle.id)
        qsAdapter.notifyDataSetChanged()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            QuickToggleActions.toggle(requireContext(), "flashlight")
            qsAdapter.notifyDataSetChanged()
        }
    }

    private fun showToggleMenu(toggle: QuickToggle, anchor: View) {
        val menu = PopupMenu(requireContext(), anchor)
        menu.menu.add(Menu.NONE, 1, 0, R.string.qs_move_start)
        menu.menu.add(Menu.NONE, 2, 1, R.string.qs_move_end)
        menu.menu.add(Menu.NONE, 3, 2, R.string.qs_hide)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> QuickSettingsStore.moveToStart(requireContext(), toggle.id)
                2 -> QuickSettingsStore.moveToEnd(requireContext(), toggle.id)
                3 -> {
                    QuickSettingsStore.setHidden(requireContext(), toggle.id, true)
                    Toast.makeText(requireContext(), R.string.qs_hidden, Toast.LENGTH_SHORT).show()
                }
            }
            qsAdapter.submit(QuickSettingsStore.visible(requireContext()))
            true
        }
        menu.show()
    }

    private fun openNotificationApp(entry: NotifEntry) {
        try {
            val intent = requireContext().packageManager
                .getLaunchIntentForPackage(entry.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                dismiss()
            }
        } catch (_: Exception) {
        }
    }

    private fun dismiss() {
        parentFragmentManager.beginTransaction().remove(this).commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "notification_panel"
        private const val REQ_CAMERA = 40
    }
}
