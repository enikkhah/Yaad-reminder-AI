package com.yaad.reminder.data.repository

import com.yaad.reminder.AlarmScheduler
import com.yaad.reminder.data.local.ItemCategory
import com.yaad.reminder.data.local.ItemDao
import com.yaad.reminder.data.local.ItemEntity
import com.yaad.reminder.domain.nlp.TextProcessor
import kotlinx.coroutines.flow.Flow

data class SaveAndScheduleResult(
    val item: ItemEntity,
    val alarmScheduled: Boolean,
    val scheduledEpochMillis: Long?
)

class ItemRepository(
    private val itemDao: ItemDao,
    private val alarmScheduler: AlarmScheduler
) {
    val timelineItems: Flow<List<ItemEntity>> = itemDao.getAllItemsTimeline()

    /**
     * Processes raw Persian input, saves the [ItemEntity] to Room, and immediately invokes
     * [AlarmScheduler.schedule] when the intent is [ItemCategory.REMINDER] (or has an extracted time).
     */
    suspend fun processAndSave(rawText: String): SaveAndScheduleResult? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        val processed = TextProcessor.process(trimmed)
        val epochMillis = processed.formattedDateTime?.let {
            TextProcessor.parseDateTimeToEpochMillis(it)
        } ?: processed.extractedTimeMillis

        val entityToInsert = ItemEntity(
            originalText = processed.originalText,
            category = processed.category,
            extractedTime = epochMillis,
            createdAt = System.currentTimeMillis()
        )

        val rowId = itemDao.insertItem(entityToInsert)
        val savedItem = entityToInsert.copy(id = rowId)

        var isScheduled = false
        if (savedItem.category == ItemCategory.REMINDER || savedItem.extractedTime != null) {
            val triggerMillis = savedItem.extractedTime
                ?: (System.currentTimeMillis() + 10 * 60 * 1000L)
            isScheduled = alarmScheduler.schedule(
                triggerAtMillis = triggerMillis,
                message = savedItem.originalText,
                alarmId = savedItem.id
            )
        }

        return SaveAndScheduleResult(
            item = savedItem,
            alarmScheduled = isScheduled,
            scheduledEpochMillis = savedItem.extractedTime
        )
    }

    suspend fun deleteItem(id: Long) {
        alarmScheduler.cancelReminder(id)
        itemDao.deleteById(id)
    }
}
