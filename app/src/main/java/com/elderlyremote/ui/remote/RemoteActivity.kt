package com.elderlyremote.ui.remote

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.elderlyremote.ElderlyRemoteApp
import com.elderlyremote.R
import com.elderlyremote.bluetooth.ConnectionState
import com.elderlyremote.bluetooth.HidKeyCodes
import com.elderlyremote.bluetooth.HidService
import com.elderlyremote.databinding.ActivityRemoteBinding
import com.elderlyremote.model.AppConfig
import com.elderlyremote.model.ControlsVisibility
import com.elderlyremote.ui.admin.AdminActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class RemoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoteBinding
    private val viewModel: RemoteViewModel by viewModels()

    private var hidService: HidService? = null
    private var serviceBound = false

    private lateinit var favoritesAdapter: FavoritesAdapter

    private var stripPressStart = 0L
    private val ADMIN_LONG_PRESS_MS = 5000L

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as HidService.HidBinder
            hidService = b.getService()
            viewModel.hidService = hidService
            serviceBound = true
            observeConnectionState()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            hidService = null
            viewModel.hidService = null
            serviceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startAndBindService()
    }

    // ---- Lifecycle ----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setupFavoritesGrid()
        setupFixedControls()
        setupConnectionStrip()
        observeViewModel()
        checkPermissionsAndStart()
    }

    override fun onDestroy() {
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false }
        super.onDestroy()
    }

    // ---- Permissions ----

    private fun checkPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (needed.isEmpty()) startAndBindService() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startAndBindService() {
        val intent = Intent(this, HidService::class.java).apply { action = HidService.ACTION_START }
        ContextCompat.startForegroundService(this, intent)
        bindService(Intent(this, HidService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ---- Favorites Grid ----

    private fun setupFavoritesGrid() {
        val imagesDir = File(filesDir, "config/images")
        favoritesAdapter = FavoritesAdapter(
            context = this,
            items = emptyList(),
            imagesDir = imagesDir,
            onFavoriteClick = { fav -> viewModel.sendFavorite(fav) }
        )
        binding.favoritesGrid.adapter = favoritesAdapter
        binding.favoritesGrid.layoutManager = GridLayoutManager(this, 2)
    }

    // ---- Fixed Controls ----

    private fun setupFixedControls() {
        with(binding) {
            btnVolUp.setOnClickListener    { animatePress(it as Button); viewModel.sendConsumerKey(HidKeyCodes.CONSUMER_VOLUME_UP,   "Volume Up") }
            btnVolDown.setOnClickListener  { animatePress(it as Button); viewModel.sendConsumerKey(HidKeyCodes.CONSUMER_VOLUME_DOWN, "Volume Down") }
            btnMute.setOnClickListener     { animatePress(it as Button); viewModel.sendConsumerKey(HidKeyCodes.CONSUMER_MUTE,        "Mute") }
            btnUp.setOnClickListener       { animatePress(it as Button); viewModel.sendKeyboardKey(HidKeyCodes.KEY_ARROW_UP,    label = "Up") }
            btnDown.setOnClickListener     { animatePress(it as Button); viewModel.sendKeyboardKey(HidKeyCodes.KEY_ARROW_DOWN,  label = "Down") }
            btnLeft.setOnClickListener     { animatePress(it as Button); viewModel.sendKeyboardKey(HidKeyCodes.KEY_ARROW_LEFT,  label = "Left") }
            btnRight.setOnClickListener    { animatePress(it as Button); viewModel.sendKeyboardKey(HidKeyCodes.KEY_ARROW_RIGHT, label = "Right") }
            btnOk.setOnClickListener       { animatePress(it as Button); viewModel.sendKeyboardKey(HidKeyCodes.KEY_ENTER,       label = "OK") }
            btnHome.setOnClickListener     { animatePress(it as Button); viewModel.sendKeyboardKey(HidKeyCodes.KEY_HOME,        label = "Home") }
            btnBack.setOnClickListener     { animatePress(it as Button); viewModel.sendKeyboardKey(HidKeyCodes.KEY_ESCAPE,      label = "Back") }
            btnGuide.setOnClickListener    { animatePress(it as Button); viewModel.sendKeyboardKey(HidKeyCodes.KEY_F1,          label = "Guide") }
            btnLastChannel.setOnClickListener { animatePress(it as Button); viewModel.sendLastChannel() }
            btnPower.setOnClickListener    { animatePress(it as Button); viewModel.sendConsumerKey(HidKeyCodes.CONSUMER_POWER,  "Power") }
            btnReconnect.setOnClickListener { viewModel.reconnect() }
        }
    }

    // ---- Connection Strip (Admin Mode entry) ----

    private fun setupConnectionStrip() {
        binding.connectionStrip.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN  -> stripPressStart = System.currentTimeMillis()
                MotionEvent.ACTION_UP    -> {
                    if (System.currentTimeMillis() - stripPressStart >= ADMIN_LONG_PRESS_MS) openAdminMode()
                    stripPressStart = 0L
                }
                MotionEvent.ACTION_CANCEL -> stripPressStart = 0L
            }
            true
        }
    }

    private fun openAdminMode() { startActivity(Intent(this, AdminActivity::class.java)) }

    // ---- Observers ----

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.config.collectLatest { config ->
                config ?: return@collectLatest
                applyConfig(config)
            }
        }
        lifecycleScope.launch {
            viewModel.statusMessage.collectLatest { msg ->
                binding.tvStatusMessage.text = msg
            }
        }
    }

    private fun observeConnectionState() {
        val svc = hidService ?: return
        lifecycleScope.launch {
            svc.connectionState.collectLatest { state ->
                updateConnectionUI(state)
                updateButtonsEnabled(state is ConnectionState.Connected)
            }
        }
    }

    /**
     * Apply configuration to the UI.
     * Controls visibility uses View.GONE so no empty space remains when a button is hidden.
     * The LinearLayout weight system automatically reflows remaining visible buttons.
     */
    private fun applyConfig(config: AppConfig) {
        // Update favorites grid
        val displayFavs = config.favorites.take(config.favoritesCount)
        favoritesAdapter.updateItems(displayFavs)
        val spanCount = when (config.favoritesCount) { 12 -> 3; else -> 2 }
        (binding.favoritesGrid.layoutManager as GridLayoutManager).spanCount = spanCount

        // Apply controls visibility — all six toggleable controls + Last Channel
        applyControlsVisibility(config.controlsVisibility)
    }

    /**
     * Set each toggleable button to VISIBLE or GONE based on the caregiver's settings.
     * GONE removes the button from layout flow entirely — no placeholder space remains.
     */
    private fun applyControlsVisibility(vis: ControlsVisibility) {
        binding.btnPower.visibility       = if (vis.showPower)       View.VISIBLE else View.GONE
        binding.btnVolUp.visibility       = if (vis.showVolumeUp)    View.VISIBLE else View.GONE
        binding.btnVolDown.visibility     = if (vis.showVolumeDown)  View.VISIBLE else View.GONE
        binding.btnMute.visibility        = if (vis.showMute)        View.VISIBLE else View.GONE
        binding.btnGuide.visibility       = if (vis.showGuide)       View.VISIBLE else View.GONE
        binding.btnBack.visibility        = if (vis.showBack)        View.VISIBLE else View.GONE
        binding.btnLastChannel.visibility = if (vis.showLastChannel) View.VISIBLE else View.GONE
    }

    private fun updateConnectionUI(state: ConnectionState) {
        when (state) {
            is ConnectionState.Connected -> {
                binding.statusDot.setBackgroundResource(R.drawable.dot_connected)
                binding.tvConnectionStatus.text = state.deviceName
                binding.btnReconnect.visibility = View.GONE
            }
            is ConnectionState.Disconnected -> {
                binding.statusDot.setBackgroundResource(R.drawable.dot_disconnected)
                binding.tvConnectionStatus.setText(R.string.disconnected)
                binding.btnReconnect.visibility = View.VISIBLE
            }
            is ConnectionState.Waiting -> {
                binding.statusDot.setBackgroundResource(R.drawable.dot_waiting)
                binding.tvConnectionStatus.setText(R.string.waiting_for_connection)
                binding.btnReconnect.visibility = View.GONE
            }
            is ConnectionState.BluetoothUnavailable -> {
                binding.statusDot.setBackgroundResource(R.drawable.dot_disconnected)
                binding.tvConnectionStatus.setText(R.string.bluetooth_unavailable)
                binding.btnReconnect.visibility = View.GONE
            }
            else -> {
                binding.statusDot.setBackgroundResource(R.drawable.dot_disconnected)
                binding.tvConnectionStatus.setText(R.string.not_connected)
                binding.btnReconnect.visibility = View.VISIBLE
            }
        }
    }

    private fun updateButtonsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.4f
        binding.fixedControlsPanel.alpha = alpha
        binding.favoritesGrid.alpha = alpha
        setControlsEnabled(binding.fixedControlsPanel, enabled)
    }

    private fun setControlsEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) setControlsEnabled(view.getChildAt(i), enabled)
        }
    }

    private fun animatePress(btn: Button) {
        btn.isPressed = true
        btn.postDelayed({ btn.isPressed = false }, 150)
    }
}
