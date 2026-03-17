package com.elderlyremote.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.elderlyremote.R
import com.elderlyremote.databinding.FragmentPinSetupBinding

class PinSetupFragment : Fragment() {

    private var _binding: FragmentPinSetupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPinSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSavePin.setOnClickListener {
            val newPin = binding.etNewPin.text.toString()
            val confirmPin = binding.etConfirmPin.text.toString()

            when {
                newPin.length < 4 -> {
                    binding.tvPinError.setText(R.string.pin_too_short)
                }
                newPin != confirmPin -> {
                    binding.tvPinError.setText(R.string.pin_mismatch)
                }
                else -> {
                    viewModel.setPin(newPin)
                    binding.tvPinError.setText(R.string.pin_saved)
                    binding.etNewPin.text?.clear()
                    binding.etConfirmPin.text?.clear()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
