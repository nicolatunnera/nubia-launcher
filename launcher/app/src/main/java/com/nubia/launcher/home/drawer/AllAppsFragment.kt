package com.nubia.launcher.home.drawer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.nubia.launcher.LauncherApplication
import com.nubia.launcher.databinding.FragmentAllAppsBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Cassetto delle app a schermo intero, con ricerca. */
class AllAppsFragment : Fragment() {

    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!

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

        binding.allAppsList.layoutManager = GridLayoutManager(requireContext(), current.columns)
        binding.allAppsList.adapter = adapter

        binding.closeButton.setOnClickListener { dismiss() }
        binding.searchInput.doAfterTextChanged { text ->
            adapter.filter(text?.toString().orEmpty())
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appManager.apps.collect { apps -> adapter.submit(apps) }
        }

        binding.searchInput.requestFocus()
    }

    fun dismiss() {
        parentFragmentManager.beginTransaction().remove(this).commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "all_apps_drawer"
    }
}
