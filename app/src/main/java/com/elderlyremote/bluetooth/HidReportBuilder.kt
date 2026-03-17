package com.elderlyremote.bluetooth

/**
 * Builds HID report byte arrays for keyboard and consumer control reports.
 *
 * Keyboard report layout (8 bytes, Report ID 1):
 *   [0] modifier bitmask
 *   [1] reserved (0x00)
 *   [2..7] key usages (up to 6 simultaneous keys)
 *
 * Consumer report layout (2 bytes, Report ID 2):
 *   [0..1] usage value, little-endian 16-bit
 */
object HidReportBuilder {

    /** Keyboard key-down report with optional modifier. */
    fun keyboardPress(keyCode: Byte, modifier: Byte = HidKeyCodes.MOD_NONE): ByteArray {
        return ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE).also { report ->
            report[0] = modifier
            report[1] = 0x00
            report[2] = keyCode
            // bytes 3–7 remain 0x00 (no additional simultaneous keys)
        }
    }

    /** Keyboard key-up (all zeros) report. */
    fun keyboardRelease(): ByteArray = ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE)

    /** Consumer control key-down report. */
    fun consumerPress(usage: Int): ByteArray {
        return ByteArray(HidDescriptor.CONSUMER_REPORT_SIZE).also { report ->
            report[0] = (usage and 0xFF).toByte()
            report[1] = ((usage shr 8) and 0xFF).toByte()
        }
    }

    /** Consumer control key-up (all zeros) report. */
    fun consumerRelease(): ByteArray = ByteArray(HidDescriptor.CONSUMER_REPORT_SIZE)
}
