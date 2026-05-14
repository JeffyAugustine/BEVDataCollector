package com.example.bevdatacollector.utils

import android.content.Context
import android.util.Log
import com.example.bevdatacollector.data.CameraCalibration
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class CalibrationHelper(private val context: Context) {
    private val TAG = "CalibrationHelper"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var calibration: CameraCalibration = CameraCalibration.default()
    private var imageWidth = 4000
    private var imageHeight = 2256

    private fun getCalibrationFile(): File {
        return File(context.getExternalFilesDir(null), "camera_calibration.json")
    }

    fun loadOrCreateCalibration() {
        val calibrationFile = getCalibrationFile()

        if (calibrationFile.exists()) {
            try {
                FileReader(calibrationFile).use { reader ->
                    calibration = gson.fromJson(reader, CameraCalibration::class.java)
                    Log.d(TAG, " Loaded calibration from file:")
                    Log.d(TAG, calibration.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load calibration: ${e.message}")
                createDefaultCalibration()
            }
        } else {
            Log.d(TAG, "No calibration file found. Creating default.")
            createDefaultCalibration()
        }
    }

    fun createDefaultCalibration() {
        calibration = CameraCalibration.default()
        saveCalibration()
        Log.d(TAG, " Created default calibration (NOT calibrated)")
    }

    fun markAsCalibrated() {
        calibration = calibration.copy(isCalibrated = true)
        saveCalibration()
        Log.d(TAG, " Calibration marked as user-calibrated")
    }

    fun setCalibratedValues(
        fx: Float, fy: Float, cx: Float, cy: Float,
        k1: Float, k2: Float, p1: Float, p2: Float, k3: Float,
        cameraHeightMeters: Float,
        gpsOffsetX: Float, gpsOffsetY: Float, gpsOffsetZ: Float
    ) {
        calibration = CameraCalibration.calibrated(
            fx = fx,
            fy = fy,
            cx = cx,
            cy = cy,
            k1 = k1,
            k2 = k2,
            p1 = p1,
            p2 = p2,
            k3 = k3,
            cameraHeightMeters = cameraHeightMeters,
            gpsOffsetX = gpsOffsetX,
            gpsOffsetY = gpsOffsetY,
            gpsOffsetZ = gpsOffsetZ
        )
        saveCalibration()
        Log.d(TAG, " Calibration saved with calibrated values")
        Log.d(TAG, calibration.toString())
    }

    fun updateImageSize(width: Int, height: Int) {
        val landscapeWidth = maxOf(width, height)
        val landscapeHeight = minOf(width, height)

        if (landscapeWidth != imageWidth || landscapeHeight != imageHeight) {
            imageWidth = landscapeWidth
            imageHeight = landscapeHeight
            Log.d(TAG, " Image size updated to ${imageWidth}x${imageHeight}")
        }
    }

    fun saveCalibration() {
        val calibrationFile = getCalibrationFile()
        try {
            FileWriter(calibrationFile).use { writer ->
                val json = gson.toJson(calibration)
                Log.d(TAG, "Saving calibration JSON: $json")
                writer.write(json)
            }
            Log.d(TAG, " Saved calibration to: ${calibrationFile.absolutePath}")
            Log.d(TAG, "File size: ${calibrationFile.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save calibration: ${e.message}", e)
        }
    }

    fun getCalibration(): CameraCalibration = calibration

    fun updateCalibration(newCalibration: CameraCalibration) {
        calibration = newCalibration
        saveCalibration()
        Log.d(TAG, " Calibration updated:")
        Log.d(TAG, calibration.toString())
    }

    fun updateCameraHeight(heightMeters: Float) {
        calibration = calibration.copy(cameraHeightMeters = heightMeters)
        saveCalibration()
        Log.d(TAG, " Camera height updated to: ${heightMeters}m")
    }

    fun updateGpsOffset(offsetX: Float, offsetY: Float, offsetZ: Float) {
        calibration = calibration.copy(
            gpsOffsetX = offsetX,
            gpsOffsetY = offsetY,
            gpsOffsetZ = offsetZ
        )
        saveCalibration()
        Log.d(TAG, " GPS offset updated: ($offsetX, $offsetY, $offsetZ)m")
    }

    // SIMPLE FILE-BASED CHECK - Does not break existing flow
    fun isCalibrated(): Boolean {
        // First check in-memory calibration
        if (calibration.hasValidValues() && calibration.isValid()) {
            return true
        }

        // Then check the file directly
        val calibrationFile = getCalibrationFile()
        if (!calibrationFile.exists()) {
            Log.d(TAG, "isCalibrated: No calibration file found")
            return false
        }

        return try {
            FileReader(calibrationFile).use { reader ->
                val tempCal = gson.fromJson(reader, CameraCalibration::class.java)
                val isValid = tempCal.hasValidValues()
                Log.d(TAG, "isCalibrated: File check = $isValid")
                isValid
            }
        } catch (e: Exception) {
            Log.e(TAG, "isCalibrated: Error reading file", e)
            false
        }
    }

    fun resetToDefault() {
        createDefaultCalibration()
        Log.d(TAG, " Reset to default calibration (not calibrated)")
    }
}