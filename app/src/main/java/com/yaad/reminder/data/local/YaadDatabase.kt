package com.yaad.reminder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ItemEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(CategoryConverters::class)
abstract class YaadDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {
        private const val DATABASE_NAME = "yaad_offline.db"

        @Volatile
        private var INSTANCE: YaadDatabase? = null

        fun getInstance(context: Context): YaadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    YaadDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
