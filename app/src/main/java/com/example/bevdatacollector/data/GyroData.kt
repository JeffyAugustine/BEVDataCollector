package com.example.bevdatacollector.data

data class GyroData(
    val gyroX: Float,   // Rotation rate around X-axis (rad/s)
    val gyroY: Float,   // Rotation rate around Y-axis (rad/s)
    val gyroZ: Float,   // Rotation rate around Z-axis (rad/s)
    val timestamp: Long
)