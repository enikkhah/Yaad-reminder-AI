package com.yaad.reminder.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yaad.reminder.alarm.AlarmScheduler
import com.yaad.reminder.data.local.ItemCategory
import com.yaad.reminder.data.local.ItemEntity
import com.yaad.reminder.data.repository.ItemRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InputUiState(
    val isExpanded: Boolean = false,
    val draftText: String = "",
    val isListening: Boolean = false,
    val partialSpeechText: String = "",
    val modelReady: Boolean = false
)

class TimelineViewModel(
    private val repository: ItemRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val timelineItems: StateFlow<List<ItemEntity>> = repository.timelineItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _inputState = MutableStateFlow(InputUiState())
    val inputState: StateFlow<InputUiState> = _inputState.asStateFlow()

    private val _snackbarMessages = Channel<String>(Channel.BUFFERED)
    val snackbarMessages: Flow<String> = _snackbarMessages.receiveAsFlow()

    fun onExpandInput() {
        _inputState.update { it.copy(isExpanded = true) }
    }

    fun onCollapseInput() {
        _inputState.update {
            if (it.draftText.isBlank() && !it.isListening) {
                it.copy(isExpanded = false, partialSpeechText = "")
            } else {
                it
            }
        }
    }

    fun onDraftChanged(newText: String) {
        _inputState.update {
            it.copy(
                draftText = newText,
                isExpanded = true
            )
        }
    }

    fun onSubmitText(textToSubmit: String = _inputState.value.draftText) {
        val clean = textToSubmit.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            val savedItem = repository.processAndSave(clean)
            _inputState.update {
                it.copy(
                    draftText = "",
                    partialSpeechText = "",
                    isExpanded = false,
                    isListening = false
                )
            }

            // Immediately schedule an exact alarm if the saved item is a REMINDER with an extracted time
            if (savedItem != null &&
                savedItem.category == ItemCategory.REMINDER &&
                savedItem.extractedTime != null
            ) {
                val isScheduled = alarmScheduler.scheduleExactReminder(savedItem)
                if (isScheduled) {
                    val formatter = SimpleDateFormat("HH:mm", Locale("fa", "IR"))
                    val formattedTime = formatter.format(Date(savedItem.extractedTime))
                    _snackbarMessages.send("آلارم یادآوری برای ساعت $formattedTime تنظیم شد")
                }
            }
        }
    }

    fun onVoskModelReady(ready: Boolean) {
        _inputState.update { it.copy(modelReady = ready) }
    }

    fun onListeningStateChanged(listening: Boolean) {
        _inputState.update {
            it.copy(
                isListening = listening,
                isExpanded = if (listening) true else it.isExpanded
            )
        }
    }

    fun onPartialSpeechResult(partialPersian: String) {
        _inputState.update {
            it.copy(
                partialSpeechText = partialPersian,
                isExpanded = true
            )
        }
    }

    /**
     * Called by VoskVoiceManager when a final Persian utterance is recognized.
     */
    fun onFinalSpeechResult(finalPersianText: String) {
        val clean = finalPersianText.trim()
        if (clean.isEmpty()) return
        onSubmitText(clean)
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            alarmScheduler.cancelReminder(itemId)
            repository.deleteItem(itemId)
        }
    }

    class Factory(
        private val repository: ItemRepository,
        private val alarmScheduler: AlarmScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimelineViewModel(repository, alarmScheduler) as T
        }
    }
}
