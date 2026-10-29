package com.yaad.reminder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    /**
     * Streams all saved items ordered by creation time (newest first) for the main Timeline.
     */
    @Query("SELECT * FROM items ORDER BY created_at DESC")
    fun getAllItemsTimeline(): Flow<List<ItemEntity>>

    /**
     * Inserts a processed item into the local SQLite database.
     * @return The newly inserted row ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    /**
     * Removes an item by its primary key ID.
     */
    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteById(itemId: Long)
}
