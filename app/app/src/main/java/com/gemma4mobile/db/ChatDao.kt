package com.gemma4mobile.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(sessionId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSession)

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getSessions(): Flow<List<ChatSession>>

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    @Delete
    suspend fun deleteSession(session: ChatSession)

    @Query("SELECT * FROM sessions WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchSessions(query: String): Flow<List<ChatSession>>

    @Query("UPDATE sessions SET title = :title WHERE id = :sessionId")
    suspend fun renameSession(sessionId: String, title: String)
}
