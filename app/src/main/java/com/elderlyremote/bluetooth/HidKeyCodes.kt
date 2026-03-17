package com.elderlyremote.bluetooth

/**
 * HID Usage values for Keyboard (Usage Page 0x07) and Consumer Control (Usage Page 0x0C).
 * All values are per the USB HID Usage Tables specification.
 */
object HidKeyCodes {

    // ---- Keyboard modifier bits (byte 0 of keyboard report) ----
    const val MOD_NONE: Byte = 0x00
    const val MOD_LEFT_CTRL: Byte = 0x01
    const val MOD_LEFT_SHIFT: Byte = 0x02
    const val MOD_LEFT_ALT: Byte = 0x04
    const val MOD_LEFT_GUI: Byte = 0x08
    const val MOD_RIGHT_CTRL: Byte = 0x10
    const val MOD_RIGHT_SHIFT: Byte = 0x20
    const val MOD_RIGHT_ALT: Byte = 0x40
    const val MOD_RIGHT_GUI: Byte = 0x80.toByte()

    // ---- Digits (Usage Page 0x07) ----
    const val KEY_0: Byte = 0x27
    const val KEY_1: Byte = 0x1E
    const val KEY_2: Byte = 0x1F
    const val KEY_3: Byte = 0x20
    const val KEY_4: Byte = 0x21
    const val KEY_5: Byte = 0x22
    const val KEY_6: Byte = 0x23
    const val KEY_7: Byte = 0x24
    const val KEY_8: Byte = 0x25
    const val KEY_9: Byte = 0x26

    // ---- Navigation (Usage Page 0x07) ----
    const val KEY_ENTER: Byte = 0x28
    const val KEY_ESCAPE: Byte = 0x29   // Back
    const val KEY_HOME: Byte = 0x4A
    const val KEY_ARROW_RIGHT: Byte = 0x4F
    const val KEY_ARROW_LEFT: Byte = 0x50
    const val KEY_ARROW_DOWN: Byte = 0x51
    const val KEY_ARROW_UP: Byte = 0x52

    // ---- Function key for Guide ----
    const val KEY_F1: Byte = 0x3A

    // ---- Consumer Control usages (Usage Page 0x0C) — 16-bit values ----
    const val CONSUMER_VOLUME_UP: Int = 0x00E9
    const val CONSUMER_VOLUME_DOWN: Int = 0x00EA
    const val CONSUMER_MUTE: Int = 0x00E2
    const val CONSUMER_POWER: Int = 0x0030   // Power/Sleep — best-effort

    // ---- Modifier aliases (short names used by LastChannelConfig) ----
    const val MOD_LCTRL:  Byte = MOD_LEFT_CTRL
    const val MOD_LSHIFT: Byte = MOD_LEFT_SHIFT
    const val MOD_LALT:   Byte = MOD_LEFT_ALT
    const val MOD_LGUI:   Byte = MOD_LEFT_GUI

    // ---- Last Channel default: ALT + LEFT ----
    // Sent as keyboard report with MOD_LEFT_ALT modifier + KEY_ARROW_LEFT
    const val LAST_CHANNEL_DEFAULT_MOD: Byte = MOD_LEFT_ALT
    const val LAST_CHANNEL_DEFAULT_KEY: Byte = KEY_ARROW_LEFT

    // ---- Helper: map digit character to HID keycode ----
    fun digitToKeyCode(digit: Char): Byte = when (digit) {
        '0' -> KEY_0
        '1' -> KEY_1
        '2' -> KEY_2
        '3' -> KEY_3
        '4' -> KEY_4
        '5' -> KEY_5
        '6' -> KEY_6
        '7' -> KEY_7
        '8' -> KEY_8
        '9' -> KEY_9
        else -> throw IllegalArgumentException("Not a digit: $digit")
    }
}
