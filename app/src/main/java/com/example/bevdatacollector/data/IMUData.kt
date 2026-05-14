package com.example.bevdatacollector.data

data class IMUData(
    val azimuth: Float,     // True north heading (0-360°)
    val pitch: Float,       // Camera pitch (looking up/down) - degrees
    val roll: Float,        // Camera roll (leaning left/right) - degrees
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,       // Angular velocity around X (rad/s)
    val gyroY: Float,       // Angular velocity around Y (rad/s)
    val gyroZ: Float,       // Angular velocity around Z (rad/s)
    val timestamp: Long
)