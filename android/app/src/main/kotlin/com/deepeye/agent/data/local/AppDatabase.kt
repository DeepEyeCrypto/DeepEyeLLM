package com.deepeye.agent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.deepeye.agent.core.model.ChatMessage

/**
 * Primary offline Room Database for persistent chat conversations, token generation records,
 * and multi-turn agent history.
 */
@Database(
    entities = [
        ChatMessage::class,
        KnowledgeChunkEntity::class,
        KnowledgeFtsEntity::class,
        KnowledgeEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        private const val DATABASE_NAME = "deepeye_app_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
