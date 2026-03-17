package com.elderlyremote.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.elderlyremote.bluetooth.HidKeyCodes
import com.elderlyremote.databinding.FragmentTestPadBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TestPadFragment : Fragment() {

    private var _binding: FragmentTestPadBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestPadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        observeLog()
    }

    private fun setupButtons() {
        with(binding) {
            testBtnUp.setOnClickListener    { viewModel.testSendKey(HidKeyCodes.KEY_ARROW_UP,    label = "Up") }
            testBtnDown.setOnClickListener  { viewModel.testSendKey(HidKeyCodes.KEY_ARROW_DOWN,  label = "Down") }
            testBtnLeft.setOnClickListener  { viewModel.testSendKey(HidKeyCodes.KEY_ARROW_LEFT,  label = "Left") }
            testBtnRight.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_ARROW_RIGHT, label = "Right") }
            testBtnOk.setOnClickListener    { viewModel.testSendKey(HidKeyCodes.KEY_ENTER,       label = "OK") }
            testBtnBack.setOnClickListener  { viewModel.testSendKey(HidKeyCodes.KEY_ESCAPE,      label = "Back") }
            testBtnHome.setOnClickListener  { viewModel.testSendKey(HidKeyCodes.KEY_HOME,        label = "Home") }

            testBtn0.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_0, label = "0") }
            testBtn1.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_1, label = "1") }
            testBtn2.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_2, label = "2") }
            testBtn3.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_3, label = "3") }
            testBtn4.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_4, label = "4") }
            testBtn5.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_5, label = "5") }
            testBtn6.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_6, label = "6") }
            testBtn7.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_7, label = "7") }
            testBtn8.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_8, label = "8") }
            testBtn9.setOnClickListener { viewModel.testSendKey(HidKeyCodes.KEY_9, label = "9") }

            btnSendSample.setOnClickListener {
                val digits = etTestSequence.text.toString().filter { it.isDigit() }.ifEmpty { "202" }
                val delay = etTestDelay.text.toString().toLongOrNull()?.coerceIn(100L, 500L) ?: 200L
                val sendEnter = cbTestEnter.isChecked
                viewModel.testSendSampleSequence(digits, delay, sendEnter)
            }
        }
    }

    private fun observeLog() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.testLog.collectLatest { log ->
                binding.tvTestLog.text = log
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
