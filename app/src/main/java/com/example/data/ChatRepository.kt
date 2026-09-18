package com.example.data

import com.example.health.SystemHealthMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for fast indexed chat message persistence and retrieval.
 */
class ChatRepository(
    private val chatDao: ChatMessageDao,
    private val healthMonitor: SystemHealthMonitor? = null
) {
    suspend fun getConversationHistory(userA: String, userB: String, limit: Int = 100): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("getConversationHistory") {
                chatDao.getConversationHistory(userA, userB, limit)
            }
        } else {
            chatDao.getConversationHistory(userA, userB, limit)
        }
    }

    fun getConversationFlow(userA: String, userB: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getConversationFlow(userA, userB)
    }

    suspend fun sendMessage(senderId: String, receiverId: String, content: String): Long = withContext(Dispatchers.IO) {
        val entity = ChatMessageEntity(
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        if (healthMonitor != null) {
            healthMonitor.trackQuery("sendMessage") {
                chatDao.insertMessage(entity)
            }
        } else {
            chatDao.insertMessage(entity)
        }
    }
}
