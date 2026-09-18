package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.UserEntity
import com.example.data.UserRepository
import com.example.health.SystemHealthMonitor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoveRandomDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var monitor: SystemHealthMonitor
    private lateinit var repository: UserRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemoryDatabase(context)
        monitor = SystemHealthMonitor(context, db)
        repository = UserRepository(db.userDao(), monitor)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testUserInsertionAndRetrieval() = runBlocking {
        val user = UserEntity(
            id = "u_test_1",
            username = "testuser",
            password = "password123",
            name = "Test User",
            city = "Global",
            isLive = true
        )
        repository.insertUser(user)
        val fetched = repository.getUserByUsername("testuser")
        assertNotNull(fetched)
        assertEquals("Test User", fetched?.name)
    }

    @Test
    fun testLiveMatchCandidates() = runBlocking {
        val users = listOf(
            UserEntity(id = "u1", username = "alex", password = "1", name = "Alex", isLive = true, status = "active"),
            UserEntity(id = "u2", username = "elena", password = "1", name = "Elena", isLive = true, status = "active"),
            UserEntity(id = "u3", username = "banned", password = "1", name = "Banned", isLive = true, status = "banned")
        )
        repository.insertUsers(users)
        val candidates = repository.getLiveMatchCandidates("u1")
        assertEquals(1, candidates.size)
        assertEquals("u2", candidates[0].id)
    }

    @Test
    fun testChatMessageConversationFlow() = runBlocking {
        val chatDao = db.chatMessageDao()
        chatDao.insertMessage(ChatMessageEntity(senderId = "u1", receiverId = "u2", content = "Hey!"))
        chatDao.insertMessage(ChatMessageEntity(senderId = "u2", receiverId = "u1", content = "Hello!"))
        val history = chatDao.getConversationHistory("u1", "u2")
        assertEquals(2, history.size)
        assertEquals("Hey!", history[0].content)
        assertEquals("Hello!", history[1].content)
    }

    @Test
    fun testSystemHealthSnapshot() {
        monitor.recordQueryLatency("test_query", 3.5)
        val snapshot = monitor.refreshSnapshot()
        assertTrue(snapshot.totalQueriesLogged >= 1)
        assertTrue(snapshot.avgQueryDurationMs > 0.0)
    }
}
