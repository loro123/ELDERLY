package com.elderlyremote.ui.admin

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.elderlyremote.bluetooth.ConnectionState
import com.elderlyremote.databinding.FragmentPairingBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PairingFragment : Fragment() {

    private var _binding: FragmentPairingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnOpenBluetooth.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        // Observe connection state from the HID service via the ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hidService?.connectionState?.collectLatest { state ->
                binding.tvPairingStatus.text = when (state) {
                    is ConnectionState.Connected -> "Connected: ${state.deviceName}"
                    is ConnectionState.Waiting -> "Waiting for device to connect…"
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.BluetoothUnavailable -> "Bluetooth not available"
                    else -> "Not connected"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
