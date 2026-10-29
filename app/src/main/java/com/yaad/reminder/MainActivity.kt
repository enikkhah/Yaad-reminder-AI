package com.yaad.reminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.yaad.reminder.alarm.AlarmScheduler
import com.yaad.reminder.data.local.YaadDatabase
import com.yaad.reminder.data.repository.ItemRepository
import com.yaad.reminder.speech.VoskVoiceManager
import com.yaad.reminder.ui.timeline.TimelineScreen
import com.yaad.reminder.ui.timeline.TimelineViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TimelineViewModel by viewModels {
        val dao = YaadDatabase.getInstance(applicationContext).itemDao()
        val alarmScheduler = AlarmScheduler(applicationContext)
        TimelineViewModel.Factory(ItemRepository(dao), alarmScheduler)
    }

    private lateinit var voskVoiceManager: VoskVoiceManager

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voskVoiceManager.toggleListening()
        } else {
            Toast.makeText(
                this,
                "برای ثبت صوتی، دسترسی به میکروفون لازم است.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* High-priority alarm notifications enabled if granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        voskVoiceManager = VoskVoiceManager(
            context = applicationContext,
            onModelReady = viewModel::onVoskModelReady,
            onListeningChanged = viewModel::onListeningStateChanged,
            onPartialResultText = viewModel::onPartialSpeechResult,
            onFinalResultText = viewModel::onFinalSpeechResult,
            onErrorMessage = { msg ->
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        ).also { it.initModel() }

        setContent {
            TimelineScreen(
                viewModel = viewModel,
                onToggleVoiceRecognition = ::handleMicButtonClicked
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleMicButtonClicked() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            voskVoiceManager.toggleListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voskVoiceManager.isInitialized) {
            voskVoiceManager.release()
        }
    }
}
