package com.yaad.reminder.data.repository

import com.yaad.reminder.data.local.ItemDao
import com.yaad.reminder.data.local.ItemEntity
import com.yaad.reminder.domain.nlp.TextProcessor
import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val itemDao: ItemDao
) {
    val timelineItems: Flow<List<ItemEntity>> = itemDao.getAllItemsTimeline()

    suspend fun processAndSave(rawText: String): ItemEntity? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        val processed = TextProcessor.process(trimmed)
        val entity = processed.toEntity()
        val rowId = itemDao.insertItem(entity)
        return entity.copy(id = rowId)
    }

    suspend fun deleteItem(id: Long) {
        itemDao.deleteById(id)
    }
}
