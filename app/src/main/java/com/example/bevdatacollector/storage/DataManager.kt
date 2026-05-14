package com.example.bevdatacollector.storage

import android.content.Context
import android.util.Log
import com.example.bevdatacollector.data.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

// Data classes for sensor buffering
data class TimedGPS(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val provider: String
)

data class TimedIMU(
    val timestamp: Long,
    val azimuth: Float,
    val pitch: Float,
    val roll: Float,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float
) {
    fun toIMUData(): IMUData = IMUData(
        azimuth = azimuth,
        pitch = pitch,
        roll = roll,
        accelX = accelX,
        accelY = accelY,
        accelZ = accelZ,
        gyroX = gyroX,
        gyroY = gyroY,
        gyroZ = gyroZ,
        timestamp = timestamp
    )
}

class DataManager(private val context: Context) {
    private val TAG = "DataManager"

    private val capturedFrames = ArrayList<CaptureFrame>()
    private var currentSessionId: String = ""
    private var currentSessionFolder: File? = null

    // Circular buffers for sensor interpolation
    private val gpsBuffer = mutableListOf<TimedGPS>()
    private val imuBuffer = mutableListOf<TimedIMU>()
    private val maxBufferSize = 100

    fun startNewSession() {
        currentSessionId = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            .format(Date(System.currentTimeMillis()))
        val rootDir = context.getExternalFilesDir(null)
        currentSessionFolder = File(rootDir, "Session_$currentSessionId")

        if (!currentSessionFolder!!.exists()) {
            currentSessionFolder!!.mkdirs()
            Log.d(TAG, " Created session folder: ${currentSessionFolder!!.absolutePath}")
        }
        capturedFrames.clear()
        gpsBuffer.clear()
        imuBuffer.clear()
        Log.d(TAG, " New session started: $currentSessionId")
    }

    fun getCurrentSessionFolder(): File? = currentSessionFolder

    fun getImageSavePath(sequenceNumber: Int): File {
        val folder = currentSessionFolder
        if (folder == null) {
            Log.e(TAG, "Session folder is null! Creating fallback.")
            return File(context.getExternalFilesDir(null), "BEV_${System.currentTimeMillis()}.jpg")
        }
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        return File(folder, "BEV_${String.format("%06d", sequenceNumber)}_$name.jpg")
    }

    @Synchronized
    fun addGPSReading(timedGPS: TimedGPS) {
        gpsBuffer.add(timedGPS)
        while (gpsBuffer.size > maxBufferSize) {
            gpsBuffer.removeAt(0)
        }
        Log.d(TAG, " GPS added to buffer. Buffer size: ${gpsBuffer.size}")
    }

    @Synchronized
    fun addIMUReading(timedIMU: TimedIMU) {
        imuBuffer.add(timedIMU)
        while (imuBuffer.size > maxBufferSize) {
            imuBuffer.removeAt(0)
        }
        Log.d(TAG, " IMU added to buffer. Buffer size: ${imuBuffer.size}")
    }

    fun addGPSReadingFromGPSData(gpsData: GPSData) {
        val timedGPS = TimedGPS(
            timestamp = gpsData.timestamp,
            latitude = gpsData.latitude,
            longitude = gpsData.longitude,
            altitude = gpsData.altitude,
            accuracy = gpsData.accuracy,
            speed = gpsData.speed,
            bearing = gpsData.bearing,
            provider = gpsData.provider
        )
        addGPSReading(timedGPS)
    }

    fun addIMUReadingFromIMUData(imuData: IMUData) {
        val timedIMU = TimedIMU(
            timestamp = imuData.timestamp,
            azimuth = imuData.azimuth,
            pitch = imuData.pitch,
            roll = imuData.roll,
            accelX = imuData.accelX,
            accelY = imuData.accelY,
            accelZ = imuData.accelZ,
            gyroX = imuData.gyroX,
            gyroY = imuData.gyroY,
            gyroZ = imuData.gyroZ
        )
        addIMUReading(timedIMU)
    }

