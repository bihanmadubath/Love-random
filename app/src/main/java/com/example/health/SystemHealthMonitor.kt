package com.example.health

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.example.data.AppDatabase
import com.example.data.SystemHealthMetricEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

enum class HealthStatus {
    EXCELLENT,
    HEALTHY,
    WARNING,
    CRITICAL
}

data class HealthSnapshot(
    val memoryUsedMb: Long = 0,
    val memoryMaxMb: Long = 0,
    val memoryUsagePercent: Int = 0,
    val avgQueryDurationMs: Double = 0.0,
    val totalQueriesLogged: Int = 0,
    val slowQueryCount: Int = 0,
    val isNetworkConnected: Boolean = true,
    val status: HealthStatus = HealthStatus.EXCELLENT,
    val uptimeSeconds: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Production APM & System Health Monitoring Tool.
 * Tracks memory pressure, database query latencies, slow queries, network connectivity,
 * and system health metrics in real time.
 */
class SystemHealthMonitor(
    private val context: Context,
    private val database: AppDatabase? = null
) {
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appStartTime = SystemClock.elapsedRealtime()

    private val totalQueries = AtomicInteger(0)
    private val totalQueryDurationNanos = AtomicLong(0)
    private val slowQueries = AtomicInteger(0)
    private val recentQueryTimes = ConcurrentLinkedQueue<Double>()

    private val _healthState = MutableStateFlow(createSnapshot())
    val healthState: StateFlow<HealthSnapshot> = _healthState.asStateFlow()

    companion object {
        const val SLOW_QUERY_THRESHOLD_MS = 16.0 // Beyond 1 frame budget (60fps)

        @Volatile
        private var INSTANCE: SystemHealthMonitor? = null

        fun getInstance(context: Context): SystemHealthMonitor {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val inst = SystemHealthMonitor(context.applicationContext, db)
                INSTANCE = inst
                inst
            }
        }
    }

    suspend fun <T> trackQuery(queryName: String, block: suspend () -> T): T {
        val startNano = System.nanoTime()
        return try {
            block()
        } finally {
            val durationMs = (System.nanoTime() - startNano) / 1_000_000.0
            recordQueryLatency(queryName, durationMs)
        }
    }

    fun recordQueryLatency(queryName: String, durationMs: Double) {
        totalQueries.incrementAndGet()
        totalQueryDurationNanos.addAndGet((durationMs * 1_000_000).toLong())
        if (durationMs > SLOW_QUERY_THRESHOLD_MS) {
            slowQueries.incrementAndGet()
        }
        recentQueryTimes.offer(durationMs)
        while (recentQueryTimes.size > 50) {
            recentQueryTimes.poll()
        }

        database?.let { db ->
            monitorScope.launch {
                try {
                    db.systemHealthDao().insertMetric(
                        SystemHealthMetricEntity(
                            metricType = "QUERY_LATENCY",
                            metricName = queryName,
                            valueDouble = durationMs,
                            details = if (durationMs > SLOW_QUERY_THRESHOLD_MS) "SLOW_QUERY" else "OPTIMAL"
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        refreshSnapshot()
    }

    fun recordCustomMetric(metricType: String, name: String, value: Double, details: String = "") {
        database?.let { db ->
            monitorScope.launch {
                try {
                    db.systemHealthDao().insertMetric(
                        SystemHealthMetricEntity(
                            metricType = metricType,
                            metricName = name,
                            valueDouble = value,
                            details = details
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun refreshSnapshot(): HealthSnapshot {
        val snap = createSnapshot()
        _healthState.value = snap
        return snap
    }

    private fun createSnapshot(): HealthSnapshot {
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory()
        val freeMem = runtime.freeMemory()
        val maxMem = runtime.maxMemory()
        val usedMem = totalMem - freeMem
        val usedMb = usedMem / (1024 * 1024)
        val maxMb = maxMem / (1024 * 1024)
        val usagePercent = if (maxMb > 0) ((usedMb.toDouble() / maxMb.toDouble()) * 100).roundToInt() else 0

        val qCount = totalQueries.get()
        val avgMs = if (qCount > 0) {
            val recent = recentQueryTimes.toList()
            if (recent.isNotEmpty()) recent.average() else (totalQueryDurationNanos.get() / 1_000_000.0) / qCount
        } else {
            0.0
        }

        val networkOk = checkNetworkConnected()
        val slowCount = slowQueries.get()

        val status = when {
            usagePercent > 85 || (qCount > 10 && slowCount > qCount / 2) -> HealthStatus.CRITICAL
            usagePercent > 70 || slowCount > 5 -> HealthStatus.WARNING
            usagePercent > 45 || avgMs > 10.0 -> HealthStatus.HEALTHY
            else -> HealthStatus.EXCELLENT
        }

        val uptimeSec = (SystemClock.elapsedRealtime() - appStartTime) / 1000

        return HealthSnapshot(
            memoryUsedMb = usedMb,
            memoryMaxMb = maxMb,
            memoryUsagePercent = usagePercent,
            avgQueryDurationMs = (avgMs * 100).roundToInt() / 100.0,
            totalQueriesLogged = qCount,
            slowQueryCount = slowCount,
            isNetworkConnected = networkOk,
            status = status,
            uptimeSeconds = uptimeSec
        )
    }

    private fun checkNetworkConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val active = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun getHealthSummaryJson(): String {
        val s = refreshSnapshot()
        return """
            {
                "status": "${s.status.name}",
                "memoryUsedMb": ${s.memoryUsedMb},
                "memoryMaxMb": ${s.memoryMaxMb},
                "memoryUsagePercent": ${s.memoryUsagePercent},
                "avgQueryDurationMs": ${s.avgQueryDurationMs},
                "totalQueriesLogged": ${s.totalQueriesLogged},
                "slowQueryCount": ${s.slowQueryCount},
                "isNetworkConnected": ${s.isNetworkConnected},
                "uptimeSeconds": ${s.uptimeSeconds}
            }
        """.trimIndent()
    }
}
