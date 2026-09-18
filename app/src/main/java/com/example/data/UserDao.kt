package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    /**
     * O(1) indexed lookup by username.
     */
    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    /**
     * Leverages compound index on (status, isLive) to quickly match candidates
     * without full table scan.
     */
    @Query("SELECT * FROM users WHERE status != 'banned' AND isLive = 1 AND id != :excludeUserId ORDER BY lastActive DESC")
    suspend fun getLiveMatchCandidates(excludeUserId: String): List<UserEntity>

    /**
     * Flow of all users for real-time reactivity, sorted by most recent activity.
     */
    @Query("SELECT * FROM users ORDER BY lastActive DESC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY lastActive DESC")
    suspend fun getAllUsers(): List<UserEntity>

    /**
     * Filtered queries utilizing dedicated index.
     */
    @Query("SELECT * FROM users WHERE status = :status ORDER BY lastActive DESC")
    suspend fun getUsersByStatus(status: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE sessionState = :sessionState ORDER BY lastActive DESC")
    suspend fun getUsersBySessionState(sessionState: String): List<UserEntity>

    /**
     * Fast indexed aggregate counts for admin KPI dashboard.
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getTotalUserCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE isLive = 1 AND status != 'banned'")
    suspend fun getLiveUserCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE status = 'banned'")
    suspend fun getBannedUserCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE sessionState = 'incall'")
    suspend fun getInCallUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    /**
     * In-place targeted status updates without rewriting unneeded columns.
     */
    @Query("UPDATE users SET status = :status, sessionState = :sessionState, isLive = :isLive, lastActive = :lastActive WHERE id = :userId")
    suspend fun updateUserStatus(userId: String, status: String, sessionState: String, isLive: Boolean, lastActive: Long = System.currentTimeMillis())

    @Query("UPDATE users SET sessionState = :sessionState, isLive = :isLive, lastActive = :lastActive WHERE id = :userId")
    suspend fun updateSessionState(userId: String, sessionState: String, isLive: Boolean, lastActive: Long = System.currentTimeMillis())

    @Query("UPDATE users SET isVip = :isVip WHERE id = :userId")
    suspend fun updateVipStatus(userId: String, isVip: Boolean)

    @Query("UPDATE users SET name = :name, age = :age WHERE id = :userId")
    suspend fun updateProfile(userId: String, name: String, age: Int)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)
}
