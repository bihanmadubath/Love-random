package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User entity optimized with composite and unique indexes for fast lookups,
 * rapid matchmaking queries, and real-time session filtering.
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["status", "isLive"]),
        Index(value = ["sessionState"]),
        Index(value = ["lastActive"])
    ]
)
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    val password: String,
    val name: String,
    val role: String = "user",
    val isVip: Boolean = false,
    val age: Int = 22,
    val gender: String = "Female",
    val city: String = "Global",
    val avatar: String = "",
    val status: String = "active", // "active", "banned"
    val isLive: Boolean = true,
    val sessionState: String = "online", // "online", "seeking", "incall", "offline"
    val lastActive: Long = System.currentTimeMillis()
)
