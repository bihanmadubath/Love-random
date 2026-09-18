package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    /**
     * Fast retrieval of messages between two users ordered by timestamp,
     * utilizing composite index on (senderId, receiverId, timestamp).
     */
    @Query("""
        SELECT * FROM chat_messages 
        WHERE (senderId = :userA AND receiverId = :userB)
           OR (senderId = :userB AND receiverId = :userA)
        ORDER BY timestamp ASC 
        LIMIT :limit
    """)
    suspend fun getConversationHistory(userA: String, userB: String, limit: Int = 100): List<ChatMessageEntity>

    @Query("""
        SELECT * FROM chat_messages 
        WHERE (senderId = :userA AND receiverId = :userB)
           OR (senderId = :userB AND receiverId = :userA)
        ORDER BY timestamp ASC
    """)
    fun getConversationFlow(userA: String, userB: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getTotalMessageCount(): Int

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)
}
