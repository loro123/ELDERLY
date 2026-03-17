package com.elderlyremote.ui.admin

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elderlyremote.ElderlyRemoteApp
import com.elderlyremote.bluetooth.HidKeyCodes
import com.elderlyremote.bluetooth.HidService
import com.elderlyremote.model.AppConfig
import com.elderlyremote.model.ButtonSize
import com.elderlyremote.model.FavoriteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ElderlyRemoteApp).configRepository

    private val _config = MutableStateFlow<AppConfig?>(null)
    val config: StateFlow<AppConfig?> = _config

    private val _testLog = MutableStateFlow("")
    val testLog: StateFlow<String> = _testLog

    private val _exportEvent = MutableSharedFlow<Unit>()
    val exportEvent: SharedFlow<Unit> = _exportEvent

    private val _importEvent = MutableSharedFlow<Unit>()
    val importEvent: SharedFlow<Unit> = _importEvent

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent

    var hidService: HidService? = null

    init { loadConfig() }

    fun loadConfig() {
        viewModelScope.launch { _config.value = repo.loadConfig() }
    }

    // ---- PIN ----

    fun isPinSet(): Boolean = _config.value?.let { repo.isPinSet(it) } ?: false

    fun verifyPin(pin: String): Boolean {
        val cfg = _config.value ?: return false
        return repo.verifyPin(pin, cfg.pinHash)
    }

    fun setPin(pin: String) {
        val cfg = _config.value ?: return
        cfg.pinHash = repo.hashPin(pin)
        saveConfig()
    }

    // ---- Favorites ----

    fun setFavoritesCount(count: Int) {
        val cfg = _config.value ?: return
        if (count !in AppConfig.VALID_FAVORITES_COUNTS) return
        cfg.favoritesCount = count
        while (cfg.favorites.size < count) {
            val nextId = (cfg.favorites.maxOfOrNull { it.id } ?: 0) + 1
            cfg.favorites.add(FavoriteConfig(id = nextId, label = "Channel $nextId"))
        }
        _config.value = cfg.copy()
        saveConfig()
    }

    fun updateFavorite(updated: FavoriteConfig) {
        val cfg = _config.value ?: return
        val idx = cfg.favorites.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            cfg.favorites[idx] = updated
            _config.value = cfg.copy()
            saveConfig()
        }
    }

    fun reorderFavorites(fromPos: Int, toPos: Int) {
        val cfg = _config.value ?: return
        val item = cfg.favorites.removeAt(fromPos)
        cfg.favorites.add(toPos, item)
        _config.value = cfg.copy()
        saveConfig()
    }

    fun setButtonSize(size: ButtonSize) {
        val cfg = _config.value ?: return
        cfg.buttonSize = size
        _config.value = cfg.copy()
        saveConfig()
    }

    fun importImageForFavorite(favId: Int, uri: Uri, label: String) {
        viewModelScope.launch {
            val fileName = repo.importImage(uri, label)
            if (fileName != null) {
                val cfg = _config.value ?: return@launch
                val idx = cfg.favorites.indexOfFirst { it.id == favId }
                if (idx >= 0) {
                    cfg.favorites[idx] = cfg.favorites[idx].copy(imageFile = fileName)
                    _config.value = cfg.copy()
                    saveConfig()
                }
            }
        }
    }

    // ---- Controls Visibility (all six + Last Channel) ----

    fun setShowPower(show: Boolean) = updateVisibility { it.showPower = show }
    fun setShowVolumeUp(show: Boolean) = updateVisibility { it.showVolumeUp = show }
    fun setShowVolumeDown(show: Boolean) = updateVisibility { it.showVolumeDown = show }
    fun setShowMute(show: Boolean) = updateVisibility { it.showMute = show }
    fun setShowGuide(show: Boolean) = updateVisibility { it.showGuide = show }
    fun setShowBack(show: Boolean) = updateVisibility { it.showBack = show }
    fun setShowLastChannel(show: Boolean) = updateVisibility { it.showLastChannel = show }

    private fun updateVisibility(block: (com.elderlyremote.model.ControlsVisibility) -> Unit) {
        val cfg = _config.value ?: return
        block(cfg.controlsVisibility)
        _config.value = cfg.copy()
        saveConfig()
    }

    fun setLastChannelKeyCode(keyCode: Byte, modifier: Byte) {
        val cfg = _config.value ?: return
        cfg.lastChannelConfig.keyCode = keyCode
        cfg.lastChannelConfig.modifier = modifier
        _config.value = cfg.copy()
        saveConfig()
    }

    // ---- Import / Export ----

    fun triggerExport() { viewModelScope.launch { _exportEvent.emit(Unit) } }
    fun triggerImport() { viewModelScope.launch { _importEvent.emit(Unit) } }

    fun exportToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.exportToZip(uri)
                _toastEvent.emit("Configuration exported successfully.")
            } catch (e: Exception) {
                _toastEvent.emit("Export failed: ${e.message}")
            }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.importFromZip(uri)
                loadConfig()
                _toastEvent.emit("Configuration imported successfully.")
            } catch (e: Exception) {
                _toastEvent.emit("Import failed: ${e.message}")
            }
        }
    }

    // ---- Test Pad ----

    fun testSendKey(keyCode: Byte, modifier: Byte = HidKeyCodes.MOD_NONE, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            hidService?.sendKeyboardKey(keyCode, modifier)
            appendTestLog("Sent: $label")
        }
    }

    fun testSendConsumer(usage: Int, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            hidService?.sendConsumerKey(usage)
            appendTestLog("Sent: $label")
        }
    }

    fun testSendSampleSequence(digits: String, delayMs: Long, sendEnter: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            hidService?.sendDigitSequence(digits, delayMs, sendEnter)
            val enterStr = if (sendEnter) ", Enter: on" else ", Enter: off"
            appendTestLog("Sent: ${digits.toCharArray().joinToString(" ")} (delay=${delayMs}ms$enterStr)")
        }
    }

    private fun appendTestLog(msg: String) {
        viewModelScope.launch(Dispatchers.Main) { _testLog.value = msg }
    }

    // ---- Persistence ----

    private fun saveConfig() {
        val cfg = _config.value ?: return
        viewModelScope.launch { repo.saveConfig(cfg) }
    }
}
