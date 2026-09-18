package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SystemHealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: SystemHealthMetricEntity): Long

    @Query("SELECT * FROM health_metrics WHERE metricType = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMetricsByType(type: String, limit: Int = 50): List<SystemHealthMetricEntity>

    @Query("SELECT AVG(valueDouble) FROM health_metrics WHERE metricType = 'QUERY_LATENCY' AND timestamp >= :sinceTimestamp")
    suspend fun getAverageQueryLatency(sinceTimestamp: Long): Double?

    @Query("SELECT * FROM health_metrics ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMetrics(limit: Int = 100): List<SystemHealthMetricEntity>

    @Query("DELETE FROM health_metrics WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldMetrics(cutoffTimestamp: Long): Int
}
