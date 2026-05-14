package com.example.bevdatacollector.projection

import android.content.Context
import android.util.Log
import com.example.bevdatacollector.utils.CalibrationHelper
import java.io.File
import java.io.FileWriter

private const val TAG = "LatLongConverter"

data class DetectionRecord(
    val frameIndex: Int,
    val trackId: Int,
    val objType: Int,
    val bboxX1: Float,
    val bboxY1: Float,
    val bboxX2: Float,
    val bboxY2: Float,
    val bottomCenterX: Float,
    val bottomCenterY: Float,
    val confidence: Float,
    var latitude: Double? = null,
    var longitude: Double? = null
)

class LatLongConverter(private val context: Context) {

    fun convertSession(sessionFolder: File): File? {
        try {
            //  Load tracking results
            val trackingCsv = File(sessionFolder, "tracking_results.csv")
            if (!trackingCsv.exists()) {
                Log.e(TAG, "No tracking_results.csv found in ${sessionFolder.name}")
                return null
            }

            //  Load calibration
            val calibrationHelper = CalibrationHelper(context)
            calibrationHelper.loadOrCreateCalibration()
            val cal = calibrationHelper.getCalibration()

            //  Load sensor data from metadata CSV
            val metadataCsv = sessionFolder.listFiles { file ->
                file.name.startsWith("metadata_") && file.name.endsWith(".csv")
            }?.firstOrNull()

            if (metadataCsv == null) {
                Log.e(TAG, "No metadata CSV found")
                return null
            }

            val sensorData = readSensorData(metadataCsv)
            if (sensorData == null) {
                Log.e(TAG, "Failed to read sensor data")
                return null
            }

            //  Parse tracking results
            val detections = parseTrackingCsv(trackingCsv)
            Log.d(TAG, "Loaded ${detections.size} detections")

            //  Apply orientation corrections (same as Python script)
            val headingCorrection = 1.5
            var correctedHeading = (sensorData.heading + headingCorrection) % 360.0
            if (correctedHeading < 0) correctedHeading += 360.0

            val correctedPitch = -sensorData.pitch - 5.3
            val correctedRoll = sensorData.roll

            val manualHeadingOffset = 0.5
            var finalHeading = (manualHeadingOffset - correctedHeading) % 360.0
            if (finalHeading < 0) finalHeading += 360.0

            Log.d(TAG, "Orientation: Raw Heading=${sensorData.heading}, Final Heading=$finalHeading")
            Log.d(TAG, "Orientation: Raw Pitch=${sensorData.pitch}, Corrected Pitch=$correctedPitch")
            Log.d(TAG, "Orientation: Raw Roll=${sensorData.roll}, Corrected Roll=$correctedRoll")

            //  Create projector with corrected orientation
            val projector = GroundPlaneProjector(
                fx = cal.fx.toDouble(),
                fy = cal.fy.toDouble(),
                cx = cal.cx.toDouble(),
                cy = cal.cy.toDouble(),
                k1 = cal.k1.toDouble(),
                k2 = cal.k2.toDouble(),
                p1 = cal.p1.toDouble(),
                p2 = cal.p2.toDouble(),
                k3 = cal.k3.toDouble(),
                cameraHeight = cal.cameraHeightMeters.toDouble(),
                headingDeg = finalHeading,
                pitchDeg = correctedPitch,
                rollDeg = correctedRoll,
                cameraLat = sensorData.latitude,
                cameraLon = sensorData.longitude
            )

            //  Convert each detection
            var convertedCount = 0
            for (detection in detections) {
                val result = projector.projectPixelToLatLon(
                    detection.bottomCenterX.toDouble(),
                    detection.bottomCenterY.toDouble()
                )

                if (result != null) {
                    detection.latitude = result.first
                    detection.longitude = result.second
                    convertedCount++
                }
            }

            Log.d(TAG, "Converted $convertedCount/${detections.size} detections")

            //  Save to CSV
            val outputFile = File(sessionFolder, "latlong_results.csv")
            saveToCsv(detections, outputFile, sensorData, finalHeading, correctedPitch, correctedRoll, cal.cameraHeightMeters.toDouble())

            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed: ${e.message}", e)
            return null
        }
    }

