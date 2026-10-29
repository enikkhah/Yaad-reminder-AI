package com.yaad.reminder.speech

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Online Persian speech-to-text manager powered by Android's native [SpeechRecognizer].
 *
 * Key features:
 * 1. Explicitly sets [RecognizerIntent.EXTRA_LANGUAGE] = "fa-IR" for high-accuracy online Persian recognition.
 * 2. Verifies active internet connectivity before starting and surfaces clear Persian error messages on disconnection.
 * 3. Streams partial and final Persian transcripts directly to [com.yaad.reminder.ui.timeline.TimelineViewModel]
 *    so [com.yaad.reminder.domain.nlp.TextProcessor] and [com.yaad.reminder.AlarmScheduler] process the reminder automatically.
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
            // Requirement 4: Check internet connectivity upfront and show a clear Persian message if offline
            if (!isInternetAvailable()) {
                isListening = false
                onListeningChanged(false)
                onErrorMessage("خطا در اتصال به اینترنت: لطفاً اتصال اینترنت خود را برای تشخیص گفتار بررسی کنید.")
                return@runOnMain
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                isListening = false
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

                val recognizerIntent = createOnlinePersianRecognizerIntent()
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
     * Builds the [RecognizerIntent] configured for online Persian ("fa-IR") speech recognition.
     */
    private fun createOnlinePersianRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Requirement 1: Explicitly set input language to Persian (fa-IR)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, PERSIAN_LOCALE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, PERSIAN_LOCALE)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, PERSIAN_LOCALE)

            // Use online cloud recognition for maximum Persian accuracy
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)

            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    private fun isInternetAvailable(): Boolean {
        return runCatching {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
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

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

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
     * Requirement 4: Handles network disconnection and speech errors with clear Persian messages.
     */
    override fun onError(error: Int) {
        val wasListening = isListening
        isListening = false
        onListeningChanged(false)

        if (error == SpeechRecognizer.ERROR_CLIENT && !wasListening) {
            return
        }

        val message = when (error) {
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                "قطعی ارتباط با اینترنت: لطفاً اتصال اینترنت خود را بررسی کرده و دوباره تلاش کنید."
            }
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                "خطا در ارتباط با سرور تشخیص گفتار. لطفاً اتصال اینترنت خود را بررسی کنید."
            }
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                "زبان فارسی (fa-IR) در سرویس تشخیص گفتار دستگاه در دسترس نیست."
            }
            SpeechRecognizer.ERROR_NO_MATCH -> {
                "عبارتی تشخیص داده نشد. لطفاً واضح‌تر صحبت کنید."
            }
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                "صدایی دریافت نشد. لطفاً پس از زدن دکمه میکروفون صحبت کنید."
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                "مجوز دسترسی به میکروفون تایید نشده است."
            }
            SpeechRecognizer.ERROR_AUDIO -> {
                "خطا در دریافت صدا از میکروفون دستگاه."
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                "سرویس تشخیص گفتار مشغول است. لحظه‌ای دیگر امتحان کنید."
            }
            else -> {
                if (!isInternetAvailable()) {
                    "قطعی ارتباط با اینترنت: لطفاً اتصال اینترنت دستگاه را بررسی کنید."
                } else {
                    "خطا در تشخیص گفتار (کد خطا: $error). لطفاً دوباره تلاش کنید."
                }
            }
        }

        onErrorMessage(message)

        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        ) {
            recreateRecognizer()
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

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
