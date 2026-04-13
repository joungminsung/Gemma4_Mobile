package com.gemma4mobile.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessage::class, ChatSession::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