    @Synchronized
    fun getInterpolatedGPS(captureTimeNanos: Long): GPSData? {
        val captureTimeMillis = captureTimeNanos / 1_000_000

        if (gpsBuffer.isEmpty()) {
            Log.w(TAG, " GPS buffer is empty! No GPS data available.")
            return null
        }

        // Create a copy to avoid ConcurrentModificationException
        val gpsBufferCopy = ArrayList(gpsBuffer)

        var before: TimedGPS? = null
        var after: TimedGPS? = null

        for (reading in gpsBufferCopy) {
            if (reading.timestamp <= captureTimeMillis) {
                if (before == null || reading.timestamp > before.timestamp) {
                    before = reading
                }
            } else {
                if (after == null || reading.timestamp < after.timestamp) {
                    after = reading
                }
            }
        }

        if (before == null && gpsBufferCopy.isNotEmpty()) {
            before = gpsBufferCopy.first()
        }

        if (after == null && gpsBufferCopy.isNotEmpty()) {
            after = gpsBufferCopy.last()
        }

        return when {
            before != null && after != null && before != after -> {
                val t = (captureTimeMillis - before.timestamp).toFloat() /
                        (after.timestamp - before.timestamp).toFloat()
                val interpolatedT = t.coerceIn(0f, 1f)

                GPSData(
                    latitude = before.latitude + (after.latitude - before.latitude) * interpolatedT,
                    longitude = before.longitude + (after.longitude - before.longitude) * interpolatedT,
                    altitude = before.altitude + (after.altitude - before.altitude) * interpolatedT,
                    accuracy = before.accuracy + (after.accuracy - before.accuracy) * interpolatedT,
                    speed = before.speed + (after.speed - before.speed) * interpolatedT,
                    bearing = interpolateBearing(before.bearing, after.bearing, interpolatedT),
                    timestamp = captureTimeMillis,
                    provider = before.provider
                )
            }
            before != null -> {
                GPSData(
                    latitude = before.latitude,
                    longitude = before.longitude,
                    altitude = before.altitude,
                    accuracy = before.accuracy,
                    speed = before.speed,
                    bearing = before.bearing,
                    timestamp = captureTimeMillis,
                    provider = before.provider
                )
            }
            else -> {
                Log.w(TAG, " No GPS data available")
                null
            }
        }
    }

    @Synchronized
    fun getInterpolatedIMU(captureTimeNanos: Long): IMUData? {
        val captureTimeMillis = captureTimeNanos / 1_000_000

        if (imuBuffer.isEmpty()) {
            Log.w(TAG, " IMU buffer is empty! No IMU data available.")
            return null
        }

        // Create a copy to avoid ConcurrentModificationException
        val imuBufferCopy = ArrayList(imuBuffer)

        var before: TimedIMU? = null
        var after: TimedIMU? = null

        for (reading in imuBufferCopy) {
            if (reading.timestamp <= captureTimeMillis) {
                if (before == null || reading.timestamp > before.timestamp) {
                    before = reading
                }
            } else {
                if (after == null || reading.timestamp < after.timestamp) {
                    after = reading
                }
            }
        }

        if (before == null && imuBufferCopy.isNotEmpty()) {
            before = imuBufferCopy.first()
        }

        if (after == null && imuBufferCopy.isNotEmpty()) {
            after = imuBufferCopy.last()
        }

        return when {
            before != null && after != null && before != after -> {
                val t = (captureTimeMillis - before.timestamp).toFloat() /
                        (after.timestamp - before.timestamp).toFloat()
                val interpolatedT = t.coerceIn(0f, 1f)

                IMUData(
                    azimuth = interpolateAngle(before.azimuth, after.azimuth, interpolatedT),
                    pitch = before.pitch + (after.pitch - before.pitch) * interpolatedT,
                    roll = before.roll + (after.roll - before.roll) * interpolatedT,
                    accelX = before.accelX + (after.accelX - before.accelX) * interpolatedT,
                    accelY = before.accelY + (after.accelY - before.accelY) * interpolatedT,
                    accelZ = before.accelZ + (after.accelZ - before.accelZ) * interpolatedT,
                    gyroX = before.gyroX + (after.gyroX - before.gyroX) * interpolatedT,
                    gyroY = before.gyroY + (after.gyroY - before.gyroY) * interpolatedT,
                    gyroZ = before.gyroZ + (after.gyroZ - before.gyroZ) * interpolatedT,
                    timestamp = captureTimeMillis
                )
            }
            before != null -> {
                before.toIMUData()
            }
            else -> {
                Log.w(TAG, "️ No IMU data available")
                null
            }
        }
    }

    private fun interpolateAngle(before: Float, after: Float, t: Float): Float {
        var diff = after - before
        if (abs(diff) > 180) {
            diff = if (diff > 0) diff - 360 else diff + 360
        }
        var result = before + diff * t
        if (result < 0) result += 360
        if (result >= 360) result -= 360
        return result
    }

