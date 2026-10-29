package com.yaad.reminder.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Offline-first Persian speech-to-text manager powered by Android's native [SpeechRecognizer].
 *
 * Key configurations:
 * 1. [RecognizerIntent.EXTRA_PREFER_OFFLINE] = true (enforces offline recognition engine)
 * 2. [RecognizerIntent.EXTRA_LANGUAGE] = "fa-IR" (explicit Persian language input)
 * 3. Comprehensive Persian error handling (including missing offline Persian language pack)
 */
class PersianSpeechRecognizer(
    private val context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialResultText: (String) -> Unit,
    private val onFinalResultText: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val PERSIAN_LOCALE = "fa-IR"
    }

    fun toggleListening() {
        runOnMain {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
    }

    fun startListening() {
        runOnMain {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onListeningChanged(false)
                onErrorMessage(
                    "سرویس تشخیص گفتار روی این دستگاه فعال نیست. لطفاً برنامه Google را فعال یا به‌روزرسانی کنید."
                )
                return@runOnMain
            }

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@PersianSpeechRecognizer)
                    }
                }

                val recognizerIntent = createOfflinePersianRecognizerIntent()
                isListening = true
                onListeningChanged(true)
                speechRecognizer?.startListening(recognizerIntent)
            } catch (e: Exception) {
                isListening = false
                onListeningChanged(false)
                onErrorMessage("خطا در راه‌اندازی تشخیص گفتار: ${e.localizedMessage ?: "نامشخص"}")
            }
        }
    }

    /**
     * Builds the [RecognizerIntent] configured strictly for offline Persian ("fa-IR") recognition.
     */
    private fun createOfflinePersianRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Requirement 2: Explicitly set input language to Persian (fa-IR)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, PERSIAN_LOCALE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, PERSIAN_LOCALE)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, PERSIAN_LOCALE)

            // Requirement 1: Enforce offline speech recognition
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    fun stopListening() {
        runOnMain {
            if (isListening) {
                isListening = false
                runCatching { speechRecognizer?.stopListening() }
                onListeningChanged(false)
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        isListening = true
        onListeningChanged(true)
    }

    override fun onBeginningOfSpeech() {
        // User started speaking
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Audio level changed
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Raw audio buffer received
    }

    override fun onEndOfSpeech() {
        isListening = false
        onListeningChanged(false)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestPartial = matches?.firstOrNull()?.trim().orEmpty()
        if (bestPartial.isNotEmpty()) {
            onPartialResultText(bestPartial)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        onListeningChanged(false)

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestMatch = matches?.firstOrNull()?.trim().orEmpty()
        if (bestMatch.isNotEmpty()) {
            onFinalResultText(bestMatch)
        } else {
            onErrorMessage("متنی از صدای شما دریافت نشد. لطفاً دوباره تلاش کنید.")
        }
    }

    /**
     * Requirement 3: Detailed error handling for native Android SpeechRecognizer
     * (including missing offline Persian language pack on the device).
     */
    override fun onError(error: Int) {
        val wasListening = isListening
        isListening = false
        onListeningChanged(false)

        // Ignore ERROR_CLIENT when user manually cancels/stops listening
        if (error == SpeechRecognizer.ERROR_CLIENT && !wasListening) {
            return
        }

        val message = when (error) {
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                "بسته زبان فارسی (fa-IR) برای تشخیص گفتار آفلاین روی گوشی نصب نیست. لطفاً از تنظیمات گوگل (Offline speech recognition) زبان فارسی را دانلود کنید."
            }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                "برای کارکرد آفلاین بدون اینترنت، لطفاً بسته زبان فارسی آفلاین را در تنظیمات تشخیص گفتار گوشی دانلود کنید."
            }
            SpeechRecognizer.ERROR_NO_MATCH -> {
                "عبارتی تشخیص داده نشد. لطفاً واضح‌تر صحبت کنید."
            }
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                "صدایی دریافت نشد. لطفاً پس از زدن دکمه میکروفون صحبت کنید."
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                "مجوز دسترسی به میکروفون (RECORD_AUDIO) تایید نشده است."
            }
            SpeechRecognizer.ERROR_AUDIO -> {
                "خطا در ضبط صدا از میکروفون دستگاه."
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                "سرویس تشخیص گفتار مشغول است. چند ثانیه دیگر امتحان کنید."
            }
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                "ارتباط با موتور تشخیص گفتار قطع شد. از نصب بودن بسته آفلاین فارسی اطمینان حاصل کنید."
            }
            else -> {
                "خطا در تشخیص گفتار (کد خطا: $error). لطفاً از نصب بودن زبان فارسی در تنظیمات صوتی گوشی مطمئن شوید."
            }
        }

        onErrorMessage(message)

        // Recreate SpeechRecognizer instance on fatal service errors so subsequent taps recover cleanly
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        ) {
            recreateRecognizer()
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Reserved for future speech events
    }

    private fun recreateRecognizer() {
        runOnMain {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    fun release() {
        runOnMain {
            isListening = false
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = null
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
