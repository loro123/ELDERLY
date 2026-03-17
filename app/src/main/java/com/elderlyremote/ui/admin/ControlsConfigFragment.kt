package com.elderlyremote.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.elderlyremote.databinding.FragmentControlsConfigBinding
import com.elderlyremote.model.ButtonSize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ControlsConfigFragment : Fragment() {

    private var _binding: FragmentControlsConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by activityViewModels()

    // Suppress programmatic toggle events while loading config
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentControlsConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwitches()
        setupButtonSize()
        observeConfig()
    }

    private fun setupSwitches() {
        binding.switchPower.setOnCheckedChangeListener { _, checked ->
            if (!isLoading) viewModel.setShowPower(checked)
        }
        binding.switchVolumeUp.setOnCheckedChangeListener { _, checked ->
            if (!isLoading) viewModel.setShowVolumeUp(checked)
        }
        binding.switchVolumeDown.setOnCheckedChangeListener { _, checked ->
            if (!isLoading) viewModel.setShowVolumeDown(checked)
        }
        binding.switchMute.setOnCheckedChangeListener { _, checked ->
            if (!isLoading) viewModel.setShowMute(checked)
        }
        binding.switchGuide.setOnCheckedChangeListener { _, checked ->
            if (!isLoading) viewModel.setShowGuide(checked)
        }
        binding.switchBack.setOnCheckedChangeListener { _, checked ->
            if (!isLoading) viewModel.setShowBack(checked)
        }
        binding.switchLastChannel.setOnCheckedChangeListener { _, checked ->
            if (!isLoading) viewModel.setShowLastChannel(checked)
        }
    }

    private fun setupButtonSize() {
        binding.rgButtonSize.setOnCheckedChangeListener { _, checkedId ->
            if (!isLoading) {
                val size = when (checkedId) {
                    binding.rbSizeExtraLarge.id -> ButtonSize.EXTRA_LARGE
                    else -> ButtonSize.LARGE
                }
                viewModel.setButtonSize(size)
            }
        }
    }

    private fun observeConfig() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.config.collectLatest { config ->
                config ?: return@collectLatest
                isLoading = true
                val vis = config.controlsVisibility
                binding.switchPower.isChecked = vis.showPower
                binding.switchVolumeUp.isChecked = vis.showVolumeUp
                binding.switchVolumeDown.isChecked = vis.showVolumeDown
                binding.switchMute.isChecked = vis.showMute
                binding.switchGuide.isChecked = vis.showGuide
                binding.switchBack.isChecked = vis.showBack
                binding.switchLastChannel.isChecked = vis.showLastChannel
                when (config.buttonSize) {
                    ButtonSize.EXTRA_LARGE -> binding.rbSizeExtraLarge.isChecked = true
                    else -> binding.rbSizeLarge.isChecked = true
                }
                isLoading = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
