package com.elderlyremote.bluetooth

/**
 * Combined HID Report Descriptor: Keyboard (Report ID 1) + Consumer Control (Report ID 2).
 *
 * Report ID 1 — Keyboard (8 bytes):
 *   Byte 0: Modifier keys bitmask
 *   Byte 1: Reserved (0x00)
 *   Bytes 2–7: Up to 6 simultaneous key usages (Usage Page 0x07)
 *
 * Report ID 2 — Consumer Control (2 bytes):
 *   Bytes 0–1: Consumer usage (Usage Page 0x0C), little-endian 16-bit
 */
object HidDescriptor {

    val DESCRIPTOR: ByteArray = byteArrayOf(
        // ---- Keyboard (Report ID 1) ----
        0x05.toByte(), 0x01.toByte(),  // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),  // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),  // Collection (Application)
        0x85.toByte(), 0x01.toByte(),  //   Report ID (1)

        // Modifier keys (8 bits)
        0x05.toByte(), 0x07.toByte(),  //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),  //   Usage Minimum (224) — Left Control
        0x29.toByte(), 0xE7.toByte(),  //   Usage Maximum (231) — Right GUI
        0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),  //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),  //   Report Size (1)
        0x95.toByte(), 0x08.toByte(),  //   Report Count (8)
        0x81.toByte(), 0x02.toByte(),  //   Input (Data, Variable, Absolute)

        // Reserved byte
        0x95.toByte(), 0x01.toByte(),  //   Report Count (1)
        0x75.toByte(), 0x08.toByte(),  //   Report Size (8)
        0x81.toByte(), 0x01.toByte(),  //   Input (Constant)

        // Key array (6 keys)
        0x95.toByte(), 0x06.toByte(),  //   Report Count (6)
        0x75.toByte(), 0x08.toByte(),  //   Report Size (8)
        0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
        0x25.toByte(), 0x73.toByte(),  //   Logical Maximum (115)
        0x05.toByte(), 0x07.toByte(),  //   Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(),  //   Usage Minimum (0)
        0x29.toByte(), 0x73.toByte(),  //   Usage Maximum (115)
        0x81.toByte(), 0x00.toByte(),  //   Input (Data, Array, Absolute)

        0xC0.toByte(),                 // End Collection

        // ---- Consumer Control (Report ID 2) ----
        0x05.toByte(), 0x0C.toByte(),  // Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(),  // Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(),  // Collection (Application)
        0x85.toByte(), 0x02.toByte(),  //   Report ID (2)
        0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x03.toByte(), // Logical Maximum (1023)
        0x19.toByte(), 0x00.toByte(),  //   Usage Minimum (0)
        0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), // Usage Maximum (1023)
        0x75.toByte(), 0x10.toByte(),  //   Report Size (16)
        0x95.toByte(), 0x01.toByte(),  //   Report Count (1)
        0x81.toByte(), 0x00.toByte(),  //   Input (Data, Array, Absolute)
        0xC0.toByte()                  // End Collection
    )

    // SDP record for Bluetooth HID profile
    val SDP_RECORD: ByteArray = byteArrayOf(
        0x35.toByte(), 0x4E.toByte(),  // Data Element Sequence
        0x09.toByte(), 0x00.toByte(), 0x01.toByte(), // ServiceClassIDList
        0x35.toByte(), 0x03.toByte(),
        0x19.toByte(), 0x11.toByte(), 0x24.toByte(), // HID (0x1124)
        0x09.toByte(), 0x00.toByte(), 0x04.toByte(), // ProtocolDescriptorList
        0x35.toByte(), 0x0D.toByte(),
        0x35.toByte(), 0x06.toByte(),
        0x19.toByte(), 0x01.toByte(), 0x00.toByte(), // L2CAP
        0x09.toByte(), 0x00.toByte(), 0x11.toByte(), // PSM = 0x0011 (HID Control)
        0x35.toByte(), 0x03.toByte(),
        0x19.toByte(), 0x00.toByte(), 0x11.toByte(), // HIDP
        0x09.toByte(), 0x00.toByte(), 0x06.toByte(), // LanguageBaseAttributeIDList
        0x35.toByte(), 0x09.toByte(),
        0x09.toByte(), 0x65.toByte(), 0x6E.toByte(), // English
        0x09.toByte(), 0x00.toByte(), 0x6A.toByte(), // UTF-8
        0x09.toByte(), 0x01.toByte(), 0x00.toByte()  // Base attribute ID
    )

    // Report IDs
    const val REPORT_ID_KEYBOARD: Byte = 0x01
    const val REPORT_ID_CONSUMER: Byte = 0x02

    // Keyboard report size (excluding report ID): 8 bytes
    const val KEYBOARD_REPORT_SIZE = 8

    // Consumer report size (excluding report ID): 2 bytes
    const val CONSUMER_REPORT_SIZE = 2
}
