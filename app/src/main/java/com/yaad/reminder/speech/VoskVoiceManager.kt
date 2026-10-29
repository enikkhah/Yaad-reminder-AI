package com.yaad.reminder.speech

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Wraps the offline Vosk Android SpeechService for 16kHz Persian speech recognition.
 * Expects the unpacked lightweight Persian model inside `src/main/assets/model`.
 */
class VoskVoiceManager(
    private val context: Context,
    private val onModelReady: (Boolean) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialResultText: (String) -> Unit,
    private val onFinalResultText: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit
) : RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isListening = false

    companion object {
        private const val SAMPLE_RATE = 16000.0f
        private const val ASSET_MODEL_DIR = "model"
    }

    /**
     * Unpacks the Persian model from APK assets (`src/main/assets/model`) into internal storage.
     */
    fun initModel() {
        StorageService.unpack(
            context,
            ASSET_MODEL_DIR,
            "model-fa-unpacked",
            { unpackedModel: Model ->
                this.model = unpackedModel
                onModelReady(true)
            },
            { exception: IOException ->
                onModelReady(false)
                onErrorMessage("خطا در بارگذاری مدل صوتی آفلاین: ${exception.localizedMessage}")
            }
        )
    }

    fun toggleListening() {
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        val activeModel = model ?: run {
            onErrorMessage("مدل صوتی فارسی در حال آماده‌سازی است...")
            return
        }

        try {
            val recognizer = Recognizer(activeModel, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE).also { service ->
                service.startListening(this)
                isListening = true
                onListeningChanged(true)
            }
        } catch (e: IOException) {
            onErrorMessage("خطا در دسترسی به میکروفون: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        isListening = false
        onListeningChanged(false)
    }

    override fun onPartialResult(hypothesis: String?) {
        val partial = parseVoskJson(hypothesis, key = "partial")
        if (partial.isNotEmpty()) {
            onPartialResultText(partial)
        }
    }

    override fun onResult(hypothesis: String?) {
        val text = parseVoskJson(hypothesis, key = "text")
        if (text.isNotEmpty()) {
            stopListening()
            onFinalResultText(text)
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        val text = parseVoskJson(hypothesis, key = "text")
        if (text.isNotEmpty()) {
            onFinalResultText(text)
        }
        onListeningChanged(false)
        isListening = false
    }

    override fun onError(exception: Exception?) {
        stopListening()
        onErrorMessage(exception?.localizedMessage ?: "خطای ناشناخته در تشخیص گفتار")
    }

    override fun onTimeout() {
        stopListening()
    }

    fun release() {
        stopListening()
        model?.close()
        model = null
    }

    private fun parseVoskJson(rawJson: String?, key: String): String {
        if (rawJson.isNullOrBlank()) return ""
        return runCatching {
            JSONObject(rawJson).optString(key, "").trim()
        }.getOrDefault("")
    }
}
