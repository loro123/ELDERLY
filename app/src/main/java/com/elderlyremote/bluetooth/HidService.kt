package com.elderlyremote.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.elderlyremote.R
import com.elderlyremote.ui.remote.RemoteActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class HidService : Service() {

    companion object {
        private const val TAG = "HidService"
        private const val NOTIFICATION_CHANNEL_ID = "hid_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.elderlyremote.HID_START"
        const val ACTION_STOP = "com.elderlyremote.HID_STOP"
        const val ACTION_RECONNECT = "com.elderlyremote.HID_RECONNECT"
    }

    inner class HidBinder : Binder() {
        fun getService(): HidService = this@HidService
    }

    private val binder = HidBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val executor = Executors.newSingleThreadExecutor()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // ---- HID Device Callback ----

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered device=$pluggedDevice")
            if (registered) {
                _connectionState.value = ConnectionState.Waiting
                // If we have a previously paired device, the host will reconnect automatically.
                // We do NOT call connect() — the TV initiates the connection.
            } else {
                _connectionState.value = ConnectionState.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=${device.name} state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
                    _connectionState.value = ConnectionState.Connected(name)
                    updateNotification("Connected: $name")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    _connectionState.value = ConnectionState.Disconnected
                    updateNotification("Disconnected — tap to reconnect")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = ConnectionState.Waiting
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    // transitional — no state change needed
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            // Respond with an empty report; required by the HID protocol
            hidDevice?.replyReport(device, type, id, ByteArray(0))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    // ---- Service Lifecycle ----

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("ElderlyRemote active"))

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothUnavailable
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> registerHidApp()
            ACTION_RECONNECT -> registerHidApp()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        unregisterHidApp()
        serviceScope.cancel()
        executor.shutdown()
        super.onDestroy()
    }

    // ---- HID Registration ----

    fun registerHidApp() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothUnavailable
            return
        }

        try {
            adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = proxy as BluetoothHidDevice
                        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                            "ElderlyRemote",
                            "Elderly-Friendly TV Remote",
                            "ElderlyRemote",
                            BluetoothHidDevice.SUBCLASS1_COMBO,
                            HidDescriptor.DESCRIPTOR
                        )
                        hidDevice?.registerApp(sdpSettings, null, null, executor, hidCallback)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    hidDevice = null
                    _connectionState.value = ConnectionState.Disconnected
                }
            }, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission denied: ${e.message}")
            _connectionState.value = ConnectionState.BluetoothUnavailable
        }
    }

    private fun unregisterHidApp() {
        try {
            hidDevice?.unregisterApp()
        } catch (e: Exception) {
            Log.w(TAG, "unregisterApp failed: ${e.message}")
        }
    }

    // ---- Report Sending ----

    /**
     * Send a keyboard key press followed by a release report.
     * Must be called from a coroutine (not the main thread for the delay).
     */
    suspend fun sendKeyboardKey(keyCode: Byte, modifier: Byte = HidKeyCodes.MOD_NONE) {
        val device = connectedDevice ?: return
        val hid = hidDevice ?: return
        try {
            hid.sendReport(device, HidDescriptor.REPORT_ID_KEYBOARD.toInt(),
                HidReportBuilder.keyboardPress(keyCode, modifier))
            delay(16) // brief hold (~1 frame)
            hid.sendReport(device, HidDescriptor.REPORT_ID_KEYBOARD.toInt(),
                HidReportBuilder.keyboardRelease())
        } catch (e: SecurityException) {
            Log.e(TAG, "sendReport permission denied: ${e.message}")
        }
    }

    /**
     * Send a consumer control key press followed by a release report.
     */
    suspend fun sendConsumerKey(usage: Int) {
        val device = connectedDevice ?: return
        val hid = hidDevice ?: return
        try {
            hid.sendReport(device, HidDescriptor.REPORT_ID_CONSUMER.toInt(),
                HidReportBuilder.consumerPress(usage))
            delay(16)
            hid.sendReport(device, HidDescriptor.REPORT_ID_CONSUMER.toInt(),
                HidReportBuilder.consumerRelease())
        } catch (e: SecurityException) {
            Log.e(TAG, "sendReport permission denied: ${e.message}")
        }
    }

    /**
     * Send a digit sequence (e.g., "202") with configurable inter-digit delay.
     * Each digit is sent as press + release + delay.
     */
    suspend fun sendDigitSequence(
        digits: String,
        delayMs: Long = 200L,
        sendEnter: Boolean = false
    ) {
        for (ch in digits) {
            if (ch.isDigit()) {
                sendKeyboardKey(HidKeyCodes.digitToKeyCode(ch))
                delay(delayMs)
            }
        }
        if (sendEnter) {
            sendKeyboardKey(HidKeyCodes.KEY_ENTER)
        }
    }

    fun isConnected(): Boolean = connectedDevice != null &&
            _connectionState.value is ConnectionState.Connected

    // ---- Notification ----

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "ElderlyRemote Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Bluetooth HID connection status"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, RemoteActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ElderlyRemote")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_remote_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
