package com.example.data

import com.example.health.SystemHealthMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository providing clean, optimized access to user persistence.
 * Every query execution is tracked through SystemHealthMonitor for production APM telemetry.
 */
class UserRepository(
    private val userDao: UserDao,
    private val healthMonitor: SystemHealthMonitor? = null
) {
    val allUsersFlow: Flow<List<UserEntity>> = userDao.getAllUsersFlow()

    suspend fun getUserByUsername(username: String): UserEntity? = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("getUserByUsername") {
                userDao.getUserByUsername(username)
            }
        } else {
            userDao.getUserByUsername(username)
        }
    }

    suspend fun getUserById(id: String): UserEntity? = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("getUserById") {
                userDao.getUserById(id)
            }
        } else {
            userDao.getUserById(id)
        }
    }

    suspend fun getLiveMatchCandidates(excludeUserId: String): List<UserEntity> = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("getLiveMatchCandidates") {
                userDao.getLiveMatchCandidates(excludeUserId)
            }
        } else {
            userDao.getLiveMatchCandidates(excludeUserId)
        }
    }

    suspend fun getAllUsers(): List<UserEntity> = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("getAllUsers") {
                userDao.getAllUsers()
            }
        } else {
            userDao.getAllUsers()
        }
    }

    suspend fun getUsersByStatus(status: String): List<UserEntity> = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("getUsersByStatus") {
                userDao.getUsersByStatus(status)
            }
        } else {
            userDao.getUsersByStatus(status)
        }
    }

    suspend fun getUsersBySessionState(sessionState: String): List<UserEntity> = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("getUsersBySessionState") {
                userDao.getUsersBySessionState(sessionState)
            }
        } else {
            userDao.getUsersBySessionState(sessionState)
        }
    }

    suspend fun insertUser(user: UserEntity) = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("insertUser") {
                userDao.insertUser(user)
            }
        } else {
            userDao.insertUser(user)
        }
    }

    suspend fun insertUsers(users: List<UserEntity>) = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("insertUsers") {
                userDao.insertUsers(users)
            }
        } else {
            userDao.insertUsers(users)
        }
    }

    suspend fun updateSessionState(userId: String, sessionState: String, isLive: Boolean) = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("updateSessionState") {
                userDao.updateSessionState(userId, sessionState, isLive)
            }
        } else {
            userDao.updateSessionState(userId, sessionState, isLive)
        }
    }

    suspend fun updateUserStatus(userId: String, status: String, sessionState: String, isLive: Boolean) = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("updateUserStatus") {
                userDao.updateUserStatus(userId, status, sessionState, isLive)
            }
        } else {
            userDao.updateUserStatus(userId, status, sessionState, isLive)
        }
    }

    suspend fun updateVipStatus(userId: String, isVip: Boolean) = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("updateVipStatus") {
                userDao.updateVipStatus(userId, isVip)
            }
        } else {
            userDao.updateVipStatus(userId, isVip)
        }
    }

    suspend fun updateProfile(userId: String, name: String, age: Int) = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("updateProfile") {
                userDao.updateProfile(userId, name, age)
            }
        } else {
            userDao.updateProfile(userId, name, age)
        }
    }

    suspend fun getAdminMetrics(): AdminKpis = withContext(Dispatchers.IO) {
        if (healthMonitor != null) {
            healthMonitor.trackQuery("getAdminMetrics") {
                val total = userDao.getTotalUserCount()
                val live = userDao.getLiveUserCount()
                val banned = userDao.getBannedUserCount()
                val inCall = userDao.getInCallUserCount()
                AdminKpis(total, live, banned, inCall)
            }
        } else {
            val total = userDao.getTotalUserCount()
            val live = userDao.getLiveUserCount()
            val banned = userDao.getBannedUserCount()
            val inCall = userDao.getInCallUserCount()
            AdminKpis(total, live, banned, inCall)
        }
    }

    suspend fun seedInitialUsersIfEmpty(seedList: List<UserEntity>) = withContext(Dispatchers.IO) {
        val count = userDao.getTotalUserCount()
        if (count == 0) {
            userDao.insertUsers(seedList)
        }
    }
}

data class AdminKpis(
    val totalUsers: Int,
    val liveUsers: Int,
    val bannedUsers: Int,
    val inCallUsers: Int
)