    private fun interpolateBearing(before: Float, after: Float, t: Float): Float {
        var diff = after - before
        if (abs(diff) > 180) {
            diff = if (diff > 0) diff - 360 else diff + 360
        }
        var result = before + diff * t
        if (result < 0) result += 360
        if (result >= 360) result -= 360
        return result
    }

    fun addCapturedFrame(
        imageFile: File,
        gpsData: GPSData?,
        imuData: IMUData?,
        gyroData: GyroData?,
        calibration: CameraCalibration,
        sequenceNumber: Int,
        imageWidth: Int,
        imageHeight: Int,
        captureTimeNanos: Long
    ): CaptureFrame {
        val frame = CaptureFrame(
            imageFile = imageFile,
            timestamp = captureTimeNanos / 1_000_000,
            captureTimeNanos = captureTimeNanos,
            frameId = "BEV_${String.format("%06d", sequenceNumber)}",
            sequenceNumber = sequenceNumber,
            gps = gpsData,
            imu = imuData,
            gyro = gyroData,
            calibration = calibration,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )

        capturedFrames.add(frame)

        Log.d(TAG, " Stored frame: ${frame.frameId}")
        Log.d(TAG, "   GPS: ${if (gpsData != null) "✓" else "✗"}")
        Log.d(TAG, "   IMU: ${if (imuData != null) "✓" else "✗"}")

        return frame
    }

    fun createFullMetadataCSV(): File {
        val folder = currentSessionFolder ?: context.getExternalFilesDir(null)
        val csvFile = File(folder, "metadata_${currentSessionId}.csv")

        FileWriter(csvFile).use { writer ->
            writer.append("frameId,timestampNanos,sequenceNumber,")
            writer.append("latitude,longitude,altitude,gpsAccuracy,speed,bearing,gpsProvider,")
            writer.append("heading,pitch,roll,accelX,accelY,accelZ,gyroX,gyroY,gyroZ,")
            writer.append("fx,fy,cx,cy,k1,k2,p1,p2,k3,cameraHeight,gpsOffsetX,gpsOffsetY,gpsOffsetZ,")
            writer.append("imageWidth,imageHeight,imagePath\n")

            var framesWithGPS = 0
            var framesWithIMU = 0

            capturedFrames.forEach { frame ->
                writer.append("\"${frame.frameId}\",")
                writer.append("${frame.captureTimeNanos},")
                writer.append("${frame.sequenceNumber},")

                frame.gps?.let { gps ->
                    writer.append("${gps.latitude},${gps.longitude},${gps.altitude},${gps.accuracy},${gps.speed},${gps.bearing},\"${gps.provider}\",")
                    framesWithGPS++
                } ?: writer.append(",,,,,,,")

                frame.imu?.let { imu ->
                    writer.append("${imu.azimuth},${imu.pitch},${imu.roll},${imu.accelX},${imu.accelY},${imu.accelZ},${imu.gyroX},${imu.gyroY},${imu.gyroZ},")
                    framesWithIMU++
                } ?: writer.append(",,,,,,,,,")

                writer.append("${frame.calibration.fx},${frame.calibration.fy},${frame.calibration.cx},${frame.calibration.cy},")
                writer.append("${frame.calibration.k1},${frame.calibration.k2},${frame.calibration.p1},${frame.calibration.p2},${frame.calibration.k3},")
                writer.append("${frame.calibration.cameraHeightMeters},${frame.calibration.gpsOffsetX},${frame.calibration.gpsOffsetY},${frame.calibration.gpsOffsetZ},")
                writer.append("${frame.imageWidth},${frame.imageHeight},\"${frame.imageFile.absolutePath}\"\n")
            }

            Log.d(TAG, " CSV Summary: ${capturedFrames.size} frames, $framesWithGPS with GPS, $framesWithIMU with IMU")
        }

        Log.d(TAG, " Created metadata CSV: ${csvFile.absolutePath}")
        return csvFile
    }

    fun getCapturedFrames(): List<CaptureFrame> = capturedFrames.toList()
    fun getFrameCount(): Int = capturedFrames.size
    fun getSessionId(): String = currentSessionId

    fun getTotalDataSize(): Long {
        var totalSize = 0L
        capturedFrames.forEach { frame ->
            if (frame.imageFile.exists()) {
                totalSize += frame.imageFile.length()
            }
        }
        return totalSize
    }

    fun clearSessionData() {
        capturedFrames.forEach { frame ->
            if (frame.imageFile.exists()) {
                frame.imageFile.delete()
            }
        }
        capturedFrames.clear()
        gpsBuffer.clear()
        imuBuffer.clear()
        Log.d(TAG, " Session data cleared")
    }
}