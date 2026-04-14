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

    suspend fun createSession(title: String = "새 대화", systemPrompt: String = ""): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            systemPrompt = systemPrompt,
        )
        chatDao.upsertSession(session)
        return session
    }

    suspend fun renameSession(sessionId: String, title: String) {
        chatDao.renameSession(sessionId, title)
    }

    fun searchSessions(query: String): Flow<List<ChatSession>> {
        return chatDao.searchSessions(query)
    }

    fun groupSessionsByDate(sessions: List<ChatSession>): Map<String, List<ChatSession>> {
        val now = System.currentTimeMillis()
        val todayStart = now - (now % (24 * 60 * 60 * 1000))
        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000)
        val weekStart = todayStart - (7 * 24 * 60 * 60 * 1000)

        return sessions.groupBy { session ->
            when {
                session.updatedAt >= todayStart -> "오늘"
                session.updatedAt >= yesterdayStart -> "어제"
                session.updatedAt >= weekStart -> "이번 주"
                else -> "이전"
            }
        }
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
