package com.elderlyremote.bluetooth

/** Represents the Bluetooth HID connection lifecycle states. */
sealed class ConnectionState {
    /** HID profile not yet registered; app starting up. */
    object Idle : ConnectionState()

    /** HID profile registered; waiting for host device to connect. */
    object Waiting : ConnectionState()

    /** Host device is connected and HID reports can be sent. */
    data class Connected(val deviceName: String) : ConnectionState()

    /** Connection was lost; user can trigger reconnect. */
    object Disconnected : ConnectionState()

    /** Bluetooth is not available or not enabled on this device. */
    object BluetoothUnavailable : ConnectionState()
}
