package com.elderlyremote.model

import com.elderlyremote.bluetooth.HidKeyCodes

// ---- Favorite Action Types ----

/** Discriminator for the three favorite action types. */
enum class FavoriteType { DIGIT_SEQUENCE, SINGLE_KEY, MACRO }

/** A single step within a macro sequence. */
sealed class MacroStep {
    data class KeyPress(val keyCode: Byte, val modifier: Byte = HidKeyCodes.MOD_NONE) : MacroStep()
    data class DigitSequence(val digits: String, val delayMs: Long = 200L) : MacroStep()
    data class DelayStep(val delayMs: Long) : MacroStep()
    data class RepeatKey(val keyCode: Byte, val count: Int, val modifier: Byte = HidKeyCodes.MOD_NONE) : MacroStep()
}

/**
 * Configuration for a single favorite button.
 *
 * @param id         Stable identifier (1-based index, preserved across reorders)
 * @param label      Display label shown on the button
 * @param imageFile  Filename of the optional image in config/images/ (null = text only)
 * @param type       Action type: DIGIT_SEQUENCE, SINGLE_KEY, or MACRO
 *
 * Type A — DIGIT_SEQUENCE fields:
 * @param digits        Channel digit string, e.g. "202"
 * @param digitDelayMs  Inter-digit delay in ms (100–500)
 * @param sendEnter     Whether to send Enter after the digit sequence
 *
 * Type B — SINGLE_KEY fields:
 * @param singleKeyCode  HID keycode to send
 * @param singleModifier Modifier bitmask (0 = none)
 *
 * Type C — MACRO fields:
 * @param macroSteps  Ordered list of macro steps
 */
data class FavoriteConfig(
    val id: Int,
    var label: String,
    var imageFile: String? = null,
    var type: FavoriteType = FavoriteType.DIGIT_SEQUENCE,

    // Type A
    var digits: String = "",
    var digitDelayMs: Long = 200L,
    var sendEnter: Boolean = false,

    // Type B
    var singleKeyCode: Byte = HidKeyCodes.KEY_ENTER,
    var singleModifier: Byte = HidKeyCodes.MOD_NONE,

    // Type C
    var macroSteps: MutableList<MacroStep> = mutableListOf()
)

// ---- Controls Visibility ----

/**
 * Per-button visibility toggles for the fixed controls panel.
 * All default to true (visible). Stored as individual booleans in config JSON.
 * When a control is false its button is set to View.GONE so no empty space remains.
 */
data class ControlsVisibility(
    var showPower: Boolean = true,
    var showVolumeUp: Boolean = true,
    var showVolumeDown: Boolean = true,
    var showMute: Boolean = true,
    var showGuide: Boolean = true,
    var showBack: Boolean = true,
    var showLastChannel: Boolean = true
)

// ---- Last Channel Config ----

data class LastChannelConfig(
    var keyCode: Byte = HidKeyCodes.LAST_CHANNEL_DEFAULT_KEY,
    var modifier: Byte = HidKeyCodes.LAST_CHANNEL_DEFAULT_MOD
)

// ---- Button Size ----

enum class ButtonSize { LARGE, EXTRA_LARGE }

// ---- Root App Configuration ----

data class AppConfig(
    val version: Int = 1,
    var pinHash: String = "",           // SHA-256 hex of the PIN
    var favoritesCount: Int = 6,        // 4, 6, 8, or 12
    var globalDigitDelayMs: Long = 200L,
    var buttonSize: ButtonSize = ButtonSize.LARGE,
    var controlsVisibility: ControlsVisibility = ControlsVisibility(),
    var lastChannelConfig: LastChannelConfig = LastChannelConfig(),
    var favorites: MutableList<FavoriteConfig> = mutableListOf()
) {
    companion object {
        val VALID_FAVORITES_COUNTS = listOf(4, 6, 8, 12)
    }
}
