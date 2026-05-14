package com.example.bevdatacollector.data

import java.io.File

data class CaptureFrame(
    val imageFile: File,
    val timestamp: Long,           // Milliseconds
    val captureTimeNanos: Long,    // Nanoseconds for precise interpolation
    val frameId: String,
    val sequenceNumber: Int,
    val gps: GPSData?,
    val imu: IMUData?,
    val gyro: GyroData?,
    val calibration: CameraCalibration,
    val imageWidth: Int,
    val imageHeight: Int
)