package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Chat message entity indexed by participants and timestamp for fast conversation retrieval.
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["senderId", "receiverId", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isDelivered: Boolean = true
)
