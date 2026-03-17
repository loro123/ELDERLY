package com.elderlyremote.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.elderlyremote.bluetooth.HidKeyCodes
import com.elderlyremote.model.AppConfig
import com.elderlyremote.model.ButtonSize
import com.elderlyremote.model.ControlsVisibility
import com.elderlyremote.model.FavoriteConfig
import com.elderlyremote.model.FavoriteType
import com.elderlyremote.model.LastChannelConfig
import com.elderlyremote.model.MacroStep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ConfigRepository(private val context: Context) {

    companion object {
        private const val TAG = "ConfigRepository"
        private const val CONFIG_DIR = "config"
        private const val IMAGES_DIR = "config/images"
        private const val CONFIG_FILE = "config/config.json"
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(MacroStep::class.java, MacroStepAdapter())
        .setPrettyPrinting()
        .create()

    // ---- Directory helpers ----

    private fun configDir(): File = File(context.filesDir, CONFIG_DIR).also { it.mkdirs() }
    private fun imagesDir(): File = File(context.filesDir, IMAGES_DIR).also { it.mkdirs() }
    private fun configFile(): File = File(context.filesDir, CONFIG_FILE)

    // ---- Load / Save ----

    suspend fun loadConfig(): AppConfig = withContext(Dispatchers.IO) {
        val file = configFile()
        if (!file.exists()) return@withContext createDefaultConfig()
        try {
            val json = file.readText()
            val dto = gson.fromJson(json, ConfigDto::class.java)
            dto.toAppConfig()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config, using defaults: ${e.message}")
            createDefaultConfig()
        }
    }

    suspend fun saveConfig(config: AppConfig) = withContext(Dispatchers.IO) {
        try {
            configDir()
            val dto = ConfigDto.fromAppConfig(config)
            configFile().writeText(gson.toJson(dto))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config: ${e.message}")
        }
    }

    // ---- PIN helpers ----

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, hash: String): Boolean = hashPin(pin) == hash

    fun isPinSet(config: AppConfig): Boolean = config.pinHash.isNotEmpty()

    // ---- Image management ----

    suspend fun importImage(uri: Uri, desiredName: String): String? = withContext(Dispatchers.IO) {
        try {
            val ext = context.contentResolver.getType(uri)?.let {
                when {
                    it.contains("png") -> ".png"
                    it.contains("gif") -> ".gif"
                    else -> ".jpg"
                }
            } ?: ".jpg"
            val fileName = "${desiredName.replace("[^a-zA-Z0-9_]".toRegex(), "_")}$ext"
            val dest = File(imagesDir(), fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            fileName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import image: ${e.message}")
            null
        }
    }

    fun getImageFile(fileName: String): File = File(imagesDir(), fileName)

    fun imageExists(fileName: String?): Boolean =
        fileName != null && File(imagesDir(), fileName).exists()

    // ---- Export / Import ZIP ----

    suspend fun exportToZip(outputUri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                val configFile = configFile()
                if (configFile.exists()) {
                    zip.putNextEntry(ZipEntry("config/config.json"))
                    configFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                imagesDir().listFiles()?.forEach { imgFile ->
                    zip.putNextEntry(ZipEntry("config/images/${imgFile.name}"))
                    imgFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun importFromZip(inputUri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(inputUri)?.use { inp ->
            ZipInputStream(inp).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val dest = File(context.filesDir, entry.name)
                    dest.parentFile?.mkdirs()
                    if (!entry.isDirectory) {
                        FileOutputStream(dest).use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    // ---- Default config factory ----

    private fun createDefaultConfig(): AppConfig {
        val defaults = AppConfig(favoritesCount = 6)
        for (i in 1..6) {
            defaults.favorites.add(FavoriteConfig(id = i, label = "Channel $i", digits = ""))
        }
        return defaults
    }
}

// ---- Gson adapter for sealed MacroStep ----

internal class MacroStepAdapter : JsonSerializer<MacroStep>, JsonDeserializer<MacroStep> {
    override fun serialize(src: MacroStep, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        when (src) {
            is MacroStep.KeyPress -> {
                obj.addProperty("type", "KEY_PRESS")
                obj.addProperty("keyCode", src.keyCode.toInt())
                obj.addProperty("modifier", src.modifier.toInt())
            }
            is MacroStep.DigitSequence -> {
                obj.addProperty("type", "DIGIT_SEQUENCE")
                obj.addProperty("digits", src.digits)
                obj.addProperty("delayMs", src.delayMs)
            }
            is MacroStep.DelayStep -> {
                obj.addProperty("type", "DELAY")
                obj.addProperty("delayMs", src.delayMs)
            }
            is MacroStep.RepeatKey -> {
                obj.addProperty("type", "REPEAT_KEY")
                obj.addProperty("keyCode", src.keyCode.toInt())
                obj.addProperty("count", src.count)
                obj.addProperty("modifier", src.modifier.toInt())
            }
        }
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MacroStep {
        val obj = json.asJsonObject
        return when (obj.get("type").asString) {
            "KEY_PRESS" -> MacroStep.KeyPress(
                keyCode = obj.get("keyCode").asByte,
                modifier = obj.get("modifier").asByte
            )
            "DIGIT_SEQUENCE" -> MacroStep.DigitSequence(
                digits = obj.get("digits").asString,
                delayMs = obj.get("delayMs").asLong
            )
            "DELAY" -> MacroStep.DelayStep(delayMs = obj.get("delayMs").asLong)
            "REPEAT_KEY" -> MacroStep.RepeatKey(
                keyCode = obj.get("keyCode").asByte,
                count = obj.get("count").asInt,
                modifier = obj.get("modifier").asByte
            )
            else -> MacroStep.DelayStep(0)
        }
    }
}

// ---- Flat DTO for JSON serialization ----

internal data class FavoriteDto(
    val id: Int,
    val label: String,
    val imageFile: String?,
    val type: String,
    val digits: String,
    val digitDelayMs: Long,
    val sendEnter: Boolean,
    val singleKeyCode: Int,
    val singleModifier: Int,
    val macroSteps: List<JsonElement>
)

internal data class ConfigDto(
    val version: Int,
    val pinHash: String,
    val favoritesCount: Int,
    val globalDigitDelayMs: Long,
    val buttonSize: String,
    // Controls visibility — all six toggleable controls + Last Channel
    val showPower: Boolean,
    val showVolumeUp: Boolean,
    val showVolumeDown: Boolean,
    val showMute: Boolean,
    val showGuide: Boolean,
    val showBack: Boolean,
    val showLastChannel: Boolean,
    // Last Channel keycode
    val lastChannelKeyCode: Int,
    val lastChannelModifier: Int,
    val favorites: List<FavoriteDto>
) {
    fun toAppConfig(): AppConfig {
        val gson = GsonBuilder()
            .registerTypeHierarchyAdapter(MacroStep::class.java, MacroStepAdapter())
            .create()
        return AppConfig(
            version = version,
            pinHash = pinHash,
            favoritesCount = favoritesCount,
            globalDigitDelayMs = globalDigitDelayMs,
            buttonSize = try { ButtonSize.valueOf(buttonSize) } catch (e: Exception) { ButtonSize.LARGE },
            controlsVisibility = ControlsVisibility(
                showPower = showPower,
                showVolumeUp = showVolumeUp,
                showVolumeDown = showVolumeDown,
                showMute = showMute,
                showGuide = showGuide,
                showBack = showBack,
                showLastChannel = showLastChannel
            ),
            lastChannelConfig = LastChannelConfig(
                lastChannelKeyCode.toByte(),
                lastChannelModifier.toByte()
            ),
            favorites = favorites.map { dto ->
                FavoriteConfig(
                    id = dto.id,
                    label = dto.label,
                    imageFile = dto.imageFile,
                    type = try { FavoriteType.valueOf(dto.type) } catch (e: Exception) { FavoriteType.DIGIT_SEQUENCE },
                    digits = dto.digits,
                    digitDelayMs = dto.digitDelayMs,
                    sendEnter = dto.sendEnter,
                    singleKeyCode = dto.singleKeyCode.toByte(),
                    singleModifier = dto.singleModifier.toByte(),
                    macroSteps = dto.macroSteps.map { elem ->
                        gson.fromJson(elem, MacroStep::class.java)
                    }.toMutableList()
                )
            }.toMutableList()
        )
    }

    companion object {
        fun fromAppConfig(config: AppConfig): ConfigDto {
            val gson = GsonBuilder()
                .registerTypeHierarchyAdapter(MacroStep::class.java, MacroStepAdapter())
                .create()
            return ConfigDto(
                version = config.version,
                pinHash = config.pinHash,
                favoritesCount = config.favoritesCount,
                globalDigitDelayMs = config.globalDigitDelayMs,
                buttonSize = config.buttonSize.name,
                showPower = config.controlsVisibility.showPower,
                showVolumeUp = config.controlsVisibility.showVolumeUp,
                showVolumeDown = config.controlsVisibility.showVolumeDown,
                showMute = config.controlsVisibility.showMute,
                showGuide = config.controlsVisibility.showGuide,
                showBack = config.controlsVisibility.showBack,
                showLastChannel = config.controlsVisibility.showLastChannel,
                lastChannelKeyCode = config.lastChannelConfig.keyCode.toInt() and 0xFF,
                lastChannelModifier = config.lastChannelConfig.modifier.toInt() and 0xFF,
                favorites = config.favorites.map { fav ->
                    FavoriteDto(
                        id = fav.id,
                        label = fav.label,
                        imageFile = fav.imageFile,
                        type = fav.type.name,
                        digits = fav.digits,
                        digitDelayMs = fav.digitDelayMs,
                        sendEnter = fav.sendEnter,
                        singleKeyCode = fav.singleKeyCode.toInt() and 0xFF,
                        singleModifier = fav.singleModifier.toInt() and 0xFF,
                        macroSteps = fav.macroSteps.map { step -> gson.toJsonTree(step) }
                    )
                }
            )
        }
    }
}