    private fun readSensorData(metadataCsv: File): SensorData? {
        try {
            val lines = metadataCsv.readLines()
            if (lines.size < 2) {
                Log.e(TAG, "CSV has less than 2 lines")
                return null
            }

            // Parse header (handle quoted fields)
            val header = parseCsvLine(lines[0])
            Log.d(TAG, "CSV Header: $header")

            // Find indices of required columns
            val latIdx = header.indexOf("latitude")
            val lonIdx = header.indexOf("longitude")
            val headingIdx = header.indexOf("heading")
            val pitchIdx = header.indexOf("pitch")
            val rollIdx = header.indexOf("roll")

            if (latIdx == -1 || lonIdx == -1) {
                Log.e(TAG, "Required columns not found. Header: $header")
                return null
            }

            // Find first row with valid GPS data
            for (i in 1 until lines.size) {
                val row = parseCsvLine(lines[i])
                if (row.size <= maxOf(latIdx, lonIdx)) continue

                val latStr = row[latIdx].trim()
                val lonStr = row[lonIdx].trim()

                if (latStr.isNotEmpty() && lonStr.isNotEmpty()) {
                    val latitude = latStr.toDoubleOrNull()
                    val longitude = lonStr.toDoubleOrNull()

                    if (latitude != null && longitude != null) {
                        Log.d(TAG, "Found sensor data at row $i: lat=$latitude, lon=$longitude")

                        return SensorData(
                            latitude = latitude,
                            longitude = longitude,
                            heading = if (headingIdx != -1 && headingIdx < row.size) row[headingIdx].toDoubleOrNull() ?: 0.0 else 0.0,
                            pitch = if (pitchIdx != -1 && pitchIdx < row.size) row[pitchIdx].toDoubleOrNull() ?: 0.0 else 0.0,
                            roll = if (rollIdx != -1 && rollIdx < row.size) row[rollIdx].toDoubleOrNull() ?: 0.0 else 0.0,
                            cameraHeight = 0.0
                        )
                    }
                }
            }

            Log.e(TAG, "No valid GPS data found in CSV")
            return null

        } catch (e: Exception) {
            Log.e(TAG, "Error reading sensor data: ${e.message}", e)
            return null
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        var current = StringBuilder()
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                    i++
                }
                else -> {
                    current.append(char)
                    i++
                }
            }
        }
        result.add(current.toString().trim())

        return result
    }

    private fun parseTrackingCsv(csvFile: File): MutableList<DetectionRecord> {
        val lines = csvFile.readLines()
        if (lines.isEmpty()) return mutableListOf()

        val detections = mutableListOf<DetectionRecord>()

        // Skip header row (index 0)
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val parts = line.split(",")
            if (parts.size < 10) {
                Log.w(TAG, "Skipping malformed line $i: ${parts.size} parts")
                continue
            }

            try {
                detections.add(
                    DetectionRecord(
                        frameIndex = parts[0].toInt(),
                        trackId = parts[1].toInt(),
                        objType = parts[2].toInt(),
                        bboxX1 = parts[3].toFloat(),
                        bboxY1 = parts[4].toFloat(),
                        bboxX2 = parts[5].toFloat(),
                        bboxY2 = parts[6].toFloat(),
                        bottomCenterX = parts[7].toFloat(),
                        bottomCenterY = parts[8].toFloat(),
                        confidence = parts[9].toFloat()
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing line $i: ${e.message}")
            }
        }

        return detections
    }

    private fun saveToCsv(
        detections: List<DetectionRecord>,
        outputFile: File,
        sensorData: SensorData,
        finalHeading: Double,
        correctedPitch: Double,
        correctedRoll: Double,
        cameraHeight: Double
    ) {
        FileWriter(outputFile).use { writer ->
            // Write header
            writer.write("frameIndex,trackId,objType,confidence,bottomCenterX,bottomCenterY,latitude,longitude\n")

            // Write data rows
            for (detection in detections) {
                writer.write("${detection.frameIndex},${detection.trackId},${detection.objType},${detection.confidence},")
                writer.write("${detection.bottomCenterX},${detection.bottomCenterY},")
                writer.write("${detection.latitude ?: 0.0},${detection.longitude ?: 0.0}\n")
            }

            // Write camera info as comments at the end
            writer.write("\n# Camera Info\n")
            writer.write("# Camera Latitude,${sensorData.latitude}\n")
            writer.write("# Camera Longitude,${sensorData.longitude}\n")
            writer.write("# Camera Height,$cameraHeight\n")
            writer.write("# Raw Heading,${sensorData.heading}\n")
            writer.write("# Raw Pitch,${sensorData.pitch}\n")
            writer.write("# Raw Roll,${sensorData.roll}\n")
            writer.write("# Final Heading,$finalHeading\n")
            writer.write("# Corrected Pitch,$correctedPitch\n")
            writer.write("# Corrected Roll,$correctedRoll\n")
        }

        Log.d(TAG, " CSV saved to: ${outputFile.absolutePath}")
    }

    private data class SensorData(
        val latitude: Double,
        val longitude: Double,
        val heading: Double,
        val pitch: Double,
        val roll: Double,
        val cameraHeight: Double
    )
}