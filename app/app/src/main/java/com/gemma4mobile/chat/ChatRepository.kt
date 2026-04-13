package com.gemma4mobile.chat

import com.gemma4mobile.db.ChatDao
import com.gemma4mobile.db.ChatMessage
import com.gemma4mobile.db.ChatSession
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
) {
    fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessages(sessionId)
    }

    fun getSessions(): Flow<List<ChatSession>> {
        return chatDao.getSessions()
    }

    suspend fun createSession(title: String = "새 대화"): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
        )
        chatDao.upsertSession(session)
        return session
    }

    suspend fun addMessage(sessionId: String, role: String, content: String) {
        chatDao.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = role,
                content = content,
            )
        )
        chatDao.upsertSession(
            ChatSession(
                id = sessionId,
                title = if (role == "user") content.take(30) else "",
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteSession(session: ChatSession) {
        chatDao.deleteMessages(session.id)
        chatDao.deleteSession(session)
    }
}
