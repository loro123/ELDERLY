package com.elderlyremote.ui.remote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elderlyremote.ElderlyRemoteApp
import com.elderlyremote.bluetooth.ConnectionState
import com.elderlyremote.bluetooth.HidKeyCodes
import com.elderlyremote.bluetooth.HidService
import com.elderlyremote.model.AppConfig
import com.elderlyremote.model.FavoriteConfig
import com.elderlyremote.model.FavoriteType
import com.elderlyremote.model.MacroStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ElderlyRemoteApp).configRepository

    private val _config = MutableStateFlow<AppConfig?>(null)
    val config: StateFlow<AppConfig?> = _config

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    // Reference set by RemoteActivity after service binding
    var hidService: HidService? = null

    val connectionState: StateFlow<ConnectionState>
        get() = hidService?.connectionState ?: MutableStateFlow(ConnectionState.Idle)

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            _config.value = repo.loadConfig()
        }
    }

    // ---- Key sending ----

    fun sendKeyboardKey(keyCode: Byte, modifier: Byte = HidKeyCodes.MOD_NONE, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            hidService?.sendKeyboardKey(keyCode, modifier)
            showStatus("Sent: $label")
        }
    }

    fun sendConsumerKey(usage: Int, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            hidService?.sendConsumerKey(usage)
            showStatus("Sent: $label")
        }
    }

    fun sendFavorite(fav: FavoriteConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            when (fav.type) {
                FavoriteType.DIGIT_SEQUENCE -> {
                    val delayMs = if (fav.digitDelayMs > 0) fav.digitDelayMs
                    else _config.value?.globalDigitDelayMs ?: 200L
                    hidService?.sendDigitSequence(fav.digits, delayMs, fav.sendEnter)
                    val enterSuffix = if (fav.sendEnter) " + Enter" else ""
                    showStatus("Sent: ${fav.digits}$enterSuffix")
                }
                FavoriteType.SINGLE_KEY -> {
                    hidService?.sendKeyboardKey(fav.singleKeyCode, fav.singleModifier)
                    showStatus("Sent: ${fav.label}")
                }
                FavoriteType.MACRO -> {
                    executeMacro(fav)
                    showStatus("Sent: ${fav.label} (macro)")
                }
            }
        }
    }

    private suspend fun executeMacro(fav: FavoriteConfig) {
        for (step in fav.macroSteps) {
            when (step) {
                is MacroStep.KeyPress ->
                    hidService?.sendKeyboardKey(step.keyCode, step.modifier)
                is MacroStep.DigitSequence ->
                    hidService?.sendDigitSequence(step.digits, step.delayMs, false)
                is MacroStep.DelayStep ->
                    delay(step.delayMs)
                is MacroStep.RepeatKey -> {
                    repeat(step.count) {
                        hidService?.sendKeyboardKey(step.keyCode, step.modifier)
                        delay(100)
                    }
                }
            }
        }
    }

    fun sendLastChannel() {
        val cfg = _config.value?.lastChannelConfig ?: return
        viewModelScope.launch(Dispatchers.IO) {
            hidService?.sendKeyboardKey(cfg.keyCode, cfg.modifier)
            showStatus("Sent: Last Channel")
        }
    }

    fun reconnect() {
        hidService?.registerHidApp()
    }

    private fun showStatus(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _statusMessage.value = msg
        }
    }
}
