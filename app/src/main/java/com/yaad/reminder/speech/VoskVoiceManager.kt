package com.yaad.reminder.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-accuracy offline Persian speech recognizer powered by Vosk and a custom hardware-tuned
 * [AudioRecord] pipeline (16kHz, CHANNEL_IN_MONO, PCM_16BIT) with native Android DSP
 * [NoiseSuppressor] and [AcousticEchoCanceler].
 */
class VoskVoiceManager(
    private val context: Context,
    private val onModelReady: (Boolean) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialResultText: (String) -> Unit,
    private val onFinalResultText: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit
) {

    @Volatile
    private var model: Model? = null

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    private val isRecording = AtomicBoolean(false)
    private var recognitionJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // 1. Hardware AudioRecord sample rate (16000 Hz) & explicit Vosk Recognizer float rate (16000.0f)
        private const val SAMPLE_RATE_HZ = 16000
        private const val VOSK_SAMPLE_RATE = 16000.0f
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // Multiplier applied to AudioRecord.getMinBufferSize to prevent frame drops during concurrent JNI decoding
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val ASSET_MODEL_DIR = "model"
    }

    /**
     * Unpacks the Persian Vosk model from APK assets (`src/main/assets/model`) into internal storage.
     */
    fun initModel() {
        StorageService.unpack(
            context,
            ASSET_MODEL_DIR,
            "model-fa-unpacked",
            { unpackedModel: Model ->
                this.model = unpackedModel
                postToMain { onModelReady(true) }
            },
            { exception: IOException ->
                postToMain {
                    onModelReady(false)
                    onErrorMessage("خطا در بارگذاری مدل صوتی آفلاین: ${exception.localizedMessage}")
                }
            }
        )
    }

    fun toggleListening() {
        if (isRecording.get()) {
            stopListening()
        } else {
            startListening()
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        val activeModel = model ?: run {
            postToMain { onErrorMessage("مدل صوتی فارسی هنوز آماده نشده است.") }
            return
        }

        if (!isRecording.compareAndSet(false, true)) {
            return
        }

        // 3. Optimize buffer size via AudioRecord.getMinBufferSize so no audio frames drop during recognition
        val minBufferSizeBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_ENCODING
        )

        if (minBufferSizeBytes == AudioRecord.ERROR || minBufferSizeBytes == AudioRecord.ERROR_BAD_VALUE) {
            isRecording.set(false)
            postToMain { onErrorMessage("سخت‌افزار میکروفون از فرکانس ۱۶ کیلوهرتز پشتیبانی نمی‌کند.") }
            return
        }

        val hardwareRingBufferBytes = maxOf(
            minBufferSizeBytes * BUFFER_SIZE_MULTIPLIER,
            SAMPLE_RATE_HZ * 2 // At least 1 full second of 16-bit mono PCM
        )

        val readChunkShorts = maxOf(minBufferSizeBytes / 2, 2048)

        recognitionJob = audioScope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            var localRecognizer: Recognizer? = null
            var localRecord: AudioRecord? = null

            try {
                // 4. Explicitly instantiate Vosk Recognizer with 16000.0f matching the 16kHz AudioRecord stream
                localRecognizer = Recognizer(activeModel, VOSK_SAMPLE_RATE)

                // 1. Hardware AudioRecord configured at 16000 Hz, CHANNEL_IN_MONO, ENCODING_PCM_16BIT
                localRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_ENCODING,
                    hardwareRingBufferBytes
                )

                if (localRecord.state != AudioRecord.STATE_INITIALIZED) {
                    throw IOException("راه‌اندازی میکروفون با خطا مواجه شد (AudioRecord uninitialized).")
                }

                audioRecord = localRecord

                // 2. Attach native Android NoiseSuppressor and AcousticEchoCanceler if supported by the device
                attachNativeAudioEffects(localRecord.audioSessionId)

                localRecord.startRecording()
                postToMain { onListeningChanged(true) }

                val pcmBuffer = ShortArray(readChunkShorts)
                var completedByUtteranceEnd = false

                while (isActive && isRecording.get()) {
                    val shortsRead = localRecord.read(pcmBuffer, 0, pcmBuffer.size)
                    if (shortsRead > 0) {
                        val isFinalUtterance = localRecognizer.acceptWaveForm(pcmBuffer, shortsRead)
                        if (isFinalUtterance) {
                            // 5. Immediately return final recognized speech as soon as user finishes speaking
                            val resultText = parseVoskJson(localRecognizer.result, key = "text")
                            if (resultText.isNotEmpty()) {
                                completedByUtteranceEnd = true
                                isRecording.set(false)
                                postToMain {
                                    onListeningChanged(false)
                                    onFinalResultText(resultText)
                                }
                                break
                            }
                        } else {
                            val partialText = parseVoskJson(localRecognizer.partialResult, key = "partial")
                            if (partialText.isNotEmpty()) {
                                postToMain { onPartialResultText(partialText) }
                            }
                        }
                    } else if (shortsRead < 0) {
                        throw IOException("خطا در خواندن بافر میکروفون (کد: $shortsRead)")
                    }
                }

                // If user manually tapped stop before automatic silence endpoint, flush remaining finalResult
                if (!completedByUtteranceEnd) {
                    val flushedText = parseVoskJson(localRecognizer.finalResult, key = "text")
                    postToMain {
                        onListeningChanged(false)
                        if (flushedText.isNotEmpty()) {
                            onFinalResultText(flushedText)
                        }
                    }
                }
            } catch (e: Exception) {
                postToMain {
                    onListeningChanged(false)
                    onErrorMessage(e.localizedMessage ?: "خطا در پردازش صوتی آفلاین")
                }
            } finally {
                // 5. Strict deterministic resource cleanup (AudioEffects, AudioRecord, and native Recognizer)
                isRecording.set(false)
                releaseAudioHardware(localRecord)
                runCatching { localRecognizer?.close() }
            }
        }
    }

    /**
     * Enables hardware/OS-level [NoiseSuppressor] and [AcousticEchoCanceler] on the active [AudioRecord] session.
     */
    private fun attachNativeAudioEffects(audioSessionId: Int) {
        if (audioSessionId <= 0) return

        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        }

        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        }
    }

    fun stopListening() {
        isRecording.set(false)
    }

    private fun releaseAudioHardware(record: AudioRecord?) {
        runCatching {
            noiseSuppressor?.enabled = false
            noiseSuppressor?.release()
        }
        noiseSuppressor = null

        runCatching {
            echoCanceler?.enabled = false
            echoCanceler?.release()
        }
        echoCanceler = null

        runCatching {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
            record?.release()
        }
        if (audioRecord === record) {
            audioRecord = null
        }
    }

    fun release() {
        isRecording.set(false)
        recognitionJob?.cancel()
        recognitionJob = null
        releaseAudioHardware(audioRecord)
        audioScope.cancel()
        runCatching { model?.close() }
        model = null
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun parseVoskJson(rawJson: String?, key: String): String {
        if (rawJson.isNullOrBlank()) return ""
        return runCatching {
            JSONObject(rawJson).optString(key, "").trim()
        }.getOrDefault("")
    }
}
