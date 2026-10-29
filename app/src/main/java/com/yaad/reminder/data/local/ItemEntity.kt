package com.yaad.reminder.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * Static categories assigned by [com.yaad.reminder.domain.nlp.TextProcessor].
 */
enum class ItemCategory(val persianLabel: String) {
    REMINDER("یادآوری"),
    OCCASION("مناسبت"),
    IDEA("ایده"),
    UNCATEGORIZED("یادداشت")
}

/**
 * Room Entity representing a single note or reminder on the minimalist timeline.
 */
@Entity(
    tableName = "items",
    indices = [Index(value = ["created_at"])]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "original_text")
    val originalText: String,

    @ColumnInfo(name = "category")
    val category: ItemCategory,

    @ColumnInfo(name = "extracted_time")
    val extractedTime: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room TypeConverters to persist [ItemCategory] as a String in SQLite.
 */
class CategoryConverters {
    @TypeConverter
    fun fromCategory(category: ItemCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): ItemCategory =
        runCatching { ItemCategory.valueOf(value) }
            .getOrDefault(ItemCategory.UNCATEGORIZED)
}
