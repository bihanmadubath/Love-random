package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing production telemetry and performance metrics.
 * Indexed by type and timestamp for fast analytical aggregation.
 */
@Entity(
    tableName = "health_metrics",
    indices = [
        Index(value = ["metricType", "timestamp"])
    ]
)
data class SystemHealthMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val metricType: String, // "QUERY_LATENCY", "MEMORY_USAGE", "FPS_DROP", "NETWORK_PING", "ERROR"
    val metricName: String,
    val valueDouble: Double,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
