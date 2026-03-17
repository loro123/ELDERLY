package com.elderlyremote.ui.admin

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.elderlyremote.R
import com.elderlyremote.bluetooth.HidService
import com.elderlyremote.databinding.ActivityAdminBinding
import com.elderlyremote.databinding.DialogPinEntryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val viewModel: AdminViewModel by viewModels()

    private var hidService: HidService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as HidService.HidBinder
            hidService = b.getService()
            viewModel.hidService = hidService
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            hidService = null
            viewModel.hidService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bind to HID service if already running
        bindService(Intent(this, HidService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        // Observe toast events
        lifecycleScope.launch {
            viewModel.toastEvent.collectLatest { msg ->
                Toast.makeText(this@AdminActivity, msg, Toast.LENGTH_LONG).show()
            }
        }

        // Wait for config to load, then show PIN gate
        lifecycleScope.launch {
            viewModel.config.collectLatest { config ->
                config ?: return@collectLatest
                // Only gate once
                if (savedInstanceState == null) {
                    showPinGate()
                }
            }
        }

        binding.btnAdminClose.setOnClickListener { finish() }
        setupTabs()
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }

    // ---- PIN Gate ----

    private fun showPinGate() {
        if (!viewModel.isPinSet()) {
            // First run: go straight to PIN setup tab
            showPanel(PinSetupFragment(), "pin")
            Toast.makeText(this, getString(R.string.first_run_set_pin), Toast.LENGTH_LONG).show()
            return
        }

        val dialogBinding = DialogPinEntryBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this, R.style.Theme_ElderlyRemote_Dialog)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = dialogBinding.etPinEntry.text.toString()
                if (viewModel.verifyPin(pin)) {
                    dialog.dismiss()
                    showPanel(PairingFragment(), "pairing")
                } else {
                    dialogBinding.tvPinEntryError.setText(R.string.pin_incorrect)
                }
            }
        }
        dialog.show()
    }

    // ---- Tab Navigation ----

    private fun setupTabs() {
        binding.tabPairing.setOnClickListener     { showPanel(PairingFragment(),       "pairing") }
        binding.tabFavorites.setOnClickListener   { showPanel(FavoritesConfigFragment(),"favorites") }
        binding.tabControls.setOnClickListener    { showPanel(ControlsConfigFragment(), "controls") }
        binding.tabImportExport.setOnClickListener{ showPanel(ImportExportFragment(),   "importexport") }
        binding.tabTestPad.setOnClickListener     { showPanel(TestPadFragment(),        "testpad") }
        binding.tabPin.setOnClickListener         { showPanel(PinSetupFragment(),       "pin") }
    }

    private fun showPanel(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.panelContainer, fragment, tag)
            .commit()
        // Highlight active tab
        val tabMap = mapOf(
            "pairing"     to binding.tabPairing,
            "favorites"   to binding.tabFavorites,
            "controls"    to binding.tabControls,
            "importexport"to binding.tabImportExport,
            "testpad"     to binding.tabTestPad,
            "pin"         to binding.tabPin
        )
        tabMap.forEach { (t, btn) -> btn.isSelected = t == tag }
    }
}
